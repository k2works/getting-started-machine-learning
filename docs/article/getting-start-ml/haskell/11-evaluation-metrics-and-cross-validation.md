---
type: Article
title: "第 11 章: 評価指標と交差検証"
description: "混同行列・適合率・再現率・F 値・ROC 曲線と AUC・K 分割交差検証を Haskell の TDD で自作する。Haskell には評価指標のライブラリが 1 つも無いので、突き合わせる相手はほかの言語版の数値だけになる。評価関数も分類器もただの関数で足り、閾値の無限大は Maybe で表せる一方、Either で失敗を表すと交差検証が遅延しなくなることを実測で示す。"
tags: [article,getting-start-ml,haskell]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 11 章: 評価指標と交差検証

## 11.1 はじめに

ここまでの章では、モデルの良し悪しを **正解率** ひとつで測ってきました。第 3 章の決定木も、第 10 章のロジスティック回帰もランダムフォレストも、「テストデータの何割を当てたか」で比べています。

しかし正解率は、**どちらを間違えたか** を隠します。生存者を見逃したのか、亡くなった人を生存と言ったのか、数字は区別しません。この章では、その内訳を見るための指標 — 混同行列・適合率・再現率・F 値・ROC 曲線と AUC — を自作します。

もうひとつの主題は **交差検証** です。第 2 章から使ってきた「1 回だけ訓練データとテストデータに分ける」やり方は、たまたまの分け方に結果が左右されます。データを K 個に分け、順番にテストデータの役を回す K 分割交差検証で、その振れ幅を減らします。

[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md) と同じ TODO リストで進め、[Java 版](../java/11-evaluation-metrics-and-cross-validation.md)・[Clojure 版](../clojure/11-evaluation-metrics-and-cross-validation.md)・[Elixir 版](../elixir/11-evaluation-metrics-and-cross-validation.md)・[PHP 版](../php/11-evaluation-metrics-and-cross-validation.md) と数値を対比します。注目してほしいのは次の 4 点です。

- **Haskell には評価指標のライブラリが 1 つもありません。** 混同行列も ROC 曲線も交差検証も、ここで作るものがそのまま最終実装です（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）。[Elixir 版](../elixir/11-evaluation-metrics-and-cross-validation.md) は Scholar と、[PHP 版](../php/11-evaluation-metrics-and-cross-validation.md) は Rubix ML と突き合わせて「ライブラリの癖」を 4 つ見つけました。**Haskell 版にはその相手がいません。** 代わりに、自作の妥当性を性質のテストで示し、数値をほかの言語版と照らします
- **評価関数も分類器も、ただの関数で足ります。** 第 10 章では型クラスと存在型が要りましたが、この章では `data` も `class` も増えません。部分適用がそのまま「関数を返す関数」なので、PHP 版が書いた PHPDoc も Elixir 版が書いた無名関数も要らない形になります
- **閾値の「無限大」を `Maybe` で書けます。** ほかの言語版が正の無限大を置いた場所に、Haskell では「値が無い」と書けます
- **`Either` で失敗を表すと、交差検証が遅延しなくなります。** Elixir 版の `Stream.map` は「取り出した分だけ学習する」ものでした。`traverse` は全部の結果を要求するので、そうはなりません。**型で失敗を表すことの引き換え** です

データは Survived.csv（891 件）と cinema.csv（100 件）を使います。

## 11.2 正解率だけでは足りない理由

100 人のうち 5 人だけが病気で、残り 95 人が健康だとします。「全員が健康」と答えるだけの検査は、正解率 0.95 です。数字はよく見えますが、病気の人を 1 人も見つけていません。

正解と予測の組み合わせは 4 通りあります。正例（この章では「生存」）をどう当てたか・外したかで並べたものが **混同行列** です。

| | 予測が正例 | 予測が負例 |
| :--- | :--- | :--- |
| **正解が正例** | 真陽性（tp） | 偽陰性（fn） |
| **正解が負例** | 偽陽性（fp） | 真陰性（tn） |

ここから 3 つの指標が出ます。

- **適合率（precision）** = tp / (tp + fp)。正例と予測したうち、本当に正例だった割合
- **再現率（recall）** = tp / (tp + fn)。本当の正例のうち、正例と予測できた割合
- **F 値（F1 score）** = 適合率と再現率の調和平均

先ほどの検査は、適合率も再現率も 0 です。正解率 0.95 が隠していたものが、ここで見えます。

## 11.3 TODO リストの作成

**TODO リスト**:

