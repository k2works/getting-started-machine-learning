# テスト駆動開発から始める C# 入門

## 概要

FizzBuzz 問題を題材に、テスト駆動開発（TDD）の基本サイクルから、開発環境の整備、オブジェクト指向設計、関数型プログラミングの活用まで、C# の特徴を活かしながら段階的に学びます。

## 対象読者

- C# の基本文法を理解しているプログラミング学習者
- TDD を体験してみたい開発者
- オブジェクト指向設計や LINQ による関数型プログラミングに興味がある方

## 前提条件

- .NET 8.0 SDK 以降がインストールされていること
- dotnet CLI が利用可能であること（Nix 環境推奨: `nix develop .#dotnet`）

## C# の特徴

| 特徴 | 説明 |
|------|------|
| 静的型付け + 型推論 | 強力な型システムと `var` による型推論 |
| オブジェクト指向 | class, interface, abstract class による本格的な OOP |
| 関数型要素 | LINQ, ラムダ式, デリゲートによる関数型プログラミング |
| Nullable 参照型 | null 安全性をコンパイル時に検査 |
| パターンマッチング | switch 式、型パターン、プロパティパターン |
| クロスプラットフォーム | .NET による Windows / macOS / Linux 対応 |

## 開発ツール

| ツール | 用途 |
|--------|------|
| [xUnit.net](https://xunit.net/) | テスティングフレームワーク |
| [NuGet](https://www.nuget.org/) | パッケージマネージャ |
| [dotnet format](https://learn.microsoft.com/dotnet/core/tools/dotnet-format) | コードフォーマッター |
| [Microsoft.CodeAnalysis.Analyzers](https://github.com/dotnet/roslyn-analyzers) | 静的コード解析 |
| [Cake](https://cakebuild.net/) | ビルドスクリプト |

## 目次

### 第 1 部: TDD の基本サイクル

1. [第 1 章: TODO リストと最初のテスト](chapter01.md)
2. [第 2 章: 仮実装と三角測量](chapter02.md)
3. [第 3 章: 明白な実装とリファクタリング](chapter03.md)

### 第 2 部: 開発環境と自動化

4. [第 4 章: バージョン管理と Conventional Commits](chapter04.md)
5. [第 5 章: パッケージ管理と静的解析](chapter05.md)
6. [第 6 章: タスクランナーと CI/CD](chapter06.md)

### 第 3 部: オブジェクト指向設計

7. [第 7 章: カプセル化とポリモーフィズム](chapter07.md)
8. [第 8 章: デザインパターンの適用](chapter08.md)
9. [第 9 章: SOLID 原則とモジュール設計](chapter09.md)

### 第 4 部: 関数型プログラミングへの展開

10. [第 10 章: 高階関数と関数合成](chapter10.md)
11. [第 11 章: 不変データとパイプライン処理](chapter11.md)
12. [第 12 章: エラーハンドリングと型安全性](chapter12.md)

## 実装コード

本記事のすべてのコード例は `apps/dotnet/` に実装されています。

```bash
# 開発環境に入る
nix develop .#dotnet

# テスト実行
cd apps/dotnet
dotnet test
```

## 参考文献

- Kent Beck 著『テスト駆動開発』
- Martin Fowler 著『リファクタリング: 既存のコードを安全に改善する』
- Robert C. Martin 著『Clean Code: アジャイルソフトウェア達人の技』
- Microsoft『C# プログラミングガイド』
