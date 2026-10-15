package com.ironparadise.gymcrm.ui.products

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.R
import com.ironparadise.gymcrm.data.db.entities.ProductEntity
import com.ironparadise.gymcrm.databinding.ActivityProductsListBinding
import com.ironparadise.gymcrm.databinding.ItemProductManageBinding
import com.ironparadise.gymcrm.util.Formatters
import com.ironparadise.gymcrm.util.ImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductsListBinding
    private val app by lazy { IronParadiseApp.instance }
    private lateinit var adapter: ProductManageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnAddProduct.visibility = android.view.View.VISIBLE
        binding.btnAddProduct.setOnClickListener {
            startActivity(Intent(this, AddEditProductActivity::class.java))
        }

        setupRecyclerView()
        observeProducts()
    }

    private fun setupRecyclerView() {
        adapter = ProductManageAdapter(
            isAdmin = true,
            onItemClick = { product ->
                val intent = Intent(this, ProductDetailActivity::class.java).apply {
                    putExtra("EXTRA_PRODUCT_ID", product.id)
                }
                startActivity(intent)
            },
            onEditClick = { product ->
                val intent = Intent(this, AddEditProductActivity::class.java).apply {
                    putExtra("EXTRA_PRODUCT_ID", product.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { product ->
                confirmDeleteProduct(product)
            }
        )
        binding.rvProductsManage.apply {
            layoutManager = LinearLayoutManager(this@ProductsListActivity)
            adapter = this@ProductsListActivity.adapter
        }
    }

    private fun confirmDeleteProduct(product: ProductEntity) {
        AlertDialog.Builder(this)
            .setTitle("Remove Supplement")
            .setMessage("Are you sure you want to completely remove \"${product.name}\" from the catalog? This will delete the product, its stock history, and attached photo.")
            .setPositiveButton("Remove Completely") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    app.repository.deleteProduct(product.id)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ProductsListActivity, "Supplement removed successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeProducts() {
        lifecycleScope.launch {
            app.repository.allProducts.collectLatest { list ->
                adapter.submitList(list)
            }
        }
    }

    inner class ProductManageAdapter(
        private val isAdmin: Boolean,
        private val onItemClick: (ProductEntity) -> Unit,
        private val onEditClick: (ProductEntity) -> Unit,
        private val onDeleteClick: (ProductEntity) -> Unit
    ) : RecyclerView.Adapter<ProductManageAdapter.ViewHolder>() {

        private var items: List<ProductEntity> = emptyList()

        fun submitList(newItems: List<ProductEntity>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemProductManageBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemProductManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val b = holder.binding
            val context = b.root.context

            b.tvProductId.text = item.id
            b.tvProductName.text = item.name
            b.tvProductDetails.text = "${item.category} • ${item.variant} • ${item.unit}"
            b.tvSellingPrice.text = Formatters.formatCurrency(item.sellingPrice)

            if (isAdmin) {
                b.tvCostPrice.visibility = android.view.View.VISIBLE
                b.tvCostPrice.text = "Cost: ${Formatters.formatCurrency(item.purchasePrice)}"
                b.btnEditProduct.visibility = android.view.View.VISIBLE
                b.btnDeleteProduct.visibility = android.view.View.VISIBLE
                b.btnEditProduct.setOnClickListener { onEditClick(item) }
                b.btnDeleteProduct.setOnClickListener { onDeleteClick(item) }
            } else {
                b.tvCostPrice.visibility = android.view.View.GONE
                b.btnEditProduct.visibility = android.view.View.GONE
                b.btnDeleteProduct.visibility = android.view.View.GONE
            }

            // Thumbnail Image
            if (!item.imageUri.isNullOrBlank()) {
                b.ivProductThumb.imageTintList = null
                ImageHelper.loadImage(b.ivProductThumb, item.imageUri)
            } else {
                b.ivProductThumb.setImageResource(R.drawable.ic_supplement_jar)
                b.ivProductThumb.setColorFilter(ContextCompat.getColor(context, R.color.accent_gold))
                b.ivProductThumb.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            }

            b.root.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
