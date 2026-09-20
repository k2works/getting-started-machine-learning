---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "sbt による依存の宣言と Coursier の解決、project/build.properties による sbt の版の固定、scalafmt による整形、コンパイラの警告をエラーにする設定、scoverage によるカバレッジ計測を学ぶ。"
tags: [article,getting-start-ml,scala]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T03:25:00Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、何をコミットし、何をコミットしないかを決め、実験を再現するにはシードと版を固定する必要があることを確かめました。この章では、その「版を固定する」仕組みと、コードの品質を機械的に確かめる道具を整えます。

Scala 版で使う道具は次のとおりです。

| 役割 | 道具 | 設定ファイル |
|------|------|------------|
| ビルド・依存の宣言 | sbt | `build.sbt` |
| sbt 自身の版の固定 | sbt のランチャー | `project/build.properties` |
| sbt のプラグイン | sbt | `project/plugins.sbt` |
| 依存の取得 | Coursier（sbt に内蔵） | （`build.sbt` の宣言から解決） |
| 整形 | scalafmt（sbt-scalafmt） | `.scalafmt.conf` |
| 静的解析 | Scala コンパイラの警告 | `build.sbt` の `scalacOptions` |
| カバレッジ | scoverage（sbt-scoverage） | `project/plugins.sbt` |

Java 版・Kotlin 版は、Gradle に Spotless・Error Prone・PMD・ktlint・JaCoCo といったプラグインを足して品質を見ていました。Scala 版では、**整形は scalafmt、指摘はコンパイラ自身** という分担になります。別のツールを足すかわりに、コンパイラがすでに出している警告をエラーに格上げする、というのが Scala のやり方です。この違いがこの章の見どころです。

## 5.2 sbt によるパッケージ管理

### build.sbt

sbt の設定は `build.sbt` に書きます。設定ファイルそのものが Scala のコードで、`キー := 値` の形で値を与えます。

```scala
ThisBuild / scalaVersion := "3.3.6"
ThisBuild / organization := "machinelearning"

lazy val root = (project in file("."))
  .settings(
    name := "machine-learning",
    // 警告をエラーにする。使っていない値・import や、捨てている戻り値を見つける
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Wunused:all",
      "-Wvalue-discard",
      "-Xfatal-warnings"
    ),
    libraryDependencies ++= Seq(
      "org.tribuo" % "tribuo-classification-tree" % "4.3.2",
      "org.scalatest" %% "scalatest" % "3.2.20" % Test
    ),
    // 学習データの場所（未指定なら ../data/sukkiri-ml）をテストに渡す
    Test / envVars := sys.env.get("ML_DATA_DIR").map("ML_DATA_DIR" -> _).toMap
  )
```

`ThisBuild / scalaVersion` は「このビルド全体の設定」、`lazy val root = (project in file("."))` の `.settings(...)` は「このプロジェクトの設定」です。`Test / envVars` のように、`設定の範囲 / キー` と書けるのが sbt の特徴で、`Test / scalacOptions` と書けばテストのコンパイルにだけ効きます。

Gradle の `build.gradle.kts` も Kotlin のコードでしたが、こちらは `tasks.test { ... }` のようにブロックで囲んでタスクを設定しました。sbt は「キーに値を代入する」形に統一されています。どちらも設定を型付きの言語で書くので、IDE の補完と型のチェックが効きます。

### `%` と `%%`

依存の宣言に 2 種類の記号が出てきます。

```scala
"org.tribuo" % "tribuo-classification-tree" % "4.3.2",   // Java のライブラリ
"org.scalatest" %% "scalatest" % "3.2.20" % Test         // Scala のライブラリ
```

| 記号 | 意味 |
|------|------|
| `%` | 書いたままのアーティファクト名で探す。Java のライブラリはこちら |
| `%%` | アーティファクト名に Scala の版の接尾辞を足して探す（`scalatest_3`） |

Scala は版ごとにバイナリ互換性が切れるため、Scala で書かれたライブラリは `scalatest_3`・`scalatest_2.13` のように、版ごとに別のアーティファクトとして公開されます。`%%` は「今の `scalaVersion` に合うものを選べ」という指示です。JVM のライブラリである Tribuo は Java で書かれているので `%` で足ります。

この区別は、Scala 版が Tribuo を選べた理由にもつながっています。機械学習ライブラリの Smile には Scala 向けの API（`smile-scala_3`）もありますが、Scala 3 向けの版はすべて GPL-3.0 でした。Java の API をそのまま呼べるなら、`%` で Java のライブラリを足すほうが選択肢が広がります（[ADR 007](../../../adr/007-scala-ml-libraries.md)）。

