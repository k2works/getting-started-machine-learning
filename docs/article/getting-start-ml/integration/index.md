# 多言語統合解説

本章では、「機械学習から始めるプログラミング入門」の 9 言語（第 1 波の Python・Kotlin・TypeScript・F# と、第 2 波の Java・C#・Scala・Go・Rust）で同じ 15 章を書いた結果を横断的に比べます。言語とライブラリの選び方、データの表し方、アルゴリズムの書き方、ライブラリとの突き合わせで見つかったこと、そしてどの版から読むとよいかを解説します。

## 本章の目的

各言語版では、その言語で機械学習のアルゴリズムを TDD で自作し、ライブラリに置き換えて結果を突き合わせることに集中しました。本章では視点を上げて、次の問いに答えます。

- 同じ題材・同じ手順で書いても、言語によって何が変わり、何が変わらないのか
- データの表し方（データフレーム・レコード・`Map`）の違いは、コードとテストにどう表れたか
- 型の強さ（動的型付けと型ヒント・静的型付け・判別共用体）は、誤りをどこで見つけたか
- 機械学習のライブラリが成熟していない言語では、何を自作し、何を突き合わせられたか

## 対象言語一覧

| 波 | 言語 | 実行環境 | テスト | 機械学習ライブラリ | API | Notebook |
|----|------|---------|--------|------------------|-----|----------|
| 1 | [Python](../python/index.md) | CPython 3.12 | pytest | scikit-learn | FastAPI | JupyterLab |
| 1 | [Kotlin](../kotlin/index.md) | JVM（JDK 21） | kotlin.test + JUnit | Tribuo | Ktor | Kotlin Notebook |
| 1 | [TypeScript](../typescript/index.md) | Node.js 22 | Vitest | ml.js 系 | Hono | なし |
| 1 | [F#](../fsharp/index.md) | .NET 10 | xUnit v3 | ML.NET・FSharp.Stats | Giraffe | Polyglot Notebooks |
| 2 | [Java](../java/index.md) | JVM（JDK 21） | JUnit 6 + AssertJ | Tribuo | Javalin | なし |
| 2 | [C#](../csharp/index.md) | .NET 10 | xUnit v3 | ML.NET | ASP.NET Core Minimal API | なし |
| 2 | [Scala](../scala/index.md) | JVM（Scala 3.3 LTS） | ScalaTest | Tribuo | http4s + circe | なし |
| 2 | [Go](../go/index.md) | Go 1.25 | 標準の `testing`（表駆動） | gonum | 標準の `net/http` | なし |
| 2 | [Rust](../rust/index.md) | Rust 1.91（edition 2024） | 標準の `#[test]` | linfa | axum + tokio | なし |

第 2 波の 5 言語は Notebook の節を設けず、Python 版・Kotlin 版の可視化の節へ案内しています。

## 章構成

| 章 | タイトル | 内容 |
|----|---------|------|
| 1 | [言語とライブラリの概要](01-language-and-library-overview.md) | 9 言語の道具立て（実行環境・テスト・静的解析・ライブラリ・API・Notebook）と選定の理由 |
| 2 | [データ構造の比較](02-data-structure-comparison.md) | データフレーム・レコード・`Map` によるデータの表し方、欠損値、分割と乱数、言語によって数値が変わる理由 |
| 3 | [アルゴリズムの実装の比較](03-algorithm-implementation-comparison.md) | 決定木・評価と交差検証・エラーの表し方・API の層構成を 9 言語でどう書いたか |
| 4 | [ライブラリのエコシステムの比較](04-library-ecosystem-comparison.md) | 章ごとのライブラリへの置き換えの可否と、突き合わせで見つかったライブラリの癖 |
| 5 | [学習ロードマップ](05-learning-roadmap.md) | 目的別の読み方と、版をまたいだ読み進め方 |

## 本章の数値について

本章に載せる数値と事実は、各言語版の記事・[ADR 001〜009](../../../adr/index.md)・`apps/` の実装から取ったものです。本章のために新たに測った値はありません。各言語版は同じ手順で訓練データとテストデータに分けていますが、乱数生成器が違うので、正解率などの数値は言語ごとに違います（第 2 章で扱います）。

第 3 波（Ruby・PHP・Elixir・Clojure・Haskell）の言語を追加したら、本章の各表に行と列を加えて更新します。表が横に広がりすぎる箇所は、波ごとに分けるか縦持ちに組み替えています。
