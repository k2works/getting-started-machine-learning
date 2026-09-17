---
type: Article
title: "第16章: 給与計算システム"
description: "本章では、給与計算システムを通じて、関数型デザインパターンを実践的に適用します。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第16章: 給与計算システム

## はじめに

本章では、給与計算システムを通じて、関数型デザインパターンを実践的に適用します。従業員の種類、支払い方法、控除など、複雑なビジネスロジックを純粋関数と不変データ構造で表現します。

## 1. データ構造

### 従業員タイプ

```rust
use rust_decimal::Decimal;

/// 支払い区分
#[derive(Debug, Clone, PartialEq)]
pub enum PayClassification {
    Hourly { hourly_rate: Decimal },
    Salaried { monthly_salary: Decimal },
    Commissioned { base_salary: Decimal, commission_rate: Decimal },
}

/// 支払いスケジュール
#[derive(Debug, Clone, PartialEq)]
pub enum PaySchedule {
    Weekly,
    BiWeekly,
    Monthly,
}

/// 支払い方法
#[derive(Debug, Clone, PartialEq)]
pub enum PaymentMethod {
    Hold,
    Direct { bank: String, account: String },
    Mail { address: String },
}

/// 従業員
#[derive(Debug, Clone)]
pub struct Employee {
    pub id: String,
    pub name: String,
    pub classification: PayClassification,
    pub schedule: PaySchedule,
    pub payment_method: PaymentMethod,
}
```

### タイムカードと売上

```rust
/// タイムカード
#[derive(Debug, Clone)]
pub struct TimeCard {
    pub employee_id: String,
    pub date: NaiveDate,
    pub hours: Decimal,
}

/// 売上レコード
#[derive(Debug, Clone)]
pub struct SalesReceipt {
    pub employee_id: String,
    pub date: NaiveDate,
    pub amount: Decimal,
}
```

## 2. 給与計算ロジック

```rust
/// 総支給額を計算
pub fn calculate_gross_pay(
    employee: &Employee,
    period_start: NaiveDate,
    period_end: NaiveDate,
    time_cards: &[TimeCard],
    sales_receipts: &[SalesReceipt],
) -> Decimal {
    match &employee.classification {
        PayClassification::Hourly { hourly_rate } => {
            let total_hours: Decimal = time_cards
                .iter()
                .filter(|tc| {
                    tc.employee_id == employee.id
                        && tc.date >= period_start
                        && tc.date <= period_end
                })
                .map(|tc| tc.hours)
                .sum();
            
            // 残業計算（40時間超過分は1.5倍）
            let regular_hours = total_hours.min(Decimal::from(40));
            let overtime_hours = (total_hours - Decimal::from(40)).max(Decimal::ZERO);
            
            regular_hours * hourly_rate + overtime_hours * hourly_rate * Decimal::from_str("1.5").unwrap()
        }
        PayClassification::Salaried { monthly_salary } => {
            *monthly_salary
        }
        PayClassification::Commissioned { base_salary, commission_rate } => {
            let total_sales: Decimal = sales_receipts
                .iter()
                .filter(|sr| {
                    sr.employee_id == employee.id
                        && sr.date >= period_start
                        && sr.date <= period_end
                })
                .map(|sr| sr.amount)
                .sum();
            
            *base_salary + total_sales * commission_rate
        }
    }
}
```

## 3. 控除計算

```rust
/// 控除タイプ
#[derive(Debug, Clone)]
pub enum Deduction {
    Tax { rate: Decimal },
    Insurance { amount: Decimal },
    Union { amount: Decimal },
    ServiceCharge { amount: Decimal },
}

/// 控除を適用
pub fn apply_deductions(gross_pay: Decimal, deductions: &[Deduction]) -> (Decimal, Vec<(String, Decimal)>) {
    let mut details = Vec::new();
    let mut net_pay = gross_pay;

    for deduction in deductions {
        let (name, amount) = match deduction {
            Deduction::Tax { rate } => {
                let tax = gross_pay * rate;
                ("Tax".to_string(), tax)
            }
            Deduction::Insurance { amount } => {
                ("Insurance".to_string(), *amount)
            }
            Deduction::Union { amount } => {
                ("Union".to_string(), *amount)
            }
            Deduction::ServiceCharge { amount } => {
                ("Service Charge".to_string(), *amount)
            }
        };
        
        net_pay -= amount;
        details.push((name, amount));
    }

    (net_pay, details)
}
```

