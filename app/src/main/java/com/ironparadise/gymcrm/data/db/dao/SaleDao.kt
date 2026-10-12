package com.ironparadise.gymcrm.data.db.dao

import androidx.room.*
import com.ironparadise.gymcrm.data.db.entities.SaleEntity
import com.ironparadise.gymcrm.data.db.entities.SaleItemEntity
import kotlinx.coroutines.flow.Flow

data class TodaySalesSummary(
    val totalAmount: Double,
    val itemsCount: Int
)

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY date DESC, id DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales ORDER BY date DESC, id DESC LIMIT :limit")
    fun getRecentSales(limit: Int): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE id = :id LIMIT 1")
    suspend fun getSaleById(id: Long): SaleEntity?

    @Query("SELECT * FROM sales WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getSaleByInvoiceNumber(invoiceNumber: String): SaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity): Long

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItem(item: SaleItemEntity): Long

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getItemsForSale(saleId: Long): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun getItemsForSaleFlow(saleId: Long): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sales WHERE date >= :startOfDay AND date <= :endOfDay AND status != 'CANCELLED'")
    fun getTodaySales(startOfDay: Long, endOfDay: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE date >= :startDate AND date <= :endDate AND status != 'CANCELLED' ORDER BY date DESC")
    suspend fun getSalesBetween(startDate: Long, endDate: Long): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE customerId = :customerId ORDER BY date DESC")
    suspend fun getSalesForCustomer(customerId: Long): List<SaleEntity>

    @Query("SELECT COUNT(*) FROM sales")
    suspend fun getSalesCount(): Int
}
