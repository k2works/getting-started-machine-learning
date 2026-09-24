module GettingStartedMl.DatasetSpec (spec) where

import GettingStartedMl.Dataset (defaultDir, dir, envName, exists, path)
import System.Environment (setEnv, unsetEnv)
import Test.Hspec

spec :: Spec
spec = do
  describe "学習データの置き場" $ do
    it "環境変数が無ければ既定の置き場を使う" $ do
      unsetEnv envName
      dir `shouldReturn` defaultDir

    it "環境変数があればその置き場を使う" $ do
      setEnv envName "/tmp/ml"
      actual <- dir
      unsetEnv envName
      actual `shouldBe` "/tmp/ml"

    it "置き場とファイル名をつないで道を作る" $ do
      setEnv envName "/tmp/ml"
      actual <- path "KvsT.csv"
      unsetEnv envName
      actual `shouldBe` "/tmp/ml/KvsT.csv"

    it "無いファイルは存在しないと答える" $ do
      setEnv envName "/nonexistent"
      actual <- exists "KvsT.csv"
      unsetEnv envName
      actual `shouldBe` False
