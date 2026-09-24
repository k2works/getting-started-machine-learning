---
type: Article
title: "第 14 章: K-means によるクラスタリング"
description: "K-means を Haskell の TDD で自作する。突き合わせるライブラリが無い章で、アルゴリズムそのものの性質をテストにする。printf \"%.0f\" が 0.5 を偶数側に丸めるために平均支出額がほかの言語版と 1 だけ食い違い、丸めを書き直してそろえるまでを実測で追う。"
tags: [article,getting-start-ml,haskell]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 14 章: K-means によるクラスタリング

## 14.1 はじめに

[第 13 章](13-principal-component-analysis.md) の主成分分析は「列を減らす」教師なし学習でした。この章の **クラスタリング** は「行をグループに分ける」教師なし学習です。正解ラベルが無いデータを、似たものどうしのかたまりに分けます。

使うのは **K-means** です。あらかじめクラスタの数 $k$ を決め、「各点を最も近い中心に割り当てる」「クラスタごとの平均を新しい中心にする」を中心が動かなくなるまで繰り返します。

**この章には、突き合わせるライブラリがありません。** [ADR 014](../../../adr/014-haskell-ml-libraries.md) のとおり Haskell には scikit-learn にあたるものが無く、K-means の実装も見当たりませんでした。第 13 章で hmatrix の `eigSH` と比べられたのは、固有値分解が「線形代数の道具」だったからです。K-means は機械学習のアルゴリズムそのものなので、**自作したものがそのまま最終実装になります**。

| 版 | 比べた相手 | 結果 |
|----|----------|------|
| Python | scikit-learn の `KMeans` | 初期中心を渡せる |
| Java・Scala・Clojure | Tribuo の `KMeansTrainer` | 複数回試す仕組みがある |
| Elixir | Scholar の `Cluster.KMeans` | 初期中心は渡せない（`:num_runs` はある） |
| PHP | Rubix ML の `KMeans` | 初期中心は渡せる。ただしミニバッチ |
| **Haskell** | **無い** | **自作が最終実装** |

比べる相手がいないぶん、この章では **K-means そのものの性質をテストにします**。

- 離れた 2 つのかたまりは、正しく分かれる
- 初期中心の置き方で結果が変わる（**局所解**）
- クラスタ数を増やすと SSE（誤差平方和）は必ず減る
- クラスタ数 1 の SSE は「件数 × 列数」になる（標準化したデータなら）

最後のものは特に効きます。標準化したデータの分散は列ごとに 1 なので、**$k$ = 1 の SSE は計算するまでもなく 440 × 6 = 2640 に決まっています**。自作の実装がこれを返すかどうかは、ライブラリが無くても確かめられる「正解」です。

## 14.2 K-means の仕組み

1. クラスタ数 $k$ を決め、初期中心を $k$ 個選ぶ
2. **割り当て**: 各点を、最も近い中心のクラスタに入れる
3. **更新**: クラスタごとに、属する点の平均を新しい中心にする
4. 中心が動かなくなるまで 2 と 3 を繰り返す

「最も近い」は距離の 2 乗（ユークリッド距離の 2 乗）で測ります。平方根を取らないのは、**大小関係が変わらないうえに `sqrt` を呼ばずに済む** からです。

**K-means は初期中心に依存します。** 悪い置き方をすると、中心が動かなくなっても最良の分け方になりません（局所解）。そこで初期中心を何通りか試し、SSE（各点と所属する中心の距離の 2 乗の合計）が最小のものを採ります。scikit-learn の `n_init` と同じ考え方です。

## 14.3 題材とデータ

卸売業者の顧客ごとの年間支出額（`Wholesale.csv`、440 件）を使います。`Channel`（小売か外食か）と `Region`（地域）は区分を表す番号で、支出額ではないので除きます。残る 6 列（`Fresh`・`Milk`・`Grocery`・`Frozen`・`Detergents_Paper`・`Delicassen`）が特徴量です。

金額の大きさが列ごとに違うので、[第 9 章](09-feature-engineering.md) の標準化（件数で割る標準偏差）で平均 0・標準偏差 1 にそろえてからクラスタリングします。**第 13 章と同じく、標準化は書き直しません。**

