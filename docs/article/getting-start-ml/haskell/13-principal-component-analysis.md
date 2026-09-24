---
type: Article
title: "第 13 章: 主成分分析による次元削減"
description: "中心化・分散共分散行列・固有値分解・符号・射影・寄与率を Haskell の TDD で自作し、自作の Jacobi 法と hmatrix の eigSH（LAPACK の DSYEV）を同じ手順で突き合わせる。固有値分解を引数で差し替えられるようにすると、2 つの実装の差をそのまま測れる。"
tags: [article,getting-start-ml,haskell]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 13 章: 主成分分析による次元削減

## 13.1 はじめに

ここまでの章では、正解ラベルのあるデータで予測をしてきました。この章から扱う **教師なし学習** では、正解ラベルを使わずに、データそのものの構造を調べます。

最初に扱うのは **主成分分析（PCA）** です。住宅価格のデータには 15 個の列がありますが、列どうしは互いに関係しあっていて、実際にはもっと少ない数の「軸」で大部分を説明できることがあります。主成分分析は、データのばらつきを最もよく説明する新しい軸を探し、たくさんの列を少数の軸に要約します。

[Python 版の第 13 章](../python/13-principal-component-analysis.md) は NumPy の固有値分解で主成分分析を自作し、scikit-learn の `PCA` と突き合わせました。Haskell 版も同じ構成です。

**この章は、Haskell 版でいちばんライブラリの恩恵を受ける章です。** [ADR 014](../../../adr/014-haskell-ml-libraries.md) で「機械学習のアルゴリズムはすべて自作が最終実装になる」と決めたなかで、線形代数の層だけは hmatrix と比べられます。第 7 章の正規方程式に続いて、ここでは **対称行列の固有値分解**（`eigSH`、LAPACK の DSYEV）が相手です。

そのうえで、固有値分解そのものも Jacobi 法で自作します。理由は 2 つあります。

- **[PHP 版](../php/13-principal-component-analysis.md) では、ライブラリの固有ベクトルが 15×15 の実データで落ちました。** MathPHP は固有値を返せるのに、同じ固有値を渡した固有ベクトルの計算が「それは固有値ではありません」と失敗します。**「ライブラリにその機能がある」ことは、「そのデータで動く」ことを意味しません。** hmatrix が実データに耐えるかどうかは、実際に 15×15 を通すまで分かりません（13.10 節で確かめます）
- **自作があれば、ライブラリの答えを比べる相手ができます。** 固有値分解は「答えが 1 つに決まる」計算なので、まったく違う手続き（回転を重ねる Jacobi 法と、LAPACK の DSYEV）が同じ答えを出すことが、両方の正しさの証拠になります

Haskell 版の工夫は、**固有値分解を引数で差し替えられるようにした** ことです。

```haskell
fitWith :: ([[Double]] -> Either String Eigen) -> [[Double]] -> Int -> Either String Pca
```

`fit` は `fitWith jacobiEigen` の別名にすぎません。同じ主成分分析の手順を 2 つの固有値分解で回せるので、「実装を差し替えたときに答えがどれだけ動くか」を、テストの中でそのまま測れます。

## 13.2 主成分分析とは

### ばらつきが最も大きい方向を探す

データの点がばらついている空間に、新しい軸を 1 本引くことを考えます。**第 1 主成分** は、その軸にデータを射影したときに、ばらつき（分散）が最も大きくなる向きです。第 2 主成分は、第 1 主成分と直交する向きのうち、ばらつきが最も大きい向きです。以下、列の数だけ同じように決めていきます。

### 分散共分散行列の固有ベクトル

この「ばらつきが最大になる向き」は、**分散共分散行列の固有ベクトル** として求まります。固有値が、その向きでのばらつきの大きさ（分散）です。

分散共分散行列は、対角に各列の分散、非対角に 2 列ずつの共分散を並べた正方行列で、必ず対称になります。対称行列の固有値分解は、非対称な行列より扱いやすく、実数の固有値と直交する固有ベクトルが得られます。

### 寄与率

固有値をすべて足すと、データ全体のばらつきの合計になります。ある主成分の固有値を合計で割った値が **寄与率** で、「この軸だけで全体の何割を説明できるか」を表します。寄与率を大きいほうから足していったものが **累積寄与率** です。累積寄与率が 0.8 に届くまでの主成分を採る、といった目安の決め方をします。

## 13.3 題材とデータ

ボストンの住宅価格（`Boston.csv`、100 件）を使います。[第 9 章](09-feature-engineering.md) では `PRICE` を予測する回帰の題材でしたが、この章では正解ラベルを使わないので、`PRICE` も含めたすべての列を主成分分析にかけます。`CRIME` はカテゴリ値なのでダミー変数にし、`RM` などの欠損値は列の平均値で補完します。

