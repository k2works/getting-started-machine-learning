{-# LANGUAGE OverloadedStrings #-}

{- | CSV を列名つきで読む。

cassava は BOM を取り除かないので、先頭の列名から自分で取り除く。

列名と値は 'T.Text' でやり取りする。cassava が扱うのはバイト列だが、
@OverloadedStrings@ で @ByteString@ のリテラルに日本語を書くと
1 バイトを 1 文字と見なして詰められ、そのまま化ける。境界でだけ
UTF-8 として符号化・復号する。
-}
module GettingStartedMl.Csv (
  Row,
  parseRows,
  parseTable,
  column,
  text,
  number,
  optionalNumber,
) where

import qualified Data.ByteString as BS
import qualified Data.ByteString.Char8 as BSC
import qualified Data.ByteString.Lazy as BL
import qualified Data.Csv as Csv
import qualified Data.HashMap.Strict as HM
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import qualified Data.Vector as V

-- | 列名から値への対応。値は文字列のまま持ち、数値への変換は章ごとに行う。
type Row = Csv.NamedRecord

-- | UTF-8 の BOM。記事とコードに実物を混ぜないようバイト列で書く。
bom :: BS.ByteString
bom = BS.pack [0xEF, 0xBB, 0xBF]

{- | CSV を読み、1 行目を列名にした行のリストを返す。

失敗は例外ではなく 'Left' で返す。呼ぶ側が扱いを決められる。
-}
parseRows :: BL.ByteString -> Either String [Row]
parseRows contents =
  case Csv.decodeByName (stripBom contents) of
    Left err -> Left err
    Right (_, rows) -> Right (filter (not . isBlank) (V.toList rows))

{- | CSV を読み、列名の並びと行のリストを返す。

'M.Map' はキーの順で並ぶので、CSV に現れた順を保ちたいときはこちらを使う。
-}
parseTable :: BL.ByteString -> Either String ([Text], [Row])
parseTable contents =
  case Csv.decodeByName (stripBom contents) of
    Left err -> Left err
    Right (header, rows) ->
      Right
        ( map TE.decodeUtf8Lenient (V.toList header)
        , filter (not . isBlank) (V.toList rows)
        )

-- | 先頭の BOM を取り除く。
stripBom :: BL.ByteString -> BL.ByteString
stripBom contents
  | BL.fromStrict bom `BL.isPrefixOf` contents = BL.drop 3 contents
  | otherwise = contents

-- | 全部の欄が空の行かどうか。
isBlank :: Row -> Bool
isBlank row = all (BS.null . trim) (HM.elems row)

-- | 列名で値を読む。列が無ければ 'Left' を返す。
column :: Row -> Text -> Either String Text
column row name =
  case HM.lookup (TE.encodeUtf8 name) row of
    Nothing -> Left ("列がありません: " <> T.unpack name)
    Just value -> Right (TE.decodeUtf8Lenient (trim value))

-- | 列名で値を読む。'column' の別名で、数値でない列を読むときに使う。
text :: Row -> Text -> Either String Text
text = column

{- | 列名で小数を読む。空欄なら 'Nothing' を返す。

「値が無いかもしれない」ことを 'Maybe' で表す。呼ぶ側は取り出す前に
場合分けを書くことになり、欠損の扱いを忘れられない。
-}
optionalNumber :: Row -> Text -> Either String (Maybe Double)
optionalNumber row name = do
  cell <- column row name
  if T.null (T.strip cell)
    then Right Nothing
    else case reads (T.unpack cell) of
      [(value, rest)] | all (== ' ') rest -> Right (Just value)
      _ -> Left (T.unpack name <> " を数値として読めません: " <> T.unpack cell)

-- | 列名で整数を読む。読めなければ 'Left' を返す。
number :: Row -> Text -> Either String Int
number row name = do
  cell <- column row name
  case BSC.readInt (TE.encodeUtf8 cell) of
    Just (value, rest) | BS.null rest -> Right value
    _ -> Left (T.unpack name <> " を数値として読めません: " <> T.unpack cell)

-- | 前後の空白を落とす。
trim :: BS.ByteString -> BS.ByteString
trim = BSC.dropWhile isSpace . BSC.reverse . BSC.dropWhile isSpace . BSC.reverse
 where
  isSpace c = c == ' ' || c == '\t' || c == '\r' || c == '\n'
