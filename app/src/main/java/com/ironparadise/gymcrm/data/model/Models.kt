package com.ironparadise.gymcrm.data.model

import com.ironparadise.gymcrm.data.db.entities.CustomerEntity
import com.ironparadise.gymcrm.data.db.entities.ProductEntity
import com.ironparadise.gymcrm.data.db.entities.SaleEntity
import com.ironparadise.gymcrm.data.db.entities.SaleItemEntity
import com.ironparadise.gymcrm.data.db.entities.StockTransactionEntity

data class ProductWithStock(
    val product: ProductEntity,
    val currentStock: Int
) {
    val isOutOfStock: Boolean get() = currentStock <= 0
    val isLowStock: Boolean get() = currentStock > 0 && currentStock <= product.minimumStock
    val stockStatusText: String
        get() = when {
            isOutOfStock -> "OUT OF STOCK"
            isLowStock -> "LOW STOCK"
            else -> "IN STOCK"
        }
}

data class DashboardStats(
    val totalProducts: Int = 0,
    val totalStockUnits: Int = 0,
    val todaySalesAmount: Double = 0.0,
    val todayItemsSold: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0
)

data class SaleWithDetails(
    val sale: SaleEntity,
    val items: List<SaleItemEntity>,
    val customer: CustomerEntity?
)

data class ProductLedgerEntry(
    val transaction: StockTransactionEntity,
    val dateString: String,
    val stockIn: Int,
    val stockOut: Int,
    val balance: Int
)

data class CartItem(
    val product: ProductEntity,
    val availableStock: Int,
    var quantity: Int = 1,
    var unitPrice: Double = product.sellingPrice
) {
    val total: Double get() = quantity * unitPrice
}
