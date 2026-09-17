---
type: Article
title: "第16章: 給与計算システム"
description: "本章では、給与計算システムを題材に、関数型プログラミングにおけるドメインモデリングと多態性の実現を学びます。"
tags: [article, functional-desgin-ppp, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第16章: 給与計算システム

## はじめに

本章では、給与計算システムを題材に、関数型プログラミングにおけるドメインモデリングと多態性の実現を学びます。

この問題を通じて以下の概念を学びます：

- Sealed trait と case class によるドメインモデリング
- 型クラスによる給与タイプ別の計算
- 支払いスケジュールのモデリング
- イベントソーシング風の履歴管理

## 1. ドメインモデル

### 給与タイプ

給与計算システムでは、以下の3種類の給与タイプをサポートします：

- **月給制（Salaried）**: 固定月給
- **時給制（Hourly）**: 労働時間に基づく給与
- **歩合制（Commissioned）**: 基本給 + 売上に応じたコミッション

```scala
sealed trait PayClass:
  def payType: String

case class Salaried(salary: Money) extends PayClass:
  def payType: String = "salaried"

case class Hourly(hourlyRate: Money) extends PayClass:
  def payType: String = "hourly"

case class Commissioned(basePay: Money, commissionRate: Rate) extends PayClass:
  def payType: String = "commissioned"
```

### 支払いスケジュール

```scala
enum Schedule:
  case Monthly   // 月次（月末）
  case Weekly    // 週次（毎週金曜日）
  case Biweekly  // 隔週（隔週金曜日）
```

### 支払い方法

```scala
enum PaymentMethod:
  case Hold                                   // 保留
  case DirectDeposit(accountNumber: String)  // 口座振込
  case Mail(address: String)                 // 小切手郵送
```

## 2. 従業員モデル

```scala
case class Employee(
  id: EmployeeId,
  name: String,
  payClass: PayClass,
  schedule: Schedule,
  paymentMethod: PaymentMethod,
  address: Option[String] = None
)

object Employee:
  def salaried(id: EmployeeId, name: String, salary: Money): Employee =
    Employee(id, name, Salaried(salary), Schedule.Monthly, PaymentMethod.Hold)

  def hourly(id: EmployeeId, name: String, hourlyRate: Money): Employee =
    Employee(id, name, Hourly(hourlyRate), Schedule.Weekly, PaymentMethod.Hold)

  def commissioned(id: EmployeeId, name: String, basePay: Money, commissionRate: Rate): Employee =
    Employee(id, name, Commissioned(basePay, commissionRate), Schedule.Biweekly, PaymentMethod.Hold)
```

## 3. コンテキスト（タイムカード、売上）

```scala
case class TimeCard(employeeId: EmployeeId, date: String, hours: Hours)
case class SalesReceipt(employeeId: EmployeeId, date: String, amount: Money)

case class PayrollContext(
  timeCards: Map[EmployeeId, List[TimeCard]] = Map.empty.withDefaultValue(Nil),
  salesReceipts: Map[EmployeeId, List[SalesReceipt]] = Map.empty.withDefaultValue(Nil)
):
  def addTimeCard(tc: TimeCard): PayrollContext = ...
  def addSalesReceipt(sr: SalesReceipt): PayrollContext = ...
```

### 使用例

```scala
val ctx = PayrollContext.empty
  .addTimeCard(TimeCard("E002", "2024-01-15", 8))
  .addTimeCard(TimeCard("E002", "2024-01-16", 10))
  .addSalesReceipt(SalesReceipt("E003", "2024-01-15", BigDecimal(100000)))
```

## 4. 給与計算

### 型クラスアプローチ

```scala
trait PayCalculator[P <: PayClass]:
  def calculate(payClass: P, context: PayrollContext, employeeId: EmployeeId): Money

given PayCalculator[Salaried] with
  def calculate(payClass: Salaried, context: PayrollContext, employeeId: EmployeeId): Money =
    payClass.salary

given PayCalculator[Hourly] with
  def calculate(payClass: Hourly, context: PayrollContext, employeeId: EmployeeId): Money =
    val timeCards = context.getTimeCards(employeeId)
    val totalHours = timeCards.map(_.hours).sum
    val regularHours = math.min(totalHours, 40.0)
    val overtimeHours = math.max(0.0, totalHours - 40.0)
    payClass.hourlyRate * BigDecimal(regularHours) +
      payClass.hourlyRate * BigDecimal(overtimeHours) * BigDecimal(1.5)

given PayCalculator[Commissioned] with
  def calculate(payClass: Commissioned, context: PayrollContext, employeeId: EmployeeId): Money =
    val salesReceipts = context.getSalesReceipts(employeeId)
    val totalSales = salesReceipts.map(_.amount).sum
    payClass.basePay + totalSales * BigDecimal(payClass.commissionRate)
```

### 計算例

```scala
// 月給制: 固定給与
val salaried = Employee.salaried("E001", "田中", BigDecimal(500000))
calculatePay(salaried, ctx) // => 500000

// 時給制: 40時間 * 1500 + 5時間 * 1500 * 1.5 = 67500
val hourly = Employee.hourly("E002", "佐藤", BigDecimal(1500))
val ctx = PayrollContext.empty.addTimeCard(TimeCard("E002", "2024-01-15", 45))
calculatePay(hourly, ctx) // => 67500

// 歩合制: 200000 + 150000 * 0.1 = 215000
val commissioned = Employee.commissioned("E003", "鈴木", BigDecimal(200000), 0.1)
val ctx = PayrollContext.empty
  .addSalesReceipt(SalesReceipt("E003", "2024-01-15", BigDecimal(150000)))
calculatePay(commissioned, ctx) // => 215000
```

## 5. 支払いスケジュール判定

```scala
case class PayDate(
  year: Int,
  month: Int,
  day: Int,
  dayOfWeek: DayOfWeek,
  isLastDayOfMonth: Boolean,
  isPayWeek: Boolean = true
)

def isPayDay(employee: Employee, date: PayDate): Boolean =
  employee.schedule match
    case Schedule.Monthly => date.isLastDayOfMonth
    case Schedule.Weekly => date.dayOfWeek == DayOfWeek.Friday
    case Schedule.Biweekly => date.dayOfWeek == DayOfWeek.Friday && date.isPayWeek
```

## 6. 支払い処理

```scala
sealed trait PaymentResult:
  def employeeId: EmployeeId
  def amount: Money

case class HeldPayment(employeeId: EmployeeId, amount: Money) extends PaymentResult
case class DirectDepositPayment(employeeId: EmployeeId, amount: Money, accountNumber: String) extends PaymentResult
case class MailedPayment(employeeId: EmployeeId, amount: Money, address: String) extends PaymentResult

def processPayment(employee: Employee, amount: Money): PaymentResult =
  employee.paymentMethod match
    case PaymentMethod.Hold =>
      HeldPayment(employee.id, amount)
    case PaymentMethod.DirectDeposit(account) =>
      DirectDepositPayment(employee.id, amount, account)
    case PaymentMethod.Mail(address) =>
      MailedPayment(employee.id, amount, address)
```

## 7. 給与支払い実行

```scala
def runPayroll(employees: Seq[Employee], context: PayrollContext, date: PayDate): Seq[PaymentResult] =
  employees
    .filter(isPayDay(_, date))
    .map { emp =>
      val pay = calculatePay(emp, context)
      processPayment(emp, pay)
    }
```

## 8. 給与明細と控除

### 給与明細

```scala
case class Payslip(
  employeeId: EmployeeId,
  employeeName: String,
  payPeriod: String,
  grossPay: Money,
  deductions: Map[String, Money] = Map.empty,
  netPay: Money
):
  def addDeduction(name: String, amount: Money): Payslip =
    val newDeductions = deductions + (name -> amount)
    val newNetPay = grossPay - newDeductions.values.sum
    copy(deductions = newDeductions, netPay = newNetPay)
```

### 控除タイプ

```scala
sealed trait Deduction:
  def name: String
  def calculate(grossPay: Money): Money

case class FixedDeduction(name: String, amount: Money) extends Deduction:
  def calculate(grossPay: Money): Money = amount

case class PercentageDeduction(name: String, rate: Rate) extends Deduction:
  def calculate(grossPay: Money): Money = grossPay * BigDecimal(rate)
```

### 使用例

```scala
val deductions = Seq(
  FixedDeduction("健康保険", BigDecimal(10000)),
  PercentageDeduction("所得税", 0.1)
)
val payslip = Payslip.create(emp, "2024-01", BigDecimal(500000))
val result = applyDeductions(payslip, deductions)
// grossPay: 500000, deductions: {健康保険: 10000, 所得税: 50000}, netPay: 440000
```

## 9. 給与計算サービス

```scala
class PayrollService(
  repository: EmployeeRepository,
  deductions: Seq[Deduction] = Seq.empty
):
  def addEmployee(employee: Employee): Employee = ...
  def removeEmployee(id: EmployeeId): Option[Employee] = ...
  def getEmployee(id: EmployeeId): Option[Employee] = ...
  def updateEmployee(id: EmployeeId, f: Employee => Employee): Option[Employee] = ...

  def runPayroll(context: PayrollContext, date: PayDate, payPeriod: String): Seq[Payslip] =
    val employees = repository.findAll
    employees
      .filter(isPayDay(_, date))
      .map { emp =>
        val grossPay = calculatePay(emp, context)
        val payslip = Payslip.create(emp, payPeriod, grossPay)
        applyDeductions(payslip, deductions)
      }
```

## 10. イベントソーシング

```scala
sealed trait PayrollEvent:
  def timestamp: Long
  def employeeId: EmployeeId

case class EmployeeAdded(timestamp: Long, employeeId: EmployeeId, employee: Employee) extends PayrollEvent
case class EmployeeRemoved(timestamp: Long, employeeId: EmployeeId) extends PayrollEvent
case class TimeCardAdded(timestamp: Long, employeeId: EmployeeId, timeCard: TimeCard) extends PayrollEvent
case class PayrollRun(timestamp: Long, employeeId: EmployeeId, amount: Money) extends PayrollEvent

class EventStore:
  private var events: List[PayrollEvent] = Nil
  def append(event: PayrollEvent): Unit = events = event :: events
  def getEvents: List[PayrollEvent] = events.reverse
  def getEventsByEmployee(employeeId: EmployeeId): List[PayrollEvent] = ...
```

## 11. DSL

```scala
object PayrollDSL:
  def employee(id: EmployeeId)(name: String): EmployeeBuilder =
    EmployeeBuilder(id, name)

  case class EmployeeBuilder(...):
    def salaried(salary: Money): EmployeeBuilder = ...
    def hourly(rate: Money): EmployeeBuilder = ...
    def commissioned(basePay: Money, commissionRate: Rate): EmployeeBuilder = ...
    def withDirectDeposit(account: String): EmployeeBuilder = ...
    def withMailPayment(address: String): EmployeeBuilder = ...
    def build: Employee = ...

// 使用例
import PayrollDSL.*
val emp = employee("E001")("田中太郎")
  .salaried(BigDecimal(500000))
  .withDirectDeposit("1234567890")
  .build
```

## 12. Clojure との比較

| 概念 | Clojure | Scala |
|------|---------|-------|
| ドメインモデル | マップ + Spec | case class + sealed trait |
| 多態性 | マルチメソッド | パターンマッチ / 型クラス |
| 給与計算 | `defmulti`/`defmethod` | `given`/`summon` |
| 支払い方法 | キーワード | enum |
| コンテキスト | マップ | case class |

## 13. 関数型アプローチの利点

1. **テスト容易性**: 純粋関数なのでテストが簡単
2. **拡張容易性**: 新しい給与タイプは新しい型クラスインスタンスを追加するだけ
3. **関心の分離**: 給与計算、スケジュール判定、支払い処理が分離
4. **型安全性**: コンパイル時に多くのエラーを検出

## まとめ

本章では、給与計算システムを通じて以下を学びました：

1. **ドメインモデリング**: sealed trait と case class による表現
2. **型クラス**: 給与タイプによる多態的な計算
3. **イミュータブル設計**: すべての操作が新しいインスタンスを返す
4. **DSL**: 流暢なAPIによる従業員定義

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/scala/part6/src/main/scala/PayrollSystem.scala`
- テストコード: `apps/scala/part6/src/test/scala/PayrollSystemSpec.scala`
