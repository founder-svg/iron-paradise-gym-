package com.ironparadise.gymcrm.ui.stock

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ironparadise.gymcrm.R
import com.ironparadise.gymcrm.data.db.entities.StockTransactionEntity
import com.ironparadise.gymcrm.databinding.ItemInventoryTableRowBinding
import com.ironparadise.gymcrm.util.Formatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InventoryTableAdapter(
    private val onItemClick: ((StockTransactionEntity) -> Unit)? = null
) : RecyclerView.Adapter<InventoryTableAdapter.ViewHolder>() {

    private var items: List<StockTransactionEntity> = emptyList()
    private val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

    fun submitList(newItems: List<StockTransactionEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemInventoryTableRowBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInventoryTableRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b = holder.binding
        val context = b.root.context

        // 1. Sr.no
        b.tvTableSrNo.text = "${position + 1}"

        // 2. Date
        b.tvTableDate.text = dateFormat.format(Date(item.date))

        // 3. Product
        b.tvTableProduct.text = item.productName
        b.tvTableTypeBadge.text = item.transactionType.replace("_", " ")

        // 4. Quantity (with +/- sign and color)
        val isAddition = item.transactionType in listOf("STOCK_IN", "SALE_RETURN", "ADJUSTMENT_IN")
        if (isAddition) {
            b.tvTableQuantity.text = "+${item.quantity}"
            b.tvTableQuantity.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
        } else {
            b.tvTableQuantity.text = "-${item.quantity}"
            b.tvTableQuantity.setTextColor(ContextCompat.getColor(context, R.color.accent_red))
        }

        // 5. Price
        b.tvTablePrice.text = Formatters.formatCurrency(item.unitPrice)

        // 6. Total = Quantity x Price
        val total = if (item.total > 0) item.total else (item.quantity * item.unitPrice)
        b.tvTableTotal.text = Formatters.formatCurrency(total)

        // 7. Balance
        b.tvTableBalance.text = "${item.balanceAfter}"
        if (item.balanceAfter <= 0) {
            b.tvTableBalance.setTextColor(ContextCompat.getColor(context, R.color.accent_red))
        } else {
            b.tvTableBalance.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
        }

        b.root.setOnClickListener {
            onItemClick?.invoke(item)
        }
    }

    override fun getItemCount(): Int = items.size
}
