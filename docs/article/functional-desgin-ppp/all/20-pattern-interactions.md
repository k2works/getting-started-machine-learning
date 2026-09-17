---
type: Article
title: "第20章: パターン間の相互作用 — 6言語統合ガイド"
description: "個々のデザインパターンを学んだ後、次のステップはパターンを組み合わせてより高度な設計を実現することです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第20章: パターン間の相互作用 — 6言語統合ガイド

## 1. はじめに

個々のデザインパターンを学んだ後、次のステップは**パターンを組み合わせて**より高度な設計を実現することです。Composite + Decorator、Command + Observer、Strategy + Factory — これらの組み合わせにより、単一のパターンでは達成できない柔軟性と表現力を得られます。

## 2. 共通の本質

### パターン組み合わせの原則

| 組み合わせ | 効果 |
|-----------|------|
| Composite + Decorator | 階層構造への動的な機能追加 |
| Command + Observer | 操作の実行と監視・通知の統合 |
| Strategy + Factory | 条件に基づく戦略の自動選択 |

すべての組み合わせは**関心の分離**と**合成可能性**を実現します。

## 3. 言語別実装比較

### 3.1 Composite + Decorator

階層的なデータ構造（Composite）に、ロギングやスタイリングなどの横断的関心事（Decorator）を追加するパターンです。

<details>
<summary>Clojure: マルチメソッド + 高階関数</summary>

```clojure
;; Composite: 再帰的な図形構造
(defmulti area :type)
(defmethod area :circle [{:keys [radius]}]
  (* Math/PI radius radius))
(defmethod area :composite [{:keys [children]}]
  (reduce + (map area children)))

;; Decorator: ロギングを追加
(defn with-logging [shape-fn]
  (fn [shape]
    (let [result (shape-fn shape)]
      (println (str "Area of " (:type shape) ": " result))
      result)))

;; 組み合わせ
(def logged-area (with-logging area))
(logged-area composite-shape)
```

</details>

<details>
<summary>Scala: case class + 拡張メソッド</summary>

```scala
// Composite
sealed trait Shape:
  def area: Double

case class Circle(radius: Double) extends Shape:
  def area: Double = math.Pi * radius * radius

case class CompositeShape(children: Vector[Shape]) extends Shape:
  def area: Double = children.map(_.area).sum

// Decorator: スタイル付き図形
case class StyledShape(shape: Shape, style: Style) extends Shape:
  def area: Double = shape.area

// 組み合わせ
val styled = StyledShape(
  CompositeShape(Vector(Circle(5), Circle(3))),
  Style(color = "red", outline = true)
)
```

</details>

<details>
<summary>Haskell: ADT + 関数合成</summary>

```haskell
-- Composite + Decorator を ADT で統合
data Shape
    = Circle Double
    | Square Double
    | Composite [Shape]
    | Styled Style Shape    -- Decorator を型として表現

area :: Shape -> Double
area (Circle r)      = pi * r * r
area (Square s)      = s * s
area (Composite ss)  = sum (map area ss)
area (Styled _ s)    = area s  -- スタイルは面積に影響しない

-- 関数合成でデコレータを適用
withLogging :: (Shape -> Double) -> Shape -> IO Double
withLogging f shape = do
    let result = f shape
    putStrLn $ "Area: " ++ show result
    return result
```

</details>

<details>
<summary>Rust: enum + trait</summary>

```rust
pub enum Shape {
    Circle { radius: f64 },
    Composite { shapes: Vec<Shape> },
    Styled { shape: Box<Shape>, style: Style },
}

impl Shape {
    pub fn area(&self) -> f64 {
        match self {
            Shape::Circle { radius } => std::f64::consts::PI * radius * radius,
            Shape::Composite { shapes } => shapes.iter().map(|s| s.area()).sum(),
            Shape::Styled { shape, .. } => shape.area(),
        }
    }
}
```

</details>

### 3.2 Command + Observer

コマンドの実行を Observer が監視し、Undo/Redo の履歴管理と変更通知を統合するパターンです。

<details>
<summary>Clojure: atom + watch</summary>

```clojure
;; Command 履歴の管理
(def history (atom {:undo-stack [] :redo-stack [] :document ""}))

;; Observer: 状態変更を監視
(add-watch history :logger
  (fn [_ _ old-state new-state]
    (println "Document changed:"
             (:document old-state) " -> " (:document new-state))))

;; Command 実行 + 自動通知
(defn execute-command [cmd]
  (swap! history
    (fn [state]
      (-> state
          (update :undo-stack conj cmd)
          (assoc :redo-stack [])
          (update :document (execute cmd))))))
```

