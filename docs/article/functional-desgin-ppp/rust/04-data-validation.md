---
type: Article
title: "第4章: データ検証"
description: "この章では、Rust の型システムと Result/Option を使ったデータバリデーションの実装方法を学びます。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第4章: データ検証

## 概要

この章では、Rust の型システムと Result/Option を使ったデータバリデーションの実装方法を学びます。Newtype パターン、スマートコンストラクタ、エラー蓄積パターンなど、堅牢なドメインモデルを構築するための技法を扱います。

## 学習目標

1. 基本的なバリデーション関数の実装
2. Validated 型によるエラー蓄積パターン
3. Newtype パターンによる型安全なドメインモデル
4. 列挙型とスマートコンストラクタ
5. 条件付きバリデーション

## 基本概念

### 1. バリデーション結果型

Rust の `Result` 型を使って、バリデーションの成功・失敗を表現します：

```rust
pub type ValidationResult<T> = Result<T, Vec<String>>;

pub fn validate_name(name: &str) -> ValidationResult<String> {
    if name.is_empty() {
        Err(vec!["名前は空にできません".to_string()])
    } else if name.len() > 100 {
        Err(vec!["名前は100文字以内である必要があります".to_string()])
    } else {
        Ok(name.to_string())
    }
}
```

### 2. Validated 型（エラー蓄積）

`Result` は最初のエラーで停止しますが、`Validated` 型を使うと全てのエラーを蓄積できます：

```rust
#[derive(Debug, Clone, PartialEq)]
pub enum Validated<E, A> {
    Valid(A),
    Invalid(Vec<E>),
}

impl<E: Clone, A> Validated<E, A> {
    pub fn combine<B, C, F>(self, other: Validated<E, B>, f: F) -> Validated<E, C>
    where
        F: FnOnce(A, B) -> C,
    {
        match (self, other) {
            (Validated::Valid(a), Validated::Valid(b)) => Validated::Valid(f(a, b)),
            (Validated::Invalid(e1), Validated::Invalid(e2)) => {
                let mut errors = e1;
                errors.extend(e2);
                Validated::Invalid(errors)
            }
            (Validated::Invalid(e), _) => Validated::Invalid(e),
            (_, Validated::Invalid(e)) => Validated::Invalid(e),
        }
    }
}
```

### 3. Newtype パターン

プリミティブ型をラップして型安全性を高めます：

```rust
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ProductId(String);

impl ProductId {
    pub fn new(id: &str) -> Validated<String, ProductId> {
        let pattern = regex::Regex::new(r"^PROD-\d{5}$").unwrap();
        if pattern.is_match(id) {
            Validated::valid(ProductId(id.to_string()))
        } else {
            Validated::invalid(vec![format!(
                "無効な商品ID形式: {} (PROD-XXXXXの形式が必要)",
                id
            )])
        }
    }

    pub fn value(&self) -> &str {
        &self.0
    }
}
```

### 4. ドメインモデルの構築

Newtype を組み合わせて、安全なドメインモデルを構築します：

```rust
#[derive(Debug, Clone, PartialEq)]
pub struct Product {
    pub id: ProductId,
    pub name: ProductName,
    pub price: Price,
    pub description: Option<String>,
    pub category: Option<String>,
}

impl Product {
    pub fn create(
        id: &str,
        name: &str,
        price: f64,
        description: Option<String>,
        category: Option<String>,
    ) -> Validated<String, Product> {
        ProductId::new(id).combine3(
            ProductName::new(name),
            Price::new(price),
            |pid, pname, pprice| Product {
                id: pid,
                name: pname,
                price: pprice,
                description,
                category,
            },
        )
    }
}
```

## 実装パターン

### 列挙型とスマートコンストラクタ

