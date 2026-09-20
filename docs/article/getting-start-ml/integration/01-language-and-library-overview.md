---
type: Article
title: "第 1 章: 言語とライブラリの概要"
description: "9 言語の実行環境・テスト・静的解析・パッケージ管理・機械学習ライブラリ・API・Notebook を対応表で比べ、それぞれを選んだ理由と、選ばなかった代替案を ADR 001〜009 から整理する。"
tags: [article,getting-start-ml,integration]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T10:07:04Z }
---

# 第 1 章: 言語とライブラリの概要

## 1.1 9 言語の位置づけ

9 言語は、機械学習のエコシステムの成熟度と、型の強さの組み合わせで次のように位置づけています。

| 波 | 言語 | 型 | 機械学習のエコシステム | シリーズでの役割 |
|----|------|----|---------------------|----------------|
| 1 | Python | 動的型付け + 型ヒント（mypy） | 最も成熟（pandas・NumPy・scikit-learn） | 参照実装。章の節構成を決めた |
| 1 | Kotlin | 静的型付け、OOP と FP の融合 | JVM のライブラリ（Tribuo・Kotlin DataFrame） | 静的型付けの言語で Python 版を追う |
| 1 | TypeScript | 構造的型付け、型は実行時に消える | 限られる（ml.js 系） | ライブラリが無い部分を自作で埋める環境の代表 |
| 1 | F# | 型推論のある静的型付け、関数型ファースト | .NET のライブラリ（ML.NET・FSharp.Stats） | 判別共用体・`option`・`Result` で誤りを型で防ぐ |
| 2 | Java | 静的型付け、record と sealed interface | JVM のライブラリ（Tribuo） | Kotlin 版との対比。null の検査が無い言語で同じことを書く |
| 2 | C# | 静的型付け、record と LINQ | .NET のライブラリ（ML.NET） | F# 版との対比。同じ .NET で命令型に書くとどうなるか |
| 2 | Scala | 静的型付け、case class と enum | JVM のライブラリ（Tribuo） | Java 版との対比。不変のコレクションと式指向 |
| 2 | Go | 静的型付け、例外も判別共用体も無い | 限られる（gonum のみ） | TypeScript 版と同じ「自作の比重が大きい」立場 |
| 2 | Rust | 静的型付け、所有権と `Result` | 揃っている（linfa） | Java 版・C# 版と同じ立場。所有権とクレートの版が固有の論点 |

9 言語とも、同じ 15 章（第 1 部〜第 5 部）を同じ題材・同じ TODO リストで書きました。Python 版だけに付録 A（総合演習）があり、ほかの 8 言語は Python 版の付録へ案内しています。

第 2 波の 5 言語を書く前の見込みは、2 か所で外れました。**Go は見込みどおり「ライブラリが限られる」**（gonum に決定木・K-means・ロジスティック回帰が無い）でしたが、**Rust は linfa が決定木から交差検証まで揃っており**、当初 Go 版と同じ立場に置く予定を Java 版・C# 版の側へ改めました（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。「ライブラリが成熟しているか」は、書いて確かめるまで分かりません。

## 1.2 開発の道具立て

### 実行環境・テスト・静的解析

**第 1 波**

| 用途 | Python | Kotlin | TypeScript | F# |
|------|--------|--------|------------|----|
| 実行環境 | CPython 3.12 | JVM（JDK 21） | Node.js 22（型除去で `.ts` を直接実行） | .NET 10 |
| テスト | pytest | kotlin.test + JUnit Platform | Vitest | xUnit v3（Microsoft.Testing.Platform） |
| 整形 | Ruff | ktlint | Prettier | Fantomas |
| 静的解析 | Ruff | detekt | ESLint（typescript-eslint） | FSharpLint |
| 型チェック | mypy | Kotlin のコンパイラ | tsc（`strict`・`noUncheckedIndexedAccess`） | F# のコンパイラ（警告をエラーにする） |
| カバレッジ | pytest-cov | Kover | @vitest/coverage-v8 | coverlet |

**第 2 波**

