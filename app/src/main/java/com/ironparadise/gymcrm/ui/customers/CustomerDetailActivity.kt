package com.ironparadise.gymcrm.ui.customers

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ironparadise.gymcrm.IronParadiseApp
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

        val customerId = intent.getLongExtra("EXTRA_CUSTOMER_ID", -1L)
        if (customerId != -1L) {
            loadCustomer(customerId)
        } else {
            Toast.makeText(this, "Customer not found", Toast.LENGTH_SHORT).show()
            finish()
        }
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
            val b = holder.binding

            b.tvInvoiceNumber.text = sale.invoiceNumber
            b.tvCustomerName.text = sale.customerName
            b.tvInvoiceDate.text = "${Formatters.formatDate(sale.date)} • ${sale.paymentMethod}"
            b.tvGrandTotal.text = Formatters.formatCurrency(sale.grandTotal)

            b.root.setOnClickListener { onItemClick(sale) }
        }

        override fun getItemCount(): Int = list.size
    }
}
