package com.ironparadise.gymcrm.ui.customers

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.data.db.entities.CustomerEntity
import com.ironparadise.gymcrm.databinding.ActivityCustomersBinding
import com.ironparadise.gymcrm.databinding.ItemCustomerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomersBinding
    private val app by lazy { IronParadiseApp.instance }
    private lateinit var customerAdapter: CustomerAdapter
    private var rawCustomers: List<CustomerEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnAddCustomer.setOnClickListener {
            showAddCustomerDialog()
        }

        setupRecyclerView()
        setupSearch()
        observeCustomers()
    }

    private fun setupRecyclerView() {
        customerAdapter = CustomerAdapter { customer ->
            val intent = Intent(this, CustomerDetailActivity::class.java).apply {
                putExtra("EXTRA_CUSTOMER_ID", customer.id)
            }
            startActivity(intent)
        }
        binding.rvCustomers.apply {
            layoutManager = LinearLayoutManager(this@CustomersActivity)
            adapter = customerAdapter
        }
    }

    private fun setupSearch() {
        binding.etCustomerSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCustomers(s.toString().trim())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeCustomers() {
        lifecycleScope.launch {
            app.repository.allCustomers.collectLatest { list ->
                rawCustomers = list
                filterCustomers(binding.etCustomerSearch.text.toString().trim())
            }
        }
    }

    private fun filterCustomers(query: String) {
        val filtered = if (query.isBlank()) {
            rawCustomers
        } else {
            rawCustomers.filter {
                it.name.contains(query, ignoreCase = true) || it.phone.contains(query, ignoreCase = true)
            }
        }
        customerAdapter.submitList(filtered)
    }

    private fun showAddCustomerDialog() {
        val view = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val etName = EditText(this).apply { hint = "Full Name *" }
        val etPhone = EditText(this).apply {
            hint = "Mobile Number *"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val etAddress = EditText(this).apply { hint = "Address / Notes" }

        view.addView(etName)
        view.addView(etPhone)
        view.addView(etAddress)

        AlertDialog.Builder(this)
            .setTitle("Add New Customer")
            .setView(view)
            .setPositiveButton("Save Customer") { _, _ ->
                val name = etName.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val address = etAddress.text.toString().trim()

                if (name.isBlank() || phone.isBlank()) {
                    Toast.makeText(this, "Name and Phone are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    app.repository.addCustomer(
                        CustomerEntity(name = name, phone = phone, address = address.ifBlank { null })
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CustomersActivity, "Customer $name saved!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class CustomerAdapter(
        private val onItemClick: (CustomerEntity) -> Unit
    ) : RecyclerView.Adapter<CustomerAdapter.ViewHolder>() {

        private var list: List<CustomerEntity> = emptyList()

        fun submitList(newList: List<CustomerEntity>) {
            list = newList
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemCustomerBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCustomerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            val b = holder.binding

            b.tvCustomerName.text = item.name
            b.tvCustomerPhone.text = "Phone: ${item.phone}"
            b.tvCustomerAddress.text = item.address ?: "No address on file"

            b.root.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = list.size
    }
}
