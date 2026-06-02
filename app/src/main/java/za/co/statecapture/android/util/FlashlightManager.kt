package za.co.statecapture.android.util

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build

class FlashlightManager(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraId: String? = null

    init {
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hasFlashlight(): Boolean {
        return cameraId != null
    }

    fun toggleFlashlight(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                cameraId?.let {
                    cameraManager.setTorchMode(it, enabled)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
