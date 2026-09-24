---
type: Article
title: "第 10 章: ロジスティック回帰とアンサンブル学習"
description: "ロジスティック回帰とランダムフォレストを Haskell で自作し、第 3 章の決定木を部品として使う。モデルを型クラスで抽象し、型の違うモデルを 1 つのリストに並べるために存在型を使う。浮動小数点の足し算の順をほかの言語版にそろえる話を扱う。"
tags: [article,getting-start-ml,haskell]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが増えると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。この章では **型クラス** `Classifier` を用意して、どのモデルも同じ `score` に通せるようにします。

最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作します。

[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md) と同じ TODO リストで進め、[Java 版](../java/10-logistic-regression-and-ensemble.md)・[Clojure 版](../clojure/10-logistic-regression-and-ensemble.md)・[Elixir 版](../elixir/10-logistic-regression-and-ensemble.md)・[PHP 版](../php/10-logistic-regression-and-ensemble.md) と数値を対比します。注目してほしいのは次の 4 点です。

- **Haskell には、ロジスティック回帰もランダムフォレストもライブラリがありません。** ここで作るものがそのまま最終実装です（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）。[Elixir 版](../elixir/10-logistic-regression-and-ensemble.md) は Scholar のロジスティック回帰とだけ突き合わせられ、[PHP 版](../php/10-logistic-regression-and-ensemble.md) は両方を Rubix ML と比べられました。**Haskell 版は突き合わせる相手が 1 つもない、シリーズでいちばん狭い章です**
- **モデルを型クラスで抽象すると、リストに並べるのに存在型が要ります。** PHP 版の interface と Elixir 版の関数には無かった手間です。型クラスは「同じ約束を守る」ことしか言わないので、`TreeModel` と `ForestModel` は別の型のままだからです
- **`sum` を素直に書くと、ほかの言語版と数値が合いません。** リストの `sum` は右結合になりうるので、学習の計算では左畳み込みを明示します
- **特徴量の重要度は Java 版とは一致せず、Elixir 版と一致しました。** 原因は第 3 章のジニ不純度の和を取る順で、**`Data.Map` が鍵の順にたどる**ことに由来します

データは第 2 章・第 3 章と同じ iris を使い、第 2 章の `prepareIris` で前処理します。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 合計が 1 になる確率にする
  - [ ] 大きな値でもあふれない
- [ ] 交差エントロピーで損失を測る
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 分けられるデータを正しく予測する
  - [ ] 学習した品種が名前の順に並ぶ
  - [ ] 繰り返すほど損失が小さくなる
- [ ] ランダムフォレストを作る
  - [ ] 多数決でまとめる
  - [ ] ブートストラップ標本を作る
  - [ ] 木ごとに使う特徴量を絞る
- [ ] 特徴量の重要度を求める
  - [ ] 決定木 1 本の重要度を求める
  - [ ] 森の重要度を求める
- [ ] すべてのモデルを同じ関数で評価する

置き場は `src/GettingStartedMl/Chapter10.hs` 1 つです。PHP 版が 9 つのファイル（interface 2 つ・モデル 2 つ・分類器 6 つ）に分けたところが、Haskell では **型クラス 1 つと `data` 宣言 5 つ** で収まります。

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和」（スコア）を求め、それを確率に変換します。変換に使うのが **ソフトマックス関数** です。各スコアの指数を取り、合計で割ります。

```haskell
    it "合計が 1 になる" $
      abs (sum (softmax [1.0, 2.0, 3.0]) - 1.0) < 1e-12 `shouldBe` True

    it "同じスコアなら同じ確率になる" $
      softmax [2.0, 2.0] `shouldBe` [0.5, 0.5]
```

### 大きな値でもあふれない

素直に `exp z` を書くと、スコアが 1000 を超えたところであふれます。**Haskell の `exp` は例外を投げず、`Infinity` を返します。**

```haskell
    it "大きな値でもあふれない" $ do
      -- exp は例外を投げず Infinity を返すので、最大値を引かないと NaN になる。
      isInfinite (exp (1000 :: Double)) `shouldBe` True
      isNaN (exp (1000 :: Double) / (exp (1000 :: Double) + exp (1000 :: Double))) `shouldBe` True
      softmax [1000.0, 1000.0] `shouldBe` [0.5, 0.5]
```