- [ ] 混同行列を数える
- [ ] 適合率・再現率・F 値を求める
- [ ] 正解率と平均二乗誤差を求める
- [ ] ROC 曲線と AUC を求める
- [ ] K 分割交差検証の分け方を作る
- [ ] 分割ごとに学習して採点する
- [ ] 実データ（Survived・cinema）で交差検証する

置き場は `src/GettingStartedMl/Chapter11.hs` 1 つ、テストは `test/GettingStartedMl/Chapter11Spec.hs` 1 つです。PHP 版が `ConfusionMatrix`・`Fold`・`RocPoint` を別ファイルの `readonly class` にしたところが、Haskell では同じファイルの `data` 宣言 3 つで収まります。

## 11.4 混同行列を数える

### Red: 最初のテスト

```haskell
    it "正解と予測を一件ずつ数える" $
      confusionMatrix ["1", "1", "0", "0"] ["1", "0", "1", "0"] ("1" :: Text)
        `shouldBe` Right (ConfusionMatrix 1 1 1 1)
```

4 件で 4 通りがちょうど 1 回ずつ出る並びにしました。1 つでも数え違えれば落ちます。

### Green: `fn` は欄の名前にできる

```haskell
data ConfusionMatrix = ConfusionMatrix
  { cmTruePositive :: Int
  , cmFalsePositive :: Int
  , cmFalseNegative :: Int
  , cmTrueNegative :: Int
  }
  deriving (Eq, Show)
```

[Elixir 版](../elixir/11-evaluation-metrics-and-cross-validation.md) では `fn` が予約語で `cm.fn` と書けず、パターンマッチで別名に束縛していました。Haskell では `fn` を欄の名前にできます。**書けるからといって書くかは別** で、`fn` 単独では何の略か読めないので、どの言語版でも通じる `cmFalseNegative` にしました。

数えるところは畳み込みです。

```haskell
confusionMatrix :: (Eq a) => [a] -> [a] -> a -> Either String ConfusionMatrix
confusionMatrix actual predicted positive = do
  requireSameSize actual predicted
  pure (foldr count (ConfusionMatrix 0 0 0 0) (zip actual predicted))
 where
  count (a, p) cm =
    case (a == positive, p == positive) of
      (True, True) -> cm {cmTruePositive = cmTruePositive cm + 1}
      (False, True) -> cm {cmFalsePositive = cmFalsePositive cm + 1}
      (True, False) -> cm {cmFalseNegative = cmFalseNegative cm + 1}
      (False, False) -> cm {cmTrueNegative = cmTrueNegative cm + 1}
```

2 つの `Bool` の組で場合分けしているので、**4 通りを 1 つ忘れるとコンパイルが止まります**（`-Wall -Werror`）。Elixir 版は `cell(true, true) -> :tp` の形の関数節で同じことを書きましたが、網羅性は検査されませんでした。

型は `(Eq a) =>` です。「正例と等しいかどうか」しか要らないからで、ラベルが `Text` でも第 1 章の `Faction` でも同じ関数で数えられます。第 3 章で `accuracy` を `Eq` に広げたのと同じ判断です。

### 件数が違うときは黙って切り詰めない

```haskell
requireSameSize :: [a] -> [b] -> Either String ()
requireSameSize actual predicted
  | length actual /= length predicted =
      Left (printf "正解と予測の件数が違います: %d と %d" (length actual) (length predicted))
  | otherwise = Right ()
```

`zip` は短いほうに合わせて黙って打ち切ります。それは「84 件の正解と 85 件の予測を比べても 84 件ぶんの数字が返る」ということで、間違いが見えません。先に長さを確かめて `Left` を返します。

返す値が `()` なのが Haskell らしいところです。**結果は要らないが、失敗するかもしれない** ことだけを型で言っています。`do` 記法の中に 1 行置くと、失敗したらそこで止まります。

## 11.5 適合率・再現率・F 値

### 明白な実装

```haskell
precision :: ConfusionMatrix -> Double
precision cm =
  ratio
    (fromIntegral (cmTruePositive cm))
    (fromIntegral (cmTruePositive cm + cmFalsePositive cm))

f1Score :: ConfusionMatrix -> Double
f1Score cm = ratio (2.0 * p * r) (p + r)
 where
  p = precision cm
  r = recall cm
```

定義をそのまま書いています。`Either` を返さないのは、混同行列を作る時点で検査が済んでいるからです。**検査の済んだ値からは、失敗しない計算だけが出ていく** 形になります。

### 分母が 0 になる場合

```haskell
ratio :: Double -> Double -> Double
ratio _ 0.0 = 0.0
ratio numerator denominator = numerator / denominator
```

Haskell の `Double` は 0 で割っても落ちません。`1/0` は `Infinity`、`0/0` は `NaN` です。第 10 章でソフトマックスの `exp 1000` が `Infinity` になったのと同じ性質で、**落ちないぶん、自分で場合分けしないと静かに壊れます**。