主成分分析は「ばらつきの大きさ」を見るので、単位の大きい列（`TAX` は数百、`NOX` は 0.5 前後）がそのままでは支配的になります。そこで、すべての列を平均 0・標準偏差 1 にそろえてから分析します。標準化は [第 9 章](09-feature-engineering.md) で作った `fitStandardizer`・`standardizeAll` をそのまま呼びます（13.9 節）。

## 13.4 TODO リストの作成

```text
[ ] 列ごとの平均を求める
[ ] 各列から平均を引く（中心化）
[ ] 分散共分散行列を求める
[ ] Jacobi 法で固有値分解を自作する
[ ] hmatrix の eigSH の振る舞いを学習用テストで確かめる
[ ] 固有ベクトルの符号をそろえる
[ ] 寄与率と累積寄与率を求める
[ ] データを主成分の向きに射影する
[ ] 累積寄与率がしきい値に届く主成分の数を求める
[ ] 主成分への影響が大きい列を求める
[ ] 自作と hmatrix を 15 列の実データで突き合わせる
[ ] Boston を前処理して実データで要約する
```

## 13.5 分散共分散行列を求める

### Red: 分散と共分散

行列は「長さのそろった `[Double]` のリスト」で表します。第 9 章までと同じで、データフレームのライブラリは使いません。hmatrix の `Matrix` にするのは、`eigSH` を呼ぶ関数の中だけにします。

2 列目が 1 列目のちょうど 2 倍になる、完全に相関する 3 件を架空のデータにします。分散は 1 と 4、共分散は 2 になるはずです。

```haskell
-- | 架空の 3 件 2 列。2 列目は 1 列目のちょうど 2 倍で、完全に相関する。
correlated :: [[Double]]
correlated = [[1.0, 2.0], [2.0, 4.0], [3.0, 6.0]]

    it "分散共分散行列は対称で、対角に件数から 1 を引いた数で割った分散が並ぶ" $
      fmap (closeTo [[1.0, 2.0], [2.0, 4.0]]) (covarianceMatrix correlated) `shouldBe` Right True
```

### Green: 中心化してから内積を取る

```haskell
covarianceMatrix :: [[Double]] -> Either String [[Double]]
covarianceMatrix m
  | length m < 2 = Left (printf "主成分分析には 2 件以上のデータが必要です（%d 件）" (length m))
  | otherwise = Right [[dot a b / (n - 1) | b <- centered] | a <- centered]
 where
  centered = transpose (center m (matrixColumnMeans m))
  n = fromIntegral (length m)
```

リスト内包表記の二重ループが、そのまま「行列の各成分」になります。Haskell では `transpose` が `Data.List` にあるので、転置のために行列ライブラリを持ち出す必要はありません。

**割る数を n−1 にするか n にするかは、この章では結論に影響しません。** 寄与率は「固有値 ÷ 固有値の合計」なので、行列全体を定数倍しても変わらないからです。それでも n−1 を選んだのは、ほかの言語版と分散（固有値）そのものの値をそろえるためです。

内積は `foldl'` で書きます。

```haskell
-- | 内積。'sum' は右結合になりうるので 'foldl'' を使う。
dot :: [Double] -> [Double] -> Double
dot a b = foldl' (+) 0.0 (zipWith (*) a b)
```

[第 10 章](10-logistic-regression-and-ensemble.md) で決めた約束です。この章の計算は最後まで足し算と掛け算の積み重ねなので、足す順番が変わると下の桁が動きます。

なお hmatrix には `meanCov` があり、**割る数も n−1 で自作と一致していました**（完全に相関する 3 件で `[[1,2],[2,4]]` を返します）。それでも自作を使うのは、行列を `[[Double]]` のまま持ちたいからです。ここで `Matrix` に変えると、中心化も射影も転置も hmatrix 側に寄せることになり、**「差し替えたいのは固有値分解だけ」という 13.7 節の設計が崩れます**。ライブラリで代われる場所でも、代えると別のものが動く——という判断です。

## 13.6 固有値分解を 2 通り用意する

### 対称かどうかは自分で確かめる

hmatrix の `eigSH` は `Herm`（エルミート行列）を受け取ります。`Herm` を作る方法は 2 つあります。

| 関数 | 振る舞い |
|------|---------|
| `sym` | 渡した行列と転置の平均を取って、強制的に対称にする |
| `trustSym` | 対称であることを確かめずに信じる。**上三角だけを見る** |

