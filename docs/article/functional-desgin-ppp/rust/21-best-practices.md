---
type: Article
title: "第21章: ベストプラクティス"
description: "本章では、Rust における関数型デザインのベストプラクティスをまとめます。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第21章: ベストプラクティス

## はじめに

本章では、Rust における関数型デザインのベストプラクティスをまとめます。データ中心設計、純粋関数、テスト可能な設計など、実践的なガイドラインを紹介します。

## 1. データ中心設計

### シンプルなデータ構造

```rust
use rust_decimal::Decimal;

/// ユーザー（シンプルなデータ構造）
#[derive(Debug, Clone, PartialEq)]
pub struct User {
    pub id: String,
    pub name: String,
    pub email: String,
    pub created_at: i64,
}

/// 注文アイテム
#[derive(Debug, Clone, PartialEq)]
pub struct OrderItem {
    pub product_id: String,
    pub quantity: i32,
    pub price: Decimal,
}

/// 注文
#[derive(Debug, Clone, PartialEq)]
pub struct Order {
    pub id: String,
    pub user_id: String,
    pub items: Vec<OrderItem>,
    pub status: OrderStatus,
}

#[derive(Debug, Clone, PartialEq, Default)]
pub enum OrderStatus {
    #[default]
    Pending,
    Processing,
    Completed,
    Cancelled,
}
```

## 2. 小さな純粋関数

```rust
pub mod order_calculations {
    use super::*;

    /// アイテムの合計を計算
    pub fn calculate_item_total(item: &OrderItem) -> Decimal {
        item.price * Decimal::from(item.quantity)
    }

    /// 注文の合計を計算
    pub fn calculate_order_total(order: &Order) -> Decimal {
        order.items.iter().map(calculate_item_total).sum()
    }

    /// 割引を適用
    pub fn apply_discount(total: Decimal, discount_rate: Decimal) -> Decimal {
        total * (Decimal::ONE - discount_rate)
    }

    /// 税金を適用
    pub fn apply_tax(total: Decimal, tax_rate: Decimal) -> Decimal {
        total * (Decimal::ONE + tax_rate)
    }
}
```

## 3. データ検証

```rust
/// 検証結果
pub enum ValidationResult<T> {
    Valid(T),
    Invalid(Vec<String>),
}

impl<T> ValidationResult<T> {
    pub fn is_valid(&self) -> bool {
        matches!(self, ValidationResult::Valid(_))
    }

    pub fn map<U, F: FnOnce(T) -> U>(self, f: F) -> ValidationResult<U> {
        match self {
            ValidationResult::Valid(v) => ValidationResult::Valid(f(v)),
            ValidationResult::Invalid(errs) => ValidationResult::Invalid(errs),
        }
    }
}

pub mod validation {
    use super::*;
    use regex::Regex;

    pub fn valid_email(email: &str) -> bool {
        let re = Regex::new(r"^[^@]+@[^@]+\.[^@]+$").unwrap();
        re.is_match(email)
    }

    pub fn validate_user(user: User) -> ValidationResult<User> {
        let mut errors = Vec::new();

        if user.name.trim().is_empty() {
            errors.push("Name is required".to_string());
        }
        if !valid_email(&user.email) {
            errors.push("Invalid email format".to_string());
        }

        if errors.is_empty() {
            ValidationResult::Valid(user)
        } else {
            ValidationResult::Invalid(errors)
        }
    }
}
```

## 4. イミュータブルな更新

```rust
pub mod immutable_updates {
    use super::*;

    pub fn update_user_email(user: User, new_email: &str) -> Result<User, String> {
        if validation::valid_email(new_email) {
            Ok(User {
                email: new_email.to_string(),
                ..user
            })
        } else {
            Err("Invalid email format".to_string())
        }
    }

    pub fn cancel_order(order: Order) -> Result<Order, String> {
        if order.status == OrderStatus::Pending {
            Ok(Order {
                status: OrderStatus::Cancelled,
                ..order
            })
        } else {
            Err("Only pending orders can be cancelled".to_string())
        }
    }
}
```

## 5. 高階関数によるデコレーション

