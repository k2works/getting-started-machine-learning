---
type: Article
title: "第 6 章: タスクランナーと CI/CD"
description: "前章では静的コード解析ツールとコードフォーマッターを導入しました。"
tags: [article, getting-start-tdd, csharp]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 6 章: タスクランナーと CI/CD

## 6.1 はじめに

前章では静的コード解析ツールとコードフォーマッターを導入しました。テストの実行、静的解析、フォーマットチェックと、様々なコマンドを使えるようになりましたが、毎回それぞれのコマンドを覚えて実行するのは面倒です。

この章では **タスクランナー** を使ってこれらのタスクをまとめて実行できるようにし、さらに **CI/CD** パイプラインを構築します。

## 6.2 Cake によるタスク管理

### Cake とは

> Cake (C# Make) は C# の DSL で記述するクロスプラットフォームのビルドオーケストレーションツールです。ビルド、テスト、デプロイなどのタスクを C# のコードで定義できます。

Ruby の Rake、Java の Gradle、Rust の Makefile に相当します。C# 開発者にとっては、使い慣れた C# 構文でタスクを定義できるのが利点です。

### Cake のインストール

```bash
# グローバルツールとしてインストール
$ dotnet tool install -g Cake.Tool

# またはローカルツールとして
$ dotnet new tool-manifest
$ dotnet tool install Cake.Tool
```

### build.cake の定義

```csharp
var target = Argument("target", "Default");

Task("Clean")
    .Does(() =>
{
    DotNetClean(".");
});

Task("Restore")
    .Does(() =>
{
    DotNetRestore(".");
});

Task("Build")
    .IsDependentOn("Restore")
    .Does(() =>
{
    DotNetBuild(".", new DotNetBuildSettings
    {
        NoRestore = true
    });
});

Task("Test")
    .IsDependentOn("Build")
    .Does(() =>
{
    DotNetTest(".", new DotNetTestSettings
    {
        NoRestore = true,
        NoBuild = true
    });
});

Task("Format-Check")
    .Does(() =>
{
    DotNetTool("dotnet format --verify-no-changes");
});

Task("Check")
    .IsDependentOn("Format-Check")
    .IsDependentOn("Build")
    .IsDependentOn("Test");

Task("Default")
    .IsDependentOn("Check");

RunTarget(target);
```

### 主要なタスク

| タスク | コマンド | 説明 |
|--------|---------|------|
| `dotnet cake --target=Test` | Build → Test | テスト実行 |
| `dotnet cake --target=Format-Check` | dotnet format --verify-no-changes | フォーマットチェック |
| `dotnet cake --target=Check` | Format-Check → Build → Test | 全チェック実行 |
| `dotnet cake --target=Clean` | dotnet clean | ビルド成果物の削除 |
| `dotnet cake` | Default (= Check) | デフォルトタスク |

### 実行例

```bash
# 全チェック実行
$ dotnet cake
========================================
Format-Check
========================================
========================================
Build
========================================
========================================
Test
========================================
成功!   -失敗:     0、合格:     7、スキップ:     0
```

## 6.3 GitHub Actions による CI/CD

### CI/CD とは

> CI/CD（Continuous Integration / Continuous Delivery）は、コードの変更を自動的にビルド、テスト、デプロイするプラクティスです。

### ワークフローの定義

`.github/workflows/dotnet-ci.yml` にワークフローを定義します。

```yaml
name: .NET CI

on:
  push:
    branches: [main, develop]
    paths:
      - "apps/dotnet/**"
      - ".github/workflows/dotnet-ci.yml"
  pull_request:
    branches: [main]
    paths:
      - "apps/dotnet/**"

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
          dotnet-version: '8.0.x'

      - name: Restore dependencies
        run: dotnet restore
        working-directory: apps/dotnet

      - name: Check formatting
        run: dotnet format --verify-no-changes
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
Push / PR → restore → format --verify-no-changes → build → test → 結果通知
```

## 6.4 他言語との比較

| 言語 | タスクランナー | CI ツール | テスト | 静的解析 | フォーマット |
|------|-------------|----------|--------|---------|------------|
| C# | Cake | GitHub Actions | xUnit | Roslyn Analyzers | dotnet format |
| Rust | Makefile | GitHub Actions | cargo test | Clippy | rustfmt |
| Go | Makefile | GitHub Actions | go test | golangci-lint | gofmt |
| Java | Gradle | GitHub Actions | JUnit | Checkstyle + PMD | Checkstyle |
| Python | tox | GitHub Actions | pytest | Ruff | Ruff |
| Node | npm scripts | GitHub Actions | Vitest | ESLint | Prettier |

## 6.5 まとめ

この章では以下を実現しました。

| 項目 | 内容 |
|------|------|
| Cake | Build / Test / Format-Check / Check タスクを定義 |
| `dotnet cake` | フォーマットチェック → ビルド → テストを一括実行 |
| GitHub Actions | push / PR 時に自動で CI を実行 |
| .NET SDK 統合 | CI でも `dotnet` コマンドで環境を統一 |

第 2 部を通じて、ソフトウェア開発の三種の神器（バージョン管理、テスティング、自動化）を C# の開発環境に整備しました。次の第 3 部では、オブジェクト指向設計（class、interface、デザインパターン）に進みます。
