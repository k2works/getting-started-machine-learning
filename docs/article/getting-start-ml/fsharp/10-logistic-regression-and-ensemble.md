---
type: Article
title: "第 10 章: ロジスティック回帰とアンサンブル学習"
description: "ソフトマックスと勾配降下のロジスティック回帰を List.fold で、第 3 章の決定木を再利用したランダムフォレストをシードを受け取る純粋な関数で TDD で自作し、特徴量の重要度を求める。分類器を関数の型で表して、ML.NET の LbfgsMaximumEntropy と FastForest（OneVersusAll）を同じ関数で評価する。"
tags: [article,getting-start-ml,fsharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T05:22:16Z }
---

# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが増えると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。そこで、モデルに共通する形を型で表し、どのモデルも同じ関数で評価できるようにします。最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作します。

[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md)・[Kotlin 版の第 10 章](../kotlin/10-logistic-regression-and-ensemble.md)・[TypeScript 版の第 10 章](../typescript/10-logistic-regression-and-ensemble.md) と同じ TODO リストで進めます。F# 版では、次の 3 点に注目してください。

- 勾配降下法の繰り返しを、書き換える変数を持たずに `List.fold` で書く。学習の設定は、既定値のレコードを `{ defaults with ... }` で写して変える
- ランダムフォレストの乱数は、第 2 章の `shuffle` と同じく **シードを受け取る純粋な関数** に閉じ込める。同じシードなら、同じ森が値として返る
- 他の言語の版は `fit` と `predict` を持つインターフェースで共通化しましたが、F# 版では **関数の型**（訓練データを受け取り、予測する関数を返す関数）で共通化する。第 3 章の決定木も ML.NET の学習器も、アダプターのクラスを書かずに同じ型に収まる

ライブラリとの突き合わせには、ML.NET の `LbfgsMaximumEntropy`（ソフトマックスのロジスティック回帰）と `FastForest`（ランダムフォレスト）を使います。FastForest は 2 クラス分類の学習器なので、第 3 章と同じく `OneVersusAll` で多クラスにします（[ADR 004](../../../adr/004-fsharp-ml-libraries.md)）。最適化の方法や乱数の使い方が自作と違うので、予測の完全一致は求めず、正解率を比べます。

データは第 2 章・第 3 章と同じ iris を使い、第 2 章の `prepareIris` で前処理します。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 値がすべて同じなら確率は均等になる
  - [ ] 値の差が指数の比になる
  - [ ] 大きな値でもあふれない
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 2 種類・3 種類のラベルを予測する
  - [ ] 学習を繰り返すと損失が小さくなる
- [ ] ランダムフォレストを学習して予測する
  - [ ] 多数決で予測を 1 つに決める
  - [ ] ブートストラップ標本を選ぶ
  - [ ] 第 3 章の決定木を再利用する
- [ ] 特徴量の重要度を計算する
  - [ ] 決定木 1 本の重要度
  - [ ] ランダムフォレストの重要度
- [ ] どのモデルも同じ関数で評価する
  - [ ] 第 3 章の決定木を変更せずに共通の型に合わせる
  - [ ] ML.NET の学習器も同じ関数で評価する
- [ ] 実データで ML.NET と正解率を突き合わせる

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和 + 切片」を計算し、それを **ソフトマックス関数** で確率に変換します。

```text
スコア(品種 k) = w(k, がく片長さ) × がく片長さ + … + w(k, 花弁幅) × 花弁幅 + b(k)
確率(品種 k)   = exp(スコア(品種 k)) / Σ exp(スコア(品種 j))
```

多クラスへの広げ方には、品種ごとに「その品種か、それ以外か」の 2 値分類器を作る one-vs-rest と、この章のように 1 つのモデルで全品種の確率を同時に求めるソフトマックス（多項ロジスティック回帰）があります。ソフトマックスを選んだのは、3 品種の確率の合計が必ず 1 になり、確率として解釈しやすいからです。ML.NET の `LbfgsMaximumEntropy` も、同じソフトマックスのモデルです（「最大エントロピー」は多項ロジスティック回帰の別名です）。

### 仮実装

1 サンプル分のスコア（`float[]`）を受け取り、確率の配列を返す関数にします。

```fsharp
// tests/MachineLearning.Tests/Chapter10/LogisticRegressionTest.fs
module MachineLearning.Tests.Chapter10.LogisticRegressionTest

open Xunit
open MachineLearning.Chapter10.LogisticRegression

[<Fact>]
let ``値がすべて同じなら確率は均等になる`` () =
    Assert.Equal<float[]>([| 0.25; 0.25; 0.25; 0.25 |], softmax [| 0.0; 0.0; 0.0; 0.0 |])
```

Kotlin 版では、`DoubleArray` の `equals` が中身ではなく同じ配列かどうかを比べるので、リストに直してから比べていました。xUnit の `Assert.Equal<float[]>` は、配列を要素ごとに比べるので、配列のまま比べられます。

`.fsproj` の `Compile` に、テストのファイルを第 3 章のテストの後ろに加えてビルドします。

```text
error FS0039: 名前空間 'Chapter10' が定義されていません。
error FS0039: 値またはコンストラクター 'softmax' が定義されていません。
```

均等な確率を返す仮実装で Green にします。

```fsharp
// src/MachineLearning/Chapter10/LogisticRegression.fs
module MachineLearning.Chapter10.LogisticRegression

let softmax (z: float[]) : float[] =
    Array.create z.Length (1.0 / float z.Length)
```

`Array.create 大きさ 値` は、同じ値を並べた配列を作ります。本体の `.fsproj` にも、`Chapter10/LogisticRegression.fs` を第 3 章のファイルの後ろに加えます。

### 三角測量

値の差が `log 2` なら、確率の比は 2 倍になるはずです。

```fsharp
[<Fact>]
let ``値の差が指数の比になる`` () =
    let probabilities = softmax [| 0.0; log 2.0 |]

    Assert.Equal(1.0 / 3.0, probabilities[0], 12)
    Assert.Equal(2.0 / 3.0, probabilities[1], 12)
```

F# の `log` は自然対数です。

```text
失敗 MachineLearning.Tests.Chapter10.LogisticRegressionTest.値の差が指数の比になる (20ms)
  Assert.Equal() Failure: Values are not within 12 decimal places
  Expected: 0.33333333333300003 (rounded from 0.33333333333333331)
  Actual:   0.5 (rounded from 0.5)
```

定義どおりに一般化します。

```fsharp
let softmax (z: float[]) : float[] =
    let exps = z |> Array.map exp
    let total = Array.sum exps
    exps |> Array.map (fun value -> value / total)
```

### 大きな値でもあふれない

学習の途中では、スコアが大きな値になることがあります。

```fsharp
[<Fact>]
let ``大きな値でもあふれずに確率を求める`` () =
    Assert.Equal<float[]>([| 0.5; 0.5 |], softmax [| 1000.0; 1000.0 |])
```

```text
失敗 MachineLearning.Tests.Chapter10.LogisticRegressionTest.大きな値でもあふれずに確率を求める (39ms)
  Assert.Equal() Failure: Collections differ
             ↓ (pos 0)
  Expected: [0.5, 0.5]
  Actual:   [NaN, NaN]
             ↑ (pos 0)
```

`exp 1000.0` は `float` で表せる範囲を超えて無限大になり、無限大 ÷ 無限大が `NaN`（非数）になりました。.NET の浮動小数点数の計算も、Kotlin（JVM）と同じく警告も例外も出さずに `NaN` を返します。境界の値のテストが無ければ気づけません。

ソフトマックスは、すべての値から同じ数を引いても結果が変わりません（分子と分母に同じ `exp(-c)` が掛かるため）。そこで最大値を引いてから `exp` を計算します。

```fsharp
/// スコアを確率に変換する。最大値を引いてから exp を計算して、大きな値でもあふれないようにする
let softmax (z: float[]) : float[] =
    let max = Array.max z
    let exps = z |> Array.map (fun value -> exp (value - max))
    let total = Array.sum exps
    exps |> Array.map (fun value -> value / total)
```

```text
テストの実行の概要: 成功!
```

## 10.4 ロジスティック回帰

### 仮実装と三角測量

第 3 章の決定木と同じく、`fit` は学習した **モデルを値として返し**、`predict` はそのモデルを受け取って予測します。学習の設定（学習率と繰り返し回数）はレコードにまとめ、既定値を `defaults` として用意します。

```fsharp
/// 花弁幅だけを特徴量に持つ行のリストを作る
let byPetalWidth (values: float list) : Map<string, float> list =
    values |> List.map (fun value -> Map.ofList [ "花弁幅", value ])

[<Fact>]
let ``1 種類のラベルだけを学習するとそのラベルを予測する`` () =
    let model = fit defaults (byPetalWidth [ 0.1; 0.2 ]) [ "setosa"; "setosa" ]

    Assert.Equal<string list>([ "setosa"; "setosa" ], predict model (byPetalWidth [ 0.15; 0.9 ]))
```

```text
error FS0039: 値またはコンストラクター 'fit' が定義されていません。 次のいずれかの可能性はありませんか:   fst
error FS0039: 値またはコンストラクター 'predict' が定義されていません。
```

最初のラベルを覚えておく仮実装で通します。

```fsharp
/// 学習の設定
type Settings = { LearningRate: float; Epochs: int }

let defaults = { LearningRate = 1.0; Epochs = 5000 }

type LogisticModel<'L> = { Label: 'L }

let fit (settings: Settings) (x: Map<string, float> list) (t: 'L list) : LogisticModel<'L> = { Label = List.head t }

let predict (model: LogisticModel<'L>) (x: Map<string, float> list) : 'L list = x |> List.map (fun _ -> model.Label)
```

2 種類のラベルを境界の左右で予測する例を加えると、仮実装では通りません。

```fsharp
[<Fact>]
let ``2 種類のラベルを境界の左右で予測する`` () =
    let model =
        fit defaults (byPetalWidth [ 0.1; 0.2; 0.8; 0.9 ]) [ "setosa"; "setosa"; "virginica"; "virginica" ]

    Assert.Equal<string list>([ "setosa"; "virginica" ], predict model (byPetalWidth [ 0.15; 0.85 ]))
```

```text
失敗 MachineLearning.Tests.Chapter10.LogisticRegressionTest.2 種類のラベルを境界の左右で予測する (14ms)
  Assert.Equal() Failure: Collections differ
  Expected: ["setosa", "virginica"]
  Actual:   ["setosa", "setosa"]
```

### 勾配降下法で学習する

重みを少しずつ動かして、予測した確率を正解に近づけます。損失（交差エントロピー）を小さくする方向は、「予測した確率 − 正解」から計算できます。正解の品種を 1、それ以外を 0 とした表（one-hot 表現）を作る代わりに、**正解の品種の確率からだけ 1 を引けば**、「確率 − 正解」になります。

モデルのレコードには、特徴量の列名・品種・重み・切片を持たせます。

```fsharp
/// 学習したロジスティック回帰のモデル
type LogisticModel<'L> =
    {
        /// 特徴量の列名（重みの行の順）
        Features: string list
        /// 品種（重みの列の順）
        Classes: 'L list
        /// Weights[特徴量][品種]
        Weights: float[][]
        /// Bias[品種]
        Bias: float[]
    }

/// 行の Map を、列名の順に並べた配列にする
let private toRow (features: string list) (row: Map<string, float>) : float[] =
    features |> List.map (fun feature -> row[feature]) |> List.toArray

/// 品種ごとの「特徴量の重み付きの和 + 切片」
let private scores (weights: float[][]) (bias: float[]) (row: float[]) : float[] =
    bias
    |> Array.mapi (fun k b -> b + (row |> Array.mapi (fun f value -> value * weights[f][k]) |> Array.sum))
```

