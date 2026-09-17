---
type: Article
title: "第2章: 関数合成と高階関数"
description: "関数型プログラミングの真髄は、小さな関数を組み合わせて複雑な処理を構築することにあります。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第2章: 関数合成と高階関数

## はじめに

関数型プログラミングの真髄は、小さな関数を組み合わせて複雑な処理を構築することにあります。本章では、Elixir における関数合成のテクニックと高階関数の活用方法を学びます。

## 1. 関数合成の基本

Elixir では無名関数とパイプ演算子を組み合わせて関数を合成できます。

```elixir
def add_tax(rate) do
  fn amount -> amount * (1 + rate) end
end

def apply_discount(rate) do
  fn amount -> amount * (1 - rate) end
end

def round_to_yen(amount), do: round(amount)

# 関数合成（左から右へ）
def compose(fns) do
  fn input ->
    Enum.reduce(fns, input, fn f, acc -> f.(acc) end)
  end
end

# 価格計算パイプライン
def calculate_final_price do
  discount = apply_discount(0.2)
  tax = add_tax(0.1)

  fn amount ->
    amount
    |> discount.()
    |> tax.()
    |> round_to_yen()
  end
end

# 使用例
calculate = calculate_final_price()
calculate.(1000)
# => 880
# 処理順序: 1000 → 20%割引(800) → 10%税込(880) → 丸め(880)
```

### 関数合成の利点

1. **宣言的な記述**: 処理の流れを関数のチェーンとして表現
2. **再利用性**: 合成した関数を別の場所で再利用可能
3. **テスト容易性**: 各関数を個別にテスト可能

## 2. カリー化と部分適用

Elixir では無名関数を返す関数を定義することで、カリー化と部分適用を実現できます。

```elixir
# カリー化された関数
def greet(greeting) do
  fn name -> "#{greeting}, #{name}!" end
end

say_hello = greet("Hello")
say_goodbye = greet("Goodbye")

say_hello.("田中")    # => "Hello, 田中!"
say_goodbye.("鈴木")  # => "Goodbye, 鈴木!"
```

### 複数引数の部分適用

```elixir
defmodule Email do
  defstruct [:from, :to, :subject, :body]
end

def send_email(from) do
  fn to ->
    fn subject ->
      fn body ->
        %Email{from: from, to: to, subject: subject, body: body}
      end
    end
  end
end

send_from_system = send_email("system@example.com")
send_notification = send_from_system.("user@example.com").("通知")

send_notification.("メッセージ本文")
# => %Email{from: "system@example.com",
#           to: "user@example.com",
#           subject: "通知",
#           body: "メッセージ本文"}
```

## 3. 複数の関数を並列適用（juxt）

Clojure の `juxt` に相当する機能は、Elixir ではタプルや関数を使って表現します。

```elixir
# 数値リストの統計情報を取得する
def get_stats(numbers) do
  {
    hd(numbers),
    List.last(numbers),
    length(numbers),
    Enum.min(numbers),
    Enum.max(numbers)
  }
end

get_stats([3, 1, 4, 1, 5, 9, 2, 6])
# => {3, 6, 8, 1, 9}
# (最初の値, 最後の値, 要素数, 最小値, 最大値)

# juxt の汎用実装
def juxt(fns) do
  fn input ->
    fns
    |> Enum.map(fn f -> f.(input) end)
    |> List.to_tuple()
  end
end

juxt_fn = juxt([&Enum.min/1, &Enum.max/1, &Enum.sum/1])
juxt_fn.([1, 2, 3, 4, 5])
# => {1, 5, 15}
```

## 4. 高階関数によるデータ処理

高階関数とは、関数を引数として受け取るか、関数を返す関数のことです。

### ログ出力のラッパー

```elixir
def with_logging(f) do
  fn input ->
    IO.puts("入力: #{inspect(input)}")
    result = f.(input)
    IO.puts("出力: #{inspect(result)}")
    result
  end
end

double_with_log = with_logging(fn x -> x * 2 end)
double_with_log.(5)
# 入力: 5
# 出力: 10
# => 10
```

### リトライ機能の追加

```elixir
def with_retry(f, max_retries) do
  fn input ->
    do_retry(f, input, max_retries, 0)
  end
end

defp do_retry(f, input, max_retries, attempts) do
  try do
    f.(input)
  rescue
    e ->
      if attempts < max_retries do
        do_retry(f, input, max_retries, attempts + 1)
      else
        reraise e, __STACKTRACE__
      end
  end
end

# 不安定なAPI呼び出しをリトライ付きでラップ
fetch_with_retry = with_retry(fetch_data, 3)
```

### メモ化

```elixir
def memoize(f) do
  {:ok, agent} = Agent.start_link(fn -> %{} end)

  memoized_fn = fn input ->
    Agent.get_and_update(agent, fn cache ->
      case Map.get(cache, input) do
        nil ->
          result = f.(input)
          {result, Map.put(cache, input, result)}

        cached ->
          {cached, cache}
      end
    end)
  end

  {:ok, memoized_fn}
end
```

