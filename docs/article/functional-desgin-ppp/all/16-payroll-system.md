---
type: Article
title: "第16章: 給与計算システム — 6言語統合ガイド"
description: "給与計算システムは、ドメインモデリングと多態的ディスパッチを実践的に学ぶためのケーススタディです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第16章: 給与計算システム — 6言語統合ガイド

## 1. はじめに

給与計算システムは、**ドメインモデリング**と**多態的ディスパッチ**を実践的に学ぶためのケーススタディです。従業員の種類（時給制・月給制・歩合制）に応じた給与計算ロジックを、関数型パラダイムでどう設計するかを比較します。

## 2. 共通の本質

### ドメインモデル

すべての言語で共通のドメイン概念：

- **従業員分類**: 時給制（Hourly）、月給制（Salaried）、歩合制（Commissioned）
- **支払いスケジュール**: 週次、月次、隔週
- **支払い方法**: 銀行振込、小切手、手渡し
- **計算ルール**: 残業（40 時間超）、歩合率、固定給

### 計算ロジック

```
時給制:   通常時間 × 時給 + 残業時間 × 時給 × 1.5
月給制:   固定月給
歩合制:   基本給 + 売上 × 歩合率
```

## 3. 言語別実装比較

### 3.1 従業員分類の型表現

| 言語 | 型表現 | 多態性の方法 |
|------|--------|-----------|
| Clojure | マップ + キーワード | `defmulti` でディスパッチ |
| Scala | `sealed trait` + `case class` | パターンマッチング |
| Elixir | 構造体 + タプルタグ | パターンマッチング |
| F# | 判別共用体 | パターンマッチング |
| Haskell | 代数的データ型 | case 式 |
| Rust | `enum` | `match` 式 |

<details>
<summary>Clojure: マップ + マルチメソッド</summary>

```clojure
(defmulti calculate-pay :pay-type)

(defmethod calculate-pay :hourly [{:keys [hours hourly-rate]}]
  (let [regular (min hours 40)
        overtime (max 0 (- hours 40))]
    (+ (* regular hourly-rate)
       (* overtime hourly-rate 1.5))))

(defmethod calculate-pay :salaried [{:keys [monthly-salary]}]
  monthly-salary)

(defmethod calculate-pay :commissioned [{:keys [base-salary sales commission-rate]}]
  (+ base-salary (* sales commission-rate)))
```

</details>

<details>
<summary>Scala: sealed trait + パターンマッチ</summary>

```scala
sealed trait PayClassification
case class Hourly(hourlyRate: Double) extends PayClassification
case class Salaried(monthlySalary: Double) extends PayClassification
case class Commissioned(baseSalary: Double, commissionRate: Double) extends PayClassification

def calculatePay(classification: PayClassification, hours: Double, sales: Double): Double =
  classification match
    case Hourly(rate) =>
      val regular = math.min(hours, 40)
      val overtime = math.max(0, hours - 40)
      regular * rate + overtime * rate * 1.5
    case Salaried(salary) => salary
    case Commissioned(base, rate) => base + sales * rate
```

</details>

<details>
<summary>Haskell: ADT + case 式</summary>

```haskell
data PayClassification
    = Hourly Double
    | Salaried Double
    | Commissioned Double Double

calculatePay :: PayClassification -> Double -> Double -> Double
calculatePay classification hours sales = case classification of
    Hourly rate ->
        let regular = min hours 40
            overtime = max 0 (hours - 40)
        in regular * rate + overtime * rate * 1.5
    Salaried salary -> salary
    Commissioned base rate -> base + sales * rate
```

</details>

<details>
<summary>Rust: enum + match</summary>

```rust
pub enum PayClassification {
    Hourly { hourly_rate: f64 },
    Salaried { monthly_salary: f64 },
    Commissioned { base_salary: f64, commission_rate: f64 },
}

impl PayClassification {
    pub fn calculate(&self, hours: f64, sales: f64) -> f64 {
        match self {
            Self::Hourly { hourly_rate } => {
                let regular = hours.min(40.0);
                let overtime = (hours - 40.0).max(0.0);
                regular * hourly_rate + overtime * hourly_rate * 1.5
            }
            Self::Salaried { monthly_salary } => *monthly_salary,
            Self::Commissioned { base_salary, commission_rate } => {
                base_salary + sales * commission_rate
            }
        }
    }
}
```

</details>

### 3.2 データモデルの設計哲学

| 言語 | 設計哲学 | データ定義 |
|------|---------|----------|
| Clojure | データ駆動 | マップリテラル、スキーマは Spec で後付け |
| Scala | 型駆動 | case class 階層、コンパイル時チェック |
| Elixir | データ駆動 | 構造体、タプルタグで分類 |
| F# | 型駆動 | 判別共用体、コンパイル時網羅性チェック |
| Haskell | 型駆動 | ADT、最も厳密な型チェック |
| Rust | 型駆動 | enum、所有権 + 型安全性 |

**動的型付け言語**（Clojure, Elixir）はデータ構造が柔軟で、新しいフィールドの追加が容易です。**静的型付け言語**（Scala, F#, Haskell, Rust）はコンパイル時にドメインルールの整合性を保証します。

### 3.3 拡張性の違い

新しい従業員分類（例: 日給制）を追加する場合：

| 言語 | 変更箇所 | 安全性 |
|------|---------|--------|
| Clojure | `defmethod` を 1 つ追加 | テストで確認 |
| Scala | case class 追加 + match に追加 | コンパイルエラーで検出 |
| Elixir | 関数クローズを追加 | テストで確認 |
| F# | 判別共用体に追加 + match に追加 | コンパイルエラーで検出 |
| Haskell | ADT に追加 + case に追加 | コンパイルエラーで検出 |
| Rust | enum に追加 + match に追加 | コンパイルエラーで検出 |

## 4. 比較分析

### ドメインモデリングのトレードオフ

| 基準 | 動的型付け（Clojure, Elixir） | 静的型付け（Scala, F#, Haskell, Rust） |
|------|---------------------------|-----------------------------------|
| 柔軟性 | 高い（スキーマレス） | 型定義が必要 |
| 安全性 | テストに依存 | コンパイル時チェック |
| リファクタリング | リスクあり | コンパイラが支援 |
| プロトタイピング | 高速 | 型設計が必要 |

## 5. まとめ

給与計算システムは、関数型言語でのドメインモデリングの典型例です：

1. **多態的ディスパッチ**: 従業員分類ごとの計算を言語の多態性で表現
2. **データと処理の分離**: データ定義と計算ロジックを明確に分離
3. **拡張性**: 新しい分類の追加が既存コードへの影響を最小化

## 言語別個別記事

- [Clojure](../clojure/16-payroll-system.md) | [Scala](../scala/16-payroll-system.md) | [Elixir](../elixir/16-payroll-system.md) | [F#](../fsharp/16-payroll-system.md) | [Haskell](../haskell/16-payroll-system.md) | [Rust](../rust/16-payroll-system.md)
