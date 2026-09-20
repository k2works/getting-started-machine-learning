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
| [007](007-scala-ml-libraries.md) | Scala 版のライブラリに sbt・ScalaTest・scalafmt・scoverage・Tribuo・http4s + circe を採用し、GPL-3.0 の Smile を使わない | 提案中 |

ADR の作成には `creating-adr` スキルを使用してください。