**テストの 2 行目が、あふれたあとに何が起きるかを書いています。** `Infinity / Infinity` は `NaN` で、`NaN` はどんな比較も `False` にするので、予測は静かに壊れます。落ちてくれないぶん、正解率がおかしくなってから原因を探すことになります。Elixir 版の `:math.exp/1` が `ArithmeticError` で落ちるのとは逆で、JVM 系の言語版・PHP 版と同じ側です。

最大値を引いてから指数を取れば、引いた分は分母と分子で打ち消し合うので、結果を変えずにあふれを防げます。

```haskell
softmax :: [Double] -> [Double]
softmax [] = []
softmax z = map (/ total) exps
 where
  maximum' = foldl' max (head' z) z
  exps = map (\v -> exp (v - maximum')) z
  total = sumL exps
```

空のリストは例外ではなく空のリストにします。PHP 版が `if ($z === [])` で例外を投げたところが、**関数の定義を 1 行足すだけ** で済みます。

### 交差エントロピー

学習の目標は「正解の品種の確率を大きくする」ことです。その良し悪しを測るのが **交差エントロピー** で、正解の品種の確率の対数の平均にマイナスを付けた値です。

```haskell
    it "正解の確率が 0 でも Infinity にならない" $
      isInfinite (crossEntropy [[0.0, 1.0]] [0]) `shouldBe` False
```

**`log 0` は `-Infinity` です**（`log (-1)` は `NaN`）。こちらも例外にならないので、ごく小さい値（1e-12）を足して避けます。

**TODO リスト**:

- [x] ソフトマックス関数で確率に変換する
- [x] 交差エントロピーで損失を測る

## 10.4 ロジスティック回帰

### 足し算の順をそろえる

実装に入る前に、この章でいちばん効いた判断を書きます。**`sum` を使わない** ことです。

Haskell のリストの `sum` は `foldr` で定義されることがあり、`a + (b + (c + 0))` と右から足されます。ほかの言語版（Java・Clojure・Elixir・PHP）はすべて `acc + x` の左からの足し込みです。浮動小数点の足し算は結合則が成り立たないので、**順が違えば最後の桁が変わります**。

```haskell
-- | 左から足す和。
sumL :: [Double] -> Double
sumL = foldl' (+) 0.0
```

学習の内側（スコア・勾配・損失）はすべて `sumL` か `foldl'` で書きました。「速いから `foldl'` を使う」のではなく、**「ほかの言語版と数値を合わせるために順を決める」** のが理由です。

### 学習と予測

重みは `weights[特徴量][品種]` のリストのリストで持ち、ループの順（特徴量が外、品種が内）も Java 版にそろえます。

```haskell
rowScores :: [Double] -> [[Double]] -> [Double] -> [Double]
rowScores row weights bias =
  foldl' step bias (zip row weights)
 where
  step acc (value, forFeature) = [s + value * w | (s, w) <- zip acc forFeature]
```

**切片（`bias`）を畳み込みの初期値にしています。** 「切片から始めて、特徴量の数だけ足し込む」という式がそのまま形になりました。

1 エポックは次の 5 行です。

```haskell
  descend rows targets weights bias remaining losses =
    descend rows targets weights' bias' (remaining - 1) (loss : losses)
   where
    probabilities = [softmax (rowScores row weights bias) | row <- rows]
    loss = crossEntropy probabilities targets
    errors = zipWith errorOf probabilities targets
    weights' = ...
    bias' = ...
```

**再帰で「次の状態を作って自分を呼ぶ」ので、値を書き換える場所がありません。** 損失は新しいものを先頭に積み、最後に `reverse` で並びを戻します（リストの末尾に足すのは O(n) なので）。

品種は名前の順に並べ、番号に直してから学習します。

```haskell
    it "分けられるデータを学習する" $ do
      let model = expectRight $ logisticFit tinyX tinyT ["x"] 1.0 200
      logisticClasses model `shouldBe` ["a", "b"]
      logisticPredict model tinyX `shouldBe` Right tinyT

    it "損失は繰り返すほど小さくなる" $ do
      let model = expectRight $ logisticFit tinyX tinyT ["x"] 1.0 50
          losses = logisticLosses model
      length losses `shouldBe` 50
      and (zipWith (>) losses (drop 1 losses)) `shouldBe` True
```

**「損失が単調に減る」ことを、値ではなく関係で書いています。** `zipWith (>) losses (drop 1 losses)` は「隣り合う 2 つを比べる」をそのまま表します。学習率を変えれば値は動きますが、この関係は保たれるはずです。

