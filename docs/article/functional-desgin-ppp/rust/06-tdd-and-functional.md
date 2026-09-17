---
type: Article
title: "第6章: テスト駆動開発と関数型プログラミング"
description: "テスト駆動開発（TDD）は、テストを先に書いてから実装を行う開発手法です。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第6章: テスト駆動開発と関数型プログラミング

## はじめに

テスト駆動開発（TDD）は、テストを先に書いてから実装を行う開発手法です。関数型プログラミングと TDD は相性が良く、純粋関数はテストが容易で、不変データ構造は予測可能な動作を保証します。

本章では、Red-Green-Refactor サイクルを関数型スタイルで実践する方法を学びます。

## 1. TDD の基本サイクル

### Red-Green-Refactor

```
┌─────────────────────────────────────────────────┐
│                                                 │
│    ┌───────┐     ┌───────┐     ┌───────────┐   │
│    │  Red  │ ──► │ Green │ ──► │ Refactor  │   │
│    └───────┘     └───────┘     └───────────┘   │
│        ▲                              │         │
│        └──────────────────────────────┘         │
│                                                 │
└─────────────────────────────────────────────────┘
```

1. **Red（赤）**: 失敗するテストを書く
2. **Green（緑）**: テストを通す最小限のコードを書く
3. **Refactor（リファクタリング）**: コードを改善する（テストは通ったまま）

## 2. FizzBuzz - TDD の典型例

### Step 1: Red（最初のテスト）

```rust
#[test]
fn fizzbuzz_1_returns_1() {
    assert_eq!("1", fizzbuzz(1));
}
```

### Step 2: Green（最小限の実装）

```rust
pub fn fizzbuzz(n: u32) -> String {
    "1".to_string()
}
```

### Step 3: 次のテストを追加して段階的に実装を発展

```rust
// テストを追加
#[test]
fn fizzbuzz_2_returns_2() {
    assert_eq!("2", fizzbuzz(2));
}

#[test]
fn fizzbuzz_3_returns_fizz() {
    assert_eq!("Fizz", fizzbuzz(3));
}

#[test]
fn fizzbuzz_5_returns_buzz() {
    assert_eq!("Buzz", fizzbuzz(5));
}

#[test]
fn fizzbuzz_15_returns_fizzbuzz() {
    assert_eq!("FizzBuzz", fizzbuzz(15));
}
```

### 最終実装（小さなヘルパー関数に分割）

```rust
/// 3で割り切れるかどうか
fn is_fizz(n: u32) -> bool {
    n % 3 == 0
}

/// 5で割り切れるかどうか
fn is_buzz(n: u32) -> bool {
    n % 5 == 0
}

/// 15で割り切れるかどうか（FizzBuzz）
fn is_fizzbuzz(n: u32) -> bool {
    is_fizz(n) && is_buzz(n)
}

/// FizzBuzz変換
pub fn fizzbuzz(n: u32) -> String {
    if is_fizzbuzz(n) {
        "FizzBuzz".to_string()
    } else if is_fizz(n) {
        "Fizz".to_string()
    } else if is_buzz(n) {
        "Buzz".to_string()
    } else {
        n.to_string()
    }
}

/// 1からnまでのFizzBuzz列を生成
pub fn fizzbuzz_sequence(n: u32) -> Vec<String> {
    (1..=n).map(fizzbuzz).collect()
}
```

### プロパティベーステストと組み合わせ

```rust
use proptest::prelude::*;

proptest! {
    #[test]
    fn multiples_of_3_always_contain_fizz(n in 1..1000u32) {
        let result = fizzbuzz(n * 3);
        prop_assert!(result.contains("Fizz"));
    }

    #[test]
    fn multiples_of_5_always_contain_buzz(n in 1..1000u32) {
        let result = fizzbuzz(n * 5);
        prop_assert!(result.contains("Buzz"));
    }
}
```

## 3. ローマ数字変換

### テストから始める

```rust
#[test]
fn to_roman_1_returns_i() {
    assert_eq!("I", to_roman(1));
}

#[test]
fn to_roman_4_returns_iv() {
    assert_eq!("IV", to_roman(4));
}

#[test]
fn to_roman_1994_returns_mcmxciv() {
    assert_eq!("MCMXCIV", to_roman(1994));
}

#[test]
fn to_roman_and_from_roman_are_inverse() {
    for n in 1..=100 {
        assert_eq!(n, from_roman(&to_roman(n)));
    }
}
```

### データ駆動の実装

