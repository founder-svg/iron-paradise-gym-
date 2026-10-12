package com.ironparadise.gymcrm.ui.sales

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.databinding.FragmentSalesBinding
import com.ironparadise.gymcrm.ui.invoices.InvoiceAdapter
import com.ironparadise.gymcrm.ui.invoices.InvoiceDetailActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SalesFragment : Fragment() {

    private var _binding: FragmentSalesBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { IronParadiseApp.instance }
    private lateinit var saleAdapter: InvoiceAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        binding.btnNewSaleHeader.setOnClickListener {
            startActivity(Intent(requireContext(), NewSaleActivity::class.java))
        }
        observeSales()
    }

    private fun setupRecyclerView() {
        saleAdapter = InvoiceAdapter { sale ->
            val intent = Intent(requireContext(), InvoiceDetailActivity::class.java).apply {
                putExtra("EXTRA_SALE_ID", sale.id)
            }
            startActivity(intent)
        }
        binding.rvSales.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = saleAdapter
        }
    }

    private fun observeSales() {
        viewLifecycleOwner.lifecycleScope.launch {
            app.repository.allSales.collectLatest { list ->
                saleAdapter.submitList(list)
                binding.tvEmptySales.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
