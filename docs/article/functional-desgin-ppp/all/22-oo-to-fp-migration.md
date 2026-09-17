---
type: Article
title: "第22章: OO から FP への移行 — 6言語統合ガイド"
description: "オブジェクト指向（OO）から関数型プログラミング（FP）への移行は、一夜にして完了するものではありません。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第22章: OO から FP への移行 — 6言語統合ガイド

## 1. はじめに

オブジェクト指向（OO）から関数型プログラミング（FP）への移行は、一夜にして完了するものではありません。段階的に、安全に、既存のコードベースを壊さずに移行する戦略が必要です。本章では、6 言語それぞれの**マルチパラダイム度合い**に応じた移行戦略を比較します。

## 2. 共通の本質

### OO と FP の対応関係

| OO 概念 | FP 等価物 |
|--------|---------|
| クラス | データ型（struct / record / case class） + 関数 |
| メソッド | 関数 |
| 継承 | ADT / 判別共用体 + パターンマッチ |
| インターフェース | 型クラス / trait / プロトコル |
| 可変状態 | 不変データ + 状態変換関数 |
| デザインパターン | 高階関数 / パターンマッチ / 関数合成 |

### 移行の 4 フェーズ

```
Phase 1: 新規コードを FP で書く（Strangler Fig の初期段階）
Phase 2: 純粋関数を抽出し、副作用を境界に集約
Phase 3: 可変状態を不変データ構造に置き換え
Phase 4: OO パターンを FP パターンに置き換え
```

## 3. 言語別実装比較

### 3.1 マルチパラダイム度合い

| 言語 | OO サポート | FP 度合い | 移行の容易さ |
|------|-----------|----------|-----------|
| Scala | 完全（class / trait / 継承） | 高い（case class / match） | 最も容易 |
| F# | .NET のクラス利用可能 | 高い（判別共用体） | 容易 |
| Rust | trait ベース（継承なし） | 中〜高（enum / match） | 中程度 |
| Clojure | Java 相互運用 | 高い（データ中心） | データ変換が容易 |
| Elixir | なし（モジュールベース） | 高い（パイプライン） | 概念の転換が必要 |
| Haskell | なし（純粋関数型） | 最高 | 全面的な書き換え |

### 3.2 クラスからデータ + 関数へ

<details>
<summary>OO スタイル → FP スタイルの変換（全言語）</summary>

**OO（Java 風）:**
```java
class BankAccount {
    private double balance;
    public void deposit(double amount) { balance += amount; }
    public void withdraw(double amount) { balance -= amount; }
    public double getBalance() { return balance; }
}
```

**Clojure:**
```clojure
(defn make-account [balance] {:balance balance})
(defn deposit [account amount]
  (update account :balance + amount))
(defn withdraw [account amount]
  (update account :balance - amount))
```

**Scala:**
```scala
case class BankAccount(balance: Double):
  def deposit(amount: Double): BankAccount = copy(balance = balance + amount)
  def withdraw(amount: Double): BankAccount = copy(balance = balance - amount)
```

**Elixir:**
```elixir
defmodule BankAccount do
  defstruct [:balance]

  def new(balance), do: %__MODULE__{balance: balance}
  def deposit(account, amount), do: %{account | balance: account.balance + amount}
  def withdraw(account, amount), do: %{account | balance: account.balance - amount}
end
```

**F#:**
```fsharp
type BankAccount = { Balance: float }

let deposit amount account = { account with Balance = account.Balance + amount }
let withdraw amount account = { account with Balance = account.Balance - amount }
```

**Haskell:**
```haskell
data BankAccount = BankAccount { balance :: Double }

deposit :: Double -> BankAccount -> BankAccount
deposit amount account = account { balance = balance account + amount }

withdraw :: Double -> BankAccount -> BankAccount
withdraw amount account = account { balance = balance account - amount }
```

**Rust:**
```rust
pub struct BankAccount { pub balance: f64 }

impl BankAccount {
    pub fn deposit(&self, amount: f64) -> Self {
        BankAccount { balance: self.balance + amount }
    }
    pub fn withdraw(&self, amount: f64) -> Self {
        BankAccount { balance: self.balance - amount }
    }
}
```

</details>

### 3.3 継承からパターンマッチへ

<details>
<summary>継承の置き換え</summary>

**OO（継承）:**
```java
abstract class Shape { abstract double area(); }
class Circle extends Shape { double radius; double area() { return PI * r * r; } }
class Square extends Shape { double side; double area() { return side * side; } }
```

**Scala（ADT）:**
```scala
sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Square(side: Double) extends Shape

def area(shape: Shape): Double = shape match
  case Circle(r) => math.Pi * r * r
  case Square(s) => s * s
```

**Haskell（ADT）:**
```haskell
data Shape = Circle Double | Square Double

area :: Shape -> Double
area (Circle r) = pi * r * r
area (Square s) = s * s
```

**Rust（enum）:**
```rust
pub enum Shape {
    Circle(f64),
    Square(f64),
}

pub fn area(shape: &Shape) -> f64 {
    match shape {
        Shape::Circle(r) => std::f64::consts::PI * r * r,
        Shape::Square(s) => s * s,
    }
}
```

</details>

### 3.4 Strangler Fig パターン

段階的に古いコードを新しいコードで置き換える移行戦略です。

<details>
<summary>Strangler Fig の実装</summary>

```scala
// Scala: フィーチャーフラグで切り替え
class OrderService(useNewImpl: Boolean = false):
  private val legacyService = new LegacyOrderService()
  private val fpService = new FPOrderService()

  def processOrder(order: Order): Result =
    if useNewImpl then fpService.process(order)
    else legacyService.process(order)
```

