## README.md

# Android Storage Access Investigation App

## プロジェクト概要

Android の Scoped Storage / パーミッションモデルの挙動を、OS バージョンごとに手元で確認するための調査用アプリです。
実機・エミュレータ上でボタンを押して、MediaStore から取得した画像に対して

* Uri アクセス
* File アクセス
* Glide / Coil によるロード
* それぞれの I/O チェック（実際にストリームを開いてみる）

が「成功するか / 失敗するか」を一覧とログで確認できるようにします。

アプリ自身は画像の挿入・削除・更新は行わず、「他アプリがすでに作った画像」を読むだけです。

---

## テストに関する前提

* このプロジェクトでは、UnitTest（test ディレクトリ）や InstrumentationTest（androidTest ディレクトリ）は作成しません。
* README や TASKS で出てくる「I/O チェック」は、

    * アプリ本体のコード内で
    * ボタン押下などのタイミングで実行される
    * ランタイム処理
      を指します。
* I/O チェックの目的は、

    * 「この OS / このパーミッション状態で、このアクセスパターンが通るかどうか」
      を画面とログで目視できるようにすることであり、
      自動テストフレームワークによる検証は範囲外とします。

---

## 仕様（Spec）

### 0. 全体ポリシー

* compileSdkVersion: 35
* targetSdkVersion: 35（固定）
* minSdkVersion: 26
* UI: Jetpack Compose
* 画像ライブラリ: Glide / Coil（両方を使って挙動を比較）
* アプリは画像を挿入しない（他アプリが作成した画像だけ読む）

requestLegacyExternalStorage について:

* Manifest 上の値（true / false）を取得して表示・ログ出力する
* targetSdk 35 では挙動に影響しない「参考情報」として扱う
* 設定を変えてもビヘイビア差分は追わない

### 1. 対象 OS / テストデバイス

想定テスト環境:

* Android 8 / 9（API 26–28）：参考（Scoped Storage 導入前）
* Android 10（API 29）
* Android 11（API 30）
* Android 13（API 33）
* Android 14（API 34）
* Android 16（API 35 例: Pixel 10 Pro XL）

同じアプリをこれらの OS で動かし、パーミッションと MediaStore / I/O の挙動差を観察します。

### 2. パーミッションモデル調査

#### 2.1 対象パーミッション

OS バージョンに応じて、以下のパーミッションを Manifest に宣言し、必要に応じてリクエストします。

* API 26–32

    * READ_EXTERNAL_STORAGE
* API 33–34

    * READ_MEDIA_IMAGES
* API 35 以上

    * READ_MEDIA_IMAGES
    * READ_MEDIA_VISUAL_USER_SELECTED

Manifest にはこれら全てを宣言しておき、古い OS で無視されるものは放置して構いません。

#### 2.2 パーミッションリクエストフロー

* 画面上に「Request Permission」ボタンを配置します。
* ボタン押下時、OS バージョンに応じて適切なパーミッション配列を組み立て、RequestMultiplePermissions でリクエストします。
* 結果は

    * 環境情報エリアの表示
    * ログ出力
      に反映します。

#### 2.3 パーミッション状態表示

画面上部の「環境情報エリア」に、常に以下を表示します。

* READ_EXTERNAL_STORAGE: GRANTED / DENIED / N/A
* READ_MEDIA_IMAGES: GRANTED / DENIED / N/A
* READ_MEDIA_VISUAL_USER_SELECTED: GRANTED / DENIED / N/A

N/A は「この OS では意味を持たない／リクエストしていない」を表します。

---

### 3. MediaStore クエリ仕様

#### 3.1 クエリ対象

* MediaStore.Images.Media.EXTERNAL_CONTENT_URI

#### 3.2 Projection（調査対象カラム）

以下のカラムを projection として指定します（RELATIVE_PATH は使いません）。

* _ID
* DISPLAY_NAME
* DATA（非推奨だが調査対象）
* DATE_TAKEN
* DATE_ADDED
* DATE_MODIFIED
* SIZE

実装上は getColumnIndex を使い、戻り値が -1 のカラムは「未サポート」とみなします。
未サポートのカラムについては、ログに

