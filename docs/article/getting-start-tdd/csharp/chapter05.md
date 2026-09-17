---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "前章では Conventional Commits によるコミットメッセージの規約を学びました。"
tags: [article, getting-start-tdd, csharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前章では Conventional Commits によるコミットメッセージの規約を学びました。この章では、**パッケージ管理** と **静的コード解析** を導入し、コードの品質を自動でチェックできるようにします。

## 5.2 NuGet によるパッケージ管理

### NuGet とは

> NuGet は .NET のパッケージマネージャです。開発者が作成・共有したライブラリやツールを「パッケージ」として配布・利用することができます。

Java の Gradle、Node の npm、Python の pip、Ruby の Bundler、Rust の Cargo に相当するのが NuGet です。

### .csproj ファイルの構成

本プロジェクトの `.csproj` ファイルは以下のようになっています。

```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <TargetFramework>net8.0</TargetFramework>
    <ImplicitUsings>enable</ImplicitUsings>
    <Nullable>enable</Nullable>
  </PropertyGroup>
</Project>
```

テストプロジェクトの `.csproj` にはテスト関連のパッケージが含まれます。

```xml
<ItemGroup>
  <PackageReference Include="coverlet.collector" Version="6.0.0" />
  <PackageReference Include="Microsoft.NET.Test.Sdk" Version="17.8.0" />
  <PackageReference Include="xunit" Version="2.5.3" />
  <PackageReference Include="xunit.runner.visualstudio" Version="2.5.3" />
</ItemGroup>
```

### 主要なコマンド

| コマンド | 説明 |
|---------|------|
| `dotnet new <テンプレート>` | 新しいプロジェクトを作成 |
| `dotnet build` | プロジェクトをビルド |
| `dotnet test` | テストを実行 |
| `dotnet run` | アプリケーションを実行 |
| `dotnet add package <名前>` | NuGet パッケージを追加 |
| `dotnet restore` | 依存パッケージを復元 |

### NuGet の特徴

- **`packages.lock.json` による再現性** — パッケージバージョンを固定し、チーム全員が同じ環境で開発できる
- **ソリューション構成** — 複数プロジェクトを 1 つのソリューションで管理
- **`bin/` / `obj/` ディレクトリ** — Rust の `target/`、Node の `node_modules/` に相当（`.gitignore` に追加）

## 5.3 dotnet format によるコードフォーマット

### dotnet format とは

> dotnet format は .NET の標準コードフォーマッターです。コードスタイルを統一し、チーム内のスタイル議論を排除します。

Rust の rustfmt、Go の gofmt、Python の Ruff format、TypeScript の Prettier に相当します。

### 実行してみる

```bash
# フォーマットチェック（CI 向け）
$ dotnet format --verify-no-changes

# 自動フォーマット
$ dotnet format
```

### .editorconfig による設定

`.editorconfig` ファイルでコーディングスタイルを定義できます。

```ini
root = true

[*]
indent_style = space
indent_size = 4
charset = utf-8
trim_trailing_whitespace = true
insert_final_newline = true

[*.cs]
dotnet_sort_using_directives = true
csharp_prefer_braces = true:warning
```

## 5.4 Roslyn アナライザによる静的解析

### Roslyn アナライザとは

> Microsoft.CodeAnalysis.Analyzers は .NET の公式リンターです。コンパイル時にコードの品質を自動チェックし、一般的なミスやアンチパターンを検出します。

Rust の Clippy、TypeScript の ESLint、Python の Ruff、Go の golangci-lint に相当するツールです。

### 導入方法

```bash
$ dotnet add package Microsoft.CodeAnalysis.Analyzers
```

ビルド時に自動的に静的解析が実行されます。

```bash
$ dotnet build
```

### 警告カテゴリ

| カテゴリ | 説明 |
|---------|------|
| Design | 設計上の問題 |
| Naming | 命名規則違反 |
| Performance | パフォーマンスに影響するコード |
| Reliability | 信頼性に関する問題 |
| Security | セキュリティリスク |
| Usage | API の誤用 |

## 5.5 コードカバレッジ

### coverlet

C# のカバレッジツールとして `coverlet` があります。xUnit テストプロジェクトには既に含まれています。

```bash
# カバレッジ付きテスト実行
$ dotnet test --collect:"XPlat Code Coverage"
```

HTML レポートを生成するには `reportgenerator` を利用します。

```bash
$ dotnet tool install -g dotnet-reportgenerator-globaltool
$ reportgenerator -reports:"**/coverage.cobertura.xml" -targetdir:"coverage" -reporttypes:Html
```

## 5.6 SonarAnalyzer によるコード複雑度チェック

### 認知的複雑度（Cognitive Complexity）

SonarAnalyzer.CSharp には **S3776** ルールが内蔵されており、メソッドの認知的複雑度を計測できます。

> 認知的複雑度とは、コードがどれだけ理解しにくいかを数値化した指標です。循環的複雑度（Cyclomatic Complexity）と異なり、ネストの深さやフロー制御の読みにくさも考慮します。

| 複雑度の範囲 | 意味 |
|-------------|------|
| 1〜7 | 低複雑度: 管理しやすく、問題なし |
| 8〜15 | 中程度の複雑度: リファクタリングを検討 |
| 16〜25 | 高複雑度: リファクタリングが強く推奨される |
| 26 以上 | 非常に高い複雑度: メソッドを分割する必要がある |

### SonarAnalyzer.CSharp の導入

NuGet パッケージとしてインストールします。

```bash
$ dotnet add package SonarAnalyzer.CSharp
```

`.csproj` にパッケージ参照が追加されます。

```xml
<ItemGroup>
  <PackageReference Include="SonarAnalyzer.CSharp" Version="10.20.0.135146">
    <IncludeAssets>runtime; build; native; contentfiles; analyzers; buildtransitive</IncludeAssets>
    <PrivateAssets>all</PrivateAssets>
  </PackageReference>
</ItemGroup>
```

### .editorconfig による設定

`.editorconfig` に S3776 ルールの重大度を設定します。

```ini
[*.cs]
# SonarAnalyzer: 認知的複雑度チェック
# S3776 - Cognitive Complexity of methods should not be too high
dotnet_diagnostic.S3776.severity = warning
```

他言語の複雑度チェック（Rust の Clippy `cognitive-complexity-threshold = 7`、PHP の PHPMD `reportLevel: 7`、Python の Ruff `max-complexity = 7`、TypeScript の ESLint `complexity: ["error", { max: 7 }]`）と同じ基準で、複雑度が高いメソッドを検出できます。

### 実行してみる

ビルド時に自動的にチェックが実行されます。

```bash
# 通常ビルド（警告として表示）
$ dotnet build

# 複雑度違反をエラーとして扱う（CI 向け）
$ dotnet build -warnaserror:S3776
```

現在の FizzBuzz 実装では、各メソッドが短く単純なため、閾値を超えるメソッドはありません。

### Cake への追加

`build.cake` に Complexity タスクを追加します。

```csharp
Task("Complexity")
    .IsDependentOn("Build")
    .Does(() =>
{
    var exitCode = StartProcess("dotnet", new ProcessSettings
    {
        Arguments = "build FizzBuzz.sln --no-restore -warnaserror:S3776"
    });
    if (exitCode != 0)
    {
        throw new Exception("認知的複雑度チェックに失敗しました。メソッドの複雑度を下げてください。");
    }
    Information("認知的複雑度チェック: OK（全メソッドが閾値以下）");
});

Task("Check")
    .IsDependentOn("Test")
    .IsDependentOn("Complexity");
```

`dotnet dotnet-cake --target=Complexity` で複雑度チェックを単独実行、`dotnet dotnet-cake --target=Check` で全品質チェックをまとめて実行できます。

### 他言語との比較

| 言語 | 複雑度チェックツール | 設定 |
|------|---------------------|------|
| C# | SonarAnalyzer（S3776） | `.editorconfig: dotnet_diagnostic.S3776.severity = warning` |
| Rust | Clippy（cognitive_complexity） | `clippy.toml: cognitive-complexity-threshold = 7` |
| PHP | PHPMD | `reportLevel: 7` |
| Java | PMD | `CyclomaticComplexity` |
| Python | Ruff（McCabe） | `max-complexity = 7` |
| TypeScript | ESLint | `complexity: ["error", { max: 7 }]` |
| Ruby | RuboCop | `Metrics/CyclomaticComplexity` |
| Go | golangci-lint（gocognit） | `.golangci.yml: gocognit.min-complexity: 7` |

## 5.7 まとめ

この章では以下を導入しました。

| ツール | 役割 | 他言語の対応ツール |
|--------|------|-------------------|
| NuGet | パッケージ管理 | npm, Bundler, Cargo, pip |
| dotnet format | コードフォーマット | rustfmt, gofmt, Prettier |
| Roslyn アナライザ | 静的解析（リンター） | Clippy, ESLint, Ruff |
| SonarAnalyzer（S3776） | コード複雑度チェック | Clippy cognitive_complexity, PHPMD, Ruff McCabe |
| coverlet | カバレッジ計測 | cargo-tarpaulin, c8, SimpleCov |

次章では、これらのツールを **タスクランナー**（Cake）でまとめて実行できるようにし、**CI/CD** パイプラインを構築します。
