# ADR (Architecture Decision Records)

技術的意思決定を記録した ADR です。

## ADR 一覧

| ADR | 決定内容 | ステータス |
| :--- | :--- | :--- |
| [001](001-python-ml-libraries.md) | Python 版の機械学習・可視化・API ライブラリに pandas・scikit-learn・FastAPI・matplotlib・seaborn・JupyterLab を採用する | 提案中 |
| [002](002-kotlin-ml-libraries.md) | Kotlin 版のライブラリに Kotlin DataFrame・Tribuo・Kandy・Ktor を採用し、章ごとの置き換え範囲を決める | 提案中 |
| [003](003-typescript-ml-libraries.md) | TypeScript 版のライブラリに TypeScript 6.0・Node.js 22・Vitest・ml.js 系・Hono + zod を採用し、章ごとの置き換え範囲を決める | 提案中 |
| [004](004-fsharp-ml-libraries.md) | F# 版のライブラリに .NET SDK 10・xUnit v3・ML.NET・FSharp.Stats・FSharp.Data・Polyglot Notebooks + Plotly.NET・Giraffe を採用し、廃止された Polyglot Notebooks への対応と章ごとの置き換え範囲を決める | 提案中 |
| [005](005-java-ml-libraries.md) | Java 版のライブラリに JUnit 6・AssertJ・Spotless・Error Prone・PMD・JaCoCo・Tribuo・Javalin を採用し、章ごとの置き換え範囲を決める | 提案中 |
| [006](006-csharp-ml-libraries.md) | C# 版のライブラリに xUnit v3・coverlet.MTP・dotnet format・.NET アナライザー・ML.NET・ASP.NET Core Minimal API を採用し、章ごとの置き換え範囲を決める | 提案中 |
| [007](007-scala-ml-libraries.md) | Scala 版のライブラリに sbt・ScalaTest・scalafmt・scoverage・Tribuo・http4s + circe を採用し、GPL-3.0 の Smile を使わない | 承認済み |
| [008](008-go-ml-libraries.md) | Go 版のライブラリに標準の testing・gofmt・go vet・golangci-lint・gonum・net/http を採用し、gonum に無いアルゴリズムを自作する | 承認済み |
| [009](009-rust-ml-libraries.md) | Rust 版のライブラリに Cargo 標準のテスト・rustfmt・clippy・linfa 0.8.1・ndarray 0.16・axum を採用し、クレートの版をそろえる | 承認済み |
| [010](010-ruby-ml-libraries.md) | Ruby 版のライブラリに Bundler・Minitest・RuboCop・SimpleCov・Rumale 2.2・numo-narray-alt・Sinatra を採用し、Nix の Ruby 3.3 を前提にする | 承認済み |
| [011](011-clojure-ml-libraries.md) | Clojure 版のライブラリに Clojure CLI・clojure.test・clj-kondo・cljfmt・cloverage・Tribuo 4.3・data.csv・Ring を採用し、GPL-3.0 の Smile を使わない | 承認済み |
| [012](012-elixir-ml-libraries.md) | Elixir 版のライブラリに Mix・ExUnit・mix format・Credo・Nx・Scholar・NimbleCSV・codepagex・Plug と Bandit を採用し、Scholar に決定木が無いため決定木の章を自作の最終実装とする | 承認済み |
| [013](013-php-ml-libraries.md) | PHP 版のライブラリに Composer・PHPUnit・PHP-CS-Fixer・PHPStan・pcov・Rubix ML・MathPHP を採用し、素の線形回帰とラッソが無いため代用・自作とする | 提案中 |

ADR の作成には `creating-adr` スキルを使用してください。