```haskell
    it "正例と一度も予測しなければ適合率は零" $
      precision (ConfusionMatrix 0 0 5 5) `shouldBe` 0.0

    it "適合率も再現率も零なら F 値も零" $
      f1Score (ConfusionMatrix 0 1 1 1) `shouldBe` 0.0
```

`NaN` を返していたら、この 2 本はどちらも赤になります。`NaN /= NaN` なので、`shouldBe` は `NaN` を等しいとみなしません。**`NaN` を返す実装は、`NaN` と比べるテストでも捕まえられない** のがやっかいなところで、0 を返すと決めたことがそのままテストできる形になりました。

### 正例を決めると評価関数になる

```haskell
classificationMetric
  :: (Eq a) => (ConfusionMatrix -> Double) -> a -> [a] -> [a] -> Either String Double
classificationMetric score positive actual predicted =
  score <$> confusionMatrix actual predicted positive
```

`classificationMetric precision "1"` と 2 つだけ渡せば、「正解と予測を受け取って 1 つの数を返す関数」になります。**Haskell では部分適用がそのままこの形なので、「関数を返す関数」を作るための記法が要りません。** PHP 版は戻り値側を括弧で包む PHPDoc を書き、Elixir 版は `fn actual, predicted -> ... end` を返しました。ここは型注釈が 1 行あるだけです。

## 11.6 正解率と平均二乗誤差

正解率は **書きません**。第 1 章の `accuracy` をそのまま使います。

```haskell
import GettingStartedMl.Chapter01 (accuracy)
```

第 3 章で `(Eq a) =>` に広げてあるので、ラベルが `Text` でも通ります。**3 つの章で同じ関数を使い回せている** のは、そのとき「派閥に固定せず `Eq` で書く」と決めたおかげです。

平均二乗誤差は新しく書きます。

```haskell
meanSquaredError :: [Double] -> [Double] -> Either String Double
meanSquaredError actual predicted = do
  requireSameSize actual predicted
  mean (zipWith (\a p -> (p - a) * (p - a)) actual predicted)
```

### 学習用テスト: 外れた予測への敏感さ

```haskell
    it "一件だけ大きく外れると平均二乗誤差は跳ね上がる" $ do
      let small = meanSquaredError [0.0, 0.0, 0.0, 0.0] [1.0, 1.0, 1.0, 1.0]
          large = meanSquaredError [0.0, 0.0, 0.0, 0.0] [0.0, 0.0, 0.0, 4.0]
      small `shouldBe` Right 1.0
      large `shouldBe` Right 4.0
```

誤差の合計はどちらも 4 ですが、MSE は 1 と 4 で 4 倍違います。**2 乗するとは、大きく外れた 1 件を重く見ることだ** という性質を、数字で言葉にしたテストです。

平均は分けて書きました。

```haskell
mean :: [Double] -> Either String Double
mean [] = Left "平均を求める値が 1 つもありません"
mean values = Right (sum values / fromIntegral (length values))
```

空のリストの場合をパターンで書くので、**「1 件も無いときどうするか」を決めないと関数が完成しません**。第 3 章で `head` が `-Wx-partial` に止められたのと同じ形の強制です。

## 11.7 ROC 曲線と AUC

### 閾値を動かすとどうなるか

分類器が「正例らしさ」のスコアを返すとき、どのスコアから上を正例と呼ぶかは決めの問題です。閾値を下げるほど、正例を取りこぼさなくなる代わりに、負例を正例と呼ぶ誤りが増えます。閾値を上から下まで動かしたときの (偽陽性率, 真陽性率) の軌跡が **ROC 曲線** で、その下の面積が **AUC** です。

### 曲線の 1 点と、無限大の置き場

```haskell
data RocPoint = RocPoint
  { rocThreshold :: Maybe Double
  , rocFalsePositiveRate :: Double
  , rocTruePositiveRate :: Double
  }
  deriving (Eq, Show)
```

先頭の点は「どれも正例と予測しない」状態です。そこには閾値がありません。ほかの言語版は正の無限大（Elixir は `:infinity`、PHP は `INF`）を置きましたが、**Haskell では「値が無い」と書けます**。

```haskell
    it "先頭はどれも正例と予測しない点" $ do
      curve <- expectRight (rocCurve [0.9, 0.1] [True, False])
      take 1 curve `shouldBe` [RocPoint Nothing 0.0 0.0]
```

`Double` にも `1/0` で無限大はありますが、それは「大きすぎる数」であって「無い」ではありません。**`Maybe` を選ぶと、閾値を使う側が `Nothing` の場合を書かされます。** 第 2 章で欠損値を `Maybe` にしたのと同じ理由です。

