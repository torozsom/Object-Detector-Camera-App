### Object Detector Camera App (Android)

Real-time object detection Android app built with CameraX, ML Kit, and Jetpack Compose. 

The app opens the camera preview, runs on-device object detection on each frame (optionally via a custom TensorFlow Lite model), and overlays bounding boxes and labels on top of the live camera feed. It is designed as a clean, modern sample for showcasing real-time computer vision on Android.

---

### Features

- **Real-time camera preview** using `CameraX` (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`).
- **On‑device object detection** powered by **Google ML Kit**:
  - Uses a **custom TensorFlow Lite model** from `app/src/main/assets/model.tflite` when present.
  - Automatically falls back to **ML Kit base object detector** if the custom model is not available.
- **Multiple object tracking** with per-object labels and confidence scores.
- **Live overlay** of bounding boxes on top of the camera preview.
- **Detection results panel** showing a list of detected objects.
- **Runtime permission handling** for the camera using `accompanist-permissions`.
- **Modern UI** with **Jetpack Compose** and Material 3.

---

### Tech Stack

**Platform & Language**
- Kotlin
- Minimum SDK: **24**
- Target / Compile SDK: **36**

**UI**
- Jetpack Compose
- Material 3
- Material Icons (extended)

**Camera & Vision**
- CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)
- Google ML Kit Object Detection (`object-detection`, `object-detection-custom`)
- Optional local TensorFlow Lite model (`model.tflite` in the `assets` folder)

**Other Libraries**
- `accompanist-permissions` for runtime permissions
- AndroidX Lifecycle

---

### How It Works

At a high level:

1. **App entry point** – `MainActivity` sets the content and displays `CameraScreen()` using the `ObjectDetectionTheme`.
2. **Camera screen** – `CameraScreen` (Jetpack Compose) is responsible for:
   - Requesting and handling the **CAMERA** permission.
   - Creating and remembering a `CameraManager` instance.
   - Displaying the camera preview (`CameraPreview`).
   - Drawing bounding boxes over detected objects (`ObjectBoundingBoxes`).
   - Rendering a bottom sheet/card with a list of detected objects (`DetectionResults`).
3. **Camera & detection pipeline** – `CameraManager` encapsulates CameraX and ML Kit logic:
   - On initialization, it tries to load a local model `assets/model.tflite` using `ML Kit LocalModel`.
   - If successful, it configures `CustomObjectDetectorOptions` for **stream mode**, classification, and multiple objects.
   - If not, it falls back to the default `ObjectDetectorOptions` (base on‑device detector).
   - It starts the camera with `ProcessCameraProvider` and binds:
     - `Preview` (for camera preview on the `PreviewView`).
     - `ImageAnalysis` analyzer, which processes frames in a background executor.
   - For each frame, it converts the image to `InputImage`, runs detection, and maps the results into a list of `DetectedObject` instances.
4. **Data model** – `DetectedObject` holds:
   - `label`
   - `confidence`
   - `boundingBox` (from ML Kit)
   - `imageWidth`, `imageHeight`, `rotation`
   - A helper function `getScaledBoundingBox(screenWidth, screenHeight)` to map camera coordinates to screen coordinates for drawing.

All detection runs **on-device**, so frames never leave the device.

---

### Project Structure

```text
Object-Detector-Camera-App/
├─ app/
│  ├─ src/
│  │  ├─ main/
│  │  │  ├─ AndroidManifest.xml
│  │  │  ├─ assets/
│  │  │  │  └─ model.tflite          # Optional custom object detection model
│  │  │  ├─ java/object/detection/
│  │  │  │  ├─ MainActivity.kt       # Entry point, sets Compose content
│  │  │  │  ├─ CameraScreen.kt       # Compose UI, permissions, overlays
│  │  │  │  └─ CameraManager.kt      # CameraX + ML Kit pipeline
│  │  │  └─ res/                     # Icons, themes, strings
│  └─ build.gradle.kts               # Module configuration
├─ build.gradle.kts                  # Root Gradle settings
└─ settings.gradle.kts
```

---

### Getting Started

#### Prerequisites

- Android Studio **Hedgehog** or newer.
- Android Gradle Plugin compatible with the included Gradle wrapper (see `gradle-wrapper.properties`).
- Android device or emulator with **Camera** support (physical device strongly recommended for camera apps).

#### Clone the Repository

```bash
git clone https://github.com/<your-username>/Object-Detector-Camera-App.git
cd Object-Detector-Camera-App
```

#### Open in Android Studio

1. Open **Android Studio**.
2. Select **Open an Existing Project**.
3. Choose the cloned `Object-Detector-Camera-App` directory.
4. Let Gradle sync the project and download dependencies.

#### Run the App

1. Connect an Android device with **USB debugging** enabled, or start an emulator with camera support.
2. In Android Studio, select the desired run configuration (usually `app`).
3. Click **Run ▶**.
4. On first launch, grant the **Camera** permission when prompted.

You should see a live camera preview with bounding boxes appearing around detected objects.

---

### Using a Custom Model (Optional)

By default, the app looks for a custom TFLite model at `app/src/main/assets/model.tflite`.

- If **present**, this model is loaded as a **LocalModel** and used via `CustomObjectDetectorOptions`.
- If **absent** or if loading fails, the app logs the error and falls back to the **base ML Kit object detector**.

To plug in your own custom object detection model:

1. Train / obtain a TensorFlow Lite object detection model compatible with ML Kit.
2. Place the `.tflite` file in `app/src/main/assets/` and rename it to `model.tflite`.
3. Adjust label handling (if needed) in `CameraManager.getObjectLabel()` to map model output labels to user‑friendly names.

> Note: Ensure your model is configured for on-device execution and compatible with ML Kit's custom object detection APIs.

---

### Screens & UX

- **Camera screen**
  - Fullscreen camera preview.
  - Overlay with colored rectangles indicating detected objects.
  - Bottom card listing each detected object's label and confidence.
  - Permission request UI if the camera permission has not been granted.

The UI is built entirely with Jetpack Compose, making it easy to customize theming, layout, and animations.

---

### Permissions

The app requires the following permission:

- `android.permission.CAMERA`

Permission is requested at runtime using `accompanist-permissions`. If the user denies the permission, a dedicated permission request UI is displayed guiding them to grant access.

You can find and customize this logic in `CameraScreen.kt`.
