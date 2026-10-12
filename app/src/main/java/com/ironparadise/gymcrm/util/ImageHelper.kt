package com.ironparadise.gymcrm.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.core.content.FileProvider
import android.media.ExifInterface
import com.ironparadise.gymcrm.R
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.Executors

object ImageHelper {

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun createTempCameraUri(context: Context): Pair<Uri, File> {
        val cacheFolder = File(context.cacheDir, "camera_images")
        if (!cacheFolder.exists()) cacheFolder.mkdirs()
        val tempFile = File(cacheFolder, "camera_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return Pair(uri, tempFile)
    }

    fun saveImageToInternalStorage(context: Context, sourceUri: Uri, productId: String): String? {
        return try {
            val imagesFolder = File(context.filesDir, "supplement_images")
            if (!imagesFolder.exists()) imagesFolder.mkdirs()

            val sanitizedId = productId.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "prod" }
            val destFile = File(imagesFolder, "supp_${sanitizedId}_${System.currentTimeMillis()}.jpg")

            var inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val maxDimension = 1280
            var sampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / sampleSize) >= maxDimension && (halfWidth / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            inputStream = context.contentResolver.openInputStream(sourceUri)
            var bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()

            if (bitmap != null) {
                // Correct orientation from EXIF if needed
                try {
                    val exifStream = context.contentResolver.openInputStream(sourceUri)
                    if (exifStream != null) {
                        val exif = ExifInterface(exifStream)
                        val orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                        exifStream.close()
                        bitmap = rotateBitmapIfNeeded(bitmap, orientation)
                    }
                } catch (e: Exception) {
                    // ignore exif errors
                }

                val finalBitmap = bitmap ?: return null
                FileOutputStream(destFile).use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.flush()
                }
                destFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: Throwable) {
            bitmap
        }
    }

    fun loadImage(
        imageView: ImageView,
        pathOrUri: String?,
        placeholderResId: Int = R.drawable.ic_supplement_jar
    ) {
        if (pathOrUri.isNullOrBlank()) {
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageView.setImageResource(placeholderResId)
            imageView.tag = null
            return
        }

        val file = File(pathOrUri)
        if (!file.exists()) {
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageView.setImageResource(placeholderResId)
            imageView.tag = null
            return
        }

        imageView.tag = pathOrUri

        executor.execute {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)

                val reqSize = 400
                var inSampleSize = 1
                if (opts.outHeight > reqSize || opts.outWidth > reqSize) {
                    val halfHeight = opts.outHeight / 2
                    val halfWidth = opts.outWidth / 2
                    while ((halfHeight / inSampleSize) >= reqSize && (halfWidth / inSampleSize) >= reqSize) {
                        inSampleSize *= 2
                    }
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)

                mainHandler.post {
                    if (imageView.tag == pathOrUri) {
                        if (bitmap != null) {
                            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                            imageView.setImageBitmap(bitmap)
                        } else {
                            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                            imageView.setImageResource(placeholderResId)
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (imageView.tag == pathOrUri) {
                        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        imageView.setImageResource(placeholderResId)
                    }
                }
            }
        }
    }

    fun deleteImageFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
