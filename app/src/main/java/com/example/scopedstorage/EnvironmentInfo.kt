package com.example.scopedstorage

// 画面上部で表示する環境情報
data class EnvironmentInfo(
    val androidVersion: String,
    val apiLevel: Int,
    val requestLegacyExternalStorage: String,
    val readExternalStorage: String,
    val readMediaImages: String,
    val readMediaVisualUserSelected: String
)
