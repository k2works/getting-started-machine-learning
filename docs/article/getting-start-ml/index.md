# 機械学習から始めるプログラミング入門

本記事シリーズは、機械学習を題材にして、さまざまなプログラミング言語の特徴を実践的に学ぶためのガイドです。

データを読み込み、前処理し、モデルを学習・評価し、API として届けるまでの流れを、テスト駆動開発（TDD）で一歩ずつ実装します。各アルゴリズムはまず自作し、次にその言語の機械学習ライブラリで置き換えて結果を突き合わせます。自作で原理と言語の書き方を学び、置き換えでエコシステムを学びます。

執筆計画は [執筆計画アウトライン](outline.md) を、執筆の進め方は [執筆ワークフロー](workflow.md) を参照してください。

## 言語別解説

言語は 3 つの波に分けて順に追加します。第 1 波の Python 版・Kotlin 版・TypeScript 版・F# 版は全章を執筆済みです。F# は当初第 2 波でしたが、第 1 波に移しました。第 2 波は Java 版・C# 版・Scala 版・Go 版・Rust 版の全章を執筆済みです。第 3 波は Ruby 版・Clojure 版の全章を執筆済みです。

| 波 | 言語 | 環境 | 特徴 | 状況 |
|----|------|------|------|------|
| 1 | [Python](python/index.md) | CPython | 機械学習ライブラリが成熟、型ヒント、Jupyter Lab による探索と可視化 | 完了 |
| 1 | [Kotlin](kotlin/index.md) | JVM | 静的型付け、OOP と FP の融合、Kotlin Notebook による探索と可視化 | 完了 |
| 1 | [TypeScript](typescript/index.md) | Node.js | 静的型付け、機械学習ライブラリが限られる環境での自作 | 完了 |
| 1 | [F#](fsharp/index.md) | .NET | 判別共用体・パイプライン・型プロバイダ、Polyglot Notebooks による探索と可視化 | 完了 |
| 2 | [Java](java/index.md) | JVM | record・sealed interface・Stream API、Kotlin 版との対比 | 完了 |
| 2 | [C#](csharp/index.md) | .NET | record・LINQ・ML.NET の IDataView、F# 版との対比 | 完了 |
| 2 | [Scala](scala/index.md) | JVM | case class・不変のコレクション・enum、Java 版との対比 | 完了 |
| 2 | [Go](go/index.md) | Go | 構造体・error の戻り値、ライブラリが限られる環境での自作 | 完了 |
| 2 | [Rust](rust/index.md) | Rust | 所有権と借用・`Result` と `?`・linfa、Java 版／C# 版との対比 | 完了 |
| 3 | [Ruby](ruby/index.md) | CRuby | 動的型付け・ブロックと Enumerable・Rumale、Python 版との対比 | 完了 |
| 3 | [Clojure](clojure/index.md) | JVM（JDK 21） | 不変のマップとベクタ・S 式・Java の相互運用・Tribuo、Java 版／Scala 版との対比 | 完了 |
| 3 | [Elixir](elixir/index.md) | BEAM（OTP 27） | パターンマッチと関数節・パイプライン・自作の乱数・Nx と Scholar、F# 版／Clojure 版との対比 | 完了 |
| 3 | [PHP](php/index.md) | PHP 8.4 | 漸進的な型付け（strict_types と PHPStan レベル 9）・readonly class・Rubix ML、Ruby 版／Elixir 版との対比 | 完了 |
| 3 | [Haskell](haskell/index.md) | GHC 9.10 | 失敗を Either で表す・IO を境界に閉じ込める・hmatrix、Rust 版／F# 版との対比 | 第 1 章まで |

## 章構成

### 第 1 部: 機械学習と TDD の基本サイクル

| 章 | テーマ | Python | Kotlin | TypeScript | F# | Java | C# | Scala | Go | Rust | Ruby | Clojure | Elixir | PHP | Haskell |
|----|--------|--------|--------|------------|----|------|----|-------|----|------|------|---------|------|-----|---------|
| 1 | 機械学習とはじめてのテスト | [Python](python/01-machine-learning-and-first-test.md) | [Kotlin](kotlin/01-machine-learning-and-first-test.md) | [TypeScript](typescript/01-machine-learning-and-first-test.md) | [F#](fsharp/01-machine-learning-and-first-test.md) | [Java](java/01-machine-learning-and-first-test.md) | [C#](csharp/01-machine-learning-and-first-test.md) | [Scala](scala/01-machine-learning-and-first-test.md) | [Go](go/01-machine-learning-and-first-test.md) | [Rust](rust/01-machine-learning-and-first-test.md) | [Ruby](ruby/01-machine-learning-and-first-test.md) | [Clojure](clojure/01-machine-learning-and-first-test.md) | [Elixir](elixir/01-machine-learning-and-first-test.md) | [PHP](php/01-machine-learning-and-first-test.md) | [Haskell](haskell/01-machine-learning-and-first-test.md) |
| 2 | データの前処理と三角測量 | [Python](python/02-data-preprocessing-and-triangulation.md) | [Kotlin](kotlin/02-data-preprocessing-and-triangulation.md) | [TypeScript](typescript/02-data-preprocessing-and-triangulation.md) | [F#](fsharp/02-data-preprocessing-and-triangulation.md) | [Java](java/02-data-preprocessing-and-triangulation.md) | [C#](csharp/02-data-preprocessing-and-triangulation.md) | [Scala](scala/02-data-preprocessing-and-triangulation.md) | [Go](go/02-data-preprocessing-and-triangulation.md) | [Rust](rust/02-data-preprocessing-and-triangulation.md) | [Ruby](ruby/02-data-preprocessing-and-triangulation.md) | [Clojure](clojure/02-data-preprocessing-and-triangulation.md) | [Elixir](elixir/02-data-preprocessing-and-triangulation.md) | [PHP](php/02-data-preprocessing-and-triangulation.md) | — |
| 3 | 決定木による分類と明白な実装 | [Python](python/03-decision-tree-and-obvious-implementation.md) | [Kotlin](kotlin/03-decision-tree-and-obvious-implementation.md) | [TypeScript](typescript/03-decision-tree-and-obvious-implementation.md) | [F#](fsharp/03-decision-tree-and-obvious-implementation.md) | [Java](java/03-decision-tree-and-obvious-implementation.md) | [C#](csharp/03-decision-tree-and-obvious-implementation.md) | [Scala](scala/03-decision-tree-and-obvious-implementation.md) | [Go](go/03-decision-tree-and-obvious-implementation.md) | [Rust](rust/03-decision-tree-and-obvious-implementation.md) | [Ruby](ruby/03-decision-tree-and-obvious-implementation.md) | [Clojure](clojure/03-decision-tree-and-obvious-implementation.md) | [Elixir](elixir/03-decision-tree-and-obvious-implementation.md) | [PHP](php/03-decision-tree-and-obvious-implementation.md) | — |

### 第 2 部: 開発環境と自動化

| 章 | テーマ | Python | Kotlin | TypeScript | F# | Java | C# | Scala | Go | Rust | Ruby | Clojure | Elixir | PHP | Haskell |
|----|--------|--------|--------|------------|----|------|----|-------|----|------|------|---------|------|-----|---------|
| 4 | バージョン管理とデータ管理 | [Python](python/04-version-control-and-data-management.md) | [Kotlin](kotlin/04-version-control-and-data-management.md) | [TypeScript](typescript/04-version-control-and-data-management.md) | [F#](fsharp/04-version-control-and-data-management.md) | [Java](java/04-version-control-and-data-management.md) | [C#](csharp/04-version-control-and-data-management.md) | [Scala](scala/04-version-control-and-data-management.md) | [Go](go/04-version-control-and-data-management.md) | [Rust](rust/04-version-control-and-data-management.md) | [Ruby](ruby/04-version-control-and-data-management.md) | [Clojure](clojure/04-version-control-and-data-management.md) | [Elixir](elixir/04-version-control-and-data-management.md) | [PHP](php/04-version-control-and-data-management.md) | — |
| 5 | パッケージ管理と静的解析 | [Python](python/05-package-management-and-static-analysis.md) | [Kotlin](kotlin/05-package-management-and-static-analysis.md) | [TypeScript](typescript/05-package-management-and-static-analysis.md) | [F#](fsharp/05-package-management-and-static-analysis.md) | [Java](java/05-package-management-and-static-analysis.md) | [C#](csharp/05-package-management-and-static-analysis.md) | [Scala](scala/05-package-management-and-static-analysis.md) | [Go](go/05-package-management-and-static-analysis.md) | [Rust](rust/05-package-management-and-static-analysis.md) | [Ruby](ruby/05-package-management-and-static-analysis.md) | [Clojure](clojure/05-package-management-and-static-analysis.md) | [Elixir](elixir/05-package-management-and-static-analysis.md) | [PHP](php/05-package-management-and-static-analysis.md) | — |
| 6 | タスクランナーと CI/CD | [Python](python/06-task-runner-and-ci-cd.md) | [Kotlin](kotlin/06-task-runner-and-ci-cd.md) | [TypeScript](typescript/06-task-runner-and-ci-cd.md) | [F#](fsharp/06-task-runner-and-ci-cd.md) | [Java](java/06-task-runner-and-ci-cd.md) | [C#](csharp/06-task-runner-and-ci-cd.md) | [Scala](scala/06-task-runner-and-ci-cd.md) | [Go](go/06-task-runner-and-ci-cd.md) | [Rust](rust/06-task-runner-and-ci-cd.md) | [Ruby](ruby/06-task-runner-and-ci-cd.md) | [Clojure](clojure/06-task-runner-and-ci-cd.md) | [Elixir](elixir/06-task-runner-and-ci-cd.md) | [PHP](php/06-task-runner-and-ci-cd.md) | — |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ | Python | Kotlin | TypeScript | F# | Java | C# | Scala | Go | Rust | Ruby | Clojure | Elixir | PHP | Haskell |
|----|--------|--------|--------|------------|----|------|----|-------|----|------|------|---------|------|-----|---------|
| 7 | 線形回帰による数値予測 | [Python](python/07-linear-regression.md) | [Kotlin](kotlin/07-linear-regression.md) | [TypeScript](typescript/07-linear-regression.md) | [F#](fsharp/07-linear-regression.md) | [Java](java/07-linear-regression.md) | [C#](csharp/07-linear-regression.md) | [Scala](scala/07-linear-regression.md) | [Go](go/07-linear-regression.md) | [Rust](rust/07-linear-regression.md) | [Ruby](ruby/07-linear-regression.md) | [Clojure](clojure/07-linear-regression.md) | [Elixir](elixir/07-linear-regression.md) | [PHP](php/07-linear-regression.md) | — |
| 8 | 実践的な分類と前処理パイプライン | [Python](python/08-classification-and-preprocessing-pipeline.md) | [Kotlin](kotlin/08-classification-and-preprocessing-pipeline.md) | [TypeScript](typescript/08-classification-and-preprocessing-pipeline.md) | [F#](fsharp/08-classification-and-preprocessing-pipeline.md) | [Java](java/08-classification-and-preprocessing-pipeline.md) | [C#](csharp/08-classification-and-preprocessing-pipeline.md) | [Scala](scala/08-classification-and-preprocessing-pipeline.md) | [Go](go/08-classification-and-preprocessing-pipeline.md) | [Rust](rust/08-classification-and-preprocessing-pipeline.md) | [Ruby](ruby/08-classification-and-preprocessing-pipeline.md) | [Clojure](clojure/08-classification-and-preprocessing-pipeline.md) | [Elixir](elixir/08-classification-and-preprocessing-pipeline.md) | [PHP](php/08-classification-and-preprocessing-pipeline.md) | — |
| 9 | 特徴量エンジニアリング | [Python](python/09-feature-engineering.md) | [Kotlin](kotlin/09-feature-engineering.md) | [TypeScript](typescript/09-feature-engineering.md) | [F#](fsharp/09-feature-engineering.md) | [Java](java/09-feature-engineering.md) | [C#](csharp/09-feature-engineering.md) | [Scala](scala/09-feature-engineering.md) | [Go](go/09-feature-engineering.md) | [Rust](rust/09-feature-engineering.md) | [Ruby](ruby/09-feature-engineering.md) | [Clojure](clojure/09-feature-engineering.md) | [Elixir](elixir/09-feature-engineering.md) | [PHP](php/09-feature-engineering.md) | — |

### 第 4 部: モデルの改善と評価

| 章 | テーマ | Python | Kotlin | TypeScript | F# | Java | C# | Scala | Go | Rust | Ruby | Clojure | Elixir | PHP | Haskell |
|----|--------|--------|--------|------------|----|------|----|-------|----|------|------|---------|------|-----|---------|
| 10 | ロジスティック回帰とアンサンブル学習 | [Python](python/10-logistic-regression-and-ensemble.md) | [Kotlin](kotlin/10-logistic-regression-and-ensemble.md) | [TypeScript](typescript/10-logistic-regression-and-ensemble.md) | [F#](fsharp/10-logistic-regression-and-ensemble.md) | [Java](java/10-logistic-regression-and-ensemble.md) | [C#](csharp/10-logistic-regression-and-ensemble.md) | [Scala](scala/10-logistic-regression-and-ensemble.md) | [Go](go/10-logistic-regression-and-ensemble.md) | [Rust](rust/10-logistic-regression-and-ensemble.md) | [Ruby](ruby/10-logistic-regression-and-ensemble.md) | [Clojure](clojure/10-logistic-regression-and-ensemble.md) | [Elixir](elixir/10-logistic-regression-and-ensemble.md) | [PHP](php/10-logistic-regression-and-ensemble.md) | — |
| 11 | 評価指標と交差検証 | [Python](python/11-evaluation-metrics-and-cross-validation.md) | [Kotlin](kotlin/11-evaluation-metrics-and-cross-validation.md) | [TypeScript](typescript/11-evaluation-metrics-and-cross-validation.md) | [F#](fsharp/11-evaluation-metrics-and-cross-validation.md) | [Java](java/11-evaluation-metrics-and-cross-validation.md) | [C#](csharp/11-evaluation-metrics-and-cross-validation.md) | [Scala](scala/11-evaluation-metrics-and-cross-validation.md) | [Go](go/11-evaluation-metrics-and-cross-validation.md) | [Rust](rust/11-evaluation-metrics-and-cross-validation.md) | [Ruby](ruby/11-evaluation-metrics-and-cross-validation.md) | [Clojure](clojure/11-evaluation-metrics-and-cross-validation.md) | [Elixir](elixir/11-evaluation-metrics-and-cross-validation.md) | [PHP](php/11-evaluation-metrics-and-cross-validation.md) | — |
| 12 | 正則化とモデル選択 | [Python](python/12-regularization-and-model-selection.md) | [Kotlin](kotlin/12-regularization-and-model-selection.md) | [TypeScript](typescript/12-regularization-and-model-selection.md) | [F#](fsharp/12-regularization-and-model-selection.md) | [Java](java/12-regularization-and-model-selection.md) | [C#](csharp/12-regularization-and-model-selection.md) | [Scala](scala/12-regularization-and-model-selection.md) | [Go](go/12-regularization-and-model-selection.md) | [Rust](rust/12-regularization-and-model-selection.md) | [Ruby](ruby/12-regularization-and-model-selection.md) | [Clojure](clojure/12-regularization-and-model-selection.md) | [Elixir](elixir/12-regularization-and-model-selection.md) | [PHP](php/12-regularization-and-model-selection.md) | — |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ | Python | Kotlin | TypeScript | F# | Java | C# | Scala | Go | Rust | Ruby | Clojure | Elixir | PHP | Haskell |
|----|--------|--------|--------|------------|----|------|----|-------|----|------|------|---------|------|-----|---------|
| 13 | 主成分分析による次元削減 | [Python](python/13-principal-component-analysis.md) | [Kotlin](kotlin/13-principal-component-analysis.md) | [TypeScript](typescript/13-principal-component-analysis.md) | [F#](fsharp/13-principal-component-analysis.md) | [Java](java/13-principal-component-analysis.md) | [C#](csharp/13-principal-component-analysis.md) | [Scala](scala/13-principal-component-analysis.md) | [Go](go/13-principal-component-analysis.md) | [Rust](rust/13-principal-component-analysis.md) | [Ruby](ruby/13-principal-component-analysis.md) | [Clojure](clojure/13-principal-component-analysis.md) | [Elixir](elixir/13-principal-component-analysis.md) | [PHP](php/13-principal-component-analysis.md) | — |
| 14 | K-means によるクラスタリング | [Python](python/14-k-means-clustering.md) | [Kotlin](kotlin/14-k-means-clustering.md) | [TypeScript](typescript/14-k-means-clustering.md) | [F#](fsharp/14-k-means-clustering.md) | [Java](java/14-k-means-clustering.md) | [C#](csharp/14-k-means-clustering.md) | [Scala](scala/14-k-means-clustering.md) | [Go](go/14-k-means-clustering.md) | [Rust](rust/14-k-means-clustering.md) | [Ruby](ruby/14-k-means-clustering.md) | [Clojure](clojure/14-k-means-clustering.md) | [Elixir](elixir/14-k-means-clustering.md) | [PHP](php/14-k-means-clustering.md) | — |
| 15 | 機械学習 API とモジュール設計 | [Python](python/15-machine-learning-api-and-module-design.md) | [Kotlin](kotlin/15-machine-learning-api-and-module-design.md) | [TypeScript](typescript/15-machine-learning-api-and-module-design.md) | [F#](fsharp/15-machine-learning-api-and-module-design.md) | [Java](java/15-machine-learning-api-and-module-design.md) | [C#](csharp/15-machine-learning-api-and-module-design.md) | [Scala](scala/15-machine-learning-api-and-module-design.md) | [Go](go/15-machine-learning-api-and-module-design.md) | [Rust](rust/15-machine-learning-api-and-module-design.md) | [Ruby](ruby/15-machine-learning-api-and-module-design.md) | [Clojure](clojure/15-machine-learning-api-and-module-design.md) | [Elixir](elixir/15-machine-learning-api-and-module-design.md) | [PHP](php/15-machine-learning-api-and-module-design.md) | — |

### 付録

| 付録 | テーマ | Python |
|------|--------|--------|
| A | 総合演習（Bank） | [Python](python/appendix-a-bank-exercise.md) |

## 多言語統合解説

第 1 波の 4 言語で同じ 15 章を書いた結果を、横断的に比べます。どの版から読むか迷ったら、[第 5 章 学習ロードマップ](integration/05-learning-roadmap.md) を参照してください。

| 章 | テーマ |
|----|--------|
| [第 1 章](integration/01-language-and-library-overview.md) | 言語とライブラリの概要 |
| [第 2 章](integration/02-data-structure-comparison.md) | データ構造の比較 |
| [第 3 章](integration/03-algorithm-implementation-comparison.md) | アルゴリズムの実装の比較 |
| [第 4 章](integration/04-library-ecosystem-comparison.md) | ライブラリのエコシステムの比較 |
| [第 5 章](integration/05-learning-roadmap.md) | 学習ロードマップ |

## 学習データ

学習データは書籍『スッキリわかる Python による機械学習入門』（インプレス, 2020）の配布データを使います。配布データは書籍購入者のみ利用できるため、本リポジトリには含まれていません。[書籍サポートページ](https://sukkiri.jp/books/sukkiri_ml) から `sukkiri-ml-codes.zip` を入手し、`tmp/` に置いてから次を実行してください。

```bash
npx gulp data:setup
npx gulp data:check
```

学習データは `apps/data/sukkiri-ml/` に配置されます。データが無い環境では、実データを使うテストはスキップされます。

## 対象読者

- プログラミングの基本構文を理解している方
- 機械学習の仕組みをコードを書きながら理解したい方
- 複数のプログラミング言語でのデータ処理の違いに興味がある方
- テスト駆動開発を実践的な題材で身につけたい方

## 参照

- 『テスト駆動開発』 - Kent Beck
- 『スッキリわかる Python による機械学習入門』 - インプレス, 2020
- [テスト駆動開発から始めるプログラミング入門](../getting-start-tdd/index.md)