`trustSym` に対称でない行列を渡すと、エラーにはならず「上三角だけを見た別の行列」の答えが返ります。[Elixir 版](../elixir/13-principal-component-analysis.md) の `Nx.LinAlg.eigh` とまったく同じ落とし穴です。**この連載で 2 回目なので、学習用テストを先に書きました。**

```haskell
    it "対称でない行列は固有値分解できない" $
      jacobiEigen [[1.0, 2.0], [3.0, 4.0]]
        `shouldBe` Left "固有値分解できません（対称行列ではありません）"

    it "hmatrix の eigSH も対称でない行列を断る" $
      hmatrixEigen [[1.0, 2.0], [3.0, 4.0]]
        `shouldBe` Left "固有値分解できません（対称行列ではありません）"
```

2 つ目のテストの名前は正確ではありません。断っているのは `eigSH` ではなく、その手前の自分の検査です。

```haskell
checkSymmetric :: [[Double]] -> Either String ()
checkSymmetric m
  | any ((/= size) . length) m = Left (printf "正方行列ではありません: %d 行" size)
  | m /= transpose m = Left "固有値分解できません（対称行列ではありません）"
  | otherwise = Right ()
 where
  size = length m
```

**`m /= transpose m` の 1 行で済むのは、`[[Double]]` が `Eq` のインスタンスだからです。** 行列を専用の型ではなくリストのリストで持つことの、分かりやすい見返りです。

失敗の文言を 2 つの実装で同じにしておくと、**固有値分解を差し替えても、呼ぶ側のテストが変わりません**。次の節でそれが効きます。

### Jacobi 法を自作する

Jacobi 法は、対称行列の非対角成分を 2 次元の回転で 1 つずつ 0 にしていく手続きです。1 つ 0 にすると別のところがわずかに復活するので、全体が対角行列に十分近づくまで何巡もします。回転行列を掛け合わせたものが、そのまま固有ベクトルになります。

**固有値と固有ベクトルが同時に出るので、「固有値を求めてから、それを使って固有ベクトルを探す」という 2 段構えになりません。** PHP 版で落ちたのは、その 2 段目でした。

Haskell で困るのは「行列の 1 成分を書き換える」ところです。不変のリストでは添字の書き換えができないので、[第 2 章](02-data-preprocessing-and-triangulation.md) の Fisher-Yates と同じように **`Map` に写してから書き換えます**。鍵は `(Int, Int)` の組です。

```haskell
jacobiEigen :: [[Double]] -> Either String Eigen
jacobiEigen m = do
  checkSymmetric m
  let size = length m
      initialA = M.fromList [((i, j), value) | (i, row) <- zip [0 ..] m, (j, value) <- zip [0 ..] row]
      initialV = M.fromList [((i, j), if i == j then 1.0 else 0.0) | i <- [0 .. size - 1], j <- [0 .. size - 1]]
      (a, v) = sweep maxSweeps size initialA initialV
      values = [at a (i, i) | i <- [0 .. size - 1]]
      -- 固有ベクトルは列に並ぶので、転置して 1 行に 1 つずつにする。
      vectors = transpose [[at v (i, j) | j <- [0 .. size - 1]] | i <- [0 .. size - 1]]
  pure (sortEigen values vectors)
```

回転そのものは短く書けます。

```haskell
  theta = (aqq - app) / (2.0 * apq)
  sign = if theta >= 0.0 then 1.0 else -1.0
  -- tan は絶対値の小さいほうの根を選ぶ。素直に解の公式を書くと桁落ちする。
  tangent = sign / (abs theta + sqrt (theta * theta + 1.0))
  cosine = 1.0 / sqrt (tangent * tangent + 1.0)
  sine = tangent * cosine
```

`sign / (abs theta + sqrt ...)` は、2 次方程式の根のうち絶対値の小さいほうを、引き算を通さずに求める書き方です。素直に解の公式を書くと、$\theta$ が大きいときに近い数どうしの引き算になって桁落ちします。

書き換えは、**新しい値の `Map` を作って左結合の `M.union` で重ねます**。

```haskell
  | otherwise = (M.union (M.fromList updatesA) a, M.union (M.fromList updatesV) v)
```

`M.union` は左を優先するので、これで「更新した成分だけを上書きした行列」になります。命令型の言語なら `$a[$k][$p] = ...` と書くところが、Haskell では **「変更の集合」を作ってから元に重ねる** 形になります。どの成分を触ったかがリストとして目に見えるので、更新の対称性（`(k,p)` と `(p,k)` を両方書く）を見落としにくくなりました。

