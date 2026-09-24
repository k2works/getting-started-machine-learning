---
type: Article
title: "第 1 章: 言語とライブラリの概要"
description: "14 言語の実行環境・テスト・静的解析・パッケージ管理・機械学習ライブラリ・API・Notebook を対応表で比べ、それぞれを選んだ理由と、選ばなかった代替案を ADR 001〜014 から整理する。"
tags: [article,getting-start-ml,integration]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 1 章: 言語とライブラリの概要

## 1.1 14 言語の位置づけ

14 言語は、機械学習のエコシステムの成熟度と、型の強さの組み合わせで次のように位置づけています。

**第 1 波**

| 言語 | 型 | 機械学習のエコシステム | シリーズでの役割 |
|------|----|---------------------|----------------|
| Python | 動的型付け + 型ヒント（mypy） | 最も成熟（pandas・NumPy・scikit-learn） | 参照実装。章の節構成を決めた |
| Kotlin | 静的型付け、OOP と FP の融合 | JVM のライブラリ（Tribuo・Kotlin DataFrame） | 静的型付けの言語で Python 版を追う |
| TypeScript | 構造的型付け、型は実行時に消える | 限られる（ml.js 系） | ライブラリが無い部分を自作で埋める環境の代表 |
| F# | 型推論のある静的型付け、関数型ファースト | .NET のライブラリ（ML.NET・FSharp.Stats） | 判別共用体・`option`・`Result` で誤りを型で防ぐ |

**第 2 波**

| 言語 | 型 | 機械学習のエコシステム | シリーズでの役割 |
|------|----|---------------------|----------------|
| Java | 静的型付け、record と sealed interface | JVM のライブラリ（Tribuo） | Kotlin 版との対比。null の検査が無い言語で同じことを書く |
| C# | 静的型付け、record と LINQ | .NET のライブラリ（ML.NET） | F# 版との対比。同じ .NET で命令型に書くとどうなるか |
| Scala | 静的型付け、case class と enum | JVM のライブラリ（Tribuo） | Java 版との対比。不変のコレクションと式指向 |
| Go | 静的型付け、例外も判別共用体も無い | 限られる（gonum のみ） | TypeScript 版と同じ「自作の比重が大きい」立場 |
| Rust | 静的型付け、所有権と `Result` | 揃っている（linfa） | Java 版・C# 版と同じ立場。所有権とクレートの版が固有の論点 |

**第 3 波**

| 言語 | 型 | 機械学習のエコシステム | シリーズでの役割 |
|------|----|---------------------|----------------|
| Ruby | 動的型付け（RBS・Steep を**使わない**と決めた） | 揃っている（Rumale） | Python 版に近い書き方で、型の道具を使わない側の代表 |
| Clojure | 動的型付け、不変のマップとベクタ | JVM のライブラリ（Tribuo） | Java 版・Scala 版と同じライブラリを同じ JDK で呼ぶ |
| Elixir | 動的型付け、パターンマッチ | **限られる（Scholar に決定木もランダムフォレストも無い）** | 本書の背骨である決定木が丸ごと無い環境の代表 |
| PHP | 漸進的な型付け（`strict_types` と PHPStan レベル 9） | 揃っている（Rubix ML） | Ruby 版と**逆の選択**。型の道具を使う動的型付けの言語 |
| Haskell | 型の検査が最も厳しい。`Either`・`Maybe`・`-Wall -Werror` | **ほとんど無い（線形代数の hmatrix だけ）** | 自作の比重が最も大きい。型でエラーを表す |

14 言語とも、同じ 15 章（第 1 部〜第 5 部）を同じ題材・同じ TODO リストで書きました。Python 版だけに付録 A（総合演習）があり、ほかの 13 言語は Python 版の付録へ案内しています。

