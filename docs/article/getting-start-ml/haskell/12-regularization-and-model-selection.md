---
type: Article
title: "第 12 章: 正則化とモデル選択"
description: "リッジ回帰とラッソ回帰を Haskell の TDD で自作し、リッジの正規方程式だけを hmatrix と突き合わせる。座標降下法を再代入なしで書くとどうなるか、shouldBe と近似比較の使い分けが 2 つの正則化の違いそのものになること、第 2 章・第 7 章・第 9 章の部品だけで前処理が済むことを扱う。"
tags: [article,getting-start-ml,haskell]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 12 章: 正則化とモデル選択

## 12.1 はじめに

第 7 章の線形回帰は、訓練データの誤差だけを小さくしました。特徴量が増えて互いに似てくると、係数が極端に大きくなり、訓練データにはよく当たるのに未知のデータには当たらない **過学習** が起きます。

この章では、係数の大きさそのものに罰則を加える **正則化** を扱います。

- **リッジ回帰** — 係数の 2 乗の合計に罰則をかける。係数は 0 に近づくが 0 にはならない
- **ラッソ回帰** — 係数の絶対値の合計に罰則をかける。**係数がちょうど 0 になる** ので、要らない特徴量が分かる

罰則の強さ `alpha` は自分で決める値（ハイパーパラメータ）なので、**検証データ** を用意して選びます。

[Python 版の第 12 章](../python/12-regularization-and-model-selection.md) と同じ TODO リストで進め、[Java 版](../java/12-regularization-and-model-selection.md)・[Clojure 版](../clojure/12-regularization-and-model-selection.md)・[Elixir 版](../elixir/12-regularization-and-model-selection.md)・[PHP 版](../php/12-regularization-and-model-selection.md) と数値を対比します。注目してほしいのは次の 4 点です。

- **リッジ回帰は hmatrix と突き合わせられます。** 第 7 章で自作のガウス・ジョルダン法と hmatrix の `<\>` を比べたのと同じ形です。[ADR 014](../../../adr/014-haskell-ml-libraries.md) が「突き合わせられるのは線形代数の層だけ」と決めたとおり、**この章で比べられるのは正規方程式を解くところまで** です
- **ラッソ回帰には相手がいません。** hmatrix は線形代数の道具でしかなく、座標降下法をしてくれるわけではありません。第 11 章の ROC 曲線と同じく、自作がそのまま最終実装です
- **座標降下法を再代入なしで書きます。** PHP 版は「再代入を許す言語では、反復のコードが数式に近づく」と書きました。Haskell では書き換わる変数が畳み込みの引数になります。第 2 章の Fisher-Yates と同じ形です
- **この章で新しく書いた前処理は、外れ値の除去だけです。** 標準化も 2 次の項も 3 分割も、第 2 章・第 9 章の関数をそのまま呼ぶだけで済みます

## 12.2 過学習と正則化

### 係数が大きくなりすぎる

特徴量どうしが強く相関していると、「片方を大きく足して、もう片方を大きく引く」ような解でも訓練データには当てはまってしまいます。係数の絶対値が跳ね上がり、入力がわずかに変わるだけで予測が大きく動くようになります。

### 係数の大きさに罰則を加える

最小二乗法が最小にするのは残差の 2 乗和だけです。

$$\lVert t - Xw \rVert^2$$

正則化はここに項を足します。

- リッジ回帰: $\lVert t - Xw \rVert^2 + \alpha \lVert w \rVert^2$
- ラッソ回帰: $\frac{1}{2} \lVert t - Xw \rVert^2 + \alpha \lVert w \rVert_1$

`alpha` が 0 なら最小二乗法に戻ります。大きくするほど係数は 0 のほうへ押されます。

### リッジ回帰の解き方

リッジ回帰は微分して 0 と置くだけで解けます。

$$(X^\top X + \alpha I) w = X^\top t$$

第 7 章の正規方程式の左辺に、対角の `alpha` を足しただけです。**解くところは第 7 章の `solveLinearSystem` がそのまま使えます。**

### ラッソ回帰の解き方

L1 の罰則は原点で折れているので微分できません。行列を解いて終わりにはできず、**係数を 1 つずつ順に動かす** 座標降下法を使います。1 つの係数を動かすときは、ほかを固定して最適な値を求め、そこに **軟しきい値作用素** をかけて 0 に寄せます。

