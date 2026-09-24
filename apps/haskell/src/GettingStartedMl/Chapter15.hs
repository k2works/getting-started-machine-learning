{-# LANGUAGE OverloadedStrings #-}

{- | 第 15 章: 機械学習 API とモジュール設計。

第 7 章の線形回帰（映画の興行収入）と第 8 章のパイプライン（乗客の生存）を
学習して保存し、Scotty で予測 API を起動する。

この module は組み立てだけを持つ。層の中身は
'GettingStartedMl.Chapter15.Domain'（ドメイン）、
'GettingStartedMl.Chapter15.Store'（置き場の約束）、
'GettingStartedMl.Chapter15.FileStore'（インフラ）、
'GettingStartedMl.Chapter15.Service'（アプリケーション）、
'GettingStartedMl.Chapter15.Validation' と
'GettingStartedMl.Chapter15.Api'（プレゼンテーション）にある。
-}
module GettingStartedMl.Chapter15 (
  modelDir,
  port,
  host,
  testSize,
  seed,
  maxDepth,
  trainModels,
  trainAndSaveModels,
  run,
  serve,
) where

import qualified Data.ByteString.Lazy as BL
import Data.String (fromString)
import Data.Text (Text)
import qualified Data.Text as T
import GettingStartedMl.Chapter02 (Split (..))
import qualified GettingStartedMl.Chapter07 as Chapter07
import qualified GettingStartedMl.Chapter08 as Chapter08
import GettingStartedMl.Chapter15.Api (application)
import GettingStartedMl.Chapter15.FileStore (fileStore, saveSalesModel, saveSurvivalModel)
import GettingStartedMl.Chapter15.Service (health)
import qualified GettingStartedMl.Dataset as Dataset
import qualified Network.Wai.Handler.Warp as Warp
import System.FilePath ((</>))
import Text.Printf (printf)
import qualified Web.Scotty as Scotty

-- | 学習済みモデルの保存先。apps/haskell/model/ は .gitignore の対象。
modelDir :: FilePath
modelDir = "model"

-- | 予測 API のポート。
port :: Int
port = 8015

-- | 自分のマシンからだけ接続できるループバックのアドレス。
host :: String
host = "127.0.0.1"

-- | 第 7・8 章と同じテストデータの割合。
testSize :: Double
testSize = 0.2

-- | 第 7・8 章と同じ乱数のシード。
seed :: Int
seed = 0

-- | 第 8 章と同じ決定木の深さの上限。
maxDepth :: Maybe Int
maxDepth = Just 5

{- | 第 7・8 章と同じ条件でモデルを学習する。

'IO' を持たない純粋関数なので、読み込んだ中身さえあればテストで確かめられる。
-}
trainModels
  :: BL.ByteString
  -> BL.ByteString
  -> Either String (Chapter07.LinearModel, Chapter08.FittedPipeline)
trainModels cinema survived = do
  cinemaSplit <- Chapter07.prepareCinema cinema testSize seed
  target <- Chapter07.targetValues (tTrain cinemaSplit)
  linear <- Chapter07.fit (xTrain cinemaSplit) target Chapter07.featureColumns
  survivedSplit <- Chapter08.prepareSurvived survived testSize seed
  labels <- Chapter08.labelValues (tTrain survivedSplit)
  pipeline <-
    Chapter08.fitPipeline
      (Chapter08.buildPipeline maxDepth Chapter08.Balanced)
      (Chapter08.Frame Chapter08.featureColumns (xTrain survivedSplit))
      labels
  pure (linear, pipeline)

-- | 学習データを読んでモデルを学習し、置き場に保存する。
trainAndSaveModels :: FilePath -> FilePath -> IO (Either String ())
trainAndSaveModels dataDir dir = do
  cinema <- BL.readFile (dataDir </> "cinema.csv")
  survived <- BL.readFile (dataDir </> "Survived.csv")
  case trainModels cinema survived of
    Left err -> pure (Left err)
    Right (linear, pipeline) -> do
      saveSalesModel dir linear
      saveSurvivalModel dir pipeline
      pure (Right ())

-- | モデルを学習して保存し、予測 API を起動する。Ctrl-C で止まるまで戻らない。
run :: IO ()
run = do
  dataDir <- Dataset.dir
  trained <- trainAndSaveModels dataDir modelDir
  case trained of
    Left err -> putStrLn ("学習できませんでした: " <> err)
    Right () -> do
      models <- health (fileStore modelDir)
      mapM_ (putStrLn . describe) models
      serve modelDir port

-- | モデルごとの読み込めるかどうかを 1 行にする。
describe :: (Text, Bool) -> String
describe (name, ready) = printf "モデル %s: %s" (T.unpack name) (show ready)

-- | 置き場とポートを決めて、Scotty で待ち受ける。
serve :: FilePath -> Int -> IO ()
serve dir p = do
  printf "http://%s:%d で待ち受けます\n" host p
  Scotty.scottyOpts options (application (fileStore dir))
 where
  options =
    Scotty.defaultOptions
      { Scotty.settings =
          Warp.setHost (fromString host) (Warp.setPort p (Scotty.settings Scotty.defaultOptions))
      }
