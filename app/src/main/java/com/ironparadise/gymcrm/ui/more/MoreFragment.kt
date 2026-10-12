package com.ironparadise.gymcrm.ui.more

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.data.model.UserRole
import com.ironparadise.gymcrm.databinding.FragmentMoreBinding
import com.ironparadise.gymcrm.ui.auth.LoginActivity
import com.ironparadise.gymcrm.ui.customers.CustomersActivity
import com.ironparadise.gymcrm.ui.products.ProductsListActivity
import com.ironparadise.gymcrm.ui.reports.ReportsActivity
import com.ironparadise.gymcrm.ui.stock.StockAdjustmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class MoreFragment : Fragment() {

    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!

    private val app by lazy { IronParadiseApp.instance }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUserProfile()
        setupMenuClickListeners()
    }

    private fun setupUserProfile() {
        binding.tvCurrentUserName.text = app.sessionManager.userName
        val isAdmin = app.sessionManager.isAdmin
        val role = app.sessionManager.userRole
        binding.tvCurrentUserRole.text = if (isAdmin) {
            "ROLE: ADMIN (Full Control & Financials)"
        } else {
            "ROLE: STAFF (Front Desk & Billing)"
        }

        binding.btnSwitchRole.setOnClickListener {
            promptLogout()
        }
        binding.btnLogoutMenu.setOnClickListener {
            promptLogout()
        }

        // Role-based visibility
        if (!isAdmin) {
            binding.cardStockAdjustment.visibility = View.GONE
            binding.cardReports.visibility = View.GONE
            binding.cardStaffManagement.visibility = View.GONE
        } else {
            binding.cardStockAdjustment.visibility = View.VISIBLE
            binding.cardReports.visibility = View.VISIBLE
            binding.cardStaffManagement.visibility = View.VISIBLE

            viewLifecycleOwner.lifecycleScope.launch {
                app.repository.pendingStaffCount.collect { count ->
                    if (_binding != null) {
                        if (count > 0) {
                            binding.tvPendingStaffBadge.visibility = View.VISIBLE
                            binding.tvPendingStaffBadge.text = "$count PENDING"
                        } else {
                            binding.tvPendingStaffBadge.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun setupMenuClickListeners() {
        binding.cardProductMaster.setOnClickListener {
            startActivity(Intent(requireContext(), ProductsListActivity::class.java))
        }

        binding.cardCustomers.setOnClickListener {
            startActivity(Intent(requireContext(), CustomersActivity::class.java))
        }

        binding.cardStockAdjustment.setOnClickListener {
            if (app.sessionManager.isAdmin) {
                startActivity(Intent(requireContext(), StockAdjustmentActivity::class.java))
            } else {
                Toast.makeText(requireContext(), "Admin permission required for stock adjustments", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardReports.setOnClickListener {
            if (app.sessionManager.isAdmin) {
                startActivity(Intent(requireContext(), ReportsActivity::class.java))
            } else {
                Toast.makeText(requireContext(), "Admin permission required to view profit reports", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardStaffManagement.setOnClickListener {
            startActivity(Intent(requireContext(), StaffManagementActivity::class.java))
        }

        binding.cardBackup.setOnClickListener {
            exportBackup()
        }
    }

    private fun exportBackup() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val products = app.repository.allProducts.first()
                val transactions = app.repository.allTransactions.first()
                val customers = app.repository.allCustomers.first()
                val sales = app.repository.allSales.first()

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date())
                val filename = "IRON_PARADISE_BACKUP_$timestamp.json"
                val dir = requireContext().getExternalFilesDir(null) ?: requireContext().filesDir
                val file = File(dir, filename)

                val writer = FileWriter(file)
                writer.write("{\n")
                writer.write("  \"app\": \"Iron Paradise Gym CRM\",\n")
                writer.write("  \"exportedAt\": \"$timestamp\",\n")
                writer.write("  \"productsCount\": ${products.size},\n")
                writer.write("  \"transactionsCount\": ${transactions.size},\n")
                writer.write("  \"customersCount\": ${customers.size},\n")
                writer.write("  \"salesCount\": ${sales.size}\n")
                writer.write("}\n")
                writer.flush()
                writer.close()

                withContext(Dispatchers.Main) {
                    val uri: Uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Iron Paradise Gym CRM Backup")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Save or Share Database Backup"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Backup error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun promptLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle("Switch User / Logout")
            .setMessage("Do you want to lock the CRM and return to PIN login?")
            .setPositiveButton("Logout") { _, _ ->
                app.sessionManager.logout()
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