## 12.3 題材とデータ

### Boston.csv

ボストンの住宅価格のデータ（100 件）を使います。第 9 章でも使ったものです。この章で使う列は 3 つに絞ります。

| 列 | 意味 |
| :--- | :--- |
| `RM` | 1 戸あたりの平均部屋数 |
| `PTRATIO` | 生徒と教師の比率 |
| `LSTAT` | 低所得者層の割合 |
| `PRICE` | 住宅価格（正解） |

### 過学習が起きやすい状況を作る

3 列だけでは過学習しません。そこで、標準化したあとに **2 次の項** を作ります。3 列から 2 乗と交互作用を足すと 9 列になります。2 次の項は元の列と強く相関するので、正則化の効き目が見える状況になります。

### 訓練・検証・テストの 3 つに分ける

`alpha` を選ぶのにテストデータを使ってしまうと、「テストデータに合わせて選んだ」ことになり、テストが未知のデータの代わりになりません。そこで 3 つに分けます。

- **訓練データ** — モデルを学習する
- **検証データ** — `alpha` を選ぶ
- **テストデータ** — 選び終わったあと、一度だけ測る

## 12.4 TODO リストの作成

**TODO リスト**:

- [ ] リッジ回帰を自作する
- [ ] ラッソ回帰を座標降下法で自作する
- [ ] 係数の絶対値の合計と、0 になった特徴量名を求める
- [ ] alpha ごとの実験結果を記録して、検証データで選ぶ
- [ ] z スコアで外れ値を除く
- [ ] 標準化してから 2 次の項を作り、3 つに分ける
- [ ] hmatrix のリッジ回帰と突き合わせる
- [ ] 実データで線形回帰とリッジ回帰を比べる

## 12.5 リッジ回帰を自作する

### Red: 最小二乗法と同じになるテスト

```haskell
    it "alpha が零なら最小二乗法と同じ解になる" $ do
      mine <- expectRight (ridgeFit squareX squareT ["a", "b"] 0.0)
      least <- expectRight (C7.fit squareX squareT ["a", "b"])
      modelIntercept mine `shouldSatisfy` near (modelIntercept least)
      modelCoefficients mine `shouldSatisfy` and . zipWith near (modelCoefficients least)
```

`alpha = 0` なら罰則が消えるので、第 7 章の `fit` と同じ答えになるはずです。**すでに動いているものを答えに使える** ので、期待値を手で計算する必要がありません。

### Green: 中心化して解く

```haskell
ridgeFit :: [M.Map Text Double] -> [Double] -> [Text] -> Double -> Either String C7.LinearModel
ridgeFit x t columns alpha = do
  (means, tMean, centered) <- centeredProblem x t columns
  let transposed = transpose centered
      normal = penalize alpha [[dot left right | right <- transposed] | left <- transposed]
      rhs = [dot left (map (subtract tMean) t) | left <- transposed]
  weights <- C7.solveLinearSystem normal rhs
  C7.model (tMean - dot means weights) columns weights
```

第 7 章との違いは 2 つだけです。

1. **平均を引いてから解く** — 切片に罰則をかけないため。切片まで 0 に押されると、予測全体が下に引っ張られます
2. **対角に `alpha` を足す** — `penalize` の 1 行

```haskell
penalize :: Double -> [[Double]] -> [[Double]]
penalize alpha matrix =
  [ [if i == j then value + alpha else value | (j, value) <- zip [0 :: Int ..] row]
  | (i, row) <- zip [0 ..] matrix
  ]
```

切片は中心化で外してあるので、罰則は係数だけにかかります。

`[[dot left right | right <- transposed] | left <- transposed]` が $X^\top X$ です。$X^\top$ の行と $X$ の列はどちらも「中心化した X の列」なので、**同じリストを 2 回たどるだけ** で書けます。行列の積を書かずに済むのは、リスト内包表記が二重ループそのものだからです。

### モデルは第 7 章のものを使い回す

戻り値は `C7.LinearModel` です。切片・列名・係数を持つ型で、`C7.predict` も `C7.r2Score` もそのまま使えます。**この章では新しいモデルの型を作っていません。**

