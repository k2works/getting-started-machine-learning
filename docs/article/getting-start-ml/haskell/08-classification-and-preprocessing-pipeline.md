---
type: Article
title: "第 8 章: 実践的な分類と前処理パイプライン"
description: "タイタニック号の乗客データを題材に、グループ別中央値の補完・最頻値の補完・基準列を落とすダミー変数化・クラスの重みを付けた決定木を Haskell の TDD ですべて自作する。前処理の種類を直和型で数え上げ、書き漏らしをコンパイルで捕まえる設計を PHP の interface・Elixir の関数節・Clojure の defmulti と対比し、binary に Text のインスタンスが無いことから保存の形式を手で書く。"
tags: [article,getting-start-ml,haskell]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 8 章: 実践的な分類と前処理パイプライン

## 8.1 はじめに

ここまでの章では、データが比較的きれいでした。アヤメのデータは数値ばかりで欠損値も数件、映画のデータも 100 行の数値表です。この章では、**実務でよく出会う形のデータ** を扱います。タイタニック号の乗客データには、文字列の列があり、欠損値が 177 件あり、正解ラベルの偏りがあります。

この章でやることは 3 つです。

1. **前処理を部品にして、順に並べる（パイプライン）** — 欠損値の補完、カテゴリのダミー変数化。訓練データで学習した値を、テストデータや新しいデータにも同じように適用する
2. **クラスの重みを付ける** — 生存者（342 人）より死亡者（549 人）が多いので、素直に学習すると「死亡」に寄る。少数のクラスに重みを付けて、見落としを減らす
3. **学習済みのパイプラインを保存して復元する** — 学習のたびに計算し直すのではなく、ファイルから読み込んで使う

[第 7 章](07-linear-regression.md) は、Haskell 版では数少ない「ライブラリと突き合わせられる章」でした。この章はその正反対で、**突き合わせる相手が 1 つもありません**。Haskell には scikit-learn にあたるものが無く、前処理のライブラリもありません（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）。補完も、ダミー変数化も、決定木も、全部自作です。

[PHP 版](../php/08-classification-and-preprocessing-pipeline.md) は Rubix ML の `MissingDataImputer`・`OneHotEncoder` と突き合わせて「あるけれど粒度が足りない」ことを実測で示しました。[Elixir 版](../elixir/08-classification-and-preprocessing-pipeline.md) は決定木そのものが Scholar に無く、Haskell 版と同じ立ち位置です。**何も無いなら、自作の正しさを何で支えるのか。** それがこの章のもう 1 つの主題になります。

Notebook による探索と可視化の節は設けません。年齢の分布や決定木の図は [Python 版](../python/08-classification-and-preprocessing-pipeline.md) と [Kotlin 版](../kotlin/08-classification-and-preprocessing-pipeline.md) を参照してください。

## 8.2 題材とデータ

### Survived.csv

`Survived.csv` は 891 人分の乗客記録で、次の 11 列を持ちます。

| 列 | 内容 | 欠損値 |
|----|------|-------|
| PassengerId | 乗客の ID | なし |
| Survived | 生存（1）か死亡（0）か | なし |
| Pclass | 客室の等級（1・2・3） | なし |
| Sex | 性別（`male` / `female`） | なし |
| Age | 年齢 | **177 件** |
| SibSp | 同乗した兄弟姉妹・配偶者の数 | なし |
| Parch | 同乗した親・子の数 | なし |
| Ticket | チケット番号 | なし |
| Fare | 運賃 | なし |
| Cabin | 客室番号 | **687 件** |
| Embarked | 乗船した港（`C` / `Q` / `S`） | **2 件** |

[第 2 章](02-data-preprocessing-and-triangulation.md) の `countMissing` をそのまま使って数えたところ、`Age=177`・`Cabin=687`・`Embarked=2` でした。ほかの言語版と同じです。

`Survived.csv` は BOM 付きです。第 1 章で書いた `Csv.parseTable` が先頭の BOM を落とすので、先頭の列名は `PassengerId` として読めます。cassava は BOM を取り除かないので、この処理は自分で書いてあります。

### 使う特徴量と、使わない列

| 列 | 使うか | 理由 |
|----|-------|------|
| PassengerId | 使わない | 乗客を区別する番号で、生死と関係がない |
| Ticket | 使わない | 文字列の識別子で、カテゴリとして扱うには種類が多すぎる |
| Cabin | 使わない | 687 件が欠損。補完しても情報がほとんど無い |
| Pclass・Sex・Age・SibSp・Parch・Fare・Embarked | 使う | 7 列を特徴量にする |

```haskell
-- | モデルに渡す特徴量の列。PassengerId・Ticket・Cabin は使わない。
featureColumns :: [Text]
featureColumns = ["Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked"]
```

`Cabin` を「欠損が多いから捨てる」のは、実は乱暴な判断です。「客室番号が記録されていること自体が、等級の高さと相関しているかもしれない」という見方もできます。ここでは、ほかの言語版と同じ特徴量にそろえることを優先しました。

### 年齢はグループごとの中央値で補完する

`Age` の欠損 177 件をどう埋めるかが、この章のいちばん大事な判断です。全体の中央値（28 歳）で埋めるのは簡単ですが、**1 等客室の女性と 3 等客室の男性では年齢の分布が違います**。実際に訓練データから求めた中央値はこうなりました。

