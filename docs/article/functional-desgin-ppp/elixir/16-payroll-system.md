---
type: Article
title: "第16章: 給与計算システム"
description: "給与計算システムは、従業員の給与計算と支払い処理を統合的に管理するシステムです。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第16章: 給与計算システム

## はじめに

給与計算システムは、従業員の給与計算と支払い処理を統合的に管理するシステムです。パターンマッチングによる多態性を活用して、異なる給与タイプに対応した計算ロジックを実装します。

## 1. 従業員モデル

### Employee 構造体

```elixir
defmodule Employee do
  @type pay_type :: :salaried | :hourly | :commissioned
  @type schedule :: :monthly | :weekly | :biweekly
  @type payment_method :: :hold | :direct_deposit | :mail

  defstruct [
    :id,
    :name,
    :address,
    :pay_class,
    :schedule,
    :payment_method
  ]
end
```

### 従業員の作成

```elixir
# 月給制従業員
def make_salaried(id, name, salary) do
  %Employee{
    id: id,
    name: name,
    pay_class: {:salaried, salary},
    schedule: :monthly,
    payment_method: :hold
  }
end

# 時給制従業員
def make_hourly(id, name, hourly_rate) do
  %Employee{
    id: id,
    name: name,
    pay_class: {:hourly, hourly_rate},
    schedule: :weekly,
    payment_method: :hold
  }
end

# 歩合制従業員
def make_commissioned(id, name, base_pay, commission_rate) do
  %Employee{
    id: id,
    name: name,
    pay_class: {:commissioned, base_pay, commission_rate},
    schedule: :biweekly,
    payment_method: :hold
  }
end
```

### 使用例

```elixir
# 月給50万円の従業員
salaried = Employee.make_salaried("E001", "田中太郎", 500_000)

# 時給1500円の従業員
hourly = Employee.make_hourly("E002", "佐藤花子", 1500)

# 基本給20万円 + 売上の10%の従業員
commissioned = Employee.make_commissioned("E003", "鈴木一郎", 200_000, 0.1)
```

## 2. 給与計算

### パターンマッチングによる計算

```elixir
defmodule Classification do
  # 月給制：そのまま月給を返す
  def calc_pay(%Employee{pay_class: {:salaried, salary}}, _context) do
    salary
  end

  # 時給制：タイムカードから計算（残業は1.5倍）
  def calc_pay(%Employee{pay_class: {:hourly, hourly_rate}} = employee, context) do
    time_cards = get_in(context, [:time_cards, Employee.get_id(employee)]) || []
    hours = Enum.map(time_cards, fn {_date, hours} -> hours end)
    total_hours = Enum.sum(hours)

    regular_hours = min(total_hours, 40)
    overtime_hours = max(0, total_hours - 40)

    regular_hours * hourly_rate + overtime_hours * hourly_rate * 1.5
  end

  # 歩合制：基本給 + 売上 × コミッション率
  def calc_pay(%Employee{pay_class: {:commissioned, base_pay, commission_rate}} = employee, context) do
    sales_receipts = get_in(context, [:sales_receipts, Employee.get_id(employee)]) || []
    total_sales = Enum.sum(Enum.map(sales_receipts, fn {_date, amount} -> amount end))

    base_pay + total_sales * commission_rate
  end
end
```

### コンテキストの管理

```elixir
defmodule Classification do
  def make_context do
    %{
      time_cards: %{},
      sales_receipts: %{}
    }
  end

  def add_time_card(context, employee_id, date, hours) do
    update_in(context, [:time_cards, employee_id], fn
      nil -> [{date, hours}]
      cards -> [{date, hours} | cards]
    end)
  end

  def add_sales_receipt(context, employee_id, date, amount) do
    update_in(context, [:sales_receipts, employee_id], fn
      nil -> [{date, amount}]
      receipts -> [{date, amount} | receipts]
    end)
  end
end
```

### 使用例

