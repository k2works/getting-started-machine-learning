{-# LANGUAGE OverloadedStrings #-}

{- | 学習済みモデルの置き場の約束。

Java 版の @interface ModelStore@、PHP 版の @interface ModelStore@、
Clojure 版の @defprotocol ModelStore@、Elixir 版の @behaviour@ に当たる。

Haskell には約束の書き方が 2 つある。**型クラス** と、**関数を詰めたレコード**
である。ここではレコードを選んだ。理由は記事の 15.2 節に書いた。要点は
「置き場は型ではなく値でよい」「偽物を引数で作り分けたい」の 2 つである。

このレコードが決めるのは関数の名前と型だけで、「無ければ 'ModelNotFound' を返す」
という取り決めは書けない。そこはテストの約束のテストで、本物と偽物の両方に確かめる。
-}
module GettingStartedMl.Chapter15.Store (
  LoadError (..),
  ModelStore (..),
  loadErrorMessage,
) where

import Data.Text (Text)
import GettingStartedMl.Chapter15.Domain (SalesModel, SurvivalModel)

{- | モデルを読み込めない理由。

例外ではなく直和型で表す。API がステータスコードに変えるところが
網羅的なパターンマッチになり、理由を増やしたらコンパイルが止まる。
説明にファイルの道を入れないのは、503 の応答としてそのまま外に出るため。
-}
data LoadError
  = -- | モデルがまだ保存されていない
    ModelNotFound Text
  | -- | ファイルはあるが、モデルとして読めない
    ModelUnreadable Text
  deriving (Eq, Show)

-- | 読み込めない理由の説明。応答の本文にそのまま入れられる言葉にする。
loadErrorMessage :: LoadError -> Text
loadErrorMessage (ModelNotFound name) = "学習済みモデル " <> name <> " が見つかりません"
loadErrorMessage (ModelUnreadable name) = "学習済みモデル " <> name <> " を読み込めません"

{- | 置き場。読み込む 2 つの関数を詰めたレコードで、それ自体がただの値である。

保存の関数は入れない。読み込みは API が使うが、保存は学習のときにしか
使わないので、API から見える約束を小さく保つ。
-}
data ModelStore = ModelStore
  { loadSalesModel :: IO (Either LoadError SalesModel)
  , loadSurvivalModel :: IO (Either LoadError SurvivalModel)
  }