### 同じスコアは 1 つの点にまとめる

```haskell
  grouped =
    sortOn (Down . fst)
      . M.toList
      . M.fromListWith (<>)
      $ [(score, [label]) | (score, label) <- zip scores labels]
```

`M.fromListWith (<>)` で同じスコアのラベルを 1 つに束ね、スコアの降順に並べます。`Data.Map` の鍵は `Double` でかまいません。

ここは **PHP 版がいちばん苦労した場所** です。PHP の配列のキーに浮動小数点は使えないので、Elixir 版の `group_by` がそのまま移せず、並べてから走る形に書き直すことになりました（PHP 版いわく「配列のキーに刺されたのは 3 度目」）。Haskell の `Map` は鍵の型を選ばないので、Elixir 版と同じ書き方がそのまま通ります。

累積は `scanl` です。

```haskell
    scanned = drop 1 (scanl step (0.0, 0, 0) grouped)
    step (_, tp, fp) (threshold, group) =
      ( threshold
      , tp + length (filter id group)
      , fp + length (filter not group)
      )
```

`scanl` は途中経過を全部残す畳み込みで、Elixir の `Enum.scan/3` にあたります。初期値の分は要らないので `drop 1` します。

### 台形則で面積を求める

```haskell
auc :: [RocPoint] -> Double
auc curve =
  sum
    [ (rocFalsePositiveRate right - rocFalsePositiveRate left)
        * (rocTruePositiveRate left + rocTruePositiveRate right)
        / 2.0
    | (left, right) <- zip curve (drop 1 curve)
    ]
```

`zip curve (drop 1 curve)` で隣り合う 2 点の組を作ります。Elixir の `Enum.chunk_every(2, 1, :discard)` にあたる書き方で、Haskell では専用の関数を覚えなくても `zip` で済みます。

突き合わせる相手がいないので、**両端をテストで押さえます**。

```haskell
    it "完全に分けられるスコアの AUC は一" $ do
      curve <- expectRight (rocCurve [0.9, 0.8, 0.3, 0.1] [True, True, False, False])
      auc curve `shouldBe` 1.0

    it "まったく分けられないスコアの AUC は零点五" $ do
      curve <- expectRight (rocCurve [0.5, 0.5, 0.5, 0.5] [True, False, True, False])
      auc curve `shouldBe` 0.5
```

AUC は定義上 0.5 が「でたらめ」、1.0 が「完全」です。**その 2 つをちょうど返すことが、実装が定義どおりであることのいちばん強い証拠** になります。ライブラリと比べられない章では、こういう「値が分かっている入力」を増やすことが突き合わせの代わりになります。

正例か負例が片方しか無ければ率の分母が 0 になるので、そこは `Left` で断ります。

```haskell
    it "正例か負例が片方しか無ければ曲線を描けない" $
      rocCurve [0.9, 0.1] [True, True]
        `shouldBe` Left "正例と負例が両方ないと ROC 曲線を描けません"
```

## 11.8 K 分割交差検証

### なぜ分け方を入れ替えるのか

1 回の分割では、たまたま難しい行がテストデータに集まることがあります。データを K 個のかたまりに分け、順番にテストデータの役を回して K 回測り、平均を取ると、その運の要素が薄まります。

### 分け方も `data` で表す

```haskell
data Fold = Fold
  { foldTrain :: [Int]
  , foldTest :: [Int]
  }
  deriving (Eq, Show)
```

持つのは **行の位置** です。特徴量そのものを持たないので、同じ分け方を特徴量にも正解にも使い回せます。

```haskell
kFold :: Int -> Int -> Int -> Either String [Fold]
kFold nSamples splits s = do
  checkSplits nSamples splits
  pure (folds (shuffle [0 .. nSamples - 1] s) splits)
```

並べ替えは第 2 章の `shuffle`（`java.util.Random` と同じ線形合同法）です。**ここをそろえたことが、6 つの指標がほかの言語版と一致する理由** になります。

余りの配り方も決めます。

```haskell
  sizes =
    [ total `div` splits + (if i < total `mod` splits then 1 else 0)
    | i <- [0 .. splits - 1]
    ]
```

891 件を 5 分割すると `[179, 178, 178, 178, 178]` です。**余りの 1 件を先頭の分割に配る** ので、どの行もちょうど 1 回テストされます。Elixir 版の Scholar と PHP 版の Rubix ML は、どちらも `floor(件数 / 分割数)` を全部の分割の大きさにするので、**891 番目の行がどの分割でもテストされません**。Haskell 版には比べる相手がいませんが、**その振る舞いの違い自体をテストに書けます**。