予測は、スコアが最大の品種を選びます。同じ値なら先に現れたほうです。

```haskell
argmax :: [Double] -> Int
argmax values = snd (foldl' keepFirstMaximum (negate (1 / 0), 0) (zip values [0 ..]))
 where
  keepFirstMaximum (best, bestIndex) (value, i)
    | value > best = (value, i)
    | otherwise = (best, bestIndex)
```

`maximum` を使わないのは、**同じ値のときにどちらを返すかが保証されないから** です。第 3 章でも `maximumBy` が「同値なら後ろ」を返すので避けました。同じ理由がこの章でも効きます。初期値は `negate (1 / 0)`、つまり `-Infinity` です。あふれた値を初期値として素直に書けるのは、`exp` が落ちないのと同じ理由です。

**TODO リスト**:

- [x] ロジスティック回帰を学習して予測する

## 10.5 ランダムフォレスト

### 仕組み

ランダムフォレストは、次の 2 つの「ランダム」で少しずつ違う決定木を作り、多数決でまとめます。

1. **ブートストラップ標本**: 訓練データから重複を許して同じ件数を選び直す
2. **特徴量の部分集合**: 木ごとに、使う特徴量を何個かに絞る

### 乱数の状態を持ち回る

第 2 章の `shuffle` はシード（整数）を受け取りましたが、森を作るときは **1 本目の続きから 2 本目を引く** 必要があります。そこで、状態（`Seed`）を受け取って次の状態と一緒に返す形にします。

```haskell
bootstrapSample :: Int -> Seed -> ([Int], Seed)
bootstrapSample size = go size []
 where
  go 0 acc state = (reverse acc, state)
  go remaining acc state =
    let (i, next) = nextInt size state
     in go (remaining - 1) (i : acc) next
```

**状態が引数と戻り値に現れるので、「どの乱数を使ったか」がコードから読めます。** PHP 版が大域の `mt_srand` を扱い、「単位がモデルではなくプロセス」になったところとは対照的です。第 2 章で乱数生成器を自作した判断が、ここでいちばん効きました。

```haskell
    it "同じ状態からは同じ標本が出る" $
      fst (bootstrapSample 10 (newSeed 0)) `shouldBe` fst (bootstrapSample 10 (newSeed 0))

    it "状態を進めると別の標本になる" $ do
      let (first, next) = bootstrapSample 10 (newSeed 0)
      first `shouldNotBe` fst (bootstrapSample 10 next)
```

### 森を作る

第 3 章の `fit` は純粋な関数（`[Features] -> [Text] -> [Text] -> Maybe Int -> Either String Tree`）なので、**包むアダプターは要りません**。

```haskell
  go remaining state acc =
    let (rows, afterRows) = bootstrapSample size state
        (shuffled, afterColumns) = shuffleWithState columns afterRows
        chosen = take maxFeatures shuffled
        -- 列の順は元のまま残す。
        treeColumns = filter (`elem` chosen) columns
        sampleX = [pickColumns (x !! i) treeColumns | i <- rows]
        sampleT = [t !! i | i <- rows]
     in case C3.fit sampleX sampleT treeColumns maxDepth of
          Left err -> Left err
          Right tree -> go (remaining - 1) afterColumns (ForestTree treeColumns rows tree : acc)
```

**選んだ列を、並べ替えた順ではなく元の順に戻しています。** 第 3 章の `bestSplit` は「同じ不純度なら列の順で前の分割を選ぶ」ので、列の順を変えると同点の倒し方が変わり、ほかの言語版と木が違ってしまいます。

各木は「使った列」と「学習に使った行の番号」を覚えます。特徴量の重要度を、その木が実際に見たデータで測り直すためです。

```haskell
    it "同じシードなら同じ森になる" $ do
      let forest = expectRight $ forestFit tinyX tinyT ["x"] 5 1 Nothing 0
          again = expectRight $ forestFit tinyX tinyT ["x"] 5 1 Nothing 0
      length (forestTrees forest) `shouldBe` 5
      forest `shouldBe` again
```

**森そのものを `shouldBe` で比べられます。** 木も森も `deriving (Eq, Show)` を付けた代数的データ型なので、「同じシードなら同じ森」が 1 行で書けます。PHP 版が「`assertSame` は参照の同一性なので `assertEquals` を選ぶ」と注意したところが、Haskell では `Eq` が値の等価性しか意味しないので迷いません。

