{-# LANGUAGE OverloadedStrings #-}

{- | 第 15 章のドメイン層。予測の入力・出力と、既存の章のモデルを約束に合わせるアダプター。

HTTP にも保存の形式にも依存しない。'IO' も出てこない。

モデルの約束は「特徴量を受け取って予測を返す関数」で足りる。Haskell の関数は
そのまま値なので、型クラスを作らずに @newtype@ で包むだけで約束を表せる。
包むのは、裸の関数だと引数と戻り値の型が同じ別の関数と取り違えられるためである。
-}
module GettingStartedMl.Chapter15.Domain (
  Movie (..),
  Passenger (..),
  SalesModel (..),
  SurvivalModel (..),
  salesModelName,
  survivalModelName,
  modelNames,
  linearSalesModel,
  pipelineSurvivalModel,
) where

import qualified Data.Map.Strict as M
import Data.Maybe (fromMaybe)
import Data.Text (Text)
import qualified Data.Text as T
import qualified GettingStartedMl.Chapter07 as Chapter07
import qualified GettingStartedMl.Chapter08 as Chapter08

-- | 興行収入を予測したい映画。
data Movie = Movie
  { movieSns1 :: Double
  , movieSns2 :: Double
  , movieActor :: Double
  , movieOriginal :: Int
  }
  deriving (Eq, Show)

-- | 生存を予測したい乗客。年齢と乗船港は分からないことがあるので 'Maybe' にする。
data Passenger = Passenger
  { passengerPclass :: Int
  , passengerSex :: Text
  , passengerAge :: Maybe Double
  , passengerSibSp :: Int
  , passengerParch :: Int
  , passengerFare :: Double
  , passengerEmbarked :: Maybe Text
  }
  deriving (Eq, Show)

-- | 映画から興行収入を返す約束。失敗は 'Either' で表す。
newtype SalesModel = SalesModel {predictSalesWith :: Movie -> Either String Double}

-- | 乗客から生存するかどうかを返す約束。
newtype SurvivalModel = SurvivalModel {predictSurvivalWith :: Passenger -> Either String Bool}

-- | 興行収入のモデルの名前。
salesModelName :: Text
salesModelName = "cinema"

-- | 生存予測のモデルの名前。
survivalModelName :: Text
survivalModelName = "survived"

-- | モデルの名前を置き場の順に並べたリスト。ヘルスチェックの並びもこれに従う。
modelNames :: [Text]
modelNames = [salesModelName, survivalModelName]

-- | 生存を表すラベル。
survivedLabel :: Int
survivedLabel = 1

{- | 第 7 章の線形回帰のモデルを、興行収入のモデルの約束に合わせる。

第 7 章にも第 8 章にも 1 行も手を入れていない。既存の型を約束の形に
包み直すのがアダプターの役目である。
-}
linearSalesModel :: Chapter07.LinearModel -> SalesModel
linearSalesModel model = SalesModel $ \m ->
  Chapter07.predictOne
    model
    ( M.fromList
        [ ("SNS1", movieSns1 m)
        , ("SNS2", movieSns2 m)
        , ("actor", movieActor m)
        , ("original", fromIntegral (movieOriginal m))
        ]
    )

-- | 第 8 章の学習済みパイプラインを、生存予測のモデルの約束に合わせる。
pipelineSurvivalModel :: Chapter08.FittedPipeline -> SurvivalModel
pipelineSurvivalModel pipeline = SurvivalModel $ \p -> do
  predictions <-
    Chapter08.predictPipeline
      pipeline
      (Chapter08.Frame Chapter08.featureColumns [passengerRow p])
  case predictions of
    [label] -> Right (label == survivedLabel)
    _ -> Left "予測の件数が 1 件ではありません"

{- | 乗客を、第 8 章のパイプラインが読む CSV と同じセルの文字列の行にする。

分からない値は空欄にすると、学習のときに求めた中央値・最頻値で補完される。
-}
passengerRow :: Passenger -> M.Map Text Text
passengerRow p =
  M.fromList
    [ ("Pclass", T.pack (show (passengerPclass p)))
    , ("Sex", passengerSex p)
    , ("Age", maybe "" (T.pack . show) (passengerAge p))
    , ("SibSp", T.pack (show (passengerSibSp p)))
    , ("Parch", T.pack (show (passengerParch p)))
    , ("Fare", T.pack (show (passengerFare p)))
    , ("Embarked", fromMaybe "" (passengerEmbarked p))
    ]