見込みは 2 回外れました。第 2 波では、**Go は見込みどおり「ライブラリが限られる」**（gonum に決定木・K-means・ロジスティック回帰が無い）でしたが、**Rust は linfa が決定木から交差検証まで揃っており**、当初 Go 版と同じ立場に置く予定を Java 版・C# 版の側へ改めました（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。第 3 波では、**PHP を「ライブラリが限られる TypeScript 版に近い」と見込んで書き始めた**のに、Rubix ML に決定木もランダムフォレストもあり、ほぼ全章で突き合わせられました（[ADR 013](../../../adr/013-php-ml-libraries.md)）。逆に **Elixir は Nx・Scholar が Python の NumPy・scikit-learn にあたると見込んでいた**のに、Scholar に決定木が無く、第 3 章が丸ごと自作になりました（[ADR 012](../../../adr/012-elixir-ml-libraries.md)）。「ライブラリが成熟しているか」は、書いて確かめるまで分かりません。

**この 2 つの版が第 3 波で対照をなします。** Elixir 版は「ライブラリが育っていない領域では自作がそのまま本番の実装になる」例、PHP 版は「あるものはあるが、そのまま比べられるとは限らない」例です（第 4 章で扱います）。

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

**第 3 波**

| 用途 | Ruby | Clojure | Elixir | PHP | Haskell |
|------|------|---------|--------|-----|---------|
| 実行環境 | CRuby 3.3（Nix） | JVM（JDK 21） | BEAM（OTP 27） | PHP 8.4 | GHC 9.10 |
| テスト | Minitest | `clojure.test` + test-runner | ExUnit | PHPUnit 11.5 | Hspec 2.11（`hspec-discover`） |
| 整形 | RuboCop | cljfmt | `mix format --check-formatted` | PHP-CS-Fixer（`@PSR12`） | fourmolu（`--mode check`） |
| 静的解析 | RuboCop（`NewCops: enable`） | clj-kondo | Credo と `mix compile --warnings-as-errors` | PHPStan（レベル 9） | hlint と GHC の `-Wall`（`-Werror`） |
| 型チェック | 無し（RBS・Steep を使わない） | 無し | 無し（`@spec`・Dialyzer を使わない） | `declare(strict_types=1)` と PHPStan | GHC のコンパイラ |
| カバレッジ | SimpleCov | cloverage | `mix test --cover`（標準） | pcov ＋ PHPUnit の clover 出力 | HPC（GHC 組み込み） |

- 型チェックが「テストとは別の工程」になるのは Python（mypy）・TypeScript（tsc）・PHP（PHPStan）の 3 言語です。静的型付けの 7 言語は、コンパイルが通らなければテストも動きません。動的型付けの Ruby・Clojure・Elixir は、型の誤りをテストで捕まえる立場をとりました
- **同じ動的型付けでも、型の道具を使うかどうかは版ごとの選択です。** Ruby 版は「実行時に型を検査しない言語で、型の誤りをテストで捕まえる」ことを軸にするため RBS・Steep を使わないと決め（[ADR 010](../../../adr/010-ruby-ml-libraries.md)）、**PHP 版は逆に、型宣言が言語本体に入っている以上「書かないほうが不自然」として、`strict_types` と PHPStan レベル 9 を使うと決めました**（[ADR 013](../../../adr/013-php-ml-libraries.md)）。Elixir 版は Ruby 版と同じ側です
- **整形と静的解析が言語に同梱されているか**で分かれます。Go（`gofmt`・`go vet`）と Rust（rustfmt・clippy）は言語の配布物に含まれ、設定をほとんど書きません。第 3 波では **Elixir** がこの側で、整形（`mix format`）もカバレッジ（`mix test --cover`）も Mix に入っており、依存を 1 つも増やしませんでした。Haskell もカバレッジ（HPC）は GHC 組み込みです。Java は Spotless・Error Prone・PMD の 3 つを Gradle に足す必要があり、設定がいちばん長くなりました
- 警告をエラーにする仕組みは言語ごとに名前が違います（Scala の `-Xfatal-warnings`、C# の `TreatWarningsAsErrors`、Rust の `-D warnings`、F# の `TreatWarningsAsErrors`、Elixir の `mix compile --warnings-as-errors`、Haskell の `-Wall -Werror`）。どれも「警告を残したまま先へ進めない」ためのものです
- **検査の終了コードは、道具ごとにばらばらです。** 第 3 波で数えたものだけでも、PHP-CS-Fixer は **8**、Credo の `--strict` は **4**、fourmolu の `--mode check` は **100**、cljfmt は 1、clj-kondo は警告で 2 でした。「失敗したら 1」を仮定してタスクを書くと、失敗を取りこぼします
- F# 版の第 5 章では、FSharpLint の設定ファイルが既定の設定を置き換える仕様のため、第 1 章からルールが 1 件も有効になっていなかったことが分かりました。静的解析の「警告 0 件」は、道具が本当に検査しているかを確かめて初めて意味を持ちます