学習の本体は、1 回分の更新を関数 `step` にし、それを `List.fold` で `Epochs` 回繰り返します。

```fsharp
/// バッチ勾配降下法で重みと切片を学習する
let fit (settings: Settings) (x: Map<string, float> list) (t: 'L list) : LogisticModel<'L> =
    let features =
        match x with
        | [] -> []
        | first :: _ -> first |> Map.keys |> Seq.toList

    let rows = x |> List.map (toRow features) |> List.toArray
    let classes = t |> List.distinct |> List.sort

    let targets =
        t |> List.map (fun label -> List.findIndex ((=) label) classes) |> List.toArray

    let n = float rows.Length

    let step (weights: float[][], bias: float[]) _ =
        // 確率 − 正解（正解の品種だけ 1 を引く）
        let errors =
            rows
            |> Array.mapi (fun i row ->
                softmax (scores weights bias row)
                |> Array.mapi (fun k p -> if k = targets[i] then p - 1.0 else p))

        let gradient f k =
            Array.map2 (fun (row: float[]) (error: float[]) -> row[f] * error[k]) rows errors
            |> Array.sum

        let newWeights =
            weights
            |> Array.mapi (fun f w -> w |> Array.mapi (fun k value -> value - settings.LearningRate * gradient f k / n))

        let newBias =
            bias
            |> Array.mapi (fun k value -> value - settings.LearningRate * (errors |> Array.sumBy (fun error -> error[k])) / n)

        newWeights, newBias

    let initial =
        Array.init features.Length (fun _ -> Array.zeroCreate classes.Length), Array.zeroCreate classes.Length

    let weights, bias = List.fold step initial [ 1 .. settings.Epochs ]

    {
        Features = features
        Classes = classes
        Weights = weights
        Bias = bias
    }
```

- `List.fold 関数 初期値 リスト` は、リストの要素ごとに「今の状態と要素から、次の状態を作る」関数を呼び、最後の状態を返します。ここでは状態が「重みと切片の組」で、リスト `[ 1 .. settings.Epochs ]` は回数を数えるためだけに使います（`step` の 2 つ目の引数 `_` は捨てています）
- `step` は、受け取った重みを書き換えず、`Array.mapi` で **新しい重みの配列** を作って返します。Kotlin 版は `weights[f][k] -= ...` と配列を書き換えましたが、F# 版では書き換える変数が 1 つもありません。1 回の更新で全サンプルの誤差を先に求めてから重みを作るので、Python 版・Kotlin 版と同じ **バッチ勾配降下法** です
- `Array.zeroCreate 大きさ` は、0 で埋めた配列を作ります。要素の型（`float`）は、`initial` が `step` に渡されることから型推論で決まります
- `List.findIndex ((=) label) classes` は、`classes` の中で `label` と等しい要素の位置を返します。`(=)` は等号演算子を関数として取り出したもので、`(=) label` は「`label` と等しいか」を判定する関数になります
- `predict` では確率を計算せず、スコアが最大の品種を選びます。ソフトマックスは大小関係を変えないので、結果は同じです

```fsharp
/// スコアが最大の品種を予測する（ソフトマックスは大小関係を変えないので確率は計算しない）
let predict (model: LogisticModel<'L>) (x: Map<string, float> list) : 'L list =
    x
    |> List.map (fun row ->
        scores model.Weights model.Bias (toRow model.Features row)
        |> Array.indexed
        |> Array.maxBy snd
        |> fst
        |> fun k -> model.Classes[k])
```

`Array.indexed` は、配列の要素を `(位置, 値)` の組にします。`Array.maxBy snd` で値が最大の組を選び、`fst` でその位置を取り出しています。

```text
テストの実行の概要: 成功!
```

### 損失の記録

3 品種・2 特徴量の例と、損失が下がることを確かめます。

```fsharp
[<Fact>]
let ``3 種類のラベルを 2 つの特徴量から予測する`` () =
    let x =
        List.map2
            (fun length width -> Map.ofList [ "花弁長さ", length; "花弁幅", width ])
            [ 0.1; 0.2; 0.5; 0.6; 0.5; 0.6 ]
            [ 0.1; 0.2; 0.1; 0.2; 0.8; 0.9 ]

    let t = [ "setosa"; "setosa"; "versicolor"; "versicolor"; "virginica"; "virginica" ]

    Assert.Equal<string list>(t, predict (fit defaults x t) x)

[<Fact>]
let ``学習を繰り返すと損失が小さくなる`` () =
    let model =
        fit
            { defaults with Epochs = 100 }
            (byPetalWidth [ 0.1; 0.2; 0.8; 0.9 ])
            [ "setosa"; "setosa"; "virginica"; "virginica" ]

    Assert.Equal(100, model.Losses.Length)
    Assert.True(List.last model.Losses < List.head model.Losses)
```

- `{ defaults with Epochs = 100 }` は、`defaults` を写して `Epochs` だけを変えた **新しいレコード** を作る書き方（copy-and-update）です。F# の関数には Kotlin・Python のような引数の既定値がありませんが、既定値のレコードを 1 つ用意しておけば、変えたい項目だけを書けます。`defaults` そのものは変わりません

`Losses` がまだ無いので、テストがコンパイルできません。

```text
error FS0039: 型 'LogisticModel<_>' は、フィールド、コンストラクター、またはメンバー 'Losses' を定義していません。
```

損失を記録するようにします。確率は損失の計算と誤差の計算の両方で使うので、先に全サンプル分を求めておきます。

```fsharp
/// 確率が 0 のときに log 0 が負の無限大にならないように足す小さな値
[<Literal>]
let private Epsilon = 1e-12

/// 交差エントロピー。正解の品種の確率の対数の平均にマイナスを付けたもの
let crossEntropy (probabilities: float[][]) (targets: int[]) : float =
    -(Array.map2 (fun (p: float[]) target -> log (p[target] + Epsilon)) probabilities targets
      |> Array.average)
```

```fsharp
    let step (weights: float[][], bias: float[], losses: float list) _ =
        let probabilities = rows |> Array.map (scores weights bias >> softmax)

        // 確率 − 正解（正解の品種だけ 1 を引く）
        let errors =
            probabilities
            |> Array.mapi (fun i p -> p |> Array.mapi (fun k value -> if k = targets[i] then value - 1.0 else value))
        // ...
        newWeights, newBias, crossEntropy probabilities targets :: losses

    let initial =
        Array.init features.Length (fun _ -> Array.zeroCreate classes.Length), Array.zeroCreate classes.Length, []

    let weights, bias, losses = List.fold step initial [ 1 .. settings.Epochs ]
```

- `fold` の状態を「重み・切片・損失のリスト」の 3 つ組に広げました。`::` はリストの先頭に要素を足す演算子で、F# のリストは先頭に足すのが速いので、新しい損失を先頭に積み、最後に `List.rev losses` で古い順に並べ直してから `Losses` に入れます
- `scores weights bias >> softmax` の `>>` は **関数合成** です。「スコアを求めてから確率に変換する」関数を 1 つ作り、`Array.map` に渡しています
- `-(... |> Array.average)` のかっこは、パイプラインの結果全体にマイナスを付けるためのものです

```text
テストの実行の概要: 成功!
```

Kotlin 版・TypeScript 版には「学習する前に予測するとエラーになる」テストがありました。F# 版の `predict` はモデルの値を引数に取るので、学習していなければ呼べません。第 3 章の決定木と同じく、このテストは要りません。

## 10.5 ランダムフォレスト

### 仕組み

ランダムフォレストは、次の 2 つの「ばらつき」を持たせた決定木をたくさん作り、予測を多数決で決めます。

1. **ブートストラップ標本**: 訓練データから、同じ件数を重複を許して選び直したデータで木を学習する（バギング）
2. **特徴量の部分集合**: 木ごとに使う特徴量を一部だけに絞る

1 本 1 本の決定木は訓練データの細部を覚えて過学習しがちですが、違うデータ・違う特徴量で学習した木の多数決を取ると、個々の木の癖が打ち消し合います。

この章では、第 3 章の `DecisionTree` を **変更せずに** 再利用します。そのため、特徴量の絞り込みは「木ごと」に行います。ML.NET の FastForest は、分割のたびに特徴量を選び直すなど、仕組みが少し異なります。

### 多数決・ブートストラップ標本・特徴量の選択

乱数を使う処理は、第 2 章の `shuffle` と同じく **シードを受け取る関数** にします。Kotlin 版は乱数生成器（`Random`）を引数で受け取り、呼ぶたびに生成器の状態が進みました。F# 版では生成器を関数の中に閉じ込めるので、同じ引数なら必ず同じ結果が返る **純粋な関数** として扱えます。

```fsharp
// tests/MachineLearning.Tests/Chapter10/RandomForestTest.fs
[<Fact>]
let ``サンプルごとに最も多い予測を選ぶ`` () =
    let votes =
        [
            [ "setosa"; "virginica" ]
            [ "setosa"; "virginica" ]
            [ "versicolor"; "setosa" ]
        ]

    Assert.Equal<string list>([ "setosa"; "virginica" ], majorityVote votes)

[<Fact>]
let ``元のデータと同じ件数の行番号を重複を許して選ぶ`` () =
    let rows = bootstrapSample 0 100

    Assert.Equal(100, rows.Length)
    Assert.True(rows |> List.forall (fun row -> 0 <= row && row < 100))
    Assert.True((List.distinct rows).Length < 100)

[<Fact>]
let ``同じシードなら同じ行を選ぶ`` () =
    Assert.Equal<int list>(bootstrapSample 42 10, bootstrapSample 42 10)

[<Fact>]
let ``指定した数の特徴量を元の列の順で選ぶ`` () =
    let features = [ "a"; "b"; "c"; "d" ]

    let chosen = chooseFeatures 0 2 features

    Assert.Equal(2, chosen.Length)
    Assert.Equal<string list>(List.sort chosen, chosen)
    Assert.Equal<string list>(chosen, chooseFeatures 0 2 features)
```

`votes` は「木ごとの予測のリスト」です。どれも組み立てるだけの処理なので、まとめてテストを書き、明白な実装で進めます。

```text
error FS0039: 名前空間 'RandomForest' が定義されていません。
error FS0039: 値またはコンストラクター 'majorityVote' が定義されていません。
error FS0039: 値またはコンストラクター 'bootstrapSample' が定義されていません。
error FS0039: 値またはコンストラクター 'chooseFeatures' が定義されていません。
```

このほかに FS0041（`Assert.Equal` のオーバーロードを決められない）と FS0072（型が分からないオブジェクトへの参照）も出ました。`bootstrapSample` の型が分からないために、`rows.Length` や `Assert.Equal` の引数の型を決められなかったという、連鎖的なエラーです。

```fsharp
// src/MachineLearning/Chapter10/RandomForest.fs
module MachineLearning.Chapter10.RandomForest

open System
open MachineLearning.Chapter02.Random
open MachineLearning.Chapter03

/// 木ごとの予測のリストから、サンプルごとに最も多い予測を選ぶ
let majorityVote (votes: 'L list list) : 'L list =
    votes |> List.transpose |> List.map DecisionTree.majority

/// シードを使って、0 以上 size 未満の行番号を size 個、重複を許して選ぶ
let bootstrapSample (seed: int) (size: int) : int list =
    let random = Random seed
    List.init size (fun _ -> random.Next size)

/// シードで並べ替えた先頭 maxFeatures 個の特徴量を、元の列の順で返す
let chooseFeatures (seed: int) (maxFeatures: int) (features: string list) : string list =
    let chosen = features |> shuffle seed |> List.truncate maxFeatures |> Set.ofList
    features |> List.filter chosen.Contains
```

