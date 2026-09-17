---
type: Article
title: "第2章: 関数合成と高階関数"
description: "関数型プログラミングの真髄は、小さな関数を組み合わせて複雑な処理を構築することにあります。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第2章: 関数合成と高階関数

## はじめに

関数型プログラミングの真髄は、小さな関数を組み合わせて複雑な処理を構築することにあります。本章では、Rust における関数合成のテクニックと高階関数の活用方法を学びます。

## 1. 関数合成の基本

### クロージャによる関数の連結

Rust ではクロージャを返す関数を使用して関数を合成できます。

```rust
/// 税金を追加する
pub fn add_tax(rate: f64) -> impl Fn(f64) -> f64 {
    move |amount| amount * (1.0 + rate)
}

/// 割引を適用する
pub fn apply_discount_rate(rate: f64) -> impl Fn(f64) -> f64 {
    move |amount| amount * (1.0 - rate)
}

/// 円単位に丸める
pub fn round_to_yen(amount: f64) -> i64 {
    amount.round() as i64
}

/// 最終価格を計算する（関数合成）
pub fn calculate_final_price(amount: f64) -> i64 {
    let discounted = apply_discount_rate(0.2)(amount);
    let with_tax = add_tax(0.1)(discounted);
    round_to_yen(with_tax)
}

// 使用例
calculate_final_price(1000.0)
// => 880
// 処理順序: 1000 → 20%割引(800) → 10%税込(880) → 丸め(880)
```

### compose ヘルパー関数

2つの関数を合成するヘルパー関数を作成できます：

```rust
/// 関数を合成するヘルパー
pub fn compose<A, B, C, F, G>(f: F, g: G) -> impl Fn(A) -> C
where
    F: Fn(A) -> B,
    G: Fn(B) -> C,
{
    move |x| g(f(x))
}
```

### 関数合成の利点

1. **宣言的な記述**: 処理の流れを関数のチェーンとして表現
2. **再利用性**: 合成した関数を別の場所で再利用可能
3. **テスト容易性**: 各関数を個別にテスト可能

## 2. カリー化と部分適用

### カリー化による引数の固定

Rust ではクロージャを返す関数でカリー化を実現します。

```rust
/// 挨拶する（カリー化版）
pub fn greet_curried(greeting: &str) -> impl Fn(&str) -> String + '_ {
    move |name| format!("{}, {}!", greeting, name)
}

pub fn say_hello(name: &str) -> String {
    greet_curried("Hello")(name)
}

pub fn say_goodbye(name: &str) -> String {
    greet_curried("Goodbye")(name)
}

say_hello("田中")    // => "Hello, 田中!"
say_goodbye("鈴木")  // => "Goodbye, 鈴木!"
```

### 複数引数の部分適用

```rust
#[derive(Debug, Clone, PartialEq)]
pub struct Email {
    pub from: String,
    pub to: String,
    pub subject: String,
    pub body: String,
}

/// メール送信関数（カリー化）
pub fn send_email(from: &str) -> impl Fn(&str) -> Box<dyn Fn(&str) -> Box<dyn Fn(&str) -> Email>> + '_ {
    let from = from.to_string();
    move |to: &str| {
        let from = from.clone();
        let to = to.to_string();
        Box::new(move |subject: &str| {
            let from = from.clone();
            let to = to.clone();
            let subject = subject.to_string();
            Box::new(move |body: &str| Email::new(&from, &to, &subject, body))
        })
    }
}

// 使用例
let send_from_system = send_email("system@example.com");
let send_to_user = send_from_system("user@example.com");
let send_notification = send_to_user("通知");
let email = send_notification("メッセージ本文");
```

## 3. 複数の関数を並列適用

Clojure の `juxt` に相当する機能は、Rust ではタプルや構造体を使って表現します。

```rust
/// 数値リストの統計情報を取得する
pub fn get_stats(numbers: &[i32]) -> (i32, i32, usize, i32, i32) {
    (
        *numbers.first().unwrap_or(&0),
        *numbers.last().unwrap_or(&0),
        numbers.len(),
        *numbers.iter().min().unwrap_or(&0),
        *numbers.iter().max().unwrap_or(&0),
    )
}

get_stats(&[3, 1, 4, 1, 5, 9, 2, 6])
// => (3, 6, 8, 1, 9)
// (最初の値, 最後の値, 要素数, 最小値, 最大値)
```

### 実用例：データ分析

