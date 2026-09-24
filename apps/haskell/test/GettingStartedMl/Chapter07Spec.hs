{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.Chapter07Spec (spec) where

import Control.Monad (unless)
import qualified Data.ByteString.Lazy as BL
import qualified Data.Map.Strict as M
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import GettingStartedMl.Chapter02 (Split (..), Table (..), loadTable)
import GettingStartedMl.Chapter07 (
  LinearModel (..),
  coefficient,
  designMatrix,
  featureColumns,
  fit,
  hmatrixFit,
  hmatrixNormalFit,
  meanAbsoluteError,
  model,
  predict,
  prepareCinema,
  r2Score,
  removeOutliers,
  report,
  residuals,
  rootMeanSquaredError,
  solveLinearSystem,
  sumOfSquares,
  targetValues,
 )
import qualified GettingStartedMl.Dataset as Dataset

-- Hspec も @fit@ を輸出している（注目するテストを絞る関数）ので、隠して自作の
-- 学習の関数を使う。名前が衝突するとコンパイルが止まるので、取り違えは起きない。
import Test.Hspec hiding (fit)

spec :: Spec
spec = do
  describe "評価指標" $ do
    it "残差は実測値から予測値を引いた値" $
      residuals [3.0, 5.0] [1.0, 2.0] `shouldBe` Right [2.0, 3.0]

    it "件数が違えば残差を求められない" $
      residuals [1.0] [1.0, 2.0] `shouldBe` Left "実測値と予測値の件数が違います: 1 と 2"

    it "完全に当たれば平均絶対誤差は零" $
      meanAbsoluteError [1.0, 2.0] [1.0, 2.0] `shouldBe` Right 0.0

    it "平均絶対誤差は誤差の絶対値の平均" $
      meanAbsoluteError [1.0, 2.0] [3.0, 2.0] `shouldBe` Right 1.0

    it "二乗平均平方根誤差は誤差の二乗の平均の平方根" $
      rootMeanSquaredError [0.0, 0.0] [3.0, 4.0] `shouldBe` Right (sqrt 12.5)

    it "完全に当たれば決定係数は一" $
      r2Score [1.0, 2.0, 3.0] [1.0, 2.0, 3.0] `shouldBe` Right 1.0

    it "平均を答え続ければ決定係数は零" $
      r2Score [1.0, 2.0, 3.0] [2.0, 2.0, 2.0] `shouldBe` Right 0.0

    it "実測値がすべて同じなら決定係数を求められない" $
      r2Score [2.0, 2.0] [2.0, 2.0] `shouldBe` Left "実測値がすべて同じ値なので決定係数を求められません"

  describe "モデル" $ do
    it "列名と係数の数が違えばモデルを作れない" $
      model 0.0 ["a", "b"] [1.0] `shouldBe` Left "列名と係数の数が違います: 2 と 1"

    it "列名で係数を読む" $ do
      m <- expectRight (model 1.0 ["a", "b"] [2.0, 3.0])
      coefficient m "b" `shouldBe` Right 3.0

    it "知らない列の係数は読めない" $ do
      m <- expectRight (model 1.0 ["a"] [2.0])
      coefficient m "z" `shouldBe` Left "係数がありません: z"

    it "予測値は切片と係数の重み付きの和" $ do
      m <- expectRight (model 1.0 ["a", "b"] [2.0, 3.0])
      predict m [M.fromList [("a", 10.0), ("b", 100.0)]] `shouldBe` Right [321.0]

    it "特徴量が足りなければ予測できない" $ do
      m <- expectRight (model 1.0 ["a"] [2.0])
      predict m [M.empty] `shouldBe` Left "特徴量がありません: a"

  describe "連立方程式" $ do
    it "ガウス・ジョルダン法で連立方程式を解く" $
      solveLinearSystem [[2.0, 1.0], [1.0, 3.0]] [5.0, 10.0] `shouldBe` Right [1.0, 3.0]

    it "行の順が入れ替わっていても解ける" $
      solveLinearSystem [[0.0, 1.0], [1.0, 0.0]] [3.0, 2.0] `shouldBe` Right [2.0, 3.0]

    it "特異行列は解けない" $
      solveLinearSystem [[1.0, 2.0], [2.0, 4.0]] [1.0, 2.0]
        `shouldBe` Left "解けません。特異行列です"

    it "係数と定数の件数が違えば解けない" $
      solveLinearSystem [[1.0]] [1.0, 2.0] `shouldBe` Left "行列と定数の件数が違います: 1 と 2"

  describe "計画行列" $ do
    it "先頭に一の列を足す" $
      designMatrix [M.fromList [("a", 2.0), ("b", 3.0)]] ["a", "b"]
        `shouldBe` Right [[1.0, 2.0, 3.0]]

    it "列が無ければ計画行列を作れない" $
      designMatrix [M.fromList [("a", 2.0)]] ["a", "b"]
        `shouldBe` Left "特徴量がありません: b"

  describe "正規方程式による学習" $ do
    it "答えの分かっているデータで切片と係数を求める" $ do
      m <- expectRight (fit squareX squareT ["a", "b"])
      modelIntercept m `shouldSatisfy` near 1.0
      modelCoefficients m `shouldSatisfy` and . zipWith near [2.0, -3.0]

    it "訓練データが空なら学習できない" $
      fit [] [] ["a"] `shouldBe` Left "訓練データが空です"

    it "特徴量と実測値の件数が違えば学習できない" $
      fit squareX [1.0] ["a", "b"]
        `shouldBe` Left "特徴量と実測値の件数が違います: 3 と 1"

  describe "hmatrix との突き合わせ" $ do
    it "計画行列をそのまま渡した最小二乗解が自作と一致する" $ do
      mine <- expectRight (fit squareX squareT ["a", "b"])
      theirs <- expectRight (hmatrixFit squareX squareT ["a", "b"])
      modelIntercept theirs `shouldSatisfy` near (modelIntercept mine)
      modelCoefficients theirs `shouldSatisfy` and . zipWith near (modelCoefficients mine)

    it "正規方程式を hmatrix に解かせても自作と一致する" $ do
      mine <- expectRight (fit squareX squareT ["a", "b"])
      theirs <- expectRight (hmatrixNormalFit squareX squareT ["a", "b"])
      modelIntercept theirs `shouldSatisfy` near (modelIntercept mine)
      modelCoefficients theirs `shouldSatisfy` and . zipWith near (modelCoefficients mine)

    it "訓練データが空なら hmatrix でも学習できない" $
      hmatrixFit [] [] ["a"] `shouldBe` Left "訓練データが空です"

  describe "外れ値" $ do
    it "SNS2 が大きく興行収入が小さい行を外れ値として除く" $ do
      let csv =
            utf8Csv
              ( "cinema_id,SNS1,SNS2,actor,original,sales\n"
                  <> "1,1,2000,3,0,100\n"
                  <> "2,1,2000,3,0,9000\n"
                  <> "3,1,10,3,0,100\n"
              )
      table <- expectRight (loadTable csv >>= removeOutliers)
      length (tableRows table) `shouldBe` 2

  describe "実データ" $ do
    it "学習と評価がほかの言語版と一致する" $ do
      -- 学習データは配布物なのでリポジトリに無い。無ければこのテストは外す。
      found <- Dataset.exists "cinema.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readCinema
      split <- expectRight (prepareCinema contents 0.2 0)
      (length (xTrain split), length (xTest split)) `shouldBe` (79, 20)

    it "実データでも自作と hmatrix の係数が一致する" $ do
      found <- Dataset.exists "cinema.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readCinema
      split <- expectRight (prepareCinema contents 0.2 0)
      t <- expectRight (targetValues (tTrain split))
      mine <- expectRight (fit (xTrain split) t featureColumns)
      theirs <- expectRight (hmatrixFit (xTrain split) t featureColumns)
      normal <- expectRight (hmatrixNormalFit (xTrain split) t featureColumns)
      -- 最小二乗解はただ 1 つなので、両方が解に届いていれば残差平方和も一致する。
      mineSse <- expectRight (trainSse mine split t)
      theirsSse <- expectRight (trainSse theirs split t)
      abs (mineSse - theirsSse) < 1e-6 `shouldBe` True
      -- 計画行列をそのまま渡した最小二乗解は、自作と 12 桁そろう。
      nearly (modelIntercept mine) (modelIntercept theirs) `shouldBe` True
      and (zipWith nearly (modelCoefficients mine) (modelCoefficients theirs)) `shouldBe` True
      -- 正規方程式を渡すと条件数が 2 乗になるので、同じ関数でも桁が落ちる。
      -- 12 桁では合わないが、9 桁では合う。この差そのものを固定しておく。
      nearly (modelIntercept mine) (modelIntercept normal) `shouldBe` False
      relativeError (modelIntercept mine) (modelIntercept normal) < 1e-9 `shouldBe` True

    it "実データの結果を表示する" $ do
      found <- Dataset.exists "cinema.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readCinema
      report contents
        `shouldBe` Right
          ( "データ件数: 100\n"
              <> "外れ値を除いた件数: 99\n"
              <> "訓練データ: 79 件, テストデータ: 20 件\n"
              <> "切片: 6114.60\n"
              <> "係数: SNS1=1.3804, SNS2=0.5218, actor=0.2900, original=208.8827\n"
              <> "hmatrix の切片: 6114.60, 係数: SNS1=1.3804, SNS2=0.5218, actor=0.2900, original=208.8827\n"
              <> "テストデータの評価: R2=0.6184, MAE=302.20, RMSE=376.14\n"
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

-- | 訓練データの残差平方和。
trainSse :: LinearModel -> Split (M.Map Text Double) -> [Double] -> Either String Double
trainSse m split t = do
  y <- predict m (xTrain split)
  sumOfSquares <$> residuals t y

near :: Double -> Double -> Bool
near expected actual = abs (expected - actual) < 1e-9

-- | 実データの係数は桁が大きいので、相対誤差で比べる。
nearly :: Double -> Double -> Bool
nearly expected actual = relativeError expected actual < 1e-12

-- | 相対誤差。値が 1 より小さいときは絶対誤差で見る。
relativeError :: Double -> Double -> Double
relativeError expected actual = abs (expected - actual) / max 1.0 (abs expected)

-- | UTF-8 の CSV をテストのために組み立てる。
utf8Csv :: T.Text -> BL.ByteString
utf8Csv = BL.fromStrict . TE.encodeUtf8

readCinema :: IO BL.ByteString
readCinema = Dataset.path "cinema.csv" >>= BL.readFile

{- | テストの中で「成功しているはず」の値を取り出す。

@let Right m = ...@ は網羅していないパターンなので @-Werror@ が止める。
失敗したらその場でテストを落とす形にして、失敗の場合を無視していないことを示す。
-}
expectRight :: Either String a -> IO a
expectRight (Right value) = pure value
expectRight (Left err) = expectationFailure ("失敗しました: " <> err) >> error err
