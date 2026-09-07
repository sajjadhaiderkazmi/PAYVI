package net.myjda.payvi.ui.sms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.myjda.payvi.R
import net.myjda.payvi.data.local.PayviDatabase
import net.myjda.payvi.data.local.SmsSenderEntity
import net.myjda.payvi.data.prefs.PayviPrefs
import net.myjda.payvi.sms.SmsReader
import net.myjda.payvi.ui.main.MainActivity

class SmsSenderSelectionActivity : AppCompatActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS
    )

    private lateinit var layoutPermission: View
    private lateinit var layoutList: View
    private lateinit var txtPermissionDenied: android.widget.TextView
    private lateinit var txtEmpty: android.widget.TextView
    private lateinit var adapter: SmsSenderAdapter

    private val db by lazy { PayviDatabase.getInstance(this) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onPermissionGranted()
        } else {
            txtPermissionDenied.visibility = View.VISIBLE
            txtPermissionDenied.text = getString(R.string.sms_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_senders)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.title_sms_senders)
        toolbar.setTitleTextColor(android.graphics.Color.WHITE)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        layoutPermission = findViewById(R.id.layout_permission)
        layoutList = findViewById(R.id.layout_list)
        txtPermissionDenied = findViewById(R.id.txt_permission_denied)
        txtEmpty = findViewById(R.id.txt_empty_senders)

        adapter = SmsSenderAdapter { sender, isChecked ->
            lifecycleScope.launch {
                db.smsSenderDao().setSelected(sender.address, isChecked)
            }
        }

        findViewById<RecyclerView>(R.id.recycler_senders).apply {
            layoutManager = LinearLayoutManager(this@SmsSenderSelectionActivity)
            adapter = this@SmsSenderSelectionActivity.adapter
        }

        findViewById<MaterialButton>(R.id.btn_grant_permission).setOnClickListener {
            permissionLauncher.launch(requiredPermissions)
        }

        findViewById<MaterialButton>(R.id.btn_confirm).setOnClickListener {
            onConfirmClicked()
        }

        lifecycleScope.launch {
            db.smsSenderDao().observeAll().collectLatest { senders ->
                adapter.submitList(senders)
                txtEmpty.visibility = if (senders.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()) {
            onPermissionGranted()
        } else {
            layoutPermission.visibility = View.VISIBLE
            layoutList.visibility = View.GONE
        }
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun onPermissionGranted() {
        layoutPermission.visibility = View.GONE
        layoutList.visibility = View.VISIBLE
        refreshSenders()
    }

    private fun refreshSenders() {
        lifecycleScope.launch {
            val senders = withContext(Dispatchers.IO) {
                SmsReader.querySenders(this@SmsSenderSelectionActivity)
            }
            val entities = senders.map {
                SmsSenderEntity(
                    address = it.address,
                    messageCount = it.messageCount,
                    lastMessageMillis = it.lastMessageMillis
                )
            }
            val dao = db.smsSenderDao()
            // IGNORE on conflict: doesn't clobber a sender the user already
            // selected on a previous visit to this screen.
            dao.insertIfAbsent(entities)
            // Refresh message-count/last-seen for senders that already
            // existed (insertIfAbsent skipped those rows entirely).
            entities.forEach { dao.updateStats(it.address, it.messageCount, it.lastMessageMillis) }
        }
    }

    private fun onConfirmClicked() {
        lifecycleScope.launch {
            val hasSelection = db.smsSenderDao().getSelected().isNotEmpty()
            if (hasSelection) {
                finishOnboarding()
            } else {
                AlertDialog.Builder(this@SmsSenderSelectionActivity)
                    .setMessage(R.string.sms_confirm_none_selected)
                    .setPositiveButton(R.string.action_ok) { _, _ -> finishOnboarding() }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
        }
    }

    /** Marks setup as done (this is the last step of the wizard) and opens
     * the dashboard, clearing the wizard screens out of the back stack so
     * the back button from the dashboard exits the app instead of
     * re-entering setup. */
    private fun finishOnboarding() {
        PayviPrefs.getInstance(this).onboardingComplete = true
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
