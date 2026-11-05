package com.example.cameraxapp

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
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
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cacheDir = context.cacheDir
    val scope = rememberCoroutineScope()


    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
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
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Разрешите доступ к камере", fontSize = 18.sp
            )
        }
        return
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                LifecycleCameraController.IMAGE_CAPTURE or LifecycleCameraController.VIDEO_CAPTURE
            )
            videoCaptureQualitySelector = QualitySelector.from(Quality.HD)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    LaunchedEffect(cameraController) {
        cameraController.bindToLifecycle(lifecycleOwner)
    }

    var isRecording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }


    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(), factory = { ctx ->
                PreviewView(ctx).apply {
                    controller = cameraController
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            })

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier
                .align(Alignment.BottomCenter)         // явное позиционирование внизу
                .fillMaxWidth()
                .safeDrawingPadding()                  // учитывает systemBars + displayCutout + ime
                .padding(bottom = 40.dp)               // дополнительный отступ от safe-area
        ) {
            Button(
                onClick = {
                    if (isRecording) {
                        // остановка записи: сброс состояния произойдёт в onFinalize
                        activeRecording?.stop()
                    } else {
                        takePhoto(context = context, cameraController)
                    }
                }) {
                Icon(
                    painter = painterResource(
                        if (isRecording) R.drawable.stop
                        else R.drawable.camera_24
                    ), contentDescription = if (isRecording) "Stop" else "Take Photo"
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { toggleCamera(cameraController) }) {
                    Icon(
                        painter = painterResource(R.drawable.cameraswitch_24),
                        contentDescription = "Toggle Cameras"
                    )
                }

                Switch(
                    checked = isRecording, onCheckedChange = { checked ->
                        if (checked) {
                            activeRecording = startRecording(
                                context = context,
                                controller = cameraController,
                                scope = scope,
                                cacheDir = cacheDir,
                                onFinalize = {
                                    // вызывается CameraX по завершении записи (и по успеху, и по ошибке) — синхронизирует Switch
                                    isRecording = false
                                    activeRecording = null
                                })
                            isRecording = true
                        } else {
                            activeRecording?.stop()
                            activeRecording = null
                            isRecording = false
                        }
                    })

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
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "photo_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/CameraXApp"
            )
        }
    }

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    val options = ImageCapture.OutputFileOptions
        .Builder(context.contentResolver, collection, values)
        .build()

    controller.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                Log.d("Фото", "В галерее: ${outputFileResults.savedUri}")
            }

            override fun onError(exception: ImageCaptureException) {
                Log.d("Фото", "Ошибка: ${exception.message}")
            }
        }
    )
}

private fun startRecording(
    context: Context,
    controller: LifecycleCameraController,
    cacheDir: File,
    onFinalize: () -> Unit,
    scope: CoroutineScope,
): Recording {
    val videoFile = File.createTempFile("video_", ".mp4", cacheDir)

    val outputOptions = FileOutputOptions.Builder(videoFile).build()
    val audioConfig = AudioConfig.AUDIO_DISABLED
    val executor = ContextCompat.getMainExecutor(context)

    return controller.startRecording(
        outputOptions,
        audioConfig,
        executor,
        androidx.core.util.Consumer<VideoRecordEvent> { event ->
            when (event) {
                is VideoRecordEvent.Finalize -> {
                    if (event.hasError()) {
                        Log.d("Видео", "Ошибка записи: ${event.error}")
                        videoFile.delete()
                    } else {
                        Log.d("Видео", "Видео сохранено: ${event.outputResults.outputUri}")
                        scope.launch(Dispatchers.IO) {
                            val published = publishVideoToGallery(context, videoFile)
                            Log.d("Видео", "В галерее: $published")
                        }
                    }
                    onFinalize()
                }

                else -> Unit
            }
        })
}

private fun publishVideoToGallery(context: Context, file: File): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "video_${System.currentTimeMillis()}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/CameraXApp"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val uri = resolver.insert(collection, values) ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: error("openOutputStream вернул null")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }, null, null)
        }
        file.delete()
        uri
    } catch (t: Throwable) {
        Log.d("Видео", "Не удалось опубликовать: ${t.message}")
        resolver.delete(uri, null, null)
        null
    }
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