```elixir
# 時給制従業員の給与計算
hourly = Employee.make_hourly("E002", "佐藤", 1500)
ctx =
  Classification.make_context()
  |> Classification.add_time_card("E002", "2024-01-15", 8)
  |> Classification.add_time_card("E002", "2024-01-16", 8)

Classification.calc_pay(hourly, ctx)  #=> 24000.0

# 残業を含む場合
ctx_overtime =
  Classification.make_context()
  |> Classification.add_time_card("E002", "2024-01-15", 45)

Classification.calc_pay(hourly, ctx_overtime)
#=> 40 * 1500 + 5 * 1500 * 1.5 = 67500.0
```

## 3. 支払いスケジュール

```elixir
defmodule Schedule do
  # 月給制：月末が支払日
  def is_pay_day?(%Employee{schedule: :monthly}, date) do
    is_last_day_of_month?(date)
  end

  # 時給制：毎週金曜日
  def is_pay_day?(%Employee{schedule: :weekly}, date) do
    Map.get(date, :day_of_week) == :friday
  end

  # 歩合制：隔週金曜日
  def is_pay_day?(%Employee{schedule: :biweekly}, date) do
    Map.get(date, :day_of_week) == :friday and
      Map.get(date, :is_pay_week, false) == true
  end

  # 日付ファクトリ
  def end_of_month(year, month), do: # ...
  def friday(year, month, day), do: # ...
  def pay_friday(year, month, day), do: # ...
end
```

## 4. 支払い処理

```elixir
defmodule Payment do
  def process(%Employee{payment_method: :hold} = employee, amount) do
    %{
      type: :hold,
      employee_id: Employee.get_id(employee),
      amount: amount,
      message: "支払いを保留"
    }
  end

  def process(%Employee{payment_method: :direct_deposit} = employee, amount) do
    %{
      type: :direct_deposit,
      employee_id: Employee.get_id(employee),
      amount: amount,
      message: "口座に振り込み"
    }
  end

  def process(%Employee{payment_method: :mail} = employee, amount) do
    %{
      type: :mail,
      employee_id: Employee.get_id(employee),
      amount: amount,
      message: "小切手を郵送"
    }
  end
end
```

## 5. 給与計算システム

### Payroll モジュール

```elixir
defmodule Payroll do
  def calculate_pay(employee, context) do
    Classification.calc_pay(employee, context)
  end

  def calculate_payroll(employees, context) do
    Enum.map(employees, fn emp ->
      %{employee: emp, pay: calculate_pay(emp, context)}
    end)
  end

  def run_payroll(employees, context, date) do
    employees
    |> Enum.filter(&Schedule.is_pay_day?(&1, date))
    |> Enum.map(fn emp ->
      pay = calculate_pay(emp, context)
      Payment.process(emp, pay)
    end)
  end

  def payroll_report(employees, context) do
    payroll = calculate_payroll(employees, context)
    total = Enum.sum(Enum.map(payroll, & &1.pay))

    %{
      employees: Enum.map(payroll, fn %{employee: emp, pay: pay} ->
        %{
          id: Employee.get_id(emp),
          name: Employee.get_name(emp),
          type: Employee.get_pay_type(emp),
          pay: pay
        }
      end),
      total: total,
      count: length(employees)
    }
  end
end
```

## 6. 従業員リポジトリ

```elixir
defmodule EmployeeRepository do
  def start_link do
    Agent.start_link(fn -> %{} end)
  end

  def add(pid, %Employee{} = employee) do
    Agent.update(pid, &Map.put(&1, employee.id, employee))
    {:ok, employee}
  end

  def get(pid, id) do
    case Agent.get(pid, &Map.get(&1, id)) do
      nil -> {:error, :not_found}
      employee -> {:ok, employee}
    end
  end

  def get_all(pid) do
    Agent.get(pid, &Map.values(&1))
  end

  def update(pid, id, update_fn) do
    Agent.get_and_update(pid, fn state ->
      case Map.get(state, id) do
        nil ->
          {{:error, :not_found}, state}
        employee ->
          updated = update_fn.(employee)
          {{:ok, updated}, Map.put(state, id, updated)}
      end
    end)
  end

  def delete(pid, id) do
    Agent.get_and_update(pid, fn state ->
      case Map.pop(state, id) do
        {nil, state} -> {{:error, :not_found}, state}
        {employee, state} -> {{:ok, employee}, state}
      end
    end)
  end
end
```

