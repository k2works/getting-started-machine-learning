---
type: Article
title: "第10章: Strategy パターン — 6言語統合ガイド"
description: "Strategy パターンは、アルゴリズムをカプセル化し、実行時に切り替えるための GoF パターンです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第10章: Strategy パターン — 6言語統合ガイド

## 1. はじめに

Strategy パターンは、アルゴリズムをカプセル化し、実行時に切り替えるための GoF パターンです。OOP ではインターフェースと実装クラスで表現しますが、関数型プログラミングでは**関数そのものが戦略**です。高階関数を使えば、戦略の切り替えは関数の引数を変えるだけで実現できます。

> **Elixir の読者へ**: Elixir 版は本章で「並行処理パターン」を扱っています。Elixir 固有のアクターモデルについては[コラム](#elixir-コラム並行処理パターン)を参照してください。

## 2. 共通の本質

### Strategy の構造

```
Context(strategy) → strategy(data) → result
```

OOP では Strategy インターフェースを実装するクラスを注入しますが、関数型では**関数を直接渡す**だけです。

### 典型的なユースケース

- **料金計算**: 通常・割引・会員・まとめ買い
- **ソートアルゴリズム**: バブル・クイック・マージ
- **フォーマット出力**: JSON・XML・CSV
- **配送料計算**: 通常・エクスプレス・無料

## 3. 言語別実装比較

### 3.1 戦略の表現方法

| 言語 | 戦略の表現 | コンテキストへの注入 |
|------|----------|-----------------|
| Clojure | 関数 / マルチメソッド | 引数として渡す |
| Scala | trait / 関数リテラル | 型パラメータ / 引数 |
| F# | 判別共用体 / 関数 | パイプライン / 引数 |
| Haskell | newtype でラップした関数 | 引数として渡す |
| Rust | trait object / クロージャ | ジェネリクス / `Box<dyn Fn>` |

### 3.2 料金計算の Strategy

<details>
<summary>Clojure: 関数ベースの戦略</summary>

```clojure
;; 戦略 = 単なる関数
(defn regular-pricing [amount] amount)
(defn discount-pricing [rate]
  (fn [amount] (* amount (- 1 rate))))
(defn member-pricing [amount]
  (* amount 0.9))
(defn bulk-pricing [threshold discount-rate]
  (fn [amount]
    (if (>= amount threshold)
      (* amount (- 1 discount-rate))
      amount)))

;; コンテキスト
(defn calculate-total [items pricing-strategy]
  (let [subtotal (reduce + (map :price items))]
    (pricing-strategy subtotal)))

;; 使用
(calculate-total items (discount-pricing 0.2))
```

</details>

<details>
<summary>Scala: trait + 関数型ハイブリッド</summary>

```scala
// trait ベース
trait PricingStrategy:
  def calculate(amount: Double): Double

object RegularPricing extends PricingStrategy:
  def calculate(amount: Double): Double = amount

class DiscountPricing(rate: Double) extends PricingStrategy:
  def calculate(amount: Double): Double = amount * (1 - rate)

// 関数ベース（より簡潔）
type PricingFn = Double => Double

val regular: PricingFn = identity
def discount(rate: Double): PricingFn = _ * (1 - rate)
def bulk(threshold: Double, rate: Double): PricingFn =
  amount => if amount >= threshold then amount * (1 - rate) else amount

// 戦略の合成
def compose(strategies: PricingFn*): PricingFn =
  strategies.reduce(_ andThen _)
```

</details>

<details>
<summary>Haskell: newtype + モノイド</summary>

```haskell
newtype PricingStrategy = PricingStrategy { runStrategy :: Double -> Double }

regularPricing :: PricingStrategy
regularPricing = PricingStrategy id

discountPricing :: Double -> PricingStrategy
discountPricing rate = PricingStrategy (\amount -> amount * (1 - rate))

memberPricing :: PricingStrategy
memberPricing = PricingStrategy (\amount -> amount * 0.9)

-- 戦略の合成（モノイド）
composeStrategies :: [PricingStrategy] -> PricingStrategy
composeStrategies = PricingStrategy . foldr ((.) . runStrategy) id

-- コンテキスト
calculateTotal :: PricingStrategy -> [Item] -> Double
calculateTotal strategy items =
    runStrategy strategy (sum (map itemPrice items))
```

Haskell では戦略を `newtype` でラップし、モノイドとして合成可能にしています。

</details>

<details>
<summary>F#: 判別共用体 + 関数</summary>

```fsharp
// 判別共用体版
type PricingStrategy =
    | Regular
    | Discount of rate: float
    | Member
    | Bulk of threshold: float * rate: float

let applyStrategy (strategy: PricingStrategy) (amount: float) : float =
    match strategy with
    | Regular -> amount
    | Discount rate -> amount * (1.0 - rate)
    | Member -> amount * 0.9
    | Bulk(threshold, rate) ->
        if amount >= threshold then amount * (1.0 - rate) else amount

// 関数版（より柔軟）
module FunctionalStrategy =
    let regular: float -> float = id
    let discount rate amount = amount * (1.0 - rate)
    let conditional pred strategy amount =
        if pred amount then strategy amount else amount
```

</details>

<details>
<summary>Rust: trait + クロージャ</summary>

```rust
// trait ベース
pub trait PricingStrategy {
    fn calculate(&self, amount: f64) -> f64;
}

pub struct RegularPricing;
impl PricingStrategy for RegularPricing {
    fn calculate(&self, amount: f64) -> f64 { amount }
}

pub struct DiscountPricing { pub rate: f64 }
impl PricingStrategy for DiscountPricing {
    fn calculate(&self, amount: f64) -> f64 {
        amount * (1.0 - self.rate)
    }
}

// クロージャベース（より簡潔）
pub fn calculate_total(
    items: &[Item],
    strategy: impl Fn(f64) -> f64,
) -> f64 {
    let subtotal: f64 = items.iter().map(|i| i.price).sum();
    strategy(subtotal)
}

// 使用
calculate_total(&items, |amount| amount * 0.8);
```

</details>

### 3.3 戦略の合成

複数の戦略を組み合わせて新しい戦略を作るパターンです。

| 言語 | 合成方法 | 例 |
|------|---------|-----|
| Clojure | `comp` | `(comp member-pricing (discount-pricing 0.1))` |
| Scala | `andThen` | `discount(0.1) andThen bulk(1000, 0.05)` |
| F# | `>>` | `discount 0.1 >> member` |
| Haskell | `.` | `runStrategy memberPricing . runStrategy (discountPricing 0.1)` |
| Rust | クロージャチェーン | `\|amount\| bulk(1000, 0.05, discount(0.1, amount))` |

### 3.4 条件付き戦略

実行時の条件に応じて戦略を選択するパターンです。

```clojure
;; Clojure
(defn select-strategy [customer-type]
  (case customer-type
    :regular regular-pricing
    :member member-pricing
    :vip    (discount-pricing 0.3)))
```

```scala
// Scala
def selectStrategy(customerType: CustomerType): PricingFn =
  customerType match
    case Regular => regular
    case Member  => discount(0.1)
    case VIP     => discount(0.3)
```

## 4. 比較分析

### 4.1 OOP Strategy vs 関数型 Strategy

| 観点 | OOP | 関数型 |
|------|-----|--------|
| 戦略の定義 | クラス | 関数 |
| 注入方法 | コンストラクタ / セッター | 関数引数 |
| 合成 | Composite パターン | 関数合成 |
| 状態 | フィールドに保持 | クロージャでキャプチャ |
| ボイラープレート | 多い（インターフェース + 実装クラス） | 少ない（関数のみ） |

### 4.2 trait/型クラス版 vs 関数版

静的型付け言語（Scala, Rust）では、trait/型クラス版と関数版の 2 つのアプローチを並列して提示しています：

- **trait/型クラス版**: 名前付きで文書化しやすく、複数メソッドの戦略に適する
- **関数版**: 簡潔で、単一の振る舞いの切り替えに適する

## 5. Elixir コラム：並行処理パターン

Elixir の第 10 章は Strategy パターンではなく、**並行処理パターン**を扱っています。Elixir/Erlang の BEAM VM は軽量プロセスとメッセージパッシングを基盤とし、GoF の Strategy とは異なるアプローチで「振る舞いの切り替え」を実現します。

### アクターモデルの基礎

```elixir
# プロセス間のメッセージパッシング
defmodule Counter do
  use Agent

  def start_link(initial) do
    Agent.start_link(fn -> initial end, name: __MODULE__)
  end

  def increment, do: Agent.update(__MODULE__, &(&1 + 1))
  def value, do: Agent.get(__MODULE__, & &1)
end
```

### GenServer による状態管理

```elixir
defmodule BankAccount do
  use GenServer

  def init(balance), do: {:ok, balance}

  def handle_call(:balance, _from, balance) do
    {:reply, balance, balance}
  end

  def handle_cast({:deposit, amount}, balance) do
    {:noreply, balance + amount}
  end
end
```

### Task による非同期処理

```elixir
tasks = Enum.map(urls, fn url ->
  Task.async(fn -> fetch(url) end)
end)

results = Enum.map(tasks, &Task.await/1)
```

Strategy パターンの「振る舞いの切り替え」は、Elixir ではプロセスの切り替えやメッセージパッシングのパターンとして表現されます。

## 6. まとめ

Strategy パターンは、関数型プログラミングで**最もシンプルに表現される** GoF パターンの一つです：

1. **関数が戦略**: 高階関数の引数として戦略を渡すだけ
2. **関数合成で戦略合成**: `comp` / `andThen` / `>>` / `.` で自然に合成
3. **クロージャで状態管理**: パラメータ化された戦略はクロージャで表現

## 言語別個別記事

- [Clojure](../clojure/10-strategy-pattern.md) | [Scala](../scala/10-strategy-pattern.md) | [Elixir](../elixir/10-concurrency-patterns.md) | [F#](../fsharp/10-strategy-pattern.md) | [Haskell](../haskell/10-strategy-pattern.md) | [Rust](../rust/10-strategy-pattern.md)
