---
type: ADR
title: "002 Kotlin 版の機械学習・データ・可視化・API ライブラリの選定"
description: "Kotlin 版のライブラリに Kotlin DataFrame・Tribuo・Kandy・Ktor を採用し、Tribuo で確認した機能に基づいて章ごとの置き換え範囲を決める。"
tags: [adr,getting-start-ml,kotlin]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T03:16:42Z }
---

# 002 Kotlin 版の機械学習・データ・可視化・API ライブラリの選定

「機械学習から始めるプログラミング入門」Kotlin 版で使うライブラリを決める。

日付: 2026-09-17

## ステータス

2026-09-17 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「Kotlin 版執筆計画」で、機械学習ライブラリの第一候補を Tribuo（Apache License 2.0）とし、GPL-3.0 の Smile 6.x は採用しないことを承認した。ただし、Tribuo の各アルゴリズムが各章の「ライブラリへの置き換え」に使えるかは未検証だった。

そこで B7 で、Maven Central の POM・JAR の中身の確認と、使い捨ての Kotlin プロジェクト（Kotlin 2.4.20、JDK 21 ツールチェーン）での実行によって、次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| Tribuo 4.3.2 のライセンス | Apache License 2.0 | 親 POM（`org.tribuo:tribuo:4.3.2`） |
| 決定木でジニ不純度を選べるか | 選べる。`CARTClassificationTrainer(int, float, float, float, LabelImpurity, long)` に `GiniIndex` を渡す | コンストラクタの一覧を実行時に出力 |
| クラスの重み付け | 決定木（`CARTClassificationTrainer`）・ロジスティック回帰（`LogisticRegressionTrainer`）・ランダムフォレスト（`RandomForestTrainer`）はいずれも `WeightedLabels` を実装していない | `isAssignableFrom` を実行 |
| ランダムフォレスト・Extra Trees | `RandomForestTrainer`・`ExtraTreesTrainer` がある | JAR のクラス一覧 |
| 線形回帰・正則化 | `SLMTrainer`・`LARSTrainer`・`LARSLassoTrainer`・`ElasticNetCDTrainer`・`LinearSGDTrainer`（回帰）がある。`LARSLassoTrainer` のコンストラクタは `(int)` と引数なしで、正則化の強さ（alpha）を直接指定できない | JAR のクラス一覧・コンストラクタの一覧 |
| K-means の初期中心 | `KMeansTrainer` の初期化方法は `RANDOM` と `PLUSPLUS` だけで、初期中心を直接渡すコンストラクタは無い | コンストラクタと列挙型の一覧 |
| PCA | モジュールが無い。行列の固有値分解（`DenseMatrix.EigenDecomposition`）はある | JAR のクラス一覧 |
| 標準化 | `MeanStdDevTransformation` がある | JAR のクラス一覧 |
| モデルの保存 | `Model.serializeToFile` がある | メソッドの一覧 |
| Kotlin DataFrame 0.15.0 | Kotlin 2.4.20 でコンパイルできる。`DataFrame.readCsv` は BOM を取り除いて列名を読み、欠損を含む数値列を `Double?` として読む（iris.csv で 150 行・4 列が `Double?`、1 列が `String`） | 実行して列名の先頭の文字コードと列の型を出力 |
| Kandy 0.8.5 | Kotlin 2.4.20 のプロジェクトで依存を解決してコンパイルできる | ビルド |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・ビルド | Kotlin（Gradle プラグイン）、Gradle Wrapper | 2.4.20、9.7.1 | Apache License 2.0 | 第 1 章 |
| テスト | kotlin.test + JUnit Platform | Kotlin と同じ | Apache License 2.0 | 第 1 章 |
| データフレーム | Kotlin DataFrame | 0.15.0 | Apache License 2.0 | 第 2 章 |
| 機械学習 | Tribuo | 4.3.2 | Apache License 2.0 | 第 3 章 |
| 可視化 | Kandy（lets-plot） | 0.8.5 | Apache License 2.0 | 第 2 章 |
| API | Ktor、kotlinx.serialization | 3.6.0、1.11.0 | Apache License 2.0 | 第 15 章 |
| 静的解析・カバレッジ | detekt、ktlint、Kover | 1.23.8、1.8.0、0.9.9 | Apache License 2.0（detekt・Kover）、MIT（ktlint） | 第 5 章 |