| グループ | 中央値 |
|---------|-------|
| 1 等・女性 | 35 |
| 1 等・男性 | 45 |
| 2 等・女性 | 28 |
| 2 等・男性 | 30 |
| 3 等・女性 | 22 |
| 3 等・男性 | 25 |
| （全体） | 28 |

1 等の男性と 3 等の女性で 23 歳も違います。**等級と性別のグループごとの中央値** で補完します。

## 8.3 TODO リストの作成

```text
TODO リスト（第 8 章）

- [ ] 中央値を計算する
- [ ] 前処理を「学習してから適用する」部品として表す
- [ ] 年齢をグループごとの中央値で補完する
- [ ] 乗船した港を最頻値で補完する
- [ ] カテゴリ値をダミー変数にする（基準の列を落とす）
- [ ] 重み付きのジニ不純度と、クラスの重みを計算する
- [ ] クラスの重みを付けた決定木を学習する
- [ ] 前処理と決定木をパイプラインにつなぐ
- [ ] 学習済みのパイプラインを保存して読み込む
- [ ] 実データでクラスの重みの効果を確かめる
```

PHP 版にあった「Rubix ML の前処理と突き合わせる」がありません。突き合わせる相手が無いからです。その代わりに、**自作の正しさを支える仕組みを自分で用意する**ことになります（8.11 節）。

## 8.4 前処理を直和型で数え上げる

前処理には共通の形があります。**訓練データから値を学び（fit）、その値でデータを変換する（apply）** の 2 段です。

Haskell では、この「種類」を直和型で書きます。

```haskell
data Step
  = -- | ある列を、別の列の組み合わせごとの中央値で補完する
    GroupMedianImputer Text [Text]
  | -- | ある列を最頻値で補完する
    MostFrequentImputer Text
  | -- | カテゴリの列をダミー変数にする
    DummyEncoder [Text]
  deriving (Eq, Show)
```

学習したあとの前処理は、別の型にします。**「まだ学習していない前処理」と「学習済みの前処理」を型で区別する** ので、学習していないものを適用しようとするとコンパイルが止まります。PHP 版はこれを「`fit()` が `self` を返す」ことで表し、実行時に `null` を見て失敗させていました。Haskell では実行時の判定が要りません。

```haskell
data FittedStep
  = FittedGroupMedian Text [Text] (M.Map [Text] Double) Double
  | FittedMostFrequent Text Text
  | FittedDummy [(Text, [Text])]
  deriving (Eq, Show)
```

振り分けは関数の節で書きます。

```haskell
fitStep :: Step -> Frame -> Either String FittedStep
applyStep :: FittedStep -> Frame -> Either String Frame
```

ここが、この章でいちばん言語の違いが出るところです。同じ「前処理の種類を増やしていく」設計を、5 つの言語版がそれぞれ別の道具で書いています。

| 言語 | 種類の表し方 | 全体はどこを見れば分かるか | 節の書き漏らしは |
|------|-----------|----------------------|----------------|
| Haskell | 直和型と関数の節 | `Step` の定義 1 か所 | **コンパイルが止まる**（`-Wall -Werror`） |
| PHP | インターフェースと実装クラス | `ls src/Preprocessing/` | 抽象メソッドの未実装でコンパイル相当の検査が止まる |
| Elixir | タグ付きのマップと関数節のパターンマッチ | 1 つのモジュール | 実行時に `FunctionClauseError` |
| Clojure | `defmulti` / `defmethod` | 定義が散らばる（開いた設計） | 実行時にエラー |

Haskell だけが「**種類を増やしたときに、対応を書き忘れた場所を全部コンパイラが挙げてくれる**」側にいます。`-Wall -Werror` を入れてあるので、網羅していないパターンマッチは警告ではなくエラーです（[第 1 章](01-machine-learning-and-first-test.md)）。代わりに、**種類を増やすには `Step` の定義に手を入れる必要があります**（閉じた設計）。Clojure の `defmulti` はその逆で、あとからいくらでも足せる代わりに、全体像がコードのどこにあるか分かりません。どちらが良いという話ではなく、どちらの間違いを防ぎたいかの選択です。

### 表は文字列のまま持つ

前処理は「文字列の表を文字列の表に変換する」ものとして書きます。数値にするのは、モデルに渡す直前の 1 回だけです。

```haskell
data Frame = Frame
  { frameColumns :: [Text]
  , frameRows :: [M.Map Text Text]
  }
  deriving (Eq, Show)
```

列名の並びを `M.Map` と別に持っているのは、[第 2 章](02-data-preprocessing-and-triangulation.md) と同じ理由です。**`M.Map` はキーの順で並ぶ**ので、「CSV に現れた順」や「ダミー変数化のあとの順」を保ちたければ、順そのものをリストで持ち回るしかありません。Elixir 版・Clojure 版もまったく同じ扱いをしています。

## 8.5 中央値と、グループごとの補完

### Red: 中央値から

```haskell
it "件数が奇数なら真ん中の値" $
  median [3.0, 1.0, 2.0] `shouldBe` Right 2.0

it "件数が偶数なら真ん中の二つの平均" $
  median [1.0, 2.0, 3.0, 4.0] `shouldBe` Right 2.5

it "値が無ければ中央値を求められない" $
  median [] `shouldBe` Left "値がありません"
```

