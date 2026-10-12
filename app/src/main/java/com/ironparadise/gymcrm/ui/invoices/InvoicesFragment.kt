package com.ironparadise.gymcrm.ui.invoices

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
import com.ironparadise.gymcrm.data.db.entities.SaleEntity
import com.ironparadise.gymcrm.databinding.FragmentInvoicesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class InvoicesFragment : Fragment() {

    private var _binding: FragmentInvoicesBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { IronParadiseApp.instance }
    private lateinit var invoiceAdapter: InvoiceAdapter
    private var rawInvoices: List<SaleEntity> = emptyList()
    private var searchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        observeInvoices()
    }

    private fun setupRecyclerView() {
        invoiceAdapter = InvoiceAdapter { sale ->
            val intent = Intent(requireContext(), InvoiceDetailActivity::class.java).apply {
                putExtra("EXTRA_SALE_ID", sale.id)
            }
            startActivity(intent)
        }
        binding.rvInvoices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = invoiceAdapter
        }
    }

    private fun setupSearch() {
        binding.etInvoiceSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString().trim()
                filterInvoices()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeInvoices() {
        viewLifecycleOwner.lifecycleScope.launch {
            app.repository.allSales.collectLatest { list ->
                rawInvoices = list
                filterInvoices()
            }
        }
    }

    private fun filterInvoices() {
        val filtered = if (searchQuery.isBlank()) {
            rawInvoices
        } else {
            rawInvoices.filter {
                it.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                        it.customerName.contains(searchQuery, ignoreCase = true) ||
                        it.customerPhone.contains(searchQuery, ignoreCase = true)
            }
        }
        invoiceAdapter.submitList(filtered)
        binding.tvEmptyInvoices.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