巡回は「非対角成分の 2 乗和が十分小さくなったら止める」で終わります。

```haskell
sweep 0 _ a v = (a, v)
sweep remaining size a v
  | off a < offTolerance = (a, v)
  | otherwise = sweep (remaining - 1) size a' v'
```

**再帰の引数に残り回数を持たせると、「上限に達したら打ち切る」が 1 行のパターンで書けます。** ループカウンタと `break` を書く代わりに、基底ケースが 1 つ増えるだけです。

### 学習用テストで hmatrix の約束を確かめる

`eigSH` が何を返すのかは、型からは読み取れません。学習用テストで確かめます。

| 確かめたこと | 結果 |
|------------|------|
| 固有値の並び | 大きい順 |
| 固有ベクトルの向き | **列に並ぶ**（転置して 1 行に 1 つにする） |
| 符号の規則 | 無い |

自作も同じ約束（固有値の降順、1 行に 1 つ）にそろえて `Eigen` に詰めます。

```haskell
data Eigen = Eigen
  { eigenValues :: [Double]
  , eigenVectors :: [[Double]]
  }
  deriving (Eq, Show)
```

そのうえで、小さい対称行列で 2 つの実装が一致することをテストにします。

```haskell
    it "自作と hmatrix の固有ベクトルが符号をそろえれば一致する" $ do
      let mine = normalizeSigns (eigenVectors (expectRight (jacobiEigen symmetric3)))
          theirs = normalizeSigns (eigenVectors (expectRight (hmatrixEigen symmetric3)))
      closeTo mine theirs `shouldBe` True
```

### 符号の規則を決める

固有ベクトルは、すべての要素の符号を反転しても同じ向きを表します。どちらを返すかはライブラリごとに違うので、**比べる前に規則を決めてそろえます**。この連載では「絶対値が最大の要素を正にする」で統一しています。

```haskell
normalizeSigns :: [[Double]] -> [[Double]]
normalizeSigns = map normalizeSign
 where
  normalizeSign row = if largest row < 0.0 then map negate row else row
  -- 絶対値が最大の要素。同じなら前の要素を残すように、厳密な > で畳む。
  largest = foldl' (\acc value -> if abs value > abs acc then value else acc) 0.0
```

絶対値が同じ要素が並んだときにどちらを見るかで結果が変わるので、`>=` ではなく厳密な `>` にします。この章と [第 14 章](14-k-means-clustering.md) で、同じ理由の同じ書き方が出てきます。

```haskell
    it "絶対値が同じなら前の要素を見る" $
      normalizeSigns [[-0.5, 0.5]] `shouldBe` [[0.5, -0.5]]
```

## 13.7 主成分を求める

### 固有値分解を引数にする

主成分分析の手順は「分散共分散行列を作る → 固有値分解する → 並べて寄与率を出す」です。真ん中だけが差し替えたい部分なので、そこを引数にします。

```haskell
-- | 分散共分散行列を自作の Jacobi 法で固有値分解し、寄与率の大きい順に主成分を求める。
fit :: [[Double]] -> Int -> Either String Pca
fit = fitWith jacobiEigen

-- | 固有値分解のしかたを差し替えられる 'fit'。自作と hmatrix を同じ手順で比べるために使う。
fitWith :: ([[Double]] -> Either String Eigen) -> [[Double]] -> Int -> Either String Pca
fitWith decompose m nComponents = do
  cov <- covarianceMatrix m
  Eigen values vectors <- decompose cov
  checkNComponents nComponents (length values)
  let total = foldl' (+) 0.0 values
      variances = take nComponents values
  pure
    Pca
      { pcaMean = matrixColumnMeans m
      , pcaComponents = normalizeSigns (take nComponents vectors)
      , pcaExplainedVariance = variances
      , pcaExplainedVarianceRatio = map (/ total) variances
      }
```

**引数が関数 1 つなので、依存を差し替える仕掛けが `fit = fitWith jacobiEigen` の 1 行で済みます。** ほかの言語版では、`Pca` を作る手順を 2 回書いたり（Elixir 版の `fit` と `scholar_fit`）、ライブラリの結果を自作と同じ形に詰め替えたりしていました。ここでは **前後の手順が共有されるので、「固有値分解以外は同じ」ことが型で保証されます**。

`Eigen values vectors <- decompose cov` のように、`do` 記法の束縛でレコードを分解できるのも効いています。`Either` のエラーはそのまま外に出るので、`fitWith` の中に `case` は 1 つも要りません。

### 完全に相関する 2 列

完全に相関する 2 列なら、1 本の軸ですべてを説明できます。第 1 主成分の寄与率が 1、第 2 主成分が 0 になるはずです。

