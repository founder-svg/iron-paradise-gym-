package com.ironparadise.gymcrm.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.databinding.ActivityReportsBinding
import com.ironparadise.gymcrm.databinding.ItemReportProductBinding
import com.ironparadise.gymcrm.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ReportsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportsBinding
    private val app by lazy { IronParadiseApp.instance }

    private var selectedDays: Int = 0 // 0 = Today, 7 = 7 days, 30 = Month, -1 = All

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)



        binding.btnBack.setOnClickListener { finish() }

        setupPeriodChips()
        loadReportData()
    }

    private fun setupPeriodChips() {
        binding.chipGroupPeriod.setOnCheckedChangeListener { _, checkedId ->
            selectedDays = when (checkedId) {
                binding.chipToday.id -> 0
                binding.chip7Days.id -> 7
                binding.chipThisMonth.id -> 30
                else -> -1
            }
            loadReportData()
        }
    }

    private fun loadReportData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            val endDate = cal.timeInMillis

            val startDate = if (selectedDays == 0) {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            } else if (selectedDays > 0) {
                cal.add(Calendar.DAY_OF_YEAR, -selectedDays)
                cal.timeInMillis
            } else {
                0L // All time
            }

            val sales = app.database.saleDao().getSalesBetween(startDate, endDate)
            var grossSales = 0.0
            var estCost = 0.0
            var totalUnitsSold = 0

            data class ProductAggregate(
                val productName: String,
                var qty: Int = 0,
                var revenue: Double = 0.0,
                var cost: Double = 0.0
            ) {
                val profit: Double get() = revenue - cost
            }

            val productMap = mutableMapOf<String, ProductAggregate>()

            for (sale in sales) {
                grossSales += sale.grandTotal
                val items = app.database.saleDao().getItemsForSale(sale.id)
                for (item in items) {
                    totalUnitsSold += item.quantity
                    estCost += item.purchasePrice * item.quantity

                    val agg = productMap.getOrPut(item.productId) {
                        ProductAggregate(item.productName)
                    }
                    agg.qty += item.quantity
                    agg.revenue += item.total
                    agg.cost += item.purchasePrice * item.quantity
                }
            }

            val grossProfit = grossSales - estCost

            withContext(Dispatchers.Main) {
                binding.tvReportGrossSales.text = Formatters.formatCurrency(grossSales)
                binding.tvReportGrossProfit.text = Formatters.formatCurrency(grossProfit)
                binding.tvReportCostValue.text = Formatters.formatCurrency(estCost)
                binding.tvReportUnitsSold.text = "$totalUnitsSold Units"

                // Render product breakdown table
                binding.layoutReportItems.removeAllViews()
                if (productMap.isEmpty()) {
                    binding.tvEmptyReports.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyReports.visibility = View.GONE
                    val inflater = LayoutInflater.from(this@ReportsActivity)

                    for ((_, agg) in productMap) {
                        val row = ItemReportProductBinding.inflate(inflater, binding.layoutReportItems, false)
                        row.tvReportProductName.text = agg.productName
                        row.tvReportQtySold.text = "${agg.qty}"
                        row.tvReportSalesVal.text = Formatters.formatCurrency(agg.revenue)
                        row.tvReportProfitVal.text = "+ ${Formatters.formatCurrency(agg.profit)}"
                        binding.layoutReportItems.addView(row.root)
                    }
                }
            }
        }
    }
}
