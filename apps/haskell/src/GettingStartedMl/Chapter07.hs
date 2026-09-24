{-# LANGUAGE OverloadedStrings #-}

{- | 第 7 章: 線形回帰による数値予測。

正規方程式 @(Xᵀ X) w = Xᵀ t@ をガウス・ジョルダン法で自作してから、
hmatrix の @\<\\\>@（最小二乗解）と突き合わせる。

Haskell には scikit-learn にあたるものが無いので、機械学習のアルゴリズムは
自作が最終実装になる。突き合わせられるのは線形代数の層だけで、この章は
その数少ない章の 1 つである。
-}
module GettingStartedMl.Chapter07 (
  LinearModel (..),
  featureColumns,
  targetColumn,
  outlierSns2,
  outlierSales,
  model,
  coefficient,
  predictOne,
  predict,
  residuals,
  sumOfSquares,
  meanAbsoluteError,
  rootMeanSquaredError,
  r2Score,
  designMatrix,
  featureRow,
  solveLinearSystem,
  fit,
  hmatrixFit,
  hmatrixNormalFit,
  removeOutliers,
  targetValues,
  prepareCinema,
  run,
  report,
) where

import qualified Data.ByteString.Lazy as BL
import Data.List (transpose)
import qualified Data.Map.Strict as M
import Data.Text (Text)
import qualified Data.Text as T
import GettingStartedMl.Chapter02 (
  Split (..),
  Table (..),
  columnMeans,
  fillMissing,
  loadTable,
  splitTrainTest,
 )
import GettingStartedMl.Csv (Row, optionalNumber, text)
import qualified GettingStartedMl.Dataset as Dataset
import qualified Numeric.LinearAlgebra as LA
import Text.Printf (printf)

{- | 切片と、列名に対応した係数を持つ線形モデル。

係数は列名と別のリストで持つ。'M.Map' はキーの順で並ぶので、
「CSV に現れた順」や「表示したい順」を保ちたいところではリストにする。
-}
data LinearModel = LinearModel
  { modelIntercept :: Double
  , modelColumns :: [Text]
  , modelCoefficients :: [Double]
  }
  deriving (Eq, Show)

-- | 映画のデータの特徴量の列。cinema_id は映画を区別する番号なので使わない。
featureColumns :: [Text]
featureColumns = ["SNS1", "SNS2", "actor", "original"]

-- | 映画のデータの正解ラベル（予測したい数値）の列。
targetColumn :: Text
targetColumn = "sales"

-- | 外れ値とみなす SNS2 の下限。
outlierSns2 :: Double
outlierSns2 = 1000.0

-- | 外れ値とみなす興行収入の上限。
outlierSales :: Double
outlierSales = 8500.0

-- | 列名と、同じ順に並んだ係数からモデルを作る。数が違えば 'Left' を返す。
model :: Double -> [Text] -> [Double] -> Either String LinearModel
model intercept columns coefficients
  | length columns /= length coefficients =
      Left (printf "列名と係数の数が違います: %d と %d" (length columns) (length coefficients))
  | otherwise = Right (LinearModel intercept columns coefficients)

-- | 列名で係数を読む。無ければ 'Left' を返す。
coefficient :: LinearModel -> Text -> Either String Double
coefficient m name =
  case lookup name (zip (modelColumns m) (modelCoefficients m)) of
    Nothing -> Left ("係数がありません: " <> T.unpack name)
    Just value -> Right value

-- | 1 行分の特徴量の予測値。係数は列名で対応させるので、並び順は問わない。
predictOne :: LinearModel -> M.Map Text Double -> Either String Double
predictOne m features =
  (modelIntercept m +) . sum
    <$> traverse weighted (zip (modelColumns m) (modelCoefficients m))
 where
  weighted (name, weight) = (weight *) <$> feature features name

-- | 行ごとの予測値。
predict :: LinearModel -> [M.Map Text Double] -> Either String [Double]
predict m = traverse (predictOne m)

-- | 特徴量を列名で読む。無ければ 'Left' を返す。
feature :: M.Map Text Double -> Text -> Either String Double
feature features name =
  case M.lookup name features of
    Nothing -> Left ("特徴量がありません: " <> T.unpack name)
    Just value -> Right value

-- | 残差。実測値から予測値を引いた値。
residuals :: [Double] -> [Double] -> Either String [Double]
residuals t y
  | length t /= length y =
      Left (printf "実測値と予測値の件数が違います: %d と %d" (length t) (length y))
  | null t = Left "実測値がありません"
  | otherwise = Right (zipWith (-) t y)

-- | 二乗の合計。
sumOfSquares :: [Double] -> Double
sumOfSquares = sum . map (\v -> v * v)

-- | 平均絶対誤差（MAE）。誤差の絶対値の平均。
meanAbsoluteError :: [Double] -> [Double] -> Either String Double
meanAbsoluteError t y = do
  r <- residuals t y
  pure (sum (map abs r) / fromIntegral (length r))

-- | 平均二乗誤差の平方根（RMSE）。
rootMeanSquaredError :: [Double] -> [Double] -> Either String Double
rootMeanSquaredError t y = do
  r <- residuals t y
  pure (sqrt (sumOfSquares r / fromIntegral (length r)))

{- | 決定係数（R²）。

1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。
実測値がすべて同じだと割る数が 0 になるので、そこだけ 'Left' で返す。
-}
r2Score :: [Double] -> [Double] -> Either String Double
r2Score t y = do
  r <- residuals t y
  let mean = sum t / fromIntegral (length t)
      total = sumOfSquares (map (subtract mean) t)
  if total == 0.0
    then Left "実測値がすべて同じ値なので決定係数を求められません"
    else Right (1.0 - sumOfSquares r / total)

-- | 1 行分の特徴量を、列の順に並べたリストにする。
featureRow :: M.Map Text Double -> [Text] -> Either String [Double]
featureRow features = traverse (feature features)

-- | 先頭に 1 の列を足した特徴量の行列（計画行列）。1 の列の係数が切片になる。
designMatrix :: [M.Map Text Double] -> [Text] -> Either String [[Double]]
designMatrix x columns = traverse (fmap (1.0 :) . (`featureRow` columns)) x

{- | ガウス・ジョルダン法で連立方程式 @A w = b@ を解く。

各段で絶対値が最大の行を軸に選ぶ（部分ピボット選択）。軸が 0 に近ければ
特異行列とみなして 'Left' を返す。逆行列を作らずに解くのは、そのほうが
数値計算の誤差が小さくなるためで、ほかの言語版と同じ判断である。
-}
solveLinearSystem :: [[Double]] -> [Double] -> Either String [Double]
solveLinearSystem a b
  | length a /= length b =
      Left (printf "行列と定数の件数が違います: %d と %d" (length a) (length b))
  | null a = Left "連立方程式が空です"
  | any ((/= length a) . length) a = Left "正方行列ではありません"
  | otherwise = map last <$> eliminate 0 (zipWith (\row rhs -> row <> [rhs]) a b)
 where
  size = length a

  eliminate i rows
    | i == size = Right rows
    | otherwise = do
        let (before, rest) = splitAt i rows
        (pivotRow, others) <- pickPivot i rest
        let normalized = map (/ (pivotRow !! i)) pivotRow
            reduce row = zipWith (\v p -> v - (row !! i) * p) row normalized
        eliminate (i + 1) (map reduce before <> (normalized : map reduce others))

  pickPivot i rows =
    case rows of
      [] -> Left "解けません。特異行列です"
      _ ->
        let best = maximum (map (abs . (!! i)) rows)
         in if best < 1e-12
              then Left "解けません。特異行列です"
              else
                let (skipped, chosen) = break ((== best) . abs . (!! i)) rows
                 in case chosen of
                      [] -> Left "解けません。特異行列です"
                      (pivotRow : after) -> Right (pivotRow, skipped <> after)

{- | 正規方程式 @(Xᵀ X) w = Xᵀ t@ を解いて、切片と係数を求める。

'w' の先頭が切片、残りが係数になる。
-}
fit :: [M.Map Text Double] -> [Double] -> [Text] -> Either String LinearModel
fit x t columns
  | null x = Left "訓練データが空です"
  | length x /= length t =
      Left (printf "特徴量と実測値の件数が違います: %d と %d" (length x) (length t))
  | otherwise = do
      design <- designMatrix x columns
      let transposed = transpose design
          normal = [[sum (zipWith (*) r c) | c <- transpose design] | r <- transposed]
          rhs = [sum (zipWith (*) r t) | r <- transposed]
      weights <- solveLinearSystem normal rhs
      case weights of
        [] -> Left "解が空です"
        (intercept : coefficients) -> model intercept columns coefficients

{- | hmatrix の @\<\\\>@ に計画行列をそのまま渡して、最小二乗解を求める。

正規方程式を組み立てずに @X w ≈ t@ を直接解く。hmatrix の @\<\\\>@ は
LAPACK の最小二乗の routine を呼ぶので、@Xᵀ X@ を作るぶんの条件数の悪化が
起きない。自作と一致するかは 'Chapter07Spec' で実測する。
-}
hmatrixFit :: [M.Map Text Double] -> [Double] -> [Text] -> Either String LinearModel
hmatrixFit x t columns = do
  design <- checkedDesign x t columns
  toModel columns (LA.toList (LA.fromLists design LA.<\> LA.fromList t))

{- | 正規方程式を組み立て、その @(Xᵀ X) w = Xᵀ t@ だけを hmatrix に解かせる。

自作との違いを「解き方」だけにするための比較用。'hmatrixFit' との差が
「正規方程式を作るかどうか」の差になる。
-}
hmatrixNormalFit :: [M.Map Text Double] -> [Double] -> [Text] -> Either String LinearModel
hmatrixNormalFit x t columns = do
  design <- checkedDesign x t columns
  let matrix = LA.fromLists design
      transposed = LA.tr matrix
      normal = transposed LA.<> matrix
      rhs = transposed LA.#> LA.fromList t
  toModel columns (LA.toList (normal LA.<\> rhs))

-- | 件数を確かめてから計画行列を作る。hmatrix に渡す前の門番。
checkedDesign :: [M.Map Text Double] -> [Double] -> [Text] -> Either String [[Double]]
checkedDesign x t columns
  | null x = Left "訓練データが空です"
  | length x /= length t =
      Left (printf "特徴量と実測値の件数が違います: %d と %d" (length x) (length t))
  | otherwise = designMatrix x columns

-- | 解のベクトルを、先頭を切片としてモデルにする。
toModel :: [Text] -> [Double] -> Either String LinearModel
toModel _ [] = Left "解が空です"
toModel columns (intercept : coefficients) = model intercept columns coefficients

-- | 外れ値の行を除いた表を返す。列はそのまま残す。
removeOutliers :: Table -> Either String Table
removeOutliers table = do
  keep <- traverse (fmap not . isOutlier) (tableRows table)
  pure table {tableRows = [row | (row, k) <- zip (tableRows table) keep, k]}

-- | SNS2 が大きく、それなのに興行収入が小さい行を外れ値とする。
isOutlier :: Row -> Either String Bool
isOutlier row = do
  sns2 <- optionalNumber row "SNS2"
  sales <- optionalNumber row targetColumn
  pure (maybe False (> outlierSns2) sns2 && maybe False (< outlierSales) sales)

-- | 正解ラベルの文字列を数値にする。
targetValues :: [Text] -> Either String [Double]
targetValues = traverse readDouble
 where
  readDouble cell = case reads (T.unpack (T.strip cell)) of
    [(value, rest)] | all (== ' ') rest -> Right value
    _ -> Left (T.unpack targetColumn <> " を数値として読めません: " <> T.unpack cell)

{- | cinema.csv を読み込み、外れ値を除き、分割してから訓練データの平均値で補完する。

順番が大事で、**外れ値を除いてから分割し、訓練データだけから平均を求めて
両方を補完する**。テストデータの平均を混ぜると、テストデータの情報が訓練に漏れる。
-}
prepareCinema :: BL.ByteString -> Double -> Int -> Either String (Split (M.Map Text Double))
prepareCinema contents testSize seed = do
  table <- loadTable contents >>= removeOutliers
  labels <- traverse (`text` targetColumn) (tableRows table)
  split <- splitTrainTest (tableRows table) labels testSize seed
  means <- columnMeans (xTrain split) featureColumns
  filledTrain <- fillMissing (xTrain split) featureColumns means
  filledTest <- fillMissing (xTest split) featureColumns means
  pure split {xTrain = filledTrain, xTest = filledTest}

-- | 映画の興行収入を線形回帰で予測し、係数と評価指標を表示する。
run :: IO (Either String String)
run = do
  file <- Dataset.path "cinema.csv"
  contents <- BL.readFile file
  pure (report contents)

-- | 読み込んだ中身から報告の文字列を作る。純粋関数なのでテストで確かめられる。
report :: BL.ByteString -> Either String String
report contents = do
  table <- loadTable contents
  kept <- removeOutliers table
  split <- prepareCinema contents 0.2 0
  trainT <- targetValues (tTrain split)
  testT <- targetValues (tTest split)
  mine <- fit (xTrain split) trainT featureColumns
  theirs <- hmatrixFit (xTrain split) trainT featureColumns
  y <- predict mine (xTest split)
  r2 <- r2Score testT y
  mae <- meanAbsoluteError testT y
  rmse <- rootMeanSquaredError testT y
  pure $
    concat
      [ printf "データ件数: %d\n" (length (tableRows table))
      , printf "外れ値を除いた件数: %d\n" (length (tableRows kept))
      , printf
          "訓練データ: %d 件, テストデータ: %d 件\n"
          (length (xTrain split))
          (length (xTest split))
      , printf "切片: %.2f\n" (modelIntercept mine)
      , printf "係数: %s\n" (formatCoefficients mine)
      , printf
          "hmatrix の切片: %.2f, 係数: %s\n"
          (modelIntercept theirs)
          (formatCoefficients theirs)
      , printf "テストデータの評価: R2=%.4f, MAE=%.2f, RMSE=%.2f\n" r2 mae rmse
      ]

-- | 係数を「列名=値」の形で並べる。
formatCoefficients :: LinearModel -> String
formatCoefficients m =
  T.unpack . T.intercalate ", " $
    [ name <> "=" <> T.pack (printf "%.4f" weight)
    | (name, weight) <- zip (modelColumns m) (modelCoefficients m)
    ]