- `List.transpose` は、リストのリストの行と列を入れ替えます。「木ごとの予測」が「サンプルごとの、各木の予測」になるので、あとは第 3 章の葉の多数決 `DecisionTree.majority` をそのまま使えます。Kotlin 版は添字で組み替え、多数決も書き直していましたが、F# 版は 2 つの関数の組み合わせで済みました
- `random.Next size` は、0 以上 `size` 未満の整数を返します。`Random` は呼ぶたびに状態が変わる可変なオブジェクトですが、関数の中で作って外に出さないので、関数の外からは見えません
- `chooseFeatures` は第 2 章の `shuffle` で並べ替え、`List.truncate` で先頭の `maxFeatures` 個を取り、`List.filter` で元の列の順に戻します。同じ組み合わせが同じ並びになります
- `chosen.Contains` は、`Set` のメソッドを関数として `List.filter` に渡しています

```text
テストの実行の概要: 成功!
```

### 森を作る

```fsharp
/// がく片幅と花弁幅を持つ、2 品種 10 件のデータ
let twoSpeciesX =
    List.map2
        (fun sepal petal -> Map.ofList [ "がく片幅", sepal; "花弁幅", petal ])
        [ 0.5; 0.3; 0.6; 0.4; 0.5; 0.3; 0.6; 0.4; 0.5; 0.3 ]
        [ 0.1; 0.12; 0.14; 0.16; 0.18; 0.8; 0.82; 0.84; 0.86; 0.88 ]

let twoSpeciesT = List.replicate 5 "setosa" @ List.replicate 5 "virginica"

[<Fact>]
let ``指定した数だけ第 3 章の決定木を学習する`` () =
    let forest =
        fit
            { defaults with
                NEstimators = 5
                MaxFeatures = 1
            }
            twoSpeciesX
            twoSpeciesT

    Assert.Equal(5, forest.Length)

[<Fact>]
let ``各決定木は指定した数の特徴量だけを使う`` () =
    let forest =
        fit
            { defaults with
                NEstimators = 5
                MaxFeatures = 1
            }
            twoSpeciesX
            twoSpeciesT

    Assert.True(forest |> List.forall (fun fitted -> fitted.Columns.Length = 1))

[<Fact>]
let ``決定木の多数決で予測する`` () =
    let forest = fit { defaults with NEstimators = 25 } twoSpeciesX twoSpeciesT

    let newX =
        [
            Map.ofList [ "がく片幅", 0.4; "花弁幅", 0.13 ]
            Map.ofList [ "がく片幅", 0.4; "花弁幅", 0.83 ]
        ]

    Assert.Equal<string list>([ "setosa"; "virginica" ], predict forest newX)

[<Fact>]
let ``同じシードなら同じ森になる`` () =
    let settings =
        { defaults with
            NEstimators = 5
            MaxFeatures = 1
            Seed = 7
        }

    Assert.Equal<FittedTree<string> list>(fit settings twoSpeciesX twoSpeciesT, fit settings twoSpeciesX twoSpeciesT)
```

- 最後のテストは、森全体（学習した木・使った列・行番号）が等しいことを確かめています。F# のレコード・リスト・判別共用体は **構造の等価性** を持つので、2 つの森を `Assert.Equal` でまるごと比べられます。Kotlin 版は使った列と予測を別々に比べていました
- テストのデータは 10 件と小さくしてあります。木を何本も学習するので、データを大きくするとテストが遅くなります

```text
error FS0039: 値またはコンストラクター 'defaults' が定義されていません。 次のいずれかの可能性はありませんか:   defaultArg   defaultIfNull   defaultIfNullV   defaultValueArg   DefaultValueAttribute
error FS0039: レコード ラベル 'NEstimators' が定義されていません。
error FS0039: レコード ラベル 'MaxFeatures' が定義されていません。
error FS0039: レコード ラベル 'Seed' が定義されていません。
error FS0039: 値またはコンストラクター 'fit' が定義されていません。 次のいずれかの可能性はありませんか:   fst
error FS0039: 値またはコンストラクター 'predict' が定義されていません。
error FS0039: 型 'FittedTree' が定義されていません。
error FS0786: { expr with ... } という形式の式を使用できるのはレコード型のみです。オブジェクトの型を構築するには、{ new Type(...) with ... } を使用してください。
```

FS0786 は、`defaults` の型が分からないので `{ defaults with ... }` をレコードの copy-and-update と判断できなかった、という連鎖的なエラーです。

部品（多数決・ブートストラップ標本・特徴量の選択・第 3 章の決定木）がそろっているので、組み立てるだけの明白な実装で進めます。

```fsharp
/// 学習の設定
type Settings =
    {
        NEstimators: int
        MaxFeatures: int
        MaxDepth: int option
        Seed: int
    }

let defaults =
    {
        NEstimators = 10
        MaxFeatures = 2
        MaxDepth = None
        Seed = 0
    }

/// 1 本分の学習結果。使った列と、ブートストラップ標本の行番号と、学習した木
type FittedTree<'L> =
    {
        Columns: string list
        Rows: int list
        Tree: DecisionTree.Tree<'L>
    }

/// 森のシードから、木ごとに「ブートストラップ標本のシード」と「特徴量を選ぶシード」の組を作る
let private treeSeeds (seed: int) (count: int) : (int * int) list =
    let random = Random seed

    List.init count (fun _ ->
        let rowSeed = random.Next()
        let featureSeed = random.Next()
        rowSeed, featureSeed)

/// ブートストラップ標本と特徴量の部分集合で、第 3 章の決定木を NEstimators 本学習する
let fit (settings: Settings) (x: Map<string, float> list) (t: 'L list) : FittedTree<'L> list =
    let xs = List.toArray x
    let ts = List.toArray t
    let features = xs[0] |> Map.keys |> Seq.toList

    treeSeeds settings.Seed settings.NEstimators
    |> List.map (fun (rowSeed, featureSeed) ->
        let rows = bootstrapSample rowSeed xs.Length
        let columns = chooseFeatures featureSeed settings.MaxFeatures features

        let sampleX =
            rows
            |> List.map (fun row -> xs[row] |> Map.filter (fun column _ -> List.contains column columns))

        let sampleT = rows |> List.map (fun row -> ts[row])

        {
            Columns = columns
            Rows = rows
            Tree = DecisionTree.fit settings.MaxDepth sampleX sampleT
        })

/// 木ごとに予測して多数決する。第 3 章の木は分割に使った列だけを見るので、列を絞らずに渡せる
let predict (forest: FittedTree<'L> list) (x: Map<string, float> list) : 'L list =
    forest
    |> List.map (fun fitted -> DecisionTree.predict fitted.Tree x)
    |> majorityVote
```

- 森のシード 1 つから、木ごとに 2 つのシードを `treeSeeds` で作ります。シードを `seed + 1`・`seed + 2` と足して作ることもできますが、.NET の `Random` は近いシードから近い乱数を作ります。F# Interactive で確かめると、シード 0・2・4 で作った `Random` の最初の `Next 105` は 76・80・85 でした。そこで、乱数生成器にシードを作らせています
- `treeSeeds` の中で `let rowSeed = ...` と `let featureSeed = ...` を別の行に分けたのは、どちらを先に引くかを読み手にはっきり見せるためです
- 行番号で行を取り出すので、`List.toArray` で配列にしてから `xs[row]` で取り出します。F# のリストは連結リストで、`list[i]` は先頭から i 個たどるので、何度も添字で取り出すなら配列のほうが速く済みます
- 使わない列は `Map.filter` で落としてから第 3 章の `DecisionTree.fit` に渡します。ブートストラップ標本の行番号は、特徴量の重要度を計算するときに使うので `Rows` にも残します（10.6 節）
- `predict` では列を絞りません。第 3 章の木は `Node` の分け方に書かれた特徴量しか読まないので、使わなかった列が行に入っていても結果は変わりません

```text
テストの実行の概要: 成功!
```

第 3 章の `DecisionTree` には一切手を入れていません。`fit` が木を返し、`predict` が木を受け取るという小さな関数の組み合わせだったので、部品としてそのまま組み込めました。

## 10.6 特徴量の重要度

### 計算方法

決定木の各節で、

```text
減少量 = その節に届いた件数 × (その節のジニ不純度 − 分割後のジニ不純度)
```

を求め、分割に使った特徴量ごとに合計し、全体が 1 になるように割合にします。第 3 章の `Split.Impurity` は「分割後のジニ不純度（件数で重み付けした平均）」なので、そのまま使えます。

ただし、第 3 章の `Node` は、その節に届いた件数を持っていません。そこで、学習に使ったデータをもう一度木に流して、節ごとに件数とジニ不純度を求めます。重要度は手で計算した値でテストします。

### 決定木 1 本の重要度

```fsharp
// tests/MachineLearning.Tests/Chapter10/FeatureImportanceTest.fs
module MachineLearning.Tests.Chapter10.FeatureImportanceTest

open Xunit
open MachineLearning.Chapter03
open MachineLearning.Chapter10
open MachineLearning.Chapter10.FeatureImportance

/// 2 つの特徴量の値のリストから、行のリストを作る
let rowsOf (first: string, firstValues: float list) (second: string, secondValues: float list) =
    List.map2 (fun a b -> Map.ofList [ first, a; second, b ]) firstValues secondValues

[<Fact>]
let ``分割しない木はすべての特徴量の重要度が 0`` () =
    let x = rowsOf ("がく片幅", [ 0.3; 0.5 ]) ("花弁幅", [ 0.1; 0.2 ])

    Assert.Equal<Map<string, float>>(
        Map.ofList [ "がく片幅", 0.0; "花弁幅", 0.0 ],
        treeImportances (DecisionTree.Leaf "setosa") x [ "setosa"; "setosa" ]
    )
```

判別共用体の `DecisionTree.Leaf "setosa"` で、分割しない木をテストの中で直接作っています。

```text
error FS0039: 名前空間 'FeatureImportance' が定義されていません。
error FS0039: 値またはコンストラクター 'treeImportances' が定義されていません。
```

すべて 0 を返す仮実装で通ります。

```fsharp
// src/MachineLearning/Chapter10/FeatureImportance.fs
module MachineLearning.Chapter10.FeatureImportance

open MachineLearning.Chapter03.DecisionTree

let treeImportances (tree: Tree<'L>) (x: Map<string, float> list) (t: 'L list) : Map<string, float> =
    x.Head |> Map.map (fun _ _ -> 0.0)
```

次に、花弁幅だけで分割する木と、2 つの特徴量で 2 回分割する木を例にします。

```fsharp
[<Fact>]
let ``1 回だけ分割する木は分割に使った特徴量の重要度が 1`` () =
    let x = rowsOf ("がく片幅", [ 0.3; 0.5; 0.4; 0.6 ]) ("花弁幅", [ 0.1; 0.2; 0.8; 0.9 ])
    let t = [ "setosa"; "setosa"; "virginica"; "virginica" ]

    let tree = DecisionTree.fit None x t

    Assert.Equal<Map<string, float>>(Map.ofList [ "がく片幅", 0.0; "花弁幅", 1.0 ], treeImportances tree x t)

[<Fact>]
let ``分割で減った不純度を件数で重み付けして割合にする`` () =
    let x =
        rowsOf ("花弁長さ", [ 0.1; 0.2; 0.3; 0.8; 0.7; 0.9 ]) ("花弁幅", [ 0.1; 0.1; 0.1; 0.2; 0.9; 0.9 ])

    let t = [ "setosa"; "setosa"; "setosa"; "versicolor"; "virginica"; "virginica" ]

    let importances = treeImportances (DecisionTree.fit None x t) x t

    Assert.Equal(7.0 / 11.0, importances["花弁長さ"], 12)
    Assert.Equal(4.0 / 11.0, importances["花弁幅"], 12)
```

