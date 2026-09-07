package net.myjda.payvi.ui.plugin

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import net.myjda.payvi.R
import net.myjda.payvi.ui.pairing.ConnectStoreActivity
import net.myjda.payvi.util.PluginAssetHelper

class GetPluginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_get_plugin)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.title_get_plugin)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val txtSaveStatus = findViewById<TextView>(R.id.txt_save_status)

        findViewById<MaterialButton>(R.id.btn_share).setOnClickListener {
            try {
                val intent = PluginAssetHelper.buildShareIntent(this)
                startActivity(Intent.createChooser(intent, getString(R.string.plugin_btn_share)))
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.plugin_save_failed), Toast.LENGTH_LONG).show()
            }
        }

        findViewById<MaterialButton>(R.id.btn_save).setOnClickListener {
            val saved = PluginAssetHelper.saveToDownloads(this)
            txtSaveStatus.visibility = android.view.View.VISIBLE
            txtSaveStatus.text = getString(
                if (saved) R.string.plugin_saved else R.string.plugin_save_failed
            )
        }

        findViewById<MaterialButton>(R.id.btn_continue).setOnClickListener {
            startActivity(Intent(this, ConnectStoreActivity::class.java))
        }
    }
}
