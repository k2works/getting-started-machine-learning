{-# LANGUAGE OverloadedStrings #-}

{- | 第 8 章: 実践的な分類と前処理パイプライン。タイタニック号の乗客データを扱う。

Haskell には scikit-learn にあたるものが無いので、前処理も決定木もすべて自作する。
前処理は「訓練データから値を学び（fit）、その値でデータを変換する（apply）」の
2 段に分け、種類は 1 つの直和型で数え上げる。取りうる前処理はこのファイルの
'Step' を読めば全部わかり、節を書き漏らせばコンパイルが止まる。

学習済みのパイプラインは @Data.Binary@ で保存・復元する。
-}
module GettingStartedMl.Chapter08 (
  Frame (..),
  Step (..),
  FittedStep (..),
  ClassWeight (..),
  Rule (..),
  Tree (..),
  Pipeline (..),
  FittedPipeline (..),
  Evaluation (..),
  featureColumns,
  targetColumn,
  modelFile,
  formatVersion,
  groupMedianImputer,
  mostFrequentImputer,
  dummyEncoder,
  dummyColumn,
  median,
  fitStep,
  applyStep,
  toFeatures,
  weightedGini,
  balancedWeights,
  weightsOf,
  weightedMajority,
  bestSplit,
  fitTree,
  predictTree,
  predictTreeOne,
  buildPipeline,
  fitPipeline,
  transform,
  predictPipeline,
  saveModel,
  loadModel,
  labelValues,
  prepareSurvived,
  evaluate,
  run,
  report,
) where

import Data.Bifunctor (bimap, first)
import Data.Binary (Binary (..), Get, Put, decodeOrFail, encode)
import Data.Binary.Get (getWord8)
import Data.Binary.Put (putWord8)
import qualified Data.ByteString.Lazy as BL
import Data.List (nub, sort, sortOn)
import qualified Data.Map.Strict as M
import Data.Text (Text)
import qualified Data.Text as T
import GettingStartedMl.Chapter02 (Split (..), Table (..), loadTable, splitTrainTest)
import GettingStartedMl.Csv (Row, text)
import qualified GettingStartedMl.Dataset as Dataset
import System.Directory (createDirectoryIfMissing)
import System.FilePath (takeDirectory)
import Text.Printf (printf)

{- | 列名の並びと、列名から値への対応を持つ行の表。

値は文字列のまま持つ。前処理は文字列の表を文字列の表に変換し、
モデルに渡す直前に一度だけ数値にする。
-}
data Frame = Frame
  { frameColumns :: [Text]
  , frameRows :: [M.Map Text Text]
  }
  deriving (Eq, Show)

{- | 前処理の種類。

直和型なので、取りうる前処理はここを読めば全部わかる。増やしたときに
'fitStep' や 'applyStep' の節を書き漏らせば、テストではなくコンパイルが止まる。
-}
data Step
  = -- | ある列を、別の列の組み合わせごとの中央値で補完する
    GroupMedianImputer Text [Text]
  | -- | ある列を最頻値で補完する
    MostFrequentImputer Text
  | -- | カテゴリの列をダミー変数にする
    DummyEncoder [Text]
  deriving (Eq, Show)

-- | 訓練データから値を学んだあとの前処理。
data FittedStep
  = FittedGroupMedian Text [Text] (M.Map [Text] Double) Double
  | FittedMostFrequent Text Text
  | FittedDummy [(Text, [Text])]
  deriving (Eq, Show)

-- | クラスの重みの付け方。
data ClassWeight
  = -- | 重みを付けない
    NoWeight
  | -- | 件数に反比例する重みを付ける
    Balanced
  deriving (Eq, Show)

-- | 枝の分かれ方。特徴量の値がしきい値以下なら左へ進む。
data Rule = Rule
  { ruleFeature :: Text
  , ruleThreshold :: Double
  }
  deriving (Eq, Show)

-- | 決定木。葉はラベル、枝は分かれ方と左右の部分木を持つ。
data Tree
  = Leaf Int
  | Branch Rule Tree Tree
  deriving (Eq, Show)

-- | 前処理の並びと、決定木の設定。
data Pipeline = Pipeline
  { pipelineSteps :: [Step]
  , pipelineMaxDepth :: Maybe Int
  , pipelineClassWeight :: ClassWeight
  }
  deriving (Eq, Show)

-- | 学習済みのパイプライン。
data FittedPipeline = FittedPipeline
  { fittedSteps :: [FittedStep]
  , fittedColumns :: [Text]
  , fittedTree :: Tree
  }
  deriving (Eq, Show)

-- | 評価の結果。
data Evaluation = Evaluation
  { trainAccuracy :: Double
  , testAccuracy :: Double
  , foundSurvivors :: Int
  , survivors :: Int
  }
  deriving (Eq, Show)

-- | モデルに渡す特徴量の列。PassengerId・Ticket・Cabin は使わない。
featureColumns :: [Text]
featureColumns = ["Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked"]

-- | 正解ラベルの列。1 が生存、0 が死亡。
targetColumn :: Text
targetColumn = "Survived"

-- | 生存を表すラベル。
survived :: Int
survived = 1

-- | 学習済みのパイプラインの保存先。model/ は .gitignore の対象。
modelFile :: FilePath
modelFile = "model/survived.model"

-- | 保存する形式の版。読み込むときに確かめる。
formatVersion :: Int
formatVersion = 1

-- | グループ別の中央値で補完する前処理を作る。
groupMedianImputer :: Text -> [Text] -> Step
groupMedianImputer = GroupMedianImputer

-- | 最頻値で補完する前処理を作る。
mostFrequentImputer :: Text -> Step
mostFrequentImputer = MostFrequentImputer

-- | ダミー変数化する前処理を作る。
dummyEncoder :: [Text] -> Step
dummyEncoder = DummyEncoder

-- | ダミー変数の列の名前。「元の列名_カテゴリ」にする。
dummyColumn :: Text -> Text -> Text
dummyColumn column category = column <> "_" <> category

-- | 中央値。件数が偶数なら中央の 2 つの平均。
median :: [Double] -> Either String Double
median [] = Left "値がありません"
median values
  | odd count = Right (sorted !! middle)
  | otherwise = Right ((sorted !! (middle - 1) + sorted !! middle) / 2.0)
 where
  sorted = sort values
  count = length sorted
  middle = count `div` 2

-- | 値が空欄かどうか。
isMissing :: M.Map Text Text -> Text -> Bool
isMissing row column = maybe True (T.null . T.strip) (M.lookup column row)

-- | 列の値を文字列で読む。無ければ空文字とみなす。
cell :: M.Map Text Text -> Text -> Text
cell row column = T.strip (M.findWithDefault "" column row)

-- | 列の値を数値で読む。
numberOf :: M.Map Text Text -> Text -> Either String Double
numberOf row column
  | isMissing row column = Left ("欠損値が残っています: " <> T.unpack column)
  | otherwise = case reads (T.unpack (cell row column)) of
      [(value, rest)] | all (== ' ') rest -> Right value
      _ ->
        Left
          ( T.unpack column
              <> " を数値として読めません: "
              <> T.unpack (cell row column)
          )

-- | 行のグループ。@by@ の列の値を並べたリストで、'M.Map' のキーに使う。
groupOf :: [Text] -> M.Map Text Text -> [Text]
groupOf by row = map (cell row) by

-- | 訓練データから変換に必要な値を求め、学習済みの前処理を返す。
fitStep :: Step -> Frame -> Either String FittedStep
fitStep (GroupMedianImputer column by) frame = do
  let known = filter (\row -> not (isMissing row column)) (frameRows frame)
  values <- traverse (`numberOf` column) known
  overall <- median values
  medians <-
    traverse median $
      M.fromListWith (<>) (zipWith (\row value -> (groupOf by row, [value])) known values)
  pure (FittedGroupMedian column by medians overall)
fitStep (MostFrequentImputer column) frame =
  case counted of
    [] -> Left ("値がすべて空欄です: " <> T.unpack column)
    (start : rest) -> Right (FittedMostFrequent column (fst (foldl' larger start rest)))
 where
  values = [cell row column | row <- frameRows frame, not (isMissing row column)]
  -- 値の順に並べ、厳密な不等号で畳むので、同数なら値の順で前のものを選ぶ。
  counted = sortOn fst (M.toList (M.fromListWith (+) [(value, 1 :: Int) | value <- values]))
  larger best candidate = if snd candidate > snd best then candidate else best
fitStep (DummyEncoder columns) frame =
  Right (FittedDummy [(column, drop 1 (categoriesOf column)) | column <- columns])
 where
  categoriesOf column =
    sort (nub [cell row column | row <- frameRows frame, not (isMissing row column)])

-- | 学習済みの前処理でデータを変換する。
applyStep :: FittedStep -> Frame -> Either String Frame
applyStep (FittedGroupMedian column by medians overall) frame =
  Right frame {frameRows = map fill (frameRows frame)}
 where
  fill row
    | isMissing row column =
        M.insert column (showDouble (M.findWithDefault overall (groupOf by row) medians)) row
    | otherwise = row
applyStep (FittedMostFrequent column value) frame =
  Right frame {frameRows = map fill (frameRows frame)}
 where
  fill row
    | isMissing row column = M.insert column value row
    | otherwise = row
applyStep (FittedDummy dummies) frame =
  Right (Frame (foldl' widen (frameColumns frame) dummies) (map encodeRow (frameRows frame)))
 where
  widen columns (column, categories) =
    filter (/= column) columns <> map (dummyColumn column) categories

  encodeRow row = foldl' (encodeColumn row) row dummies

  encodeColumn source encoded (column, categories) =
    foldl'
      ( \acc category ->
          M.insert
            (dummyColumn column category)
            (if cell source column == category then "1" else "0")
            acc
      )
      encoded
      categories

-- | 前処理の済んだ表を特徴量にする。欠損値が残っていれば失敗する。
toFeatures :: Frame -> Either String [M.Map Text Double]
toFeatures frame = traverse featuresOf (frameRows frame)
 where
  featuresOf row =
    M.fromList <$> traverse (\column -> (,) column <$> numberOf row column) (frameColumns frame)

-- | ラベルごとの重みの合計を、ラベルが先に現れた順のリストで返す。
weightSums :: [Int] -> [Double] -> [(Int, Double)]
weightSums labels weights =
  [(label, M.findWithDefault 0.0 label sums) | label <- nub labels]
 where
  sums = M.fromListWith (+) (zip labels weights)

-- | 重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。
weightedGini :: [Int] -> [Double] -> Either String Double
weightedGini labels weights
  | length labels /= length weights =
      Left (printf "ラベルと重みの件数が違います: %d と %d" (length labels) (length weights))
  | total <= 0.0 = Left "重みがありません"
  | otherwise =
      Right (1.0 - sum [(w / total) * (w / total) | (_, w) <- weightSums labels weights])
 where
  total = sum weights

-- | クラスの件数に反比例する重み（件数 ÷ (クラスの数 × そのクラスの件数)）。
balancedWeights :: [Int] -> [Double]
balancedWeights t =
  [ fromIntegral (length t)
      / (fromIntegral (M.size counts) * fromIntegral (M.findWithDefault 1 label counts))
  | label <- t
  ]
 where
  counts = M.fromListWith (+) [(label, 1 :: Int) | label <- t]

-- | クラスの重みの付け方から、1 件ごとの重みを求める。
weightsOf :: [Int] -> ClassWeight -> [Double]
weightsOf t NoWeight = map (const 1.0) t
weightsOf t Balanced = balancedWeights t

-- | 重みの合計が最も大きいラベル。同じなら先に現れたラベルを選ぶ。
weightedMajority :: [Int] -> [Double] -> Either String Int
weightedMajority labels weights =
  case weightSums labels weights of
    [] -> Left "ラベルがありません"
    (start : rest) -> Right (fst (foldl' larger start rest))
 where
  larger best candidate = if snd candidate > snd best then candidate else best

{- | 左右の重み付き不純度の、重みによる平均が最も小さくなる分かれ方を返す。

分けられなければ 'Nothing' を返す。同じ値の候補が並んだときは先に現れたものを選ぶ。
-}
bestSplit
  :: [M.Map Text Double]
  -> [Int]
  -> [Double]
  -> [Text]
  -> Either String (Maybe Rule)
bestSplit [] _ _ _ = Right Nothing
bestSplit x t w columns = do
  impurity <- weightedGini t w
  if impurity == 0.0
    then pure Nothing
    else do
      found <- concat <$> traverse (candidates x t w (sum w)) columns
      pure $ case found of
        [] -> Nothing
        (start : rest) -> Just (fst (foldl' smaller start rest))
 where
  smaller best candidate = if snd candidate < snd best then candidate else best

-- | 1 つの列で、隣り合う値の中点をしきい値にした候補をすべて返す。
candidates
  :: [M.Map Text Double]
  -> [Int]
  -> [Double]
  -> Double
  -> Text
  -> Either String [(Rule, Double)]
candidates x t w total column = do
  values <- traverse (`featureOf` column) x
  let sorted = sortOn (\(value, _, _) -> value) (zip3 values t w)
      sortedValues = [value | (value, _, _) <- sorted]
      sortedLabels = [label | (_, label, _) <- sorted]
      sortedWeights = [weight | (_, _, weight) <- sorted]
  sequence
    [ candidateAt sortedValues sortedLabels sortedWeights i
    | i <- [1 .. length sorted - 1]
    , sortedValues !! (i - 1) /= sortedValues !! i
    ]
 where
  candidateAt values labels weights i = do
    let (leftLabels, rightLabels) = splitAt i labels
        (leftWeights, rightWeights) = splitAt i weights
    left <- weightedGini leftLabels leftWeights
    right <- weightedGini rightLabels rightWeights
    pure
      ( Rule column ((values !! (i - 1) + values !! i) / 2.0)
      , (sum leftWeights * left + sum rightWeights * right) / total
      )

-- | 特徴量を列名で読む。
featureOf :: M.Map Text Double -> Text -> Either String Double
featureOf features column =
  case M.lookup column features of
    Nothing -> Left ("特徴量がありません: " <> T.unpack column)
    Just value -> Right value

-- | 訓練データから、クラスの重みを付けた決定木を作る。
fitTree
  :: [M.Map Text Double]
  -> [Int]
  -> [Text]
  -> Maybe Int
  -> ClassWeight
  -> Either String Tree
fitTree x t columns maxDepth classWeight
  | length x /= length t =
      Left (printf "特徴量と正解ラベルの件数が違います: %d と %d" (length x) (length t))
  | otherwise = build x t (weightsOf t classWeight) maxDepth
 where
  build rows labels weights depth = do
    rule <- if depth == Just 0 then pure Nothing else bestSplit rows labels weights columns
    case rule of
      Nothing -> Leaf <$> weightedMajority labels weights
      Just chosen -> do
        sides <- traverse (goesLeft chosen) rows
        let next = fmap (subtract 1) depth
            pick side =
              unzip3 [triple | (triple, s) <- zip (zip3 rows labels weights) sides, s == side]
            (leftX, leftT, leftW) = pick True
            (rightX, rightT, rightW) = pick False
        Branch chosen
          <$> build leftX leftT leftW next
          <*> build rightX rightT rightW next

-- | 特徴量が左の部分木へ進むかどうか。
goesLeft :: Rule -> M.Map Text Double -> Either String Bool
goesLeft rule features = (<= ruleThreshold rule) <$> featureOf features (ruleFeature rule)

-- | 木をたどって 1 件のラベルを予測する。
predictTreeOne :: Tree -> M.Map Text Double -> Either String Int
predictTreeOne (Leaf label) _ = Right label
predictTreeOne (Branch rule left right) features = do
  toLeft <- goesLeft rule features
  predictTreeOne (if toLeft then left else right) features

-- | 特徴量ごとのラベルを予測する。
predictTree :: Tree -> [M.Map Text Double] -> Either String [Int]
predictTree tree = traverse (predictTreeOne tree)

-- | Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に分類する。
buildPipeline :: Maybe Int -> ClassWeight -> Pipeline
buildPipeline =
  Pipeline
    [ groupMedianImputer "Age" ["Pclass", "Sex"]
    , mostFrequentImputer "Embarked"
    , dummyEncoder ["Sex", "Embarked"]
    ]

{- | 訓練データで前処理とモデルを学習する。

前処理は、前の前処理で変換したデータで学習する。順番を取り違えると、
ダミー変数化のあとの列名で補完しようとして失敗する。
-}
fitPipeline :: Pipeline -> Frame -> [Int] -> Either String FittedPipeline
fitPipeline pipeline x t = do
  (fitted, prepared) <- foldl' step (Right ([], x)) (pipelineSteps pipeline)
  features <- toFeatures prepared
  tree <-
    fitTree
      features
      t
      (frameColumns prepared)
      (pipelineMaxDepth pipeline)
      (pipelineClassWeight pipeline)
  pure (FittedPipeline fitted (frameColumns prepared) tree)
 where
  step acc definition = do
    (fitted, prepared) <- acc
    learned <- fitStep definition prepared
    next <- applyStep learned prepared
    pure (fitted <> [learned], next)

-- | 学習済みの前処理を順に合成して、データを変換する。
transform :: FittedPipeline -> Frame -> Either String Frame
transform fitted x = foldl' (\acc step -> acc >>= applyStep step) (Right x) (fittedSteps fitted)

-- | 前処理をしてから、1 行ごとのラベル（1 が生存、0 が死亡）を予測する。
predictPipeline :: FittedPipeline -> Frame -> Either String [Int]
predictPipeline fitted x = do
  prepared <- transform fitted x
  features <- toFeatures prepared {frameColumns = fittedColumns fitted}
  predictTree (fittedTree fitted) features

-- | 学習済みのパイプラインを保存する。形式の版を先頭に書く。
saveModel :: FittedPipeline -> FilePath -> IO ()
saveModel fitted file = do
  createDirectoryIfMissing True (takeDirectory file)
  BL.writeFile file (encode (formatVersion, fitted))

{- | 保存したパイプラインを読み込む。

@decodeOrFail@ を使うので、モデルでないファイルを渡しても例外にならず
'Left' が返る。形式の版が違えばそのことを返す。
-}
loadModel :: FilePath -> IO (Either String FittedPipeline)
loadModel file = do
  contents <- BL.readFile file
  pure $ case decodeOrFail contents of
    Left _ -> Left ("モデルとして読めません: " <> file)
    Right (rest, _, (version, fitted))
      | not (BL.null rest) -> Left ("モデルとして読めません: " <> file)
      | version /= formatVersion ->
          Left (printf "対応していない形式のモデルです: %d" (version :: Int))
      | otherwise -> Right fitted

-- | 正解ラベルの文字列を整数にする。
labelValues :: [Text] -> Either String [Int]
labelValues = traverse readLabel
 where
  readLabel value = case reads (T.unpack (T.strip value)) of
    [(label, rest)] | all (== ' ') rest -> Right label
    _ -> Left (T.unpack targetColumn <> " を数値として読めません: " <> T.unpack value)

-- | 表の 1 行を、列名から文字列への対応にする。
rowToMap :: [Text] -> Row -> Either String (M.Map Text Text)
rowToMap columns row = M.fromList <$> traverse (\column -> (,) column <$> text row column) columns

-- | Survived.csv を読み込み、訓練データとテストデータに分ける。
prepareSurvived
  :: BL.ByteString
  -> Double
  -> Int
  -> Either String (Split (M.Map Text Text))
prepareSurvived contents testSize seed = do
  table <- loadTable contents
  rows <- traverse (rowToMap (tableColumns table)) (tableRows table)
  splitTrainTest rows (map (`cell` targetColumn) rows) testSize seed

-- | 学習済みのパイプラインを、訓練データとテストデータで評価する。
evaluate :: FittedPipeline -> Split (M.Map Text Text) -> Either String Evaluation
evaluate fitted split = do
  trainLabels <- labelValues (tTrain split)
  testLabels <- labelValues (tTest split)
  trainPredictions <- predictPipeline fitted (Frame featureColumns (xTrain split))
  testPredictions <- predictPipeline fitted (Frame featureColumns (xTest split))
  train <- accuracy trainPredictions trainLabels
  test <- accuracy testPredictions testLabels
  pure
    Evaluation
      { trainAccuracy = train
      , testAccuracy = test
      , foundSurvivors =
          length
            [() | (p, l) <- zip testPredictions testLabels, p == survived, l == survived]
      , survivors = length (filter (== survived) testLabels)
      }

-- | 予測が正解ラベルと一致した割合。
accuracy :: [Int] -> [Int] -> Either String Double
accuracy predictions labels
  | length predictions /= length labels =
      Left (printf "予測と正解ラベルの件数が違います: %d と %d" (length predictions) (length labels))
  | null labels = Left "正解ラベルがありません"
  | otherwise =
      Right
        ( fromIntegral (length (filter id (zipWith (==) predictions labels)))
            / fromIntegral (length labels)
        )

-- | クラスの重みごとの評価結果を表示し、学習済みのパイプラインを保存して読み込む。
run :: IO (Either String String)
run = do
  file <- Dataset.path "Survived.csv"
  contents <- BL.readFile file
  report contents modelFile

{- | 読み込んだ中身から報告の文字列を作る。

保存と読み込みがあるので、この関数だけは 'IO' になる。
学習と評価そのものは純粋関数のまま。
-}
report :: BL.ByteString -> FilePath -> IO (Either String String)
report contents file =
  case prepared of
    Left err -> pure (Left err)
    Right (split, allLabels, balanced, plainResult, balancedResult) -> do
      saveModel balanced file
      loaded <- loadModel file
      pure $ do
        prediction <- (`predictPipeline` newPassengers) =<< loaded
        let alive = length (filter (== survived) allLabels)
        pure $
          concat
            [ printf
                "データ件数: %d（生存 %d, 死亡 %d）\n"
                (length allLabels)
                alive
                (length allLabels - alive)
            , printf
                "訓練データ: %d 件, テストデータ: %d 件\n"
                (length (xTrain split))
                (length (xTest split))
            , line "none" plainResult
            , line "balanced" balancedResult
            , printf
                "保存したモデル: %s\n"
                (if loaded == Right balanced then "読み込めました" else "読み込めません" :: String)
            , printf
                "架空の乗客の予測: %s\n"
                (T.unpack (T.intercalate ", " (map (T.pack . show) prediction)))
            ]
 where
  prepared = do
    split <- prepareSurvived contents 0.2 0
    allLabels <- labelValues (tTrain split <> tTest split)
    trainLabels <- labelValues (tTrain split)
    let trainFrame = Frame featureColumns (xTrain split)
    plain <- fitPipeline (buildPipeline (Just 5) NoWeight) trainFrame trainLabels
    balanced <- fitPipeline (buildPipeline (Just 5) Balanced) trainFrame trainLabels
    plainResult <- evaluate plain split
    balancedResult <- evaluate balanced split
    pure (split, allLabels, balanced, plainResult, balancedResult)

  line name result =
    printf
      "classWeight=%s: 訓練 %.3f, テスト %.3f, 生存者 %d 人中 %d 人を発見\n"
      (name :: String)
      (trainAccuracy result)
      (testAccuracy result)
      (survivors result)
      (foundSurvivors result)

-- | 年齢が分からない架空の乗客 2 人（1 等客室の女性と、3 等客室の男性）。
newPassengers :: Frame
newPassengers =
  Frame
    featureColumns
    [ M.fromList (zip featureColumns ["1", "female", "", "0", "0", "50", "C"])
    , M.fromList (zip featureColumns ["3", "male", "", "0", "0", "8", "S"])
    ]

-- | 小数を文字列にする。補完した値をそのまま列に書き戻すために使う。
showDouble :: Double -> Text
showDouble = T.pack . show

{- | 'Text' を保存する。

@binary@ は @text@ に依存していないので、'Text' の 'Binary' の実装が無い。
孤児インスタンスを足す代わりに、文字列に直して読み書きする。
-}
putText :: Text -> Put
putText = put . T.unpack

-- | 'Text' を読み込む。
getText :: Get Text
getText = T.pack <$> get

instance Binary Rule where
  put (Rule feature threshold) = putText feature >> put threshold
  get = Rule <$> getText <*> get

instance Binary Tree where
  put (Leaf label) = putWord8 0 >> put label
  put (Branch rule left right) = putWord8 1 >> put rule >> put left >> put right
  get = do
    tag <- getWord8
    case tag of
      0 -> Leaf <$> get
      1 -> Branch <$> get <*> get <*> get
      _ -> fail "知らない木の形です"

instance Binary FittedStep where
  put (FittedGroupMedian column by medians overall) =
    putWord8 0
      >> putText column
      >> put (map T.unpack by)
      >> put [(map T.unpack group, value) | (group, value) <- M.toList medians]
      >> put overall
  put (FittedMostFrequent column value) = putWord8 1 >> putText column >> putText value
  put (FittedDummy dummies) =
    putWord8 2
      >> put [(T.unpack column, map T.unpack categories) | (column, categories) <- dummies]

  get = do
    tag <- getWord8
    case tag of
      0 ->
        FittedGroupMedian
          <$> getText
          <*> (map T.pack <$> get)
          <*> (M.fromList . map (first (map T.pack)) <$> get)
          <*> get
      1 -> FittedMostFrequent <$> getText <*> getText
      2 ->
        FittedDummy . map (bimap T.pack (map T.pack)) <$> get
      _ -> fail "知らない前処理です"

instance Binary FittedPipeline where
  put (FittedPipeline steps columns tree) =
    put steps >> put (map T.unpack columns) >> put tree
  get = FittedPipeline <$> get <*> (map T.pack <$> get) <*> get
