# Grokking Concurrency - Haskell 版

本シリーズは「Grokking Concurrency」（Kirill Bobrov 著）の学習コンパニオンとして、並行処理プログラミングの概念を Haskell で実装しながら日本語で解説します。

---

## 対象読者

- Haskell の基礎知識があり、並行処理に興味がある開発者
- STM（Software Transactional Memory）を学びたい方
- 純粋関数型言語での並行処理を理解したいエンジニア
- async ライブラリや forkIO を使った並列処理に興味がある方

---

## 記事一覧

### [Part I: 並行処理の基礎](part-1.md)

逐次処理と並行処理の違い、基本概念を学びます。

| 章 | トピック |
|----|----------|
| 第2章 | 逐次処理、パスワードクラッキング例 |

**キーワード**: 逐次処理、純粋関数、Maybe モナド

---

### [Part II: プロセスとスレッド](part-2.md)

スレッドの基本と async ライブラリを学びます。

| 章 | トピック |
|----|----------|
| 第4章 | forkIO、async、軽量スレッド |
| 第5章 | 並列処理、mapConcurrently |

**キーワード**: forkIO、async、Async

---

### [Part III: マルチタスキングとスケジューリング](part-3.md)

マルチタスキングと STM を学びます。

| 章 | トピック |
|----|----------|
| 第6章 | TVar、STM、ゲームループ |

**キーワード**: STM、TVar、atomically

---

### [Part IV: タスク分解と並列パターン](part-4.md)

並列処理のデザインパターンを学びます。

| 章 | トピック |
|----|----------|
| 第7章 | タスク分解、Fork/Join、パイプライン |

**キーワード**: mapConcurrently、TQueue、Pipeline

---

### [Part V: 同期と排他制御](part-5.md)

STM によるデッドロックフリーな同期を学びます。

| 章 | トピック |
|----|----------|
| 第8章 | STM、TVar、アトミックトランザクション |

**キーワード**: STM、atomically、retry

---

### [Part VI: ノンブロッキング I/O](part-6.md)

ノンブロッキング I/O と非同期処理の基礎を学びます。

| 章 | トピック |
|----|----------|
| 第10章 | IO モナド、非同期処理 |

**キーワード**: IO、async、concurrently

---

### [Part VII: 非同期プログラミング](part-7.md)

Haskell の async ライブラリを詳しく学びます。

| 章 | トピック |
|----|----------|
| 第12章 | async、wait、cancel、race |

**キーワード**: Async、race、concurrently

---

### [Part VIII: 分散並列処理](part-8.md)

MapReduce パターンと並列処理を学びます。

| 章 | トピック |
|----|----------|
| 第13章 | MapReduce、ワードカウント |

**キーワード**: MapReduce、mapConcurrently

---

## 使用ライブラリ

| ライブラリ | 用途 | 対応章 |
|------------|------|--------|
| base | 基本ライブラリ | 全章 |
| async | 非同期処理 | Part II-VIII |
| stm | Software Transactional Memory | Part III, V |
| cryptohash-sha256 | ハッシュ計算 | Part I, II |
| containers | Map、Set | Part IV, VIII |

---

## リポジトリ構成

```
grokking_concurrency/
├── apps/haskell/                  # Haskell サンプルコード
│   ├── grokking-concurrency-haskell.cabal
│   ├── stack.yaml
│   └── src/
│       ├── Ch02/PasswordCracker.hs
│       ├── Ch04/ThreadBasics.hs
│       ├── Ch05/PasswordCrackerParallel.hs
│       ├── Ch06/GameLoop.hs
│       ├── Ch07/VoteCounter.hs
│       ├── Ch07/Pipeline.hs
│       ├── Ch08/BankAccount.hs
│       └── Ch13/WordCount.hs
└── docs/article/haskell/          # 解説記事
    ├── index.md
    ├── part-1.md
    └── ...
```

---

## Haskell と並行処理

Haskell は**純粋関数型言語**であり、並行処理に独自のアプローチを提供します。

### forkIO（軽量スレッド）

```haskell
import Control.Concurrent (forkIO)

main :: IO ()
main = do
    _ <- forkIO $ putStrLn "Hello from thread!"
    putStrLn "Hello from main!"
```

### async ライブラリ

```haskell
import Control.Concurrent.Async

main :: IO ()
main = do
    result <- async $ return (1 + 1)
    value <- wait result
    print value
```

### STM（Software Transactional Memory）

```haskell
import Control.Concurrent.STM

main :: IO ()
main = do
    counter <- newTVarIO 0
    atomically $ modifyTVar' counter (+1)
    value <- readTVarIO counter
    print value  -- 1
```

### 並列実行

```haskell
import Control.Concurrent.Async

main :: IO ()
main = do
    results <- mapConcurrently id
        [return 1, return 2, return 3]
    print results  -- [1, 2, 3]
```

---

## 主要な概念

### IO モナドと純粋関数

| 種類 | 説明 |
|------|------|
| 純粋関数 | 副作用なし、参照透過性 |
| IO a | 副作用を持つ計算 |
| STM a | トランザクショナルな計算 |

### STM の特徴

| 機能 | 説明 |
|------|------|
| atomically | トランザクションを実行 |
| retry | 条件が満たされるまで待機 |
| orElse | 代替トランザクション |
| TVar | トランザクショナル変数 |

### 並行処理の利点

1. **デッドロックフリー** - STM による自動リトライ
2. **型安全性** - コンパイル時の検証
3. **合成可能性** - 小さな関数を組み合わせる
4. **純粋性** - 副作用の明示的な管理

---

## 参考資料

- [Grokking Concurrency](https://www.manning.com/books/grokking-concurrency) - 原著
- [Parallel and Concurrent Programming in Haskell](https://simonmar.github.io/pages/pcph.html)
- [async package](https://hackage.haskell.org/package/async)
- [stm package](https://hackage.haskell.org/package/stm)
