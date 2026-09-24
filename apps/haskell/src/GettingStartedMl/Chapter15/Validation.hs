{-# LANGUAGE OverloadedStrings #-}

{- | 第 15 章のプレゼンテーション層。要求の JSON を読み、検証してドメインの値にする。

aeson は JSON をそのまま @Value@ という直和型にするので、@FromJSON@ を書けば
読み込みと同時に型を確かめられる。ただしこの API は「型が違えば 422、
値が範囲の外でも 422 で理由を並べる」という二段の振る舞いを求めるので、
@FromJSON@ には頼らず、型の確認と値の検証を自分で書く。

この層は純粋関数だけでできている。'IO' も HTTP も出てこない。
-}
module GettingStartedMl.Chapter15.Validation (
  FieldType (..),
  invalidJson,
  movieTypes,
  passengerTypes,
  readJson,
  movie,
  passenger,
) where

import qualified Data.Aeson as A
import qualified Data.Aeson.Key as K
import qualified Data.Aeson.KeyMap as KM
import qualified Data.ByteString.Lazy as BL
import qualified Data.Map.Strict as M
import Data.Maybe (catMaybes, fromMaybe)
import Data.Scientific (isInteger, toBoundedInteger, toRealFloat)
import Data.Text (Text)
import qualified Data.Text as T
import GettingStartedMl.Chapter15.Domain (Movie (..), Passenger (..))

-- | 要求の列に許す JSON の型。
data FieldType
  = -- | 整数でも小数でもよい
    NumberField
  | -- | 整数だけ。@1.5@ を弾くために分ける
    IntegerField
  | StringField
  deriving (Eq, Show)

-- | JSON そのものが読めないときの理由。列ごとの理由とは別にする。
invalidJson :: Text
invalidJson = "JSON の形式または値の型が正しくありません"

-- | 興行収入の予測の要求の列と型。原作の有無は整数で受け取る。
movieTypes :: M.Map Text FieldType
movieTypes =
  M.fromList
    [ ("sns1", NumberField)
    , ("sns2", NumberField)
    , ("actor", NumberField)
    , ("original", IntegerField)
    ]

-- | 生存の予測の要求の列と型。年齢と乗船港は省略できる。
passengerTypes :: M.Map Text FieldType
passengerTypes =
  M.fromList
    [ ("pclass", IntegerField)
    , ("sex", StringField)
    , ("age", NumberField)
    , ("sib_sp", IntegerField)
    , ("parch", IntegerField)
    , ("fare", NumberField)
    , ("embarked", StringField)
    ]

{- | 本文を JSON のオブジェクトとして読み、列の型を確かめる。

aeson の @Value@ は @Object@・@Array@・@String@・@Number@・@Bool@・@Null@ の
直和型なので、**オブジェクトと配列を取り違えようがない**。PHP 版が
@array_is_list@ で配列かどうかを見分けていたところが、ここではパターン 1 つで済む。
-}
readJson :: M.Map Text FieldType -> BL.ByteString -> Either [Text] A.Object
readJson types body = case A.decode body of
  Just (A.Object fields) | all typed (KM.toList fields) -> Right fields
  _ -> Left [invalidJson]
 where
  typed (key, value) = matches (M.lookup (K.toText key) types) value

  -- null と、表に無い列（型が Nothing）は問わない。
  matches _ A.Null = True
  matches Nothing _ = True
  matches (Just NumberField) (A.Number _) = True
  matches (Just IntegerField) (A.Number n) = isInteger n
  matches (Just StringField) (A.String _) = True
  matches _ _ = False

-- | 列の値を読む。無いか null なら 'Nothing'。
field :: A.Object -> Text -> Maybe A.Value
field fields name = case KM.lookup (K.fromText name) fields of
  Just A.Null -> Nothing
  other -> other

-- | 列の小数を読む。
number :: A.Object -> Text -> Maybe Double
number fields name = case field fields name of
  Just (A.Number n) -> Just (toRealFloat n)
  _ -> Nothing

-- | 列の整数を読む。
integer :: A.Object -> Text -> Maybe Int
integer fields name = case field fields name of
  Just (A.Number n) -> toBoundedInteger n
  _ -> Nothing

-- | 列の文字列を読む。
string :: A.Object -> Text -> Maybe Text
string fields name = case field fields name of
  Just (A.String value) -> Just value
  _ -> Nothing

-- | 必須の列が空なら理由を返す。
required :: Text -> Maybe a -> Maybe Text
required name Nothing = Just (name <> " は必須です")
required _ (Just _) = Nothing

-- | 負の数なら理由を返す。空欄は問わない。
notNegative :: Text -> Maybe Double -> Maybe Text
notNegative name (Just value) | value < 0.0 = Just (name <> " は 0 以上にしてください")
notNegative _ _ = Nothing

{- | 選択肢の外の値なら理由を返す。空欄は問わない。

選択肢は並べた順のまま表示する。数を文字列にしてから比べるのは、
整数の列と文字列の列で同じ関数を使うためである。
-}
oneOf :: Text -> Maybe Text -> [Text] -> Maybe Text
oneOf _ Nothing _ = Nothing
oneOf name (Just value) allowed
  | value `elem` allowed = Nothing
  | otherwise = Just (name <> " は " <> T.intercalate "、" allowed <> " のどれかにしてください")

{- | 理由を集め、1 つも無ければ値を返す。

値は 'Right' のときにしか使われないので、遅延評価のおかげで
「理由があるのに値を作る」ことにならない。
-}
validate :: [Maybe Text] -> a -> Either [Text] a
validate reasons value = case catMaybes reasons of
  [] -> Right value
  errors -> Left errors

-- | 整数を選択肢と比べるための表示。
showInt :: Int -> Text
showInt = T.pack . show

-- | 検証して、正しければ映画の特徴量にする。
movie :: A.Object -> Either [Text] Movie
movie fields =
  validate
    [ required "sns1" sns1
    , required "sns2" sns2
    , required "actor" actor
    , required "original" original
    , notNegative "sns1" sns1
    , notNegative "sns2" sns2
    , notNegative "actor" actor
    , oneOf "original" (showInt <$> original) ["0", "1"]
    ]
    Movie
      { movieSns1 = orZero sns1
      , movieSns2 = orZero sns2
      , movieActor = orZero actor
      , movieOriginal = fromMaybe 0 original
      }
 where
  sns1 = number fields "sns1"
  sns2 = number fields "sns2"
  actor = number fields "actor"
  original = integer fields "original"
  orZero = fromMaybe 0.0

-- | 検証して、正しければ乗客の特徴量にする。年齢と乗船港は省略できる。
passenger :: A.Object -> Either [Text] Passenger
passenger fields =
  validate
    [ required "pclass" pclass
    , required "sex" sex
    , required "sib_sp" sibSp
    , required "parch" parch
    , required "fare" fare
    , oneOf "pclass" (showInt <$> pclass) ["1", "2", "3"]
    , oneOf "sex" sex ["female", "male"]
    , notNegative "age" age
    , notNegative "sib_sp" (fromIntegral <$> sibSp)
    , notNegative "parch" (fromIntegral <$> parch)
    , notNegative "fare" fare
    , oneOf "embarked" embarked ["C", "Q", "S"]
    ]
    Passenger
      { passengerPclass = fromMaybe 0 pclass
      , passengerSex = fromMaybe "" sex
      , passengerAge = age
      , passengerSibSp = fromMaybe 0 sibSp
      , passengerParch = fromMaybe 0 parch
      , passengerFare = fromMaybe 0.0 fare
      , passengerEmbarked = embarked
      }
 where
  pclass = integer fields "pclass"
  sex = string fields "sex"
  age = number fields "age"
  sibSp = integer fields "sib_sp"
  parch = integer fields "parch"
  fare = number fields "fare"
  embarked = string fields "embarked"
