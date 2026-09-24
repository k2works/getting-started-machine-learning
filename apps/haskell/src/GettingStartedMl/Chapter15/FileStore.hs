{-# LANGUAGE OverloadedStrings #-}

{- | 第 15 章のインフラ層。学習済みモデルをディレクトリのファイルに保存し、読み込む。

第 8 章で、学習済みのパイプラインが @Data.Binary@ の往復で戻ることを
確かめてある。第 7 章の線形回帰のモデルも切片とリストなので、同じやり方で保存できる。

この層だけが 'IO' を持つ。'fileStore' が返すのは 'ModelStore' という
ただのレコードなので、上の層はファイルのことを何も知らない。
-}
module GettingStartedMl.Chapter15.FileStore (
  fileStore,
  modelFile,
  salesFormatVersion,
  saveSalesModel,
  saveSurvivalModel,
) where

import Data.Binary (decodeOrFail, encode)
import qualified Data.ByteString.Lazy as BL
import Data.Text (Text)
import qualified Data.Text as T
import qualified GettingStartedMl.Chapter07 as Chapter07
import qualified GettingStartedMl.Chapter08 as Chapter08
import GettingStartedMl.Chapter15.Domain (
  linearSalesModel,
  pipelineSurvivalModel,
  salesModelName,
  survivalModelName,
 )
import GettingStartedMl.Chapter15.Store (LoadError (..), ModelStore (..))
import System.Directory (createDirectoryIfMissing, doesFileExist)
import System.FilePath (takeDirectory, (</>))

-- | 線形回帰のモデルを保存する形式の版。読み込むときに確かめる。
salesFormatVersion :: Int
salesFormatVersion = 1

-- | モデルの名前からファイルへの道を作る。
modelFile :: FilePath -> Text -> FilePath
modelFile dir name = dir </> T.unpack name <> ".model"

-- | ディレクトリを置き場にする。ディレクトリはまだ無くてもよい。
fileStore :: FilePath -> ModelStore
fileStore dir =
  ModelStore
    { loadSalesModel = fmap linearSalesModel <$> readModel dir salesModelName decodeSales
    , loadSurvivalModel =
        fmap pipelineSurvivalModel <$> readModel dir survivalModelName decodeSurvival
    }

{- | ファイルを読む。無ければ 'ModelNotFound' に、読めなければ 'ModelUnreadable' に変える。

「ファイルが無い」と「中身が壊れている」を分けるのは、前者が 503 に
値する一時的な状態で、後者が置き場の異常だからである。
-}
readModel :: FilePath -> Text -> (BL.ByteString -> Either String a) -> IO (Either LoadError a)
readModel dir name decoder = do
  let file = modelFile dir name
  found <- doesFileExist file
  if not found
    then pure (Left (ModelNotFound name))
    else do
      contents <- BL.readFile file
      pure (either (const (Left (ModelUnreadable name))) Right (decoder contents))

-- | 第 7 章の線形回帰のモデルを保存する。形式の版を先頭に書く。
saveSalesModel :: FilePath -> Chapter07.LinearModel -> IO ()
saveSalesModel dir model = do
  let file = modelFile dir salesModelName
  createDirectoryIfMissing True (takeDirectory file)
  BL.writeFile
    file
    ( encode
        ( salesFormatVersion
        , Chapter07.modelIntercept model
        , map T.unpack (Chapter07.modelColumns model)
        , Chapter07.modelCoefficients model
        )
    )

-- | 第 8 章の学習済みパイプラインを、第 8 章の 'Chapter08.saveModel' で保存する。
saveSurvivalModel :: FilePath -> Chapter08.FittedPipeline -> IO ()
saveSurvivalModel dir = flip Chapter08.saveModel (modelFile dir survivalModelName)

{- | 保存した線形回帰のモデルを読む。

@binary@ は @text@ に依存していないので 'Text' の実装が無く、
列名は 'String' に直してから書く。第 8 章と同じ扱いである。
-}
decodeSales :: BL.ByteString -> Either String Chapter07.LinearModel
decodeSales contents = case decodeOrFail contents of
  Left _ -> Left "モデルとして読めません"
  Right (rest, _, (version, intercept, columns, coefficients))
    | not (BL.null rest) -> Left "モデルとして読めません"
    | version /= salesFormatVersion -> Left "対応していない形式のモデルです"
    | otherwise -> Chapter07.model intercept (map T.pack columns) coefficients

-- | 保存したパイプラインを読む。'Chapter08.loadModel' は 'IO' なので、ここで読み直す。
decodeSurvival :: BL.ByteString -> Either String Chapter08.FittedPipeline
decodeSurvival contents = case decodeOrFail contents of
  Left _ -> Left "モデルとして読めません"
  Right (rest, _, (version, fitted))
    | not (BL.null rest) -> Left "モデルとして読めません"
    | version /= Chapter08.formatVersion -> Left "対応していない形式のモデルです"
    | otherwise -> Right fitted
