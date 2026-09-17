# 機械学習から始める Kotlin 入門

Kotlin は JVM 上で動く静的型付けの言語で、data class・sealed interface・null 安全・拡張関数・高階関数を備えています。本シリーズでは、Python 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、次に JVM の機械学習ライブラリ Tribuo に置き換えて結果を突き合わせながら、Kotlin の書き方とエコシステムを学びます。

Kotlin 版は、Python 版とともに Notebook（Kotlin Notebook）によるデータの探索と可視化も扱います。

## 特徴

- **静的型付けと null 安全**: 欠損値を `Double?` のような null 許容型で表し、補完するまでモデルに渡せないことをコンパイラが保証する
- **data class と sealed interface**: データや決定木の節を、値として比較できる型で簡潔に表せる
- **関数型の引数と拡張関数**: 評価関数や依存を関数として渡し、既存の型に処理を足せる
- **JVM のエコシステム**: Tribuo・Kotlin DataFrame・Ktor などを Gradle で導入できる

## Python 版との違い

分割の手順は Python 版と同じですが、乱数生成器が異なるため、訓練データとテストデータに入る行は Python 版と一致しません。件数は一致しますが、正解率や係数などの数値は Kotlin 版の実装で実測した値を載せています。理由は [執筆計画](../outline.md) の「Python 版との数値の違い」を参照してください。

## 開発環境

| ツール | 用途 |
|--------|------|
| [Gradle](https://gradle.org/)（Gradle Wrapper） | ビルドツール・タスクランナー |
| [kotlin.test](https://kotlinlang.org/api/latest/kotlin.test/) + JUnit Platform | テスティングフレームワーク |
| [ktlint](https://pinterest.github.io/ktlint/) | コードスタイルの検査・整形 |
| JDK 21 | Gradle のツールチェーンで自動取得 |

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [Kotlin DataFrame](https://kotlin.github.io/dataframe/) | データフレーム | 第 2 章 |
| [Kandy](https://kotlin.github.io/kandy/) | データの探索と可視化 | 第 2 章 |
| [Tribuo](https://tribuo.org/) | 機械学習（自作との突き合わせ） | 第 3 章 |
| [Ktor](https://ktor.io/)・kotlinx.serialization | 予測 API | 第 15 章 |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 002](../../../adr/002-kotlin-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/kotlin/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
cd apps/kotlin
./gradlew test
./gradlew runChapter -Pchapter=01
```

Windows の PowerShell では `./gradlew` の代わりに `.\gradlew.bat` を使います。

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
| 第 11 章（未執筆） | 評価指標と交差検証 |
| 第 12 章（未執筆） | 正則化とモデル選択 |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ |
|----|--------|
| [第 13 章](13-principal-component-analysis.md) | 主成分分析による次元削減 |
| 第 14 章（未執筆） | K-means によるクラスタリング |
| 第 15 章（未執筆） | 機械学習 API とモジュール設計 |

### 付録

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Kotlin で取り組んでください。