2 つ目の例は Kotlin 版と同じデータで、期待値は次のように手で計算したものです。

| 節 | 件数 | 節のジニ不純度 | 分割 | 分割後のジニ不純度 | 減少量 |
|----|------|--------------|------|-----------------|--------|
| 根 | 6 | 1 − (9 + 1 + 4) / 36 = 11/18 | 花弁長さ ≤ 0.5（setosa 3 件と残り 3 件） | 3/6 × 0 + 3/6 × 4/9 = 2/9 | 6 × (11/18 − 2/9) = 7/3 |
| 右の子 | 3 | 1 − (1 + 4) / 9 = 4/9 | 花弁幅 ≤ 0.55（花弁長さでは分け切れない） | 0 | 3 × 4/9 = 4/3 |

合計 11/3 に対する割合で、花弁長さが 7/11、花弁幅が 4/11 になるはずです。

```text
失敗 MachineLearning.Tests.Chapter10.FeatureImportanceTest.1 回だけ分割する木は分割に使った特徴量の重要度が 1 (109ms)
  Assert.Equal() Failure: Collections differ
                           ↓ (pos 1)
  Expected: [["がく片幅"] = 0, ["花弁幅"] = 1]
  Actual:   [["がく片幅"] = 0, ["花弁幅"] = 0]
                           ↑ (pos 1)
失敗 MachineLearning.Tests.Chapter10.FeatureImportanceTest.分割で減った不純度を件数で重み付けして割合にする (65ms)
  Assert.Equal() Failure: Values are not within 12 decimal places
  Expected: 0.63636363636399995 (rounded from 0.63636363636363635)
  Actual:   0 (rounded from 0)
```

木をたどりながら「特徴量と減少量の組」を集め、特徴量ごとに合計して割合に直します。

```fsharp
/// 学習に使ったデータをもう一度木に流して、節ごとに「分割に使った特徴量と、件数で重み付けした不純度の減少量」を集める
let rec private impurityDecreases (tree: Tree<'L>) (x: Map<string, float> list) (t: 'L list) : (string * float) list =
    match tree with
    | Leaf _ -> []
    | Node(split, left, right) ->
        let goesLeft (row: Map<string, float>, _) = row[split.Feature] <= split.Threshold
        let leftPart, rightPart = List.zip x t |> List.partition goesLeft

        let decreasesOf subtree part =
            impurityDecreases subtree (List.map fst part) (List.map snd part)

        (split.Feature, float t.Length * (gini t - split.Impurity))
        :: decreasesOf left leftPart
        @ decreasesOf right rightPart

/// 合計が 1 になるように割合にする。合計が 0 ならそのまま返す
let private normalize (totals: Map<string, float>) : Map<string, float> =
    let total = totals |> Map.values |> Seq.sum

    if total = 0.0 then
        totals
    else
        totals |> Map.map (fun _ value -> value / total)

/// 決定木 1 本の特徴量の重要度
let treeImportances (tree: Tree<'L>) (x: Map<string, float> list) (t: 'L list) : Map<string, float> =
    let decreases = impurityDecreases tree x t

    x.Head
    |> Map.map (fun feature _ ->
        decreases
        |> List.filter (fun (splitFeature, _) -> splitFeature = feature)
        |> List.sumBy snd)
    |> normalize
```

- データを左右に振り分ける部分は、第 3 章の `fit` と同じ形の再帰です。`Leaf` と `Node` をパターンマッチで場合分けし、`Node(split, left, right)` で節の中身を名前に取り出しています
- `a :: b @ c` は、「`a` を先頭に置き、`b` と `c` をつなげたリスト」です。書き換える変数を持たずに、再帰の結果をつないでいます
- `x.Head |> Map.map ...` は、1 行目の列名をキーにした `Map` を作り、値を「その特徴量の減少量の合計」に置き換えています

ところが、1 つ目のテストは通り、2 つ目のテストは失敗したままでした。

```text
失敗 MachineLearning.Tests.Chapter10.FeatureImportanceTest.分割で減った不純度を件数で重み付けして割合にする (70ms)
  Assert.Equal() Failure: Values are not within 12 decimal places
  Expected: 0.63636363636399995 (rounded from 0.63636363636363635)
  Actual:   0 (rounded from 0)
```

花弁長さの重要度が 0 です。木の根で、花弁長さではなく花弁幅が選ばれていました。このデータでは、根の分割の候補に次の 2 つが **同じ不純度 2/9** で並びます。

- 花弁長さ ≤ 0.5（setosa 3 件と残り 3 件）
- 花弁幅 ≤ 0.15（setosa 3 件と残り 3 件。setosa の花弁幅はすべて 0.1）

第 3 章の `bestSplit` は、不純度が同じなら先に調べた特徴量を選びます。第 3 章の 3.4 節のとおり、F# 版は特徴量を `Map` のキーの順（文字列の順）に調べるので、「花弁幅」（幅は U+5E45）が「花弁長さ」（長は U+9577）より先になります。Kotlin 版はデータフレームの列の順（花弁長さが先）に調べるので、同じデータから違う木ができたのです。

実装の誤りではなく、手計算の前提（根で花弁長さが選ばれる）が F# 版では成り立っていなかったことが原因です。そこで、テストのデータを、同点にならないように変えました。setosa の 1 件の花弁幅を 0.1 から 0.2 にします。

```fsharp
        rowsOf ("花弁長さ", [ 0.1; 0.2; 0.3; 0.8; 0.7; 0.9 ]) ("花弁幅", [ 0.1; 0.1; 0.2; 0.2; 0.9; 0.9 ])
```

これで花弁幅の最良の分割は花弁幅 ≤ 0.55（左に setosa 3 件と versicolor 1 件）の不純度 1/4 になり、花弁長さの 2/9 より大きくなるので、根は花弁長さで分けます。右の子（versicolor 1 件と virginica 2 件）は変わらないので、手計算の表と期待値 7/11・4/11 はそのまま使えます。

```text
テストの実行の概要: 成功!
```

同点のときにどちらを選ぶかは、実装の細部で決まります。手計算で期待値を作るテストでは、同点にならないデータを選ぶと、言語や実装に左右されないテストになります。

### ランダムフォレストの重要度

森の重要度は、木ごとの重要度（学習に使ったブートストラップ標本で計算）の平均を、もう一度割合に直したものです。木が 1 本なら、その木の重要度と一致するはずです。

```fsharp
[<Fact>]
let ``木が 1 本なら学習に使った行でのその木の重要度と一致する`` () =
    let x =
        rowsOf
            ("がく片幅", [ 0.5; 0.3; 0.6; 0.4; 0.5; 0.3; 0.6; 0.4 ])
            ("花弁幅", [ 0.1; 0.12; 0.14; 0.16; 0.8; 0.82; 0.84; 0.86 ])

    let t = List.replicate 4 "setosa" @ List.replicate 4 "virginica"

    let forest =
        RandomForest.fit
            { RandomForest.defaults with
                NEstimators = 1
                MaxFeatures = 2
            }
            x
            t

    let fitted = List.exactlyOne forest

    let expected =
        treeImportances
            fitted.Tree
            (fitted.Rows |> List.map (fun row -> x[row]))
            (fitted.Rows |> List.map (fun row -> t[row]))

    Assert.Equal<Map<string, float>>(expected, forestImportances forest x t)
```

`List.exactlyOne` は、要素がちょうど 1 つのリストからその要素を取り出します。要素が 0 個や 2 個以上なら例外になるので、「木が 1 本である」ことの確認も兼ねています。

```text
error FS0039: 値またはコンストラクター 'forestImportances' が定義されていません。 次のいずれかの可能性はありませんか:   treeImportances   FeatureImportance   forest
```

`forestImportances` は、木ごとに「使った行と列だけのデータ」を作る必要があります。これは `RandomForest.fit` の中で書いた処理と同じなので、先に `RandomForest` に `sampleOf` として切り出してから、`fit` と `forestImportances` の両方で使いました。

```fsharp
/// 行番号と列名で、1 本分の学習データを取り出す。同じ行番号が重複していれば、その回数だけ行が並ぶ
let sampleOf (rows: int list) (columns: string list) (x: Map<string, float> list) (t: 'L list) =
    let xs = List.toArray x
    let ts = List.toArray t

    let sampleX =
        rows
        |> List.map (fun row -> xs[row] |> Map.filter (fun column _ -> List.contains column columns))

    sampleX, rows |> List.map (fun row -> ts[row])

/// ブートストラップ標本と特徴量の部分集合で、第 3 章の決定木を NEstimators 本学習する
let fit (settings: Settings) (x: Map<string, float> list) (t: 'L list) : FittedTree<'L> list =
    let features = x.Head |> Map.keys |> Seq.toList

    treeSeeds settings.Seed settings.NEstimators
    |> List.map (fun (rowSeed, featureSeed) ->
        let rows = bootstrapSample rowSeed x.Length
        let columns = chooseFeatures featureSeed settings.MaxFeatures features
        let sampleX, sampleT = sampleOf rows columns x t

        {
            Columns = columns
            Rows = rows
            Tree = DecisionTree.fit settings.MaxDepth sampleX sampleT
        })
```

```fsharp
/// ランダムフォレストの特徴量の重要度。木ごとの重要度（学習に使ったブートストラップ標本で計算）を平均し、割合にする
let forestImportances (forest: FittedTree<'L> list) (x: Map<string, float> list) (t: 'L list) : Map<string, float> =
    let perTree =
        forest
        |> List.map (fun fitted ->
            let sampleX, sampleT = sampleOf fitted.Rows fitted.Columns x t
            treeImportances fitted.Tree sampleX sampleT)

    x.Head
    |> Map.map (fun feature _ -> perTree |> List.averageBy (Map.tryFind feature >> Option.defaultValue 0.0))
    |> normalize
```

- `sampleOf` は、`(sampleX, sampleT)` の組を返します。呼び出し側では `let sampleX, sampleT = ...` と組を分解して受け取ります
- 木ごとの重要度には、その木が使った列しか入っていません。`Map.tryFind feature` はキーが無ければ `None` を返すので、`Option.defaultValue 0.0` で 0 として平均します。2 つの関数を `>>` で合成して、`List.averageBy` に 1 つの関数として渡しています

`FeatureImportance.fs` は `RandomForest.fs` の後ろに置きます。F# では、ファイルの順が依存の向きを表します。重要度は森に依存しますが、森は重要度に依存しません。

```text
テストの実行の概要: 成功!
```

## 10.7 モデル共通の型

### 分類器を関数の型で表す

決定木・ロジスティック回帰・ランダムフォレストは、どれも「訓練データで学習し、新しいデータのラベルを予測する」ものです。他の言語の版は、これを `fit` と `predict` を持つインターフェースで表しました。

F# 版では、第 3 章の `trainFastTree` と同じ形の **関数の型** で表します。訓練データ（特徴量と正解ラベル）を受け取り、**予測する関数を返す** 関数です。

まず、テスト用の単純な分類器 `alwaysSetosa` で、評価関数の振る舞いを決めます。

```fsharp
// tests/MachineLearning.Tests/Chapter10/ClassifierTest.fs
module MachineLearning.Tests.Chapter10.ClassifierTest

open Xunit
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter03
open MachineLearning.Chapter10
open MachineLearning.Chapter10.Classifier

let byPetalWidth (values: float list) : Map<string, float> list =
    values |> List.map (fun value -> Map.ofList [ "花弁幅", value ])

let smallSplit: TrainTestSplit<Map<string, float>, string> =
    {
        XTrain = byPetalWidth [ 0.1; 0.2; 0.8; 0.9 ]
        XTest = byPetalWidth [ 0.15; 0.25 ]
        TTrain = [ "setosa"; "setosa"; "virginica"; "virginica" ]
        TTest = [ "setosa"; "setosa" ]
    }

/// 何を学習しても setosa と予測する分類器
let alwaysSetosa: Classifier<string> = fun _ _ -> List.map (fun _ -> "setosa")

[<Fact>]
let ``学習させてから訓練データとテストデータの正解率を求める`` () =
    Assert.Equal({ Train = 0.5; Test = 1.0 }, evaluate alwaysSetosa smallSplit)
```