### カバレッジの仕組みは言語ごとにばらばらだった

第 3 波でいちばん手間がかかったのは、実は機械学習ではなくカバレッジでした。**3 つの版で続けて、カバレッジの仕組みそのものに手を入れています。**

| 言語 | 何が起きたか | どう解いたか |
|------|------------|------------|
| Elixir | 標準の `mix test --cover` に**既定で 90% のしきい値がある**。しかも設定を `test_coverage: [threshold: N]` と直に書くと**黙って無視され**、既定の 90% のまま失敗する（同じ `test_coverage` の `ignore_modules` は効くので気づきにくい） | `test_coverage: [summary: [threshold: N]]` と正しい場所に書いた |
| PHP | **素の Nix 環境にカバレッジドライバ（xdebug も pcov も）が無く、そもそもカバレッジを取れなかった。** さらに **PHPUnit には最低カバレッジのしきい値の機能が無い** | `php.withExtensions` で pcov を足し、`--coverage-clover` の XML の `project/metrics` を読んで判定する短いスクリプトを自作した（終了コードは 3 と自分で決めた） |
| Haskell | 仕組み（HPC）は GHC 組み込みで追加の依存が要らないが、**しきい値の機能は無い**。そのうえ **15 章がそろった時点で `cabal test --enable-coverage` がリンクで落ちるようになった**（`initializer '...Chapter10Spec_init__hpc' is >4GB from start of image`。HPC が各モジュールに仕込む初期化配列が増えて macOS のリンカの範囲を超えた） | しきい値は自作し、リンクは `--enable-executable-dynamic`（動的リンク）で解決した |

**しきい値は言語の標準ではなく、プロジェクトの約束です。** Elixir のように標準が勝手に 90% を課してくる言語もあれば、PHP・Haskell のように自分で書くしかない言語もあります。Haskell の例は、**章が増えるまで現れない種類の失敗**でもありました。カバレッジ無しの `cabal test` は 375 件すべて通っていたので、テストの問題ではなくリンクの問題だと切り分けられています。

### 環境に道具が無く、手元の PATH のものが見えていた

第 3 波では、**検査の道具が Nix の環境に入っておらず、手元の PATH にあるものが見えていた**ことが 2 回起きました。

| 言語 | 何が見えていたか |
|------|---------------|
| PHP | カバレッジドライバが無いうえ、`php`（8.4.16）と `php83Packages.composer`（PHP 8.3.29 で動く）が混ざっていた。`php.withExtensions` の `packages.composer` にして版をそろえた |
| Haskell | 素の環境に `fourmolu` も `hlint` も無く、**手元の `/usr/local/bin` のものが見えていた**。環境の側で版を固定した |

同じ形の取り違えは Ruby 版でも起きています。Nix の環境で `RUBYLIB` に Solargraph の依存の gem が並び、**`bundle exec` が `Gemfile.lock` より古い parser・prism・rubocop-ast を読み込んでいました**。検査は通るので気づけず、`rubocop -V` の表示で初めて分かっています（`shellHook` で `unset RUBYLIB` して直しました）。Clojure 版では cljfmt も clj-kondo も環境に無く、環境定義に足しました。

**「検査が通った」は「意図した版の道具が検査した」を意味しません。** 第 3 波の 5 言語のうち 4 言語で、道具の出所を確かめる作業が必要になりました。

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

**第 3 波**