```rust
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Membership {
    Bronze,
    Silver,
    Gold,
    Platinum,
}

impl Membership {
    pub fn from_str(s: &str) -> ValidationResult<Membership> {
        match s.to_lowercase().as_str() {
            "bronze" => Ok(Membership::Bronze),
            "silver" => Ok(Membership::Silver),
            "gold" => Ok(Membership::Gold),
            "platinum" => Ok(Membership::Platinum),
            _ => Err(vec![format!("無効な会員種別: {}", s)]),
        }
    }
}
```

### 条件付きバリデーション（ADT）

通知タイプによって異なるバリデーションルールを適用：

```rust
#[derive(Debug, Clone, PartialEq)]
pub enum Notification {
    Email { to: String, subject: String, body: String },
    SMS { phone_number: String, body: String },
    Push { device_token: String, body: String },
}

impl Notification {
    pub fn create_email(to: &str, subject: &str, body: &str) -> Validated<String, Notification> {
        let mut errors = Vec::new();

        if !to.contains('@') || !to.contains('.') {
            errors.push("無効なメールアドレス形式です".to_string());
        }
        if subject.is_empty() {
            errors.push("件名は空にできません".to_string());
        }
        if body.is_empty() {
            errors.push("本文は空にできません".to_string());
        }

        if errors.is_empty() {
            Validated::valid(Notification::Email {
                to: to.to_string(),
                subject: subject.to_string(),
                body: body.to_string(),
            })
        } else {
            Validated::invalid(errors)
        }
    }
}
```

### バリデーションレスポンス

API レスポンス向けの構造化された結果：

```rust
#[derive(Debug, Clone, PartialEq)]
pub struct ValidationResponse<A> {
    pub valid: bool,
    pub data: Option<A>,
    pub errors: Vec<String>,
}

impl<A> ValidationResponse<A> {
    pub fn from_validated(validated: Validated<String, A>) -> Self {
        match validated {
            Validated::Valid(a) => ValidationResponse {
                valid: true,
                data: Some(a),
                errors: Vec::new(),
            },
            Validated::Invalid(errors) => ValidationResponse {
                valid: false,
                data: None,
                errors,
            },
        }
    }
}
```

## 他言語との比較

| 概念 | Rust | Scala | F# |
|------|------|-------|-----|
| バリデーション結果 | `Result<T, Vec<E>>` | `Validated[E, A]` | `Result<'a, 'e>` |
| エラー蓄積 | カスタム `Validated` 型 | cats `Validated` | カスタム実装 |
| Newtype | `struct Name(Type)` | `case class` / opaque type | 単一ケース DU |
| スマートコンストラクタ | `impl` ブロック内 | `apply` メソッド | モジュール内関数 |

## テスト例

```rust
#[test]
fn test_product_create_all_invalid() {
    let result = Product::create("INVALID", "", -100.0, None, None);
    match result {
        Validated::Invalid(errors) => {
            assert_eq!(errors.len(), 3);  // 3つのエラーが蓄積される
        }
        _ => panic!("Expected Invalid"),
    }
}

#[test]
fn test_validated_combine_both_invalid() {
    let v1: Validated<String, i32> = Validated::invalid(vec!["error1".to_string()]);
    let v2: Validated<String, i32> = Validated::invalid(vec!["error2".to_string()]);
    let result = v1.combine(v2, |a, b| a + b);
    match result {
        Validated::Invalid(errors) => {
            assert_eq!(errors.len(), 2);
            assert!(errors.contains(&"error1".to_string()));
            assert!(errors.contains(&"error2".to_string()));
        }
        _ => panic!("Expected Invalid"),
    }
}
```

## まとめ

- **Result vs Validated**: `Result` は fail-fast、`Validated` はエラー蓄積
- **Newtype パターン**: プリミティブ型に意味と制約を付与
- **スマートコンストラクタ**: 不正な値の生成を型レベルで防止
- **combine/combine3**: 複数のバリデーションを合成
- **ADT**: タイプごとに異なるバリデーションルールを適用

## 次の章

[第5章: プロパティベーステスト](05-property-based-testing.md) では、proptest クレートを使った自動テスト生成を学びます。