`alwaysSetosa` は、訓練データを 2 つとも捨て（`fun _ _ ->`）、「行の数だけ setosa を並べる関数」を返します。Kotlin 版ではテスト用のクラスを 1 つ書きましたが、F# 版ではラムダ式 1 つです。

```text
error FS0039: 名前空間 'Classifier' が定義されていません。
error FS0039: 型 'Classifier' が定義されていません。 次のいずれかの可能性はありませんか:   ClassAttribute
error FS0039: 値またはコンストラクター 'evaluate' が定義されていません。
error FS0039: レコード ラベル 'Train' が定義されていません。
error FS0039: レコード ラベル 'Test' が定義されていません。
```

このほかに、`Assert.Equal` のオーバーロードを決められないという FS0041 も出ました（`Score` の型が分からないための連鎖的なエラーです）。

```fsharp
// src/MachineLearning/Chapter10/Classifier.fs
module MachineLearning.Chapter10.Classifier

open MachineLearning.Chapter01.KinokoTakenoko
open MachineLearning.Chapter02.IrisPreprocessing

/// 分類器。訓練データ（特徴量と正解ラベル）を受け取り、予測する関数を返す関数
type Classifier<'L> = Map<string, float> list -> 'L list -> (Map<string, float> list -> 'L list)

/// 訓練データとテストデータの正解率
type Score = { Train: float; Test: float }

/// 訓練データで学習させてから、訓練データとテストデータの正解率を求める
let evaluate (classifier: Classifier<'L>) (split: TrainTestSplit<Map<string, float>, 'L>) : Score =
    let predict = classifier split.XTrain split.TTrain

    {
        Train = accuracy (predict split.XTrain) split.TTrain
        Test = accuracy (predict split.XTest) split.TTest
    }
```

- `type Classifier<'L> = ... -> ... -> (... -> ...)` は、関数の型に名前を付ける **型の略称**（type abbreviation）です。新しい型を作るのではなく、同じ形の関数の型に別名を付けるだけなので、この形の関数なら何でも `Classifier<'L>` として扱えます
- 正解率は第 1 章の `accuracy`、分割結果は第 2 章の `TrainTestSplit` を再利用しています
- `Score` はレコードなので、`{ Train = 0.5; Test = 1.0 }` と組み立てた値とそのまま `Assert.Equal` で比べられます

```text
テストの実行の概要: 成功!
```

### 3 つのモデルを同じ関数で評価する

第 3 章の決定木・ロジスティック回帰・ランダムフォレストは、どれも「`fit` でモデルを作り、`predict` にモデルを渡す」形です。この 2 つの関数を組み合わせて `Classifier` にする関数 `ofModel`（まだ無い関数）を使って、テストを書きました。

```fsharp
[<Fact>]
let ``第 3 章の決定木と自作のモデルを同じ関数で評価できる`` () =
    let classifiers: Classifier<string> list =
        [
            ofModel (DecisionTree.fit (Some 1)) DecisionTree.predict
            ofModel (LogisticRegression.fit LogisticRegression.defaults) LogisticRegression.predict
            ofModel
                (RandomForest.fit
                    { RandomForest.defaults with
                        NEstimators = 5
                        MaxFeatures = 1
                    })
                RandomForest.predict
        ]

    let scores =
        classifiers |> List.map (fun classifier -> evaluate classifier smallSplit)

    Assert.Equal<Score list>(List.replicate 3 { Train = 1.0; Test = 1.0 }, scores)
```

```text
error FS0039: 値またはコンストラクター 'ofModel' が定義されていません。
```

```fsharp
/// 「学習してモデルを返す関数」と「モデルで予測する関数」を組み合わせて分類器にする
let ofModel
    (fit: Map<string, float> list -> 'L list -> 'M)
    (predict: 'M -> Map<string, float> list -> 'L list)
    : Classifier<'L> =
    fun x t -> predict (fit x t)
```

```text
テストの実行の概要: 成功!
```

- `DecisionTree.fit (Some 1)` は、第 3 章の `fit` に深さの上限だけを渡した **部分適用** です。残りの引数（特徴量と正解ラベル）を受け取ってモデルを返す関数になります。ロジスティック回帰とランダムフォレストも、設定だけを渡して同じ形にしています
- モデルの型 `'M` は、決定木なら `Tree<string>`、ロジスティック回帰なら `LogisticModel<string>`、ランダムフォレストなら `FittedTree<string> list` です。`ofModel` はどれでも受け付け、モデルを返された関数の中に閉じ込めます。使う側からはモデルの型は見えません
- 第 3 章の `DecisionTree` には手を入れていません。Kotlin 版は、名前で型を判定する `interface` に合わせるため、第 3 章の決定木を包むアダプターのクラスを書きました。F# の関数の型は形（引数と戻り値の型）だけで決まるので、部分適用と `ofModel` で済みます。`ofModel` を使わずに `fun x t -> DecisionTree.fit (Some 1) x t |> DecisionTree.predict` と書いても、同じ `Classifier<string>` になります

| 観点 | Kotlin の `interface` | F# の関数の型 |
|------|-----------------------|---------------|
| 型が合う条件 | 実装を宣言している（名前的部分型） | 引数と戻り値の型が一致している |
| 既存の決定木（第 3 章） | アダプターのクラスで包む | 部分適用と `ofModel` で組み立てる |
| 学習前の予測 | 実行時の例外（テストで確かめる） | 予測する関数は学習した後にしか手に入らない |
| 学習したモデルの中身 | プロパティで読める | `ofModel` の中に閉じ込められる。中身を読みたいとき（重要度・損失）は、`fit` を直接呼ぶ |

最後の行のとおり、関数の型にすると学習したモデルが外から見えなくなります。10.10 節の重要度や、10.11 節の Notebook の損失の推移では、`RandomForest.fit` や `LogisticRegression.fit` を直接呼んでモデルを受け取ります。評価のように「予測さえできればよい」ところでは `Classifier`、中身を読みたいところではモデルの値、と使い分けます。

## 10.8 ML.NET の学習器を同じ型で使う

### ML.NET の学習器を Classifier にする

ML.NET の学習器も、第 3 章の `trainFastTree` と同じ手順で「予測する関数を返す」形にできます。違うのはパイプラインの 2 段目の学習器だけなので、学習器を作る関数を受け取る `multiclass` を書き、ロジスティック回帰とランダムフォレストで共有します。ML.NET への橋渡しは第 3 章の `MlNetAdapter` の書き方（`[<CLIMutable>]` のレコードと `SchemaDefinition`）に倣い、レコードの型 `MlRow`・`MlPrediction` は第 3 章のものをそのまま使います。

```fsharp
// tests/MachineLearning.Tests/Chapter10/MlNetClassifierTest.fs
module MachineLearning.Tests.Chapter10.MlNetClassifierTest

open Xunit
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter10.Classifier
open MachineLearning.Chapter10.MlNetClassifier
open MachineLearning.Tests.Chapter10.RandomForestTest

let twoSpeciesSplit: TrainTestSplit<Map<string, float>, string> =
    {
        XTrain = twoSpeciesX
        XTest =
            [
                Map.ofList [ "がく片幅", 0.4; "花弁幅", 0.13 ]
                Map.ofList [ "がく片幅", 0.4; "花弁幅", 0.83 ]
            ]
        TTrain = twoSpeciesT
        TTest = [ "setosa"; "virginica" ]
    }

[<Fact>]
let ``ML.NET のロジスティック回帰とランダムフォレストも同じ関数で評価できる`` () =
    let classifiers = [ lbfgsMaximumEntropy 1.0f 1.0f; fastForest 10 1 ]

    let scores =
        classifiers |> List.map (fun classifier -> evaluate classifier twoSpeciesSplit)

    Assert.Equal<Score list>(List.replicate 2 { Train = 1.0; Test = 1.0 }, scores)
```

- テストのデータは、`RandomForestTest` の 10 件の `twoSpeciesX`・`twoSpeciesT` を `open` して使い回しています
- `lbfgsMaximumEntropy` には L1・L2 正則化の強さ（`float32`）を、`fastForest` には木の数と、葉ごとの最小件数を渡します。`1.0f` の `f` は、単精度の浮動小数点数（`float32`）のリテラルです。ML.NET の既定値は、L1・L2 正則化がどちらも 1、FastForest の葉ごとの最小件数が 10 です（リフレクションで引数の既定値を読んで確かめました）。10 件のデータでは葉に 10 件を求めると分けられないので、テストでは 1 にしています

```text
error FS0039: 名前空間 'MlNetClassifier' が定義されていません。
error FS0039: 値またはコンストラクター 'lbfgsMaximumEntropy' が定義されていません。
error FS0039: 値またはコンストラクター 'fastForest' が定義されていません。
```

最初は、学習器を `IEstimator<ITransformer>`（どんな変換器でも作れる学習器）として受け取るように書きました。

```fsharp
let private multiclass (trainer: MLContext -> IEstimator<ITransformer>) : Classifier<string> =
    // ...

let lbfgsMaximumEntropy: Classifier<string> =
    multiclass (fun context -> context.MulticlassClassification.Trainers.LbfgsMaximumEntropy())
```

```text
MlNetClassifier.fs(53,32): error FS0193: 型の制約が一致しません。次の型    'Trainers.LbfgsMaximumEntropyMulticlassTrainer'    は次の型と互換性がありません    'IEstimator<ITransformer>'
MlNetClassifier.fs(58,9): error FS0193: 型の制約が一致しません。次の型    'Trainers.OneVersusAllTrainer'    は次の型と互換性がありません    'IEstimator<ITransformer>'
```

ADR 004 で見つけた `Append` のエラーと同じ原因です。`LbfgsMaximumEntropyMulticlassTrainer` が実装しているのは `IEstimator<MulticlassPredictionTransformer<...>>` で、C# ではこれを `IEstimator<ITransformer>` として扱えます（`IEstimator` の型引数が「共変」と宣言されているため）。F# の型検査はこの共変性を使わないので、別の型として扱います。

そこで、`multiclass` の学習器の型を `IEstimator<'T>` と型引数にし、呼ぶ側では `:> IEstimator<_>` で学習器が実装しているインターフェースへ **アップキャスト** します。`_` の部分（変換器の型）は、コンパイラが推論します。

