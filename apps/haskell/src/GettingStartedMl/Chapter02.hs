{-# LANGUAGE OverloadedStrings #-}

{- | 第 2 章: データの前処理。

表の読み込み・欠損値の補完・訓練データとテストデータへの分割。
-}
module GettingStartedMl.Chapter02 (
  Table (..),
  Split (..),
  targetColumn,
  loadTable,
  countMissing,
  columnMeans,
  fillMissing,
  splitFeaturesAndTarget,
  splitTrainTest,
  prepareIris,
  run,
  report,
) where

import qualified Data.ByteString.Lazy as BL
import qualified Data.Map.Strict as M
import Data.Maybe (catMaybes)
import Data.Text (Text)
import qualified Data.Text as T
import GettingStartedMl.Csv (Row, optionalNumber, parseTable, text)
import qualified GettingStartedMl.Dataset as Dataset
import GettingStartedMl.Random (shuffle)
import Text.Printf (printf)

{- | 列名の並びと行のリストを持つ表。

'M.Map' はキーの順で並ぶので、CSV に現れた順を保ちたい列名は別に持つ。
-}
data Table = Table
  { tableColumns :: [Text]
  , tableRows :: [Row]
  }
  deriving (Eq, Show)

-- | 訓練データとテストデータ。
data Split a = Split
  { xTrain :: [a]
  , xTest :: [a]
  , tTrain :: [Text]
  , tTest :: [Text]
  }
  deriving (Eq, Show)

-- | アヤメのデータの正解ラベルの列。
targetColumn :: Text
targetColumn = "種類"

-- | CSV を読み込んで表にする。列の順は CSV の順のまま。
loadTable :: BL.ByteString -> Either String Table
loadTable contents = do
  (columns, rows) <- parseTable contents
  pure (Table columns rows)

-- | 列ごとに欠損値の数を数える。列の順は表の列の順のまま。
countMissing :: Table -> Either String [(Text, Int)]
countMissing table = traverse count (tableColumns table)
 where
  count column = do
    cells <- traverse (`text` column) (tableRows table)
    pure (column, length (filter (T.null . T.strip) cells))

-- | 欠損値を除いて、列ごとの平均値を求める。
columnMeans :: [Row] -> [Text] -> Either String (M.Map Text Double)
columnMeans rows columns = M.fromList <$> traverse mean columns
 where
  mean column = do
    values <- traverse (`optionalNumber` column) rows
    case catMaybes values of
      [] -> Left ("値がすべて空欄です: " <> T.unpack column)
      found -> Right (column, sum found / fromIntegral (length found))

-- | 欠損値を列ごとに指定した値で補完し、特徴量にする。
fillMissing :: [Row] -> [Text] -> M.Map Text Double -> Either String [M.Map Text Double]
fillMissing rows columns fillValues = traverse fillRow rows
 where
  fillRow row = M.fromList <$> traverse (fillColumn row) columns

  fillColumn row column = do
    value <- optionalNumber row column
    case value of
      Just v -> Right (column, v)
      Nothing -> case M.lookup column fillValues of
        Just v -> Right (column, v)
        Nothing -> Left ("補完する値がありません: " <> T.unpack column)

-- | 正解ラベルの列を取り出し、残りの列を特徴量の列にする。
splitFeaturesAndTarget :: Table -> Text -> Either String ([Text], [Row], [Text])
splitFeaturesAndTarget table target = do
  labels <- traverse (`text` target) (tableRows table)
  pure (filter (/= target) (tableColumns table), tableRows table, labels)

-- | 並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。
splitTrainTest :: [a] -> [Text] -> Double -> Int -> Either String (Split a)
splitTrainTest x t testSize seed
  | length x /= length t =
      Left (printf "件数が違います: %d と %d" (length x) (length t))
  | otherwise =
      let pairs = shuffle (zip x t) seed
          total = length pairs
          trainCount = total - ceiling (fromIntegral total * testSize :: Double)
          (train, test) = splitAt trainCount pairs
       in Right
            Split
              { xTrain = map fst train
              , xTest = map fst test
              , tTrain = map snd train
              , tTest = map snd test
              }

-- | iris.csv の中身を読み込み、分割してから訓練データの平均値で両方を補完する。
prepareIris :: BL.ByteString -> Double -> Int -> Either String (Split (M.Map Text Double))
prepareIris contents testSize seed = do
  table <- loadTable contents
  (columns, rows, labels) <- splitFeaturesAndTarget table targetColumn
  split <- splitTrainTest rows labels testSize seed
  means <- columnMeans (xTrain split) columns
  filledTrain <- fillMissing (xTrain split) columns means
  filledTest <- fillMissing (xTest split) columns means
  pure split {xTrain = filledTrain, xTest = filledTest}

-- | アヤメのデータの前処理の結果を表示する。
run :: IO (Either String String)
run = do
  file <- Dataset.path "iris.csv"
  contents <- BL.readFile file
  pure (report contents)

-- | 読み込んだ中身から報告の文字列を作る。純粋関数なのでテストで確かめられる。
report :: BL.ByteString -> Either String String
report contents = do
  table <- loadTable contents
  missing <- countMissing table
  split <- prepareIris contents 0.3 0
  let features = filter (/= targetColumn) (tableColumns table)
      missingText = T.intercalate ", " [column <> "=" <> T.pack (show n) | (column, n) <- missing]
  pure $
    printf
      "データ件数: %d\n欠損値の数: %s\n訓練データ: %d 件, テストデータ: %d 件\n特徴量: %s\n"
      (length (tableRows table))
      (T.unpack missingText)
      (length (xTrain split))
      (length (xTest split))
      (T.unpack (T.intercalate ", " features))