```haskell
    it "テストデータはすべての行をちょうど一度ずつ使う" $ do
      folds <- expectRight (kFold 10 5 0)
      sort (concatMap foldTest folds) `shouldBe` [0 .. 9]

    it "訓練データとテストデータは重ならない" $ do
      folds <- expectRight (kFold 10 5 0)
      let overlapping f = filter (`elem` foldTest f) (foldTrain f)
      concatMap overlapping folds `shouldBe` []
```

この 2 本が、交差検証の分け方に求められることのすべてです。**ライブラリと比べる代わりに、満たすべき性質を書きました。**

並べ替えない版も用意して、余りの配り方だけを確かめられるようにしています。

```haskell
    it "余りは先頭の分割から一件ずつ配る" $ do
      folds <- expectRight (kFoldSequential 7 3)
      map (length . foldTest) folds `shouldBe` [3, 2, 2]
```

## 11.9 評価関数も分類器も、ただの関数

### 分類器の型

```haskell
type Trainer x t p = [x] -> [t] -> Either String ([x] -> Either String [p])
```

「訓練データを受け取って、予測する関数を返す関数」です。学習した結果をどんな型で持つかは、呼ぶ側から見えません。第 10 章では同じことを型クラス `Classifier` と存在型 `SomeClassifier` でやりましたが、**ここでは `type` の別名 1 行で足ります**。

違いは「モデルそのものを持ち回るかどうか」です。第 10 章は特徴量の重要度を取り出すために、モデルを値として持つ必要がありました。この章は学習して予測するだけなので、関数に畳んでしまえます。

```haskell
treeTrainer :: [Text] -> Maybe Int -> Trainer C3.Features Text Text
treeTrainer columns maxDepth x t = do
  tree <- C3.fit x t columns maxDepth
  pure (C3.predict tree)

linearTrainer :: [Text] -> Trainer C3.Features Double Double
linearTrainer columns x t = do
  m <- C7.fit x t columns
  pure (C7.predict m)
```

第 3 章と第 7 章の関数を、引数の順を入れ替えて包むだけです。分類（予測は `Text`）と回帰（予測は `Double`）が、**同じ型の別名で表せている** ことに注目してください。`p` が型引数なので、予測の型を混同すればコンパイルが止まります。

### 交差検証の手順を 1 つの関数にする

```haskell
crossValidate
  :: Trainer x t p
  -> [x]
  -> [t]
  -> [Fold]
  -> ([t] -> [p] -> Either String Double)
  -> Either String [Double]
crossValidate trainer x t theFolds metric = traverse scoreFold theFolds
 where
  scoreFold fold = do
    xTrain <- pick x (foldTrain fold)
    tTrain <- pick t (foldTrain fold)
    xTest <- pick x (foldTest fold)
    tTest <- pick t (foldTest fold)
    predictor <- trainer xTrain tTrain
    predictor xTest >>= metric tTest
```

分割・学習・予測・採点が 6 行に並んでいます。`do` 記法のおかげで、**どこか 1 つでも失敗すれば全体が `Left` になる** ことが書かずに済んでいます。

### 遅延しない

Elixir 版の `cross_validate` は `Stream.map/2` を返しました。1 つ取り出せば 1 回だけ学習する、という遅延の効いた作りです。Haskell は遅延評価の言語なので同じことができそうに見えますが、**`traverse` は `Either` を 1 つにまとめるために全部の結果を要求します**。5 分割なら必ず 5 回学習します。

これは `Either` で失敗を表すことの引き換えです。「どれか 1 つでも失敗したら全体が失敗」と言うには、全部を見るしかありません。**遅延評価と失敗の表現は、そこでぶつかります。** 遅延させたいなら戻り値を `[Either String Double]` にして、判断を呼ぶ側に渡すことになりますが、この章ではそこまで要りませんでした。

### 三角測量: 評価関数を差し替える、回帰も同じ関数で

```haskell
    it "評価関数を差し替えても同じ分割で採点できる" $ do
      folds <- expectRight (kFoldSequential 6 3)
      hit <- expectRight (crossValidate (treeTrainer ["a"] (Just 1)) x t folds accuracy)
      f <- expectRight (crossValidate (treeTrainer ["a"] (Just 1)) x t folds (classificationMetric f1Score "ろ"))
      hit `shouldBe` [1.0, 1.0, 1.0]
      f `shouldBe` [1.0, 1.0, 1.0]
```

同じ `folds` を 2 回渡して、指標だけを差し替えています。11.8 節で「分割は行の位置だけを持つ」と決めたことが、そのままテストの書きやすさになりました。