## 7. サービス層

```elixir
defmodule PayrollService do
  defstruct [:repo, :context]

  def new do
    {:ok, repo} = EmployeeRepository.start_link()
    %__MODULE__{
      repo: repo,
      context: Classification.make_context()
    }
  end

  def add_salaried_employee(service, id, name, salary) do
    employee = Employee.make_salaried(id, name, salary)
    EmployeeRepository.add(service.repo, employee)
  end

  def add_time_card(%__MODULE__{context: ctx} = service, employee_id, date, hours) do
    new_ctx = Classification.add_time_card(ctx, employee_id, date, hours)
    %{service | context: new_ctx}
  end

  def run_payroll(service, date) do
    employees = EmployeeRepository.get_all(service.repo)
    Payroll.run_payroll(employees, service.context, date)
  end

  def payroll_report(service) do
    employees = EmployeeRepository.get_all(service.repo)
    Payroll.payroll_report(employees, service.context)
  end
end
```

## 8. 完全なワークフロー例

```elixir
# サービスを作成
service = PayrollService.new()

# 従業員を追加
PayrollService.add_salaried_employee(service, "E001", "田中太郎", 500_000)
PayrollService.add_hourly_employee(service, "E002", "佐藤花子", 1500)
PayrollService.add_commissioned_employee(service, "E003", "鈴木一郎", 200_000, 0.1)

# 支払い方法を変更
PayrollService.change_payment_method(service, "E001", :direct_deposit)

# タイムカードを追加
service = PayrollService.add_time_card(service, "E002", "2024-01-15", 8)
service = PayrollService.add_time_card(service, "E002", "2024-01-16", 8)

# 売上レシートを追加
service = PayrollService.add_sales_receipt(service, "E003", "2024-01-15", 100_000)

# レポートを生成
report = PayrollService.payroll_report(service)
#=> %{count: 3, total: 722_000.0, employees: [...]}

# 給与を支払い（月末）
end_of_month = Schedule.end_of_month(2024, 1)
payments = PayrollService.run_payroll(service, end_of_month)
#=> 月給制従業員への支払いが処理される

# クリーンアップ
PayrollService.stop(service)
```

## 設計のポイント

### パターンマッチングによる多態性

Clojure のマルチメソッドに相当する機能を、Elixir ではパターンマッチングで実現しています。

```elixir
# 給与タイプごとに異なる計算ロジック
def calc_pay(%Employee{pay_class: {:salaried, salary}}, _), do: salary
def calc_pay(%Employee{pay_class: {:hourly, rate}}, ctx), do: # ...
def calc_pay(%Employee{pay_class: {:commissioned, base, rate}}, ctx), do: # ...
```

### タプルによる分類データの表現

```elixir
pay_class: {:salaried, 500_000}
pay_class: {:hourly, 1500}
pay_class: {:commissioned, 200_000, 0.1}
```

### Agent による状態管理

リポジトリは `Agent` を使用して状態を管理します。これにより、関数型の純粋性を保ちながら、必要な箇所でのみ状態を持つことができます。

### コンテキストによる補助データの管理

タイムカードや売上レシートは、コンテキスト（マップ）として管理し、必要に応じて給与計算に渡します。

## まとめ

給与計算システムは以下の関数型プログラミングの概念を活用しています：

1. **パターンマッチング**: 給与タイプごとの計算ロジックを明確に分離
2. **不変データ構造**: 従業員データの安全な管理
3. **高階関数**: リスト処理の簡潔な実装
4. **Agent**: 必要最小限の状態管理
5. **パイプライン**: データ変換の連鎖的な処理

Elixir のパターンマッチングと関数型の特性により、複雑なビジネスロジックを明確かつ保守しやすい形で実装できます。
