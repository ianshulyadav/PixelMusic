package com.unshoo.pixelmusic.utils

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

/**
 * Represents the type of storage device
 */
enum class StorageType {
    INTERNAL,
    SD_CARD,
    USB
}

/**
 * Data class representing storage information
 */
data class StorageInfo(
    val path: File,
    val displayName: String,
    val storageType: StorageType,
    val isRemovable: Boolean
)

/**
 * Utility object for detecting and managing storage devices
 */
object StorageUtils {

    /**
     * Get all available storage devices (Internal Storage, SD Card, USB OTG)
     * @param context Application context
     * @return List of available StorageInfo objects
     */
    fun getAvailableStorages(context: Context): List<StorageInfo> {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumes = storageManager.storageVolumes
        val storages = mutableListOf<StorageInfo>()

        var usbCounter = 0

        for (volume in storageVolumes) {
            if (volume.state != Environment.MEDIA_MOUNTED) continue

            val path = getVolumePath(volume) ?: continue

            val storageType = determineStorageType(volume)
            val displayName = when (storageType) {
                StorageType.INTERNAL -> "Internal Storage"
                StorageType.SD_CARD -> "SD Card"
                StorageType.USB -> {
                    usbCounter++
                    if (usbCounter > 1) "USB Storage $usbCounter" else "USB Storage"
                }
            }

            storages.add(
                StorageInfo(
                    path = path,
                    displayName = displayName,
                    storageType = storageType,
                    isRemovable = volume.isRemovable
                )
            )
        }

        return storages.sortedBy { it.storageType.ordinal }
    }

    /**
     * Get the file path for a StorageVolume
     */
    private fun getVolumePath(volume: StorageVolume): File? {
        return try {
            volume.directory
        } catch (e: Exception) {
            try {
                val getPath = volume.javaClass.getMethod("getPath")
                val path = getPath.invoke(volume) as? String
                path?.let { File(it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Determine the storage type based on StorageVolume properties
     */
    private fun determineStorageType(volume: StorageVolume): StorageType {
        if (volume.isPrimary) {
            return StorageType.INTERNAL
        }

        if (!volume.isRemovable) {
            return StorageType.INTERNAL
        }

        val description = volume.getDescription(null)?.lowercase() ?: ""
        
        return when {
            description.contains("usb") -> StorageType.USB
            description.contains("otg") -> StorageType.USB
            description.contains("sd") -> StorageType.SD_CARD
            else -> StorageType.SD_CARD
        }
    }

    fun getSdCardStorage(context: Context): StorageInfo? {
        val storages = getAvailableStorages(context)
        return storages.firstOrNull { it.storageType == StorageType.SD_CARD }
            ?: storages.firstOrNull { it.isRemovable && it.storageType != StorageType.INTERNAL }
    }

    /**
     * Check if any external storage (SD Card or USB) is available
     */
    fun hasExternalStorage(context: Context): Boolean {
        return getAvailableStorages(context).any { it.storageType != StorageType.INTERNAL }
    }

    /**
     * Get internal storage only
     */
    fun getInternalStorage(): StorageInfo {
        return StorageInfo(
            path = Environment.getExternalStorageDirectory(),
            displayName = "Internal Storage",
            storageType = StorageType.INTERNAL,
            isRemovable = false
        )
    }
}
