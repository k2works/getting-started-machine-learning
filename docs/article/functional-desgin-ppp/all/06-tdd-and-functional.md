---
type: Article
title: "第6章: TDD と関数型プログラミング — 6言語統合ガイド"
description: "テスト駆動開発（TDD）は「テストを先に書き、テストが通る最小限のコードを実装し、リファクタリングする」サイクルです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第6章: TDD と関数型プログラミング — 6言語統合ガイド

## 1. はじめに

テスト駆動開発（TDD）は「テストを先に書き、テストが通る最小限のコードを実装し、リファクタリングする」サイクルです。関数型プログラミングの**純粋関数**と**不変データ**は、TDD と極めて相性が良い組み合わせです。副作用がなく、同じ入力に対して常に同じ出力を返す関数は、テストが書きやすく、結果が予測可能です。

## 2. 共通の本質

### Red-Green-Refactor サイクル

すべての言語で共通の TDD サイクル：

```
1. Red:      失敗するテストを書く
2. Green:    テストが通る最小限のコードを書く
3. Refactor: コードをきれいにする（テストは緑のまま）
```

### 関数型 TDD の利点

| 利点 | 説明 |
|------|------|
| テスト容易性 | 純粋関数は入力→出力のみ、セットアップ不要 |
| 再現性 | 副作用がないため、テスト結果が決定的 |
| 合成可能性 | 小さな関数をテスト → 合成して大きな機能を構築 |
| リファクタリング安全性 | 不変データにより意図しない状態変更がない |

## 3. 言語別実装比較

### 3.1 テストフレームワーク

| 言語 | フレームワーク | テスト記述スタイル |
|------|-------------|-----------------|
| Clojure | clojure.test / speclj | `deftest` + `is` / `describe` + `it` |
| Scala | ScalaTest / MUnit | `test("...")` + `shouldBe` |
| Elixir | ExUnit | `test "..." do` + `assert` |
| F# | xUnit / Expecto | `[<Fact>]` + `Assert` / `testCase` |
| Haskell | HSpec / QuickCheck | `describe` + `it` + `shouldBe` |
| Rust | 組み込み `#[test]` | `#[test]` + `assert_eq!` |

### 3.2 FizzBuzz で学ぶ TDD サイクル

FizzBuzz は TDD の入門に最適な問題です。すべての言語で同じ段階的アプローチを取ります。

#### ステップ 1: 通常の数値を返す

<details>
<summary>テスト（全言語比較）</summary>

```clojure
;; Clojure
(deftest test-normal-number
  (is (= "1" (fizzbuzz 1)))
  (is (= "2" (fizzbuzz 2))))
```

```scala
// Scala
test("通常の数値を文字列で返す") {
  FizzBuzz.fizzbuzz(1) shouldBe "1"
  FizzBuzz.fizzbuzz(2) shouldBe "2"
}
```

```elixir
# Elixir
test "通常の数値を返す" do
  assert FizzBuzz.fizzbuzz(1) == "1"
  assert FizzBuzz.fizzbuzz(2) == "2"
end
```

```fsharp
// F#
[<Fact>]
let ``通常の数値を文字列で返す`` () =
    fizzbuzz 1 |> should equal "1"
    fizzbuzz 2 |> should equal "2"
```

```haskell
-- Haskell
describe "FizzBuzz" $ do
    it "通常の数値を返す" $ do
        fizzBuzz 1 `shouldBe` "1"
        fizzBuzz 2 `shouldBe` "2"
```

```rust
// Rust
#[test]
fn fizzbuzz_1_returns_1() {
    assert_eq!("1", fizzbuzz(1));
}

#[test]
fn fizzbuzz_2_returns_2() {
    assert_eq!("2", fizzbuzz(2));
}
```

</details>

#### ステップ 2: 3 の倍数で "Fizz"

<details>
<summary>実装（全言語比較）</summary>

```clojure
;; Clojure
(defn fizz? [n] (zero? (mod n 3)))
(defn fizzbuzz [n]
  (cond
    (fizz? n) "Fizz"
    :else (str n)))
```

```scala
// Scala
def isFizz(n: Int): Boolean = n % 3 == 0
def fizzbuzz(n: Int): String =
  if isFizz(n) then "Fizz" else n.toString
```

