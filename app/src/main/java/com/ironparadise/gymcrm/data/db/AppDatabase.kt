package com.ironparadise.gymcrm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ironparadise.gymcrm.data.db.dao.*
import com.ironparadise.gymcrm.data.db.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProductEntity::class,
        StockTransactionEntity::class,
        CustomerEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        UserEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun stockTransactionDao(): StockTransactionDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iron_paradise_gym_crm.db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            private suspend fun populateInitialData(database: AppDatabase) {
                val userDao = database.userDao()
                val productDao = database.productDao()
                val stockDao = database.stockTransactionDao()
                val customerDao = database.customerDao()
                val saleDao = database.saleDao()

                // Default Users
                userDao.insertUser(UserEntity(email = "admin@ironparadise.com", name = "Gym Owner (Admin)", password = "admin123", pin = "1234", role = "ADMIN", status = "APPROVED", authProvider = "EMAIL"))
                userDao.insertUser(UserEntity(email = "staff@ironparadise.com", name = "Front Desk (Staff)", password = "staff123", pin = "0000", role = "STAFF", status = "APPROVED", authProvider = "EMAIL"))

                // Initial Products
                val p1 = ProductEntity("WP001", "Gold Standard 100% Whey", "Optimum Nutrition", "Protein", "Double Rich Chocolate 2kg", "Tub", 2999.0, 2300.0, 5)
                val p2 = ProductEntity("CR001", "Creatine Monohydrate", "MuscleBlaze", "Creatine", "Unflavored 250g", "Box", 1499.0, 950.0, 5)
                val p3 = ProductEntity("MG001", "Muscle Mass Gainer", "Labrada", "Mass Gainer", "Belgian Chocolate 3kg", "Jar", 3200.0, 2400.0, 4)
                val p4 = ProductEntity("PW001", "C4 Original Pre-Workout", "Cellucor", "Pre-Workout", "Fruit Punch 30 Serv", "Tub", 2299.0, 1650.0, 3)
                val p5 = ProductEntity("BC001", "BCAA 6000", "Gaspari Nutrition", "BCAA", "Watermelon 300g", "Bottle", 1850.0, 1250.0, 3)
                val p6 = ProductEntity("FO001", "Triple Strength Fish Oil", "TrueBasics", "Health", "60 Softgels", "Bottle", 899.0, 550.0, 5)

                productDao.insertProduct(p1)
                productDao.insertProduct(p2)
                productDao.insertProduct(p3)
                productDao.insertProduct(p4)
                productDao.insertProduct(p5)
                productDao.insertProduct(p6)

                // Opening Stock Transactions
                val now = System.currentTimeMillis()
                stockDao.insertTransaction(StockTransactionEntity(productId = p1.id, productName = p1.name, transactionType = "STOCK_IN", quantity = 15, unitPrice = p1.purchasePrice, total = 15 * p1.purchasePrice, referenceId = "INIT-STOCK", date = now, notes = "Opening Inventory", createdBy = "System", balanceAfter = 15))
                stockDao.insertTransaction(StockTransactionEntity(productId = p2.id, productName = p2.name, transactionType = "STOCK_IN", quantity = 20, unitPrice = p2.purchasePrice, total = 20 * p2.purchasePrice, referenceId = "INIT-STOCK", date = now, notes = "Opening Inventory", createdBy = "System", balanceAfter = 20))
                stockDao.insertTransaction(StockTransactionEntity(productId = p3.id, productName = p3.name, transactionType = "STOCK_IN", quantity = 8, unitPrice = p3.purchasePrice, total = 8 * p3.purchasePrice, referenceId = "INIT-STOCK", date = now, notes = "Opening Inventory", createdBy = "System", balanceAfter = 8))
                stockDao.insertTransaction(StockTransactionEntity(productId = p4.id, productName = p4.name, transactionType = "STOCK_IN", quantity = 10, unitPrice = p4.purchasePrice, total = 10 * p4.purchasePrice, referenceId = "INIT-STOCK", date = now, notes = "Opening Inventory", createdBy = "System", balanceAfter = 10))
                stockDao.insertTransaction(StockTransactionEntity(productId = p5.id, productName = p5.name, transactionType = "STOCK_IN", quantity = 7, unitPrice = p5.purchasePrice, total = 7 * p5.purchasePrice, referenceId = "INIT-STOCK", date = now, notes = "Opening Inventory", createdBy = "System", balanceAfter = 7))
                stockDao.insertTransaction(StockTransactionEntity(productId = p6.id, productName = p6.name, transactionType = "STOCK_IN", quantity = 12, unitPrice = p6.purchasePrice, total = 12 * p6.purchasePrice, referenceId = "INIT-STOCK", date = now, notes = "Opening Inventory", createdBy = "System", balanceAfter = 12))

                // Default Customers
                val c1Id = customerDao.insertCustomer(CustomerEntity(name = "Rahul Patel", phone = "9876543210", email = "rahul.patel@gmail.com", address = "Sector 14, Main Road"))
                customerDao.insertCustomer(CustomerEntity(name = "Amit Kumar", phone = "9811223344", email = "amit.k@gmail.com", address = "Crossfit Wing, Block B"))
                customerDao.insertCustomer(CustomerEntity(name = "Priya Sharma", phone = "9988776655", email = "priya.s@yahoo.com", address = "Fitness Heights, Apt 402"))
                customerDao.insertCustomer(CustomerEntity(name = "Vikram Singh", phone = "9765432109", email = "vikram.power@gmail.com", address = "Gym Enclave 12"))

                // Sample Sale demonstration: 15 existing - 2 sold = 13 balance
                val sale1 = SaleEntity(
                    invoiceNumber = "IPG-20261007-001",
                    customerId = c1Id,
                    customerName = "Rahul Patel",
                    customerPhone = "9876543210",
                    date = now + 1000,
                    subtotal = 6998.0,
                    discount = 0.0,
                    tax = 0.0,
                    grandTotal = 6998.0,
                    paymentMethod = "UPI"
                )
                val saleId1 = saleDao.insertSale(sale1)
                saleDao.insertSaleItem(
                    SaleItemEntity(
                        saleId = saleId1,
                        productId = p1.id,
                        productName = p1.name,
                        quantity = 2,
                        unitPrice = p1.sellingPrice,
                        purchasePrice = p1.purchasePrice,
                        total = 2 * p1.sellingPrice
                    )
                )
                stockDao.insertTransaction(
                    StockTransactionEntity(
                        productId = p1.id,
                        productName = p1.name,
                        transactionType = "SALE",
                        quantity = 2,
                        unitPrice = p1.sellingPrice,
                        total = 2 * p1.sellingPrice,
                        referenceId = "IPG-20261007-001",
                        date = now + 1000,
                        notes = "Sold 2 units to Rahul Patel",
                        createdBy = "Admin",
                        balanceAfter = 13
                    )
                )
            }
        }
    }
}