**TODO リスト**:

- [x] ランダムフォレストを作る

## 10.6 特徴量の重要度

### 計算方法

重要度は、「その特徴量で分割したことで、ジニ不純度がどれだけ減ったか」を件数で重み付けして足し上げ、合計が 1 になるように割ったものです。

```haskell
impurityDecreases :: C3.Tree -> [C3.Features] -> [Text] -> Either String [(Text, Double)]
impurityDecreases (C3.Leaf _) _ _ = Right []
impurityDecreases (C3.Branch split left right) x t = do
  ...
  let here = (C3.splitFeature split, fromIntegral (length t) * (C3.gini t - C3.splitImpurity split))
  fromLeft <- impurityDecreases left (map fst toLeft) (map snd toLeft)
  fromRight <- impurityDecreases right (map fst toRight) (map snd toRight)
  pure ((here : fromLeft) <> fromRight)
```

**「葉か節のどちらかである」ことが型に書いてあるので、場合分けの漏れはコンパイルで止まります。** `-Wall -Werror` があるので、`Leaf` の行を消すと警告ではなくエラーになります。Elixir 版はマップに鍵があるかどうかで見分けていて、網羅性は検査されませんでした。

### 森の重要度

木ごとの重要度を、その木のブートストラップ標本の上で測り、木の数で割って足し合わせます。木が使わなかった特徴量は、その木では 0 です。

```haskell
    it "森の重要度も合計が 1 になる" $ do
      let forest = expectRight $ forestFit tinyX tinyT ["x"] 5 1 Nothing 0
          importances = expectRight $ forestImportances forest tinyX tinyT ["x"]
      abs (sum (M.elems importances) - 1.0) < 1e-12 `shouldBe` True
```

**TODO リスト**:

- [x] 特徴量の重要度を求める

## 10.7 モデル共通の約束

### 型クラスで抽象する

モデルは「訓練データを受け取り、予測する関数を返す」ものです。これを型クラスにします。

```haskell
type Predictor = [C3.Features] -> Either String [Text]

class Classifier m where
  classifierName :: m -> Text
  fitModel :: m -> [C3.Features] -> [Text] -> [Text] -> Either String Predictor
```

**「予測するもの」には型クラスが要りません。** 関数そのものが `Predictor` だからです。PHP 版が `Predictor` interface と、クロージャを包む `FunctionPredictor` を書いたところが、Haskell では型の別名 1 行で済みます。

第 3 章の決定木も、この章の 2 つのモデルも、同じ形で書きます。

```haskell
instance Classifier TreeModel where
  classifierName (TreeModel depth) = "決定木（" <> depthLabel depth <> "）"
  fitModel (TreeModel depth) x t columns = do
    tree <- C3.fit x t columns depth
    pure (C3.predict tree)
```

`C3.predict tree` は「木を部分適用した関数」で、そのまま `Predictor` になります。**アダプターのクラスが 1 つも要りません。** Java 版がモデルごとにアダプターを書き、PHP 版が器を 1 つ書いたところです。

### 型の違うモデルを 1 つのリストに並べる

ところが、ここで Haskell だけの手間が出ます。

```haskell
models =
  [ TreeModel (Just 2)
  , LogisticModel 1.0 5000  -- ← 型が違うのでコンパイルが通らない
  ]
```

**リストの要素は 1 つの型でなければなりません。** 型クラスは「同じ約束を守る」ことしか言わないので、`TreeModel` と `LogisticModel` は別の型のままです。PHP の interface や Java のインターフェースが「同じ型として扱える」ものであるのに対し、**Haskell の型クラスは型をそろえません**。

解決には存在型を使います。

```haskell
{-# LANGUAGE ExistentialQuantification #-}

data SomeClassifier = forall m. (Classifier m) => SomeClassifier m
```

「`Classifier` の約束を守る何かの型 `m` の値」という 1 つの型を作り、中身の型を隠します。並べるときに包み、使うときに開きます。

```haskell
models :: [SomeClassifier]
models =
  [ SomeClassifier (TreeModel (Just shallowDepth))
  , SomeClassifier (LogisticModel 1.0 5000)
  , SomeClassifier (ForestModel defaultEstimators defaultMaxFeatures Nothing defaultSeed)
  , SomeClassifier (ForestModel defaultEstimators defaultMaxFeatures (Just shallowDepth) defaultSeed)
  ]
```

