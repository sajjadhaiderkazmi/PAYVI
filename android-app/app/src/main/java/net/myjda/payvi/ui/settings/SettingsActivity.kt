package net.myjda.payvi.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import net.myjda.payvi.R
import net.myjda.payvi.data.prefs.PayviPrefs
import net.myjda.payvi.data.repo.PairingRepository
import net.myjda.payvi.service.PayviSyncService
import net.myjda.payvi.ui.pairing.ConnectStoreActivity
import net.myjda.payvi.ui.plugin.GetPluginActivity
import net.myjda.payvi.ui.sms.SmsSenderSelectionActivity
import net.myjda.payvi.work.PayviSyncWorker

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PayviPrefs
    private lateinit var editPollInterval: TextInputEditText
    private lateinit var editLookback: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = PayviPrefs.getInstance(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.title_settings)
        toolbar.setTitleTextColor(android.graphics.Color.WHITE)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editPollInterval = findViewById(R.id.edit_poll_interval)
        editLookback = findViewById(R.id.edit_lookback)

        editPollInterval.setText(prefs.syncIntervalMinutes.toString())
        editLookback.setText(prefs.smsLookbackDays.toString())

        findViewById<MaterialButton>(R.id.btn_save_settings).setOnClickListener {
            saveSettings()
        }

        findViewById<MaterialButton>(R.id.btn_disconnect).setOnClickListener {
            confirmDisconnect()
        }

        findViewById<MaterialButton>(R.id.btn_get_plugin).setOnClickListener {
            startActivity(Intent(this, GetPluginActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btn_connect_store).setOnClickListener {
            startActivity(Intent(this, ConnectStoreActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btn_sms_senders).setOnClickListener {
            startActivity(Intent(this, SmsSenderSelectionActivity::class.java))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun saveSettings() {
        val interval = editPollInterval.text?.toString()?.toIntOrNull()?.coerceIn(1, 240)
            ?: PayviPrefs.DEFAULT_SYNC_INTERVAL_MINUTES
        val lookback = editLookback.text?.toString()?.toIntOrNull()?.coerceIn(1, 30)
            ?: PayviPrefs.DEFAULT_SMS_LOOKBACK_DAYS

        prefs.syncIntervalMinutes = interval
        prefs.smsLookbackDays = lookback

        if (prefs.backgroundSyncEnabled) {
            // Re-schedule so the new interval takes effect immediately.
            PayviSyncWorker.schedule(this, interval)
        }

        android.widget.Toast.makeText(this, getString(R.string.action_save), android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun confirmDisconnect() {
        AlertDialog.Builder(this)
            .setMessage(R.string.settings_disconnect_confirm)
            .setPositiveButton(R.string.action_ok) { _, _ -> disconnect() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun disconnect() {
        PayviSyncService.stop(this)
        PayviSyncWorker.cancel(this)
        prefs.backgroundSyncEnabled = false
        PairingRepository(prefs).disconnect()
        finish()
    }
}
