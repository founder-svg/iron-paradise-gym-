package com.ironparadise.gymcrm.ui.sales

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ironparadise.gymcrm.R
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.data.db.entities.CustomerEntity
import com.ironparadise.gymcrm.data.db.entities.ProductEntity
import com.ironparadise.gymcrm.data.model.CartItem
import com.ironparadise.gymcrm.databinding.ActivityNewSaleBinding
import com.ironparadise.gymcrm.databinding.ItemCartProductBinding
import com.ironparadise.gymcrm.ui.invoices.InvoiceDetailActivity
import com.ironparadise.gymcrm.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NewSaleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewSaleBinding
    private val app by lazy { IronParadiseApp.instance }

    private var customerList: List<CustomerEntity> = emptyList()
    private var selectedCustomer: CustomerEntity? = null

    private var productList: List<ProductEntity> = emptyList()
    private var selectedProduct: ProductEntity? = null
    private var currentProductStock: Int = 0

    private val cartItems = mutableListOf<CartItem>()
    private val paymentMethods = listOf("CASH", "UPI", "CARD", "OTHER")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewSaleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupPaymentSpinner()
        setupRoleRestrictions()
        setupListeners()
        loadCustomers()
        loadProducts()
    }

    private fun setupRoleRestrictions() {
        binding.etSalePrice.isEnabled = true
        binding.etSalePrice.isFocusable = true
        binding.layoutDiscountRow.visibility = View.VISIBLE
        binding.etDiscount.isEnabled = true
    }

    private fun setupPaymentSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, paymentMethods)
        binding.spinnerPaymentMethod.adapter = adapter
    }

    private fun loadCustomers() {
        lifecycleScope.launch(Dispatchers.IO) {
            customerList = app.repository.allCustomers.first()
            withContext(Dispatchers.Main) {
                val customerNames = mutableListOf("Select Registered Customer (Optional)")
                customerNames.addAll(customerList.map { "${it.name} (${it.phone})" })

                val adapter = ArrayAdapter(this@NewSaleActivity, android.R.layout.simple_spinner_dropdown_item, customerNames)
                binding.spinnerCustomers.adapter = adapter

                binding.spinnerCustomers.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position > 0) {
                            selectedCustomer = customerList[position - 1]
                            selectedCustomer?.let {
                                binding.etCustomerName.setText(it.name)
                                binding.etCustomerPhone.setText(it.phone)
                            }
                        } else {
                            selectedCustomer = null
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
        }
    }

    private fun loadProducts() {
        lifecycleScope.launch(Dispatchers.IO) {
            productList = app.repository.allProducts.first()
            withContext(Dispatchers.Main) {
                if (productList.isNotEmpty()) {
                    val titles = productList.map { "${it.name} (${it.variant})" }
                    val adapter = ArrayAdapter(this@NewSaleActivity, android.R.layout.simple_spinner_dropdown_item, titles)
                    binding.spinnerSaleProducts.adapter = adapter

                    binding.spinnerSaleProducts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            selectedProduct = productList[position]
                            selectedProduct?.let { p ->
                                binding.etSalePrice.setText(p.sellingPrice.toString())
                                checkAvailableStockForProduct(p)
                            }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                }
            }
        }
    }

    private fun checkAvailableStockForProduct(p: ProductEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            currentProductStock = app.repository.getNetStock(p.id)
            withContext(Dispatchers.Main) {
                if (currentProductStock <= 0) {
                    binding.tvAvailableStockNotice.text = "SOLD OUT (0 units available)"
                    binding.tvAvailableStockNotice.setTextColor(ContextCompat.getColor(this@NewSaleActivity, R.color.accent_red))
                } else {
                    binding.tvAvailableStockNotice.text = "Available: $currentProductStock units"
                    binding.tvAvailableStockNotice.setTextColor(ContextCompat.getColor(this@NewSaleActivity, R.color.accent_green))
                }
                validateStockQuantity()
            }
        }
    }

    private fun setupListeners() {
        binding.etSaleQuantity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateStockQuantity()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSalePrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateStockQuantity()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etDiscount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateBillTotals()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnAddItemToCart.setOnClickListener {
            addItemToCart()
        }

        binding.btnConfirmSale.setOnClickListener {
            confirmSaleAndInvoice()
        }
    }

    private fun validateStockQuantity(): Boolean {
        val qty = binding.etSaleQuantity.text.toString().toIntOrNull() ?: 1
        val isAdmin = app.sessionManager.isAdmin
        val price = if (isAdmin) {
            binding.etSalePrice.text.toString().toDoubleOrNull() ?: (selectedProduct?.sellingPrice ?: 0.0)
        } else {
            selectedProduct?.sellingPrice ?: 0.0
        }
        val alreadyInCart = cartItems.find { it.product.id == selectedProduct?.id }?.quantity ?: 0
        val totalRequested = qty + alreadyInCart

        val itemTotal = qty * price
        binding.tvSaleCalcFormula.text = "Total = $qty × ${Formatters.formatCurrency(price)} = ${Formatters.formatCurrency(itemTotal)}"

        val remainingBalance = currentProductStock - totalRequested
        if (remainingBalance >= 0) {
            binding.tvSaleBalanceImpact.text = "Balance: Existing ($currentProductStock) - Sold ($totalRequested) = $remainingBalance units"
            binding.tvSaleBalanceImpact.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            binding.tvSaleBalanceImpact.text = "Balance: Deficit by ${-remainingBalance} units! Stock unavailable."
            binding.tvSaleBalanceImpact.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
        }

        if (currentProductStock <= 0) {
            binding.layoutStockError.visibility = View.VISIBLE
            binding.tvStockErrorText.text = "SOLD OUT! Item is out of stock and cannot be added."
            binding.btnAddItemToCart.isEnabled = false
            return false
        } else if (totalRequested > currentProductStock) {
            binding.layoutStockError.visibility = View.VISIBLE
            binding.tvStockErrorText.text = "Insufficient Stock! Available: $currentProductStock, Requested: $totalRequested"
            binding.btnAddItemToCart.isEnabled = false
            return false
        } else {
            binding.layoutStockError.visibility = View.GONE
            binding.btnAddItemToCart.isEnabled = true
            return true
        }
    }

    private fun addItemToCart() {
        val product = selectedProduct ?: return
        val qty = binding.etSaleQuantity.text.toString().toIntOrNull() ?: 1
        val isAdmin = app.sessionManager.isAdmin
        val price = if (isAdmin) {
            binding.etSalePrice.text.toString().toDoubleOrNull() ?: product.sellingPrice
        } else {
            product.sellingPrice
        }

        if (qty <= 0) {
            Toast.makeText(this, "Quantity must be at least 1", Toast.LENGTH_SHORT).show()
            return
        }

        if (!validateStockQuantity()) {
            Toast.makeText(this, "Cannot add more than available stock ($currentProductStock)", Toast.LENGTH_SHORT).show()
            return
        }

        val existing = cartItems.find { it.product.id == product.id }
        if (existing != null) {
            existing.quantity += qty
            existing.unitPrice = price
        } else {
            cartItems.add(CartItem(product, currentProductStock, qty, price))
        }

        renderCartView()
        updateBillTotals()
        Toast.makeText(this, "Added ${product.name} to bill", Toast.LENGTH_SHORT).show()
        binding.etSaleQuantity.setText("1")
    }

    private fun renderCartView() {
        binding.layoutCartItemsContainer.removeAllViews()

        if (cartItems.isEmpty()) {
            binding.tvEmptyCart.visibility = View.VISIBLE
        } else {
            binding.tvEmptyCart.visibility = View.GONE
            val inflater = LayoutInflater.from(this)

            for (item in cartItems) {
                val itemBinding = ItemCartProductBinding.inflate(inflater, binding.layoutCartItemsContainer, false)
                itemBinding.tvCartProductName.text = item.product.name
                itemBinding.tvCartProductMeta.text = "${item.quantity} × ${Formatters.formatCurrency(item.unitPrice)}"
                itemBinding.tvCartItemTotal.text = Formatters.formatCurrency(item.total)

                if (!item.product.imageUri.isNullOrBlank()) {
                    itemBinding.ivCartProductThumb.imageTintList = null
                    com.ironparadise.gymcrm.util.ImageHelper.loadImage(itemBinding.ivCartProductThumb, item.product.imageUri)
                } else {
                    itemBinding.ivCartProductThumb.setImageResource(R.drawable.ic_supplement_jar)
                    itemBinding.ivCartProductThumb.setColorFilter(ContextCompat.getColor(this, R.color.accent_gold))
                    itemBinding.ivCartProductThumb.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                }

                itemBinding.btnRemoveCartItem.setOnClickListener {
                    cartItems.remove(item)
                    renderCartView()
                    updateBillTotals()
                    validateStockQuantity()
                }

                binding.layoutCartItemsContainer.addView(itemBinding.root)
            }
        }
    }

    private fun updateBillTotals() {
        val subtotal = cartItems.sumOf { it.total }
        val discount = binding.etDiscount.text.toString().toDoubleOrNull() ?: 0.0
        val grandTotal = (subtotal - discount).coerceAtLeast(0.0)

        binding.tvSubtotal.text = Formatters.formatCurrency(subtotal)
        binding.tvGrandTotal.text = Formatters.formatCurrency(grandTotal)
    }

    private fun confirmSaleAndInvoice() {
        if (cartItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one supplement to the sale", Toast.LENGTH_SHORT).show()
            return
        }

        val customerName = binding.etCustomerName.text.toString().trim().ifBlank { "Walk-in Customer" }
        val customerPhone = binding.etCustomerPhone.text.toString().trim().ifBlank { "-" }
        val isAdmin = app.sessionManager.isAdmin
        val discount = if (isAdmin) {
            binding.etDiscount.text.toString().toDoubleOrNull() ?: 0.0
        } else {
            0.0
        }
        val paymentMethod = binding.spinnerPaymentMethod.selectedItem.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // If customer is entered but not in database, save for CRM
                var customerId = selectedCustomer?.id
                if (customerId == null && customerPhone.length >= 7) {
                    customerId = app.repository.addCustomer(
                        CustomerEntity(name = customerName, phone = customerPhone)
                    )
                }

                val sale = app.repository.recordSale(
                    customerId = customerId,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    items = cartItems,
                    discount = discount,
                    paymentMethod = paymentMethod,
                    user = app.sessionManager.userName
                )

                // Background cloud sync push to update server and all other 9 devices
                val itemsCopy = ArrayList(cartItems)
                lifecycleScope.launch(Dispatchers.IO) {
                    com.ironparadise.gymcrm.data.network.CloudSyncManager.pushSale(applicationContext, sale, itemsCopy)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NewSaleActivity, "Sale Confirmed! Invoice: ${sale.invoiceNumber}", Toast.LENGTH_LONG).show()

                    val intent = Intent(this@NewSaleActivity, InvoiceDetailActivity::class.java).apply {
                        putExtra("EXTRA_SALE_ID", sale.id)
                    }
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@NewSaleActivity)
                        .setTitle("Sale Failed")
                        .setMessage(e.message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
}
