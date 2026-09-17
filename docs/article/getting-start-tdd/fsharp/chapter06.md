---
type: Article
title: "第 6 章: タスクランナーと CI/CD"
description: "前章では NuGet によるパッケージ管理と Fantomas によるコードフォーマットを導入しました。"
tags: [article, getting-start-tdd, fsharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 6 章: タスクランナーと CI/CD

## 6.1 はじめに

前章では NuGet によるパッケージ管理と Fantomas によるコードフォーマットを導入しました。テストの実行、静的解析、フォーマットチェックと、様々なコマンドを使えるようになりましたが、毎回それぞれのコマンドを覚えて実行するのは面倒です。

この章では **タスクランナー** を使ってこれらのタスクをまとめて実行できるようにし、さらに **CI/CD** パイプラインを構築します。

## 6.2 Cake によるタスク管理

### Cake とは

> Cake（C# Make）は C# で記述できるクロスプラットフォームのビルド自動化ツールです。タスクの定義、依存関係の管理、実行をスクリプトで記述します。

Ruby の Rake、Java の Gradle、Node の npm scripts、Rust の Makefile に相当します。F# プロジェクトでは Cake が広く使われています。

### Cake ビルドスクリプトの定義

```csharp
// build.cake
var target = Argument("target", "Default");
var configuration = Argument("configuration", "Release");

Task("Clean")
    .Does(() =>
{
    CleanDirectory("./src/bin");
    CleanDirectory("./src/obj");
    CleanDirectory("./tests/bin");
    CleanDirectory("./tests/obj");
});

Task("Build")
    .IsDependentOn("Clean")
    .Does(() =>
{
    DotNetBuild("./FizzBuzzFSharp.sln", new DotNetBuildSettings
    {
        Configuration = configuration
    });
});

Task("Format")
    .Does(() =>
{
    StartProcess("dotnet", "fantomas ./src --recurse");
    StartProcess("dotnet", "fantomas ./tests --recurse");
});

Task("Test")
    .IsDependentOn("Build")
    .Does(() =>
{
    DotNetTest("./tests/FizzBuzzFSharpTest.fsproj", new DotNetTestSettings
    {
        Configuration = configuration,
        NoBuild = true
    });
});

Task("All")
    .IsDependentOn("Clean")
    .IsDependentOn("Format")
    .IsDependentOn("Build")
    .IsDependentOn("Test");

Task("Default")
    .IsDependentOn("All");

RunTarget(target);
```

### 主要なタスク

| タスク | コマンド | 説明 |
|--------|---------|------|
| `dotnet cake` | 全タスク実行 | Clean → Format → Build → Test |
| `dotnet cake --target=Test` | テスト実行 | Build → Test |
| `dotnet cake --target=Format` | フォーマット | Fantomas 実行 |
| `dotnet cake --target=Clean` | クリーン | ビルド成果物の削除 |

### 実行例

```bash
# 全チェック実行
$ dotnet cake
========================================
Clean
========================================
Build
========================================
Format
========================================
Test
  合計: 7、成功: 7、失敗: 0、スキップ: 0
========================================
Task Duration
--------------------------------------------------
Clean    00:00:00.1234567
Build    00:00:02.3456789
Format   00:00:01.2345678
Test     00:00:03.4567890
--------------------------------------------------
Total:   00:00:07.1604924
```

## 6.3 GitHub Actions による CI/CD

### CI/CD とは

> CI/CD（Continuous Integration / Continuous Delivery）は、コードの変更を自動的にビルド、テスト、デプロイするプラクティスです。

### ワークフローの定義

`.github/workflows/fsharp-ci.yml` にワークフローを定義します。

```yaml
name: F# CI

on:
  push:
    branches: [main, develop]
    paths:
      - "apps/dotnet/FizzBuzzFSharp/**"
      - "apps/dotnet/FizzBuzzFSharpTest/**"
      - ".github/workflows/fsharp-ci.yml"
  pull_request:
    branches: [main]
    paths:
      - "apps/dotnet/FizzBuzzFSharp/**"
      - "apps/dotnet/FizzBuzzFSharpTest/**"

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout the repository
        uses: actions/checkout@v4

      - name: Setup .NET
        uses: actions/setup-dotnet@v4
        with:
          dotnet-version: 8.0.x

      - name: Restore dependencies
        run: dotnet restore
        working-directory: apps/dotnet

      - name: Build
        run: dotnet build --no-restore
        working-directory: apps/dotnet

      - name: Run tests
        run: dotnet test --no-build
        working-directory: apps/dotnet
```

### CI パイプラインの流れ

```
Push / PR → Restore → Build → Test → 結果通知
```

## 6.4 他言語との比較

| 言語 | タスクランナー | CI ツール | テスト | フォーマット |
|------|-------------|----------|--------|------------|
| F# | Cake | GitHub Actions | xUnit | Fantomas |
| Rust | Makefile | GitHub Actions | cargo test | rustfmt |
| Go | Makefile | GitHub Actions | go test | gofmt |
| Java | Gradle | GitHub Actions | JUnit | Checkstyle |
| Python | tox | GitHub Actions | pytest | Ruff |
| Node | npm scripts | GitHub Actions | Vitest | Prettier |

## 6.5 まとめ

この章では以下を実現しました。

| 項目 | 内容 |
|------|------|
| Cake | Clean / Format / Build / Test タスクを定義 |
| `dotnet cake` | フォーマット → ビルド → テストを一括実行 |
| GitHub Actions | push / PR 時に自動で CI を実行 |
| .NET SDK 統合 | CI でも同じ dotnet コマンドを使用し環境を統一 |

第 2 部を通じて、ソフトウェア開発の三種の神器（バージョン管理、テスティング、自動化）を F# の開発環境に整備しました。次の第 3 部では、F# の関数型アプローチによる設計（レコード型、判別共用体、モジュール分割）に進みます。