```fsharp
// src/MachineLearning/Chapter10/MlNetClassifier.fs
module MachineLearning.Chapter10.MlNetClassifier

open Microsoft.ML
open Microsoft.ML.Data
open MachineLearning.Chapter03.MlNetAdapter
open MachineLearning.Chapter10.Classifier

/// 特徴量の Map を、列名の順（Map のキーの順）に並べた float32 の配列にする
let private toVector (row: Map<string, float>) : float32[] =
    row |> Map.values |> Seq.map float32 |> Seq.toArray

/// Features 列のベクトルの長さを、実行時に SchemaDefinition で指定する
let private schemaFor (featureCount: int) : SchemaDefinition =
    let schema = SchemaDefinition.Create(typeof<MlRow>)
    schema["Features"].ColumnType <- VectorDataViewType(NumberDataViewType.Single, featureCount)
    schema

/// 多クラス分類の学習器を受け取り、文字列のラベルを番号にして学習し、予測を文字列に戻す分類器にする
let private multiclass (trainer: MLContext -> IEstimator<'T>) : Classifier<string> =
    fun x t ->
        let context = MLContext(seed = 0)
        let schema = schemaFor x.Head.Count

        let toRows (features: Map<string, float> list) (labels: string list) =
            context.Data.LoadFromEnumerable(
                List.map2
                    (fun row label ->
                        {
                            Features = toVector row
                            Label = label
                        })
                    features
                    labels,
                schema
            )

        let pipeline =
            EstimatorChain()
                .Append(context.Transforms.Conversion.MapValueToKey("Label"))
                .Append(trainer context)
                .Append(context.Transforms.Conversion.MapKeyToValue("PredictedLabel"))

        let model = pipeline.Fit(toRows x t)

        fun newX ->
            model.Transform(toRows newX (newX |> List.map (fun _ -> "")))
            |> fun predictions -> context.Data.CreateEnumerable<MlPrediction>(predictions, reuseRowObject = false)
            |> Seq.map (fun prediction -> prediction.PredictedLabel)
            |> Seq.toList

/// ML.NET のソフトマックスのロジスティック回帰（L-BFGS で最適化する）。L1・L2 正則化の強さを指定する
let lbfgsMaximumEntropy (l1Regularization: float32) (l2Regularization: float32) : Classifier<string> =
    multiclass (fun context ->
        context.MulticlassClassification.Trainers.LbfgsMaximumEntropy(
            l1Regularization = l1Regularization,
            l2Regularization = l2Regularization
        )
        :> IEstimator<_>)

/// ML.NET のランダムフォレスト（FastForest）。2 クラス用なので、OneVersusAll で多クラスにする
let fastForest (numberOfTrees: int) (minimumExampleCountPerLeaf: int) : Classifier<string> =
    multiclass (fun context ->
        context.MulticlassClassification.Trainers.OneVersusAll(
            context.BinaryClassification.Trainers.FastForest(
                numberOfTrees = numberOfTrees,
                minimumExampleCountPerLeaf = minimumExampleCountPerLeaf
            )
        )
        :> IEstimator<_>)
```

- `multiclass` の型引数 `'T` には、`Append` が求める制約（`ITransformer` を実装したクラスであること）が型推論で付きます。`'T` を書いたのは引数の型注釈の中だけで、制約は書いていません
- `multiclass` の戻り値は、`fun x t -> ... fun newX -> ...` と 2 段のラムダ式です。1 段目で学習し、2 段目の関数が `model` と `context` を覚えている **クロージャ** になります。`Classifier<string>` の形そのものです
- 予測するデータにはラベルが無いので、`toRows newX (newX |> List.map (fun _ -> ""))` と空のラベルを付けて同じ形のレコードにしています
- `toVector` と `schemaFor` は、第 3 章の `MlNetAdapter` の関数と同じ内容です。第 3 章では `private` にしていたので、第 3 章のファイルを変えずに済むよう、この章のモジュールにも書きました

```text
テストの実行の概要: 成功!
```

### ML.NET の設定と自作との違い

ML.NET の既定の設定は、自作と次の点が違います。

| 項目 | 自作 | ML.NET |
|------|------|--------|
| ロジスティック回帰の最適化 | バッチ勾配降下法（学習率 1.0）、5000 回 | L-BFGS（勾配の履歴から曲がり具合を近似する準ニュートン法） |
| ロジスティック回帰の正則化 | なし | L1・L2 正則化がどちらも 1（`l1Regularization`・`l2Regularization` の既定値） |
| ランダムフォレストの多クラス化 | 決定木そのものが多クラスを扱う | FastForest は 2 クラス用なので、品種ごとに「その品種か、それ以外か」の森を作る（OneVersusAll） |
| 決定木を分割する最小の件数 | 1 件になるまで分ける | `minimumExampleCountPerLeaf` の既定値 10（葉ごとに最低 10 件） |
| 葉の数 | 制限なし | `numberOfLeaves` の既定値 20 |

**正則化** は、重みが大きくなりすぎないように、損失に重みの大きさの罰則を足す仕組みです。訓練データへの当てはまりを少し犠牲にして、過学習を抑えます。第 12 章で詳しく扱います。

## 10.9 リファクタリング

ここまでで、TODO リストは実データでの突き合わせを残すだけになりました。

- `sampleOf` の切り出しは、10.6 節で `forestImportances` を書く前に済ませました。同じ処理が 2 か所に現れる前に切り出したので、重複は一度も生まれていません
- Kotlin 版は、detekt の指摘（スプレッド演算子による配列のコピー）でデータフレームの列の選び方を書き直しました。F# 版では `Map.filter` で列を選んでいるので、該当する書き直しはありません

整形と静的解析を実行して、警告が無いことを確かめます。

```bash
dotnet fantomas .
dotnet fsharplint lint MachineLearning.sln
```

```text
========== Summary: 0 warnings ==========
```

Fantomas は、`{ defaults with NEstimators = 5; MaxFeatures = 1 }` のように 1 行に書いた copy-and-update の式を、フィールドごとの行に展開しました。この記事のコードは、整形した後のものです。

## 10.10 実データで突き合わせる

### モデルを比べる

`Main.run` で、iris のテストデータでの正解率と、ランダムフォレストの特徴量の重要度を表示します。

```fsharp
// src/MachineLearning/Chapter10/Main.fs
module MachineLearning.Chapter10.Main

open System.IO
open MachineLearning.Dataset
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter03
open MachineLearning.Chapter10.Classifier
open MachineLearning.Chapter10.FeatureImportance
open MachineLearning.Chapter10.MlNetClassifier

[<Literal>]
let TestSize = 0.3

[<Literal>]
let Seed = 0

[<Literal>]
let NEstimators = 100

[<Literal>]
let ShallowDepth = 2

/// ML.NET の LbfgsMaximumEntropy の L1・L2 正則化の既定値
[<Literal>]
let MlNetRegularization = 1.0f

/// ML.NET の FastForest の既定値（葉ごとに最低 10 件）
[<Literal>]
let MlNetMinimumExampleCountPerLeaf = 10

let forestSettings: RandomForest.Settings =
    { RandomForest.defaults with
        NEstimators = NEstimators
        MaxFeatures = 2
        Seed = Seed
    }

/// 表示名と分類器の組
let models: (string * Classifier<string>) list =
    [
        $"決定木（深さ {ShallowDepth}）", ofModel (DecisionTree.fit (Some ShallowDepth)) DecisionTree.predict
        "ロジスティック回帰", ofModel (LogisticRegression.fit LogisticRegression.defaults) LogisticRegression.predict
        $"ランダムフォレスト（{NEstimators} 本）", ofModel (RandomForest.fit forestSettings) RandomForest.predict
        $"ランダムフォレスト（{NEstimators} 本・深さ {ShallowDepth}）",
        ofModel
            (RandomForest.fit
                { forestSettings with
                    MaxDepth = Some ShallowDepth
                })
            RandomForest.predict
        "ML.NET LbfgsMaximumEntropy", lbfgsMaximumEntropy MlNetRegularization MlNetRegularization
        $"ML.NET FastForest（{NEstimators} 本）", fastForest NEstimators MlNetMinimumExampleCountPerLeaf
    ]

/// iris.csv でモデルごとの正解率と、ランダムフォレストの特徴量の重要度を表示する
let run (print: string -> unit) : unit =
    let split = prepareIris (Path.Combine(dataDir (), "iris.csv")) TestSize Seed
    print "モデル\t訓練データ\tテストデータ"

    for name, classifier in models do
        let score = evaluate classifier split
        print $"{name}\t{score.Train:F4}\t{score.Test:F4}"

    let forest = RandomForest.fit forestSettings split.XTrain split.TTrain
    print ""
    print $"ランダムフォレスト（{NEstimators} 本）の特徴量の重要度:"

    for KeyValue(feature, importance) in forestImportances forest split.XTrain split.TTrain do
        print $"{feature}\t{importance:F4}"
```

- `models` の型は `(string * Classifier<string>) list` です。自作のモデルか ML.NET かを気にせず、`for name, classifier in models do` のループで同じ `evaluate` を呼べます
- `for KeyValue(feature, importance) in ... do` の `KeyValue` は、`Map` の要素（キーと値の組）をパターンで分解する **アクティブパターン** です
- 学習の設定は、`forestSettings` を 1 つ作り、深さを制限する森は `{ forestSettings with MaxDepth = Some ShallowDepth }` で作っています

`Program.fs` の対応表に `"chapter10", Chapter10.Main.run` を加えて実行します。

```bash
dotnet run --project src/MachineLearning -- chapter10
```

```text
モデル	訓練データ	テストデータ
決定木（深さ 2）	0.9619	0.8889
ロジスティック回帰	0.9524	0.8667
ランダムフォレスト（100 本）	1.0000	0.8889
ランダムフォレスト（100 本・深さ 2）	0.9619	0.8889
ML.NET LbfgsMaximumEntropy	0.9238	0.8444
ML.NET FastForest（100 本）	0.9714	0.9111

ランダムフォレスト（100 本）の特徴量の重要度:
がく片幅	0.0982
がく片長さ	0.2055
花弁幅	0.4929
花弁長さ	0.2034
```

重要度は `Map` のキーの順（文字列の順）に並ぶので、Kotlin 版とは表示の順が違います。訓練データに入った行も他の言語の版と違うので、正解率の値は他の版と一致しません。

結果から読み取れることは次のとおりです。

- **アンサンブルがいつも勝つわけではない**。深さを制限しないランダムフォレスト（0.8889）は、第 3 章の深さ 2 の決定木（0.8889）と同じで、上回っていません。木 1 本 1 本が訓練データを分け切るまで深く育ち（訓練データの正解率 1.0000）、iris のような小さなデータでは多数決でも過学習を打ち消し切れていません
- **テストデータ 45 件では、1 件の違いが 0.0222 の差になる**。最も高い ML.NET の FastForest（0.9111）と、自作のロジスティック回帰（0.8667）の差は 2 件です。1 回の分割で測った正解率の小さな差でモデルの優劣を決めるのは危険です。分け方を変えて何度も測る交差検証は第 11 章で扱います
- **ランダムフォレストの重要度は、決定木より複数の特徴量に分散する**。木ごとに使える特徴量を 2 つに絞っているので、花弁幅を使えない木は別の特徴量で分割するためです。深さ 3 の決定木では、花弁幅の重要度は 0.9590 でした（10.11 節の Notebook）

### 実データのテスト

値は、実装を実データで動かして得たものをテストで固定しました（Red を経ていません）。

```fsharp
// tests/MachineLearning.Tests/Chapter10/IrisDataTest.fs
let irisSplit () = prepareIris csvFile 0.3 0

let logisticRegression =
    ofModel (LogisticRegression.fit LogisticRegression.defaults) LogisticRegression.predict

[<Fact>]
let ``ロジスティック回帰はテストデータの 45 件中 39 件を正しく分類する`` () =
    requireData ()

    let score = evaluate logisticRegression (irisSplit ())

    Assert.Equal(39.0 / 45.0, score.Test, 12)

[<Fact>]
let ``ランダムフォレストは訓練データを分け切りテストデータの 45 件中 40 件を正しく分類する`` () =
    requireData ()

    let forest =
        ofModel
            (RandomForest.fit
                { RandomForest.defaults with
                    NEstimators = 100
                })
            RandomForest.predict

    let score = evaluate forest (irisSplit ())

    Assert.Equal(1.0, score.Train)
    Assert.Equal(40.0 / 45.0, score.Test, 12)

[<Fact>]
let ``ML.NET のロジスティック回帰は正則化を 0 にすると自作と同じ正解率になる`` () =
    requireData ()

    Assert.Equal(evaluate logisticRegression (irisSplit ()), evaluate (lbfgsMaximumEntropy 0.0f 0.0f) (irisSplit ()))

[<Fact>]
let ``ML.NET の FastForest は葉の最小件数を 1 にすると訓練データを分け切る`` () =
    requireData ()

    let score = evaluate (fastForest 100 1) (irisSplit ())

    Assert.Equal(1.0, score.Train)
    Assert.Equal(41.0 / 45.0, score.Test, 12)
```

