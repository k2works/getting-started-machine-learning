{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE ScopedTypeVariables #-}

{- | 第 9 章: 特徴量エンジニアリング。

ダミー変数・標準化・多項式特徴量・外れ値・表の結合。標準化は
@statistics@ パッケージの平均・分散と突き合わせる。

この章から Shift_JIS の CSV を読む。Haskell には文字コードを変換する
ライブラリが標準では無いように見えるが、@System.IO.mkTextEncoding@ が
iconv の名前をそのまま受け取るので、@CP932@ を指定すれば追加の依存なしに
読める（@text-icu@ のように C ライブラリを足す必要はない）。
-}
module GettingStartedMl.Chapter09 (
  -- * 列の名前
  targetColumn,
  categoryColumn,
  joinKey,
  columnsToExpand,
  squareTerms,

  -- * カテゴリ値をダミー変数にする
  categories,
  encode,

  -- * 標準化
  Standardizer (..),
  fitStandardizer,
  standardize,
  standardizeAll,
  toRows,
  statisticsMean,
  statisticsStdDevN,
  statisticsStdDevN1,

  -- * 多項式特徴量
  pairsWithReplacement,
  termName,
  expandedColumns,
  expand,
  selectColumns,

  -- * 外れ値
  quantile,
  iqrOutliers,
  removeTargetOutliers,

  -- * 区切り文字と文字コードを指定した読み込み・表の結合
  Encoding (..),
  readDecoded,
  parseTableWith,
  loadDelimited,
  joinWeather,
  meanCountByWeather,

  -- * 線形回帰と決定係数
  LinearModel (..),
  linearFit,
  linearPredict,
  rSquared,

  -- * ボストンの住宅価格
  BostonSplit (..),
  prepareBoston,
  scoreFeatureSet,
  featureSets,
  report,
  run,
) where

import Control.Exception (IOException, try)
import Data.Bifunctor (first)
import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as BL
import Data.Char (ord)
import qualified Data.Csv as Csv
import qualified Data.HashMap.Strict as HM
import Data.List (nub, sort, sortBy, transpose)
import qualified Data.Map.Strict as M
import Data.Ord (Down (..), comparing)
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import qualified Data.Vector as V
import qualified Data.Vector.Unboxed as U
import Data.Word (Word8)
import GettingStartedMl.Chapter02 (Split (..), Table (..), columnMeans, fillMissing, loadTable, splitFeaturesAndTarget, splitTrainTest)
import qualified GettingStartedMl.Chapter07 as C7
import GettingStartedMl.Csv (number, text)
import qualified GettingStartedMl.Dataset as Dataset
import qualified Statistics.Sample as Stat
import System.FilePath ((</>))
import System.IO (IOMode (ReadMode), TextEncoding, hGetContents', hSetEncoding, mkTextEncoding, withFile)
import Text.Printf (printf)

-- | ボストンの住宅価格のデータの正解の列。
targetColumn :: Text
targetColumn = "PRICE"

-- | カテゴリ値の列。
categoryColumn :: Text
categoryColumn = "CRIME"

-- | 表を結合する鍵。
joinKey :: Text
joinKey = "weather_id"

-- | 多項式特徴量を作る元の列。
columnsToExpand :: [Text]
columnsToExpand = ["RM", "LSTAT", "PTRATIO"]

-- | 2 乗の項。
squareTerms :: [Text]
squareTerms = map (\c -> termName (c, c)) columnsToExpand

-- | 四分位範囲の何倍を外れ値とみなすか。
defaultK :: Double
defaultK = 1.5

-- | 表示が -0.00 にならないように、ごく小さい値は 0 とみなす。
zeroTolerance :: Double
zeroTolerance = 1.0e-9

-- カテゴリ値をダミー変数にする -----------------------------------------------

{- | 空欄を除いたカテゴリを辞書順に並べ、先頭を除いて返す。

pandas の @get_dummies(drop_first=True)@ と同じで、3 つのカテゴリなら 2 列で足りる。
-}
categories :: [Text] -> [Text]
categories values = drop 1 (sort (nub (filter (not . T.null . T.strip) values)))

{- | 列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。

値が一致すれば @"1"@、それ以外は @"0"@。セルを文字列のままにしておくと、
第 2 章の 'splitFeaturesAndTarget'・'columnMeans'・'fillMissing' を
ダミー変数の列にもそのまま使える。
-}
encode :: Table -> Text -> [Text] -> Either String Table
encode table col cats = do
  rows <- traverse encodeRow (tableRows table)
  pure
    Table
      { tableColumns = filter (/= col) (tableColumns table) <> dummyColumns
      , tableRows = rows
      }
 where
  dummyColumns = [col <> "_" <> c | c <- cats]

  encodeRow row = do
    value <- text row col
    let dropped = HM.delete (TE.encodeUtf8 col) row
        added = [(TE.encodeUtf8 name, if c == value then "1" else "0") | (c, name) <- zip cats dummyColumns]
    pure (foldr (uncurry HM.insert) dropped added)

-- 標準化 ---------------------------------------------------------------------

-- | 列ごとの平均と標準偏差を覚える。
data Standardizer = Standardizer
  { standardizerColumns :: [Text]
  , standardizerMeans :: M.Map Text Double
  , standardizerStds :: M.Map Text Double
  }
  deriving (Eq, Show)

{- | 列ごとの平均と、件数（n）で割る標準偏差を求める。

すべて同じ値の列は標準偏差を 1 にして、標準化した値が 0 になるようにする。
-}
fitStandardizer :: [M.Map Text Double] -> [Text] -> Either String Standardizer
fitStandardizer [] _ = Left "特徴量が 1 件もありません"
fitStandardizer x cols = do
  stats <- traverse stat cols
  pure
    Standardizer
      { standardizerColumns = cols
      , standardizerMeans = M.fromList [(c, m) | (c, m, _) <- stats]
      , standardizerStds = M.fromList [(c, s) | (c, _, s) <- stats]
      }
 where
  stat col = do
    values <- traverse (featureValue col) x
    let n = fromIntegral (length values)
        mean = sum values / n
        variance = sum [(v - mean) * (v - mean) | v <- values] / n
        std = sqrt variance
    pure (col, mean, if std == 0.0 then 1.0 else std)

-- | 1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。
standardize :: Standardizer -> M.Map Text Double -> M.Map Text Double
standardize std = M.mapWithKey scaleOne
 where
  scaleOne col value =
    case (M.lookup col (standardizerMeans std), M.lookup col (standardizerStds std)) of
      (Just mean, Just deviation) -> (value - mean) / deviation
      _ -> value

-- | 特徴量のリストを標準化する。
standardizeAll :: Standardizer -> [M.Map Text Double] -> [M.Map Text Double]
standardizeAll std = map (standardize std)

-- | 特徴量を、列の順に並べた数値の行にする。
toRows :: [M.Map Text Double] -> [Text] -> Either String [[Double]]
toRows x cols = traverse (\features -> traverse (`featureValue` features) cols) x

{- | @statistics@ パッケージの平均。

自作の平均と突き合わせるために使う。
-}
statisticsMean :: [Double] -> Double
statisticsMean = Stat.mean . U.fromList

{- | @statistics@ の 'Stat.variance' から求めた標準偏差。件数（n）で割る。

@statistics@ の名前は紛らわしい。'Stat.variance' は最尤推定（n で割る）で、
'Stat.stdDev' は不偏分散（n−1 で割る）の平方根である。
-}
statisticsStdDevN :: [Double] -> Double
statisticsStdDevN = sqrt . Stat.variance . U.fromList

-- | @statistics@ の 'Stat.stdDev'。標本標準偏差（n−1 で割る）。
statisticsStdDevN1 :: [Double] -> Double
statisticsStdDevN1 = Stat.stdDev . U.fromList

-- 多項式特徴量 ---------------------------------------------------------------

-- | 重複を許して 2 つの列を選ぶ組を、scikit-learn の @PolynomialFeatures@ と同じ順に並べる。
pairsWithReplacement :: [Text] -> [(Text, Text)]
pairsWithReplacement cols =
  [(left, right) | (i, left) <- indexed, (j, right) <- indexed, j >= i]
 where
  indexed = zip [0 :: Int ..] cols

-- | 項の名前。scikit-learn の @get_feature_names_out@ と同じ形（@RM^2@・@RM LSTAT@）にする。
termName :: (Text, Text) -> Text
termName (left, right)
  | left == right = left <> "^2"
  | otherwise = left <> " " <> right

-- | 元の列の後ろに、2 乗の項と交互作用の項の列を並べた列名を返す。
expandedColumns :: [Text] -> [Text]
expandedColumns cols = cols <> map termName (pairsWithReplacement cols)

-- | 指定した列と、その 2 乗の項・交互作用の項だけを持つ特徴量にする。
expand :: [M.Map Text Double] -> [Text] -> Either String [M.Map Text Double]
expand x cols = traverse expandOne x
 where
  pairs = pairsWithReplacement cols

  expandOne features = do
    kept <- traverse (\c -> (,) c <$> featureValue c features) cols
    terms <- traverse (term features) pairs
    pure (M.fromList (kept <> terms))

  term features pair@(left, right) = do
    l <- featureValue left features
    r <- featureValue right features
    pure (termName pair, l * r)

-- | 指定した列だけを選ぶ。
selectColumns :: [M.Map Text Double] -> [Text] -> Either String [M.Map Text Double]
selectColumns x cols = traverse pick x
 where
  pick features = M.fromList <$> traverse (\c -> (,) c <$> featureValue c features) cols

-- 外れ値 ---------------------------------------------------------------------

{- | 分位数を求める。

位置が値の間にあれば前後の値から線形補間する（pandas の @quantile@ の既定と同じ）。
-}
quantile :: [Double] -> Double -> Either String Double
quantile [] _ = Left "値が 1 件もありません"
quantile values q =
  Right (atLower + (sorted !! upper - atLower) * (position - fromIntegral lower))
 where
  sorted = sort values
  position = fromIntegral (length sorted - 1) * q
  lower = floor position
  upper = ceiling position
  atLower = sorted !! lower

-- | 第 1 四分位数から IQR の k 倍より小さい値と、第 3 四分位数から k 倍より大きい値を外れ値とする。
iqrOutliers :: [Double] -> Double -> Either String [Bool]
iqrOutliers values k = do
  q1 <- quantile values 0.25
  q3 <- quantile values 0.75
  let iqr = q3 - q1
  pure [v < q1 - k * iqr || v > q3 + k * iqr | v <- values]

{- | 訓練データから、正解の値が外れ値の行を取り除く。

テストデータは「本番で来るデータ」の代わりなので、外れ値を含んでいてもそのまま残す。
-}
removeTargetOutliers :: BostonSplit -> Either String BostonSplit
removeTargetOutliers split = do
  outliers <- iqrOutliers (bostonTTrain split) defaultK
  let kept = [(features, value) | (features, value, False) <- zip3 (bostonXTrain split) (bostonTTrain split) outliers]
  pure split {bostonXTrain = map fst kept, bostonTTrain = map snd kept}

-- 区切り文字と文字コードを指定した読み込み -----------------------------------

{- | 読み込むときの文字コード。

@Cp932@ は Shift_JIS の Windows 拡張で、iconv の名前をそのまま使う。
-}
data Encoding
  = Utf8
  | Cp932
  deriving (Eq, Show)

-- | iconv の名前。
encodingName :: Encoding -> String
encodingName Utf8 = "UTF-8"
encodingName Cp932 = "CP932"

{- | 文字コードを指定してファイルを読む。

取り違えると 'Left' になる。Shift_JIS のファイルを @UTF-8@ として読むと
「復号できないバイト列がある」という 'IOException' が上がるので、それを
'Either' に移し替える。逆向き（UTF-8 のファイルを @CP932@ として読む）は
バイトの並びとして正しく解釈できてしまうので、例外にはならず文字化けする。
-}
readDecoded :: FilePath -> Encoding -> IO (Either String Text)
readDecoded file enc = do
  encoder <- mkTextEncoding (encodingName enc)
  result <- try (readAll encoder)
  pure (first describe result)
 where
  readAll :: TextEncoding -> IO Text
  readAll encoder = withFile file ReadMode $ \handle -> do
    hSetEncoding handle encoder
    T.pack <$> hGetContents' handle

  describe :: IOException -> String
  describe err = encodingName enc <> " として読めません: " <> show err

{- | 区切り文字を指定して、1 行目を列名にした表にする。

第 2 章の 'GettingStartedMl.Csv.parseTable' はカンマ区切りに決め打ちなので、
区切り文字を選べる読み方をこの章で足す。
-}
parseTableWith :: Char -> Text -> Either String Table
parseTableWith separator contents =
  case Csv.decodeByNameWith options (BL.fromStrict (TE.encodeUtf8 (T.dropWhile (== '\65279') contents))) of
    Left err -> Left err
    Right (header, rows) ->
      Right
        Table
          { tableColumns = map TE.decodeUtf8Lenient (V.toList header)
          , tableRows = filter (not . isBlank) (V.toList rows)
          }
 where
  options = Csv.defaultDecodeOptions {Csv.decDelimiter = fromIntegral (ord separator) :: Word8}

  isBlank row = all (BS.null . BS.filter (\c -> c /= 32 && c /= 9 && c /= 13)) (HM.elems row)

-- | 文字コードと区切り文字を指定して読み込み、1 行目を列名にする。
loadDelimited :: FilePath -> Encoding -> Char -> IO (Either String Table)
loadDelimited file enc separator = do
  contents <- readDecoded file enc
  pure (contents >>= parseTableWith separator)

{- | 天気 ID をキーにした表を引いて、天気の列を加える（内部結合）。

天気の表に無い ID の行は残さない。'M.fromList' はキーが重複すると静かに
上書きするので、件数を比べて一意でないことに気付けるようにする。
-}
joinWeather :: Table -> Table -> Either String Table
joinWeather bike weather = do
  keys <- traverse (`text` joinKey) (tableRows weather)
  let byId = M.fromList (zip keys (tableRows weather))
      added = filter (/= joinKey) (tableColumns weather)
  if M.size byId /= length (tableRows weather)
    then Left (T.unpack joinKey <> " が一意ではありません")
    else do
      rows <- traverse (joinRow byId added) (tableRows bike)
      pure
        Table
          { tableColumns = tableColumns bike <> added
          , tableRows = concat rows
          }
 where
  joinRow byId added row = do
    key <- text row joinKey
    case M.lookup key byId of
      Nothing -> Right []
      Just found -> do
        cells <- traverse (\c -> (,) (TE.encodeUtf8 c) . TE.encodeUtf8 <$> text found c) added
        Right [foldr (uncurry HM.insert) row cells]

-- | 天気ごとの平均利用者数を、多い順に並べて返す。
meanCountByWeather :: Table -> Either String [(Text, Double)]
meanCountByWeather joined = do
  pairs <- traverse pick (tableRows joined)
  let grouped = M.fromListWith (<>) [(weather, [count]) | (weather, count) <- pairs]
      means = [(weather, sum counts / fromIntegral (length counts)) | (weather, counts) <- M.toList grouped]
  pure (sortBy (comparing (Down . snd)) means)
 where
  pick row = do
    weather <- text row "weather"
    count <- number row "cnt"
    pure (weather, fromIntegral count :: Double)

-- 線形回帰と決定係数 ---------------------------------------------------------

-- | 切片と、列ごとの重み。
data LinearModel = LinearModel
  { linearIntercept :: Double
  , linearWeights :: [Double]
  }
  deriving (Eq, Show)

{- | 先頭に 1 の列を加えた計画行列 X で、正規方程式 XᵀX β = Xᵀt を解く。

解くのは第 7 章で自作したガウス・ジョルダン法（'C7.solveLinearSystem'）である。
第 7 章では hmatrix と突き合わせたが、**この環境の hmatrix は LAPACK との
整数幅が合わず `DGESV parameter number 1 had an illegal value` で解けない**
ので、この章では自作の解法だけを使う。
-}
linearFit :: [[Double]] -> [Double] -> Either String LinearModel
linearFit [] _ = Left "特徴量が 1 件もありません"
linearFit rows t =
  case first (const notIndependent) (C7.solveLinearSystem normal rhs) of
    Left err -> Left err
    Right beta -> case beta of
      (intercept : weights)
        | all sane beta -> Right (LinearModel intercept weights)
      _ -> Left notIndependent
 where
  design = map (1.0 :) rows
  transposed = transpose design
  normal = [[sum (zipWith (*) r c) | c <- transposed] | r <- transposed]
  rhs = [sum (zipWith (*) r t) | r <- transposed]
  sane v = not (isNaN v || isInfinite v)
  notIndependent = "特徴量の列が互いに独立でないため、正規方程式を解けません"

-- | 行ごとに予測する。
linearPredict :: LinearModel -> [[Double]] -> [Double]
linearPredict model rows =
  [linearIntercept model + sum (zipWith (*) (linearWeights model) row) | row <- rows]

-- | 決定係数。1 から、残差の 2 乗和を平均との差の 2 乗和で割った値を引く。
rSquared :: [Double] -> [Double] -> Either String Double
rSquared [] _ = Left "正解の値がありません"
rSquared actual predicted = Right (1.0 - residual / total)
 where
  mean = sum actual / fromIntegral (length actual)
  residual = sum [(a - p) * (a - p) | (a, p) <- zip actual predicted]
  total = sum [(a - mean) * (a - mean) | a <- actual]

-- ボストンの住宅価格 ---------------------------------------------------------

-- | 列名つきの分割。正解の値は数値なので、第 2 章の 'Split' とは別に持つ。
data BostonSplit = BostonSplit
  { bostonColumns :: [Text]
  , bostonXTrain :: [M.Map Text Double]
  , bostonXTest :: [M.Map Text Double]
  , bostonTTrain :: [Double]
  , bostonTTest :: [Double]
  }
  deriving (Eq, Show)

-- | CRIME をダミー変数にし、分割してから訓練データの平均値で両方の欠損値を補完する。
prepareBoston :: BL.ByteString -> Double -> Int -> Either String BostonSplit
prepareBoston contents testSize seed = do
  table <- loadTable contents
  crimes <- traverse (`text` categoryColumn) (tableRows table)
  encoded <- encode table categoryColumn (categories crimes)
  (cols, rows, labels) <- splitFeaturesAndTarget encoded targetColumn
  split <- splitTrainTest rows labels testSize seed
  means <- columnMeans (xTrain split) cols
  xs <- fillMissing (xTrain split) cols means
  ys <- fillMissing (xTest split) cols means
  trainTargets <- traverse readDouble (tTrain split)
  testTargets <- traverse readDouble (tTest split)
  pure
    BostonSplit
      { bostonColumns = cols
      , bostonXTrain = xs
      , bostonXTest = ys
      , bostonTTrain = trainTargets
      , bostonTTest = testTargets
      }

{- | 多項式特徴量を作って @terms@ の項を選び、標準化してから線形回帰で学習し、決定係数を求める。

平均と標準偏差は訓練データだけで求め、テストデータにも同じ値を使う。
-}
scoreFeatureSet :: BostonSplit -> [Text] -> [Text] -> Either String (Double, Double)
scoreFeatureSet split cols terms = do
  train <- expand (bostonXTrain split) cols >>= (`selectColumns` terms)
  test <- expand (bostonXTest split) cols >>= (`selectColumns` terms)
  std <- fitStandardizer train terms
  trainRows <- toRows (standardizeAll std train) terms
  testRows <- toRows (standardizeAll std test) terms
  model <- linearFit trainRows (bostonTTrain split)
  trainScore <- rSquared (bostonTTrain split) (linearPredict model trainRows)
  testScore <- rSquared (bostonTTest split) (linearPredict model testRows)
  pure (trainScore, testScore)

-- | 特徴量の組の名前と、使う項。表示する順に並べる。
featureSets :: [(Text, [Text])]
featureSets =
  [ ("元の特徴量", columnsToExpand)
  , ("2 乗の項を追加", columnsToExpand <> squareTerms)
  , ("交互作用の項も追加", expandedColumns columnsToExpand)
  ]

-- | ボストンの住宅価格と、天気を結合した自転車の利用者数から報告の文字列を作る。
report :: BL.ByteString -> Table -> Table -> Either String String
report boston bike weather = do
  split <- prepareBoston boston 0.3 0
  standardized <- fitStandardizer (bostonXTrain split) (bostonColumns split)
  check <- fitStandardizer (standardizeAll standardized (bostonXTrain split)) (bostonColumns split)
  rm <- lookupColumn (standardizerMeans check) "RM"
  rmStd <- lookupColumn (standardizerStds check) "RM"
  scores <- traverse (\(name, terms) -> (,,) name (length terms) <$> scoreFeatureSet split columnsToExpand terms) featureSets
  outliers <- iqrOutliers (bostonTTrain split) defaultK
  withoutOutliers <- removeTargetOutliers split >>= \s -> scoreFeatureSet s columnsToExpand (columnsToExpand <> squareTerms)
  joined <- joinWeather bike weather
  means <- meanCountByWeather joined
  pure $
    unlines $
      [ printf "訓練データ: %d 件, テストデータ: %d 件" (length (bostonXTrain split)) (length (bostonXTest split))
      , "特徴量の列: " <> T.unpack (T.intercalate ", " (bostonColumns split))
      , printf "標準化した訓練データの RM: 平均 %s, 標準偏差 %s" (formatNumber 2 rm) (formatNumber 2 rmStd)
      , "決定係数:"
      ]
        <> [printf "  %s（%d 列）: %s" (T.unpack name) n (formatScores score) | (name, n, score) <- scores]
        <> [ printf "訓練データの PRICE の外れ値: %d 件" (length (filter id outliers))
           , "  外れ値を除いて 2 乗の項を追加: " <> formatScores withoutOutliers
           , "天気ごとの平均利用者数: "
               <> T.unpack (T.intercalate ", " [weather' <> "=" <> T.pack (formatNumber 1 mean) | (weather', mean) <- means])
           ]

-- | 実データで特徴量エンジニアリングを試し、自転車の利用者数に天気を結合して集計する。
run :: IO (Either String String)
run = do
  directory <- Dataset.dir
  boston <- BL.readFile (directory </> "Boston.csv")
  bike <- loadDelimited (directory </> "bike.tsv") Utf8 '\t'
  weather <- loadDelimited (directory </> "weather.csv") Cp932 ','
  pure (do bike' <- bike; weather' <- weather; report boston bike' weather')

-- 補助 -----------------------------------------------------------------------

-- | 列の値を読む。列が無ければ失敗する。
featureValue :: Text -> M.Map Text Double -> Either String Double
featureValue col features =
  case M.lookup col features of
    Just value -> Right value
    Nothing -> Left ("列がありません: " <> T.unpack col)

-- | 覚えた値を読む。
lookupColumn :: M.Map Text Double -> Text -> Either String Double
lookupColumn table col =
  case M.lookup col table of
    Just value -> Right value
    Nothing -> Left ("列がありません: " <> T.unpack col)

-- | 文字列を小数として読む。
readDouble :: Text -> Either String Double
readDouble cell =
  case reads (T.unpack (T.strip cell)) of
    [(value, rest)] | all (== ' ') rest -> Right value
    _ -> Left ("数値として読めません: " <> T.unpack cell)

-- | 訓練データとテストデータの決定係数を並べる。
formatScores :: (Double, Double) -> String
formatScores (train, test) = printf "訓練 %s, テスト %s" (formatNumber 4 train) (formatNumber 4 test)

-- | 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす。
formatNumber :: Int -> Double -> String
formatNumber digits value =
  printf ("%." <> show digits <> "f") (if abs value < zeroTolerance then 0.0 else value)
