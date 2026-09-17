---
type: Article
title: "Chapter 05: プロパティベーステスト"
description: "Elixir における StreamData を使ったプロパティベーステストについて学びます。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# Chapter 05: プロパティベーステスト

Elixir における StreamData を使ったプロパティベーステストについて学びます。

## 概要

従来の例示ベーステストでは、特定の入力に対する期待される出力を手動で指定します。
プロパティベーステストでは、**すべての入力に対して成り立つべき性質（プロパティ）**を
定義し、ランダムに生成された多数の入力でその性質を検証します。

## 主なトピック

1. **ジェネレータの基本**
2. **プロパティの定義**
3. **収縮（シュリンキング）**
4. **カスタムジェネレータ**
5. **ドメイン固有のプロパティ**

## StreamData ライブラリ

Elixir でプロパティベーステストを行うには、`stream_data` ライブラリを使用します。

```elixir
# mix.exs
defp deps do
  [
    {:stream_data, "~> 1.0", only: :test}
  ]
end
```

## プロパティの種類

### 冪等性（Idempotency）

同じ操作を複数回適用しても結果が変わらない性質。

```elixir
property "sort は冪等" do
  check all list <- list_of(integer()) do
    assert Enum.sort(Enum.sort(list)) == Enum.sort(list)
  end
end
```

### ラウンドトリップ（Round-trip）

エンコード・デコードで元の値に戻る性質。

```elixir
property "encode/decode はラウンドトリップ" do
  check all n <- integer() do
    encoded = encode(n)
    {:ok, decoded} = decode(encoded)
    assert decoded == n
  end
end
```

### 不変条件（Invariant）

操作の前後で保存される性質。

```elixir
property "reverse は長さを保存する" do
  check all list <- list_of(term()) do
    assert length(Enum.reverse(list)) == length(list)
  end
end
```

### 反転（Inverse）

ある操作とその逆操作。

```elixir
property "reverse の reverse は元に戻る" do
  check all list <- list_of(integer()) do
    assert Enum.reverse(Enum.reverse(list)) == list
  end
end
```

## 基本的なジェネレータ

```elixir
use ExUnitProperties

# 整数
integer()           # 任意の整数
positive_integer()  # 正の整数
non_negative_integer()  # 0 以上の整数
integer(1..100)     # 範囲付き整数

# 文字列
string(:alphanumeric)
string(:printable, min_length: 1, max_length: 10)

# リスト
list_of(integer())
list_of(integer(), min_length: 1, max_length: 5)

# マップ
map_of(atom(:alphanumeric), integer())

# その他
boolean()
binary()
term()
member_of(["a", "b", "c"])
```

## カスタムジェネレータ

```elixir
# メールアドレスのカスタムジェネレータ
defp email_generator do
  gen all local <- string(:alphanumeric, min_length: 1, max_length: 10),
          domain <- string(:alphanumeric, min_length: 1, max_length: 10),
          tld <- member_of(["com", "org", "net", "io"]) do
    "#{local}@#{domain}.#{tld}"
  end
end

property "生成されたメールは有効" do
  check all email <- email_generator() do
    assert String.contains?(email, "@")
    assert String.contains?(email, ".")
  end
end
```

## 実装例

### 数学関数のプロパティ

```elixir
defmodule Chapter05 do
  @doc """
  絶対値を返す。
  
  ## プロパティ
  - 結果は常に非負
  - 冪等性: abs(abs(x)) == abs(x)
  - 偶関数: abs(-x) == abs(x)
  """
  def absolute(n) when n < 0, do: -n
  def absolute(n), do: n
end
```

```elixir
describe "absolute/1 のプロパティ" do
  property "結果は常に非負" do
    check all n <- integer() do
      assert Chapter05.absolute(n) >= 0
    end
  end

  property "冪等性" do
    check all n <- integer() do
      assert Chapter05.absolute(Chapter05.absolute(n)) == Chapter05.absolute(n)
    end
  end

  property "偶関数" do
    check all n <- integer() do
      assert Chapter05.absolute(-n) == Chapter05.absolute(n)
    end
  end
end
```

### ドメインモデルのプロパティ

```elixir
defmodule Money do
  defstruct [:amount, :currency]

  def new(amount, currency), do: %__MODULE__{amount: amount, currency: currency}
  
  def add(%{currency: c} = m1, %{currency: c} = m2) do
    {:ok, new(m1.amount + m2.amount, c)}
  end
  def add(_, _), do: {:error, "Currency mismatch"}
  
  def zero(currency), do: new(0, currency)
end
```

```elixir
describe "Money のモノイド則" do
  defp money_generator(currency) do
    gen all amount <- integer() do
      Money.new(amount, currency)
    end
  end

  property "結合律" do
    check all a <- money_generator("JPY"),
              b <- money_generator("JPY"),
              c <- money_generator("JPY") do
      {:ok, ab} = Money.add(a, b)
      {:ok, ab_c} = Money.add(ab, c)
      {:ok, bc} = Money.add(b, c)
      {:ok, a_bc} = Money.add(a, bc)
      assert ab_c == a_bc
    end
  end

  property "単位元" do
    check all m <- money_generator("JPY") do
      zero = Money.zero("JPY")
      {:ok, result} = Money.add(m, zero)
      assert result == m
    end
  end

  property "可換律" do
    check all a <- money_generator("JPY"),
              b <- money_generator("JPY") do
      {:ok, ab} = Money.add(a, b)
      {:ok, ba} = Money.add(b, a)
      assert ab == ba
    end
  end
end
```

## 収縮（シュリンキング）

テストが失敗した場合、StreamData は失敗を引き起こす最小の入力を見つけようとします。

```elixir
# 故意に失敗するテスト（デモ用）
property "リストの合計は100未満" do
  check all list <- list_of(positive_integer(), min_length: 1) do
    assert Enum.sum(list) < 100
  end
end

# 出力例:
# Failed with generated values (after 5 successful runs):
#     * list <- [100]
# 
# 収縮により、最小の反例 [100] が見つかる
```

## テストのベストプラクティス

1. **明確なプロパティを定義する** - 何を検証するか明確に
2. **ジェネレータを適切に制約する** - 無効な入力を生成しない
3. **カスタムジェネレータを活用する** - ドメイン固有の値を生成
4. **収縮を理解する** - 最小の反例を活用
5. **例示テストと併用する** - 両方のアプローチを使う

## まとめ

- **プロパティベーステスト**で多くのケースを自動検証
- **StreamData**で Elixir のプロパティテストを実現
- **カスタムジェネレータ**でドメイン固有のテストデータを生成
- **収縮**で最小の失敗ケースを特定
- **例示テストと併用**で堅牢なテストスイートを構築

## 関連リソース

- [StreamData ドキュメント](https://hexdocs.pm/stream_data/)
- [Property-based testing with PropEr, Erlang, and Elixir](https://pragprog.com/titles/fhproper/property-based-testing-with-proper-erlang-and-elixir/)
