package `object`.detection

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onObjectsDetected: (List<DetectedObject>) -> Unit
) {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var objectDetector: ObjectDetector
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Kamera kép méretei
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    init {
        setupObjectDetector()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private val TAG = "CameraManager"

    private fun setupObjectDetector() {
        val assetModelPath = "model.tflite"
        val customDetector = try {
            if (assetExists(assetModelPath)) {
                Log.i(
                    TAG,
                    "Custom model found at assets/$assetModelPath, initializing ML Kit custom detector."
                )
                val localModel = LocalModel.Builder()
                    .setAssetFilePath(assetModelPath)
                    .build()

                val customOptions = CustomObjectDetectorOptions.Builder(localModel)
                    .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                    .enableClassification()
                    .setMaxPerObjectLabelCount(3)
                    .enableMultipleObjects()
                    .build()

                ObjectDetection.getClient(customOptions)
            } else null
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load custom model: ${t.message}", t)
            null
        }

        objectDetector = if (customDetector != null) {
            Log.i(TAG, "Using custom TFLite object detector.")
            customDetector
        } else {
            Log.i(TAG, "Using base on-device ML Kit object detector.")
            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .enableMultipleObjects()
                .build()
            ObjectDetection.getClient(options)
        }
    }

    private fun assetExists(fileName: String): Boolean {
        return try {
            context.assets.open(fileName).use { }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Preview beállítása
            preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            // Képelemző beállítása
            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ObjectDetectionAnalyzer())
                }

            // Kamera választása (hátsó kamera)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Előző kötések megszüntetése
                cameraProvider?.unbindAll()

                // Kamera kötése az életciklushoz
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

            } catch (exc: Exception) {
                exc.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(context))
    }

    private inner class ObjectDetectionAnalyzer : ImageAnalysis.Analyzer {
        @ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                // Kép méretének mentése
                imageWidth = mediaImage.width
                imageHeight = mediaImage.height

                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                // Objektum detektálás végrehajtása
                objectDetector.process(image)
                    .addOnSuccessListener { objects ->
                        // Detektált objektumok feldolgozása
                        val detectedObjects = objects.map { obj ->
                            DetectedObject(
                                label = getObjectLabel(obj.labels.firstOrNull()?.text),
                                confidence = obj.labels.firstOrNull()?.confidence ?: 0f,
                                boundingBox = obj.boundingBox,
                                imageWidth = imageWidth,
                                imageHeight = imageHeight,
                                rotation = imageProxy.imageInfo.rotationDegrees
                            )
                        }
                        onObjectsDetected(detectedObjects)
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun getObjectLabel(label: String?): String {
        return when (label) {
            "Fashion good" -> "Ruházat"
            "Food" -> "Étel"
            "Home good" -> "Háztartási tárgy"
            "Place" -> "Hely"
            "Plant" -> "Növény"
            else -> label ?: "Ismeretlen"
        }
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        objectDetector.close()
    }
}

// Detektált objektum adatszerkezet
data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: Rect,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotation: Int
) {
    fun getScaledBoundingBox(screenWidth: Float, screenHeight: Float): RectF {
        // Rotáció figyelembevétele
        val sourceWidth = if (rotation == 90 || rotation == 270) imageHeight else imageWidth
        val sourceHeight = if (rotation == 90 || rotation == 270) imageWidth else imageHeight

        // Skálázási faktor számítása (PreviewView FILL_CENTER móddal)
        val widthRatio = screenWidth / sourceWidth
        val heightRatio = screenHeight / sourceHeight
        val scaleFactor = maxOf(widthRatio, heightRatio)

        // Eltolás számítása (center)
        val scaledWidth = sourceWidth * scaleFactor
        val scaledHeight = sourceHeight * scaleFactor
        val offsetX = (screenWidth - scaledWidth) / 2f
        val offsetY = (screenHeight - scaledHeight) / 2f

        // Koordináták átszámítása
        return RectF(
            boundingBox.left * scaleFactor + offsetX,
            boundingBox.top * scaleFactor + offsetY,
            boundingBox.right * scaleFactor + offsetX,
            boundingBox.bottom * scaleFactor + offsetY
        )
    }
}