```haskell
  rows <- traverse (\(SomeClassifier model) -> (,) (classifierName model) <$> score model split columns) models
```

**この包みは、抽象の代償です。** Elixir 版は関数を並べるだけで済み（型を宣言しないから）、PHP 版は interface に揃えるだけで済みました（interface が型だから）。Haskell では「約束」と「型」が別のものなので、両方を書きます。そのかわり、`score` は型クラスの制約（`Classifier m =>`）だけで書けて、どのモデルにも使い回せます。

```haskell
score :: (Classifier m) => m -> C2.Split C3.Features -> [Text] -> Either String (Double, Double)
score model split columns = do
  predict <- fitModel model (C2.xTrain split) (C2.tTrain split) columns
  train <- predict (C2.xTrain split) >>= (`C1.accuracy` C2.tTrain split)
  test <- predict (C2.xTest split) >>= (`C1.accuracy` C2.tTest split)
  pure (train, test)
```

第 3 章で第 1 章の `accuracy` を `Eq a =>` に抽象しておいたので、派閥にも品種にもそのまま使えます。**抽象を書いたぶんだけ再利用できる** という第 3 章の結論が、この章で回収されました。

**TODO リスト**:

- [x] すべてのモデルを同じ関数で評価する

## 10.8 実データで突き合わせる

### モデルを比べる

```haskell
main :: IO ()
main = GettingStartedMl.Chapter10.run >>= either putStrLn putStr
```

```text
モデル	訓練データ	テストデータ
決定木（深さ 2）	0.9333	0.9556
ロジスティック回帰	0.9143	0.9111
ランダムフォレスト（100 本）	1.0000	0.9333
ランダムフォレスト（100 本・深さ 2）	0.9429	0.9556

ランダムフォレスト（100 本）の特徴量の重要度:
がく片長さ	0.1882
がく片幅	0.1265
花弁長さ	0.2713
花弁幅	0.4140
```

**8 個の正解率は、[Java 版の 10.10 節](../java/10-logistic-regression-and-ensemble.md)・[Clojure 版の 10.9 節](../clojure/10-logistic-regression-and-ensemble.md)・[Elixir 版の 10.9 節](../elixir/10-logistic-regression-and-ensemble.md)・[PHP 版の 10.9 節](../php/10-logistic-regression-and-ensemble.md) と完全に一致しました。** 分割（第 2 章の自作の線形合同法 + Fisher-Yates）・ブートストラップ標本・列の並べ替え・ループの順・足し算の向きをすべて Java 版にそろえたからです。

`assertSame` にあたる主張も書けます。

```haskell
    it "正解率は浮動小数点数として完全に一致する" $ do
      ...
          score (LogisticModel 1.0 5000) s C3.featureColumns
            `shouldBe` Right (0.9142857142857143, 0.9111111111111111)
```

正解率は「当たった件数 ÷ 全件数」なので、同じ予測をすれば浮動小数点数としても同じ値になります。**「近い」ではなく「同じ」と書けるテストは、それだけで強い主張になります。** Haskell では `shouldBe` が `Eq` の等価性なので、誤差の許容を書くかどうかを毎回選べます。

結果から読み取れることは次のとおりです。

- **アンサンブルがいつも勝つわけではありません。** 深さを制限しないランダムフォレスト（0.9333）は、第 3 章の深さ 2 の決定木（0.9556）より低くなりました。木 1 本 1 本が訓練データを分け切るまで深く育ち（訓練データの正解率 1.0000）、iris のような小さなデータでは多数決でも過学習を打ち消し切れていません。木の深さを 2 に制限した森は、決定木と同じ 0.9556 です
- **テストデータ 45 件では、1 件の違いが 0.0222 の差になります。** 1 回の分割で測った正解率の小さな差でモデルの優劣を決めるのは危険です。分け方を変えて何度も測る交差検証は第 11 章で扱います
- **突き合わせる相手はライブラリではなく、ほかの言語版でした。** PHP 版は Rubix ML の森と比べて「標本の作り方が違っても正解率は一致する」ことを示せましたが、Haskell 版にはその相手がいません。**同じ数値を 5 つの言語で独立に作れたことが、ここでの検証のすべてです**

### 特徴量の重要度