`C7.model` は列名と係数の長さが違えばモデルを作れません。第 7 章でそう書いたので、ここで確かめ直す必要がありません。**不正な状態のモデルを作れないようにしておくと、それを使う関数の検査が減ります。**

### 切片には罰則がかからない

性質でも押さえておきます。

```haskell
    it "切片には罰則がかからないので正解の平均は保たれる" $ do
      -- 強く正則化すると係数は 0 に近づき、予測は正解の平均に寄る
      strong <- expectRight (ridgeFit squareX squareT ["a", "b"] 1.0e8)
      y <- expectRight (C7.predict strong squareX)
      let tMean = sum squareT / fromIntegral (length squareT)
      all (\v -> abs (v - tMean) < 1e-6) y `shouldBe` True
```

`alpha` を極端に大きくすると係数は 0 に押し切られ、残るのは切片だけです。**その切片が正解の平均になる** ことが、中心化が効いている証拠になります。中心化を忘れていたら、予測は 0 に寄って落ちます。

## 12.6 ラッソ回帰を座標降下法で自作する

### 軟しきい値作用素

```haskell
softThreshold :: Double -> Double -> Double
softThreshold value threshold
  | value > threshold = value - threshold
  | value < negate threshold = value + threshold
  | otherwise = 0.0
```

しきい値の外なら 0 のほうへ縮め、中なら 0 にします。**ガードで 3 つに分けると、定義がそのまま式になります。** Elixir 版が 3 つの関数節で書いたのと同じ形です。

### 書き換わる変数が、畳み込みの引数になる

PHP 版はこの節を「再代入を許す言語では、反復のコードが数式に近づく」と書きました。Haskell には再代入がありません。係数は `Data.Map Int Double` に持ち、畳み込みで作り直します。

```haskell
sweep alpha (weights, residual, delta) (index, col, norm)
  | norm == 0.0 = (weights, residual, delta)
  | otherwise = (M.insert index weight weights, removed, max delta (abs (weight - old)))
 where
  old = M.findWithDefault 0.0 index weights
  -- いったん列の寄与を残差に戻してから、残差との相関で係数を決め直す
  restored = zipWith (\r value -> r + old * value) residual col
  weight = softThreshold (dot col restored) alpha / norm
  removed = zipWith (\r value -> r - weight * value) restored col
```

「書き換わるもの」が 3 つ（係数・残差・いちばん大きな動き）あるので、組にして持ち回ります。PHP 版の `$weights[$i] = ...` が `M.insert index weight weights` に、`$residuals` の書き換えが `removed` という別の値に変わっただけで、**やっていることは 1 行ずつ対応します**。

第 2 章の Fisher-Yates で「不変のリストでは添字の書き換えができないので `Map` に写してから交換する」と書いたのと同じ形です。**添字で書き換える処理は、Haskell では毎回この形になります。**

繰り返しは再帰です。

```haskell
  descend _ 0 weights _ = weights
  descend entries steps weights residual =
    let (moved, nextResidual, delta) = foldl (sweep alpha) (weights, residual, 0.0) entries
     in if delta < tolerance
          then moved
          else descend entries (steps - 1) moved nextResidual
```

「上限に達した」場合をパターンの第 1 節で書くので、**無限ループにならないことがひと目で分かります**。

`M.elems` は鍵の順に返すので、係数は列の順に並びます。第 2 章から続けている「`Map` はキーの順で並ぶ」という性質を、ここでは **利用する側** に回しました。

### Red: 0 になることを確かめる

```haskell
    it "alpha が大きければ係数はちょうど零になる" $ do
      mine <- expectRight (lassoFit squareX squareT ["a", "b"] 1000.0)
      -- リッジ回帰は 0 に近づくだけだが、ラッソ回帰は 0 そのものにする
      modelCoefficients mine `shouldBe` [0.0, 0.0]
```

リッジ回帰の同じテストは `near`（差が 1e-9 未満）でした。**`shouldBe` と近似比較の使い分けが、2 つの正則化の違いそのもの** になっています。リッジ回帰を `shouldBe [0.0, 0.0]` で書いたら落ちますし、ラッソ回帰を近似で書いたらこの章の値打ちが消えます。

もう片方の端も押さえます。