```haskell
    it "完全に相関する 2 列なら第 1 主成分の寄与率が 1 になる" $ do
      let model = expectRight (fit correlated 2)
      closeTo [[1.0, 0.0]] [pcaExplainedVarianceRatio model] `shouldBe` True

    it "hmatrix の eigSH で学習しても同じ寄与率になる" $ do
      let mine = expectRight (fit correlated 2)
          theirs = expectRight (fitWith hmatrixEigen correlated 2)
      closeTo [pcaExplainedVarianceRatio mine] [pcaExplainedVarianceRatio theirs] `shouldBe` True
```

## 13.8 データを主成分の向きに射影する

```haskell
transform :: Pca -> [[Double]] -> [[Double]]
transform model m = map (\row -> map (dot row) (pcaComponents model)) (center m (pcaMean model))
```

完全に相関する 3 件を 1 次元に落とすと、真ん中の点が 0、前後の点が同じ大きさで符号だけ逆になります。**具体的な値ではなく、この関係をテストにします。** 符号の規則を変えたときに落ちないテストになるからです。

```haskell
    it "主成分の向きに射影すると、完全に相関する 2 列が 1 列で表せる" $ do
      let model = expectRight (fit correlated 1)
      case transform model correlated of
        -- 中心の行は 0 に、前後の行は同じ大きさで符号が逆になる
        [[first], [middle], [last']] -> closeTo [[0.0, negate first]] [[middle, last']] `shouldBe` True
        projected -> expectationFailure ("1 列 3 行になりません: " <> show projected)
```

**`case` のパターンが、そのまま「1 列 3 行である」という主張になっています。** `length` を 2 回数える代わりに形を書けるので、テストが短くなりました。`-Wincomplete-uni-patterns` が `-Werror` で止めるので `let [[a],[b],[c]] = ...` とは書けず、形が違ったときの枝を必ず書くことになります。**書かされた枝が、そのまま親切なエラーメッセージになりました。**

なお `last` は `Prelude` の関数名と衝突するので `last'` にしています。衝突しても `-Wname-shadowing` で止まるだけで、気づかずに影を作ることはありません。

## 13.9 必要な主成分の数と、影響が大きい列

### 累積寄与率がしきい値に届くまでの数

```haskell
componentsNeeded :: [Double] -> Double -> Int
componentsNeeded ratios threshold =
  case [index | (index, cumulative) <- zip [1 ..] (scanl1 (+) ratios), cumulative >= threshold] of
    found : _ -> found
    [] -> length ratios
```

`scanl1 (+)` が累積和です。**遅延評価なので、`scanl1` がリスト全体を作ってから探すわけではありません。** 最初に見つかったところで内包表記の評価が止まるので、「見つけたら抜ける」ループを自分で書く必要がありません。

しきい値に届かないまま終わったら、すべての主成分の数を返します。「見つからなかった」を `Maybe` で表して呼び出し側に判断させるより、**この関数の中で決め切る** ほうが使う側が短くなります。ここは `Maybe` を返さない側に倒した、という判断です。

### 主成分への影響が大きい列

主成分の「意味」は、係数（ローディング）の大きい列から読みます。

```haskell
{- | 主成分の係数の絶対値が大きい順に、列名と係数を k 個返す。

'sortBy' は安定なので、絶対値が同じなら元の列の順が残る。
-}
topLoadings :: [Double] -> [Text] -> Int -> [(Text, Double)]
topLoadings component columns k =
  take k (sortBy (comparing (Down . abs . snd)) (zip columns component))
```

`comparing (Down . abs . snd)` で「絶対値の降順」が 1 つの式になります。PHP 版が `usort` に比較関数を書き、Elixir 版が `sort_by(..., :desc)` と書いたところです。

**`Data.List.sortBy` が安定であることは仕様で決まっています。** [第 3 章](03-decision-tree-and-obvious-implementation.md) で `maximumBy` が「同値なら後ろを返す」ことに気づいて多数決を書き直したのとは逆で、ここは寄りかかってよい側です。それでもテストには書きます。

```haskell
    it "係数の絶対値が同じなら元の列の順を残す" $
      topLoadings [0.5, -0.5, 0.1] ["a", "b", "c"] 2 `shouldBe` [("a", 0.5), ("b", -0.5)]
```

## 13.10 Boston を前処理して突き合わせる

### 第 9 章の標準化をそのまま呼ぶ

前処理は、ダミー変数化・欠損値の補完・標準化の 3 つを並べるだけです。どれも [第 2 章](02-data-preprocessing-and-triangulation.md)・[第 9 章](09-feature-engineering.md) で書いたものをそのまま呼びます。

