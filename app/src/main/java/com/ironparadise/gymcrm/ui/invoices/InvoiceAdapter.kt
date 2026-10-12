package com.ironparadise.gymcrm.ui.invoices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ironparadise.gymcrm.R
import com.ironparadise.gymcrm.data.db.entities.SaleEntity
import com.ironparadise.gymcrm.databinding.ItemInvoiceBinding
import com.ironparadise.gymcrm.util.Formatters

class InvoiceAdapter(
    private val onInvoiceClick: (SaleEntity) -> Unit
) : ListAdapter<SaleEntity, InvoiceAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(private val binding: ItemInvoiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SaleEntity) {
            val context = binding.root.context
            binding.tvInvoiceNumber.text = item.invoiceNumber
            binding.tvCustomerName.text = item.customerName
            binding.tvInvoiceDate.text = "${Formatters.formatDate(item.date)} • ${item.paymentMethod}"
            binding.tvGrandTotal.text = Formatters.formatCurrency(item.grandTotal)

            if (item.status == "CANCELLED") {
                binding.tvInvoiceStatus.text = "CANCELLED"
                binding.tvInvoiceStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_red))
                binding.tvInvoiceStatus.setBackgroundResource(R.drawable.bg_badge_out_of_stock)
            } else {
                binding.tvInvoiceStatus.text = "PAID"
                binding.tvInvoiceStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
                binding.tvInvoiceStatus.setBackgroundResource(R.drawable.bg_badge_in_stock)
            }

            binding.root.setOnClickListener {
                onInvoiceClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInvoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SaleEntity>() {
            override fun areItemsTheSame(oldItem: SaleEntity, newItem: SaleEntity): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: SaleEntity, newItem: SaleEntity): Boolean =
                oldItem == newItem
        }
    }
}
