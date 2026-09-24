{-# LANGUAGE OverloadedStrings #-}

{- | 第 13 章: 主成分分析による次元削減。

中心化・分散共分散行列・固有値分解・符号・射影・寄与率を自作し、固有値分解だけを
hmatrix の @eigSH@（LAPACK の DSYEV）と突き合わせる。

固有値分解は 2 通り用意する。1 つは自作の Jacobi 法（'jacobiEigen'）、もう 1 つは
hmatrix の @eigSH@（'hmatrixEigen'）である。'fitWith' はどちらでも受け取れるので、
同じ主成分分析を 2 つの固有値分解で回して差を測れる。

行列は「長さのそろった @[Double]@ のリスト」で表し、hmatrix の @Matrix@ にするのは
'hmatrixEigen' の中だけにする。

標準化は第 9 章の 'fitStandardizer'・'standardizeAll' をそのまま使う。同じ標準化を
2 つ持つと、片方だけ直したときに気づけない。
-}
module GettingStartedMl.Chapter13 (
  -- * 設定
  categoryColumn,
  varianceThreshold,
  topK,
  componentsToExplain,

  -- * 中心化と分散共分散行列
  matrixColumnMeans,
  center,
  covarianceMatrix,

  -- * 固有値分解
  Eigen (..),
  jacobiEigen,
  hmatrixEigen,
  normalizeSigns,

  -- * 主成分分析
  Pca (..),
  fit,
  fitWith,
  transform,
  hmatrixGaps,

  -- * 主成分の数と解釈
  componentsNeeded,
  topLoadings,

  -- * ボストンの住宅価格
  standardizeTable,
  toMatrix,
  loadBoston,
  report,
  run,
) where

import qualified Data.ByteString.Lazy as BL
import Data.List (sortBy, transpose)
import qualified Data.Map.Strict as M
import Data.Ord (Down (..), comparing)
import Data.Text (Text)
import qualified Data.Text as T
import GettingStartedMl.Chapter02 (Table (..), columnMeans, fillMissing, loadTable)
import GettingStartedMl.Chapter09 (categories, encode, fitStandardizer, standardizeAll, toRows)
import GettingStartedMl.Csv (text)
import qualified GettingStartedMl.Dataset as Dataset
import qualified Numeric.LinearAlgebra as LA
import Text.Printf (printf)

-- | カテゴリ値の列。
categoryColumn :: Text
categoryColumn = "CRIME"

-- | 何割のばらつきを説明できれば十分とみなすか。
varianceThreshold :: Double
varianceThreshold = 0.8

-- | 主成分ごとに表示する列の数。
topK :: Int
topK = 3

-- | 意味を読む主成分の数。
componentsToExplain :: Int
componentsToExplain = 2

-- | Jacobi 法の掃き出しの回数の上限。15×15 なら 10 巡もあれば収束する。
maxSweeps :: Int
maxSweeps = 100

-- | 非対角成分の 2 乗和がこれを下回ったら対角行列とみなす。
offTolerance :: Double
offTolerance = 1.0e-30

-- 中心化と分散共分散行列 -----------------------------------------------------

-- | 列ごとの平均を返す。
matrixColumnMeans :: [[Double]] -> [Double]
matrixColumnMeans [] = []
matrixColumnMeans m = map (\col -> foldl' (+) 0.0 col / n) (transpose m)
 where
  n = fromIntegral (length m)

-- | 各列から平均を引く（中心化）。
center :: [[Double]] -> [Double] -> [[Double]]
center m means = map (zipWith (-) `flip` means) m

{- | 列ごとの分散と、2 列ずつの共分散を並べた行列。件数から 1 を引いた数で割る。

割る数を n−1 にするか n にするかは、この章の結論に影響しない。寄与率は
「固有値 ÷ 固有値の合計」なので、行列全体を定数倍しても変わらないからである。
それでも n−1 を選んだのは、ほかの言語版と分散そのものの値をそろえるため。
-}
covarianceMatrix :: [[Double]] -> Either String [[Double]]
covarianceMatrix m
  | length m < 2 = Left (printf "主成分分析には 2 件以上のデータが必要です（%d 件）" (length m))
  | otherwise = Right [[dot a b / (n - 1) | b <- centered] | a <- centered]
 where
  centered = transpose (center m (matrixColumnMeans m))
  n = fromIntegral (length m)

-- 固有値分解 -----------------------------------------------------------------

-- | 固有値と、1 行に 1 つずつ並べた固有ベクトル。固有値の大きい順に並ぶ。
data Eigen = Eigen
  { eigenValues :: [Double]
  , eigenVectors :: [[Double]]
  }
  deriving (Eq, Show)

{- | 対称行列を Jacobi 法で固有値分解し、固有値と固有ベクトルを大きい順に返す。

上三角の非対角成分を順に 2 次元の回転で 0 にしていき、全体が対角行列に近づくまで
何巡もする。回転行列を掛け合わせたものが固有ベクトルになる。

固有値と固有ベクトルが同時に求まるので、「固有値を求めてから、それを使って
固有ベクトルを探す」という 2 段構えにならない。PHP 版では MathPHP の
2 段構えの実装が 15×15 の実データで落ちた。
-}
jacobiEigen :: [[Double]] -> Either String Eigen
jacobiEigen m = do
  checkSymmetric m
  let size = length m
      initialA = M.fromList [((i, j), value) | (i, row) <- zip [0 ..] m, (j, value) <- zip [0 ..] row]
      initialV = M.fromList [((i, j), if i == j then 1.0 else 0.0) | i <- [0 .. size - 1], j <- [0 .. size - 1]]
      (a, v) = sweep maxSweeps size initialA initialV
      values = [at a (i, i) | i <- [0 .. size - 1]]
      -- 固有ベクトルは列に並ぶので、転置して 1 行に 1 つずつにする。
      vectors = transpose [[at v (i, j) | j <- [0 .. size - 1]] | i <- [0 .. size - 1]]
  pure (sortEigen values vectors)

{- | 対称行列を hmatrix の @eigSH@（LAPACK の DSYEV）で固有値分解する。

@eigSH@ は @Herm@ を受け取る。@trustSym@ は対称かどうかを確かめずに信じるので、
対称でない行列を渡すと黙って上三角だけを見た別の行列の答えを返す。
対称かどうかはここで確かめて失敗させる。
-}
hmatrixEigen :: [[Double]] -> Either String Eigen
hmatrixEigen m = do
  checkSymmetric m
  let (values, vectors) = LA.eigSH (LA.trustSym (LA.fromLists m))
  pure (sortEigen (LA.toList values) (LA.toLists (LA.tr vectors)))

-- | 固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の要素が正になるようにそろえる。
normalizeSigns :: [[Double]] -> [[Double]]
normalizeSigns = map normalizeSign
 where
  normalizeSign row = if largest row < 0.0 then map negate row else row
  -- 絶対値が最大の要素。同じなら前の要素を残すように、厳密な > で畳む。
  largest = foldl' (\acc value -> if abs value > abs acc then value else acc) 0.0

-- 主成分分析 -----------------------------------------------------------------

-- | 学習した主成分分析。
data Pca = Pca
  { pcaMean :: [Double]
  , pcaComponents :: [[Double]]
  , pcaExplainedVariance :: [Double]
  , pcaExplainedVarianceRatio :: [Double]
  }
  deriving (Eq, Show)

-- | 分散共分散行列を自作の Jacobi 法で固有値分解し、寄与率の大きい順に主成分を求める。
fit :: [[Double]] -> Int -> Either String Pca
fit = fitWith jacobiEigen

-- | 固有値分解のしかたを差し替えられる 'fit'。自作と hmatrix を同じ手順で比べるために使う。
fitWith :: ([[Double]] -> Either String Eigen) -> [[Double]] -> Int -> Either String Pca
fitWith decompose m nComponents = do
  cov <- covarianceMatrix m
  Eigen values vectors <- decompose cov
  checkNComponents nComponents (length values)
  let total = foldl' (+) 0.0 values
      variances = take nComponents values
  pure
    Pca
      { pcaMean = matrixColumnMeans m
      , pcaComponents = normalizeSigns (take nComponents vectors)
      , pcaExplainedVariance = variances
      , pcaExplainedVarianceRatio = map (/ total) variances
      }

-- | 平均を引いてから、データを主成分の向きに射影する。
transform :: Pca -> [[Double]] -> [[Double]]
transform model m = map (\row -> map (dot row) (pcaComponents model)) (center m (pcaMean model))

-- | 自作と hmatrix の、寄与率と主成分の差の絶対値の最大。
hmatrixGaps :: [[Double]] -> Int -> Either String (Double, Double)
hmatrixGaps m nComponents = do
  mine <- fit m nComponents
  theirs <- fitWith hmatrixEigen m nComponents
  pure
    ( maxGap [pcaExplainedVarianceRatio mine] [pcaExplainedVarianceRatio theirs]
    , maxGap (pcaComponents mine) (pcaComponents theirs)
    )

-- 主成分の数と解釈 -----------------------------------------------------------

-- | 累積寄与率がしきい値に届くまでの主成分の数。届かなければすべての主成分の数。
componentsNeeded :: [Double] -> Double -> Int
componentsNeeded ratios threshold =
  case [index | (index, cumulative) <- zip [1 ..] (scanl1 (+) ratios), cumulative >= threshold] of
    found : _ -> found
    [] -> length ratios

{- | 主成分の係数の絶対値が大きい順に、列名と係数を k 個返す。

'sortBy' は安定なので、絶対値が同じなら元の列の順が残る。
-}
topLoadings :: [Double] -> [Text] -> Int -> [(Text, Double)]
topLoadings component columns k =
  take k (sortBy (comparing (Down . abs . snd)) (zip columns component))

-- ボストンの住宅価格 ---------------------------------------------------------

-- | CRIME をダミー変数にし、欠損値を列の平均値で補完してから、すべての列を標準化する。
standardizeTable :: Table -> Either String ([Text], [M.Map Text Double])
standardizeTable table = do
  crimes <- traverse (`text` categoryColumn) (tableRows table)
  encoded <- encode table categoryColumn (categories crimes)
  means <- columnMeans (tableRows encoded) (tableColumns encoded)
  filled <- fillMissing (tableRows encoded) (tableColumns encoded) means
  std <- fitStandardizer filled (tableColumns encoded)
  pure (tableColumns encoded, standardizeAll std filled)

{- | 特徴量のリストを、1 件を 1 行とする行列にする。

'M.Map' はキーの順で並ぶので、列の順は必ずリストで持ち回る。中身は第 9 章の
'toRows' をそのまま呼ぶだけで、この章での呼び名を与えている。
-}
toMatrix :: [M.Map Text Double] -> [Text] -> Either String [[Double]]
toMatrix = toRows

-- | CSV を読み込んで前処理する。
loadBoston :: FilePath -> IO (Either String ([Text], [M.Map Text Double]))
loadBoston file = do
  contents <- BL.readFile file
  pure (loadTable contents >>= standardizeTable)

-- | ボストンの住宅価格の 15 列を主成分分析し、寄与率と主成分の意味をまとめる。
report :: BL.ByteString -> Either String String
report contents = do
  table <- loadTable contents
  (columns, x) <- standardizeTable table
  m <- toMatrix x columns
  model <- fit m (length columns)
  let ratios = pcaExplainedVarianceRatio model
      needed = componentsNeeded ratios varianceThreshold
      cumulative = foldl' (+) 0.0 (take needed ratios)
  (ratioGap, componentGap) <- hmatrixGaps m (length columns)
  explained <- traverse (explain columns (pcaComponents model)) [0 .. componentsToExplain - 1]
  pure $
    unlines $
      [ printf "データ件数: %d, 列数: %d" (length m) (length columns)
      , "寄与率: " <> formatRatios (take needed ratios)
      , printf "累積寄与率が %s に届く主成分の数: %d（累積寄与率 %s）" (formatNumber 1 varianceThreshold) needed (formatNumber 4 cumulative)
      ]
        <> explained
        <> [printf "hmatrix の eigSH との差: 寄与率 %s, 主成分 %s" (exponent' ratioGap) (exponent' componentGap)]

-- | 実データで主成分分析を試す。
run :: IO (Either String String)
run = do
  file <- Dataset.path "Boston.csv"
  report <$> BL.readFile file

-- 補助 -----------------------------------------------------------------------

-- | 主成分の意味を、係数の大きい列から読む。
explain :: [Text] -> [[Double]] -> Int -> Either String String
explain columns components index =
  case drop index components of
    component : _ ->
      Right $
        printf "第 %d 主成分で影響の大きい列: %s" (index + 1) (formatLoadings (topLoadings component columns topK))
    [] -> Left (printf "第 %d 主成分がありません" (index + 1))

-- | 寄与率を PC1 から順に並べる。
formatRatios :: [Double] -> String
formatRatios ratios =
  intercalate' [printf "PC%d %s" (index :: Int) (formatNumber 4 ratio) | (index, ratio) <- zip [1 ..] ratios]

-- | 列名と係数を並べる。
formatLoadings :: [(Text, Double)] -> String
formatLoadings loadings =
  intercalate' [T.unpack column <> " " <> formatNumber 3 value | (column, value) <- loadings]

-- | 読点で区切って並べる。
intercalate' :: [String] -> String
intercalate' [] = ""
intercalate' (x : xs) = foldl' (\acc value -> acc <> ", " <> value) x xs

-- | 小数点以下の桁数を指定して整える。
formatNumber :: Int -> Double -> String
formatNumber digits = printf ("%." <> show digits <> "f")

-- | 差の大きさを指数で表す。
exponent' :: Double -> String
exponent' = printf "%.2e"

-- | 正方かつ対称かを確かめる。
checkSymmetric :: [[Double]] -> Either String ()
checkSymmetric m
  | any ((/= size) . length) m = Left (printf "正方行列ではありません: %d 行" size)
  | m /= transpose m = Left "固有値分解できません（対称行列ではありません）"
  | otherwise = Right ()
 where
  size = length m

-- | 主成分の数が 1 以上、列の数以下かを確かめる。
checkNComponents :: Int -> Int -> Either String ()
checkNComponents nComponents size
  | nComponents >= 1 && nComponents <= size = Right ()
  | otherwise = Left (printf "主成分の数は 1 以上 %d 以下にしてください: %d" size nComponents)

-- | 固有値の大きい順に並べる。'sortBy' は安定なので、同じ固有値なら元の順が残る。
sortEigen :: [Double] -> [[Double]] -> Eigen
sortEigen values vectors = Eigen (map fst sorted) (map snd sorted)
 where
  sorted = sortBy (comparing (Down . fst)) (zip values vectors)

-- | 行列の 1 巡。非対角成分が十分小さくなったら止める。
sweep :: Int -> Int -> M.Map (Int, Int) Double -> M.Map (Int, Int) Double -> (M.Map (Int, Int) Double, M.Map (Int, Int) Double)
sweep 0 _ a v = (a, v)
sweep remaining size a v
  | off a < offTolerance = (a, v)
  | otherwise = sweep (remaining - 1) size a' v'
 where
  (a', v') = foldl' (rotate size) (a, v) [(p, q) | p <- [0 .. size - 2], q <- [p + 1 .. size - 1]]

-- | 非対角成分の 2 乗和。
off :: M.Map (Int, Int) Double -> Double
off a = foldl' (+) 0.0 [value * value | ((i, j), value) <- M.toList a, i /= j]

{- | (p, q) 成分を 0 にする 2 次元の回転を、行列と固有ベクトルの両方に掛ける。

@tan@ は絶対値の小さいほうの根を選ぶ。素直に解の公式を書くと、@theta@ が
大きいときに近い数どうしの引き算になって桁落ちする。
-}
rotate :: Int -> (M.Map (Int, Int) Double, M.Map (Int, Int) Double) -> (Int, Int) -> (M.Map (Int, Int) Double, M.Map (Int, Int) Double)
rotate size (a, v) (p, q)
  | apq == 0.0 = (a, v)
  | otherwise = (M.union (M.fromList updatesA) a, M.union (M.fromList updatesV) v)
 where
  apq = at a (p, q)
  app = at a (p, p)
  aqq = at a (q, q)
  theta = (aqq - app) / (2.0 * apq)
  sign = if theta >= 0.0 then 1.0 else -1.0
  tangent = sign / (abs theta + sqrt (theta * theta + 1.0))
  cosine = 1.0 / sqrt (tangent * tangent + 1.0)
  sine = tangent * cosine
  others = [k | k <- [0 .. size - 1], k /= p, k /= q]
  updatesA =
    [((p, p), app - tangent * apq), ((q, q), aqq + tangent * apq), ((p, q), 0.0), ((q, p), 0.0)]
      <> concat
        [ [((k, p), kp), ((p, k), kp), ((k, q), kq), ((q, k), kq)]
        | k <- others
        , let kp = cosine * at a (k, p) - sine * at a (k, q)
        , let kq = sine * at a (k, p) + cosine * at a (k, q)
        ]
  updatesV =
    concat
      [ [((k, p), cosine * at v (k, p) - sine * at v (k, q)), ((k, q), sine * at v (k, p) + cosine * at v (k, q))]
      | k <- [0 .. size - 1]
      ]

-- | 行列の成分を読む。無ければ 0 とみなす。
at :: M.Map (Int, Int) Double -> (Int, Int) -> Double
at = flip (M.findWithDefault 0.0)

-- | 内積。'sum' は右結合になりうるので 'foldl'' を使う。
dot :: [Double] -> [Double] -> Double
dot a b = foldl' (+) 0.0 (zipWith (*) a b)

-- | 2 つの行列の、要素ごとの差の絶対値の最大。
maxGap :: [[Double]] -> [[Double]] -> Double
maxGap left right =
  foldl' max 0.0 (concat (zipWith (zipWith (\x y -> abs (x - y))) left right))