3 つめが大事です。**「値が無い」は起こりうる** ので、`Double` ではなく `Either String Double` を返します。ほかの言語版が例外を投げたり `nil` を返したりするところで、Haskell では**呼ぶ側が場合分けを書くことになります**。

### Green

```haskell
median :: [Double] -> Either String Double
median [] = Left "値がありません"
median values
  | odd count = Right (sorted !! middle)
  | otherwise = Right ((sorted !! (middle - 1) + sorted !! middle) / 2.0)
 where
  sorted = sort values
  count = length sorted
  middle = count `div` 2
```

空リストの節を先に書くので、そのあとの `sorted !! middle` が範囲の外に出ることはありません。**パターンマッチで不可能な場合を先に落としてから、残りを書く** のが Haskell の型の使い方です。

### グループごとの補完

グループは「`by` の列の値を並べたリスト」で表します。

```haskell
-- | 行のグループ。@by@ の列の値を並べたリストで、'M.Map' のキーに使う。
groupOf :: [Text] -> M.Map Text Text -> [Text]
groupOf by row = map (cell row) by
```

PHP 版は連想配列のキーが文字列か整数しか取れないので、**値をタブでつないで 1 本の文字列にする** 必要がありました。区切り文字が元のデータに現れないことを根拠にする、という気を使う判断です。Haskell では `[Text]` がそのまま `Ord` なので、**リストをキーにできます**。区切り文字を選ぶ必要がありません。Clojure 版がベクタをキーにしたのと同じ形です。

学習は「欠損でない行から、グループごとの中央値と、全体の中央値を求める」だけです。

```haskell
fitStep (GroupMedianImputer column by) frame = do
  let known = filter (\row -> not (isMissing row column)) (frameRows frame)
  values <- traverse (`numberOf` column) known
  overall <- median values
  medians <-
    traverse median $
      M.fromListWith (<>) (zipWith (\row value -> (groupOf by row, [value])) known values)
  pure (FittedGroupMedian column by medians overall)
```

`traverse median` が `M.Map [Text] [Double]` を `Either String (M.Map [Text] Double)` にします。**`Map` の中身を全部変換して、1 つでも失敗したら全体を失敗にする** という処理が 1 語で書けるのは、`Traversable` のおかげです。ほかの言語版では、ここに畳み込みと失敗の伝播を手で書いていました。

### 知らないグループは全体の中央値で

```haskell
it "知らないグループは全体の中央値で補完する" $ do
  let train = textFrame ["Pclass", "Age"] [["1", "40"], ["1", "50"], ["3", "10"]]
      test = textFrame ["Pclass", "Age"] [["2", ""]]
  fitted <- expectRight (fitStep (groupMedianImputer "Age" ["Pclass"]) train)
  applied <- expectRight (applyStep fitted test)
  map (M.! "Age") (frameRows applied) `shouldBe` ["40.0"]
```

訓練データに 2 等が無くても、テストデータに 2 等が出てきたら落ちてはいけません。全体の中央値（10・40・50 の中央値で 40）で埋めます。

```haskell
fill row
  | isMissing row column =
      M.insert column (showDouble (M.findWithDefault overall (groupOf by row) medians)) row
  | otherwise = row
```

`M.findWithDefault` が、そのまま「知らないグループなら全体の中央値」という意味になります。

## 8.6 乗船した港を最頻値で補完する

港（`Embarked`）は文字列なので、中央値が使えません。最も多く現れた値で埋めます。

問題は **同数のとき** です。`C` と `S` が同じ回数なら、どちらを選ぶのか。ここを決めておかないと、ほかの言語版と結果が合いません。

```haskell
it "同数なら値の順で前のものを選ぶ" $ do
  let frame = textFrame ["Embarked"] [["S"], ["C"], [""]]
  fitted <- expectRight (fitStep (mostFrequentImputer "Embarked") frame)
  applied <- expectRight (applyStep fitted frame)
  map (M.! "Embarked") (frameRows applied) `shouldBe` ["S", "C", "C"]
```

実装は「値の順に並べてから、厳密な不等号で畳む」です。

```haskell
  -- 値の順に並べ、厳密な不等号で畳むので、同数なら値の順で前のものを選ぶ。
  counted = sortOn fst (M.toList (M.fromListWith (+) [(value, 1 :: Int) | value <- values]))
  larger best candidate = if snd candidate > snd best then candidate else best
```

`>` であって `>=` でないことに、数値の一致がかかっています。`>=` にすると「同数なら後のものを選ぶ」になり、実データで結果が変わります。

PHP 版は、ここで `ksort` に `SORT_STRING` を渡す必要がありました。既定の `SORT_REGULAR` が「数字に見える文字列を数値として比べる」ためです。Haskell の `Text` の `Ord` は辞書順しかないので、この落とし穴はありません。**型が 1 つしかないと、比べ方も 1 つに決まります。**

## 8.7 カテゴリ値をダミー変数にする

`Sex` と `Embarked` は文字列なので、そのままでは決定木の「しきい値以下か」という判定に載りません。カテゴリごとに 0 と 1 の列に広げます。

### 基準の列を落とす

