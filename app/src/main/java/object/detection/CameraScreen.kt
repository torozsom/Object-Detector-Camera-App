package `object`.detection

import android.Manifest
import android.annotation.SuppressLint
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    // Kamera engedély kezelése
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Detektált objektumok állapota
    var detectedObjects by remember { mutableStateOf<List<DetectedObject>>(emptyList()) }

    // Kontextus és életciklus
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Kamera manager
    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onObjectsDetected = { objects ->
                detectedObjects = objects
            }
        )
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            cameraManager.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            cameraPermissionState.status.isGranted -> {
                // Kamera előnézet
                CameraPreview(
                    cameraManager = cameraManager,
                    modifier = Modifier.fillMaxSize()
                )

                // Objektum dobozok rajzolása
                ObjectBoundingBoxes(
                    objects = detectedObjects,
                    modifier = Modifier.fillMaxSize()
                )

                // Detektált objektumok listája
                DetectionResults(
                    objects = detectedObjects,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            else -> {
                // Engedély kérése
                PermissionRequest(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }
        }
    }
}

@Composable
fun CameraPreview(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).also { previewView ->
                cameraManager.startCamera(previewView)
            }
        },
        modifier = modifier
    )
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ObjectBoundingBoxes(
    objects: List<DetectedObject>,
    modifier: Modifier = Modifier
) {
    // BoxWithConstraints használatával megkapjuk a Canvas tényleges méretét
    BoxWithConstraints(modifier = modifier) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            objects.forEach { obj ->
                // Koordináta transzformáció
                val scaledBox = obj.getScaledBoundingBox(
                    screenWidth = screenWidth,
                    screenHeight = screenHeight
                )

                // Doboz rajzolása
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(
                        scaledBox.left,
                        scaledBox.top
                    ),
                    size = Size(
                        scaledBox.width(),
                        scaledBox.height()
                    ),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun DetectionResults(
    objects: List<DetectedObject>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Detektált objektumok",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (objects.isEmpty()) {
                Text(
                    text = "Nincs detektált objektum",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else {
                objects.take(5).forEach { obj ->
                    ObjectItem(obj)
                }
            }
        }
    }
}

@Composable
fun ObjectItem(obj: DetectedObject) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = obj.label,
            color = Color.White,
            fontSize = 14.sp
        )
        Text(
            text = "${(obj.confidence * 100).toInt()}%",
            color = Color.Green,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PermissionRequest(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = "Camera"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Kamera engedély szükséges",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Az objektum detektáláshoz szükségünk van a kamera használatára.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
        ) {
            Text("Engedély megadása")
        }
    }
}