## 5. パイプライン処理

複数の関数を順次適用するパイプラインを構築します。

```elixir
defmodule OrderItem do
  defstruct [:price, :quantity]
end

defmodule Customer do
  defstruct [:membership]
end

defmodule Order do
  defstruct [:items, :customer, total: 0, shipping: 0]
end

def validate_order(%Order{items: []} = _order) do
  raise ArgumentError, "注文にアイテムがありません"
end

def validate_order(%Order{} = order), do: order

def calculate_order_total(%Order{items: items} = order) do
  total = Enum.reduce(items, 0, fn item, acc ->
    acc + item.price * item.quantity
  end)
  %{order | total: total}
end

def apply_order_discount(%Order{customer: customer, total: total} = order) do
  discount_rates = %{"gold" => 0.1, "silver" => 0.05, "bronze" => 0.02}
  discount_rate = Map.get(discount_rates, customer.membership, 0.0)
  %{order | total: total * (1 - discount_rate)}
end

def add_shipping(%Order{total: total} = order) do
  shipping = if total >= 5000, do: 0, else: 500
  %{order | shipping: shipping, total: total + shipping}
end

# パイプラインで注文を処理
def process_order_pipeline(order) do
  order
  |> validate_order()
  |> calculate_order_total()
  |> apply_order_discount()
  |> add_shipping()
end
```

## 6. 関数合成によるバリデーション

バリデーションロジックを関数合成で表現します。

```elixir
defmodule ValidationResult do
  defstruct [:valid, :value, :error]
end

def validator(pred, error_msg) do
  fn value ->
    if pred.(value) do
      %ValidationResult{valid: true, value: value, error: nil}
    else
      %ValidationResult{valid: false, value: value, error: error_msg}
    end
  end
end

def combine_validators(validators) do
  fn value ->
    Enum.reduce_while(validators, %ValidationResult{valid: true, value: value},
      fn v, acc ->
        result = v.(acc.value)
        if result.valid, do: {:cont, result}, else: {:halt, result}
      end)
  end
end

# 個別のバリデータ
is_positive = validator(fn x -> x > 0 end, "値は正の数である必要があります")
under_100 = validator(fn x -> x < 100 end, "値は100未満である必要があります")

# バリデータの合成
validate_quantity = combine_validators([is_positive, under_100])

validate_quantity.(50)   # => %ValidationResult{valid: true, value: 50, error: nil}
validate_quantity.(-1)   # => %ValidationResult{valid: false, value: -1, error: "..."}
```

## 7. 関数の変換

関数自体を変換するユーティリティ関数を作成します。

```elixir
# 引数の順序を反転
def flip(f) do
  fn b, a -> f.(a, b) end
end

subtract = fn a, b -> a - b end
flip(subtract).(3, 5)  # => 2  (5 - 3)

# カリー化
def curry(f) do
  fn a -> fn b -> f.(a, b) end end
end

add = fn a, b -> a + b end
curried_add = curry(add)
add_5 = curried_add.(5)
add_5.(3)  # => 8

# 補関数（complement）
def complement(pred) do
  fn x -> not pred.(x) end
end

is_even = fn x -> rem(x, 2) == 0 end
is_odd = complement(is_even)
is_odd.(3)  # => true
```

## 8. 述語の合成

```elixir
def compose_predicates_and(preds) do
  fn x -> Enum.all?(preds, fn pred -> pred.(x) end) end
end

def compose_predicates_or(preds) do
  fn x -> Enum.any?(preds, fn pred -> pred.(x) end) end
end

# 有効な年齢チェック
valid_age = compose_predicates_and([
  fn x -> x > 0 end,
  fn x -> x <= 150 end
])

valid_age.(25)   # => true
valid_age.(-1)   # => false
valid_age.(200)  # => false
```

## まとめ

本章では、関数合成と高階関数について学びました：

1. **関数合成**: 複数の関数を組み合わせて新しい関数を作成
2. **カリー化**: 引数を部分適用して特化した関数を作成
3. **juxt**: 複数の関数を並列適用して結果を取得
4. **高階関数**: ログ、リトライ、メモ化などの横断的関心事を抽象化
5. **パイプライン**: `|>` 演算子で処理の流れを関数のチェーンとして表現
6. **バリデーション**: 関数合成による柔軟な検証ロジック
7. **述語合成**: AND/OR で複数の条件を組み合わせ

これらのテクニックにより、小さく再利用可能な関数から複雑なビジネスロジックを構築できます。

## 参考コード

本章のコード例は以下のファイルで確認できます：

- ソースコード: `apps/elixir/part1/lib/chapter02.ex`
- テストコード: `apps/elixir/part1/test/chapter02_test.exs`

## 次章予告

次章では、**多態性の実現方法**について学びます。プロトコル、ビヘイビア、パターンマッチングを活用した柔軟な設計パターンを探ります。
