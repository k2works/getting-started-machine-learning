{-# LANGUAGE OverloadedStrings #-}

{- | 第 11 章: 評価指標と交差検証。

混同行列・適合率・再現率・F 値・ROC 曲線と AUC・回帰の指標・K 分割交差検証を自作する。

**Haskell には評価指標のライブラリが無い**ので、この章で作るものがそのまま
最終実装になる（ADR 014）。突き合わせる相手がライブラリではないぶん、
自作の妥当性はテストで示し、数値はほかの言語版と照らす。

評価関数は「正解と予測を受け取って 1 つの数を返す関数」、分類器は
「訓練データを受け取って予測する関数を返す関数」。どちらもただの関数なので、
型クラスも存在型も要らない（第 10 章の 'Classifier' とは対照的である）。
-}
module GettingStartedMl.Chapter11 (
  -- * 設定
  survivedColumns,
  survivedLabel,
  treeDepth,
  nSplits,
  seed,

  -- * 混同行列
  ConfusionMatrix (..),
  confusionMatrix,
  requireSameSize,

  -- * 混同行列から求める指標
  ratio,
  precision,
  recall,
  f1Score,
  classificationMetric,

  -- * 正解と予測から直接求める指標
  meanSquaredError,
  mean,

  -- * ROC 曲線と AUC
  RocPoint (..),
  rocCurve,
  auc,

  -- * K 分割交差検証
  Fold (..),
  kFold,
  kFoldSequential,
  pick,
  crossValidate,

  -- * 分類器
  Trainer,
  treeTrainer,
  linearTrainer,

  -- * 実データ
  prepareSurvived,
  prepareCinema,
  evaluateSurvived,
  evaluateCinema,
  report,
  run,
) where

import qualified Data.ByteString.Lazy as BL
import Data.List (sortOn)
import qualified Data.Map.Strict as M
import Data.Maybe (fromMaybe)
import Data.Ord (Down (..))
import qualified Data.Set as Set
import Data.Text (Text)
import qualified Data.Text as T
import GettingStartedMl.Chapter01 (accuracy)
import qualified GettingStartedMl.Chapter02 as C2
import qualified GettingStartedMl.Chapter03 as C3
import qualified GettingStartedMl.Chapter07 as C7
import GettingStartedMl.Csv (Row, optionalNumber, text)
import qualified GettingStartedMl.Dataset as Dataset
import GettingStartedMl.Random (shuffle)
import Text.Printf (printf)

-- 設定 -----------------------------------------------------------------------

-- | Survived.csv の特徴量の列。
survivedColumns :: [Text]
survivedColumns = ["Pclass", "Age", "male"]

-- | 正例にするラベル（生存）。
survivedLabel :: Text
survivedLabel = "1"

-- | 決定木の深さ。
treeDepth :: Maybe Int
treeDepth = Just 2

-- | 分割の数。
nSplits :: Int
nSplits = 5

-- | 分割の乱数のシード。
seed :: Int
seed = 0

-- 混同行列 -------------------------------------------------------------------

{- | 正解と予測を突き合わせた 4 つの数。

@fn@ は Elixir では予約語で @cm.fn@ と書けなかったところだが、Haskell では
レコードの欄の名前にできる。ただし @fn@ 単独だと何の略か読めないので、
どの言語版でも通じる @cmFalseNegative@ の形にした。
-}
data ConfusionMatrix = ConfusionMatrix
  { cmTruePositive :: Int
  , cmFalsePositive :: Int
  , cmFalseNegative :: Int
  , cmTrueNegative :: Int
  }
  deriving (Eq, Show)

-- | 正解と予測の件数が同じでなければ失敗する。短いほうに黙って合わせない。
requireSameSize :: [a] -> [b] -> Either String ()
requireSameSize actual predicted
  | length actual /= length predicted =
      Left (printf "正解と予測の件数が違います: %d と %d" (length actual) (length predicted))
  | otherwise = Right ()

{- | 正解と予測を 1 件ずつ比べて数える。

@positive@ と等しいラベルを正例、それ以外をまとめて負例とする。
ラベルの型は「同じかどうかを比べられる」ことしか要らないので 'Eq' で書く。
-}
confusionMatrix :: (Eq a) => [a] -> [a] -> a -> Either String ConfusionMatrix
confusionMatrix actual predicted positive = do
  requireSameSize actual predicted
  pure (foldr count (ConfusionMatrix 0 0 0 0) (zip actual predicted))
 where
  count (a, p) cm =
    case (a == positive, p == positive) of
      (True, True) -> cm {cmTruePositive = cmTruePositive cm + 1}
      (False, True) -> cm {cmFalsePositive = cmFalsePositive cm + 1}
      (True, False) -> cm {cmFalseNegative = cmFalseNegative cm + 1}
      (False, False) -> cm {cmTrueNegative = cmTrueNegative cm + 1}

-- 混同行列から求める指標 -----------------------------------------------------

-- | 分母が 0 なら 0 を返す割り算。NaN にしない。
ratio :: Double -> Double -> Double
ratio _ 0.0 = 0.0
ratio numerator denominator = numerator / denominator

-- | 適合率。正例と予測したうち、本当に正例だった割合。
precision :: ConfusionMatrix -> Double
precision cm =
  ratio
    (fromIntegral (cmTruePositive cm))
    (fromIntegral (cmTruePositive cm + cmFalsePositive cm))

-- | 再現率。本当の正例のうち、正例と予測できた割合。
recall :: ConfusionMatrix -> Double
recall cm =
  ratio
    (fromIntegral (cmTruePositive cm))
    (fromIntegral (cmTruePositive cm + cmFalseNegative cm))

-- | F 値。適合率と再現率の調和平均。
f1Score :: ConfusionMatrix -> Double
f1Score cm = ratio (2.0 * p * r) (p + r)
 where
  p = precision cm
  r = recall cm

{- | 混同行列から求める指標を、正解と予測を受け取る評価関数に変える。

'precision' や 'f1Score' を渡し、正例を決めると、'crossValidate' にそのまま
渡せる形になる。関数を返す関数だが、Haskell では部分適用がそのままこの形なので
特別な記法は要らない。
-}
classificationMetric
  :: (Eq a) => (ConfusionMatrix -> Double) -> a -> [a] -> [a] -> Either String Double
classificationMetric score positive actual predicted =
  score <$> confusionMatrix actual predicted positive

-- 正解と予測から直接求める指標 -----------------------------------------------

{- | 平均二乗誤差（MSE）。誤差の 2 乗の平均。

正解率は第 1 章の 'accuracy' をそのまま使う。第 3 章で 'Eq' に広げてあるので、
ラベルが文字列でも派閥でも同じ関数で測れる。
-}
meanSquaredError :: [Double] -> [Double] -> Either String Double
meanSquaredError actual predicted = do
  requireSameSize actual predicted
  mean (zipWith (\a p -> (p - a) * (p - a)) actual predicted)

-- | 平均。1 つも無ければ失敗する。
mean :: [Double] -> Either String Double
mean [] = Left "平均を求める値が 1 つもありません"
mean values = Right (sum values / fromIntegral (length values))

-- ROC 曲線と AUC -------------------------------------------------------------

{- | ROC 曲線の 1 点。

閾値は「どれも正例と予測しない」点だけ無いので 'Maybe' で表す。ほかの言語版は
正の無限大を置いたが、Haskell では「値が無い」ことを型で書ける。
-}
data RocPoint = RocPoint
  { rocThreshold :: Maybe Double
  , rocFalsePositiveRate :: Double
  , rocTruePositiveRate :: Double
  }
  deriving (Eq, Show)

{- | スコア（正例らしさ）と正解（正例なら 'True'）から ROC 曲線を求める。

スコアの高いほうから閾値を下げていき、同じスコアは 1 つの点にまとめる。
先頭には「どれも正例と予測しない」点（偽陽性率も真陽性率も 0）を置く。
-}
rocCurve :: [Double] -> [Bool] -> Either String [RocPoint]
rocCurve scores labels = do
  requireSameSize scores labels
  let positives = length (filter id labels)
      negatives = length labels - positives
  if positives == 0 || negatives == 0
    then Left "正例と負例が両方ないと ROC 曲線を描けません"
    else Right (RocPoint Nothing 0.0 0.0 : points positives negatives)
 where
  -- 同じスコアのかたまりごとに、正例と負例の数を高いほうから足し込む。
  grouped =
    sortOn (Down . fst)
      . M.toList
      . M.fromListWith (<>)
      $ [(score, [label]) | (score, label) <- zip scores labels]

  points positives negatives =
    [ RocPoint
        (Just threshold)
        (fromIntegral falsePositive / fromIntegral negatives)
        (fromIntegral truePositive / fromIntegral positives)
    | (threshold, truePositive, falsePositive) <- scanned
    ]
   where
    scanned = drop 1 (scanl step (0.0, 0, 0) grouped)
    step (_, tp, fp) (threshold, group) =
      ( threshold
      , tp + length (filter id group)
      , fp + length (filter not group)
      )

-- | ROC 曲線の下の面積（AUC）を台形則で求める。
auc :: [RocPoint] -> Double
auc curve =
  sum
    [ (rocFalsePositiveRate right - rocFalsePositiveRate left)
        * (rocTruePositiveRate left + rocTruePositiveRate right)
        / 2.0
    | (left, right) <- zip curve (drop 1 curve)
    ]

-- K 分割交差検証 -------------------------------------------------------------

-- | 1 つの分割。訓練データとテストデータの「行の位置」を持つ。
data Fold = Fold
  { foldTrain :: [Int]
  , foldTest :: [Int]
  }
  deriving (Eq, Show)

{- | シード付きの乱数で行の位置を並べ替え、@splits@ 個のテストデータに分ける。

件数が割り切れないときは、余りを先頭の分割から 1 件ずつ配る。並べ替えは
第 2 章の 'shuffle'（@java.util.Random@ と同じ線形合同法）なので、
ほかの言語版と同じ分け方になる。
-}
kFold :: Int -> Int -> Int -> Either String [Fold]
kFold nSamples splits s = do
  checkSplits nSamples splits
  pure (folds (shuffle [0 .. nSamples - 1] s) splits)

-- | 並べ替えずに、先頭から順に @splits@ 個のかたまりに分ける。
kFoldSequential :: Int -> Int -> Either String [Fold]
kFoldSequential nSamples splits = do
  checkSplits nSamples splits
  pure (folds [0 .. nSamples - 1] splits)

-- | 分割の数が使える範囲にあるかを確かめる。
checkSplits :: Int -> Int -> Either String ()
checkSplits nSamples splits
  | splits < 2 || splits > nSamples =
      Left (printf "分割の数は 2 以上 %d 以下にしてください: %d" nSamples splits)
  | otherwise = Right ()

-- | 並べた位置を @splits@ 個に分け、1 つをテストデータ、残りを訓練データにする。
folds :: [Int] -> Int -> [Fold]
folds positions splits =
  [ Fold (filter (`Set.notMember` Set.fromList test) positions) test
  | test <- chunks sizes positions
  ]
 where
  total = length positions
  sizes =
    [ total `div` splits + (if i < total `mod` splits then 1 else 0)
    | i <- [0 .. splits - 1]
    ]

-- | 指定した大きさの順に切り分ける。
chunks :: [Int] -> [a] -> [[a]]
chunks [] _ = []
chunks (size : rest) values = taken : chunks rest remaining
 where
  (taken, remaining) = splitAt size values

-- | 行の位置で値を選ぶ。範囲の外を指定すれば失敗する。
pick :: [a] -> [Int] -> Either String [a]
pick values = traverse at
 where
  table = M.fromList (zip [0 ..] values)
  at position =
    case M.lookup position table of
      Nothing -> Left (printf "行の位置が範囲の外です: %d" position)
      Just value -> Right value

{- | 分類器（訓練データを受け取って、予測する関数を返す関数）。

「学習に失敗するかもしれない」ことも「予測に失敗するかもしれない」ことも
'Either' で表れる。返ってくるのが関数なので、学習した結果をどんな型で持つかは
呼ぶ側から見えない。
-}
type Trainer x t p = [x] -> [t] -> Either String ([x] -> Either String [p])

{- | 分割ごとに分類器を訓練データで学習し、テストデータの予測を評価関数で採点する。

遅延評価なので、必要な分割の分だけ学習する……とはいかない。'Either' を
1 つにまとめる 'traverse' が全部の結果を要求するためで、Elixir 版の
@Stream.map@ のような「取り出した分だけ」にはならない。失敗を型で表すことの
引き換えである。
-}
crossValidate
  :: Trainer x t p
  -> [x]
  -> [t]
  -> [Fold]
  -> ([t] -> [p] -> Either String Double)
  -> Either String [Double]
crossValidate trainer x t theFolds metric = traverse scoreFold theFolds
 where
  scoreFold fold = do
    xTrain <- pick x (foldTrain fold)
    tTrain <- pick t (foldTrain fold)
    xTest <- pick x (foldTest fold)
    tTest <- pick t (foldTest fold)
    predictor <- trainer xTrain tTrain
    predictor xTest >>= metric tTest

-- 分類器 ---------------------------------------------------------------------

-- | 第 3 章の決定木の分類器。
treeTrainer :: [Text] -> Maybe Int -> Trainer C3.Features Text Text
treeTrainer columns maxDepth x t = do
  tree <- C3.fit x t columns maxDepth
  pure (C3.predict tree)

-- | 第 7 章の線形回帰の分類器（回帰なので予測は数値）。
linearTrainer :: [Text] -> Trainer C3.Features Double Double
linearTrainer columns x t = do
  m <- C7.fit x t columns
  pure (C7.predict m)

-- 実データの前処理 -----------------------------------------------------------

{- | 客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、Survived の文字列を正解にする。

この章では **分割の前に全体の平均値で年齢の欠損値を補う**、簡略化した前処理を使う。
第 8 章のパイプラインは分割のあとで補完した（そちらが本来の順序である）。
ここはほかの言語版と条件をそろえるために、あえて簡略化した手順に合わせている。
-}
prepareSurvived :: BL.ByteString -> Either String ([C3.Features], [Text])
prepareSurvived contents = do
  table <- C2.loadTable contents
  means <- C2.columnMeans (C2.tableRows table) ["Age"]
  ageMean <- lookupColumn means "Age"
  x <- traverse (features ageMean) (C2.tableRows table)
  t <- traverse (`text` "Survived") (C2.tableRows table)
  pure (x, t)
 where
  features ageMean row = do
    pclass <- requiredNumber row "Pclass"
    age <- optionalNumber row "Age"
    sex <- text row "Sex"
    pure $
      M.fromList
        [ ("Pclass", pclass)
        , ("Age", fromMaybe ageMean age)
        , ("male", if sex == "male" then 1.0 else 0.0)
        ]

-- | 第 7 章の 4 列を特徴量に、興行収入を正解にする。欠損値は列ごとの平均値で補う。
prepareCinema :: BL.ByteString -> Either String ([C3.Features], [Double])
prepareCinema contents = do
  table <- C2.loadTable contents
  means <- C2.columnMeans (C2.tableRows table) C7.featureColumns
  x <- C2.fillMissing (C2.tableRows table) C7.featureColumns means
  t <- traverse (`requiredNumber` C7.targetColumn) (C2.tableRows table)
  pure (x, t)

-- | 空欄を許さない数値の列を読む。
requiredNumber :: Row -> Text -> Either String Double
requiredNumber row name = do
  value <- optionalNumber row name
  case value of
    Nothing -> Left ("値が空欄です: " <> T.unpack name)
    Just found -> Right found

-- | 'M.Map' から列を読む。無ければ失敗する。
lookupColumn :: M.Map Text Double -> Text -> Either String Double
lookupColumn table name =
  case M.lookup name table of
    Nothing -> Left ("列がありません: " <> T.unpack name)
    Just value -> Right value

-- 実データでの実行 -----------------------------------------------------------

-- | Survived を深さ 2 の決定木で評価する。指標の名前と交差検証の平均を、表示する順に返す。
evaluateSurvived :: BL.ByteString -> Either String [(Text, Double)]
evaluateSurvived contents = do
  (x, t) <- prepareSurvived contents
  theFolds <- kFold (length x) nSplits seed
  let trainer = treeTrainer survivedColumns treeDepth
      metrics =
        [ ("正解率", accuracy)
        , ("適合率", classificationMetric precision survivedLabel)
        , ("再現率", classificationMetric recall survivedLabel)
        , ("F値", classificationMetric f1Score survivedLabel)
        ]
  traverse (average trainer x t theFolds) metrics

-- | cinema を線形回帰で評価する。
evaluateCinema :: BL.ByteString -> Either String [(Text, Double)]
evaluateCinema contents = do
  (x, t) <- prepareCinema contents
  theFolds <- kFold (length x) nSplits seed
  let trainer = linearTrainer C7.featureColumns
      metrics =
        [ ("RMSE", C7.rootMeanSquaredError)
        , ("MAE", C7.meanAbsoluteError)
        ]
  traverse (average trainer x t theFolds) metrics

-- | 1 つの指標について、分割ごとのスコアの平均を求める。
average
  :: Trainer x t p
  -> [x]
  -> [t]
  -> [Fold]
  -> (Text, [t] -> [p] -> Either String Double)
  -> Either String (Text, Double)
average trainer x t theFolds (name, metric) =
  (,) name <$> (crossValidate trainer x t theFolds metric >>= mean)

-- | Survived と cinema を K 分割交差検証で評価し、指標ごとの平均を表示する。
run :: IO (Either String String)
run = do
  survived <- Dataset.path "Survived.csv" >>= BL.readFile
  cinema <- Dataset.path "cinema.csv" >>= BL.readFile
  pure (report survived cinema)

-- | 読み込んだ中身から報告の文字列を作る。純粋関数なのでテストで確かめられる。
report :: BL.ByteString -> BL.ByteString -> Either String String
report survived cinema = do
  survivedScores <- evaluateSurvived survived
  cinemaScores <- evaluateCinema cinema
  (x, _) <- prepareSurvived survived
  theFolds <- kFold (length x) nSplits seed
  pure $
    concat
      [ printf "Survived（決定木、%d 分割交差検証の平均）\n" nSplits
      , concatMap (formatScore 4) survivedScores
      , printf "cinema（線形回帰、%d 分割交差検証の平均）\n" nSplits
      , concatMap (formatScore 2) cinemaScores
      , printf "分割ごとのテストデータの件数（%d 分割）\n" nSplits
      , printf
          "  %d 件を自作: %s\n"
          (length x)
          (T.unpack (T.intercalate ", " (map (T.pack . show . length . foldTest) theFolds)))
      ]

-- | 「  名前: 値」の 1 行にする。小数点以下の桁数は指標によって変える。
formatScore :: Int -> (Text, Double) -> String
formatScore digits (name, value) =
  printf "  %s: %s\n" (T.unpack name) (printf "%.*f" digits value :: String)
