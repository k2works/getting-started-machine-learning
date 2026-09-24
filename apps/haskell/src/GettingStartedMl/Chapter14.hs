{-# LANGUAGE OverloadedStrings #-}

{- | 第 14 章: K-means によるクラスタリング。

点は数値のリスト、点の集まりはそのリストのリストで表す。中心が変わらなくなるまで
「割り当て」と「中心の更新」を繰り返す。

Haskell には K-means のライブラリが無いので、この章は自作が最終実装になる。
突き合わせる相手がいないぶん、「クラスタ数を増やせば SSE は減る」「初期中心で
結果が変わる（局所解）」といった K-means そのものの性質をテストで示す。

初期中心は 'GettingStartedMl.Random'（@java.util.Random@ と同じ線形合同法）で
選ぶので、ほかの言語版と同じ点から始まる。標準化は第 9 章の
'fitStandardizer'・'standardizeAll' をそのまま使う。
-}
module GettingStartedMl.Chapter14 (
  -- * 設定
  defaultMaxIterations,
  defaultNInit,
  nonSpending,
  clusterCounts,
  nClusters,

  -- * 割り当てと中心の更新
  squaredDistance,
  assignClusters,
  updateCenters,
  sumOfSquaredErrors,

  -- * 繰り返し
  KMeans (..),
  fitCenters,

  -- * 初期中心
  chooseInitialCenters,
  best,
  fitWithRestarts,
  sseByClusterCount,

  -- * 卸売業者の顧客ごとの支出額
  spending,
  loadSpending,
  standardizePoints,
  ClusterSummary (..),
  summarizeClusters,
  roundHalfUp,
  report,
  run,
) where

import qualified Data.ByteString.Lazy as BL
import Data.List (sortBy)
import qualified Data.Map.Strict as M
import Data.Ord (Down (..), comparing)
import Data.Text (Text)
import qualified Data.Text as T
import GettingStartedMl.Chapter02 (Table (..), loadTable)
import GettingStartedMl.Chapter09 (fitStandardizer, standardizeAll, toRows)
import GettingStartedMl.Csv (number)
import qualified GettingStartedMl.Dataset as Dataset
import GettingStartedMl.Random (shuffle)
import Text.Printf (printf)

-- | 更新の回数の既定の上限（scikit-learn の KMeans と同じ）。
defaultMaxIterations :: Int
defaultMaxIterations = 300

-- | 初期中心を試す回数の既定値（scikit-learn の KMeans の n_init と同じ）。
defaultNInit :: Int
defaultNInit = 10

-- | 区分を表す番号で、支出額ではない列。
nonSpending :: [Text]
nonSpending = ["Channel", "Region"]

-- | エルボー法で試すクラスタ数。
clusterCounts :: [Int]
clusterCounts = [1 .. 10]

-- | 特徴を読むために選んだクラスタ数。
nClusters :: Int
nClusters = 5

-- | 初期中心の乱数のシード。
seedValue :: Int
seedValue = 0

-- 割り当てと中心の更新 -------------------------------------------------------

-- | 2 点間の距離の 2 乗。
squaredDistance :: [Double] -> [Double] -> Double
squaredDistance a b = foldl' (+) 0.0 (zipWith (\x y -> (x - y) * (x - y)) a b)

{- | 各点を、最も近い中心のクラスタ番号に割り当てる。

距離が同じなら先に並ぶ中心を選ぶように、厳密な < で畳む。
-}
assignClusters :: [[Double]] -> [[Double]] -> [Int]
assignClusters points centers = map nearest points
 where
  nearest point =
    fst $
      foldl'
        ( \acc@(_, bestDistance) (k, centerPoint) ->
            let distance = squaredDistance point centerPoint
             in if distance < bestDistance then (k, distance) else acc
        )
        (0, 1 / 0)
        (zip [0 ..] centers)

{- | クラスタごとに、割り当てられた点の平均を新しい中心にする。

点が 1 つも割り当てられなかったクラスタは、前の中心をそのまま残す。
-}
updateCenters :: [[Double]] -> [Int] -> [[Double]] -> [[Double]]
updateCenters points labels previous =
  [meanPoint (M.findWithDefault [] k members) centerPoint | (k, centerPoint) <- zip [0 ..] previous]
 where
  members = M.fromListWith (flip (<>)) [(label, [point]) | (point, label) <- zip points labels]
  meanPoint [] centerPoint = centerPoint
  meanPoint assigned _ = map (\column -> foldl' (+) 0.0 column / fromIntegral (length assigned)) (columnsOf assigned)
  columnsOf [] = []
  columnsOf rows@(row : _) = [[values !! i | values <- rows] | i <- [0 .. length row - 1]]

-- | 各点と、所属するクラスタの中心との距離の 2 乗の合計（誤差平方和）。
sumOfSquaredErrors :: [[Double]] -> [Int] -> [[Double]] -> Double
sumOfSquaredErrors points labels centers =
  foldl' (+) 0.0 [squaredDistance point (centerAt label) | (point, label) <- zip points labels]
 where
  table = M.fromList (zip [0 ..] centers)
  centerAt label = M.findWithDefault [] label table

-- 繰り返し -------------------------------------------------------------------

-- | クラスタリングの結果。
data KMeans = KMeans
  { kmeansLabels :: [Int]
  , kmeansCenters :: [[Double]]
  , kmeansSse :: Double
  }
  deriving (Eq, Show)

-- | 中心が変わらなくなるか、更新の回数が上限に達するまで、割り当てと中心の更新を繰り返す。
fitCenters :: [[Double]] -> [[Double]] -> Int -> KMeans
fitCenters points initialCenters maxIterations =
  KMeans
    { kmeansLabels = labels
    , kmeansCenters = centers
    , kmeansSse = sumOfSquaredErrors points labels centers
    }
 where
  centers = converge maxIterations initialCenters
  labels = assignClusters points centers
  converge 0 current = current
  converge remaining current =
    let next = updateCenters points (assignClusters points current) current
     in if next == current then current else converge (remaining - 1) next

-- 初期中心 -------------------------------------------------------------------

{- | シード付きの乱数で点を並べ替え、先頭から nClusters 個を初期中心にする。

'shuffle' は @java.util.Random@ と同じ線形合同法なので、ほかの言語版と同じ点を選ぶ。
-}
chooseInitialCenters :: [[Double]] -> Int -> Int -> Either String [[Double]]
chooseInitialCenters points k seed
  | k > length points =
      Left (printf "点は %d 件しかないので %d 個の中心を選べません" (length points) k)
  | otherwise = traverse pointAt (take k (shuffle [0 .. length points - 1] seed))
 where
  table = M.fromList (zip [0 ..] points)
  pointAt index =
    case M.lookup index table of
      Just point -> Right point
      Nothing -> Left (printf "点がありません: %d" index)

-- | 初期中心の候補ごとにクラスタリングし、SSE が最小の結果を返す。
best :: [[Double]] -> [[[Double]]] -> Either String KMeans
best points candidates =
  case map (\initial -> fitCenters points initial defaultMaxIterations) candidates of
    [] -> Left "初期中心の候補がありません"
    first : rest -> Right (foldl' smaller first rest)
 where
  smaller current result = if kmeansSse result < kmeansSse current then result else current

-- | シードを 1 ずつずらして初期中心を nInit 通り選び、SSE が最小の結果を返す。
fitWithRestarts :: [[Double]] -> Int -> Int -> Int -> Either String KMeans
fitWithRestarts points k seed nInit = do
  candidates <- traverse (chooseInitialCenters points k) [seed .. seed + nInit - 1]
  best points candidates

{- | クラスタ数ごとに、初期中心を nInit 通り試した最小の SSE を返す。

'M.Map' はキーの順で並ぶので、@(クラスタ数, SSE)@ の組のリストにする。
-}
sseByClusterCount :: [[Double]] -> [Int] -> Int -> Int -> Either String [(Int, Double)]
sseByClusterCount points counts seed nInit =
  traverse (\k -> (,) k . kmeansSse <$> fitWithRestarts points k seed nInit) counts

-- 卸売業者の顧客ごとの支出額 -------------------------------------------------

-- | Channel と Region を除いた支出額の列を読む。欠損値があれば失敗する。
spending :: Table -> Either String ([Text], [M.Map Text Double])
spending table = do
  rows <- traverse amounts (tableRows table)
  pure (columns, rows)
 where
  columns = filter (`notElem` nonSpending) (tableColumns table)
  amounts row = M.fromList <$> traverse (\column -> (,) column . fromIntegral <$> number row column) columns

-- | CSV を読み込んで支出額の列だけにする。
loadSpending :: FilePath -> IO (Either String ([Text], [M.Map Text Double]))
loadSpending file = do
  contents <- BL.readFile file
  pure (loadTable contents >>= spending)

-- | 第 9 章の標準化（件数で割る標準偏差）で列ごとにそろえ、1 件を 1 つの点にする。
standardizePoints :: [M.Map Text Double] -> [Text] -> Either String [[Double]]
standardizePoints x columns = do
  std <- fitStandardizer x columns
  toRows (standardizeAll std x) columns

-- | クラスタごとの件数と、元の単位での列ごとの平均。
data ClusterSummary = ClusterSummary
  { summaryCluster :: Int
  , summaryCount :: Int
  , summaryMeans :: [Double]
  }
  deriving (Eq, Show)

-- | クラスタごとの件数と平均を、件数の多い順に並べる。
summarizeClusters :: [M.Map Text Double] -> [Text] -> [Int] -> Either String [ClusterSummary]
summarizeClusters x columns labels = do
  summaries <- traverse summarize (M.toList members)
  pure (sortBy (comparing (\s -> (Down (summaryCount s), summaryCluster s))) summaries)
 where
  members = M.fromListWith (flip (<>)) [(label, [features]) | (features, label) <- zip x labels]
  summarize (cluster, rows) = do
    values <- toRows rows columns
    pure
      ClusterSummary
        { summaryCluster = cluster
        , summaryCount = length rows
        , summaryMeans = map (\column -> foldl' (+) 0.0 column / fromIntegral (length rows)) (columnsOf values)
        }
  columnsOf rows = [[values !! i | values <- rows] | i <- [0 .. length columns - 1]]

-- | 卸売業者の顧客を支出額で K-means にかけ、エルボー法の SSE とクラスタごとの特徴をまとめる。
report :: BL.ByteString -> Either String String
report contents = do
  table <- loadTable contents
  (columns, x) <- spending table
  points <- standardizePoints x columns
  sses <- sseByClusterCount points clusterCounts seedValue defaultNInit
  labels <- kmeansLabels <$> fitWithRestarts points nClusters seedValue defaultNInit
  summaries <- summarizeClusters x columns labels
  pure $
    unlines $
      [ printf "データ件数: %d（支出額 %d 列）" (length x) (length columns)
      , printf "クラスタ数ごとの SSE（初期中心 %d 通りの最小値）:" defaultNInit
      , "クラスタ数\tSSE"
      ]
        <> [printf "%d\t%s" k (formatNumber 2 sse) | (k, sse) <- sses]
        <> [ ""
           , printf "クラスタ数 %d のクラスタごとの件数と平均支出額:" nClusters
           , T.unpack (T.intercalate "\t" (["クラスタ", "件数"] <> columns))
           ]
        <> map formatSummary summaries

-- | 実データでクラスタリングを試す。
run :: IO (Either String String)
run = do
  file <- Dataset.path "Wholesale.csv"
  report <$> BL.readFile file

-- 補助 -----------------------------------------------------------------------

-- | クラスタ 1 つぶんの行。平均は整数に丸める。
formatSummary :: ClusterSummary -> String
formatSummary summary =
  T.unpack $
    T.intercalate "\t" $
      [T.pack (show (summaryCluster summary)), T.pack (show (summaryCount summary))]
        <> [T.pack (show (roundHalfUp mean)) | mean <- summaryMeans summary]

{- | 0.5 を 0 から遠いほうへ丸める。

Haskell の @round@ も @printf "%.0f"@ も、ちょうど 0.5 のときは偶数側に丸める
（銀行家の丸め）。Java 版・Elixir 版の丸めは 0.5 を切り上げるので、平均支出額に
ちょうど 34708.5 が出たところで 1 だけ食い違った。表示をそろえるために
「0.5 を足して切り下げる」ほうを使う。支出額は必ず 0 以上なのでこれで足りる。
-}
roundHalfUp :: Double -> Integer
roundHalfUp value = floor (value + 0.5)

-- | 小数点以下の桁数を指定して整える。
formatNumber :: Int -> Double -> String
formatNumber digits = printf ("%." <> show digits <> "f")
