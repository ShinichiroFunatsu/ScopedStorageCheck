@file:Suppress("ObjectPropertyName", "NonAsciiCharacters")

package com.example.scopedstorage

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.bumptech.glide.Glide
import com.example.scopedstorage.ui.theme.ScopedStorageCheckTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

private const val LogTag = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScopedStorageCheckTheme {
                val context = LocalContext.current
                var environmentInfo by remember { mutableStateOf(buildEnvironmentInfo(context)) }
                var imageResults by remember { mutableStateOf(emptyList<ImageTestResult>()) }
                val coroutineScope = rememberCoroutineScope()
                val permissionLauncher = rememberPermissionLauncher {
                    environmentInfo = buildEnvironmentInfo(context)
                    logPermissionStates(environmentInfo)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        environmentInfo = environmentInfo,
                        imageResults = imageResults,
                        onRequestPermission = {
                            val requestPermissions = requiredImagePerms().toTypedArray()
                            permissionLauncher.launch(requestPermissions)
                        },
                        onExecuteImageSearch = {
                            coroutineScope.launch {
                                // Glide の submit().get() をメインスレッドで実行すると例外になるため、I/O スレッドでクエリを実行する
                                imageResults = withContext(Dispatchers.IO) { queryImages(context) }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreen(
    modifier: Modifier = Modifier,
    environmentInfo: EnvironmentInfo,
    imageResults: List<ImageTestResult>,
    onRequestPermission: () -> Unit,
    onExecuteImageSearch: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        EnvironmentInfoArea(environmentInfo = environmentInfo)

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRequestPermission) {
                Text(text = "Request Permission")
            }
            Button(onClick = onExecuteImageSearch) {
                Text(text = "Execute Image Search")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ImageResultList(imageResults = imageResults)
    }
}

@Composable
private fun EnvironmentInfoArea(environmentInfo: EnvironmentInfo) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Android Version: ${environmentInfo.androidVersion}", style = MaterialTheme.typography.bodyLarge)
        Text(text = "API Level: ${environmentInfo.apiLevel}")
        Text(text = "Target SDK: 35")
        Text(text = "scoped storage: ${environmentInfo.isScopedStorage}")
        Text(text = "READ_EXTERNAL_STORAGE: ${environmentInfo.readExternalStorage}")
        Text(text = "READ_MEDIA_IMAGES: ${environmentInfo.readMediaImages}")
        Text(text = "READ_MEDIA_VISUAL_USER_SELECTED: ${environmentInfo.readMediaVisualUserSelected}")
    }
}

@Composable
private fun ImageResultList(imageResults: List<ImageTestResult>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(imageResults, key = { it.id }) { result ->
            ImageResultItem(result = result)
        }
    }
}

@Composable
private fun ImageResultItem(result: ImageTestResult) {
    var expanded by remember { mutableStateOf(false) }

    val hasErrors = result.errors.isNotEmpty()
    val errorBackgroundColor = if (hasErrors) {
        Color.Red.copy(alpha = 0.2f)
    } else {
        Color.Transparent
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(errorBackgroundColor)
                .padding(12.dp)
        ) {
            Text(text = "${result.type} / ${result.displayName}", style = MaterialTheme.typography.bodyLarge)
            Text(text = "dataPath: ${result.dataPath ?: "null"}")
            Text(text = "DateTaken: ${result.dateTaken ?: "null"} / DateAdded: ${result.dateAdded ?: "null"} / DateModified: ${result.dateModified ?: "null"} / Size: ${result.size ?: "null"}")
            Text(text = "FileExists: ${result.fileExists.toCheckMark()}")
            Text(text = "Uri I/O: ${result.canOpenUriInputStream.toCheckMark()}")
            Text(text = "File I/O: ${result.canOpenFileInputStream.toCheckMark()}")
            Text(text = "Glide(Uri/File): ${result.glideUriSuccess.toCheckMark()} / ${result.glideFileSuccess.toCheckMark()}")
            Text(text = "Coil(Uri/File): ${result.coilUriSuccess.toCheckMark()} / ${result.coilFileSuccess.toCheckMark()}")
            Button(onClick = { expanded = !expanded }) {
                Text(text = if (expanded) "Hide Details" else "Details")
            }
            if (expanded) {
                Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                    if (result.errors.isEmpty()) {
                        Text(text = "No errors")
                    } else {
                        result.errors.forEach { error ->
                            Text(text = error)
                        }
                    }
                }
            }
        }

        if (hasErrors) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(text = result.errors.size.toString())
            }
        }
    }
}

internal val `33＋` = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
internal val `34＋` = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

internal fun requiredImagePerms(): List<String> = when {
    `34＋` -> listOf(READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED)
    `33＋` -> listOf(READ_MEDIA_IMAGES)
    else -> listOf(READ_EXTERNAL_STORAGE)
}

