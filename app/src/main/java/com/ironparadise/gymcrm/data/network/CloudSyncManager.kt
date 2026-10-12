package com.ironparadise.gymcrm.data.network

import android.content.Context
import android.util.Log
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.data.db.entities.CustomerEntity
import com.ironparadise.gymcrm.data.db.entities.ProductEntity
import com.ironparadise.gymcrm.data.db.entities.SaleEntity
import com.ironparadise.gymcrm.data.db.entities.SaleItemEntity
import com.ironparadise.gymcrm.data.db.entities.StockTransactionEntity
import com.ironparadise.gymcrm.data.db.entities.UserEntity
import com.ironparadise.gymcrm.data.model.CartItem
import com.ironparadise.gymcrm.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object CloudSyncManager {

    private const val TAG = "CloudSyncManager"
    private const val CONNECT_TIMEOUT = 5000
    private const val READ_TIMEOUT = 12000

    private fun getServerUrl(context: Context): String {
        val prefs = context.getSharedPreferences("iron_paradise_server", Context.MODE_PRIVATE)
        return prefs.getString("server_url", AppConfig.DEFAULT_SERVER_URL) ?: AppConfig.DEFAULT_SERVER_URL
    }

    suspend fun checkRemoteUserStatus(context: Context, email: String): String? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getServerUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/api/sync/status?email=${email.trim()}"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val json = JSONObject(response)
                val userObj = json.optJSONObject("user")
                return@withContext userObj?.optString("status", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkRemoteUserStatus error: ${e.message}")
        }
        null
    }

    suspend fun registerRemoteUser(context: Context, user: UserEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getServerUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/api/auth/register"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("name", user.name)
                put("email", user.email)
                put("password", user.password)
                put("role", user.role)
                put("authProvider", user.authProvider)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            return@withContext conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "registerRemoteUser error: ${e.message}")
        }
        false
    }

    /**
     * Upload local image file to cloud server, returns cloud public URL
     */
    suspend fun uploadPhoto(context: Context, localFilePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(localFilePath)
            if (!file.exists()) return@withContext null

            val baseUrl = getServerUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/api/upload"
            val boundary = "==Boundary${System.currentTimeMillis()}=="
            val lineEnd = "\r\n"
            val twoHyphens = "--"

            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 15000
                doInput = true
                doOutput = true
                useCaches = false
                setRequestProperty("Connection", "Keep-Alive")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }

            val outputStream = DataOutputStream(conn.outputStream)
            outputStream.writeBytes("$twoHyphens$boundary$lineEnd")
            outputStream.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"${file.name}\"$lineEnd")
            outputStream.writeBytes("Content-Type: image/jpeg$lineEnd$lineEnd")

            val fileInputStream = FileInputStream(file)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            fileInputStream.close()

            outputStream.writeBytes(lineEnd)
            outputStream.writeBytes("$twoHyphens$boundary$twoHyphens$lineEnd")
            outputStream.flush()
            outputStream.close()

            if (conn.responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                val json = JSONObject(response)
                return@withContext json.optString("url", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadPhoto error: ${e.message}")
        }
        null
    }

    /**
     * Push newly created/edited product to server
     */
    suspend fun pushProduct(context: Context, product: ProductEntity, initialStock: Int = 0): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getServerUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/api/sync/push"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val prodObj = JSONObject().apply {
                put("id", product.id)
                put("name", product.name)
                put("brand", product.brand)
                put("category", product.category)
                put("variant", product.variant)
                put("unit", product.unit)
                put("sellingPrice", product.sellingPrice)
                put("purchasePrice", product.purchasePrice)
                put("minimumStock", product.minimumStock)
                put("imageUri", product.imageUri ?: "")
                put("createdAt", product.createdAt)
            }

            val payload = JSONObject().apply {
                put("products", JSONArray().put(prodObj))
            }

            if (initialStock > 0) {
                val txObj = JSONObject().apply {
                    put("productId", product.id)
                    put("productName", product.name)
                    put("transactionType", "STOCK_IN")
                    put("quantity", initialStock)
                    put("unitPrice", product.purchasePrice)
                    put("total", initialStock * product.purchasePrice)
                    put("balanceAfter", initialStock)
                    put("referenceId", "INIT-${product.id}")
                    put("notes", "Opening inventory")
                    put("date", System.currentTimeMillis())
                    put("user", IronParadiseApp.instance.sessionManager.userName)
                }
                payload.put("transactions", JSONArray().put(txObj))
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            return@withContext conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "pushProduct error: ${e.message}")
        }
        false
    }

    /**
     * Push a confirmed sale to the server
     */
    suspend fun pushSale(context: Context, sale: SaleEntity, items: List<CartItem>): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getServerUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/api/sync/push"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val itemsArray = JSONArray()
            val transactionsArray = JSONArray()

            items.forEach { cartItem ->
                itemsArray.put(JSONObject().apply {
                    put("productId", cartItem.product.id)
                    put("productName", cartItem.product.name)
                    put("quantity", cartItem.quantity)
                    put("unitPrice", cartItem.unitPrice)
                    put("total", cartItem.total)
                })

                transactionsArray.put(JSONObject().apply {
                    put("productId", cartItem.product.id)
                    put("productName", cartItem.product.name)
                    put("transactionType", "SALE")
                    put("quantity", cartItem.quantity)
                    put("unitPrice", cartItem.unitPrice)
                    put("total", cartItem.total)
                    put("balanceAfter", cartItem.availableStock - cartItem.quantity)
                    put("referenceId", sale.invoiceNumber)
                    put("notes", "Sale to ${sale.customerName}")
                    put("date", sale.date)
                    put("user", sale.createdByUser)
                })
            }

            val saleObj = JSONObject().apply {
                put("invoiceNumber", sale.invoiceNumber)
                put("customerId", sale.customerId)
                put("customerName", sale.customerName)
                put("customerPhone", sale.customerPhone)
                put("subtotal", sale.subtotal)
                put("discount", sale.discount)
                put("grandTotal", sale.grandTotal)
                put("paymentMethod", sale.paymentMethod)
                put("user", sale.createdByUser)
                put("date", sale.date)
                put("items", itemsArray)
            }

            val payload = JSONObject().apply {
                put("sales", JSONArray().put(saleObj))
                put("transactions", transactionsArray)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            return@withContext conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "pushSale error: ${e.message}")
        }
        false
    }

    /**
     * Push stock transaction (Stock In, Damage, Adjustment) to server
     */
    suspend fun pushStockTransaction(context: Context, transaction: StockTransactionEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getServerUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/api/sync/push"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val txObj = JSONObject().apply {
                put("productId", transaction.productId)
                put("productName", transaction.productName)
                put("transactionType", transaction.transactionType)
                put("quantity", transaction.quantity)
                put("unitPrice", transaction.unitPrice)
                put("total", transaction.total)
                put("balanceAfter", transaction.balanceAfter)
                put("referenceId", transaction.referenceId)
                put("notes", transaction.notes ?: "")
                put("date", transaction.date)
                put("user", transaction.createdBy)
            }

            val payload = JSONObject().apply {
                put("transactions", JSONArray().put(txObj))
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            return@withContext conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "pushStockTransaction error: ${e.message}")
        }
        false
    }

    /**
     * Full bidirectional cloud pull: updates products (including images), sales, customers, and stock transactions
     */
    suspend fun pullRemoteSync(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getServerUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/api/sync/pull"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val app = IronParadiseApp.instance
                val productDao = app.database.productDao()
                val customerDao = app.database.customerDao()
                val saleDao = app.database.saleDao()
                val txDao = app.database.stockTransactionDao()
                val isAdmin = app.sessionManager.isAdmin

                // 1. Sync Products (including photos uploaded by Admin)
                val productsArray = json.optJSONArray("products")
                if (productsArray != null) {
                    val existingProducts = productDao.getAllProductsSync().associateBy { it.id }

                    for (i in 0 until productsArray.length()) {
                        val p = productsArray.getJSONObject(i)
                        val id = p.optString("id", "")
                        if (id.isBlank()) continue

                        val existing = existingProducts[id]
                        val rawImageUri = p.optString("imageUri", "").ifBlank { null }
                        val cloudPurchasePrice = p.optDouble("purchasePrice", 0.0)

                        val finalPurchasePrice = if (isAdmin) {
                            if (cloudPurchasePrice > 0) cloudPurchasePrice else (existing?.purchasePrice ?: 0.0)
                        } else {
                            0.0 // Staff cannot see sensitive purchase price
                        }

                        val productEntity = ProductEntity(
                            id = id,
                            name = p.optString("name", existing?.name ?: "Product"),
                            brand = p.optString("brand", existing?.brand ?: "Iron Paradise"),
                            category = p.optString("category", existing?.category ?: "Supplements"),
                            variant = p.optString("variant", existing?.variant ?: "Standard"),
                            unit = p.optString("unit", existing?.unit ?: "Unit"),
                            sellingPrice = p.optDouble("sellingPrice", existing?.sellingPrice ?: 0.0),
                            purchasePrice = finalPurchasePrice,
                            minimumStock = p.optInt("minimumStock", existing?.minimumStock ?: 5),
                            imageUri = rawImageUri ?: existing?.imageUri,
                            createdAt = p.optLong("createdAt", existing?.createdAt ?: System.currentTimeMillis()),
                            updatedAt = System.currentTimeMillis()
                        )
                        productDao.insertProduct(productEntity)
                    }
                }

                // 2. Sync Customers
                val customersArray = json.optJSONArray("customers")
                if (customersArray != null) {
                    val existingCustomers = customerDao.getAllCustomersSync().associateBy { it.phone }

                    for (i in 0 until customersArray.length()) {
                        val c = customersArray.getJSONObject(i)
                        val phone = c.optString("phone", "")
                        if (phone.isNotBlank() && !existingCustomers.containsKey(phone)) {
                            customerDao.insertCustomer(
                                CustomerEntity(
                                    name = c.optString("name", "Customer"),
                                    phone = phone,
                                    email = c.optString("email", "").ifBlank { null },
                                    address = c.optString("address", "").ifBlank { null }
                                )
                            )
                        }
                    }
                }

                // 3. Sync Sales and Line Items
                val salesArray = json.optJSONArray("sales")
                if (salesArray != null) {
                    for (i in 0 until salesArray.length()) {
                        val s = salesArray.getJSONObject(i)
                        val invoiceNumber = s.optString("invoiceNumber", "")
                        if (invoiceNumber.isBlank()) continue

                        val existingSale = saleDao.getSaleByInvoiceNumber(invoiceNumber)
                        if (existingSale == null) {
                            val newSale = SaleEntity(
                                invoiceNumber = invoiceNumber,
                                customerId = if (s.has("customerId") && !s.isNull("customerId")) s.optLong("customerId") else null,
                                customerName = s.optString("customerName", "Walk-in Customer"),
                                customerPhone = s.optString("customerPhone", "-"),
                                subtotal = s.optDouble("subtotal", 0.0),
                                discount = s.optDouble("discount", 0.0),
                                grandTotal = s.optDouble("grandTotal", 0.0),
                                paymentMethod = s.optString("paymentMethod", "CASH"),
                                createdByUser = s.optString("user", "Staff"),
                                date = s.optLong("date", System.currentTimeMillis())
                            )
                            val insertedSaleId = saleDao.insertSale(newSale)

                            // Insert Line Items
                            val itemsArray = s.optJSONArray("items")
                            if (itemsArray != null && itemsArray.length() > 0) {
                                val saleItems = mutableListOf<SaleItemEntity>()
                                for (j in 0 until itemsArray.length()) {
                                    val item = itemsArray.getJSONObject(j)
                                    saleItems.add(
                                        SaleItemEntity(
                                            saleId = insertedSaleId,
                                            productId = item.optString("productId", ""),
                                            productName = item.optString("productName", ""),
                                            quantity = item.optInt("quantity", 1),
                                            unitPrice = item.optDouble("unitPrice", 0.0),
                                            purchasePrice = if (isAdmin) item.optDouble("purchasePrice", 0.0) else 0.0,
                                            total = item.optDouble("total", 0.0)
                                        )
                                    )
                                }
                                saleDao.insertSaleItems(saleItems)
                            }
                        }
                    }
                }

                // 4. Sync Stock Transactions (so live stock balances match on all devices)
                val txArray = json.optJSONArray("transactions")
                if (txArray != null) {
                    for (i in 0 until txArray.length()) {
                        val t = txArray.getJSONObject(i)
                        val productId = t.optString("productId", "")
                        val refId = t.optString("referenceId", "")
                        val txType = t.optString("transactionType", "")
                        if (productId.isBlank() || txType.isBlank()) continue

                        // Check if transaction with this referenceId already exists
                        val existingForProduct = txDao.getTransactionsForProductSync(productId)
                        val exists = if (refId.isNotBlank()) {
                            existingForProduct.any { it.referenceId == refId && it.transactionType == txType }
                        } else false

                        if (!exists) {
                            txDao.insertTransaction(
                                StockTransactionEntity(
                                    productId = productId,
                                    productName = t.optString("productName", "Product"),
                                    transactionType = txType,
                                    quantity = t.optInt("quantity", 0),
                                    unitPrice = if (isAdmin || txType != "STOCK_IN") t.optDouble("unitPrice", 0.0) else 0.0,
                                    total = if (isAdmin || txType != "STOCK_IN") t.optDouble("total", 0.0) else 0.0,
                                    balanceAfter = t.optInt("balanceAfter", 0),
                                    referenceId = refId,
                                    notes = t.optString("notes", "").ifBlank { null },
                                    date = t.optLong("date", System.currentTimeMillis()),
                                    createdBy = t.optString("user", "Admin")
                                )
                            )
                        }
                    }
                }

                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "pullRemoteSync error: ${e.message}")
        }
        false
    }

    suspend fun updateRemoteStaffStatus(context: Context, email: String, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getServerUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/api/sync/staff-status"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("email", email)
                put("status", status)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            return@withContext conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "updateRemoteStaffStatus error: ${e.message}")
        }
        false
    }

    suspend fun deleteRemoteStaff(context: Context, email: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getServerUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/api/sync/delete-staff"
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("email", email)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            return@withContext conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "deleteRemoteStaff error: ${e.message}")
        }
        false
    }
}
