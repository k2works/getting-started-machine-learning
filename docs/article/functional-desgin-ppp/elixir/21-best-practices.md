---
type: Article
title: "第21章: ベストプラクティス"
description: "関数型プログラミングのベストプラクティスを実践的な例とともに解説します。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第21章: ベストプラクティス

## はじめに

関数型プログラミングのベストプラクティスを実践的な例とともに解説します。良いコードと改善が必要なコードを比較しながら学びます。

## 1. 不変性と純粋関数

### 悪い例: 外部状態への依存

```elixir
# ❌ 純粋関数ではない
def get_current_time_greeting do
  hour = DateTime.utc_now().hour
  cond do
    hour < 12 -> "Good morning"
    hour < 18 -> "Good afternoon"
    true -> "Good evening"
  end
end
```

### 良い例: 依存性の注入

```elixir
# ✅ 純粋関数
def greeting_for_hour(hour) when hour >= 0 and hour < 24 do
  cond do
    hour < 12 -> "Good morning"
    hour < 18 -> "Good afternoon"
    true -> "Good evening"
  end
end

# 不純なラッパー
def current_greeting do
  greeting_for_hour(DateTime.utc_now().hour)
end
```

### 不変データの操作

```elixir
# マップの安全な更新
def update_user(user, updates) do
  Map.merge(user, updates)
end

# ネストしたデータの更新
def update_nested(data, path, func) do
  update_in(data, path, func)
end

# リストへの追加（先頭が効率的）
def prepend(list, item), do: [item | list]
```

## 2. パイプラインと関数合成

### 悪い例: ネストした関数呼び出し

```elixir
# ❌ 読みにくい
def process(data) do
  Enum.join(
    Enum.map(
      Enum.filter(
        String.split(data, ","),
        fn s -> String.length(String.trim(s)) > 0 end
      ),
      &String.upcase(String.trim(&1))
    ),
    " | "
  )
end
```

### 良い例: パイプライン

```elixir
# ✅ 読みやすい
def process(data) do
  data
  |> String.split(",")
  |> Enum.map(&String.trim/1)
  |> Enum.filter(&(String.length(&1) > 0))
  |> Enum.map(&String.upcase/1)
  |> Enum.join(" | ")
end
```

### 関数合成

```elixir
# 複数の関数を合成
def pipe_compose(functions) do
  fn x ->
    Enum.reduce(functions, x, fn f, acc -> f.(acc) end)
  end
end

# 使用例
pipeline = pipe_compose([
  fn x -> x + 1 end,
  fn x -> x * 2 end
])
pipeline.(5)  # => 12
```

## 3. エラーハンドリング

### タグ付きタプル

```elixir
# 成功/失敗を明示的に返す
def divide(a, b) when b != 0, do: {:ok, a / b}
def divide(_a, 0), do: {:error, :division_by_zero}

# nilの代わりにタグ付きタプル
def find_user(users, id) do
  case Enum.find(users, &(&1.id == id)) do
    nil -> {:error, :not_found}
    user -> {:ok, user}
  end
end
```

### with構文

```elixir
def process_order(order_id, user_id, users, orders) do
  with {:ok, user} <- find_user(users, user_id),
       {:ok, order} <- find_order(orders, order_id),
       :ok <- validate_owner(user, order) do
    {:ok, %{user: user, order: order}}
  end
end
```

### Railway Oriented Programming

```elixir
defmodule Railway do
  def bind({:ok, value}, func), do: func.(value)
  def bind({:error, _} = error, _func), do: error

  def pipeline(value, functions) do
    Enum.reduce(functions, {:ok, value}, fn func, acc ->
      bind(acc, func)
    end)
  end
end
```

## 4. テスタビリティ

### 依存性注入