```haskell
standardizeTable :: Table -> Either String ([Text], [M.Map Text Double])
standardizeTable table = do
  crimes <- traverse (`text` categoryColumn) (tableRows table)
  encoded <- encode table categoryColumn (categories crimes)
  means <- columnMeans (tableRows encoded) (tableColumns encoded)
  filled <- fillMissing (tableRows encoded) (tableColumns encoded) means
  std <- fitStandardizer filled (tableColumns encoded)
  pure (tableColumns encoded, standardizeAll std filled)
```

**第 9 章の `fitStandardizer` をここで書き直さないことが、この章でいちばん大事な判断です。** 同じ標準化が 2 つあると、片方だけ直したときに気づけません。Elixir 版では第 13 章で標準化を重複実装してしまい、あとから直しました。

第 9 章で `statistics` の `sqrt . variance`（n で割る最尤推定）と浮動小数点数として完全に一致することを確かめてあるので、ここでも定義の心配は要りません。

### 列の順はリストで持ち回る

特徴量は `M.Map Text Double` です。**`Map` は鍵の順（辞書順）で並ぶので、CSV に現れた順は保ちません。** 行列にするときは、列名のリストを別に持ち回って、その順で並べます。

```haskell
toMatrix :: [M.Map Text Double] -> [Text] -> Either String [[Double]]
toMatrix = toRows
```

中身は第 9 章の `toRows` をそのまま呼ぶだけです。同じことをする関数を 2 つ作らず、**この章での呼び名だけを与えました**。

架空の 4 件（`CRIME` が 3 種類、`RM` に欠損値が 1 件）で、ダミー変数の列名と標準化の結果を確かめます。

```haskell
    it "欠損値を補完してから各列を平均 0・標準偏差 1 にそろえる" $ do
      let table = expectRight (loadTable (utf8Csv bostonLike))
          (columns, x) = expectRight (standardizeTable table)
          rows = expectRight (toMatrix x columns)
      columns `shouldBe` ["ZN", "RM", "PRICE", "CRIME_low", "CRIME_very_low"]
      closeTo [replicate 5 0.0] [matrixColumnMeans rows] `shouldBe` True
      closeTo [replicate 5 1.0] [map stdDevN (transpose rows)] `shouldBe` True
```

テストの CSV は `utf8Csv` で組み立てます。`ByteString` のリテラルに日本語を書くと 1 バイトを 1 文字として詰められて化けるので、[第 1 章](01-machine-learning-and-first-test.md) から `Text` で書いて境界で符号化する形にしています。この章の架空データは英数字だけですが、同じ書き方をそろえています。

### hmatrix は 15×15 の実データに耐えた

PHP 版が落ちた場所です。15 列の分散共分散行列を、自作の Jacobi 法と `eigSH` の両方に通します。

```haskell
    it "自作の Jacobi 法と hmatrix の eigSH が 15 列の実データで一致する" $ do
      rows <- bostonMatrix
      case rows of
        Nothing -> pendingWith "学習データがありません"
        Just m -> do
          let (ratioGap, componentGap) = expectRight (hmatrixGaps m (width m))
          ratioGap `shouldSatisfy` (< 1.0e-12)
          componentGap `shouldSatisfy` (< 1.0e-9)
```

**通りました。** 実測した差は **寄与率 5.55e-17、主成分 1.41e-14** です。倍精度の丸め誤差の範囲で、**回転を重ねる Jacobi 法と LAPACK の DSYEV が、15 列の実データで同じ答えを返しました**。

PHP 版の MathPHP と何が違ったのかは、実装の方針で説明がつきます。MathPHP の `Eigenvector::eigenvectors()` は、固有値ごとに $A - \lambda I$ を作って掃き出し法で零空間を探す 2 段構えでした。`eigSH`（DSYEV）は三重対角化してから QR 法で分解する 1 本の手続きで、途中で「これは固有値か」を判定する場所がありません。**Jacobi 法と DSYEV は、どちらも 2 段構えを通らない実装です。**

実データのテストは、学習データが無ければ `pendingWith` でスキップします（[第 1 章](01-machine-learning-and-first-test.md) から使っている仕組みです）。`Dataset.exists` が偽なら学習データを 1 行も読まないので、リポジトリにデータを置かない環境でもテストは緑のままです。

### 実行して結果を表示する

```text
データ件数: 100, 列数: 15
寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581
累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）
第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328
第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405
hmatrix の eigSH との差: 寄与率 5.55e-17, 主成分 1.41e-14
```

