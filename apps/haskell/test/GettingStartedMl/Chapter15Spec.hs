{-# LANGUAGE OverloadedStrings #-}

module GettingStartedMl.Chapter15Spec (spec) where

import Control.Monad (void)
import qualified Data.Aeson as A
import qualified Data.Aeson.KeyMap as KM
import qualified Data.ByteString.Lazy as BL
import qualified Data.Map.Strict as M
import Data.Text (Text)
import GettingStartedMl.Chapter02 (Split (..))
import qualified GettingStartedMl.Chapter07 as Chapter07
import qualified GettingStartedMl.Chapter08 as Chapter08
import GettingStartedMl.Chapter15 (maxDepth, seed, testSize, trainAndSaveModels)
import GettingStartedMl.Chapter15.Api (
  Response (..),
  Route (..),
  handle,
  route,
 )
import GettingStartedMl.Chapter15.Domain (
  Movie (..),
  Passenger (..),
  SalesModel (..),
  SurvivalModel (..),
  linearSalesModel,
  modelNames,
  pipelineSurvivalModel,
  salesModelName,
  survivalModelName,
 )
import GettingStartedMl.Chapter15.FileStore (
  fileStore,
  modelFile,
  saveSalesModel,
  saveSurvivalModel,
 )
import GettingStartedMl.Chapter15.Service (
  PredictError (..),
  health,
  predictSales,
  predictSurvival,
 )
import GettingStartedMl.Chapter15.Store (
  LoadError (..),
  ModelStore (..),
  loadErrorMessage,
 )
import GettingStartedMl.Chapter15.Validation (
  FieldType (..),
  invalidJson,
  movie,
  movieTypes,
  passenger,
  passengerTypes,
  readJson,
 )
import qualified GettingStartedMl.Dataset as Dataset
import System.Directory (createDirectoryIfMissing, removeDirectoryRecursive)
import System.FilePath ((</>))
import Test.Hspec

-- | この章の予測に使う架空の映画。約束のテストと実データのテストで共有する。
sampleMovie :: Movie
sampleMovie = Movie {movieSns1 = 200.0, movieSns2 = 500.0, movieActor = 3000.0, movieOriginal = 1}

-- | 1 等客室の女性。年齢と乗船港は送らない。
sampleWoman :: Passenger
sampleWoman =
  Passenger
    { passengerPclass = 1
    , passengerSex = "female"
    , passengerAge = Nothing
    , passengerSibSp = 0
    , passengerParch = 0
    , passengerFare = 50.0
    , passengerEmbarked = Nothing
    }

-- | 3 等客室の男性。
sampleMan :: Passenger
sampleMan =
  sampleWoman
    { passengerPclass = 3
    , passengerSex = "male"
    , passengerFare = 8.0
    , passengerEmbarked = Just "S"
    }

-- | 失敗したらその場で落ちる。'Either' の中身をテストで取り出すときに使う。
expectRight :: (Show e) => Either e a -> IO a
expectRight (Left err) = expectationFailure (show err) >> fail "unreachable"
expectRight (Right value) = pure value

{- | 女性が生存し、男性が死亡する 4 件の作り物のデータで学習したパイプライン。

学習データが無くても走らせられるので、約束のテストと API のテストはこれを使う。
-}
fakePipeline :: IO Chapter08.FittedPipeline
fakePipeline =
  expectRight $
    Chapter08.fitPipeline
      (Chapter08.buildPipeline (Just 3) Chapter08.Balanced)
      (Chapter08.Frame Chapter08.featureColumns (map toRow rows))
      [1, 1, 0, 0]
 where
  rows =
    [ ("1", "female", "30", "50", "S")
    , ("2", "female", "40", "30", "C")
    , ("3", "male", "20", "8", "S")
    , ("3", "male", "50", "9", "Q")
    ]
  toRow (pclass, sex, age, fare, embarked) =
    M.fromList
      [ ("Pclass", pclass)
      , ("Sex", sex)
      , ("Age", age)
      , ("SibSp", "0")
      , ("Parch", "0")
      , ("Fare", fare)
      , ("Embarked", embarked)
      ]

-- | 作り物の線形回帰のモデル。売上 = 1 + 2*SNS1 + 3*SNS2 + 4*actor + 5*original。
fakeLinearModel :: IO Chapter07.LinearModel
fakeLinearModel = expectRight (Chapter07.model 1.0 Chapter07.featureColumns [2.0, 3.0, 4.0, 5.0])

-- | 置き場に何も入っていない状態と、両方のモデルが入っている状態を作る。
withFileStore :: FilePath -> ((ModelStore, IO ()) -> IO a) -> IO a
withFileStore name body = do
  let dir = "dist-newstyle" </> "chapter15-test" </> name
  createDirectoryIfMissing True dir
  removeDirectoryRecursive dir
  createDirectoryIfMissing True dir
  let store = fileStore dir
      fill = do
        linear <- fakeLinearModel
        pipeline <- fakePipeline
        saveSalesModel dir linear
        saveSurvivalModel dir pipeline
  body (store, fill)

-- | モデルを持たない偽の置き場。'LoadError' を返す関数を 2 つ詰めるだけで作れる。
emptyStore :: ModelStore
emptyStore =
  ModelStore
    { loadSalesModel = pure (Left (ModelNotFound salesModelName))
    , loadSurvivalModel = pure (Left (ModelNotFound survivalModelName))
    }

-- | 作り物のモデルを持つ偽の置き場。ファイルを触らない。
fakeStore :: IO ModelStore
fakeStore = do
  linear <- fakeLinearModel
  pipeline <- fakePipeline
  pure
    ModelStore
      { loadSalesModel = pure (Right (linearSalesModel linear))
      , loadSurvivalModel = pure (Right (pipelineSurvivalModel pipeline))
      }

-- | 予測のときに必ず失敗する偽の置き場。500 を出すために使う。
brokenStore :: ModelStore
brokenStore =
  ModelStore
    { loadSalesModel = pure (Right (SalesModel (const (Left "係数がありません: SNS1"))))
    , loadSurvivalModel = pure (Right (SurvivalModel (const (Left "特徴量がありません: Age"))))
    }

{- | 置き場の約束。本物にも偽物にも同じものを走らせる。

型（'ModelStore' のレコード）が決めるのは関数の名前と型だけで、
「無ければ 'ModelNotFound' を返す」「同じ入力には同じ答えを返す」は書けない。
そこをこのテストで守る。
-}
storeContract :: String -> IO ModelStore -> IO ModelStore -> Spec
storeContract label withModels withoutModels = describe ("置き場の約束: " <> label) $ do
  it "興行収入のモデルを読み込むと、映画から数値を返す" $ do
    store <- withModels
    model <- expectRight =<< loadSalesModel store
    value <- expectRight (predictSalesWith model sampleMovie)
    value `shouldSatisfy` (> 0.0)

  it "生存予測のモデルを読み込むと、乗客から真偽値を返す" $ do
    store <- withModels
    model <- expectRight =<< loadSurvivalModel store
    expectRight (predictSurvivalWith model sampleWoman) `shouldReturn` True
    expectRight (predictSurvivalWith model sampleMan) `shouldReturn` False

  it "同じ入力を二度読み込んでも同じ答えを返す" $ do
    store <- withModels
    first <- expectRight =<< loadSalesModel store
    second <- expectRight =<< loadSalesModel store
    predictSalesWith first sampleMovie `shouldBe` predictSalesWith second sampleMovie

  it "興行収入のモデルが無ければ ModelNotFound を返す" $ do
    store <- withoutModels
    result <- loadSalesModel store
    void result `shouldBe` Left (ModelNotFound salesModelName)

  it "生存予測のモデルが無ければ ModelNotFound を返す" $ do
    store <- withoutModels
    result <- loadSurvivalModel store
    void result `shouldBe` Left (ModelNotFound survivalModelName)

-- | ハンドラーを呼び、応答の状態と本文の JSON を返す。サーバーは起動しない。
call :: ModelStore -> Text -> Text -> BL.ByteString -> IO (Int, A.Value)
call store method path body = do
  response <- handle store method path body
  lookup "Content-Type" (responseHeaders response)
    `shouldBe` Just "application/json; charset=utf-8"
  decoded <- expectRight (A.eitherDecode (responseBody response))
  pure (responseStatus response, decoded)

{- | モデルとして読めないバイト列。

@ByteString@ のリテラルに日本語を書くと 1 文字が 1 バイトに詰められて化けるので
（[ADR 014](../../../docs/adr/014-haskell-ml-libraries.md) の第 1 章の知見）、
中身を数で書く。
-}
garbage :: BL.ByteString
garbage = BL.pack [0, 1, 2, 3]

-- | JSON のリテラルを書くための短い別名。
json :: BL.ByteString -> BL.ByteString
json = id

spec :: Spec
spec = do
  describe "要求の JSON を読む" $ do
    it "型が合っていれば読める" $
      void (readJson movieTypes (json "{\"sns1\": 200, \"original\": 1}"))
        `shouldBe` Right ()

    it "オブジェクトでなければ読めない" $
      void (readJson movieTypes (json "[1, 2]")) `shouldBe` Left [invalidJson]

    it "壊れた JSON は読めない" $
      void (readJson movieTypes (json "{")) `shouldBe` Left [invalidJson]

    it "数値の列に文字列が来たら読めない" $
      void (readJson movieTypes (json "{\"sns1\": \"おおい\"}")) `shouldBe` Left [invalidJson]

    it "整数の列に小数が来たら読めない" $
      void (readJson movieTypes (json "{\"original\": 1.5}")) `shouldBe` Left [invalidJson]

    it "数値の列に整数が来てもよい" $
      void (readJson movieTypes (json "{\"sns1\": 200}")) `shouldBe` Right ()

    it "表に無い列は問わない" $
      void (readJson movieTypes (json "{\"unknown\": []}")) `shouldBe` Right ()

    it "null は型を問わない" $
      void (readJson passengerTypes (json "{\"age\": null}")) `shouldBe` Right ()

    it "乗客の列の型" $ do
      M.lookup "pclass" passengerTypes `shouldBe` Just IntegerField
      M.lookup "sex" passengerTypes `shouldBe` Just StringField
      M.lookup "fare" passengerTypes `shouldBe` Just NumberField

  describe "映画の検証" $ do
    it "そろっていれば映画になる" $ do
      request <- expectRight (readJson movieTypes (json "{\"sns1\": 200, \"sns2\": 500, \"actor\": 3000, \"original\": 1}"))
      movie request `shouldBe` Right sampleMovie

    it "必須の列が無ければ理由を返す" $ do
      request <- expectRight (readJson movieTypes (json "{}"))
      movie request
        `shouldBe` Left
          [ "sns1 は必須です"
          , "sns2 は必須です"
          , "actor は必須です"
          , "original は必須です"
          ]

    it "負の数と選択肢の外の値は理由を並べて返す" $ do
      request <- expectRight (readJson movieTypes (json "{\"sns1\": -1, \"sns2\": 2000, \"actor\": 300, \"original\": 2}"))
      movie request
        `shouldBe` Left
          [ "sns1 は 0 以上にしてください"
          , "original は 0、1 のどれかにしてください"
          ]

  describe "乗客の検証" $ do
    it "年齢と乗船港は省略できる" $ do
      request <- expectRight (readJson passengerTypes (json "{\"pclass\": 1, \"sex\": \"female\", \"sib_sp\": 0, \"parch\": 0, \"fare\": 50}"))
      passenger request `shouldBe` Right sampleWoman

    it "年齢と乗船港を送ってもよい" $ do
      request <- expectRight (readJson passengerTypes (json "{\"pclass\": 3, \"sex\": \"male\", \"age\": 20, \"sib_sp\": 0, \"parch\": 0, \"fare\": 8, \"embarked\": \"S\"}"))
      passenger request `shouldBe` Right sampleMan {passengerAge = Just 20.0}

    it "選択肢の外の客室と性別は理由を返す" $ do
      request <- expectRight (readJson passengerTypes (json "{\"pclass\": 4, \"sex\": \"ねこ\", \"sib_sp\": 0, \"parch\": 0, \"fare\": 8}"))
      passenger request
        `shouldBe` Left
          [ "pclass は 1、2、3 のどれかにしてください"
          , "sex は female、male のどれかにしてください"
          ]

    it "必須の列が無ければ理由を返す" $ do
      request <- expectRight (readJson passengerTypes (json "{}"))
      passenger request
        `shouldBe` Left
          [ "pclass は必須です"
          , "sex は必須です"
          , "sib_sp は必須です"
          , "parch は必須です"
          , "fare は必須です"
          ]

    it "負の運賃は理由を返す" $ do
      request <- expectRight (readJson passengerTypes (json "{\"pclass\": 1, \"sex\": \"female\", \"sib_sp\": 0, \"parch\": 0, \"fare\": -1}"))
      passenger request `shouldBe` Left ["fare は 0 以上にしてください"]

  describe "ドメイン" $ do
    it "モデルの名前は置き場の順に並ぶ" $
      modelNames `shouldBe` [salesModelName, survivalModelName]

    it "線形回帰のモデルを映画の約束に合わせる" $ do
      linear <- fakeLinearModel
      -- 1 + 2*200 + 3*500 + 4*3000 + 5*1 = 13906
      predictSalesWith (linearSalesModel linear) sampleMovie `shouldBe` Right 13906.0

    it "パイプラインを乗客の約束に合わせる" $ do
      pipeline <- fakePipeline
      predictSurvivalWith (pipelineSurvivalModel pipeline) sampleWoman `shouldBe` Right True

  describe "読み込みの失敗" $ do
    it "モデルが無いことの説明にファイルの道は入らない" $
      loadErrorMessage (ModelNotFound salesModelName)
        `shouldBe` "学習済みモデル cinema が見つかりません"

    it "読めないモデルは別の失敗として表す" $
      loadErrorMessage (ModelUnreadable survivalModelName)
        `shouldBe` "学習済みモデル survived を読み込めません"

  storeContract
    "ファイルの置き場"
    (withFileStore "contract-real" (\(store, fill) -> fill >> pure store))
    (withFileStore "contract-empty" (pure . fst))
  storeContract "偽の置き場" fakeStore (pure emptyStore)

  describe "壊れたモデルのファイル" $ do
    it "モデルとして読めなければ ModelUnreadable を返す" $
      withFileStore "broken" $ \(store, _) -> do
        let dir = "dist-newstyle" </> "chapter15-test" </> "broken"
        BL.writeFile (modelFile dir salesModelName) garbage
        BL.writeFile (modelFile dir survivalModelName) garbage
        salesResult <- loadSalesModel store
        void salesResult `shouldBe` Left (ModelUnreadable salesModelName)
        survivalResult <- loadSurvivalModel store
        void survivalResult `shouldBe` Left (ModelUnreadable survivalModelName)

    it "読めないモデルは中身を出さずに 500 になる" $
      withFileStore "broken-api" $ \(store, _) -> do
        let dir = "dist-newstyle" </> "chapter15-test" </> "broken-api"
        BL.writeFile (modelFile dir salesModelName) garbage
        (status, body) <-
          call store "POST" "/cinema/sales" "{\"sns1\": 200, \"sns2\": 500, \"actor\": 3000, \"original\": 1}"
        status `shouldBe` 500
        body `shouldBe` A.object ["detail" A..= ("予測できませんでした" :: Text)]

    it "読めないモデルはヘルスでも偽になる" $
      withFileStore "broken-health" $ \(store, _) -> do
        let dir = "dist-newstyle" </> "chapter15-test" </> "broken-health"
        BL.writeFile (modelFile dir salesModelName) garbage
        health store `shouldReturn` [(salesModelName, False), (survivalModelName, False)]

  describe "サービス" $ do
    it "置き場からモデルを読んで興行収入を予測する" $ do
      store <- fakeStore
      predictSales store sampleMovie `shouldReturn` Right 13906.0

    it "置き場からモデルを読んで生存を予測する" $ do
      store <- fakeStore
      predictSurvival store sampleMan `shouldReturn` Right False

    it "モデルが無ければ読み込みの失敗を返す" $
      predictSales emptyStore sampleMovie
        `shouldReturn` Left (LoadFailed (ModelNotFound salesModelName))

    it "予測が失敗したら予測の失敗を返す" $
      predictSurvival brokenStore sampleMan
        `shouldReturn` Left (PredictFailed "特徴量がありません: Age")

    it "両方読み込めればヘルスは真" $ do
      store <- fakeStore
      health store `shouldReturn` [(salesModelName, True), (survivalModelName, True)]

    it "読み込めなければヘルスは偽" $
      health emptyStore `shouldReturn` [(salesModelName, False), (survivalModelName, False)]

  describe "経路の振り分け" $ do
    it "知っているメソッドとパス" $ do
      route "GET" "/health" `shouldBe` Health
      route "POST" "/cinema/sales" `shouldBe` PredictSalesRoute
      route "POST" "/survived" `shouldBe` PredictSurvivalRoute

    it "知っているパスに違うメソッドなら許すメソッドを添える" $ do
      route "POST" "/health" `shouldBe` MethodNotAllowed "GET"
      route "GET" "/cinema/sales" `shouldBe` MethodNotAllowed "POST"

    it "知らないパスは見つからない" $
      route "GET" "/unknown" `shouldBe` Unknown

  describe "ハンドラー" $ do
    it "ヘルスチェックを返す" $ do
      store <- fakeStore
      (status, body) <- call store "GET" "/health" ""
      status `shouldBe` 200
      body
        `shouldBe` A.object
          [ "status" A..= ("ok" :: Text)
          , "models" A..= A.object ["cinema" A..= True, "survived" A..= True]
          ]

    it "モデルが無ければヘルスは degraded" $ do
      (status, body) <- call emptyStore "GET" "/health" ""
      status `shouldBe` 200
      body
        `shouldBe` A.object
          [ "status" A..= ("degraded" :: Text)
          , "models" A..= A.object ["cinema" A..= False, "survived" A..= False]
          ]

    it "興行収入を予測する" $ do
      store <- fakeStore
      (status, body) <-
        call store "POST" "/cinema/sales" "{\"sns1\": 200, \"sns2\": 500, \"actor\": 3000, \"original\": 1}"
      status `shouldBe` 200
      body `shouldBe` A.object ["sales" A..= (13906.0 :: Double)]

    it "生存を予測する" $ do
      store <- fakeStore
      (status, body) <-
        call store "POST" "/survived" "{\"pclass\": 1, \"sex\": \"female\", \"sib_sp\": 0, \"parch\": 0, \"fare\": 50}"
      status `shouldBe` 200
      body `shouldBe` A.object ["survived" A..= True]

    it "JSON が読めなければ 422" $ do
      store <- fakeStore
      (status, body) <- call store "POST" "/cinema/sales" "{\"sns1\": \"たくさん\"}"
      status `shouldBe` 422
      body `shouldBe` A.object ["detail" A..= ["JSON の形式または値の型が正しくありません" :: Text]]

    it "検証に落ちたら理由を並べて 422" $ do
      store <- fakeStore
      (status, body) <-
        call store "POST" "/cinema/sales" "{\"sns1\": -1, \"sns2\": 2000, \"actor\": 300, \"original\": 2}"
      status `shouldBe` 422
      body
        `shouldBe` A.object
          [ "detail"
              A..= (["sns1 は 0 以上にしてください", "original は 0、1 のどれかにしてください"] :: [Text])
          ]

    it "モデルが無ければ 503" $ do
      (status, body) <-
        call emptyStore "POST" "/cinema/sales" "{\"sns1\": 200, \"sns2\": 500, \"actor\": 3000, \"original\": 1}"
      status `shouldBe` 503
      body `shouldBe` A.object ["detail" A..= ("学習済みモデル cinema が見つかりません" :: Text)]

    it "予測が失敗したら中身を出さずに 500" $ do
      (status, body) <-
        call brokenStore "POST" "/survived" "{\"pclass\": 1, \"sex\": \"female\", \"sib_sp\": 0, \"parch\": 0, \"fare\": 50}"
      status `shouldBe` 500
      body `shouldBe` A.object ["detail" A..= ("予測できませんでした" :: Text)]

    it "知らないパスは 404" $ do
      store <- fakeStore
      (status, body) <- call store "GET" "/unknown" ""
      status `shouldBe` 404
      body `shouldBe` A.object ["detail" A..= ("見つかりません" :: Text)]

    it "許していないメソッドは 405 で Allow を添える" $ do
      store <- fakeStore
      response <- handle store "GET" "/cinema/sales" ""
      responseStatus response `shouldBe` 405
      lookup "Allow" (responseHeaders response) `shouldBe` Just "POST"

  describe "実データで学習したモデル" $ do
    it "学習して保存したモデルを API から使える" $ do
      ready <- (&&) <$> Dataset.exists "cinema.csv" <*> Dataset.exists "Survived.csv"
      if not ready
        then pendingWith "学習データがありません"
        else withFileStore "data" $ \(store, _) -> do
          dir <- Dataset.dir
          let modelDir = "dist-newstyle" </> "chapter15-test" </> "data"
          trained <- trainAndSaveModels dir modelDir
          expectRight trained
          health store `shouldReturn` [(salesModelName, True), (survivalModelName, True)]
          (status, body) <-
            call store "POST" "/cinema/sales" "{\"sns1\": 200, \"sns2\": 500, \"actor\": 3000, \"original\": 1}"
          status `shouldBe` 200
          sales <- expectRight (salesOf body)
          -- Java 版・Scala 版・Clojure 版は 7730.457421687023、PHP 版は …019、
          -- Elixir 版は …016。正規方程式を解くのが自作のガウス・ジョルダン法か
          -- 各言語の行列ライブラリかの違いで、丸めの順が変わる。
          -- ほかの言語版とは許容を決めて突き合わせ、Haskell 版の値そのものも固定する。
          abs (sales - 7730.457421687023) `shouldSatisfy` (< 1.0e-6)
          sales `shouldBe` 7730.457421687022
          (survivedStatus, survivedBody) <-
            call store "POST" "/survived" "{\"pclass\": 1, \"sex\": \"female\", \"sib_sp\": 0, \"parch\": 0, \"fare\": 50}"
          survivedStatus `shouldBe` 200
          survivedBody `shouldBe` A.object ["survived" A..= True]

    it "保存と読み込みを挟んでも予測値は変わらない" $ do
      ready <- Dataset.exists "cinema.csv"
      if not ready
        then pendingWith "学習データがありません"
        else withFileStore "roundtrip" $ \(store, _) -> do
          file <- Dataset.path "cinema.csv"
          contents <- BL.readFile file
          split <- expectRight (Chapter07.prepareCinema contents testSize seed)
          target <- expectRight (Chapter07.targetValues (tTrain split))
          linear <- expectRight (Chapter07.fit (xTrain split) target Chapter07.featureColumns)
          direct <- expectRight (predictSalesWith (linearSalesModel linear) sampleMovie)
          saveSalesModel ("dist-newstyle" </> "chapter15-test" </> "roundtrip") linear
          loaded <- expectRight =<< loadSalesModel store
          predictSalesWith loaded sampleMovie `shouldBe` Right direct

    it "第 8 章と同じ深さと重みで学習する" $ do
      maxDepth `shouldBe` Just 5
      testSize `shouldBe` 0.2
      seed `shouldBe` 0

-- | 応答の JSON から sales の値を取り出す。
salesOf :: A.Value -> Either String Double
salesOf (A.Object o) = case KM.lookup "sales" o of
  Just (A.Number n) -> Right (realToFrac n)
  _ -> Left "sales がありません"
salesOf _ = Left "オブジェクトではありません"
