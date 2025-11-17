package com.example.scopedstorage

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.scopedstorage.ui.theme.ScopedStorageCheckTheme
import java.io.File

private const val LogTag = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScopedStorageCheckTheme {
                val context = LocalContext.current
                var environmentInfo by remember { mutableStateOf(buildEnvironmentInfo(context)) }
                var imageResults by remember { mutableStateOf(emptyList<ImageTestResult>()) }

                val permissionLauncher = rememberPermissionLauncher(environmentInfoUpdater = {
                    environmentInfo = buildEnvironmentInfo(context)
                    logPermissionStates(environmentInfo)
                })

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        environmentInfo = environmentInfo,
                        imageResults = imageResults,
                        onRequestPermission = {
                            val requestPermissions = requiredPermissionsForSdk(Build.VERSION.SDK_INT)
                            permissionLauncher.launch(requestPermissions)
                        },
                        onExecuteImageSearch = {
                            imageResults = queryImages(context)
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
        Text(text = "requestLegacyExternalStorage: ${environmentInfo.requestLegacyExternalStorage}")
        Text(text = "READ_EXTERNAL_STORAGE: ${environmentInfo.readExternalStorage}")
        Text(text = "READ_MEDIA_IMAGES: ${environmentInfo.readMediaImages}")
        Text(text = "READ_MEDIA_VISUAL_USER_SELECTED: ${environmentInfo.readMediaVisualUserSelected}")
    }
}

@Composable
private fun ImageResultList(imageResults: List<ImageTestResult>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(imageResults) { result ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "${result.type} / ${result.displayName}", style = MaterialTheme.typography.bodyLarge)
                Text(text = "dataPath: ${result.dataPath ?: "null"}")
                Text(text = "fileExists: ${result.fileExists}")
            }
        }
    }
}

private fun requiredPermissionsForSdk(sdkInt: Int): Array<String> {
    return when (sdkInt) {
        in 26..32 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        in 33..34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
    }
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
    val requestLegacyValue = resolveRequestLegacyValue(context)
    return EnvironmentInfo(
        androidVersion = Build.VERSION.RELEASE ?: "Unknown",
        apiLevel = sdkInt,
        requestLegacyExternalStorage = requestLegacyValue,
        readExternalStorage = context.permissionStatusForLegacy(sdkInt),
        readMediaImages = context.permissionStatusForMediaImages(sdkInt),
        readMediaVisualUserSelected = context.permissionStatusForVisualUserSelected(sdkInt)
    )
}

private fun Context.permissionStatusForLegacy(sdkInt: Int): String {
    return when (sdkInt) {
        in 26..32 -> permissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> "N/A"
    }
}

private fun Context.permissionStatusForMediaImages(sdkInt: Int): String {
    return when (sdkInt) {
        in 33..Int.MAX_VALUE -> permissionState(Manifest.permission.READ_MEDIA_IMAGES)
        else -> "N/A"
    }
}

private fun Context.permissionStatusForVisualUserSelected(sdkInt: Int): String {
    return when (sdkInt) {
        in 35..Int.MAX_VALUE -> permissionState(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        else -> "N/A"
    }
}

private fun Context.permissionState(permission: String): String {
    val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    return if (granted) "GRANTED" else "DENIED"
}

private fun resolveRequestLegacyValue(context: Context): String {
    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.ApplicationInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationInfo(context.packageName, 0)
    }

    val legacyFlagValue = ApplicationInfo::class.java.fields.firstOrNull { field ->
        field.name == "FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE"
    }?.getInt(null) ?: return "N/A"

    val flagEnabled = appInfo.flags and legacyFlagValue != 0
    return flagEnabled.toString()
}

private fun logPermissionStates(environmentInfo: EnvironmentInfo) {
    Log.i(LogTag, "[INFO] READ_EXTERNAL_STORAGE: ${environmentInfo.readExternalStorage}")
    Log.i(LogTag, "[INFO] READ_MEDIA_IMAGES: ${environmentInfo.readMediaImages}")
    Log.i(LogTag, "[INFO] READ_MEDIA_VISUAL_USER_SELECTED: ${environmentInfo.readMediaVisualUserSelected}")
}

private fun queryImages(context: android.content.Context): List<ImageTestResult> {
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
    val results = mutableListOf<ImageTestResult>()
    val cursor = contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        null
    )

    cursor?.use { c ->
        val columnIndices = projection.associateWith { column ->
            val index = c.getColumnIndex(column)
            if (index == -1) {
                Log.i(LogTag, "[INFO] COLUMN $column: not available on this OS")
            }
            index
        }

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

            results.add(
                ImageTestResult(
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
                    canOpenUriInputStream = false,
                    canOpenFileInputStream = false,
                    glideUriSuccess = false,
                    glideFileSuccess = false,
                    coilUriSuccess = false,
                    coilFileSuccess = false,
                    errors = emptyList()
                )
            )
        }
    }

    Log.i(LogTag, "[INFO] MediaStore query returned: ${results.size} images")
    return results
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
