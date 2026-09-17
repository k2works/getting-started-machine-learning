---
type: Article
title: "第15章: ゴシップ好きなバスの運転手"
description: "本章では、「ゴシップ好きなバスの運転手」という問題を通じて、関数型プログラミングの実践的なアプローチを学びます。"
tags: [article, functional-desgin-ppp, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-27T04:43:33Z }
---

# 第15章: ゴシップ好きなバスの運転手

## はじめに

本章では、「ゴシップ好きなバスの運転手」という問題を通じて、関数型プログラミングの実践的なアプローチを学びます。この問題は、バス運転手が停留所で出会ったときに噂を共有するというシミュレーションです。

この問題を通じて以下の概念を学びます：

- 無限シーケンス（`cycle`）による循環データの表現
- 集合演算による状態の伝播
- データ変換パイプラインの設計

## 1. 問題の説明

複数のバス運転手がそれぞれのルートを巡回しています。各運転手は最初に1つの噂（自分のID）を知っています。同じ停留所に複数の運転手がいると、彼らは知っている噂をすべて共有します。

**ゴール**: 全ての運転手が全ての噂を知るまでに何分かかるか？

## 2. データモデル

### ドライバーの表現

```haskell
-- | A stop identifier
type Stop = Int

-- | A gossip piece (unique per driver)
type Gossip = Int

-- | A bus driver with their route and known gossip
data Driver = Driver
  { driverId :: Int
  , driverRoute :: [Stop]     -- ^ Infinite cyclic route
  , driverGossip :: Set Gossip
  } deriving (Show, Eq)

-- | The world state is a list of drivers
type World = [Driver]
```

### ドライバーの作成

```haskell
-- | Create a driver with a route (cycles infinitely) and initial gossip
makeDriver :: Int -> [Stop] -> Driver
makeDriver dId route = Driver
  { driverId = dId
  , driverRoute = cycle route
  , driverGossip = Set.singleton dId
  }

-- | Create multiple drivers from routes
makeDrivers :: [[Stop]] -> [Driver]
makeDrivers routes = zipWith makeDriver [0..] routes
```

ポイント:


- `cycle` 関数で有限のルートを無限の循環シーケンスに変換
- 噂は集合（Set）として管理
- 各ドライバーは自分のIDを初期の噂として持つ

## 3. 移動と噂の伝播

### ドライバーの移動

```haskell
-- | Get the current stop of a driver
currentStop :: Driver -> Stop
currentStop driver = head (driverRoute driver)

-- | Move a driver to their next stop
moveDriver :: Driver -> Driver
moveDriver driver = driver { driverRoute = tail (driverRoute driver) }

-- | Move all drivers to their next stops
moveDrivers :: World -> World
moveDrivers = map moveDriver
```

### 停留所ごとのドライバー集計

```haskell
-- | Group drivers by their current stop
driversAtStops :: World -> Map Stop [Driver]
driversAtStops = foldr addDriver Map.empty
  where
    addDriver driver acc = 
      let stop = currentStop driver
      in Map.insertWith (++) stop [driver] acc
```

### 噂の共有

```haskell
-- | Merge gossip among a group of drivers
mergeGossip :: [Driver] -> [Driver]
mergeGossip drivers =
  let allGossips = Set.unions (map driverGossip drivers)
  in map (\d -> d { driverGossip = allGossips }) drivers

-- | Spread gossip at all stops
spreadGossip :: World -> World
spreadGossip world =
  let byStop = driversAtStops world
      merged = Map.map mergeGossip byStop
  in concatMap snd (Map.toList merged)
```

## 4. シミュレーション

### 1ステップの処理

```haskell
-- | One step of simulation: move then spread gossip
drive :: World -> World
drive = spreadGossip . moveDrivers
```

### 完了判定

```haskell
-- | Check if all drivers know all gossip
allGossipShared :: World -> Bool
allGossipShared [] = True
allGossipShared (d:ds) = all (\driver -> driverGossip driver == driverGossip d) ds

-- | Get the total set of all gossip in the world
totalGossip :: World -> Set Gossip
totalGossip = Set.unions . map driverGossip
```

## 5. ソルバー

```haskell
-- | Solve the problem: find minutes until all gossip is shared
-- Returns Nothing if it takes more than 480 minutes (8 hours)
solve :: [[Stop]] -> Maybe Int
solve = solveWithLimit 480

-- | Solve with a custom time limit
solveWithLimit :: Int -> [[Stop]] -> Maybe Int
solveWithLimit limit routes = go 0 (makeDrivers routes)
  where
    go minutes world
      | minutes > limit = Nothing
      | allGossipShared world = Just minutes
      | otherwise = go (minutes + 1) (drive world)
```

## 6. テスト

```haskell
describe "Solver" $ do
  it "solves case requiring multiple steps" $ do
    -- d0: 1 -> 2 -> 3
    -- d1: 3 -> 2 -> 1
    -- Initial: d0 at 1, d1 at 3
    -- After 1: d0 at 2, d1 at 2 - they meet!
    let routes = [[1, 2, 3], [3, 2, 1]]
    solve routes `shouldBe` Just 1
  
  it "returns Nothing when drivers never meet" $ do
    let routes = [[1, 2], [3, 4]]
    solve routes `shouldBe` Nothing
```

## まとめ

ゴシップ好きなバスの運転手問題の Haskell 実装のポイント：

1. **無限リスト**: `cycle` で有限ルートを無限化
2. **不変データ**: 各ステップで新しい World を生成
3. **集合演算**: `Set.unions` で噂を効率的にマージ
4. **パイプライン**: `drive = spreadGossip . moveDrivers` で処理を合成
5. **終了条件**: 全ドライバーの噂が等しければ完了
