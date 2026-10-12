package com.ironparadise.gymcrm.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.databinding.FragmentDashboardBinding
import com.ironparadise.gymcrm.ui.products.ProductsListActivity
import com.ironparadise.gymcrm.ui.sales.NewSaleActivity
import com.ironparadise.gymcrm.ui.stock.StockInActivity
import com.ironparadise.gymcrm.ui.stock.StockLedgerActivity
import com.ironparadise.gymcrm.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { IronParadiseApp.instance }
    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupQuickActions()
        loadDashboardData()
    }

    override fun onResume() {
        super.onResume()
        loadDashboardData()
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter { transaction ->
            val intent = Intent(requireContext(), StockLedgerActivity::class.java).apply {
                putExtra("EXTRA_PRODUCT_ID", transaction.productId)
            }
            startActivity(intent)
        }
        binding.rvRecentTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionAdapter
        }
    }

    private fun setupQuickActions() {
        val isAdmin = app.sessionManager.isAdmin
        if (isAdmin) {
            binding.btnQuickStockIn.visibility = View.VISIBLE
            binding.btnQuickStockIn.setOnClickListener {
                startActivity(Intent(requireContext(), StockInActivity::class.java))
            }
        } else {
            binding.btnQuickStockIn.visibility = View.GONE
        }

        binding.btnQuickNewSale.setOnClickListener {
            startActivity(Intent(requireContext(), NewSaleActivity::class.java))
        }

        binding.btnQuickCreateInvoice.setOnClickListener {
            startActivity(Intent(requireContext(), NewSaleActivity::class.java))
        }

        binding.btnQuickProducts.setOnClickListener {
            startActivity(Intent(requireContext(), ProductsListActivity::class.java))
        }

        binding.tvViewAllHistory.setOnClickListener {
            // Can open stock screen or products
        }
    }

    private fun loadDashboardData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val stats = app.repository.getDashboardStats()
            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    binding.tvTotalProducts.text = "${stats.totalProducts} Products"
                    binding.tvTotalStockUnits.text = "${stats.totalStockUnits} Units"
                    binding.tvTodaySales.text = Formatters.formatCurrency(stats.todaySalesAmount)
                    binding.tvTodayItemsSold.text = "${stats.todayItemsSold} Items"
                    binding.tvLowStockCount.text = "${stats.lowStockCount} Products"
                    binding.tvOutOfStockCount.text = "${stats.outOfStockCount} Products"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            app.repository.getRecentTransactions(10).collect { transactions ->
                if (_binding != null) {
                    transactionAdapter.submitList(transactions)
                    binding.tvEmptyTransactions.visibility =
                        if (transactions.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
