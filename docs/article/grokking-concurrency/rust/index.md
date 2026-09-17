# Grokking Concurrency - Rust 版

本シリーズは「Grokking Concurrency」（Kirill Bobrov 著）の学習コンパニオンとして、並行処理プログラミングの概念を Rust で実装しながら日本語で解説します。

---

## 対象読者

- Rust の基礎知識があり、並行処理に興味がある開発者
- 所有権と借用を活用した安全な並行処理を学びたい方
- マルチスレッド、非同期プログラミングを理解したいエンジニア
- Rayon や Tokio を使った並列処理に興味がある方

---

## 記事一覧

### [Part I: 並行処理の基礎](part-1.md)

逐次処理と並行処理の違い、基本概念を学びます。

| 章 | トピック |
|----|----------|
| 第2章 | 逐次処理、パスワードクラッキング例 |

**キーワード**: 逐次処理、Iterator、Option/Result

---

### [Part II: プロセスとスレッド](part-2.md)

スレッドの基本と所有権の関係を学びます。

| 章 | トピック |
|----|----------|
| 第4章 | スレッド、JoinHandle、Send/Sync |
| 第5章 | 並列処理、Rayon |

**キーワード**: std::thread、Send、Sync、Rayon

---

### [Part III: マルチタスキングとスケジューリング](part-3.md)

マルチタスキングとイベント駆動の概念を学びます。

| 章 | トピック |
|----|----------|
| 第6章 | マルチタスキング、Condvar、ゲームループ |

**キーワード**: Condvar、Mutex、イベント駆動

---

### [Part IV: タスク分解と並列パターン](part-4.md)

並列処理のデザインパターンを学びます。

| 章 | トピック |
|----|----------|
| 第7章 | タスク分解、Fork/Join、パイプライン |

**キーワード**: Rayon、mpsc、Pipeline

---

### [Part V: 同期と排他制御](part-5.md)

並行処理における同期問題と解決策を学びます。

| 章 | トピック |
|----|----------|
| 第8章 | Mutex、Arc、デッドロック回避 |

**キーワード**: Mutex、Arc、MutexGuard

---

### [Part VI: ノンブロッキング I/O](part-6.md)

ノンブロッキング I/O と非同期処理の基礎を学びます。

| 章 | トピック |
|----|----------|
| 第10章 | ブロッキング vs ノンブロッキング I/O |

**キーワード**: async、Future、Tokio

---

### [Part VII: 非同期プログラミング](part-7.md)

Rust の async/await を使った非同期プログラミングを学びます。

| 章 | トピック |
|----|----------|
| 第12章 | Future、async/await、Tokio |

**キーワード**: async、await、tokio

---

### [Part VIII: 分散並列処理](part-8.md)

MapReduce パターンと分散処理を学びます。

| 章 | トピック |
|----|----------|
| 第13章 | MapReduce、ワードカウント |

**キーワード**: MapReduce、Rayon、並列イテレータ

---

## 使用ライブラリ

| ライブラリ | 用途 | 対応章 |
|------------|------|--------|
| std::thread | スレッド操作 | Part II-V |
| std::sync | 同期プリミティブ | Part III, V |
| rayon | データ並列処理 | Part II, IV, VIII |
| tokio | 非同期ランタイム | Part VI, VII |
| sha2 | ハッシュ計算 | Part I, II |

---

## リポジトリ構成

```
grokking_concurrency/
├── apps/rust/                     # Rust サンプルコード
│   ├── Cargo.toml                 # プロジェクト設定
│   └── src/
│       ├── lib.rs                 # モジュール定義
│       ├── ch02.rs                # 逐次処理
│       ├── ch04.rs                # スレッド基礎
│       ├── ch05.rs                # 並列パスワードクラッカー
│       ├── ch06.rs                # ゲームループ
│       ├── ch07.rs                # Fork/Join, Pipeline
│       ├── ch08.rs                # 銀行口座（同期）
│       └── ch13.rs                # MapReduce
└── docs/article/rust/             # 解説記事
    ├── index.md                   # この記事
    ├── part-1.md                  # Part I
    └── ...                        # 以降の Part
```

---

## Rust と並行処理

Rust は**所有権システム**により、コンパイル時にデータ競合を防ぎます。

### スレッド

```rust
use std::thread;

fn main() {
    let handle = thread::spawn(|| {
        println!("Hello from thread!");
    });
    handle.join().unwrap();
}
```

### Arc と Mutex

```rust
use std::sync::{Arc, Mutex};
use std::thread;

fn main() {
    let counter = Arc::new(Mutex::new(0));
    let mut handles = vec![];

    for _ in 0..10 {
        let counter = Arc::clone(&counter);
        let handle = thread::spawn(move || {
            let mut num = counter.lock().unwrap();
            *num += 1;
        });
        handles.push(handle);
    }

    for handle in handles {
        handle.join().unwrap();
    }

    println!("Result: {}", *counter.lock().unwrap());
}
```

### Rayon による並列処理

```rust
use rayon::prelude::*;

fn main() {
    let sum: i32 = (1..=1000)
        .into_par_iter()
        .map(|x| x * 2)
        .sum();
    println!("Sum: {}", sum);
}
```

### 非同期プログラミング

```rust
use tokio;

#[tokio::main]
async fn main() {
    let result = fetch_data().await;
    println!("{}", result);
}

async fn fetch_data() -> String {
    tokio::time::sleep(tokio::time::Duration::from_secs(1)).await;
    "Data fetched".to_string()
}
```

---

## 主要な概念

### 所有権と並行処理

| トレイト | 説明 |
|----------|------|
| Send | 別スレッドに所有権を移動可能 |
| Sync | 複数スレッドから参照可能 |

### 同期プリミティブ

| 型 | 説明 |
|----|------|
| Mutex<T> | 排他制御によるデータ保護 |
| RwLock<T> | 読み書きロック |
| Arc<T> | スレッド間での共有所有権 |
| Condvar | 条件変数 |

### 並行処理の利点

1. **コンパイル時の安全性** - データ競合をコンパイル時に検出
2. **ゼロコスト抽象化** - 安全性を犠牲にしないパフォーマンス
3. **表現力** - 所有権により意図を明確に表現
4. **エコシステム** - Rayon、Tokio 等の強力なライブラリ

---

## 参考資料

- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency) - 原著
- [The Rust Programming Language - Fearless Concurrency](https://doc.rust-lang.org/book/ch16-00-concurrency.html)
- [Rust 公式ドキュメント - std::thread](https://doc.rust-lang.org/std/thread/)
- [Rayon](https://github.com/rayon-rs/rayon)
- [Tokio](https://tokio.rs/)
