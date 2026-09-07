package net.myjda.payvi.ui.orders

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import net.myjda.payvi.R
import net.myjda.payvi.data.local.OrderEntity
import net.myjda.payvi.data.local.PayviDatabase
import net.myjda.payvi.data.prefs.PayviPrefs
import net.myjda.payvi.data.repo.OrderRepository
import net.myjda.payvi.matching.PayviStatus
import net.myjda.payvi.sync.SyncCoordinator
import net.myjda.payvi.util.StatusUi

class OrderDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
    }

    private lateinit var repository: OrderRepository
    private var orderId: Long = -1

    private lateinit var txtOrderNumber: TextView
    private lateinit var txtOrderMeta: TextView
    private lateinit var txtStatusBadge: TextView
    private lateinit var imgScreenshot: ImageView
    private lateinit var txtOpenScreenshot: TextView
    private lateinit var btnRecheck: MaterialButton
    private lateinit var txtOcrFields: TextView
    private lateinit var txtMatchedSms: TextView

    private var currentOrder: OrderEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_detail)

        orderId = intent.getLongExtra(EXTRA_ORDER_ID, -1)
        if (orderId <= 0) {
            finish()
            return
        }

        val db = PayviDatabase.getInstance(this)
        repository = OrderRepository(PayviPrefs.getInstance(this), db.orderDao())

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.title_order_detail)
        toolbar.setTitleTextColor(android.graphics.Color.WHITE)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        txtOrderNumber = findViewById(R.id.txt_order_number)
        txtOrderMeta = findViewById(R.id.txt_order_meta)
        txtStatusBadge = findViewById(R.id.txt_status_badge)
        imgScreenshot = findViewById(R.id.img_screenshot)
        txtOpenScreenshot = findViewById(R.id.txt_open_screenshot)
        btnRecheck = findViewById(R.id.btn_recheck)
        txtOcrFields = findViewById(R.id.txt_ocr_fields)
        txtMatchedSms = findViewById(R.id.txt_matched_sms)

        btnRecheck.setOnClickListener { recheck() }

        findViewById<MaterialButton>(R.id.btn_status_completed).setOnClickListener {
            setManualStatus(PayviStatus.COMPLETED)
        }
        findViewById<MaterialButton>(R.id.btn_status_not_sure).setOnClickListener {
            setManualStatus(PayviStatus.NOT_SURE)
        }
        findViewById<MaterialButton>(R.id.btn_status_not_received).setOnClickListener {
            setManualStatus(PayviStatus.NOT_RECEIVED)
        }
        findViewById<MaterialButton>(R.id.btn_status_duplicate).setOnClickListener {
            setManualStatus(PayviStatus.DUPLICATE)
        }

        loadOrder()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadOrder() {
        lifecycleScope.launch {
            val order = repository.getOrder(orderId) ?: run {
                finish()
                return@launch
            }
            currentOrder = order
            render(order)
        }
    }

    private fun render(order: OrderEntity) {
        txtOrderNumber.text = getString(R.string.order_row_number, order.number)
        txtOrderMeta.text = "${order.billingName}  •  Rs ${order.total}  •  ${order.billingPhone}"

        val status = StatusUi.effectiveStatus(order.localStatus, order.payviStatus)
        txtStatusBadge.text = StatusUi.label(this, status)
        val bg = (txtStatusBadge.background as? GradientDrawable)?.mutate() as? GradientDrawable
        bg?.setColor(StatusUi.color(this, status))
        txtStatusBadge.background = bg ?: txtStatusBadge.background

        if (order.hasScreenshot && order.screenshotUrl.isNotBlank()) {
            imgScreenshot.load(order.screenshotUrl) {
                crossfade(true)
            }
            txtOpenScreenshot.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(order.screenshotUrl)))
                } catch (e: Exception) {
                    Toast.makeText(this@OrderDetailActivity, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            txtOpenScreenshot.text = getString(R.string.order_no_screenshot)
            txtOpenScreenshot.setOnClickListener(null)
        }

        val ocrLines = listOfNotNull(
            order.ocrName?.let { "Name: $it" },
            order.ocrAmount?.let { "Amount: $it" },
            order.ocrDate?.let { "Date: $it" },
            order.ocrTxnId?.let { "Transaction ID: $it" },
            order.ocrNumber?.let { "Number: $it" }
        )
        txtOcrFields.text = if (ocrLines.isEmpty()) "—" else ocrLines.joinToString("\n")

        txtMatchedSms.text = order.matchedSmsBody?.takeIf { it.isNotBlank() }
            ?: getString(R.string.order_no_matched_sms)
    }

    private fun recheck() {
        val order = currentOrder ?: return
        btnRecheck.isEnabled = false
        lifecycleScope.launch {
            SyncCoordinator.processOrder(this@OrderDetailActivity, order.id)
            loadOrder()
            btnRecheck.isEnabled = true
        }
    }

    private fun setManualStatus(status: PayviStatus) {
        lifecycleScope.launch {
            repository.setManualStatus(orderId, status)
            loadOrder()
        }
    }
}
