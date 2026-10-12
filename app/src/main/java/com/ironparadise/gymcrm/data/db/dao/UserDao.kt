package com.ironparadise.gymcrm.data.db.dao

import androidx.room.*
import com.ironparadise.gymcrm.data.db.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE pin = :pin LIMIT 1")
    suspend fun getUserByPin(pin: String): UserEntity?

    @Query("SELECT * FROM users WHERE (email = :identifier OR name = :identifier) AND (password = :credential OR pin = :credential) LIMIT 1")
    suspend fun getUserByCredentials(identifier: String, credential: String): UserEntity?

    @Query("SELECT * FROM users WHERE role = 'STAFF' ORDER BY id DESC")
    fun getAllStaffFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE role = 'STAFF' AND status = 'PENDING' ORDER BY id DESC")
    fun getPendingStaffFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY id ASC")
    suspend fun getAllUsers(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET status = :status WHERE id = :userId")
    suspend fun updateUserStatus(userId: Long, status: String)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Long)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT COUNT(*) FROM users WHERE role = 'STAFF' AND status = 'PENDING'")
    fun getPendingStaffCountFlow(): Flow<Int>
}