```elixir
# HTTPクライアントを注入可能に
def fetch_user(user_id, http_client \\ &default_http_client/1) do
  case http_client.("https://api.example.com/users/#{user_id}") do
    {:ok, body} -> {:ok, parse_json(body)}
    {:error, _} = error -> error
  end
end

# 時計を注入可能に
def is_weekend?(clock \\ &DateTime.utc_now/0) do
  day = clock.() |> Date.day_of_week()
  day in [6, 7]
end

# テストでの使用
test "is_weekend? with mock clock" do
  saturday = fn -> ~D[2024-01-06] end
  assert is_weekend?(saturday)
end
```

### プロパティベーステスト向けの設計

```elixir
# 逆演算が存在する関数
def encode(string), do: Base.encode64(string)
def decode(encoded), do: Base.decode64!(encoded)

# 冪等な関数
def normalize_email(email) do
  email |> String.downcase() |> String.trim()
end
```

## 5. パフォーマンス最適化

### 遅延評価

```elixir
# 大きなデータセットにはStreamを使用
def process_large_file(path) do
  File.stream!(path)
  |> Stream.map(&String.trim/1)
  |> Stream.filter(&(String.length(&1) > 0))
  |> Enum.take(100)
end

# 無限ストリーム
def fibonacci_stream do
  Stream.unfold({0, 1}, fn {a, b} -> {a, {b, a + b}} end)
end
```

### 末尾再帰

```elixir
# ❌ 末尾再帰でない
def sum_bad([]), do: 0
def sum_bad([h | t]), do: h + sum_bad(t)

# ✅ 末尾再帰
def sum_good(list), do: do_sum(list, 0)
defp do_sum([], acc), do: acc
defp do_sum([h | t], acc), do: do_sum(t, acc + h)
```

### データ構造の選択

```elixir
# O(1)のメンバーシップ検査にはMapSetを使用
def member_check_efficient(items, item) do
  set = MapSet.new(items)
  MapSet.member?(set, item)
end

# キーによる高速ルックアップにはMapを使用
def build_index(items, key_fn) do
  Map.new(items, fn item -> {key_fn.(item), item} end)
end
```

## 6. コード構成

### 小さな関数

```elixir
def validate_email(email) do
  email
  |> check_format()
  |> check_domain()
  |> check_length()
end

defp check_format({:error, _} = error), do: error
defp check_format(email) do
  if String.match?(email, ~r/^[\w.+-]+@[\w.-]+\.\w+$/) do
    email
  else
    {:error, :invalid_format}
  end
end
```

### モジュールの責務分離

```elixir
# データ定義
defmodule User do
  defstruct [:id, :name, :email, :role]
end

# ビジネスロジック
defmodule UserService do
  def can_access?(user, resource) do
    user.role in allowed_roles(resource)
  end
end

# フォーマット
defmodule UserFormatter do
  def to_display_name(%User{name: name}), do: name
end
```

## 7. 実践的なパターン

### オプションパターン

```elixir
@default_options [timeout: 5000, retries: 3]

def fetch_data(url, opts \\ []) do
  opts = Keyword.merge(@default_options, opts)
  # ...
end
```

### ビルダーパターン

```elixir
defmodule Builder do
  defstruct fields: %{}, validations: []

  def new, do: %__MODULE__{}
  def set(b, key, value), do: %{b | fields: Map.put(b.fields, key, value)}
  def build(b), do: {:ok, b.fields}
end
```

### メモ化

```elixir
defmodule Memoization do
  use Agent

  def memoize(key, func) do
    case Agent.get(__MODULE__, &Map.get(&1, key)) do
      nil ->
        result = func.()
        Agent.update(__MODULE__, &Map.put(&1, key, result))
        result
      cached ->
        cached
    end
  end
end
```

## まとめ

| ベストプラクティス | 目的 |
|------------------|------|
| 純粋関数 | テスタビリティ、予測可能性 |
| パイプライン | 可読性、データフロー明確化 |
| タグ付きタプル | 明示的なエラーハンドリング |
| 依存性注入 | テスト容易性 |
| 遅延評価 | パフォーマンス |
| 末尾再帰 | メモリ効率 |
| 小さな関数 | 保守性、再利用性 |

これらのプラクティスを意識することで、より堅牢で保守しやすい Elixir コードが書けます。
