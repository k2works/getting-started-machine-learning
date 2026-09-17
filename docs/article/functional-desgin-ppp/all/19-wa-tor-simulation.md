---
type: Article
title: "第19章: Wa-Tor シミュレーション — 6言語統合ガイド"
description: "Wa-Tor は、トーラス型の 2D グリッド上で魚とサメの生態系をシミュレートするセルオートマトンです。"
tags: [article, functional-desgin-ppp, all]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第19章: Wa-Tor シミュレーション — 6言語統合ガイド

## 1. はじめに

Wa-Tor は、トーラス型の 2D グリッド上で魚とサメの生態系をシミュレートするセルオートマトンです。関数型プログラミングの**不変状態管理**、**再帰的データ処理**、**多態的ディスパッチ**を大規模に実践する総合的なケーススタディです。

## 2. 共通の本質

### シミュレーションルール

| 生物 | ルール |
|------|--------|
| **魚** | 隣接する空きセルに移動。一定年齢で繁殖（子を残して移動） |
| **サメ** | 隣接する魚を捕食（エネルギー回復）。魚がいなければ空きセルに移動。エネルギー切れで死亡。一定年齢で繁殖 |
| **水** | 空きセル。魚やサメの移動先 |

### トーラス座標

グリッドの端がつながったトーラス構造：

```
x' = ((x % width) + width) % width
y' = ((y % height) + height) % height
```

### 各ステップの処理

```
1. すべてのセルを走査
2. 各生物のルールを適用（移動・捕食・繁殖・死亡）
3. 新しいワールド状態を生成
```

## 3. 言語別実装比較

### 3.1 グリッドの表現

| 言語 | グリッド型 | セル表現 | 隣接方向 |
|------|----------|---------|---------|
| Clojure | `Map<[x,y], Cell>` | キーワードマップ | 8 方向（Moore） |
| Scala | `Map[Position, Cell]` | sealed trait | 8 方向 |
| Elixir | `Map<{x,y}, Cell>` | マップ / nil（水） | 4 方向（Von Neumann） |
| F# | `Map<Position, Cell>` | 判別共用体 | 4 方向 |
| Haskell | `Map Location Cell` | ADT | 8 方向 |
| Rust | `Vec<Creature>` | struct + enum | 4 方向（スパース） |

<details>
<summary>Clojure: マップベースのグリッド</summary>

```clojure
(defn make-world [width height]
  {:type ::world :width width :height height
   :cells {}})

(defn make-fish []
  {:type ::fish :age 0})

(defn make-shark [health]
  {:type ::shark :age 0 :health health})

;; トーラス座標のラップ
(defn wrap [{:keys [width height]} [x y]]
  [(mod (+ x width) width)
   (mod (+ y height) height)])
```

</details>

<details>
<summary>Scala: sealed trait + case class</summary>

```scala
sealed trait Cell
case object Water extends Cell
case class Fish(age: Int = 0) extends Cell
case class Shark(age: Int = 0, health: Int) extends Cell

case class World(
  width: Int, height: Int,
  cells: Map[Position, Cell],
  generation: Int = 0
)
```

</details>

<details>
<summary>Haskell: ADT + Map</summary>

```haskell
data Cell
    = WaterCell
    | FishCell { fishAge :: Int, fishReproductionAge :: Int }
    | SharkCell { sharkAge :: Int, sharkReproductionAge :: Int, sharkEnergy :: Int }

data World = World
    { worldWidth  :: Int
    , worldHeight :: Int
    , worldCells  :: Map Location Cell
    , worldFishReproAge :: Int
    , worldSharkReproAge :: Int
    , worldSharkInitialEnergy :: Int
    }
```

</details>

<details>
<summary>Rust: スパースな生物リスト</summary>

```rust
pub enum CreatureType { Fish, Shark }

pub struct Creature {
    pub creature_type: CreatureType,
    pub position: Position,
    pub energy: i32,
    pub breed_time: i32,
}

pub struct World {
    pub width: i32,
    pub height: i32,
    pub creatures: Vec<Creature>,
    pub fish_breed_time: i32,
    pub shark_breed_time: i32,
}
```

Rust はグリッド（`Map<Position, Cell>`）ではなく**生物リスト（`Vec<Creature>`）**を使用します。密度が低いワールドでのメモリ効率が高くなります。

</details>

### 3.2 状態更新のアプローチ

| 言語 | 更新方法 | 乱数の扱い |
|------|---------|----------|
| Clojure | `assoc` / `update` で新マップ生成 | `rand-int`（暗黙的） |
| Scala | `copy` + `Map.updated` | `Random`（暗黙的） |
| Elixir | `Map.put` で新マップ生成 | `:rand.uniform`（暗黙的） |
| F# | `Map.add` で新マップ生成 | `System.Random`（明示的参照） |
| Haskell | `Map.insert` で新マップ生成 | `StdGen`（**明示的に引き回し**） |
| Rust | `Vec` の clone + 変更 | `rand::Rng`（明示的） |

<details>
<summary>Haskell: 乱数の明示的な引き回し</summary>

```haskell
tickWorld :: World -> StdGen -> (World, StdGen)
tickWorld world gen =
    foldl' tickLocation (world, gen) allLocations
  where
    allLocations = [(x, y) | x <- [0..worldWidth world - 1]
                           , y <- [0..worldHeight world - 1]]

tickLocation :: (World, StdGen) -> Location -> (World, StdGen)
tickLocation (world, gen) loc =
    case Map.lookup loc (worldCells world) of
        Just (FishCell age _) -> tickFish world loc age gen
        Just (SharkCell age _ energy) -> tickShark world loc age energy gen
        _ -> (world, gen)
```