## 14.4 TODO リストの作成

```text
[ ] 支出額の列だけを読み込む
[ ] 列ごとに標準化する
[ ] 2 点間の距離の 2 乗を求める
[ ] 各点を最も近い中心に割り当てる
[ ] クラスタごとの平均を新しい中心にする
[ ] SSE を計算する
[ ] 中心が変わらなくなるまで繰り返す
[ ] 初期中心をシードで選ぶ
[ ] 初期中心を何通りか試して SSE が最小の結果を採る
[ ] クラスタ数ごとの SSE を並べる（エルボー法）
[ ] クラスタごとの件数と平均支出額をまとめる
```

## 14.5 支出額の列を読み込む

```haskell
spending :: Table -> Either String ([Text], [M.Map Text Double])
spending table = do
  rows <- traverse amounts (tableRows table)
  pure (columns, rows)
 where
  columns = filter (`notElem` nonSpending) (tableColumns table)
  amounts row = M.fromList <$> traverse (\column -> (,) column . fromIntegral <$> number row column) columns
```

`number`（[第 1 章](01-machine-learning-and-first-test.md) の `Csv` モジュール）は空欄を `Left` で返すので、**欠損値があればそこで読み込みが失敗します**。この章の題材に欠損値は無いので、補完はしません。「無いはず」を型で言い切って、あったときは止まる側に倒しています。

```haskell
    it "空欄があれば失敗する" $ do
      let table = expectRight (loadTable (utf8Csv "Channel,Region,Fresh,Milk\n1,3,10,\n"))
      spending table `shouldBe` Left "Milk を数値として読めません: "
```

`traverse` が 2 段重なっているのが Haskell らしいところです。外側は「440 行すべてを読む」、内側は「1 行の 6 列すべてを読む」で、**どちらかで 1 つでも失敗すれば全体が `Left` になります**。`for` ループとフラグの代わりに、同じ関数が 2 回出てくるだけです。

## 14.6 列ごとに標準化する

```haskell
standardizePoints :: [M.Map Text Double] -> [Text] -> Either String [[Double]]
standardizePoints x columns = do
  std <- fitStandardizer x columns
  toRows (standardizeAll std x) columns
```

3 行とも第 9 章のものです。`toRows` で「列の順に並べた数値の行」にしたところが、この章での「点」になります。

**`M.Map` は鍵の順（辞書順）で並ぶので、列の順は必ずリストで持ち回ります。** これも第 9 章・第 13 章と同じ約束です。

```haskell
    it "列ごとに平均 0・標準偏差 1 にそろえる" $ do
      let table = expectRight (loadTable (utf8Csv wholesaleLike))
          (columns, x) = expectRight (spending table)
          points = expectRight (standardizePoints x columns)
      points `shouldBe` [[-1.0, -1.0], [1.0, 1.0]]
```

2 件しかないときは、標準化した値が必ず −1 と 1 になります。**浮動小数点数でも誤差なく一致するので、`closeTo` ではなく `shouldBe` で書けます。** 架空のデータを 2 件にしたのは、この「ぴったり」が使えるからです。

## 14.7 各点を最も近い中心に割り当てる

### Red

```haskell
    it "各点を最も近い中心に割り当てる" $
      assignClusters [[0.0], [10.0], [4.0]] [[0.0], [10.0]] `shouldBe` [0, 1, 0]

    it "距離が同じなら先に並ぶ中心を選ぶ" $
      assignClusters [[5.0]] [[0.0], [10.0]] `shouldBe` [0]
```

2 つ目が大事です。ちょうど真ん中にある点をどちらに入れるかは、**アルゴリズムの定義には書かれていません**。ほかの言語版と同じ結果を出すには、ここをそろえる必要があります。この連載では「先に並ぶ中心」で統一しています。

### Green

```haskell
assignClusters :: [[Double]] -> [[Double]] -> [Int]
assignClusters points centers = map nearest points
 where
  nearest point =
    fst $
      foldl'
        ( \acc@(_, bestDistance) (k, centerPoint) ->
            let distance = squaredDistance point centerPoint
             in if distance < bestDistance then (k, distance) else acc
        )
        (0, 1 / 0)
        (zip [0 ..] centers)
```