```haskell
it "基準の列を落とす" $ do
  let frame = textFrame ["Sex"] [["male"], ["female"]]
  fitted <- expectRight (fitStep (dummyEncoder ["Sex"]) frame)
  applied <- expectRight (applyStep fitted frame)
  frameColumns applied `shouldBe` ["Sex_male"]
  map (M.! "Sex_male") (frameRows applied) `shouldBe` ["1", "0"]
```

`female` の列を作りません。`Sex_male` が 0 なら `female` だと分かるので、作ると情報が重複します（多重共線性）。カテゴリを並べ替えて先頭を落とす、という決め方にそろえます。

```haskell
fitStep (DummyEncoder columns) frame =
  Right (FittedDummy [(column, drop 1 (categoriesOf column)) | column <- columns])
 where
  categoriesOf column =
    sort (nub [cell row column | row <- frameRows frame, not (isMissing row column)])
```

### 知らないカテゴリは全部 0

訓練データに無かった値がテストデータに出てきたら、どのダミー変数も 0 になります。落ちません。

```haskell
it "学習していないカテゴリはすべて零になる" $ do
  let train = textFrame ["Sex"] [["male"], ["female"]]
      test = textFrame ["Sex"] [["unknown"]]
  fitted <- expectRight (fitStep (dummyEncoder ["Sex"]) train)
  applied <- expectRight (applyStep fitted test)
  map (M.! "Sex_male") (frameRows applied) `shouldBe` ["0"]
```

### 列の順

ダミー変数化は列の並びを変えます。元の列を外して、作ったダミー変数を末尾に足します。

```haskell
  widen columns (column, categories) =
    filter (/= column) columns <> map (dummyColumn column) categories
```

実データではこうなりました。ほかの言語版と同じ並びです。

```text
Pclass, Age, SibSp, Parch, Fare, Sex_male, Embarked_Q, Embarked_S
```

## 8.8 クラスの重みを付けた決定木を作る

### なぜ自作するのか

Haskell には決定木のライブラリがありません。`hlearn` は保守が止まっていて、現在の GHC ではビルドできません（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）。[第 3 章](03-decision-tree-and-obvious-implementation.md) で書いた決定木を、「1 件ごとの重み」を通す形に書き直したものが、この章の最終実装になります。

もっとも、**クラスの重みに関しては、ライブラリがある言語版でも自作でした**。PHP 版は Rubix ML の分類器にも前処理にも重みの口が無く、Elixir 版は決定木そのものが Scholar に無く、どちらも自作しています。

### 木を型で表す

```haskell
-- | 枝の分かれ方。特徴量の値がしきい値以下なら左へ進む。
data Rule = Rule
  { ruleFeature :: Text
  , ruleThreshold :: Double
  }
  deriving (Eq, Show)

-- | 決定木。葉はラベル、枝は分かれ方と左右の部分木を持つ。
data Tree
  = Leaf Int
  | Branch Rule Tree Tree
  deriving (Eq, Show)
```

木は再帰的な直和型そのものです。`predictTreeOne` は 2 つの節で書けて、それで全部の場合を尽くしています。

```haskell
predictTreeOne :: Tree -> M.Map Text Double -> Either String Int
predictTreeOne (Leaf label) _ = Right label
predictTreeOne (Branch rule left right) features = do
  toLeft <- goesLeft rule features
  predictTreeOne (if toLeft then left else right) features
```

`deriving (Eq, Show)` が付いているので、**木そのものをテストで比べられます**。

```haskell
it "分かれ目を見つけて左右の部分木を作る" $ do
  let x = [M.fromList [("a", v)] | v <- [1.0, 2.0]]
  fitTree x [0, 1] ["a"] Nothing NoWeight
    `shouldBe` Right (Branch (Rule "a" 1.5) (Leaf 0) (Leaf 1))
```

予測の結果ではなく、**学習した木の形そのもの**を固定できます。PHP 版・Elixir 版では、木がマップやオブジェクトだったので、ここまで直接には書けませんでした。

### 重み付きのジニ不純度

ラベルの件数の代わりに、重みの合計で割合を求めます。

```haskell
it "半々ならジニ不純度は零点五" $
  weightedGini [0, 1] [1.0, 1.0] `shouldBe` Right 0.5

it "重みを変えると不純度が変わる" $
  weightedGini [0, 1] [3.0, 1.0] `shouldBe` Right 0.375
```

重みをすべて 1 にすると、[第 3 章](03-decision-tree-and-obvious-implementation.md) のジニ不純度と同じ値になります。**重みなしの木は、第 3 章の木と同じ木になる** — これが自作の正しさを支える 1 本目の柱です。

### balanced の重み

```haskell
it "balanced の重みは件数に反比例する" $
  balancedWeights [1, 0, 0, 0] `shouldBe` [2.0, 2.0 / 3.0, 2.0 / 3.0, 2.0 / 3.0]
```

「件数 ÷ (クラスの数 × そのクラスの件数)」です。1 件しかないクラスの 1 件は 2.0、3 件あるクラスの 1 件は 0.667。クラスごとの重みの合計は、どちらも 2.0 でそろいます。

### 同点のときの決め方

数値をほかの言語版と一致させるために、**3 か所の「同点の決め方」** をそろえる必要があります。どれか 1 つでもずれると、深い木で予測が食い違います。