* [INFO] COLUMN DATE_ADDED: not available on this OS

のようなメッセージを出します。

#### 3.3 調査項目

MediaStore クエリに関して、以下を記録します。

* クエリの成功 / 失敗（例外種別とメッセージ）
* 取得件数
* 各カラムの可用性

    * DATA が null / 空文字 / 正常なパスのどれか
    * DATE_TAKEN / DATE_ADDED / DATE_MODIFIED の値の傾向（0、異常に大きい値など）

---

### 4. 画像種別の判定

MediaStore から取得した 1 行ごとに、DATA のパスから画像種別を推定します。

* CAMERA: /storage/emulated/0/DCIM/Camera/ 配下
* SCREENSHOT: /storage/emulated/0/Pictures/Screenshots/ 配下
* DOWNLOAD: /storage/emulated/0/Download/ 配下
* OTHER: 上記以外

各画像について、以下を確認します。

* MediaStore に検出されているか
* DATA カラムの値（そのままログ出力）
* File(DATA).exists() の結果
* I/O チェック（アプリ内処理）

    * contentResolver.openInputStream(uri) が成功するか
    * DATA が非 null のとき FileInputStream(File(DATA)) が成功するか
* Glide / Coil ロードチェック

    * Uri 指定でロードできるか
    * File 指定でロードできるか

---

### 5. アクセス方法別 I/O チェック（アプリ内処理）

ここでの「I/O チェック」はすべて、アプリ本体のコード内でボタン押下時などに実行されるランタイム処理です。
test / androidTest ディレクトリには何も追加しません。

#### Method A: 直接 Uri アクセス

* MediaStore.Images.Media.EXTERNAL_CONTENT_URI に _ID を付けて Uri を生成
* I/O チェック:

    * contentResolver.openInputStream(uri) を try/catch で実行し、成功 / 失敗を記録
* Glide / Coil:

    * Uri を指定してロードし、成功 / 失敗を記録

#### Method B: DATA パス経由アクセス

* DATA カラムから dataPath を取得
* dataPath が非 null の場合:

    * File(dataPath) を作成し、fileExists を記録
    * FileInputStream(File(dataPath)) を try/catch で実行し、成功 / 失敗を記録
    * Glide / Coil で File を指定してロードし、成功 / 失敗を記録

#### Method C: ハードコードパスアクセス

* 代表的なカメラ画像パス（例: /storage/emulated/0/DCIM/Camera/IMG_yyyyMMdd_hhmmss.jpg）に対して File を作成
* I/O チェック:

    * FileInputStream などで実際にアクセス
* Glide:

    * File 指定でロードし、成功 / 失敗を記録
* 端末ごとに実在するファイル名に合わせて調整して構いません。

---

### 6. ImageTestResult データ構造

MediaStore の 1 行ごとに、以下の情報をまとめた ImageTestResult を生成します。

* id: Long（_ID）

* displayName: String（DISPLAY_NAME）

* type: ImageType（CAMERA / SCREENSHOT / DOWNLOAD / OTHER）

* dataPath: String?（DATA）

* uri: Uri

* dateTaken: Long?（DATE_TAKEN）

* dateAdded: Long?（DATE_ADDED）

* dateModified: Long?（DATE_MODIFIED）

* size: Long?（SIZE）

* fileExists: Boolean

* canOpenUriInputStream: Boolean

* canOpenFileInputStream: Boolean

* glideUriSuccess: Boolean

* glideFileSuccess: Boolean

* coilUriSuccess: Boolean

* coilFileSuccess: Boolean

* errors: List<String>（例外クラス名＋メッセージの先頭行など）

調査中に必要な情報が増えた場合は、後方互換性を意識しつつフィールド追加をして構いません。

---

### 7. 画面仕様

#### 7.1 環境情報エリア

画面上部に、以下を表示するエリアを設けます。

