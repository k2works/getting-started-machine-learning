---
type: ADR
title: "005 Java 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定"
description: "Java 版のライブラリに JUnit 6・AssertJ・Spotless・Error Prone・PMD・JaCoCo・Tribuo・Javalin を採用し、章ごとの置き換え範囲を Kotlin 版の ADR 002 を起点に決める。"
tags: [adr,getting-start-ml,java]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T14:30:00Z }
---

# 005 Java 版のビルド・テスト・静的解析・機械学習・API ライブラリの選定

「機械学習から始めるプログラミング入門」Java 版で使うライブラリを決める。

日付: 2026-09-19

## ステータス

2026-09-19 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「Java 版執筆計画」で、Java 版は Kotlin 版の実装を対比の相手にし、データフレームのライブラリを使わず record のリストと Stream API でデータを表すことを承認した。JDK と Gradle の版は Kotlin 版にそろえる。

B24 のステップ 1 で、Maven Central の POM・JAR のクラスファイルの版と Gradle Plugin Portal のメタデータによって、次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| ライセンス | Tribuo 4.3.2・AssertJ 3.27.7・Error Prone 2.50.0・google-java-format 1.36.1・Spotless 8.10.2・Javalin 7.2.3 は Apache License 2.0。JUnit 6.1.3・JaCoCo 0.8.15 は EPL-2.0。PMD 7.27.0 は BSD 系 | 各 POM の `licenses` |
| 必要な JDK | JUnit 6.1.3・Javalin 7.2.3 は Java 17（クラスファイルの major 61）、Error Prone 2.50.0・google-java-format 1.36.1 は Java 21（major 65）、Tribuo 4.3.2・PMD 7.27.0 は Java 8（major 52） | JAR のクラスファイルの版 |
| Gradle プラグイン | Error Prone は `net.ltgt.errorprone` 5.1.1、Spotless は `com.diffplug.spotless` 8.10.2。PMD と JaCoCo は Gradle に組み込み | Gradle Plugin Portal |
| Tribuo の保守 | 最新は 4.3.2 で、Maven Central の最終更新は 2025-04-08 | Maven Central のメタデータ |
| AssertJ の版 | 4.0.0 はマイルストーン版（4.0.0-M1）のみ。安定版の最新は 3.27.7 | Maven Central のメタデータ |
| 静的解析が効いているか（B24） | わざと違反を入れた 3 つのファイルで、`./gradlew check` がそれぞれ失敗した。例外を作って投げない（Error Prone の `DeadException`、エラー）、整形の崩れ（`spotlessJavaCheck`）、波かっこの無い if（PMD の `ControlStatementBraces`）。Error Prone の警告（`UnusedVariable`・`EmptyCatch`）も `-Werror` によってコンパイルを失敗させる | `apps/java` に一時的なファイルを置いて実行し、確かめた後に消した |
| 名前による除外（B24） | `unused` という名前の変数は、Error Prone も PMD も意図して使わない変数とみなし、指摘しない | 同上 |
| 名前付きのパッケージ（B24） | PMD の quickstart の `NoPackage` が、無名のパッケージのクラスを指摘する。テストも `setup` などのパッケージに置く | 同上 |
| Nix の `java` 環境（B24） | `nix develop .#java` の JDK 21.0.8 で `./gradlew test` が成功した | 手元で実行 |

Tribuo の各アルゴリズムの振る舞いは、Kotlin 版の [ADR 002](002-kotlin-ml-libraries.md) で確かめている。Tribuo は Java 製なので、Java から使っても振る舞いは変わらないと考え、置き換えの範囲は ADR 002 を起点にする。

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・ビルド | Java（ツールチェーン）、Gradle Wrapper（Kotlin DSL）、バージョンカタログ | 21、9.7.1 | — | 第 1 章 |
| テスト | JUnit、AssertJ | 6.1.3、3.27.7 | EPL-2.0、Apache License 2.0 | 第 1 章 |
| 整形 | Spotless（google-java-format） | 8.10.2（1.36.1） | Apache License 2.0 | 第 1 章（記事での解説は第 5 章） |
| 静的解析 | Error Prone、PMD | 2.50.0（プラグイン 5.1.1）、7.27.0 | Apache License 2.0、BSD 系 | 第 1 章（記事での解説は第 5 章） |
| カバレッジ | JaCoCo | 0.8.15 | EPL-2.0 | 第 1 章（記事での解説は第 5 章） |
| データの表現 | record のリストと Stream API（ライブラリなし） | — | — | 第 1 章 |
| 機械学習 | Tribuo | 4.3.2 | Apache License 2.0 | 第 3 章 |
| API | Javalin | 7.2.3 | Apache License 2.0 | 第 15 章 |

