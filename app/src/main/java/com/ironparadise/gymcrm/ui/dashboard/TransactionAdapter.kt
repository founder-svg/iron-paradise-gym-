package com.ironparadise.gymcrm.ui.dashboard

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ironparadise.gymcrm.R
import com.ironparadise.gymcrm.data.db.entities.StockTransactionEntity
import com.ironparadise.gymcrm.databinding.ItemTransactionBinding
import com.ironparadise.gymcrm.util.Formatters

class TransactionAdapter(
    private val onItemClick: ((StockTransactionEntity) -> Unit)? = null
) : ListAdapter<StockTransactionEntity, TransactionAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(private val binding: ItemTransactionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StockTransactionEntity) {
            val context = binding.root.context
            binding.tvProductName.text = item.productName

            val dateStr = Formatters.formatDateTime(item.date)
            val refStr = if (item.referenceId.isNotBlank()) " • Ref: ${item.referenceId}" else ""
            binding.tvTransactionMeta.text = "$dateStr$refStr"

            val isStockIn = item.transactionType in listOf("STOCK_IN", "SALE_RETURN", "ADJUSTMENT_IN")
            val sign = if (isStockIn) "+" else "-"

            when (item.transactionType) {
                "STOCK_IN" -> {
                    binding.iconContainer.setBackgroundResource(R.drawable.bg_tx_icon_green)
                    binding.ivTransactionIcon.setImageResource(R.drawable.ic_download)
                    binding.ivTransactionIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_green))
                    binding.tvQuantityBadge.text = "+${item.quantity} Stock In"
                    binding.tvQuantityBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_green))
                    binding.tvQuantityBadge.setBackgroundResource(R.drawable.bg_badge_in_stock)
                }
                "SALE" -> {
                    binding.iconContainer.setBackgroundResource(R.drawable.bg_tx_icon_red)
                    binding.ivTransactionIcon.setImageResource(R.drawable.ic_cart)
                    binding.ivTransactionIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_red))
                    binding.tvQuantityBadge.text = "-${item.quantity} Sold"
                    binding.tvQuantityBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_red))
                    binding.tvQuantityBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock)
                }
                "SALE_RETURN" -> {
                    binding.iconContainer.setBackgroundResource(R.drawable.bg_tx_icon_green)
                    binding.ivTransactionIcon.setImageResource(R.drawable.ic_download)
                    binding.ivTransactionIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_blue))
                    binding.tvQuantityBadge.text = "+${item.quantity} Return"
                    binding.tvQuantityBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_blue))
                    binding.tvQuantityBadge.setBackgroundResource(R.drawable.bg_badge_in_stock)
                }
                "ADJUSTMENT_IN" -> {
                    binding.iconContainer.setBackgroundResource(R.drawable.bg_tx_icon_amber)
                    binding.ivTransactionIcon.setImageResource(R.drawable.ic_nav_dashboard)
                    binding.ivTransactionIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_gold))
                    binding.tvQuantityBadge.text = "+${item.quantity} Adj In"
                    binding.tvQuantityBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_gold))
                    binding.tvQuantityBadge.setBackgroundResource(R.drawable.bg_badge_low_stock)
                }
                "ADJUSTMENT_OUT" -> {
                    binding.iconContainer.setBackgroundResource(R.drawable.bg_tx_icon_amber)
                    binding.ivTransactionIcon.setImageResource(R.drawable.ic_nav_dashboard)
                    binding.ivTransactionIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent_gold))
                    binding.tvQuantityBadge.text = "-${item.quantity} Adj Out"
                    binding.tvQuantityBadge.setTextColor(ContextCompat.getColor(context, R.color.accent_gold))
                    binding.tvQuantityBadge.setBackgroundResource(R.drawable.bg_badge_low_stock)
                }
                else -> {
                    binding.iconContainer.setBackgroundResource(R.drawable.bg_tx_icon_amber)
                    binding.ivTransactionIcon.setImageResource(R.drawable.ic_nav_dashboard)
                    binding.ivTransactionIcon.setColorFilter(ContextCompat.getColor(context, R.color.text_primary))
                    binding.tvQuantityBadge.text = "$sign${item.quantity} ${item.transactionType}"
                    binding.tvQuantityBadge.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                }
            }

            binding.tvBalanceRemaining.text = "Bal: ${item.balanceAfter} units"

            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<StockTransactionEntity>() {
            override fun areItemsTheSame(oldItem: StockTransactionEntity, newItem: StockTransactionEntity): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StockTransactionEntity, newItem: StockTransactionEntity): Boolean =
                oldItem == newItem
        }
    }
}
