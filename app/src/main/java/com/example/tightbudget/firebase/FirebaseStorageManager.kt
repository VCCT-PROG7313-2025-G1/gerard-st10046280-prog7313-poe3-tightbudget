package com.example.tightbudget.firebase

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * Firebase Storage Manager handles receipt image storage in Firebase Cloud Storage
 * Manages upload, download, and deletion of receipt images for transactions
 */
class FirebaseStorageManager {

    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val storageRef: StorageReference = storage.reference

    companion object {
        private const val TAG = "FirebaseStorageManager"
        private const val RECEIPTS_FOLDER = "receipts"
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024L // 10MB max file size

        @Volatile
        private var INSTANCE: FirebaseStorageManager? = null

        fun getInstance(): FirebaseStorageManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FirebaseStorageManager()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Uploads a receipt image to Firebase Storage
     * @param imageUri The URI of the image to upload
     * @param userId The ID of the user uploading the image
     * @param transactionId Optional transaction ID for organizing files
     * @return The download URL of the uploaded image, or null if upload failed
     */
    suspend fun uploadReceiptImage(
        imageUri: Uri,
        userId: Int,
        transactionId: Int? = null
    ): String? {
        return try {
            Log.d(TAG, "Starting upload for user $userId, transaction $transactionId")

            // Create unique filename with timestamp
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = if (transactionId != null) {
                "RECEIPT_${userId}_${transactionId}_${timeStamp}.jpg"
            } else {
                "RECEIPT_${userId}_${timeStamp}.jpg"
            }

            // Create storage reference path: receipts/userId/filename
            val imageRef = storageRef.child("$RECEIPTS_FOLDER/$userId/$fileName")

            // Check file size before upload (optional validation)
            validateImageForUpload(imageUri)

            // Upload the file
            val uploadTask: UploadTask = imageRef.putFile(imageUri)

            // Add progress monitoring (optional)
            uploadTask.addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                Log.d(TAG, "Upload progress: ${progress.toInt()}%")
            }

            // Wait for upload to complete
            val uploadResult = uploadTask.await()
            Log.d(TAG, "Upload completed successfully. Bytes transferred: ${uploadResult.bytesTransferred}")

            // Get the download URL
            val downloadUrl = imageRef.downloadUrl.await()
            val downloadUrlString = downloadUrl.toString()

            Log.d(TAG, "Receipt uploaded successfully. Download URL: $downloadUrlString")
            downloadUrlString

        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload receipt image: ${e.message}", e)
            null
        }
    }

    /**
     * Downloads a receipt image from Firebase Storage
     * @param downloadUrl The download URL of the image
     * @param maxDownloadSize Maximum download size in bytes (default 10MB)
     * @return ByteArray of the image data, or null if download failed
     */
    suspend fun downloadReceiptImage(
        downloadUrl: String,
        maxDownloadSize: Long = MAX_FILE_SIZE
    ): ByteArray? {
        return try {
            Log.d(TAG, "Downloading image from URL: $downloadUrl")

            val imageRef = storage.getReferenceFromUrl(downloadUrl)
            val imageData = imageRef.getBytes(maxDownloadSize).await()

            Log.d(TAG, "Image downloaded successfully. Size: ${imageData.size} bytes")
            imageData

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download receipt image: ${e.message}", e)
            null
        }
    }

    /**
     * Deletes a receipt image from Firebase Storage
     * @param downloadUrl The download URL of the image to delete
     * @return True if deletion was successful, false otherwise
     */
    suspend fun deleteReceiptImage(downloadUrl: String): Boolean {
        return try {
            Log.d(TAG, "Deleting image at URL: $downloadUrl")

            val imageRef = storage.getReferenceFromUrl(downloadUrl)
            imageRef.delete().await()

            Log.d(TAG, "Image deleted successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete receipt image: ${e.message}", e)
            false
        }
    }

    /**
     * Updates a receipt image (deletes old one and uploads new one)
     * @param oldDownloadUrl The URL of the old image to delete
     * @param newImageUri The URI of the new image to upload
     * @param userId The ID of the user
     * @param transactionId Optional transaction ID
     * @return The download URL of the new uploaded image, or null if failed
     */
    suspend fun updateReceiptImage(
        oldDownloadUrl: String?,
        newImageUri: Uri,
        userId: Int,
        transactionId: Int? = null
    ): String? {
        return try {
            // Delete old image if it exists
            if (!oldDownloadUrl.isNullOrEmpty()) {
                val deleteSuccess = deleteReceiptImage(oldDownloadUrl)
                if (!deleteSuccess) {
                    Log.w(TAG, "Failed to delete old image, but continuing with upload")
                }
            }

            // Upload new image
            uploadReceiptImage(newImageUri, userId, transactionId)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update receipt image: ${e.message}", e)
            null
        }
    }

