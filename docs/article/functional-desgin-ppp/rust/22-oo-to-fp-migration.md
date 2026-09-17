---
type: Article
title: "第22章: OO から FP への移行"
description: "本章では、オブジェクト指向（OO）スタイルのコードを関数型（FP）スタイルに移行する方法を学びます。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第22章: OO から FP への移行

## はじめに

本章では、オブジェクト指向（OO）スタイルのコードを関数型（FP）スタイルに移行する方法を学びます。Rust はデフォルトで不変なため、FP スタイルに自然に適合しますが、OO から来た開発者のために移行のガイドラインを示します。

## 1. OO スタイル vs FP スタイル

### OO スタイル（可変）

```rust
/// OO スタイル: 内部状態を持つオブジェクト
pub mod oo_style {
    pub struct Account {
        id: String,
        balance: f64,
        transactions: Vec<Transaction>,
    }

    pub struct Transaction {
        pub transaction_type: TransactionType,
        pub amount: f64,
    }

    pub enum TransactionType {
        Deposit,
        Withdrawal,
    }

    impl Account {
        pub fn new(id: &str, initial_balance: f64) -> Account {
            Account {
                id: id.to_string(),
                balance: initial_balance,
                transactions: Vec::new(),
            }
        }

        // 可変メソッド - 内部状態を変更
        pub fn deposit(&mut self, amount: f64) -> f64 {
            if amount > 0.0 {
                self.balance += amount;
                self.transactions.push(Transaction {
                    transaction_type: TransactionType::Deposit,
                    amount,
                });
            }
            self.balance
        }

        pub fn withdraw(&mut self, amount: f64) -> f64 {
            if amount > 0.0 && self.balance >= amount {
                self.balance -= amount;
                self.transactions.push(Transaction {
                    transaction_type: TransactionType::Withdrawal,
                    amount,
                });
            }
            self.balance
        }
    }
}
```

### FP スタイル（不変）

```rust
/// FP スタイル: 不変データと純粋関数
pub mod fp_style {
    #[derive(Debug, Clone)]
    pub struct Account {
        pub id: String,
        pub balance: f64,
        pub transactions: Vec<Transaction>,
    }

    #[derive(Debug, Clone)]
    pub struct Transaction {
        pub transaction_type: TransactionType,
        pub amount: f64,
    }

    #[derive(Debug, Clone)]
    pub enum TransactionType {
        Deposit,
        Withdrawal,
    }

    /// 口座を作成（純粋関数）
    pub fn make_account(id: &str, initial_balance: f64) -> Account {
        Account {
            id: id.to_string(),
            balance: initial_balance,
            transactions: Vec::new(),
        }
    }

    /// 入金（新しい口座を返す - 元は不変）
    pub fn deposit(account: Account, amount: f64) -> Account {
        if amount > 0.0 {
            Account {
                balance: account.balance + amount,
                transactions: {
                    let mut txns = account.transactions;
                    txns.push(Transaction {
                        transaction_type: TransactionType::Deposit,
                        amount,
                    });
                    txns
                },
                ..account
            }
        } else {
            account
        }
    }

    /// 出金（新しい口座を返す - 元は不変）
    pub fn withdraw(account: Account, amount: f64) -> Account {
        if amount > 0.0 && account.balance >= amount {
            Account {
                balance: account.balance - amount,
                transactions: {
                    let mut txns = account.transactions;
                    txns.push(Transaction {
                        transaction_type: TransactionType::Withdrawal,
                        amount,
                    });
                    txns
                },
                ..account
            }
        } else {
            account
        }
    }
}
```

## 2. 移行戦略

### Strangler Fig パターン

既存のコードを徐々に新しいスタイルに置き換えます。

```rust
pub mod migration {
    use super::fp_style::*;

    pub enum Style {
        FP,
        OO,
    }

    pub struct StranglerAccount {
        pub style: Style,
        pub data: Account,
    }

    /// フィーチャーフラグによる切り替え
    pub fn create_account(id: &str, balance: f64, use_fp: bool) -> StranglerAccount {
        let style = if use_fp { Style::FP } else { Style::OO };
        StranglerAccount {
            style,
            data: make_account(id, balance),
        }
    }

    /// 統一インターフェース
    pub fn account_deposit(account: StranglerAccount, amount: f64) -> StranglerAccount {
        StranglerAccount {
            data: deposit(account.data, amount),
            ..account
        }
    }
}
```

### アダプターパターン

既存のインターフェースを維持しながら、内部をFPに移行します。