**厳密な `<` で畳むと「同じなら前を残す」になります。** 第 13 章の `normalizeSigns`（絶対値が最大の要素）とまったく同じ書き方です。

`minimumBy` を使わなかったのは、[第 3 章](03-decision-tree-and-obvious-implementation.md) の教訓です。`Data.List.maximumBy` は同値なら後ろを返し、`minimumBy` は先を返します。**この非対称は覚えておけるものではない** ので、順序に意味がある畳み込みは自分で書くことにしました。

初期値の `1 / 0` は `Infinity` です。Haskell の `Double` は IEEE 754 なので、これで「まだ何も見ていない」を表せます。`Maybe` で包む手もありますが、**距離の比較に一度も `Nothing` の場合分けが出てこない** ぶん、こちらのほうが読みやすくなりました。

## 14.8 中心を更新する

```haskell
updateCenters :: [[Double]] -> [Int] -> [[Double]] -> [[Double]]
updateCenters points labels previous =
  [meanPoint (M.findWithDefault [] k members) centerPoint | (k, centerPoint) <- zip [0 ..] previous]
 where
  members = M.fromListWith (flip (<>)) [(label, [point]) | (point, label) <- zip points labels]
  meanPoint [] centerPoint = centerPoint
  meanPoint assigned _ = map (\column -> foldl' (+) 0.0 column / fromIntegral (length assigned)) (columnsOf assigned)
```

**点が 1 つも割り当てられなかったクラスタは、前の中心をそのまま残します。** 空のクラスタの平均は定義できないので、ここは実装ごとに決めごとが要る場所です（scikit-learn は点を選び直します）。ほかの言語版と同じ「前の中心を残す」にそろえました。

```haskell
    it "点が割り当てられなかったクラスタは前の中心を残す" $
      updateCenters [[0.0], [2.0]] [0, 0] [[0.0], [7.0]] `shouldBe` [[1.0], [7.0]]
```

`meanPoint` の 2 つの等式が、そのまま 2 つの場合分けになっています。**空リストの場合を書き忘れると `-Wincomplete-patterns` がコンパイルを止める** ので、「空だったらどうするか」を決めないままでは先へ進めません。第 3 章から繰り返し出てくる、この連載の Haskell 版の基本線です。

`M.fromListWith (flip (<>))` は「同じ鍵の値をつなぐ」畳み込みです。`fromListWith f` は重複したときに `f 新しい値 古い値` を呼ぶので、そのままだと逆順に積まれます。`flip` を挟んで **点が現れた順のままにしました**。順序は SSE の足し算の順に効くので、ほかの言語版と桁まで合わせるために要ります。

## 14.9 SSE を計算する

```haskell
sumOfSquaredErrors :: [[Double]] -> [Int] -> [[Double]] -> Double
sumOfSquaredErrors points labels centers =
  foldl' (+) 0.0 [squaredDistance point (centerAt label) | (point, label) <- zip points labels]
 where
  table = M.fromList (zip [0 ..] centers)
  centerAt label = M.findWithDefault [] label table
```

ここでも `sum` ではなく `foldl'` です。440 個の足し算なので、順序と結合が変わると下の桁が動きます。**[第 10 章](10-logistic-regression-and-ensemble.md) でほかの言語版と数値を合わせるために決めた約束が、そのままここでも効きました。**

## 14.10 中心が変わらなくなるまで繰り返す

```haskell
fitCenters :: [[Double]] -> [[Double]] -> Int -> KMeans
fitCenters points initialCenters maxIterations =
  KMeans
    { kmeansLabels = labels
    , kmeansCenters = centers
    , kmeansSse = sumOfSquaredErrors points labels centers
    }
 where
  centers = converge maxIterations initialCenters
  labels = assignClusters points centers
  converge 0 current = current
  converge remaining current =
    let next = updateCenters points (assignClusters points current) current
     in if next == current then current else converge (remaining - 1) next
```

**終了条件が `next == current` の 1 つの式で書けます。** 中心は `[[Double]]` なので、`Eq` がそのまま使えます。第 13 章で対称性を `m /= transpose m` と書けたのと同じ見返りです。

