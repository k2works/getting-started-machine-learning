---
type: Article
title: "第17章: ビデオレンタルシステム"
description: "ビデオレンタルシステムは、Martin Fowler の「Refactoring」で有名なサンプルコードを Elixir で関数型に再実装したものです。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第17章: ビデオレンタルシステム

## はじめに

ビデオレンタルシステムは、Martin Fowler の「Refactoring」で有名なサンプルコードを Elixir で関数型に再実装したものです。Strategy パターンに相当する価格計算を、パターンマッチングと関数の合成で実現します。

## 1. 映画と価格設定

### Movie 構造体

```elixir
defmodule Movie do
  @type price_code :: :regular | :new_release | :children

  defstruct [:title, :price_code]

  def new(title, price_code) do
    %__MODULE__{title: title, price_code: price_code}
  end

  def regular(title), do: new(title, :regular)
  def new_release(title), do: new(title, :new_release)
  def children(title), do: new(title, :children)
end
```

### 価格計算モジュール

```elixir
defmodule Pricing do
  @doc "レンタル料金を計算"
  def calculate_amount(movie, days_rented) do
    case movie.price_code do
      :regular -> regular_price(days_rented)
      :new_release -> new_release_price(days_rented)
      :children -> children_price(days_rented)
    end
  end

  defp regular_price(days) do
    if days > 2, do: 2.0 + (days - 2) * 1.5, else: 2.0
  end

  defp new_release_price(days) do
    days * 3.0
  end

  defp children_price(days) do
    if days > 3, do: 1.5 + (days - 3) * 1.5, else: 1.5
  end
end
```

### ポイント計算

```elixir
def calculate_points(movie, days_rented) do
  base_points = 1
  bonus = if movie.price_code == :new_release and days_rented > 1, do: 1, else: 0
  base_points + bonus
end
```

## 2. レンタル管理

### Rental 構造体

```elixir
defmodule Rental do
  defstruct [:movie, :days_rented]

  def new(movie, days_rented) do
    %__MODULE__{movie: movie, days_rented: days_rented}
  end

  def amount(rental) do
    Pricing.calculate_amount(rental.movie, rental.days_rented)
  end

  def points(rental) do
    Pricing.calculate_points(rental.movie, rental.days_rented)
  end
end
```

## 3. 明細生成

### Statement モジュール

```elixir
defmodule Statement do
  @doc "テキスト形式の明細を生成"
  def generate(customer_name, rentals) do
    {total, points, lines} = calculate_statement(rentals)

    header = "Rental Record for #{customer_name}\n"
    body = Enum.join(lines, "\n")
    footer = """

    Amount owed is #{format_currency(total)}
    You earned #{points} frequent renter points
    """

    header <> body <> footer
  end

  defp calculate_statement(rentals) do
    Enum.reduce(rentals, {0.0, 0, []}, fn rental, {total, points, lines} ->
      amount = Rental.amount(rental)
      rental_points = Rental.points(rental)
      line = "\t#{rental.movie.title}\t#{format_currency(amount)}"
      {total + amount, points + rental_points, lines ++ [line]}
    end)
  end

  defp format_currency(amount) do
    :io_lib.format("~.2f", [amount]) |> to_string()
  end
end
```

### HTML 形式の明細

```elixir
def generate_html(customer_name, rentals) do
  {total, points, rows} = calculate_html_statement(rentals)

  """
  <html>
  <head><title>Rental Statement</title></head>
  <body>
  <h1>Rental Record for #{customer_name}</h1>
  <table>
  <tr><th>Title</th><th>Amount</th></tr>
  #{Enum.join(rows, "\n")}
  </table>
  <p>Amount owed is <strong>#{format_currency(total)}</strong></p>
  <p>You earned <strong>#{points}</strong> frequent renter points</p>
  </body>
  </html>
  """
end
```

## 4. 顧客管理

### Customer モジュール

```elixir
defmodule Customer do
  defstruct [:name, rentals: []]

  def new(name) do
    %__MODULE__{name: name}
  end

  def add_rental(customer, rental) do
    %{customer | rentals: customer.rentals ++ [rental]}
  end

  def statement(customer) do
    Statement.generate(customer.name, customer.rentals)
  end

  def html_statement(customer) do
    Statement.generate_html(customer.name, customer.rentals)
  end

  def total_amount(customer) do
    customer.rentals
    |> Enum.map(&Rental.amount/1)
    |> Enum.sum()
  end

  def total_points(customer) do
    customer.rentals
    |> Enum.map(&Rental.points/1)
    |> Enum.sum()
  end
end
```

