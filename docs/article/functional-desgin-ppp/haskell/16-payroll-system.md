---
type: Article
title: "第16章: 給与計算システム"
description: "本章では、給与計算システムを題材に、関数型プログラミングにおけるドメインモデリングと多態性の実現を学びます。"
tags: [article, functional-desgin-ppp, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第16章: 給与計算システム

## はじめに

本章では、給与計算システムを題材に、関数型プログラミングにおけるドメインモデリングと多態性の実現を学びます。

この問題を通じて以下の概念を学びます：

- ADT によるデータ仕様定義
- パターンマッチングによる給与タイプ別の計算
- 支払いスケジュールのモデリング

## 1. ドメインモデル

### 従業員の給与タイプ

給与計算システムでは、以下の3種類の給与タイプをサポートします：

- **月給制（Salaried）**: 固定月給
- **時給制（Hourly）**: 労働時間に基づく給与（残業は1.5倍）
- **歩合制（Commissioned）**: 基本給 + 売上に応じたコミッション

## 2. 型定義

### 給与分類

```haskell
-- | Pay classification determines how pay is calculated
data PayClass
  = Salaried Double                    -- ^ Fixed salary
  | Hourly Double                      -- ^ Hourly rate
  | Commissioned Double Double         -- ^ Base pay, commission rate
  deriving (Show, Eq)
```

### 支払いスケジュールと方法

```haskell
-- | Payment schedule
data PaySchedule
  = Monthly    -- ^ Paid monthly (last day of month)
  | Weekly     -- ^ Paid weekly (every Friday)
  | BiWeekly   -- ^ Paid every two weeks
  deriving (Show, Eq)

-- | Payment method
data PaymentMethod
  = Hold            -- ^ Hold for pickup
  | DirectDeposit String  -- ^ Direct deposit to account
  | Mail String     -- ^ Mail to address
  deriving (Show, Eq)
```

### 従業員レコード

```haskell
-- | Employee record
data Employee = Employee
  { employeeId :: EmployeeId
  , employeeName :: String
  , employeePayClass :: PayClass
  , employeeSchedule :: PaySchedule
  , employeePaymentMethod :: PaymentMethod
  } deriving (Show, Eq)
```

## 3. 従業員の作成

```haskell
-- | Create a salaried employee
makeSalariedEmployee :: EmployeeId -> String -> Double -> Employee
makeSalariedEmployee eid name salary = Employee
  { employeeId = eid
  , employeeName = name
  , employeePayClass = Salaried salary
  , employeeSchedule = Monthly
  , employeePaymentMethod = Hold
  }

-- | Create an hourly employee
makeHourlyEmployee :: EmployeeId -> String -> Double -> Employee
makeHourlyEmployee eid name hourlyRate = Employee
  { employeeId = eid
  , employeeName = name
  , employeePayClass = Hourly hourlyRate
  , employeeSchedule = Weekly
  , employeePaymentMethod = Hold
  }

-- | Create a commissioned employee
makeCommissionedEmployee :: EmployeeId -> String -> Double -> Double -> Employee
makeCommissionedEmployee eid name basePay commissionRate = Employee
  { employeeId = eid
  , employeeName = name
  , employeePayClass = Commissioned basePay commissionRate
  , employeeSchedule = BiWeekly
  , employeePaymentMethod = Hold
  }
```

## 4. タイムカードと売上データ

```haskell
-- | Time card for hourly employees
data TimeCard = TimeCard
  { tcDate :: Day
  , tcHours :: Double
  } deriving (Show, Eq)

-- | Sales receipt for commissioned employees
data SalesReceipt = SalesReceipt
  { srDate :: Day
  , srAmount :: Double
  } deriving (Show, Eq)

-- | Context for pay calculation
data PayContext = PayContext
  { ctxTimeCards :: Map EmployeeId [TimeCard]
  , ctxSalesReceipts :: Map EmployeeId [SalesReceipt]
  , ctxPayDate :: Day
  } deriving (Show, Eq)
```

## 5. 給与計算

### パターンマッチングによる計算

```haskell
-- | Calculate pay for an employee
calculatePay :: Employee -> PayContext -> Double
calculatePay emp ctx = case employeePayClass emp of
  Salaried salary -> salary
  Hourly rate -> calculateHourlyPay rate (getTimeCards emp ctx)
  Commissioned basePay commRate -> 
    calculateCommissionedPay basePay commRate (getSalesReceipts emp ctx)
```

### 時給制の計算

```haskell
-- | Calculate hourly pay with overtime (1.5x for hours over 40)
calculateHourlyPay :: Double -> [TimeCard] -> Double
calculateHourlyPay rate cards =
  let totalHours = sum (map tcHours cards)
      regularHours = min totalHours 40
      overtimeHours = max 0 (totalHours - 40)
  in regularHours * rate + overtimeHours * rate * 1.5
```

### 歩合制の計算

```haskell
-- | Calculate commissioned pay
calculateCommissionedPay :: Double -> Double -> [SalesReceipt] -> Double
calculateCommissionedPay basePay commRate receipts =
  let totalSales = sum (map srAmount receipts)
  in basePay + totalSales * commRate
```

## 6. 給与支払い処理

```haskell
-- | Pay check issued to an employee
data PayCheck = PayCheck
  { checkEmployeeId :: EmployeeId
  , checkEmployeeName :: String
  , checkAmount :: Double
  , checkMethod :: PaymentMethod
  } deriving (Show, Eq)

-- | Process a single employee
processEmployee :: Employee -> PayContext -> PayCheck
processEmployee emp ctx = PayCheck
  { checkEmployeeId = employeeId emp
  , checkEmployeeName = employeeName emp
  , checkAmount = calculatePay emp ctx
  , checkMethod = employeePaymentMethod emp
  }

-- | Run payroll for all employees
runPayroll :: [Employee] -> PayContext -> [PayCheck]
runPayroll employees ctx = map (`processEmployee` ctx) employees
```

## 7. テスト

```haskell
describe "calculatePay" $ do
  it "calculates salaried pay" $ do
    let emp = makeSalariedEmployee "E001" "John" 5000
        ctx = emptyContext (fromGregorian 2026 1 31)
    calculatePay emp ctx `shouldBe` 5000

  it "calculates hourly pay with overtime" $ do
    let emp = makeHourlyEmployee "E001" "Jane" 20.0
        ctx = emptyContext (fromGregorian 2026 1 7)
        cards = [TimeCard (fromGregorian 2026 1 d) 10.0 | d <- [1..5]]
        ctx' = foldr (addTimeCard "E001") ctx cards
    -- 40 * 20 + 10 * 20 * 1.5 = 800 + 300 = 1100
    calculatePay emp ctx' `shouldBe` 1100.0

  it "calculates commissioned pay" $ do
    let emp = makeCommissionedEmployee "E001" "Bob" 1500 0.10
        ctx = emptyContext (fromGregorian 2026 1 14)
        sr1 = SalesReceipt (fromGregorian 2026 1 10) 5000.0
        ctx' = addSalesReceipt "E001" sr1 ctx
    -- 1500 + 5000 * 0.10 = 1500 + 500 = 2000
    calculatePay emp ctx' `shouldBe` 2000.0
```

## まとめ

給与計算システムの Haskell 実装のポイント：

1. **ADT による分類**: `PayClass` で給与タイプを表現
2. **パターンマッチング**: `case` 式で給与タイプ別に処理
3. **コンテキストパターン**: `PayContext` で計算に必要なデータを渡す
4. **純粋関数**: 副作用のない計算で予測可能な動作
5. **スマートコンストラクタ**: `makeSalariedEmployee` 等で一貫した初期化