| 用途 | Ruby | Clojure | Elixir | PHP | Haskell |
|------|------|---------|--------|-----|---------|
| パッケージ管理 | Bundler（`Gemfile.lock`） | Clojure CLI（`deps.edn`。**ロックファイルに当たるものが無い**） | Mix（`mix.exs`・`mix.lock`） | Composer（`composer.json`・`composer.lock`） | cabal（`*.cabal`・`cabal.project`） |
| 実行環境の版の固定 | Nix の Ruby 3.3（手元の macOS は 2.6） | Nix の Clojure が持つ JDK 21（`java` コマンドは 25） | Nix の Elixir が持つ OTP 27（`erl` コマンドは 28） | Nix の PHP 8.4（composer も同じ PHP から取る） | Nix の GHC 9.10 |
| タスク | Rake | `deps.edn` の別名 | `mix` のタスク | Composer のスクリプト | `cabal` のサブコマンド |
| 開発環境 | Nix（`.#ruby`） | Nix（`.#clojure`） | Nix（`.#elixir`） | Nix（`.#php`） | Nix（`.#haskell`） |

リポジトリ全体のタスクは 14 言語とも Gulp にそろえました（`npx gulp apps:check:<言語>`）。ほとんどの言語は、依存関係の依存関係まで正確な版をロックファイルに記録してコミットしています。TypeScript（`npm ci`）と F#・C#（`dotnet restore --locked-mode`）の CI は、ロックファイルと食い違えば失敗します。

**ロックファイルが無い言語もあります。** Clojure の `deps.edn` にはロックファイルに当たるものが無いので、再現性は「依存を固定した版で書く」ことに頼っています（[ADR 011](../../../adr/011-clojure-ml-libraries.md)）。

逆に、**ロックファイルが「再現性の最後の砦」になった例**が第 2 波で出ました。Rust 版の第 2 章で使う `rand` 0.8 の `StdRng` は、ドキュメントに「版をまたいで再現可能と考えるべきではない」と明記されています。つまり記事に載せた数値は、`Cargo.lock` で版を固定しているから再現できるのであって、言語の保証ではありません（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。

**そして第 3 波では、「何を固定すれば数値が再現するか」が言語で違うことがはっきりしました。** Ruby 版の乱数の並びを決めるのは `Gemfile.lock` ではなく **Ruby 本体の版**です（2.6.10 と 3.3.10 で同じ並びであることを実測しました）。Elixir・PHP・Haskell は乱数生成器そのものを自作したので、固定しているのはリポジトリのコードです。言語ごとのパッケージ管理の違いは、各版の第 5 章で扱いました。

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

**第 3 波**

| 用途 | Ruby | Clojure | Elixir | PHP | Haskell |
|------|------|---------|--------|-----|---------|
| データの表し方 | `Hash` と `Struct`／`Data` | 素のマップとベクタ | 素のマップとリスト | 素の配列と `readonly class` | レコードと `Map`・リスト |
| CSV の読み込み | `csv` gem | `clojure.data.csv` | NimbleCSV | 標準の `fgetcsv` | cassava |
| 行列 | `numo-narray-alt`（Rumale の依存） | Tribuo の `DenseMatrix` | Nx（`type: :f64` を明示） | MathPHP と `rubix/tensor` | hmatrix（BLAS/LAPACK） |
| 機械学習 | Rumale 2.2 | Tribuo 4.3.2 | Scholar 0.4.2（**決定木もランダムフォレストも無い**） | Rubix ML 2.6 | **無し**（自作が最終実装） |
| モデルの保存 | `Marshal` | EDN（`pr-str`／`read-string`） | `:erlang.term_to_binary` | `serialize` | `Data.Binary` |
| API | Sinatra + Puma | Ring + Jetty | Plug + Bandit | 標準の組み込みサーバー | Scotty + aeson |
| API の統合テスト | rack-test | ハンドラーの関数を直接呼ぶ | `Plug.Test.conn/3` | ハンドラーの関数を直接呼ぶ | ハンドラーの関数を直接呼ぶ |