- JDK と Gradle は Kotlin 版と同じく、ツールチェーン（`java.toolchain`）と `gradle/gradle-daemon-jvm.properties` で 21 に固定する。手元の JDK が 25 でも、ビルドとデーモンは JDK 21 で動く
- 整形・静的解析・カバレッジは B24 から `./gradlew check` に組み込む。F# 版で静的解析のルールが 1 件も有効になっていなかった教訓から、わざと違反を入れて `check` が失敗することを確かめる
- 機械学習と API のライブラリは、使う章に入ってから `gradle/libs.versions.toml` に追加する

### 章ごとのライブラリへの置き換え方針

ADR 002 の方針をそのまま使う。Java 版の各章で実装しながら振る舞いを確かめ、Kotlin 版と違う結果になったときは本 ADR に書き足す。

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | Tribuo の CART（ジニ不純度） | 自作の決定木と予測を突き合わせる |
| 7 | Tribuo の `SLMTrainer(true)`・`LARSTrainer` | 係数と決定係数を突き合わせる |
| 8 | Tribuo の決定木 | クラスの重み付けは自作の木で示し、Tribuo との突き合わせは重み付けなしで行う |
| 9 | Tribuo の `MeanStdDevTransformation` | 不偏標準偏差を使う違いを踏まえて自作の標準化と突き合わせる |
| 10 | Tribuo の `LogisticRegressionTrainer`・`RandomForestTrainer` | 正解率を比べる |
| 11 | Tribuo の評価器 | 自作の評価指標と突き合わせる |
| 12 | Tribuo の `ElasticNetCDTrainer` | ラッソ回帰とリッジ回帰を突き合わせる |
| 13 | なし | PCA のモジュールが無いので、Tribuo の固有値分解を使った自作を最終実装とする |
| 14 | Tribuo の `KMeansTrainer` | 初期中心を渡せないので、SSE の大きさを比べるにとどめる |

### 各章で確かめた結果

