package com.ironparadise.gymcrm.ui.invoices

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.R
import com.ironparadise.gymcrm.data.model.SaleWithDetails
import com.ironparadise.gymcrm.databinding.ActivityInvoiceDetailBinding
import com.ironparadise.gymcrm.util.Formatters
import com.ironparadise.gymcrm.util.PdfInvoiceGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class InvoiceDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInvoiceDetailBinding
    private val app by lazy { IronParadiseApp.instance }

    private var currentSaleDetails: SaleWithDetails? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInvoiceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val saleId = intent.getLongExtra("EXTRA_SALE_ID", -1L)
        if (saleId != -1L) {
            loadInvoice(saleId)
        } else {
            Toast.makeText(this, "Invoice not found", Toast.LENGTH_SHORT).show()
            finish()
        }

        setupButtons()
    }

    private fun loadInvoice(saleId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val details = app.repository.getSaleDetails(saleId)
            withContext(Dispatchers.Main) {
                if (details != null) {
                    currentSaleDetails = details
                    populateInvoiceView(details)
                } else {
                    Toast.makeText(this@InvoiceDetailActivity, "Unable to load invoice", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun populateInvoiceView(details: SaleWithDetails) {
        val sale = details.sale

        binding.tvCustomerName.text = sale.customerName
        binding.tvCustomerPhone.text = "Phone: ${sale.customerPhone}"
        binding.tvInvoiceNum.text = sale.invoiceNumber
        binding.tvInvoiceDate.text = Formatters.formatDate(sale.date)
        binding.tvPaymentMethod.text = "Payment: ${sale.paymentMethod}"

        binding.layoutInvoiceItems.removeAllViews()
        val inflater = LayoutInflater.from(this)

        details.items.forEachIndexed { index, item ->
            val rowBinding = com.ironparadise.gymcrm.databinding.ItemInvoiceTableRowBinding.inflate(inflater, binding.layoutInvoiceItems, false)
            rowBinding.tvItemIndex.text = "${index + 1}"
            rowBinding.tvItemProduct.text = item.productName
            rowBinding.tvItemQty.text = "${item.quantity}"
            rowBinding.tvItemPrice.text = Formatters.formatCurrency(item.unitPrice)
            rowBinding.tvItemTotal.text = Formatters.formatCurrency(item.total)
            binding.layoutInvoiceItems.addView(rowBinding.root)
        }

        binding.tvSubtotal.text = Formatters.formatCurrency(sale.subtotal)
        if (sale.discount > 0) {
            binding.layoutDiscountRow.visibility = View.VISIBLE
            binding.tvDiscount.text = "- ${Formatters.formatCurrency(sale.discount)}"
        } else {
            binding.layoutDiscountRow.visibility = View.GONE
        }
        binding.tvGrandTotal.text = Formatters.formatCurrency(sale.grandTotal)

        binding.tvBilledBy.text = "Billed By: ${sale.createdByUser}"

        val isAdmin = app.sessionManager.isAdmin
        when (sale.status) {
            "CANCELLED" -> {
                binding.tvInvoiceStatus.text = "CANCELLED & REVERSED"
                binding.tvInvoiceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
                binding.tvInvoiceStatus.setBackgroundResource(R.drawable.bg_badge_out_of_stock)
                binding.btnApproveSale.visibility = View.GONE
                binding.btnCancelSale.visibility = if (isAdmin) View.VISIBLE else View.GONE
                binding.btnCancelSale.isEnabled = false
                binding.btnCancelSale.text = "SALE CANCELLED & REVERSED"
            }
            "PENDING_APPROVAL" -> {
                binding.tvInvoiceStatus.text = "PENDING ADMIN APPROVAL"
                binding.tvInvoiceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_gold))
                binding.tvInvoiceStatus.setBackgroundResource(R.drawable.bg_badge_low_stock)
                binding.btnApproveSale.visibility = if (isAdmin) View.VISIBLE else View.GONE
                binding.btnCancelSale.visibility = if (isAdmin) View.VISIBLE else View.GONE
                binding.btnCancelSale.isEnabled = true
                binding.btnCancelSale.text = "Reject & Void Sale (Admin)"
            }
            else -> { // "APPROVED" or "COMPLETED"
                binding.tvInvoiceStatus.text = "APPROVED BY ADMIN"
                binding.tvInvoiceStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                binding.tvInvoiceStatus.setBackgroundResource(R.drawable.bg_badge_in_stock)
                binding.btnApproveSale.visibility = View.GONE
                binding.btnCancelSale.visibility = if (isAdmin) View.VISIBLE else View.GONE
                binding.btnCancelSale.isEnabled = true
                binding.btnCancelSale.text = "Cancel & Reverse Sale (Restore Stock)"
            }
        }
    }

    private fun setupButtons() {
        binding.btnActionShare.setOnClickListener { shareInvoicePdf() }
        binding.btnActionDownload.setOnClickListener { downloadInvoicePdf() }
        binding.btnActionPrint.setOnClickListener { shareInvoicePdf() }
        binding.btnActionWhatsApp.setOnClickListener { shareToWhatsApp() }
        binding.btnActionEmail.setOnClickListener { shareToEmail() }

        binding.btnApproveSale.setOnClickListener {
            if (!app.sessionManager.isAdmin) {
                Toast.makeText(this, "Access Denied: Only Admin can approve sales", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val details = currentSaleDetails ?: return@setOnClickListener
            lifecycleScope.launch(Dispatchers.IO) {
                val success = app.repository.approveSale(details.sale.id)
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@InvoiceDetailActivity, "Sale Verified & Approved by Admin!", Toast.LENGTH_SHORT).show()
                        loadInvoice(details.sale.id)
                    } else {
                        Toast.makeText(this@InvoiceDetailActivity, "Failed to approve sale", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.btnCancelSale.setOnClickListener {
            if (!app.sessionManager.isAdmin) {
                Toast.makeText(this, "Access Denied: Only Admin can cancel or reverse sales", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val details = currentSaleDetails ?: return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Reverse Sale & Restore Stock?")
                .setMessage("Are you sure you want to cancel Invoice ${details.sale.invoiceNumber}? All sold items will be immediately returned to the stock ledger.")
                .setPositiveButton("Yes, Reverse Sale") { _, _ ->
                    reverseSale(details.sale.id)
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    private fun downloadInvoicePdf() {
        val details = currentSaleDetails ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = PdfInvoiceGenerator.generateInvoicePdf(this@InvoiceDetailActivity, details)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@InvoiceDetailActivity, "Invoice PDF saved at: ${file.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@InvoiceDetailActivity, "PDF save error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareToWhatsApp() {
        val details = currentSaleDetails ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = PdfInvoiceGenerator.generateInvoicePdf(this@InvoiceDetailActivity, details)
                withContext(Dispatchers.Main) {
                    val uri: Uri = FileProvider.getUriForFile(
                        this@InvoiceDetailActivity,
                        "${applicationContext.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        setPackage("com.whatsapp")
                        putExtra(Intent.EXTRA_TEXT, "Hello ${details.sale.customerName}, here is your invoice #${details.sale.invoiceNumber} from IRON Paradise GYM. Grand Total: ${Formatters.formatCurrency(details.sale.grandTotal)}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        // WhatsApp not installed, fallback to chooser
                        sharePdfFile(file, details.sale.invoiceNumber)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@InvoiceDetailActivity, "WhatsApp share error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareToEmail() {
        val details = currentSaleDetails ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = PdfInvoiceGenerator.generateInvoicePdf(this@InvoiceDetailActivity, details)
                withContext(Dispatchers.Main) {
                    val uri: Uri = FileProvider.getUriForFile(
                        this@InvoiceDetailActivity,
                        "${applicationContext.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "IRON Paradise GYM Invoice #${details.sale.invoiceNumber}")
                        putExtra(Intent.EXTRA_TEXT, "Hi ${details.sale.customerName},\n\nPlease find attached your invoice #${details.sale.invoiceNumber} from IRON Paradise GYM.\nTotal: ${Formatters.formatCurrency(details.sale.grandTotal)}\n\nThank you!")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Send Invoice via Email"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@InvoiceDetailActivity, "Email error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun reverseSale(saleId: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val success = app.repository.cancelSale(saleId, "Cancelled by Admin", app.sessionManager.userName)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@InvoiceDetailActivity, "Sale Cancelled. Inventory Restored!", Toast.LENGTH_LONG).show()
                    loadInvoice(saleId)
                } else {
                    Toast.makeText(this@InvoiceDetailActivity, "Failed to cancel sale", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareInvoicePdf() {
        val details = currentSaleDetails ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = PdfInvoiceGenerator.generateInvoicePdf(this@InvoiceDetailActivity, details)
                withContext(Dispatchers.Main) {
                    sharePdfFile(file, details.sale.invoiceNumber)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@InvoiceDetailActivity, "PDF Generation error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sharePdfFile(file: File, invoiceNo: String) {
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Iron Paradise Gym Invoice $invoiceNo")
            putExtra(Intent.EXTRA_TEXT, "Here is your invoice $invoiceNo from Iron Paradise Gym. Thank you for your purchase!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Share Invoice via WhatsApp / Email"))
    }
}