ライブラリは使う章に入ってから `gradle/libs.versions.toml` に追加する。detekt・Kover・Ktor・kotlinx.serialization（Apache License 2.0）と ktlint（MIT）のライセンスは、Maven Central の POM で確認した。

### 章ごとのライブラリへの置き換え方針

確認結果から、執筆計画の「ライブラリへの置き換え」を次のとおり具体化する。

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | Tribuo の CART（ジニ不純度） | 自作の決定木と予測を突き合わせる |
| 7 | Tribuo の線形回帰（`SLMTrainer` など） | 係数と決定係数を突き合わせる。どのトレーナーが最小二乗解になるかは第 7 章で確認する |
| 8 | Tribuo の決定木 | クラスの重み付けが Tribuo の決定木に無いので、重み付けの効果は自作の重み付き不純度で示し、Tribuo との突き合わせは重み付けなしで行う |
| 9 | Tribuo の `MeanStdDevTransformation` | 自作の標準化と突き合わせる |
| 10 | Tribuo の `LogisticRegressionTrainer`・`RandomForestTrainer` | 正解率を比べる（最適化手法と乱数の使い方が違うので、予測の完全一致は求めない） |
| 11 | Tribuo の評価器（`LabelEvaluator` など） | 自作の評価指標と突き合わせる |
| 12 | Tribuo の `ElasticNetCDTrainer` | 自作のリッジ回帰は閉形式で書く。ラッソ回帰は `LARSLassoTrainer` が alpha を取らないので、`ElasticNetCDTrainer` で表せるかを第 12 章で確認し、表せなければ置き換えの節を省略する |
| 13 | なし | PCA のモジュールが無いので、Tribuo の固有値分解を使った自作を最終実装とし、置き換えの節を省略する |
| 14 | Tribuo の `KMeansTrainer` | 初期中心を渡せないので、同じ初期中心での一致は確かめない。同じクラスタ数での SSE の大きさを比べるにとどめる |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| Smile 6.x | GPL-3.0 で、PUBLIC リポジトリで配布するサンプルコードの利用条件に影響する |
| Smile 2.6.0（LGPL-3.0） | 古い版に固定することになり、Kotlin 2.4 系との組み合わせで保守される見込みが低い |
| KotlinDL | 深層学習向けで、決定木・線形回帰などの古典的な手法を扱わない |
| multik（行列演算） | 正規方程式や固有値分解の仕組みを見せるため、行列は自作の小さな型と Tribuo の `DenseMatrix` で足りる。必要になった章で再検討する |
| Spring Boot（API） | 第 15 章の主題（レイヤードアーキテクチャと統合テスト）に対して依存が大きい |

## 影響

- 良い影響: すべてのライブラリが Apache License 2.0 などの寛容なライセンスで、サンプルコードの利用条件が単純になる
- 良い影響: Kotlin DataFrame が BOM と欠損値（`Double?`）を扱うので、Python 版の pandas と同じ節構成で第 2 章を書ける
- 悪い影響: Tribuo には PCA が無く、クラスの重み付けと K-means の初期中心の指定もできないので、第 8・13・14 章は Python 版より置き換えの範囲が狭い
- 悪い影響: Kotlin DataFrame・Kandy は 1.0 未満で、API が変わる可能性がある。記事のコードは CI でビルドして検証する

## コンプライアンス

- `apps/kotlin/gradle/libs.versions.toml` に上記のライブラリと版だけが記載されている
- Kotlin CI（`.github/workflows/kotlin-ci.yml`）がグリーンである
- 置き換えを省略した章の記事に、省略した理由が書かれている

## 備考

- 著者: claude-code/claude-opus-5
- 確認に使った使い捨てのプロジェクトはスクラッチパッドに置き、リポジトリには含めていない