## 4. 給与明細

```rust
/// 給与明細
#[derive(Debug, Clone)]
pub struct Payslip {
    pub employee_id: String,
    pub employee_name: String,
    pub period_start: NaiveDate,
    pub period_end: NaiveDate,
    pub gross_pay: Decimal,
    pub deductions: Vec<(String, Decimal)>,
    pub net_pay: Decimal,
}

/// 給与明細を生成
pub fn generate_payslip(
    employee: &Employee,
    period_start: NaiveDate,
    period_end: NaiveDate,
    time_cards: &[TimeCard],
    sales_receipts: &[SalesReceipt],
    deductions: &[Deduction],
) -> Payslip {
    let gross_pay = calculate_gross_pay(
        employee, period_start, period_end, time_cards, sales_receipts
    );
    
    let (net_pay, deduction_details) = apply_deductions(gross_pay, deductions);

    Payslip {
        employee_id: employee.id.clone(),
        employee_name: employee.name.clone(),
        period_start,
        period_end,
        gross_pay,
        deductions: deduction_details,
        net_pay,
    }
}
```

## 5. 支払い日判定

```rust
/// 支払い日かどうかを判定
pub fn is_pay_day(employee: &Employee, date: NaiveDate) -> bool {
    match employee.schedule {
        PaySchedule::Weekly => date.weekday() == Weekday::Fri,
        PaySchedule::BiWeekly => {
            date.weekday() == Weekday::Fri && date.iso_week().week() % 2 == 0
        }
        PaySchedule::Monthly => {
            let next_day = date + chrono::Duration::days(1);
            next_day.month() != date.month()
        }
    }
}

/// 支払い期間を計算
pub fn calculate_pay_period(employee: &Employee, pay_date: NaiveDate) -> (NaiveDate, NaiveDate) {
    match employee.schedule {
        PaySchedule::Weekly => {
            let start = pay_date - chrono::Duration::days(6);
            (start, pay_date)
        }
        PaySchedule::BiWeekly => {
            let start = pay_date - chrono::Duration::days(13);
            (start, pay_date)
        }
        PaySchedule::Monthly => {
            let start = NaiveDate::from_ymd_opt(pay_date.year(), pay_date.month(), 1).unwrap();
            (start, pay_date)
        }
    }
}
```

## 6. ペイロール実行

```rust
/// ペイロールコンテキスト
pub struct PayrollContext {
    pub employees: Vec<Employee>,
    pub time_cards: Vec<TimeCard>,
    pub sales_receipts: Vec<SalesReceipt>,
    pub deductions: HashMap<String, Vec<Deduction>>,
}

/// ペイロールを実行
pub fn run_payroll(context: &PayrollContext, date: NaiveDate) -> Vec<Payslip> {
    context.employees
        .iter()
        .filter(|emp| is_pay_day(emp, date))
        .map(|emp| {
            let (start, end) = calculate_pay_period(emp, date);
            let emp_deductions = context.deductions.get(&emp.id).cloned().unwrap_or_default();
            
            generate_payslip(
                emp,
                start,
                end,
                &context.time_cards,
                &context.sales_receipts,
                &emp_deductions,
            )
        })
        .collect()
}
```

## 7. パターンの適用

この問題では以下のパターンが適用されています：

1. **ADT (enum)**: PayClassification, PaySchedule, PaymentMethod
2. **不変データ**: Employee, TimeCard, Payslip
3. **純粋関数**: calculate_gross_pay, apply_deductions
4. **Strategy**: 支払い区分ごとの計算ロジック

## まとめ

本章では、給与計算システムを通じて：

1. enum による多様な支払いタイプの表現
2. 純粋関数によるビジネスロジック
3. 不変データ構造による安全な状態管理
4. 高階関数によるデータ処理

を学びました。

## 参考コード

- ソースコード: `apps/rust/part6/src/chapter16.rs`

## 次章予告

次章では、**レンタルビデオシステム**を通じて、さらに複雑なドメインモデリングを学びます。
