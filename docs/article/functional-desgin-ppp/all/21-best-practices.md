---
type: Article
title: "第21章: ベストプラクティス — 6言語統合ガイド"
description: "本章は、これまでの 20 章で学んだ関数型デザインパターンを実践で活かすためのベストプラクティスをまとめます。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第21章: ベストプラクティス — 6言語統合ガイド

## 1. はじめに

本章は、これまでの 20 章で学んだ関数型デザインパターンを実践で活かすための**ベストプラクティス**をまとめます。6 言語に共通する原則と、各言語固有のイディオムを整理し、「よいソフトウェア」を関数型で実現するための指針を提供します。

## 2. 共通の原則

### 2.1 データ中心設計

すべての言語で共通する最も重要な原則です。

```
1. まずデータ構造を定義する
2. データを変換する関数を書く
3. 関数をパイプラインで合成する
```

| 言語 | データ定義 | 変換 | 合成 |
|------|----------|------|------|
| Clojure | マップ | 関数 | `->` / `->>` |
| Scala | case class | メソッド / 関数 | `map` / `flatMap` |
| Elixir | 構造体 | 関数 | `\|>` |
| F# | レコード | 関数 | `\|>` |
| Haskell | data / newtype | 関数 | `.` / `$` |
| Rust | struct | impl / 関数 | メソッドチェーン / `iter()` |

### 2.2 純粋関数の優先

```
純粋関数 = 同じ入力に対して常に同じ出力を返し、副作用がない
```

<details>
<summary>純粋関数 vs 非純粋関数の例</summary>

```clojure
;; 純粋（テスト容易）
(defn calculate-total [items tax-rate]
  (* (reduce + (map :price items)) (+ 1 tax-rate)))

;; 非純粋（テスト困難）
(defn calculate-total-impure [items]
  (let [tax-rate (fetch-tax-rate!)  ;; 副作用
        total (* (reduce + (map :price items)) (+ 1 tax-rate))]
    (save-to-db! total)              ;; 副作用
    total))
```

```haskell
-- 純粋
calculateTotal :: [Item] -> Double -> Double
calculateTotal items taxRate = sum (map itemPrice items) * (1 + taxRate)

-- 副作用は型で明示
calculateTotalIO :: [Item] -> IO Double
calculateTotalIO items = do
    taxRate <- fetchTaxRate
    let total = sum (map itemPrice items) * (1 + taxRate)
    saveToDB total
    return total
```

</details>

### 2.3 副作用の分離（Functional Core / Imperative Shell）

```
Functional Core:  純粋なビジネスロジック（テスト容易）
Imperative Shell: I/O・DB・外部 API（薄いレイヤー）
```

| 言語 | Core の表現 | Shell の表現 |
|------|-----------|-------------|
| Clojure | 純粋関数 | atom / Agent / I/O |
| Scala | 純粋関数 / IO モナド | Future / Side effects |
| Elixir | 純粋関数 | GenServer / Agent |
| F# | 純粋関数 | Async / Task |
| Haskell | 純粋関数 | IO モナド |
| Rust | 純粋関数 | `Result<T, E>` / I/O |

### 2.4 テスト可能性の設計

<details>
<summary>依存性注入による副作用の分離</summary>

```clojure
;; Clojure: 関数を引数として渡す
(defn process-order [order fetch-price save-order]
  (let [total (reduce + (map #(fetch-price (:product-id %)) (:items order)))]
    (save-order (assoc order :total total))))

;; テスト
(deftest test-process-order
  (let [mock-fetch (constantly 100)
        mock-save identity]
    (is (= 200 (:total (process-order order mock-fetch mock-save))))))
```

```rust
// Rust: trait bounds で抽象化
pub fn process_order<P, S>(order: &Order, pricer: P, saver: S) -> Result<Order, String>
where
    P: Fn(&str) -> f64,
    S: Fn(&Order) -> Result<(), String>,
{
    let total: f64 = order.items.iter().map(|i| pricer(&i.product_id)).sum();
    let updated = Order { total, ..order.clone() };
    saver(&updated)?;
    Ok(updated)
}
```

```haskell
-- Haskell: 型クラスで抽象化
class Monad m => OrderProcessor m where
    fetchPrice :: ProductId -> m Double
    saveOrder  :: Order -> m ()

processOrder :: OrderProcessor m => Order -> m Order
processOrder order = do
    prices <- mapM (fetchPrice . productId) (items order)
    let total = sum prices
    let updated = order { orderTotal = total }
    saveOrder updated
    return updated
```

</details>

### 2.5 エラーハンドリング

| 言語 | 成功/失敗の型 | チェーン方法 |
|------|-----------|-----------|
| Clojure | `nil` / 例外 / `{:ok v}` | `some->` / `try-catch` |
| Scala | `Either[E, A]` / `Option[A]` | `flatMap` / `for` |
| Elixir | `{:ok, v}` / `{:error, e}` | `with` 式 |
| F# | `Result<'T, 'E>` / `Option<'T>` | `Result.bind` / パイプ |
| Haskell | `Either e a` / `Maybe a` | `>>=` / `do` 記法 |
| Rust | `Result<T, E>` / `Option<T>` | `?` 演算子 / `and_then` |

