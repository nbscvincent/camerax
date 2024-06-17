package com.nbscvincent.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberImagePainter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData

@Composable
fun CameraNavHost(photoViewModel: PhotoViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") {
            CameraScreen(navController, photoViewModel)
        }
        composable("photoList") {
            PhotoListScreen(navController, photoViewModel)
        }
    }
}

@Composable
fun PhotoListScreen(navController: NavHostController, photoViewModel: PhotoViewModel) {
    val photoUris by photoViewModel.photoUris.observeAsState(emptyList())
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = { navController.navigate("camera") }) {
            Text(text = "Back to Camera")
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(photoUris) { uri ->
                Image(
                    painter = rememberImagePainter(uri),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable {
                            selectedImageUri = uri
                        },
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    selectedImageUri?.let { uri ->
        ImageDialog(
            uri = uri,
            onDismissRequest = { selectedImageUri = null },
            onSaveClick = {
                saveImageManually(context, uri)
                Toast.makeText(context, "Photo Saved", Toast.LENGTH_SHORT).show()
                selectedImageUri = null
            },
            onDeleteClick = {
                deleteImage(context, uri, photoViewModel)
                Toast.makeText(context, "Photo Deleted", Toast.LENGTH_SHORT).show()
                selectedImageUri = null
            }
        )
    }
}

@Composable
fun ImageDialog(
    uri: Uri,
    onDismissRequest: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "Image") },
        text = {
            Image(
                painter = rememberImagePainter(uri),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(300.dp),
                contentScale = ContentScale.Crop
            )
        },

        confirmButton = {
            Button(onClick = onSaveClick) {
                Text("Save Image")
            }
            Button(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        dismissButton = {

            Button(onClick = onDeleteClick) {
                Text("Delete Image")
            }


        }
    )
}

@Composable
fun CameraScreen(navController: NavHostController, photoViewModel: PhotoViewModel) {
    val lensFacing = CameraSelector.LENS_FACING_BACK
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val preview = Preview.Builder().build()
    val previewView = remember {
        PreviewView(context)
    }
    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
    val imageCapture = remember {
        ImageCapture.Builder().build()
    }
    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
        preview.setSurfaceProvider(previewView.surfaceProvider)
    }
    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
        AndroidView({ previewView }, modifier = Modifier.fillMaxSize())
        Button(onClick = { captureImage(imageCapture, context, navController, photoViewModel) }) {
            Text(text = "Capture Image")
        }
    }
}

private fun captureImage(imageCapture: ImageCapture, context: Context, navController: NavHostController, photoViewModel: PhotoViewModel) {
    val name = "CameraxImage_${System.currentTimeMillis()}.jpeg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
        }
    }
    val outputOptions = ImageCapture.OutputFileOptions
        .Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        .build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val uri = outputFileResults.savedUri
                uri?.let {
                    photoViewModel.addPhotoUri(it)
                    Toast.makeText(context, "Photo Saved", Toast.LENGTH_SHORT).show()
                    navController.navigate("photoList")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                println("Failed $exception")
            }
        })
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }
private fun saveImageManually(context: Context, uri: Uri) {
    val resolver = context.contentResolver
    val inputStream = resolver.openInputStream(uri)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "SavedImage_${System.currentTimeMillis()}.jpeg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SavedImages")
        }
    }
    val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    outputUri?.let { outUri ->
        val outputStream = resolver.openOutputStream(outUri)
        inputStream?.copyTo(outputStream!!)
        outputStream?.close()
        inputStream?.close()
    }
}

private fun deleteImage(context: Context, uri: Uri, photoViewModel: PhotoViewModel) {
    val resolver = context.contentResolver
    resolver.delete(uri, null, null)
    photoViewModel.removePhotoUri(uri)
}

class PhotoViewModel : ViewModel() {
    private val _photoUris = MutableLiveData<List<Uri>>(emptyList())
    val photoUris: LiveData<List<Uri>> = _photoUris

    fun addPhotoUri(uri: Uri) {
        _photoUris.value = _photoUris.value?.plus(uri)
    }

    fun removePhotoUri(uri: Uri) {
        _photoUris.value = _photoUris.value?.filterNot { it == uri }
    }
}
