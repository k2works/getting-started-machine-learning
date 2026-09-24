{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.Chapter01Spec (spec) where

import Control.Monad (unless)
import qualified Data.ByteString.Lazy as BL
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import GettingStartedMl.Chapter01 (
  Faction (..),
  Features (..),
  Person (..),
  accuracy,
  loadPeople,
  predictByRule,
  run,
  splitFeaturesAndLabels,
 )
import qualified GettingStartedMl.Dataset as Dataset
import Test.Hspec

spec :: Spec
spec = do
  describe "ルールによる判定" $ do
    it "二十代はきのこ派と判定する" $
      predictByRule (Features 170 60 20) `shouldBe` Kinoko

    it "二十代以外はたけのこ派と判定する" $
      map (predictByRule . Features 170 60) [10, 30, 40, 50]
        `shouldBe` [Takenoko, Takenoko, Takenoko, Takenoko]

  describe "正解率" $ do
    it "全部当たれば正解率は一になる" $
      accuracy [Kinoko, Takenoko] [Kinoko, Takenoko] `shouldBe` Right 1.0

    it "半分当たれば正解率は零点五になる" $
      accuracy [Kinoko, Kinoko] [Kinoko, Takenoko] `shouldBe` Right 0.5

    it "件数が違えば正解率を求められない" $
      accuracy [Kinoko] [Kinoko, Takenoko]
        `shouldBe` Left "予測と正解ラベルの件数が違います: 1 と 2"

    it "正解ラベルが無ければ正解率を求められない" $
      -- accuracy は (Eq a) => なので、空のリストだけでは型が決まらない。
      -- 何の正解率を測っているのかを型注釈で示す。
      accuracy ([] :: [Faction]) [] `shouldBe` Left "正解ラベルがありません"

  describe "特徴量と正解ラベルに分ける" $ do
    it "人物のリストを特徴量と正解ラベルに分ける" $
      splitFeaturesAndLabels
        [ Person 170 60 20 Kinoko
        , Person 160 50 30 Takenoko
        ]
        `shouldBe` ([Features 170 60 20, Features 160 50 30], [Kinoko, Takenoko])

  describe "CSV の読み込み" $ do
    it "BOM 付きの CSV を列名で読み込む" $ do
      let csv = utf8Csv "\65279身長,体重,年代,派閥\n170,60,20,きのこ\n"
      loadPeople csv `shouldBe` Right [Person 170 60 20 Kinoko]

    it "数値でない値があれば読み込めない" $ do
      let csv = utf8Csv "身長,体重,年代,派閥\n高い,60,20,きのこ\n"
      loadPeople csv `shouldBe` Left "身長 を数値として読めません: 高い"

    it "列が無ければ読み込めない" $ do
      let csv = utf8Csv "身長,体重,年代\n170,60,20\n"
      loadPeople csv `shouldBe` Left "列がありません: 派閥"

    it "知らない派閥は読み込めない" $ do
      let csv = utf8Csv "身長,体重,年代,派閥\n170,60,20,ぶどう\n"
      loadPeople csv `shouldBe` Left "派閥を読めません: ぶどう"

    it "空行は読み飛ばす" $ do
      let csv = utf8Csv "身長,体重,年代,派閥\n170,60,20,きのこ\n\n"
      fmap length (loadPeople csv) `shouldBe` Right 1

  describe "実データ" $ do
    it "ルールによる判定の正解率がほかの言語版と一致する" $ do
      -- 学習データは配布物なのでリポジトリに無い。無ければこのテストは外す。
      found <- Dataset.exists "KvsT.csv"
      unless found $ pendingWith "学習データがありません"
      actual <- run
      actual `shouldBe` Right "データ件数: 19\nルールによる判定の正解率: 0.7368\n"

{- | UTF-8 の CSV をテストのために組み立てる。

'Data.ByteString.Lazy.Char8' の @pack@ は 1 文字を 1 バイトに詰めるので、
日本語をそのまま渡すと壊れたバイト列になる。テストのデータでも必ず
UTF-8 として符号化する。
-}
utf8Csv :: T.Text -> BL.ByteString
utf8Csv = BL.fromStrict . TE.encodeUtf8