**重要度の 4 個は、Elixir 版とは完全に一致し、Java 版・Clojure 版・PHP 版とは 2 つずれました。**

| 特徴量 | Haskell 版 | Elixir 版 | Java 版・Clojure 版・PHP 版 |
|--------|-----------|----------|------------------------|
| がく片長さ | 0.1882 | 0.1882 | 0.1882 |
| がく片幅 | **0.1265** | **0.1265** | 0.1271 |
| 花弁長さ | **0.2713** | **0.2713** | 0.2708 |
| 花弁幅 | 0.4140 | 0.4140 | 0.4140 |

原因は [Elixir 版の 10.9 節](../elixir/10-logistic-regression-and-ensemble.md) が突き止めたものと同じでした。**第 3 章のジニ不純度が、ラベルの出現数を数えたあと `M.elems` でたどる** からです。

```haskell
gini :: [Text] -> Double
gini labels = 1.0 - sum [ratio n * ratio n | n <- M.elems (countLabels labels)]
```

`Data.Map` は **鍵の順**（品種の名前の順）に並びます。PHP の連想配列と Clojure の小さなマップは **挿入順**（データに現れた順）なので、和を積む順が違います。浮動小数点の足し算は順によって最後の桁が変わるので、**同点だった 2 つの分割に 1 ulp の差が付き、どちらを選ぶかが変わります**。第 3 章の `bestSplit` は「同じ不純度なら先に見つけたほう」という規則なので、同点が同点でなくなれば結果も変わります。

Elixir のマップも鍵の項順でたどるので、Haskell 版は **Elixir 版と同じ側** に落ちました。

**正解率は 4 桁では変わらず、重要度だけが変わった**ところも Elixir 版と同じです。分割が 1 つ違っても、100 本の多数決では打ち消されます。**重要度は正解率よりもずっと実装に敏感である**、という Elixir 版の結論がそのまま確かめられました。

この差はテストにコメントとして残しました。

```haskell
          -- Elixir 版と完全に一致する。Java 版・Clojure 版・PHP 版は
          -- がく片幅 0.1271・花弁長さ 0.2708 で、4 桁目がずれる。第 3 章の
          -- ジニ不純度がラベルの出現数を 'M.elems' でたどるため、和を取る順が
          -- 「挿入順」ではなく「鍵の順」になり、同点だった分割に 1 ulp の差が付く。
          map (\c -> rounded 4 (M.findWithDefault 0 c importances)) columns
            `shouldBe` [0.1882, 0.1265, 0.2713, 0.4140]
```

**「合わなかった」ことを、理由つきでテストに書くのが正しい扱いです。** 値を合わせるために第 3 章の `gini` に手を入れることもできますが、それは「Java 版に合わせるためだけに `Data.Map` を避ける」という、言語に合わない設計になります。**版をまたいで数値を比べるなら、同点をどう倒すかまで決めておく必要がある** という結論のほうが大事です。

### 実データのテスト

損失が減ることも実データで確かめたいところですが、**「毎回減る」は成り立ちませんでした**。

小さなデータ（`x` が 1・2・8・9）に学習率 1.0 をかけると、最初の 50 回の損失はこうなります。

```text
0.6931, 2.6327, 1.1071, 0.2023, 0.2959, 1.2925, 0.2826, 0.0832, ...
（最後の 3 回）0.022866, 0.022464, 0.022077
```

**49 回の変化のうち 3 回は増えています。** 標準化していない値に大きな学習率をかけたので、序盤は行きすぎているのです。そこでテストは 2 つに分けました。

```haskell
    it "損失は最初より最後のほうが小さくなる" $ do
      ...
      -- 学習率 1.0 で標準化していない値を渡すと、序盤は行きすぎて損失が増える回もある。
      -- 「毎回減る」ではなく「最後は最初より小さい」が正しい主張になる。
      -- head と last は部分関数なので、-Wx-partial が -Werror で止める。
      case (take 1 losses, drop 49 losses) of
        ([firstLoss], [lastLoss]) -> lastLoss < firstLoss `shouldBe` True
        _ -> expectationFailure "損失が 50 個ありません"

    it "終盤の損失は単調に減る" $ do
      ...
      and (zipWith (>) tailLosses (drop 1 tailLosses)) `shouldBe` True
```

