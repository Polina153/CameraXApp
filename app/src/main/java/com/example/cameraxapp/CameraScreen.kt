package com.example.cameraxapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.function.Consumer

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cacheDir = context.cacheDir

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Разрешите доступ к камере",
                fontSize = 18.sp
            )
        }
        return
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                LifecycleCameraController.IMAGE_CAPTURE or
                        LifecycleCameraController.VIDEO_CAPTURE
            )
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    LaunchedEffect(cameraController) {
        cameraController.bindToLifecycle(lifecycleOwner)
    }

    var isRecording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewView(context).apply {
                    controller = cameraController
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
        ) {
            Button(
                onClick = {
                    if (isRecording) {
                        activeRecording?.stop()
                    } else {
                        takePhoto(context = context, cameraController)
                    }
                }
            ) {
                Icon(
                    painter = painterResource(
                        if (isRecording) R.drawable.stop
                        else R.drawable.camera_24
                    ),
                    contentDescription = if (isRecording) "Stop" else "Take Photo"
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { toggleCamera(cameraController) }) {
                    Icon(
                        painter = painterResource(R.drawable.cameraswitch_24),
                        contentDescription = "Toggle Cameras"
                    )
                }

                Switch(
                    checked = isRecording,
                    onCheckedChange = { checked ->
                        if (checked) {
                            activeRecording = startRecording(
                                context = context,
                                controller = cameraController,
                                recorder = recorder,
                                cacheDir = cacheDir
                            )
                            isRecording = true
                        } else {
                            activeRecording?.stop()
                            activeRecording = null
                            isRecording = false
                        }
                    }
                )

                IconButton(onClick = { toggleFlash(cameraController) }) {
                    Icon(
                        painter = painterResource(R.drawable.flash_on_24),
                        contentDescription = "Flash"
                    )
                }
            }
        }
    }
}

private fun takePhoto(context: Context, controller: LifecycleCameraController) {
    val outputFile = File.createTempFile(
        "photo_",
        ".jpg",
        context.cacheDir
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

    controller.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                Log.d("Фото", "Фото сохранено: ${outputFileResults.savedUri}")
            }

            override fun onError(exception: ImageCaptureException) {
                Log.d("Фото", "Ошибка фото: ${exception.message}")
            }
        }
    )
}

private fun startRecording(
    context: Context,
    controller: LifecycleCameraController,
    recorder: Recorder,
    cacheDir: File
): Recording {
    val videoFile = File.createTempFile("video_", ".mp4", cacheDir)

    val outputOptions = FileOutputOptions.Builder(videoFile).build()
    val audioConfig = AudioConfig.AUDIO_DISABLED// или AudioConfig.AUDIO_DISABLED
    val executor = ContextCompat.getMainExecutor(context)

    return controller.startRecording(
        outputOptions,
        audioConfig,
        executor,
        object : Consumer<VideoRecordEvent>, androidx.core.util.Consumer<VideoRecordEvent> {
            override fun accept(event: VideoRecordEvent) {
                when (event) {
                    is VideoRecordEvent.Finalize -> {
                        if (event.hasError()) {
                            Log.d("Видео", "Ошибка записи: ${event.error}")
                        } else {
                            println(
                                Log.d("Видео", "Видео сохранено: ${event.outputResults.outputUri}")
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
    )
}

private fun toggleCamera(controller: LifecycleCameraController) {
    controller.cameraSelector =
        if (controller.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
}

private fun toggleFlash(controller: LifecycleCameraController) {
    controller.enableTorch(true)
}