```clojure
;; Clojure: 動的切り替え
(def use-new-impl (atom false))

(defn process-order [order]
  (if @use-new-impl
    (fp-process-order order)
    (legacy-process-order order)))
```

```rust
// Rust: trait による差し替え
pub trait OrderProcessor {
    fn process(&self, order: &Order) -> Result<ProcessedOrder, String>;
}

pub struct LegacyProcessor;
pub struct FPProcessor;

impl OrderProcessor for FPProcessor {
    fn process(&self, order: &Order) -> Result<ProcessedOrder, String> {
        validate(order)
            .and_then(calculate_total)
            .and_then(apply_discount)
    }
}
```

</details>

### 3.5 GoF パターンの FP 変換

| OO パターン | FP 変換 | 関数型での表現 |
|-----------|---------|-------------|
| Strategy | 高階関数 | 関数を引数として渡す |
| Observer | イベントバス / コールバック | 関数のリスト |
| Command | データ構造 | コマンドを ADT で表現 |
| Decorator | 関数合成 | 高階関数でラップ |
| Visitor | パターンマッチ | case / match で分岐 |
| Factory | ファクトリ関数 | 関数を返す関数 |
| Composite | 再帰的 ADT | ADT の再帰的定義 |
| Adapter | 変換関数 | `from` / `into` / 関数 |

<details>
<summary>Observer パターンの FP 変換例</summary>

```clojure
;; OO スタイル
;; subject.addObserver(observer)
;; subject.notifyAll()

;; FP スタイル: イベントバス
(def listeners (atom {}))

(defn subscribe [event-type handler]
  (swap! listeners update event-type (fnil conj []) handler))

(defn publish [event-type data]
  (doseq [handler (get @listeners event-type)]
    (handler data)))
```

```elixir
# Elixir: プロセスベースの Observer
defmodule EventBus do
  use GenServer

  def subscribe(event_type, pid) do
    GenServer.cast(__MODULE__, {:subscribe, event_type, pid})
  end

  def publish(event_type, data) do
    GenServer.cast(__MODULE__, {:publish, event_type, data})
  end

  def handle_cast({:publish, event_type, data}, subscribers) do
    subscribers
    |> Map.get(event_type, [])
    |> Enum.each(&send(&1, {:event, event_type, data}))
    {:noreply, subscribers}
  end
end
```

</details>

## 4. 比較分析

### 4.1 移行難易度の評価

| 言語 | 移行元からの距離 | 難易度 | 理由 |
|------|---------------|--------|------|
| Scala | 最も近い | 低 | OO + FP のハイブリッド、Java との相互運用 |
| F# | 近い | 低〜中 | .NET の OO と自然に共存 |
| Rust | 中程度 | 中 | 継承なし、trait ベースで FP に近い |
| Clojure | やや遠い | 中 | データ中心への概念転換が必要 |
| Elixir | 遠い | 中〜高 | クラスの概念がなく、アクターモデルへの転換 |
| Haskell | 最も遠い | 高 | 純粋関数型への完全な転換が必要 |

### 4.2 段階的移行 vs 全面書き換え

| 戦略 | 適した言語 | 理由 |
|------|----------|------|
| 段階的移行（推奨） | Scala, F#, Clojure | 既存 OO コードとの共存が容易 |
| 全面書き換え | Haskell, Elixir | パラダイムが根本的に異なるため部分移行が困難 |
| ハイブリッド | Rust | trait ベースで OO 的コードも FP 的コードも書ける |

### 4.3 イベントソーシングによる移行

OO の可変状態を FP の不変イベント列に置き換える強力な移行パターンです：

```
OO:  object.setState(newState)  ← 状態を直接変更
FP:  events = [..., event]       ← イベントを蓄積し、状態を再構築
```

```clojure
;; Clojure: イベントソーシング
(defn apply-event [state event]
  (case (:type event)
    :deposited  (update state :balance + (:amount event))
    :withdrawn  (update state :balance - (:amount event))))

(defn rebuild-state [events]
  (reduce apply-event {:balance 0} events))
```

## 5. 実践的な選択指針

| 移行シナリオ | 推奨言語 | 理由 |
|------------|---------|------|
| Java コードベースからの移行 | Scala | JVM 互換、段階的移行が最も容易 |
| C# コードベースからの移行 | F# | .NET 互換、段階的移行が容易 |
| 新規プロジェクトで FP 採用 | Haskell, Elixir | パラダイムの純粋性 |
| パフォーマンス要件が高い | Rust | ゼロコスト抽象化 + FP スタイル |
| 動的型付けからの移行 | Clojure | データ中心で概念が近い |
| 大規模チームでの移行 | Scala, F# | OO 経験者の学習曲線が緩やか |

## 6. まとめ

OO から FP への移行は、**考え方の転換**です：

1. **データと振る舞いの分離**: クラス → データ型 + 関数
2. **不変性の採用**: 可変状態 → 状態変換関数
3. **パターンの簡素化**: GoF パターン → 高階関数 + パターンマッチ
4. **段階的な移行**: Strangler Fig パターンで安全に移行

最も重要なのは、移行は**一度にすべてを変える必要はない**ということです。新しいコードを FP で書き始め、徐々に既存コードを置き換えていくアプローチが、リスクを最小化しながら FP の恩恵を得る最善の方法です。

## 言語別個別記事

- [Clojure](../clojure/22-oo-to-fp-migration.md) | [Scala](../scala/22-oo-to-fp-migration.md) | [Elixir](../elixir/22-oo-to-fp-migration.md) | [F#](../fsharp/22-oo-to-fp-migration.md) | [Haskell](../haskell/22-oo-to-fp-migration.md) | [Rust](../rust/22-oo-to-fp-migration.md)