| 用途 | Java | C# | Scala | Go | Rust |
|------|------|----|-------|----|------|
| 実行環境 | JVM（JDK 21） | .NET 10 | JVM（Scala 3.3 LTS） | Go 1.25 | Rust 1.91（edition 2024） |
| テスト | JUnit 6 + AssertJ | xUnit v3 | ScalaTest | 標準の `testing`（表駆動テスト） | 標準の `#[test]`（実装と同じファイル） |
| 整形 | Spotless（google-java-format） | `dotnet format` と `.editorconfig` | scalafmt | `gofmt` | rustfmt |
| 静的解析 | Error Prone・PMD | .NET アナライザー（`TreatWarningsAsErrors`） | コンパイラの警告 + `-Xfatal-warnings` | `go vet`・golangci-lint | clippy（`-D warnings`） |
| 型チェック | Java のコンパイラ | C# のコンパイラ（null 許容参照型） | Scala のコンパイラ | Go のコンパイラ | Rust のコンパイラ（所有権・借用も検査） |
| カバレッジ | JaCoCo | coverlet.MTP | sbt-scoverage | `go test -cover` | cargo-llvm-cov |

- 型チェックが「テストとは別の工程」になるのは Python（mypy）と TypeScript（tsc）だけです。ほかの 7 言語は、コンパイルが通らなければテストも動きません
- **整形と静的解析が言語に同梱されているか**で分かれます。Go（`gofmt`・`go vet`）と Rust（rustfmt・clippy）は言語の配布物に含まれ、設定をほとんど書きません。Java は Spotless・Error Prone・PMD の 3 つを Gradle に足す必要があり、設定がいちばん長くなりました
- 警告をエラーにする仕組みは言語ごとに名前が違います（Scala の `-Xfatal-warnings`、C# の `TreatWarningsAsErrors`、Rust の `-D warnings`、F# の `TreatWarningsAsErrors`）。どれも「警告を残したまま先へ進めない」ためのもので、9 言語のうち 5 言語で使いました
- TypeScript の第 1 章では、型チェックとテストの実行が別の工程であることを確かめました
- F# 版では、コンパイラの警告をエラーにする設定（`TreatWarningsAsErrors`）にしています。パターンマッチの網羅漏れ（FS0025）や非推奨の API（FS0044）がコンパイルエラーになり、第 3・14・15 章で実際に誤りを止めました
- F# 版の第 5 章では、FSharpLint の設定ファイルが既定の設定を置き換える仕様のため、第 1 章からルールが 1 件も有効になっていなかったことが分かりました。静的解析の「警告 0 件」は、道具が本当に検査しているかを確かめて初めて意味を持ちます

### パッケージ管理とタスク

**第 1 波**

| 用途 | Python | Kotlin | TypeScript | F# |
|------|--------|--------|------------|----|
| パッケージ管理 | uv（`uv.lock`） | Gradle（バージョンカタログ） | npm（`package-lock.json`、`save-exact`） | NuGet（中央パッケージ管理、`packages.lock.json`） |
| 実行環境の版の固定 | `.python-version` | Gradle のツールチェーン・デーモンの JDK | `engines` と `engine-strict` | `global.json`・ローカルツール |
| 品質チェックのまとめ | tox | Gradle の `check` | npm scripts の `check` | `dotnet` CLI |
| 開発環境 | Nix（`nix develop .#python`） | Nix（`.#kotlin`） | Nix（`.#node`） | Nix（`.#dotnet`） |

**第 2 波**

| 用途 | Java | C# | Scala | Go | Rust |
|------|------|----|-------|----|------|
| パッケージ管理 | Gradle（バージョンカタログ） | NuGet（中央パッケージ管理、`packages.lock.json`） | sbt | Go Modules（`go.mod`・`go.sum`） | Cargo（`Cargo.toml`・`Cargo.lock`） |
| 実行環境の版の固定 | Gradle のツールチェーン | `global.json` | `project/build.properties` の sbt の版 | `go.mod` の `go` 指令 | edition と `rust-toolchain` 相当（Nix で固定） |
| 品質チェックのまとめ | Gradle の `check` | `dotnet` CLI | sbt のタスク | `go` のサブコマンドを並べる | `cargo` のサブコマンドを並べる |
| 開発環境 | Nix（`.#java`） | Nix（`.#dotnet`） | Nix（`.#scala`） | Nix（`.#go`） | Nix（`.#rust`） |

リポジトリ全体のタスクは 9 言語とも Gulp にそろえました（`npx gulp apps:check:<言語>`）。どの言語も、依存関係の依存関係まで正確な版をロックファイルに記録してコミットしています。TypeScript（`npm ci`）と F#・C#（`dotnet restore --locked-mode`）の CI は、ロックファイルと食い違えば失敗します。

