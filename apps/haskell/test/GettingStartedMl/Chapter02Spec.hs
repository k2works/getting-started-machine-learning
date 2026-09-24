{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.Chapter02Spec (spec) where

import Control.Monad (unless)
import qualified Data.ByteString.Lazy as BL
import qualified Data.Map.Strict as M
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import GettingStartedMl.Chapter02 (
  Split (..),
  Table (..),
  columnMeans,
  countMissing,
  fillMissing,
  loadTable,
  prepareIris,
  report,
  splitFeaturesAndTarget,
  splitTrainTest,
  targetColumn,
 )
import qualified GettingStartedMl.Dataset as Dataset
import Test.Hspec

spec :: Spec
spec = do
  describe "欠損値" $ do
    it "空欄は欠損値として数える" $ do
      let table = expectRight $ loadTable (utf8Csv "a,b\n1,\n,2\n3,4\n")
      countMissing table `shouldBe` Right [("a", 1), ("b", 1)]

    it "欠損値を除いて平均値を求める" $ do
      let table = expectRight $ loadTable (utf8Csv "a\n1\n\n3\n")
      columnMeans (tableRows table) ["a"] `shouldBe` Right (M.fromList [("a", 2.0)])

    it "値がすべて空欄なら平均値を求められない" $ do
      let table = expectRight $ loadTable (utf8Csv "a\n \n \n")
      columnMeans (tableRows table) ["a"] `shouldBe` Left "値がすべて空欄です: a"

    it "欠損値を指定した値で補完する" $ do
      -- 1 列だけの CSV では「空欄の行」と「空行」を区別できない（空行は読み飛ばす）
      -- ので、2 列にして片方だけを空欄にする。
      let table = expectRight $ loadTable (utf8Csv "a,b\n1,x\n,y\n")
      fillMissing (tableRows table) ["a"] (M.fromList [("a", 9.0)])
        `shouldBe` Right [M.fromList [("a", 1.0)], M.fromList [("a", 9.0)]]

    it "補完する値が無ければ失敗する" $ do
      let table = expectRight $ loadTable (utf8Csv "a,b\n,y\n")
      fillMissing (tableRows table) ["a"] M.empty
        `shouldBe` Left "補完する値がありません: a"

  describe "特徴量と正解ラベル" $ do
    it "正解ラベルの列を特徴量から外す" $ do
      let table = expectRight $ loadTable (utf8Csv "a,種類\n1,setosa\n")
      fmap (\(c, _, l) -> (c, l)) (splitFeaturesAndTarget table targetColumn)
        `shouldBe` Right (["a"], ["setosa"])

  describe "訓練データとテストデータの分割" $ do
    it "テストデータの割合は切り上げる" $ do
      let x = [1 .. 10] :: [Int]
          t = map (T.pack . show) x
          split = expectRight $ splitTrainTest x t 0.25 0
      -- 10 件の 25% は 2.5 なので、切り上げて 3 件がテストデータになる。
      (length (xTrain split), length (xTest split)) `shouldBe` (7, 3)

    it "件数が違えば分割できない" $
      splitTrainTest [1 :: Int, 2] ["t"] 0.5 0
        `shouldBe` (Left "件数が違います: 2 と 1" :: Either String (Split Int))

    it "分割しても特徴量とラベルの組が崩れない" $ do
      let x = [1 .. 10] :: [Int]
          t = map (T.pack . show) x
          split = expectRight $ splitTrainTest x t 0.3 0
      map (T.pack . show) (xTrain split) `shouldBe` tTrain split

  describe "前処理" $ do
    it "補完に使う平均値は訓練データだけから求める" $ do
      let table = expectRight $ loadTable (utf8Csv "a\n1\n3\n")
          means = expectRight $ columnMeans (tableRows table) ["a"]
          blank = expectRight $ loadTable (utf8Csv "a,b\n,y\n")
      -- 訓練データだけの平均（1 と 3 の平均＝2.0）で補完する。
      means `shouldBe` M.fromList [("a", 2.0)]
      fillMissing (tableRows blank) ["a"] means
        `shouldBe` Right [M.fromList [("a", 2.0)]]

    it "前処理のあとに空欄が残らない" $ do
      let csv = utf8Csv "がく片長さ,種類\n1,setosa\n3,setosa\n,versicolor\n5,versicolor\n"
          split = expectRight $ prepareIris csv 0.25 0
          values = concatMap M.elems (xTrain split <> xTest split)
      length values `shouldBe` 4

  describe "実データ" $ do
    it "前処理の結果がほかの言語版と一致する" $ do
      found <- Dataset.exists "iris.csv"
      unless found $ pendingWith "学習データがありません"
      file <- Dataset.path "iris.csv"
      contents <- BL.readFile file
      let split = expectRight $ prepareIris contents 0.3 0
          values = [v | row <- xTrain split, Just v <- [M.lookup "がく片長さ" row]]
          mean = sum values / fromIntegral (length values)
      (length (xTrain split), length (xTest split)) `shouldBe` (105, 45)
      -- Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版と一致する。
      abs (mean - 0.4215384615384616) < 1e-15 `shouldBe` True

    it "前処理の結果を表示する" $ do
      found <- Dataset.exists "iris.csv"
      unless found $ pendingWith "学習データがありません"
      file <- Dataset.path "iris.csv"
      contents <- BL.readFile file
      report contents
        `shouldBe` Right
          "データ件数: 150\n欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0\n訓練データ: 105 件, テストデータ: 45 件\n特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅\n"

{- | テストの中で「成功しているはず」の値を取り出す。

@let Right table = ...@ と書くと、'Left' の場合を書いていないので
@-Wincomplete-uni-patterns@ が止める。テストの中でも失敗の場合を
無視できないので、失敗したらその場で落ちる関数にして明示する。
-}
expectRight :: Either String a -> a
expectRight (Right value) = value
expectRight (Left err) = error ("失敗しました: " <> err)

-- | UTF-8 の CSV をテストのために組み立てる。
utf8Csv :: T.Text -> BL.ByteString
utf8Csv = BL.fromStrict . TE.encodeUtf8
