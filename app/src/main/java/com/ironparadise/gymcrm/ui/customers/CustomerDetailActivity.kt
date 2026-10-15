package com.ironparadise.gymcrm.ui.customers

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.R
import com.ironparadise.gymcrm.data.db.entities.CustomerEntity
import com.ironparadise.gymcrm.data.db.entities.SaleEntity
import com.ironparadise.gymcrm.databinding.ActivityCustomerDetailBinding
import com.ironparadise.gymcrm.databinding.ItemInvoiceBinding
import com.ironparadise.gymcrm.ui.invoices.InvoiceDetailActivity
import com.ironparadise.gymcrm.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerDetailBinding
    private val app by lazy { IronParadiseApp.instance }
    private var customer: CustomerEntity? = null
    private var customerSales: List<SaleEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnEditCustomer.visibility = View.VISIBLE
        binding.btnDeleteCustomer.visibility = View.VISIBLE
        setupAdminCustomerActions()

        val customerId = intent.getLongExtra("EXTRA_CUSTOMER_ID", -1L)
        if (customerId != -1L) {
            loadCustomer(customerId)
        } else {
            Toast.makeText(this, "Customer not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupAdminCustomerActions() {
        binding.btnEditCustomer.setOnClickListener {
            if (!app.sessionManager.isAdmin) {
                Toast.makeText(this, "Access Denied: Only Admin can edit customer data", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val target = customer ?: return@setOnClickListener
            showEditCustomerDialog(target)
        }

        binding.btnDeleteCustomer.setOnClickListener {
            if (!app.sessionManager.isAdmin) {
                Toast.makeText(this, "Access Denied: Only Admin can remove customers", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val target = customer ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Delete Customer Record")
                .setMessage("Are you sure you want to remove ${target.name} (${target.phone}) from customer records?")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        app.repository.deleteCustomer(target)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@CustomerDetailActivity, "Customer record deleted by Admin", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showEditCustomerDialog(target: CustomerEntity) {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val etName = EditText(this).apply {
            hint = "Customer Name"
            setText(target.name)
        }
        val etPhone = EditText(this).apply {
            hint = "Mobile Phone Number"
            setText(target.phone)
        }
        val etEmail = EditText(this).apply {
            hint = "Email Address (Optional)"
            setText(target.email ?: "")
        }

        view.addView(etName)
        view.addView(etPhone)
        view.addView(etEmail)

        AlertDialog.Builder(this)
            .setTitle("Edit Customer (Admin Only)")
            .setView(view)
            .setPositiveButton("Save Changes") { _, _ ->
                val newName = etName.text.toString().trim()
                val newPhone = etPhone.text.toString().trim()
                val newEmail = etEmail.text.toString().trim().ifBlank { null }

                if (newName.isBlank() || newPhone.isBlank()) {
                    Toast.makeText(this, "Name and Phone cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    val updated = target.copy(name = newName, phone = newPhone, email = newEmail)
                    app.repository.updateCustomer(updated)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CustomerDetailActivity, "Customer updated successfully", Toast.LENGTH_SHORT).show()
                        loadCustomer(target.id)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadCustomer(customerId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val allCust = app.repository.allCustomers.first()
            val target = allCust.find { it.id == customerId }
            val allSales = app.repository.allSales.first()

            withContext(Dispatchers.Main) {
                if (target != null) {
                    customer = target
                    customerSales = allSales.filter {
                        it.customerPhone.trim() == target.phone.trim() ||
                        it.customerName.equals(target.name, ignoreCase = true)
                    }
                    bindCustomerData(target, customerSales)
                } else {
                    Toast.makeText(this@CustomerDetailActivity, "Unable to load customer", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun bindCustomerData(cust: CustomerEntity, sales: List<SaleEntity>) {
        binding.tvCustomerName.text = cust.name
        binding.tvCustomerPhone.text = "+91 ${cust.phone}"

        val totalOrders = sales.size
        val totalSpent = sales.sumOf { it.grandTotal }
        val lastDate = if (sales.isNotEmpty()) {
            Formatters.formatDate(sales.maxOf { it.date })
        } else {
            "No purchases"
        }

        binding.tvTotalOrders.text = "$totalOrders"
        binding.tvTotalSpent.text = Formatters.formatCurrency(totalSpent)
        binding.tvLastPurchase.text = lastDate

        val adapter = CustomerInvoiceAdapter(sales) { sale ->
            val intent = Intent(this, InvoiceDetailActivity::class.java).apply {
                putExtra("EXTRA_SALE_ID", sale.id)
            }
            startActivity(intent)
        }
        binding.rvCustomerInvoices.layoutManager = LinearLayoutManager(this)
        binding.rvCustomerInvoices.adapter = adapter
    }

    inner class CustomerInvoiceAdapter(
        private val list: List<SaleEntity>,
        private val onItemClick: (SaleEntity) -> Unit
    ) : RecyclerView.Adapter<CustomerInvoiceAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemInvoiceBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemInvoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sale = list[position]
            holder.binding.tvInvoiceNumber.text = sale.invoiceNumber
            holder.binding.tvCustomerName.text = sale.customerName
            holder.binding.tvInvoiceDate.text = "${Formatters.formatDate(sale.date)} • ${sale.paymentMethod}"
            holder.binding.tvGrandTotal.text = Formatters.formatCurrency(sale.grandTotal)

            if (sale.status == "CANCELLED") {
                holder.binding.tvInvoiceStatus.text = "CANCELLED"
                holder.binding.tvInvoiceStatus.setTextColor(ContextCompat.getColor(this@CustomerDetailActivity, R.color.accent_red))
                holder.binding.tvInvoiceStatus.setBackgroundResource(R.drawable.bg_badge_out_of_stock)
            } else if (sale.status == "PENDING_APPROVAL") {
                holder.binding.tvInvoiceStatus.text = "PENDING APPROVAL"
                holder.binding.tvInvoiceStatus.setTextColor(ContextCompat.getColor(this@CustomerDetailActivity, R.color.accent_gold))
                holder.binding.tvInvoiceStatus.setBackgroundResource(R.drawable.bg_badge_low_stock)
            } else {
                holder.binding.tvInvoiceStatus.text = "PAID"
                holder.binding.tvInvoiceStatus.setTextColor(ContextCompat.getColor(this@CustomerDetailActivity, R.color.accent_green))
                holder.binding.tvInvoiceStatus.setBackgroundResource(R.drawable.bg_badge_in_stock)
            }

            holder.itemView.setOnClickListener { onItemClick(sale) }
        }

        override fun getItemCount() = list.size
    }
}