**ロックファイルが「再現性の最後の砦」になった例**が第 2 波で出ました。Rust 版の第 2 章で使う `rand` 0.8 の `StdRng` は、ドキュメントに「版をまたいで再現可能と考えるべきではない」と明記されています。つまり記事に載せた数値は、`Cargo.lock` で版を固定しているから再現できるのであって、言語の保証ではありません（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。言語ごとのパッケージ管理の違いは、各版の第 5 章で扱いました。

## 1.3 機械学習とデータのライブラリ

**第 1 波**

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

**第 2 波**

| 用途 | Java | C# | Scala | Go | Rust |
|------|------|----|-------|----|------|
| データの表し方 | record のリストと Stream API | record のリストと LINQ | case class と不変のコレクション | 構造体と `map[string]string` | 構造体と `HashMap<String, String>` |
| CSV の読み込み | 標準ライブラリ（自作） | 標準ライブラリ（自作） | 標準ライブラリ（自作） | 標準ライブラリ（自作） | csv クレート |
| 行列 | 自作の小さな型 | 自作の小さな型 | 自作の小さな不変の型 | gonum の `mat` | ndarray 0.16 |
| 機械学習 | Tribuo 4.3.2 | ML.NET 5.0.0 | Tribuo 4.3.2 | gonum v0.17.0 | linfa 0.8.1 |
| モデルの保存 | Java のシリアライズ | JSON（System.Text.Json） | JSON（circe） | `encoding/gob` | serde + serde_json |
| API | Javalin | ASP.NET Core Minimal API | http4s + circe | 標準の `net/http` | axum + tokio |
| API の統合テスト | `JavalinTest` | `WebApplicationFactory` | http4s の `Router` を直接呼ぶ | `httptest` | tower の `oneshot` |
| 可視化 | なし（Python 版・Kotlin 版へ案内） | なし | なし | なし | なし |

- Python は、データの読み込みから学習・保存・API までを、事実上の標準のライブラリでそろえられます。自作したアルゴリズムの突き合わせ先が、ほぼすべての章にありました
- **データフレームのライブラリを使ったのは Python と Kotlin の 2 言語だけ**です。ほかの 7 言語はレコード・構造体・`Map` でデータを表しました。シリーズの方針として、データの表し方そのものを題材にしたかったためです
- **機械学習ライブラリの充実度は 3 段階に分かれました。** scikit-learn（Python）・Tribuo（Kotlin・Java・Scala）・ML.NET（F#・C#）・linfa（Rust）はほとんどの章で突き合わせられ、ml.js 系（TypeScript）は一部、gonum（Go）は線形回帰・PCA・ROC だけでした
- **API は「標準ライブラリだけで書けるか」で分かれました。** Go は `net/http`（Go 1.22 のルーティング）だけで足りましたが、ほかの 8 言語はフレームワークを入れています。Rust の標準ライブラリには HTTP サーバーがありません
- 可視化の節は、Python・Kotlin・F# の 3 言語が、Notebook を使って第 2・3・7〜14 章で扱いました。TypeScript 版と第 2 波の 5 言語は Notebook を作らず、Python 版・Kotlin 版の同じ節へ案内しています

## 1.4 選定の理由と、選ばなかった代替案

各言語のライブラリは、ADR 001〜009 で選定の理由と代替案を記録しています。判断の軸は、9 言語でおおむね共通でした。