`Score` はレコードなので、3 つ目のテストでは訓練データとテストデータの正解率の組をまとめて比べています。表示のテストも、第 3 章と同じく `Main.run` の出力をまるごと比べます（完成したテストファイルにあります）。

### ML.NET と突き合わせる

**ロジスティック回帰**: ML.NET の既定（正則化あり）は、訓練データ 0.9238・テストデータ 0.8444 で、自作より低くなりました。正則化の強さだけを変えて実測しました。

| L1 正則化 | L2 正則化 | 訓練データ | テストデータ |
|----------|----------|-----------|-------------|
| 1（既定） | 1（既定） | 0.9238 | 0.8444 |
| 0 | 1 | 0.9333 | 0.8222 |
| 1 | 0 | 0.9429 | 0.8444 |
| 0 | 0.1 | 0.9333 | 0.8444 |
| 0 | 0.01 | 0.9524 | 0.8444 |
| 0 | 0 | 0.9524 | 0.8667 |

正則化を 0 にすると、自作（5000 回）と同じ訓練 0.9524・テスト 0.8667 になりました。この組み合わせはテストで固定しています。最適化の方法（L-BFGS と勾配降下法）が違っても、同じ損失を最小にしているので、正解率がそろいます。正則化は過学習を抑えるための仕組みですが、この iris の分割では、正則化を強くしてもテストデータの正解率は上がりませんでした。

自作のほうも繰り返し回数を変えて確かめました。

| 繰り返し回数 | 訓練データ | テストデータ |
|-------------|-----------|-------------|
| 100 | 0.9143 | 0.8667 |
| 1000 | 0.9429 | 0.8444 |
| 5000（既定） | 0.9524 | 0.8667 |
| 20000 | 0.9524 | 0.8667 |

5000 回と 20000 回で正解率が変わらないので、既定の繰り返し回数は Python 版と同じ 5000 回のままにしています。

**ランダムフォレスト**: ML.NET の FastForest は、テストデータで 0.9111 と自作（0.8889）より 1 件多く当てましたが、訓練データでは 0.9714 と分け切っていません。10.8 節の表の `minimumExampleCountPerLeaf`（既定値 10）が原因ではないかと考え、値を変えて確かめました。

| 葉ごとの最小件数 | 訓練データ | テストデータ |
|----------------|-----------|-------------|
| 1 | 1.0000 | 0.9111 |
| 2 | 1.0000 | 0.9111 |
| 5 | 0.9714 | 0.9111 |
| 10（既定） | 0.9714 | 0.9111 |

1 にすると訓練データを分け切りました。葉ごとに最低 10 件を求める既定の設定が、1 本 1 本の木の深さを抑えていたのです。テストデータの正解率はどの設定でも 0.9111 でした。

同じ「ランダムフォレスト」という名前でも、多クラスの扱い方（1 つの森か、品種ごとの森か）や、分割を止める条件が違えば、正解率は変わります。ライブラリの結果と比べるときは、名前ではなく設定をそろえて比べます。

テストの実行結果です。

```bash
dotnet test
```

第 10 章のテストは 27 件すべて通ります。データが無い環境では、実データのテスト 5 件がスキップされ、残りの 22 件が通ります。

## 10.11 Notebook で探索する

Notebook は `apps/dotnet/notebooks/chapter10_ensemble_exploration.ipynb` にあります。先に `dotnet build` でプロジェクトをビルドしておきます。Polyglot Notebooks が廃止されていることは、[第 2 章の 2.10 節](02-data-preprocessing-and-triangulation.md) を参照してください。ML.NET を使うので、先頭のセルで ML.NET のパッケージも読み込みます。

```fsharp
#r "nuget: FSharp.Data, 8.2.0"
#r "nuget: Microsoft.ML, 5.0.0"
#r "nuget: Microsoft.ML.FastTree, 5.0.0"
#r "nuget: Plotly.NET, 5.1.0"
#r "nuget: Plotly.NET.Interactive, 5.0.0"
#r "../src/MachineLearning/bin/Debug/net10.0/MachineLearning.dll"
```

### モデルごとの正解率

```fsharp
Main.models
|> List.map (fun (name, classifier) ->
    let score = evaluate classifier split
    {| モデル = name; 訓練データ = score.Train; テストデータ = score.Test |})
|> List.toArray
```

`Main.run` と同じ `Main.models` を使うので、表の値は 10.10 節の表示と同じです。

表の列は、書いた順（モデル・訓練データ・テストデータ）ではなく、テストデータ・モデル・訓練データの順に並びました。F# の匿名レコードは、フィールドを名前の順に並べて型を作るためです。

### ロジスティック回帰の損失の推移

```fsharp
let logistic = LogisticRegression.fit LogisticRegression.defaults split.XTrain split.TTrain

Chart.Line(x = [ 1 .. logistic.Losses.Length ], y = logistic.Losses)
|> Chart.withTitle "勾配降下法の繰り返し回数と損失"
|> Chart.withXAxisStyle "繰り返し回数"
|> Chart.withYAxisStyle "交差エントロピー"
```

```fsharp
[ 1; 10; 100; 1000; 5000 ]
|> List.map (fun epoch -> {| 繰り返し回数 = epoch; 交差エントロピー = logistic.Losses[epoch - 1] |})
|> List.toArray
```

表は、Notebook の出力を小数第 3 位に丸めたものです。

| 繰り返し回数 | 交差エントロピー |
|-------------|----------------|
| 1 | 1.099 |
| 10 | 0.806 |
| 100 | 0.373 |
| 1000 | 0.173 |
| 5000 | 0.135 |

最初の損失 1.099 は、3 品種に均等な確率（1/3）を出したときの交差エントロピー（ln 3）です。重みがすべて 0 から始まるので、最初はどの品種にも同じ確率を出します。折れ線グラフでは、最初の 100 回で急に下がり、その後はゆっくり下がり続けます。

```fsharp
logistic.Features
|> List.mapi (fun f feature ->
    {| 特徴量 = feature
       setosa = logistic.Weights[f][0]
       versicolor = logistic.Weights[f][1]
       virginica = logistic.Weights[f][2] |})
|> List.toArray
```

`Weights[特徴量][品種]` から、品種ごとの列を組み立てています。品種は `List.sort` で並べたので、0 番目が Iris-setosa、1 番目が Iris-versicolor、2 番目が Iris-virginica です。表は小数第 3 位に丸めたものです。

| 特徴量 | Iris-setosa | Iris-versicolor | Iris-virginica |
|--------|------------|-----------------|----------------|
| がく片幅 | 9.761 | -3.917 | -5.844 |
| がく片長さ | -5.635 | 3.318 | 2.317 |
| 花弁幅 | -13.736 | -1.673 | 15.408 |
| 花弁長さ | -5.067 | -2.331 | 7.398 |

重みの表は、決定木のルールと同じく「モデルが何を手がかりにしたか」を読むのに使えます。花弁幅の重みは setosa で大きく負、virginica で大きく正です。花弁幅が大きいほど virginica のスコアが上がり setosa のスコアが下がる、という第 3 章の決定木と同じ傾向を、ロジスティック回帰は連続的な重みとして学習しています。

### モデル別の特徴量の重要度

```fsharp
let tree = DecisionTree.fit (Some 3) split.XTrain split.TTrain
let forest = RandomForest.fit { RandomForest.defaults with NEstimators = 100 } split.XTrain split.TTrain

let importances =
    [
        "決定木（深さ 3）", treeImportances tree split.XTrain split.TTrain
        "ランダムフォレスト（100 本）", forestImportances forest split.XTrain split.TTrain
    ]

importances
|> List.map (fun (name, values) ->
    Chart.Column(keysValues = (values |> Map.toList), Name = name))
|> Chart.combine
|> Chart.withTitle "モデル別の特徴量の重要度"
|> Chart.withXAxisStyle "特徴量"
|> Chart.withYAxisStyle "重要度"
```

モデルごとに `Chart.Column`（縦棒グラフ）を作り、`Chart.combine` で重ねると、特徴量ごとに 2 本の棒が並びます。値を小数第 3 位に丸めて横に並べると、次のとおりです。

| 特徴量 | 決定木（深さ 3） | ランダムフォレスト（100 本） |
|--------|----------------|--------------------------|
| がく片幅 | 0.000 | 0.098 |
| がく片長さ | 0.000 | 0.205 |
| 花弁幅 | 0.959 | 0.493 |
| 花弁長さ | 0.041 | 0.203 |

2 つのモデルとも花弁幅が最も重要という点は共通しています。決定木は花弁幅に 9 割以上が集中するのに対し、ランダムフォレストは他の特徴量にも分散します。

### 森の大きさと正解率

```fsharp
let sizes = [ 1; 5; 10; 25; 50; 100 ]

let sizeScores =
    sizes
    |> List.map (fun n ->
        let mine =
            evaluate (ofModel (RandomForest.fit { RandomForest.defaults with NEstimators = n }) RandomForest.predict) split

        let library = evaluate (fastForest n Main.MlNetMinimumExampleCountPerLeaf) split
        n, mine, library)
```

表は、Notebook の出力を小数第 3 位に丸めたものです。Notebook では、この表の 3 つの列を折れ線グラフにも描いています。

| 木の数 | 自作（訓練データ） | 自作（テストデータ） | ML.NET（テストデータ） |
|-------|-----------------|------------------|--------------------|
| 1 | 0.857 | 0.733 | 0.578 |
| 5 | 0.962 | 0.778 | 0.889 |
| 10 | 0.990 | 0.800 | 0.911 |
| 25 | 1.000 | 0.889 | 0.911 |
| 50 | 1.000 | 0.889 | 0.911 |
| 100 | 1.000 | 0.889 | 0.911 |

自作の森は、木が 1 本のときテストデータの正解率が 0.733 にとどまり、木を増やすと上がって 25 本以降は横ばいです。自作の木は 1 本の中で使える特徴量が 2 つに固定されるので、花弁幅を含まない組み合わせを引いた木は弱くなります。ML.NET の森は、木が 1 本のときは 0.578 と自作より低く、10 本以降は 0.911 で横ばいです。どちらも、木を増やすほど多数決が安定し、一定の本数を超えると正解率が変わらなくなります。`evaluate` を使い回しているので、`List.map` で条件を変えるだけで自作と ML.NET を比べられます。

グラフの画像は、第 2 章と同じ理由で記事に載せていません。学習データを配置して、手元の Notebook で確認してください。

<details>
<summary>この章の完成コード（src/MachineLearning/Chapter10/LogisticRegression.fs）</summary>