最初は `last losses < head losses` と書いたのですが、これも通りませんでした。**`head` と `last` は空のリストで落ちる部分関数なので、`-Wx-partial` が `-Werror` でコンパイルを止めます。** テストの中でも部分関数は書けません。第 2 章で `let Right x = ...` が `-Wincomplete-uni-patterns` に止められたのと同じ形で、**「たぶん空ではない」という前提をコードに書かせない**という方針が一貫しています。

**テストが落ちたときに実装ではなく主張のほうを直した**例です。勾配降下法は「損失が毎回減る」ことを保証しません。保証されるのは、学習率が十分小さければ最終的に下がる、ということだけです。**通らないテストは、実装が間違っているのか、こちらの理解が間違っているのかを、先に切り分けます。**

実データのテストは、学習データが無ければ飛ばします。

```haskell
      split <- irisSplit
      case split of
        Nothing -> pendingWith "学習データがありません"
        Just s -> do
          ...
```

`Dataset.exists` が偽なら `pendingWith` で飛ばすので、学習データを持たない環境でもテストは緑になります。飛ばしたことは Hspec の結果に残ります。

## 10.9 Notebook で探索する

Haskell 版では Notebook と可視化を扱いません。モデルごとの正解率のグラフ、ロジスティック回帰の損失の推移、モデル別の特徴量の重要度、森の大きさと正解率の関係は、[Python 版の 10.11 節](../python/10-logistic-regression-and-ensemble.md) と [Kotlin 版の 10.11 節](../kotlin/10-logistic-regression-and-ensemble.md) の可視化を参照してください。

`logisticLosses`（繰り返しごとの損失の並び）と `forestImportances`（特徴量ごとの重要度）は、ほかの版と同じ形のデータを返すので、同じ観点で読めます。

## 10.10 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、モデルを型クラスでそろえてまとめて評価しました。

| モデル | 自作したもの | 突き合わせた相手 |
|--------|------------|-------------------|
| ロジスティック回帰 | `softmax`・`crossEntropy`・`logisticFit` | **ライブラリなし**（Java 版・Clojure 版・Elixir 版・PHP 版の数値と一致） |
| ランダムフォレスト | `bootstrapSample`・`forestFit`・`majorityVote` | **ライブラリなし**（同上） |
| 特徴量の重要度 | `treeImportances`・`forestImportances` | **ライブラリなし**（Elixir 版とだけ一致） |

Haskell 版ならではの学びは 6 つです。

1. **型クラスは型をそろえない** — PHP の interface は「同じ型として扱える」ものだが、Haskell の型クラスは「同じ約束を守る」だけで、`TreeModel` と `ForestModel` は別の型のまま。1 つのリストに並べるには存在型（`SomeClassifier`）で包む。Elixir 版は関数を並べるだけ、PHP 版は interface に揃えるだけで済んだところが、Haskell では「約束」と「型」の両方を書く
2. **予測する側には抽象が要らない** — 「特徴量からラベルを返す」は関数そのものなので、`type Predictor = [Features] -> Either String [Text]` の 1 行で済む。PHP 版が `Predictor` interface と `FunctionPredictor` を書いたところ、アダプターのクラスが 1 つも要らなかった
3. **`sum` を書いてよいか毎回考える** — リストの `sum` は右結合になりうる。ほかの言語版と桁まで合わせたい計算では `foldl'` を明示する。**速度のためではなく、数値を合わせるために順を決めた**
4. **例外にならない数が多い** — `exp 1000` は `Infinity`、`log 0` は `-Infinity`、`0/0` は `NaN`。どれも落ちないので、ソフトマックスで最大値を引くことと、対数にごく小さい値を足すことが必須になる。`-Infinity` を `argmax` の初期値に素直に書けるのは、同じ性質の裏返し
5. **森そのものを `shouldBe` で比べられる** — 木も森も `deriving (Eq, Show)` を付けた代数的データ型なので、「同じシードなら同じ森」が 1 行。PHP 版が `assertSame` と `assertEquals` を使い分けたところで迷わない
6. **`Data.Map` の鍵の順が、版間の一致を分けた** — 重要度の 4 桁目で Elixir 版と同じ側に落ち、Java 版・Clojure 版・PHP 版とずれた。第 3 章のジニ不純度が `M.elems` でたどるためで、**同点の倒し方を決めていない設計であることが、言語ごとに違う形で現れる**

iris のテストデータ 45 件では、どのモデルも数件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。

Haskell 版のほかの章は [Haskell 版のトップ](index.md) から辿れます。
