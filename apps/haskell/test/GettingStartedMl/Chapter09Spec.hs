{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.Chapter09Spec (spec) where

import Control.Monad (unless)
import qualified Data.ByteString.Lazy as BL
import Data.List (isInfixOf)
import qualified Data.Map.Strict as M
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import GettingStartedMl.Chapter02 (Table (..), loadTable)
import GettingStartedMl.Chapter09 (
  BostonSplit (..),
  Encoding (..),
  LinearModel (..),
  Standardizer (..),
  categories,
  categoryColumn,
  columnsToExpand,
  encode,
  expand,
  expandedColumns,
  fitStandardizer,
  iqrOutliers,
  joinWeather,
  linearFit,
  linearPredict,
  loadDelimited,
  meanCountByWeather,
  pairsWithReplacement,
  prepareBoston,
  quantile,
  rSquared,
  readDecoded,
  removeTargetOutliers,
  report,
  scoreFeatureSet,
  selectColumns,
  squareTerms,
  standardize,
  standardizeAll,
  statisticsMean,
  statisticsStdDevN,
  statisticsStdDevN1,
  termName,
  toRows,
 )
import GettingStartedMl.Csv (Row, text)
import qualified GettingStartedMl.Dataset as Dataset
import System.Directory (removeFile)
import System.FilePath ((</>))
import System.IO (IOMode (WriteMode), hPutStr, hSetEncoding, mkTextEncoding, withFile)
import Test.Hspec

spec :: Spec
spec = do
  describe "カテゴリ値をダミー変数にする" $ do
    it "空欄を除いたカテゴリを辞書順に並べ、先頭を落とす" $
      categories ["low", "high", "", "very_low", "low"] `shouldBe` ["low", "very_low"]

    it "カテゴリごとに 0 と 1 の列を作る" $ do
      let table = expectRight $ loadTable (utf8Csv "CRIME,ZN\nlow,1\nhigh,2\n")
          encoded = expectRight $ encode table categoryColumn (categories ["low", "high"])
      tableColumns encoded `shouldBe` ["ZN", "CRIME_low"]
      map (`cell` "CRIME_low") (tableRows encoded) `shouldBe` ["1", "0"]

    it "カテゴリに無い値はすべての列を 0 にする" $ do
      let table = expectRight $ loadTable (utf8Csv "CRIME,ZN\nvery_low,1\n")
          encoded = expectRight $ encode table categoryColumn ["low"]
      map (`cell` "CRIME_low") (tableRows encoded) `shouldBe` ["0"]

  describe "標準化" $ do
    it "平均 0・標準偏差 1 になる" $ do
      let std = expectRight $ fitStandardizer (features [1, 2, 3]) ["a"]
          scaled = standardizeAll std (features [1, 2, 3])
          values = [v | row <- scaled, Just v <- [M.lookup "a" row]]
      sum values `shouldSatisfy` \total -> abs total < 1e-12
      M.lookup "a" (standardizerMeans std) `shouldBe` Just 2.0

    it "すべて同じ値の列は 0 になる" $ do
      let std = expectRight $ fitStandardizer (features [5, 5]) ["a"]
      M.lookup "a" (standardizerStds std) `shouldBe` Just 1.0
      standardize std (M.fromList [("a", 5.0)]) `shouldBe` M.fromList [("a", 0.0)]

    it "平均と標準偏差を持たない列はそのまま残す" $ do
      let std = expectRight $ fitStandardizer (features [1, 3]) ["a"]
      standardize std (M.fromList [("b", 7.0)]) `shouldBe` M.fromList [("b", 7.0)]

    it "特徴量が 1 件も無ければ標準化できない" $
      fitStandardizer [] ["a"] `shouldBe` Left "特徴量が 1 件もありません"

    it "平均は statistics の mean と一致する" $
      statisticsMean sample `shouldBe` sum sample / fromIntegral (length sample)

    it "自作の標準偏差は statistics の variance（n で割る）と一致する" $ do
      let std = expectRight $ fitStandardizer (features sample) ["a"]
      M.lookup "a" (standardizerStds std) `shouldBe` Just (statisticsStdDevN sample)

    it "statistics の stdDev は n−1 で割るので自作とは一致しない" $ do
      let std = expectRight $ fitStandardizer (features sample) ["a"]
      M.lookup "a" (standardizerStds std) `shouldNotBe` Just (statisticsStdDevN1 sample)

    it "n と n−1 の比は sqrt((n-1)/n) になる" $ do
      let n = fromIntegral (length sample) :: Double
      abs (statisticsStdDevN sample / statisticsStdDevN1 sample - sqrt ((n - 1) / n)) < 1e-12
        `shouldBe` True

    it "列の順に並べた数値の行にする" $
      toRows [M.fromList [("a", 1.0), ("b", 2.0)]] ["b", "a"] `shouldBe` Right [[2.0, 1.0]]

    it "無い列を並べようとすると失敗する" $
      toRows [M.fromList [("a", 1.0)]] ["z"] `shouldBe` Left "列がありません: z"

  describe "多項式特徴量" $ do
    it "重複を許して 2 つの列を選ぶ" $
      pairsWithReplacement ["x", "y"] `shouldBe` [("x", "x"), ("x", "y"), ("y", "y")]

    it "項の名前は scikit-learn と同じ形にする" $ do
      termName ("RM", "RM") `shouldBe` "RM^2"
      termName ("RM", "LSTAT") `shouldBe` "RM LSTAT"

    it "元の列の後ろに 2 乗と交互作用の項を並べる" $
      expandedColumns ["x", "y"] `shouldBe` ["x", "y", "x^2", "x y", "y^2"]

    it "2 乗の項と交互作用の項を作る" $ do
      let expanded = expectRight $ expand [M.fromList [("x", 2.0), ("y", 3.0)]] ["x", "y"]
      expanded `shouldBe` [M.fromList [("x", 2.0), ("y", 3.0), ("x^2", 4.0), ("x y", 6.0), ("y^2", 9.0)]]

    it "指定した列だけを選ぶ" $
      selectColumns [M.fromList [("x", 1.0), ("y", 2.0)]] ["y"]
        `shouldBe` Right [M.fromList [("y", 2.0)]]

    it "2 乗の項の名前は列から作る" $
      squareTerms `shouldBe` ["RM^2", "LSTAT^2", "PTRATIO^2"]

  describe "外れ値" $ do
    it "分位数は前後の値から線形補間する" $ do
      quantile [1, 2, 3, 4] 0.25 `shouldBe` Right 1.75
      quantile [1, 2, 3, 4] 0.5 `shouldBe` Right 2.5

    it "値が 1 件も無ければ分位数を求められない" $
      quantile [] 0.5 `shouldBe` Left "値が 1 件もありません"

    it "四分位範囲の 1.5 倍から外れた値を外れ値とする" $
      iqrOutliers [1, 2, 3, 4, 100] 1.5 `shouldBe` Right [False, False, False, False, True]

    it "訓練データからだけ外れ値を取り除く" $ do
      let split =
            BostonSplit
              { bostonColumns = ["a"]
              , bostonXTrain = features [1, 2, 3, 4, 100]
              , bostonXTest = features [7]
              , bostonTTrain = [1, 2, 3, 4, 100]
              , bostonTTest = [100]
              }
          kept = expectRight $ removeTargetOutliers split
      bostonTTrain kept `shouldBe` [1, 2, 3, 4]
      bostonTTest kept `shouldBe` [100]

  describe "線形回帰と決定係数" $ do
    it "直線に乗るデータを当てる" $ do
      let model = expectRight $ linearFit [[1], [2], [3]] [3, 5, 7]
      abs (linearIntercept model - 1.0) < 1e-9 `shouldBe` True
      map (\w -> abs (w - 2.0) < 1e-9) (linearWeights model) `shouldBe` [True]

    it "同じ列を 2 つ渡すと解けない" $
      linearFit [[1, 1], [2, 2], [3, 3]] [3, 5, 7]
        `shouldBe` Left "特徴量の列が互いに独立でないため、正規方程式を解けません"

    it "特徴量が 1 件も無ければ学習できない" $
      linearFit [] [] `shouldBe` Left "特徴量が 1 件もありません"

    it "完全に当たれば決定係数は 1 になる" $ do
      let model = expectRight $ linearFit [[1], [2], [3]] [3, 5, 7]
      fmap (\r -> abs (r - 1.0) < 1e-9) (rSquared [3, 5, 7] (linearPredict model [[1], [2], [3]]))
        `shouldBe` Right True

    it "正解の値が無ければ決定係数を求められない" $
      rSquared [] [] `shouldBe` Left "正解の値がありません"

  describe "区切り文字と文字コード" $ do
    it "タブ区切りを読む" $ do
      withTempFile "bike.tsv" Utf8 "weather_id\tcnt\n1\t100\n" $ \file -> do
        table <- loadDelimited file Utf8 '\t'
        fmap tableColumns table `shouldBe` Right ["weather_id", "cnt"]

    it "Shift_JIS の CSV を CP932 として読む" $ do
      withTempFile "weather.csv" Cp932 "weather_id,weather\n1,晴れ\n" $ \file -> do
        table <- loadDelimited file Cp932 ','
        fmap (map (`cell` "weather") . tableRows) table `shouldBe` Right ["晴れ"]

    it "Shift_JIS を UTF-8 として読むと失敗する" $ do
      withTempFile "weather.csv" Cp932 "weather_id,weather\n1,晴れ\n" $ \file -> do
        result <- readDecoded file Utf8
        case result of
          Left err -> err `shouldSatisfy` isInfixOf "cannot decode byte sequence"
          Right value -> expectationFailure ("読めてしまいました: " <> show value)

    it "UTF-8 を CP932 として読んでも失敗せず、文字化けする" $ do
      withTempFile "weather.csv" Utf8 "weather_id,weather\n1,晴れ\n" $ \file -> do
        result <- readDecoded file Cp932
        case result of
          Left err -> expectationFailure ("失敗しました: " <> err)
          Right value -> do
            value `shouldNotSatisfy` T.isInfixOf "晴れ"
            T.isInfixOf "weather_id" value `shouldBe` True

  describe "表の結合" $ do
    it "鍵で引いて列を加える" $ do
      let bike = expectRight $ loadTable (utf8Csv "weather_id,cnt\n1,100\n2,200\n")
          weather = expectRight $ loadTable (utf8Csv "weather_id,weather\n1,晴れ\n2,雨\n")
          joined = expectRight $ joinWeather bike weather
      tableColumns joined `shouldBe` ["weather_id", "cnt", "weather"]
      map (`cell` "weather") (tableRows joined) `shouldBe` ["晴れ", "雨"]

    it "引けない行は残さない" $ do
      let bike = expectRight $ loadTable (utf8Csv "weather_id,cnt\n1,100\n9,200\n")
          weather = expectRight $ loadTable (utf8Csv "weather_id,weather\n1,晴れ\n")
      fmap (length . tableRows) (joinWeather bike weather) `shouldBe` Right 1

    it "鍵が一意でなければ失敗する" $ do
      let bike = expectRight $ loadTable (utf8Csv "weather_id,cnt\n1,100\n")
          weather = expectRight $ loadTable (utf8Csv "weather_id,weather\n1,晴れ\n1,雨\n")
      joinWeather bike weather `shouldBe` Left "weather_id が一意ではありません"

    it "天気ごとの平均利用者数を多い順に並べる" $ do
      let joined = expectRight $ loadTable (utf8Csv "weather,cnt\n雨,100\n晴れ,300\n晴れ,500\n")
      meanCountByWeather joined `shouldBe` Right [("晴れ", 400.0), ("雨", 100.0)]

  describe "実データ" $ do
    it "ボストンの住宅価格を分割して補完する" $ do
      found <- Dataset.exists "Boston.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readDataset "Boston.csv"
      let split = expectRight $ prepareBoston contents 0.3 0
      (length (bostonXTrain split), length (bostonXTest split)) `shouldBe` (70, 30)
      bostonColumns split
        `shouldBe` [ "ZN"
                   , "INDUS"
                   , "CHAS"
                   , "NOX"
                   , "RM"
                   , "AGE"
                   , "DIS"
                   , "RAD"
                   , "TAX"
                   , "PTRATIO"
                   , "B"
                   , "LSTAT"
                   , "CRIME_low"
                   , "CRIME_very_low"
                   ]

    it "決定係数がほかの言語版と一致する" $ do
      found <- Dataset.exists "Boston.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readDataset "Boston.csv"
      let split = expectRight $ prepareBoston contents 0.3 0
          score terms = expectRight $ scoreFeatureSet split columnsToExpand terms
      -- Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版と一致する。
      map (rounded 4) (both (score columnsToExpand)) `shouldBe` [0.6056, 0.6950]
      map (rounded 4) (both (score (columnsToExpand <> squareTerms))) `shouldBe` [0.7740, 0.8628]
      map (rounded 4) (both (score (expandedColumns columnsToExpand))) `shouldBe` [0.7953, 0.8213]

    it "外れ値を除くとテストデータの決定係数が下がる" $ do
      found <- Dataset.exists "Boston.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readDataset "Boston.csv"
      let split = expectRight $ prepareBoston contents 0.3 0
          outliers = expectRight $ iqrOutliers (bostonTTrain split) 1.5
          kept = expectRight $ removeTargetOutliers split
          score = expectRight $ scoreFeatureSet kept columnsToExpand (columnsToExpand <> squareTerms)
      length (filter id outliers) `shouldBe` 8
      map (rounded 4) (both score) `shouldBe` [0.6717, 0.7947]

    it "特徴量エンジニアリングの結果を表示する" $ do
      found <- Dataset.exists "Boston.csv"
      foundWeather <- Dataset.exists "weather.csv"
      unless (found && foundWeather) $ pendingWith "学習データがありません"
      contents <- readDataset "Boston.csv"
      directory <- Dataset.dir
      bike <- expectRight <$> loadDelimited (directory </> "bike.tsv") Utf8 '\t'
      weather <- expectRight <$> loadDelimited (directory </> "weather.csv") Cp932 ','
      report contents bike weather
        `shouldBe` Right
          ( unlines
              [ "訓練データ: 70 件, テストデータ: 30 件"
              , "特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low"
              , "標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00"
              , "決定係数:"
              , "  元の特徴量（3 列）: 訓練 0.6056, テスト 0.6950"
              , "  2 乗の項を追加（6 列）: 訓練 0.7740, テスト 0.8628"
              , "  交互作用の項も追加（9 列）: 訓練 0.7953, テスト 0.8213"
              , "訓練データの PRICE の外れ値: 8 件"
              , "  外れ値を除いて 2 乗の項を追加: 訓練 0.6717, テスト 0.7947"
              , "天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3"
              ]
          )

-- | 標本。統計の関数を突き合わせるために使う。
sample :: [Double]
sample = [2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0]

-- | 1 列だけの特徴量を作る。
features :: [Double] -> [M.Map Text Double]
features = map (\v -> M.fromList [("a", v)])

-- | 訓練データとテストデータの値を並べる。
both :: (Double, Double) -> [Double]
both (train, test) = [train, test]

-- | 小数点以下を丸める。記事に載せる桁で比べる。
rounded :: Int -> Double -> Double
rounded digits value = fromIntegral (round (value * scale) :: Integer) / scale
 where
  scale = 10 ^^ digits

-- | 行の値を読む。テストの中でだけ使うので、読めなければその場で落ちる。
cell :: Row -> Text -> Text
cell row name = expectRight (text row name)

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

-- | 指定した文字コードで一時ファイルを書き、使い終わったら消す。
withTempFile :: String -> Encoding -> Text -> (FilePath -> IO a) -> IO a
withTempFile name enc contents action = do
  let file = "chapter09-test-" <> name
  encoder <- mkTextEncoding (case enc of Utf8 -> "UTF-8"; Cp932 -> "CP932")
  withFile file WriteMode $ \handle -> do
    hSetEncoding handle encoder
    hPutStr handle (T.unpack contents)
  result <- action file
  removeFile file
  pure result