```rust
pub mod function_decorators {
    use std::collections::HashMap;
    use std::sync::{Arc, Mutex};
    use std::time::Instant;

    /// ロギングデコレータ
    pub fn with_logging<A: Clone + std::fmt::Debug, B: std::fmt::Debug>(
        f: impl Fn(A) -> B,
        mut logger: impl FnMut(String),
    ) -> impl FnMut(A) -> B {
        move |a: A| {
            logger(format!("Input: {:?}", a));
            let result = f(a);
            logger(format!("Output: {:?}", result));
            result
        }
    }

    /// キャッシュデコレータ
    pub fn with_cache<A: Clone + Eq + std::hash::Hash + 'static, B: Clone + 'static>(
        f: impl Fn(A) -> B + 'static,
    ) -> impl FnMut(A) -> B {
        let cache: Arc<Mutex<HashMap<A, B>>> = Arc::new(Mutex::new(HashMap::new()));
        move |a: A| {
            let mut cache = cache.lock().unwrap();
            if let Some(v) = cache.get(&a) {
                v.clone()
            } else {
                let result = f(a.clone());
                cache.insert(a, result.clone());
                result
            }
        }
    }
}
```

## 6. 依存性注入

```rust
/// リポジトリトレイト
pub trait Repository<T: Clone, ID> {
    fn find_by_id(&self, id: &ID) -> Option<T>;
    fn find_all(&self) -> Vec<T>;
    fn save(&mut self, entity: T) -> T;
    fn delete(&mut self, id: &ID) -> bool;
}

/// 時間プロバイダー
pub trait Clock {
    fn now(&self) -> i64;
}

/// ID生成器
pub trait IdGenerator {
    fn generate(&mut self) -> String;
}

/// テスト用の実装
pub struct FixedClock {
    time: i64,
}

impl Clock for FixedClock {
    fn now(&self) -> i64 {
        self.time
    }
}

pub struct SequentialIdGenerator {
    prefix: String,
    counter: usize,
}

impl IdGenerator for SequentialIdGenerator {
    fn generate(&mut self) -> String {
        self.counter += 1;
        format!("{}-{}", self.prefix, self.counter)
    }
}
```

## 7. サービス層

```rust
/// ユーザーサービス
pub struct UserService<R, C, I>
where
    R: Repository<User, String>,
    C: Clock,
    I: IdGenerator,
{
    repository: R,
    clock: C,
    id_generator: I,
}

impl<R, C, I> UserService<R, C, I>
where
    R: Repository<User, String>,
    C: Clock,
    I: IdGenerator,
{
    pub fn new(repository: R, clock: C, id_generator: I) -> Self {
        Self { repository, clock, id_generator }
    }

    pub fn create_user(&mut self, name: &str, email: &str) -> Result<User, Vec<String>> {
        let user = User {
            id: self.id_generator.generate(),
            name: name.to_string(),
            email: email.to_string(),
            created_at: self.clock.now(),
        };

        match validation::validate_user(user) {
            ValidationResult::Valid(u) => Ok(self.repository.save(u)),
            ValidationResult::Invalid(errors) => Err(errors),
        }
    }
}
```

## 8. 結果型

```rust
/// カスタム Result 型
#[derive(Debug, Clone)]
pub enum Result2<E, A> {
    Success(A),
    Failure(E),
}

impl<E, A> Result2<E, A> {
    pub fn map<B, F: FnOnce(A) -> B>(self, f: F) -> Result2<E, B> {
        match self {
            Result2::Success(a) => Result2::Success(f(a)),
            Result2::Failure(e) => Result2::Failure(e),
        }
    }

    pub fn flat_map<B, F: FnOnce(A) -> Result2<E, B>>(self, f: F) -> Result2<E, B> {
        match self {
            Result2::Success(a) => f(a),
            Result2::Failure(e) => Result2::Failure(e),
        }
    }
}
```

## 9. ベストプラクティスまとめ

1. **データ中心**: ビジネスロジックをデータ構造で表現
2. **純粋関数**: 副作用を分離し、テスト容易性を向上
3. **不変データ**: 状態変更は新しいデータを作成
4. **型で表現**: enum で状態、トレイトで振る舞いを表現
5. **依存性注入**: テスト可能な設計
6. **小さな関数**: 単一責任、合成可能

## まとめ

本章では、Rust における関数型デザインのベストプラクティスを学びました：

1. データ中心設計
2. 純粋関数による計算
3. 検証と不変更新
4. 高階関数によるデコレーション
5. 依存性注入によるテスト可能性

## 参考コード

- ソースコード: `apps/rust/part7/src/chapter21.rs`

## 次章予告

次章では、**OO から FP への移行**について学びます。既存のオブジェクト指向コードを関数型に移行する方法を探ります。
