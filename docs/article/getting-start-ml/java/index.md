---
type: Article
title: "機械学習から始める Java 入門"
description: "Java 版の概要。record・sealed interface・Stream API で機械学習のアルゴリズムを TDD で自作し、Tribuo に置き換えて突き合わせる。"
tags: [article,getting-start-ml,java]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T15:45:00Z }
---

# 機械学習から始める Java 入門

Java は JVM 上で動く静的型付けの言語で、Java 16 以降の record、Java 17 以降の sealed interface、Java 21 の `switch` のパターンマッチによって、データとモデルを簡潔に表せるようになりました。本シリーズでは、Python 版と同じ題材の機械学習のアルゴリズムを TDD で自作し、次に Java 製の機械学習ライブラリ Tribuo に置き換えて結果を突き合わせながら、Java の書き方とエコシステムを学びます。

Java 版は [Kotlin 版](../kotlin/index.md) と同じ JVM・Gradle・Tribuo を使います。同じ処理を 2 つの言語で書いた違いに注目できるように、各章で Kotlin 版と対比します。

## 特徴

- **record と sealed interface**: データや決定木の節を、値として比較できる型で表す。Kotlin の data class・sealed interface に当たる
- **Stream API**: データフレームのライブラリを使わず、record のリストを Stream API で変換・集計する
- **null はコンパイラが検査しない**: Kotlin の null 許容型に対し、Java では `Optional` や検査で「値が無い場合」を明示する
- **静的解析を最初から効かせる**: Error Prone・PMD・Spotless を `./gradlew check` に組み込み、第 1 章から指摘を受けながら書く

## ほかの版との違い

- Notebook による探索と可視化の節は設けません。グラフは [Python 版](../python/index.md) と [Kotlin 版](../kotlin/index.md) の可視化の節を参照してください
- 分割の手順は Python 版と同じですが、乱数生成器が異なるため、訓練データとテストデータに入る行は Python 版と一致しません。記事の数値は Java 版の実装で実測した値を載せています

## 開発環境

| ツール | 用途 |
|--------|------|
| [Gradle](https://gradle.org/)（Gradle Wrapper） | ビルドツール・タスクランナー |
| [JUnit 6](https://junit.org/) + [AssertJ](https://assertj.github.io/doc/) | テスティングフレームワークとアサーション |
| [Spotless](https://github.com/diffplug/spotless)（google-java-format） | 整形 |
| [Error Prone](https://errorprone.info/)・[PMD](https://pmd.github.io/) | 静的解析 |
| [JaCoCo](https://www.jacoco.org/jacoco/) | カバレッジ |
| JDK 21 | Gradle のツールチェーンで自動取得 |

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [Tribuo](https://tribuo.org/) | 機械学習（自作との突き合わせ） | 第 3 章 |
| [Javalin](https://javalin.io/) | 予測 API | 第 15 章 |

選定の理由と、章ごとにライブラリへ置き換えられる範囲は [ADR 005](../../../adr/005-java-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/java/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
cd apps/java
./gradlew check
./gradlew runChapter -Pchapter=01
```

Windows の PowerShell では `./gradlew` の代わりに `.\gradlew.bat` を使います。

## 章構成

Java 版は執筆中です。書き終えた章から順に公開します。

### 第 1 部: 機械学習と TDD の基本サイクル

| 章 | テーマ |
|----|--------|
| [第 1 章](01-machine-learning-and-first-test.md) | 機械学習とはじめてのテスト |
| 第 2 章 | データの前処理と三角測量 |
| 第 3 章 | 決定木による分類と明白な実装 |

### 第 2 部: 開発環境と自動化

| 章 | テーマ |
|----|--------|
| 第 4 章 | バージョン管理とデータ管理 |
| 第 5 章 | パッケージ管理と静的解析 |
| 第 6 章 | タスクランナーと CI/CD |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ |
|----|--------|
| 第 7 章 | 線形回帰による数値予測 |
| 第 8 章 | 実践的な分類と前処理パイプライン |
| 第 9 章 | 特徴量エンジニアリング |

### 第 4 部: モデルの改善と評価

| 章 | テーマ |
|----|--------|
| 第 10 章 | ロジスティック回帰とアンサンブル学習 |
| 第 11 章 | 評価指標と交差検証 |
| 第 12 章 | 正則化とモデル選択 |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ |
|----|--------|
| 第 13 章 | 主成分分析による次元削減 |
| 第 14 章 | K-means によるクラスタリング |
| 第 15 章 | 機械学習 API とモジュール設計 |

### 付録

総合演習（Bank）の解答例は Python 版のみです。演習問題は言語に依存しないので、[Python 版の付録 A](../python/appendix-a-bank-exercise.md) の問題に Java で取り組んでください。
