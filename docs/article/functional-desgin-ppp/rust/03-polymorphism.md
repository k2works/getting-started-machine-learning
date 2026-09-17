---
type: Article
title: "第3章: 多態性とディスパッチ"
description: "多態性（ポリモーフィズム）は、同じインターフェースで異なる振る舞いを実現する強力な概念です。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第3章: 多態性とディスパッチ

## はじめに

多態性（ポリモーフィズム）は、同じインターフェースで異なる振る舞いを実現する強力な概念です。Rust では、enum、trait、ジェネリクスという複数のメカニズムで多態性を実現します。

本章では、これらのメカニズムを使い分けて、柔軟で拡張性の高いコードを書く方法を学びます。

## 1. Enum による多態性（代数的データ型）

Rust の `enum` は、有限の型のバリエーションを定義するのに最適です。コンパイラがパターンマッチングの網羅性をチェックしてくれます。

### 基本的な使い方

```rust
/// 図形を表す enum
#[derive(Debug, Clone, PartialEq)]
pub enum Shape {
    Rectangle { width: f64, height: f64 },
    Circle { radius: f64 },
    Triangle { base: f64, height: f64 },
}

impl Shape {
    /// 図形の面積を計算する
    pub fn area(&self) -> f64 {
        match self {
            Shape::Rectangle { width, height } => width * height,
            Shape::Circle { radius } => std::f64::consts::PI * radius * radius,
            Shape::Triangle { base, height } => base * height / 2.0,
        }
    }
}

// 使用例
let rect = Shape::Rectangle { width: 4.0, height: 5.0 };
rect.area()  // => 20.0

let circle = Shape::Circle { radius: 3.0 };
circle.area()  // => 28.27...
```

### Enum の利点

1. **網羅性チェック**: コンパイラがすべてのケースがカバーされているか確認
2. **型安全性**: 不正な型の値を渡せない
3. **パターンマッチング**: 分岐を簡潔に記述可能

## 2. 複合ディスパッチ

タプルを使ったパターンマッチングで、複数の値に基づくディスパッチを実現できます。

```rust
#[derive(Debug, Clone, PartialEq)]
pub enum PaymentMethod {
    CreditCard,
    BankTransfer,
    Cash,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Currency {
    JPY,
    USD,
    EUR,
}

impl Payment {
    /// 支払いを処理する（複合ディスパッチ）
    pub fn process(&self) -> PaymentResult {
        match (&self.method, &self.currency) {
            (PaymentMethod::CreditCard, Currency::JPY) => PaymentResult {
                status: "processed".to_string(),
                message: "クレジットカード（円）で処理しました".to_string(),
                amount: self.amount,
                converted: None,
            },
            (PaymentMethod::CreditCard, Currency::USD) => PaymentResult {
                status: "processed".to_string(),
                message: "Credit card (USD) processed".to_string(),
                amount: self.amount,
                converted: Some(self.amount * 150),
            },
            (PaymentMethod::BankTransfer, Currency::JPY) => PaymentResult {
                status: "pending".to_string(),
                message: "銀行振込を受け付けました".to_string(),
                amount: self.amount,
                converted: None,
            },
            _ => PaymentResult {
                status: "error".to_string(),
                message: "サポートされていない支払い方法です".to_string(),
                amount: self.amount,
                converted: None,
            },
        }
    }
}
```

## 3. Trait による階層的ディスパッチ

Rust では trait を使って型の共通インターフェースを定義し、各型に固有の振る舞いを持たせることができます。

```rust
/// 口座の共通トレイト
pub trait Account {
    fn balance(&self) -> i32;
    fn interest_rate(&self) -> f64;

    fn calculate_interest(&self) -> f64 {
        self.balance() as f64 * self.interest_rate()
    }
}

/// 普通預金口座
#[derive(Debug, Clone, PartialEq)]
pub struct SavingsAccount {
    pub balance: i32,
}

impl Account for SavingsAccount {
    fn balance(&self) -> i32 { self.balance }
    fn interest_rate(&self) -> f64 { 0.02 }
}

/// プレミアム普通預金口座
#[derive(Debug, Clone, PartialEq)]
pub struct PremiumSavingsAccount {
    pub balance: i32,
}

impl Account for PremiumSavingsAccount {
    fn balance(&self) -> i32 { self.balance }
    fn interest_rate(&self) -> f64 { 0.05 }
}

// 使用例
let savings = SavingsAccount { balance: 10000 };
savings.calculate_interest()  // => 200.0

let premium = PremiumSavingsAccount { balance: 10000 };
premium.calculate_interest()  // => 500.0
```