```haskell
    it "alpha が十分小さければ最小二乗法に近づく" $ do
      mine <- expectRight (lassoFit squareX squareT ["a", "b"] 1.0e-8)
      least <- expectRight (C7.fit squareX squareT ["a", "b"])
      modelCoefficients mine `shouldSatisfy` and . zipWith roughly (modelCoefficients least)
```

**ライブラリと照らせないラッソ回帰を、両側から挟んでいます。** 0 に押し切れることと、罰則を消せば最小二乗法に戻ることの 2 本です。

分散 0 の列（`norm == 0.0`）の道も、消さずにテストしました。

```haskell
    it "値がすべて同じ列があっても落ちない" $ do
      ...
      -- 分散 0 の列は係数を動かしようがないので 0 のまま
      take 1 (modelCoefficients mine) `shouldBe` [0.0]
```

実データでは起きにくい道ですが、値がすべて同じ列は十分ありえますし、そのとき 0 で割って `NaN` を返すより、係数 0 で返すほうが親切です。

## 12.7 実験結果を記録して選ぶ

```haskell
data Experiment = Experiment
  { experimentAlpha :: Double
  , experimentTrainScore :: Double
  , experimentValidationScore :: Double
  , experimentCoefficientAbsSum :: Double
  }
  deriving (Eq, Show)
```

選ぶところは「検証データの決定係数が最大」です。

```haskell
bestExperiment :: [Experiment] -> Either String Experiment
bestExperiment [] = Left "実験結果が 1 件もありません"
bestExperiment (first : rest) = Right (foldl better first rest)
 where
  better current candidate
    | experimentValidationScore candidate > experimentValidationScore current = candidate
    | otherwise = current
```

`Data.List.maximumBy` を使っていません。**同値なら後ろを返す** からで、第 3 章の多数決で一度刺された性質です。ここは「同じ値なら先の実験（小さい `alpha`）」にしたいので、`>` で畳み込みます。

```haskell
    it "同じ値なら先の実験を選ぶ" $
      fmap experimentAlpha
        ( bestExperiment
            [ Experiment 0.0 0.9 0.5 10.0
            , Experiment 1.0 0.8 0.7 8.0
            , Experiment 10.0 0.7 0.7 5.0
            ]
        )
        `shouldBe` Right 1.0
```

**「同値のときどちらを選ぶか」をテストに書いておくと、あとで `maximumBy` に書き換えたくなったときに止まります。** PHP 版は「関数が無いことが、決めごとを表に出させた」と書きました。Haskell では **関数はあるが向きが逆** だったので、やはり自分で書くことになりました。

空のリストの場合をパターンの第 1 節で書くので、「1 件も無いときどうするか」を決めないと関数が完成しません。

## 12.8 0 になった係数の特徴量名を返す

ラッソ回帰の値打ちは「どの特徴量が要らなかったか」が分かることです。

```haskell
zeroCoefficientNames :: C7.LinearModel -> [Text]
zeroCoefficientNames m =
  [ name
  | (name, value) <- zip (C7.modelColumns m) (C7.modelCoefficients m)
  , value == 0.0
  ]
```

`LinearModel` が列名と係数を同じ順のリストで持っているので、`zip` で対応が付きます。Elixir 版は係数のリストと名前のリストを別々に受け取り、長さが違えば失敗する検査を書きました。Haskell 版では **`C7.model` がすでに長さを検査している**（第 7 章で書きました）ので、ここでは不要です。

浮動小数点を `==` で比べるのは普通なら赤信号ですが、ここは **`softThreshold` が `0.0` そのものを返す** ことが分かっています。「0 に近い」ではなく「0 である」を見たいので、これが正しい比較です。

## 12.9 最小限の前処理

### 標準化してから 2 次の項を作る

```haskell
  build scaler rows = C9.expand (C9.standardizeAll scaler rows) featureColumns
```

- `C9.Standardizer` — 第 9 章で書いた標準化。**件数 n で割る母標準偏差** を使う
- `C9.expand` — 第 9 章で書いた 2 次の項の展開。名前の付け方（`RM^2`・`RM LSTAT`）も順序も scikit-learn の `PolynomialFeatures` に合わせてある

**新しく書いた前処理はありません。** 第 9 章の 2 つの部品に列の並びを渡すだけで、9 列の特徴量ができます。

```text
RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2
```

