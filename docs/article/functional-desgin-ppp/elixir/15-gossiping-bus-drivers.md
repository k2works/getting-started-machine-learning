---
type: Article
title: "第15章: ゴシップ好きなバスの運転手"
description: "「ゴシップ好きなバスの運転手」は、バス運転手が停留所で噂を共有するシミュレーション問題です。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第15章: ゴシップ好きなバスの運転手

## はじめに

「ゴシップ好きなバスの運転手」は、バス運転手が停留所で噂を共有するシミュレーション問題です。無限ストリーム（`Stream.cycle`）を使って循環ルートを表現し、集合演算（`MapSet`）で噂の伝播をモデル化します。

## 1. 問題の概要

- 複数のバス運転手がそれぞれのルートを循環する
- 同じ停留所に居合わせたドライバー同士は噂を共有する
- 全員が全ての噂を知るまでの時間を求める
- 8時間（480分）以内に共有されない場合は `:never` を返す

## 2. ドライバーの作成と操作

### Driver 構造体

```elixir
defmodule Driver do
  defstruct [:name, :route, :rumors]

  def new(name, route, rumors) when is_list(route) do
    %__MODULE__{
      name: name,
      route: Stream.cycle(route),  # 無限ストリームで循環ルート
      rumors: MapSet.new(rumors)
    }
  end

  def current_stop(%__MODULE__{route: route}) do
    Enum.at(route, 0)
  end

  def move(%__MODULE__{route: route} = driver) do
    %{driver | route: Stream.drop(route, 1)}
  end

  def set_rumors(%__MODULE__{} = driver, rumors) do
    %{driver | rumors: rumors}
  end
end
```

### 使用例

```elixir
# ドライバーを作成
driver = Driver.new("Alice", [1, 2, 3], [:rumor_a])

# 現在の停留所
Driver.current_stop(driver)  #=> 1

# 次の停留所に移動
moved = Driver.move(driver)
Driver.current_stop(moved)   #=> 2

# ルートは循環する
driver
|> Driver.move()
|> Driver.move()
|> Driver.move()
|> Driver.current_stop()     #=> 1（最初に戻る）
```

## 3. ワールド（世界）の操作

### 停留所の集計

```elixir
defmodule World do
  def get_stops(drivers) do
    Enum.reduce(drivers, %{}, fn driver, stops ->
      stop = Driver.current_stop(driver)
      Map.update(stops, stop, [driver], &[driver | &1])
    end)
  end
end
```

### 噂の伝播

```elixir
defmodule World do
  def merge_rumors(drivers) when is_list(drivers) do
    # 全ドライバーの噂を統合
    all_rumors = Enum.reduce(drivers, MapSet.new(), fn driver, acc ->
      MapSet.union(acc, driver.rumors)
    end)

    # 各ドライバーに統合された噂を設定
    Enum.map(drivers, &Driver.set_rumors(&1, all_rumors))
  end

  def spread_rumors(drivers) do
    stops = get_stops(drivers)

    stops
    |> Map.values()
    |> Enum.flat_map(&merge_rumors/1)
  end
end
```

### 1ステップのシミュレーション

```elixir
def drive(drivers) do
  drivers
  |> move_drivers()      # 全員を移動
  |> spread_rumors()     # 噂を伝播
end

def move_drivers(drivers) do
  Enum.map(drivers, &Driver.move/1)
end
```

## 4. シミュレーション実行

### 収束判定

```elixir
def all_rumors_shared?(drivers) do
  rumors_list = Enum.map(drivers, & &1.rumors)

  case rumors_list do
    [] -> true
    [first | rest] -> Enum.all?(rest, &(&1 == first))
  end
end
```

### メインシミュレーション

```elixir
defmodule Simulation do
  @max_steps 480  # 8時間

  def run_until_spread(drivers) do
    run_until_spread(World.drive(drivers), 1)
  end

  defp run_until_spread(_drivers, step) when step > @max_steps, do: :never
  defp run_until_spread(drivers, step) do
    if World.all_rumors_shared?(drivers) do
      step
    else
      run_until_spread(World.drive(drivers), step + 1)
    end
  end
end
```

