{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.CsvSpec (spec) where

import qualified Data.ByteString.Lazy as BL
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import GettingStartedMl.Csv (column, number, parseRows)
import Test.Hspec

spec :: Spec
spec = do
  describe "行の読み込み" $ do
    it "BOM を取り除いて列名にする" $ do
      let csv = utf8Csv "\65279身長,体重\n170,60\n"
      fmap (fmap (`column` "身長")) (parseRows csv) `shouldBe` Right [Right "170"]

    it "空行を読み飛ばす" $ do
      let csv = utf8Csv "身長,体重\n170,60\n\n160,50\n"
      fmap length (parseRows csv) `shouldBe` Right 2

  describe "列の読み取り" $ do
    it "列が無ければ読めない" $ do
      let csv = utf8Csv "身長\n170\n"
      fmap (fmap (`column` "体重")) (parseRows csv)
        `shouldBe` Right [Left "列がありません: 体重"]

    it "前後の空白があっても数値として読む" $ do
      let csv = utf8Csv "身長\n 170 \n"
      fmap (fmap (`number` "身長")) (parseRows csv) `shouldBe` Right [Right 170]

    it "数値として読めない値は弾く" $ do
      let csv = utf8Csv "身長\n高い\n"
      fmap (fmap (`number` "身長")) (parseRows csv)
        `shouldBe` Right [Left "身長 を数値として読めません: 高い"]

    it "途中までしか数値でない値も弾く" $ do
      let csv = utf8Csv "身長\n170cm\n"
      fmap (fmap (`number` "身長")) (parseRows csv)
        `shouldBe` Right [Left "身長 を数値として読めません: 170cm"]

{- | UTF-8 の CSV をテストのために組み立てる。

'Data.ByteString.Lazy.Char8' の @pack@ は 1 文字を 1 バイトに詰めるので、
日本語をそのまま渡すと壊れたバイト列になる。テストのデータでも必ず
UTF-8 として符号化する。
-}
utf8Csv :: T.Text -> BL.ByteString
utf8Csv = BL.fromStrict . TE.encodeUtf8