### 本番依存とテスト依存

末尾の `% Test` は、その依存を **テストのコンパイルと実行のときだけ** クラスパスに載せる、という指定です。ScalaTest に `% Test` を付けておくと、本番のコードから間違ってテストのライブラリを使ってしまうことがコンパイルエラーになります。Gradle の `testImplementation`、.NET の開発用パッケージに当たるものです。

### 依存の取得 — Coursier

sbt は依存の解決と取得に [Coursier](https://get-coursier.io/) を使います。取得したファイルは、プロジェクトの中ではなく、ユーザーのホームのキャッシュ（Linux・macOS では `~/.cache/coursier`）に置かれます。同じライブラリを複数のプロジェクトで使っても、ダウンロードは 1 回で済みます。第 6 章で見るように、CI ではこのディレクトリをキャッシュします。

宣言した 2 つの依存が、実際には何を連れてくるかは `dependencyTree` で確かめられます。

```bash
sbt -batch --no-colors 'Compile / dependencyTree'
```

```text
[info] machinelearning:machine-learning_3:0.1.0-SNAPSHOT
[info]   +-org.scala-lang:scala3-library_3:3.3.6 [S]
[info]   +-org.tribuo:tribuo-classification-tree:4.3.2
[info]     +-org.tribuo:tribuo-classification-core:4.3.2
[info]     | +-com.oracle.labs.olcut:olcut-core:5.3.1
[info]     | | +-org.jline:jline-builtins:3.27.1
...
[info]     | +-org.tribuo:tribuo-common-tree:4.3.2
[info]     | | +-org.tribuo:tribuo-core:4.3.2
[info]     | | | +-com.google.protobuf:protobuf-java:3.25.6
```

`build.sbt` に書いたのは 1 行でも、Tribuo は分類の共通部分・決定木の共通部分・設定ライブラリ（OLCUT）・protobuf・jline を連れてきます。`[S]` が付いているのは Scala の標準ライブラリで、`scalaVersion` から自動で足されたものです。

ライブラリを足す前にこの木を見ておくと、「すでに入っているものを重ねて足していないか」を確かめられます。

### ロックファイルが無い

Java 版では Gradle の dependency locking、F# 版では NuGet の `packages.lock.json` と、依存の解決結果をファイルに固定する仕組みを扱いました。sbt には、標準では同じ仕組みがありません。本シリーズでは代わりに、**`build.sbt` に厳密な版だけを書く** ことで版を決めています。

- 版に範囲（`4.3.+` のような動的な版）を書かない
- 推移的に入ってくるライブラリの版は、必要になったときに `dependencyTree` で確かめ、固定したければ明示的に宣言する

この方針でも、`build.sbt` がコミットされていれば、直接の依存の版はどの環境でも同じになります。推移的な依存まで含めて完全に固定したい場合は、sbt のプラグインを足すか、社内のリポジトリで版を固定することになります。本シリーズの規模では、直接の依存が 2 つなので、プラグインを増やさないほうを選びました。

## 5.3 sbt と Scala と JDK の版を固定する

### project/build.properties

sbt には「sbt を起動するプログラム（ランチャー）」と「実際に動く sbt 本体」があります。本体の版を決めるのは、プロジェクトの中の 1 行です。

```text
sbt.version=1.12.9
```

これがあるので、手元に入っている sbt の版がいくつであっても、このプロジェクトのビルドは 1.12.9 で動きます。Nix の開発環境に入っているランチャーは 1.11.7 ですが、起動したときの表示は次のようになります。

```text
[info] sbt runner (sbt-the-shell-script) is a runner to run any declared version of sbt.
[info] Actual version of the sbt is declared using project/build.properties for each build.
  - sbt: sbt runner version: 1.11.7
...
[info] welcome to sbt 1.12.9 (Azul Systems, Inc. Java 21.0.8)
```

CI（GitHub Actions）では、キャッシュが無い最初の実行でランチャーが本体を取りに行く様子がログに残っていました。

```text
[info] [launcher] getting org.scala-sbt sbt 1.12.9  (this may take some time)...
[info] [launcher] getting Scala 2.12.21 (for sbt)...
[info] welcome to sbt 1.12.9 (N/A Java 21.0.9)
```

Gradle Wrapper と役割は同じですが、Gradle が `gradlew` というスクリプトと JAR をリポジトリに置くのに対し、sbt は **版を書いた 1 行だけ** で済みます。そのかわり、sbt のランチャー自体は各自の環境に必要です（本シリーズでは Nix の開発環境が用意します）。

なお、`Scala 2.12.21 (for sbt)` とあるとおり、sbt 本体は Scala 2.12 で動きます。ビルド対象のコードが Scala 3 でも、ビルド定義（`build.sbt`）は sbt の側の Scala で解釈されます。

### 版がずれると何が起きるか

この 1 行を軽く見て、Nix の開発環境に入っている sbt の版（1.12.0 のころ）に任せていた時期がありました。そのとき整形の検査を走らせると、こうなりました。

```bash
sbt -batch --no-colors scalafmtCheckAll
```

```text
[error] java.lang.RuntimeException: sbt-scalafmt requires sbt 1.12.9+ [current=1.12.0
[error] 	at scala.sys.package$.error(package.scala:30)
[error] 	at org.scalafmt.sbt.ScalafmtPlugin$FormatSession.$anonfun$new$1(ScalafmtPlugin.scala:178)
...
[error] (Compile / scalafmtCheck) sbt-scalafmt requires sbt 1.12.9+ [current=1.12.0
[error] (Test / scalafmtCheck) sbt-scalafmt requires sbt 1.12.9+ [current=1.12.0
[error] Total time: 0 s, completed 2026/09/20 11:55:29
```

sbt-scalafmt 2.6.2 は sbt 1.12.9 以上を求めます。`project/build.properties` に `sbt.version=1.12.9` と書けば、環境に入っている sbt が 1.12.0 でも本体の 1.12.9 が取得されて動きます。「プラグインの都合でビルドツールの版が決まる」ことは珍しくないので、ビルドツールの版は環境任せにせず、プロジェクトの中で宣言しておきます。

### project/plugins.sbt

sbt のプラグインは `project/plugins.sbt` に書きます。

```scala
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")
```

`project/` の下は「ビルドを作るためのビルド」です。ここに書いた依存は、ビルド定義をコンパイルするときに解決されます。だから `project/` はコミットし、その中に作られる `project/target/` は除外する、という前の章の `.gitignore` になります。

### Scala と JDK の版

Scala の版は `build.sbt` の `ThisBuild / scalaVersion := "3.3.6"` で固定します。3.3.x は Scala 3 の LTS（長期サポート）系列です。

JDK の版は、sbt の設定では固定していません。Nix の開発環境（`ops/nix/environments/scala/shell.nix`）が JDK を用意し、CI もその環境の中で動きます。前の章で見たとおり、乱数の再現性は `java.util.Random` の仕様で保証されるので、JDK の版が上がっても第 2 章の分け方は変わりません。JDK の版に敏感なのは、むしろ Tribuo のようなライブラリの側です。実測では、Nix の JDK 21.0.8 でも、ローカルにあった JDK 25.0.2 でも、テストは 43 件すべて通りました。

## 5.4 コードスタイル — scalafmt

### .scalafmt.conf

整形の設定は `.scalafmt.conf` に書きます。

```text
version = 3.9.4
runner.dialect = scala3
maxColumn = 100
```

3 行しかありませんが、どれも欠かせません。

| キー | 意味 |
|------|------|
| `version` | 使う scalafmt の版。**必須**で、書かないと実行できない。版を固定するので、整形の結果が環境で変わらない |
| `runner.dialect` | 構文の方言。Scala 3 なら `scala3` を明示する |
| `maxColumn` | 1 行の最大の長さ。既定は 80。本シリーズは日本語のコメントが多いので 100 にした |

`version` を設定ファイル側に書くのは scalafmt の作法です。sbt のプラグインの版（`sbt-scalafmt` 2.6.2）と、整形エンジンの版（scalafmt 3.9.4）が別に決まるので、プラグインを上げても整形結果は変わりません。逆に言えば、`version` を書かないと版が決まらないので、実行できません。実際に消して試すと、こうなりました。

```text
[error] org.scalafmt.sbt.ScalafmtSbtReporter$ScalafmtSbtError: scalafmt: missing setting 'version'. To fix this problem, add the following line to .scalafmt.conf: 'version=3.11.4'. [APP/.scalafmt.conf]
```

親切なことに、そのとき最新の版（この実行では 3.11.4）を書いた行まで提示してくれます。ただし、提示された版をそのまま貼ると、整形の結果が変わってプロジェクト全体に差分が出ることがあります。版を上げるのは意識して行い、そのときは `scalafmtAll` の結果をまとめて 1 つのコミットにします。

`runner.dialect` も、消すとエラーになりました。

```text
[error] (Compile / scalafmtCheck) org.scalafmt.sbt.ScalafmtSbtReporter$ScalafmtSbtError: scalafmt: Invalid config: Default dialect is deprecated; use explicit: [Sbt0137,Sbt1,Scala211,Scala212,Scala212Source3,Scala213,Scala213Source3,Scala3,Scala30,Scala31,Scala32,Scala33,Scala34,Scala35,Scala36,Scala3Future]
```

既定の方言に頼ることは非推奨で、どの方言かを明示せよ、という指摘です。第 3 章で使った `enum` や、波かっこを使わないインデント構文は Scala 3 の構文なので、`scala3` を選びます。

### scalafmt の実行

```bash
# 検査する（CI 向け。本番・テストの両方を見る）
sbt -batch --no-colors scalafmtCheckAll

# 整形する
sbt -batch --no-colors scalafmtAll
```

`scalafmtCheckAll` は、整形されていないファイルがあると失敗します。わざと空白と型注釈の書き方を崩したファイルを置いて実行すると、こうなりました。

```scala
object Violation:
  def show( counts : Vector[Int] ) : Int = counts.size
```

```text
[info] scalafmt: Checking 9 Scala sources (APP)...
[info] scalafmt: Checking 11 Scala sources (APP)...
[warn] scalafmt: APP/src/main/scala/machinelearning/Violation.scala isn't formatted properly!
[error] scalafmt: 1 files must be formatted (APP)
[error] (Compile / scalafmtCheck) scalafmt: 1 files must be formatted (APP)
```

`Checking ... sources` が 2 回出るのは、本番（`Compile`）とテスト（`Test`）を別々に検査しているからです。直すには `scalafmtAll` を実行するだけで、人が空白を数える必要はありません。

Kotlin 版の ktlint、Java 版の Spotless（google-java-format）、F# 版の Fantomas と、役割はまったく同じです。「整形は機械がやる、人はレビューで中身を見る」という分担にすることで、差分が読みやすくなり、スタイルの議論が減ります。

## 5.5 静的解析 — コンパイラの警告をエラーにする

### Scala のやり方

Java 版では Error Prone と PMD、Kotlin 版では ktlint、F# 版では FSharpLint と、別立てのツールで指摘を出していました。Scala では、指摘の多くをコンパイラ自身がすでに出しています。`build.sbt` の `scalacOptions` はそれを有効にし、かつ **無視できないようにする** 設定です。

```scala
    scalacOptions ++= Seq(
      "-deprecation",   // 非推奨の API を使ったら知らせる
      "-feature",       // 明示的に有効化すべき機能を使ったら知らせる
      "-unchecked",     // 型消去で確かめられない型の検査を知らせる
      "-Wunused:all",   // 使っていない import・ローカルの値・private の定義を知らせる
      "-Wvalue-discard",// 値を捨てていることを知らせる
      "-Xfatal-warnings"// すべての警告をエラーにする
    )
```

最後の `-Xfatal-warnings` が要です。警告のままだと、ログに流れて誰も読まなくなります。エラーにすれば、コンパイルが止まるので必ず向き合うことになります。「あとで直す」を許さない設定です。

### わざと違反を入れて、検査が効いていることを確かめる

設定を書いただけでは、本当に検査されているか分かりません。検査の仕組みを入れたら、**わざと違反を入れて落ちることを確かめる** のが確実です（複製したプロジェクトで試し、確かめたら消します）。

#### 使っていない import とローカルの値

```scala
package machinelearning

import java.util.Random

object Violation:
  def show(counts: Vector[Int]): Int =
    val unused = counts.sum
    counts.map(_ + 1)
    counts.size
```

```text
[error] -- [E198] Unused Symbol Error: APP/src/main/scala/machinelearning/Violation.scala:3:17
[error] 3 |import java.util.Random
[error]   |                 ^^^^^^
[error]   |                 unused import
[error] -- [E198] Unused Symbol Error: APP/src/main/scala/machinelearning/Violation.scala:8:8
[error] 8 |    val unused = counts.sum
[error]   |        ^^^^^^
[error]   |        unused local definition
[error] two errors found
[error] (Compile / compileIncremental) Compilation failed
```

`-Wunused:all` が、使っていない import と使っていないローカルの値の両方を捕まえました。`-Xfatal-warnings` があるので Error と表示され、コンパイルが止まります。

#### 値を捨てている

```scala
object Violation:
  def show(counts: Vector[Int]): Unit =
    counts.map(_ + 1)
```

```text
[error] -- [E175] Potential Issue Error: APP/src/main/scala/machinelearning/Violation.scala:6:14
[error] 6 |    counts.map(_ + 1)
[error]   |    ^^^^^^^^^^^^^^^^^
[error]   |discarded non-Unit value of type Vector[Int]. Add `: Unit` to discard silently.
[error] one error found
```

`Vector` は不変なので、`counts.map(...)` の結果を使わないコードは何もしていないのと同じです。`-Wvalue-discard` はこれを捕まえます。第 1 章から使っている `requireData(): Unit` という書き方は、この検査に「捨てるのは承知のうえ」と伝えるためのものでした。エラーメッセージ自身が `Add \`: Unit\` to discard silently.` と直し方を教えてくれます。

ここで 1 つ、実際に試して分かった細かい話があります。`-Wvalue-discard` が働くのは「戻り値の型が `Unit` の定義の中で、非 `Unit` の値を捨てたとき」です。次のように、戻り値の型が `Int` で、途中の文として値を捨てた場合は、この設定では **指摘されませんでした**。

```scala
object Violation:
  def show(counts: Vector[Int]): Int =
    counts.map(_ + 1)   // 結果を使っていないが、-Wvalue-discard では通る
    counts.size
```

これを捕まえるのは `-Wnonunit-statement` という別の設定です。試しに `scalacOptions` に足すと、同じコードがエラーになりました。

```text
[error] -- [E176] Potential Issue Error: APP/src/main/scala/machinelearning/Violation.scala:5:14
[error] 5 |    counts.map(_ + 1)
[error]   |    ^^^^^^^^^^^^^^^^^
[error]   |    unused value of type Vector[Int]
```

本シリーズではこの設定は入れていません。必要になった時点で、増える指摘を見てから判断します。大事なのは、「設定の名前から期待する範囲」と「実際に捕まる範囲」は必ずしも一致しないということです。入れた検査は、わざと違反を書いて範囲を測っておきます。

#### 網羅していない match

```scala
  def matchExample(value: Option[Int]): Int =
    value match
      case Some(n) => n
```

```text
[error] -- [E029] Pattern Match Exhaustivity Error: APP/src/main/scala/machinelearning/Violation.scala:10:4
[error] 10 |    value match
[error]    |    ^^^^^
[error]    |    match may not be exhaustive.
[error]    |
[error]    |    It would fail on pattern case: None
[error]    |
[error]    | longer explanation available when compiling with `-explain`
```

「`None` のときに落ちる」と、抜けている場合まで教えてくれます。これは第 3 章で決定木を `enum` で書いたことと直結します。`enum` は取りうる形が閉じているので、`match` に節を足し忘れるとコンパイルが止まります。Java の sealed interface、C# の抽象レコード、F# の判別共用体と同じ効き目を、追加のツールなしで得られます。

この検査は既定では警告なので、`-Xfatal-warnings` が無ければ通ってしまいます。「型で表した安全」を本当に効かせるには、警告をエラーにする設定まで含めて 1 組です。

### この設定が捕まえないもの

`-Wunused:all` は、**公開メソッドの使っていない引数** を指摘しません。Java 版では Error Prone の `UnusedMethod` などが、仮実装（引数を無視して定数を返す）を止めてしまう場面がありましたが、Scala 版ではそれが起きません。TDD の仮実装（Fake It）を書きやすいという意味では都合がよく、「引数を渡し忘れている」バグを見逃しやすいという意味では弱点です。ツールの守備範囲を把握したうえで、足りない部分はテストで守ります。

### 第 1〜3 章で受けた指摘

この設定で第 1 章から繰り返し出たのは、`assume` の戻り値（`Assertion`）を捨てている、という指摘でした。直し方は毎回同じで、`requireData(): Unit` と型を書きます。実データのテストを持つ章では必ず出るので、最初に定型として覚えてしまうのが早道です（[ADR 007](../../../adr/007-scala-ml-libraries.md) にも書いてあります）。

指摘を抑制する注釈（`@nowarn`）を使わず、すべて直しました。抑制を許すと、抑制されたコードが増えていき、検査の意味が薄れていきます。

## 5.6 型チェック — Scala コンパイラ

静的解析とは別に、Scala のコンパイラそのものが強い型検査をしています。第 1〜3 章で書いたコードのうち、次のものはコンパイラだけで守られています。

- `Option[Double]`（欠損値がありうる値）と `Double`（補完済みの値）を取り違えられない
- `enum` で書いた決定木の節を、`match` で漏らせない（5.5 節）
- `TrainTestSplit[X, T]` の型引数で、特徴量と正解ラベルの型が混ざらない

Python 版では、同じことを型ヒントと実行時のチェックで補っていました。JVM の 3 言語（Java・Kotlin・Scala）と F# は、この部分をコンパイル時に片付けられます。テストで守るべきことが減るので、テストはロジックの検証に集中できます。

## 5.7 コードカバレッジ — scoverage

カバレッジは、テストがコードのどこを通ったかの割合です。scoverage（`sbt-scoverage`）で計測します。

```bash
sbt -batch --no-colors 'coverage; test; coverageReport'
```

`coverage` は「次のコンパイルに計測用のコードを埋め込む」という設定で、その状態で `test` を走らせ、`coverageReport` でレポートを作ります。

```text
[info] Written Cobertura report [.../target/scala-3.3.6/coverage-report/cobertura.xml]
[info] Written XML coverage report [.../target/scala-3.3.6/scoverage-report/scoverage.xml]
[info] Written HTML coverage report [.../target/scala-3.3.6/scoverage-report/index.html]
[info] Statement coverage.: 92.57%
[info] Branch coverage....: 89.66%
[info] Coverage reports completed
[info] All done. Coverage was stmt=[92.57%] branch=[89.66%]
```

scoverage が数えるのは、行ではなく **文（statement）** と **分岐（branch）** です。第 1〜3 章まで書いた時点で、学習データを配置した状態では文 92.57%・分岐 89.66% でした。

学習データが無い環境では、実データのテストが取り消される（前の章の CANCELED）ので、同じコードでも数字が下がります。

| 実行の条件 | 文カバレッジ | 分岐カバレッジ |
|-----------|------------|--------------|
| 学習データあり | 92.57% | 89.66% |
| 学習データなし | 66.91% | 79.31% |

CI には学習データを置けないので、CI で出る数字は後者です。カバレッジに下限を設けて CI を失敗させる設定（`coverageMinimumStmtTotal`）もありますが、本シリーズでは入れていません。データの有無で 25 ポイントも動く数字を合格ラインにすると、「データが無いと落ちる CI」になってしまうからです。カバレッジは、合格・不合格を決める指標ではなく、**テストが通っていない場所を探すための地図** として使います。

レポートの HTML（`target/scala-3.3.6/scoverage-report/index.html`）をブラウザで開くと、どの文が通っていないかが色で分かります。第 8 章以降でコードが増えたら、ここを見て「テストを書いたつもりで通っていない分岐」を探します。

## 5.8 まとめ

この章では、版を固定する仕組みと、品質を機械的に確かめる道具を整えました。

1. **sbt によるパッケージ管理** — `build.sbt` に依存を宣言し、Java のライブラリは `%`、Scala のライブラリは `%%` で書く。取得は Coursier が行い、ホームのキャッシュに置かれる。ロックファイルは使わず、厳密な版だけを書く
2. **版の固定** — sbt 本体の版は `project/build.properties` の 1 行で決まる。環境の sbt が 1.12.0 でも、ここに 1.12.9 と書けばその版で動く（sbt-scalafmt 2.6.2 が 1.12.9 以上を求めるので、これが必要だった）。Scala の版は `build.sbt`、JDK は Nix の開発環境で固定する
3. **整形** — scalafmt に `version`・`runner.dialect = scala3`・`maxColumn` を設定し、`scalafmtCheckAll` で検査、`scalafmtAll` で整形する
4. **静的解析** — 別立てのツールを足すかわりに、コンパイラの警告（`-Wunused:all`・`-Wvalue-discard`・網羅していない `match`）を `-Xfatal-warnings` でエラーにする。わざと違反を入れて、捕まる範囲を実際に測る
5. **カバレッジ** — scoverage で文と分岐を計測する。学習データの有無で数字が動くので、合格ラインではなく地図として使う

次の章では、これらのタスクを 1 つのコマンドにまとめ、GitHub Actions で自動的に実行する仕組みを作ります。
