package com.example.util

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * Result data holder when importing a photo file locally.
 */
data class PhotoImportResult(
    val localUri: String,
    val contentHash: String,
    val takenTimestamp: Long?,
    val width: Int?,
    val height: Int?,
    val location: String?
)

/**
 * Local-first utility to persist family photos and individual photo memories
 * in the application's internal files directory.
 * This ensures photos remain accessible completely offline and survive app restarts
 * without expiring URI permissions or needing internet/cloud connectivity.
 */
object PhotoStorageHelper {
    private const val COVER_DIR_NAME = "family_photos"
    private const val MEMORY_DIR_NAME = "person_memories"

    fun savePhotoFromUri(context: Context, sourceUri: Uri, personId: String): String? {
        return try {
            val photosDir = File(context.filesDir, COVER_DIR_NAME).apply {
                if (!exists()) {
                    mkdirs()
                }
            }
            val fileName = "cover_${personId}_${System.currentTimeMillis()}.jpg"
            val destinationFile = File(photosDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copies a photo from a picked Uri into the local memories folder, computes a SHA-256
     * hash for duplicate detection, and extracts available EXIF metadata (capture timestamp,
     * dimensions, and location if present).
     */
    fun savePhotoMemoryFile(context: Context, sourceUri: Uri, personId: String): PhotoImportResult? {
        return try {
            val memoriesDir = File(context.filesDir, MEMORY_DIR_NAME).apply {
                if (!exists()) {
                    mkdirs()
                }
            }
            val uniqueId = UUID.randomUUID().toString()
            val destinationFile = File(memoriesDir, "photo_${personId}_$uniqueId.jpg")

            val digest = MessageDigest.getInstance("SHA-256")
            val copied = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                    }
                }
                true
            } ?: false

            if (!copied || !destinationFile.exists() || destinationFile.length() == 0L) {
                if (destinationFile.exists()) destinationFile.delete()
                return null
            }

            val hashBytes = digest.digest()
            val contentHash = hashBytes.joinToString("") { "%02x".format(it) }

            var takenTimestamp: Long? = null
            var width: Int? = null
            var height: Int? = null
            var location: String? = null

            // Inspect standard image metadata locally
            try {
                val exif = ExifInterface(destinationFile.absolutePath)
                val dateString = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

                if (!dateString.isNullOrBlank()) {
                    val exifFormats = listOf(
                        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US),
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    )
                    for (fmt in exifFormats) {
                        try {
                            val parsed = fmt.parse(dateString)
                            if (parsed != null) {
                                takenTimestamp = parsed.time
                                break
                            }
                        } catch (_: Exception) {}
                    }
                }

                val exifWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                val exifHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                if (exifWidth > 0 && exifHeight > 0) {
                    width = exifWidth
                    height = exifHeight
                }

                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    if (latLong[0] != 0.0f || latLong[1] != 0.0f) {
                        location = "%.4f, %.4f".format(Locale.US, latLong[0], latLong[1])
                    }
                }
            } catch (e: Exception) {
                // Non-critical EXIF reading exception
            }

            // Fallback for dimensions
            if (width == null || height == null) {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(destinationFile.absolutePath, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        width = options.outWidth
                        height = options.outHeight
                    }
                } catch (_: Exception) {}
            }

            PhotoImportResult(
                localUri = destinationFile.absolutePath,
                contentHash = contentHash,
                takenTimestamp = takenTimestamp,
                width = width,
                height = height,
                location = location
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Resolves an image path, URI, or old file:/ scheme into a valid File or Uri
     * for Coil / image loading. Ensures images display reliably offline, after app restarts,
     * and across all screens without appearing blank.
     */
    fun resolveImageModel(context: Context, uriString: String?): Any? {
        if (uriString.isNullOrBlank()) return null
        return try {
            if (uriString.startsWith("content://")) {
                return Uri.parse(uriString)
            }
            val cleanPath = when {
                uriString.startsWith("file://") -> uriString.removePrefix("file://")
                uriString.startsWith("file:/") -> uriString.removePrefix("file:")
                uriString.startsWith("file:") -> uriString.removePrefix("file:")
                else -> uriString
            }
            val directFile = File(cleanPath)
            if (directFile.exists() && directFile.length() > 0L) {
                return directFile
            }

            // In case of app reinstall/container path difference, search in app-internal directories by filename
            val fileName = directFile.name
            val memoryDir = File(context.filesDir, MEMORY_DIR_NAME)
            val candidateMemoryFile = File(memoryDir, fileName)
            if (candidateMemoryFile.exists() && candidateMemoryFile.length() > 0L) {
                return candidateMemoryFile
            }

            val coverDir = File(context.filesDir, COVER_DIR_NAME)
            val candidateCoverFile = File(coverDir, fileName)
            if (candidateCoverFile.exists() && candidateCoverFile.length() > 0L) {
                return candidateCoverFile
            }

            // Fallback: try parsing Uri
            val parsed = Uri.parse(uriString)
            val p = parsed.path
            if (p != null) {
                val f = File(p)
                if (f.exists() && f.length() > 0L) return f
            }
            directFile
        } catch (e: Exception) {
            uriString
        }
    }

    /**
     * Resolves local photo file for Coil image model.
     */
    fun getExistingPhotoFile(context: Context, uriString: String?): Any? {
        return resolveImageModel(context, uriString)
    }

    fun deletePhoto(fileUriString: String?) {
        if (fileUriString.isNullOrBlank()) return
        try {
            val cleanPath = when {
                fileUriString.startsWith("file://") -> fileUriString.removePrefix("file://")
                fileUriString.startsWith("file:/") -> fileUriString.removePrefix("file:")
                fileUriString.startsWith("file:") -> fileUriString.removePrefix("file:")
                else -> fileUriString
            }
            val file = File(cleanPath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
