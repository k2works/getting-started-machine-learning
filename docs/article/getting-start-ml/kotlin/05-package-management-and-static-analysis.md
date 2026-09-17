---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "Gradle のバージョンカタログと依存の構成、Wrapper のチェックサム・JDK ツールチェーン・Gradle デーモンの JDK の固定、ktlint・detekt による静的解析、Kover によるカバレッジ計測を学ぶ。"
tags: [article,getting-start-ml,kotlin]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T04:50:50Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、実験を再現するには乱数のシードに加えて、Kotlin・ライブラリ・JDK・Gradle のバージョンを固定する必要があると述べました。この章では、それを担う Gradle の仕組みと、コードを実行せずに問題を見つける **静的解析**、テストがコードのどこを通ったかを測る **カバレッジ** を整えます。

| 道具 | 役割 | 本章の節 |
|------|------|---------|
| [Gradle](https://gradle.org/)（Kotlin DSL・バージョンカタログ） | 依存ライブラリとビルドの手順を管理する | 5.2 |
| Gradle Wrapper・JDK ツールチェーン | Gradle と JDK のバージョンを固定する | 5.3 |
| [ktlint](https://pinterest.github.io/ktlint/) | コードスタイルの検査と整形 | 5.4 |
| [detekt](https://detekt.dev/) | コードの問題（複雑さ・マジックナンバーなど）の検査 | 5.5 |
| Kotlin コンパイラ | 型チェック | 5.6 |
| [Kover](https://kotlin.github.io/kotlinx-kover/gradle-plugin/) | テストのカバレッジ計測 | 5.7 |

本章のバージョンは、執筆時点の `gradle/libs.versions.toml` と Wrapper の設定に書かれたものです（Gradle 9.7.1、Kotlin 2.4.20、ktlint 1.8.0、detekt 1.23.8、Kover 0.9.9）。選定の理由は [ADR 002](../../../adr/002-kotlin-ml-libraries.md) を参照してください。

[Python 版の第 5 章](../python/05-package-management-and-static-analysis.md) と比べると、Python の uv・Ruff・mypy・pytest-cov に当たるものを、Kotlin では Gradle とそのプラグインで組み立てます。

## 5.2 Gradle によるパッケージ管理

### 2 つの設定ファイル

Gradle の設定は、Kotlin で書くスクリプト（Kotlin DSL）の 2 つのファイルに分かれています。

| ファイル | 役割 |
|---------|------|
| `settings.gradle.kts` | プロジェクトの名前と、ビルド全体に効く設定（JDK の自動取得など） |
| `build.gradle.kts` | プラグイン・依存ライブラリ・タスクの設定 |

`build.gradle.kts` の先頭では、プラグインと依存ライブラリを宣言しています。

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

repositories {
    mavenCentral()
}

val ktlint: Configuration = configurations.create("ktlint")

dependencies {
    implementation(libs.dataframe)
    implementation(libs.tribuo.classification.tree)
    // DataFrame・Tribuo が使う SLF4J の警告を出さないための、何もしないログ実装
    runtimeOnly(libs.slf4j.nop)
    testImplementation(kotlin("test"))
    ktlint(libs.ktlint.cli) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
}

kotlin {
    jvmToolchain(21)
}
```

Kotlin DSL は Kotlin のコードなので、IDE で補完が効き、`libs.dataframe` のような名前の打ち間違いはビルドの前にエラーとして分かります。

### バージョンカタログ

依存ライブラリとプラグインの版は、`gradle/libs.versions.toml`（**バージョンカタログ**）の 1 か所にまとめています。

```toml
[versions]
kotlin = "2.4.20"
ktlint = "1.8.0"
dataframe = "0.15.0"
tribuo = "4.3.2"
slf4j = "2.0.16"
detekt = "1.23.8"
kover = "0.9.9"

[libraries]
dataframe = { module = "org.jetbrains.kotlinx:dataframe", version.ref = "dataframe" }
tribuo-classification-tree = { module = "org.tribuo:tribuo-classification-tree", version.ref = "tribuo" }
slf4j-nop = { module = "org.slf4j:slf4j-nop", version.ref = "slf4j" }
ktlint-cli = { module = "com.pinterest.ktlint:ktlint-cli", version.ref = "ktlint" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
```

- `[versions]` に版を書き、`[libraries]` と `[plugins]` から `version.ref` で参照します
- カタログに書いた名前は、`build.gradle.kts` から `libs.` で始まる名前で使えます。`tribuo-classification-tree` の `-` は `.` に変わり、`libs.tribuo.classification.tree` になります
- Kotlin の版は Kotlin の Gradle プラグイン（`kotlin-jvm`）の版で決まり、標準ライブラリ（`kotlin-stdlib`）も同じ版になります。第 4 章で見た `Random(seed)` の乱数列も、この 1 行で固定されています

Python 版の `pyproject.toml` は `pandas>=3.0.5` のように範囲で書きましたが、バージョンカタログには正確な版を書いています。

### 本番依存と開発依存

`dependencies` の中では、**構成**（configuration）で依存の使いみちを分けます。

| 構成 | 中身 | 判断の基準 |
|------|------|----------|
| `implementation` | Kotlin DataFrame・Tribuo | コンパイルにも実行にも必要なもの |
| `runtimeOnly` | slf4j-nop | コードからは直接呼ばず、実行するときだけ必要なもの |
| `testImplementation` | kotlin.test | テストのコンパイルと実行だけに必要なもの |
| `ktlint`（自作） | ktlint-cli | ビルドするプログラムとは無関係で、ktlint のタスクだけが使うもの |

`ktlint` は、`configurations.create("ktlint")` で作った独自の構成です。ここに入れた依存は、アプリのクラスパスに混ざりません。`Bundling.EXTERNAL` の指定は、ktlint のドキュメントにある Gradle から CLI を呼び出す書き方に従ったものです。

プラグインとして入れる detekt と Kover は、`dependencies` ではなく `plugins` に書きます。どちらもビルドの手順（タスク）を増やす道具で、アプリのクラスパスには入りません。

### ライブラリを追加する

ライブラリを追加するときは、カタログに 1 行書き、`build.gradle.kts` の `dependencies` から参照します。第 2 章で Kotlin DataFrame と Tribuo を追加したときの手順です。

1. `[versions]` に `dataframe = "0.15.0"` と `tribuo = "4.3.2"` を追加する
2. `[libraries]` に `dataframe` と `tribuo-classification-tree` を追加する
3. `dependencies` に `implementation(libs.dataframe)` と `implementation(libs.tribuo.classification.tree)` を追加する

依存関係は `dependencies` タスクで確認できます。直接の依存関係は、行頭が `+---` か `\---` の行です。

```bash
./gradlew dependencies --configuration runtimeClasspath
```

```text
runtimeClasspath - Runtime classpath of 'main'.
+--- org.jetbrains.kotlin:kotlin-stdlib:2.4.20
|    +--- org.jetbrains:annotations:13.0
|    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0 -> 2.0.20 (c)
|    +--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0 -> 2.0.20 (c)
...
+--- org.jetbrains.kotlinx:dataframe:0.15.0
...
+--- org.tribuo:tribuo-classification-tree:4.3.2
...
\--- org.slf4j:slf4j-nop:2.0.16
```

`1.8.0 -> 2.0.20` は、複数のライブラリが同じライブラリの違う版を求めたとき、Gradle が最も新しい版を選んだことを表します。

### 依存関係をロックしない理由

Python 版では、`uv.lock` に依存関係の依存関係まで正確な版を記録しました。Gradle にも同じ役割の **dependency locking**（`gradle.lockfile`）がありますが、本リポジトリでは使っていません。

Python 版で `uv.lock` が必要だったのは、`pyproject.toml` に版の範囲を書いていたからです。範囲で書くと、インストールした日によって入る版が変わります。Kotlin 版では次の理由で、ロックファイルが無くても毎回同じ版に解決されます。

- 直接の依存関係は、バージョンカタログに正確な版で書いている
- 依存関係の依存関係の版は、各ライブラリの公開済みの POM に書かれていて、後から変わらない
- `dependencies` タスクの出力を調べ、`[1.0,2.0)` のような範囲指定や `1.+` のような動的な版が無いことを確かめた

依存関係の中に範囲指定や動的な版が入ってきたら、そのときに dependency locking を導入します。

## 5.3 JDK と Gradle のバージョンを固定する

Kotlin のプログラムは JDK の上で動き、ビルドは Gradle が行います。どちらも版が変わると動作が変わりうるので、Python 版で `.python-version` を置いたのと同じように固定します。

### Gradle Wrapper

`gradlew`（Windows では `gradlew.bat`）は **Gradle Wrapper** です。`gradle/wrapper/gradle-wrapper.properties` に書かれた版の Gradle を自動でダウンロードして使うので、Gradle を入れていない環境でも、決まった版でビルドできます。

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
networkTimeout=10000
retries=0
retryBackOffMs=500
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

`distributionSha256Sum` は、ダウンロードした Gradle の SHA-256 です。値が一致しなければ Wrapper は Gradle を使わずに失敗するので、配布物が途中で差し替えられていないことを確かめられます。値は Gradle の配布サイトが公開している `gradle-9.7.1-bin.zip.sha256` から取り、`wrapper` タスクで設定しました。

```bash
./gradlew wrapper --gradle-version 9.7.1 --gradle-distribution-sha256-sum acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a
```

このとき、`gradlew`・`gradlew.bat`・`gradle-wrapper.jar` も Gradle 9.7.1 のものに作り直されました。第 1 章で最初に Wrapper を作ったのは、手元にあった Gradle 8.11.1 だったためです。Wrapper のファイルはどれもコミットします。

もう 1 つ、`gradlew` の改行コードに注意が必要です。Windows の Git は、既定の設定でチェックアウトするときに改行コードを CRLF に変えることがあり、CRLF になった `gradlew` はシェルで実行できません。リポジトリの `.gitattributes` で LF に固定しています。

```text
gradlew text eol=lf
```

### JDK ツールチェーン

`build.gradle.kts` の `jvmToolchain(21)` は、コンパイルとテストに使う JDK を 21 に固定します。手元に JDK 21 が無ければ、`settings.gradle.kts` の foojay プラグインが自動でダウンロードします。

```kotlin
plugins {
    // jvmToolchain で指定した JDK が無ければ自動で取得する
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "getting-started-ml"
```

### Gradle デーモンの JDK

ツールチェーンが固定するのは、コンパイルとテストに使う JDK です。Gradle 本体（**Gradle デーモン**）とプラグインは、それとは別に、`gradlew` を起動した環境の JDK で動きます。手元の Windows の既定の JDK は 25 なので、Gradle デーモンは JDK 25 で動いていました。

このことが問題になったのは、5.5 節の detekt を追加したときです。detekt 1.23.8 は Gradle デーモンの中で動くので、JDK 25 では、次のようにバージョン番号だけを表示して失敗しました。

```text
* What went wrong:
Execution failed for task ':detekt' (registered by plugin 'io.gitlab.arturbosch.detekt').
> 25.0.2
```

エラーメッセージは `25.0.2` だけで、原因は書かれていません。JDK 21 で Gradle を起動し直すと解析まで進んだので、デーモンの JDK の版が原因だと分かりました。

そこで、Gradle デーモンが使う JDK も 21 に固定します。Gradle の `updateDaemonJvm` タスクで設定ファイルを作ります。

```bash
./gradlew updateDaemonJvm --jvm-version=21
```

`gradle/gradle-daemon-jvm.properties` に、`toolchainVersion=21` と、JDK 21 が無いときのダウンロード先が書き込まれます。これで、`gradlew` を JDK 25 で起動しても、デーモンは JDK 21 で動きます。

```bash
./gradlew --version
```

```text
Gradle 9.7.1
Kotlin:        2.4.0
Launcher JVM:  25.0.2 (Oracle Corporation 25.0.2+10-69)
Daemon JVM:    Compatible with Java 21, any vendor, nativeImageCapable=false (from gradle/gradle-daemon-jvm.properties)
```

`Launcher JVM` は `gradlew` を起動した JDK、`Daemon JVM` は実際にビルドを行うデーモンの JDK です。`Kotlin: 2.4.0` は Gradle 本体が内部で使う Kotlin の版で、プロジェクトのコードをコンパイルする Kotlin（2.4.20）とは別のものです。

| 何の JDK か | 固定する場所 | 版 |
|-----------|------------|----|
| コンパイル・テスト | `build.gradle.kts` の `jvmToolchain` | 21 |
| Gradle デーモン（プラグインの実行） | `gradle/gradle-daemon-jvm.properties` | 21 |
| `gradlew` の起動 | 固定しない（手元の既定の JDK） | 手元では 25 |

## 5.4 コードスタイル — ktlint

### ktlint の設定

コードの整形と検査には ktlint を使います。ktlint は Gradle のプラグインとしてではなく、CLI を `JavaExec` タスクから呼び出しています。

```kotlin
val kotlinSources = listOf("src/**/*.kt", "*.kts")

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "ktlint でコードスタイルを検査する"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args(kotlinSources)
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "ktlint でコードを整形する"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    args(listOf("-F") + kotlinSources)
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}
```

- 検査するファイルは、`src` 以下の Kotlin のファイルと、`build.gradle.kts` などの Gradle のスクリプトです
- `classpath = ktlint` で、5.2 節で作った `ktlint` 構成（ktlint-cli）を使います
- `check` タスクに `ktlintCheck` を加えたので、`./gradlew check` でテストと一緒に検査されます

`.editorconfig` を置いていないので、ktlint の既定のコードスタイル `ktlint_official` が使われます。1 行の上限は 140 文字です。

### ktlint の実行

```bash
# 検査する（CI 向け）
./gradlew ktlintCheck

# 整形する
./gradlew ktlintFormat
```

ktlint は、読みやすさの問題を見つけるきっかけにもなります。第 2 章では、整形したら 1 行に収まらなくなったコードを拡張関数に切り出し、値が縦に並んで読めなくなったテストのデータを列ごとの書き方に改めました。整形の結果が読みにくいときは、ktlint を黙らせるのではなく、コードの形を見直すサインとして扱います。

## 5.5 静的コード解析 — detekt

### ktlint と detekt の役割

ktlint が「どう書くか（書式）」を揃えるのに対して、detekt は「何を書いたか（コードの中身）」の問題を探します。

| 観点 | ktlint | detekt |
|------|--------|--------|
| 字下げ・空白・改行の位置 | 検査・整形する | 扱わない |
| import の並び・未使用の import | 検査・整形する | ワイルドカード import を検査する（未使用の import のルールは既定で無効） |
| 関数の長さ・複雑さ | 扱わない | 検査する |
| マジックナンバー・命名規則 | 扱わない（命名の一部だけ） | 検査する |
| 自動修正 | できる | 本リポジトリの設定ではしない |

### detekt の設定

detekt は Gradle のプラグインとして追加し、`build.gradle.kts` で設定ファイルを指定します。

```kotlin
// detekt の既定の設定に、detekt.yml に書いた項目だけを重ねる。check タスクから実行される
detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("detekt.yml"))
}
```

`buildUponDefaultConfig = true` にすると、detekt の既定の設定をすべて使ったうえで、`detekt.yml` に書いた項目だけを上書きします。設定ファイルには、既定から変えた理由だけが残ります。detekt のプラグインは `check` タスクに `detekt` を自動で加えます。

### 最初の実行

まず `detekt.yml` を置かずに、既定の設定で実行しました（パスは `apps/kotlin/` からの相対パスに直しています）。

```bash
./gradlew detekt
```

```text
src/main/kotlin/chapter01/KinokoTakenoko.kt:1:1: Package name should match the pattern: [a-z]+(\.[a-z][A-Za-z0-9]*)* [PackageNaming]
src/main/kotlin/chapter01/Main.kt:1:1: Package name should match the pattern: [a-z]+(\.[a-z][A-Za-z0-9]*)* [PackageNaming]
...
src/main/kotlin/chapter01/KinokoTakenoko.kt:41:74: This expression contains a magic number. Consider defining it to a well named constant. [MagicNumber]
src/main/kotlin/chapter02/Main.kt:9:1: Line detected, which is longer than the defined maximum line length in the code style. [MaxLineLength]
src/main/kotlin/chapter03/Main.kt:11:39: This expression contains a magic number. Consider defining it to a well named constant. [MagicNumber]
src/main/kotlin/chapter03/Main.kt:11:42: This expression contains a magic number. Consider defining it to a well named constant. [MagicNumber]
src/main/kotlin/chapter03/Main.kt:11:45: This expression contains a magic number. Consider defining it to a well named constant. [MagicNumber]
src/main/kotlin/chapter03/TribuoAdapter.kt:48:1: Line detected, which is longer than the defined maximum line length in the code style. [MaxLineLength]
src/main/kotlin/dataset/Dataset.kt:6:1: Line detected, which is longer than the defined maximum line length in the code style. [MaxLineLength]
src/test/kotlin/chapter02/IrisPreprocessingTest.kt:188:1: Line detected, which is longer than the defined maximum line length in the code style. [MaxLineLength]
src/test/kotlin/chapter03/TribuoAdapterTest.kt:45:1: Line detected, which is longer than the defined maximum line length in the code style. [MaxLineLength]

* What went wrong:
Execution failed for task ':detekt' (registered by plugin 'io.gitlab.arturbosch.detekt').
> Analysis failed with 20 weighted issues.
```

指摘は 3 種類、合わせて 20 件でした。

| ルール | 件数 | 内容 |
|--------|------|------|
| PackageNaming | 11 | パッケージ名 `chapter01` などに数字が入っている |
| MaxLineLength | 5 | 1 行が 120 文字を超えている |
| MagicNumber | 4 | 意味の分からない数値がコードに直接書かれている |

### 指摘ごとに対応を決める

指摘は、すべてコードを直すとも、すべて設定で消すとも決めず、1 つずつ理由を考えて対応しました。

**PackageNaming（設定で合わせる）**: 既定のパターン `[a-z]+` は、パッケージ名の最初の部分に英小文字しか認めません。本シリーズでは、記事の章とコードの対応が分かるように、`chapter01` のような章ごとのパッケージ名を全言語で使っています。この命名は意図したものなので、数字を認めるパターンに変えます。

**MaxLineLength（設定で合わせる）**: detekt の既定の上限は 120 文字ですが、ktlint の `ktlint_official` は 140 文字です。2 つのツールの上限が違うと、ktlint が整形したコードを detekt が指摘する、という食い違いが起きます。整形を担う ktlint に合わせて 140 文字にします。

**MagicNumber（コードを直す）**: 第 1 章の `predictByRule` の `20` は、「20 代ならきのこ派」というルールの年代です。数値のままでは、この `20` が何を表すのかがコードから読み取れません。名前付きの定数にしました。

```kotlin
/** 「20 代ならきのこ派」というルールの年代 */
private const val KINOKO_AGE_GROUP = 20

fun predictByRule(features: Features): String = if (features.ageGroup == KINOKO_AGE_GROUP) "きのこ" else "たけのこ"
```

振る舞いを変えないリファクタリングなので、第 1 章のテストが通ったままであることを確かめてからコミットしました。

**MagicNumber（設定で合わせる）**: 第 3 章の `Main.kt` への 3 件は、次の行の `3`・`4`・`5` です（`1` と `2` は detekt が既定で許す数値です）。

```kotlin
private val MAX_DEPTHS = listOf(1, 2, 3, 4, 5, null)
```

これは「表に並べる深さの一覧」に `MAX_DEPTHS` という名前を付けたプロパティで、値の意味は名前が表しています。detekt の既定でも、`const val` の定数とテストのコードは MagicNumber の対象外です。`3`・`4`・`5` にさらに名前を付けても読みやすくはならないので、名前の付いたプロパティの値は対象外にする設定を使います。

3 つの判断を `detekt.yml` にまとめました。

```yaml
# detekt の既定の設定（buildUponDefaultConfig）から、このプロジェクトで変える項目だけを書く
naming:
  PackageNaming:
    # 章ごとのパッケージ名（chapter01 など）に数字を使う
    packagePattern: '[a-z][a-z0-9]*(\.[a-z][A-Za-z0-9]*)*'

style:
  MaxLineLength:
    # ktlint（ktlint_official）の上限にそろえる
    maxLineLength: 140
  MagicNumber:
    # 名前の付いたプロパティに並べた値（例: MAX_DEPTHS）は、名前が値の意味を表している
    ignorePropertyDeclaration: true
```

```bash
./gradlew detekt
```

```text
> Task :detekt
BUILD SUCCESSFUL in 1s
```

### detekt の注意点

detekt 1.23.8 のプラグインは、Gradle 10 で削除される予定の API を使っています。`--warning-mode all` を付けると、次の警告が表示されます。

```text
The ReportingExtension.file(String) method has been deprecated. This is scheduled to be removed in Gradle 10. Please use the getBaseDirectory().file(String) or getBaseDirectory().dir(String) method instead.
```

detekt の次の大きな版（2.0 系）は、執筆時点では alpha 版しか出ていません。本リポジトリでは安定版の 1.23.8 を使い、Gradle 10 に上げる前に 2.0 系の安定版へ移行すると決めています（[ADR 002](../../../adr/002-kotlin-ml-libraries.md)）。ツールを選ぶときは、機能だけでなく、ビルドツールの版とどこまで一緒に上げられるかも確かめておきます。

## 5.6 型チェック — Kotlin コンパイラ

Python 版では、型ヒントを mypy で検査しました。Kotlin は静的型付けの言語なので、型チェックはコンパイラが行い、型が合わないコードはそもそもビルドできません。

第 1 部でも、型のおかげでテストを実行する前に気づけた問題がありました。

- 第 2 章: 関数が無いと戻り値の型も分からず、`df["がく片幅"][0]` の添字アクセスまで連鎖してエラーになった
- 第 2・3 章: 欠損値を含む列は `Double?` になり、null を確かめない限り `Double` として使えなかった
- 第 3 章: `sealed interface Tree` の `when` に場合分けの漏れがあれば、コンパイルエラーになる

一方で、型で守られない部分もあります。Kotlin DataFrame の `AnyFrame` は列の型を静的には決めていないので、第 3 章では `(it as Number).toDouble()` のように実行時の型変換を書きました。型変換が失敗すれば実行時に例外になります。こうした場所は、テストで守る必要があります。

## 5.7 コードカバレッジ — Kover

テストがプロダクションコードのどこを実行したかを Kover で計測します。Kover も Gradle のプラグインとして追加しただけで、設定は書いていません。

```bash
./gradlew koverLog
```

学習データを配置した環境での結果です。

```text
application line coverage: 100%
```

学習データが無い環境での結果です。

```bash
ML_DATA_DIR=/nonexistent ./gradlew koverLog
```

```text
application line coverage: 82.3529%
```

データが無いと、実データのテストがスキップされ、各章の `main` 関数などが実行されないので、カバレッジが下がります。Python 版と同じく、カバレッジの数値を比べるときは、データのある環境で計測したものかを確認してください。

行ごとにどこが実行されたかは、HTML のレポートで確認できます。

```bash
./gradlew koverHtmlReport
```

レポートは `build/reports/kover/html/index.html` に出力されます。

本リポジトリでは、カバレッジの下限（`koverVerify` のルール）を設けていません。CI には学習データを置けないので、CI で計測したカバレッジは手元より必ず低くなり、下限を決めても手元と CI で意味が変わってしまうためです。

## 5.8 まとめ

この章では、再現できるビルドと、実行せずに問題を見つける仕組みを整えました。

1. **Gradle とバージョンカタログ** — 依存ライブラリとプラグインの正確な版を `libs.versions.toml` にまとめ、構成（`implementation`・`runtimeOnly`・`testImplementation`）で使いみちを分ける。範囲指定の版が無いので、ロックファイルは使わない
2. **Wrapper とツールチェーン** — Gradle の版とチェックサムを Wrapper に、コンパイルとテストの JDK を `jvmToolchain` に、Gradle デーモンの JDK を `gradle-daemon-jvm.properties` に固定する
3. **ktlint** — 書式を検査・整形する。整形の結果が読みにくいときは、コードの形を見直す
4. **detekt** — コードの中身の問題を検査する。指摘は 1 つずつ理由を考えて、コードを直すか設定で合わせるかを決める
5. **型と Kover** — 型チェックはコンパイラが行う。カバレッジは Kover で計測し、データが無い環境では数値が下がることに注意する

次の章では、これらを Gradle のタスクとしてまとめ、GitHub Actions で自動実行します。
