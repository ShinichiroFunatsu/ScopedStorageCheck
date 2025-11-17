## AGENTS.md

# AGENTS ガイド（Android Storage Access Investigation App）

このドキュメントは、エージェント（人間でも LLM でも）がこのリポジトリで作業するときの方針メモです。

---

## 1. 仕様の優先度

* アプリの挙動に関する正式な仕様は README.md の「仕様（Spec）」セクションとします。
* コードと README の仕様が矛盾している場合、README を優先してください。
* 大きな仕様変更が必要な場合は、先に README を更新し、それから実装を変えます。

---

## 2. テストと I/O チェックの扱い

* このプロジェクトでは UnitTest / InstrumentationTest は作成しません。

    * test ディレクトリ、androidTest ディレクトリを新規に触らないでください。
* README や TASKS に出てくる「I/O チェック」は、すべてアプリ本体内のランタイム処理です。

    * 例: ボタン押下時に contentResolver.openInputStream(uri) を呼んでみて、成功 / 失敗をフラグに保存する。
* I/O チェックの結果は

    * ImageTestResult の各フラグ
    * 画面の表示
    * Logcat の出力
      として人間が観察できれば十分です。

---

## 3. ビルド・実行の基本

* 基本的には Android Studio から普通に Run すれば問題ありません。
* コマンドラインを使う場合の目安:

    * 軽いコンパイル確認:

        * ./gradlew :app:compileDebugKotlin
    * デバッグビルド生成（テストは走らせない）:

        * ./gradlew :app:assembleDebug -x test
* フルビルドや CI 用の高度な設定は、このプロジェクトでは必須ではありません。

---

## 4. パーミッションと MediaStore

* パーミッション宣言とリクエスト内容は、README の Spec に合わせてください。
* RequestMultiplePermissions の配列は、Build.VERSION.SDK_INT に応じて組み立てます。
* MediaStore クエリの projection も README の Spec に従います。
* getColumnIndex が -1 を返したカラムについては、

    * 例: [INFO] COLUMN DATE_ADDED: not available on this OS
      のようにログを出して、無理に扱わないようにします。

---

## 5. I/O チェックとエラーハンドリング

* Uri / File に対して実際に I/O を投げてみて、

    * 成功したか
    * どんな例外が出たか
      を簡単に記録します。
* 例外は握りつぶさず、少なくとも

    * 例外クラス名
    * メッセージの先頭部分
      を errors リストに格納してください。
* ログには [SUCCESS] / [FAILED] といったプレフィックスを使うと分かりやすいです。

---

## 6. UI 実装の方針

* UI は Jetpack Compose で実装します。
* 1 画面で

    * 環境情報
    * パーミッションボタン
    * MediaStore 実行ボタン
    * 結果一覧
      が見える構成であれば十分です。
* 結果一覧の表示内容や見た目は、読みやすさが確保できていれば細かくこだわらなくても構いません。

---

## 7. ドキュメントの更新

* README の Spec に影響する変更をするときは、必ず README を更新してください。
* TASKS.md のタスク構成を変えたくなった場合も、README の記述と矛盾しないようにします。
* あいまいな仕様や OS ごとの挙動の違いに遭遇した場合、

    * まずはログに「要確認」と残しつつ
    * 可能であれば README に補足を追加します。

---