```rust
pub mod adapter {
    use super::fp_style::*;

    /// 既存インターフェースを維持するアダプター
    pub struct FPAccountAdapter {
        account: Account,
    }

    impl FPAccountAdapter {
        pub fn new(id: &str, initial_balance: f64) -> FPAccountAdapter {
            FPAccountAdapter {
                account: make_account(id, initial_balance),
            }
        }

        pub fn get_balance(&self) -> f64 {
            self.account.balance
        }

        // 既存のインターフェースを維持
        pub fn deposit_to_account(&mut self, amount: f64) -> f64 {
            self.account = deposit(self.account.clone(), amount);
            self.account.balance
        }

        pub fn withdraw_from_account(&mut self, amount: f64) -> f64 {
            self.account = withdraw(self.account.clone(), amount);
            self.account.balance
        }
    }
}
```

## 3. 多態性の実現

### OO: トレイトオブジェクト

```rust
pub trait Shape {
    fn area(&self) -> f64;
}

struct Circle { radius: f64 }
struct Rectangle { width: f64, height: f64 }

impl Shape for Circle {
    fn area(&self) -> f64 { std::f64::consts::PI * self.radius * self.radius }
}

impl Shape for Rectangle {
    fn area(&self) -> f64 { self.width * self.height }
}
```

### FP: enum とパターンマッチ

```rust
pub enum Shape {
    Circle { radius: f64 },
    Rectangle { width: f64, height: f64 },
    Triangle { base: f64, height: f64 },
}

pub fn area(shape: &Shape) -> f64 {
    match shape {
        Shape::Circle { radius } => std::f64::consts::PI * radius * radius,
        Shape::Rectangle { width, height } => width * height,
        Shape::Triangle { base, height } => base * height / 2.0,
    }
}

pub fn scale(shape: &Shape, factor: f64) -> Shape {
    match shape {
        Shape::Circle { radius } => Shape::Circle { radius: radius * factor },
        Shape::Rectangle { width, height } => Shape::Rectangle {
            width: width * factor,
            height: height * factor,
        },
        Shape::Triangle { base, height } => Shape::Triangle {
            base: base * factor,
            height: height * factor,
        },
    }
}
```

## 4. 段階的な関数抽出

```rust
/// 純粋関数を抽出
pub fn calculate_interest(balance: f64, rate: f64, days: i32) -> f64 {
    balance * rate * (days as f64 / 365.0)
}

/// 手数料計算（純粋関数）
pub struct FeeStructure {
    pub minimum_balance: f64,
    pub low_balance_fee: f64,
    pub premium_threshold: f64,
    pub standard_fee: f64,
}

pub fn calculate_fee(balance: f64, fee_structure: &FeeStructure) -> f64 {
    if balance < fee_structure.minimum_balance {
        fee_structure.low_balance_fee
    } else if balance > fee_structure.premium_threshold {
        0.0
    } else {
        fee_structure.standard_fee
    }
}
```

## 5. OO vs FP 比較表

| 側面 | OO | FP |
|------|----|----|
| 基本単位 | オブジェクト | データ + 関数 |
| 状態管理 | 可変（mutable） | 不変（immutable） |
| 多態性 | 継承・トレイト | ADT・パターンマッチ |
| コード再利用 | 継承 | 関数合成 |
| 副作用 | どこでも可能 | 境界に分離 |
| テスト | モックが必要 | 入力→出力のみ |
| デバッグ | 状態追跡が困難 | 値が不変で追跡容易 |
| 並行処理 | ロックが必要 | 不変データで安全 |

## 6. 移行チェックリスト

### 準備

- [ ] 現在のコードの状態を把握
- [ ] テストカバレッジを確認
- [ ] 移行の優先順位を決定

### 実行

- [ ] 純粋関数を抽出
- [ ] 副作用を分離
- [ ] ADT に移行
- [ ] テストを追加

### 完了

- [ ] 古いコードを削除
- [ ] ドキュメントを更新
- [ ] パフォーマンスを確認

## まとめ

本章では、OO から FP への移行について学びました：

1. **OO vs FP**: 可変 vs 不変の違い
2. **Strangler Fig パターン**: 段階的な移行
3. **アダプターパターン**: 既存インターフェースの維持
4. **ADT への移行**: enum とパターンマッチ
5. **純粋関数の抽出**: ビジネスロジックの分離

Rust は不変性がデフォルトなので、FP スタイルへの移行は自然に行えます。

## 参考コード

- ソースコード: `apps/rust/part7/src/chapter22.rs`

---

**Simple made easy.**