`report` は標準出力に書かずに `Either String String` を返します（第 1 章から一貫しています）。テストは戻り値の文字列を比べるだけで、出力を横取りするヘルパーは要りません。差の行は実装によって動くので、テストでは先頭 5 行だけを比べています。

### ほかの言語版との一致

**寄与率も、必要な主成分の数も、係数の 3 桁目まで [Java 版](../java/13-principal-component-analysis.md)・[Scala 版](../scala/13-principal-component-analysis.md)・[Clojure 版](../clojure/13-principal-component-analysis.md)・[Elixir 版](../elixir/13-principal-component-analysis.md)・[PHP 版](../php/13-principal-component-analysis.md) と一致しました。** この章は分割も乱数も使わず、計算は中心化・積・固有値分解だけです。前処理（ダミー変数の作り方・平均値での補完・件数で割る標準偏差）も、符号の規則も同じにしたので、**固有値分解の実装だけが違っても同じ値になりました**。

これで 4 通りの固有値分解が同じ答えを返したことになります。

| 版 | 固有値分解 |
|----|----------|
| Java・Scala・Clojure | Tribuo から借りた固有値分解 |
| Elixir | `Nx.LinAlg.eigh` |
| PHP | 自作の Jacobi 法 |
| **Haskell** | **自作の Jacobi 法と hmatrix の `eigSH`（LAPACK の DSYEV）の 2 通り** |

[第 10 章](10-logistic-regression-and-ensemble.md) では、`Data.Map` が鍵の順で畳むために特徴量の重要度が Elixir 版と一致し、挿入順の PHP 版・Clojure 版と 1 ulp ずれました。**この章ではそうしたずれが出ていません。** 足し算の順序が問題になるのは「多数の小さい値を足し合わせる」計算で、ここでの合計はたかだか 15 個だからです。表示する 4 桁にはもちろん、比較に使った 1e-12 の桁にも届きませんでした。

### 結果を読む

- **第 1 主成分だけで 4 割を説明する**: 15 列のばらつきのうち 41.1% を、1 本の軸で説明できます
- **15 列を 6 本の軸に要約できる**: 累積寄与率 0.8 を目安にすると、6 つの主成分で全体の 84.3% を説明できます
- **第 1 主成分は「産業化・都市化」の軸と読める**: 非小売業の土地の割合（INDUS）、窒素酸化物の濃度（NOX）、固定資産税率（TAX）が同じ向きに強く効いています
- **第 2 主成分は「住環境のよさ」の軸と読める**: 住宅価格（PRICE）、犯罪率が低い地区（CRIME_low）、部屋数（RM）が同じ向きに効いています

主成分の「意味」は計算で決まるものではなく、係数を見た人間の解釈です。符号の向きも `normalizeSigns` の規則で決めたものなので、「値が大きいほど都市化している」のか「小さいほど」なのかは、係数の符号とあわせて読む必要があります。

## 13.11 Notebook による探索と可視化

Haskell 版では Notebook と可視化の節を設けません。主成分の散布図、累積寄与率の折れ線（スクリープロット）、ローディングのヒートマップは、[Python 版の第 13 章](../python/13-principal-component-analysis.md) と [Kotlin 版の第 13 章](../kotlin/13-principal-component-analysis.md) の「Notebook による探索と可視化」の節を参照してください。

## 13.12 何が置き換えられて、何が置き換えられないのか

| 手順 | 自作 | hmatrix で代われるか |
|------|------|------------------|
| 中心化 | `center` | 無い |
| 分散共分散行列 | `covarianceMatrix` | **代われる（`meanCov`）** |
| 固有値分解 | `jacobiEigen` | **代われる（`eigSH`）** |
| 符号をそろえる | `normalizeSigns` | 規則が無い |
| 射影 | `transform` | 行列積は代われるが、行列にする手間のほうが大きい |
| 寄与率 | `fitWith` の中 | 無い |
| 主成分の数の目安 | `componentsNeeded` | 無い |
| 係数の大きい列 | `topLoadings` | 無い |

置き換えられるのは固有値分解だけです。これは **[ADR 014](../../../adr/014-haskell-ml-libraries.md) で見込んだとおり** で、「hmatrix は線形代数のライブラリであって、機械学習のライブラリではない」ことがそのまま表に出ています。

その意味で、この章の hmatrix は **scikit-learn や Scholar の `PCA` の代わりではなく、NumPy の `eigh` の代わり** です。[Python 版](../python/13-principal-component-analysis.md) が NumPy で自作してから scikit-learn と比べたのに対し、Haskell 版には比べる相手の `PCA` がありません。**そのぶん「自作どうしを比べる」——Jacobi 法と LAPACK という、方針の違う 2 つの固有値分解を比べる——構成にしました。**

