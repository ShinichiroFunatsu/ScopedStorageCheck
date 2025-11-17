## TASKS.md

# TASKS（Android Storage Access Investigation App – Simple）

このアプリは「Scoped Storage と画像アクセス周りをざっくり観察するための調査ツール」です。
ここでは、実装を一気に終わらせるための大きめタスクを 5 個だけ定義します。

I/O の確認はすべてアプリ内のランタイム処理（I/O チェック）で行い、UnitTest / InstrumentationTest は書かない前提です。

---

## T1: プロジェクト雛形と依存関係

* 目的
  minSdk 26 / targetSdk 35 / Jetpack Compose / Glide / Coil が動く最小構成を作る。

* やること

    * :app モジュールを用意する（既存があれば調整でも可）。
    * compileSdk 35, targetSdk 35, minSdk 26 を設定する。
    * Compose を有効にして、簡単な画面を 1 枚表示できるようにする。
    * Glide / Coil の依存関係を追加する。
    * Manifest に以下のパーミッションを宣言する。

        * READ_EXTERNAL_STORAGE
        * READ_MEDIA_IMAGES
        * READ_MEDIA_VISUAL_USER_SELECTED
    * requestLegacyExternalStorage の値を設定しておく（true / false どちらでもよいが、表示のために入れておく）。

* 完了条件

    * アプリが起動して空の Compose 画面が表示される。
    * Gradle ビルドがエラーなく完了する。

---

## T2: 環境情報表示とパーミッションリクエスト

* 目的
  画面上部で「今どんな環境か」が一目で分かるようにする。

* やること

    * 上部に「環境情報エリア」を作成する。

        * Android バージョン / API Level
        * Target SDK: 35（固定）
        * requestLegacyExternalStorage: true / false / N/A
        * READ_EXTERNAL_STORAGE: GRANTED / DENIED / N/A
        * READ_MEDIA_IMAGES: GRANTED / DENIED / N/A
        * READ_MEDIA_VISUAL_USER_SELECTED: GRANTED / DENIED / N/A
    * 「Request Permission」ボタンを配置する。
    * ボタン押下で:

        * OS バージョンに応じてパーミッション配列を組み立てて RequestMultiplePermissions を実行。
        * 結果を UI に反映。
        * ログに [INFO] で結果を出力。

* 完了条件

    * ボタン押下でシステムのパーミッションダイアログが表示される。
    * 許可 / 拒否の結果が環境情報エリアに反映される。
    * Logcat にパーミッション状態が出力される。

---

## T3: MediaStore クエリと ImageTestResult の基礎

* 目的
  「Execute Image Search」ボタンで MediaStore を叩き、最低限の ImageTestResult リストを作れるようにする。

* やること

    * 「Execute Image Search」ボタンを追加する。
    * ボタン押下時に Images.Media.EXTERNAL_CONTENT_URI へクエリを投げる。
    * projection は README の Spec 通りにする。
    * getColumnIndex が -1 のカラムは「not available」としてログ出力し、無理にアクセスしない。
    * 1 行ごとに:

        * DATA から ImageType（CAMERA / SCREENSHOT / DOWNLOAD / OTHER）を判定。
        * id / displayName / type / dataPath / uri / 日付系 / size / fileExists（存在判定だけ）などを詰めた ImageTestResult を作る。
    * クエリ全体の件数と、各カラムの可否を [INFO] ログとして出力する。

* 完了条件

    * Execute Image Search ボタン押下で、端末内の画像が何件か一覧化される。
    * ログに取得件数とカラム可用性が出力される。
    * アプリが落ちずに動作する。

---

## T4: I/O チェック（Uri / File）と Glide / Coil 成否の記録

* 目的
  1 つの ImageTestResult に対して、「どのアクセスが通って、どこで失敗するか」をアプリ内で判定して埋める。

* 前提

    * ここでの「I/O チェック」は UnitTest / InstrumentationTest ではなく、アプリ本体のランタイム処理です。
    * test / androidTest ディレクトリにはファイルを作らない。

* やること

    * Uri I/O チェック:

        * contentResolver.openInputStream(uri) を try/catch で実行し、成功したら canOpenUriInputStream = true、失敗したら false とし、errors に例外情報を追加する。
    * File I/O チェック:

        * dataPath が null の場合はスキップ。
        * dataPath がある場合:

            * File(dataPath).exists() を fileExists に保存。
            * FileInputStream(File(dataPath)) を try/catch で実行し、成功 / 失敗を canOpenFileInputStream に保存、失敗時は errors に追加。
    * Glide / Coil ロードチェック:

        * Uri 指定でロードして glideUriSuccess / coilUriSuccess を埋める。
        * dataPath がある場合は File 指定でもロードして glideFileSuccess / coilFileSuccess を埋める。
        * 成否判定は完了コールバックや例外ベースでざっくりでよい。
    * 例外発生時は、例外クラス名とメッセージの先頭部分を errors リストに追加し、ログにも [FAILED] で出す。

* 完了条件

    * 少なくともいくつかの画像について、canOpenUriInputStream / canOpenFileInputStream / glideUriSuccess / glideFileSuccess / coilUriSuccess / coilFileSuccess がバラける。
    * パーミッションを付与／剥奪して再度実行すると、フラグの結果が変わる。
    * Logcat と errors を見れば「どこで失敗しているか」が判別できる。

---

## T5: 結果一覧 UI の整備

* 目的
  I/O チェックと Glide / Coil の結果を、画面上でざっくり見渡せるようにする。

* やること

    * LazyColumn で ImageTestResult のリストを表示する。
    * 各行に最低限表示する情報:

        * type / displayName
        * dataPath（null か /storage/... か）
        * fileExists（✓ / ✗）
        * Uri I/O チェック（✓ / ✗）
        * File I/O チェック（✓ / ✗）
        * Glide(Uri/File) / Coil(Uri/File) の成功 / 失敗（✓ / ✗ など）
    * 行をタップするか、「Details」的なボタンで errors を展開表示できるようにする（折りたたみやダイアログなど、簡単なものでよい）。
    * Request Permission / Execute Image Search ボタンと同じ画面内に収める。

* 完了条件

    * 1 画面で「環境情報」「ボタン」「結果一覧」がひととおり触れる。
    * パーミッション状態を変えてから Execute Image Search を押すと、✓ / ✗ の付き方が変化するのを目で確認できる。
    * 必要なときに errors の内容を UI 上から確認できる。