Haskell は純粋関数型言語のため、乱数生成器 `StdGen` を**引数と戻り値で明示的に受け渡し**します。これにより参照透過性が保たれ、同じシードで同じ結果を再現できます。

</details>

### 3.3 魚のティック処理

<details>
<summary>魚の移動・繁殖ロジック比較</summary>

```clojure
;; Clojure
(defmethod tick ::fish [world loc]
  (let [cell (get-cell world loc)
        neighbors (empty-neighbors world loc)
        can-reproduce? (>= (:age cell) fish-reproduction-age)]
    (if (seq neighbors)
      (let [target (rand-nth neighbors)]
        (if can-reproduce?
          (-> world
              (set-cell target (assoc cell :age 0))
              (set-cell loc (make-fish)))
          (-> world
              (set-cell target (update cell :age inc))
              (set-cell loc (make-water)))))
      (update-in world [:cells loc :age] inc))))
```

```scala
// Scala
def tickFish(world: World, pos: Position, fish: Fish): World =
  val emptyNeighbors = getEmptyNeighbors(world, pos)
  if emptyNeighbors.nonEmpty then
    val target = emptyNeighbors(Random.nextInt(emptyNeighbors.size))
    val aged = fish.copy(age = fish.age + 1)
    if aged.age >= world.fishBreedTime then
      world.updated(target, Fish()).updated(pos, Fish())
    else
      world.updated(target, aged).updated(pos, Water)
  else
    world.updated(pos, fish.copy(age = fish.age + 1))
```

```haskell
-- Haskell
tickFish :: World -> Location -> Int -> StdGen -> (World, StdGen)
tickFish world loc age gen =
    let emptyNeighbors = getEmptyNeighbors world loc
    in if null emptyNeighbors
       then (updateCell world loc (FishCell (age + 1) reproAge), gen)
       else let (idx, gen') = randomR (0, length emptyNeighbors - 1) gen
                target = emptyNeighbors !! idx
            in if age >= reproAge
               then (reproducefish world loc target, gen')
               else (moveFish world loc target age, gen')
```

</details>

### 3.4 可視化

| 言語 | 可視化方法 |
|------|----------|
| Clojure | Quil（Processing ラッパー）で GUI |
| Scala | ASCII + DSL |
| Elixir | ASCII ターミナル出力 |
| F# | ASCII + 統計表示 |
| Haskell | ASCII + 統計表示 |
| Rust | ASCII ターミナル出力 |

## 4. 比較分析

### 4.1 設計アプローチの違い

| アプローチ | 言語 | 利点 |
|-----------|------|------|
| グリッドベース（密） | Clojure, Scala, F#, Haskell | 隣接セルの参照が O(1) |
| 生物リスト（疎） | Rust | メモリ効率が高い（低密度時） |
| nil = 水 | Elixir | マップのサイズが小さい |
| 明示的 WaterCell | Clojure, Haskell | 水セルも明示的に管理 |

### 4.2 純粋性と乱数

| 言語 | 乱数の純粋性 | テストの再現性 |
|------|-----------|-------------|
| Haskell | 完全に純粋（StdGen を引き回し） | シードで完全再現 |
| Rust | 明示的だが副作用あり | シードで再現可能 |
| Clojure, Scala, Elixir, F# | 暗黙的な副作用 | シード設定で再現可能 |

Haskell のアプローチは最も厳密ですが、コードの複雑さが増します。他の言語は実用的なトレードオフとして暗黙的な乱数を使用しています。

### 4.3 パフォーマンス特性

| 言語 | メモリ管理 | 大規模ワールドでの効率 |
|------|----------|-------------------|
| Clojure | 永続データ構造（構造共有） | 中程度 |
| Scala | 不変 Map + copy | 中程度 |
| Elixir | 不変 Map | 中程度 |
| F# | 不変 Map | 中程度 |
| Haskell | 不変 Map + 遅延評価 | 遅延により効率化の可能性 |
| Rust | Vec + clone | 高い（スパース表現） |

## 5. 実践的な選択指針

| 要件 | 推奨言語 | 理由 |
|------|---------|------|
| GUI 可視化 | Clojure | Quil による豊富なグラフィックス |
| テストの完全再現性 | Haskell | 純粋な乱数管理 |
| 大規模シミュレーション | Rust | スパース表現 + ゼロコスト抽象化 |
| 分散シミュレーション | Elixir | プロセス分散が容易 |
| DSL による構築 | Scala | ビルダーパターンの表現力 |

## 6. まとめ

Wa-Tor シミュレーションは、関数型プログラミングの総合力を試すケーススタディです：

1. **不変状態管理**: 各ステップで新しいワールド状態を生成
2. **多態的ディスパッチ**: 魚・サメ・水を統一的に処理
3. **乱数と純粋性**: Haskell の明示的アプローチ vs 他言語の実用的トレードオフ
4. **データ表現の選択**: グリッド vs スパースリスト

## 言語別個別記事

- [Clojure](../clojure/19-wator-simulation.md) | [Scala](../scala/19-wator-simulation.md) | [Elixir](../elixir/19-wator-simulation.md) | [F#](../fsharp/19-wator-simulation.md) | [Haskell](../haskell/19-wator-simulation.md) | [Rust](../rust/19-wator-simulation.md)
