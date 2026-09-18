# 機械学習から始めるプログラミング入門

本記事シリーズは、機械学習を題材にして、さまざまなプログラミング言語の特徴を実践的に学ぶためのガイドです。

データを読み込み、前処理し、モデルを学習・評価し、API として届けるまでの流れを、テスト駆動開発（TDD）で一歩ずつ実装します。各アルゴリズムはまず自作し、次にその言語の機械学習ライブラリで置き換えて結果を突き合わせます。自作で原理と言語の書き方を学び、置き換えでエコシステムを学びます。

執筆計画は [執筆計画アウトライン](outline.md) を、執筆の進め方は [執筆ワークフロー](workflow.md) を参照してください。

## 言語別解説

言語は 3 つの波に分けて順に追加します。第 1 波の Python 版と Kotlin 版は全章を執筆済みで、現在は TypeScript 版を執筆しています。

| 波 | 言語 | 環境 | 特徴 | 状況 |
|----|------|------|------|------|
| 1 | [Python](python/index.md) | CPython | 機械学習ライブラリが成熟、型ヒント、Jupyter Lab による探索と可視化 | 完了 |
| 1 | [Kotlin](kotlin/index.md) | JVM | 静的型付け、OOP と FP の融合、Kotlin Notebook による探索と可視化 | 完了 |
| 1 | [TypeScript](typescript/index.md) | Node.js | 静的型付け、機械学習ライブラリが限られる環境での自作 | 執筆中 |
| 2 | Java / C# / F# / Scala / Rust / Go | — | — | 未着手 |
| 3 | Ruby / PHP / Elixir / Clojure / Haskell | — | — | 未着手 |

## 章構成

### 第 1 部: 機械学習と TDD の基本サイクル

| 章 | テーマ | Python | Kotlin | TypeScript |
|----|--------|--------|--------|------------|
| 1 | 機械学習とはじめてのテスト | [Python](python/01-machine-learning-and-first-test.md) | [Kotlin](kotlin/01-machine-learning-and-first-test.md) | [TypeScript](typescript/01-machine-learning-and-first-test.md) |
| 2 | データの前処理と三角測量 | [Python](python/02-data-preprocessing-and-triangulation.md) | [Kotlin](kotlin/02-data-preprocessing-and-triangulation.md) | [TypeScript](typescript/02-data-preprocessing-and-triangulation.md) |
| 3 | 決定木による分類と明白な実装 | [Python](python/03-decision-tree-and-obvious-implementation.md) | [Kotlin](kotlin/03-decision-tree-and-obvious-implementation.md) | [TypeScript](typescript/03-decision-tree-and-obvious-implementation.md) |

### 第 2 部: 開発環境と自動化

| 章 | テーマ | Python | Kotlin | TypeScript |
|----|--------|--------|--------|------------|
| 4 | バージョン管理とデータ管理 | [Python](python/04-version-control-and-data-management.md) | [Kotlin](kotlin/04-version-control-and-data-management.md) | [TypeScript](typescript/04-version-control-and-data-management.md) |
| 5 | パッケージ管理と静的解析 | [Python](python/05-package-management-and-static-analysis.md) | [Kotlin](kotlin/05-package-management-and-static-analysis.md) | [TypeScript](typescript/05-package-management-and-static-analysis.md) |
| 6 | タスクランナーと CI/CD | [Python](python/06-task-runner-and-ci-cd.md) | [Kotlin](kotlin/06-task-runner-and-ci-cd.md) | [TypeScript](typescript/06-task-runner-and-ci-cd.md) |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ | Python | Kotlin | TypeScript |
|----|--------|--------|--------|------------|
| 7 | 線形回帰による数値予測 | [Python](python/07-linear-regression.md) | [Kotlin](kotlin/07-linear-regression.md) | — |
| 8 | 実践的な分類と前処理パイプライン | [Python](python/08-classification-and-preprocessing-pipeline.md) | [Kotlin](kotlin/08-classification-and-preprocessing-pipeline.md) | — |
| 9 | 特徴量エンジニアリング | [Python](python/09-feature-engineering.md) | [Kotlin](kotlin/09-feature-engineering.md) | — |

### 第 4 部: モデルの改善と評価

| 章 | テーマ | Python | Kotlin | TypeScript |
|----|--------|--------|--------|------------|
| 10 | ロジスティック回帰とアンサンブル学習 | [Python](python/10-logistic-regression-and-ensemble.md) | [Kotlin](kotlin/10-logistic-regression-and-ensemble.md) | — |
| 11 | 評価指標と交差検証 | [Python](python/11-evaluation-metrics-and-cross-validation.md) | [Kotlin](kotlin/11-evaluation-metrics-and-cross-validation.md) | — |
| 12 | 正則化とモデル選択 | [Python](python/12-regularization-and-model-selection.md) | [Kotlin](kotlin/12-regularization-and-model-selection.md) | — |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ | Python | Kotlin | TypeScript |
|----|--------|--------|--------|------------|
| 13 | 主成分分析による次元削減 | [Python](python/13-principal-component-analysis.md) | [Kotlin](kotlin/13-principal-component-analysis.md) | — |
| 14 | K-means によるクラスタリング | [Python](python/14-k-means-clustering.md) | [Kotlin](kotlin/14-k-means-clustering.md) | — |
| 15 | 機械学習 API とモジュール設計 | [Python](python/15-machine-learning-api-and-module-design.md) | [Kotlin](kotlin/15-machine-learning-api-and-module-design.md) | — |

### 付録

| 付録 | テーマ | Python |
|------|--------|--------|
| A | 総合演習（Bank） | [Python](python/appendix-a-bank-exercise.md) |

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
