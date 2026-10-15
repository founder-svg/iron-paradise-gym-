package com.ironparadise.gymcrm.ui.stock

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.data.model.ProductWithStock
import com.ironparadise.gymcrm.databinding.FragmentStockBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StockFragment : Fragment() {

    private var _binding: FragmentStockBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { IronParadiseApp.instance }
    private lateinit var stockAdapter: ProductStockAdapter
    private lateinit var tableAdapter: InventoryTableAdapter

    private var rawList: List<ProductWithStock> = emptyList()
    private var rawTransactions: List<com.ironparadise.gymcrm.data.db.entities.StockTransactionEntity> = emptyList()
    private var currentFilterType: FilterType = FilterType.ALL
    private var selectedCategory: String? = null
    private var searchQuery: String = ""

    enum class FilterType { ALL, IN_STOCK, LOW_STOCK, OUT_OF_STOCK }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupListeners()
        observeStock()
        observeTransactions()
    }

    private fun setupRecyclerViews() {
        stockAdapter = ProductStockAdapter(
            onProductClick = { item ->
                val intent = Intent(requireContext(), com.ironparadise.gymcrm.ui.products.ProductDetailActivity::class.java).apply {
                    putExtra("EXTRA_PRODUCT_ID", item.product.id)
                }
                startActivity(intent)
            },
            onEditClick = { item ->
                val intent = Intent(requireContext(), com.ironparadise.gymcrm.ui.products.AddEditProductActivity::class.java).apply {
                    putExtra("EXTRA_PRODUCT_ID", item.product.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { item ->
                confirmDeleteProduct(item.product.id, item.product.name)
            }
        )
        binding.rvProducts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = stockAdapter
        }

        tableAdapter = InventoryTableAdapter { item ->
            showInventoryRowActionDialog(item)
        }
        binding.rvInventoryTable.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tableAdapter
        }
    }

    private fun showInventoryRowActionDialog(item: com.ironparadise.gymcrm.data.db.entities.StockTransactionEntity) {
        val options = arrayOf(
            "✏️ Edit Supplement & Stock",
            "🗑️ Remove Supplement Completely",
            "➕ Stock In (Add Units)",
            "⚖️ Adjust Stock Count",
            "📜 View Stock Ledger History"
        )
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(item.productName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        startActivity(Intent(requireContext(), com.ironparadise.gymcrm.ui.products.AddEditProductActivity::class.java).apply {
                            putExtra("EXTRA_PRODUCT_ID", item.productId)
                        })
                    }
                    1 -> {
                        confirmDeleteProduct(item.productId, item.productName)
                    }
                    2 -> {
                        startActivity(Intent(requireContext(), StockInActivity::class.java).apply {
                            putExtra("EXTRA_PRODUCT_ID", item.productId)
                        })
                    }
                    3 -> {
                        startActivity(Intent(requireContext(), StockAdjustmentActivity::class.java))
                    }
                    4 -> {
                        startActivity(Intent(requireContext(), StockLedgerActivity::class.java).apply {
                            putExtra("EXTRA_PRODUCT_ID", item.productId)
                        })
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteProduct(productId: String, productName: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Remove Supplement")
            .setMessage("Are you sure you want to completely remove \"$productName\" from inventory and catalog? This will delete the product and its stock history.")
            .setPositiveButton("Remove Completely") { _, _ ->
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    app.repository.deleteProduct(productId)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(requireContext(), "Supplement removed from inventory", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupListeners() {
        binding.btnStockInHeader.visibility = View.VISIBLE
        binding.fabAddProduct.visibility = View.VISIBLE
        binding.btnStockInHeader.setOnClickListener {
            startActivity(Intent(requireContext(), StockInActivity::class.java))
        }
        binding.fabAddProduct.setOnClickListener {
            startActivity(Intent(requireContext(), com.ironparadise.gymcrm.ui.products.AddEditProductActivity::class.java))
        }

        binding.toggleViewMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == binding.btnViewTable.id) {
                    binding.hsvInventoryTable.visibility = View.VISIBLE
                    binding.rvProducts.visibility = View.GONE
                    binding.tvTableScrollHint.visibility = View.VISIBLE
                } else {
                    binding.hsvInventoryTable.visibility = View.GONE
                    binding.rvProducts.visibility = View.VISIBLE
                    binding.tvTableScrollHint.visibility = View.GONE
                }
                applyFilters()
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString().trim()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipGroupFilter.setOnCheckedChangeListener { _, checkedId ->
            selectedCategory = null
            currentFilterType = FilterType.ALL
            when (checkedId) {
                binding.chipInStock.id -> currentFilterType = FilterType.IN_STOCK
                binding.chipLowStock.id -> currentFilterType = FilterType.LOW_STOCK
                binding.chipOutOfStock.id -> currentFilterType = FilterType.OUT_OF_STOCK
                binding.chipProtein.id -> selectedCategory = "Protein"
                binding.chipCreatine.id -> selectedCategory = "Creatine"
                binding.chipBcaa.id -> selectedCategory = "BCAA"
                binding.chipOthers.id -> selectedCategory = "Others"
                else -> { /* All */ }
            }
            applyFilters()
        }
    }

    private fun observeStock() {
        viewLifecycleOwner.lifecycleScope.launch {
            app.repository.getProductsWithStockFlow().collectLatest { list ->
                rawList = list
                applyFilters()
            }
        }
    }

    private fun observeTransactions() {
        viewLifecycleOwner.lifecycleScope.launch {
            app.repository.allTransactions.collectLatest { txs ->
                rawTransactions = txs
                applyFilters()
            }
        }
    }

    private fun applyFilters() {
        // 1. Filter Products for Cards View
        var filteredProducts = rawList
        filteredProducts = when (currentFilterType) {
            FilterType.IN_STOCK -> filteredProducts.filter { !it.isOutOfStock }
            FilterType.LOW_STOCK -> filteredProducts.filter { it.isLowStock }
            FilterType.OUT_OF_STOCK -> filteredProducts.filter { it.isOutOfStock }
            FilterType.ALL -> filteredProducts
        }
        if (selectedCategory != null) {
            if (selectedCategory == "Others") {
                filteredProducts = filteredProducts.filter {
                    !it.product.category.contains("Protein", ignoreCase = true) &&
                    !it.product.category.contains("Creatine", ignoreCase = true) &&
                    !it.product.category.contains("BCAA", ignoreCase = true)
                }
            } else {
                filteredProducts = filteredProducts.filter {
                    it.product.category.contains(selectedCategory!!, ignoreCase = true)
                }
            }
        }
        if (searchQuery.isNotBlank()) {
            filteredProducts = filteredProducts.filter {
                it.product.name.contains(searchQuery, ignoreCase = true) ||
                        it.product.brand.contains(searchQuery, ignoreCase = true) ||
                        it.product.category.contains(searchQuery, ignoreCase = true) ||
                        it.product.id.contains(searchQuery, ignoreCase = true)
            }
        }
        stockAdapter.submitList(filteredProducts)

        // 2. Filter Transactions for Table View
        var filteredTxs = rawTransactions
        if (searchQuery.isNotBlank()) {
            filteredTxs = filteredTxs.filter {
                it.productName.contains(searchQuery, ignoreCase = true) ||
                        it.referenceId.contains(searchQuery, ignoreCase = true) ||
                        it.transactionType.contains(searchQuery, ignoreCase = true)
            }
        }
        tableAdapter.submitList(filteredTxs)

        val isTable = binding.toggleViewMode.checkedButtonId == binding.btnViewTable.id
        val isEmpty = if (isTable) filteredTxs.isEmpty() else filteredProducts.isEmpty()
        binding.tvEmptyStock.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
