package com.ironparadise.gymcrm.ui.stock

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.data.db.entities.ProductEntity
import com.ironparadise.gymcrm.databinding.ActivityStockInBinding
import com.ironparadise.gymcrm.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class StockInActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockInBinding
    private val app by lazy { IronParadiseApp.instance }

    private var productList: List<ProductEntity> = emptyList()
    private var selectedProduct: ProductEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockInBinding.inflate(layoutInflater)
        setContentView(binding.root)



        setupHeader()
        setupListeners()
        loadProducts()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener { finish() }

        val dateCode = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val randomSuffix = (100..999).random()
        binding.tvEntryId.text = "STK-$dateCode-$randomSuffix"
        binding.tvDate.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    }

    private var currentProductStock: Int = 0

    private fun loadProducts() {
        lifecycleScope.launch(Dispatchers.IO) {
            productList = app.repository.allProducts.first()
            withContext(Dispatchers.Main) {
                if (productList.isNotEmpty()) {
                    val titles = productList.map { "${it.name} (${it.brand} - ${it.variant})" }
                    val adapter = ArrayAdapter(this@StockInActivity, android.R.layout.simple_spinner_dropdown_item, titles)
                    binding.spinnerProducts.adapter = adapter

                    binding.spinnerProducts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            selectedProduct = productList[position]
                            selectedProduct?.let { p ->
                                binding.etPurchasePrice.setText(p.purchasePrice.toString())
                                fetchProductStock(p.id)
                            }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }

                    val extraId = intent.getStringExtra("EXTRA_PRODUCT_ID")
                    if (!extraId.isNullOrBlank()) {
                        val targetIdx = productList.indexOfFirst { it.id == extraId }
                        if (targetIdx != -1) {
                            binding.spinnerProducts.setSelection(targetIdx)
                        }
                    }
                } else {
                    Toast.makeText(this@StockInActivity, "Please add products first in Product Master", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchProductStock(productId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            currentProductStock = app.repository.getNetStock(productId)
            withContext(Dispatchers.Main) {
                updateLiveTotal()
            }
        }
    }

    private fun setupListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateLiveTotal()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        binding.etQuantity.addTextChangedListener(watcher)
        binding.etPurchasePrice.addTextChangedListener(watcher)

        binding.btnSubmitStockIn.setOnClickListener {
            submitStockIn()
        }
    }

    private fun updateLiveTotal() {
        val qty = binding.etQuantity.text.toString().toIntOrNull() ?: 0
        val price = binding.etPurchasePrice.text.toString().toDoubleOrNull() ?: 0.0
        val total = qty * price
        binding.tvTotalValue.text = Formatters.formatCurrency(total)

        val newBalance = currentProductStock + qty
        binding.tvStockReconciliation.text = "Existing: $currentProductStock units | Stock In: +$qty units | New Balance: $newBalance units"
    }

    private fun submitStockIn() {
        val product = selectedProduct
        if (product == null) {
            Toast.makeText(this, "Please select a product", Toast.LENGTH_SHORT).show()
            return
        }

        val qty = binding.etQuantity.text.toString().toIntOrNull()
        if (qty == null || qty <= 0) {
            Toast.makeText(this, "Please enter a valid quantity", Toast.LENGTH_SHORT).show()
            return
        }

        val purchasePrice = binding.etPurchasePrice.text.toString().toDoubleOrNull()
        if (purchasePrice == null || purchasePrice < 0) {
            Toast.makeText(this, "Please enter purchase price", Toast.LENGTH_SHORT).show()
            return
        }

        val supplier = binding.etSupplier.text.toString().trim()
        val batch = binding.etBatchNumber.text.toString().trim()
        val expiry = binding.etExpiryDate.text.toString().trim()
        val notes = binding.etNotes.text.toString().trim()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.repository.recordStockIn(
                    productId = product.id,
                    quantity = qty,
                    purchasePrice = purchasePrice,
                    supplier = supplier.ifBlank { null },
                    batchNumber = batch.ifBlank { null },
                    expiryDate = expiry.ifBlank { null },
                    notes = notes.ifBlank { null },
                    user = app.sessionManager.userName
                )

                // Push stock transaction to cloud
                val tx = com.ironparadise.gymcrm.data.db.entities.StockTransactionEntity(
                    productId = product.id,
                    productName = product.name,
                    transactionType = "STOCK_IN",
                    quantity = qty,
                    unitPrice = purchasePrice,
                    total = qty * purchasePrice,
                    referenceId = binding.tvEntryId.text.toString(),
                    date = System.currentTimeMillis(),
                    notes = "Supplier: $supplier | Batch: $batch | Exp: $expiry | $notes".trim(),
                    createdBy = app.sessionManager.userName,
                    balanceAfter = currentProductStock + qty
                )
                com.ironparadise.gymcrm.data.network.CloudSyncManager.pushStockTransaction(applicationContext, tx)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@StockInActivity, "Stock In Recorded: +$qty units of ${product.name}", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@StockInActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
