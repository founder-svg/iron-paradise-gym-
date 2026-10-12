package com.ironparadise.gymcrm.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val invoiceNumber: String, // e.g. IPG-20261007-001
    val customerId: Long? = null,
    val customerName: String,
    val customerPhone: String,
    val date: Long = System.currentTimeMillis(),
    val subtotal: Double,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val grandTotal: Double,
    val paymentMethod: String = "CASH", // CASH, UPI, CARD, OTHER
    val status: String = "COMPLETED", // COMPLETED, CANCELLED
    val createdByUser: String = "Admin"
)