- Python は、データの読み込みから学習・保存・API までを、事実上の標準のライブラリでそろえられます。自作したアルゴリズムの突き合わせ先が、ほぼすべての章にありました
- **データフレームのライブラリを使ったのは Python と Kotlin の 2 言語だけ**です。ほかの 12 言語はレコード・構造体・`Map`・素の配列でデータを表しました。シリーズの方針として、データの表し方そのものを題材にしたかったためです。第 3 波でも Explorer（Elixir）・`tech.ml.dataset`（Clojure）・Daru や Polars（Ruby）を採らないと決めています
- **機械学習ライブラリの充実度は 4 段階に分かれました。** scikit-learn（Python）・Tribuo（Kotlin・Java・Scala・Clojure）・ML.NET（F#・C#）・linfa（Rust）・Rumale（Ruby）・Rubix ML（PHP）はほとんどの章で突き合わせられ、ml.js 系（TypeScript）と Scholar（Elixir）は一部、gonum（Go）は線形回帰・PCA・ROC だけ、**Haskell には機械学習のライブラリが無く、突き合わせられるのは線形代数の層（正規方程式・固有値分解）だけ**でした
- **API は「標準ライブラリだけで書けるか」で分かれました。** Go は `net/http`（Go 1.22 のルーティング）だけ、**PHP は標準の組み込みサーバーと素のハンドラーだけ**で足りました。ほかの 12 言語はフレームワークを入れています。Rust の標準ライブラリには HTTP サーバーがありません
- **統合テストは、ほとんどの言語がサーバーを起動せずに書けました。** 第 3 波では Clojure・PHP・Haskell が「ハンドラーはただの関数なので呼ぶだけ」で済み、Elixir は `Plug.Test.conn/3`、Ruby は rack-test を使っています。**PHP 版だけは事情が逆で、組み込みサーバーがプログラムから起動できない**（`php -S` 自体がサーバーで、要求ごとにスクリプトを最初から走らせる）ため、学習・保存と待ち受けがコマンドとして分かれ、結果として組み立てのクラスまで 100% テストできました
- 可視化の節は、Python・Kotlin・F# の 3 言語が、Notebook を使って第 2・3・7〜14 章で扱いました。TypeScript 版と第 2 波・第 3 波の 10 言語は Notebook を作らず（Elixir の Livebook も使わず）、Python 版・Kotlin 版の同じ節へ案内しています

## 1.4 選定の理由と、選ばなかった代替案

各言語のライブラリは、ADR 001〜014 で選定の理由と代替案を記録しています。判断の軸は、14 言語でおおむね共通でした。

| 判断の軸 | 例 |
|---------|----|
| 参照元との対応 | Python は、参照元の記事と同じ pandas・scikit-learn・FastAPI にした（[ADR 001](../../../adr/001-python-ml-libraries.md)） |
| ライセンス | Kotlin は、GPL-3.0 の Smile 6.x を採らず、Apache License 2.0 の Tribuo にした（[ADR 002](../../../adr/002-kotlin-ml-libraries.md)）。Scala も同じ理由で Tribuo にした（[ADR 007](../../../adr/007-scala-ml-libraries.md)）。**Clojure も、Smile 3.1.1 が Java の相互運用で問題なく動いたにもかかわらず、POM のライセンスが GPL-3.0 だったので採らず Tribuo にした**（[ADR 011](../../../adr/011-clojure-ml-libraries.md)）。F# は、Intel MKL に依存する ML.NET の `Ols` を採らず、最小二乗は FSharp.Stats にした（[ADR 004](../../../adr/004-fsharp-ml-libraries.md)） |
| 保守の見込み | TypeScript は、更新が止まった danfojs-node を採らず、データフレームを使わない形にした（[ADR 003](../../../adr/003-typescript-ml-libraries.md)）。Go は、最後のコミットが 2022 年の GoLearn を採らず gonum だけにした（[ADR 008](../../../adr/008-go-ml-libraries.md)）。**PHP は、保守がほぼ止まっている php-ai/php-ml を採らず Rubix ML にし**（[ADR 013](../../../adr/013-php-ml-libraries.md)）、**Haskell は hlearn が現在の GHC でビルドできないので採らなかった**（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）。Ruby は、2022 年で更新の止まった本家の `numo-narray` ではなく、Rumale 2.x が依存するフォークの `numo-narray-alt` を使った |
| ほかの版との依存の重なり | Rust は、linfa が依存する ndarray 0.16・rand 0.8 に固定した。最新の 0.17・0.9 を混ぜると「同じ名前の別の型」になって渡せない（[ADR 009](../../../adr/009-rust-ml-libraries.md)） |
| 依存の重さ | Elixir は EXLA（巨大な XLA のバイナリ）を入れず `Nx.BinaryBackend` にした。**Haskell は逆に、BLAS/LAPACK を環境に足してでも hmatrix を使うと決めた**——そうしないと突き合わせる相手が 1 つも無くなるためである |
| 題材としての価値 | TypeScript は、seedrandom を使わず、シード付き乱数を TDD で作る題材にした。**Elixir・PHP・Haskell は、ライブラリの乱数がほかの言語版と並びが合わないので、`java.util.Random` と同じ線形合同法を自作した**（第 2 章で扱います） |
| 統合テストのしやすさ | TypeScript の Hono（`app.request`）、F# の Giraffe（TestHost）、Java の `JavalinTest`、Go の `httptest`、Rust の tower の `oneshot`、Elixir の `Plug.Test.conn/3` は、サーバーを起動せずに API をテストできる |
| 依存の小ささ | Kotlin は Spring Boot ではなく Ktor、Python は Flask ではなく FastAPI にした。Go・PHP は第 15 章の要件が標準のもので足りたので、フレームワークを入れなかった。Elixir は Phoenix、Ruby は Rails、PHP は Laravel・Symfony、Clojure は reitit・compojure を採っていない |

