---
type: Article
title: "第17章: レンタルビデオシステム"
description: "本章では、レンタルビデオシステムを通じて、価格計算、明細書生成、ポイント計算など、複雑なビジネスロジックを関数型で実装します。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第17章: レンタルビデオシステム

## はじめに

本章では、レンタルビデオシステムを通じて、価格計算、明細書生成、ポイント計算など、複雑なビジネスロジックを関数型で実装します。Strategy パターンを使って、映画の種類ごとに異なる価格計算を行います。

## 1. データ構造

### 映画とレンタル

```rust
use rust_decimal::Decimal;

/// 映画の価格コード
#[derive(Debug, Clone, PartialEq)]
pub enum PriceCode {
    Regular,
    NewRelease,
    Children,
}

/// 映画
#[derive(Debug, Clone)]
pub struct Movie {
    pub title: String,
    pub price_code: PriceCode,
}

/// レンタル
#[derive(Debug, Clone)]
pub struct Rental {
    pub movie: Movie,
    pub days_rented: i32,
}

/// 顧客
#[derive(Debug, Clone)]
pub struct Customer {
    pub name: String,
    pub rentals: Vec<Rental>,
}
```

## 2. 価格計算 Strategy

```rust
/// 価格計算トレイト
pub trait PricingStrategy {
    fn calculate_price(&self, days_rented: i32) -> Decimal;
    fn calculate_points(&self, days_rented: i32) -> i32;
}

/// 通常料金
pub struct RegularPricing;

impl PricingStrategy for RegularPricing {
    fn calculate_price(&self, days_rented: i32) -> Decimal {
        let mut amount = Decimal::from(2);
        if days_rented > 2 {
            amount += Decimal::from(days_rented - 2) * Decimal::from_str("1.5").unwrap();
        }
        amount
    }

    fn calculate_points(&self, _days_rented: i32) -> i32 {
        1
    }
}

/// 新作料金
pub struct NewReleasePricing;

impl PricingStrategy for NewReleasePricing {
    fn calculate_price(&self, days_rented: i32) -> Decimal {
        Decimal::from(days_rented) * Decimal::from(3)
    }

    fn calculate_points(&self, days_rented: i32) -> i32 {
        if days_rented > 1 { 2 } else { 1 }
    }
}

/// 子供向け料金
pub struct ChildrenPricing;

impl PricingStrategy for ChildrenPricing {
    fn calculate_price(&self, days_rented: i32) -> Decimal {
        let mut amount = Decimal::from_str("1.5").unwrap();
        if days_rented > 3 {
            amount += Decimal::from(days_rented - 3) * Decimal::from_str("1.5").unwrap();
        }
        amount
    }

    fn calculate_points(&self, _days_rented: i32) -> i32 {
        1
    }
}

/// 価格コードから Strategy を取得
pub fn get_pricing_strategy(price_code: &PriceCode) -> Box<dyn PricingStrategy> {
    match price_code {
        PriceCode::Regular => Box::new(RegularPricing),
        PriceCode::NewRelease => Box::new(NewReleasePricing),
        PriceCode::Children => Box::new(ChildrenPricing),
    }
}
```

## 3. 関数型アプローチ

```rust
/// レンタル料金を計算
pub fn calculate_rental_amount(rental: &Rental) -> Decimal {
    match rental.movie.price_code {
        PriceCode::Regular => {
            let mut amount = Decimal::from(2);
            if rental.days_rented > 2 {
                amount += Decimal::from(rental.days_rented - 2) * Decimal::from_str("1.5").unwrap();
            }
            amount
        }
        PriceCode::NewRelease => {
            Decimal::from(rental.days_rented) * Decimal::from(3)
        }
        PriceCode::Children => {
            let mut amount = Decimal::from_str("1.5").unwrap();
            if rental.days_rented > 3 {
                amount += Decimal::from(rental.days_rented - 3) * Decimal::from_str("1.5").unwrap();
            }
            amount
        }
    }
}

/// ポイントを計算
pub fn calculate_rental_points(rental: &Rental) -> i32 {
    let mut points = 1;
    if rental.movie.price_code == PriceCode::NewRelease && rental.days_rented > 1 {
        points += 1;
    }
    points
}

/// 顧客の合計金額を計算
pub fn calculate_total_amount(customer: &Customer) -> Decimal {
    customer.rentals.iter().map(calculate_rental_amount).sum()
}

/// 顧客の合計ポイントを計算
pub fn calculate_total_points(customer: &Customer) -> i32 {
    customer.rentals.iter().map(calculate_rental_points).sum()
}
```

