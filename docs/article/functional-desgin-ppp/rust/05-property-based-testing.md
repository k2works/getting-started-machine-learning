---
type: Article
title: "第5章: プロパティベーステスト"
description: "この章では、Rust の proptest クレートを使ったプロパティベーステストを学びます。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第5章: プロパティベーステスト

## 概要

この章では、Rust の `proptest` クレートを使ったプロパティベーステストを学びます。従来のサンプルベーステストとは異なり、データの性質（プロパティ）を検証することで、より堅牢なテストを実現します。

## 学習目標

1. プロパティベーステストの概念理解
2. proptest クレートの使い方
3. 数学的プロパティの検証
4. ドメインモデルのプロパティ
5. カスタムジェネレータの作成

## 基本概念

### サンプルベースとプロパティベースの違い

**サンプルベーステスト**：特定の入力に対する出力を検証
```rust
#[test]
fn test_reverse_specific() {
    assert_eq!(reverse(&[1, 2, 3]), vec![3, 2, 1]);
}
```

**プロパティベーステスト**：任意の入力に対する性質を検証
```rust
proptest! {
    #[test]
    fn prop_reverse_involutive(xs: Vec<i32>) {
        prop_assert_eq!(reverse(&reverse(&xs)), xs);
    }
}
```

## 実装パターン

### リスト操作のプロパティ

```rust
use proptest::prelude::*;

pub fn reverse<T: Clone>(list: &[T]) -> Vec<T> {
    list.iter().rev().cloned().collect()
}

pub fn concat<T: Clone>(list1: &[T], list2: &[T]) -> Vec<T> {
    let mut result = list1.to_vec();
    result.extend(list2.iter().cloned());
    result
}

proptest! {
    /// reverse の自己逆元性: reverse(reverse(xs)) == xs
    #[test]
    fn prop_reverse_involutive(xs: Vec<i32>) {
        prop_assert_eq!(reverse(&reverse(&xs)), xs);
    }

    /// reverse は長さを保存する
    #[test]
    fn prop_reverse_preserves_length(xs: Vec<i32>) {
        prop_assert_eq!(reverse(&xs).len(), xs.len());
    }

    /// concat の長さは各リストの長さの和
    #[test]
    fn prop_concat_length(xs: Vec<i32>, ys: Vec<i32>) {
        prop_assert_eq!(concat(&xs, &ys).len(), xs.len() + ys.len());
    }

    /// 空リストとの連結は恒等操作
    #[test]
    fn prop_concat_identity(xs: Vec<i32>) {
        prop_assert_eq!(concat(&xs, &[]), xs);
        prop_assert_eq!(concat(&[], &xs), xs);
    }
}
```

### 数学的プロパティ

```rust
proptest! {
    /// 加算の可換法則: a + b == b + a
    #[test]
    fn prop_add_commutative(a: i64, b: i64) {
        prop_assert_eq!(a + b, b + a);
    }

    /// 加算の結合法則: (a + b) + c == a + (b + c)
    #[test]
    fn prop_add_associative(a: i32, b: i32, c: i32) {
        let a = a as i64;
        let b = b as i64;
        let c = c as i64;
        prop_assert_eq!((a + b) + c, a + (b + c));
    }

    /// max は可換かつ結果は両方以上
    #[test]
    fn prop_max_properties(a: i32, b: i32) {
        let m = std::cmp::max(a, b);
        prop_assert_eq!(m, std::cmp::max(b, a));
        prop_assert!(m >= a && m >= b);
    }
}
```

### ドメインモデルのプロパティ

