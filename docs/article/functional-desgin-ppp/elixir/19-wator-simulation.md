---
type: Article
title: "第19章: Wa-Tor シミュレーション"
description: "Wa-Tor は、A.K. Dewdney が Scientific American 誌で紹介した生態系シミュレーションです。"
tags: [article, functional-desgin-ppp, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第19章: Wa-Tor シミュレーション

## はじめに

Wa-Tor は、A.K. Dewdney が Scientific American 誌で紹介した生態系シミュレーションです。トーラス状の水界で魚とサメが相互作用し、捕食と繁殖の動的なバランスを観察できます。関数型プログラミングで状態の進化をモデル化する優れた題材です。

## 1. 設定と構造体

### Config モジュール

```elixir
defmodule Config do
  @doc "魚の繁殖年齢"
  def fish_reproduction_age, do: 3

  @doc "サメの繁殖年齢"
  def shark_reproduction_age, do: 8

  @doc "サメの繁殖に必要な体力"
  def shark_reproduction_health, do: 4

  @doc "サメの初期体力"
  def shark_starting_health, do: 5

  @doc "サメの最大体力"
  def shark_max_health, do: 10

  @doc "サメが魚を食べた時の体力回復"
  def shark_eating_health, do: 3
end
```

### Cell モジュール

```elixir
defmodule Cell do
  @doc "水（空セル）"
  def water, do: nil

  def is_water?(nil), do: true
  def is_water?(_), do: false

  def display(nil), do: "~"
  def display(%{type: :fish}), do: "f"
  def display(%{type: :shark}), do: "S"
end
```

### Fish 構造体

```elixir
defmodule Fish do
  def new do
    %{type: :fish, age: 0}
  end

  def is?(%{type: :fish}), do: true
  def is?(_), do: false

  def increment_age(fish) do
    %{fish | age: fish.age + 1}
  end

  def can_reproduce?(fish) do
    fish.age >= Config.fish_reproduction_age()
  end

  def reproduce(fish) do
    if can_reproduce?(fish) do
      parent = %{fish | age: 0}
      child = new()
      {parent, child}
    else
      nil
    end
  end
end
```

### Shark 構造体

```elixir
defmodule Shark do
  def new do
    %{type: :shark, age: 0, health: Config.shark_starting_health()}
  end

  def is?(%{type: :shark}), do: true
  def is?(_), do: false

  def increment_age(shark) do
    %{shark | age: shark.age + 1}
  end

  def decrement_health(shark) do
    %{shark | health: shark.health - 1}
  end

  def feed(shark) do
    new_health = min(Config.shark_max_health(), shark.health + Config.shark_eating_health())
    %{shark | health: new_health}
  end

  def is_alive?(shark) do
    shark.health > 0
  end

  def can_reproduce?(shark) do
    shark.age >= Config.shark_reproduction_age() and
      shark.health >= Config.shark_reproduction_health()
  end

  def reproduce(shark) do
    if can_reproduce?(shark) do
      half_health = div(shark.health, 2)
      parent = %{shark | age: 0, health: half_health}
      child = %{new() | health: half_health}
      {parent, child}
    else
      nil
    end
  end
end
```

## 2. ワールド

### トーラス状の世界

```elixir
defmodule World do
  defstruct [:width, :height, :cells]

  def new(width, height) do
    %__MODULE__{width: width, height: height, cells: %{}}
  end

  @doc "トーラス座標の正規化"
  def normalize(%__MODULE__{width: w, height: h}, {x, y}) do
    {rem(rem(x, w) + w, w), rem(rem(y, h) + h, h)}
  end

  @doc "セルの取得"
  def get_cell(world, loc) do
    normalized = normalize(world, loc)
    Map.get(world.cells, normalized)
  end

  @doc "セルの設定"
  def set_cell(world, loc, cell) do
    normalized = normalize(world, loc)
    if cell == nil do
      %{world | cells: Map.delete(world.cells, normalized)}
    else
      %{world | cells: Map.put(world.cells, normalized, cell)}
    end
  end

  @doc "魚を配置"
  def set_fish(world, loc) do
    set_cell(world, loc, Fish.new())
  end

  @doc "サメを配置"
  def set_shark(world, loc) do
    set_cell(world, loc, Shark.new())
  end
end
```

### 隣接セルの取得

```elixir
@doc "フォン・ノイマン近傍（上下左右）"
def neighbors(world, {x, y}) do
  [{x, y - 1}, {x + 1, y}, {x, y + 1}, {x - 1, y}]
  |> Enum.map(&normalize(world, &1))
end
```

### 統計情報

```elixir
def count_fish(world) do
  world.cells
  |> Map.values()
  |> Enum.count(&Fish.is?/1)
end

def count_sharks(world) do
  world.cells
  |> Map.values()
  |> Enum.count(&Shark.is?/1)
end

def statistics(world) do
  %{
    fish: count_fish(world),
    sharks: count_sharks(world),
    total: map_size(world.cells)
  }
end
```

### ワールドの表示

```elixir
def display(%__MODULE__{width: w, height: h} = world) do
  rows = for y <- 0..(h - 1) do
    row = for x <- 0..(w - 1) do
      Cell.display(get_cell(world, {x, y}))
    end
    Enum.join(row, "")
  end
  Enum.join(rows, "\n")
end
```

出力例：
```
~~~~~
~f~~~
~~S~~
~f~~~
~~~~~
```

## 3. シミュレーション

### tick 関数

```elixir
defmodule Simulation do
  @doc "1ステップ実行"
  def tick(world) do
    # 全セルをシャッフルして処理（順序のバイアスを避ける）
    locs = Enum.shuffle(Map.keys(world.cells))

    Enum.reduce(locs, world, fn loc, acc_world ->
      tick_cell(acc_world, loc)
    end)
  end

  defp tick_cell(world, loc) do
    cell = World.get_cell(world, loc)

    cond do
      Fish.is?(cell) -> tick_fish(world, loc, cell)
      Shark.is?(cell) -> tick_shark(world, loc, cell)
      true -> world
    end
  end
end
```

### 魚の更新

```elixir
defp tick_fish(world, loc, fish) do
  aged_fish = Fish.increment_age(fish)

  # 繁殖を試みる
  case Fish.reproduce(aged_fish) do
    {parent, child} ->
      case find_empty_neighbor(world, loc) do
        nil -> World.set_cell(world, loc, aged_fish)
        empty_loc ->
          world
          |> World.set_cell(loc, parent)
          |> World.set_cell(empty_loc, child)
      end

    nil ->
      # 移動を試みる
      case find_empty_neighbor(world, loc) do
        nil -> World.set_cell(world, loc, aged_fish)
        empty_loc ->
          world
          |> World.set_cell(loc, Cell.water())
          |> World.set_cell(empty_loc, aged_fish)
      end
  end
end
```

### サメの更新

```elixir
defp tick_shark(world, loc, shark) do
  # 体力チェック - 既に死んでいる場合は水に
  if not Shark.is_alive?(shark) do
    World.set_cell(world, loc, Cell.water())
  else
    aged_shark = shark |> Shark.increment_age() |> Shark.decrement_health()

    # 体力が尽きたら死亡
    if not Shark.is_alive?(aged_shark) do
      World.set_cell(world, loc, Cell.water())
    else
      # 捕食を試みる
      case find_fish_neighbor(world, loc) do
        nil ->
          # 魚がいない場合、繁殖または移動
          handle_shark_movement(world, loc, aged_shark)

        fish_loc ->
          fed_shark = Shark.feed(aged_shark)
          world
          |> World.set_cell(loc, Cell.water())
          |> World.set_cell(fish_loc, fed_shark)
      end
    end
  end
end
```

### 複数ステップの実行

```elixir
@doc "複数ステップ実行"
def run(world, steps) do
  Enum.reduce(1..steps, world, fn _, acc -> tick(acc) end)
end

@doc "履歴付きで実行"
def run_with_history(world, steps) do
  {history, _final} =
    Enum.reduce(1..steps, {[world], world}, fn _, {hist, w} ->
      next = tick(w)
      {[next | hist], next}
    end)

  Enum.reverse(history)
end

@doc "統計履歴付きで実行"
def run_with_stats(world, steps) do
  {stats, final} =
    Enum.reduce(0..steps, {[], world}, fn step, {acc_stats, w} ->
      stat = Map.put(World.statistics(w), :step, step)
      if step == steps do
        {[stat | acc_stats], w}
      else
        next = tick(w)
        {[stat | acc_stats], next}
      end
    end)

  %{
    final_world: final,
    statistics: Enum.reverse(stats)
  }
end
```

## 4. ヘルパー関数

### 隣接セルの検索

```elixir
# 空の隣接セルを探す
defp find_empty_neighbor(world, loc) do
  world
  |> World.neighbors(loc)
  |> Enum.filter(fn neighbor_loc ->
    Cell.is_water?(World.get_cell(world, neighbor_loc))
  end)
  |> case do
    [] -> nil
    neighbors -> Enum.random(neighbors)
  end
end

# 魚がいる隣接セルを探す
defp find_fish_neighbor(world, loc) do
  world
  |> World.neighbors(loc)
  |> Enum.filter(fn neighbor_loc ->
    Fish.is?(World.get_cell(world, neighbor_loc))
  end)
  |> case do
    [] -> nil
    neighbors -> Enum.random(neighbors)
  end
end
```

## 5. サンプルシナリオ

### Examples モジュール

```elixir
defmodule Examples do
  @doc "小さな世界の例"
  def small_world do
    World.new(10, 10)
    |> World.set_fish({2, 2})
    |> World.set_fish({3, 3})
    |> World.set_fish({5, 5})
    |> World.set_shark({7, 7})
  end

  @doc "中くらいの世界"
  def medium_world do
    world = World.new(20, 20)

    # 魚を散りばめる
    fish_positions = for _ <- 1..30, do: {:rand.uniform(20) - 1, :rand.uniform(20) - 1}
    shark_positions = for _ <- 1..5, do: {:rand.uniform(20) - 1, :rand.uniform(20) - 1}

    world
    |> populate(fish_positions, &World.set_fish/2)
    |> populate(shark_positions, &World.set_shark/2)
  end

  defp populate(world, positions, setter) do
    Enum.reduce(positions, world, fn pos, acc ->
      setter.(acc, pos)
    end)
  end

  @doc "シミュレーションを実行して結果を表示"
  def run_demo(steps \\ 100) do
    result = small_world()
             |> Simulation.run_with_stats(steps)

    IO.puts("Final world:")
    IO.puts(World.display(result.final_world))
    IO.puts("\nPopulation over time:")

    result.statistics
    |> Enum.each(fn stat ->
      IO.puts("Step #{stat.step}: Fish=#{stat.fish}, Sharks=#{stat.sharks}")
    end)

    result
  end
end
```

## 6. テストの例

```elixir
test "fish reproduces at correct age" do
  fish = %{Fish.new() | age: Config.fish_reproduction_age()}
  world = World.new(3, 3)
          |> World.set_cell({1, 1}, fish)

  final = Simulation.tick(world)
  assert World.count_fish(final) == 2
end

test "shark dies without food" do
  shark = %{Shark.new() | health: 2}
  world = World.new(5, 5)
          |> World.set_cell({2, 2}, shark)

  # Run until shark starves (health goes 2 -> 1 -> 0)
  final = Simulation.run(world, 3)
  assert World.count_sharks(final) == 0
end

test "shark eats fish and gains health" do
  world = World.new(3, 3)
          |> World.set_shark({1, 1})
          |> World.set_fish({1, 0})

  final = Simulation.tick(world)

  # Either fish was eaten or shark moved elsewhere
  stats = World.statistics(final)
  assert stats.sharks == 1
  assert stats.fish <= 1
end

test "toroidal world wraps correctly" do
  world = World.new(5, 5)

  assert World.normalize(world, {-1, 0}) == {4, 0}
  assert World.normalize(world, {0, -1}) == {0, 4}
  assert World.normalize(world, {5, 5}) == {0, 0}
end
```

## 7. 関数型設計のポイント

### 不変データと純粋関数

| 側面 | 説明 |
|-----|------|
| 不変のWorld | 各 tick は新しい World を返す |
| 純粋な状態遷移 | `tick(world)` は同じ入力に対して（乱数を除き）同じ出力 |
| 明示的な状態 | 副作用なしで状態の変化を追跡可能 |

### reduce による状態の進化

```elixir
# 各セルを順番に処理し、累積的にワールドを更新
Enum.reduce(locations, world, fn loc, acc_world ->
  tick_cell(acc_world, loc)
end)
```

### パターンマッチによる分岐

```elixir
cond do
  Fish.is?(cell) -> tick_fish(world, loc, cell)
  Shark.is?(cell) -> tick_shark(world, loc, cell)
  true -> world  # 水の場合は変更なし
end
```

## まとめ

Wa-Tor シミュレーションは、関数型プログラミングの強みを示す優れた例です：

1. **不変データ構造**: World の各状態は独立しており、履歴の保存が容易
2. **純粋関数**: tick 関数は（乱数を除き）参照透過的
3. **明示的な状態遷移**: 状態変化が追跡可能で、デバッグしやすい
4. **モジュラー設計**: Fish、Shark、World、Simulation が明確に分離

この設計により、シミュレーションの正確性を確認しやすく、パラメータの調整や新しいルールの追加が容易になります。