| 章 | 確かめたこと |
|----|------------|
| 2 | record の成分に配列を使うと、Error Prone の `ArrayRecordComponent` が警告する（`-Werror` でエラー）。特徴量の `Features` は `double[]` を持つので、record ではなくクラスにし、配列を写して持って equals・hashCode・toString を中身で比べる |
| 3 | Tribuo の CART（`CARTClassificationTrainer`、`minChildWeight` 1）と、同数の多数決・同じ不純度の分割候補の扱いが違うことは Kotlin 版（ADR 002）と同じ。Java 版の分割（`java.util.Random(0)`）では、深さ 1〜5 と制限なしのどれでも、テストデータ 45 件の予測が自作と全件一致した |
| 4 | `java.util.Random` の数列は Javadoc の「Java implementations must use all the algorithms shown here」により仕様で保証されるが、`Collections.shuffle` の並べ方は実装の説明にとどまる。JDK 17・21・25 では `Collections.shuffle(0..9, new Random(0))` が同じ並びになった。ツールチェーンを JDK 21 に固定して再現性を保つ |
| 5 | Gradle の依存のロック（`dependencyLocking`）を試すと、ロックファイルがバージョンカタログより優先され、カタログの版を下げても警告なくロックの版で解決した。範囲指定・動的な版が無いので、ロックは使わず、バージョンカタログの版を正とする。BOM の文字は Spotless・Error Prone・PMD・javac のどれも検出しないので、`verifyNoBomCharacter` で検査する |
| 7 | Java でも最小二乗解と一致するのは `SLMTrainer(true)` と `LARSTrainer()` だけ（実データの決定係数が自作と 1e-9 以内で一致）。`SLMTrainer(false)`・`LinearSGDTrainer` の予測は Kotlin 版と同じ値になる。`SparseLinearModel` の重みは正規化した空間の値で、元の単位に戻すと自作の係数と 1e-9 以内で一致する。Tribuo は特徴量を名前の順に並べるので、重みの位置は `getFeatureIDMap().getID(name)` で引く。`RegressionEvaluator` の MAE・RMSE・R² は自作と一致する |
| 8 | Tribuo の決定木にクラスの重みが無いので、自作の重み付きジニ不純度で示した。前処理後のテストデータ 179 件で、重み付けなしの自作の木と Tribuo の CART の予測が違ったのは深さ 5 で 2 件、深さ 9 で 1 件（ほかの深さは全件一致。原因は未調査）。`toList()` の結果の `subList` はシリアライズできない（`NotSerializableException`）ので、保存するモデルでは `List.copyOf` で写す。読み込みは `ObjectInputFilter` の許可リスト（`chapter08.*;java.lang.*;java.util.*;!*`）で絞る |
| 9 | Tribuo の `MeanStdDevTransformation` は Java から呼んでも不偏標準偏差（件数 − 1 で割る）を使い、自作の値に √((n−1)/n) を掛けると一致する（ADR 002 と同じ）。Shift_JIS の `weather.csv` を UTF-8 として読むと、Java の `Files.readAllLines` は `MalformedInputException` を投げる（Kotlin DataFrame は置換文字に置き換えて読み進める）。正規方程式は Tribuo の `DenseMatrix` のコレスキー分解で解き、`choleskyFactorization()` は `Optional` を返す |
| 10 | `LinearSGDTrainer`（ロジスティック回帰の既定の設定）は 5 エポックで訓練 0.9238・テスト 0.8889、50・500・5000 エポックでは自作と同じ 0.9143・0.9111 で落ち着く。`RandomForestTrainer` は `minChildWeight` の既定値 5 では訓練データを分け切らず（0.9905）、1 にすると分け切る。内側の決定木の `fractionFeaturesInSplit` が 1 だとコンストラクターで例外になる。`RandomForestTrainer` の `tribuo-common-tree` は `tribuo-classification-tree` から推移的に入る。PMD は型のパターンの使わない変数を `UnusedLocalVariable` で指摘するので、Java 21 では `case Leaf ignored ->` と書く |
| 11 | `LabelEvaluator` は正例を 1 件も予測しないと適合率・再現率・F 値を NaN ではなく 0.0 にし、混同行列の件数を double で返す。`RegressionEvaluator` に MSE は無いが、RMSE の 2 乗が自作の MSE と一致する。`KFoldSplitter` の余りの配り方は自作と一致し、1 回分の分割 `TrainTestFold` は `train`・`test` を public なフィールドで持つ。Error Prone は `DoubleStream` の戻り値を捨てると `ReturnValueIgnored` で止める |
| 12 | `ElasticNetCDTrainer` の `l1Ratio` は 0 と 1e-13 を拒否し（`PropertyException`）、1e-12 を受け付ける。alpha を件数で割って渡すと、自作のリッジ回帰と係数・切片が 1e-6 以内で一致する。係数は `getWeights()` で取り出し、特徴量の番号は `getFeatureIDMap().get(name).getID()` で引く |
| 13 | `DenseMatrix.eigenDecomposition()` は Java から呼んでも ADR 002 と同じく固有値を大きい順に返し、対称でない行列では空の `Optional` を返し、固有ベクトルの符号に規則は無い。分割も乱数も使わないので、寄与率・負荷量は Kotlin 版と一致した。PCA のモジュールが無いので自作を最終実装とした |
| 14 | `KMeansTrainer` は初期中心を受け取れず、同じシードなら SSE は Kotlin 版と一致する。`KMeansModel.getCentroidVectors()` は `DenseVector[]` の配列を返す。自作の K-means では、空のクラスタの中心が 0.0 / 0 で NaN になる問題をテストで見つけて直した |
| 15 | Javalin 7 の `ctx.bodyAsClass` は、JSON が読めないときや型が合わないときに `BadRequestResponse` に包まず、Jackson の例外（`JsonEOFException`・`InvalidFormatException` など）をそのまま投げる。`BadRequestResponse` にハンドラーを登録すると 500 になるので、`JacksonException` に登録して 422 にする。422 の定数名は `HttpStatus.UNPROCESSABLE_CONTENT`。PMD の `AvoidUsingHardCodedIP` を受けて、待ち受けるアドレスは `InetAddress.getLoopbackAddress()` から求める。Javalin 7.2.3 は Jackson 2 系（`com.fasterxml.jackson`）の `JavalinJackson` と Jackson 3 系の `JavalinJackson3` の両方を持ち、Jackson は任意の依存なので明示して加える |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| Maven | Kotlin 版と同じ Gradle にそろえると、ビルドの設定の違いではなく言語の違いに記事を集中できる |
| JUnit 5 | JUnit 6 が安定版で、JDK 21 のツールチェーンで動く |
| AssertJ 4.0.0-M1 | マイルストーン版で、API が変わる可能性がある |
| Checkstyle | 整形は Spotless、規約の検査は PMD、バグになりやすい書き方は Error Prone で分担でき、規約の検査を 2 つ持つ必要が無い |
| Tablesaw（データフレーム） | Kotlin 版の data class と対比するため、record と Stream API で表す。依存を増やさない |
| Smile 6.x | GPL-3.0 で、PUBLIC リポジトリで配布するサンプルコードの利用条件に影響する（ADR 002 と同じ理由） |
| Spring Boot（API） | 第 15 章の主題（層の分離と統合テスト）に対して依存が大きい |

## 影響

- 良い影響: すべてのライブラリが寛容なライセンスで、サンプルコードの利用条件が単純になる
- 良い影響: Kotlin 版と同じ Tribuo・JDK・Gradle を使うので、記事の違いが言語の違いだけになる
- 悪い影響: Tribuo の最終更新は 2025-04 で、保守が続くかは分からない。Kotlin 版と Java 版の両方が影響を受ける
- 悪い影響: データフレームを使わないので、第 2 章・第 8 章・第 9 章の前処理は Kotlin 版より記述が長くなる。その長さ自体を対比の題材にする

## コンプライアンス

- `apps/java/gradle/libs.versions.toml` に上記のライブラリと版だけが記載されている
- Java CI（`.github/workflows/java-ci.yml`）がグリーンである
- わざと違反を入れると `./gradlew check` が失敗する（B24 で確かめた）

## 備考

- 著者: claude-code/claude-opus-5