| 場面 | 決め方 | 実装 |
|------|-------|------|
| 最頻値が同数 | 値の順で前のもの | 値の順に並べて `>` で畳む |
| 葉のラベルの重みが同じ | 先に現れたラベル | `nub` の順に並べて `>` で畳む |
| 分割の不純度が同じ | 先に現れた候補 | 列の順・しきい値の昇順で並べて `<` で畳む |

2 番目は、`balanced` の効果がいちばん素直に見える場所です。

```haskell
it "balanced にすると少数派が葉に選ばれる" $ do
  let x = replicate 3 (M.fromList [("a", 1.0)])
  -- 重みを付けなければ多数派の 0 が葉になる。
  fitTree x [1, 0, 0] ["a"] (Just 0) NoWeight `shouldBe` Right (Leaf 0)
  -- balanced はクラスごとの重みの合計をそろえるので、同点になり、
  -- 先に現れた少数派の 1 が葉になる。
  fitTree x [1, 0, 0] ["a"] (Just 0) Balanced `shouldBe` Right (Leaf 1)
```

`balanced` は**クラスごとの重みの合計をぴったりそろえる**ので、2 クラスなら必ず同点になります。そこで何が選ばれるかは、同点の決め方が決めます。「同点の決め方はどうでもいい細部」ではなく、**重みの効果そのもの** がそこに乗っています。

### ラベルの順を保つ

`M.Map` はキーの順で並ぶので、「先に現れたラベル」を知るには、順を別に持つ必要があります。

```haskell
-- | ラベルごとの重みの合計を、ラベルが先に現れた順のリストで返す。
weightSums :: [Int] -> [Double] -> [(Int, Double)]
weightSums labels weights =
  [(label, M.findWithDefault 0.0 label sums) | label <- nub labels]
 where
  sums = M.fromListWith (+) (zip labels weights)
```

合計は `Map` で取り、並べる順は `nub labels` から取ります。**「速く引ける入れ物」と「順を覚えている入れ物」を分ける** のは、この章で 3 回出てくる形です（列の順、カテゴリの順、ラベルの順）。

## 8.9 前処理とモデルをパイプラインにつなぐ

### 前処理の順番と fit の順番

パイプラインは、前処理の並びと決定木の設定を持ちます。

```haskell
buildPipeline :: Maybe Int -> ClassWeight -> Pipeline
buildPipeline =
  Pipeline
    [ groupMedianImputer "Age" ["Pclass", "Sex"]
    , mostFrequentImputer "Embarked"
    , dummyEncoder ["Sex", "Embarked"]
    ]
```

引数の `maxDepth` と `classWeight` が書かれていないのは、hlint に「そのまま `Pipeline` に渡しているだけなので省ける」と言われて省いたからです（eta 簡約）。型の宣言には残っているので、何を取るかは読めます。

深さの上限を `Maybe Int` で表しています。**「上限なし」を `-1` や `0` のような特別な数で表さない**のは、型でその意味を書けるからです。`Nothing` が上限なし、`Just 0` がそれ以上分けない。

学習は「前の前処理で変換したデータで、次の前処理を学習する」です。

```haskell
fitPipeline :: Pipeline -> Frame -> [Int] -> Either String FittedPipeline
fitPipeline pipeline x t = do
  (fitted, prepared) <- foldl' step (Right ([], x)) (pipelineSteps pipeline)
  features <- toFeatures prepared
  tree <- fitTree features t (frameColumns prepared) ...
```

順番を取り違えると、ダミー変数化のあとの列名で年齢を補完しようとして失敗します。**「学習用のデータ」が前処理ごとに変わっていく** ことは、`foldl'` の途中の値としてコードに現れます。

### 前処理が済んだ表を特徴量にする

ここが、文字列の世界から数値の世界に移る唯一の場所です。

```haskell
it "欠損値が残っていれば特徴量にできない" $
  toFeatures (textFrame ["Age"] [[""]])
    `shouldBe` Left "欠損値が残っています: Age"

it "数値として読めない値は特徴量にできない" $
  toFeatures (textFrame ["Age"] [["若い"]])
    `shouldBe` Left "Age を数値として読めません: 若い"
```

補完を書き忘れれば、モデルに渡る前に `Left` で止まります。**前処理の書き忘れが、学習の途中のおかしな数値ではなく、はっきりしたエラーになる** ようにしてあります。

## 8.10 モデルを保存して読み込む

### binary には Text のインスタンスが無い

学習済みのパイプラインは `Data.Binary` で保存します。ここで最初の落とし穴に出会いました。

**`binary` は `text` に依存していないので、`Text` の `Binary` インスタンスがありません。** `ByteString` や `Map` のインスタンスはあるのに、`Text` だけありません。`deriving (Generic)` して `instance Binary FittedPipeline` と書くだけでは、`Text` のところでコンパイルが止まります。

選択肢は 2 つでした。

1. **孤児インスタンス（orphan instance）を書く** — `instance Binary Text` をこちらのモジュールに足す。短いが、ほかのライブラリが同じことをしていたら衝突する
2. **保存の形式を手で書く** — `Text` を `String` に直して読み書きする

2 を選びました。

