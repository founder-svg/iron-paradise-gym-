package com.ironparadise.gymcrm.data.db.dao

import androidx.room.*
import com.ironparadise.gymcrm.data.db.entities.StockTransactionEntity
import kotlinx.coroutines.flow.Flow

data class ProductStockResult(
    val productId: String,
    val netStock: Int
)

@Dao
interface StockTransactionDao {
    @Query("SELECT * FROM stock_transactions ORDER BY date DESC, id DESC")
    fun getAllTransactions(): Flow<List<StockTransactionEntity>>

    @Query("SELECT * FROM stock_transactions WHERE productId = :productId ORDER BY date DESC, id DESC")
    fun getTransactionsForProduct(productId: String): Flow<List<StockTransactionEntity>>

    @Query("SELECT * FROM stock_transactions WHERE productId = :productId ORDER BY date DESC, id DESC")
    suspend fun getTransactionsForProductSync(productId: String): List<StockTransactionEntity>

    @Query("SELECT * FROM stock_transactions ORDER BY date DESC, id DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<StockTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: StockTransactionEntity): Long

    @Query("""
        SELECT COALESCE(
            SUM(CASE 
                WHEN transactionType IN ('STOCK_IN', 'SALE_RETURN', 'ADJUSTMENT_IN') THEN quantity 
                WHEN transactionType IN ('SALE', 'ADJUSTMENT_OUT') THEN -quantity 
                ELSE 0 END), 0)
        FROM stock_transactions 
        WHERE productId = :productId
    """)
    suspend fun getNetStockForProduct(productId: String): Int

    @Query("""
        SELECT productId,
            COALESCE(
                SUM(CASE 
                    WHEN transactionType IN ('STOCK_IN', 'SALE_RETURN', 'ADJUSTMENT_IN') THEN quantity 
                    WHEN transactionType IN ('SALE', 'ADJUSTMENT_OUT') THEN -quantity 
                    ELSE 0 END), 0) as netStock
        FROM stock_transactions 
        GROUP BY productId
    """)
    fun getAllProductsStock(): Flow<List<ProductStockResult>>

    @Query("""
        SELECT COALESCE(
            SUM(CASE 
                WHEN transactionType IN ('STOCK_IN', 'SALE_RETURN', 'ADJUSTMENT_IN') THEN quantity 
                WHEN transactionType IN ('SALE', 'ADJUSTMENT_OUT') THEN -quantity 
                ELSE 0 END), 0)
        FROM stock_transactions
    """)
    fun getTotalStockUnits(): Flow<Int>

    @Query("DELETE FROM stock_transactions WHERE productId = :productId")
    suspend fun deleteTransactionsForProduct(productId: String)
}