```haskell
-- Haskell
fizz :: Int -> Bool
fizz n = n `mod` 3 == 0

fizzBuzz :: Int -> String
fizzBuzz n
    | fizz n    = "Fizz"
    | otherwise = show n
```

```rust
// Rust
fn is_fizz(n: u32) -> bool { n % 3 == 0 }

pub fn fizzbuzz(n: u32) -> String {
    if is_fizz(n) { "Fizz".to_string() }
    else { n.to_string() }
}
```

</details>

#### ステップ 3: 完成形

<details>
<summary>完成した fizzbuzz（全言語比較）</summary>

```clojure
;; Clojure
(defn fizzbuzz [n]
  (cond
    (and (fizz? n) (buzz? n)) "FizzBuzz"
    (fizz? n) "Fizz"
    (buzz? n) "Buzz"
    :else (str n)))
```

```scala
// Scala
def fizzbuzz(n: Int): String =
  if isFizz(n) && isBuzz(n) then "FizzBuzz"
  else if isFizz(n) then "Fizz"
  else if isBuzz(n) then "Buzz"
  else n.toString
```

```elixir
# Elixir
def fizzbuzz(n) do
  cond do
    rem(n, 15) == 0 -> "FizzBuzz"
    rem(n, 3) == 0  -> "Fizz"
    rem(n, 5) == 0  -> "Buzz"
    true            -> Integer.to_string(n)
  end
end
```

```fsharp
// F#
let fizzbuzz n =
    if isFizzBuzz n then "FizzBuzz"
    elif isFizz n then "Fizz"
    elif isBuzz n then "Buzz"
    else string n
```

```haskell
-- Haskell
fizzBuzz :: Int -> String
fizzBuzz n
    | fizz n && buzz n = "FizzBuzz"
    | fizz n           = "Fizz"
    | buzz n           = "Buzz"
    | otherwise        = show n
```

```rust
// Rust
fn is_fizzbuzz(n: u32) -> bool { is_fizz(n) && is_buzz(n) }

pub fn fizzbuzz(n: u32) -> String {
    if is_fizzbuzz(n) { "FizzBuzz".to_string() }
    else if is_fizz(n) { "Fizz".to_string() }
    else if is_buzz(n) { "Buzz".to_string() }
    else { n.to_string() }
}
```

</details>

### 3.3 ボウリングスコア — 複雑な TDD

ボウリングスコア計算は、TDD で複雑なロジックを段階的に構築する典型例です。

| ルール | 説明 |
|--------|------|
| 通常 | フレーム内のピン数の合計 |
| スペア | 10 + 次の 1 投のボーナス |
| ストライク | 10 + 次の 2 投のボーナス |
| 10 フレーム目 | ストライク/スペア時に追加投球 |

<details>
<summary>ボウリングスコア計算の実装比較</summary>

```clojure
;; Clojure: loop/recur による再帰
(defn score [rolls]
  (loop [frame 0, roll-idx 0, total 0]
    (if (= frame 10) total
      (cond
        (strike? rolls roll-idx)
        (recur (inc frame) (inc roll-idx)
               (+ total (strike-score rolls roll-idx)))
        (spare? rolls roll-idx)
        (recur (inc frame) (+ roll-idx 2)
               (+ total (spare-score rolls roll-idx)))
        :else
        (recur (inc frame) (+ roll-idx 2)
               (+ total (frame-score rolls roll-idx)))))))
```

```scala
// Scala: パターンマッチ + 尾部再帰
@tailrec
def scoreFrames(rolls: List[Int], frame: Int, acc: Int): Int =
  if frame >= 10 then acc
  else rolls match
    case x :: y :: z :: rest if x == 10 =>
      scoreFrames(y :: z :: rest, frame + 1, acc + 10 + y + z)
    case x :: y :: z :: rest if x + y == 10 =>
      scoreFrames(z :: rest, frame + 1, acc + 10 + z)
    case x :: y :: rest =>
      scoreFrames(rest, frame + 1, acc + x + y)
    case _ => acc
```

```haskell
-- Haskell: リスト再帰 + ガード式
score :: [Int] -> Int
score = go 0 0
  where
    go 10 total _ = total
    go frame total (x:y:z:rest)
        | x == 10       = go (frame+1) (total+10+y+z) (y:z:rest)
        | x + y == 10   = go (frame+1) (total+10+z) (z:rest)
        | otherwise     = go (frame+1) (total+x+y) (z:rest)
    go _ total _ = total
```

