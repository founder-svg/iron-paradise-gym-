package com.ironparadise.gymcrm.ui.stock

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.databinding.ActivityStockLedgerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StockLedgerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStockLedgerBinding
    private val app by lazy { IronParadiseApp.instance }
    private lateinit var tableAdapter: InventoryTableAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockLedgerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()

        val productId = intent.getStringExtra("EXTRA_PRODUCT_ID")
        if (productId != null) {
            loadLedger(productId)
        } else {
            // Load first product if available
            lifecycleScope.launch(Dispatchers.IO) {
                val list = app.repository.allProducts.first()
                if (list.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        loadLedger(list.first().id)
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        tableAdapter = InventoryTableAdapter()
        binding.rvLedgerRows.apply {
            layoutManager = LinearLayoutManager(this@StockLedgerActivity)
            adapter = tableAdapter
        }
    }

    private fun loadLedger(productId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val productWithStock = app.repository.getProductWithStock(productId)
            val entries = app.repository.getProductLedger(productId)

            withContext(Dispatchers.Main) {
                if (productWithStock != null) {
                    val p = productWithStock.product
                    binding.tvLedgerBrand.text = p.brand.uppercase()
                    binding.tvLedgerProductName.text = p.name
                    binding.tvLedgerVariant.text = "${p.variant} • ${p.unit}"
                    binding.tvCurrentBalance.text = "${productWithStock.currentStock} units"
                }

                val transactions = entries.map { it.transaction }
                tableAdapter.submitList(transactions)
                binding.tvEmptyLedger.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
