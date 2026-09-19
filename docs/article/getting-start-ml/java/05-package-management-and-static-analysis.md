---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "Gradle のバージョンカタログと JUnit の BOM、依存のロックを試した結果、Wrapper・JDK ツールチェーン・Gradle デーモンの JDK の固定、Spotless・Error Prone・PMD による静的解析と、わざと違反を入れて検査が効いていることを確かめる手順、JaCoCo によるカバレッジ計測を学ぶ。"
tags: [article,getting-start-ml,java]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T16:30:00Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、実験を再現するには乱数のシードに加えて、JDK・ライブラリ・Gradle のバージョンを固定する必要があると述べました。この章では、それを担う Gradle の仕組みと、コードを実行せずに問題を見つける **静的解析**、テストがコードのどこを通ったかを測る **カバレッジ** を整えます。

| 道具 | 役割 | 本章の節 |
|------|------|---------|
| [Gradle](https://gradle.org/)（Kotlin DSL・バージョンカタログ） | 依存ライブラリとビルドの手順を管理する | 5.2 |
| Gradle Wrapper・JDK ツールチェーン | Gradle と JDK のバージョンを固定する | 5.3 |
| [Spotless](https://github.com/diffplug/spotless)（[google-java-format](https://github.com/google/google-java-format)） | コードの整形と、整形の崩れの検査 | 5.4 |
| [Error Prone](https://errorprone.info/) | コンパイル時に、バグになりやすい書き方を検査する | 5.5 |
| [PMD](https://pmd.github.io/) | コードの規約と問題の検査 | 5.5 |
| 自作のタスク `verifyNoBomCharacter` | ソースに BOM の文字がそのまま入っていないかの検査 | 5.6 |
| Java コンパイラ | 型チェック | 5.7 |
| [JaCoCo](https://www.jacoco.org/jacoco/) | テストのカバレッジ計測 | 5.8 |

本章のバージョンは、執筆時点の `gradle/libs.versions.toml` と Wrapper の設定に書かれたものです（Gradle 9.7.1、JDK 21、JUnit 6.1.3、Spotless 8.10.2、google-java-format 1.36.1、Error Prone 2.50.0、PMD 7.27.0、JaCoCo 0.8.15）。選定の理由は [ADR 005](../../../adr/005-java-ml-libraries.md) を参照してください。

[Kotlin 版の第 5 章](../kotlin/05-package-management-and-static-analysis.md) では、ktlint・detekt・Kover を使いました。Java 版では、同じ役割を Spotless・Error Prone と PMD・JaCoCo が担います。Kotlin 版と違うのは、最初の実装（B24 のウォーキングスケルトン）の時点で、これらをすべて `./gradlew check` に組み込んでおいたことです。Kotlin 版は章を書き進めてから detekt を入れ、20 件の指摘に 1 つずつ対応しました。Java 版は指摘が 0 件の状態から始め、指摘が出たらその場で直してきました。

## 5.2 Gradle によるパッケージ管理

### 2 つの設定ファイル

Gradle の設定は、Kotlin で書くスクリプト（Kotlin DSL）の 2 つのファイルに分かれています。アプリのコードは Java ですが、ビルドの設定は Kotlin 版と同じ Kotlin DSL で書いています。

| ファイル | 役割 |
|---------|------|
| `settings.gradle.kts` | プロジェクトの名前と、ビルド全体に効く設定（JDK の自動取得など） |
| `build.gradle.kts` | プラグイン・依存ライブラリ・タスクの設定 |

`build.gradle.kts` の先頭では、プラグインと依存ライブラリを宣言しています。

```kotlin
plugins {
    java
    pmd
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

repositories {
    mavenCentral()
}

dependencies {
    errorprone(libs.errorprone.core)
    implementation(libs.tribuo.classification.tree)
    implementation(libs.tribuo.classification.sgd)
    implementation(libs.tribuo.regression.slm)
    implementation(libs.tribuo.regression.sgd)
    implementation(libs.tribuo.clustering.kmeans)
    implementation(libs.tribuo.math)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

`plugins` のうち、`java`・`pmd`・`jacoco` は Gradle に組み込まれているプラグインなので、版を書きません。版は、使っている Gradle（9.7.1）の版で決まります。Spotless と Error Prone は外部のプラグインなので、バージョンカタログから版を指定します。

### バージョンカタログ

依存ライブラリとプラグインの版は、`gradle/libs.versions.toml`（**バージョンカタログ**）の 1 か所にまとめています。

```toml
[versions]
junit = "6.1.3"
assertj = "3.27.7"
spotless = "8.10.2"
google-java-format = "1.36.1"
errorprone = "2.50.0"
errorprone-plugin = "5.1.1"
pmd = "7.27.0"
jacoco = "0.8.15"
tribuo = "4.3.2"

[libraries]
junit-bom = { module = "org.junit:junit-bom", version.ref = "junit" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
assertj-core = { module = "org.assertj:assertj-core", version.ref = "assertj" }
tribuo-classification-tree = { module = "org.tribuo:tribuo-classification-tree", version.ref = "tribuo" }
tribuo-classification-sgd = { module = "org.tribuo:tribuo-classification-sgd", version.ref = "tribuo" }
tribuo-regression-slm = { module = "org.tribuo:tribuo-regression-slm", version.ref = "tribuo" }
tribuo-regression-sgd = { module = "org.tribuo:tribuo-regression-sgd", version.ref = "tribuo" }
tribuo-clustering-kmeans = { module = "org.tribuo:tribuo-clustering-kmeans", version.ref = "tribuo" }
tribuo-math = { module = "org.tribuo:tribuo-math", version.ref = "tribuo" }
errorprone-core = { module = "com.google.errorprone:error_prone_core", version.ref = "errorprone" }

[plugins]
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
errorprone = { id = "net.ltgt.errorprone", version.ref = "errorprone-plugin" }
```

- `[versions]` に版を書き、`[libraries]` と `[plugins]` から `version.ref` で参照します
- カタログに書いた名前は、`build.gradle.kts` から `libs.` で始まる名前で使えます。`tribuo-classification-tree` の `-` は `.` に変わり、`libs.tribuo.classification.tree` になります
- `google-java-format`・`pmd`・`jacoco` の版は、ライブラリとしてではなく、各ツールの設定から `libs.versions.pmd.get()` のように読み出して使います（5.4〜5.8 節）
- Tribuo のモジュールは、第 3 章で使う `tribuo-classification-tree` に加えて、第 7〜14 章で使うものを先に入れてあります

Kotlin 版のカタログにあった `kotlin` の版は、Java 版にはありません。Java の標準ライブラリは JDK に含まれているので、JDK の版（5.3 節）を固定すれば、第 4 章の `java.util.Random` も一緒に固定されます。

### JUnit の BOM

`[libraries]` の `junit-jupiter` と `junit-platform-launcher` には、`version.ref` がありません。版は **BOM**（Bill of Materials）の `junit-bom` から決まります。

```kotlin
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
```

JUnit は、テストを書くための `junit-jupiter`、テストを実行する `junit-platform-launcher` など、いくつものモジュールに分かれています。これらは同じ版どうしで使う前提で作られていて、版がずれると実行時にエラーになることがあります。BOM は「このモジュール群はこの版の組み合わせで使う」という一覧で、`platform(...)` で読み込むと、版を書かなかったモジュールの版を BOM が決めます。版を書くのは `junit-bom` の 1 か所だけなので、JUnit を上げるときも `junit = "6.1.3"` の 1 行を変えれば、すべてのモジュールがそろって上がります。

`junit-platform-launcher` を `testRuntimeOnly` で明示しているのは、Gradle 9 からは、テストの実行に使う launcher をプロジェクトの依存として宣言する必要があるからです。

### 本番依存と開発依存

`dependencies` の中では、**構成**（configuration）で依存の使いみちを分けます。

| 構成 | 中身 | 判断の基準 |
|------|------|----------|
| `implementation` | Tribuo | コンパイルにも実行にも必要なもの |
| `testImplementation` | JUnit Jupiter・AssertJ | テストのコンパイルと実行だけに必要なもの |
| `testRuntimeOnly` | JUnit Platform Launcher | テストのコードからは直接呼ばず、テストを実行するときだけ必要なもの |
| `errorprone` | Error Prone | アプリとは無関係で、コンパイラに組み込んで検査に使うもの |

`errorprone` は、Error Prone のプラグインが作る構成です。ここに入れた依存は、アプリのクラスパスに混ざりません。Kotlin 版では ktlint のために自分で構成を作りましたが、Java 版ではプラグインが用意してくれます。

### 依存関係を確かめる

依存関係は `dependencies` タスクで確認できます。直接の依存関係は、行頭が `+---` か `\---` の行です。テストの実行時のクラスパスで確かめます。

```bash
./gradlew dependencies --configuration testRuntimeClasspath
```

直接の依存関係の行だけを抜き出したものです。

```text
+--- org.tribuo:tribuo-classification-tree:4.3.2
+--- org.tribuo:tribuo-classification-sgd:4.3.2
+--- org.tribuo:tribuo-regression-slm:4.3.2
+--- org.tribuo:tribuo-regression-sgd:4.3.2
+--- org.tribuo:tribuo-clustering-kmeans:4.3.2
+--- org.tribuo:tribuo-math:4.3.2 (*)
+--- org.junit:junit-bom:6.1.3
+--- org.junit.jupiter:junit-jupiter -> 6.1.3
+--- org.assertj:assertj-core:3.27.7
\--- org.junit.platform:junit-platform-launcher -> 6.1.3
```

`org.junit.jupiter:junit-jupiter -> 6.1.3` は、版を書かなかった依存に、BOM が 6.1.3 を割り当てたことを表します。`(*)` は、同じ依存関係の木がすでに上に表示されたので省略した、という印です。

### 依存関係のロックを試す

Python 版では、`uv.lock` に依存関係の依存関係まで正確な版を記録しました。Gradle にも同じ役割の **dependency locking**（`gradle.lockfile`）があります。Kotlin 版は「範囲指定の版が無いので使わない」と判断しました。Java 版では、実際にロックを有効にして、何が起きるかを確かめてから判断しました。

まず、すべての構成の依存関係に範囲指定や動的な版が無いかを調べます。`dependencies` タスクの出力を検索しましたが、`[1.0,2.0)` のような範囲指定、`1.+` のような動的な版、`latest.release` は 1 件もありませんでした。複数の依存関係が同じライブラリの違う版を求めた箇所（`->` の行）は、次のとおりです。

```text
com.google.errorprone:error_prone_annotations:2.10.0 -> 2.50.0
com.google.errorprone:error_prone_annotations:2.41.0 -> 2.50.0
com.google.guava:guava:32.0.1-jre -> 33.5.0-jre (*)
com.google.guava:guava:32.1.3-jre -> 33.5.0-jre (*)
org.junit.jupiter:junit-jupiter -> 6.1.3
org.junit.platform:junit-platform-launcher -> 6.1.3
```

どれも Gradle が最も新しい版を選んだか、BOM が版を決めた結果で、解決された版は POM に書かれた正確な版だけから決まっています。

次に、`apps/java` をスクラッチパッドに複製し、`build.gradle.kts` の末尾にロックの設定を足して、ロックファイルを書き出しました（本物の `build.gradle.kts` は変えていません）。

```kotlin
dependencyLocking {
    lockAllConfigurations()
}
```

```bash
./gradlew dependencies --write-locks
```

```text
BUILD SUCCESSFUL in 1m 1s
```

`gradle.lockfile`（91 行）と `settings-gradle.lockfile` ができました。`gradle.lockfile` には、依存関係の依存関係まで、どの構成で使われるかと一緒に記録されます。

```text
# This is a Gradle generated file for dependency locking.
# Manual edits can break the build and are not advised.
# This file is expected to be part of source control.
# To regenerate this file, run: ./gradlew :dependencies --write-locks
com.github.ben-manes.caffeine:caffeine:3.0.5=annotationProcessor,testAnnotationProcessor
...
org.junit:junit-bom:6.1.3=testCompileClasspath,testRuntimeClasspath
...
```

最後に、ロックがあるときに版を変えると何が起きるかを確かめました。カタログの `junit` を 6.1.2 に下げて、テストをコンパイルしました。

```text
> Task :compileTestJava
BUILD SUCCESSFUL in 45s
```

ビルドは成功しましたが、依存関係を表示すると、6.1.2 に下がっていませんでした。

```text
+--- org.junit:junit-bom:6.1.2 -> 6.1.3
```

ロックファイルに記録された 6.1.3 が制約として働き、カタログに書いた 6.1.2 より優先されたのです。エラーにも警告にもならないので、カタログだけを見て「6.1.2 にした」と思い込んでしまいます。ロックを使うなら、版を変えるたびに `--write-locks` でロックファイルも書き直す必要があります。

以上から、Java 版でもロックは使わないことにしました。

- 直接の依存関係は、バージョンカタログに正確な版で書いている
- 依存関係の依存関係の版は、各ライブラリの公開済みの POM に書かれていて、後から変わらない。範囲指定や動的な版は無い
- ロックを入れると、版を書く場所がカタログとロックファイルの 2 か所になり、上のように片方だけを変えたときに気付きにくい

依存関係の中に範囲指定や動的な版が入ってきたら、そのときに dependency locking を導入します。

## 5.3 JDK と Gradle のバージョンを固定する

Java のプログラムは JDK の上で動き、ビルドは Gradle が行います。どちらも版が変わると動作が変わりうるので、Python 版で `.python-version` を置いたのと同じように固定します。仕組みは Kotlin 版と同じなので、ここでは Java 版の設定を確かめるにとどめます。詳しい説明と、固定するまでに起きた問題は [Kotlin 版の 5.3 節](../kotlin/05-package-management-and-static-analysis.md) を参照してください。

### Gradle Wrapper

`gradle/wrapper/gradle-wrapper.properties` は Kotlin 版と同じ Gradle 9.7.1 を指し、ダウンロードした Gradle を `distributionSha256Sum` で検証します。

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

`gradlew` の改行コードは、リポジトリの `.gitattributes` の `gradlew text eol=lf` で LF に固定され、実行権限付きで記録されています。

```bash
git ls-files -s apps/java/gradlew
```

```text
100755 249efbb032ce46a80c687c0723eb172e85f6a136 0	apps/java/gradlew
```

### JDK ツールチェーンと Gradle デーモンの JDK

コンパイルとテストに使う JDK は、`build.gradle.kts` のツールチェーンで 21 に固定しています。Kotlin 版の `jvmToolchain(21)` に当たる書き方です。

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

手元に JDK 21 が無ければ、`settings.gradle.kts` の foojay プラグインが自動でダウンロードします。

```kotlin
plugins {
    // toolchain で指定した JDK が無ければ自動で取得する
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "getting-started-ml"
```

Gradle 本体（Gradle デーモン）が使う JDK も、`gradle/gradle-daemon-jvm.properties` の `toolchainVersion=21` で固定しています。

```bash
./gradlew --version
```

```text
Gradle 9.7.1
Kotlin:        2.4.0
Launcher JVM:  25.0.2 (Oracle Corporation 25.0.2+10-69)
Daemon JVM:    Compatible with Java 21, any vendor, nativeImageCapable=false (from gradle/gradle-daemon-jvm.properties)
```

手元の既定の JDK は 25 ですが、デーモンは JDK 21 で動きます。

Java 版では、JDK 21 の固定に Kotlin 版とは別の理由もあります。ADR 005 で確かめたとおり、Error Prone 2.50.0 と google-java-format 1.36.1 のクラスファイルは Java 21 向けで、JDK 21 より古い JDK では動きません。第 4 章で見た `Collections.shuffle` の並べ方を固定する役目と合わせて、JDK 21 はこのプロジェクトの前提です。

| 何の JDK か | 固定する場所 | 版 |
|-----------|------------|----|
| コンパイル・テスト・Error Prone | `build.gradle.kts` の `java.toolchain` | 21 |
| Gradle デーモン（Spotless・PMD・JaCoCo のタスクの実行） | `gradle/gradle-daemon-jvm.properties` | 21 |
| `gradlew` の起動 | 固定しない（手元の既定の JDK） | 手元では 25 |

## 5.4 コードスタイル — Spotless と google-java-format

### Spotless の設定

コードの整形と検査には、Spotless のプラグインから google-java-format を呼び出して使います。

```kotlin
// 整形は google-java-format に任せる。spotlessCheck は check タスクから実行される
spotless {
    java {
        googleJavaFormat(
            libs.versions.google.java.format
                .get(),
        )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}
```

- `java` の中の `googleJavaFormat(...)` で、Java のソースを google-java-format で整形します。版はバージョンカタログから読み出します
- `kotlinGradle` で、`build.gradle.kts` などの Gradle のスクリプトも ktlint で整形します。ビルドの設定も Kotlin のコードなので、書式をそろえておきます
- Spotless のプラグインは `check` タスクに `spotlessCheck` を自動で加えます

google-java-format には設定項目がほとんどありません。字下げは 2 文字、1 行は 100 文字までといった書式は、Google Java Style Guide で決まっています。Kotlin 版で ktlint と detekt の 1 行の上限をそろえる必要があったような、ツールどうしの調整は要りません。

### Spotless の実行

```bash
# 検査する（CI 向け。check にも含まれる）
./gradlew spotlessCheck

# 整形する
./gradlew spotlessApply
```

整形が崩れたファイルがあると、`spotlessCheck` は差分を表示して失敗します。確かめるために、整形の崩れたファイルを一時的に置きました（5.5 節の最後で、確かめ方をまとめます）。

```text
> Task :spotlessJavaCheck FAILED
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':spotlessJavaCheck' (registered in build file 'build.gradle.kts').
> The following files had format violations:
      src/main/java/violation/DeadExceptionSample.java
          @@ -5,7 +5,7 @@
           ··private·DeadExceptionSample()·{}
           ··/**·値をそのまま返す。·*/
          -··public·static·int·check(int·value){
          -······return·value;
          +··public·static·int·check(int·value)·{
          +····return·value;
           ··}
           }
  Run './gradlew spotlessApply' to fix all violations.
```

空白が `·` で表示されるので、どこに空白が足りないか、字下げがいくつずれているかが分かります。直し方（`./gradlew spotlessApply`）も表示されます。本シリーズでは、コミットの前に `./gradlew spotlessApply check` を実行して、整形してから検査することにしています。

## 5.5 静的コード解析 — Error Prone と PMD

### 3 つのツールの役割

Kotlin 版では、ktlint が書式を、detekt がコードの中身を検査しました。Java 版では、コードの中身の検査を Error Prone と PMD の 2 つで分担しています。

| 観点 | Spotless（google-java-format） | Error Prone | PMD（quickstart） |
|------|------------------------------|-------------|------------------|
| いつ動くか | `spotlessCheck` タスク | コンパイルのたび（`javac` のプラグイン） | `pmdMain`・`pmdTest` タスク |
| 字下げ・空白・改行の位置 | 検査・整形する | 扱わない | 扱わない |
| バグになりやすい書き方（例外を投げ忘れる、など） | 扱わない | 検査する | 一部を検査する |
| 使わない変数 | 扱わない | 検査する（`UnusedVariable`） | 検査する（`UnusedLocalVariable`） |
| 規約（波かっこの省略、パッケージの無いクラス、など） | 扱わない | 扱わない | 検査する |
| 自動修正 | できる | 修正案を表示する | しない |

Error Prone は Google が作った、`javac` に組み込んで動く検査ツールです。コンパイルの一部として動くので、型の情報を使って「例外を作ったのに投げていない」のような、コンパイラなら通してしまう間違いを見つけます。PMD は、ソースコードの構文木を規則と照らし合わせる検査ツールで、規約の違反を広く見つけます。規約の検査にはほかに Checkstyle がありますが、書式は Spotless、規約は PMD、バグになりやすい書き方は Error Prone で分担でき、規約の検査を 2 つ持つ必要が無いので採用していません（ADR 005）。

### Error Prone の設定

Error Prone はプラグインを入れ、`errorprone` 構成に本体を追加すると、すべての `JavaCompile` タスクで動きます。加えて、コンパイラの警告をエラーにする設定をしています。

```kotlin
// Error Prone の指摘を警告ではなくエラーにする
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
```

- `-Xlint:all` は、`javac` 自身の警告をすべて有効にします
- `-Werror` は、警告が 1 件でもあればコンパイルを失敗させます。Error Prone の指摘は、重大なものが「エラー」、それ以外が「警告」に分かれていますが、`-Werror` によって警告の指摘でもビルドが止まります

警告を警告のままにしておくと、出力に埋もれて誰も読まなくなり、やがて数十件の警告が当たり前になります。最初から警告をエラーとして扱えば、指摘はそのつど直すしかなくなり、警告が 0 件の状態を保てます。

### PMD の設定

PMD は Gradle に組み込まれているプラグインで、ルールセットのファイルを指定します。

```kotlin
// PMD の既定のルールセット（quickstart）で検査する。違反があれば check が失敗する
pmd {
    toolVersion = libs.versions.pmd.get()
    ruleSetFiles = files("config/pmd/ruleset.xml")
    ruleSets = emptyList()
    isConsoleOutput = true
}
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ruleset name="getting-started-ml"
         xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0 https://pmd.sourceforge.io/ruleset_2_0_0.xsd">
  <description>PMD の quickstart ルールセットをそのまま使う</description>
  <rule ref="rulesets/java/quickstart.xml"/>
</ruleset>
```

- `config/pmd/ruleset.xml` は、PMD が用意している **quickstart** のルールセットをそのまま参照します。quickstart は、PMD のすべてのルールのうち、多くのプロジェクトでそのまま使えるものを PMD の開発者が選んだものです
- `ruleSets = emptyList()` で、Gradle のプラグインが既定で設定するルールセットを外し、`ruleSetFiles` のファイルだけを使います
- `isConsoleOutput = true` で、違反を HTML のレポートだけでなく、コンソールにも表示します。CI のログで違反の内容が読めるようにするためです
- PMD のプラグインは、`main` と `test` のソースごとに `pmdMain`・`pmdTest` タスクを作り、`check` に加えます。テストのコードも検査の対象です

Kotlin 版の detekt では、既定の設定を 3 か所変えました。Java 版では quickstart から何も変えていません。最初から有効にしていたので、既定のルールに合わない書き方をした時点で、そのつど直してきたからです。

### わざと違反を入れて、検査が効いていることを確かめる

静的解析のツールは、設定を間違えると、何も検査していないのに成功します。F# 版では、静的解析のルールが 1 件も有効になっていないことに、しばらく気付きませんでした。成功しているのが「違反が無いから」なのか「検査していないから」なのかは、成功の出力を見ても区別できません。

そこで、B24（Java 版の最初の Bolt）では、**わざと違反を入れたファイルを置いて `./gradlew check` が失敗すること** を確かめてから第 1 章に入りました。この章を書くにあたり、同じ手順をもう一度実行して、出力を取り直しました（`apps/java` をスクラッチパッドに複製し、そこにファイルを置いています。パスは読みやすいように短くしています）。

**Error Prone のエラー（`DeadException`）**: 例外を作っただけで投げていないコードです。

```java
  public static int check(int value) {
    if (value < 0) {
      new IllegalArgumentException("負の値です");
    }
    return value;
  }
```

```text
> Task :compileJava
src/main/java/violation/DeadExceptionSample.java:10: エラー: [DeadException] Exception created but not thrown
      new IllegalArgumentException("負の値です");
      ^
    (see https://errorprone.info/bugpattern/DeadException)
  Did you mean 'throw new IllegalArgumentException("負の値です");'?
エラー1個
> Task :compileJava FAILED
```

`throw` を書き忘れても、Java の文法としては正しいので、`javac` だけではコンパイルが通ります。Error Prone は修正案（`Did you mean ...`）も表示します。

**Error Prone の警告（`UnusedVariable`）**: 計算したのに使っていない変数です。

```java
  public static int check(int value) {
    int doubled = value * 2;
    return value;
  }
```

```text
> Task :compileJava
src/main/java/violation/DeadExceptionSample.java:9: 警告: [UnusedVariable] The local variable 'doubled' is never read.
    int doubled = value * 2;
        ^
    (see https://errorprone.info/bugpattern/UnusedVariable)
  Did you mean to remove this line?
エラー: 警告が見つかり-Werrorが指定されました
エラー1個
警告1個
> Task :compileJava FAILED
```

指摘そのものは「警告」ですが、`-Werror` によってコンパイルが失敗しています。同じコードを PMD にかけると、PMD も `UnusedLocalVariable` として指摘します。

```text
src/main/java/violation/DeadExceptionSample.java:9:	UnusedLocalVariable:	Avoid unused local variables such as 'doubled'.
> Task :pmdMain FAILED
```

ところが、変数の名前を `unused` に変えると、Error Prone も PMD も指摘しなくなりました。

```java
    int unused = value * 2;
```

```text
> Task :compileJava
> Task :processResources NO-SOURCE
> Task :classes
> Task :pmdMain
BUILD SUCCESSFUL in 53s
```

どちらのツールも、`unused` という名前の変数を「意図して使わない変数」とみなします。たとえば、例外が投げられることだけを確かめたいテストで、戻り値を受ける変数が必要になったときに使える書き方です。ただし、名前を変えるだけで検査を外せるということでもあります。本シリーズでは、`unused` という名前を検査を黙らせるためには使わず、本当に使わない理由があるときだけ使うことにしています。

**PMD（`ControlStatementBraces`）**: 波かっこを省略した if です。

```java
  public static int check(int value) {
    if (value < 0) return 0;
    return value;
  }
```

```text
src/main/java/violation/DeadExceptionSample.java:9:	ControlStatementBraces:	This statement should have braces
> Task :pmdMain FAILED
```

**PMD（`NoPackage`）**: パッケージを宣言していないテストのクラスです。

```java
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoPackageTest {
  @Test
  @DisplayName("名前の無いパッケージに置いたテスト")
  void noPackage() {
    assertThat(1 + 1).isEqualTo(2);
  }
}
```

```text
src/test/java/NoPackageTest.java:6:	NoPackage:	All classes, interfaces, enums and annotations must belong to a named package
> Task :pmdTest FAILED
```

テストのコードも `pmdTest` で検査されるので、テストのクラスもパッケージに置く必要があります。B24 で書いた環境確認のテスト（`SetupTest`）も、このために `setup` パッケージに置いています。本シリーズの章のコードは `chapter01` のような章ごとのパッケージに置くので、ふだんは問題になりません。

**整形の崩れ（`spotlessJavaCheck`）**: 5.4 節で見たとおりです。

確かめたあとは、置いたファイルを消し、`./gradlew check` が成功に戻ることを確かめます。この手順は、ツールの版を上げたときや設定を変えたときにも繰り返す価値があります。

| わざと入れた違反 | 検出したツール | 指摘 | 失敗したタスク |
|----------------|--------------|------|--------------|
| 例外を作って投げない | Error Prone | `DeadException`（エラー） | `compileJava` |
| 使わない変数 | Error Prone、PMD | `UnusedVariable`（警告）、`UnusedLocalVariable` | `compileJava`（`-Werror`）、`pmdMain` |
| 波かっこの無い if | PMD | `ControlStatementBraces` | `pmdMain` |
| パッケージの無いクラス | PMD | `NoPackage` | `pmdTest` |
| 整形の崩れ | Spotless | 差分の表示 | `spotlessJavaCheck` |
| 使わない変数の名前が `unused` | なし | — | — |

### 第 1〜3 章で受けた指摘

第 1 部を書き進める間にも、これらのツールはいくつかの問題を見つけました。

- 第 1 章: 仮実装の段階で、本実装で使うつもりの `stripBom` メソッドを先に書いておいたところ、どこからも呼ばれていないメソッドとして Error Prone の `UnusedMethod` に指摘され、コンパイルが止まりました。TDD の「先回りして実装しない」を、ツールが守らせてくれた形です
- 第 2 章: 特徴量を record にして `double[]` の成分を持たせたところ、Error Prone の `ArrayRecordComponent` に指摘されました。record の `equals` は配列を中身ではなく参照で比べるからです。`Features` は record をやめてクラスにし、配列を写して持ち、`equals`・`hashCode`・`toString` を中身で書きました（ADR 005）

Kotlin 版の detekt のように、指摘を設定で消したことはまだありません。指摘は抑えずに直すのが原則で、どうしても抑える場合は、理由をコメントに書いてから抑えることにしています。

## 5.6 BOM の文字を検査する — verifyNoBomCharacter

第 1 章では、CSV の先頭にある BOM（U+FEFF）を取り除く処理を書きました。そのときのソースに、`\uFEFF` のエスケープではなく、BOM の文字そのものが入ってしまったことがあります。BOM の文字は画面に表示されないので、ソースを読んでも気付けません。第 4 章で見た 2 つの `fix(java)` のコミットは、これを直したものです。

困ったことに、BOM の文字がソースに入っていても、コンパイルも、Spotless も、Error Prone も、PMD も通ります。確かめるために、BOM の文字そのものを文字列に入れたファイルを置いて、それぞれのタスクを実行しました。

```text
> Task :compileJava
> Task :spotlessJava
> Task :spotlessJavaCheck
> Task :spotlessKotlinGradle UP-TO-DATE
> Task :spotlessKotlinGradleCheck UP-TO-DATE
> Task :spotlessCheck
> Task :processResources NO-SOURCE
> Task :classes
> Task :pmdMain
BUILD SUCCESSFUL in 1m 10s
```

既存のツールでは見つからないので、第 2 章で、検査のタスクを自分で書いて `check` に加えました。

```kotlin
// ソースに BOM の文字（U+FEFF）がそのまま入っていれば失敗する。BOM は \uFEFF のエスケープで書く
tasks.register("verifyNoBomCharacter") {
    group = "verification"
    description = "ソースに BOM の文字がそのまま入っていないかを検査する"
    val sources = fileTree("src") { include("**/*.java") }
    inputs.files(sources)
    doLast {
        val bom = "\uFEFF"
        val found = sources.files.filter { it.readText(Charsets.UTF_8).contains(bom) }
        if (found.isNotEmpty()) {
            throw GradleException("BOM の文字が入っているファイル: ${found.joinToString { it.name }}（\\uFEFF のエスケープで書く）")
        }
    }
}

tasks.named("check") {
    dependsOn("verifyNoBomCharacter")
}
```

- `fileTree("src") { include("**/*.java") }` は、`src` 以下のすべての Java のファイルです
- `inputs.files(sources)` で、ソースをタスクの入力として宣言します。ソースが変わらなければ、2 回目からは実行を飛ばします
- ビルドスクリプトは Kotlin なので、`"\uFEFF"` は Kotlin の文字列のエスケープです。ビルドスクリプト自身にも BOM の文字をそのまま書かずに済みます
- 失敗のメッセージには、見つかったファイルの名前と直し方を書いています

同じファイルで、このタスクは失敗しました。

```text
> Task :verifyNoBomCharacter FAILED
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':verifyNoBomCharacter' (registered in build file 'build.gradle.kts').
> BOM の文字が入っているファイル: DeadExceptionSample.java（\uFEFF のエスケープで書く）
```

一度起きた問題を、人の注意ではなく機械の検査で防ぐようにしておくと、同じ間違いを繰り返しません。ツールが無ければ、小さなタスクを書けば済みます。

## 5.7 型チェック — Java コンパイラ

Python 版では、型ヒントを mypy で検査しました。Java は静的型付けの言語なので、型チェックはコンパイラが行い、型が合わないコードはそもそもビルドできません。第 1 部でも、型のおかげで気づけた問題がありました。

- 第 2 章: 欠損値を含むかもしれないセルは `OptionalDouble` で返すので、値が無い場合の扱いを書かない限り `double` として使えません
- 第 3 章: 決定木を `sealed interface Tree` と、それを実装する record の `Node`・`Leaf` で表しました。`switch` のパターンマッチで場合分けすると、実装の漏れはコンパイルエラーになります

Kotlin の `Double?` と違い、Java の参照型はどれも null になりえます。`null` を返さず `Optional` を使う、record の成分を検査する、といった約束はコンパイラでは守られないので、Error Prone の検査とテストで守ります。

## 5.8 コードカバレッジ — JaCoCo

テストがプロダクションコードのどこを実行したかを JaCoCo で計測します。JaCoCo は Gradle に組み込まれているプラグインで、版と、レポートの形式だけを設定しています。

```kotlin
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// CI でカバレッジを表示するため、HTML に加えて CSV を出す
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        csv.required = true
    }
}
```

`jacoco` のプラグインを入れると、`test` タスクの実行中にどの命令が実行されたかが記録されます。`jacocoTestReport` タスクがそれをレポートにします。レポートは `build/reports/jacoco/test/html/index.html` に出力されます。

Kover の `koverLog` のように、カバレッジの数値をコンソールに表示するタスクは JaCoCo にはありません。そこで、CSV のレポートも出力し、命令（instruction）単位のカバレッジを `awk` で計算します。CSV の 4 列目が実行されなかった命令の数、5 列目が実行された命令の数です。

```bash
./gradlew jacocoTestReport
awk -F, 'NR>1{m+=$4;c+=$5}END{printf "Instruction coverage: %.1f%%\n", 100*c/(m+c)}' build/reports/jacoco/test/jacocoTestReport.csv
```

学習データを配置した環境での結果です。

```text
Instruction coverage: 95.1%
```

学習データが無い環境（`ML_DATA_DIR=/nonexistent`）での結果です。

```text
Instruction coverage: 82.3%
```

データが無いと、実データのテストがスキップされ、各章の `Main` などが実行されないので、カバレッジが下がります。Python 版・Kotlin 版と同じく、カバレッジの数値を比べるときは、データのある環境で計測したものかを確認してください。

Kover は行単位で数えましたが、ここでは JaCoCo の命令単位で数えているので、Kotlin 版の数値とは単位が違います。数値の大小を言語の間で比べることはできません。

本リポジトリでは、カバレッジの下限（`jacocoTestCoverageVerification` のルール）を設けていません。CI には学習データを置けないので、CI で計測したカバレッジは手元より必ず低くなり、下限を決めても手元と CI で意味が変わってしまうためです。

## 5.9 まとめ

この章では、再現できるビルドと、実行せずに問題を見つける仕組みを整えました。

1. **Gradle とバージョンカタログ** — 依存ライブラリとプラグインの正確な版を `libs.versions.toml` にまとめ、構成（`implementation`・`testImplementation`・`testRuntimeOnly`・`errorprone`）で使いみちを分ける。JUnit のモジュールの版は BOM でそろえる
2. **ロックは試してから決める** — dependency locking を実際に有効にし、範囲指定の版が無いこと、ロックがカタログより優先されて気付きにくいことを確かめて、使わないと判断した
3. **Wrapper とツールチェーン** — Gradle の版とチェックサムを Wrapper に、コンパイルとテストの JDK をツールチェーンに、Gradle デーモンの JDK を `gradle-daemon-jvm.properties` に固定する
4. **Spotless・Error Prone・PMD** — 書式、バグになりやすい書き方、規約を分担して検査する。`-Werror` で警告もエラーにし、わざと違反を入れて検査が効いていることを確かめる
5. **足りない検査は自分で書く** — BOM の文字はどのツールも見つけないので、`verifyNoBomCharacter` タスクを `check` に加えた
6. **型と JaCoCo** — 型チェックはコンパイラが行う。カバレッジは JaCoCo で計測し、データが無い環境では数値が下がることに注意する

次の章では、これらを Gradle のタスクとしてまとめ、GitHub Actions で自動実行します。