**hmatrix の話は、ライブラリ選定の「確かめ方」そのものが題材になった例です。** Haskell 版では、C ライブラリへの依存の確認に 3 段あることが分かりました。(1) `configure` が C ライブラリを見つけられるか、(2) 整数幅などの **ABI が噛み合っているか**、(3) 環境定義を直したあと **cabal の store の成果物が作り直されるか**。最初に足した `openblas` は nixpkgs の既定が ILP64（整数引数が 64 ビット）で、32 ビット整数で呼ぶ hmatrix と噛み合わず、**ビルドは成功するのに 2×2 の連立方程式すら解けませんでした**。`blas` と `lapack` に差し替えて解決しています。**2×2 の `DGESV` で「行列の次数が不正」と言われたら、数値の問題ではなく ABI の不一致を疑う**、というのがここでの教訓です（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）。

F# 版の Notebook には、2026 年に廃止された Polyglot Notebooks を、廃止を明記したうえで使っています。Notebook は探索と可視化だけに使い、テストと記事の数値は `apps/fsharp/` のプロジェクトから求めているので、Notebook が動かなくなっても本文には影響しません。

## 1.5 同じ手順で書いたことで見えたこと

14 言語とも「自作 → テスト → ライブラリと突き合わせ」の同じ手順で書いたので、言語の違いが、どこで誤りを見つけたかの違いとして表れました。

| 誤りを見つけた場所 | 例 |
|------------------|----|
| 実行時のテスト | Python の第 9 章で、pandas の `std()` が不偏標準偏差を返すことをテストが見つけた。Rust の第 3 章で、`max_by_key` が同点のとき最後の要素を返すことを 2 件のテストの失敗が見つけた。**PHP の第 3 章で、「数字だけの文字列」を鍵にすると整数に化けることをテストが見つけた**（PHPStan レベル 9 は通してしまう） |
| 型チェック（テストとは別の工程） | TypeScript の第 7 章で、`null` の比較と正解ラベルの欠損を型チェックが見つけた。**PHP の第 15 章で、「型で守れるものを約束のテストに書く」と PHPStan が「常に真である」と叱った**ので、型では書けない約束（「同じ入力には同じ答え」「値が有限」）に書き換えた |
| コンパイル（テストの前） | F# の第 15 章で、エラーの種類を増やしたときに、説明の書き忘れを網羅性の検査（FS0025）が見つけた。Rust の第 14 章で、rand 0.9 の乱数生成器を linfa に渡そうとして「別の版の型」だと分かった。**Haskell の第 8 章で、前処理の種類を直和型で数え上げたら節の書き漏らしがコンパイルで止まった。第 15 章では `LoadError` に節を増やすとステータスコードへの変換の関数が止まる** |
| 警告をエラーにする設定 | **Haskell の第 3 章で、`head` が `-Wx-partial` でコンパイルエラーになり、空の場合をパターンで書くことになった。それがそのまま `Left "正解ラベルがありません"` になった。** 第 12 章では `-Wname-shadowing` がテストの局所変数と `Test.Hspec.sequential` の衝突を見つけた（`-Werror` でなければ見逃していた） |
| 静的解析 | Java の第 2 章で、Error Prone が配列を持つ record の `equals` の落とし穴（`ArrayRecordComponent`）を止めた。**Ruby の第 10 章では逆に、RuboCop の `Style/Sample` に従って書き換えたら同じシードでも選ばれる列が変わった**（第 14 章では従わずに残した） |
| 非推奨の警告 | **PHP の第 2 章で、`failOnDeprecation="true"` が「整数が float に化けて精度を失う」ことを失敗として教えた。** 入れていなければ「なぜか並びが合わない」という形でしか現れなかった |
| 学習用テスト | 14 言語とも、ライブラリの既定値や罰則の尺度の違いを、使う前に学習用テストで見つけた（第 4 章） |

