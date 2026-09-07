package net.myjda.payvi.ui.sms

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import net.myjda.payvi.R
import net.myjda.payvi.data.local.SmsSenderEntity

class SmsSenderAdapter(
    private val onToggle: (SmsSenderEntity, Boolean) -> Unit
) : ListAdapter<SmsSenderEntity, SmsSenderAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sms_sender, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onToggle)
    }

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val checkbox = itemView.findViewById<CheckBox>(R.id.checkbox_selected)
        private val txtAddress = itemView.findViewById<TextView>(R.id.txt_address)
        private val txtSummary = itemView.findViewById<TextView>(R.id.txt_summary)

        fun bind(sender: SmsSenderEntity, onToggle: (SmsSenderEntity, Boolean) -> Unit) {
            txtAddress.text = sender.address

            val when_ = if (sender.lastMessageMillis > 0) {
                DateUtils.getRelativeTimeSpanString(sender.lastMessageMillis)
            } else {
                ""
            }
            txtSummary.text = "${sender.messageCount} messages · last $when_"

            // Avoid feedback loops from list re-binds triggering onCheckedChangeListener.
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = sender.selected
            checkbox.setOnCheckedChangeListener { _, isChecked -> onToggle(sender, isChecked) }

            itemView.setOnClickListener { checkbox.toggle() }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SmsSenderEntity>() {
            override fun areItemsTheSame(oldItem: SmsSenderEntity, newItem: SmsSenderEntity) =
                oldItem.address == newItem.address
            override fun areContentsTheSame(oldItem: SmsSenderEntity, newItem: SmsSenderEntity) =
                oldItem == newItem
        }
    }
}
