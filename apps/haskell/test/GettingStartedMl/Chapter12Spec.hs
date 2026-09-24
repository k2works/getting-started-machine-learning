{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.Chapter12Spec (spec) where

import Control.Monad (unless)
import qualified Data.ByteString.Lazy as BL
import qualified Data.Map.Strict as M
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import GettingStartedMl.Chapter02 (Table (..), loadTable)
import GettingStartedMl.Chapter07 (LinearModel (..), r2Score)
import qualified GettingStartedMl.Chapter07 as C7
import GettingStartedMl.Chapter12 (
  BostonData (..),
  Experiment (..),
  alphas,
  bestExperiment,
  coefficientAbsSum,
  hmatrixRidgeFit,
  lassoAlpha,
  lassoFit,
  prepareBoston,
  removeOutliers,
  report,
  ridgeFit,
  runRidgeExperiments,
  seed,
  softThreshold,
  testScore,
  testSize,
  validationSize,
  zeroCoefficientNames,
 )
import qualified GettingStartedMl.Dataset as Dataset
import Test.Hspec

spec :: Spec
spec = do
  describe "リッジ回帰" $ do
    it "alpha が零なら最小二乗法と同じ解になる" $ do
      mine <- expectRight (ridgeFit squareX squareT ["a", "b"] 0.0)
      least <- expectRight (C7.fit squareX squareT ["a", "b"])
      modelIntercept mine `shouldSatisfy` near (modelIntercept least)
      modelCoefficients mine `shouldSatisfy` and . zipWith near (modelCoefficients least)

    it "alpha を大きくすると係数は零に近づく" $ do
      weak <- expectRight (ridgeFit squareX squareT ["a", "b"] 0.0)
      strong <- expectRight (ridgeFit squareX squareT ["a", "b"] 100.0)
      coefficientAbsSum strong `shouldSatisfy` (< coefficientAbsSum weak)

    it "切片には罰則がかからないので正解の平均は保たれる" $ do
      -- 強く正則化すると係数は 0 に近づき、予測は正解の平均に寄る
      strong <- expectRight (ridgeFit squareX squareT ["a", "b"] 1.0e8)
      y <- expectRight (C7.predict strong squareX)
      let tMean = sum squareT / fromIntegral (length squareT)
      all (\v -> abs (v - tMean) < 1e-6) y `shouldBe` True

    it "訓練データが空なら学習できない" $
      ridgeFit [] [] ["a"] 1.0 `shouldBe` Left "訓練データが空です"

    it "件数が違えば学習できない" $
      ridgeFit squareX [1.0] ["a", "b"] 1.0
        `shouldBe` Left "特徴量と正解の件数が違います: 3 と 1"

  describe "hmatrix との突き合わせ" $ do
    it "自作のガウス・ジョルダン法と hmatrix の解が一致する" $ do
      mine <- expectRight (ridgeFit squareX squareT ["a", "b"] 1.0)
      theirs <- expectRight (hmatrixRidgeFit squareX squareT ["a", "b"] 1.0)
      modelIntercept theirs `shouldSatisfy` near (modelIntercept mine)
      modelCoefficients theirs `shouldSatisfy` and . zipWith near (modelCoefficients mine)

    it "alpha が零でも一致する" $ do
      mine <- expectRight (ridgeFit squareX squareT ["a", "b"] 0.0)
      theirs <- expectRight (hmatrixRidgeFit squareX squareT ["a", "b"] 0.0)
      modelCoefficients theirs `shouldSatisfy` and . zipWith near (modelCoefficients mine)

    it "hmatrix でも件数が違えば学習できない" $
      hmatrixRidgeFit squareX [1.0] ["a", "b"] 1.0
        `shouldBe` Left "特徴量と正解の件数が違います: 3 と 1"

  describe "軟しきい値作用素" $ do
    it "しきい値より大きければ零のほうへ縮める" $
      softThreshold 5.0 2.0 `shouldBe` 3.0

    it "しきい値より小さければ零のほうへ縮める" $
      softThreshold (-5.0) 2.0 `shouldBe` (-3.0)

    it "しきい値の中なら零にする" $
      softThreshold 1.5 2.0 `shouldBe` 0.0

  describe "ラッソ回帰" $ do
    it "alpha が十分小さければ最小二乗法に近づく" $ do
      mine <- expectRight (lassoFit squareX squareT ["a", "b"] 1.0e-8)
      least <- expectRight (C7.fit squareX squareT ["a", "b"])
      modelCoefficients mine `shouldSatisfy` and . zipWith roughly (modelCoefficients least)

    it "alpha が大きければ係数はちょうど零になる" $ do
      mine <- expectRight (lassoFit squareX squareT ["a", "b"] 1000.0)
      -- リッジ回帰は 0 に近づくだけだが、ラッソ回帰は 0 そのものにする
      modelCoefficients mine `shouldBe` [0.0, 0.0]

    it "係数が零になった特徴量の名前を返す" $ do
      mine <- expectRight (lassoFit squareX squareT ["a", "b"] 1000.0)
      zeroCoefficientNames mine `shouldBe` ["a", "b"]

    it "値がすべて同じ列があっても落ちない" $ do
      let x = [M.fromList [("a", 1.0), ("b", v)] | v <- [1.0, 2.0, 3.0]]
          t = [1.0, 2.0, 3.0]
      mine <- expectRight (lassoFit x t ["a", "b"] 0.001)
      -- 分散 0 の列は係数を動かしようがないので 0 のまま
      take 1 (modelCoefficients mine) `shouldBe` [0.0]

  describe "モデル選択" $ do
    it "検証データの決定係数が最も高い実験を選ぶ" $
      fmap
        experimentAlpha
        ( bestExperiment
            [ Experiment 0.0 0.9 0.5 10.0
            , Experiment 1.0 0.8 0.7 8.0
            , Experiment 10.0 0.7 0.6 5.0
            ]
        )
        `shouldBe` Right 1.0

    it "同じ値なら先の実験を選ぶ" $
      fmap
        experimentAlpha
        ( bestExperiment
            [ Experiment 0.0 0.9 0.5 10.0
            , Experiment 1.0 0.8 0.7 8.0
            , Experiment 10.0 0.7 0.7 5.0
            ]
        )
        `shouldBe` Right 1.0

    it "実験が一件も無ければ選べない" $
      bestExperiment [] `shouldBe` Left "実験結果が 1 件もありません"

  describe "外れ値" $ do
    it "z スコアがしきい値を超える行を除く" $ do
      -- 件数が n のとき z スコアの上限は (n - 1) / √n なので、
      -- 10 件では 2.85 までしか届かず 3.0 のしきい値に当たらない
      let csv = utf8Csv ("v\n" <> T.intercalate "\n" (replicate 19 "1") <> "\n1000\n")
      table <- expectRight (loadTable csv >>= \t -> removeOutliers t ["v"] 3.0)
      length (tableRows table) `shouldBe` 19

    it "件数が少ないと上限に届かず一件も除かれない" $ do
      let csv = utf8Csv ("v\n" <> T.intercalate "\n" (replicate 9 "1") <> "\n1000\n")
      table <- expectRight (loadTable csv >>= \t -> removeOutliers t ["v"] 3.0)
      length (tableRows table) `shouldBe` 10

    it "値が空欄なら判定できない" $ do
      -- 全欄が空の行は読み込みの時点で落ちるので、別の列に値を入れて空欄を残す
      let csv = utf8Csv "v,w\n1,1\n,2\n2,3\n"
      (loadTable csv >>= \t -> removeOutliers t ["v"] 3.0)
        `shouldBe` Left "値が空欄です: v"

  describe "実データ" $ do
    it "外れ値を除いて三つに分ける" $ do
      dataset <- boston
      case dataset of
        Nothing -> pendingWith "学習データがありません"
        Just d -> do
          bdKept d `shouldBe` 98
          (length (bdTTrain d), length (bdTValid d), length (bdTTest d))
            `shouldBe` (47, 21, 30)
          bdFeatureNames d
            `shouldBe` [ "RM"
                       , "PTRATIO"
                       , "LSTAT"
                       , "RM^2"
                       , "RM PTRATIO"
                       , "RM LSTAT"
                       , "PTRATIO^2"
                       , "PTRATIO LSTAT"
                       , "LSTAT^2"
                       ]

    it "実データでも自作と hmatrix のリッジ回帰が一致する" $ do
      dataset <- boston
      case dataset of
        Nothing -> pendingWith "学習データがありません"
        Just d -> do
          mine <- expectRight (ridgeFit (bdXTrain d) (bdTTrain d) (bdFeatureNames d) 10.0)
          theirs <- expectRight (hmatrixRidgeFit (bdXTrain d) (bdTTrain d) (bdFeatureNames d) 10.0)
          maximum (zipWith (\a b -> abs (a - b)) (modelCoefficients mine) (modelCoefficients theirs))
            `shouldSatisfy` (< 1e-9)

    it "検証データで選んだリッジ回帰はテストデータで線形回帰を上回る" $ do
      dataset <- boston
      case dataset of
        Nothing -> pendingWith "学習データがありません"
        Just d -> do
          experiments <- expectRight (runRidgeExperiments d alphas)
          best <- expectRight (bestExperiment experiments)
          experimentAlpha best `shouldBe` 10.0
          linear <- expectRight (testScore d 0.0)
          ridge <- expectRight (testScore d (experimentAlpha best))
          ridge `shouldSatisfy` (> linear)

    it "ラッソ回帰が零にした特徴量がほかの言語版と一致する" $ do
      dataset <- boston
      case dataset of
        Nothing -> pendingWith "学習データがありません"
        Just d -> do
          lasso <- expectRight (lassoFit (bdXTrain d) (bdTTrain d) (bdFeatureNames d) lassoAlpha)
          zeroCoefficientNames lasso
            `shouldBe` ["PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"]

    it "訓練データでは線形回帰がリッジ回帰を上回る" $ do
      dataset <- boston
      case dataset of
        Nothing -> pendingWith "学習データがありません"
        Just d -> do
          linear <- expectRight (ridgeFit (bdXTrain d) (bdTTrain d) (bdFeatureNames d) 0.0)
          ridge <- expectRight (ridgeFit (bdXTrain d) (bdTTrain d) (bdFeatureNames d) 10.0)
          linearScore <- expectRight (trainScore d linear)
          ridgeScore <- expectRight (trainScore d ridge)
          linearScore `shouldSatisfy` (> ridgeScore)

    it "実データの結果を表示する" $ do
      found <- Dataset.exists "Boston.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readBoston
      report contents
        `shouldBe` Right
          ( "データ件数: 98（外れ値 2 件を除外）\n"
              <> "訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件\n"
              <> "特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2\n"
              <> "alpha  訓練 R²  検証 R²  係数の絶対値の合計\n"
              <> "  0.0  0.8827  0.7272  14.187\n"
              <> "  0.1  0.8827  0.7274  14.104\n"
              <> "  1.0  0.8823  0.7288  13.594\n"
              <> " 10.0  0.8681  0.7349  11.573\n"
              <> "100.0  0.6583  0.5985  5.684\n"
              <> "検証データで選んだ alpha: 10.0\n"
              <> "テストデータの決定係数: 線形回帰 0.5224, リッジ回帰 0.6243\n"
              <> "hmatrix のリッジ回帰との係数の最大の差: 8.0e-15\n"
              <> "ラッソ回帰（alpha=23.5）で係数が 0 になった特徴量: PTRATIO^2, PTRATIO LSTAT, LSTAT^2\n"
          )

-- | 答えが分かっているデータ。切片 1、係数 2 と -3。
squareX :: [M.Map Text Double]
squareX =
  [ M.fromList [("a", 1.0), ("b", 0.0)]
  , M.fromList [("a", 0.0), ("b", 1.0)]
  , M.fromList [("a", 1.0), ("b", 1.0)]
  ]

squareT :: [Double]
squareT = [3.0, -2.0, 0.0]

-- | 訓練データの決定係数。
trainScore :: BostonData -> LinearModel -> Either String Double
trainScore d m = C7.predict m (bdXTrain d) >>= r2Score (bdTTrain d)

-- | 学習データがあれば前処理した結果を返す。無ければ 'Nothing'。
boston :: IO (Maybe BostonData)
boston = do
  found <- Dataset.exists "Boston.csv"
  if not found
    then pure Nothing
    else do
      contents <- readBoston
      Just <$> expectRight (prepareBoston contents testSize validationSize seed)

readBoston :: IO BL.ByteString
readBoston = Dataset.path "Boston.csv" >>= BL.readFile

near :: Double -> Double -> Bool
near expected actual = abs (expected - actual) < 1e-9

-- | ラッソ回帰は反復で近づくので、最小二乗法との比較はゆるく見る。
roughly :: Double -> Double -> Bool
roughly expected actual = abs (expected - actual) < 1e-6

-- | UTF-8 の CSV をテストのために組み立てる。
utf8Csv :: T.Text -> BL.ByteString
utf8Csv = BL.fromStrict . TE.encodeUtf8

{- | テストの中で「成功しているはず」の値を取り出す。

@let Right x = ...@ は網羅していないパターンなので @-Werror@ が止める。
-}
expectRight :: Either String a -> IO a
expectRight (Right value) = pure value
expectRight (Left err) = expectationFailure ("失敗しました: " <> err) >> error err
