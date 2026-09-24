{-# LANGUAGE OverloadedStrings #-}

{- | 第 15 章のプレゼンテーション層。予測 API のハンドラー。

経路の振り分け（'route'）と応答の組み立て（'jsonResponse'）は純粋関数で、
'IO' が出てくるのは 'handle' がモデルを読み込むところだけである。
Scotty はいちばん外側で 'handle' を呼ぶだけの薄い層にしてある。

経路の振り分けのライブラリ（Scotty の @get@・@post@）は使わず、
メソッドとパスを 'Route' という直和型に写す。404 と 405 の場合分けを
自分で持てるうえ、**サーバーを起動せずにテストできる**。
-}
module GettingStartedMl.Chapter15.Api (
  Response (..),
  Route (..),
  route,
  jsonResponse,
  handle,
  application,
) where

import Control.Monad.IO.Class (liftIO)
import qualified Data.Aeson as A
import qualified Data.Aeson.Key as K
import qualified Data.ByteString.Lazy as BL
import qualified Data.Map.Strict as M
import Data.Text (Text)
import qualified Data.Text.Encoding as TE
import qualified Data.Text.Lazy as TL
import GettingStartedMl.Chapter15.Service (
  PredictError (..),
  health,
  predictSales,
  predictSurvival,
 )
import GettingStartedMl.Chapter15.Store (LoadError (..), ModelStore, loadErrorMessage)
import GettingStartedMl.Chapter15.Validation (
  FieldType,
  movie,
  movieTypes,
  passenger,
  passengerTypes,
  readJson,
 )
import qualified Network.HTTP.Types.Status as Status
import qualified Network.Wai as Wai
import qualified Web.Scotty as Scotty

-- | 応答。Scotty にも WAI にも依存しないただの値なので、テストで中身を比べられる。
data Response = Response
  { responseStatus :: Int
  , responseHeaders :: [(Text, Text)]
  , responseBody :: BL.ByteString
  }
  deriving (Eq, Show)

{- | 要求の行き先。

直和型なので、行き先を増やしたときに 'handle' の節を書き漏らせば
コンパイルが止まる。405 は許すメソッドを一緒に持つ。
-}
data Route
  = Health
  | PredictSalesRoute
  | PredictSurvivalRoute
  | MethodNotAllowed Text
  | Unknown
  deriving (Eq, Show)

-- | メソッドとパスから行き先を決める。純粋関数。
route :: Text -> Text -> Route
route method path = case path of
  "/health" -> allow "GET" Health
  "/cinema/sales" -> allow "POST" PredictSalesRoute
  "/survived" -> allow "POST" PredictSurvivalRoute
  _ -> Unknown
 where
  allow expected found = if method == expected then found else MethodNotAllowed expected

-- | JSON の応答を組み立てる。Content-Type を付ける場所を 1 か所にまとめる。
jsonResponse :: Int -> [(Text, Text)] -> A.Value -> Response
jsonResponse status headers body =
  Response
    { responseStatus = status
    , responseHeaders = ("Content-Type", "application/json; charset=utf-8") : headers
    , responseBody = A.encode body
    }

-- | 理由を並べた 422 の応答。
unprocessable :: [Text] -> Response
unprocessable reasons = jsonResponse 422 [] (A.object ["detail" A..= reasons])

-- | 説明を 1 つ持つ応答。
detail :: Int -> Text -> Response
detail status message = jsonResponse status [] (A.object ["detail" A..= message])

{- | 予測の失敗をステータスコードに変える。

**例外ではなく直和型なので、場合分けの網羅をコンパイラが確かめる。**
PHP 版・Elixir 版は @catch@ や @rescue@ の節を並べていたが、
そこに漏れがあってもコンパイルは通る。
-}
predictFailure :: PredictError -> Response
predictFailure (LoadFailed err@(ModelNotFound _)) = detail 503 (loadErrorMessage err)
predictFailure (LoadFailed (ModelUnreadable _)) = failed
predictFailure (PredictFailed _) = failed

-- | 内部の事情を応答に出さないための 500。
failed :: Response
failed = detail 500 "予測できませんでした"

-- | 要求を受けて応答を返す。'IO' はモデルを読み込むためだけに要る。
handle :: ModelStore -> Text -> Text -> BL.ByteString -> IO Response
handle store method path body = case route method path of
  Health -> do
    models <- health store
    pure $
      jsonResponse
        200
        []
        ( A.object
            [ "status" A..= (if all snd models then "ok" else "degraded" :: Text)
            , "models" A..= A.object [K.fromText name A..= ready | (name, ready) <- models]
            ]
        )
  PredictSalesRoute ->
    predictWith body movieTypes movie (predictSales store) (\value -> A.object ["sales" A..= value])
  PredictSurvivalRoute ->
    predictWith
      body
      passengerTypes
      passenger
      (predictSurvival store)
      (\value -> A.object ["survived" A..= value])
  MethodNotAllowed allowed ->
    pure $
      jsonResponse 405 [("Allow", allowed)] (A.object ["detail" A..= ("許していないメソッドです" :: Text)])
  Unknown -> pure (detail 404 "見つかりません")

-- | 本文を読んで検証し、正しければ予測して 200 にする。失敗はステータスコードに変える。
predictWith
  :: (A.ToJSON output)
  => BL.ByteString
  -> M.Map Text FieldType
  -> (A.Object -> Either [Text] input)
  -> (input -> IO (Either PredictError output))
  -> (output -> A.Value)
  -> IO Response
predictWith body types check predict toBody =
  case readJson types body >>= check of
    Left reasons -> pure (unprocessable reasons)
    Right input -> either predictFailure (jsonResponse 200 [] . toBody) <$> predict input

-- | Scotty のアプリケーション。どのメソッド・どのパスも 'handle' に渡すだけ。
application :: ModelStore -> Scotty.ScottyM ()
application store =
  Scotty.matchAny (Scotty.function (const (Just []))) $ do
    request <- Scotty.request
    body <- Scotty.body
    response <-
      liftIO $
        handle
          store
          (TE.decodeUtf8Lenient (Wai.requestMethod request))
          (TE.decodeUtf8Lenient (Wai.rawPathInfo request))
          body
    Scotty.status (statusOf (responseStatus response))
    mapM_
      (\(name, value) -> Scotty.setHeader (TL.fromStrict name) (TL.fromStrict value))
      (responseHeaders response)
    Scotty.raw (responseBody response)

{- | 状態の番号を HTTP の状態にする。

この API が返す番号だけを並べる。知らない番号が来たら説明を空にする
（そこに来ないことは 'handle' の型ではなく 'route' と各節が保証している）。
-}
statusOf :: Int -> Status.Status
statusOf 200 = Status.status200
statusOf 404 = Status.status404
statusOf 405 = Status.status405
statusOf 422 = Status.status422
statusOf 500 = Status.status500
statusOf 503 = Status.status503
statusOf other = Status.mkStatus other ""