```rust
#[derive(Debug, Clone, PartialEq)]
pub struct PersonAnalysis {
    pub name: String,
    pub age: i32,
    pub category: String,
}

pub fn analyze_person(name: &str, age: i32) -> PersonAnalysis {
    let category = if age >= 18 { "adult" } else { "minor" };
    PersonAnalysis {
        name: name.to_string(),
        age,
        category: category.to_string(),
    }
}

analyze_person("田中", 25)
// => PersonAnalysis { name: "田中", age: 25, category: "adult" }
```

## 4. 高階関数によるデータ処理

高階関数とは、関数を引数として受け取るか、関数を返す関数のことです。

### ログ出力のラッパー

```rust
pub fn process_with_logging<A: std::fmt::Debug, B: std::fmt::Debug>(
    f: impl Fn(A) -> B,
) -> impl Fn(A) -> B {
    move |input| {
        println!("入力: {:?}", input);
        let result = f(input);
        println!("出力: {:?}", result);
        result
    }
}

let double_with_log = process_with_logging(|x: i32| x * 2);
double_with_log(5)
// 入力: 5
// 出力: 10
// => 10
```

### リトライ機能の追加

```rust
pub fn retry<A: Clone, B, E, F>(f: F, max_retries: u32) -> impl Fn(A) -> Result<B, E>
where
    F: Fn(A) -> Result<B, E>,
{
    move |input: A| {
        let mut attempts = 0;
        loop {
            match f(input.clone()) {
                Ok(result) => return Ok(result),
                Err(e) => {
                    if attempts < max_retries {
                        attempts += 1;
                    } else {
                        return Err(e);
                    }
                }
            }
        }
    }
}
```

### TTL 付きメモ化

```rust
use std::cell::RefCell;
use std::collections::HashMap;
use std::hash::Hash;
use std::time::{Duration, Instant};

pub fn memoize_with_ttl<A, B, F>(f: F, ttl: Duration) -> impl Fn(A) -> B
where
    A: Eq + Hash + Clone,
    B: Clone,
    F: Fn(A) -> B,
{
    let cache: RefCell<HashMap<A, (B, Instant)>> = RefCell::new(HashMap::new());

    move |input: A| {
        let now = Instant::now();
        let mut cache_ref = cache.borrow_mut();

        if let Some((value, time)) = cache_ref.get(&input) {
            if now.duration_since(*time) < ttl {
                return value.clone();
            }
        }

        let result = f(input.clone());
        cache_ref.insert(input, (result.clone(), now));
        result
    }
}
```

## 5. パイプライン処理

複数の関数を順次適用するパイプラインを構築します。

```rust
#[derive(Debug, Clone, PartialEq)]
pub struct Order {
    pub items: Vec<OrderItem>,
    pub customer: OrderCustomer,
    pub total: f64,
    pub shipping: i32,
}

pub fn validate_order(order: Order) -> Result<Order, String> {
    if order.items.is_empty() {
        Err("注文にアイテムがありません".to_string())
    } else {
        Ok(order)
    }
}

pub fn calculate_order_total(mut order: Order) -> Order {
    let total: i32 = order.items.iter()
        .map(|item| item.price * item.quantity)
        .sum();
    order.total = total as f64;
    order
}

pub fn apply_order_discount(mut order: Order) -> Order {
    let discount_rate = match order.customer.membership.as_str() {
        "gold" => 0.1,
        "silver" => 0.05,
        "bronze" => 0.02,
        _ => 0.0,
    };
    order.total *= 1.0 - discount_rate;
    order
}

pub fn add_shipping(mut order: Order) -> Order {
    let shipping = if order.total >= 5000.0 { 0 } else { 500 };
    order.shipping = shipping;
    order.total += shipping as f64;
    order
}

/// 注文処理パイプライン
pub fn process_order_pipeline(order: Order) -> Result<Order, String> {
    validate_order(order).map(|o| {
        let o = calculate_order_total(o);
        let o = apply_order_discount(o);
        add_shipping(o)
    })
}
```

## 6. 関数合成によるバリデーション

バリデーションロジックを関数合成で表現します。

