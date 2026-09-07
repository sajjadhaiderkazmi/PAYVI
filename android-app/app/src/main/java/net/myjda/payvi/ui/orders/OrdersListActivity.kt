package net.myjda.payvi.ui.orders

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.myjda.payvi.R
import net.myjda.payvi.data.local.PayviDatabase
import net.myjda.payvi.data.prefs.PayviPrefs
import net.myjda.payvi.data.repo.OrderRepository
import net.myjda.payvi.data.repo.SyncResult

class OrdersListActivity : AppCompatActivity() {

    private lateinit var repository: OrderRepository
    private lateinit var adapter: OrderAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var txtEmpty: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders_list)

        val db = PayviDatabase.getInstance(this)
        repository = OrderRepository(PayviPrefs.getInstance(this), db.orderDao())

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.title_orders)
        toolbar.setTitleTextColor(android.graphics.Color.WHITE)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = OrderAdapter { order ->
            startActivity(
                Intent(this, OrderDetailActivity::class.java)
                    .putExtra(OrderDetailActivity.EXTRA_ORDER_ID, order.id)
            )
        }

        findViewById<RecyclerView>(R.id.recycler_orders).apply {
            layoutManager = LinearLayoutManager(this@OrdersListActivity)
            adapter = this@OrdersListActivity.adapter
        }

        swipeRefresh = findViewById(R.id.swipe_refresh)
        txtEmpty = findViewById(R.id.txt_empty)
        swipeRefresh.setOnRefreshListener { refresh() }

        lifecycleScope.launch {
            repository.observeOrders().collectLatest { orders ->
                adapter.submitList(orders)
                txtEmpty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        refresh()
    }

    private fun refresh() {
        swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val result = repository.syncOrders()
            swipeRefresh.isRefreshing = false
            if (result is SyncResult.NotPaired) {
                txtEmpty.text = getString(R.string.orders_not_connected)
                txtEmpty.visibility = View.VISIBLE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
