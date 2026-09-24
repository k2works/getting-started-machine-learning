{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.Chapter11Spec (spec) where

import Control.Monad (unless)
import qualified Data.ByteString.Lazy as BL
import Data.List (sort)
import qualified Data.Map.Strict as M
import Data.Text (Text)
import GettingStartedMl.Chapter01 (accuracy)
import GettingStartedMl.Chapter11 (
  ConfusionMatrix (..),
  Fold (..),
  RocPoint (..),
  auc,
  classificationMetric,
  confusionMatrix,
  crossValidate,
  evaluateCinema,
  evaluateSurvived,
  f1Score,
  kFold,
  kFoldSequential,
  linearTrainer,
  mean,
  meanSquaredError,
  nSplits,
  pick,
  precision,
  prepareCinema,
  prepareSurvived,
  recall,
  report,
  rocCurve,
  seed,
  survivedColumns,
  treeTrainer,
 )
import qualified GettingStartedMl.Dataset as Dataset
import Test.Hspec

spec :: Spec
spec = do
  describe "混同行列" $ do
    it "正解と予測を一件ずつ数える" $
      confusionMatrix ["1", "1", "0", "0"] ["1", "0", "1", "0"] ("1" :: Text)
        `shouldBe` Right (ConfusionMatrix 1 1 1 1)

    it "件数が違えば数えられない" $
      confusionMatrix ["1"] ["1", "0"] ("1" :: Text)
        `shouldBe` Left "正解と予測の件数が違います: 1 と 2"

    it "正例以外はすべて負例として数える" $
      confusionMatrix ["a", "b", "c"] ["a", "a", "b"] ("a" :: Text)
        `shouldBe` Right (ConfusionMatrix 1 1 0 1)

  describe "適合率・再現率・F 値" $ do
    it "適合率は正例と予測したうち本当に正例だった割合" $
      precision (ConfusionMatrix 3 1 2 4) `shouldBe` 0.75

    it "再現率は本当の正例のうち正例と予測できた割合" $
      recall (ConfusionMatrix 3 1 2 4) `shouldBe` 0.6

    it "F 値は適合率と再現率の調和平均" $
      f1Score (ConfusionMatrix 3 1 1 4) `shouldBe` 0.75

    it "正例と一度も予測しなければ適合率は零" $
      precision (ConfusionMatrix 0 0 5 5) `shouldBe` 0.0

    it "正例が一件も無ければ再現率は零" $
      recall (ConfusionMatrix 0 5 0 5) `shouldBe` 0.0

    it "適合率も再現率も零なら F 値も零" $
      f1Score (ConfusionMatrix 0 1 1 1) `shouldBe` 0.0

    it "正例を決めれば正解と予測から採点する関数になる" $
      classificationMetric precision ("1" :: Text) ["1", "1", "0"] ["1", "0", "0"]
        `shouldBe` Right 1.0

  describe "正解率と平均二乗誤差" $ do
    it "全員を健康と答えても正解率は高くなる" $
      accuracy (replicate 100 ("健康" :: Text)) (replicate 95 "健康" <> replicate 5 "病気")
        `shouldBe` Right 0.95

    it "平均二乗誤差は誤差の二乗の平均" $
      meanSquaredError [1.0, 2.0] [3.0, 2.0] `shouldBe` Right 2.0

    it "一件だけ大きく外れると平均二乗誤差は跳ね上がる" $ do
      let small = meanSquaredError [0.0, 0.0, 0.0, 0.0] [1.0, 1.0, 1.0, 1.0]
          large = meanSquaredError [0.0, 0.0, 0.0, 0.0] [0.0, 0.0, 0.0, 4.0]
      small `shouldBe` Right 1.0
      large `shouldBe` Right 4.0

    it "件数が違えば平均二乗誤差を求められない" $
      meanSquaredError [1.0] [1.0, 2.0] `shouldBe` Left "正解と予測の件数が違います: 1 と 2"

    it "値が一件も無ければ平均を求められない" $
      mean [] `shouldBe` Left "平均を求める値が 1 つもありません"

  describe "ROC 曲線と AUC" $ do
    it "完全に分けられるスコアの AUC は一" $ do
      curve <- expectRight (rocCurve [0.9, 0.8, 0.3, 0.1] [True, True, False, False])
      auc curve `shouldBe` 1.0

    it "まったく分けられないスコアの AUC は零点五" $ do
      curve <- expectRight (rocCurve [0.5, 0.5, 0.5, 0.5] [True, False, True, False])
      auc curve `shouldBe` 0.5

    it "先頭はどれも正例と予測しない点" $ do
      curve <- expectRight (rocCurve [0.9, 0.1] [True, False])
      take 1 curve `shouldBe` [RocPoint Nothing 0.0 0.0]

    it "同じスコアは一つの点にまとめる" $ do
      curve <- expectRight (rocCurve [0.5, 0.5, 0.1] [True, False, False])
      length curve `shouldBe` 3

    it "正例か負例が片方しか無ければ曲線を描けない" $
      rocCurve [0.9, 0.1] [True, True]
        `shouldBe` Left "正例と負例が両方ないと ROC 曲線を描けません"

    it "件数が違えば曲線を描けない" $
      rocCurve [0.9] [True, False]
        `shouldBe` Left "正解と予測の件数が違います: 1 と 2"

  describe "K 分割交差検証の分け方" $ do
    it "余りは先頭の分割から一件ずつ配る" $ do
      folds <- expectRight (kFoldSequential 7 3)
      map (length . foldTest) folds `shouldBe` [3, 2, 2]

    it "テストデータはすべての行をちょうど一度ずつ使う" $ do
      folds <- expectRight (kFold 10 5 0)
      sort (concatMap foldTest folds) `shouldBe` [0 .. 9]

    it "訓練データとテストデータは重ならない" $ do
      folds <- expectRight (kFold 10 5 0)
      let overlapping f = filter (`elem` foldTest f) (foldTrain f)
      concatMap overlapping folds `shouldBe` []

    it "並べ替えないときは先頭から順に分ける" $ do
      folds <- expectRight (kFoldSequential 6 3)
      map foldTest folds `shouldBe` [[0, 1], [2, 3], [4, 5]]

    it "並べ替えると先頭から順にはならない" $ do
      shuffled <- expectRight (kFold 10 5 0)
      -- Test.Hspec も sequential を輸出しているので、別の名前にする
      inOrder <- expectRight (kFoldSequential 10 5)
      map foldTest shuffled `shouldNotBe` map foldTest inOrder

    it "分割の数は二以上でなければならない" $
      kFold 10 1 0 `shouldBe` Left "分割の数は 2 以上 10 以下にしてください: 1"

    it "分割の数は件数を超えられない" $
      kFold 3 5 0 `shouldBe` Left "分割の数は 2 以上 3 以下にしてください: 5"

  describe "行の位置で選ぶ" $ do
    it "指定した位置の値を順に選ぶ" $
      pick ["a", "b", "c" :: Text] [2, 0] `shouldBe` Right ["c", "a"]

    it "無い位置は選べない" $
      pick ["a" :: Text] [3] `shouldBe` Left "行の位置が範囲の外です: 3"

  describe "交差検証" $ do
    it "分割ごとに学習して採点する" $ do
      let x = [M.fromList [("a", v)] | v <- [1.0, 2.0, 3.0, 4.0, 5.0, 6.0 :: Double]]
          t = [2.0, 4.0, 6.0, 8.0, 10.0, 12.0 :: Double]
      folds <- expectRight (kFoldSequential 6 3)
      scores <- expectRight (crossValidate (linearTrainer ["a"]) x t folds meanSquaredError)
      length scores `shouldBe` 3
      -- t = 2a はどの分割でも完全に当たる
      all (< 1e-18) scores `shouldBe` True

    it "評価関数を差し替えても同じ分割で採点できる" $ do
      -- どの分割のテストデータにも「い」と「ろ」が 1 件ずつ入るように並べる
      let x = [M.fromList [("a", v)] | v <- [0.0, 1.0, 0.0, 1.0, 0.0, 1.0 :: Double]]
          t = ["い", "ろ", "い", "ろ", "い", "ろ"] :: [Text]
      folds <- expectRight (kFoldSequential 6 3)
      hit <- expectRight (crossValidate (treeTrainer ["a"] (Just 1)) x t folds accuracy)
      f <- expectRight (crossValidate (treeTrainer ["a"] (Just 1)) x t folds (classificationMetric f1Score "ろ"))
      hit `shouldBe` [1.0, 1.0, 1.0]
      f `shouldBe` [1.0, 1.0, 1.0]

  describe "実データ" $ do
    it "Survived の前処理で年齢の欠損値が埋まる" $ do
      found <- Dataset.exists "Survived.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readData "Survived.csv"
      (x, t) <- expectRight (prepareSurvived contents)
      length x `shouldBe` 891
      length t `shouldBe` 891
      all (\row -> M.keys row == sort survivedColumns) x `shouldBe` True

    it "分割ごとの平均二乗誤差は第 7 章の RMSE の二乗と一致する" $ do
      found <- Dataset.exists "cinema.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readData "cinema.csv"
      (x, t) <- expectRight (prepareCinema contents)
      folds <- expectRight (kFold (length x) nSplits seed)
      mse <- expectRight (crossValidate (linearTrainer cinemaColumns) x t folds meanSquaredError)
      rmse <- expectRight (crossValidate (linearTrainer cinemaColumns) x t folds rootMse)
      and (zipWith (\a b -> abs (a - b * b) < 1e-6) mse rmse) `shouldBe` True

    it "Survived の交差検証がほかの言語版と一致する" $ do
      found <- Dataset.exists "Survived.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readData "Survived.csv"
      scores <- expectRight (evaluateSurvived contents)
      map (round4 . snd) scores `shouldBe` [0.7811, 0.7759, 0.6306, 0.6833]

    it "cinema の交差検証がほかの言語版と一致する" $ do
      found <- Dataset.exists "cinema.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readData "cinema.csv"
      scores <- expectRight (evaluateCinema contents)
      map (round2 . snd) scores `shouldBe` [405.77, 321.53]

    it "実データの結果を表示する" $ do
      survivedFound <- Dataset.exists "Survived.csv"
      cinemaFound <- Dataset.exists "cinema.csv"
      unless (survivedFound && cinemaFound) $ pendingWith "学習データがありません"
      survived <- readData "Survived.csv"
      cinema <- readData "cinema.csv"
      report survived cinema
        `shouldBe` Right
          ( "Survived（決定木、5 分割交差検証の平均）\n"
              <> "  正解率: 0.7811\n"
              <> "  適合率: 0.7759\n"
              <> "  再現率: 0.6306\n"
              <> "  F値: 0.6833\n"
              <> "cinema（線形回帰、5 分割交差検証の平均）\n"
              <> "  RMSE: 405.77\n"
              <> "  MAE: 321.53\n"
              <> "分割ごとのテストデータの件数（5 分割）\n"
              <> "  891 件を自作: 179, 178, 178, 178, 178\n"
          )

-- | cinema の特徴量の列。
cinemaColumns :: [Text]
cinemaColumns = ["SNS1", "SNS2", "actor", "original"]

-- | 平均二乗誤差の平方根。評価関数を差し替えられることを示すために使う。
rootMse :: [Double] -> [Double] -> Either String Double
rootMse t y = sqrt <$> meanSquaredError t y

round4 :: Double -> Double
round4 value = fromIntegral (round (value * 10000) :: Int) / 10000

round2 :: Double -> Double
round2 value = fromIntegral (round (value * 100) :: Int) / 100

readData :: String -> IO BL.ByteString
readData name = Dataset.path name >>= BL.readFile

{- | テストの中で「成功しているはず」の値を取り出す。

@let Right x = ...@ は網羅していないパターンなので @-Werror@ が止める。
-}
expectRight :: Either String a -> IO a
expectRight (Right value) = pure value
expectRight (Left err) = expectationFailure ("失敗しました: " <> err) >> error err
