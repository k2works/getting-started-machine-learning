{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.Chapter08Spec (spec) where

import Control.Monad (unless, void)
import qualified Data.ByteString.Lazy as BL
import qualified Data.Map.Strict as M
import Data.Text (Text)
import GettingStartedMl.Chapter02 (Split (..))
import GettingStartedMl.Chapter08 (
  ClassWeight (..),
  Evaluation (..),
  FittedPipeline (..),
  Frame (..),
  Pipeline (..),
  Rule (..),
  Tree (..),
  applyStep,
  balancedWeights,
  buildPipeline,
  dummyEncoder,
  evaluate,
  featureColumns,
  fitPipeline,
  fitStep,
  fitTree,
  groupMedianImputer,
  labelValues,
  loadModel,
  median,
  mostFrequentImputer,
  predictPipeline,
  prepareSurvived,
  report,
  saveModel,
  toFeatures,
  weightedGini,
  weightedMajority,
  weightsOf,
 )
import qualified GettingStartedMl.Dataset as Dataset
import System.Directory (createDirectoryIfMissing, removeFile)
import System.FilePath ((</>))
import Test.Hspec

spec :: Spec
spec = do
  describe "中央値" $ do
    it "件数が奇数なら真ん中の値" $
      median [3.0, 1.0, 2.0] `shouldBe` Right 2.0

    it "件数が偶数なら真ん中の二つの平均" $
      median [1.0, 2.0, 3.0, 4.0] `shouldBe` Right 2.5

    it "値が無ければ中央値を求められない" $
      median [] `shouldBe` Left "値がありません"

  describe "グループごとの中央値で補完する" $ do
    it "グループごとの中央値で年齢を補完する" $ do
      let frame = textFrame ["Pclass", "Age"] [["1", "40"], ["1", "50"], ["3", "10"], ["3", ""]]
      fitted <- expectRight (fitStep (groupMedianImputer "Age" ["Pclass"]) frame)
      applied <- expectRight (applyStep fitted frame)
      map (M.! "Age") (frameRows applied) `shouldBe` ["40", "50", "10", "10.0"]

    it "知らないグループは全体の中央値で補完する" $ do
      let train = textFrame ["Pclass", "Age"] [["1", "40"], ["1", "50"], ["3", "10"]]
          test = textFrame ["Pclass", "Age"] [["2", ""]]
      fitted <- expectRight (fitStep (groupMedianImputer "Age" ["Pclass"]) train)
      applied <- expectRight (applyStep fitted test)
      map (M.! "Age") (frameRows applied) `shouldBe` ["40.0"]

    it "値がすべて空欄なら学習できない" $ do
      let frame = textFrame ["Pclass", "Age"] [["1", ""]]
      fitStep (groupMedianImputer "Age" ["Pclass"]) frame `shouldBe` Left "値がありません"

  describe "最頻値で補完する" $ do
    it "最頻値で補完する" $ do
      let frame = textFrame ["Embarked"] [["S"], ["S"], ["C"], [""]]
      fitted <- expectRight (fitStep (mostFrequentImputer "Embarked") frame)
      applied <- expectRight (applyStep fitted frame)
      map (M.! "Embarked") (frameRows applied) `shouldBe` ["S", "S", "C", "S"]

    it "同数なら値の順で前のものを選ぶ" $ do
      let frame = textFrame ["Embarked"] [["S"], ["C"], [""]]
      fitted <- expectRight (fitStep (mostFrequentImputer "Embarked") frame)
      applied <- expectRight (applyStep fitted frame)
      map (M.! "Embarked") (frameRows applied) `shouldBe` ["S", "C", "C"]

  describe "ダミー変数化" $ do
    it "基準の列を落とす" $ do
      let frame = textFrame ["Sex"] [["male"], ["female"]]
      fitted <- expectRight (fitStep (dummyEncoder ["Sex"]) frame)
      applied <- expectRight (applyStep fitted frame)
      frameColumns applied `shouldBe` ["Sex_male"]
      map (M.! "Sex_male") (frameRows applied) `shouldBe` ["1", "0"]

    it "学習していないカテゴリはすべて零になる" $ do
      let train = textFrame ["Sex"] [["male"], ["female"]]
          test = textFrame ["Sex"] [["unknown"]]
      fitted <- expectRight (fitStep (dummyEncoder ["Sex"]) train)
      applied <- expectRight (applyStep fitted test)
      map (M.! "Sex_male") (frameRows applied) `shouldBe` ["0"]

  describe "特徴量にする" $ do
    it "欠損値が残っていれば特徴量にできない" $
      toFeatures (textFrame ["Age"] [[""]])
        `shouldBe` Left "欠損値が残っています: Age"

    it "数値として読めない値は特徴量にできない" $
      toFeatures (textFrame ["Age"] [["若い"]])
        `shouldBe` Left "Age を数値として読めません: 若い"

  describe "重み付きのジニ不純度" $ do
    it "半々ならジニ不純度は零点五" $
      weightedGini [0, 1] [1.0, 1.0] `shouldBe` Right 0.5

    it "すべて同じラベルならジニ不純度は零" $
      weightedGini [1, 1] [1.0, 1.0] `shouldBe` Right 0.0

    it "重みを変えると不純度が変わる" $
      weightedGini [0, 1] [3.0, 1.0] `shouldBe` Right 0.375

    it "重みの合計が零なら不純度を求められない" $
      weightedGini [] [] `shouldBe` Left "重みがありません"

  describe "クラスの重み" $ do
    it "balanced の重みは件数に反比例する" $
      balancedWeights [1, 0, 0, 0] `shouldBe` [2.0, 2.0 / 3.0, 2.0 / 3.0, 2.0 / 3.0]

    it "重みを付けなければすべて一になる" $
      weightsOf [1, 0] NoWeight `shouldBe` [1.0, 1.0]

    it "重みの合計が最も大きいラベルを選ぶ" $
      weightedMajority [0, 0, 1] [1.0, 1.0, 3.0] `shouldBe` Right 1

    it "同じなら先に現れたラベルを選ぶ" $
      weightedMajority [0, 1] [1.0, 1.0] `shouldBe` Right 0

  describe "決定木" $ do
    it "深さ零なら葉だけの木になる" $
      fitTree [M.fromList [("a", 1.0)]] [1] ["a"] (Just 0) NoWeight
        `shouldBe` Right (Leaf 1)

    it "balanced にすると少数派が葉に選ばれる" $ do
      let x = replicate 3 (M.fromList [("a", 1.0)])
      -- 重みを付けなければ多数派の 0 が葉になる。
      fitTree x [1, 0, 0] ["a"] (Just 0) NoWeight `shouldBe` Right (Leaf 0)
      -- balanced はクラスごとの重みの合計をそろえるので、同点になり、
      -- 先に現れた少数派の 1 が葉になる。
      fitTree x [1, 0, 0] ["a"] (Just 0) Balanced `shouldBe` Right (Leaf 1)

    it "分かれ目を見つけて左右の部分木を作る" $ do
      let x = [M.fromList [("a", v)] | v <- [1.0, 2.0]]
      fitTree x [0, 1] ["a"] Nothing NoWeight
        `shouldBe` Right (Branch (Rule "a" 1.5) (Leaf 0) (Leaf 1))

    it "特徴量と正解ラベルの件数が違えば学習できない" $
      fitTree [M.fromList [("a", 1.0)]] [0, 1] ["a"] Nothing NoWeight
        `shouldBe` Left "特徴量と正解ラベルの件数が違います: 1 と 2"

  describe "パイプライン" $ do
    it "欠損値を含むデータで学習して予測できる" $ do
      let frame =
            textFrame
              ["Pclass", "Sex", "Age"]
              [ ["1", "female", "30"]
              , ["1", "female", ""]
              , ["3", "male", "20"]
              , ["3", "male", "22"]
              ]
          pipeline =
            Pipeline
              [groupMedianImputer "Age" ["Pclass"], dummyEncoder ["Sex"]]
              (Just 3)
              NoWeight
      fitted <- expectRight (fitPipeline pipeline frame [1, 1, 0, 0])
      fittedColumns fitted `shouldBe` ["Pclass", "Age", "Sex_male"]
      predictPipeline fitted frame `shouldBe` Right [1, 1, 0, 0]

  describe "保存と読み込み" $ do
    it "学習済みのパイプラインは保存して復元できる" $ do
      let frame = textFrame ["Pclass", "Age"] [["1", "30"], ["3", "20"]]
      fitted <- expectRight (fitPipeline (Pipeline [] (Just 2) NoWeight) frame [1, 0])
      let file = "model" </> "spec.model"
      createDirectoryIfMissing True "model"
      saveModel fitted file
      loaded <- loadModel file
      removeFile file
      loaded `shouldBe` Right fitted

    it "モデルとして読めないファイルは読み込めない" $ do
      let file = "model" </> "broken.model"
      createDirectoryIfMissing True "model"
      BL.writeFile file "これはモデルではありません"
      loaded <- loadModel file
      removeFile file
      void loaded `shouldBe` Left ("モデルとして読めません: " <> file)

  describe "実データ" $ do
    it "件数と分割がほかの言語版と一致する" $ do
      -- 学習データは配布物なのでリポジトリに無い。無ければこのテストは外す。
      found <- Dataset.exists "Survived.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readSurvived
      split <- expectRight (prepareSurvived contents 0.2 0)
      t <- expectRight (labelValues (tTrain split <> tTest split))
      (length (xTrain split), length (xTest split)) `shouldBe` (712, 179)
      length (filter (== 1) t) `shouldBe` 342

    it "深さ二では balanced にすると見つかる生存者が増える" $ do
      found <- Dataset.exists "Survived.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readSurvived
      split <- expectRight (prepareSurvived contents 0.2 0)
      plain <- expectRight (evaluateWith split (Just 2) NoWeight)
      balanced <- expectRight (evaluateWith split (Just 2) Balanced)
      (foundSurvivors plain, foundSurvivors balanced) `shouldBe` (41, 68)

    it "実データの結果を表示する" $ do
      found <- Dataset.exists "Survived.csv"
      unless found $ pendingWith "学習データがありません"
      contents <- readSurvived
      createDirectoryIfMissing True "model"
      actual <- report contents ("model" </> "survived-spec.model")
      removeFile ("model" </> "survived-spec.model")
      actual
        `shouldBe` Right
          ( "データ件数: 891（生存 342, 死亡 549）\n"
              <> "訓練データ: 712 件, テストデータ: 179 件\n"
              <> "classWeight=none: 訓練 0.854, テスト 0.799, 生存者 79 人中 59 人を発見\n"
              <> "classWeight=balanced: 訓練 0.848, テスト 0.804, 生存者 79 人中 65 人を発見\n"
              <> "保存したモデル: 読み込めました\n"
              <> "架空の乗客の予測: 1, 0\n"
          )

-- | 分割済みのデータを、深さと重みを変えて評価する。
evaluateWith
  :: Split (M.Map Text Text)
  -> Maybe Int
  -> ClassWeight
  -> Either String Evaluation
evaluateWith split maxDepth classWeight = do
  t <- labelValues (tTrain split)
  fitted <- fitPipeline (buildPipeline maxDepth classWeight) (featuresFrame (xTrain split)) t
  evaluate fitted split

featuresFrame :: [M.Map Text Text] -> Frame
featuresFrame = Frame featureColumns

-- | 列名と値の並びから表を組み立てる。テストのための道具。
textFrame :: [Text] -> [[Text]] -> Frame
textFrame columns rows = Frame columns (map (M.fromList . zip columns) rows)

readSurvived :: IO BL.ByteString
readSurvived = Dataset.path "Survived.csv" >>= BL.readFile

expectRight :: Either String a -> IO a
expectRight (Right value) = pure value
expectRight (Left err) = expectationFailure ("失敗しました: " <> err) >> error err