* Android バージョン / API Level
* Target SDK: 35（固定表示）
* requestLegacyExternalStorage: true / false / N/A
* READ_EXTERNAL_STORAGE: GRANTED / DENIED / N/A
* READ_MEDIA_IMAGES: GRANTED / DENIED / N/A
* READ_MEDIA_VISUAL_USER_SELECTED: GRANTED / DENIED / N/A

#### 7.2 操作ボタン

* Request Permission ボタン

    * OS に応じたパーミッションセットをリクエストする
* Execute Image Search ボタン

    * 現在のパーミッション状態のまま MediaStore クエリを実行
    * 取得したレコードごとに I/O チェックと Glide / Coil チェックを実施し、ImageTestResult のリストを生成

#### 7.3 画像一覧表示

* LazyColumn などで ImageTestResult のリストを表示
* 各行に表示する情報（最低限）:

    * Type / DISPLAY_NAME
    * DATA パス（null または /storage/...）
    * DateTaken / DateAdded / DateModified（簡易フォーマットで可）
    * FileExists（✓ / ✗）
    * Uri I/O チェック（✓ / ✗）
    * File I/O チェック（✓ / ✗）
    * Glide(Uri/File)（✓ / ✗）
    * Coil(Uri/File)（✓ / ✗）
* 詳細表示（ボタンやタップなど）で errors 内のメッセージを展開できるようにする

---

### 8. 基本フロー

1. アプリ起動
   環境情報エリアに OS / targetSdk / legacy フラグ / パーミッション状態が表示される。

2. Request Permission ボタン
   OS 用のパーミッションセットをリクエストし、結果を UI とログに反映。

3. Execute Image Search ボタン
   現在のパーミッション状態で MediaStore クエリを実行し、
   各行に対して I/O チェック / Glide / Coil チェックを実施して ImageTestResult を生成・表示。

4. パーミッション状態を変更し、再度 3 を実行することで差分を観察。

---

### 9. テストデータ準備

各テスト端末で、事前に次の操作を行います。

1. カメラアプリで写真を 3 枚撮影（DCIM/Camera）
2. スクリーンショットを 3 枚撮影（Pictures/Screenshots）
3. Chrome などのブラウザで画像を 3 枚ダウンロード（Download）

これにより、最低限の CAMERA / SCREENSHOT / DOWNLOAD のサンプルが揃います。

---

### 10. ログ出力の例（イメージ）

ログはおおよそ次のような形式を想定します。

* [2024-11-17 12:00:00] === Test Started ===

* [INFO] Android Version: 16 (API 35)

* [INFO] Target SDK: 35

* [INFO] requestLegacyExternalStorage: false

* [INFO] READ_MEDIA_IMAGES: GRANTED

* [INFO] READ_MEDIA_VISUAL_USER_SELECTED: DENIED

* [INFO] MediaStore query returned: 42 images

* [INFO] COLUMN DATA: available

* [TEST] Image #1: IMG_20241117_120000.jpg

* [INFO] Type: CAMERA

* [INFO] DATA: /storage/emulated/0/DCIM/Camera/IMG_20241117_120000.jpg

* [SUCCESS] Uri InputStream: Opened

* [SUCCESS] File InputStream: Opened

* [SUCCESS] Glide + Uri: Loaded

* [SUCCESS] Glide + File: Loaded

* [TEST] Image #2: Screenshot_20241117.png

* [INFO] Type: SCREENSHOT

* [INFO] DATA: /storage/emulated/0/Pictures/Screenshots/Screenshot_20241117.png

* [SUCCESS] Uri InputStream: Opened

* [FAILED] File InputStream: FileNotFoundException - Permission denied

* [SUCCESS] Glide + Uri: Loaded

* [FAILED] Glide + File: Permission denied

---

## 非機能要件

* 言語: Kotlin
* UI: Jetpack Compose
* 依存ライブラリ: Glide / Coil
* Gradle Wrapper を使用し、ローカルに独自の Gradle を持たない
* ビルド・実行は Android Studio 標準機能か簡単な Gradle コマンドのみを利用

---

## コーディング方針（ざっくり）

* パッケージ分割はシンプルで構わない（ui / data / model 程度）
* 仕様に影響する変更をするときは、README のこの Spec セクションを更新してから実装する

---


