-- | 学習データのディレクトリを求める。
module GettingStartedMl.Dataset (
  envName,
  defaultDir,
  dir,
  path,
  exists,
) where

import System.Directory (doesFileExist)
import System.Environment (lookupEnv)
import System.FilePath ((</>))

-- | 実データの置き場をテストや CI から差し替えるための環境変数の名前。
envName :: String
envName = "ML_DATA_DIR"

-- | 既定の置き場。テストは apps/haskell で走るので、相対パスで apps/data に届く。
defaultDir :: FilePath
defaultDir = ".." </> "data" </> "sukkiri-ml"

-- | 学習データのディレクトリを返す。
dir :: IO FilePath
dir = do
  value <- lookupEnv envName
  pure $ case value of
    Just v | not (null v) -> v
    _ -> defaultDir

-- | 学習データのファイルへの道を返す。
path :: String -> IO FilePath
path name = (</> name) <$> dir

-- | 学習データがあるかを返す。実データのテストはこれで外す。
exists :: String -> IO Bool
exists name = path name >>= doesFileExist
