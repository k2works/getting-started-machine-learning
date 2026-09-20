# 機械学習から始める Scala 入門

Scala は JVM 上で動く言語で、オブジェクト指向と関数型の書き方をひとつの型システムの上でまとめています。Scala 3 の case class・enum・`Option`・パターンマッチによって、データとモデルを値として表し、変換の連なりとして処理を書けます。本シリーズでは、Python 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、次に JVM の機械学習ライブラリ Tribuo に置き換えて結果を突き合わせながら、Scala の書き方とエコシステムを学びます。

Scala 版は [Java 版](../java/index.md)・[Kotlin 版](../kotlin/index.md) と同じ JVM・同じ Tribuo を使い、関数型の書き方では [F# 版](../fsharp/index.md) と対比します。同じ処理を 4 つの言語で書いた違いに注目できるように、各章で対比しながら進めます。

## 特徴

- **case class と enum**: データや決定木の節を、値として比較できる型で表す。Java の record・Kotlin の data class に当たり、F# のレコードと判別共用体にも近い
- **不変のコレクション**: データフレームのライブラリを使わず、`Vector`・`Map` と `map`・`filter`・`foldLeft` でデータを変換・集計する。`Vector` は中身で比較されるので、Java 版・C# 版が配列で苦労した「値で比べる」がそのまま書ける
- **`Option` で「値が無い場合」を表す**: `null` を使わず、`Option` を返す関数を引数で渡して環境変数のような外部の値をテストで差し替える
- **警告をエラーにする**: `-Wunused:all`・`-Wvalue-discard` を `-Xfatal-warnings` でエラーにし、第 1 章から指摘を受けながら書く

## ほかの版との違い

- Notebook による探索と可視化の節は設けません。グラフは [Python 版](../python/index.md) と [Kotlin 版](../kotlin/index.md) の可視化の節を参照してください
- 機械学習ライブラリには Smile ではなく Tribuo を使います。Scala 3 向けの `smile-scala_3` は 3.0.2 以降しか無く、そのすべてが GPL-3.0 で、LGPL-3.0 の 2.6.0 には Scala 3 向けの成果物がありません。公開リポジトリで配るサンプルコードの利用条件を単純に保つため、Apache License 2.0 の Tribuo を選びました
- 分割の手順は Python 版と同じですが、乱数生成器が異なるため、訓練データとテストデータに入る行は Python 版と一致しません。記事の数値は Scala 版の実装で実測した値を載せています

## 開発環境

| ツール | 用途 |
|--------|------|
| [sbt](https://www.scala-sbt.org/)（`project/build.properties` で版を固定） | ビルドツール・タスクランナー |
| [ScalaTest](https://www.scalatest.org/)（AnyFunSuite） | テスティングフレームワークとアサーション |
| [scalafmt](https://scalameta.org/scalafmt/)（sbt-scalafmt） | 整形 |
| コンパイラの警告（`-Wunused:all`・`-Wvalue-discard`・`-Xfatal-warnings`） | 静的解析 |
| [scoverage](https://github.com/scoverage/sbt-scoverage) | カバレッジ |
| Scala 3.3.6（LTS）・JDK 21 | `nix develop .#scala` で用意する |

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [Tribuo](https://tribuo.org/) | 機械学習（自作との突き合わせ） | 第 3 章 |
| [http4s](https://http4s.org/) + [circe](https://circe.github.io/circe/) | 予測 API | 第 15 章 |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 007](../../../adr/007-scala-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/scala/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
nix develop .#scala
cd apps/scala
sbt test
sbt "run chapter01"
```

リポジトリのルートからは `npx gulp apps:check:scala` で整形の検査とテストをまとめて実行できます。

## 章構成

### 第 1 部: 機械学習と TDD の基本サイクル

| 章 | テーマ |
|----|--------|
| [第 1 章](01-machine-learning-and-first-test.md) | 機械学習とはじめてのテスト |
| [第 2 章](02-data-preprocessing-and-triangulation.md) | データの前処理と三角測量 |
| [第 3 章](03-decision-tree-and-obvious-implementation.md) | 決定木による分類と明白な実装 |

### 第 2 部: 開発環境と自動化

| 章 | テーマ |
|----|--------|
| [第 4 章](04-version-control-and-data-management.md) | バージョン管理とデータ管理 |
| [第 5 章](05-package-management-and-static-analysis.md) | パッケージ管理と静的解析 |
| [第 6 章](06-task-runner-and-ci-cd.md) | タスクランナーと CI/CD |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ |
|----|--------|
| [第 7 章](07-linear-regression.md) | 線形回帰による数値予測 |
| [第 8 章](08-classification-and-preprocessing-pipeline.md) | 実践的な分類と前処理パイプライン |
| [第 9 章](09-feature-engineering.md) | 特徴量エンジニアリング |

### 第 4 部: モデルの改善と評価

| 章 | テーマ |
|----|--------|
| [第 10 章](10-logistic-regression-and-ensemble.md) | ロジスティック回帰とアンサンブル学習 |
| [第 11 章](11-evaluation-metrics-and-cross-validation.md) | 評価指標と交差検証 |
| [第 12 章](12-regularization-and-model-selection.md) | 正則化とモデル選択 |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ |
|----|--------|
| [第 13 章](13-principal-component-analysis.md) | 主成分分析による次元削減 |
| [第 14 章](14-k-means-clustering.md) | K-means によるクラスタリング |
| 第 15 章 | 機械学習 API とモジュール設計 |

### 付録

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Scala で取り組んでください。
