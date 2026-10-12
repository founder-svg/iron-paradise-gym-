package com.ironparadise.gymcrm.data.network

import android.content.Context
import android.util.Log
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.data.db.entities.CustomerEntity
import com.ironparadise.gymcrm.data.db.entities.ProductEntity
import com.ironparadise.gymcrm.data.db.entities.UserEntity
import com.ironparadise.gymcrm.util.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object CloudSyncManager {

    private const val TAG = "CloudSyncManager"
    private const val CONNECT_TIMEOUT = 5000
    private const val READ_TIMEOUT = 10000

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

                // Sync products
                val productsArray = json.optJSONArray("products")
                if (productsArray != null) {
                    val existingProducts = productDao.getAllProductsSync()
                    val existingIds = existingProducts.map { it.id }.toSet()

                    for (i in 0 until productsArray.length()) {
                        val p = productsArray.getJSONObject(i)
                        val id = p.optString("id", "P${System.currentTimeMillis()}_$i")
                        if (!existingIds.contains(id)) {
                            productDao.insertProduct(
                                ProductEntity(
                                    id = id,
                                    name = p.optString("name", "Product"),
                                    brand = p.optString("brand", "Iron Paradise"),
                                    category = p.optString("category", "Supplements"),
                                    variant = p.optString("variant", "Standard"),
                                    unit = p.optString("unit", "Jar"),
                                    sellingPrice = p.optDouble("sellingPrice", 0.0),
                                    purchasePrice = p.optDouble("purchasePrice", 0.0),
                                    minimumStock = p.optInt("minimumStock", 5),
                                    imageUri = p.optString("photoUri", "").ifBlank { null }
                                )
                            )
                        }
                    }
                }

                // Sync customers
                val customersArray = json.optJSONArray("customers")
                if (customersArray != null) {
                    val existingCustomers = customerDao.getAllCustomersSync()
                    val existingPhones = existingCustomers.map { it.phone }.toSet()

                    for (i in 0 until customersArray.length()) {
                        val c = customersArray.getJSONObject(i)
                        val phone = c.optString("phone", "")
                        if (phone.isNotBlank() && !existingPhones.contains(phone)) {
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
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "pullRemoteSync error: ${e.message}")
        }
        false
    }
}