浮動小数点数の等値比較は普通は避けますが、ここでは正しい判定になります。K-means が収束すると **割り当てがまったく変わらなくなり、同じ点の同じ順序の平均を計算する** ので、ビット単位で同じ値が出るからです。「値が近い」ではなく「計算が同じ」ことを見ています。

`converge 0 current = current` の 1 行が、回数の上限です。ループカウンタと `break` の代わりに、基底ケースが 1 つ増えるだけになります。

```haskell
    it "更新の回数の上限に達したらそこで打ち切る" $ do
      let result = fitCenters twoBlobs [[0.0, 0.0], [10.0, 10.0]] 0
      kmeansCenters result `shouldBe` [[0.0, 0.0], [10.0, 10.0]]
```

上限 0 で呼べば 1 度も更新されない——という形で、打ち切りをテストできます。

## 14.11 局所解を再現する

離れた 2 つのかたまりを架空のデータにします。

```haskell
-- | 離れた 2 つのかたまり。どちらも 2 点ずつで、中心は (0.5, 0.5) と (10.5, 10.5)。
twoBlobs :: [[Double]]
twoBlobs = [[0.0, 0.0], [1.0, 1.0], [10.0, 10.0], [11.0, 11.0]]

    it "離れた 2 つのかたまりを分ける" $ do
      let result = fitCenters twoBlobs [[0.0, 0.0], [10.0, 10.0]] 300
      kmeansLabels result `shouldBe` [0, 0, 1, 1]
      kmeansCenters result `shouldBe` [[0.5, 0.5], [10.5, 10.5]]
      kmeansSse result `shouldBe` 2.0
```

**ここでも `shouldBe` で書けます。** 0.5 も 10.5 も 2 の冪の分数なので、倍精度で誤差なく表せるからです。架空のデータは、こういう値を選ぶと期待値がきれいになります。

### 局所解は 2 つのかたまりでは作れなかった

最初は、このデータの左のかたまりの中に両方の初期中心を置けば局所解になると思って、こう書きました。

```haskell
      let bad = fitCenters twoBlobs [[0.0, 0.0], [1.0, 1.0]] 300
```

**落ちました。** SSE は良い初期中心のときと同じ 2.0 でした。理由を追うと、1 巡目で片方の中心が右のかたまりを巻き込んで大きく動き、2 巡目には正しい分け方になっていました。**$k$ = 2 で 2 つのかたまりを分けるだけなら、K-means はたいてい自分で直ります。**

局所解には、少なくとも 3 つのかたまりと 3 つの中心が要ります。1 次元に 3 つ並べて、**手前のかたまりを 2 つの中心で分け合わせます**。

```haskell
-- | 1 次元に並んだ 3 つのかたまり。クラスタ数 3 で局所解を再現するために使う。
threeBlobs :: [[Double]]
threeBlobs = [[0.0], [1.0], [10.0], [11.0], [20.0], [21.0]]

    it "初期中心の置き方で結果が変わる（局所解）" $ do
      -- 手前のかたまりを 2 つの中心で分け合うと、奥の 2 つを 1 つの中心が抱えたまま動かなくなる
      let bad = fitCenters threeBlobs [[0.0], [1.0], [10.0]] 300
          good = fitCenters threeBlobs [[0.5], [10.5], [20.5]] 300
      kmeansSse good `shouldBe` 1.5
      kmeansSse bad `shouldBe` 101.0
      kmeansLabels bad `shouldBe` [0, 1, 2, 2, 2, 2]
```

0 と 1 をそれぞれ 1 つの中心が独占し、残る 1 つの中心が 10・11・20・21 の 4 点を抱えたまま動かなくなります。SSE は 101.0 で、最良の 1.5 の 67 倍です。**ラベルまでテストに書いたのは、「どう間違えるか」が分かっていないと、直ったことも分からないからです。**

**このテストは、初期中心を何通りも試す仕組みが要る理由そのものです。** ライブラリと突き合わせられない章では、こうやって「アルゴリズムの性質」を書き留めておくことが、実装が正しいことの根拠になります。そして **最初の書き方が落ちたことが、「局所解はいつでも起きるわけではない」という、もう 1 つの性質を教えてくれました**。