</details>

<details>
<summary>F#: イベント + パイプライン</summary>

```fsharp
type CommandEvent =
    | Executed of command: TextCommand * document: string
    | Undone of command: TextCommand * document: string

type CommandProcessor = {
    Document: string
    History: TextCommand list
    Listeners: (CommandEvent -> unit) list
}

let executeCommand (cmd: TextCommand) (processor: CommandProcessor) =
    let newDoc = TextCommand.execute cmd processor.Document
    let event = Executed(cmd, newDoc)
    processor.Listeners |> List.iter (fun listener -> listener event)
    { processor with
        Document = newDoc
        History = cmd :: processor.History }
```

</details>

### 3.3 Strategy + Factory

条件に基づいて適切な Strategy を自動選択する Factory パターンです。

<details>
<summary>全言語のStrategy + Factory比較</summary>

```clojure
;; Clojure: マップベースのファクトリ
(def strategy-factory
  {:regular  regular-pricing
   :member   member-pricing
   :vip      (discount-pricing 0.3)
   :bulk     (bulk-pricing 10000 0.15)})

(defn get-strategy [customer-type]
  (get strategy-factory customer-type regular-pricing))
```

```scala
// Scala: パターンマッチ + 関数リテラル
def createStrategy(customerType: CustomerType): PricingFn =
  customerType match
    case Regular => identity
    case Member  => _ * 0.9
    case VIP     => _ * 0.7

// Factory でコンテキストに基づき自動選択
def autoPricing(customer: Customer, amount: Double): Double =
  createStrategy(customer.customerType)(amount)
```

```haskell
-- Haskell: 関数の直接返却
createStrategy :: CustomerType -> PricingStrategy
createStrategy Regular = regularPricing
createStrategy Member  = memberPricing
createStrategy VIP     = discountPricing 0.3
```

```rust
// Rust: match + クロージャ
pub fn create_strategy(customer_type: &CustomerType) -> Box<dyn Fn(f64) -> f64> {
    match customer_type {
        CustomerType::Regular => Box::new(|amount| amount),
        CustomerType::Member => Box::new(|amount| amount * 0.9),
        CustomerType::VIP => Box::new(|amount| amount * 0.7),
    }
}
```

</details>

## 4. 比較分析

### 4.1 パターン合成の容易さ

| 言語 | 合成の仕組み | 特徴 |
|------|-----------|------|
| Clojure | 関数合成 + マルチメソッド | 最も動的で柔軟 |
| Scala | trait 継承 + パターンマッチ | OOP と FP のハイブリッド |
| Elixir | パイプライン + プロトコル | 可読性の高い合成 |
| F# | パイプ演算子 + 判別共用体 | 型安全な合成 |
| Haskell | 関数合成 `(.)` + ADT | 最も数学的な合成 |
| Rust | trait + enum + クロージャ | 所有権を考慮した合成 |

### 4.2 組み合わせの安全性

パターンを組み合わせる際、静的型付け言語はコンパイル時に整合性を検証できます：

- **Haskell, F#**: ADT / 判別共用体の網羅性チェック
- **Scala, Rust**: sealed trait / enum の網羅性チェック
- **Clojure, Elixir**: テストで整合性を確認

## 5. 実践的な選択指針

| 組み合わせ | 推奨言語 | 理由 |
|-----------|---------|------|
| Composite + Decorator | Haskell | ADT で両パターンを型として統合 |
| Command + Observer | Clojure | atom + watch で自然に統合 |
| Strategy + Factory | Scala | パターンマッチで簡潔に選択 |
| 全般的な合成 | F# | パイプ演算子で可読性の高い合成 |

## 6. まとめ

パターン間の相互作用は、関数型プログラミングの**合成可能性**を最大限に活かす実践です：

1. **関数合成がパターン合成**: 高階関数でパターンを組み合わせ
2. **ADT が構造を統合**: Composite と Decorator を同じ型階層で表現
3. **イベント駆動が連携を実現**: Command の実行が Observer に自動通知

## 言語別個別記事

- [Clojure](../clojure/20-pattern-interactions.md) | [Scala](../scala/20-pattern-interactions.md) | [Elixir](../elixir/20-pattern-interactions.md) | [F#](../fsharp/20-pattern-interactions.md) | [Haskell](../haskell/20-pattern-interactions.md) | [Rust](../rust/20-pattern-interactions.md)