```rust
/// ローマ数字の対応表（大きい順）
const ROMAN_NUMERALS: &[(u32, &str)] = &[
    (1000, "M"), (900, "CM"), (500, "D"), (400, "CD"),
    (100, "C"),  (90, "XC"),  (50, "L"),  (40, "XL"),
    (10, "X"),   (9, "IX"),   (5, "V"),   (4, "IV"),
    (1, "I"),
];

/// 整数をローマ数字に変換
pub fn to_roman(n: u32) -> String {
    assert!(n >= 1 && n <= 3999, "n must be between 1 and 3999");

    let mut remaining = n;
    let mut result = String::new();

    for &(value, numeral) in ROMAN_NUMERALS {
        while remaining >= value {
            result.push_str(numeral);
            remaining -= value;
        }
    }

    result
}

/// ローマ数字から整数へ変換（逆変換）
pub fn from_roman(roman: &str) -> u32 {
    let roman_value = |c: char| -> i32 {
        match c {
            'I' => 1, 'V' => 5, 'X' => 10, 'L' => 50,
            'C' => 100, 'D' => 500, 'M' => 1000,
            _ => 0,
        }
    };

    let values: Vec<i32> = roman.chars().map(roman_value).collect();

    let total = values.windows(2).fold(0i32, |acc, w| {
        if w[0] < w[1] { acc - w[0] } else { acc + w[0] }
    }) + values.last().copied().unwrap_or(0);

    total as u32
}
```

## 4. ボウリングスコア計算

### 複雑なビジネスロジックの TDD

```rust
#[test]
fn gutter_game_scores_0() {
    assert_eq!(0, bowling_score(&vec![0; 20]));
}

#[test]
fn all_ones_scores_20() {
    assert_eq!(20, bowling_score(&vec![1; 20]));
}

#[test]
fn spare_adds_next_roll_as_bonus() {
    let mut rolls = vec![5, 5, 3, 0];
    rolls.extend(vec![0; 16]);
    assert_eq!(16, bowling_score(&rolls));
}

#[test]
fn strike_adds_next_two_rolls_as_bonus() {
    let mut rolls = vec![10, 3, 4];
    rolls.extend(vec![0; 16]);
    assert_eq!(24, bowling_score(&rolls));
}

#[test]
fn perfect_game_scores_300() {
    assert_eq!(300, bowling_score(&vec![10; 12]));
}
```

### 小さな関数に分割

```rust
/// ストライクかどうか
fn is_strike(rolls: &[u32]) -> bool {
    !rolls.is_empty() && rolls[0] == 10
}

/// スペアかどうか
fn is_spare(rolls: &[u32]) -> bool {
    rolls.len() >= 2 && rolls[0] + rolls[1] == 10 && rolls[0] != 10
}

/// ストライクボーナス
fn strike_bonus(remaining: &[u32]) -> u32 {
    remaining.iter().take(2).sum()
}

/// スペアボーナス
fn spare_bonus(remaining: &[u32]) -> u32 {
    remaining.first().copied().unwrap_or(0)
}

/// ボウリングスコアを計算
pub fn bowling_score(rolls: &[u32]) -> u32 {
    fn loop_score(remaining: &[u32], frame: u32, total: u32) -> u32 {
        if frame > 10 || remaining.is_empty() {
            total
        } else if is_strike(remaining) {
            loop_score(&remaining[1..], frame + 1, total + 10 + strike_bonus(&remaining[1..]))
        } else if is_spare(remaining) {
            loop_score(&remaining[2..], frame + 1, total + 10 + spare_bonus(&remaining[2..]))
        } else {
            let frame_score: u32 = remaining.iter().take(2).sum();
            loop_score(&remaining[2..], frame + 1, total + frame_score)
        }
    }

    loop_score(rolls, 1, 0)
}
```

## 5. 素数 - シンプルな関数の TDD

### テストから設計を導く

```rust
#[test]
fn zero_is_not_prime() {
    assert!(!is_prime(0));
}

#[test]
fn two_is_prime() {
    assert!(is_prime(2));
}

#[test]
fn primes_up_to_20_returns_correct_list() {
    let expected = vec![2, 3, 5, 7, 11, 13, 17, 19];
    assert_eq!(expected, primes_up_to(20));
}

#[test]
fn prime_factors_24_returns_2_2_2_3() {
    let expected = vec![2, 2, 2, 3];
    assert_eq!(expected, prime_factors(24));
}

#[test]
fn prime_factors_product_equals_original() {
    for n in 2..=100 {
        let factors = prime_factors(n);
        let product: u64 = factors.iter().product();
        assert_eq!(n, product);
    }
}
```

### 実装