## 14.12 初期中心と複数回の試行

### シードで選ぶ

```haskell
chooseInitialCenters :: [[Double]] -> Int -> Int -> Either String [[Double]]
chooseInitialCenters points k seed
  | k > length points =
      Left (printf "点は %d 件しかないので %d 個の中心を選べません" (length points) k)
  | otherwise = traverse pointAt (take k (shuffle [0 .. length points - 1] seed))
```

`shuffle` は [第 2 章](02-data-preprocessing-and-triangulation.md) で自作した Fisher-Yates で、乱数は `java.util.Random` と同じ線形合同法です。**これにより Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版とまったく同じ点が初期中心になります。**

添字で点を引くところは `!!` を使わず、`Map` に写してから引きます。`!!` は範囲外で例外になる部分関数なので、`Map` の `lookup` を `Either` に変える形にしました。**`traverse` に渡せば、1 つでも引けなければ全体が `Left` になります。**

### 何通りか試して最小を採る

```haskell
fitWithRestarts :: [[Double]] -> Int -> Int -> Int -> Either String KMeans
fitWithRestarts points k seed nInit = do
  candidates <- traverse (chooseInitialCenters points k) [seed .. seed + nInit - 1]
  best points candidates
```

シードを 1 ずつずらして初期中心を 10 通り選び、SSE が最小の結果を採ります。scikit-learn の `n_init` と同じ既定値です。

```haskell
best :: [[Double]] -> [[[Double]]] -> Either String KMeans
best points candidates =
  case map (\initial -> fitCenters points initial defaultMaxIterations) candidates of
    [] -> Left "初期中心の候補がありません"
    first : rest -> Right (foldl' smaller first rest)
 where
  smaller current result = if kmeansSse result < kmeansSse current then result else current
```

`minimumBy` を使わずに畳んでいるのは、14.7 節と同じ理由です。ここでも「同じ SSE なら先に試したほうを残す」が、厳密な `<` から読めます。

**空リストの枝を書かされることが、ここでは設計の役に立ちました。** `foldl1` なら書かずに済みますが、空のときに例外になります。`case` に 2 つの枝を書いたおかげで、「候補が 0 個」という呼び間違いが `Left` として型に現れます。

### エルボー法

```haskell
sseByClusterCount :: [[Double]] -> [Int] -> Int -> Int -> Either String [(Int, Double)]
sseByClusterCount points counts seed nInit =
  traverse (\k -> (,) k . kmeansSse <$> fitWithRestarts points k seed nInit) counts
```

**`M.Map` は鍵の順で並ぶので、`(クラスタ数, SSE)` の組のリストで返します。** この章では鍵が 1 から 10 の整数なので辞書順でも困らないのですが、「順序を持たせたいならリスト」という約束をそろえました。

クラスタ数を増やせば SSE は必ず減ります。これもテストにします。

```haskell
    it "クラスタ数を増やすと SSE が小さくなる" $ do
      let sses = expectRight (sseByClusterCount twoBlobs [1, 2, 3] 0 defaultNInit)
      map fst sses `shouldBe` [1, 2, 3]
      and (zipWith (>=) (map snd sses) (drop 1 (map snd sses))) `shouldBe` True
```

`zipWith (>=) xs (drop 1 xs)` で「隣どうしを比べる」が 1 行になります。

## 14.13 クラスタごとの特徴をまとめる

```haskell
data ClusterSummary = ClusterSummary
  { summaryCluster :: Int
  , summaryCount :: Int
  , summaryMeans :: [Double]
  }
  deriving (Eq, Show)
```

平均は標準化した値ではなく、**元の単位（金額）** で出します。標準化した空間で分けても、読むのは元の金額だからです。

件数の多い順に並べ、同数ならクラスタ番号の小さい順にします。

```haskell
  pure (sortBy (comparing (\s -> (Down (summaryCount s), summaryCluster s))) summaries)
```

**タプルの `Ord` が辞書式なので、2 段の並べ替えが 1 つの式になります。** `Down` を片方にだけ付けられるのも効いていて、「件数は降順、番号は昇順」が素直に書けました。