このテストは **1 回 Red になりました**。最初はラベルを `["い", "い", "ろ", "ろ", "い", "ろ"]` と並べていて、F 値が `[0.0, 1.0, 1.0]` になったのです。先頭の分割のテストデータは 0 番と 1 番、つまり「い」が 2 件で、**正例が 1 件も入っていません**。tp も fp も fn も 0 なので、11.5 節で決めたとおり F 値は 0 になります。

実装は正しく、テストのデータの並べ方が悪いだけでした。ただしこれは、**交差検証で実際に起きうる事故** でもあります。正例が少ないデータを並べ替えて分けると、正例が 1 件も入らない分割ができて、その回の F 値が 0 になり、平均を押し下げます。scikit-learn の `StratifiedKFold` のように、分割ごとのラベルの比率をそろえる方法があるのはこのためです。この章では層化を実装していないので、**性質として知っておく** ところまでにしました。テストのデータは「どの分割にも 1 件ずつ入る」並びに直しています。

回帰でも同じ関数が使えることは、線形回帰と MSE で確かめます。

```haskell
    it "分割ごとに学習して採点する" $ do
      let x = [M.fromList [("a", v)] | v <- [1.0, 2.0, 3.0, 4.0, 5.0, 6.0 :: Double]]
          t = [2.0, 4.0, 6.0, 8.0, 10.0, 12.0 :: Double]
      folds <- expectRight (kFoldSequential 6 3)
      scores <- expectRight (crossValidate (linearTrainer ["a"]) x t folds meanSquaredError)
      all (< 1e-18) scores `shouldBe` True
```

`t = 2a` はどの分割で学習しても完全に当たるので、MSE はどの分割でも 0 のはずです。**答えの分かっているデータを使うと、交差検証そのものの正しさを確かめられます。**

## 11.10 突き合わせる相手がいない

この章は、シリーズで初めて **ライブラリとの比較の節が無い章** です。

| 言語版 | 評価指標 | 交差検証 | 見つかったライブラリの癖 |
| :--- | :--- | :--- | :--- |
| Elixir 版 | `Scholar.Metrics` | `Scholar.ModelSelection` | f32 に落ちる、`k_fold_split` が余りを捨てる |
| PHP 版 | `Rubix\ML\CrossValidation\Metrics` | `KFold`・`fold()` | 誤差の符号が反転、`FBeta` がマクロ平均、`fold()` が余りを捨てる、`stratifiedFold()` がラベルを整数に化けさせる |
| **Haskell 版** | **無し** | **無し** | — |

代わりにこの章がしたことは 3 つです。

1. **値の分かっている入力を使う** — AUC の 1.0 と 0.5、`t = 2a` の MSE 0、4 通りが 1 回ずつ出る混同行列
2. **満たすべき性質を書く** — テストデータが全行をちょうど 1 度ずつ使う、訓練とテストが重ならない、MSE が外れ値に敏感
3. **ほかの言語版と数値を照らす** — 11.11 節の 6 つの指標

3 番目がいちばん強い保証です。**分割の乱数・余りの配り方・前処理の手順をそろえてあるので、どこか 1 つでも実装が違えば数字がずれます。** Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版の 5 つと一致したことは、5 つの独立した実装と突き合わせたのと同じことです。

## 11.11 実データで評価する

### 簡略化した前処理

```haskell
prepareSurvived :: BL.ByteString -> Either String ([C3.Features], [Text])
prepareSurvived contents = do
  table <- C2.loadTable contents
  means <- C2.columnMeans (C2.tableRows table) ["Age"]
  ageMean <- lookupColumn means "Age"
  ...
        [ ("Pclass", pclass)
        , ("Age", maybe ageMean id age)
        , ("male", if sex == "male" then 1.0 else 0.0)
        ]
```

補完に使う平均値を **分割の前に全体から** 求めているので、厳密にはテストデータの情報が訓練に漏れています（リーク）。第 8 章のパイプラインは分割のあとで補完しました。ここはほかの言語版と条件をそろえるために、あえて簡略化した手順に合わせています。

`maybe ageMean id age` は、第 2 章の `optionalNumber` が欠損値に `Nothing` を返すことを利用しています。PHP の `??`、Elixir の `||`、Java の `Optional.orElse` にあたるものです。**`Maybe` なので、年齢 0.0 と欠損値を取り違えようがありません。** PHP 版が `?:` と `??` の違いに刺された場所が、Haskell では型の違いになっています。

### 交差検証の実験