```haskell
{- | 'Text' を保存する。

@binary@ は @text@ に依存していないので、'Text' の 'Binary' の実装が無い。
孤児インスタンスを足す代わりに、文字列に直して読み書きする。
-}
putText :: Text -> Put
putText = put . T.unpack

-- | 'Text' を読み込む。
getText :: Get Text
getText = T.pack <$> get
```

そして木と前処理の形式を手で書きます。

```haskell
instance Binary Tree where
  put (Leaf label) = putWord8 0 >> put label
  put (Branch rule left right) = putWord8 1 >> put rule >> put left >> put right
  get = do
    tag <- getWord8
    case tag of
      0 -> Leaf <$> get
      1 -> Branch <$> get <*> get <*> get
      _ -> fail "知らない木の形です"
```

手で書くのは面倒ですが、**保存の形式が 1 バイト単位でコードに書いてある** という良さがあります。`_ -> fail` の節があるので、壊れたファイルを読んでも `Leaf` や `Branch` が勝手に作られることはありません。

ここは、ほかの言語版が用心して制限していたことと同じです。PHP 版は `unserialize()` に `allowed_classes` を渡し、Elixir 版は `:erlang.binary_to_term` に `:safe` を渡し、Clojure 版は `clojure.core/read-string` ではなく `clojure.edn/read-string` を使いました。どれも「ファイルの中身で任意のものを作らせない」ための指定です。**Haskell では、`Binary` の `get` が読み取れる形を自分で書いているので、そもそも任意のものが作られません。** 用心が設計に組み込まれている形です。

### 形式の版と、壊れたファイル

保存するときに版を書き、読むときに確かめます。

```haskell
saveModel :: FittedPipeline -> FilePath -> IO ()
saveModel fitted file = do
  createDirectoryIfMissing True (takeDirectory file)
  BL.writeFile file (encode (formatVersion, fitted))
```

読み込みは `decode` ではなく **`decodeOrFail`** を使います。`decode` は失敗すると例外を投げますが、`decodeOrFail` は `Left` を返します。この章の設計（失敗は型で表す）にそろえるためです。

```haskell
loadModel :: FilePath -> IO (Either String FittedPipeline)
loadModel file = do
  contents <- BL.readFile file
  pure $ case decodeOrFail contents of
    Left _ -> Left ("モデルとして読めません: " <> file)
    Right (rest, _, (version, fitted))
      | not (BL.null rest) -> Left ("モデルとして読めません: " <> file)
      | version /= formatVersion ->
          Left (printf "対応していない形式のモデルです: %d" (version :: Int))
      | otherwise -> Right fitted
```

`rest` を確かめているのは、**`decodeOrFail` が「途中まで読めたら成功」を返す** からです。余りが残っていたら、それはこの形式のファイルではありません。

### 往復することをテストする

```haskell
it "学習済みのパイプラインは保存して復元できる" $ do
  let frame = textFrame ["Pclass", "Age"] [["1", "30"], ["3", "20"]]
  fitted <- expectRight (fitPipeline (Pipeline [] (Just 2) NoWeight) frame [1, 0])
  let file = "model" </> "spec.model"
  createDirectoryIfMissing True "model"
  saveModel fitted file
  loaded <- loadModel file
  removeFile file
  loaded `shouldBe` Right fitted
```

`deriving Eq` があるので、**復元したものが元と等しいかを 1 行で確かめられます**。手で書いた形式が壊れていれば、ここで落ちます。

## 8.11 実データでクラスの重みの効果を確かめる

### 実行する

```bash
cabal repl lib:getting-started-ml
```

```haskell
ghci> GettingStartedMl.Chapter08.run >>= either putStrLn putStr
```

```text
データ件数: 891（生存 342, 死亡 549）
訓練データ: 712 件, テストデータ: 179 件
classWeight=none: 訓練 0.854, テスト 0.799, 生存者 79 人中 59 人を発見
classWeight=balanced: 訓練 0.848, テスト 0.804, 生存者 79 人中 65 人を発見
保存したモデル: 読み込めました
架空の乗客の予測: 1, 0
```

深さ 5 の木で、`balanced` にすると正解率はわずかに上がり（0.799 → 0.804）、見つけた生存者は 59 人から 65 人に増えました。年齢の分からない架空の乗客 2 人（1 等客室の女性と 3 等客室の男性）は、生存・死亡と予測されました。

この出力を、テストで丸ごと固定します。

```haskell
it "実データの結果を表示する" $ do
  found <- Dataset.exists "Survived.csv"
  unless found $ pendingWith "学習データがありません"
  ...
  actual
    `shouldBe` Right
      ( "データ件数: 891（生存 342, 死亡 549）\n"
          <> "訓練データ: 712 件, テストデータ: 179 件\n"
          <> "classWeight=none: 訓練 0.854, テスト 0.799, 生存者 79 人中 59 人を発見\n"
          <> "classWeight=balanced: 訓練 0.848, テスト 0.804, 生存者 79 人中 65 人を発見\n"
          <> "保存したモデル: 読み込めました\n"
          <> "架空の乗客の予測: 1, 0\n"
      )
```

学習データはリポジトリに無いので、`Dataset.exists` が偽なら `pendingWith` でこのテストを外します。

### ほかの言語版と数値が一致するか

