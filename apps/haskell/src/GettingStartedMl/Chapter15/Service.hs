{- | 第 15 章のアプリケーション層。置き場からモデルを読み込んで予測する。HTTP を知らない。

置き場は 'ModelStore' のレコードならなんでもよいので、テストでは偽物を渡せる。
状態を持たないので、同時に走るハンドラーから呼ばれても困らない。

'IO' が出てくるのは置き場から読むところだけで、**予測そのものは純粋関数** である。
第 1 章から保ってきた「読み込みだけが 'IO'」という構造が、そのまま層の境界になった。
-}
module GettingStartedMl.Chapter15.Service (
  PredictError (..),
  predictSales,
  predictSurvival,
  health,
) where

import Data.Text (Text)
import GettingStartedMl.Chapter15.Domain (
  Movie,
  Passenger,
  SalesModel (..),
  SurvivalModel (..),
  salesModelName,
  survivalModelName,
 )
import GettingStartedMl.Chapter15.Store (LoadError, ModelStore (..))

{- | 予測に失敗する理由。

読み込みの失敗と予測の失敗を分けておくと、API がステータスコードに
変えるところ（503 と 500）が網羅的なパターンマッチになる。
-}
data PredictError
  = LoadFailed LoadError
  | PredictFailed String
  deriving (Eq, Show)

-- | 映画の特徴量から興行収入を予測する。
predictSales :: ModelStore -> Movie -> IO (Either PredictError Double)
predictSales store movie = run (`predictSalesWith` movie) (loadSalesModel store)

-- | 乗客の特徴量から生存するかどうかを予測する。
predictSurvival :: ModelStore -> Passenger -> IO (Either PredictError Bool)
predictSurvival store passenger =
  run (`predictSurvivalWith` passenger) (loadSurvivalModel store)

-- | モデルを読み込んで当てはめ、2 種類の失敗を 1 つの型にまとめる。
run :: (m -> Either String a) -> IO (Either LoadError m) -> IO (Either PredictError a)
run apply load = do
  loaded <- load
  pure $ case loaded of
    Left err -> Left (LoadFailed err)
    Right model -> either (Left . PredictFailed) Right (apply model)

{- | モデルごとに、読み込めるかどうかを返す。

'GettingStartedMl.Chapter15.Domain.modelNames' と同じ順のリストで返す。
'Data.Map' はキーの順で並ぶので、表示したい順を保ちたいところはリストにする
（第 1 章から変わらない扱い）。
-}
health :: ModelStore -> IO [(Text, Bool)]
health store = do
  sales <- loadSalesModel store
  survival <- loadSurvivalModel store
  pure [(salesModelName, isReady sales), (survivalModelName, isReady survival)]
 where
  isReady = either (const False) (const True)
