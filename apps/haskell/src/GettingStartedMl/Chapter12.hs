{-# LANGUAGE OverloadedStrings #-}

{- | 第 12 章: 正則化とモデル選択。

リッジ回帰を第 7 章の正規方程式に 1 項足して自作し、hmatrix と突き合わせる。
ラッソ回帰は座標降下法で自作する。**hmatrix は線形代数の道具でしかないので、
ラッソ回帰に突き合わせる相手は無く、自作がそのまま最終実装になる**（ADR 014）。

前処理は新しく書かない。第 9 章の 'C9.Standardizer' と 'C9.expand'、
第 2 章の 'C2.splitTrainTest'、第 7 章の 'C7.LinearModel' をそのまま使う。
-}
module GettingStartedMl.Chapter12 (
  -- * 設定
  featureColumns,
  targetColumn,
  outlierThreshold,
  testSize,
  validationSize,
  seed,
  alphas,
  lassoAlpha,

  -- * リッジ回帰
  ridgeFit,
  hmatrixRidgeFit,

  -- * ラッソ回帰
  softThreshold,
  lassoFit,

  -- * 係数を読む
  coefficientAbsSum,
  zeroCoefficientNames,

  -- * 実験の記録とモデル選択
  Experiment (..),
  runRidgeExperiments,
  bestExperiment,

  -- * 外れ値と前処理
  removeOutliers,
  BostonData (..),
  prepareBoston,
  testScore,

  -- * 実データでの実行
  report,
  run,
) where

import qualified Data.ByteString.Lazy as BL
import Data.List (transpose)
import qualified Data.Map.Strict as M
import Data.Text (Text)
import qualified Data.Text as T
import qualified GettingStartedMl.Chapter02 as C2
import qualified GettingStartedMl.Chapter07 as C7
import qualified GettingStartedMl.Chapter09 as C9
import GettingStartedMl.Csv (Row, optionalNumber, text)
import qualified GettingStartedMl.Dataset as Dataset
import qualified Numeric.LinearAlgebra as LA
import Text.Printf (printf)

-- 設定 -----------------------------------------------------------------------

-- | 使う特徴量の列。2 次の項を作る順もこの並びで決まる。
featureColumns :: [Text]
featureColumns = ["RM", "PTRATIO", "LSTAT"]

-- | 正解の列（住宅価格）。
targetColumn :: Text
targetColumn = "PRICE"

-- | z スコアの絶対値がこの値を超える値を持つ行を外れ値とする。
outlierThreshold :: Double
outlierThreshold = 3.0

-- | テストデータの割合。
testSize :: Double
testSize = 0.3

-- | 訓練用をさらに分けるときの、検証データの割合。
validationSize :: Double
validationSize = 0.3

-- | 分割の乱数のシード。
seed :: Int
seed = 0

-- | 試す正則化の強さ。
alphas :: [Double]
alphas = [0.0, 0.1, 1.0, 10.0, 100.0]

{- | ラッソ回帰の正則化の強さ。

自作の目的関数は件数で割らないので、件数で割る実装（Java 版・Clojure 版が使う
Tribuo の @ElasticNetCDTrainer@）の @alpha=0.5@ は、訓練データ 47 件では
@0.5 * 47 = 23.5@ にあたる。
-}
lassoAlpha :: Double
lassoAlpha = 23.5

-- | 座標降下法の繰り返しの上限。
maxIterations :: Int
maxIterations = 10000

-- | 係数の動きがこれより小さくなったら打ち切る。
tolerance :: Double
tolerance = 1.0e-10

-- リッジ回帰 -----------------------------------------------------------------

{- | 平均を引いてから @(Xᵀ X + alpha I) w = Xᵀ t@ を解いて係数を求める。

平均を引くのは **切片に罰則をかけないため**。@alpha@ が 0 なら
最小二乗法（第 7 章の 'C7.fit'）と同じ解になる。解くところは第 7 章の
'C7.solveLinearSystem'（ガウス・ジョルダン法）をそのまま使う。
-}
ridgeFit :: [M.Map Text Double] -> [Double] -> [Text] -> Double -> Either String C7.LinearModel
ridgeFit x t columns alpha = do
  (means, tMean, centered) <- centeredProblem x t columns
  -- 中心化した X の列（＝ Xᵀ の行）。Xᵀ X はこの列どうしの内積でできる。
  let transposed = transpose centered
      normal = penalize alpha [[dot left right | right <- transposed] | left <- transposed]
      rhs = [dot left (map (subtract tMean) t) | left <- transposed]
  weights <- C7.solveLinearSystem normal rhs
  C7.model (tMean - dot means weights) columns weights

{- | 同じ正規方程式を hmatrix に解かせる。

自作との違いを「解き方」だけにするための比較用。第 7 章で自作のガウス・ジョルダン法と
hmatrix を突き合わせたのと同じ形である。**突き合わせられるのはここまで**で、
ラッソ回帰には相手がいない。
-}
hmatrixRidgeFit
  :: [M.Map Text Double] -> [Double] -> [Text] -> Double -> Either String C7.LinearModel
hmatrixRidgeFit x t columns alpha = do
  (means, tMean, centered) <- centeredProblem x t columns
  let matrix = LA.fromLists centered
      transposed = LA.tr matrix
      penalty = LA.scale alpha (LA.ident (length means))
      normal = (transposed LA.<> matrix) + penalty
      rhs = transposed LA.#> LA.fromList (map (subtract tMean) t)
      weights = LA.toList (normal LA.<\> rhs)
  C7.model (tMean - dot means weights) columns weights

{- | 件数を確かめ、列ごとの平均・正解の平均・中心化した行を返す。

リッジ回帰とラッソ回帰で同じ前準備をするので 1 か所にまとめた。
-}
centeredProblem
  :: [M.Map Text Double]
  -> [Double]
  -> [Text]
  -> Either String ([Double], Double, [[Double]])
centeredProblem x t columns
  | null x = Left "訓練データが空です"
  | length x /= length t =
      Left (printf "特徴量と正解の件数が違います: %d と %d" (length x) (length t))
  | otherwise = do
      rows <- C9.toRows x columns
      let means = map average (transpose rows)
          tMean = average t
      pure (means, tMean, map (\row -> zipWith (-) row means) rows)

-- | 対角に @alpha@ を足す。切片は中心化で外してあるので、罰則は係数だけにかかる。
penalize :: Double -> [[Double]] -> [[Double]]
penalize alpha matrix =
  [ [if i == j then value + alpha else value | (j, value) <- zip [0 :: Int ..] row]
  | (i, row) <- zip [0 ..] matrix
  ]

-- ラッソ回帰 -----------------------------------------------------------------

{- | 軟しきい値作用素。

@|value|@ が @threshold@ 以下なら 0 にし、そうでなければ 0 のほうへ縮める。
ガードで 3 つの場合に分けると、定義がそのまま式になる。
-}
softThreshold :: Double -> Double -> Double
softThreshold value threshold
  | value > threshold = value - threshold
  | value < negate threshold = value + threshold
  | otherwise = 0.0

{- | 平均を引いてから座標降下法で @½‖t - Xw‖² + alpha ‖w‖₁@ を最小にする。

L1 の罰則は原点で折れているので、リッジ回帰のように行列を解いて終わりにはできない。
係数を 1 つずつ順に動かし、'softThreshold' で 0 に寄せる。

再代入が書けないので、係数は 'M.Map' に持って畳み込みで書き換える。第 2 章の
Fisher-Yates と同じ形で、**「書き換わる変数」が「次の値を返す関数」になる**。
-}
lassoFit :: [M.Map Text Double] -> [Double] -> [Text] -> Double -> Either String C7.LinearModel
lassoFit x t columns alpha = do
  (means, tMean, centered) <- centeredProblem x t columns
  let cols = transpose centered
      norms = map (\c -> dot c c) cols
      entries = zip3 [0 ..] cols norms
      initial = M.fromList (zip [0 :: Int ..] (map (const 0.0) cols))
      weights = M.elems (descend entries maxIterations initial (map (subtract tMean) t))
  C7.model (tMean - dot means weights) columns weights
 where
  -- 係数がどれも tolerance より動かなくなるまで、すべての係数を 1 回ずつ動かす。
  descend _ 0 weights _ = weights
  descend entries steps weights residual =
    let (moved, nextResidual, delta) = foldl (sweep alpha) (weights, residual, 0.0) entries
     in if delta < tolerance
          then moved
          else descend entries (steps - 1) moved nextResidual

-- | 1 つの係数を動かし、残差といちばん大きな動きを一緒に返す。
sweep
  :: Double
  -> (M.Map Int Double, [Double], Double)
  -> (Int, [Double], Double)
  -> (M.Map Int Double, [Double], Double)
sweep alpha (weights, residual, delta) (index, col, norm)
  | norm == 0.0 = (weights, residual, delta)
  | otherwise = (M.insert index weight weights, removed, max delta (abs (weight - old)))
 where
  old = M.findWithDefault 0.0 index weights
  -- いったん列の寄与を残差に戻してから、残差との相関で係数を決め直す
  restored = zipWith (\r value -> r + old * value) residual col
  weight = softThreshold (dot col restored) alpha / norm
  removed = zipWith (\r value -> r - weight * value) restored col

-- 係数を読む -----------------------------------------------------------------

-- | 係数の絶対値の合計。正則化が強いほど小さくなる。
coefficientAbsSum :: C7.LinearModel -> Double
coefficientAbsSum = sum . map abs . C7.modelCoefficients

{- | 係数がちょうど 0 になった特徴量の名前を、列の順に返す。

'C7.model' が列名と係数の長さをそろえてからでないとモデルを作れないので、
ここで長さを確かめ直す必要はない。**不正な状態のモデルを作れない**ことの見返りである。
-}
zeroCoefficientNames :: C7.LinearModel -> [Text]
zeroCoefficientNames m =
  [ name
  | (name, value) <- zip (C7.modelColumns m) (C7.modelCoefficients m)
  , value == 0.0
  ]

-- 実験の記録とモデル選択 -----------------------------------------------------

-- | @alpha@ ごとの実験の記録。
data Experiment = Experiment
  { experimentAlpha :: Double
  , experimentTrainScore :: Double
  , experimentValidationScore :: Double
  , experimentCoefficientAbsSum :: Double
  }
  deriving (Eq, Show)

-- | @alpha@ ごとにリッジ回帰を学習し、訓練データと検証データの決定係数を記録する。
runRidgeExperiments :: BostonData -> [Double] -> Either String [Experiment]
runRidgeExperiments dataset = traverse experiment
 where
  experiment alpha = do
    fitted <- ridgeFit (bdXTrain dataset) (bdTTrain dataset) (bdFeatureNames dataset) alpha
    trainY <- C7.predict fitted (bdXTrain dataset)
    validY <- C7.predict fitted (bdXValid dataset)
    trainScore <- C7.r2Score (bdTTrain dataset) trainY
    validScore <- C7.r2Score (bdTValid dataset) validY
    pure (Experiment alpha trainScore validScore (coefficientAbsSum fitted))

{- | 検証データの決定係数が最も高い実験。同じ値なら **先の実験**（小さい @alpha@）を選ぶ。

'Data.List.maximumBy' は同値なら後ろを返す（第 3 章で刺された）ので、
畳み込みで「厳密に大きいときだけ入れ替える」と書く。
-}
bestExperiment :: [Experiment] -> Either String Experiment
bestExperiment [] = Left "実験結果が 1 件もありません"
bestExperiment (first : rest) = Right (foldl better first rest)
 where
  better current candidate
    | experimentValidationScore candidate > experimentValidationScore current = candidate
    | otherwise = current

-- 外れ値と前処理 -------------------------------------------------------------

{- | 列ごとの z スコアの絶対値が @threshold@ を超える値を 1 つでも持つ行を除く。

標準偏差は **件数 n - 1 で割る標本標準偏差**を使う（第 9 章の標準化が使う
n の標準偏差とは違う）。n 件のデータの z スコアには @(n - 1) / √n@ という
上限があるので、件数が少ないとしきい値 3.0 には決して届かない。
-}
removeOutliers :: C2.Table -> [Text] -> Double -> Either String C2.Table
removeOutliers table columns threshold = do
  stats <- traverse statistic columns
  kept <- traverse (fmap not . isOutlier stats) (C2.tableRows table)
  pure table {C2.tableRows = [row | (row, k) <- zip (C2.tableRows table) kept, k]}
 where
  statistic column = do
    values <- traverse (`requiredNumber` column) (C2.tableRows table)
    if length values < 2
      then Left ("外れ値を判定するには 2 件以上が要ります: " <> T.unpack column)
      else
        let m = average values
         in Right (column, m, sqrt (sum [(v - m) ** 2 | v <- values] / fromIntegral (length values - 1)))

  isOutlier stats row = or <$> traverse (beyond row) stats

  beyond row (column, m, deviation) = do
    value <- requiredNumber row column
    pure (deviation /= 0.0 && abs ((value - m) / deviation) > threshold)

-- | 訓練・検証・テストの 3 つに分けた特徴量と正解、特徴量名、外れ値を除いた件数。
data BostonData = BostonData
  { bdXTrain :: [M.Map Text Double]
  , bdTTrain :: [Double]
  , bdXValid :: [M.Map Text Double]
  , bdTValid :: [Double]
  , bdXTest :: [M.Map Text Double]
  , bdTTest :: [Double]
  , bdFeatureNames :: [Text]
  , bdKept :: Int
  }
  deriving (Eq, Show)

{- | 外れ値を除き、全体を訓練用とテスト用に、訓練用をさらに訓練データと検証データに分ける。

標準化の平均と標準偏差は **訓練データだけから** 求め、3 つとも同じ値で変換する。
第 2 章の 'C2.splitTrainTest' を 2 回呼ぶだけで 3 分割になるので、新しい分割の
関数は書いていない。
-}
prepareBoston :: BL.ByteString -> Double -> Double -> Int -> Either String BostonData
prepareBoston contents outerSize innerSize s = do
  table <- C2.loadTable contents >>= \t -> removeOutliers t (targetColumn : featureColumns) outlierThreshold
  x <- traverse featureMap (C2.tableRows table)
  labels <- traverse (`text` targetColumn) (C2.tableRows table)
  outer <- C2.splitTrainTest x labels outerSize s
  inner <- C2.splitTrainTest (C2.xTrain outer) (C2.tTrain outer) innerSize s
  scaler <- C9.fitStandardizer (C2.xTrain inner) featureColumns
  xTrain <- build scaler (C2.xTrain inner)
  xValid <- build scaler (C2.xTest inner)
  xTest <- build scaler (C2.xTest outer)
  tTrain <- traverse readDouble (C2.tTrain inner)
  tValid <- traverse readDouble (C2.tTest inner)
  tTest <- traverse readDouble (C2.tTest outer)
  pure
    BostonData
      { bdXTrain = xTrain
      , bdTTrain = tTrain
      , bdXValid = xValid
      , bdTValid = tValid
      , bdXTest = xTest
      , bdTTest = tTest
      , bdFeatureNames = C9.expandedColumns featureColumns
      , bdKept = length (C2.tableRows table)
      }
 where
  featureMap row = M.fromList <$> traverse (\c -> (,) c <$> requiredNumber row c) featureColumns
  build scaler rows = C9.expand (C9.standardizeAll scaler rows) featureColumns

-- | 指定した @alpha@ のリッジ回帰を訓練データで学習し、テストデータの決定係数を返す。
testScore :: BostonData -> Double -> Either String Double
testScore dataset alpha = do
  fitted <- ridgeFit (bdXTrain dataset) (bdTTrain dataset) (bdFeatureNames dataset) alpha
  y <- C7.predict fitted (bdXTest dataset)
  C7.r2Score (bdTTest dataset) y

-- 小さな道具 -----------------------------------------------------------------

-- | 内積。
dot :: [Double] -> [Double] -> Double
dot a b = sum (zipWith (*) a b)

-- | 平均。空のリストは 'centeredProblem' で先に弾いてある。
average :: [Double] -> Double
average [] = 0.0
average values = sum values / fromIntegral (length values)

-- | 空欄を許さない数値の列を読む。
requiredNumber :: Row -> Text -> Either String Double
requiredNumber row name = do
  value <- optionalNumber row name
  case value of
    Nothing -> Left ("値が空欄です: " <> T.unpack name)
    Just found -> Right found

-- | 文字列を小数として読む。
readDouble :: Text -> Either String Double
readDouble cell =
  case reads (T.unpack (T.strip cell)) of
    [(value, rest)] | all (== ' ') rest -> Right value
    _ -> Left (T.unpack targetColumn <> " を数値として読めません: " <> T.unpack cell)

-- 実データでの実行 -----------------------------------------------------------

-- | 正則化の強さごとにリッジ回帰を学習し、検証データで選んだモデルの結果を表示する。
run :: IO (Either String String)
run = do
  contents <- Dataset.path "Boston.csv" >>= BL.readFile
  pure (report contents)

-- | 読み込んだ中身から報告の文字列を作る。純粋関数なのでテストで確かめられる。
report :: BL.ByteString -> Either String String
report contents = do
  table <- C2.loadTable contents
  dataset <- prepareBoston contents testSize validationSize seed
  experiments <- runRidgeExperiments dataset alphas
  best <- bestExperiment experiments
  linearScore <- testScore dataset 0.0
  ridgeScore <- testScore dataset (experimentAlpha best)
  mine <- ridgeFit (bdXTrain dataset) (bdTTrain dataset) (bdFeatureNames dataset) (experimentAlpha best)
  theirs <- hmatrixRidgeFit (bdXTrain dataset) (bdTTrain dataset) (bdFeatureNames dataset) (experimentAlpha best)
  lasso <- lassoFit (bdXTrain dataset) (bdTTrain dataset) (bdFeatureNames dataset) lassoAlpha
  pure $
    concat
      [ printf
          "データ件数: %d（外れ値 %d 件を除外）\n"
          (bdKept dataset)
          (length (C2.tableRows table) - bdKept dataset)
      , printf
          "訓練データ: %d 件, 検証データ: %d 件, テストデータ: %d 件\n"
          (length (bdTTrain dataset))
          (length (bdTValid dataset))
          (length (bdTTest dataset))
      , printf "特徴量: %s\n" (T.unpack (T.intercalate ", " (bdFeatureNames dataset)))
      , "alpha  訓練 R²  検証 R²  係数の絶対値の合計\n"
      , concatMap formatExperiment experiments
      , printf "検証データで選んだ alpha: %.1f\n" (experimentAlpha best)
      , printf
          "テストデータの決定係数: 線形回帰 %.4f, リッジ回帰 %.4f\n"
          linearScore
          ridgeScore
      , printf
          "hmatrix のリッジ回帰との係数の最大の差: %.1e\n"
          (maxDifference mine theirs)
      , printf
          "ラッソ回帰（alpha=%.1f）で係数が 0 になった特徴量: %s\n"
          lassoAlpha
          (T.unpack (T.intercalate ", " (zeroCoefficientNames lasso)))
      ]

-- | 実験 1 件を表の 1 行にする。
formatExperiment :: Experiment -> String
formatExperiment e =
  printf
    "%5s  %.4f  %.4f  %.3f\n"
    (printf "%.1f" (experimentAlpha e) :: String)
    (experimentTrainScore e)
    (experimentValidationScore e)
    (experimentCoefficientAbsSum e)

-- | 2 つのモデルの係数の差のうち、いちばん大きいもの。
maxDifference :: C7.LinearModel -> C7.LinearModel -> Double
maxDifference left right =
  maximum (0.0 : zipWith (\a b -> abs (a - b)) (C7.modelCoefficients left) (C7.modelCoefficients right))