```rust
// Rust: スライス + ヘルパー関数による再帰
fn is_strike(rolls: &[u32]) -> bool { !rolls.is_empty() && rolls[0] == 10 }
fn is_spare(rolls: &[u32]) -> bool { rolls.len() >= 2 && rolls[0] + rolls[1] == 10 && rolls[0] != 10 }
fn strike_bonus(r: &[u32]) -> u32 { r.iter().take(2).sum() }
fn spare_bonus(r: &[u32]) -> u32 { r.first().copied().unwrap_or(0) }

pub fn bowling_score(rolls: &[u32]) -> u32 {
    fn go(r: &[u32], frame: u32, total: u32) -> u32 {
        if frame > 10 || r.is_empty() { total }
        else if is_strike(r) { go(&r[1..], frame + 1, total + 10 + strike_bonus(&r[1..])) }
        else if is_spare(r) { go(&r[2..], frame + 1, total + 10 + spare_bonus(&r[2..])) }
        else { go(&r[2..], frame + 1, total + r.iter().take(2).sum::<u32>()) }
    }
    go(rolls, 1, 0)
}
```

</details>

### 3.4 依存性注入と副作用の分離

関数型 TDD では、副作用を持つ処理を**関数の引数**として注入し、テスト時にモックに差し替えます。

| 言語 | 依存性注入の方法 | テスト時の差し替え |
|------|----------------|-----------------|
| Clojure | 高階関数の引数 | テスト用関数を渡す |
| Scala | 関数型パラメータ / trait | テスト用実装を渡す |
| Elixir | 高階関数の引数 | テスト用クロージャ |
| F# | 関数型パラメータ | テスト用関数を渡す |
| Haskell | 型クラス / Reader モナド | テスト用インスタンス |
| Rust | trait object / 関数ポインタ | テスト用 struct |

<details>
<summary>依存性注入の実装比較</summary>

```clojure
;; Clojure: 高階関数
(defn calculate-price [product-id base-price discount-fetcher]
  (let [discount (discount-fetcher product-id)]
    (* base-price (- 1 discount))))

;; テスト
(deftest test-calculate-price
  (let [mock-fetcher (constantly 0.1)]
    (is (= 900.0 (calculate-price "P001" 1000 mock-fetcher)))))
```

```elixir
# Elixir: クロージャ
def calculate_price(product_id, base_price, discount_fetcher) do
  discount = discount_fetcher.(product_id)
  base_price * (1.0 - discount)
end

# テスト
test "価格計算" do
  mock = fn _id -> 0.10 end
  assert PricingService.calculate_price("P001", 1000, mock) == 900.0
end
```

```rust
// Rust: 関数ポインタ / クロージャ
pub fn calculate_price(
    product_id: &str,
    base_price: f64,
    discount_fetcher: impl Fn(&str) -> f64,
) -> f64 {
    let discount = discount_fetcher(product_id);
    base_price * (1.0 - discount)
}

// テスト
#[test]
fn test_calculate_price() {
    let mock = |_: &str| 0.10;
    assert_eq!(calculate_price("P001", 1000.0, mock), 900.0);
}
```

```haskell
-- Haskell: 型クラスによる抽象化
class DiscountFetcher m where
    fetchDiscount :: String -> m Double

calculatePrice :: (Monad m, DiscountFetcher m) => String -> Double -> m Double
calculatePrice productId basePrice = do
    discount <- fetchDiscount productId
    return $ basePrice * (1.0 - discount)
```

</details>

### 3.5 不変データ構造のテスト

<details>
<summary>スタックの TDD 実装比較</summary>

```clojure
;; Clojure: リストベース
(defn create-stack [] '())
(defn stack-push [stack item] (conj stack item))
(defn stack-pop [stack] [(first stack) (rest stack)])

(deftest test-stack
  (let [stack (-> (create-stack) (stack-push "a") (stack-push "b"))
        [top remaining] (stack-pop stack)]
    (is (= "b" top))
    (is (= '("a") remaining))))
```

