package com.ironparadise.gymcrm.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val email: String,
    val name: String,
    val password: String = "",
    val pin: String = "",
    val role: String = "STAFF", // ADMIN, STAFF
    val status: String = "APPROVED", // APPROVED, PENDING, REJECTED
    val authProvider: String = "EMAIL", // EMAIL, GOOGLE
    val phone: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
