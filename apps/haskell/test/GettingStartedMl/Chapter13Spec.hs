{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.Chapter13Spec (spec) where

import Control.Monad (unless)
import qualified Data.ByteString.Lazy as BL
import Data.List (transpose)
import Data.Text (Text)
import qualified Data.Text.Encoding as TE
import GettingStartedMl.Chapter02 (loadTable)
import GettingStartedMl.Chapter13 (
  Eigen (..),
  Pca (..),
  center,
  componentsNeeded,
  covarianceMatrix,
  fit,
  fitWith,
  hmatrixEigen,
  hmatrixGaps,
  jacobiEigen,
  loadBoston,
  matrixColumnMeans,
  normalizeSigns,
  report,
  standardizeTable,
  toMatrix,
  topLoadings,
  transform,
 )
import qualified GettingStartedMl.Dataset as Dataset

-- 'fit'（主成分分析）が Test.Hspec の fit（focused it）と衝突するので隠す。
import Test.Hspec hiding (fit)

spec :: Spec
spec = do
  describe "中心化と分散共分散行列" $ do
    it "列ごとの平均を求める" $
      matrixColumnMeans correlated `shouldBe` [2.0, 4.0]

    it "各列から平均を引く" $
      center correlated [2.0, 4.0] `shouldBe` [[-1.0, -2.0], [0.0, 0.0], [1.0, 2.0]]

    it "分散共分散行列は対称で、対角に件数から 1 を引いた数で割った分散が並ぶ" $
      fmap (closeTo [[1.0, 2.0], [2.0, 4.0]]) (covarianceMatrix correlated) `shouldBe` Right True

    it "1 件だけでは分散共分散行列を求められない" $
      covarianceMatrix [[1.0, 2.0]] `shouldBe` Left "主成分分析には 2 件以上のデータが必要です（1 件）"

  describe "固有値分解" $ do
    it "自作の Jacobi 法が対称行列の固有値を大きい順に返す" $ do
      let eigen = expectRight (jacobiEigen [[2.0, 1.0], [1.0, 2.0]])
      closeTo [[3.0, 1.0]] [eigenValues eigen] `shouldBe` True

    it "固有ベクトルは単位ベクトルで、互いに直交する" $ do
      case eigenVectors (expectRight (jacobiEigen [[2.0, 1.0], [1.0, 2.0]])) of
        [v1, v2] -> closeTo [[1.0, 1.0, 0.0]] [[dot v1 v1, dot v2 v2, dot v1 v2]] `shouldBe` True
        vectors -> expectationFailure ("固有ベクトルが 2 本ではありません: " <> show (length vectors))

    it "対称でない行列は固有値分解できない" $
      jacobiEigen [[1.0, 2.0], [3.0, 4.0]]
        `shouldBe` Left "固有値分解できません（対称行列ではありません）"

    it "正方行列でなければ固有値分解できない" $
      jacobiEigen [[1.0, 2.0, 3.0], [2.0, 4.0, 5.0]]
        `shouldBe` Left "正方行列ではありません: 2 行"

    it "hmatrix の eigSH も対称でない行列を断る" $
      hmatrixEigen [[1.0, 2.0], [3.0, 4.0]]
        `shouldBe` Left "固有値分解できません（対称行列ではありません）"

    it "自作と hmatrix の固有値が一致する" $ do
      let mine = eigenValues (expectRight (jacobiEigen symmetric3))
          theirs = eigenValues (expectRight (hmatrixEigen symmetric3))
      closeTo [mine] [theirs] `shouldBe` True

    it "自作と hmatrix の固有ベクトルが符号をそろえれば一致する" $ do
      let mine = normalizeSigns (eigenVectors (expectRight (jacobiEigen symmetric3)))
          theirs = normalizeSigns (eigenVectors (expectRight (hmatrixEigen symmetric3)))
      closeTo mine theirs `shouldBe` True

  describe "符号をそろえる" $ do
    it "絶対値が最大の要素が正になるように反転する" $
      normalizeSigns [[-0.8, 0.6], [0.8, -0.6]] `shouldBe` [[0.8, -0.6], [0.8, -0.6]]

    it "絶対値が同じなら前の要素を見る" $
      normalizeSigns [[-0.5, 0.5]] `shouldBe` [[0.5, -0.5]]

  describe "主成分" $ do
    it "完全に相関する 2 列なら第 1 主成分の寄与率が 1 になる" $ do
      let model = expectRight (fit correlated 2)
      closeTo [[1.0, 0.0]] [pcaExplainedVarianceRatio model] `shouldBe` True

    it "主成分の数は 1 以上、列の数以下でなければならない" $
      fit correlated 3 `shouldBe` Left "主成分の数は 1 以上 2 以下にしてください: 3"

    it "主成分の向きに射影すると、完全に相関する 2 列が 1 列で表せる" $ do
      let model = expectRight (fit correlated 1)
      case transform model correlated of
        -- 中心の行は 0 に、前後の行は同じ大きさで符号が逆になる
        [[first], [middle], [last']] -> closeTo [[0.0, negate first]] [[middle, last']] `shouldBe` True
        projected -> expectationFailure ("1 列 3 行になりません: " <> show projected)

    it "hmatrix の eigSH で学習しても同じ寄与率になる" $ do
      let mine = expectRight (fit correlated 2)
          theirs = expectRight (fitWith hmatrixEigen correlated 2)
      closeTo [pcaExplainedVarianceRatio mine] [pcaExplainedVarianceRatio theirs] `shouldBe` True

  describe "主成分の数と解釈" $ do
    it "累積寄与率がしきい値に届くまでの数を返す" $
      componentsNeeded [0.5, 0.3, 0.2] 0.8 `shouldBe` 2

    it "しきい値に届かなければすべての主成分の数を返す" $
      componentsNeeded [0.5, 0.3] 0.9 `shouldBe` 2

    it "係数の絶対値が大きい順に列名と係数を返す" $
      topLoadings [0.1, -0.9, 0.5] ["a", "b", "c"] 2 `shouldBe` [("b", -0.9), ("c", 0.5)]

    it "係数の絶対値が同じなら元の列の順を残す" $
      topLoadings [0.5, -0.5, 0.1] ["a", "b", "c"] 2 `shouldBe` [("a", 0.5), ("b", -0.5)]

  describe "前処理" $ do
    it "欠損値を補完してから各列を平均 0・標準偏差 1 にそろえる" $ do
      let table = expectRight (loadTable (utf8Csv bostonLike))
          (columns, x) = expectRight (standardizeTable table)
          rows = expectRight (toMatrix x columns)
      columns `shouldBe` ["ZN", "RM", "PRICE", "CRIME_low", "CRIME_very_low"]
      map length rows `shouldBe` [5, 5, 5, 5]
      closeTo [replicate 5 0.0] [matrixColumnMeans rows] `shouldBe` True
      closeTo [replicate 5 1.0] [map stdDevN (transpose rows)] `shouldBe` True

  describe "実データ" $ do
    it "ボストンの住宅価格は 100 件 15 列になる" $ do
      rows <- bostonMatrix
      case rows of
        Nothing -> pendingWith "学習データがありません"
        Just m -> (length m, width m) `shouldBe` (100, 15)

    it "自作の Jacobi 法と hmatrix の eigSH が 15 列の実データで一致する" $ do
      rows <- bostonMatrix
      case rows of
        Nothing -> pendingWith "学習データがありません"
        Just m -> do
          let (ratioGap, componentGap) = expectRight (hmatrixGaps m (width m))
          ratioGap `shouldSatisfy` (< 1.0e-12)
          componentGap `shouldSatisfy` (< 1.0e-9)

    it "寄与率と必要な主成分の数がほかの言語版と一致する" $ do
      rows <- bostonMatrix
      case rows of
        Nothing -> pendingWith "学習データがありません"
        Just m -> do
          let model = expectRight (fit m (width m))
              ratios = pcaExplainedVarianceRatio model
          map (rounded 4) (take 6 ratios) `shouldBe` [0.4110, 0.1448, 0.1019, 0.0645, 0.0623, 0.0581]
          componentsNeeded ratios 0.8 `shouldBe` 6
          rounded 4 (foldl' (+) 0.0 (take 6 ratios)) `shouldBe` 0.8427

    it "主成分分析の結果を表示する" $ do
      found <- Dataset.exists "Boston.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readDataset "Boston.csv"
      fmap (take 5 . lines) (report contents)
        `shouldBe` Right
          [ "データ件数: 100, 列数: 15"
          , "寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581"
          , "累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）"
          , "第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328"
          , "第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405"
          ]

-- | 架空の 3 件 2 列。2 列目は 1 列目のちょうど 2 倍で、完全に相関する。
correlated :: [[Double]]
correlated = [[1.0, 2.0], [2.0, 4.0], [3.0, 6.0]]

-- | 固有値分解を突き合わせるための、対称な 3×3。
symmetric3 :: [[Double]]
symmetric3 = [[4.0, 1.0, 2.0], [1.0, 3.0, 0.0], [2.0, 0.0, 5.0]]

-- | ボストンの住宅価格に似せた架空の 4 件。CRIME は 3 種類で、RM に欠損値がある。
bostonLike :: Text
bostonLike =
  "CRIME,ZN,RM,PRICE\n\
  \low,18,6.5,24\n\
  \high,0,,21\n\
  \very_low,12,7.1,35\n\
  \low,0,5.9,18\n"

-- | 実データを読み込んで行列にする。学習データが無ければ 'Nothing'。
bostonMatrix :: IO (Maybe [[Double]])
bostonMatrix = do
  found <- Dataset.exists "Boston.csv"
  if not found
    then pure Nothing
    else do
      file <- Dataset.path "Boston.csv"
      loaded <- loadBoston file
      let (columns, x) = expectRight loaded
      pure (Just (expectRight (toMatrix x columns)))

-- | 行列の列数。'head' は使えないのでパターンで書く。
width :: [[Double]] -> Int
width [] = 0
width (row : _) = length row

-- | 件数で割る標準偏差。標準化の結果を確かめるために使う。
stdDevN :: [Double] -> Double
stdDevN values = sqrt (foldl' (+) 0.0 [(v - mean) * (v - mean) | v <- values] / n)
 where
  n = fromIntegral (length values)
  mean = foldl' (+) 0.0 values / n

-- | 内積。
dot :: [Double] -> [Double] -> Double
dot a b = foldl' (+) 0.0 (zipWith (*) a b)

-- | 行列どうしが倍精度の丸め誤差の範囲で一致するか。
closeTo :: [[Double]] -> [[Double]] -> Bool
closeTo expected actual =
  length expected == length actual
    && and (zipWith rowClose expected actual)
 where
  rowClose a b = length a == length b && and (zipWith (\x y -> abs (x - y) < 1.0e-9) a b)

-- | 小数点以下を丸める。記事に載せる桁で比べる。
rounded :: Int -> Double -> Double
rounded digits value = fromIntegral (round (value * scale) :: Integer) / scale
 where
  scale = 10 ^^ digits

-- | テストの中で「成功しているはず」の値を取り出す。
expectRight :: Either String a -> a
expectRight (Right value) = value
expectRight (Left err) = error ("失敗しました: " <> err)

-- | UTF-8 の CSV をテストのために組み立てる。
utf8Csv :: Text -> BL.ByteString
utf8Csv = BL.fromStrict . TE.encodeUtf8

-- | 学習データを読む。
readDataset :: String -> IO BL.ByteString
readDataset name = Dataset.path name >>= BL.readFile