### 使用例

```elixir
# ドライバーを作成
drivers = [
  Driver.new("D1", [3, 1, 2, 3], [:r1]),
  Driver.new("D2", [3, 2, 3, 1], [:r2]),
  Driver.new("D3", [4, 2, 3, 4, 5], [:r3])
]

# シミュレーション実行
steps = Simulation.run_until_spread(drivers)
#=> 例: 5（5ステップで全噂が共有された）
```

## 5. 問題ソルバー

```elixir
defmodule Solver do
  def solve(routes) when is_list(routes) do
    drivers =
      routes
      |> Enum.with_index(1)
      |> Enum.map(fn {route, idx} ->
        rumor = String.to_atom("rumor_#{idx}")
        Driver.new("Driver_#{idx}", route, [rumor])
      end)

    Simulation.run_until_spread(drivers)
  end

  def parse_and_solve(input) when is_binary(input) do
    routes =
      input
      |> String.trim()
      |> String.split("\n")
      |> Enum.map(fn line ->
        line
        |> String.split()
        |> Enum.map(&String.to_integer/1)
      end)

    solve(routes)
  end
end
```

### 使用例

```elixir
# ルートリストから解く
routes = [[3, 1, 2, 3], [3, 2, 3, 1], [4, 2, 3, 4, 5]]
Solver.solve(routes)

# テキスト入力から解く
input = """
3 1 2 3
3 2 3 1
4 2 3 4 5
"""
Solver.parse_and_solve(input)
```

## 6. 設計のポイント

### 無限ストリームの活用

`Stream.cycle/1` を使用することで、循環ルートを自然に表現できます。

```elixir
route = Stream.cycle([1, 2, 3])
Enum.take(route, 7)  #=> [1, 2, 3, 1, 2, 3, 1]
```

### 不変データ構造

ドライバーの状態は不変で、移動や噂の更新は常に新しい構造体を返します。

```elixir
# 元のドライバーは変更されない
driver = Driver.new("Alice", [1, 2], [:a])
moved = Driver.move(driver)

Driver.current_stop(driver)  #=> 1（元のまま）
Driver.current_stop(moved)   #=> 2（新しい状態）
```

### 集合演算による噂の統合

`MapSet.union/2` を使用して、噂の統合を簡潔に表現できます。

```elixir
rumors1 = MapSet.new([:a, :b])
rumors2 = MapSet.new([:b, :c])
MapSet.union(rumors1, rumors2)  #=> MapSet.new([:a, :b, :c])
```

### 遅延評価

`Stream` を使用することで、無限シーケンスでもメモリ効率良く処理できます。

## 7. サンプル問題

### 出会わないケース

```elixir
drivers = [
  Driver.new("D1", [1], [:r1]),  # 停留所1に固定
  Driver.new("D2", [2], [:r2])   # 停留所2に固定
]

Simulation.run_until_spread(drivers)  #=> :never
```

### すぐに出会うケース

```elixir
drivers = [
  Driver.new("D1", [1, 2], [:r1]),
  Driver.new("D2", [1, 3], [:r2]),
  Driver.new("D3", [1, 4], [:r3])
]

# 全員が最初から停留所1にいるので、1ステップ目で噂が共有される
Simulation.run_until_spread(drivers)  #=> 1
```

## まとめ

この問題は以下の関数型プログラミングの概念を活用しています：

1. **無限ストリーム**: `Stream.cycle` で循環ルートを表現
2. **不変データ構造**: 状態変更は常に新しいデータを返す
3. **集合演算**: `MapSet` で噂の統合を簡潔に実装
4. **再帰とパターンマッチ**: シミュレーションの終了条件を明確に表現
5. **高階関数**: `Enum.map`, `Enum.reduce`, `Enum.filter` でデータ変換

Elixir の強力なストリーム処理と不変データ構造により、シミュレーション問題を宣言的に記述できます。