### `printf "%.0f"` は 0.5 を偶数側に丸める

平均支出額を整数で表示するところで、ほかの言語版と 1 だけ違う値が出ました。

```text
3	10	15965	34708	48537	3055	24875	2943   ← Haskell の printf "%.0f"
3	10	15965	34709	48537	3055	24875	2943   ← Java 版・Elixir 版・PHP 版
```

`Milk` の平均をそのまま出すと **34708.5** で、ちょうど境目でした。

**Haskell の `printf "%.0f"` も `round` も、ちょうど 0.5 のときは偶数側に丸めます（銀行家の丸め）。** `round` が IEEE 754 の最近接偶数丸めに従うのは `Prelude` の仕様どおりで、`printf` の `%f` も `Numeric.showFFloat` 経由で同じ側になります。Java の `String.format("%.0f")` や Elixir の `round/1` は 0.5 を切り上げるので 34709 です。

**同じ書式指定子に見えて、境目の振る舞いが違います。** PHP 版も同じところで止まり（PHP の `sprintf('%.0f')` は C ライブラリの丸めで偶数側）、`round()` を通してそろえていました。

ほかの言語版と数値をそろえるほうを選び、丸めを自分で書きました。

```haskell
{- | 0.5 を 0 から遠いほうへ丸める。

Haskell の @round@ も @printf "%.0f"@ も、ちょうど 0.5 のときは偶数側に丸める
（銀行家の丸め）。Java 版・Elixir 版の丸めは 0.5 を切り上げるので、平均支出額に
ちょうど 34708.5 が出たところで 1 だけ食い違った。表示をそろえるために
「0.5 を足して切り下げる」ほうを使う。支出額は必ず 0 以上なのでこれで足りる。
-}
roundHalfUp :: Double -> Integer
roundHalfUp value = floor (value + 0.5)
```

`floor (value + 0.5)` は、値が負なら望む向きになりません。**支出額は必ず 0 以上** という前提があるからこの 1 行で足りる、ということを doc コメントに書いてあります。汎用の丸めを作る場面ではないので、前提を明示して短く済ませました。

テストには「2 つの丸めが違う」ことをそのまま書きます。

```haskell
    it "ちょうど 0.5 は 0 から遠いほうへ丸める（round は偶数側に丸める）" $ do
      roundHalfUp 34708.5 `shouldBe` 34709
      round (34708.5 :: Double) `shouldBe` (34708 :: Integer)
```

**2 行目は「標準ライブラリはこう振る舞う」という学習用テストです。** これが無いと、後から読む人に `roundHalfUp` が何のためにあるのか伝わりません。

なお、テストでも同じ `roundHalfUp` を使って期待値と比べています。テスト側で `round` を使うと、実装と同じ食い違いをテストが再現してしまい、**ほかの言語版と一致していないことに気づけなくなる** からです。

## 14.14 実データでクラスタリングする

### 実データのテスト

```haskell
    it "クラスタごとの件数と平均支出額がほかの言語版と一致する" $ do
      loaded <- loadWholesale
      case loaded of
        Nothing -> pendingWith "学習データがありません"
        Just (columns, x) -> do
          let points = expectRight (standardizePoints x columns)
              labels = kmeansLabels (expectRight (fitWithRestarts points nClusters 0 defaultNInit))
              summaries = expectRight (summarizeClusters x columns labels)
          map summaryCluster summaries `shouldBe` [2, 1, 0, 3, 4]
          map summaryCount summaries `shouldBe` [265, 96, 65, 10, 4]
```

**クラスタ番号まで一致させています。** 番号は初期中心の選び方で決まるので、乱数と割り当ての規則がそろっていれば同じになるはずのものです。件数だけを比べると、番号が入れ替わっていても気づけません。

学習データが無ければ `pendingWith` でスキップします。`Dataset.exists` が偽なら 1 行も読まないので、データを置かない環境でもテストは緑のままです。**学習データの行は、コードにもテストにも記事にも書きません。**

### 実行して結果を表示する