## 4. 明細書生成

### テキスト形式

```rust
/// 明細書を生成（テキスト形式）
pub fn statement(customer: &Customer) -> String {
    let mut result = format!("Rental Record for {}\n", customer.name);

    for rental in &customer.rentals {
        let amount = calculate_rental_amount(rental);
        result.push_str(&format!(
            "\t{}\t{}\n",
            rental.movie.title,
            amount
        ));
    }

    let total_amount = calculate_total_amount(customer);
    let total_points = calculate_total_points(customer);

    result.push_str(&format!("Amount owed is {}\n", total_amount));
    result.push_str(&format!("You earned {} frequent renter points", total_points));

    result
}
```

### HTML 形式

```rust
/// 明細書を生成（HTML形式）
pub fn html_statement(customer: &Customer) -> String {
    let mut result = format!("<h1>Rental Record for <em>{}</em></h1>\n", customer.name);
    result.push_str("<ul>\n");

    for rental in &customer.rentals {
        let amount = calculate_rental_amount(rental);
        result.push_str(&format!(
            "<li>{} - {}</li>\n",
            rental.movie.title,
            amount
        ));
    }

    result.push_str("</ul>\n");

    let total_amount = calculate_total_amount(customer);
    let total_points = calculate_total_points(customer);

    result.push_str(&format!("<p>Amount owed is <strong>{}</strong></p>\n", total_amount));
    result.push_str(&format!("<p>You earned <strong>{}</strong> frequent renter points</p>", total_points));

    result
}
```

## 5. フォーマッター Strategy

```rust
/// 明細書フォーマッター
pub trait StatementFormatter {
    fn format_header(&self, customer_name: &str) -> String;
    fn format_rental(&self, movie_title: &str, amount: Decimal) -> String;
    fn format_footer(&self, total_amount: Decimal, total_points: i32) -> String;
}

/// テキストフォーマッター
pub struct TextFormatter;

impl StatementFormatter for TextFormatter {
    fn format_header(&self, customer_name: &str) -> String {
        format!("Rental Record for {}\n", customer_name)
    }

    fn format_rental(&self, movie_title: &str, amount: Decimal) -> String {
        format!("\t{}\t{}\n", movie_title, amount)
    }

    fn format_footer(&self, total_amount: Decimal, total_points: i32) -> String {
        format!(
            "Amount owed is {}\nYou earned {} frequent renter points",
            total_amount, total_points
        )
    }
}

/// HTML フォーマッター
pub struct HtmlFormatter;

impl StatementFormatter for HtmlFormatter {
    fn format_header(&self, customer_name: &str) -> String {
        format!("<h1>Rental Record for <em>{}</em></h1>\n<ul>\n", customer_name)
    }

    fn format_rental(&self, movie_title: &str, amount: Decimal) -> String {
        format!("<li>{} - {}</li>\n", movie_title, amount)
    }

    fn format_footer(&self, total_amount: Decimal, total_points: i32) -> String {
        format!(
            "</ul>\n<p>Amount owed is <strong>{}</strong></p>\n<p>You earned <strong>{}</strong> frequent renter points</p>",
            total_amount, total_points
        )
    }
}

/// フォーマッターを使って明細書を生成
pub fn statement_with_formatter(customer: &Customer, formatter: &dyn StatementFormatter) -> String {
    let mut result = formatter.format_header(&customer.name);

    for rental in &customer.rentals {
        let amount = calculate_rental_amount(rental);
        result.push_str(&formatter.format_rental(&rental.movie.title, amount));
    }

    let total_amount = calculate_total_amount(customer);
    let total_points = calculate_total_points(customer);
    result.push_str(&formatter.format_footer(total_amount, total_points));

    result
}
```

## 6. パターンの適用

1. **Strategy**: 価格計算、明細書フォーマット
2. **ADT**: PriceCode による映画種別の表現
3. **不変データ**: Movie, Rental, Customer
4. **高階関数**: map, sum による集計

## まとめ

本章では、レンタルビデオシステムを通じて：

1. Strategy パターンによる価格計算の切り替え
2. ADT による映画種別の型安全な表現
3. フォーマッター Strategy による出力形式の切り替え
4. 純粋関数による計算ロジック

を学びました。

## 参考コード

- ソースコード: `apps/rust/part6/src/chapter17.rs`

## 次章予告

次章では、**並行処理システム**を通じて、関数型での並行プログラミングを学びます。
