package com.ironparadise.gymcrm.data.repository

import com.ironparadise.gymcrm.data.db.AppDatabase
import com.ironparadise.gymcrm.data.db.entities.*
import com.ironparadise.gymcrm.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.*

class InsufficientStockException(
    val productName: String,
    val available: Int,
    val requested: Int
) : Exception("Insufficient Stock for $productName: Available $available, Requested $requested")

class GymRepository(private val db: AppDatabase) {

    private val productDao = db.productDao()
    private val stockDao = db.stockTransactionDao()
    private val customerDao = db.customerDao()
    private val saleDao = db.saleDao()
    private val userDao = db.userDao()

    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()
    val allTransactions: Flow<List<StockTransactionEntity>> = stockDao.getAllTransactions()
    val allSales: Flow<List<SaleEntity>> = saleDao.getAllSales()

    fun getRecentTransactions(limit: Int = 15): Flow<List<StockTransactionEntity>> =
        stockDao.getRecentTransactions(limit)

    fun getProductsWithStockFlow(): Flow<List<ProductWithStock>> {
        return combine(
            productDao.getAllProducts(),
            stockDao.getAllProductsStock()
        ) { products, stockResults ->
            val stockMap = stockResults.associate { it.productId to it.netStock }
            products.map { product ->
                ProductWithStock(
                    product = product,
                    currentStock = stockMap[product.id] ?: 0
                )
            }
        }
    }

    suspend fun getProductWithStock(productId: String): ProductWithStock? {
        val product = productDao.getProductById(productId) ?: return null
        val netStock = stockDao.getNetStockForProduct(productId)
        return ProductWithStock(product, netStock)
    }

    suspend fun getNetStock(productId: String): Int {
        return stockDao.getNetStockForProduct(productId)
    }

    suspend fun getDashboardStats(): DashboardStats {
        val products = productDao.getAllProductsSync()
        val totalProducts = products.size

        var totalStock = 0
        var lowStockCount = 0
        var outOfStockCount = 0

        for (p in products) {
            val stock = stockDao.getNetStockForProduct(p.id)
            totalStock += stock
            if (stock <= 0) {
                outOfStockCount++
            } else if (stock <= p.minimumStock) {
                lowStockCount++
            }
        }

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis

        val todaySales = saleDao.getSalesBetween(startOfDay, endOfDay)
        var todaySalesAmount = 0.0
        var todayItemsSold = 0

        for (sale in todaySales) {
            todaySalesAmount += sale.grandTotal
            val items = saleDao.getItemsForSale(sale.id)
            todayItemsSold += items.sumOf { it.quantity }
        }

        return DashboardStats(
            totalProducts = totalProducts,
            totalStockUnits = totalStock,
            todaySalesAmount = todaySalesAmount,
            todayItemsSold = todayItemsSold,
            lowStockCount = lowStockCount,
            outOfStockCount = outOfStockCount
        )
    }

    suspend fun addProduct(product: ProductEntity, initialStock: Int = 0, user: String = "Admin") {
        productDao.insertProduct(product)
        if (initialStock > 0) {
            val dateCode = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val randomSuffix = (100..999).random()
            val entryId = "INIT-$dateCode-$randomSuffix"
            stockDao.insertTransaction(
                StockTransactionEntity(
                    productId = product.id,
                    productName = product.name,
                    transactionType = "STOCK_IN",
                    quantity = initialStock,
                    unitPrice = product.purchasePrice,
                    total = initialStock * product.purchasePrice,
                    referenceId = entryId,
                    balanceAfter = initialStock,
                    notes = "Initial Opening Stock Entry",
                    createdBy = user
                )
            )
        }
    }

    suspend fun updateProduct(product: ProductEntity) {
        productDao.updateProduct(product)
    }

    suspend fun deleteProduct(productId: String) {
        val product = productDao.getProductById(productId)
        if (product != null) {
            com.ironparadise.gymcrm.util.ImageHelper.deleteImageFile(product.imageUri)
            stockDao.deleteTransactionsForProduct(productId)
            productDao.deleteProductById(productId)
        }
    }

