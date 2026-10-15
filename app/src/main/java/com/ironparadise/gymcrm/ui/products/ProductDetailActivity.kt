package com.ironparadise.gymcrm.ui.products

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.R
import com.ironparadise.gymcrm.data.db.entities.ProductEntity
import com.ironparadise.gymcrm.databinding.ActivityProductDetailBinding
import com.ironparadise.gymcrm.ui.stock.StockInActivity
import com.ironparadise.gymcrm.ui.stock.StockLedgerActivity
import com.ironparadise.gymcrm.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private val app by lazy { IronParadiseApp.instance }
    private var currentProduct: ProductEntity? = null
    private var currentStock: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val productId = intent.getStringExtra("EXTRA_PRODUCT_ID") ?: ""
        if (productId.isNotBlank()) {
            loadProduct(productId)
        } else {
            Toast.makeText(this, "Product not found", Toast.LENGTH_SHORT).show()
            finish()
        }

        setupTabClicks(productId)
        setupActionButtons()
    }

    private fun loadProduct(productId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val itemWithStock = app.repository.getProductWithStock(productId)
            withContext(Dispatchers.Main) {
                if (itemWithStock != null) {
                    currentProduct = itemWithStock.product
                    currentStock = itemWithStock.currentStock
                    bindProductData(itemWithStock.product, itemWithStock.currentStock)
                } else {
                    Toast.makeText(this@ProductDetailActivity, "Unable to load product", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun bindProductData(product: ProductEntity, stock: Int) {
        val isAdmin = app.sessionManager.isAdmin

        binding.tvProductName.text = product.name
        binding.tvBrandCategory.text = "${product.brand} | ${product.category}"
        binding.tvSellingPrice.text = Formatters.formatCurrency(product.sellingPrice)

        // Stock Badge - prominent SOLD OUT for staff and admin
        if (stock <= 0) {
            binding.tvStockBadge.text = "SOLD OUT"
            binding.tvStockBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
            binding.tvStockBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock)
        } else if (stock <= product.minimumStock) {
            binding.tvStockBadge.text = "Low Stock"
            binding.tvStockBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_gold))
            binding.tvStockBadge.setBackgroundResource(R.drawable.bg_badge_low_stock)
        } else {
            binding.tvStockBadge.text = "In Stock"
            binding.tvStockBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.tvStockBadge.setBackgroundResource(R.drawable.bg_badge_in_stock)
        }

        // Specs
        binding.tvProductId.text = product.id
        binding.tvBrand.text = product.brand
        binding.tvCategory.text = product.category
        binding.tvVariant.text = product.variant.ifBlank { "Standard" }
        binding.tvUnit.text = product.unit.ifBlank { "Unit" }
        binding.tvMinStock.text = "${product.minimumStock}"
        binding.tvCurrentBalance.text = "$stock ${product.unit.ifBlank { "units" }}"
        binding.tvSupplier.text = "Generic Supplier"

        // Purchase Price & Financials visibility (Available to all users)
        binding.rowPurchasePrice.visibility = View.VISIBLE
        binding.dividerPurchasePrice.visibility = View.VISIBLE
        binding.tvPurchasePrice.text = Formatters.formatCurrency(product.purchasePrice)
        binding.layoutBottomActions.visibility = View.VISIBLE
        binding.tabStockHistory.visibility = View.VISIBLE

        // Load photo
        if (!product.imageUri.isNullOrBlank()) {
            binding.ivProductImage.imageTintList = null
            com.ironparadise.gymcrm.util.ImageHelper.loadImage(binding.ivProductImage, product.imageUri)
        } else {
            binding.ivProductImage.setImageResource(R.drawable.ic_supplement_jar)
            binding.ivProductImage.setColorFilter(ContextCompat.getColor(this, R.color.accent_gold))
            binding.ivProductImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun setupTabClicks(productId: String) {
        binding.tabStockHistory.setOnClickListener {
            val intent = Intent(this, StockLedgerActivity::class.java).apply {
                putExtra("EXTRA_PRODUCT_ID", productId)
            }
            startActivity(intent)
        }

        binding.tabRelated.setOnClickListener {
            Toast.makeText(this, "No related variants configured yet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupActionButtons() {
        binding.btnEditProduct.setOnClickListener {
            val product = currentProduct ?: return@setOnClickListener
            val intent = Intent(this, AddEditProductActivity::class.java).apply {
                putExtra("EXTRA_PRODUCT_ID", product.id)
            }
            startActivity(intent)
        }

        binding.btnStockInProduct.setOnClickListener {
            val product = currentProduct ?: return@setOnClickListener
            val intent = Intent(this, StockInActivity::class.java).apply {
                putExtra("EXTRA_PRODUCT_ID", product.id)
            }
            startActivity(intent)
        }

        binding.btnDeleteProduct.setOnClickListener {
            val product = currentProduct ?: return@setOnClickListener
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Remove Supplement")
                .setMessage("Are you sure you want to completely remove \"${product.name}\" from the catalog? This will delete the product and its stock history.")
                .setPositiveButton("Remove Completely") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        app.repository.deleteProduct(product.id)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ProductDetailActivity, "Supplement removed successfully", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        currentProduct?.id?.let { loadProduct(it) }
    }
}
