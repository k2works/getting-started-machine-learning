# Grokking Concurrency

Python、Java、C#、Scala、F#、Rust、Haskell、Clojure で学ぶ並行処理プログラミング

---

## 概要

本サイトは「Grokking Concurrency」（Kirill Bobrov 著）の学習コンパニオンとして、並行処理プログラミングの概念を複数の言語で実装しながら日本語で解説します。

---

## 多言語統合解説

8 つの言語を横断的に比較し、並行処理の**本質**と**言語固有の表現**を統合的に解説します。

| Part | トピック | 統合記事 |
|------|----------|----------|
| I | 並行処理の基礎 | [逐次処理の言語別比較](all/part-1-ch02-sequential.md) |
| II | プロセスとスレッド | [スレッド生成・管理の API 比較](all/part-2-ch04-05-threads.md) |
| III | マルチタスキング | [イベント駆動モデルの比較](all/part-3-ch06-multitasking.md) |
| IV | 並列パターン | [Fork/Join・パイプラインの比較](all/part-4-ch07-parallel-patterns.md) |
| V | 同期と排他制御 | [Lock/STM/所有権の比較](all/part-5-ch08-09-synchronization.md) |
| VI | ノンブロッキング I/O | [Reactor パターンと I/O モデル比較](all/part-6-ch10-11-nonblocking-io.md) |
| VII | 非同期プログラミング | [async/await と Future/Promise 比較](all/part-7-ch12-async.md) |
| VIII | 分散並列処理 | [MapReduce と並列コレクション比較](all/part-8-ch13-mapreduce.md) |

> 詳細は [多言語統合解説 概要](all/index.md) を参照

---

## 言語別学習ガイド

| 言語 | 特徴 | 主要な並行処理機能 |
|------|------|-------------------|
| [Python](python/index.md) | 動的型付け、シンプル | threading, multiprocessing, asyncio |
| [Java](java/index.md) | JVM、エンタープライズ | ExecutorService, CompletableFuture |
| [C#](csharp/index.md) | .NET、モダン構文 | Task, async/await, TPL |
| [Scala](scala/index.md) | 関数型+オブジェクト指向 | Future, Akka Actor |
| [F#](fsharp/index.md) | .NET 関数型 | Async, MailboxProcessor |
| [Rust](rust/index.md) | メモリ安全、ゼロコスト | std::thread, Rayon, tokio |
| [Haskell](haskell/index.md) | 純粋関数型 | STM, async, forkIO |
| [Clojure](clojure/index.md) | Lisp 系、JVM | atom, ref/STM, core.async |

---

## 学習トピック

本シリーズでは以下のトピックを扱います：

| Part | トピック | キーワード |
|------|----------|------------|
| I | 並行処理の基礎 | 逐次処理、パフォーマンス測定 |
| II | プロセスとスレッド | Process、Thread、スレッドプール |
| III | マルチタスキング | タイムシェアリング、スケジューリング |
| IV | 並列パターン | Fork/Join、パイプライン |
| V | 同期と排他制御 | Lock、Semaphore、デッドロック |
| VI | ノンブロッキング I/O | イベントループ、Reactor |
| VII | 非同期プログラミング | asyncio、async/await |
| VIII | 分散並列処理 | MapReduce、分散処理 |

---

## 参考資料

- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency) - 原著（Manning Publications）
