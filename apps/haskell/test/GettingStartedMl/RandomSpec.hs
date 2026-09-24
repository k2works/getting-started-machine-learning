module GettingStartedMl.RandomSpec (spec) where

import GettingStartedMl.Random (newSeed, nextInt, shuffle)
import Test.Hspec

-- | 同じ状態から続けて n 個の値を取り出す。
takeInts :: Int -> Int -> Int -> [Int]
takeInts bound seed = go (newSeed seed)
 where
  go _ 0 = []
  go s n = let (v, s2) = nextInt bound s in v : go s2 (n - 1)

spec :: Spec
spec = do
  describe "線形合同法" $ do
    it "java.util.Random と同じ並びを返す" $
      -- Java 版・Kotlin 版・Scala 版・Clojure 版・Elixir 版・PHP 版と同じ並び。
      takeInts 100 0 5 `shouldBe` [60, 48, 29, 47, 15]

    it "二の冪の範囲でも同じ並びを返す" $
      takeInts 16 42 5 `shouldBe` [11, 0, 10, 0, 4]

    it "範囲の中の値だけを返す" $
      all (\v -> v >= 0 && v < 10) (takeInts 10 7 1000) `shouldBe` True

    it "同じシードなら同じ並びになる" $
      takeInts 100 123 20 `shouldBe` takeInts 100 123 20

  describe "Fisher-Yates の並べ替え" $ do
    it "ほかの言語版と一致する" $
      shuffle [0 .. 9 :: Int] 0 `shouldBe` [4, 8, 9, 6, 3, 5, 2, 1, 7, 0]

    it "要素が一つ以下なら変わらない" $ do
      shuffle ([] :: [Int]) 0 `shouldBe` []
      shuffle [1 :: Int] 0 `shouldBe` [1]

    it "並べ替えても要素は増えも減りもしない" $
      length (shuffle [0 .. 20 :: Int] 5) `shouldBe` 21

    it "同じシードなら同じ並びになる" $
      shuffle [0 .. 20 :: Int] 123 `shouldBe` shuffle [0 .. 20 :: Int] 123