## 4. Trait（Protocol に相当）

Trait は、特定の操作セットを定義するインターフェースです。

### Trait の定義

```rust
/// バウンディングボックス
#[derive(Debug, Clone, PartialEq)]
pub struct BoundingBox {
    pub x: f64,
    pub y: f64,
    pub width: f64,
    pub height: f64,
}

/// 描画可能なオブジェクトのトレイト
pub trait Drawable {
    fn draw(&self) -> String;
    fn bounding_box(&self) -> BoundingBox;
}

/// 変換可能なオブジェクトのトレイト
pub trait Transformable: Sized {
    fn translate(&self, dx: f64, dy: f64) -> Self;
    fn scale(&self, factor: f64) -> Self;
    fn rotate(&self, angle: f64) -> Self;
}
```

### Trait の利点

1. **パフォーマンス**: 静的ディスパッチによる高速な実行
2. **明確なコントラクト**: 実装すべきメソッドが明示的
3. **複数実装**: 一つの型が複数の trait を実装可能

## 5. Trait を実装する構造体

```rust
#[derive(Debug, Clone, PartialEq)]
pub struct DrawableRectangle {
    pub x: f64,
    pub y: f64,
    pub width: f64,
    pub height: f64,
}

impl Drawable for DrawableRectangle {
    fn draw(&self) -> String {
        format!(
            "Rectangle at ({},{}) with size {}x{}",
            self.x, self.y, self.width, self.height
        )
    }

    fn bounding_box(&self) -> BoundingBox {
        BoundingBox {
            x: self.x,
            y: self.y,
            width: self.width,
            height: self.height,
        }
    }
}

impl Transformable for DrawableRectangle {
    fn translate(&self, dx: f64, dy: f64) -> Self {
        DrawableRectangle {
            x: self.x + dx,
            y: self.y + dy,
            ..*self
        }
    }

    fn scale(&self, factor: f64) -> Self {
        DrawableRectangle {
            width: self.width * factor,
            height: self.height * factor,
            ..*self
        }
    }

    fn rotate(&self, _angle: f64) -> Self {
        self.clone()
    }
}

// 使用例
let rect = DrawableRectangle::new(10.0, 20.0, 100.0, 50.0);
rect.draw()           // => "Rectangle at (10,20) with size 100x50"
rect.translate(5.0, 10.0)  // => DrawableRectangle { x: 15.0, y: 30.0, ... }
```

## 6. 既存型への拡張（Extension Trait）

Rust では既存の型に後から振る舞いを追加できます。

```rust
/// 文字列に変換可能なトレイト
pub trait Stringable {
    fn to_custom_string(&self) -> String;
}

impl<T: std::fmt::Display> Stringable for Vec<T> {
    fn to_custom_string(&self) -> String {
        let parts: Vec<String> = self.iter().map(|x| x.to_string()).collect();
        format!("[{}]", parts.join(", "))
    }
}

impl Stringable for i32 {
    fn to_custom_string(&self) -> String {
        self.to_string()
    }
}

impl<T: Stringable> Stringable for Option<T> {
    fn to_custom_string(&self) -> String {
        match self {
            Some(v) => v.to_custom_string(),
            None => "nil".to_string(),
        }
    }
}

// 使用例
let v = vec![1, 2, 3];
v.to_custom_string()  // => "[1, 2, 3]"

let opt: Option<i32> = Some(42);
opt.to_custom_string()  // => "42"

let none: Option<i32> = None;
none.to_custom_string()  // => "nil"
```

## 7. コンポーネントパターン

Trait を使って、コンポーネントのライフサイクル管理を実現します。

