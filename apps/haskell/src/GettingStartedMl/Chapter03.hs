{-# LANGUAGE OverloadedStrings #-}

{- | 第 3 章: 決定木による分類。

Haskell には決定木のライブラリが無いので、ここで作る木がそのまま最終実装になる。

木は代数的データ型で表す。葉と節のどちらかしかないことが型に書いてあるので、
場合分けの漏れはコンパイルで止まる（@-Wall -Werror@）。Elixir 版がマップの鍵で、
PHP 版が共用型で表したところが、Haskell では @data@ 1 つで済む。
-}
module GettingStartedMl.Chapter03 (
  Features,
  Split (..),
  Tree (..),
  featureColumns,
  maxDepths,
  gini,
  majority,
  bestSplit,
  fit,
  predictOne,
  predict,
  scoreTree,
  formatTree,
  run,
  report,
) where

import qualified Data.ByteString.Lazy as BL
import Data.List (minimumBy, nub, sortOn)
import qualified Data.Map.Strict as M
import Data.Ord (comparing)
import Data.Text (Text)
import qualified Data.Text as T
import GettingStartedMl.Chapter01 (accuracy)
import qualified GettingStartedMl.Chapter02 as C2
import qualified GettingStartedMl.Dataset as Dataset
import Text.Printf (printf)

-- | 列名から値への対応。第 2 章の前処理が返す形をそのまま使う。
type Features = M.Map Text Double

{- | 決定木の分割。特徴量の値が境界以下なら左、境界より大きければ右へ進む。

'splitImpurity' は、この分割で分けたときの左右のジニ不純度の重み付き平均。
-}
data Split = Split
  { splitFeature :: Text
  , splitThreshold :: Double
  , splitImpurity :: Double
  }
  deriving (Eq, Show)

{- | 決定木。葉はラベルを、節は分割と左右の部分木を持つ。

「木は葉か節のどちらかである」ことが型に書いてあるので、場合分けを 1 つ忘れると
コンパイルが止まる。Elixir 版はマップに鍵があるかどうかで見分けていて、
網羅性は検査されなかった。
-}
data Tree
  = Leaf Text
  | Branch Split Tree Tree
  deriving (Eq, Show)

-- | アヤメのデータの特徴量の列。正解ラベルの列を除いた順。
featureColumns :: [Text]
featureColumns = ["がく片長さ", "がく片幅", "花弁長さ", "花弁幅"]

-- | 表に載せる深さの並び。'Nothing' は上限なし。
maxDepths :: [Maybe Int]
maxDepths = [Just 1, Just 2, Just 3, Just 4, Just 5, Nothing]

-- | ジニ不純度。ラベルが 1 種類なら 0 で、種類が多く均等なほど大きい。
gini :: [Text] -> Double
gini [] = 0.0
gini labels = 1.0 - sum [ratio n * ratio n | n <- M.elems (countLabels labels)]
 where
  total = fromIntegral (length labels) :: Double
  ratio n = fromIntegral n / total

{- | いちばん多いラベルを返す。同数なら先に現れたほうを選ぶ。

'Data.List.maximumBy' は同値なら **後ろ** のものを返すので使えない。
最初に現れた順（'nub'）でたどり、「今より多いときだけ入れ替える」畳み込みにする。
-}
majority :: [Text] -> Either String Text
majority labels =
  case nub labels of
    [] -> Left "正解ラベルがありません"
    -- head は -Wx-partial が止めるので、空かどうかをパターンで分ける。
    (first : rest) -> Right (foldl' pick first rest)
 where
  counts = countLabels labels
  count label = M.findWithDefault 0 label counts
  pick best candidate
    | count candidate > count best = candidate
    | otherwise = best

{- | 左右の不純度の重み付き平均が最も小さくなる分割を返す。分けられなければ 'Nothing'。

同じ不純度なら先に見つけた（列の順で前の）分割を選ぶ。列が無ければ 'Left' を返す。
-}
bestSplit :: [Features] -> [Text] -> [Text] -> Either String (Maybe Split)
bestSplit [] _ _ = Right Nothing
bestSplit x t columns
  | gini t == 0.0 = Right Nothing
  | otherwise = do
      found <- concat <$> traverse (candidates x t) columns
      pure $ case found of
        [] -> Nothing
        -- minimumBy は同値なら先のものを返すので、列の順がそのまま優先順になる。
        _ -> Just (minimumBy (comparing splitImpurity) found)

{- | 深さの上限まで分割を繰り返して木を作る。'Nothing' なら上限なし。

失敗は 'Either' で返るので、再帰の奥で列が足りなくても @do@ 記法のまま呼び出し元まで伝わる。
-}
fit :: [Features] -> [Text] -> [Text] -> Maybe Int -> Either String Tree
fit x t columns maxDepth
  | length x /= length t =
      Left (printf "特徴量と正解ラベルの件数が違います: %d と %d" (length x) (length t))
  | otherwise = build x t maxDepth
 where
  build xs ts depth = do
    split <- if depth == Just 0 then Right Nothing else bestSplit xs ts columns
    case split of
      Nothing -> Leaf <$> majority ts
      Just s -> do
        sides <- traverse (side s) (zip xs ts)
        let next = subtract 1 <$> depth
            left = [pair | (goesLeft, pair) <- sides, goesLeft]
            right = [pair | (goesLeft, pair) <- sides, not goesLeft]
        Branch s
          <$> build (map fst left) (map snd left) next
          <*> build (map fst right) (map snd right) next

  side s (features, label) = do
    value <- featureValue features (splitFeature s)
    pure (value <= splitThreshold s, (features, label))

-- | 木をたどって 1 件のラベルを予測する。
predictOne :: Tree -> Features -> Either String Text
predictOne (Leaf label) _ = Right label
predictOne (Branch s left right) features = do
  value <- featureValue features (splitFeature s)
  predictOne (if value <= splitThreshold s then left else right) features

-- | 特徴量ごとのラベルを予測する。
predict :: Tree -> [Features] -> Either String [Text]
predict tree = traverse (predictOne tree)

-- | 木の予測と正解ラベルの一致率を返す。
scoreTree :: Tree -> [Features] -> [Text] -> Either String Double
scoreTree tree x t = predict tree x >>= (`accuracy` t)

-- | 木を字下げ付きの文字列にする。
formatTree :: Tree -> String
formatTree = go ""
 where
  go indent (Leaf label) = indent <> T.unpack label <> "\n"
  go indent (Branch s left right) =
    printf "%s%s <= %s\n" indent feature border
      <> go (indent <> "  ") left
      <> printf "%s%s > %s\n" indent feature border
      <> go (indent <> "  ") right
   where
    feature = T.unpack (splitFeature s)
    -- printf はロケールに依らないので、小数点は常に「.」になる。
    border = printf "%.4f" (splitThreshold s) :: String

-- | 深さごとの正解率と、深さ 2 の決定木を表示する。
run :: IO (Either String String)
run = do
  file <- Dataset.path "iris.csv"
  contents <- BL.readFile file
  pure (report contents)

-- | 読み込んだ中身から報告の文字列を作る。純粋関数なのでテストで確かめられる。
report :: BL.ByteString -> Either String String
report contents = do
  split <- C2.prepareIris contents 0.3 0
  rows <- traverse (accuracyRow split) maxDepths
  tree <- fit (C2.xTrain split) (C2.tTrain split) featureColumns (Just 2)
  pure $
    "深さ\t訓練データ\tテストデータ\n"
      <> concat rows
      <> "\n深さ 2 の決定木:\n"
      <> formatTree tree

-- | 深さ 1 つ分の行を作る。
accuracyRow :: C2.Split Features -> Maybe Int -> Either String String
accuracyRow split maxDepth = do
  tree <- fit (C2.xTrain split) (C2.tTrain split) featureColumns maxDepth
  train <- scoreTree tree (C2.xTrain split) (C2.tTrain split)
  test <- scoreTree tree (C2.xTest split) (C2.tTest split)
  pure (printf "%s\t%.4f\t%.4f\n" (maybe "制限なし" show maxDepth) train test)

{- | 1 つの列で、隣り合う値の中点を境界にした分割の候補をすべて返す。

'sortOn' は安定なので、同じ値の並びは元の順のまま。
-}
candidates :: [Features] -> [Text] -> Text -> Either String [Split]
candidates x t feature = do
  values <- traverse (`featureValue` feature) x
  let sorted = sortOn fst (zip values t)
      sortedValues = map fst sorted
      labels = map snd sorted
  pure
    [ Split feature ((before + after) / 2.0) (weightedGini (take i labels) (drop i labels))
    | (i, before, after) <- zip3 [1 ..] sortedValues (drop 1 sortedValues)
    , before /= after
    ]

-- | 左右のジニ不純度を件数で重み付けして平均する。
weightedGini :: [Text] -> [Text] -> Double
weightedGini left right =
  (count left * gini left + count right * gini right) / (count left + count right)
 where
  count = fromIntegral . length

-- | 特徴量から列の値を読む。列が無ければ 'Left' を返す。
featureValue :: Features -> Text -> Either String Double
featureValue features column =
  case M.lookup column features of
    Just value -> Right value
    Nothing -> Left ("列がありません: " <> T.unpack column)

-- | ラベルごとの件数。
countLabels :: [Text] -> M.Map Text Int
countLabels = foldl' (\counts label -> M.insertWith (+) label 1 counts) M.empty