```text
データ件数: 440（支出額 6 列）
クラスタ数ごとの SSE（初期中心 10 通りの最小値）:
クラスタ数	SSE
1	2640.00
2	1954.18
3	1614.52
4	1334.36
5	1085.27
6	947.20
7	888.22
8	775.24
9	690.81
10	618.17

クラスタ数 5 のクラスタごとの件数と平均支出額:
クラスタ	件数	Fresh	Milk	Grocery	Frozen	Detergents_Paper	Delicassen
2	265	8909	2967	3804	2248	989	962
1	96	5509	10556	16478	1420	7199	1659
0	65	31117	4260	5374	7225	849	2286
3	10	15965	34709	48537	3055	24875	2943
4	4	52022	31696	18491	29826	2699	19656
```

**SSE の列は 1 列だけです。** ほかの言語版にはここにライブラリの列が並びます（Elixir 版は Scholar の k-means++、PHP 版は Rubix ML のミニバッチ）。Haskell 版には並べる相手がいません。

### ほかの言語版との一致

**SSE の 10 行も、クラスタごとの件数と平均支出額も、[Java 版](../java/14-k-means-clustering.md)・[Scala 版](../scala/14-k-means-clustering.md)・[Clojure 版](../clojure/14-k-means-clustering.md)・[Elixir 版](../elixir/14-k-means-clustering.md)・[PHP 版](../php/14-k-means-clustering.md) と完全に一致しました。** 初期中心の乱数（自作の線形合同法による Fisher-Yates）と、中心の更新・SSE の足し算の順序をそろえたからです。

食い違ったのは 14.13 節の丸めだけでした。[第 10 章](10-logistic-regression-and-ensemble.md) では `Data.Map` が鍵の順で畳むために特徴量の重要度が Elixir 版と一致し、挿入順の PHP 版・Clojure 版と 1 ulp ずれました。**この章のずれは計算ではなく表示の側で、原因も対処もはっきりしています。**

**いちばん分かりやすいのは $k$ = 1 の行です。** クラスタが 1 つなら、答えは全点の平均に決まっていて、SSE は 2640.00 です。標準化した 6 列の分散が 1 なので 440 × 6 になります。**ライブラリが無くても検算できる 1 点** で、実装の足し算がどこかで壊れていればここで分かります。

**エルボー法で読むと**、SSE の減り方は $k$ = 3 → 4 で 280.16、$k$ = 4 → 5 で 249.09、$k$ = 5 → 6 で 138.07、$k$ = 6 → 7 で 58.98 と小さくなっていきますが、$k$ = 7 → 8 では 112.98 と再び大きくなり、はっきりした肘は見えません。ここでは Python 版・Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版と同じくクラスタ数を 5 にしました。エルボーが 1 点に決まらないときは、クラスタを解釈できるかどうかも合わせて判断します。

### 結果を読む

- **265 件のクラスタ**: どの支出額も少なめの、最も多いグループ
- **96 件のクラスタ**: Grocery・Milk・Detergents_Paper が多めのグループ
- **65 件のクラスタ**: Fresh が突出して多いグループ
- **10 件のクラスタ**: Milk・Grocery・Detergents_Paper が非常に多いグループ
- **4 件のクラスタ**: Fresh・Milk・Frozen・Delicassen がどれも極端に多い顧客。グループというより外れ値に近い存在です

K-means は外れ値にも中心を 1 つ割いてしまうことが、この結果から分かります。

## 14.15 Notebook による探索と可視化

Haskell 版では Notebook と可視化の節を設けません。クラスタの散布図、エルボー図、クラスタごとの支出額のヒートマップは、[Python 版の第 14 章](../python/14-k-means-clustering.md) と [Kotlin 版の第 14 章](../kotlin/14-k-means-clustering.md) の「Notebook による探索と可視化」の節を参照してください。

## 14.16 何が置き換えられて、何が置き換えられないのか

**何も置き換えられません。** [ADR 014](../../../adr/014-haskell-ml-libraries.md) で見込んだとおり、この章は第 3 章（決定木）・第 8 章（前処理）・第 10 章（ロジスティック回帰とランダムフォレスト）・第 11 章（評価と交差検証）と同じく、自作だけで完結します。

