{-# LANGUAGE ExistentialQuantification #-}
{-# LANGUAGE OverloadedStrings #-}

{- | 第 10 章: ロジスティック回帰とアンサンブル学習。

Haskell には scikit-learn にあたるものが無いので、**ロジスティック回帰も
ランダムフォレストも、ここで作るものがそのまま最終実装** になる（[ADR 014]）。
突き合わせる相手はライブラリではなく、ほかの言語版の数値である。

モデルは型クラス 'Classifier' で抽象する。第 3 章の決定木も、この章で作る
2 つのモデルも、同じ 'fitModel' で学習して同じ 'score' で評価できる。
ただし **型の違うモデルを 1 つのリストに並べるには存在型が要る**
（'SomeClassifier'）。PHP 版の interface や Elixir 版の関数には無かった手間である。
-}
module GettingStartedMl.Chapter10 (
  -- * 分類器の約束
  Classifier (..),
  Predictor,
  SomeClassifier (..),
  score,
  models,

  -- * ソフトマックス関数と交差エントロピー
  softmax,
  crossEntropy,

  -- * ロジスティック回帰
  LogisticRegression (..),
  LogisticModel (..),
  logisticFit,
  logisticPredict,

  -- * ランダムフォレスト
  TreeModel (..),
  ForestModel (..),
  Forest (..),
  ForestTree (..),
  majorityVote,
  bootstrapSample,
  shuffleWithState,
  forestFit,
  forestPredict,

  -- * 特徴量の重要度
  treeImportances,
  forestImportances,

  -- * 実データでの実行
  report,
  run,
) where

import qualified Data.ByteString.Lazy as BL
import Data.List (elemIndex, nub, sort)
import qualified Data.Map.Strict as M
import Data.Text (Text)
import qualified Data.Text as T
import qualified GettingStartedMl.Chapter01 as C1
import qualified GettingStartedMl.Chapter02 as C2
import qualified GettingStartedMl.Chapter03 as C3
import qualified GettingStartedMl.Dataset as Dataset
import GettingStartedMl.Random (Seed, newSeed, nextInt)
import Text.Printf (printf)

-- | 確率が 0 のときに @log 0@ が @-Infinity@ にならないように足す値。
epsilon :: Double
epsilon = 1.0e-12

-- | 森に作る木の数。
defaultEstimators :: Int
defaultEstimators = 100

-- | 1 本の木に渡す特徴量の数。
defaultMaxFeatures :: Int
defaultMaxFeatures = 2

-- | 比べる決定木の深さ。
shallowDepth :: Int
shallowDepth = 2

-- | 分割と森のシード。
defaultSeed :: Int
defaultSeed = 0

-- 分類器の約束 ---------------------------------------------------------------

-- | 学習したあとに残る「予測する関数」。
type Predictor = [C3.Features] -> Either String [Text]

{- | 分類器の約束。

「学習して、予測する関数を返す」ことだけを決める。第 3 章の決定木も
この章の 2 つのモデルも、同じ形で評価できる。
-}
class Classifier m where
  -- | 表に載せる名前。
  classifierName :: m -> Text

  -- | 訓練データと特徴量の列を受け取り、予測する関数を返す。
  fitModel :: m -> [C3.Features] -> [Text] -> [Text] -> Either String Predictor

{- | 型の違う分類器を 1 つのリストに並べるための包み。

型クラスは「同じ約束を守る」ことしか言わないので、'TreeModel' と
'ForestModel' は別の型のままである。リストの要素は 1 つの型でなければ
ならないので、存在型で包んで型の違いを隠す。
-}
data SomeClassifier = forall m. (Classifier m) => SomeClassifier m

-- | 分類器を訓練データで学習させてから、訓練データとテストデータの正解率を求める。
score :: (Classifier m) => m -> C2.Split C3.Features -> [Text] -> Either String (Double, Double)
score model split columns = do
  predict <- fitModel model (C2.xTrain split) (C2.tTrain split) columns
  train <- predict (C2.xTrain split) >>= (`C1.accuracy` C2.tTrain split)
  test <- predict (C2.xTest split) >>= (`C1.accuracy` C2.tTest split)
  pure (train, test)

-- | 表示する順に並べた分類器。
models :: [SomeClassifier]
models =
  [ SomeClassifier (TreeModel (Just shallowDepth))
  , SomeClassifier (LogisticModel 1.0 5000)
  , SomeClassifier (ForestModel defaultEstimators defaultMaxFeatures Nothing defaultSeed)
  , SomeClassifier (ForestModel defaultEstimators defaultMaxFeatures (Just shallowDepth) defaultSeed)
  ]

-- ソフトマックス関数と交差エントロピー ---------------------------------------

{- | スコアを、合計が 1 になる確率に変換する。

最大値を引いてから @exp@ を求めるので、大きな値でもあふれない。
引いた分は分母と分子で打ち消し合うので、結果は変わらない。

Haskell の @exp@ は例外を投げず、あふれると @Infinity@ を返す。引き算を
省くと、@Infinity / Infinity@ で @NaN@ になって静かに壊れる。
-}
softmax :: [Double] -> [Double]
softmax [] = []
softmax z = map (/ total) exps
 where
  maximum' = foldl' max (head' z) z
  exps = map (\v -> exp (v - maximum')) z
  total = sumL exps
  head' (v : _) = v
  head' [] = 0.0

{- | 交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。

確率が 0 のときに @log 0@ が @-Infinity@ にならないよう、ごく小さい値を足す。
-}
crossEntropy :: [[Double]] -> [Int] -> Double
crossEntropy [] _ = 0.0
crossEntropy probabilities targets =
  negate (total / fromIntegral (length probabilities))
 where
  total = sumL [log (at p target + epsilon) | (p, target) <- zip probabilities targets]
  at values i = case drop i values of
    (v : _) -> v
    [] -> 0.0

-- ロジスティック回帰 ---------------------------------------------------------

-- | 学習したロジスティック回帰。重みは @weights[特徴量][品種]@ で持つ。
data LogisticRegression = LogisticRegression
  { logisticColumns :: [Text]
  , logisticClasses :: [Text]
  , logisticWeights :: [[Double]]
  , logisticBias :: [Double]
  , logisticLosses :: [Double]
  }
  deriving (Eq, Show)

-- | 学習率と繰り返し回数を持つロジスティック回帰の分類器。
data LogisticModel = LogisticModel Double Int
  deriving (Eq, Show)

instance Classifier LogisticModel where
  classifierName _ = "ロジスティック回帰"
  fitModel (LogisticModel rate epochs) x t columns = do
    model <- logisticFit x t columns rate epochs
    pure (logisticPredict model)

{- | バッチ勾配降下法で重みと切片を学習する。品種は名前の順に並べる。

ループの順（特徴量が外、品種が内）と、足し込みが左から右であることを
Java 版・Clojure 版・PHP 版にそろえる。**浮動小数点の足し算は順によって
結果が変わる** ので、数値を一致させるには順まで合わせる。Haskell の 'sum' は
リストでは右畳み込みになりうるので、明示的に左畳み込みの 'sumL' を使う。
-}
logisticFit :: [C3.Features] -> [Text] -> [Text] -> Double -> Int -> Either String LogisticRegression
logisticFit x t columns rate epochs
  | length x /= length t =
      Left (printf "特徴量と正解ラベルの件数が違います: %d と %d" (length x) (length t))
  | null x = Left "特徴量が 1 件もありません"
  | otherwise = do
      rows <- traverse (\features -> traverse (`featureValue` features) columns) x
      targets <- traverse classIndex t
      let zeros = replicate (length columns) (replicate (length classes) 0.0)
          bias = replicate (length classes) 0.0
          (weights, finalBias, losses) = descend rows targets zeros bias epochs []
      pure
        LogisticRegression
          { logisticColumns = columns
          , logisticClasses = classes
          , logisticWeights = weights
          , logisticBias = finalBias
          , logisticLosses = reverse losses
          }
 where
  classes = sort (nub t)
  n = fromIntegral (length x) :: Double

  classIndex label =
    case elemIndex label classes of
      Just i -> Right i
      Nothing -> Left ("品種を読めません: " <> T.unpack label)

  descend _ _ weights bias 0 losses = (weights, bias, losses)
  descend rows targets weights bias remaining losses =
    descend rows targets weights' bias' (remaining - 1) (loss : losses)
   where
    probabilities = [softmax (rowScores row weights bias) | row <- rows]
    loss = crossEntropy probabilities targets
    errors = zipWith errorOf probabilities targets
    weights' =
      [ [w - rate * g / n | (w, g) <- zip forFeature gradients]
      | (forFeature, gradients) <- zip weights (weightGradient rows errors)
      ]
    bias' = [b - rate * g / n | (b, g) <- zip bias (biasGradient bias errors)]

-- | 誤差は「確率 − 正解」。正解の品種だけ 1 を引く。
errorOf :: [Double] -> Int -> [Double]
errorOf probabilities target =
  [if i == target then p - 1.0 else p | (i, p) <- zip [0 ..] probabilities]

-- | 1 行のスコア（品種ごと）。特徴量を外、品種を内にして足し込む。
rowScores :: [Double] -> [[Double]] -> [Double] -> [Double]
rowScores row weights bias =
  foldl' step bias (zip row weights)
 where
  step acc (value, forFeature) = [s + value * w | (s, w) <- zip acc forFeature]

-- | @gradient[特徴量][品種]@ に「その行の特徴量 × 誤差」を足し込む。
weightGradient :: [[Double]] -> [[Double]] -> [[Double]]
weightGradient rows errors =
  foldl' step zeros (zip rows errors)
 where
  zeros = case rows of
    (row : _) -> replicate (length row) (replicate classCount 0.0)
    [] -> []
  classCount = case errors of
    (err : _) -> length err
    [] -> 0
  step acc (row, err) =
    [[s + value * e | (s, e) <- zip forFeature err] | (forFeature, value) <- zip acc row]

-- | 品種ごとの誤差を足し合わせる。
biasGradient :: [Double] -> [[Double]] -> [Double]
biasGradient bias = foldl' step (map (const 0.0) bias)
 where
  step acc err = [s + e | (s, e) <- zip acc err]

-- | スコアが最大の品種を予測する。同じ値なら先に現れたほうを選ぶ。
logisticPredict :: LogisticRegression -> [C3.Features] -> Either String [Text]
logisticPredict model = traverse predictOne
 where
  predictOne features = do
    row <- traverse (`featureValue` features) (logisticColumns model)
    let scores = rowScores row (logisticWeights model) (logisticBias model)
    case drop (argmax scores) (logisticClasses model) of
      (label : _) -> Right label
      [] -> Left "品種がありません"

-- | 最大値の添字。同じ値なら先に現れたほうを選ぶ。
argmax :: [Double] -> Int
argmax values = snd (foldl' keepFirstMaximum (negate (1 / 0), 0) (zip values [0 ..]))
 where
  keepFirstMaximum (best, bestIndex) (value, i)
    | value > best = (value, i)
    | otherwise = (best, bestIndex)

-- ランダムフォレスト ---------------------------------------------------------

-- | 第 3 章の決定木の分類器。
newtype TreeModel = TreeModel (Maybe Int)
  deriving (Eq, Show)

instance Classifier TreeModel where
  classifierName (TreeModel depth) = "決定木（" <> depthLabel depth <> "）"
  fitModel (TreeModel depth) x t columns = do
    tree <- C3.fit x t columns depth
    pure (C3.predict tree)

-- | 木の数・1 本に渡す特徴量の数・深さの上限・シードを持つランダムフォレストの分類器。
data ForestModel = ForestModel Int Int (Maybe Int) Int
  deriving (Eq, Show)

instance Classifier ForestModel where
  classifierName (ForestModel n _ depth _) =
    "ランダムフォレスト（" <> T.pack (show n) <> " 本" <> suffix <> "）"
   where
    suffix = case depth of
      Nothing -> ""
      Just _ -> "・" <> depthLabel depth
  fitModel (ForestModel n maxFeatures depth seed) x t columns = do
    forest <- forestFit x t columns n maxFeatures depth seed
    pure (forestPredict forest)

-- | 深さの呼び名。
depthLabel :: Maybe Int -> Text
depthLabel Nothing = "深さの上限なし"
depthLabel (Just depth) = "深さ " <> T.pack (show depth)

-- | 森の 1 本。使った列と、学習に使った行の番号を覚えておく（重要度で使う）。
data ForestTree = ForestTree
  { forestTreeColumns :: [Text]
  , forestTreeRows :: [Int]
  , forestTreeTree :: C3.Tree
  }
  deriving (Eq, Show)

-- | 森。
newtype Forest = Forest {forestTrees :: [ForestTree]}
  deriving (Eq, Show)

-- | サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。
majorityVote :: [[Text]] -> Either String [Text]
majorityVote [] = Right []
majorityVote votes@(first : _) =
  traverse (\i -> C3.majority [vote !! i | vote <- votes]) [0 .. length first - 1]

{- | 0 から @size - 1@ までの行番号を、重複を許して @size@ 個選ぶ。

乱数の状態を受け取り、使い終わった状態と一緒に返す。森を作る途中で
状態を引き継ぐ必要があるので、シードではなく状態を回す。
-}
bootstrapSample :: Int -> Seed -> ([Int], Seed)
bootstrapSample size = go size []
 where
  go 0 acc state = (reverse acc, state)
  go remaining acc state =
    let (i, next) = nextInt size state
     in go (remaining - 1) (i : acc) next

{- | 渡された乱数の状態で Fisher-Yates の並べ替えをする。

第 2 章の @shuffle@ と同じ手順だが、シードではなく状態を受け取って続きから引ける。
-}
shuffleWithState :: [a] -> Seed -> ([a], Seed)
shuffleWithState items state
  | count <= 1 = (items, state)
  | otherwise =
      let (table, next) = go (count - 1) state (M.fromList (zip [0 ..] items))
       in (M.elems table, next)
 where
  count = length items

  go i state' table
    | i < 1 = (table, state')
    | otherwise =
        let (j, next) = nextInt (i + 1) state'
         in go (i - 1) next (swap i j table)

  swap i j table =
    case (M.lookup i table, M.lookup j table) of
      (Just atI, Just atJ) -> M.insert i atJ (M.insert j atI table)
      _ -> table

{- | ブートストラップ標本と特徴量の部分集合で、第 3 章の決定木を @n@ 本学習する。

第 3 章の 'C3.fit' は純粋な関数なので、包むアダプターは要らない。
-}
forestFit :: [C3.Features] -> [Text] -> [Text] -> Int -> Int -> Maybe Int -> Int -> Either String Forest
forestFit x t columns n maxFeatures maxDepth seed
  | length x /= length t =
      Left (printf "特徴量と正解ラベルの件数が違います: %d と %d" (length x) (length t))
  | null x = Left "特徴量が 1 件もありません"
  | otherwise = Forest <$> go n (newSeed seed) []
 where
  size = length x

  go 0 _ acc = Right (reverse acc)
  go remaining state acc =
    let (rows, afterRows) = bootstrapSample size state
        (shuffled, afterColumns) = shuffleWithState columns afterRows
        chosen = take maxFeatures shuffled
        -- 列の順は元のまま残す。
        treeColumns = filter (`elem` chosen) columns
        sampleX = [pickColumns (x !! i) treeColumns | i <- rows]
        sampleT = [t !! i | i <- rows]
     in case C3.fit sampleX sampleT treeColumns maxDepth of
          Left err -> Left err
          Right tree -> go (remaining - 1) afterColumns (ForestTree treeColumns rows tree : acc)

-- | 木ごとの予測を多数決でまとめる。
forestPredict :: Forest -> [C3.Features] -> Either String [Text]
forestPredict forest x = do
  votes <- traverse vote (forestTrees forest)
  majorityVote votes
 where
  vote tree = C3.predict (forestTreeTree tree) [pickColumns features (forestTreeColumns tree) | features <- x]

-- 特徴量の重要度 -------------------------------------------------------------

-- | 決定木 1 本の重要度。合計が 1 になるようにする。
treeImportances :: C3.Tree -> [C3.Features] -> [Text] -> [Text] -> Either String (M.Map Text Double)
treeImportances tree x t columns = do
  decreases <- impurityDecreases tree x t
  let zeros = M.fromList [(c, 0.0) | c <- columns]
  pure (normalize (foldl' add zeros decreases))
 where
  add totals (feature, amount) = M.adjust (+ amount) feature totals

-- | 木ごとの重要度の平均。木が使わなかった特徴量は、その木では 0 とする。
forestImportances :: Forest -> [C3.Features] -> [Text] -> [Text] -> Either String (M.Map Text Double)
forestImportances forest x t columns = do
  let zeros = M.fromList [(c, 0.0) | c <- columns]
      count = fromIntegral (length (forestTrees forest)) :: Double
  totals <- foldl' (addTree count) (Right zeros) (forestTrees forest)
  pure (normalize totals)
 where
  addTree count acc tree = do
    totals <- acc
    let sampleX = [pickColumns (x !! i) (forestTreeColumns tree) | i <- forestTreeRows tree]
        sampleT = [t !! i | i <- forestTreeRows tree]
    found <- treeImportances (forestTreeTree tree) sampleX sampleT (forestTreeColumns tree)
    pure (foldl' (\totals' (feature, value) -> M.adjust (+ (value / count)) feature totals') totals (M.toList found))

{- | 分割ごとに減った不純度（件数で重み付け）を、@(列, 減った量)@ の並びにする。

木が「葉か節のどちらか」であることは型に書いてあるので、場合分けの漏れは
コンパイルで止まる。Elixir 版はマップの鍵で見分けていて、網羅性は検査されなかった。
-}
impurityDecreases :: C3.Tree -> [C3.Features] -> [Text] -> Either String [(Text, Double)]
impurityDecreases (C3.Leaf _) _ _ = Right []
impurityDecreases (C3.Branch split left right) x t = do
  sides <- traverse goesLeft (zip x t)
  let toLeft = [pair | (True, pair) <- sides]
      toRight = [pair | (False, pair) <- sides]
      here = (C3.splitFeature split, fromIntegral (length t) * (C3.gini t - C3.splitImpurity split))
  fromLeft <- impurityDecreases left (map fst toLeft) (map snd toLeft)
  fromRight <- impurityDecreases right (map fst toRight) (map snd toRight)
  pure ((here : fromLeft) <> fromRight)
 where
  goesLeft (features, label) = do
    value <- featureValue (C3.splitFeature split) features
    pure (value <= C3.splitThreshold split, (features, label))

-- | 合計が 1 になるようにする。合計が 0 なら何もしない。
normalize :: M.Map Text Double -> M.Map Text Double
normalize totals
  | total == 0.0 = totals
  | otherwise = M.map (/ total) totals
 where
  total = sumL (M.elems totals)

-- 実データでの実行 -----------------------------------------------------------

-- | 読み込んだ中身から報告の文字列を作る。純粋関数なのでテストで確かめられる。
report :: BL.ByteString -> Either String String
report contents = do
  split <- C2.prepareIris contents 0.3 defaultSeed
  let columns = C3.featureColumns
  rows <- traverse (\(SomeClassifier model) -> (,) (classifierName model) <$> score model split columns) models
  forest <- forestFit (C2.xTrain split) (C2.tTrain split) columns defaultEstimators defaultMaxFeatures Nothing defaultSeed
  importances <- forestImportances forest (C2.xTrain split) (C2.tTrain split) columns
  values <- traverse (`lookupColumn` importances) columns
  pure $
    unlines $
      ["モデル\t訓練データ\tテストデータ"]
        <> [printf "%s\t%.4f\t%.4f" (T.unpack name) train test | (name, (train, test)) <- rows]
        <> [ ""
           , printf "ランダムフォレスト（%d 本）の特徴量の重要度:" defaultEstimators
           ]
        <> [printf "%s\t%.4f" (T.unpack column) value | (column, value) <- zip columns values]

-- | 実データでモデルごとの正解率と特徴量の重要度を表示する。
run :: IO (Either String String)
run = do
  file <- Dataset.path "iris.csv"
  contents <- BL.readFile file
  pure (report contents)

-- 補助 -----------------------------------------------------------------------

-- | 列の値を読む。列が無ければ失敗する。
featureValue :: Text -> C3.Features -> Either String Double
featureValue column features =
  case M.lookup column features of
    Just value -> Right value
    Nothing -> Left ("列がありません: " <> T.unpack column)

-- | 覚えた値を読む。
lookupColumn :: Text -> M.Map Text Double -> Either String Double
lookupColumn column table =
  case M.lookup column table of
    Just value -> Right value
    Nothing -> Left ("列がありません: " <> T.unpack column)

-- | 指定した列だけを残す。
pickColumns :: C3.Features -> [Text] -> C3.Features
pickColumns features columns = M.restrictKeys features (M.keysSet (M.fromList [(c, ()) | c <- columns]))

{- | 左から足す和。

'sum' はリストでは右畳み込みになりうるので、浮動小数点の足し算の順を
ほかの言語版にそろえるために明示する。
-}
sumL :: [Double] -> Double
sumL = foldl' (+) 0.0