    suspend fun recordStockIn(
        productId: String,
        quantity: Int,
        purchasePrice: Double,
        supplier: String? = null,
        batchNumber: String? = null,
        expiryDate: String? = null,
        notes: String? = null,
        user: String = "Admin"
    ): Long {
        val product = productDao.getProductById(productId)
            ?: throw IllegalArgumentException("Product not found")

        val currentStock = stockDao.getNetStockForProduct(productId)
        val balanceAfter = currentStock + quantity

        val dateCode = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val randomSuffix = (100..999).random()
        val entryId = "STK-$dateCode-$randomSuffix"

        val noteBuilder = StringBuilder()
        if (!notes.isNullOrBlank()) noteBuilder.append(notes)
        if (!supplier.isNullOrBlank()) noteBuilder.append(" | Supplier: $supplier")
        if (!batchNumber.isNullOrBlank()) noteBuilder.append(" | Batch: $batchNumber")
        if (!expiryDate.isNullOrBlank()) noteBuilder.append(" | Exp: $expiryDate")

        val transaction = StockTransactionEntity(
            productId = productId,
            productName = product.name,
            transactionType = TransactionType.STOCK_IN.name,
            quantity = quantity,
            unitPrice = purchasePrice,
            total = quantity * purchasePrice,
            referenceId = entryId,
            date = System.currentTimeMillis(),
            notes = noteBuilder.toString().ifBlank { null },
            createdBy = user,
            balanceAfter = balanceAfter
        )

        return stockDao.insertTransaction(transaction)
    }

    suspend fun recordSale(
        customerId: Long?,
        customerName: String,
        customerPhone: String,
        items: List<CartItem>,
        discount: Double,
        tax: Double = 0.0,
        paymentMethod: String,
        user: String = "Admin"
    ): SaleEntity {
        // Step 1: Pre-validate stock for ALL items (Atomic validation)
        for (item in items) {
            val available = stockDao.getNetStockForProduct(item.product.id)
            if (item.quantity > available) {
                throw InsufficientStockException(item.product.name, available, item.quantity)
            }
        }

        // Step 2: Compute invoice number
        val dateCode = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val count = saleDao.getSalesCount() + 1
        val invoiceNo = "IPG-$dateCode-${String.format("%03d", count)}"

        val subtotal = items.sumOf { it.total }
        val grandTotal = (subtotal - discount + tax).coerceAtLeast(0.0)

        // Step 3: Insert Sale
        val sale = SaleEntity(
            invoiceNumber = invoiceNo,
            customerId = customerId,
            customerName = customerName.ifBlank { "Walk-in Customer" },
            customerPhone = customerPhone.ifBlank { "-" },
            date = System.currentTimeMillis(),
            subtotal = subtotal,
            discount = discount,
            tax = tax,
            grandTotal = grandTotal,
            paymentMethod = paymentMethod,
            status = if (user.contains("Admin", ignoreCase = true)) "APPROVED" else "PENDING_APPROVAL",
            createdByUser = user
        )

        val saleId = saleDao.insertSale(sale)

        // Step 4: Insert Sale Items
        val saleItems = items.map {
            SaleItemEntity(
                saleId = saleId,
                productId = it.product.id,
                productName = it.product.name,
                quantity = it.quantity,
                unitPrice = it.unitPrice,
                purchasePrice = it.product.purchasePrice,
                total = it.total
            )
        }
        saleDao.insertSaleItems(saleItems)

        // Step 5: Insert Stock Out transactions for each item
        for (item in items) {
            val currentStock = stockDao.getNetStockForProduct(item.product.id)
            val balanceAfter = currentStock - item.quantity

            val transaction = StockTransactionEntity(
                productId = item.product.id,
                productName = item.product.name,
                transactionType = TransactionType.SALE.name,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                total = item.total,
                referenceId = invoiceNo,
                date = System.currentTimeMillis(),
                notes = "Sale to ${sale.customerName}",
                createdBy = user,
                balanceAfter = balanceAfter
            )
            stockDao.insertTransaction(transaction)
        }

        return sale.copy(id = saleId)
    }

