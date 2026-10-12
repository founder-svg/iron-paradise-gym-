package com.ironparadise.gymcrm.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    val id: String, // e.g. "WP001"
    val name: String,
    val brand: String,
    val category: String, // Protein, Creatine, Pre-workout, Gainer, etc.
    val variant: String, // Chocolate, Vanilla, 250g, etc.
    val unit: String, // Box, Bottle, Packet
    val sellingPrice: Double,
    val purchasePrice: Double,
    val minimumStock: Int = 5,
    val status: String = "ACTIVE",
    val imageUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
