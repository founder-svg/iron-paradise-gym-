package com.ironparadise.gymcrm.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_transactions",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["productId"]), Index(value = ["date"])]
)
data class StockTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: String,
    val productName: String,
    val transactionType: String, // STOCK_IN, SALE, SALE_RETURN, ADJUSTMENT_IN, ADJUSTMENT_OUT
    val quantity: Int,
    val unitPrice: Double,
    val total: Double,
    val referenceId: String,
    val date: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val createdBy: String = "Admin",
    val balanceAfter: Int = 0
)
