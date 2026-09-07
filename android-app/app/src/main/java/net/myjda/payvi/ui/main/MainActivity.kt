package net.myjda.payvi.ui.main

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import net.myjda.payvi.R
import net.myjda.payvi.data.prefs.PayviPrefs
import net.myjda.payvi.service.PayviSyncService
import net.myjda.payvi.ui.orders.OrdersListActivity
import net.myjda.payvi.ui.pairing.ConnectStoreActivity
import net.myjda.payvi.ui.plugin.GetPluginActivity
import net.myjda.payvi.ui.settings.SettingsActivity
import net.myjda.payvi.ui.sms.SmsSenderSelectionActivity
import net.myjda.payvi.work.PayviSyncWorker

/**
 * The dashboard - but only once setup is done. Setup is a one-time,
 * ordered wizard (Get Plugin -> Connect Store -> Choose SMS Numbers ->
 * Confirm), and this activity is also the entry point the launcher opens,
 * so on every create/resume it first checks whether the wizard has been
 * completed and, if not, sends the user to whichever step comes next
 * instead of showing the dashboard.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PayviPrefs
    private var redirecting = false

    private lateinit var txtConnectionStatus: TextView
    private lateinit var txtLastSync: TextView
    private lateinit var switchBackgroundSync: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PayviPrefs.getInstance(this)

        if (redirectToWizardIfNeeded()) {
            return // activity is finishing - don't inflate the dashboard
        }

        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.app_name)
        setSupportActionBar(toolbar)

        txtConnectionStatus = findViewById(R.id.txt_connection_status)
        txtLastSync = findViewById(R.id.txt_last_sync)
        switchBackgroundSync = findViewById(R.id.switch_background_sync)

        findViewById<MaterialButton>(R.id.btn_orders).setOnClickListener {
            startActivity(Intent(this, OrdersListActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        switchBackgroundSync.setOnCheckedChangeListener { button, isChecked ->
            if (!button.isPressed) return@setOnCheckedChangeListener // programmatic set, ignore
            prefs.backgroundSyncEnabled = isChecked
            applyBackgroundSyncState(isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        if (redirecting) return
        if (redirectToWizardIfNeeded()) return
        refreshStatus()
    }

    /** Returns true (and finishes this activity) if the setup wizard
     * isn't done yet and we just launched the next step of it. */
    private fun redirectToWizardIfNeeded(): Boolean {
        val next = when {
            !prefs.isPaired -> GetPluginActivity::class.java
            !prefs.onboardingComplete -> SmsSenderSelectionActivity::class.java
            else -> null
        } ?: return false

        redirecting = true
        startActivity(Intent(this, next))
        finish()
        return true
    }

    private fun refreshStatus() {
        if (prefs.isPaired) {
            txtConnectionStatus.text = getString(
                R.string.main_connected_to,
                prefs.siteName ?: prefs.siteUrl.orEmpty()
            )
        } else {
            txtConnectionStatus.text = getString(R.string.main_not_connected)
        }

        val lastSync = prefs.lastSyncAtMillis
        txtLastSync.text = getString(
            R.string.main_last_sync,
            if (lastSync > 0) {
                DateUtils.getRelativeTimeSpanString(lastSync).toString()
            } else {
                getString(R.string.main_never)
            }
        )

        switchBackgroundSync.isChecked = prefs.backgroundSyncEnabled
        switchBackgroundSync.text = getString(
            if (prefs.backgroundSyncEnabled) R.string.main_sync_toggle_on else R.string.main_sync_toggle_off
        )
    }

    private fun applyBackgroundSyncState(enabled: Boolean) {
        if (enabled) {
            PayviSyncService.start(this)
            PayviSyncWorker.schedule(this, prefs.syncIntervalMinutes)
        } else {
            PayviSyncService.stop(this)
            PayviSyncWorker.cancel(this)
        }
        switchBackgroundSync.text = getString(
            if (enabled) R.string.main_sync_toggle_on else R.string.main_sync_toggle_off
        )
    }
}