```rust
/// ライフサイクル管理トレイト
pub trait Lifecycle: Sized {
    fn start(self) -> Self;
    fn stop(self) -> Self;
}

#[derive(Debug, Clone, PartialEq)]
pub struct DatabaseConnection {
    pub host: String,
    pub port: u16,
    pub connected: bool,
}

impl Lifecycle for DatabaseConnection {
    fn start(mut self) -> Self {
        println!("データベースに接続中: {} : {}", self.host, self.port);
        self.connected = true;
        self
    }

    fn stop(mut self) -> Self {
        println!("データベース接続を切断中");
        self.connected = false;
        self
    }
}

// 使用例
let db = DatabaseConnection::new("localhost", 5432);
let db = db.start();   // データベースに接続中
let db = db.stop();    // データベース接続を切断中
```

## 8. 条件分岐の置き換え（Strategy パターン）

多態性を使って、switch/case 文による型判定を排除できます。

### Before（条件分岐）

```rust
// 悪い例：型による条件分岐
fn send_notification_bad(notification_type: &str, message: &str, to: &str) -> Result<(), String> {
    match notification_type {
        "email" => { /* メール送信 */ Ok(()) }
        "sms" => { /* SMS送信 */ Ok(()) }
        "push" => { /* プッシュ通知 */ Ok(()) }
        _ => Err("未知の通知タイプ".to_string())
    }
}
```

### After（多態性）

```rust
/// 通知送信トレイト
pub trait NotificationSender {
    fn send_notification(&self, message: &str) -> NotificationResult;
    fn delivery_time(&self) -> &str;
}

#[derive(Debug, Clone, PartialEq)]
pub struct EmailNotification {
    pub to: String,
    pub subject: String,
}

impl NotificationSender for EmailNotification {
    fn send_notification(&self, message: &str) -> NotificationResult {
        NotificationResult {
            notification_type: "email".to_string(),
            to: self.to.clone(),
            body: message.to_string(),
            status: "sent".to_string(),
            subject: Some(self.subject.clone()),
        }
    }

    fn delivery_time(&self) -> &str {
        "1-2分"
    }
}

// ファクトリ関数
pub fn create_notification(
    notification_type: &str,
    to: &str,
    subject: Option<&str>,
) -> Result<Box<dyn NotificationSender>, String> {
    match notification_type {
        "email" => Ok(Box::new(EmailNotification::new(to, subject.unwrap_or("通知")))),
        "sms" => Ok(Box::new(SMSNotification::new(to))),
        "push" => Ok(Box::new(PushNotification::new(to))),
        _ => Err(format!("未知の通知タイプ: {}", notification_type)),
    }
}

// 使用例
let email = create_notification("email", "user@example.com", Some("お知らせ")).unwrap();
email.send_notification("重要なお知らせ")
```

## 9. Enum、Trait、ジェネリクスの使い分け

| 特徴 | Enum | Trait | ジェネリクス |
|------|------|-------|------------|
| ディスパッチ | 値に基づく | 型に基づく | コンパイル時 |
| 拡張性 | クローズド | オープン | オープン |
| パフォーマンス | 良い | 良い〜最良 | 最良 |
| 用途 | 有限の型バリエーション | 共通インターフェース | 汎用的な処理 |

### 使い分けの指針

- **Enum**: 有限で固定された型のバリエーション（Option、Result など）
- **Trait**: 新しい型が追加される可能性がある場合、共通のインターフェースが必要な場合
- **Trait + dyn**: 実行時に異なる型を扱う必要がある場合（動的ディスパッチ）
- **ジェネリクス + Trait bounds**: コンパイル時に型が決まり、最大のパフォーマンスが必要な場合

## まとめ

本章では、Rust における多態性について学びました：

1. **Enum**: 代数的データ型による有限の型バリエーション
2. **複合ディスパッチ**: タプルによる複数の値に基づくパターンマッチング
3. **Trait**: 共通インターフェースの定義
4. **Extension Trait**: 既存型への振る舞いの追加
5. **コンポーネントパターン**: ライフサイクル管理
6. **Strategy パターン**: 条件分岐の置き換え

これらのメカニズムを適切に使い分けることで、拡張性が高く保守しやすいコードを実現できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/rust/part1/src/chapter03.rs`

## 次章予告

次章から第2部「仕様とテスト」に入ります。Rust のデータバリデーションと仕様定義について学びます。
