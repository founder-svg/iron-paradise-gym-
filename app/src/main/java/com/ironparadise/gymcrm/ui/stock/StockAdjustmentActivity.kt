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
import com.ironparadise.gymcrm.databinding.ActivityStockAdjustmentBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StockAdjustmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockAdjustmentBinding
    private val app by lazy { IronParadiseApp.instance }

    private var productList: List<ProductEntity> = emptyList()
    private var selectedProduct: ProductEntity? = null
    private var currentSystemStock: Int = 0

    private val reasons = listOf(
        "Physical Counting Correction",
        "Damaged Product",
        "Expired Supplement",
        "Lost / Discrepancy",
        "Customer Returned",
        "Other"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockAdjustmentBinding.inflate(layoutInflater)
        setContentView(binding.root)



        binding.btnBack.setOnClickListener { finish() }

        setupReasonSpinner()
        setupListeners()
        loadProducts()
    }

    private fun setupReasonSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, reasons)
        binding.spinnerAdjReason.adapter = adapter
    }

    private fun loadProducts() {
        lifecycleScope.launch(Dispatchers.IO) {
            productList = app.repository.allProducts.first()
            withContext(Dispatchers.Main) {
                if (productList.isNotEmpty()) {
                    val titles = productList.map { "${it.name} (${it.brand})" }
                    val adapter = ArrayAdapter(this@StockAdjustmentActivity, android.R.layout.simple_spinner_dropdown_item, titles)
                    binding.spinnerAdjProducts.adapter = adapter

                    binding.spinnerAdjProducts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            selectedProduct = productList[position]
                            selectedProduct?.let { p ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    currentSystemStock = app.repository.getNetStock(p.id)
                                    withContext(Dispatchers.Main) {
                                        binding.tvSystemStock.text = "$currentSystemStock units"
                                        updateDiff()
                                    }
                                }
                            }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.etPhysicalStock.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateDiff()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSubmitAdjustment.setOnClickListener {
            submitAdjustment()
        }
    }

    private fun updateDiff() {
        val physical = binding.etPhysicalStock.text.toString().toIntOrNull()
        if (physical != null) {
            val diff = physical - currentSystemStock
            val sign = if (diff > 0) "+" else ""
            val status = when {
                diff > 0 -> "Surplus (Stock In)"
                diff < 0 -> "Deficit (Stock Out)"
                else -> "Exact Match"
            }
            binding.tvAdjustmentDiff.text = "$sign$diff units ($status)"
        } else {
            binding.tvAdjustmentDiff.text = "Enter physical count"
        }
    }

    private fun submitAdjustment() {
        val product = selectedProduct
        if (product == null) {
            Toast.makeText(this, "Please select a product", Toast.LENGTH_SHORT).show()
            return
        }

        val physical = binding.etPhysicalStock.text.toString().toIntOrNull()
        if (physical == null || physical < 0) {
            Toast.makeText(this, "Please enter a valid physical count", Toast.LENGTH_SHORT).show()
            return
        }

        if (physical == currentSystemStock) {
            Toast.makeText(this, "Physical stock matches system stock. No adjustment needed.", Toast.LENGTH_LONG).show()
            return
        }

        val reason = binding.spinnerAdjReason.selectedItem.toString()
        val notes = binding.etAdjNotes.text.toString().trim()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.repository.recordStockAdjustment(
                    productId = product.id,
                    physicalCount = physical,
                    reason = reason,
                    notes = notes.ifBlank { null },
                    user = app.sessionManager.userName
                )

                val diff = physical - currentSystemStock
                val isAddition = diff > 0
                val qty = if (isAddition) diff else -diff
                val transType = if (isAddition) "ADJUSTMENT_IN" else "ADJUSTMENT_OUT"
                val tx = com.ironparadise.gymcrm.data.db.entities.StockTransactionEntity(
                    productId = product.id,
                    productName = product.name,
                    transactionType = transType,
                    quantity = qty,
                    unitPrice = product.purchasePrice,
                    total = qty * product.purchasePrice,
                    referenceId = "ADJ-${System.currentTimeMillis()}",
                    date = System.currentTimeMillis(),
                    notes = "Adjustment ($reason). System: $currentSystemStock -> Physical: $physical. $notes".trim(),
                    createdBy = app.sessionManager.userName,
                    balanceAfter = physical
                )
                com.ironparadise.gymcrm.data.network.CloudSyncManager.pushStockTransaction(applicationContext, tx)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@StockAdjustmentActivity, "Inventory Reconciled: ${product.name} is now $physical units", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@StockAdjustmentActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