```rust
/// 素数判定
pub fn is_prime(n: u64) -> bool {
    if n < 2 {
        return false;
    }
    if n == 2 {
        return true;
    }
    if n % 2 == 0 {
        return false;
    }
    let sqrt_n = (n as f64).sqrt() as u64;
    !(3..=sqrt_n).step_by(2).any(|i| n % i == 0)
}

/// n以下の素数をすべて返す
pub fn primes_up_to(n: u64) -> Vec<u64> {
    (2..=n).filter(|&x| is_prime(x)).collect()
}

/// 素因数分解
pub fn prime_factors(n: u64) -> Vec<u64> {
    let mut remaining = n;
    let mut factor = 2;
    let mut factors = Vec::new();

    while remaining > 1 {
        while remaining % factor == 0 {
            factors.push(factor);
            remaining /= factor;
        }
        factor += 1;
    }

    factors
}
```

## 6. 不変データ構造 - スタックとキュー

### 不変スタック

```rust
#[derive(Debug, Clone, PartialEq)]
pub struct Stack<T: Clone> {
    items: Vec<T>,
}

impl<T: Clone> Stack<T> {
    pub fn empty() -> Self {
        Stack { items: Vec::new() }
    }

    pub fn is_empty(&self) -> bool {
        self.items.is_empty()
    }

    pub fn size(&self) -> usize {
        self.items.len()
    }

    /// アイテムを追加（新しいスタックを返す）
    pub fn push(&self, item: T) -> Self {
        let mut new_items = self.items.clone();
        new_items.push(item);
        Stack { items: new_items }
    }

    /// 先頭を取り出す（新しいスタックを返す）
    pub fn pop(&self) -> Option<(T, Self)> {
        if self.items.is_empty() {
            None
        } else {
            let mut new_items = self.items.clone();
            let value = new_items.pop().unwrap();
            Some((value, Stack { items: new_items }))
        }
    }

    /// 先頭を参照する
    pub fn peek(&self) -> Option<&T> {
        self.items.last()
    }
}
```

### テスト

```rust
#[test]
fn stack_works_in_lifo_order() {
    let stack = Stack::empty()
        .push("a")
        .push("b")
        .push("c");

    let (v1, s1) = stack.pop().unwrap();
    assert_eq!("c", v1);

    let (v2, s2) = s1.pop().unwrap();
    assert_eq!("b", v2);

    let (v3, s3) = s2.pop().unwrap();
    assert_eq!("a", v3);

    assert!(s3.is_empty());
}
```

### 不変キュー（2つの Vec で実装）

```rust
#[derive(Debug, Clone, PartialEq)]
pub struct Queue<T: Clone> {
    front: Vec<T>,
    back: Vec<T>,
}

impl<T: Clone> Queue<T> {
    pub fn empty() -> Self {
        Queue { front: Vec::new(), back: Vec::new() }
    }

    pub fn is_empty(&self) -> bool {
        self.front.is_empty() && self.back.is_empty()
    }

    /// アイテムを追加（新しいキューを返す）
    pub fn enqueue(&self, item: T) -> Self {
        let mut new_back = self.back.clone();
        new_back.push(item);
        Queue { front: self.front.clone(), back: new_back }
    }

    /// 先頭を取り出す（新しいキューを返す）
    pub fn dequeue(&self) -> Option<(T, Self)> {
        if let Some((head, tail)) = self.front.split_first() {
            Some((head.clone(), Queue { front: tail.to_vec(), back: self.back.clone() }))
        } else if !self.back.is_empty() {
            // Vec::push は末尾追加のため、back は oldest-first の順序
            let (head, tail) = self.back.split_first().unwrap();
            Some((head.clone(), Queue { front: tail.to_vec(), back: Vec::new() }))
        } else {
            None
        }
    }
}
```

## 7. 文字列電卓 - 段階的な要件追加

### テスト

```rust
#[test]
fn empty_string_returns_0() {
    assert_eq!(0, string_calculator_add(""));
}

#[test]
fn single_number_returns_its_value() {
    assert_eq!(5, string_calculator_add("5"));
}

#[test]
fn comma_separated_numbers_are_summed() {
    assert_eq!(6, string_calculator_add("1,2,3"));
}

#[test]
fn newline_separator_is_handled() {
    assert_eq!(6, string_calculator_add("1\n2,3"));
}

#[test]
fn custom_delimiter_can_be_used() {
    assert_eq!(3, string_calculator_add("//;\n1;2"));
}

#[test]
#[should_panic(expected = "-2")]
fn negative_numbers_cause_panic() {
    string_calculator_add("1,-2,3");
}

#[test]
fn numbers_greater_than_1000_are_ignored() {
    assert_eq!(2, string_calculator_add("2,1001"));
}
```

