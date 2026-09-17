# Grokking Concurrency：多言語統合解説

本記事シリーズは、8 つの言語（Python, Java, C#, Scala, F#, Rust, Haskell, Clojure）での実装を横断的に比較し、並行処理プログラミングの**本質**と**言語固有の表現**を統合的に解説します。

## 本シリーズの目的

各言語の個別記事は「その言語でどう実装するか」に焦点を当てています。本統合記事は、それらを横断して以下を明らかにします：

- **共通の本質**: 言語を超えて成り立つ並行処理の原則
- **言語間の差異**: ランタイム、メモリモデル、並行処理プリミティブの違いが実装にどう影響するか
- **選択の指針**: どの言語・ライブラリがどの並行処理パターンに適しているか

## 言語特性マトリクス

### 言語分類

| グループ | 言語 | 特徴 |
|---------|------|------|
| 関数型ファースト | Haskell, Clojure | 不変性・STM による安全な並行処理 |
| マルチパラダイム（静的） | Scala, Rust, F# | 型システムを活用した並行処理の安全性保証 |
| OOP ファースト + 並行処理ライブラリ | Java, C#, Python | プラットフォームの並行処理 API を活用 |

### 特性比較

| 特性 | Python | Java | C# | Scala | F# | Rust | Haskell | Clojure |
|------|--------|------|-----|-------|-----|------|---------|---------|
| 実行環境 | CPython | JVM | .NET (CLR) | JVM | .NET (CLR) | ネイティブ | GHC | JVM |
| スレッドモデル | OS スレッド (GIL) | OS スレッド + Virtual Thread | OS スレッド + Task | OS スレッド + Future | OS スレッド + Async | OS スレッド | Green Thread | OS スレッド (JVM) |
| 軽量タスク | asyncio Task | Virtual Thread (21+) | Task (TPL) | Future | Async ワークフロー | tokio Task | forkIO | core.async go ブロック |
| 排他制御 | threading.Lock | synchronized / ReentrantLock | lock / Monitor | synchronized | MailboxProcessor | Mutex / RwLock | MVar / TVar | atom / ref |
| 共有状態管理 | Lock + 変数 | synchronized | lock + volatile | synchronized | Agent / MailboxProcessor | Arc\<Mutex\<T\>\> | STM / TVar | atom / ref / STM |
| チャネル / メッセージ | queue.Queue | BlockingQueue | Channel | なし（標準） | MailboxProcessor | mpsc / crossbeam | Chan / TChan | core.async chan |
| 非同期 I/O | asyncio | NIO / CompletableFuture | async/await (Task) | Future | Async ワークフロー | tokio (async/await) | async ライブラリ | core.async |
| 並列イテレータ | multiprocessing.Pool | parallelStream | Parallel LINQ | .par | Array.Parallel | Rayon par_iter | par (parallel) | pmap / reducers |
| メモリ安全性 | ランタイム (GIL) | ランタイム (JVM) | ランタイム (CLR) | ランタイム (JVM) | ランタイム (CLR) | コンパイル時 (所有権) | ランタイム (GC) | ランタイム (JVM) |
| データ競合防止 | GIL（部分的） | ランタイム検出 | ランタイム検出 | ランタイム検出 | 型レベル（部分的） | コンパイル時 (Send/Sync) | 型レベル (STM) | 不変データ + STM |

## 記事構成

### Part I: 並行処理の基礎（第 2 章）

| Part | 統合記事 | テーマ | 比較のポイント |
|------|---------|--------|---------------|
| I | [並行処理の基礎](./part-1-ch02-sequential.md) | 逐次処理、パスワードクラッキング | 各言語の基本構文、イテレーション、ハッシュ計算の表現スタイル |

#### 主な言語間比較テーマ

- **基本構文**: 各言語のループ・イテレーション表現（for vs イテレータ vs 再帰）
- **ハッシュ計算**: 標準ライブラリ vs サードパーティ（sha2 crate vs MessageDigest vs hashlib）
- **パフォーマンス計測**: 各言語のベンチマーク手法の違い

### Part II: プロセスとスレッド（第 4-5 章）

| Part | 統合記事 | テーマ | 比較のポイント |
|------|---------|--------|---------------|
| II | [プロセスとスレッド](./part-2-ch04-05-threads.md) | プロセス、スレッド、スレッドプール | スレッド生成 API、ライフサイクル管理、IPC の違い |

#### 主な言語間比較テーマ

