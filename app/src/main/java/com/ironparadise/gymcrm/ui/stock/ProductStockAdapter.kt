package com.ironparadise.gymcrm.ui.stock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ironparadise.gymcrm.R
import com.ironparadise.gymcrm.data.model.ProductWithStock
import com.ironparadise.gymcrm.databinding.ItemProductStockBinding
import com.ironparadise.gymcrm.util.Formatters

class ProductStockAdapter(
    private val onProductClick: (ProductWithStock) -> Unit,
    private val onEditClick: ((ProductWithStock) -> Unit)? = null,
    private val onDeleteClick: ((ProductWithStock) -> Unit)? = null
) : ListAdapter<ProductWithStock, ProductStockAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(private val binding: ItemProductStockBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ProductWithStock) {
            val context = binding.root.context
            val p = item.product

            binding.tvBrandCategory.text = "${p.brand} | ${p.category}"
            binding.tvProductName.text = p.name
            binding.tvStockUnits.text = "Stock: ${item.currentStock}"
            binding.tvSellingPrice.text = Formatters.formatCurrency(p.sellingPrice)

            if (!p.imageUri.isNullOrBlank()) {
                binding.ivProductThumb.imageTintList = null
                com.ironparadise.gymcrm.util.ImageHelper.loadImage(binding.ivProductThumb, p.imageUri)
            } else {
                binding.ivProductThumb.setImageResource(R.drawable.ic_supplement_jar)
                binding.ivProductThumb.setColorFilter(ContextCompat.getColor(context, R.color.accent_gold))
                binding.ivProductThumb.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }

            when {
                item.isOutOfStock -> {
                    binding.tvStockStatusBadge.text = "SOLD OUT"
                    binding.tvStockStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_out_of_stock_text))
                    binding.tvStockStatusBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock)
                }
                item.isLowStock -> {
                    binding.tvStockStatusBadge.text = "Low Stock"
                    binding.tvStockStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_low_stock_text))
                    binding.tvStockStatusBadge.setBackgroundResource(R.drawable.bg_badge_low_stock)
                }
                else -> {
                    binding.tvStockStatusBadge.text = "In Stock"
                    binding.tvStockStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_in_stock_text))
                    binding.tvStockStatusBadge.setBackgroundResource(R.drawable.bg_badge_in_stock)
                }
            }

            binding.btnEditStock.setOnClickListener {
                onEditClick?.invoke(item)
            }

            binding.btnDeleteStock.setOnClickListener {
                onDeleteClick?.invoke(item)
            }

            binding.root.setOnClickListener {
                onProductClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProductStockBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ProductWithStock>() {
            override fun areItemsTheSame(oldItem: ProductWithStock, newItem: ProductWithStock): Boolean =
                oldItem.product.id == newItem.product.id

            override fun areContentsTheSame(oldItem: ProductWithStock, newItem: ProductWithStock): Boolean =
                oldItem == newItem
        }
    }
}
