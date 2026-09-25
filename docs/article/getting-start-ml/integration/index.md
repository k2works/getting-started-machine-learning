# 多言語統合解説

本章では、「機械学習から始めるプログラミング入門」の 14 言語（第 1 波の Python・Kotlin・TypeScript・F#、第 2 波の Java・C#・Scala・Go・Rust、第 3 波の Ruby・Clojure・Elixir・PHP・Haskell）で同じ 15 章を書いた結果を横断的に比べます。言語とライブラリの選び方、データの表し方、アルゴリズムの書き方、ライブラリとの突き合わせで見つかったこと、そしてどの版から読むとよいかを解説します。

## 本章の目的

各言語版では、その言語で機械学習のアルゴリズムを TDD で自作し、ライブラリに置き換えて結果を突き合わせることに集中しました。本章では視点を上げて、次の問いに答えます。

- 同じ題材・同じ手順で書いても、言語によって何が変わり、何が変わらないのか
- データの表し方（データフレーム・レコード・`Map`）の違いは、コードとテストにどう表れたか
- 型の強さ（動的型付けと型ヒント・漸進的な型付け・静的型付け・判別共用体）は、誤りをどこで見つけたか
- 機械学習のライブラリが成熟していない言語では、何を自作し、何を突き合わせられたか
- 乱数生成器そのものを自作すると、言語をまたいで数値を突き合わせられるのか

## 対象言語一覧

**第 1 波**

| 言語 | 実行環境 | テスト | 機械学習ライブラリ | API | Notebook |
|------|---------|--------|------------------|-----|----------|
| [Python](../python/index.md) | CPython 3.12 | pytest | scikit-learn | FastAPI | JupyterLab |
| [Kotlin](../kotlin/index.md) | JVM（JDK 21） | kotlin.test + JUnit | Tribuo | Ktor | Kotlin Notebook |
| [TypeScript](../typescript/index.md) | Node.js 22 | Vitest | ml.js 系 | Hono | なし |
| [F#](../fsharp/index.md) | .NET 10 | xUnit v3 | ML.NET・FSharp.Stats | Giraffe | Polyglot Notebooks |

**第 2 波**

| 言語 | 実行環境 | テスト | 機械学習ライブラリ | API | Notebook |
|------|---------|--------|------------------|-----|----------|
| [Java](../java/index.md) | JVM（JDK 21） | JUnit 6 + AssertJ | Tribuo | Javalin | なし |
| [C#](../csharp/index.md) | .NET 10 | xUnit v3 | ML.NET | ASP.NET Core Minimal API | なし |
| [Scala](../scala/index.md) | JVM（Scala 3.3 LTS） | ScalaTest | Tribuo | http4s + circe | なし |
| [Go](../go/index.md) | Go 1.25 | 標準の `testing`（表駆動） | gonum | 標準の `net/http` | なし |
| [Rust](../rust/index.md) | Rust 1.91（edition 2024） | 標準の `#[test]` | linfa | axum + tokio | なし |

**第 3 波**

| 言語 | 実行環境 | テスト | 機械学習ライブラリ | API | Notebook |
|------|---------|--------|------------------|-----|----------|
| [Ruby](../ruby/index.md) | CRuby 3.3 | Minitest + SimpleCov | Rumale 2.2 | Sinatra + Puma | なし |
| [Clojure](../clojure/index.md) | JVM（JDK 21） | clojure.test | Tribuo 4.3 | Ring + Jetty | なし |
| [Elixir](../elixir/index.md) | BEAM（OTP 27） | ExUnit | Scholar 0.4 | Plug + Bandit | なし |
| [PHP](../php/index.md) | PHP 8.4 | PHPUnit 11 | Rubix ML 2.6 | 標準の組み込みサーバー | なし |
| [Haskell](../haskell/index.md) | GHC 9.10 | Hspec | 無し（線形代数の hmatrix のみ） | Scotty | なし |

Notebook を作ったのは第 1 波の 4 言語のうち Python・Kotlin・F# の 3 言語だけです。第 2 波・第 3 波の 10 言語は Notebook の節を設けず、Python 版・Kotlin 版の可視化の節へ案内しています。

## 章構成

| 章 | タイトル | 内容 |
|----|---------|------|
| 1 | [言語とライブラリの概要](01-language-and-library-overview.md) | 14 言語の道具立て（実行環境・テスト・静的解析・ライブラリ・API・Notebook）と選定の理由 |
| 2 | [データ構造の比較](02-data-structure-comparison.md) | データフレーム・レコード・`Map` によるデータの表し方、欠損値、分割と乱数、言語によって数値が変わる理由と、そろえるために乱数生成器を自作した 3 言語 |
| 3 | [アルゴリズムの実装の比較](03-algorithm-implementation-comparison.md) | 決定木・評価と交差検証・エラーの表し方・API の層構成を 14 言語でどう書いたか |
| 4 | [ライブラリのエコシステムの比較](04-library-ecosystem-comparison.md) | 章ごとのライブラリへの置き換えの可否と、突き合わせで見つかったライブラリの癖 |
| 5 | [学習ロードマップ](05-learning-roadmap.md) | 目的別の読み方と、版をまたいだ読み進め方、14 言語の特性を 6 軸で比べるレーダーチャートと総合スコア |

## 本章の数値について

本章に載せる数値と事実は、各言語版の記事・[ADR 001〜014](../../../adr/index.md)・`apps/` の実装から取ったものです。本章のために新たに測った値はありません。

第 2 波までは「乱数生成器が違うので正解率などの数値は言語ごとに違う」と書いていましたが、第 3 波でこの前提が変わりました。**Elixir・PHP・Haskell の 3 言語は `java.util.Random` と同じ線形合同法を自作した**ので、`java.util.Random` を使う 3 言語（Java・Scala・Clojure）と分割が一致し、第 3 章以降の数値が言語をまたいで突き合わせられるようになっています。詳しくは第 2 章で扱います。

表が横に広がりすぎる箇所は、波ごとに分けるか縦持ちに組み替えています。
