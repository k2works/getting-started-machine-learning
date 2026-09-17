# C# で学ぶ並行処理プログラミング

## 概要

このセクションでは、Grokking Concurrency の内容を **C# 12 / .NET 8** で実装しながら学びます。TDD（テスト駆動開発）で実装されたコード例と、詳細なドキュメントを通じて、並行処理の基礎から応用までを理解できます。

---

## 目次

### Part I: 並行処理の基礎

逐次処理の特徴と限界を理解し、なぜ並行処理が必要かを学びます。

- [Part I - 並行処理の基礎](part-1.md)

### Part II: プロセスとスレッド

OS レベルの並行処理の基本単位であるプロセスとスレッドを学びます。

- [Part II - プロセスとスレッド](part-2.md)

### Part III: マルチタスキング

OS がどのようにタスクを切り替えるか、タイムシェアリングの仕組みを学びます。

- [Part III - マルチタスキングとスケジューリング](part-3.md)

### Part IV: タスク分解と並列パターン

Fork/Join パターンとパイプラインパターンを学びます。

- [Part IV - タスク分解と並列パターン](part-4.md)

### Part V: 同期と排他制御

Lock、Monitor、デッドロックについて学びます。

- [Part V - 同期と排他制御](part-5.md)

### Part VI: ノンブロッキング I/O

async/await を使ったノンブロッキング I/O を学びます。

- [Part VI - ノンブロッキング I/O](part-6.md)

### Part VII: 非同期プログラミング

Task と async/await を学びます。

- [Part VII - 非同期プログラミング](part-7.md)

### Part VIII: 分散並列処理

MapReduce パターンと PLINQ を学びます。

- [Part VIII - 分散並列処理](part-8.md)

---

## 技術スタック

- **C# 12** (Primary Constructor, Collection Expressions)
- **.NET 8** (ランタイム)
- **xUnit** (テストフレームワーク)

---

## 実行方法

### テストの実行

```bash
cd apps/csharp
dotnet test
```

### サンプルコードの実行

```bash
cd apps/csharp
dotnet run --project src
```

---

## 参考資料

- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency) - 原著
- [C# in Depth](https://csharpindepth.com/) - C# 深掘り
- [.NET 8 Documentation](https://docs.microsoft.com/ja-jp/dotnet/)