標準化の平均と標準偏差は **訓練データだけから** 求め、検証データにもテストデータにも同じ値を当てます。

### 3 つに分けるのに、第 2 章の関数を 2 回呼ぶ

```haskell
  outer <- C2.splitTrainTest x labels outerSize s
  inner <- C2.splitTrainTest (C2.xTrain outer) (C2.tTrain outer) innerSize s
```

外側で訓練用とテスト用に分け、**外側の訓練用をもう一度分けます**。内側の「テストデータ」が検証データになるので、`BostonData` に詰めるときに名前を付け替えます。第 2 章の関数を作り直さずに済みました。

3 つ分の特徴量と正解、特徴量名、外れ値を除いた件数をまとめて持つ型を新しく作ります。

```haskell
data BostonData = BostonData
  { bdXTrain :: [M.Map Text Double]
  , bdTTrain :: [Double]
  , bdXValid :: [M.Map Text Double]
  , bdTValid :: [Double]
  , bdXTest :: [M.Map Text Double]
  , bdTTest :: [Double]
  , bdFeatureNames :: [Text]
  , bdKept :: Int
  }
  deriving (Eq, Show)
```

第 9 章の `BostonSplit` は 2 分割の結果でした。**型を増やすか、既存の型を広げるか** の判断です。ここは増やしました。広げると第 9 章のコードに「検証データがあるかもしれない」分岐が入り込みます。`Maybe` にすれば型では表せますが、第 9 章には常に `Nothing` の欄が増えるだけです。

### z スコアで外れ値を除く

第 9 章は四分位範囲（IQR）で外れ値を判定しました。この章は **z スコア** です。標準偏差は **件数 n − 1 で割る標本標準偏差** を使います（標準化の n とは違うので注意が要ります）。

```haskell
         in Right (column, m, sqrt (sum [(v - m) ** 2 | v <- values] / fromIntegral (length values - 1)))
```

テストを書いたとき、期待どおりに動かずに 1 回 Red になりました。

```haskell
      -- 件数が n のとき z スコアの上限は (n - 1) / √n なので、
      -- 10 件では 2.85 までしか届かず 3.0 のしきい値に当たらない
      let csv = utf8Csv ("v\n" <> T.intercalate "\n" (replicate 19 "1") <> "\n1000\n")
```

9 個の 1 と 1 個の 1000 を並べて「1000 が外れ値になるはず」と書いたら、1 行も減りませんでした。調べると、**n 件のデータの z スコアには (n − 1) / √n という上限があります**。10 件では 2.846 が最大で、どんなに極端な値を混ぜてもしきい値 3.0 には届きません。20 件にして通しました。

実装の誤りではなく **テストの前提の誤り** でしたが、この上限を知らないまま実データに使うと「しきい値 3.0 では 1 件も除かれない」ことの理由が分からなくなります。届かないほうの場合もテストに残しました。

```haskell
    it "件数が少ないと上限に届かず一件も除かれない" $ do
      let csv = utf8Csv ("v\n" <> T.intercalate "\n" (replicate 9 "1") <> "\n1000\n")
      table <- expectRight (loadTable csv >>= \t -> removeOutliers t ["v"] 3.0)
      length (tableRows table) `shouldBe` 10
```

空欄を弾くテストでも 1 回 Red になりました。`"v\n1\n\n2\n"` と書いて「2 行目が空欄だから `Left` が返るはず」としたのに、`Right` が返ったのです。第 1 章の `Csv.parseRows` は **全部の欄が空の行を読み込みの時点で落とします**。1 列しかない CSV では「空欄の行」と「空の行」が同じものになってしまいます。列を 2 つにして、片方に値を残すと通りました。

```haskell
      -- 全欄が空の行は読み込みの時点で落ちるので、別の列に値を入れて空欄を残す
      let csv = utf8Csv "v,w\n1,1\n,2\n2,3\n"
```

**第 1 章で決めた「空の行は落とす」が、11 章あとのテストの書き方を決めた** 形です。落とす判断自体は正しい（CSV の末尾の改行が 1 行に見えるのを防ぐため）ので、テストのほうを直しました。

## 12.10 hmatrix と突き合わせる

### 突き合わせられるのは、正規方程式を解くところまで