    suspend fun cancelSale(saleId: Long, reason: String = "Sale Cancelled", user: String = "Admin"): Boolean {
        val sale = saleDao.getSaleById(saleId) ?: return false
        if (sale.status == "CANCELLED") return false

        val items = saleDao.getItemsForSale(saleId)

        // Restore stock via SALE_RETURN transactions
        for (item in items) {
            val currentStock = stockDao.getNetStockForProduct(item.productId)
            val balanceAfter = currentStock + item.quantity

            val transaction = StockTransactionEntity(
                productId = item.productId,
                productName = item.productName,
                transactionType = TransactionType.SALE_RETURN.name,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                total = item.total,
                referenceId = "REV-${sale.invoiceNumber}",
                date = System.currentTimeMillis(),
                notes = "Cancelled: $reason",
                createdBy = user,
                balanceAfter = balanceAfter
            )
            stockDao.insertTransaction(transaction)
        }

        saleDao.updateSale(sale.copy(status = "CANCELLED"))
        return true
    }

    suspend fun approveSale(saleId: Long): Boolean {
        val sale = saleDao.getSaleById(saleId) ?: return false
        saleDao.updateSale(sale.copy(status = "APPROVED"))
        return true
    }

    suspend fun recordStockAdjustment(
        productId: String,
        physicalCount: Int,
        reason: String,
        notes: String? = null,
        user: String = "Admin"
    ): Long {
        val product = productDao.getProductById(productId)
            ?: throw IllegalArgumentException("Product not found")

        val systemStock = stockDao.getNetStockForProduct(productId)
        val diff = physicalCount - systemStock

        if (diff == 0) return 0L

        val isAddition = diff > 0
        val qty = if (isAddition) diff else -diff
        val transType = if (isAddition) TransactionType.ADJUSTMENT_IN.name else TransactionType.ADJUSTMENT_OUT.name

        val transaction = StockTransactionEntity(
            productId = productId,
            productName = product.name,
            transactionType = transType,
            quantity = qty,
            unitPrice = product.purchasePrice,
            total = qty * product.purchasePrice,
            referenceId = "ADJ-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())}",
            date = System.currentTimeMillis(),
            notes = "Adjustment ($reason). System: $systemStock -> Physical: $physicalCount. ${notes ?: ""}".trim(),
            createdBy = user,
            balanceAfter = physicalCount
        )

        return stockDao.insertTransaction(transaction)
    }

    suspend fun getProductLedger(productId: String): List<ProductLedgerEntry> {
        val transactions = stockDao.getTransactionsForProductSync(productId)
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        return transactions.map { t ->
            val isStockIn = t.transactionType in listOf("STOCK_IN", "SALE_RETURN", "ADJUSTMENT_IN")
            ProductLedgerEntry(
                transaction = t,
                dateString = dateFormat.format(Date(t.date)),
                stockIn = if (isStockIn) t.quantity else 0,
                stockOut = if (!isStockIn) t.quantity else 0,
                balance = t.balanceAfter
            )
        }
    }

    suspend fun getSaleDetails(saleId: Long): SaleWithDetails? {
        val sale = saleDao.getSaleById(saleId) ?: return null
        val items = saleDao.getItemsForSale(saleId)
        val customer = sale.customerId?.let { customerDao.getCustomerById(it) }
        return SaleWithDetails(sale, items, customer)
    }

    suspend fun addCustomer(customer: CustomerEntity): Long = customerDao.insertCustomer(customer)
    suspend fun updateCustomer(customer: CustomerEntity) = customerDao.updateCustomer(customer)
    suspend fun deleteCustomer(customer: CustomerEntity) = customerDao.deleteCustomer(customer)
    suspend fun getCustomerById(id: Long) = customerDao.getCustomerById(id)
    suspend fun getSalesForCustomer(customerId: Long) = saleDao.getSalesForCustomer(customerId)

    suspend fun verifyPin(pin: String): UserEntity? = userDao.getUserByPin(pin)

    suspend fun getUserByEmail(email: String): UserEntity? = userDao.getUserByEmail(email)

    suspend fun getUserByCredentials(identifier: String, secret: String): UserEntity? =
        userDao.getUserByCredentials(identifier, secret) ?: userDao.getUserByPin(secret)

    suspend fun registerUser(user: UserEntity): Long = userDao.insertUser(user)

    val allStaff: Flow<List<UserEntity>> = userDao.getAllStaffFlow()
    val pendingStaff: Flow<List<UserEntity>> = userDao.getPendingStaffFlow()
    val pendingStaffCount: Flow<Int> = userDao.getPendingStaffCountFlow()

    suspend fun updateStaffStatus(userId: Long, status: String) {
        userDao.updateUserStatus(userId, status)
    }

    suspend fun deleteStaff(userId: Long) {
        userDao.deleteUser(userId)
    }
}