```rust
#[derive(Debug, Clone, PartialEq)]
pub struct ValidationResult<T> {
    pub valid: bool,
    pub value: T,
    pub error: Option<String>,
}

pub fn validator<T: Clone>(
    pred: impl Fn(&T) -> bool + 'static,
    error_msg: &str,
) -> Box<dyn Fn(T) -> ValidationResult<T>> {
    let error_msg = error_msg.to_string();
    Box::new(move |value: T| {
        if pred(&value) {
            ValidationResult::ok(value)
        } else {
            ValidationResult::err(value, &error_msg)
        }
    })
}

pub fn combine_validators<T: Clone + 'static>(
    validators: Vec<Box<dyn Fn(T) -> ValidationResult<T>>>,
) -> Box<dyn Fn(T) -> ValidationResult<T>> {
    Box::new(move |value: T| {
        validators.iter().fold(ValidationResult::ok(value), |result, v| {
            if result.valid { v(result.value) } else { result }
        })
    })
}

// 使用例
let validate_positive = validator(|&x: &i32| x > 0, "値は正の数である必要があります");
let validate_under_100 = validator(|&x: &i32| x < 100, "値は100未満である必要があります");

validate_quantity(50)   // => ValidationResult { valid: true, value: 50, error: None }
validate_quantity(-1)   // => ValidationResult { valid: false, value: -1, error: Some("値は正の数である必要があります") }
```

## 7. 関数の変換

関数自体を変換するユーティリティ関数を作成します。

### 引数の順序を反転

```rust
pub fn flip<A, B, C, F>(f: F) -> impl Fn(B, A) -> C
where
    F: Fn(A, B) -> C,
{
    move |b, a| f(a, b)
}

let subtract = |a: i32, b: i32| a - b;
flip(subtract)(3, 5)  // => 2  (5 - 3)
```

### 補関数（complement）

```rust
pub fn complement<A, F>(pred: F) -> impl Fn(A) -> bool
where
    F: Fn(A) -> bool,
{
    move |a| !pred(a)
}

let is_even = |x: i32| x % 2 == 0;
let is_odd = complement(is_even);
is_odd(3)  // => true
```

### カリー化

```rust
pub fn curry<A: Clone + 'static, B: 'static, C: 'static, F: Fn(A, B) -> C + Clone + 'static>(
    f: F,
) -> impl Fn(A) -> Box<dyn Fn(B) -> C> {
    move |a: A| {
        let f = f.clone();
        let a = a.clone();
        Box::new(move |b: B| f(a.clone(), b))
    }
}

let add = |a: i32, b: i32| a + b;
let curried_add = curry(add);
let add_5 = curried_add(5);
add_5(3)  // => 8
```

## 8. 関数合成のパターン

### 述語の合成

```rust
pub fn compose_predicates<T: Clone>(preds: Vec<Box<dyn Fn(&T) -> bool>>) -> impl Fn(&T) -> bool {
    move |x| preds.iter().all(|p| p(x))
}

pub fn compose_predicates_or<T: Clone>(preds: Vec<Box<dyn Fn(&T) -> bool>>) -> impl Fn(&T) -> bool {
    move |x| preds.iter().any(|p| p(x))
}

// 有効な年齢チェック
pub fn valid_age(age: &i32) -> bool {
    let preds: Vec<Box<dyn Fn(&i32) -> bool>> = vec![
        Box::new(|&x| x > 0),
        Box::new(|&x| x <= 150),
    ];
    compose_predicates(preds)(age)
}

valid_age(&25)   // => true
valid_age(&-1)   // => false
valid_age(&200)  // => false

// プレミアム顧客チェック
pub fn premium_customer(customer: &CustomerInfo) -> bool {
    let preds: Vec<Box<dyn Fn(&CustomerInfo) -> bool>> = vec![
        Box::new(|c| c.membership == "gold"),
        Box::new(|c| c.purchase_count >= 100),
        Box::new(|c| c.total_spent >= 100000),
    ];
    compose_predicates_or(preds)(customer)
}

premium_customer(&CustomerInfo::new("gold", 0, 0))           // => true
premium_customer(&CustomerInfo::new("bronze", 100, 0))       // => true
premium_customer(&CustomerInfo::new("bronze", 10, 1000))     // => false
```

## まとめ

本章では、関数合成と高階関数について学びました：

1. **クロージャによる合成**: クロージャを返す関数で関数を合成
2. **カリー化**: 引数を部分適用して特化した関数を作成
3. **タプル/構造体**: 複数の関数を並列適用して結果を取得
4. **高階関数**: ログ、リトライ、メモ化などの横断的関心事を抽象化
5. **パイプライン**: 処理の流れを関数のチェーンとして表現
6. **バリデーション**: 関数合成による柔軟な検証ロジック
7. **述語合成**: AND/OR で複数の条件を組み合わせ

これらのテクニックにより、小さく再利用可能な関数から複雑なビジネスロジックを構築できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/rust/part1/src/chapter02.rs`

## 次章予告

次章では、**多態性とディスパッチ**について学びます。trait、enum、パターンマッチングを活用した柔軟な設計パターンを探ります。