**「結果を読むための道具」（累積寄与率がしきい値に届く主成分の数、係数の大きい列）はどのライブラリにも無い**、というのは scikit-learn でも Scholar でも同じでした。これは自分で書く部分です。

## 13.13 品質チェック

`nix develop .#haskell` の中で、整形の検査・静的解析・テスト・カバレッジをまとめて実行します。

```console
$ npx gulp apps:check:haskell
```

この章で追加した依存はありません。hmatrix（第 7 章から）と `statistics`（第 9 章から）をそのまま使っています。

`-Wall -Werror` に 2 回止められました。どちらも「うっかり」を捕まえるものです。

- **`-Wname-shadowing`**: 行列を `Map` に詰める内包表記の束縛 `v` が、外側の固有ベクトルの `v` を隠していました。数値は正しく出ていたので、テストでは見つかりません
- **`-Wunused-imports`**: GHC 9.10 では `foldl'` が `Prelude` にあるので、`Data.List (foldl')` が余計になります。第 9 章と同じ指摘で、**この連載の Haskell 版では毎章出ます**

テストでは、名前の衝突にも 1 度止められました。`fit`（主成分分析の学習）が `Test.Hspec` の `fit`（focused `it`）と衝突します。

```haskell
-- 'fit'（主成分分析）が Test.Hspec の fit（focused it）と衝突するので隠す。
import Test.Hspec hiding (fit)
```

[第 3 章](03-decision-tree-and-obvious-implementation.md) でまったく同じ衝突に当たっています。**機械学習の `fit` は、この連載では章をまたいで出てくる名前なので、これからも毎回出ます。**

## 13.14 まとめ

この章では、主成分分析を Haskell の TDD で自作し、固有値分解を自作の Jacobi 法と hmatrix の `eigSH` の 2 通りで突き合わせました。

1. **hmatrix は 15×15 の実データに耐えた** — 寄与率の差は 5.55e-17、主成分の差は 1.41e-14。PHP 版で MathPHP が落ちた大きさの行列を、`eigSH` は問題なく分解した
2. **それでも Jacobi 法を自作した** — 回転を重ねるだけで固有値と固有ベクトルが同時に出るので、「固有値を求めてから固有ベクトルを探す」2 段構えを通らない。そして自作があるからこそ、ライブラリの答えを比べる相手ができた
3. **固有値分解を引数にした** — `fit = fitWith jacobiEigen` の 1 行で、同じ手順を 2 つの実装で回せる。前後の手順が共有されるので、「固有値分解以外は同じ」ことが型で保証される
4. **ほかの言語版と数値が一致した** — 寄与率 PC1 0.4110、必要な主成分 6 個、累積寄与率 0.8427。Tribuo・Nx・自作の Jacobi 法を含む 4 通りの固有値分解が、同じ前処理と同じ符号の規則のもとで同じ答えを返した

Haskell 版ならではの学びもありました。

- **`m /= transpose m` で対称性が確かめられる** — 行列を専用の型ではなくリストのリストで持つと、`Eq` がそのまま使える。`trustSym` が黙って上三角だけを見る落とし穴を、1 行で塞げた
- **書き換えを「変更の集合」として書く** — 命令型の `$a[$k][$p] = ...` の代わりに、更新する成分の `Map` を作って左結合の `M.union` で重ねる。触った成分がリストとして目に見えるので、更新の対称性を見落としにくい
- **再帰の引数が繰り返しの上限になる** — `sweep 0 _ a v = (a, v)` の 1 行が、ループカウンタと `break` の代わりになる
- **`case` のパターンがテストの主張になる** — `[[first], [middle], [last']]` と書けば「1 列 3 行である」ことまで同時に確かめられる。`-Wincomplete-uni-patterns` に書かされた枝が、そのままエラーメッセージになった
- **`sortBy` の安定性には寄りかかってよい** — 第 3 章の `maximumBy`（同値なら後ろ）とは逆で、こちらは仕様で決まっている。それでもテストには書く
- **`scanl1` と遅延評価で「見つけたら抜ける」が書ける** — 累積和の全体を作ってから探すように見えて、実際には最初に見つかったところで止まる

次の章では、同じ教師なし学習でも「行をグループに分ける」側、K-means によるクラスタリングを実装します。**Haskell には K-means のライブラリが無いので、この章のように突き合わせる相手はいません。** 自作したものがそのまま最終実装になります。

Haskell 版のほかの章は [Haskell 版のトップ](index.md) から辿れます。
