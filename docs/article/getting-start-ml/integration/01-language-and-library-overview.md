---
type: Article
title: "第 1 章: 言語とライブラリの概要"
description: "第 1 波の 4 言語（Python・Kotlin・TypeScript・F#）の実行環境・テスト・静的解析・パッケージ管理・機械学習ライブラリ・API・Notebook を対応表で比べ、それぞれを選んだ理由と、選ばなかった代替案を ADR 001〜004 から整理する。"
tags: [article,getting-start-ml,integration]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T10:07:04Z }
---

# 第 1 章: 言語とライブラリの概要

## 1.1 4 言語の位置づけ

第 1 波の 4 言語は、機械学習のエコシステムの成熟度と、型の強さの組み合わせで次のように位置づけています。

| 言語 | 型 | 機械学習のエコシステム | シリーズでの役割 |
|------|----|---------------------|----------------|
| Python | 動的型付け + 型ヒント（mypy） | 最も成熟（pandas・NumPy・scikit-learn） | 参照実装。章の節構成を決めた |
| Kotlin | 静的型付け、OOP と FP の融合 | JVM のライブラリ（Tribuo・Kotlin DataFrame） | 静的型付けの言語で Python 版を追う |
| TypeScript | 構造的型付け、型は実行時に消える | 限られる（ml.js 系） | ライブラリが無い部分を自作で埋める環境の代表 |
| F# | 型推論のある静的型付け、関数型ファースト | .NET のライブラリ（ML.NET・FSharp.Stats） | 判別共用体・`option`・`Result` で誤りを型で防ぐ |

4 言語とも、同じ 15 章（第 1 部〜第 5 部）を同じ題材・同じ TODO リストで書きました。Python 版だけに付録 A（総合演習）があり、ほかの 3 言語は Python 版の付録へ案内しています。

## 1.2 開発の道具立て

### 実行環境・テスト・静的解析

| 用途 | Python | Kotlin | TypeScript | F# |
|------|--------|--------|------------|----|
| 実行環境 | CPython 3.12 | JVM（JDK 21） | Node.js 22（型除去で `.ts` を直接実行） | .NET 10 |
| テスト | pytest | kotlin.test + JUnit Platform | Vitest | xUnit v3（Microsoft.Testing.Platform） |
| 整形 | Ruff | ktlint | Prettier | Fantomas |
| 静的解析 | Ruff | detekt | ESLint（typescript-eslint） | FSharpLint |
| 型チェック | mypy | Kotlin のコンパイラ | tsc（`strict`・`noUncheckedIndexedAccess`） | F# のコンパイラ（警告をエラーにする） |
| カバレッジ | pytest-cov | Kover | @vitest/coverage-v8 | coverlet |

- 型チェックが「テストとは別の工程」になるのは Python（mypy）と TypeScript（tsc）です。TypeScript の第 1 章では、型チェックとテストの実行が別の工程であることを確かめました。Kotlin と F# は、コンパイルが通らなければテストも動きません
- F# 版では、コンパイラの警告をエラーにする設定（`TreatWarningsAsErrors`）にしています。パターンマッチの網羅漏れ（FS0025）や非推奨の API（FS0044）がコンパイルエラーになり、第 3・14・15 章で実際に誤りを止めました
- F# 版の第 5 章では、FSharpLint の設定ファイルが既定の設定を置き換える仕様のため、第 1 章からルールが 1 件も有効になっていなかったことが分かりました。静的解析の「警告 0 件」は、道具が本当に検査しているかを確かめて初めて意味を持ちます

### パッケージ管理とタスク

| 用途 | Python | Kotlin | TypeScript | F# |
|------|--------|--------|------------|----|
| パッケージ管理 | uv（`uv.lock`） | Gradle（バージョンカタログ） | npm（`package-lock.json`、`save-exact`） | NuGet（中央パッケージ管理、`packages.lock.json`） |
| 実行環境の版の固定 | `.python-version` | Gradle のツールチェーン・デーモンの JDK | `engines` と `engine-strict` | `global.json`・ローカルツール |
| 品質チェックのまとめ | tox | Gradle の `check` | npm scripts の `check` | `dotnet` CLI |
| リポジトリ全体のタスク | Gulp（`apps:check:python`） | Gulp（`apps:check:kotlin`） | Gulp（`apps:check:node`） | Gulp（`apps:check:fsharp`） |
| 開発環境 | Nix（`nix develop .#python`） | Nix（`.#kotlin`） | Nix（`.#node`） | Nix（`.#dotnet`） |

どの言語も、依存関係の依存関係まで正確な版をロックファイルに記録してコミットしています。TypeScript（`npm ci`）と F#（`dotnet restore --locked-mode`）の CI は、ロックファイルと食い違えば失敗します。言語ごとのパッケージ管理の違いは、各版の第 5 章で扱いました。

## 1.3 機械学習とデータのライブラリ

