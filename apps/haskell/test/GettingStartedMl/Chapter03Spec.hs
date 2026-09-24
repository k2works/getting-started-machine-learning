{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.Chapter03Spec (spec) where

import Control.Monad (unless)
import qualified Data.ByteString.Lazy as BL
import qualified Data.Map.Strict as M
import Data.Text (Text)
import qualified GettingStartedMl.Chapter02 as C2
import GettingStartedMl.Chapter03 (
  Split (..),
  Tree (..),
  bestSplit,
  featureColumns,
  fit,
  formatTree,
  gini,
  majority,
  predict,
  predictOne,
  report,
  scoreTree,
 )
import qualified GettingStartedMl.Dataset as Dataset

-- Test.Hspec の fit は「この例だけを走らせる」印。名前が衝突するので隠す。
import Test.Hspec hiding (fit)

spec :: Spec
spec = do
  describe "ジニ不純度" $ do
    it "ラベルが一種類なら零になる" $
      gini ["a", "a", "a"] `shouldBe` 0.0

    it "二種類が半々なら零点五になる" $
      gini ["a", "a", "b", "b"] `shouldBe` 0.5

    it "空なら零になる" $
      gini [] `shouldBe` 0.0

    it "三種類が均等なら三分の二になる" $
      abs (gini ["a", "b", "c"] - 2 / 3) < 1e-12 `shouldBe` True

  describe "いちばん多いラベル" $ do
    it "多数派のラベルを返す" $
      majority ["a", "b", "a"] `shouldBe` Right "a"

    it "同数なら先に現れたほうを返す" $
      majority ["b", "a", "a", "b"] `shouldBe` Right "b"

    it "ラベルが無ければ求められない" $
      majority [] `shouldBe` Left "正解ラベルがありません"

  describe "最良の分割" $ do
    it "ラベルを完全に分けられる境界を見つける" $ do
      let x = map (feature "花弁幅") [0.1, 0.2, 0.8, 0.9]
          t = ["setosa", "setosa", "virginica", "virginica"]
      bestSplit x t ["花弁幅"]
        `shouldBe` Right (Just (Split "花弁幅" 0.5 0.0))

    it "複数の特徴量から最良の特徴量と境界を選ぶ" $ do
      -- 「幅」だけでラベルが完全に分かれ、「長さ」では分かれない架空のデータ。
      let x =
            [ M.fromList [("長さ", 1.0), ("幅", 0.1)]
            , M.fromList [("長さ", 2.0), ("幅", 0.2)]
            , M.fromList [("長さ", 1.0), ("幅", 0.8)]
            , M.fromList [("長さ", 2.0), ("幅", 0.9)]
            ]
          t = ["a", "a", "b", "b"]
      bestSplit x t ["長さ", "幅"]
        `shouldBe` Right (Just (Split "幅" 0.5 0.0))

    it "ラベルが一種類なら分割しない" $ do
      let x = map (feature "幅") [0.1, 0.2]
      bestSplit x ["a", "a"] ["幅"] `shouldBe` Right Nothing

    it "値がすべて同じなら分割しない" $ do
      let x = map (feature "幅") [0.5, 0.5]
      bestSplit x ["a", "b"] ["幅"] `shouldBe` Right Nothing

    it "データが無ければ分割しない" $
      bestSplit [] [] ["幅"] `shouldBe` Right Nothing

    it "同じ不純度なら列の順で前の分割を選ぶ" $ do
      -- どちらの列でもラベルを完全に分けられる架空のデータ。
      let x =
            [ M.fromList [("左", 0.1), ("右", 0.1)]
            , M.fromList [("左", 0.9), ("右", 0.9)]
            ]
          t = ["a", "b"]
      fmap (fmap splitFeature) (bestSplit x t ["左", "右"]) `shouldBe` Right (Just "左")
      fmap (fmap splitFeature) (bestSplit x t ["右", "左"]) `shouldBe` Right (Just "右")

    it "列がなければ失敗する" $ do
      let x = map (feature "幅") [0.1, 0.9]
      bestSplit x ["a", "b"] ["長さ"]
        `shouldBe` (Left "列がありません: 長さ" :: Either String (Maybe Split))

  describe "学習と予測" $ do
    it "学習した木で訓練データを言い当てる" $ do
      let x = map (feature "幅") [0.1, 0.2, 0.8, 0.9]
          t = ["a", "a", "b", "b"]
          tree = expectRight $ fit x t ["幅"] Nothing
      predict tree x `shouldBe` Right t

    it "分けられなければ葉になる" $
      fit (map (feature "幅") [0.1, 0.2]) ["a", "a"] ["幅"] Nothing
        `shouldBe` Right (Leaf "a")

    it "境界の値は左へ進む" $ do
      let tree = Branch (Split "幅" 0.5 0.0) (Leaf "左") (Leaf "右")
      predictOne tree (feature "幅" 0.5) `shouldBe` Right "左"
      predictOne tree (feature "幅" 0.5000001) `shouldBe` Right "右"

    it "深さを制限すると分割の回数が減る" $ do
      let x = map (feature "幅") [0.1, 0.4, 0.6, 0.9]
          t = ["a", "b", "c", "d"]
          shallow = expectRight $ fit x t ["幅"] (Just 1)
          deep = expectRight $ fit x t ["幅"] Nothing
      depth shallow `shouldBe` 1
      depth deep `shouldBe` 3

    it "深さ零なら葉だけになる" $ do
      let x = map (feature "幅") [0.1, 0.9]
      fit x ["a", "b"] ["幅"] (Just 0) `shouldBe` Right (Leaf "a")

    it "件数が違えば学習できない" $
      fit [feature "幅" 0.1] ["a", "b"] ["幅"] Nothing
        `shouldBe` (Left "特徴量と正解ラベルの件数が違います: 1 と 2" :: Either String Tree)

    it "ラベルが無ければ学習できない" $
      fit [] [] ["幅"] Nothing `shouldBe` (Left "正解ラベルがありません" :: Either String Tree)

    it "予測する列がなければ失敗する" $ do
      let tree = Branch (Split "幅" 0.5 0.0) (Leaf "左") (Leaf "右")
      predictOne tree (feature "長さ" 0.5) `shouldBe` Left "列がありません: 幅"

  describe "木の表示" $ do
    it "葉はラベルだけを表示する" $
      formatTree (Leaf "setosa") `shouldBe` "setosa\n"

    it "節は境界と左右の部分木を字下げして表示する" $
      formatTree (Branch (Split "幅" 0.295 0.0) (Leaf "a") (Leaf "b"))
        `shouldBe` "幅 <= 0.2950\n  a\n幅 > 0.2950\n  b\n"

  describe "正解率" $ do
    it "木の予測と正解ラベルの一致率を返す" $ do
      let tree = Branch (Split "幅" 0.5 0.0) (Leaf "a") (Leaf "b")
          x = map (feature "幅") [0.1, 0.9]
      scoreTree tree x ["a", "a"] `shouldBe` Right 0.5

  describe "実データ" $ do
    it "深さごとの正解率がほかの言語版と一致する" $ do
      contents <- irisOrSkip
      let split = expectRight $ C2.prepareIris contents 0.3 0
          score maxDepth = do
            tree <- fit (C2.xTrain split) (C2.tTrain split) featureColumns maxDepth
            train <- scoreTree tree (C2.xTrain split) (C2.tTrain split)
            test <- scoreTree tree (C2.xTest split) (C2.tTest split)
            pure (round4 train, round4 test)
      traverse score [Just 1, Just 2, Just 3, Just 4, Just 5, Nothing]
        `shouldBe` Right
          [ (0.6762, 0.6444)
          , (0.9333, 0.9556)
          , (0.9524, 0.9556)
          , (0.9619, 0.9556)
          , (0.9810, 0.9333)
          , (1.0000, 0.9333)
          ]

    it "深さ二の決定木がほかの言語版と一致する" $ do
      contents <- irisOrSkip
      let split = expectRight $ C2.prepareIris contents 0.3 0
          tree = expectRight $ fit (C2.xTrain split) (C2.tTrain split) featureColumns (Just 2)
      formatTree tree
        `shouldBe` "花弁幅 <= 0.2950\n\
                   \  Iris-setosa\n\
                   \花弁幅 > 0.2950\n\
                   \  花弁幅 <= 0.6500\n\
                   \    Iris-versicolor\n\
                   \  花弁幅 > 0.6500\n\
                   \    Iris-virginica\n"

    it "深さごとの正解率と木を表示する" $ do
      contents <- irisOrSkip
      report contents
        `shouldBe` Right
          "深さ\t訓練データ\tテストデータ\n\
          \1\t0.6762\t0.6444\n\
          \2\t0.9333\t0.9556\n\
          \3\t0.9524\t0.9556\n\
          \4\t0.9619\t0.9556\n\
          \5\t0.9810\t0.9333\n\
          \制限なし\t1.0000\t0.9333\n\
          \\n深さ 2 の決定木:\n\
          \花弁幅 <= 0.2950\n\
          \  Iris-setosa\n\
          \花弁幅 > 0.2950\n\
          \  花弁幅 <= 0.6500\n\
          \    Iris-versicolor\n\
          \  花弁幅 > 0.6500\n\
          \    Iris-virginica\n"

-- | 1 つの列だけを持つ特徴量を作る。
feature :: Text -> Double -> M.Map Text Double
feature = M.singleton

-- | 木の深さ。葉を 0 とする。
depth :: Tree -> Int
depth (Leaf _) = 0
depth (Branch _ left right) = 1 + max (depth left) (depth right)

-- | 小数点以下 4 桁に丸める。記事に載せる表と同じ桁で比べる。
round4 :: Double -> Double
round4 value = fromIntegral (round (value * 10000) :: Int) / 10000

-- | 実データを読む。無ければテストを飛ばす。
irisOrSkip :: IO BL.ByteString
irisOrSkip = do
  found <- Dataset.exists "iris.csv"
  unless found $ pendingWith "学習データがありません"
  file <- Dataset.path "iris.csv"
  BL.readFile file

{- | テストの中で「成功しているはず」の値を取り出す。

@let Right tree = ...@ は網羅していないパターンとして @-Werror@ が止めるので、
失敗したらその場で落ちる関数にして明示する。
-}
expectRight :: Either String a -> a
expectRight (Right value) = value
expectRight (Left err) = error ("失敗しました: " <> err)
