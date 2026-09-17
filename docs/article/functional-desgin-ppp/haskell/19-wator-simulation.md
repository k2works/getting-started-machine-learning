---
type: Article
title: "第19章 Wa-Tor シミュレーション - セルオートマトン"
description: "この章では、Wa-Tor（Water Torus）と呼ばれる捕食者-被食者シミュレーションを実装します。"
tags: [article, functional-desgin-ppp, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第19章 Wa-Tor シミュレーション - セルオートマトン

## 概要

この章では、Wa-Tor（Water Torus）と呼ばれる捕食者-被食者シミュレーションを実装します。魚（被食者）とサメ（捕食者）がトーラス状の世界で相互作用する生態系をモデル化します。

## 学習目標

- セルオートマトンの関数型実装
- 不変データ構造による状態管理
- トーラス位相の座標系
- 乱数を使用したシミュレーション

## セルの定義

### セルの種類

```haskell
-- | セルタイプの識別子
data CellType = Water | Fish | Shark
  deriving (Show, Eq, Ord)

-- | セル
data Cell
  = WaterCell                           -- 水
  | FishCell
    { fishAge :: Int                    -- 年齢
    , fishReproductionAge :: Int        -- 繁殖可能年齢
    }
  | SharkCell
    { sharkAge :: Int                   -- 年齢
    , sharkReproductionAge :: Int       -- 繁殖可能年齢
    , sharkEnergy :: Int                -- エネルギー
    }
  deriving (Show, Eq)
```

### セルの識別関数

```haskell
-- | 水かどうか
isWater :: Cell -> Bool
isWater WaterCell = True
isWater _ = False

-- | 魚かどうか
isFish :: Cell -> Bool
isFish (FishCell _ _) = True
isFish _ = False

-- | サメかどうか
isShark :: Cell -> Bool
isShark (SharkCell _ _ _) = True
isShark _ = False
```

### セルの作成

```haskell
-- | 水セルを作成
makeWater :: Cell
makeWater = WaterCell

-- | 魚セルを作成
makeFish :: Int -> Cell
makeFish reproAge = FishCell 0 reproAge

-- | サメセルを作成
makeShark :: Int -> Int -> Cell
makeShark reproAge energy = SharkCell 0 reproAge energy
```

## 世界の構築

### 世界の定義

```haskell
-- | 位置
type Location = (Int, Int)

-- | 世界
data World = World
  { worldWidth :: Int                   -- 幅
  , worldHeight :: Int                  -- 高さ
  , worldCells :: Map Location Cell     -- セルのマップ
  , worldFishReproAge :: Int            -- 魚の繁殖年齢
  , worldSharkReproAge :: Int           -- サメの繁殖年齢
  , worldSharkInitialEnergy :: Int      -- サメの初期エネルギー
  , worldSharkEnergyFromFish :: Int     -- 魚を食べた時のエネルギー獲得量
  } deriving (Show, Eq)
```

### 世界の作成

```haskell
-- | 空の世界を作成（全て水）
makeWorld :: Width -> Height -> Int -> Int -> Int -> Int -> World
makeWorld w h fishRepro sharkRepro sharkEnergy sharkEnergyGain = World
  { worldWidth = w
  , worldHeight = h
  , worldCells = Map.fromList [((x, y), WaterCell) | x <- [0..w-1], y <- [0..h-1]]
  , worldFishReproAge = fishRepro
  , worldSharkReproAge = sharkRepro
  , worldSharkInitialEnergy = sharkEnergy
  , worldSharkEnergyFromFish = sharkEnergyGain
  }

-- | 特定のセルを配置して世界を作成
makeWorldWithCells :: Width -> Height -> Int -> Int -> Int -> Int 
                   -> [(Location, Cell)] -> World
makeWorldWithCells w h fishRepro sharkRepro sharkEnergy sharkEnergyGain cells =
  let baseWorld = makeWorld w h fishRepro sharkRepro sharkEnergy sharkEnergyGain
  in baseWorld { worldCells = Map.union (Map.fromList cells) (worldCells baseWorld) }
```

## トーラス位相

### 座標のラップ

```haskell
-- | トーラス位相のための座標ラップ
wrapCoord :: World -> Location -> Location
wrapCoord world (x, y) = (x `mod` worldWidth world, y `mod` worldHeight world)
```

この関数により、世界の端を超えた座標は反対側にラップされます：

```
x = -1 → x = width - 1
y = height → y = 0
```

### 近傍の取得

```haskell
-- | 近傍オフセット（8方向）
neighborOffsets :: [Location]
neighborOffsets = 
  [ (-1, -1), (0, -1), (1, -1)
  , (-1, 0),          (1, 0)
  , (-1, 1),  (0, 1),  (1, 1)
  ]

-- | 近傍位置を取得
neighbors :: World -> Location -> [Location]
neighbors world (x, y) = 
  map (\(dx, dy) -> wrapCoord world (x + dx, y + dy)) neighborOffsets

-- | 特定タイプの近傍を取得
neighborsOf :: World -> Location -> (Cell -> Bool) -> [Location]
neighborsOf world loc predicate =
  filter (\l -> predicate (getCell world l)) (neighbors world loc)
```

## シミュレーションルール

### ティック結果

```haskell
-- | セルのティック結果
data TickResult
  = Stay Cell                    -- その場に留まる
  | Move Location Cell Cell      -- 移動（移動先, 新セル, 残すセル）
  | Die                          -- 死亡
  deriving (Show, Eq)
```

### 魚のルール

1. 空の近傍セルがあれば移動
2. 繁殖年齢に達したら繁殖（元の位置に子を残す）
3. 移動できなければその場に留まる

### サメのルール

1. エネルギーが0以下になると死亡
2. 隣接する魚がいれば食べて移動（エネルギー獲得）
3. 魚がいなければ空のセルに移動
4. 繁殖年齢に達したら繁殖
5. 移動できなければその場に留まる

### セルのティック処理

```haskell
-- | 単一セルをティック
tickCell :: World -> Location -> StdGen -> (TickResult, StdGen)
tickCell world loc gen = case getCell world loc of
  WaterCell -> (Stay WaterCell, gen)
  
  FishCell age reproAge ->
    let emptyNeighbors = neighborsOf world loc isWater
        newAge = age + 1
        canReproduce = newAge >= reproAge
    in if null emptyNeighbors
       then (Stay (FishCell newAge reproAge), gen)
       else
         let (idx, gen') = randomR (0, length emptyNeighbors - 1) gen
             moveTo = emptyNeighbors !! idx
         in if canReproduce
            then (Move moveTo (FishCell 0 reproAge) (FishCell 0 reproAge), gen')
            else (Move moveTo (FishCell newAge reproAge) WaterCell, gen')
  
  SharkCell age reproAge energy ->
    let newEnergy = energy - 1
    in if newEnergy <= 0
       then (Die, gen)
       else
         let fishNeighbors = neighborsOf world loc isFish
             emptyNeighbors = neighborsOf world loc isWater
             newAge = age + 1
             canReproduce = newAge >= reproAge
         in if not (null fishNeighbors)
            then -- 魚を食べる
              let (idx, gen') = randomR (0, length fishNeighbors - 1) gen
                  moveTo = fishNeighbors !! idx
                  gainedEnergy = worldSharkEnergyFromFish world
              in if canReproduce
                 then (Move moveTo (SharkCell 0 reproAge (newEnergy + gainedEnergy)) 
                                  (SharkCell 0 reproAge (worldSharkInitialEnergy world)), gen')
                 else (Move moveTo (SharkCell newAge reproAge (newEnergy + gainedEnergy)) WaterCell, gen')
            else if not (null emptyNeighbors)
            then -- 空のセルに移動
              let (idx, gen') = randomR (0, length emptyNeighbors - 1) gen
                  moveTo = emptyNeighbors !! idx
              in if canReproduce
                 then (Move moveTo (SharkCell 0 reproAge newEnergy) 
                                  (SharkCell 0 reproAge (worldSharkInitialEnergy world)), gen')
                 else (Move moveTo (SharkCell newAge reproAge newEnergy) WaterCell, gen')
            else (Stay (SharkCell newAge reproAge newEnergy), gen)
```

## 世界の更新

### ティック結果の適用

```haskell
-- | ティック結果を世界に適用
applyTickResult :: World -> Location -> TickResult -> World
applyTickResult world loc result = case result of
  Stay cell -> setCell world loc cell
  Move newLoc newCell leftBehind ->
    setCell (setCell world loc leftBehind) newLoc newCell
  Die -> setCell world loc WaterCell
```

### 世界全体のティック

```haskell
-- | 世界全体をティック
tickWorld :: World -> StdGen -> (World, StdGen)
tickWorld world gen =
  let allLocs = [(x, y) | x <- [0..worldWidth world - 1], y <- [0..worldHeight world - 1]]
  in foldl' tickLoc (world, gen) allLocs
  where
    tickLoc (w, g) loc =
      let (result, g') = tickCell w loc g
      in (applyTickResult w loc result, g')
```

### シミュレーションの実行

```haskell
-- | n ステップのシミュレーションを実行
runSimulation :: Int -> World -> StdGen -> [(World, WorldStats)]
runSimulation 0 _ _ = []
runSimulation n world gen =
  let (world', gen') = tickWorld world gen
      stats = getStats world'
  in (world', stats) : runSimulation (n - 1) world' gen'
```

## 表示と統計

### セルの表示

```haskell
-- | セルの表示文字
displayCell :: Cell -> Char
displayCell WaterCell = '.'
displayCell (FishCell _ _) = 'f'
displayCell (SharkCell _ _ _) = 'S'

-- | 世界の表示
displayWorld :: World -> String
displayWorld world = unlines
  [ [displayCell (getCell world (x, y)) | x <- [0..worldWidth world - 1]]
  | y <- [0..worldHeight world - 1]
  ]
```

### 統計情報

```haskell
-- | 世界の統計
data WorldStats = WorldStats
  { statsWater :: Int
  , statsFish :: Int
  , statsSharks :: Int
  } deriving (Show, Eq)

-- | 統計を取得
getStats :: World -> WorldStats
getStats world =
  let cells = Map.elems (worldCells world)
  in WorldStats
    { statsWater = length (filter isWater cells)
    , statsFish = length (filter isFish cells)
    , statsSharks = length (filter isShark cells)
    }
```

## 使用例

### 基本的なシミュレーション

```haskell
import System.Random (mkStdGen)

main :: IO ()
main = do
  let cells = [ ((10, 10), makeFish 3)
              , ((15, 15), makeFish 3)
              , ((20, 20), makeShark 5 10)
              ]
      world = makeWorldWithCells 30 30 3 5 10 3 cells
      gen = mkStdGen 42
      results = runSimulation 100 world gen
  
  mapM_ (\(w, stats) -> do
    putStrLn $ displayWorld w
    print stats
    putStrLn "---"
    ) results
```

### 生態系のダイナミクス

```haskell
-- 生態系シミュレーション
ecosystem :: IO ()
ecosystem = do
  -- 魚をたくさん、サメを少数配置
  let fishLocs = [(x, y) | x <- [5,10..45], y <- [5,10..45]]
      sharkLocs = [(25, 25), (10, 40)]
      cells = [(loc, makeFish 3) | loc <- fishLocs] ++
              [(loc, makeShark 5 10) | loc <- sharkLocs]
      world = makeWorldWithCells 50 50 3 5 10 3 cells
      gen = mkStdGen 12345
      results = runSimulation 200 world gen
  
  -- 統計の変化を観察
  let statsHistory = map snd results
  mapM_ print statsHistory
```

## テスト

```haskell
spec :: Spec
spec = do
  describe "Cell Types" $ do
    it "identifies water cells" $ do
      isWater makeWater `shouldBe` True
      isWater (makeFish 3) `shouldBe` False

  describe "Coordinate Operations" $ do
    it "wraps coordinates correctly" $ do
      let world = makeWorld 5 5 3 5 10 3
      wrapCoord world (5, 2) `shouldBe` (0, 2)
      wrapCoord world (-1, 2) `shouldBe` (4, 2)

  describe "Cell Ticking" $ do
    it "fish moves to empty neighbor" $ do
      let cells = [((2, 2), makeFish 3)]
          world = makeWorldWithCells 5 5 3 5 10 3 cells
          gen = mkStdGen 42
          (result, _) = tickCell world (2, 2) gen
      case result of
        Move loc cell _ -> do
          isFish cell `shouldBe` True
          loc `elem` neighbors world (2, 2) `shouldBe` True
        _ -> expectationFailure "Expected Move"

    it "shark dies when energy reaches 0" $ do
      let shark = SharkCell 0 5 1
          cells = [((2, 2), shark)]
          world = makeWorldWithCells 5 5 3 5 10 3 cells
          gen = mkStdGen 42
          (result, _) = tickCell world (2, 2) gen
      result `shouldBe` Die

  describe "World Ticking" $ do
    it "handles fish reproduction" $ do
      let fish = FishCell 2 3  -- Will reproduce at age 3
          cells = [((2, 2), fish)]
          world = makeWorldWithCells 5 5 3 5 10 3 cells
          gen = mkStdGen 42
          (world', _) = tickWorld world gen
          stats = getStats world'
      statsFish stats `shouldBe` 2
```

## 設計のポイント

### 不変データ構造

- `World` は不変で、更新時に新しい `World` を返す
- `Map` を使用した効率的なセル管理

### 純粋関数と副作用の分離

- シミュレーションロジックは純粋（乱数生成器を引数で受け取る）
- IO は表示と初期化のみ

### トーラス位相

- 端を超えた座標が反対側にラップ
- 有限サイズで無限の世界をシミュレート

### 代数的データ型

- `Cell` の異なる種類を安全に表現
- `TickResult` で遷移の結果を明示的に表現

## 拡張のアイデア

1. **環境要因**: 水温や季節の影響を追加
2. **複数種**: より多くの生物種を追加
3. **可視化**: グラフィカルな表示
4. **並列処理**: 大きな世界での並列更新
5. **統計分析**: 個体数の時系列分析

## まとめ

- セルオートマトンは関数型プログラミングで自然に表現できる
- 不変データ構造により、状態の変化を追跡しやすい
- 代数的データ型でセルの種類を安全に表現
- 純粋関数による乱数使用でテストが容易