| 用途 | Python | Kotlin | TypeScript | F# |
|------|--------|--------|------------|----|
| データの表し方 | pandas の DataFrame | Kotlin DataFrame | 型付きレコードの配列（ライブラリなし） | レコード・`Map` と FSharp.Data の型プロバイダ |
| CSV の読み込み | pandas | Kotlin DataFrame | csv-parse | FSharp.Data（`CsvProvider`・`CsvFile`） |
| 行列 | NumPy | 自作の小さな行列の型と Tribuo の `DenseMatrix` | ml-matrix | 配列の配列（自作）と FSharp.Stats |
| 機械学習 | scikit-learn | Tribuo | ml.js 系（ml-cart・ml-kmeans など 9 パッケージ） | ML.NET・FSharp.Stats |
| モデルの保存 | joblib | Java のシリアライズ | JSON（zod で検証） | JSON（System.Text.Json）・ML.NET の zip |
| API | FastAPI | Ktor + kotlinx.serialization | Hono + zod | Giraffe |
| API の統合テスト | `TestClient` | `testApplication` | `app.request` | TestHost |
| 可視化 | matplotlib・seaborn | Kandy | なし | Plotly.NET |

- Python は、データの読み込みから学習・保存・API までを、事実上の標準のライブラリでそろえられます。自作したアルゴリズムの突き合わせ先が、ほぼすべての章にありました
- Kotlin と F# は、JVM・.NET の機械学習ライブラリを使いました。どちらも一部の手法が無く（Tribuo の PCA、ML.NET の単一の決定木など）、その章では自作を最終実装にするか、近い手法で比べました（第 4 章）
- TypeScript は、データフレームのライブラリを使わず、型付きのレコードの配列でデータを表しました。シード付きの乱数生成器も第 2 章で自作しています
- 可視化の節は、Python・Kotlin・F# の 3 言語が、Notebook を使って第 2・3・7〜14 章で扱いました。TypeScript 版は Notebook を作らず、Python 版・Kotlin 版の同じ節へ案内しています

## 1.4 選定の理由と、選ばなかった代替案

各言語のライブラリは、ADR で選定の理由と代替案を記録しています。判断の軸は、4 言語でおおむね共通でした。

| 判断の軸 | 例 |
|---------|----|
| 参照元との対応 | Python は、参照元の記事と同じ pandas・scikit-learn・FastAPI にした（[ADR 001](../../../adr/001-python-ml-libraries.md)） |
| ライセンス | Kotlin は、GPL-3.0 の Smile 6.x を採らず、Apache License 2.0 の Tribuo にした（[ADR 002](../../../adr/002-kotlin-ml-libraries.md)）。F# は、Intel MKL に依存する ML.NET の `Ols` を採らず、最小二乗は FSharp.Stats にした（[ADR 004](../../../adr/004-fsharp-ml-libraries.md)） |
| 保守の見込み | TypeScript は、更新が止まった danfojs-node を採らず、データフレームを使わない形にした（[ADR 003](../../../adr/003-typescript-ml-libraries.md)） |
| 題材としての価値 | TypeScript は、seedrandom を使わず、シード付き乱数を TDD で作る題材にした |
| 統合テストのしやすさ | TypeScript の Hono（`app.request`）、F# の Giraffe（TestHost）は、サーバーを起動せずに API をテストできる |
| 依存の小ささ | Kotlin は Spring Boot ではなく Ktor、Python は Flask ではなく FastAPI（型ヒントから入力の検証が得られる）にした |

F# 版の Notebook には、2026 年に廃止された Polyglot Notebooks を、廃止を明記したうえで使っています。Notebook は探索と可視化だけに使い、テストと記事の数値は `apps/fsharp/` のプロジェクトから求めているので、Notebook が動かなくなっても本文には影響しません。

## 1.5 同じ手順で書いたことで見えたこと

4 言語とも「自作 → テスト → ライブラリと突き合わせ」の同じ手順で書いたので、言語の違いが、どこで誤りを見つけたかの違いとして表れました。

| 誤りを見つけた場所 | 例 |
|------------------|----|
| 実行時のテスト | Python の第 9 章で、pandas の `std()` が不偏標準偏差を返すことをテストが見つけた |
| 型チェック（テストとは別の工程） | TypeScript の第 7 章で、`null` の比較と正解ラベルの欠損を型チェックが見つけた |
| コンパイル（テストの前） | F# の第 15 章で、エラーの種類を増やしたときに、説明の書き忘れを網羅性の検査（FS0025）が見つけた |
| 学習用テスト | 4 言語とも、ライブラリの既定値や罰則の尺度の違いを、使う前に学習用テストで見つけた（第 4 章） |

型が強いほど、誤りを早い段階（書いた直後のコンパイル）で見つけられます。一方で、ライブラリの振る舞い（既定値・尺度・並び順）は型では分からないので、どの言語でも学習用テストが欠かせませんでした。

## 1.6 まとめ

1. 4 言語は、エコシステムの成熟度と型の強さの組み合わせで選んだ。Python が参照実装、TypeScript がライブラリの少ない環境の代表
2. テスト・整形・静的解析・カバレッジ・ロックファイル・CI の道具立ては、4 言語とも同じ役割のものをそろえた
3. ライブラリは、参照元との対応・ライセンス・保守の見込み・題材としての価値・統合テストのしやすさで選び、ADR に記録した
4. 型の強さは「誤りをどの工程で見つけるか」に表れた。ライブラリの振る舞いは、どの言語でも学習用テストで確かめた

次の章では、4 言語のデータの表し方を比べます。