### 実装

```rust
/// 区切り文字と数値文字列をパース
fn parse_input(input: &str) -> (&str, &str) {
    if let Some(rest) = input.strip_prefix("//") {
        if let Some(newline_pos) = rest.find('\n') {
            let delimiter = &rest[..newline_pos];
            let numbers = &rest[newline_pos + 1..];
            (delimiter, numbers)
        } else {
            (",", input)
        }
    } else {
        (",", input)
    }
}

/// 数値をパース
fn parse_numbers(numbers: &str, delimiter: &str) -> Vec<i32> {
    numbers
        .replace('\n', delimiter)
        .split(delimiter)
        .filter(|s| !s.is_empty())
        .map(|s| s.trim().parse::<i32>().unwrap_or(0))
        .collect()
}

/// 負の数をバリデーション
fn validate_numbers(nums: &[i32]) {
    let negatives: Vec<i32> = nums.iter().filter(|&&n| n < 0).copied().collect();
    if !negatives.is_empty() {
        let neg_str: Vec<String> = negatives.iter().map(|n| n.to_string()).collect();
        panic!("negatives not allowed: {}", neg_str.join(", "));
    }
}

/// 文字列電卓
pub fn string_calculator_add(input: &str) -> i32 {
    if input.is_empty() {
        return 0;
    }

    let (delimiter, numbers) = parse_input(input);
    let nums = parse_numbers(numbers, delimiter);
    validate_numbers(&nums);
    nums.iter().filter(|&&n| n <= 1000).sum()
}
```

## 8. 純粋関数とテスト容易性

### 純粋関数の利点

```rust
#[derive(Debug, Clone)]
pub struct Item {
    pub name: String,
    pub price: f64,
}

#[derive(Debug, Clone, PartialEq)]
pub struct TaxCalculation {
    pub subtotal: f64,
    pub tax: f64,
    pub total: f64,
}

/// 税額を計算
pub fn calculate_tax(amount: f64, rate: f64) -> f64 {
    amount * rate
}

/// 税込み総額を計算
pub fn calculate_total_with_tax(items: &[Item], tax_rate: f64) -> TaxCalculation {
    let subtotal: f64 = items.iter().map(|i| i.price).sum();
    let tax = calculate_tax(subtotal, tax_rate);
    TaxCalculation {
        subtotal,
        tax,
        total: subtotal + tax,
    }
}
```

### テスト

```rust
#[test]
fn calculate_total_with_tax_computes_correctly() {
    let items = vec![
        Item { name: "商品A".to_string(), price: 1000.0 },
        Item { name: "商品B".to_string(), price: 2000.0 },
    ];
    let result = calculate_total_with_tax(&items, 0.1);

    assert_eq!(3000.0, result.subtotal);
    assert_eq!(300.0, result.tax);
    assert_eq!(3300.0, result.total);
}
```

## 9. リファクタリングパターン - データ駆動の実装

### Before: 複雑な条件分岐

```rust
fn calculate_shipping_before(total: f64, weight: f64, region: &Region) -> u32 {
    if total >= 10000.0 {
        0
    } else {
        match (region, weight < 5.0) {
            (Region::Local, true) => 300,
            (Region::Local, false) => 500,
            (Region::Domestic, true) => 500,
            (Region::Domestic, false) => 800,
            (Region::International, true) => 2000,
            (Region::International, false) => 3000,
        }
    }
}
```

### After: データ駆動の実装

```rust
use std::collections::HashMap;

#[derive(Debug, Clone, Hash, Eq, PartialEq)]
pub enum Region {
    Local,
    Domestic,
    International,
}

#[derive(Debug, Clone)]
pub struct ShippingOrder {
    pub total: f64,
    pub weight: f64,
    pub region: Region,
}

pub fn is_free_shipping(total: f64) -> bool {
    total >= 10000.0
}

fn shipping_rates() -> HashMap<(Region, bool), u32> {
    HashMap::from([
        ((Region::Local, true), 300),
        ((Region::Local, false), 500),
        ((Region::Domestic, true), 500),
        ((Region::Domestic, false), 800),
        ((Region::International, true), 2000),
        ((Region::International, false), 3000),
    ])
}

pub fn calculate_shipping(order: &ShippingOrder) -> u32 {
    if is_free_shipping(order.total) {
        return 0;
    }

    let is_light = order.weight < 5.0;
    let rates = shipping_rates();
    rates
        .get(&(order.region.clone(), is_light))
        .copied()
        .unwrap_or(500)
}
```

## 10. パスワードバリデーター - ルールの合成