- **スレッド生成**: `std::thread::spawn` vs `Thread()` vs `threading.Thread` vs `forkIO` vs `future`
- **スレッドプール**: Rayon vs ExecutorService vs ThreadPoolExecutor vs Async ワークフロー
- **所有権とスレッド**: Rust の `Send`/`Sync` トレイト vs 他言語のランタイム保護

### Part III: マルチタスキングとスケジューリング（第 6 章）

| Part | 統合記事 | テーマ | 比較のポイント |
|------|---------|--------|---------------|
| III | [マルチタスキング](./part-3-ch06-multitasking.md) | タイムシェアリング、ゲームループ | イベントループ、条件変数、協調的マルチタスキングの実現方法 |

#### 主な言語間比較テーマ

- **ゲームループ**: Condvar (Rust) vs wait/notify (Java) vs Monitor (C#) vs MVar (Haskell)
- **協調的スケジューリング**: asyncio (Python) vs core.async (Clojure) vs Async (F#)
- **プリエンプティブ vs 協調的**: 各言語ランタイムのスケジューリングモデル

### Part IV: タスク分解と並列パターン（第 7 章）

| Part | 統合記事 | テーマ | 比較のポイント |
|------|---------|--------|---------------|
| IV | [並列パターン](./part-4-ch07-parallel-patterns.md) | Fork/Join、パイプライン、データ並列 | 並列パターンのイディオム、チャネル vs ストリーム |

#### 主な言語間比較テーマ

- **Fork/Join**: ForkJoinPool (Java/Scala) vs Rayon (Rust) vs parallel (Haskell) vs pmap (Clojure)
- **パイプライン**: mpsc (Rust) vs BlockingQueue (Java) vs Channel (C#) vs core.async pipeline (Clojure)
- **データ並列**: parallelStream (Java) vs par_iter (Rust) vs Array.Parallel (F#) vs pmap (Clojure)

### Part V: 同期と排他制御（第 8-9 章）

| Part | 統合記事 | テーマ | 比較のポイント |
|------|---------|--------|---------------|
| V | [同期と排他制御](./part-5-ch08-09-synchronization.md) | Race Condition、Lock、Semaphore、デッドロック | 排他制御の抽象度、デッドロック防止策の違い |

#### 主な言語間比較テーマ

- **排他制御**: `Mutex<T>` (Rust) vs `synchronized` (Java) vs `lock` (C#) vs STM (Haskell/Clojure)
- **デッドロック回避**: Rust のコンパイル時検出 vs Haskell STM の楽観的並行制御 vs ロック順序規約
- **不変性による回避**: Clojure の永続データ構造 + atom vs Haskell の純粋性による副作用分離

### Part VI: ノンブロッキング I/O（第 10-11 章）

| Part | 統合記事 | テーマ | 比較のポイント |
|------|---------|--------|---------------|
| VI | [ノンブロッキング I/O](./part-6-ch10-11-nonblocking-io.md) | ブロッキング vs ノンブロッキング、イベントループ、Reactor パターン | I/O モデル、イベントループ実装、Reactor パターンの表現 |

#### 主な言語間比較テーマ

- **I/O モデル**: NIO (Java) vs async I/O (Python) vs tokio (Rust) vs async ワークフロー (F#)
- **イベントループ**: asyncio (Python) vs libuv 相当 (tokio) vs GHC I/O マネージャ (Haskell)
- **Reactor パターン**: Selector (Java) vs epoll/kqueue ラッパー (Rust) vs 各言語の抽象化レベル

### Part VII: 非同期プログラミング（第 12 章）

| Part | 統合記事 | テーマ | 比較のポイント |
|------|---------|--------|---------------|
| VII | [非同期プログラミング](./part-7-ch12-async.md) | Future、Coroutine、async/await | async/await の意味論、Future/Promise の型表現 |

#### 主な言語間比較テーマ

- **async/await**: Rust (ゼロコスト Future) vs Python (コルーチン) vs C# (Task ベース) vs Scala (Future)
- **コルーチン**: Python ジェネレータ vs Clojure go ブロック vs Haskell 軽量スレッド
- **Future の型表現**: `Future<Output=T>` (Rust) vs `CompletableFuture<T>` (Java) vs `Task<T>` (C#) vs `Async<'a>` (F#)

### Part VIII: 分散並列処理（第 13 章）

| Part | 統合記事 | テーマ | 比較のポイント |
|------|---------|--------|---------------|
| VIII | [分散並列処理](./part-8-ch13-mapreduce.md) | MapReduce、ワードカウント、行列乗算 | MapReduce の関数型的表現、並列コレクション |

#### 主な言語間比較テーマ

- **MapReduce**: parallelStream (Java) vs par_iter (Rust) vs pmap/reducers (Clojure) vs parMap (Haskell)
- **並列コレクション**: Scala `.par` vs Rayon vs Array.Parallel (F#) vs multiprocessing.Pool (Python)
- **関数型的表現**: map/reduce の合成、不変データ構造上での並列演算

## 各章の統合記事構成テンプレート

各統合記事は以下の構成で執筆します：

```
1. はじめに
   - テーマの概要と並行処理での意義

2. 共通の本質
   - 言語を超えて成り立つ並行処理の原則

3. 言語別実装比較
   - 8 言語の代表的なコードを並べて比較
   - 各言語のイディオムを活かした実装の違い
   - 言語グループ別の傾向分析

4. 比較分析
   - ランタイムとメモリモデルの影響
   - 安全性保証のアプローチ（コンパイル時 vs ランタイム）
   - 表現力と安全性のトレードオフ

5. 実践的な選択指針
   - どの言語特性がこのテーマに最も適しているか
   - ユースケースに応じた選択基準

6. まとめ
   - 言語横断的な学び
   - 各言語の個別記事へのリンク
```

## 執筆ルール

### 1. 執筆フォーマット

````markdown
# Part N: パートタイトル

## N.1 セクションタイトル

本文...

### 言語別実装比較

#### 関数型ファースト言語

<details>
<summary>Haskell 実装</summary>

```haskell
-- コード例
```

</details>

<details>
<summary>Clojure 実装</summary>

```clojure
;; コード例
```

</details>

#### マルチパラダイム言語

<details>
<summary>Scala 実装</summary>

```scala
// コード例
```

</details>

<details>
<summary>Rust 実装</summary>

```rust
// コード例
```

</details>

<details>
<summary>F# 実装</summary>

```fsharp
// コード例
```

</details>

#### OOP + 並行処理ライブラリ言語

<details>
<summary>Java 実装</summary>

```java
// コード例
```

</details>

<details>
<summary>C# 実装</summary>

```csharp
// コード例
```

</details>

<details>
<summary>Python 実装</summary>

```python
# コード例
```

</details>
````

### 2. リスト記述ルール

タスク項目やリスト項目は、ラベル行の後に **1 行空けて** から記述します。

**OK**:

```markdown
**比較ポイント**:

- 排他制御の抽象度
- パフォーマンス特性
- 安全性保証
```

### 3. 言語グループ別の整理

コード比較では 8 言語を以下のグループに分けて提示します：

1. **関数型ファースト**: Haskell → Clojure
2. **マルチパラダイム（静的）**: Scala → Rust → F#
3. **OOP + 並行処理ライブラリ**: Java → C# → Python

## 執筆計画

### 概要

全 8 Part の統合記事を、Part ごとの統合難易度に基づき 3 つのイテレーションで執筆します。

- **総ソース量**: 8 言語 × 8 Part = 64 ファイル
- **統合記事数**: 8 本
- **各記事の想定規模**: 500〜1,000 行

### 難易度分類

| 難易度 | Part 数 | 特徴 |
|--------|---------|------|
| Low | 3 Part（I-III） | 全言語で構造が一貫、基礎概念で統合しやすい |
| Medium | 3 Part（IV, VII, VIII） | パターンの表現方法に言語差があるが共通構造あり |
| High | 2 Part（V, VI） | 同期モデル・I/O モデルが言語ごとに根本的に異なる |

### イテレーション 1: 基礎と並行処理入門（Low 難易度）

**対象**: Part I + Part II + Part III（第 2, 4-6 章）

全言語で概念構造が一貫しており、統合が最も容易です。

| Part | タイトル | 難易度 | 統合方針 |
|------|---------|--------|---------|
| I | 並行処理の基礎 | Low | 逐次処理の表現を全言語で横断比較 |
| II | プロセスとスレッド | Low | スレッド生成・管理の API を言語別に比較 |
| III | マルチタスキング | Low | ゲームループ実装を通じたイベント駆動モデルの比較 |

**執筆順序**: I → II → III

**完了条件**:

- [ ] 各 Part が統合記事構成テンプレートに準拠
- [ ] 8 言語すべてのコード例を含む
- [ ] 言語グループ別の傾向分析を含む

### イテレーション 2: 並列パターンと非同期（Medium 難易度）

**対象**: Part IV + Part VII + Part VIII（第 7, 12, 13 章）

並列パターンの表現差が大きいが、共通のアルゴリズム構造を軸に統合可能です。

| Part | タイトル | 難易度 | 統合方針 |
|------|---------|--------|---------|
| IV | タスク分解と並列パターン | Medium | Fork/Join・パイプラインの構造を軸に言語別実装を比較 |
| VII | 非同期プログラミング | Medium | async/await と Future/Promise の型表現を比較 |
| VIII | 分散並列処理 | Medium | MapReduce の関数型的表現と並列コレクションを比較 |

**執筆順序**: IV → VIII → VII

**完了条件**:

- [ ] Fork/Join パターンの 8 言語比較表を含む
- [ ] async/await の意味論の違いを図解
- [ ] MapReduce のパフォーマンス特性の比較を含む

### イテレーション 3: 同期と I/O モデル（High 難易度）

**対象**: Part V + Part VI（第 8-11 章）

同期モデルと I/O モデルが言語ごとに根本的に異なり、最難関です。

| Part | タイトル | 難易度 | 統合方針 |
|------|---------|--------|---------|
| V | 同期と排他制御 | High | Lock/STM/所有権の根本的な違いを言語別に深掘り |
| VI | ノンブロッキング I/O | High | Reactor パターンと I/O モデルの違いを言語別に解説 |

**執筆順序**: V → VI

**完了条件**:

- [ ] 排他制御モデル（Lock vs STM vs 所有権）の比較表を含む
- [ ] I/O モデル（ブロッキング vs ノンブロッキング vs 非同期）の比較表を含む
- [ ] デッドロック防止策の言語別アプローチを比較

### 全体スケジュール

| イテレーション | Part 数 | 難易度構成 | 主な課題 |
|---------------|---------|-----------|---------|
| 1 | 3 Part（I-III） | Low × 3 | ウォームアップ、8 言語比較のフォーマット確立 |
| 2 | 3 Part（IV, VII, VIII） | Medium × 3 | 並列パターンの表現差の整理、async モデルの比較 |
| 3 | 2 Part（V, VI） | High × 2 | 同期モデル・I/O モデルの根本的差異への対応 |

### 進捗管理

| Part | タイトル | イテレーション | 難易度 | 状態 |
|------|---------|---------------|--------|------|
| I | 並行処理の基礎 | 1 | Low | 完了 |
| II | プロセスとスレッド | 1 | Low | 完了 |
| III | マルチタスキング | 1 | Low | 完了 |
| IV | タスク分解と並列パターン | 2 | Medium | 完了 |
| V | 同期と排他制御 | 3 | High | 完了 |
| VI | ノンブロッキング I/O | 3 | High | 完了 |
| VII | 非同期プログラミング | 2 | Medium | 完了 |
| VIII | 分散並列処理 | 2 | Medium | 完了 |

## 言語別個別記事へのリンク

| 言語 | 並行処理ライブラリ | 個別記事一覧 |
|------|-------------------|-------------|
| Python | threading, asyncio, multiprocessing | [全 8 Part](../python/index.md) |
| Java | ExecutorService, CompletableFuture, Virtual Thread | [全 8 Part](../java/index.md) |
| C# | Task (TPL), async/await, Channel | [全 8 Part](../csharp/index.md) |
| Scala | Future, synchronized, Parallel Collections | [全 8 Part](../scala/index.md) |
| F# | Async ワークフロー, MailboxProcessor, Agent | [全 8 Part](../fsharp/index.md) |
| Rust | std::thread, Rayon, tokio | [全 8 Part](../rust/index.md) |
| Haskell | forkIO, async, STM | [全 8 Part](../haskell/index.md) |
| Clojure | atom, ref/STM, core.async | [全 8 Part](../clojure/index.md) |

## 参照

- 「Grokking Concurrency」Kirill Bobrov, Manning Publications
- [Python threading ドキュメント](https://docs.python.org/3/library/threading.html)
- [Java Concurrency in Practice](https://jcip.net/)
- [C# 非同期プログラミング](https://docs.microsoft.com/ja-jp/dotnet/csharp/async)
- [Scala 並行処理ドキュメント](https://docs.scala-lang.org/overviews/core/futures.html)
- [F# 非同期プログラミング](https://docs.microsoft.com/ja-jp/dotnet/fsharp/tutorials/async)
- [Rust Fearless Concurrency](https://doc.rust-lang.org/book/ch16-00-concurrency.html)
- [Haskell Concurrency](https://wiki.haskell.org/Concurrency)
- [Clojure Concurrency](https://clojure.org/about/concurrent_programming)
- [Rayon](https://github.com/rayon-rs/rayon)
- [Tokio](https://tokio.rs/)
