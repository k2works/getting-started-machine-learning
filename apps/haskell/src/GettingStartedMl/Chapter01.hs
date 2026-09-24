{-# LANGUAGE OverloadedStrings #-}

-- | 第 1 章: 人間が決めたルールできのこ派・たけのこ派を判定する。
module GettingStartedMl.Chapter01 (
  Faction (..),
  Person (..),
  Features (..),
  kinokoAgeGroup,
  factionName,
  parseFaction,
  loadPeople,
  splitFeaturesAndLabels,
  predictByRule,
  accuracy,
  run,
) where

import qualified Data.ByteString.Lazy as BL
import Data.Text (Text)
import qualified Data.Text as T
import GettingStartedMl.Csv (Row, column, number, parseRows)
import qualified GettingStartedMl.Dataset as Dataset
import Text.Printf (printf)

-- | 派閥。取りうる値を型で数え上げるので、綴りの間違いはコンパイルで止まる。
data Faction
  = Kinoko
  | Takenoko
  deriving (Eq, Show)

-- | 学習データ KvsT.csv の 1 行。身長・体重・年代と、正解ラベルの派閥を持つ。
data Person = Person
  { personHeight :: Int
  , personWeight :: Int
  , personAgeGroup :: Int
  , personFaction :: Faction
  }
  deriving (Eq, Show)

-- | 判定の手がかりになる特徴量。正解ラベルを持たない。
data Features = Features
  { featuresHeight :: Int
  , featuresWeight :: Int
  , featuresAgeGroup :: Int
  }
  deriving (Eq, Show)

-- | 「20 代ならきのこ派」というルールの年代。
kinokoAgeGroup :: Int
kinokoAgeGroup = 20

-- | 派閥の呼び名。
factionName :: Faction -> Text
factionName Kinoko = "きのこ"
factionName Takenoko = "たけのこ"

-- | 呼び名から派閥を読む。知らない値は 'Left' で返す。
parseFaction :: Text -> Either String Faction
parseFaction cell
  | cell == factionName Kinoko = Right Kinoko
  | cell == factionName Takenoko = Right Takenoko
  | otherwise = Left ("派閥を読めません: " <> T.unpack cell)

-- | CSV を読み込み、列名で値を取り出して人物のリストにする。
loadPeople :: BL.ByteString -> Either String [Person]
loadPeople contents = parseRows contents >>= traverse toPerson

-- | 1 行を人物にする。どれか 1 つでも失敗すれば全体が 'Left' になる。
toPerson :: Row -> Either String Person
toPerson row =
  Person
    <$> number row "身長"
    <*> number row "体重"
    <*> number row "年代"
    <*> (column row "派閥" >>= parseFaction)

-- | 人物のリストを特徴量と正解ラベルに分ける。
splitFeaturesAndLabels :: [Person] -> ([Features], [Faction])
splitFeaturesAndLabels people = (map toFeatures people, map personFaction people)
 where
  toFeatures person =
    Features
      { featuresHeight = personHeight person
      , featuresWeight = personWeight person
      , featuresAgeGroup = personAgeGroup person
      }

-- | 人間が決めたルールで派閥を判定する。
predictByRule :: Features -> Faction
predictByRule features
  | featuresAgeGroup features == kinokoAgeGroup = Kinoko
  | otherwise = Takenoko

{- | 予測が正解ラベルと一致した割合を返す。件数が違えば 'Left' を返す。

「同じかどうかを比べられる」ことだけが要るので、派閥に固定せず 'Eq' の型クラスで
書く。こうしておくと、第 3 章で決定木の予測（文字列のラベル）を測るときも
そのまま使える。
-}
accuracy :: (Eq a) => [a] -> [a] -> Either String Double
accuracy predictions labels
  | length predictions /= length labels =
      Left (printf "予測と正解ラベルの件数が違います: %d と %d" (length predictions) (length labels))
  | null labels = Left "正解ラベルがありません"
  | otherwise =
      Right (fromIntegral hits / fromIntegral (length labels))
 where
  hits = length (filter id (zipWith (==) predictions labels))

{- | 実データでルールによる判定の正解率を表示する。

ファイルを読むところだけが 'IO' で、そこから先は純粋関数になっている。
-}
run :: IO (Either String String)
run = do
  file <- Dataset.path "KvsT.csv"
  contents <- BL.readFile file
  pure (report contents)

-- | 読み込んだ中身から報告の文字列を作る。純粋関数なのでテストで確かめられる。
report :: BL.ByteString -> Either String String
report contents = do
  people <- loadPeople contents
  let (x, t) = splitFeaturesAndLabels people
  rate <- accuracy (map predictByRule x) t
  pure (printf "データ件数: %d\nルールによる判定の正解率: %.4f\n" (length people) rate)