第 7 章と同じ形で、**同じ正規方程式を hmatrix に解かせます**。

```haskell
hmatrixRidgeFit x t columns alpha = do
  (means, tMean, centered) <- centeredProblem x t columns
  let matrix = LA.fromLists centered
      transposed = LA.tr matrix
      penalty = LA.scale alpha (LA.ident (length means))
      normal = (transposed LA.<> matrix) + penalty
      rhs = transposed LA.#> LA.fromList (map (subtract tMean) t)
      weights = LA.toList (normal LA.<\> rhs)
  C7.model (tMean - dot means weights) columns weights
```

中心化も罰則の足し方も自作とまったく同じで、**違うのは「解き方」だけ** です。自作はガウス・ジョルダン法（部分ピボット選択つき）、hmatrix は LAPACK の最小二乗の routine です。

`LA.ident` が単位行列、`LA.scale` がスカラー倍です。行列どうしの足し算は `+` がそのまま使えます（hmatrix の `Matrix` は `Num` のインスタンスです）。

```haskell
    it "自作のガウス・ジョルダン法と hmatrix の解が一致する" $ do
      mine <- expectRight (ridgeFit squareX squareT ["a", "b"] 1.0)
      theirs <- expectRight (hmatrixRidgeFit squareX squareT ["a", "b"] 1.0)
      modelIntercept theirs `shouldSatisfy` near (modelIntercept mine)
      modelCoefficients theirs `shouldSatisfy` and . zipWith near (modelCoefficients mine)
```

実データの 9 列でも確かめます。

```haskell
    it "実データでも自作と hmatrix のリッジ回帰が一致する" $ do
      ...
          maximum (zipWith (\a b -> abs (a - b)) (modelCoefficients mine) (modelCoefficients theirs))
            `shouldSatisfy` (< 1e-9)
```

実際の差は **8.0e-15** でした。第 7 章の結果（自作と hmatrix が残差平方和まで一致）と同じで、`alpha` を足しても条件数が悪くなるどころか良くなるので、むしろ第 7 章より安全な計算になっています。

[Elixir 版](../elixir/12-regularization-and-model-selection.md) は Scholar のリッジ回帰と 5e-6 しか合いませんでした（Scholar の既定が特異値分解だったため）。[PHP 版](../php/12-regularization-and-model-selection.md) は Rubix ML と 1e-13 まで合いました。**Haskell 版は「同じ式を別の解法に渡す」形なので、ライブラリの設計に左右されません。** 比べているのは自分の書いた式そのものだからです。

### ラッソ回帰には相手がいない

hmatrix にあるのは行列の演算・分解・連立方程式の求解で、座標降下法はありません。**12.6 節で書いたものがそのまま最終実装** です。支えは 3 つです。

1. 両側から挟むテスト 2 本（12.6 節）
2. 分散 0 の列など、例外の道のテスト
3. **ほかの言語版との間接的な突き合わせ** — 0 になった 3 列が一致すること（12.11 節）

### 正則化の強さの尺度をそろえる

自作の目的関数は件数で割りません。件数で割る実装（Java 版・Clojure 版が使う Tribuo の `ElasticNetCDTrainer`）の `alpha = 0.5` は、訓練データ 47 件では `0.5 × 47 = 23.5` にあたります。

```haskell
lassoAlpha :: Double
lassoAlpha = 23.5
```

この読み替えがあって初めて、12.11 節の「0 になった 3 列」が一致します。**尺度をそろえないと、間接的な突き合わせも成り立ちません。**

## 12.11 実データで比べる

### 結果を表示する

```console
$ cabal repl
ghci> GettingStartedMl.Chapter12.run >>= either putStrLn putStr
データ件数: 98（外れ値 2 件を除外）
訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件
特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2
alpha  訓練 R²  検証 R²  係数の絶対値の合計
  0.0  0.8827  0.7272  14.187
  0.1  0.8827  0.7274  14.104
  1.0  0.8823  0.7288  13.594
 10.0  0.8681  0.7349  11.573
100.0  0.6583  0.5985  5.684
検証データで選んだ alpha: 10.0
テストデータの決定係数: 線形回帰 0.5224, リッジ回帰 0.6243
hmatrix のリッジ回帰との係数の最大の差: 8.0e-15
ラッソ回帰（alpha=23.5）で係数が 0 になった特徴量: PTRATIO^2, PTRATIO LSTAT, LSTAT^2
```