```console
$ cabal repl
ghci> GettingStartedMl.Chapter11.run >>= either putStrLn putStr
Survived（決定木、5 分割交差検証の平均）
  正解率: 0.7811
  適合率: 0.7759
  再現率: 0.6306
  F値: 0.6833
cinema（線形回帰、5 分割交差検証の平均）
  RMSE: 405.77
  MAE: 321.53
分割ごとのテストデータの件数（5 分割）
  891 件を自作: 179, 178, 178, 178, 178
```

Survived の決定木は、正解率 0.7811 に対して再現率が 0.6306 です。**生存者のうち 4 割近くを見逃している** ことが、正解率だけでは見えませんでした。適合率 0.7759 は、「生存」と予測した人の 8 割近くが本当に生存していたことを表します。11.2 節で述べたとおり、正解率は「どちらを間違えたか」を隠します。

cinema の RMSE（405.77）は MAE（321.53）より大きく、11.6 節で見たとおり、大きく外れた予測があることを示します。第 7 章では 1 回の分割で RMSE が 376.14 でしたが、この章は外れ値を除かずに 5 回の平均を取っているので、単純には比べられません。

**6 つの評価指標の数値は、Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版の同じ節とすべて一致しました。** 分割の並べ替えを `java.util.Random` と同じ線形合同法（`GettingStartedMl.Random`）で行い、余りの配り方も訓練データの並びもそろえたためです。乱数が別実装の Kotlin 版とは一致しません（Kotlin 版の正解率は 0.7677）。

最後の 1 行は、ほかの言語版が「ライブラリの癖」を見せた場所です。891 件を 5 分割すると、自作は先頭の分割に 1 件多く配って 179 件にします。Scholar も Rubix ML も 178 件ずつにして 891 番目の行を捨てていました。**Haskell 版には捨てるライブラリがいないので、この行は自作の振る舞いを示すだけの行になりました。**

### 実データのテスト

```haskell
    it "分割ごとの平均二乗誤差は第 7 章の RMSE の二乗と一致する" $ do
      found <- Dataset.exists "cinema.csv"
      unless found $ pendingWith "学習データがありません"
      ...
      mse <- expectRight (crossValidate (linearTrainer cinemaColumns) x t folds meanSquaredError)
      rmse <- expectRight (crossValidate (linearTrainer cinemaColumns) x t folds rootMse)
      and (zipWith (\a b -> abs (a - b * b) < 1e-6) mse rmse) `shouldBe` True
```

同じ `folds` を 2 回渡して、指標だけを差し替えています。**第 7 章で書いた指標と、この章で書いた指標が、定義どおりの関係にあること** を確かめる形になりました。

学習データは配布物なのでリポジトリにありません。`Dataset.exists` が偽なら `pendingWith` で飛ばすので、データを持たない環境でもテストは緑になります（第 1 章から続けている形です）。

表示は、第 1 章から続けている形のテストで固定します。値は実装を実データで動かして得たものなので、Red を経ていません。

## 11.12 品質チェック

```console
$ npx gulp apps:check:haskell
```

fourmolu → hlint → `cabal test` → カバレッジの順に走ります。

| ファイル | 式のカバレッジ |
| :--- | :--- |
| `src/GettingStartedMl/Chapter11.hs` | 95%（613/643） |

残った 5% はほとんどが `run`（ファイルを読む `IO` の部分）です。読み込んだ中身から報告の文字列を作る `report` は純粋関数に切り出してあるので、そちらはテストで通っています。**第 1 章から続けている「`IO` は入口だけ」の形が、そのままカバレッジの形に出ています。**

**hlint の指摘は 1 つも出ませんでした。** 理由は書いたコードの形にあります。この章の関数はほとんどが「定義をそのまま式にしたもの」で、hlint が得意な「短く書ける書き方の見落とし」が入り込む隙がありませんでした。1 か所だけ危なかったのが欠損値の補完で、最初 `maybe ageMean id age` と書いていました。これは hlint が `fromMaybe ageMean age` に直せと言う定番の形です。**書いた時点で `Data.Maybe` を開いて `fromMaybe` にしてあった** ので指摘されずに済みました。

`-Wall -Werror` に止められたのは 1 つ、**テストの中の変数名が Hspec の関数と衝突したこと** でした。

```haskell
      sequential <- expectRight (kFoldSequential 10 5)
```

`Test.Hspec` は `sequential`（テストを順に走らせる指定）を輸出しています。`-Wname-shadowing` がこれを警告にし、`-Werror` がエラーに変えました。**第 7 章で `fit` が Hspec の `fit` とぶつかった**のと同じ形です（あのときは `import Test.Hspec hiding (fit)` で解決しました）。ここは自分の変数の名前を `inOrder` に変えるほうが素直でした。

