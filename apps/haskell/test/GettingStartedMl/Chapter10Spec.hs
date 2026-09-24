{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.Chapter10Spec (spec) where

import Control.Monad (unless)
import qualified Data.ByteString.Lazy as BL
import qualified Data.Map.Strict as M
import Data.Text (Text)
import qualified Data.Text as T
import qualified GettingStartedMl.Chapter02 as C2
import qualified GettingStartedMl.Chapter03 as C3
import GettingStartedMl.Chapter10 (
  Classifier (..),
  Forest (..),
  ForestModel (..),
  LogisticModel (..),
  LogisticRegression (..),
  SomeClassifier (..),
  TreeModel (..),
  bootstrapSample,
  crossEntropy,
  forestFit,
  forestImportances,
  forestPredict,
  logisticFit,
  logisticPredict,
  majorityVote,
  models,
  report,
  score,
  shuffleWithState,
  softmax,
  treeImportances,
 )
import qualified GettingStartedMl.Dataset as Dataset
import GettingStartedMl.Random (newSeed)
import Test.Hspec

spec :: Spec
spec = do
  describe "ソフトマックス関数" $ do
    it "合計が 1 になる" $
      abs (sum (softmax [1.0, 2.0, 3.0]) - 1.0) < 1e-12 `shouldBe` True

    it "同じスコアなら同じ確率になる" $
      softmax [2.0, 2.0] `shouldBe` [0.5, 0.5]

    it "大きな値でもあふれない" $ do
      -- exp は例外を投げず Infinity を返すので、最大値を引かないと NaN になる。
      isInfinite (exp (1000 :: Double)) `shouldBe` True
      isNaN (exp (1000 :: Double) / (exp (1000 :: Double) + exp (1000 :: Double))) `shouldBe` True
      softmax [1000.0, 1000.0] `shouldBe` [0.5, 0.5]

    it "小さな値でも 0 で割らない" $
      softmax [-1000.0, -1000.0] `shouldBe` [0.5, 0.5]

    it "空の並びは空のまま" $
      softmax [] `shouldBe` []

  describe "交差エントロピー" $ do
    it "正解の確率が 1 なら 0 になる" $
      abs (crossEntropy [[1.0, 0.0]] [0]) < 1e-9 `shouldBe` True

    it "正解の確率が 0 でも Infinity にならない" $
      isInfinite (crossEntropy [[0.0, 1.0]] [0]) `shouldBe` False

    it "外した予測のほうが損失は大きい" $
      crossEntropy [[0.9, 0.1]] [0] < crossEntropy [[0.1, 0.9]] [0] `shouldBe` True

  describe "ロジスティック回帰" $ do
    it "分けられるデータを学習する" $ do
      let model = expectRight $ logisticFit tinyX tinyT ["x"] 1.0 200
      logisticClasses model `shouldBe` ["a", "b"]
      logisticPredict model tinyX `shouldBe` Right tinyT

    it "損失は最初より最後のほうが小さくなる" $ do
      let model = expectRight $ logisticFit tinyX tinyT ["x"] 1.0 50
          losses = logisticLosses model
      -- 学習率 1.0 で標準化していない値を渡すと、序盤は行きすぎて損失が増える回もある。
      -- 「毎回減る」ではなく「最後は最初より小さい」が正しい主張になる。
      -- head と last は部分関数なので、-Wx-partial が -Werror で止める。
      case (take 1 losses, drop 49 losses) of
        ([firstLoss], [lastLoss]) -> lastLoss < firstLoss `shouldBe` True
        _ -> expectationFailure "損失が 50 個ありません"

    it "終盤の損失は単調に減る" $ do
      let model = expectRight $ logisticFit tinyX tinyT ["x"] 1.0 50
          tailLosses = drop 40 (logisticLosses model)
      and (zipWith (>) tailLosses (drop 1 tailLosses)) `shouldBe` True

    it "件数が違えば学習できない" $
      fmap logisticClasses (logisticFit tinyX ["a"] ["x"] 1.0 10)
        `shouldBe` Left "特徴量と正解ラベルの件数が違います: 4 と 1"

    it "特徴量が 1 件も無ければ学習できない" $
      fmap logisticClasses (logisticFit [] [] ["x"] 1.0 10)
        `shouldBe` Left "特徴量が 1 件もありません"

    it "無い列を指定すると失敗する" $
      fmap logisticClasses (logisticFit tinyX tinyT ["z"] 1.0 10)
        `shouldBe` Left "列がありません: z"

  describe "多数決とブートストラップ標本" $ do
    it "サンプルごとに最も多い予測を選ぶ" $
      majorityVote [["a", "b"], ["a", "b"], ["b", "a"]] `shouldBe` Right ["a", "b"]

    it "同数なら先に現れた予測を選ぶ" $
      majorityVote [["b"], ["a"]] `shouldBe` Right ["b"]

    it "木が 1 本も無ければ予測も無い" $
      majorityVote [] `shouldBe` Right []

    it "重複を許して件数ぶん選ぶ" $ do
      let (rows, _) = bootstrapSample 10 (newSeed 0)
      length rows `shouldBe` 10
      all (\i -> i >= 0 && i < 10) rows `shouldBe` True

    it "同じ状態からは同じ標本が出る" $
      fst (bootstrapSample 10 (newSeed 0)) `shouldBe` fst (bootstrapSample 10 (newSeed 0))

    it "状態を進めると別の標本になる" $ do
      let (first, next) = bootstrapSample 10 (newSeed 0)
      first `shouldNotBe` fst (bootstrapSample 10 next)

    it "並べ替えても要素は変わらない" $ do
      let (shuffled, _) = shuffleWithState [1 :: Int .. 5] (newSeed 0)
      length shuffled `shouldBe` 5
      all (`elem` shuffled) [1 .. 5] `shouldBe` True

    it "1 件以下なら並べ替えない" $
      fst (shuffleWithState [1 :: Int] (newSeed 0)) `shouldBe` [1]

  describe "ランダムフォレスト" $ do
    it "同じシードなら同じ森になる" $ do
      let forest = expectRight $ forestFit tinyX tinyT ["x"] 5 1 Nothing 0
          again = expectRight $ forestFit tinyX tinyT ["x"] 5 1 Nothing 0
      length (forestTrees forest) `shouldBe` 5
      forest `shouldBe` again

    it "分けられるデータを当てる" $ do
      let forest = expectRight $ forestFit tinyX tinyT ["x"] 5 1 Nothing 0
      forestPredict forest tinyX `shouldBe` Right tinyT

    it "件数が違えば森を作れない" $
      fmap (length . forestTrees) (forestFit tinyX ["a"] ["x"] 5 1 Nothing 0)
        `shouldBe` Left "特徴量と正解ラベルの件数が違います: 4 と 1"

    it "特徴量が 1 件も無ければ森を作れない" $
      fmap (length . forestTrees) (forestFit [] [] ["x"] 5 1 Nothing 0)
        `shouldBe` Left "特徴量が 1 件もありません"

  describe "特徴量の重要度" $ do
    it "合計が 1 になる" $ do
      let tree = expectRight $ C3.fit tinyX tinyT ["x"] Nothing
          importances = expectRight $ treeImportances tree tinyX tinyT ["x"]
      abs (sum (M.elems importances) - 1.0) < 1e-12 `shouldBe` True

    it "分割しない木の重要度はすべて 0 になる" $ do
      let tree = expectRight $ C3.fit tinyX (map (const "a") tinyT) ["x"] Nothing
          importances = expectRight $ treeImportances tree tinyX (map (const "a") tinyT) ["x"]
      M.elems importances `shouldBe` [0.0]

    it "森の重要度も合計が 1 になる" $ do
      let forest = expectRight $ forestFit tinyX tinyT ["x"] 5 1 Nothing 0
          importances = expectRight $ forestImportances forest tinyX tinyT ["x"]
      abs (sum (M.elems importances) - 1.0) < 1e-12 `shouldBe` True

  describe "モデル共通の約束" $ do
    it "型の違うモデルを同じリストに並べられる" $
      map (\(SomeClassifier model) -> classifierName model) models
        `shouldBe` [ "決定木（深さ 2）"
                   , "ロジスティック回帰"
                   , "ランダムフォレスト（100 本）"
                   , "ランダムフォレスト（100 本・深さ 2）"
                   ]

    it "同じ score でどのモデルも評価できる" $ do
      let split = tinySplit
      score (TreeModel Nothing) split ["x"] `shouldBe` Right (1.0, 1.0)
      score (LogisticModel 1.0 200) split ["x"] `shouldBe` Right (1.0, 1.0)
      score (ForestModel 5 1 Nothing 0) split ["x"] `shouldBe` Right (1.0, 1.0)

  describe "実データ" $ do
    it "正解率がほかの言語版と一致する" $ do
      split <- irisSplit
      case split of
        Nothing -> pendingWith "学習データがありません"
        Just s -> do
          let columns = C3.featureColumns
              rounded4 (train, test) = (rounded 4 train, rounded 4 test)
          -- Java 版・Clojure 版・Elixir 版・PHP 版と一致する。
          fmap rounded4 (score (TreeModel (Just 2)) s columns) `shouldBe` Right (0.9333, 0.9556)
          fmap rounded4 (score (LogisticModel 1.0 5000) s columns) `shouldBe` Right (0.9143, 0.9111)
          fmap rounded4 (score (ForestModel 100 2 Nothing 0) s columns) `shouldBe` Right (1.0, 0.9333)
          fmap rounded4 (score (ForestModel 100 2 (Just 2) 0) s columns) `shouldBe` Right (0.9429, 0.9556)

    it "正解率は浮動小数点数として完全に一致する" $ do
      split <- irisSplit
      case split of
        Nothing -> pendingWith "学習データがありません"
        Just s ->
          score (LogisticModel 1.0 5000) s C3.featureColumns
            `shouldBe` Right (0.9142857142857143, 0.9111111111111111)

    it "特徴量の重要度が Elixir 版と一致する" $ do
      split <- irisSplit
      case split of
        Nothing -> pendingWith "学習データがありません"
        Just s -> do
          let columns = C3.featureColumns
              forest = expectRight $ forestFit (C2.xTrain s) (C2.tTrain s) columns 100 2 Nothing 0
              importances = expectRight $ forestImportances forest (C2.xTrain s) (C2.tTrain s) columns
          -- Elixir 版と完全に一致する。Java 版・Clojure 版・PHP 版は
          -- がく片幅 0.1271・花弁長さ 0.2708 で、4 桁目がずれる。第 3 章の
          -- ジニ不純度がラベルの出現数を 'M.elems' でたどるため、和を取る順が
          -- 「挿入順」ではなく「鍵の順」になり、同点だった分割に 1 ulp の差が付く。
          map (\c -> rounded 4 (M.findWithDefault 0 c importances)) columns
            `shouldBe` [0.1882, 0.1265, 0.2713, 0.4140]

    it "モデルごとの正解率と重要度を表示する" $ do
      found <- Dataset.exists "iris.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readIris
      report contents
        `shouldBe` Right
          ( unlines
              [ "モデル\t訓練データ\tテストデータ"
              , "決定木（深さ 2）\t0.9333\t0.9556"
              , "ロジスティック回帰\t0.9143\t0.9111"
              , "ランダムフォレスト（100 本）\t1.0000\t0.9333"
              , "ランダムフォレスト（100 本・深さ 2）\t0.9429\t0.9556"
              , ""
              , "ランダムフォレスト（100 本）の特徴量の重要度:"
              , "がく片長さ\t0.1882"
              , "がく片幅\t0.1265"
              , "花弁長さ\t0.2713"
              , "花弁幅\t0.4140"
              ]
          )

-- | 1 つの特徴量で 2 つに分かれる小さなデータ。
tinyX :: [C3.Features]
tinyX = [M.fromList [("x", v)] | v <- [1.0, 2.0, 8.0, 9.0]]

-- | 小さなデータの正解ラベル。
tinyT :: [Text]
tinyT = ["a", "a", "b", "b"]

-- | 小さなデータを、訓練とテストが同じ分割にしたもの。
tinySplit :: C2.Split C3.Features
tinySplit = C2.Split {C2.xTrain = tinyX, C2.xTest = tinyX, C2.tTrain = tinyT, C2.tTest = tinyT}

-- | 実データの分割。学習データが無ければ 'Nothing'。
irisSplit :: IO (Maybe (C2.Split C3.Features))
irisSplit = do
  found <- Dataset.exists "iris.csv"
  if not found
    then pure Nothing
    else do
      contents <- readIris
      pure (Just (expectRight (C2.prepareIris contents 0.3 0)))

-- | 学習データを読む。
readIris :: IO BL.ByteString
readIris = Dataset.path "iris.csv" >>= BL.readFile

-- | 小数点以下を丸める。記事に載せる桁で比べる。
rounded :: Int -> Double -> Double
rounded digits value = fromIntegral (round (value * scale) :: Integer) / scale
 where
  scale = 10 ^^ digits

-- | テストの中で「成功しているはず」の値を取り出す。
expectRight :: Either String a -> a
expectRight (Right value) = value
expectRight (Left err) = error ("失敗しました: " <> T.unpack (T.pack err))