hmatrix は線形代数のライブラリなので、距離の計算を行列積に書き換えることはできます。しかし **K-means の割り当てと更新の繰り返しそのものは、hmatrix の守備範囲の外** です。第 13 章で `eigSH` が使えたのは、固有値分解が「線形代数の一手続き」として閉じていたからでした。

比べる相手がいない章で実装の正しさを支えるのは、次の 3 つです。

| 支え | この章での形 |
|------|------------|
| **決まっている答え** | $k$ = 1 の SSE が件数 × 列数（2640.00）になる |
| **アルゴリズムの性質** | クラスタ数を増やすと SSE が減る、初期中心で結果が変わる |
| **ほかの言語版との一致** | SSE の 10 行とクラスタごとの平均が完全一致 |

3 つ目が使えるのは、この連載が同じ題材を 10 以上の言語で書いているからです。**ライブラリの代わりに、ほかの言語版の実装が突き合わせる相手になりました。**

## 14.17 品質チェック

`nix develop .#haskell` の中で、整形の検査・静的解析・テスト・カバレッジをまとめて実行します。

```console
$ npx gulp apps:check:haskell
```

この章で追加した依存はありません。第 2 章の `Random`、第 9 章の標準化、第 1 章の `Csv`・`Dataset` を使っているだけです。**第 13 章と違って hmatrix すら import していません。**

## 14.18 まとめ

この章では、K-means を Haskell の TDD で自作しました。突き合わせるライブラリがない章です。

1. **自作が最終実装になった** — Haskell には K-means のライブラリが無い。第 13 章で hmatrix の `eigSH` と比べられたのは、固有値分解が線形代数の道具として閉じていたから
2. **アルゴリズムの性質をテストにした** — 離れたかたまりが分かれること、局所解が起きること、クラスタ数を増やすと SSE が減ること。$k$ = 1 の SSE が「件数 × 列数」に決まることは、ライブラリが無くても使える検算になった
3. **ほかの言語版と完全に一致した** — SSE の 10 行も、クラスタごとの件数・平均支出額・クラスタ番号も。初期中心の乱数と足し算の順序をそろえた結果
4. **食い違ったのは丸めだけだった** — 平均支出額にちょうど 34708.5 が出て、偶数側に丸める `printf "%.0f"` が 1 だけ低い値を出した

Haskell 版ならではの学びもありました。

- **`printf "%.0f"` も `round` も銀行家の丸めである** — 0.5 は偶数側。Java・Elixir の「0.5 を切り上げる」とは違う。PHP 版と同じ場所で止まり、同じ判断（丸めを書き直してそろえる）をした
- **前提を doc コメントに書いて短く済ませる** — `floor (value + 0.5)` は負の値では望む向きにならない。「支出額は 0 以上」という前提を書いて、汎用の丸めを作らなかった
- **収束の判定に `==` を使ってよい場所がある** — 浮動小数点数の等値比較は普通は避けるが、K-means が収束すると「同じ点の同じ順序の平均」を計算するので、ビット単位で同じ値になる。見ているのは「値が近い」ではなく「計算が同じ」こと
- **`1 / 0`（Infinity）が `Maybe` より読みやすいことがある** — 「まだ何も見ていない」を表すのに `Nothing` を使うと、距離の比較のたびに場合分けが出てくる
- **空リストの枝を書かされることが設計の役に立つ** — `foldl1` の代わりに `case` を 2 つ書いたおかげで、「候補が 0 個」という呼び間違いが `Left` として型に現れた
- **タプルの `Ord` で 2 段の並べ替えが 1 式になる** — `(Down count, cluster)` で「件数は降順、番号は昇順」
- **局所解を作るには 3 つのかたまりが要った** — 2 つのかたまりを $k$ = 2 で分けるだけなら K-means は自分で直る。「悪い例」を書いたつもりのテストが落ちて、そのことを知った

次の章では、ここまでに作ったモデルを Web API として公開します。**Scotty と aeson を使うので、この章とは逆に、ライブラリがほとんどの仕事をします。**

Haskell 版のほかの章は [Haskell 版のトップ](index.md) から辿れます。
