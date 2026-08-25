# Chrome Time Tracker

Windows版Google Chromeで最後に選択されたアクティブタブを検知し、サイト別の利用時間をローカルに記録・分析するスクリーンタイムアプリです。Chrome拡張機能が現在のhostnameをSpring Bootへ送り、PostgreSQLに保存した記録をブラウザ上のダッシュボードで確認できます。

## 開発理由

Windows標準の機能だけでは、Chrome内部の `chatgpt.com`、`youtube.com`、`docs.aws.amazon.com` などをサイト別に分けて利用時間を確認しにくいため、自分用のスクリーンタイムとして作成しました。

## 技術構成

- Java 25
- Spring Boot 4.1.1
- Spring Web MVC
- Spring JDBC
- PostgreSQL 18 / PostgreSQL JDBC Driver
- Chrome Extension Manifest V3
- HTML / CSS / JavaScript
- Maven 3.9.16（Maven Wrapper）
- jpackage（Windowsアプリとしてインストールする場合）
- Windows Registry Run（ログオン時の自動起動）

## アーキテクチャ

```text
Chrome Extension
        ↓ localhost REST
Spring Boot
        ↓ JDBC
PostgreSQL
        ↓
HTML / CSS / JavaScript Dashboard
```

Chrome拡張機能はタブやChromeウィンドウのイベントを受けて、`http://127.0.0.1:18080/api/context` へ現在のhostnameを送ります。Spring Bootは利用区間をPostgreSQLに記録し、同じローカルサーバーからダッシュボードと集計APIを提供します。

## 主な機能

- Chromeのアクティブタブを取得
- 同じタブ内でのURL変更を検知
- タブ切替、タブClose、Chromeウィンドウのフォーカス変更・Closeを検知
- ポーリングではなくChromeイベントを起点に利用サイトを更新
- URL全文ではなくドメイン（hostname）単位で保存
- 選択日のサイト別使用時間を表示
- 固定ルールによるカテゴリー分類（AI、開発・学習、動画・音楽、検索・情報収集、SNS、その他）
- 0時〜23時のカテゴリー別利用分布
- 日曜日〜土曜日のカテゴリー別週間利用分布
- 直近7日間と、その前の7日間を比較
- 過去4回の同曜日の中央値と比較
- カテゴリー別・サイト別の増減要因を表示
- Windowsログオン時の自動起動に対応したローカルインストール構成
- Javaアプリの正常終了時に計測中レコードを確定
- 1分ごとのチェックポイントと、未終了レコードの起動時復旧

## セットアップ

### 1. PostgreSQLの準備

PostgreSQLを起動し、アプリ用のロールとデータベースを作成します。パスワードは任意の安全な値に置き換えてください。

```sql
CREATE ROLE tracker_user
WITH LOGIN
PASSWORD '任意のパスワード';

CREATE DATABASE chrome_time_tracker
OWNER tracker_user;
```

テーブルはアプリ起動時に `src/main/resources/schema.sql` から作成・更新されます。

### 2. 環境変数の設定

PowerShellからユーザー環境変数を設定する例です。パスワード例はダミー値なので、実際には別の安全な値を使用してください。

```powershell
[Environment]::SetEnvironmentVariable(
    "TRACKER_DB_USER",
    "tracker_user",
    "User"
)

[Environment]::SetEnvironmentVariable(
    "TRACKER_DB_PASSWORD",
    "replace-with-a-secure-password",
    "User"
)
```

設定後、新しくPowerShellを開いてください。

### 3. ビルドと起動

```powershell
.\mvnw.cmd clean package
java -jar .\target\chrome-time-tracker-0.0.1-SNAPSHOT.jar
```

起動後、ダッシュボードを開きます。

```text
http://127.0.0.1:18080/
```

### 4. Chrome拡張機能の読み込み

1. Chromeで `chrome://extensions/` を開く
2. 「デベロッパー モード」を有効にする
3. 「パッケージ化されていない拡張機能を読み込む」を選ぶ
4. このリポジトリの `chrome-extension` フォルダーを指定する

## プライバシー

- 利用情報を外部サーバーへ送信せず、`127.0.0.1` 上のアプリだけに送信します。
- 記録はローカルのPostgreSQLへ保存します。
- URL全文ではなくhostnameだけを保存します。
- ページタイトルやページへの入力内容は取得・保存しません。

## 現在の制約

- 計測対象はGoogle Chromeだけです。
- Chromeで最後に選択されたウィンドウのアクティブタブを、次のChromeイベントが起きるまで利用中として扱います。
- Chromeを最小化している時間や離席時間も利用時間に含まれます。
- カテゴリー分類はJavaコード内の固定ルールです。
- 強制終了時は最後のチェックポイントまで、最大約1分の誤差が生じる可能性があります。
- Chrome拡張機能は現在、デベロッパーモードで手動読み込みします。
- jpackage用の配布物やインストーラーは、このリポジトリには含めていません。

## ディレクトリ構成

```text
chrome-time-tracker/
├─ .mvn/                         Maven Wrapper設定
├─ chrome-extension/
│  ├─ manifest.json             Manifest V3設定
│  └─ service-worker.js         Chromeイベント検知とlocalhost API送信
├─ src/
│  ├─ main/
│  │  ├─ java/                  Controller、Service、Repository、DTO
│  │  └─ resources/
│  │     ├─ static/             ダッシュボードのHTML/CSS/JavaScript
│  │     ├─ application.properties
│  │     └─ schema.sql
│  └─ test/                     Spring Bootテスト
├─ .gitignore
├─ mvnw
├─ mvnw.cmd
├─ pom.xml
└─ README.md
```
