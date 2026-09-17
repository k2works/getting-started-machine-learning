# F# で学ぶ並行処理

## 概要

本シリーズでは、F# を使用して並行処理プログラミングの基礎から応用までを学びます。

## 目次

| Part | トピック | 内容 |
|------|----------|------|
| [Part I](part-1.md) | 逐次処理 | パスワードクラッキング、パフォーマンス |
| [Part II](part-2.md) | スレッド | Thread、Async、並列実行 |
| [Part III](part-3.md) | マルチタスキング | ゲームループ、タスクスケジューリング |
| [Part IV](part-4.md) | 並列パターン | Fork/Join、パイプライン |
| [Part V](part-5.md) | 同期と排他制御 | lock、デッドロック回避 |
| [Part VI](part-6.md) | ノンブロッキング I/O | Async |
| [Part VII](part-7.md) | 非同期プログラミング | Async ワークフロー |
| [Part VIII](part-8.md) | 分散並列処理 | MapReduce、Array.Parallel |

## 環境設定

### 必要なツール

- .NET 8.0 SDK
- F# 8.0

### プロジェクト構成

```
apps/fsharp/
├── GrokkingConcurrency.FSharp.sln
├── src/
│   ├── Ch02/    # Part I
│   ├── Ch04/    # Part II
│   ├── Ch05/    # Part II
│   ├── Ch06/    # Part III
│   ├── Ch07/    # Part IV
│   ├── Ch08/    # Part V
│   └── Ch13/    # Part VIII
└── tests/
```

### ビルドと実行

```bash
cd apps/fsharp
dotnet build    # ビルド
dotnet test     # テスト実行
```

## 参考資料

- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency)
- [F# Documentation](https://docs.microsoft.com/ja-jp/dotnet/fsharp/)
- [F# Async Programming](https://docs.microsoft.com/ja-jp/dotnet/fsharp/tutorials/asynchronous-and-concurrent-programming/async)
