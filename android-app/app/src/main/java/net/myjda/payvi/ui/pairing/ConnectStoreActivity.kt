package net.myjda.payvi.ui.pairing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import net.myjda.payvi.R
import net.myjda.payvi.data.model.PairingPayload
import net.myjda.payvi.data.prefs.PayviPrefs
import net.myjda.payvi.data.repo.PairingRepository
import net.myjda.payvi.data.repo.PairingResult
import net.myjda.payvi.ui.sms.SmsSenderSelectionActivity

class ConnectStoreActivity : AppCompatActivity() {

    private val gson = Gson()
    private lateinit var repository: PairingRepository

    private lateinit var layoutManual: View
    private lateinit var editSite: TextInputEditText
    private lateinit var editKey: TextInputEditText
    private lateinit var editSecret: TextInputEditText
    private lateinit var progress: ProgressBar
    private lateinit var txtStatus: TextView

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw.isNullOrBlank()) {
            return@registerForActivityResult // user cancelled the scan
        }
        val payload = parsePayload(raw)
        if (payload == null) {
            txtStatus.text = getString(R.string.connect_invalid_qr)
            return@registerForActivityResult
        }
        attemptConnect(payload)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchScanner()
        } else {
            txtStatus.text = getString(R.string.connect_camera_permission_needed)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect_store)

        repository = PairingRepository(PayviPrefs.getInstance(this))

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.title_connect_store)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        layoutManual = findViewById(R.id.layout_manual)
        editSite = findViewById(R.id.edit_site)
        editKey = findViewById(R.id.edit_key)
        editSecret = findViewById(R.id.edit_secret)
        progress = findViewById(R.id.progress)
        txtStatus = findViewById(R.id.txt_status)

        findViewById<TextView>(R.id.txt_manual_toggle).setOnClickListener {
            layoutManual.visibility = if (layoutManual.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        findViewById<MaterialButton>(R.id.btn_scan).setOnClickListener {
            requestCameraAndScan()
        }

        findViewById<MaterialButton>(R.id.btn_verify).setOnClickListener {
            val payload = PairingPayload(
                site = editSite.text?.toString()?.trim().orEmpty(),
                key = editKey.text?.toString()?.trim().orEmpty(),
                secret = editSecret.text?.toString()?.trim().orEmpty()
            )
            if (!payload.isValid()) {
                txtStatus.text = getString(R.string.connect_failed_generic)
                return@setOnClickListener
            }
            attemptConnect(payload)
        }
    }

    private fun requestCameraAndScan() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            launchScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setBeepEnabled(false)
            .setOrientationLocked(true)
        scanLauncher.launch(options)
    }

    private fun parsePayload(raw: String): PairingPayload? {
        return try {
            val payload = gson.fromJson(raw, PairingPayload::class.java)
            if (payload != null && payload.isValid()) payload else null
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    private fun attemptConnect(payload: PairingPayload) {
        progress.visibility = View.VISIBLE
        txtStatus.text = getString(R.string.connect_verifying)

        lifecycleScope.launch {
            val result = repository.verifyAndSave(payload)
            progress.visibility = View.GONE

            when (result) {
                is PairingResult.Success -> {
                    txtStatus.text = getString(R.string.connect_success, result.siteName)
                    Toast.makeText(
                        this@ConnectStoreActivity,
                        getString(R.string.connect_success, result.siteName),
                        Toast.LENGTH_SHORT
                    ).show()
                    startActivity(Intent(this@ConnectStoreActivity, SmsSenderSelectionActivity::class.java))
                    finish()
                }
                is PairingResult.Unauthorized ->
                    txtStatus.text = getString(R.string.connect_failed_unauthorized)
                is PairingResult.NetworkError ->
                    txtStatus.text = getString(R.string.connect_failed_network)
                is PairingResult.OtherError ->
                    txtStatus.text = result.message
            }
        }
    }
}