| 判断の軸 | 例 |
|---------|----|
| 参照元との対応 | Python は、参照元の記事と同じ pandas・scikit-learn・FastAPI にした（[ADR 001](../../../adr/001-python-ml-libraries.md)） |
| ライセンス | Kotlin は、GPL-3.0 の Smile 6.x を採らず、Apache License 2.0 の Tribuo にした（[ADR 002](../../../adr/002-kotlin-ml-libraries.md)）。Scala も同じ理由で Smile を採らず Tribuo にした（[ADR 007](../../../adr/007-scala-ml-libraries.md)）。F# は、Intel MKL に依存する ML.NET の `Ols` を採らず、最小二乗は FSharp.Stats にした（[ADR 004](../../../adr/004-fsharp-ml-libraries.md)） |
| 保守の見込み | TypeScript は、更新が止まった danfojs-node を採らず、データフレームを使わない形にした（[ADR 003](../../../adr/003-typescript-ml-libraries.md)）。Go は、最後のコミットが 2022 年の GoLearn を採らず、gonum だけにした（[ADR 008](../../../adr/008-go-ml-libraries.md)） |
| ほかの版との依存の重なり | Rust は、linfa が依存する ndarray 0.16・rand 0.8 に固定した。最新の 0.17・0.9 を混ぜると「同じ名前の別の型」になって渡せない（[ADR 009](../../../adr/009-rust-ml-libraries.md)） |
| 題材としての価値 | TypeScript は、seedrandom を使わず、シード付き乱数を TDD で作る題材にした |
| 統合テストのしやすさ | TypeScript の Hono（`app.request`）、F# の Giraffe（TestHost）、Java の `JavalinTest`、Go の `httptest`、Rust の tower の `oneshot` は、サーバーを起動せずに API をテストできる |
| 依存の小ささ | Kotlin は Spring Boot ではなく Ktor、Python は Flask ではなく FastAPI（型ヒントから入力の検証が得られる）にした。Go は第 15 章の要件が標準の `net/http` で足りたので、フレームワークを入れなかった |

F# 版の Notebook には、2026 年に廃止された Polyglot Notebooks を、廃止を明記したうえで使っています。Notebook は探索と可視化だけに使い、テストと記事の数値は `apps/fsharp/` のプロジェクトから求めているので、Notebook が動かなくなっても本文には影響しません。

## 1.5 同じ手順で書いたことで見えたこと

9 言語とも「自作 → テスト → ライブラリと突き合わせ」の同じ手順で書いたので、言語の違いが、どこで誤りを見つけたかの違いとして表れました。

| 誤りを見つけた場所 | 例 |
|------------------|----|
| 実行時のテスト | Python の第 9 章で、pandas の `std()` が不偏標準偏差を返すことをテストが見つけた。Rust の第 3 章で、`max_by_key` が同点のとき最後の要素を返すことを 2 件のテストの失敗が見つけた |
| 型チェック（テストとは別の工程） | TypeScript の第 7 章で、`null` の比較と正解ラベルの欠損を型チェックが見つけた |
| コンパイル（テストの前） | F# の第 15 章で、エラーの種類を増やしたときに、説明の書き忘れを網羅性の検査（FS0025）が見つけた。Rust の第 14 章で、rand 0.9 の乱数生成器を linfa に渡そうとして「別の版の型」だと分かった |
| 静的解析 | Java の第 2 章で、Error Prone が配列を持つ record の `equals` の落とし穴（`ArrayRecordComponent`）を止めた |
| 学習用テスト | 9 言語とも、ライブラリの既定値や罰則の尺度の違いを、使う前に学習用テストで見つけた（第 4 章） |

型が強いほど、誤りを早い段階（書いた直後のコンパイル）で見つけられます。一方で、ライブラリの振る舞い（既定値・尺度・並び順・決定性）は型では分からないので、どの言語でも学習用テストが欠かせませんでした。

第 2 波では、**ライブラリのほうが自作より不安定だった例**も出ました。Rust の linfa-trees は、ダミー変数の多いデータ（第 8 章の `Survived.csv`）で実行ごとに正解率が 0.788〜0.810 と揺れます。「ライブラリは自分で書くより正しい」とは限らず、突き合わせる相手がいるからこそ気づけます。

## 1.6 まとめ

1. 9 言語は、エコシステムの成熟度と型の強さの組み合わせで選んだ。Python が参照実装、TypeScript と Go がライブラリの少ない環境の代表。**Rust は「少ない」と見込んで書き始めたが、linfa が揃っていたので位置づけを改めた**
2. テスト・整形・静的解析・カバレッジ・ロックファイル・CI の道具立ては、9 言語とも同じ役割のものをそろえた。整形と静的解析が言語に同梱されているのは Go と Rust だけ
3. ライブラリは、参照元との対応・ライセンス・保守の見込み・題材としての価値・統合テストのしやすさ・ほかの依存との版の重なりで選び、ADR 001〜009 に記録した
4. 型の強さは「誤りをどの工程で見つけるか」に表れた。ライブラリの振る舞い（既定値・尺度・並び順・決定性）は、どの言語でも学習用テストで確かめた

次の章では、9 言語のデータの表し方を比べます。