```rust
#[derive(Debug, Clone, PartialEq)]
pub struct Money {
    pub amount: i64,
    pub currency: Currency,
}

impl Money {
    pub fn add(&self, other: &Money) -> Option<Money> {
        if self.currency == other.currency {
            Some(Money::new(self.amount + other.amount, self.currency))
        } else {
            None
        }
    }
}

proptest! {
    /// Money の加算は可換
    #[test]
    fn prop_money_add_commutative(a: i32, b: i32) {
        let m1 = Money::new(a as i64, Currency::JPY);
        let m2 = Money::new(b as i64, Currency::JPY);
        prop_assert_eq!(m1.add(&m2), m2.add(&m1));
    }

    /// ゼロは加算の単位元
    #[test]
    fn prop_money_add_identity(a: i64) {
        let m = Money::new(a, Currency::JPY);
        let zero = Money::new(0, Currency::JPY);
        prop_assert_eq!(m.add(&zero), Some(m));
    }

    /// 異なる通貨の加算は None
    #[test]
    fn prop_money_different_currency_fails(a: i64, b: i64) {
        let m1 = Money::new(a, Currency::JPY);
        let m2 = Money::new(b, Currency::USD);
        prop_assert!(m1.add(&m2).is_none());
    }
}
```

### カスタムストラテジー

```rust
proptest! {
    /// カスタムジェネレータを使用
    #[test]
    fn prop_order_total(
        prices in prop::collection::vec(1i64..1000, 1..5),
        quantities in prop::collection::vec(1u32..100, 1..5)
    ) {
        let items: Vec<OrderItem> = prices.iter()
            .zip(quantities.iter())
            .enumerate()
            .map(|(i, (&p, &q))| OrderItem::new(&format!("PROD-{}", i), p, q))
            .collect();

        let order = Order::new("ORD-001", items.clone());
        let expected: i64 = items.iter().map(|i| i.subtotal()).sum();
        prop_assert_eq!(order.total(), expected);
    }
}
```

### 前提条件の指定

```rust
proptest! {
    /// abs(n) >= 0 (i32::MIN は除外)
    #[test]
    fn prop_abs_non_negative(n: i32) {
        prop_assume!(n != i32::MIN);  // オーバーフロー回避
        prop_assert!(n.abs() >= 0);
    }
}
```

## 主要なプロパティパターン

| パターン | 説明 | 例 |
|---------|------|-----|
| 冪等性 | 2回適用しても結果が変わらない | `sort(sort(x)) == sort(x)` |
| 自己逆元 | 2回適用すると元に戻る | `reverse(reverse(x)) == x` |
| 可換法則 | 順序を変えても結果が同じ | `a + b == b + a` |
| 結合法則 | 括弧の位置を変えても同じ | `(a+b)+c == a+(b+c)` |
| 単位元 | 特定の値との演算で変化なし | `a + 0 == a` |
| 保存則 | 特定の性質が保存される | `len(reverse(x)) == len(x)` |

## 他言語との比較

| 機能 | Rust (proptest) | Scala (ScalaCheck) | Haskell (QuickCheck) |
|------|-----------------|-------------------|---------------------|
| マクロ | `proptest!` | `forAll` | `property` |
| 前提条件 | `prop_assume!` | `whenever` | `==>` |
| 縮小 | 自動 | 自動 | 自動 |
| カスタム生成 | `Strategy` trait | `Gen`/`Arbitrary` | `Gen`/`Arbitrary` |

## テスト例

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use proptest::prelude::*;

    proptest! {
        /// head と tail で元のリストを再構築できる
        #[test]
        fn prop_head_tail_reconstruction(xs: Vec<i32>) {
            if !xs.is_empty() {
                let h = xs[0];
                let t = &xs[1..];
                let mut reconstructed = vec![h];
                reconstructed.extend_from_slice(t);
                prop_assert_eq!(reconstructed, xs);
            }
        }
    }
}
```

## まとめ

- **プロパティベーステスト**: 具体的な値ではなく、データの性質を検証
- **proptest**: Rust でのプロパティベーステストの標準ライブラリ
- **自動縮小**: 失敗時に最小の反例を発見
- **数学的性質**: 可換、結合、冪等性などの普遍的な性質を検証
- **ドメインモデル**: ビジネスルールの不変条件を検証

## 次の章

[第6章: TDD と関数型](06-tdd-and-functional.md) では、関数型プログラミングと TDD を組み合わせた開発手法を学びます。