```rust
/// バリデーションルールの型
pub type Rule = fn(&str) -> Option<String>;

pub fn min_length(min: usize) -> Rule {
    fn check(password: &str) -> Option<String> {
        if password.len() >= 8 {
            None
        } else {
            Some(format!("Password must be at least 8 characters"))
        }
    }
    check
}

pub fn has_uppercase(password: &str) -> Option<String> {
    if password.chars().any(|c| c.is_uppercase()) {
        None
    } else {
        Some("Password must contain at least one uppercase letter".to_string())
    }
}

pub fn has_lowercase(password: &str) -> Option<String> {
    if password.chars().any(|c| c.is_lowercase()) {
        None
    } else {
        Some("Password must contain at least one lowercase letter".to_string())
    }
}

pub fn has_digit(password: &str) -> Option<String> {
    if password.chars().any(|c| c.is_ascii_digit()) {
        None
    } else {
        Some("Password must contain at least one digit".to_string())
    }
}

pub fn validate_password(password: &str, rules: &[Rule]) -> Result<String, Vec<String>> {
    let errors: Vec<String> = rules.iter().filter_map(|rule| rule(password)).collect();
    if errors.is_empty() {
        Ok(password.to_string())
    } else {
        Err(errors)
    }
}

pub fn validate_with_defaults(password: &str) -> Result<String, Vec<String>> {
    let rules: Vec<Rule> = vec![min_length(8), has_uppercase, has_lowercase, has_digit];
    validate_password(password, &rules)
}
```

## TDD のベストプラクティス

### 1. 小さなステップで進む

- 一度に1つのテストだけを追加
- テストが通ったら次のテストへ

### 2. テスト名は仕様として読める

```rust
#[test]
fn orders_over_10000_yen_have_free_shipping() { ... }

#[test]
fn negative_numbers_cause_panic() { ... }

#[test]
fn perfect_game_scores_300() { ... }
```

### 3. 純粋関数を優先

- 副作用を持つ関数は最小限に
- 副作用は境界に追い出す

### 4. エッジケースをテスト

```rust
#[test]
fn empty_string_returns_0() { ... }

#[test]
fn empty_list_returns_empty_list() { ... }

#[test]
fn boundary_values_work_correctly() { ... }
```

## Clojure/Scala との比較

| 概念 | Clojure | Scala | Rust |
|------|---------|-------|------|
| テストフレームワーク | speclj, clojure.test | ScalaTest | `#[cfg(test)]`, proptest |
| テスト構文 | `(it "..." (should= ...))` | `test("...") { ... shouldBe ... }` | `#[test] fn ...() { assert_eq!(...) }` |
| 例外テスト | `(should-throw ...)` | `intercept[...] { ... }` | `#[should_panic(expected = "...")]` |
| データ構造 | 永続化データ構造（デフォルト） | `case class` + `copy` | `struct` + `clone` |
| ループ | `loop/recur` | `@annotation.tailrec` | ループ / 再帰 |
| パターンマッチ | `cond`, `case` | `match` | `match` |
| 型システム | 動的型付け | 静的型付け | 静的型付け + 所有権 |

## Rust 特有のパターン

### `#[should_panic]` による例外テスト

Rust では `#[should_panic]` アトリビュートでパニックをテストできる：

```rust
#[test]
#[should_panic(expected = "negatives not allowed")]
fn negative_numbers_cause_panic() {
    string_calculator_add("1,-2,3");
}
```

### 所有権と不変性のコンパイル時保証

```rust
pub fn pop(&self) -> Option<(T, Self)> {
    // self は不変参照のため、元のスタックは変更されない
    // 新しいスタックを返すことで不変性を保証
}
```

### イテレータチェーンによる関数型パイプライン

```rust
pub fn fizzbuzz_sequence(n: u32) -> Vec<String> {
    (1..=n).map(fizzbuzz).collect()
}
```

## まとめ

本章では、TDD と関数型プログラミングについて学びました：

1. **Red-Green-Refactor**: 基本サイクル
2. **FizzBuzz**: 典型的な TDD 例
3. **ローマ数字**: データ駆動の実装
4. **ボウリング**: 複雑なビジネスロジック
5. **素数**: シンプルな関数設計
6. **スタック/キュー**: 不変データ構造
7. **文字列電卓**: 段階的な要件追加
8. **純粋関数**: テスト容易性
9. **リファクタリング**: 条件分岐の整理
10. **パスワードバリデーター**: ルールの合成

関数型プログラミングと TDD の組み合わせにより、信頼性の高いコードを効率的に開発できます。

## 次章予告

次章から第3部「デザインパターン - 構造パターン」に入ります。Composite パターンを関数型スタイルで実装する方法を学びます。