```scala
// Scala: case class + List
case class Stack[A](items: List[A] = Nil):
  def push(item: A): Stack[A] = Stack(item :: items)
  def pop: (A, Stack[A]) = (items.head, Stack(items.tail))

test("push して pop すると LIFO") {
  val stack = Stack[String]().push("a").push("b")
  val (top, remaining) = stack.pop
  top shouldBe "b"
  remaining.pop._1 shouldBe "a"
}
```

```rust
// Rust: Vec ベース + 不変メソッド
pub struct Stack<T: Clone> {
    items: Vec<T>,
}

impl<T: Clone> Stack<T> {
    pub fn empty() -> Self { Stack { items: Vec::new() } }

    pub fn push(&self, item: T) -> Self {
        let mut new_items = self.items.clone();
        new_items.push(item);
        Stack { items: new_items }
    }

    pub fn pop(&self) -> Option<(T, Self)> {
        if self.items.is_empty() {
            None
        } else {
            let mut new_items = self.items.clone();
            let top = new_items.pop().unwrap();
            Some((top, Stack { items: new_items }))
        }
    }
}
```

</details>

## 4. 比較分析

### 4.1 テスト記述の簡潔さ

| 言語 | FizzBuzz テスト行数 | ボイラープレート |
|------|-------------------|----------------|
| Clojure | 最小 | `deftest` + `is` で完結 |
| Scala | 少 | `shouldBe` マッチャーが豊富 |
| Elixir | 少 | `assert` がシンプル |
| F# | 少 | `[<Fact>]` で宣言的 |
| Haskell | 最小 | `shouldBe` で数学的な記述 |
| Rust | 少 | `assert_eq!` マクロがシンプル |

### 4.2 副作用分離の厳密さ

```
厳密 ←――――――――――――――――――→ 柔軟

Haskell    F#    Rust    Scala    Elixir    Clojure
├─IO モナド┤     │       │        │         │
           ├─CE──┤       │        │         │
                 ├─trait──┤        │         │
                          ├─高階──┤         │
                                   ├─関数──┤
                                            ├─規約
```

Haskell は型レベルで副作用を分離します（IO モナド）。Clojure は規約ベースで分離します。他の言語はその中間に位置します。

### 4.3 F# / Rust の TDD 詳細

F# と Rust は共通の章構成（FizzBuzz、ローマ数字、ボウリング、素数、スタック/キュー、文字列電卓、リファクタリング、パスワードバリデーター）で TDD を体系的にカバーしています。

**F# の特筆点：**

- **計算式（Computation Expression）** による非同期テスト
- **パイプライン演算子** を活用したテストデータの構築
- **xUnit + FsCheck 統合** によるプロパティベーステストとの連携

**Rust の特筆点：**

- **`#[should_panic]`** アトリビュートによる例外テスト
- **所有権システム** がコンパイル時に不変性を保証
- **proptest** によるプロパティベーステストとの連携

## 5. 実践的な選択指針

| 要件 | 推奨言語 | 理由 |
|------|---------|------|
| テスト容易性最優先 | Haskell | 純粋関数 + 型システムで副作用を完全分離 |
| 段階的な TDD 導入 | Scala | ScalaTest の豊富なマッチャーと柔軟なスタイル |
| Web アプリの TDD | Elixir | ExUnit + Phoenix の統合テスト環境 |
| REPL 駆動の TDD | Clojure | 対話的な開発サイクルが高速 |
| システムプログラミングの TDD | Rust | 組み込みテストフレームワークの安定性 |
| エンタープライズの TDD | F# | xUnit + .NET エコシステムとの統合 |

## 6. まとめ

TDD と関数型プログラミングの組み合わせは、テスト可能なコードを**自然に**書くことを促進します：

1. **純粋関数**: 入力→出力のみでテスト可能、セットアップ/ティアダウン不要
2. **不変データ**: テストの再現性が保証され、テスト間の干渉がない
3. **依存性注入**: 高階関数や型クラスで副作用を分離し、テスト時にモック化
4. **段階的構築**: TDD サイクルと関数合成が自然に結びつく

## 言語別個別記事

- [Clojure](../clojure/06-tdd-in-functional.md) | [Scala](../scala/06-tdd-functional.md) | [Elixir](../elixir/06-tdd-and-fp.md) | [F#](../fsharp/06-tdd-functional.md) | [Haskell](../haskell/06-tdd-functional.md) | [Rust](../rust/06-tdd-and-functional.md)