型が強いほど、誤りを早い段階（書いた直後のコンパイル）で見つけられます。一方で、ライブラリの振る舞い（既定値・尺度・並び順・決定性）は型では分からないので、どの言語でも学習用テストが欠かせませんでした。

**型の道具をどれだけ使っても、言語の癖は型では守れません。** PHP 版の第 3 章では、PHP の配列が「数字だけの文字列」の鍵を整数に変えるため、`@return array<string, int>` と書いても PHPStan レベル 9 が通してしまい、テストで初めて見つかりました。第 11 章では、同じ癖で **Rubix ML の `stratifiedFold()` のほうが落ちています**（ラベルを配列のキーにするので `'0'`／`'1'` が整数に化ける）。

第 2 波・第 3 波では、**ライブラリのほうが自作より不安定だった例**も出ました。Rust の linfa-trees は、ダミー変数の多いデータ（第 8 章の `Survived.csv`）で実行ごとに正解率が 0.788〜0.810 と揺れます。**PHP の `ClassificationTree` は既定値のままでは決定的ですらなく**、シードを渡す口が無いまま 5 回走らせて `0.8889 0.8889 0.8889 0.8889 0.8222` と揺れました。Rubix ML のモデルは全般にシードを受け取らず大域の `mt_rand` から引きます。「ライブラリは自分で書くより正しい」とは限らず、突き合わせる相手がいるからこそ気づけます。

## 1.6 まとめ

1. 14 言語は、エコシステムの成熟度と型の強さの組み合わせで選んだ。Python が参照実装、TypeScript・Go・**Elixir**・**Haskell** がライブラリの少ない環境の代表。**見込みは Rust と PHP で外れ、どちらも「思ったより揃っていた」ほうに転んだ**
2. テスト・整形・静的解析・カバレッジ・ロックファイル・CI の道具立ては、14 言語とも同じ役割のものをそろえた。整形と静的解析が言語に同梱されているのは Go・Rust・Elixir。**カバレッジの仕組みは言語ごとにばらばらで、第 3 波では 3 回続けて手を入れることになった**
3. **「検査が通った」は「意図した版の道具が検査した」を意味しない。** 第 3 波の 5 言語のうち 4 言語で、道具が環境に無いか、別の版のものが見えていた
4. ライブラリは、参照元との対応・ライセンス・保守の見込み・依存の重さ・題材としての価値・統合テストのしやすさ・ほかの依存との版の重なりで選び、ADR 001〜014 に記録した。**C ライブラリに依存する場合は、ビルドが通っただけでは足りない**（hmatrix と ILP64 の BLAS）
5. 型の強さは「誤りをどの工程で見つけるか」に表れた。**同じ動的型付けでも、Ruby 版は型の道具を使わず、PHP 版は使うという逆の選択をした。** ライブラリの振る舞い（既定値・尺度・並び順・決定性）は、どの言語でも学習用テストで確かめた

次の章では、14 言語のデータの表し方を比べます。