### 結果を読む

- `alpha` を大きくするほど、係数の絶対値の合計は 14.187 から 5.684 へ小さくなりました。訓練データの決定係数は下がり続けます
- 検証データの決定係数は `alpha = 10.0` で最も高く（0.7349）、`100.0` では訓練・検証とも大きく下がりました。正則化が強すぎて学習不足になっています
- 検証データで選んだ `alpha = 10.0` のリッジ回帰は、テストデータの決定係数が 0.6243 で、線形回帰の 0.5224 を上回りました。**訓練データでは線形回帰のほうが高い（0.8827）のに、未知のデータでは正則化したモデルのほうがよく当たっています**
- ラッソ回帰は、9 列のうち `PTRATIO^2`・`PTRATIO LSTAT`・`LSTAT^2` の 3 列の係数をちょうど 0 にしました

**リッジ回帰の実行結果の数値は、Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版の同じ節とすべて一致します。** 外れ値の除き方（n − 1 の標準偏差で z スコア）・2 回の分割（`GettingStartedMl.Random` が `java.util.Random` と同じ線形合同法）・標準化（n の標準偏差）・2 次の項の順を、手順の細部までそろえたためです。ラッソ回帰が 0 にした 3 列も、12.10 節の尺度の読み替えを経て一致しました。

Kotlin 版では同じ手順でもリッジ回帰がテストで線形回帰を下回りました。分割の乱数が違い、3 つに入る行が違うからです。100 件ほどのデータでは、**分け方によって結論まで変わりうる** ことを示しています。1 回の分け方に頼らない方法として、第 11 章の交差検証を組み合わせられます。

### 実データのテスト

結論そのものも、性質としてテストに書いておきます。

```haskell
    it "検証データで選んだリッジ回帰はテストデータで線形回帰を上回る" $ do
      ...
          experiments <- expectRight (runRidgeExperiments d alphas)
          best <- expectRight (bestExperiment experiments)
          experimentAlpha best `shouldBe` 10.0
          linear <- expectRight (testScore d 0.0)
          ridge <- expectRight (testScore d (experimentAlpha best))
          ridge `shouldSatisfy` (> linear)
```

0.6243 という数字ではなく、**「選んだリッジ回帰が線形回帰を上回る」という関係** を確かめています。表示の数字は別のテストで固定してあるので、二重に書く必要はありません。

裏返しの性質も 1 本書きました。

```haskell
    it "訓練データでは線形回帰がリッジ回帰を上回る" $ do
```

**過学習とは「訓練でよくてテストで悪い」ことだ、という定義そのもの** が、2 本のテストで言葉になりました。

ラッソ回帰が 0 にした 3 列も `shouldBe` で固定しました。

```haskell
          zeroCoefficientNames lasso
            `shouldBe` ["PTRATIO^2", "PTRATIO LSTAT", "LSTAT^2"]
```

これが 12.10 節で述べた **間接的な突き合わせ** の実体です。このテストが緑である限り、自作のラッソ回帰は Tribuo と同じ答えを出し続けます。

## 12.12 品質チェック

```console
$ npx gulp apps:check:haskell
```

fourmolu → hlint → `cabal test` → カバレッジの順に走ります。

| ファイル | 式のカバレッジ |
| :--- | :--- |
| `src/GettingStartedMl/Chapter12.hs` | 95%（691/723） |

残りは `run`（`IO`）と、`removeOutliers` の「2 件未満なら標準偏差を求められない」道です。後者は実データでは起きませんが、12.9 節で見たとおり件数と z スコアの関係には落とし穴があるので、**消さずに残しました**。

**hlint の指摘も `-Wall -Werror` の警告も、1 つも出ませんでした。** この章は 2 つの理由でそうなります。

1. **新しく書いた関数が少ない** — 標準化も 2 次の項も 3 分割も予測も決定係数も、第 2 章・第 7 章・第 9 章の関数を呼ぶだけです。指摘される行がそもそも無い
2. **場合分けがパターンで書かれている** — `bestExperiment` の空リスト、`softThreshold` の 3 つのガード、`descend` の繰り返しの上限。どれも**書き切らないと関数が完成しない**形で、あとから hlint に指摘される種類の書き漏らしになりません