```fsharp
module MachineLearning.Chapter10.LogisticRegression

/// スコアを確率に変換する。最大値を引いてから exp を計算して、大きな値でもあふれないようにする
let softmax (z: float[]) : float[] =
    let max = Array.max z
    let exps = z |> Array.map (fun value -> exp (value - max))
    let total = Array.sum exps
    exps |> Array.map (fun value -> value / total)

/// 確率が 0 のときに log 0 が負の無限大にならないように足す小さな値
[<Literal>]
let private Epsilon = 1e-12

/// 交差エントロピー。正解の品種の確率の対数の平均にマイナスを付けたもの
let crossEntropy (probabilities: float[][]) (targets: int[]) : float =
    -(Array.map2 (fun (p: float[]) target -> log (p[target] + Epsilon)) probabilities targets
      |> Array.average)

/// 学習の設定
type Settings = { LearningRate: float; Epochs: int }

let defaults = { LearningRate = 1.0; Epochs = 5000 }

/// 学習したロジスティック回帰のモデル
type LogisticModel<'L> =
    {
        /// 特徴量の列名（重みの行の順）
        Features: string list
        /// 品種（重みの列の順）
        Classes: 'L list
        /// Weights[特徴量][品種]
        Weights: float[][]
        /// Bias[品種]
        Bias: float[]
        /// 繰り返しごとの損失（交差エントロピー）
        Losses: float list
    }

/// 行の Map を、列名の順に並べた配列にする
let private toRow (features: string list) (row: Map<string, float>) : float[] =
    features |> List.map (fun feature -> row[feature]) |> List.toArray

/// 品種ごとの「特徴量の重み付きの和 + 切片」
let private scores (weights: float[][]) (bias: float[]) (row: float[]) : float[] =
    bias
    |> Array.mapi (fun k b -> b + (row |> Array.mapi (fun f value -> value * weights[f][k]) |> Array.sum))

/// バッチ勾配降下法で重みと切片を学習する
let fit (settings: Settings) (x: Map<string, float> list) (t: 'L list) : LogisticModel<'L> =
    let features =
        match x with
        | [] -> []
        | first :: _ -> first |> Map.keys |> Seq.toList

    let rows = x |> List.map (toRow features) |> List.toArray
    let classes = t |> List.distinct |> List.sort

    let targets =
        t |> List.map (fun label -> List.findIndex ((=) label) classes) |> List.toArray

    let n = float rows.Length

    let step (weights: float[][], bias: float[], losses: float list) _ =
        let probabilities = rows |> Array.map (scores weights bias >> softmax)

        // 確率 − 正解（正解の品種だけ 1 を引く）
        let errors =
            probabilities
            |> Array.mapi (fun i p -> p |> Array.mapi (fun k value -> if k = targets[i] then value - 1.0 else value))

        let gradient f k =
            Array.map2 (fun (row: float[]) (error: float[]) -> row[f] * error[k]) rows errors
            |> Array.sum

        let newWeights =
            weights
            |> Array.mapi (fun f w ->
                w
                |> Array.mapi (fun k value -> value - settings.LearningRate * gradient f k / n))

        let newBias =
            bias
            |> Array.mapi (fun k value ->
                value
                - settings.LearningRate * (errors |> Array.sumBy (fun error -> error[k])) / n)

        newWeights, newBias, crossEntropy probabilities targets :: losses

    let initial =
        Array.init features.Length (fun _ -> Array.zeroCreate classes.Length), Array.zeroCreate classes.Length, []

    let weights, bias, losses = List.fold step initial [ 1 .. settings.Epochs ]

    {
        Features = features
        Classes = classes
        Weights = weights
        Bias = bias
        Losses = List.rev losses
    }

/// スコアが最大の品種を予測する（ソフトマックスは大小関係を変えないので確率は計算しない）
let predict (model: LogisticModel<'L>) (x: Map<string, float> list) : 'L list =
    x
    |> List.map (fun row ->
        scores model.Weights model.Bias (toRow model.Features row)
        |> Array.indexed
        |> Array.maxBy snd
        |> fst
        |> fun k -> model.Classes[k])
```

</details>

<details>
<summary>この章の完成コード（src/MachineLearning/Chapter10/RandomForest.fs）</summary>

```fsharp
module MachineLearning.Chapter10.RandomForest

open System
open MachineLearning.Chapter02.Random
open MachineLearning.Chapter03

/// 木ごとの予測のリストから、サンプルごとに最も多い予測を選ぶ
let majorityVote (votes: 'L list list) : 'L list =
    votes |> List.transpose |> List.map DecisionTree.majority

/// シードを使って、0 以上 size 未満の行番号を size 個、重複を許して選ぶ
let bootstrapSample (seed: int) (size: int) : int list =
    let random = Random seed
    List.init size (fun _ -> random.Next size)

/// シードで並べ替えた先頭 maxFeatures 個の特徴量を、元の列の順で返す
let chooseFeatures (seed: int) (maxFeatures: int) (features: string list) : string list =
    let chosen = features |> shuffle seed |> List.truncate maxFeatures |> Set.ofList
    features |> List.filter chosen.Contains

/// 学習の設定
type Settings =
    {
        NEstimators: int
        MaxFeatures: int
        MaxDepth: int option
        Seed: int
    }

let defaults =
    {
        NEstimators = 10
        MaxFeatures = 2
        MaxDepth = None
        Seed = 0
    }

/// 1 本分の学習結果。使った列と、ブートストラップ標本の行番号と、学習した木
type FittedTree<'L> =
    {
        Columns: string list
        Rows: int list
        Tree: DecisionTree.Tree<'L>
    }

/// 森のシードから、木ごとに「ブートストラップ標本のシード」と「特徴量を選ぶシード」の組を作る
let private treeSeeds (seed: int) (count: int) : (int * int) list =
    let random = Random seed

    List.init count (fun _ ->
        let rowSeed = random.Next()
        let featureSeed = random.Next()
        rowSeed, featureSeed)

/// 行番号と列名で、1 本分の学習データを取り出す。同じ行番号が重複していれば、その回数だけ行が並ぶ
let sampleOf (rows: int list) (columns: string list) (x: Map<string, float> list) (t: 'L list) =
    let xs = List.toArray x
    let ts = List.toArray t

    let sampleX =
        rows
        |> List.map (fun row -> xs[row] |> Map.filter (fun column _ -> List.contains column columns))

    sampleX, rows |> List.map (fun row -> ts[row])

/// ブートストラップ標本と特徴量の部分集合で、第 3 章の決定木を NEstimators 本学習する
let fit (settings: Settings) (x: Map<string, float> list) (t: 'L list) : FittedTree<'L> list =
    let features = x.Head |> Map.keys |> Seq.toList

    treeSeeds settings.Seed settings.NEstimators
    |> List.map (fun (rowSeed, featureSeed) ->
        let rows = bootstrapSample rowSeed x.Length
        let columns = chooseFeatures featureSeed settings.MaxFeatures features
        let sampleX, sampleT = sampleOf rows columns x t

        {
            Columns = columns
            Rows = rows
            Tree = DecisionTree.fit settings.MaxDepth sampleX sampleT
        })

/// 木ごとに予測して多数決する。第 3 章の木は分割に使った列だけを見るので、列を絞らずに渡せる
let predict (forest: FittedTree<'L> list) (x: Map<string, float> list) : 'L list =
    forest
    |> List.map (fun fitted -> DecisionTree.predict fitted.Tree x)
    |> majorityVote
```

</details>

<details>
<summary>この章の完成コード（src/MachineLearning/Chapter10/FeatureImportance.fs）</summary>

```fsharp
module MachineLearning.Chapter10.FeatureImportance

open MachineLearning.Chapter03.DecisionTree
open MachineLearning.Chapter10.RandomForest

/// 学習に使ったデータをもう一度木に流して、節ごとに「分割に使った特徴量と、件数で重み付けした不純度の減少量」を集める
let rec private impurityDecreases (tree: Tree<'L>) (x: Map<string, float> list) (t: 'L list) : (string * float) list =
    match tree with
    | Leaf _ -> []
    | Node(split, left, right) ->
        let goesLeft (row: Map<string, float>, _) = row[split.Feature] <= split.Threshold
        let leftPart, rightPart = List.zip x t |> List.partition goesLeft

        let decreasesOf subtree part =
            impurityDecreases subtree (List.map fst part) (List.map snd part)

        (split.Feature, float t.Length * (gini t - split.Impurity))
        :: decreasesOf left leftPart
        @ decreasesOf right rightPart

/// 合計が 1 になるように割合にする。合計が 0 ならそのまま返す
let private normalize (totals: Map<string, float>) : Map<string, float> =
    let total = totals |> Map.values |> Seq.sum

    if total = 0.0 then
        totals
    else
        totals |> Map.map (fun _ value -> value / total)

/// 決定木 1 本の特徴量の重要度
let treeImportances (tree: Tree<'L>) (x: Map<string, float> list) (t: 'L list) : Map<string, float> =
    let decreases = impurityDecreases tree x t

    x.Head
    |> Map.map (fun feature _ ->
        decreases
        |> List.filter (fun (splitFeature, _) -> splitFeature = feature)
        |> List.sumBy snd)
    |> normalize

/// ランダムフォレストの特徴量の重要度。木ごとの重要度（学習に使ったブートストラップ標本で計算）を平均し、割合にする
let forestImportances (forest: FittedTree<'L> list) (x: Map<string, float> list) (t: 'L list) : Map<string, float> =
    let perTree =
        forest
        |> List.map (fun fitted ->
            let sampleX, sampleT = sampleOf fitted.Rows fitted.Columns x t
            treeImportances fitted.Tree sampleX sampleT)

    x.Head
    |> Map.map (fun feature _ -> perTree |> List.averageBy (Map.tryFind feature >> Option.defaultValue 0.0))
    |> normalize
```

</details>

<details>
<summary>この章の完成コード（src/MachineLearning/Chapter10/Classifier.fs）</summary>

```fsharp
module MachineLearning.Chapter10.Classifier

open MachineLearning.Chapter01.KinokoTakenoko
open MachineLearning.Chapter02.IrisPreprocessing

/// 分類器。訓練データ（特徴量と正解ラベル）を受け取り、予測する関数を返す関数
type Classifier<'L> = Map<string, float> list -> 'L list -> (Map<string, float> list -> 'L list)

/// 「学習してモデルを返す関数」と「モデルで予測する関数」を組み合わせて分類器にする
let ofModel
    (fit: Map<string, float> list -> 'L list -> 'M)
    (predict: 'M -> Map<string, float> list -> 'L list)
    : Classifier<'L> =
    fun x t -> predict (fit x t)

/// 訓練データとテストデータの正解率
type Score = { Train: float; Test: float }

/// 訓練データで学習させてから、訓練データとテストデータの正解率を求める
let evaluate (classifier: Classifier<'L>) (split: TrainTestSplit<Map<string, float>, 'L>) : Score =
    let predict = classifier split.XTrain split.TTrain

    {
        Train = accuracy (predict split.XTrain) split.TTrain
        Test = accuracy (predict split.XTest) split.TTest
    }
```

</details>

## 10.12 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、共通の型で ML.NET と並べて評価しました。

1. **数値として正しい実装** — ソフトマックス関数は、定義どおりでは大きな値で `NaN` になった。.NET も警告を出さないので、境界の値のテストで見つけ、最大値を引く方法で直した
2. **書き換えない勾配降下法** — 1 回分の更新を関数にし、`List.fold` で繰り返した。重みは毎回新しい配列として作り、損失はリストの先頭に積んだ。学習の設定は `{ defaults with ... }` で変えた
3. **シードを受け取る純粋な関数** — ブートストラップ標本・特徴量の選択・木ごとのシードを、シードから結果が決まる関数にした。同じシードの森はレコードとしてまるごと等しい
4. **同点のときの選び方** — 特徴量の重要度の手計算の例は、F# 版の決定木が `Map` のキーの順に特徴量を調べるため、Kotlin 版と違う木になった。同点にならないデータに変えて、言語に左右されないテストにした
5. **関数の型による共通化** — 分類器を「訓練データを受け取り、予測する関数を返す関数」の型にした。第 3 章の決定木も ML.NET の学習器も、部分適用と `ofModel` で同じ `evaluate` に渡せた。ML.NET の学習器の型は、F# が共変性を使わないので `:> IEstimator<_>` でアップキャストした
6. **設定をそろえて突き合わせる** — ML.NET のロジスティック回帰は正則化、FastForest は葉ごとの最小件数が自作と違った。設定を変えて実測し、正則化を 0 にすると自作と同じ正解率になることをテストに残した

iris のテストデータ 45 件では、どのモデルも数件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。
