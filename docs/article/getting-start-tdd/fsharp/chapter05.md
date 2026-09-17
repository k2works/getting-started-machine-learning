---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "前章では Conventional Commits によるコミットメッセージの規約を学びました。"
tags: [article, getting-start-tdd, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前章では Conventional Commits によるコミットメッセージの規約を学びました。この章では、**パッケージ管理** と **静的コード解析** を導入し、コードの品質を自動でチェックできるようにします。

## 5.2 NuGet によるパッケージ管理

### NuGet とは

> NuGet は .NET のパッケージマネージャです。パッケージの追加、削除、更新、依存関係の解決を自動で行います。

Java の Gradle、Node の npm、Python の uv、Ruby の Bundler、Rust の Cargo に相当するのが NuGet です。

### .fsproj ファイルの構成

F# プロジェクトの設定は `.fsproj` ファイルで管理されます。本プロジェクトのライブラリプロジェクトは以下の構成です。

```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net8.0</TargetFramework>
    <GenerateDocumentationFile>true</GenerateDocumentationFile>
  </PropertyGroup>
  <ItemGroup>
    <Compile Include="Library.fs" />
  </ItemGroup>
</Project>
```

テストプロジェクトの設定は以下の通りです。

```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net8.0</TargetFramework>
    <IsPackable>false</IsPackable>
    <GenerateProgramFile>false</GenerateProgramFile>
    <IsTestProject>true</IsTestProject>
  </PropertyGroup>
  <ItemGroup>
    <Compile Include="Tests.fs" />
    <Compile Include="Program.fs" />
  </ItemGroup>
  <ItemGroup>
    <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.8.0" />
    <PackageReference Include="xunit" Version="2.5.3" />
    <PackageReference Include="xunit.runner.visualstudio" Version="2.5.3" />
    <PackageReference Include="coverlet.collector" Version="6.0.0" />
  </ItemGroup>
  <ItemGroup>
    <ProjectReference Include="..\FizzBuzzFSharp\FizzBuzzFSharp.fsproj" />
  </ItemGroup>
</Project>
```

F# プロジェクトの重要な特徴として、`<Compile Include>` の **順序が意味を持つ** 点があります。F# コンパイラはファイルを上から下へ順に処理するため、依存先のファイルを先に記述する必要があります。

### 主要なコマンド

| コマンド | 説明 |
|---------|------|
| `dotnet new` | 新しいプロジェクトを作成 |
| `dotnet build` | プロジェクトをビルド |
| `dotnet test` | テストを実行 |
| `dotnet run` | アプリケーションを実行 |
| `dotnet add package <name>` | パッケージを追加 |
| `dotnet restore` | 依存パッケージを復元 |

### NuGet の特徴

- **`obj/project.assets.json` による再現性** — 依存パッケージのバージョンを固定し、環境間の差異を排除
- **Central Package Management** — `Directory.Packages.props` で複数プロジェクトのパッケージバージョンを統一管理
- **`bin/` と `obj/` ディレクトリ** — Rust の `target/`、Node の `node_modules/` に相当（`.gitignore` に追加）

## 5.3 Fantomas によるコードフォーマット

### Fantomas とは

> Fantomas は F# の公式コードフォーマッターです。コードスタイルを統一し、チーム内のスタイル議論を排除します。

Rust の rustfmt、Go の gofmt、Python の Ruff format、TypeScript の Prettier に相当します。

### インストールと実行

```bash
# グローバルツールとしてインストール
$ dotnet tool install -g fantomas

# フォーマットチェック（CI 向け）
$ fantomas --check src/ tests/

# 自動フォーマット
$ fantomas src/ tests/
```

### コードスタイル例

Fantomas はデフォルトで F# のスタイルガイドに従ったフォーマットを適用します。

```fsharp
// Before（手動フォーマット）
let generate(number:int):string=match(number%3,number%5)with|(0,0)->"FizzBuzz"|(0,_)->"Fizz"|(_,0)->"Buzz"|_->string number

// After（Fantomas 適用後）
let generate (number: int) : string =
    match (number % 3, number % 5) with
    | (0, 0) -> "FizzBuzz"
    | (0, _) -> "Fizz"
    | (_, 0) -> "Buzz"
    | _ -> string number
```

## 5.4 FSharpLint によるコード複雑度チェック

### 循環的複雑度（Cyclomatic Complexity）

FSharpLint には **FL0071**（CyclomaticComplexity）ルールが内蔵されており、関数の循環的複雑度を計測できます。

> 循環的複雑度とは、コードの線形独立なパスの数を数値化した指標です。条件分岐（`if`/`elif`/`else`）、`match` 式のケース、`for`/`while` ループ、条件内のブール演算子によってインクリメントされます。

| 複雑度の範囲 | 意味 |
|-------------|------|
| 1〜7 | 低複雑度: 管理しやすく、問題なし |
| 8〜15 | 中程度の複雑度: リファクタリングを検討 |
| 16〜25 | 高複雑度: リファクタリングが強く推奨される |
| 26 以上 | 非常に高い複雑度: 関数を分割する必要がある |

### FSharpLint の導入

dotnet tool としてインストールします。

```bash
# ローカルツールとしてインストール
$ dotnet new tool-manifest  # 初回のみ
$ dotnet tool install dotnet-fsharplint
```

### fsharplint.json による閾値設定

プロジェクトルートに `fsharplint.json` を作成し、閾値を設定します。

```json
{
    "cyclomaticComplexity": {
        "enabled": true,
        "config": {
            "maxComplexity": 7
        }
    }
}
```

他言語の複雑度チェック（C# の SonarAnalyzer `S3776`、Rust の Clippy `cognitive-complexity-threshold = 7`、PHP の PHPMD `reportLevel: 7`、Python の Ruff `max-complexity = 7`）と同じ基準値 **7** を設定します。

### 実行してみる

```bash
# 複雑度チェック
$ dotnet dotnet-fsharplint lint --lint-config fsharplint.json FizzBuzzFSharp/Library.fs
========== Linting FizzBuzzFSharp/Library.fs ==========
========== Finished: 0 warnings ==========
========== Summary: 0 warnings ==========
```

現在の FizzBuzz 実装では、各関数が短く単純なため、閾値 7 を超える関数はありません。

### 複雑度違反の例

閾値を超えるとこのように検出されます。

```
The cyclomatic complexity of this section is 8, which exceeds the maximum
suggested complexity of 7.
Error on line 4 starting at column 8
    let complexFunction x =
        ^
See https://fsprojects.github.io/FSharpLint/how-tos/rules/FL0071.html
```

### F# で複雑度を低く保つコツ

F# の `match` 式は判別共用体のケースごとに複雑度がカウントされます。ヘルパー関数に分割することで複雑度を低く保てます。

```fsharp
// ヘルパー関数で複雑度を分散
let private isFizz number = number % 3 = 0
let private isBuzz number = number % 5 = 0
let private isFizzBuzz number = isFizz number && isBuzz number
```

### Cake への追加

`build.cake` に FSharpLint タスクを追加します。

```csharp
Task("FSharpLint")
    .Does(() =>
{
    var exitCode = StartProcess("dotnet", new ProcessSettings
    {
        Arguments = "dotnet-fsharplint lint --lint-config fsharplint.json FizzBuzzFSharp/Library.fs"
    });
    if (exitCode != 0)
    {
        throw new Exception("FSharpLint チェックに失敗しました。F# コードの複雑度を下げてください。");
    }
    Information("FSharpLint チェック: OK（全関数が閾値以下）");
});

Task("Check")
    .IsDependentOn("Test")
    .IsDependentOn("Complexity")
    .IsDependentOn("FSharpLint");
```

`dotnet dotnet-cake --target=FSharpLint` で F# の複雑度チェックを単独実行、`dotnet dotnet-cake --target=Check` で C#/F# 両方の全品質チェックをまとめて実行できます。

### 他言語との比較

| 言語 | 複雑度チェックツール | 設定 |
|------|---------------------|------|
| F# | FSharpLint（FL0071） | `fsharplint.json: maxComplexity: 7` |
| C# | SonarAnalyzer（S3776） | `.editorconfig: dotnet_diagnostic.S3776.severity = warning` |
| Rust | Clippy（cognitive_complexity） | `clippy.toml: cognitive-complexity-threshold = 7` |
| PHP | PHPMD | `reportLevel: 7` |
| Java | PMD | `CyclomaticComplexity` |
| Python | Ruff（McCabe） | `max-complexity = 7` |
| TypeScript | ESLint | `complexity: ["error", { max: 7 }]` |
| Ruby | RuboCop | `Metrics/CyclomaticComplexity` |
| Go | golangci-lint（gocognit） | `.golangci.yml: gocognit.min-complexity: 7` |

## 5.5 コードカバレッジ

### coverlet によるカバレッジ計測

coverlet は .NET のクロスプラットフォームカバレッジツールです。

```bash
# カバレッジ付きでテスト実行
$ dotnet test --collect:"XPlat Code Coverage"
```

テスト結果は `TestResults/` ディレクトリに Cobertura XML 形式で出力されます。

## 5.6 他言語との比較

| ツール | 役割 | 他言語の対応ツール |
|--------|------|-------------------|
| NuGet | パッケージ管理 | npm, Bundler, Cargo, Go Modules |
| Fantomas | コードフォーマット | Prettier, rustfmt, gofmt, Ruff |
| FSharpLint（FL0071） | コード複雑度チェック | Clippy cognitive_complexity, SonarAnalyzer S3776, PHPMD |
| coverlet | カバレッジ計測 | c8, tarpaulin, go test -cover |

## 5.7 まとめ

この章では以下を導入しました。

| ツール | 役割 |
|--------|------|
| NuGet | パッケージ管理と依存関係の解決 |
| .fsproj | F# プロジェクトの設定とファイル順序管理 |
| Fantomas | F# コードの自動フォーマット |
| FSharpLint | 循環的複雑度チェック（閾値 7） |
| coverlet | テストカバレッジの計測 |

次章では、これらのツールを **タスクランナー**（Cake）でまとめて実行できるようにし、**CI/CD** パイプラインを構築します。