分割は [第 2 章](02-data-preprocessing-and-triangulation.md) で `java.util.Random` と同じ線形合同法と Fisher-Yates にそろえてあるので、Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版とまったく同じ行が訓練データとテストデータに入ります。実測した結果は次のとおりで、**すべて一致しました**。

| 指標 | Haskell 版 | Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版 |
|------|-----------|-------------------------------------------|
| データ件数（生存・死亡） | 891（342・549） | 891（342・549） |
| 訓練・テストの件数 | 712・179 | 712・179 |
| 深さ 5・重み付けなしの正解率（訓練・テスト） | 0.854・0.799 | 0.854・0.799 |
| 深さ 5・balanced の正解率（訓練・テスト） | 0.848・0.804 | 0.848・0.804 |
| 深さ 5 で見つけた生存者（79 人中） | 59 人 → 65 人 | 59 人 → 65 人 |
| 深さ 2 で見つけた生存者（79 人中） | 41 人 → 68 人 | 41 人 → 68 人 |
| 架空の乗客 2 人の予測 | `1, 0` | 同じ |

学習した前処理の中身も一致しています。全体の中央値 28、グループ別の中央値（1 等・男性が 45、3 等・女性が 22）、最頻値 `S`、ダミー変数化のあとの列 `Pclass, Age, SibSp, Parch, Fare, Sex_male, Embarked_Q, Embarked_S`。

Kotlin 版は `kotlin.random.Random` を使うので分割が違い、深さ 5 では `balanced` が見落としを減らしませんでした。同じアルゴリズムでも、**1 回の分割の結果から「この設定のほうが良い」と一般化してはいけない**、ということです。

### 効果は深さによって変わる

深さを変えて測りました。

| 深さ | 重み | 訓練の正解率 | テストの正解率 | 見つけた生存者（79 人中） |
|------|-----|------------|--------------|----------------------|
| 2 | none | 0.803 | 0.765 | 41 |
| 2 | balanced | 0.754 | 0.737 | **68** |
| 3 | none | 0.824 | 0.810 | 59 |
| 3 | balanced | 0.796 | 0.771 | 69 |
| 5 | none | 0.854 | 0.799 | 59 |
| 5 | balanced | 0.848 | 0.804 | 65 |
| 8 | none | 0.903 | 0.816 | 60 |
| 8 | balanced | 0.903 | 0.827 | 58 |

8 行すべてが PHP 版・Elixir 版と一致しました。

深さ 2 で効果がいちばんはっきり出ます。見つけた生存者が 41 人から 68 人へ、27 人増えました。その代わりテストの正解率は 0.765 から 0.737 へ下がっています。**重みは「正解率」と「見落としの少なさ」を交換している** のが読み取れます。

浅い木は葉が大きく、多数派に引きずられやすいので、重みを変えた効果が出ます。深い木では葉が小さくなり、重みを付けなくても少数のクラスだけの葉ができるので、差が小さくなります。深さ 8 では `balanced` のほうが見つけた生存者が少なくなりました（60 → 58）。訓練の正解率が 0.903 まで上がっているのは過学習の兆候で、この深さで重みを議論しても意味がありません。

この 2 つの数（41 と 68）をテストに固定しておきます。

```haskell
it "深さ二では balanced にすると見つかる生存者が増える" $ do
  ...
  plain <- expectRight (evaluateWith split (Just 2) NoWeight)
  balanced <- expectRight (evaluateWith split (Just 2) Balanced)
  (foundSurvivors plain, foundSurvivors balanced) `shouldBe` (41, 68)
```

### 突き合わせる相手が 1 つも無いとき、何が正しさを支えるのか

この章には、外部の実装と照らせる場所がありません。前処理も決定木も自作で、比べる相手がいません。支えは 3 本です。

1. **重みを付けなければ、第 3 章の決定木と同じ木になる** — 重みをすべて 1 にすればジニ不純度も多数決も件数と同じになります。第 3 章の木は、ほかの言語版（Java 版の Tribuo・PHP 版の Rubix ML）と突き合わせ済みなので、ここを通して間接的につながります
2. **実データの数値が 5 つの言語版と一致する** — 正解率も、見つけた生存者の数も、深さごとの 8 行も、学習した中央値も一致しました。別々に書かれた 6 つの実装が同じ数を出すことは、それ自体が検証です
3. **木の形そのものをテストで固定できる** — `deriving (Eq, Show)` のおかげで、小さなデータに対して学習した木を `Branch (Rule "a" 1.5) (Leaf 0) (Leaf 1)` と直接書けます。予測の結果だけでなく、**中間の構造** を押さえられます

1 と 2 は、言語版を横断して同じ数値を出すことに投資してきた効果です。**ライブラリに突き合わせる相手が 1 つも無い言語版で、いちばん効いています。** 3 は Haskell 固有の支えで、代数的データ型が構造をそのまま値として書けることから来ています。

## 8.12 品質チェック

```console
$ fourmolu --mode check src test
$ hlint src test
No hints
$ cabal test --enable-coverage
$ ./tools/coverage-threshold.sh 80
```

`npx gulp apps:check:haskell` で、この 4 つをまとめて実行します。

### 検査に止められたところ