## 3. 言語固有のイディオム

### 3.1 Clojure: データリテラルとプロトコル

```clojure
;; データリテラルで直接表現
(def config
  {:db {:host "localhost" :port 5432}
   :cache {:ttl 3600}})

;; プロトコルで既存データに振る舞いを追加
(defprotocol Cacheable
  (cache-key [this])
  (ttl [this]))

(extend-type clojure.lang.PersistentHashMap
  Cacheable
  (cache-key [m] (hash m))
  (ttl [_] 3600))
```

### 3.2 Scala: given/using と型クラス

```scala
// コンテキストの自動提供
given Ordering[Product] with
  def compare(a: Product, b: Product): Int =
    a.price.compareTo(b.price)

def cheapest(products: List[Product])(using ord: Ordering[Product]): Product =
  products.min
```

### 3.3 Elixir: パイプラインと Stream

```elixir
# パイプラインで処理フローを明確に
result =
  data
  |> validate()
  |> transform()
  |> Enum.filter(&active?/1)
  |> Enum.map(&format/1)

# Stream で遅延評価
large_file
|> File.stream!()
|> Stream.map(&String.trim/1)
|> Stream.filter(&(&1 != ""))
|> Enum.take(100)
```

### 3.4 F#: パイプ演算子と計算式

```fsharp
// パイプ演算子で左から右へ
let result =
    data
    |> validate
    |> transform
    |> List.filter isActive
    |> List.map format

// 計算式で Result チェーン
let processOrder order = result {
    let! validated = validateOrder order
    let! priced = calculatePrice validated
    let! saved = saveOrder priced
    return saved
}
```

### 3.5 Haskell: newtype とプロパティテスト

```haskell
-- newtype でドメインプリミティブ
newtype Email = Email String
newtype UserId = UserId Int

-- 型クラスでプロパティを保証
prop_serializeRoundTrip :: User -> Bool
prop_serializeRoundTrip user =
    deserialize (serialize user) == Just user
```

### 3.6 Rust: 所有権とイテレータ

```rust
// 所有権を活かしたビルダーパターン
let order = OrderBuilder::new()
    .customer("Alice")
    .add_item(item1)
    .add_item(item2)
    .build()?;

// イテレータチェーンで効率的な処理
let total: f64 = orders
    .iter()
    .filter(|o| o.status == Status::Completed)
    .flat_map(|o| o.items.iter())
    .map(|i| i.price)
    .sum();
```

## 4. 比較分析

### 4.1 副作用分離の厳密さ

```
厳密 ←――――――――――――――――――→ 柔軟

Haskell    F#    Rust    Scala    Elixir    Clojure
├─IO モナド┤     │       │        │         │
           ├─CE──┤       │        │         │
                 ├─型──┤         │         │
                        ├─Effect─┤         │
                                  ├─Agent──┤
                                            ├─規約
```

### 4.2 コードの表現力

| 基準 | 最も表現力が高い | 理由 |
|------|----------------|------|
| データ変換パイプライン | Elixir / F# | `\|>` が最も直感的 |
| 型レベルの安全性 | Haskell | newtype + 型クラスで不正状態を排除 |
| 実用的なバランス | Scala / F# | OOP との調和 + 型安全性 |
| 動的な柔軟性 | Clojure | マップリテラルの自由度 |
| メモリ効率 | Rust | ゼロコスト抽象化 |

## 5. 実践的な選択指針

| プロジェクト特性 | 推奨アプローチ | 推奨言語 |
|---------------|-------------|---------|
| 高い信頼性が必要 | 純粋関数 + 型安全性 | Haskell, F# |
| 高い並行性が必要 | アクターモデル + 不変性 | Elixir, Scala |
| パフォーマンスが最重要 | ゼロコスト抽象化 | Rust |
| プロトタイピング | データリテラル + REPL | Clojure |
| エンタープライズ | マルチパラダイム | Scala, F# |

## 6. まとめ

関数型ベストプラクティスの核心は 3 つの原則に集約されます：

1. **データ中心**: まずデータ構造を定義し、次に変換関数を書く
2. **純粋性**: 副作用を分離し、ビジネスロジックを純粋関数で表現する
3. **合成可能性**: 小さな関数を合成して大きな機能を構築する

これらの原則はすべての関数型言語に共通であり、言語が変わっても**考え方**は同じです。

## 言語別個別記事

- [Clojure](../clojure/21-best-practices.md) | [Scala](../scala/21-best-practices.md) | [Elixir](../elixir/21-best-practices.md) | [F#](../fsharp/21-best-practices.md) | [Haskell](../haskell/21-best-practices.md) | [Rust](../rust/21-best-practices.md)