@Composable
private fun rememberPermissionLauncher(environmentInfoUpdater: () -> Unit) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        environmentInfoUpdater()
    }

private fun buildEnvironmentInfo(context: Context): EnvironmentInfo {
    val sdkInt = Build.VERSION.SDK_INT
    val requestLegacyValue = checkLegacyExternalStorageStatus()
    return EnvironmentInfo(
        androidVersion = Build.VERSION.RELEASE ?: "Unknown",
        apiLevel = sdkInt,
        isScopedStorage = requestLegacyValue,
        readExternalStorage = context.permissionStatusForLegacy(),
        readMediaImages = context.permissionStatusForMediaImages(),
        readMediaVisualUserSelected = context.permissionStatusForVisualUserSelected()
    )
}

private fun Context.permissionStatusForLegacy(): String {
    return permissionState(READ_EXTERNAL_STORAGE)
}

private fun Context.permissionStatusForMediaImages(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionState(READ_MEDIA_IMAGES)
    } else {
        "N/A"
    }
}

private fun Context.permissionStatusForVisualUserSelected(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        permissionState(READ_MEDIA_VISUAL_USER_SELECTED)
    } else {
        "N/A"
    }
}

private fun Context.permissionState(permission: String): String {
    val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    return if (granted) "GRANTED" else "DENIED"
}

fun checkLegacyExternalStorageStatus(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Environment.isExternalStorageLegacy().toString()
    } else {
        "N/A(SDK < Q)"
    }
}

private fun logPermissionStates(environmentInfo: EnvironmentInfo) {
    Log.i(LogTag, "[INFO] READ_EXTERNAL_STORAGE: ${environmentInfo.readExternalStorage}")
    Log.i(LogTag, "[INFO] READ_MEDIA_IMAGES: ${environmentInfo.readMediaImages}")
    Log.i(LogTag, "[INFO] READ_MEDIA_VISUAL_USER_SELECTED: ${environmentInfo.readMediaVisualUserSelected}")
}