## 5. リポジトリパターン

### MovieRepository (Agent ベース)

```elixir
defmodule MovieRepository do
  use Agent

  def start_link(_opts \\ []) do
    Agent.start_link(fn -> %{} end, name: __MODULE__)
  end

  def add(movie) do
    Agent.update(__MODULE__, fn movies ->
      Map.put(movies, movie.title, movie)
    end)
  end

  def find(title) do
    Agent.get(__MODULE__, fn movies ->
      Map.get(movies, title)
    end)
  end

  def all do
    Agent.get(__MODULE__, fn movies ->
      Map.values(movies)
    end)
  end

  def clear do
    Agent.update(__MODULE__, fn _ -> %{} end)
  end
end
```

## 6. レンタルサービス

### サービス層

```elixir
defmodule RentalService do
  @doc "映画をレンタル"
  def rent_movie(customer, movie_title, days) do
    case MovieRepository.find(movie_title) do
      nil -> {:error, :movie_not_found}
      movie ->
        rental = Rental.new(movie, days)
        {:ok, Customer.add_rental(customer, rental)}
    end
  end

  @doc "複数映画を一括レンタル"
  def rent_movies(customer, rentals_data) do
    Enum.reduce_while(rentals_data, {:ok, customer}, fn {title, days}, {:ok, c} ->
      case rent_movie(c, title, days) do
        {:ok, updated} -> {:cont, {:ok, updated}}
        {:error, _} = err -> {:halt, err}
      end
    end)
  end

  @doc "推奨映画を取得"
  def recommend_movies(customer) do
    rented_titles = MapSet.new(customer.rentals, & &1.movie.title)

    MovieRepository.all()
    |> Enum.reject(fn m -> MapSet.member?(rented_titles, m.title) end)
    |> Enum.sort_by(& &1.title)
  end
end
```

## 7. 関数型リファクタリングのポイント

### OOP との比較

| OOP アプローチ | 関数型アプローチ |
|--------------|---------------|
| 継承による価格タイプの実装 | パターンマッチによる分岐 |
| オブジェクトに状態を持つ | 構造体は不変、関数で変換 |
| Repository クラス | Agent + 純粋関数 |
| Statement クラスのメソッド | 独立した関数群 |

### パイプラインによる明確な処理フロー

```elixir
# 顧客の明細生成
customer
|> Customer.add_rental(rental1)
|> Customer.add_rental(rental2)
|> Customer.statement()
```

### 変換の分離

```elixir
# 計算と表示の分離
{total, points, lines} = calculate_statement(rentals)  # 純粋な計算
format_statement(total, points, lines)                  # フォーマット
```

## 8. テストの例

```elixir
test "regular movie pricing" do
  movie = Movie.regular("The Matrix")
  assert Pricing.calculate_amount(movie, 1) == 2.0
  assert Pricing.calculate_amount(movie, 2) == 2.0
  assert Pricing.calculate_amount(movie, 3) == 3.5
end

test "customer statement" do
  movie1 = Movie.regular("The Matrix")
  movie2 = Movie.new_release("Avatar 2")

  customer = Customer.new("John")
              |> Customer.add_rental(Rental.new(movie1, 3))
              |> Customer.add_rental(Rental.new(movie2, 2))

  statement = Customer.statement(customer)
  assert String.contains?(statement, "John")
  assert String.contains?(statement, "The Matrix")
  assert String.contains?(statement, "Avatar 2")
end
```

## まとめ

ビデオレンタルシステムは、OOP のクラシックな例題を関数型で書き直すことで、以下の洞察を得られます：

1. **継承なしの多態性**: パターンマッチングで価格タイプごとの処理を実現
2. **不変データ**: 顧客やレンタルは変更ではなく新しい値を生成
3. **関数の合成**: 小さな純粋関数を組み合わせて複雑な処理を構築
4. **責務の分離**: 計算、フォーマット、永続化が明確に分離

関数型プログラミングにより、元のコードよりもテスタブルで保守しやすい設計になります。
