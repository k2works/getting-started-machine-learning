---
type: Article
title: "Part V: 同期と排他制御"
description: "並行処理の最大の課題は共有状態の安全な管理です。"
tags: [article, grokking-concurrency, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Part V: 同期と排他制御

## 5.1 はじめに

並行処理の最大の課題は**共有状態の安全な管理**です。本章では、複数のスレッドが同じデータに同時にアクセスする際に発生する**競合状態（Race Condition）**と、それを防ぐ**ロック**、**セマフォ**、**STM** を学びます。銀行口座の送金を題材に、8 つの言語がどのように排他制御を実現するかを比較します。

### 競合状態とは

```
Thread A: balance を読む (1000)
                                    Thread B: balance を読む (1000)
Thread A: balance = 1000 + 100      Thread B: balance = 1000 + 200
Thread A: balance に 1100 を書く
                                    Thread B: balance に 1200 を書く
結果: 1200（本来は 1300 であるべき）
```

2 つのスレッドが同じ変数を同時に読み書きすると、一方の更新が失われます。

## 5.2 共通の本質

### 排他制御の 2 つのアプローチ

8 言語の実装は、排他制御のアプローチで大きく 2 つに分かれます：

| アプローチ | 言語 | 仕組み |
|-----------|------|--------|
| **ロックベース** | Python, Java, C#, Scala, F#, Rust | 明示的にロックを取得・解放 |
| **トランザクショナルメモリ (STM)** | Haskell, Clojure | トランザクション内で自動的に整合性を保証 |

### デッドロックの 4 条件

デッドロックは以下の 4 条件が**すべて**成立したとき発生します：

1. **相互排他** — リソースは 1 スレッドのみが使用可能
2. **保持と待機** — ロックを保持しつつ別のロックを待つ
3. **非横取り** — 他スレッドのロックを強制的に奪えない
4. **循環待ち** — スレッド間に循環的な待ちの連鎖がある

### 銀行口座の送金問題

```
Account A (1000) → 送金 500 → Account B (1000)

正しい結果: A = 500, B = 1500（合計 = 2000）

問題: 送金中に別のスレッドが同時に送金すると、合計が 2000 にならない
```

## 5.3 言語別実装比較

### ロックベースの排他制御

#### マルチパラダイム言語

<details>
<summary>Rust 実装（Mutex + Arc）</summary>

```rust
use std::sync::{Arc, Mutex};

pub struct BankAccount {
    id: u32,
    balance: Mutex<i64>,
}

impl BankAccount {
    pub fn new(id: u32, initial_balance: i64) -> Self {
        BankAccount {
            id,
            balance: Mutex::new(initial_balance),
        }
    }

    pub fn deposit(&self, amount: i64) {
        let mut balance = self.balance.lock().unwrap();
        *balance += amount;
    }

    pub fn withdraw(&self, amount: i64) -> bool {
        let mut balance = self.balance.lock().unwrap();
        if *balance >= amount {
            *balance -= amount;
            true
        } else {
            false
        }
    }
}

// デッドロック回避: ID 順でロック取得
pub fn transfer(from: &BankAccount, to: &BankAccount, amount: i64) -> bool {
    let (first, second, from_is_first) = if from.id < to.id {
        (from, to, true)
    } else {
        (to, from, false)
    };

    let mut first_guard = first.balance.lock().unwrap();
    let mut second_guard = second.balance.lock().unwrap();

    let (from_bal, to_bal) = if from_is_first {
        (&mut *first_guard, &mut *second_guard)
    } else {
        (&mut *second_guard, &mut *first_guard)
    };

    if *from_bal >= amount {
        *from_bal -= amount;
        *to_bal += amount;
        true
    } else {
        false
    }
}
```

**特徴**:

- `Mutex<T>` がデータとロックを一体化（ロックなしではデータにアクセス不可能）
- ガードパターン: `MutexGuard` がスコープを抜けると自動解放
- `Arc` で複数スレッド間の所有権を共有
- コンパイル時にデータ競合を防止

</details>

<details>
<summary>Scala 実装（synchronized）</summary>

```scala
class BankAccount(val id: Int, initialBalance: Int):
  private val lock = new Object
  private var _balance: Int = initialBalance

  def balance: Int = lock.synchronized { _balance }

  def deposit(amount: Int): Unit = lock.synchronized {
    _balance += amount
  }

  def withdraw(amount: Int): Boolean = lock.synchronized {
    if _balance >= amount then
      _balance -= amount
      true
    else false
  }

// デッドロック回避: identityHashCode 順でロック
def transfer(from: BankAccount, to: BankAccount, amount: Int): Boolean =
  val (first, second) = if System.identityHashCode(from) < System.identityHashCode(to)
    then (from, to) else (to, from)
  first.lock.synchronized {
    second.lock.synchronized {
      if from._balance >= amount then
        from._balance -= amount
        to._balance += amount
        true
      else false
    }
  }
```

</details>

<details>
<summary>F# 実装（lock 式）</summary>

```fsharp
type BankAccount(id: int, initialBalance: int) =
    let lockObj = obj()
    let mutable balance = initialBalance

    member _.Balance = lock lockObj (fun () -> balance)

    member _.Deposit(amount: int) =
        lock lockObj (fun () -> balance <- balance + amount)

    member _.Withdraw(amount: int) =
        lock lockObj (fun () ->
            if balance >= amount then
                balance <- balance - amount
                true
            else false)

// デッドロック回避: ハッシュ順でロック
let transfer (from: BankAccount) (toAccount: BankAccount) (amount: int) =
    let fromHash = RuntimeHelpers.GetHashCode(from)
    let toHash = RuntimeHelpers.GetHashCode(toAccount)
    let (first, second) = if fromHash < toHash then (from, toAccount) else (toAccount, from)
    lock first.Lock (fun () ->
        lock second.Lock (fun () ->
            if from.Balance >= amount then
                from.Withdraw(amount) |> ignore
                toAccount.Deposit(amount)
                true
            else false))
```

</details>

#### OOP + 並行処理ライブラリ言語

<details>
<summary>Java 実装（ReentrantLock）</summary>

```java
public class BankAccount {
    private final ReentrantLock lock = new ReentrantLock();
    private int balance;

    public void deposit(int amount) {
        lock.lock();
        try {
            balance += amount;
        } finally {
            lock.unlock();
        }
    }

    // デッドロック回避: identityHashCode 順でロック
    public static boolean transfer(BankAccount from, BankAccount to, int amount) {
        BankAccount first = System.identityHashCode(from) < System.identityHashCode(to)
            ? from : to;
        BankAccount second = first == from ? to : from;

        first.lock.lock();
        try {
            second.lock.lock();
            try {
                if (from.balance >= amount) {
                    from.balance -= amount;
                    to.balance += amount;
                    return true;
                }
                return false;
            } finally {
                second.lock.unlock();
            }
        } finally {
            first.lock.unlock();
        }
    }
}
```

**特徴**: `try-finally` で確実にロックを解放。`InterruptedException` の適切なハンドリング。

</details>

<details>
<summary>C# 実装（lock + Monitor）</summary>

```csharp
public class BankAccount {
    private readonly object _lock = new();
    private int _balance;

    public void Deposit(int amount) {
        lock (_lock) { _balance += amount; }
    }

    public static bool Transfer(BankAccount from, BankAccount to, int amount) {
        var first = RuntimeHelpers.GetHashCode(from) < RuntimeHelpers.GetHashCode(to)
            ? from : to;
        var second = first == from ? to : from;

        lock (first._lock) {
            lock (second._lock) {
                if (from._balance >= amount) {
                    from._balance -= amount;
                    to._balance += amount;
                    return true;
                }
                return false;
            }
        }
    }
}
```

**特徴**: `lock` ステートメントが `Monitor.Enter`/`Monitor.Exit` を自動管理。Java より簡潔。

</details>

<details>
<summary>Python 実装（threading.Lock）</summary>

```python
from threading import Lock

class BankAccount:
    def __init__(self, balance=0):
        self.lock = Lock()
        self.balance = balance

    def deposit(self, amount):
        with self.lock:
            self.balance += amount

    def withdraw(self, amount):
        with self.lock:
            if self.balance >= amount:
                self.balance -= amount
                return True
            return False

# デッドロック回避: id() 順でロック
def transfer(from_acc, to_acc, amount):
    first, second = sorted([from_acc, to_acc], key=id)
    with first.lock:
        with second.lock:
            if from_acc.balance >= amount:
                from_acc.balance -= amount
                to_acc.balance += amount
                return True
            return False
```

**特徴**: `with` 文（コンテキストマネージャ）で自動的にロックを解放。

</details>

### STM（トランザクショナルメモリ）

#### 関数型ファースト言語

<details>
<summary>Haskell 実装（STM + TVar）</summary>

```haskell
import Control.Concurrent.STM

data BankAccount = BankAccount
    { accountId      :: Int
    , accountBalance :: TVar Int
    }

newAccount :: Int -> Int -> IO BankAccount
newAccount aid balance = do
    tvar <- newTVarIO balance
    return $ BankAccount aid tvar

deposit :: BankAccount -> Int -> STM ()
deposit account amount =
    modifyTVar' (accountBalance account) (+ amount)

withdraw :: BankAccount -> Int -> STM Bool
withdraw account amount = do
    bal <- readTVar (accountBalance account)
    if bal >= amount
        then do
            writeTVar (accountBalance account) (bal - amount)
            return True
        else return False

-- デッドロックは原理的に発生しない
transfer :: BankAccount -> BankAccount -> Int -> IO Bool
transfer from to amount = atomically $ do
    fromBal <- readTVar (accountBalance from)
    if fromBal >= amount
        then do
            modifyTVar' (accountBalance from) (subtract amount)
            modifyTVar' (accountBalance to) (+ amount)
            return True
        else return False
```

**特徴**:

- **ロック順序の管理が不要** — `atomically` がトランザクションを自動管理
- **デッドロックフリー** — 競合時は自動リトライ
- **合成可能** — STM アクションを自由に組み合わせ可能
- `TVar` は STM コンテキスト内でのみ読み書き可能

</details>

<details>
<summary>Clojure 実装（ref + dosync）</summary>

```clojure
(defn create-account [id balance]
  {:id id
   :balance (ref balance)})

(defn deposit! [account amount]
  (dosync
    (alter (:balance account) + amount)))

(defn withdraw! [account amount]
  (dosync
    (let [bal @(:balance account)]
      (if (>= bal amount)
        (do (alter (:balance account) - amount) true)
        false))))

;; デッドロックは原理的に発生しない
(defn transfer! [from to amount]
  (dosync
    (if (>= @(:balance from) amount)
      (do
        (alter (:balance from) - amount)
        (alter (:balance to) + amount)
        true)
      false)))
```

**特徴**:

- `ref` は協調的な更新が必要な値に使用
- `dosync` がトランザクション境界を定義
- `alter` でトランザクション内の値を更新
- 競合時は自動リトライ

</details>

## 5.4 比較分析

### 排他制御メカニズムの比較

| 言語 | メカニズム | デッドロック防止 | ロック順序管理 |
|------|----------|---------------|-------------|
| Python | `Lock` + `with` | 手動（id 順） | 必要 |
| Java | `ReentrantLock` + `try-finally` | 手動（hash 順） | 必要 |
| C# | `lock` ステートメント | 手動（hash 順） | 必要 |
| Scala | `synchronized` | 手動（hash 順） | 必要 |
| F# | `lock` 式 | 手動（hash 順） | 必要 |
| Rust | `Mutex<T>` + ガード | 手動（id 順） | 必要 |
| Haskell | **STM** (`atomically`) | **自動** | **不要** |
| Clojure | **STM** (`dosync`) | **自動** | **不要** |

### 安全性の保証レベル

```
最高    ┌──────────────────────────────────┐
        │ Rust: コンパイル時にデータ競合を防止 │
        │ Haskell STM: デッドロックフリー    │
        ├──────────────────────────────────┤
高い    │ Clojure STM: 不変データ + dosync   │
        │ F#: 不変性デフォルト              │
        ├──────────────────────────────────┤
中程度  │ Java: ReentrantLock（手動管理）    │
        │ C#: lock（構文サポート）           │
        │ Scala: synchronized              │
        ├──────────────────────────────────┤
低い    │ Python: Lock（GIL は部分的保護）   │
        └──────────────────────────────────┘
```

### デッドロック回避戦略の比較

| 戦略 | 言語 | 特徴 |
|------|------|------|
| ロック順序固定 | Python, Java, C#, Scala, F#, Rust | ハッシュ/ID でソートし、常に同じ順序でロック |
| STM（楽観的並行制御） | Haskell, Clojure | トランザクション実行→競合検出→自動リトライ |
| 型システム | Rust | `Send`/`Sync` トレイトでコンパイル時検証 |

### テスト戦略

全言語で共通のテストパターン：

```
1. 2 つの口座を作成（各 1000、合計 2000）
2. 100 以上の並行スレッドで双方向送金
3. 全スレッド完了後、合計が 2000 であることを検証
4. タイムアウト内に完了することを検証（デッドロックなし）
```

## 5.5 実践的な選択指針

### デッドロックを避けたい場合

- **Haskell STM** — 原理的にデッドロックが発生しない。トランザクション内で自由に複数の変数を操作
- **Clojure STM** — `dosync` 内で `ref` を自由に操作。不変データ構造との相性が良い

### パフォーマンスが重要な場合

- **Rust** — ゼロコスト抽象化。`Mutex<T>` のガードパターンでオーバーヘッド最小
- **Java** — `ReentrantLock` の `tryLock` でノンブロッキングなロック取得が可能

### コードの簡潔さを重視する場合

- **C#** — `lock` ステートメントが最も簡潔
- **Python** — `with` 文による自動ロック管理

## 5.6 まとめ

### 言語横断的な学び

1. **競合状態は普遍的** — どの言語でも共有状態の同時アクセスは危険
2. **ロック vs STM** — ロックベースは手動管理が必要、STM は自動だが楽観的再実行のコストがある
3. **デッドロック回避は設計の問題** — ロック順序を固定するか、STM でトランザクション化するか
4. **Rust は唯一のコンパイル時保証** — `Send`/`Sync` でデータ競合を型レベルで防止
5. **テストは「合計保存則」** — 並行送金後に合計が不変であることが正しさの証明

### 次のステップ

[Part VI: ノンブロッキング I/O](./part-6-ch10-11-nonblocking-io.md) では、ロックを使わずに並行処理を実現するイベント駆動モデルを学びます。

### 各言語の個別記事

| 言語 | 個別記事 |
|------|---------|
| Python | [Part V - 同期と排他制御](../python/part-5.md) |
| Java | [Part V - 同期と排他制御](../java/part-5.md) |
| C# | [Part V - 同期と排他制御](../csharp/part-5.md) |
| Scala | [Part V - 同期と排他制御](../scala/part-5.md) |
| F# | [Part V - 同期と排他制御](../fsharp/part-5.md) |
| Rust | [Part V - 同期と排他制御](../rust/part-5.md) |
| Haskell | [Part V - 同期と排他制御](../haskell/part-5.md) |
| Clojure | [Part V - 同期と排他制御](../clojure/part-5.md) |
