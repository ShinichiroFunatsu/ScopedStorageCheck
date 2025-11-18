package com.example.scopedstorage

import android.net.Uri

// MediaStore から取得した画像のメタ情報を扱うモデル
data class ImageTestResult(
    val id: Long,
    val displayName: String,
    val type: ImageType,
    val dataPath: String?,
    val uri: Uri,
    val dateTaken: Long?,
    val dateAdded: Long?,
    val dateModified: Long?,
    val size: Long?,
    val fileExists: Boolean,
    val canOpenUriInputStream: Boolean,
    val canOpenFileInputStream: Boolean,
    val glideUriSuccess: Boolean,
    val glideFileSuccess: Boolean,
    val glideUriDurationMs: Long?,
    val glideFileDurationMs: Long?,
    val coilUriSuccess: Boolean,
    val coilFileSuccess: Boolean,
    val coilUriDurationMs: Long?,
    val coilFileDurationMs: Long?,
    val errors: List<String>
)

// 画像の分類を表す列挙
enum class ImageType {
    Camera,
    Screenshot,
    Download,
    Other
}
