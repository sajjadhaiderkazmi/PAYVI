package net.myjda.payvi.ui.orders

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.myjda.payvi.R
import net.myjda.payvi.data.local.OrderEntity
import net.myjda.payvi.util.StatusUi

class OrderAdapter(
    private val onClick: (OrderEntity) -> Unit
) : ListAdapter<OrderEntity, OrderAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_order, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val txtNumber = itemView.findViewById<android.widget.TextView>(R.id.txt_order_number)
        private val txtName = itemView.findViewById<android.widget.TextView>(R.id.txt_billing_name)
        private val txtAmount = itemView.findViewById<android.widget.TextView>(R.id.txt_amount)
        private val txtStatus = itemView.findViewById<android.widget.TextView>(R.id.txt_status)

        fun bind(order: OrderEntity, onClick: (OrderEntity) -> Unit) {
            val context = itemView.context
            txtNumber.text = context.getString(R.string.order_row_number, order.number)
            txtName.text = order.billingName
            txtAmount.text = context.getString(R.string.order_row_amount, order.total)

            val status = StatusUi.effectiveStatus(order.localStatus, order.payviStatus)
            txtStatus.text = StatusUi.label(context, status)

            val bg = (txtStatus.background as? GradientDrawable)?.mutate() as? GradientDrawable
            bg?.setColor(StatusUi.color(context, status))
            txtStatus.background = bg ?: txtStatus.background

            itemView.setOnClickListener { onClick(order) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<OrderEntity>() {
            override fun areItemsTheSame(oldItem: OrderEntity, newItem: OrderEntity) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: OrderEntity, newItem: OrderEntity) = oldItem == newItem
        }
    }
}
