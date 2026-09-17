---
type: Article
title: "Part VI: ノンブロッキング I/O"
description: "Part V でロックベースの同期を学びましたが、ロックにはデッドロックやコンテキストスイッチのオーバーヘッドが伴います。"
tags: [article, grokking-concurrency, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part VI: ノンブロッキング I/O

## 6.1 はじめに

Part V でロックベースの同期を学びましたが、ロックにはデッドロックやコンテキストスイッチのオーバーヘッドが伴います。本章では、**スレッドをブロックせずに I/O を処理する**ノンブロッキング I/O と、それを効率的に管理する**イベントループ**・**Reactor パターン**を学びます。

### なぜノンブロッキング I/O か

| モデル | 接続あたりのコスト | 10,000 接続時 | スケーラビリティ |
|-------|------------------|-------------|---------------|
| スレッドパーコネクション | ~1MB（スタック） | ~10GB メモリ | 低い |
| ノンブロッキング I/O | ~KB（イベント登録） | ~数十 MB | 高い |

従来の「1 接続 = 1 スレッド」モデルでは、大量の同時接続を処理できません。ノンブロッキング I/O は、少数のスレッドで数千の接続を効率的に多重化します。

## 6.2 共通の本質

### ブロッキング vs ノンブロッキング

```
ブロッキング I/O:
  Thread → recv() → [ブロック...データ到着を待つ...] → データ処理
  （スレッドは待機中も占有される）

ノンブロッキング I/O:
  Thread → recv() → [データなし: すぐ返る] → 他の処理を実行
                  → [データあり: すぐ返る] → データ処理
```

### Reactor パターンの構造

```
                    ┌─────────────────┐
 Client 1 ─────────┤                 ├──→ Handler A
 Client 2 ─────────┤   Reactor       ├──→ Handler B
 Client 3 ─────────┤  (Selector)     ├──→ Handler C
 Client N ─────────┤                 ├──→ Handler D
                    └─────────────────┘
                         ↑
                    I/O Multiplexer
                  (select/poll/epoll)
```

**3 つの構成要素**:

1. **Reactor（ディスパッチャ）** — イベントを検出し、対応するハンドラに振り分ける
2. **Handler（コールバック）** — 特定のイベントに対する処理を実行
3. **Multiplexer（多重化）** — OS レベルの I/O 監視（`select`, `poll`, `epoll`, `kqueue`, `IOCP`）

### イベントループのライフサイクル

```
while running:
    1. I/O Multiplexer に登録されたソケットを監視
    2. 準備完了のイベントを取得
    3. 各イベントに対応するハンドラを呼び出す
    4. ハンドラ完了後、1 に戻る
```

## 6.3 言語別実装比較

### I/O 多重化のメカニズム

| 言語 | 低レベル API | 高レベル API | I/O モデル |
|------|------------|------------|-----------|
| Python | `select` / `selectors` | `asyncio` | Selector ベース |
| Java | `Selector` / `Channel` (NIO) | `CompletableFuture` | Channel + Buffer |
| C# | `Socket.Select()` | `async/await` + `Task` | IOCP ベース |
| Scala | Java NIO `Selector` | `Future` | JVM Channel |
| F# | .NET `Socket` | `async { }` ワークフロー | .NET 非同期 |
| Rust | `mio` (低レベル) | `tokio` (高レベル) | epoll/kqueue |
| Haskell | GHC I/O Manager | `async` ライブラリ | Green Thread |
| Clojure | Java NIO | `future` / `core.async` | JVM Channel |

### Reactor パターンの実装

#### 関数型ファースト言語

<details>
<summary>Haskell 実装（async ライブラリ）</summary>

```haskell
import Control.Concurrent.Async

-- 非同期 I/O
handle <- async $ readFile "file.txt"
doSomethingElse
content <- wait handle

-- 複数の非同期タスクを並列実行
downloadAll :: [String] -> IO [String]
downloadAll urls = mapConcurrently fetchData urls

-- レース: 最初に完了した方を返す
result <- race fetchFromServerA fetchFromServerB
```

**特徴**:

- GHC ランタイムが I/O Manager でノンブロッキング I/O を管理
- `async` / `wait` で明示的にイベントループを書く必要がない
- Green Thread により、ブロッキング API をそのまま使っても内部的にはノンブロッキング
- **最も高い抽象度**: 開発者はブロッキング/ノンブロッキングを意識しない

</details>

<details>
<summary>Clojure 実装（future + promise）</summary>

```clojure
;; future: 自動的に非同期実行
(def result (future
  (Thread/sleep 1000)
  (+ 1 2)))

@result  ;; => 3（ブロッキング取得）

;; タイムアウト付き
(deref result 500 :timeout)

;; promise: 明示的に値を配信
(def p (promise))
(future
  (Thread/sleep 100)
  (deliver p :done))
@p  ;; => :done

;; 複数の非同期タスク
(defn fetch-all [urls]
  (let [futures (doall (map #(future (fetch-url %)) urls))]
    (map deref futures)))
```

**特徴**:

- `future` は JVM スレッドプール上で非同期実行
- `promise` は 1 回だけ値を配信できるコンテナ
- `deref` でブロッキング取得（タイムアウトオプション付き）

</details>

#### マルチパラダイム言語

<details>
<summary>Rust 実装（tokio）</summary>

```rust
use tokio;
use tokio::time::{sleep, Duration};

#[tokio::main]
async fn main() {
    let result = fetch_data("url").await;
    println!("{}", result);
}

async fn fetch_data(url: &str) -> String {
    sleep(Duration::from_secs(1)).await;
    format!("Data from {}", url)
}

// 複数タスクの並列実行
async fn parallel_fetch() -> (String, String, String) {
    tokio::join!(
        fetch_data("url1"),
        fetch_data("url2"),
        fetch_data("url3"),
    )
}
```

**特徴**:

- **tokio** がイベントループ（Reactor）を提供
- `async fn` + `.await` でノンブロッキング I/O を記述
- 内部的に epoll/kqueue を使用
- ゼロコスト Future: コンパイラがステートマシンを生成

</details>

<details>
<summary>Scala 実装（Java NIO ラッパー）</summary>

```scala
import java.nio.channels.{Selector, SocketChannel, SelectionKey}

val selector = Selector.open()
channel.configureBlocking(false)
channel.register(selector, SelectionKey.OP_READ)

while true do
  selector.select()
  val keys = selector.selectedKeys().iterator()
  while keys.hasNext do
    val key = keys.next()
    keys.remove()
    if key.isReadable then handleRead(key)
    if key.isWritable then handleWrite(key)
```

**特徴**: Java NIO を Scala の式ベース構文で利用。

</details>

<details>
<summary>F# 実装（Async ワークフロー）</summary>

```fsharp
// ノンブロッキング読み取り
let readFileAsync path : Async<string> =
    async {
        use reader = new StreamReader(path)
        let! content = reader.ReadToEndAsync() |> Async.AwaitTask
        return content
    }

// 並列実行
let results =
    [readFileAsync "a.txt"; readFileAsync "b.txt"; readFileAsync "c.txt"]
    |> Async.Parallel
    |> Async.RunSynchronously
```

**特徴**:

- `async { }` 計算式でノンブロッキング I/O を宣言的に記述
- `let!` でバインド（中断点）
- `Async.Parallel` で並列合成
- C# の `Task` との相互運用が可能

</details>

#### OOP + 並行処理ライブラリ言語

<details>
<summary>Java 実装（NIO Selector）</summary>

```java
Selector selector = Selector.open();
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);
serverChannel.register(selector, SelectionKey.OP_ACCEPT);

while (true) {
    selector.select();  // ブロック: イベントが来るまで待機
    Set<SelectionKey> keys = selector.selectedKeys();
    Iterator<SelectionKey> iter = keys.iterator();

    while (iter.hasNext()) {
        SelectionKey key = iter.next();
        iter.remove();

        if (key.isAcceptable()) handleAccept(key);
        if (key.isReadable()) handleRead(key);
        if (key.isWritable()) handleWrite(key);
    }
}
```

**特徴**:

- **NIO (New I/O)**: `Channel` + `Buffer` + `Selector` の三位一体
- `configureBlocking(false)` でノンブロッキングモードに設定
- `SelectionKey` がイベント種別（`OP_ACCEPT`, `OP_READ`, `OP_WRITE`）を表す
- JDK 標準ライブラリのみで実装可能

</details>

<details>
<summary>C# 実装（async/await）</summary>

```csharp
// 言語レベルの非同期サポート
public async Task<string> FetchDataAsync(string url)
{
    using var client = new HttpClient();
    return await client.GetStringAsync(url);
}

// 複数の非同期タスクを並列実行
var tasks = urls.Select(url => FetchDataAsync(url));
var results = await Task.WhenAll(tasks);
```

**特徴**:

- `async` / `await` が言語機能として組み込み
- コンパイラがステートマシンを自動生成
- 内部的に IOCP (I/O Completion Ports) を使用
- 低レベルの Selector を意識する必要がない

</details>

<details>
<summary>Python 実装（Selector + Reactor）</summary>

```python
import selectors

class PizzaReactor:
    def __init__(self, port):
        self.selector = selectors.DefaultSelector()
        self.server_socket = socket.socket()
        self.server_socket.setblocking(False)
        self.server_socket.bind(('', port))
        self.server_socket.listen()
        self.selector.register(self.server_socket, selectors.EVENT_READ, self._on_accept)

    def _on_accept(self, sock):
        conn, addr = sock.accept()
        conn.setblocking(False)
        self.selector.register(conn, selectors.EVENT_READ, self._on_read)

    def _on_read(self, conn):
        data = conn.recv(1024)
        if data:
            self.selector.modify(conn, selectors.EVENT_WRITE,
                                 lambda c: self._on_write(c, data.decode()))

    def _on_write(self, conn, message):
        conn.send(f"Thank you for {message}!\n".encode())
        self.selector.modify(conn, selectors.EVENT_READ, self._on_read)

    def run(self):
        while True:
            events = self.selector.select()
            for key, mask in events:
                callback = key.data
                callback(key.fileobj)
```

**特徴**:

- `selectors.DefaultSelector` が OS 最適な多重化を自動選択
- コールバックパターン: `register` → `select` → コールバック呼び出し
- 状態遷移: ACCEPT → READ → WRITE → READ → ...
- 最も低レベルな Reactor パターンの実装

</details>

## 6.4 比較分析

### 抽象度のスペクトラム

```
最高レベル  ┌────────────────────────────────┐
            │ Haskell: GHC が自動管理        │ ← ブロッキング API で OK
            │ C#: async/await (言語機能)     │
            ├────────────────────────────────┤
高レベル    │ Rust: tokio async/await        │
            │ F#: async { } ワークフロー      │
            │ Clojure: future / core.async   │
            ├────────────────────────────────┤
中レベル    │ Java: NIO Selector + Channel   │
            │ Scala: Java NIO ラッパー       │
            ├────────────────────────────────┤
低レベル    │ Python: selectors + コールバック │ ← 手動 Reactor 実装
            └────────────────────────────────┘
```

### イベントループの利点と欠点

| 利点 | 欠点 |
|------|------|
| ロック不要（単一スレッド実行） | コールバック地獄のリスク |
| 軽量（接続あたりのメモリ最小） | CPU バウンド処理で全体がブロック |
| スケーラブル（C10K 問題を解決） | デバッグが困難 |
| 予測可能な実行順序 | 複雑な状態管理 |

### OS レベルの I/O 多重化

| システムコール | OS | 特徴 |
|-------------|-----|------|
| `select` | 全 OS | 古典的。FD 数に上限あり（1024） |
| `poll` | Linux, macOS | `select` の改良。FD 数制限なし |
| `epoll` | Linux | 高性能。イベント通知方式 |
| `kqueue` | macOS, BSD | epoll 相当。macOS 標準 |
| `IOCP` | Windows | 完了通知方式。.NET の基盤 |

各言語のランタイムやライブラリが、OS に応じて最適な多重化を自動選択します。

## 6.5 実践的な選択指針

### 高負荷サーバーに適した言語

**最も適している**:

- **Rust (tokio)** — ゼロコスト async/await + epoll/kqueue 統合。メモリ効率が最高
- **Java (NIO)** — 成熟した NIO フレームワーク。Netty などの実績あるライブラリ

**生産性が高い**:

- **C#** — 言語レベルの async/await。ASP.NET で大規模 Web サーバーの実績
- **Haskell** — GHC が自動管理。開発者は非同期を意識しない

**プロトタイピング**:

- **Python** — `asyncio` で素早くプロトタイプ。ただし CPU バウンドには不向き
- **Clojure** — `core.async` で CSP モデルの非同期設計

### コールバック地獄の回避

| 言語 | 解決策 |
|------|--------|
| Python | `asyncio` + `async/await`（Part VII で詳述） |
| Java | `CompletableFuture` チェーン / Virtual Thread |
| C# | `async/await`（言語機能） |
| Rust | `async/await` + `tokio` |
| Haskell | `do` 記法（モナド合成） |
| Clojure | `go` ブロック（CSP モデル） |
| Scala | `for` 内包表記（モナド合成） |
| F# | `async { }` 計算式 |

## 6.6 まとめ

### 言語横断的な学び

1. **ノンブロッキング I/O は C10K 問題の解法** — 少数スレッドで大量接続を処理
2. **Reactor パターンは普遍的** — 全言語で同じ設計思想だが、抽象度が異なる
3. **イベントループはロック不要** — 単一スレッド実行で競合状態を回避
4. **抽象度と制御のトレードオフ** — Haskell（全自動）vs Python（手動 Reactor）
5. **OS の多重化 API が基盤** — epoll/kqueue/IOCP を言語ランタイムが抽象化

### 各言語の個別記事

| 言語 | 個別記事 |
|------|---------|
| Python | [Part VI - ノンブロッキング I/O](../python/part-6.md) |
| Java | [Part VI - ノンブロッキング I/O](../java/part-6.md) |
| C# | [Part VI - ノンブロッキング I/O](../csharp/part-6.md) |
| Scala | [Part VI - ノンブロッキング I/O](../scala/part-6.md) |
| F# | [Part VI - ノンブロッキング I/O](../fsharp/part-6.md) |
| Rust | [Part VI - ノンブロッキング I/O](../rust/part-6.md) |
| Haskell | [Part VI - ノンブロッキング I/O](../haskell/part-6.md) |
| Clojure | [Part VI - ノンブロッキング I/O](../clojure/part-6.md) |
