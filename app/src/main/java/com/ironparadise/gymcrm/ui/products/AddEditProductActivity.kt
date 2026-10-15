package com.ironparadise.gymcrm.ui.products

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ironparadise.gymcrm.IronParadiseApp
import com.ironparadise.gymcrm.R
import com.ironparadise.gymcrm.data.db.entities.ProductEntity
import com.ironparadise.gymcrm.databinding.ActivityAddEditProductBinding
import com.ironparadise.gymcrm.util.Formatters
import com.ironparadise.gymcrm.util.ImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AddEditProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditProductBinding
    private val app by lazy { IronParadiseApp.instance }

    private var editProductId: String? = null
    private var isEditMode: Boolean = false
    private var existingProduct: ProductEntity? = null
    private var currentImageUri: String? = null
    private var tempCameraUri: Uri? = null
    private var tempCameraFile: File? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            processAndSetImage(tempCameraUri!!)
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            processAndSetImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editProductId = intent.getStringExtra("EXTRA_PRODUCT_ID")?.trim()
        isEditMode = !editProductId.isNullOrBlank()

        binding.btnBack.setOnClickListener { finish() }

        setupPhotoButtons()
        setupFormulaWatchers()

        if (isEditMode) {
            setupEditMode(editProductId!!)
        } else {
            setupAddMode()
        }

        binding.btnSaveProduct.setOnClickListener {
            saveOrUpdateProduct()
        }

        binding.btnDeleteProduct.setOnClickListener {
            confirmDeleteProduct()
        }
    }

    private fun setupPhotoButtons() {
        binding.btnTakePhoto.setOnClickListener {
            val (uri, file) = ImageHelper.createTempCameraUri(this)
            tempCameraUri = uri
            tempCameraFile = file
            takePictureLauncher.launch(uri)
        }

        binding.btnUploadPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnRemovePhoto.setOnClickListener {
            if (!currentImageUri.isNullOrBlank()) {
                ImageHelper.deleteImageFile(currentImageUri)
            }
            currentImageUri = null
            updatePhotoPreview()
        }
    }

    private fun processAndSetImage(sourceUri: Uri) {
        val prodIdCandidate = binding.etProductId.text.toString().trim().ifBlank {
            editProductId ?: "new_${System.currentTimeMillis()}"
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val savedPath = ImageHelper.saveImageToInternalStorage(this@AddEditProductActivity, sourceUri, prodIdCandidate)
            withContext(Dispatchers.Main) {
                if (savedPath != null) {
                    if (currentImageUri != null && currentImageUri != savedPath) {
                        ImageHelper.deleteImageFile(currentImageUri)
                    }
                    currentImageUri = savedPath
                    updatePhotoPreview()
                    Toast.makeText(this@AddEditProductActivity, "Photo attached successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@AddEditProductActivity, "Unable to save photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updatePhotoPreview() {
        if (!currentImageUri.isNullOrBlank()) {
            ImageHelper.loadImage(binding.ivProductPhoto, currentImageUri)
            binding.ivProductPhoto.imageTintList = null
            binding.tvPhotoStatus.text = "Custom photo attached"
            binding.tvPhotoStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.btnRemovePhoto.visibility = View.VISIBLE
        } else {
            binding.ivProductPhoto.setImageResource(R.drawable.ic_supplement_jar)
            binding.ivProductPhoto.setColorFilter(ContextCompat.getColor(this, R.color.accent_gold))
            binding.ivProductPhoto.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            binding.tvPhotoStatus.text = "No photo uploaded"
            binding.tvPhotoStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            binding.btnRemovePhoto.visibility = View.GONE
        }
    }

    private fun setupAddMode() {
        binding.tvTitle.text = "ADD NEW PRODUCT"
        binding.btnSaveProduct.text = "SAVE PRODUCT TO CATALOG"
        binding.btnDeleteProduct.visibility = View.GONE
        binding.tilTotalQuantity.visibility = View.VISIBLE
        binding.tvEditStockNote.visibility = View.GONE
        binding.cardInventoryValue.visibility = View.VISIBLE
        updatePhotoPreview()
    }

    private var initialStockCount: Int = 0

    private fun setupEditMode(productId: String) {
        binding.tvTitle.text = "EDIT PRODUCT / SUPPLEMENT"
        binding.btnSaveProduct.text = "UPDATE PRODUCT & STOCK"
        binding.btnDeleteProduct.visibility = View.VISIBLE

        binding.etProductId.isEnabled = false
        binding.tilProductId.helperText = "Product ID cannot be changed"

        binding.tilTotalQuantity.visibility = View.VISIBLE
        binding.tilTotalQuantity.hint = "Total In-Stock Quantity"
        binding.cardInventoryValue.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val itemWithStock = app.repository.getProductWithStock(productId)
            withContext(Dispatchers.Main) {
                if (itemWithStock != null) {
                    val p = itemWithStock.product
                    existingProduct = p
                    currentImageUri = p.imageUri
                    initialStockCount = itemWithStock.currentStock

                    binding.etProductId.setText(p.id)
                    binding.etProductName.setText(p.name)
                    binding.etBrand.setText(p.brand)
                    binding.etCategory.setText(p.category)
                    binding.etVariant.setText(p.variant)
                    binding.etUnit.setText(p.unit)
                    binding.etSellingPrice.setText(if (p.sellingPrice > 0) p.sellingPrice.toString() else "")
                    binding.etPurchasePrice.setText(if (p.purchasePrice > 0) p.purchasePrice.toString() else "")
                    binding.etTotalQuantity.setText(initialStockCount.toString())
                    binding.etMinimumStock.setText(p.minimumStock.toString())

                    binding.tvEditStockNote.visibility = View.VISIBLE
                    binding.tvEditStockNote.text = "Current Stock: $initialStockCount ${p.unit}. You can edit stock directly or use Stock In / Adjustment."

                    updatePhotoPreview()
                    updateLiveInventoryTotal()
                } else {
                    Toast.makeText(this@AddEditProductActivity, "Product not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun setupFormulaWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateLiveInventoryTotal()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etTotalQuantity.addTextChangedListener(watcher)
        binding.etPurchasePrice.addTextChangedListener(watcher)
    }

    private fun updateLiveInventoryTotal() {
        val qty = binding.etTotalQuantity.text.toString().toIntOrNull() ?: 0
        val cost = binding.etPurchasePrice.text.toString().toDoubleOrNull() ?: 0.0
        val total = qty * cost
        binding.tvTotalInventoryValue.text = Formatters.formatCurrency(total)
        binding.tvTotalFormula.text = "$qty units × ${Formatters.formatCurrency(cost)} = ${Formatters.formatCurrency(total)}"
    }

    private fun saveOrUpdateProduct() {
        val id = binding.etProductId.text.toString().trim()
        val name = binding.etProductName.text.toString().trim()
        val brand = binding.etBrand.text.toString().trim().ifBlank { "Generic" }
        val category = binding.etCategory.text.toString().trim().ifBlank { "Supplement" }
        val variant = binding.etVariant.text.toString().trim().ifBlank { "Standard" }
        val unit = binding.etUnit.text.toString().trim().ifBlank { "Unit" }
        val sellingPrice = binding.etSellingPrice.text.toString().toDoubleOrNull()
        val purchasePrice = binding.etPurchasePrice.text.toString().toDoubleOrNull() ?: 0.0
        val totalQuantity = binding.etTotalQuantity.text.toString().toIntOrNull() ?: 0
        val minStock = binding.etMinimumStock.text.toString().toIntOrNull() ?: 5

        if (id.isEmpty()) {
            Toast.makeText(this, "Product ID/SKU is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (name.isEmpty()) {
            Toast.makeText(this, "Product Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        if (sellingPrice == null || sellingPrice <= 0) {
            Toast.makeText(this, "Please enter a valid selling price", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var finalImageUri = currentImageUri
            if (!currentImageUri.isNullOrBlank() && !currentImageUri!!.startsWith("http")) {
                val uploadedUrl = com.ironparadise.gymcrm.data.network.CloudSyncManager.uploadPhoto(applicationContext, currentImageUri!!)
                if (!uploadedUrl.isNullOrBlank()) {
                    finalImageUri = uploadedUrl
                }
            }

            if (isEditMode) {
                val existing = existingProduct
                val updated = ProductEntity(
                    id = id,
                    name = name,
                    brand = brand,
                    category = category,
                    variant = variant,
                    unit = unit,
                    sellingPrice = sellingPrice,
                    purchasePrice = purchasePrice,
                    minimumStock = minStock,
                    status = existing?.status ?: "ACTIVE",
                    imageUri = finalImageUri,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                app.repository.updateProduct(updated)
                com.ironparadise.gymcrm.data.network.CloudSyncManager.pushProduct(applicationContext, updated)

                if (totalQuantity != initialStockCount) {
                    try {
                        app.repository.recordStockAdjustment(
                            productId = id,
                            physicalCount = totalQuantity,
                            reason = "Direct Stock Edit in Supplement Master",
                            notes = "Stock adjusted from $initialStockCount to $totalQuantity",
                            user = app.sessionManager.userName
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddEditProductActivity, "Supplement & stock updated successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                val product = ProductEntity(
                    id = id,
                    name = name,
                    brand = brand,
                    category = category,
                    variant = variant,
                    unit = unit,
                    sellingPrice = sellingPrice,
                    purchasePrice = purchasePrice,
                    minimumStock = minStock,
                    imageUri = finalImageUri
                )
                app.repository.addProduct(product, initialStock = totalQuantity)
                com.ironparadise.gymcrm.data.network.CloudSyncManager.pushProduct(applicationContext, product, initialStock = totalQuantity)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddEditProductActivity, "Product saved with $totalQuantity units opening stock", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun confirmDeleteProduct() {
        val prodId = editProductId ?: return
        val prodName = binding.etProductName.text.toString().trim().ifBlank { prodId }

        AlertDialog.Builder(this)
            .setTitle("Remove Supplement")
            .setMessage("Are you sure you want to completely remove \"$prodName\" from the catalog? This will delete the product, its stock ledger entries, and stored image.")
            .setPositiveButton("Remove Completely") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    app.repository.deleteProduct(prodId)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddEditProductActivity, "Supplement removed successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