private fun queryImages(context: Context): List<ImageTestResult> {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATA,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.SIZE
    )

    val contentResolver = context.contentResolver
    val imageLoader = ImageLoader(context)
    val cursor = contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        null
    )

    return cursor!!.use { c ->
        val columnIndices = projection.associateWith { column ->
            val index = c.getColumnIndex(column)
            if (index == -1) {
                Log.i(LogTag, "[INFO] COLUMN $column: not available on this OS")
            }
            index
        }

        data class Item(
            val id: Long,
            val displayName: String,
            val type: ImageType,
            val dataPath: String?,
            val uri: android.net.Uri,
            val dateTaken: Long?,
            val dateAdded: Long?,
            val dateModified: Long?,
            val size: Long?,
            val fileExists: Boolean,
        )

        val items = mutableListOf<Item>()
        while (c.moveToNext()) {
            val id = c.getLongOrNull(columnIndices[MediaStore.Images.Media._ID]) ?: continue
            val displayName = c.getStringOrNull(columnIndices[MediaStore.Images.Media.DISPLAY_NAME]).orEmpty()
            val dataPath = c.getStringOrNull(columnIndices[MediaStore.Images.Media.DATA])
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val dateTaken = c.getLongOrNull(columnIndices[MediaStore.Images.Media.DATE_TAKEN])
            val dateAdded = c.getLongOrNull(columnIndices[MediaStore.Images.Media.DATE_ADDED])
            val dateModified = c.getLongOrNull(columnIndices[MediaStore.Images.Media.DATE_MODIFIED])
            val size = c.getLongOrNull(columnIndices[MediaStore.Images.Media.SIZE])

            val type = classifyImageType(dataPath)
            val fileExists = dataPath?.let { File(it).exists() } ?: false
            items.add(
                Item(
                    id = id,
                    displayName = displayName,
                    type = type,
                    dataPath = dataPath,
                    uri = uri,
                    dateTaken = dateTaken,
                    dateAdded = dateAdded,
                    dateModified = dateModified,
                    size = size,
                    fileExists = fileExists,
                )
            )
        }

        // image type別に数件になるように絞る
        fun filter(imageType: ImageType, max: Int): List<Item> {
            val filtered = items.filter { it.type == imageType }
            return if (filtered.size > max) {
                filtered.take(max)
            } else {
                filtered
            }
        }

        val allFiltered: List<Item> = ImageType.entries.map { filter(it, 3) }.flatten()

        val results = allFiltered.map {
            val errors = mutableListOf<String>()
            val uri = it.uri
            val dataPath = it.dataPath
            val canOpenUriInputStream = try {
                contentResolver.openInputStream(uri)?.use { }
                Log.i(LogTag, "[SUCCESS] Uri InputStream: $uri")
                true
            } catch (exception: Exception) {
                val message = exception.toErrorMessage()
                errors.add(message)
                Log.e(LogTag, "[FAILED] Uri InputStream: $message")
                false
            }

            val canOpenFileInputStream = if (dataPath != null) {
                try {
                    FileInputStream(File(dataPath)).use { }
                    Log.i(LogTag, "[SUCCESS] File InputStream: $dataPath")
                    true
                } catch (exception: Exception) {
                    val message = exception.toErrorMessage()
                    errors.add(message)
                    Log.e(LogTag, "[FAILED] File InputStream: $message")
                    false
                }
            } else {
                false
            }

            val glideUriSuccess = try {
                Glide.with(context).load(uri).submit().get()
                Log.i(LogTag, "[SUCCESS] Glide Uri: $uri")
                true
            } catch (exception: Exception) {
                val message = exception.toErrorMessage()
                errors.add(message)
                Log.e(LogTag, "[FAILED] Glide Uri: $message")
                false
            }

            val glideFileSuccess = if (dataPath != null) {
                try {
                    Glide.with(context).load(File(dataPath)).submit().get()
                    Log.i(LogTag, "[SUCCESS] Glide File: $dataPath")
                    true
                } catch (exception: Exception) {
                    val message = exception.toErrorMessage()
                    errors.add(message)
                    Log.e(LogTag, "[FAILED] Glide File: $message")
                    false
                }
            } else {
                false
            }

            val coilUriSuccess = try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .build()
                val result = runBlocking { imageLoader.execute(request) }
                val success = result is SuccessResult
                if (success) {
                    Log.i(LogTag, "[SUCCESS] Coil Uri: $uri")
                } else {
                    Log.e(LogTag, "[FAILED] Coil Uri: unexpected result")
                    errors.add("Coil: unexpected result")
                }
                success
            } catch (exception: Exception) {
                val message = exception.toErrorMessage()
                errors.add(message)
                Log.e(LogTag, "[FAILED] Coil Uri: $message")
                false
            }

            val coilFileSuccess = if (dataPath != null) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(File(dataPath))
                        .build()
                    val result = runBlocking { imageLoader.execute(request) }
                    val success = result is SuccessResult
                    if (success) {
                        Log.i(LogTag, "[SUCCESS] Coil File: $dataPath")
                    } else {
                        Log.e(LogTag, "[FAILED] Coil File: unexpected result")
                        errors.add("Coil: unexpected result")
                    }
                    success
                } catch (exception: Exception) {
                    val message = exception.toErrorMessage()
                    errors.add(message)
                    Log.e(LogTag, "[FAILED] Coil File: $message")
                    false
                }
            } else {
                false
            }
            ImageTestResult(
                id = it.id,
                displayName = it.displayName,
                type = it.type,
                dataPath = dataPath,
                uri = uri,
                dateTaken = it.dateTaken,
                dateAdded = it.dateAdded,
                dateModified = it.dateModified,
                size = it.size,
                fileExists = it.fileExists,
                canOpenUriInputStream = canOpenUriInputStream,
                canOpenFileInputStream = canOpenFileInputStream,
                glideUriSuccess = glideUriSuccess,
                glideFileSuccess = glideFileSuccess,
                coilUriSuccess = coilUriSuccess,
                coilFileSuccess = coilFileSuccess,
                errors = errors
            )
        }
        Log.i(LogTag, "[INFO] MediaStore query returned: ${results.size} images")
        results
    }
}

private fun classifyImageType(dataPath: String?): ImageType {
    return when {
        dataPath?.startsWith("/storage/emulated/0/DCIM/Camera/") == true -> ImageType.Camera
        dataPath?.startsWith("/storage/emulated/0/Pictures/Screenshots/") == true -> ImageType.Screenshot
        dataPath?.startsWith("/storage/emulated/0/Download/") == true -> ImageType.Download
        else -> ImageType.Other
    }
}

private fun android.database.Cursor.getStringOrNull(index: Int?): String? {
    if (index == null || index == -1) return null
    return getString(index)
}

private fun android.database.Cursor.getLongOrNull(index: Int?): Long? {
    if (index == null || index == -1) return null
    return getLong(index)
}

// 成否を視覚的に示すためのシンプルな拡張関数
private fun Boolean.toCheckMark(): String = if (this) "✓" else "✗"

// 例外の先頭行だけを抽出して短く整形する拡張関数
private fun Throwable.toErrorMessage(): String {
    val messageHead = message?.lineSequence()?.firstOrNull().orEmpty()
    return if (messageHead.isNotEmpty()) {
        "${this::class.java.simpleName}: $messageHead"
    } else {
        this::class.java.simpleName
    }
}