面白いのは、**この 2 つのぶつかり方が正反対** だったことです。`fit` は「輸出されている名前を自分が定義した」ので隠す必要があり、`sequential` は「輸出されている名前を自分が局所変数にした」だけなので、名前を変えれば済みます。前者はコンパイルが通らず、後者は警告でした。**警告をエラーにしていなければ、そのまま気づかずに通っていた** ことになります。

それ以外に止められたものはありませんでした。この章で `data` は 3 つ増えましたが、場合分けはすべて 2 つの `Bool` の組（混同行列）かリストの空・非空（`mean`）で、どちらも**パターンを書き切らないとそもそも動かない**形です。

fourmolu は **型注釈の折り返し方** を 2 か所で直しました。`crossValidate ::` のように引数が多い関数を複数行に分けるとき、`->` を行末に置くか行頭に置くかの違いです。このプロジェクトの `fourmolu.yaml` は行頭（`:: [x]` の次の行が `-> [t]`）と決めているので、`--mode inplace` に任せました。**整形の決めごとを自分で覚えない** のが、道具を入れてある意味です。

## 11.13 可視化について

ROC 曲線を描く手順は [Python 版](../python/11-evaluation-metrics-and-cross-validation.md) と [Kotlin 版](../kotlin/11-evaluation-metrics-and-cross-validation.md) を参照してください。この章では曲線を **点の列として返す** ところまでを実装し、描画は扱いません。`rocCurve` はほかの版と同じ形のデータを返すので、同じ観点で読めます。

## 11.14 まとめ

この章では、評価指標と交差検証を Haskell の TDD で実装しました。

| 作ったもの | 突き合わせた相手 |
| :--- | :--- |
| 混同行列・適合率・再現率・F 値 | **ライブラリなし**（Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版の数値と一致） |
| 正解率 | 第 1 章の `accuracy` をそのまま再利用 |
| MSE・RMSE・MAE | 第 7 章の指標と定義どおりの関係にあることをテストで固定 |
| ROC 曲線・AUC | **ライブラリなし**（1.0 と 0.5 の両端をテストで押さえた） |
| K 分割交差検証 | **ライブラリなし**（満たすべき性質をテストで押さえた） |

Haskell 版ならではの学びは 6 つです。

1. **突き合わせる相手が 1 つも無い章だった** — Elixir 版は Scholar と、PHP 版は Rubix ML と比べて「ライブラリの癖」を見つけた。Haskell 版はその節そのものが無い。代わりに「値の分かっている入力」「満たすべき性質」「ほかの言語版の数値」の 3 つで支えた
2. **評価関数も分類器も、ただの関数で足りた** — 第 10 章で型クラスと存在型が要ったところが、`type` の別名 1 行で済んだ。**モデルを値として持ち回る必要が無ければ、抽象は要らない**。部分適用がそのまま「関数を返す関数」なので、PHP 版の PHPDoc も Elixir 版の無名関数も要らなかった
3. **閾値の無限大を `Maybe` で書けた** — ほかの言語版が `:infinity` や `INF` を置いた場所に「値が無い」と書ける。使う側は `Nothing` の場合を書かされる
4. **`Either` は遅延しない** — Elixir 版の `Stream.map` は「取り出した分だけ学習する」ものだった。`traverse` は全部を要求する。**「どれか 1 つでも失敗したら全体が失敗」と言うには全部を見るしかない**。型で失敗を表すことの引き換えである
5. **`Map` の鍵は型を選ばない** — ROC 曲線のスコアのグループ化で、PHP 版は「配列のキーに浮動小数点を使えない」ために書き直しになった。Haskell では Elixir 版と同じ書き方がそのまま通った
6. **0 で割っても落ちないので、自分で決める** — `0/0` は `NaN`。`NaN` を返す実装は `NaN` と比べるテストでも捕まらないので、「分母が 0 なら 0」と決めたことがそのままテストになった。第 10 章の `exp 1000` と同じ性質の裏返し

**TODO リスト（この章の完了時点）**:

- [x] 混同行列を数える
- [x] 適合率・再現率・F 値を求める
- [x] 正解率と平均二乗誤差を求める（正解率は第 1 章の再利用）
- [x] ROC 曲線と AUC を求める（**ライブラリが無いので自作が最終実装**）
- [x] K 分割交差検証の分け方を作る（**同上**）
- [x] 分割ごとに学習して採点する
- [x] 実データ（Survived・cinema）で交差検証する

次の章では、正則化を使って過学習を抑えます。リッジ回帰の正規方程式は hmatrix と突き合わせられますが、**ラッソ回帰には相手がいません**。この章の ROC 曲線と同じく、自作が最終実装になります。

Haskell 版のほかの章は [Haskell 版のトップ](index.md) から辿れます。