1. **`foldl'` の import が要らない** — GHC 9.10 では `Data.List.foldl'` が `Prelude` に入っています。`import Data.List (foldl', nub, sort, sortOn)` が `-Wunused-imports` で止まりました。**「念のため import する」が、そのままエラーになる** のは `-Werror` の環境ならではです
2. **`Test.Hspec` が `fit` を輸出している** — 第 7 章で書いた学習の関数 `fit` を、第 7 章のテストで使おうとしたら「あいまいです」と言われました。Hspec の `fit` は「そのテストだけに注目する」関数です。`import Test.Hspec hiding (fit)` で解決しました。**名前の衝突が、実行時の取り違えではなくコンパイルエラーになる** 例です
3. **テストの中でも `let Right x = ...` は書けない** — 第 2 章と同じで、`-Wincomplete-uni-patterns` が `-Werror` で止めます。`expectRight` という関数にして、失敗したらその場でテストを落とすようにしました
4. **束縛した `first` が `Data.Bifunctor.first` を隠した** — 「重みの合計が最も大きいものを選ぶ」ところで、リストの先頭を `first` という名前で受けていました。保存の形式を書くために `Data.Bifunctor` を import した瞬間、3 か所が `-Wname-shadowing` で止まりました。**あとから import を足しただけで、前に書いたところが壊れる**という形です。`start` に変えて解決しました
5. **hlint が引数の省略を勧めてくる** — `buildPipeline maxDepth classWeight = Pipeline [...] maxDepth classWeight` は「そのまま渡しているだけ」なので省けます。`Data.Bifunctor` の `first`・`bimap` を使え、`fmap (const ())` は `void` にしろ、という指摘も受けました。**どれも「書きたかったことが、もっと短い語で言える」** という指摘で、無視せず直しました

## 8.13 探索と可視化

年齢の分布をグループごとに描いたり、決定木を図にしたりする手順は、[Python 版の第 8 章](../python/08-classification-and-preprocessing-pipeline.md) と [Kotlin 版](../kotlin/08-classification-and-preprocessing-pipeline.md) を参照してください。Haskell 版では Notebook を用意していません（[ADR 014](../../../adr/014-haskell-ml-libraries.md)）。

## 8.14 まとめ

この章では、タイタニック号の乗客データを題材に、前処理パイプラインとクラスの重みを付けた決定木を Haskell の TDD ですべて自作しました。

1. **前処理の種類を直和型で数え上げる** — `Step` の定義 1 か所を読めば、どんな前処理があるか全部わかる。種類を増やして対応を書き忘れれば、実行時ではなくコンパイルで止まる。PHP の interface（`ls` で分かる）・Elixir の関数節・Clojure の `defmulti`（開いた設計）と、同じ問題への別々の答えになっている
2. **「学習前」と「学習済み」を別の型にする** — `Step` と `FittedStep` を分けたので、学習していない前処理を適用することが書けない。PHP 版が実行時に `null` を見て失敗させていたところが、型で消える
3. **リストをそのままキーにできる** — グループを `[Text]` で表せる。PHP 版は連想配列のキーが文字列か整数しか取れず、値をタブでつなぐ必要があった
4. **`traverse` で「全部変換して、1 つでも失敗したら全体を失敗に」が 1 語で書ける** — `Map` に対しても効く。ほかの言語版では畳み込みと失敗の伝播を手で書いていた
5. **`binary` に `Text` のインスタンスが無い** — `binary` は `text` に依存していない。孤児インスタンスを足す代わりに、保存の形式を手で書いた。結果として、**壊れたファイルで任意のものが作られない**ことが設計に組み込まれた（PHP の `allowed_classes`・Elixir の `:safe`・Clojure の `edn` にあたる用心が要らない）
6. **`decode` ではなく `decodeOrFail` を使い、余りも確かめる** — `decodeOrFail` は「途中まで読めたら成功」を返すので、`rest` が空であることまで見る
7. **同点の決め方に重みの効果が乗っている** — `balanced` はクラスごとの重みの合計をぴったりそろえるので、2 クラスでは必ず同点になる。そこで何を選ぶかが結果を決める。「細部」ではない
8. **`M.Map` はキーの順で並ぶ** — 列の順、カテゴリの順、ラベルの順の 3 か所で、順を覚えるリストを別に持つことになった
9. **突き合わせる相手が無くても、正しさは支えられる** — 第 3 章の木との縮退・5 つの言語版との数値の一致・木の形そのもののテスト、の 3 本で支えた

**TODO リスト（この章の完了時点）**:

- [x] 中央値を計算する
- [x] 前処理を「学習してから適用する」部品として表す
- [x] 年齢をグループごとの中央値で補完する
- [x] 乗船した港を最頻値で補完する
- [x] カテゴリ値をダミー変数にする（基準の列を落とす）
- [x] 重み付きのジニ不純度と、クラスの重みを計算する
- [x] クラスの重みを付けた決定木を学習する
- [x] 前処理と決定木をパイプラインにつなぐ
- [x] 学習済みのパイプラインを保存して読み込む
- [x] 実データでクラスの重みの効果を確かめる

次の章では、特徴量そのものを作り変える **特徴量エンジニアリング** を扱います。[第 7 章の 7.10 節](07-linear-regression.md) で「係数の大小をそのまま影響の大きさと読めない」と書いた問題を、標準化として正面から扱い、statistics の平均・標準偏差と突き合わせます。

Haskell 版のほかの章は [Haskell 版のトップ](index.md) から辿れます。