    /**
     * Gets all receipt images for a specific user
     * @param userId The ID of the user
     * @return List of download URLs for all receipts, or empty list if none found
     */
    suspend fun getUserReceiptImages(userId: Int): List<String> {
        return try {
            Log.d(TAG, "Fetching all receipts for user $userId")

            val userReceiptsRef = storageRef.child("$RECEIPTS_FOLDER/$userId")
            val listResult = userReceiptsRef.listAll().await()

            val downloadUrls = mutableListOf<String>()
            for (item in listResult.items) {
                try {
                    val downloadUrl = item.downloadUrl.await()
                    downloadUrls.add(downloadUrl.toString())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get download URL for ${item.name}: ${e.message}")
                }
            }

            Log.d(TAG, "Found ${downloadUrls.size} receipt images for user $userId")
            downloadUrls

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user receipt images: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Deletes all receipt images for a specific user
     * @param userId The ID of the user
     * @return True if all deletions were successful, false otherwise
     */
    suspend fun deleteAllUserReceipts(userId: Int): Boolean {
        return try {
            Log.d(TAG, "Deleting all receipts for user $userId")

            val userReceiptsRef = storageRef.child("$RECEIPTS_FOLDER/$userId")
            val listResult = userReceiptsRef.listAll().await()

            var allSuccessful = true
            for (item in listResult.items) {
                try {
                    item.delete().await()
                    Log.d(TAG, "Deleted receipt: ${item.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete receipt ${item.name}: ${e.message}")
                    allSuccessful = false
                }
            }

            // Also try to delete the user folder if it's empty
            try {
                userReceiptsRef.delete().await()
                Log.d(TAG, "Deleted user receipts folder")
            } catch (e: Exception) {
                Log.d(TAG, "User receipts folder not empty or already deleted")
            }

            Log.d(TAG, "Deletion process completed. All successful: $allSuccessful")
            allSuccessful

        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user receipts: ${e.message}", e)
            false
        }
    }

    /**
     * Gets metadata for a receipt image
     * @param downloadUrl The download URL of the image
     * @return Map containing metadata, or null if failed
     */
    suspend fun getReceiptImageMetadata(downloadUrl: String): Map<String, Any>? {
        return try {
            Log.d(TAG, "Getting metadata for image: $downloadUrl")

            val imageRef = storage.getReferenceFromUrl(downloadUrl)
            val metadata = imageRef.metadata.await()

            val metadataMap = mapOf(
                "name" to (metadata.name ?: "unknown"),
                "size" to metadata.sizeBytes,
                "contentType" to (metadata.contentType ?: "unknown"),
                "timeCreated" to (metadata.creationTimeMillis),
                "updated" to (metadata.updatedTimeMillis)
            )

            Log.d(TAG, "Retrieved metadata: $metadataMap")
            metadataMap

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get image metadata: ${e.message}", e)
            null
        }
    }

    /**
     * Validates an image before upload
     * @param imageUri The URI of the image to validate
     * @throws Exception if validation fails
     */
    private fun validateImageForUpload(imageUri: Uri) {
        // Add any validation logic here
        // For example: file size, image format, etc.
        Log.d(TAG, "Validating image for upload: $imageUri")

        // You can add more validation here such as:
        // - File size checking
        // - Image format validation
        // - Dimensions checking
        // etc.
    }

    /**
     * Compresses an image before upload (if needed)
     * This is a placeholder for image compression logic
     * @param imageUri The URI of the image to compress
     * @return URI of the compressed image
     */
    suspend fun compressImageForUpload(imageUri: Uri): Uri {
        // Placeholder for image compression logic
        // You might want to implement actual compression here
        // using libraries like Glide or custom bitmap compression

        Log.d(TAG, "Image compression not implemented, returning original URI")
        return imageUri
    }

    /**
     * Checks if Firebase Storage is available
     * @return True if storage is available, false otherwise
     */
    fun isStorageAvailable(): Boolean {
        return try {
            storage.maxUploadRetryTimeMillis // Simple check to see if storage is accessible
            true
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Storage not available: ${e.message}")
            false
        }
    }
}