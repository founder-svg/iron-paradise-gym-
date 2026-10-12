package com.ironparadise.gymcrm.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.R
import com.ironparadise.gymcrm.data.model.UserRole
import com.ironparadise.gymcrm.databinding.ActivityMainBinding
import com.ironparadise.gymcrm.ui.auth.LoginActivity
import com.ironparadise.gymcrm.ui.dashboard.DashboardFragment
import com.ironparadise.gymcrm.ui.invoices.InvoicesFragment
import com.ironparadise.gymcrm.ui.more.MoreFragment
import com.ironparadise.gymcrm.ui.sales.SalesFragment
import com.ironparadise.gymcrm.ui.stock.StockFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val app by lazy { IronParadiseApp.instance }

    private val dashboardFragment = DashboardFragment()
    private val stockFragment = StockFragment()
    private val salesFragment = SalesFragment()
    private val invoicesFragment = InvoicesFragment()
    private val moreFragment = MoreFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Session check
        if (!app.sessionManager.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        setupBottomNavigation()

        // Default screen is Dashboard
        if (savedInstanceState == null) {
            loadFragment(dashboardFragment)
        }
    }

    private fun setupHeader() {
        val role = app.sessionManager.userRole
        binding.tvRoleBadge.text = role.name

        if (role == UserRole.ADMIN) {
            binding.tvRoleBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_gold))
            binding.tvRoleBadge.setBackgroundResource(R.drawable.bg_badge_low_stock)
        } else {
            binding.tvRoleBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.tvRoleBadge.setBackgroundResource(R.drawable.bg_badge_in_stock)
        }

        binding.btnMenu.setOnClickListener {
            binding.bottomNav.selectedItemId = R.id.menu_more
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Lock CRM")
                .setMessage("Return to PIN login?")
                .setPositiveButton("Lock") { _, _ ->
                    app.sessionManager.logout()
                    val intent = Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.menu_dashboard -> dashboardFragment
                R.id.menu_stock -> stockFragment
                R.id.menu_sales -> salesFragment
                R.id.menu_invoices -> invoicesFragment
                R.id.menu_more -> moreFragment
                else -> dashboardFragment
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