第 11 章も同じ結果でした。**2 章続けて指摘ゼロだったのは、道具が緩いからではなく、書き方の選択肢が先に狭められているから** です。第 1 章から `-Wall -Werror` で走ってきたことの、いちばん分かりやすい見返りだと思います。

fourmolu は型注釈の折り返しを 3 か所で直しました（`hmatrixRidgeFit`・`centeredProblem`・`sweep`）。引数が多い関数を複数行に分けるときの `->` の位置で、`--mode inplace` に任せています。

## 12.13 可視化について

`alpha` と決定係数のグラフ、係数の軌跡（正則化パス）の描き方は [Python 版](../python/12-regularization-and-model-selection.md) と [Kotlin 版](../kotlin/12-regularization-and-model-selection.md) を参照してください。この章では 12.11 節の表が同じ役割を果たしています。

## 12.14 まとめ

この章では、リッジ回帰とラッソ回帰を Haskell の TDD で実装し、検証データでモデルを選びました。

| 作ったもの | 突き合わせた相手 |
| :--- | :--- |
| リッジ回帰 | **hmatrix**（同じ正規方程式を別の解法に渡して 8.0e-15 で一致） |
| ラッソ回帰（座標降下法） | **無し**（両側から挟むテストと、ほかの言語版との間接的な突き合わせ） |
| z スコアによる外れ値の除去 | 無し（性質と上限のテスト） |
| 標準化・2 次の項・3 分割 | 第 2 章・第 9 章の部品をそのまま再利用 |

Haskell 版ならではの学びは 6 つです。

1. **リッジ回帰は第 7 章の正規方程式に 1 項足すだけ** — 中心化して対角に `alpha` を足す。解くところは `solveLinearSystem` をそのまま使えた。`alpha = 0` で最小二乗法に戻ることが最初のテストになった
2. **「同じ式を別の解法に渡す」形の突き合わせは、ライブラリの設計に左右されない** — Elixir 版は Scholar の既定が特異値分解だったために 5e-6 しか合わなかった。Haskell 版は自分で組んだ正規方程式を hmatrix に渡すだけなので、比べているのは解法の違いだけになる
3. **書き換わる変数が、畳み込みの引数になる** — 座標降下法は「係数・残差・いちばん大きな動き」の 3 つを組にして持ち回る形になった。PHP 版の 1 つのループが、`sweep` と `descend` の 2 つの関数に分かれた。**第 2 章の Fisher-Yates とまったく同じ形** で、添字で書き換える処理は毎回こうなる
4. **`shouldBe` と近似比較の使い分けが、2 つの正則化の違い** — リッジは 0 に近づくだけ、ラッソは 0 そのものにする。浮動小数点を `==` で比べてよい珍しい場所で、それは `softThreshold` が `0.0` を返すと決めたからである
5. **`maximumBy` は同値なら後ろを返す** — 第 3 章の多数決で刺されたのと同じ性質。「同じ値なら小さい `alpha`」にしたいので畳み込みを自作した。**関数はあるが向きが逆** だった
6. **新しく書いた前処理は外れ値の除去だけだった** — 標準化も 2 次の項も 3 分割も、第 2 章・第 9 章の関数を呼ぶだけ。**型が合わないところが 1 つも無かった** のは、どの章でも特徴量を `Map Text Double` で表し続けてきたからである

**TODO リスト（この章の完了時点）**:

- [x] リッジ回帰を自作する
- [x] ラッソ回帰を座標降下法で自作する（**hmatrix に無いので自作が最終実装**）
- [x] 係数の絶対値の合計と、0 になった特徴量名を求める
- [x] alpha ごとの実験結果を記録して、検証データで選ぶ
- [x] z スコアで外れ値を除く
- [x] 標準化してから 2 次の項を作り、3 つに分ける
- [x] hmatrix のリッジ回帰と突き合わせる
- [x] 実データで線形回帰とリッジ回帰を比べる

次の章では、特徴量そのものを減らす主成分分析を扱います。hmatrix の固有値分解と突き合わせられる、**この Haskell 版でいちばんライブラリの恩恵を受ける章** になります。

Haskell 版のほかの章は [Haskell 版のトップ](index.md) から辿れます。
