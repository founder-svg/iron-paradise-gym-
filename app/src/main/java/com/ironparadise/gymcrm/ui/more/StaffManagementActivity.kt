package com.ironparadise.gymcrm.ui.more

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
import com.ironparadise.gymcrm.data.db.entities.UserEntity
import com.ironparadise.gymcrm.databinding.ActivityStaffManagementBinding
import com.ironparadise.gymcrm.databinding.ItemStaffMemberBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StaffManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffManagementBinding
    private val app by lazy { IronParadiseApp.instance }
    private lateinit var adapter: StaffAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStaffManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)



        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        setupAddStaffDialog()
        observeStaffList()
    }

    private fun setupRecyclerView() {
        adapter = StaffAdapter(
            onApprove = { staff ->
                lifecycleScope.launch(Dispatchers.IO) {
                    app.repository.updateStaffStatus(staff.id, "APPROVED")
                    com.ironparadise.gymcrm.data.network.CloudSyncManager.updateRemoteStaffStatus(applicationContext, staff.email, "APPROVED")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@StaffManagementActivity, "Staff approved: ${staff.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onReject = { staff ->
                lifecycleScope.launch(Dispatchers.IO) {
                    app.repository.updateStaffStatus(staff.id, "REJECTED")
                    com.ironparadise.gymcrm.data.network.CloudSyncManager.updateRemoteStaffStatus(applicationContext, staff.email, "REJECTED")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@StaffManagementActivity, "Staff rejected: ${staff.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = { staff ->
                AlertDialog.Builder(this)
                    .setTitle("Remove Staff Member")
                    .setMessage("Are you sure you want to remove ${staff.name} (${staff.email})?")
                    .setPositiveButton("Remove") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            app.repository.deleteStaff(staff.id)
                            com.ironparadise.gymcrm.data.network.CloudSyncManager.deleteRemoteStaff(applicationContext, staff.email)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@StaffManagementActivity, "Staff removed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvStaffList.apply {
            layoutManager = LinearLayoutManager(this@StaffManagementActivity)
            adapter = this@StaffManagementActivity.adapter
        }
    }

    private fun observeStaffList() {
        lifecycleScope.launch {
            app.repository.allStaff.collectLatest { list ->
                adapter.submitList(list)
                binding.tvEmptyStaff.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setupAddStaffDialog() {
        binding.btnAddStaff.setOnClickListener {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 20, 50, 10)
            }
            val etName = EditText(this).apply { hint = "Full Name" }
            val etEmail = EditText(this).apply { hint = "Email Address"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
            val etPassword = EditText(this).apply { hint = "Password (or 4-digit PIN)"; inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
            layout.addView(etName)
            layout.addView(etEmail)
            layout.addView(etPassword)

            AlertDialog.Builder(this)
                .setTitle("Add Staff Member")
                .setView(layout)
                .setPositiveButton("Add & Pre-Approve") { _, _ ->
                    val name = etName.text.toString().trim()
                    val email = etEmail.text.toString().trim()
                    val pass = etPassword.text.toString().trim()

                    if (name.isBlank() || email.isBlank() || pass.isBlank()) {
                        Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    lifecycleScope.launch(Dispatchers.IO) {
                        val newUser = UserEntity(
                            email = email,
                            name = name,
                            password = pass,
                            pin = if (pass.length == 4 && pass.all { it.isDigit() }) pass else "",
                            role = "STAFF",
                            status = "APPROVED",
                            authProvider = "EMAIL"
                        )
                        app.repository.registerUser(newUser)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@StaffManagementActivity, "Staff pre-approved successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    inner class StaffAdapter(
        private val onApprove: (UserEntity) -> Unit,
        private val onReject: (UserEntity) -> Unit,
        private val onDelete: (UserEntity) -> Unit
    ) : RecyclerView.Adapter<StaffAdapter.ViewHolder>() {

        private var items: List<UserEntity> = emptyList()

        fun submitList(newList: List<UserEntity>) {
            items = newList
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemStaffMemberBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemStaffMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val staff = items[position]
            val b = holder.binding
            val context = b.root.context

            b.tvStaffName.text = staff.name
            b.tvStaffEmail.text = "${staff.email} • ${staff.authProvider}"

            when (staff.status) {
                "APPROVED" -> {
                    b.tvStaffStatusBadge.text = "APPROVED"
                    b.tvStaffStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_in_stock_text))
                    b.tvStaffStatusBadge.setBackgroundResource(R.drawable.bg_badge_in_stock)

                    b.btnApproveStaff.visibility = View.GONE
                    b.btnRejectStaff.text = "Remove"
                    b.btnRejectStaff.setOnClickListener { onDelete(staff) }
                }
                "REJECTED" -> {
                    b.tvStaffStatusBadge.text = "REJECTED"
                    b.tvStaffStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_out_of_stock_text))
                    b.tvStaffStatusBadge.setBackgroundResource(R.drawable.bg_badge_out_of_stock)

                    b.btnApproveStaff.visibility = View.VISIBLE
                    b.btnApproveStaff.text = "Re-Approve"
                    b.btnApproveStaff.setOnClickListener { onApprove(staff) }
                    b.btnRejectStaff.text = "Delete"
                    b.btnRejectStaff.setOnClickListener { onDelete(staff) }
                }
                else -> { // PENDING
                    b.tvStaffStatusBadge.text = "PENDING APPROVAL"
                    b.tvStaffStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_low_stock_text))
                    b.tvStaffStatusBadge.setBackgroundResource(R.drawable.bg_badge_low_stock)

                    b.btnApproveStaff.visibility = View.VISIBLE
                    b.btnApproveStaff.text = "Approve"
                    b.btnApproveStaff.setOnClickListener { onApprove(staff) }
                    b.btnRejectStaff.text = "Reject"
                    b.btnRejectStaff.setOnClickListener { onReject(staff) }
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
