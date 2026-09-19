---
type: Article
title: "第 11 章: 評価指標と交差検証"
description: "混同行列から適合率・再現率・F 値を求め、評価関数を関数の型 Metric<'T> として渡す。K 分割交差検証のスコアを seq 式で必要な分だけ計算し、同じ正解と予測を ML.NET の評価に渡して自作の指標と突き合わせる。"
tags: [article,getting-start-ml,fsharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T08:07:37Z }
---

# 第 11 章: 評価指標と交差検証

## 11.1 はじめに

ここまでの章では、分類の良し悪しを正解率で、回帰の良し悪しを決定係数などで測ってきました。この章では、評価の道具を 2 つ加えます。

- **評価指標**: 正解率だけでは見えない「正例をどれだけ拾えたか」を、混同行列・適合率・再現率・F 値で測る
- **K 分割交差検証**: 1 回だけの分割に頼らず、データを K 個に分けてテストデータを入れ替えながら評価する

[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md)・[Kotlin 版の第 11 章](../kotlin/11-evaluation-metrics-and-cross-validation.md)・[TypeScript 版の第 11 章](../typescript/11-evaluation-metrics-and-cross-validation.md) と同じ TODO リストで進めます。F# 版では、次の 3 点に注目してください。

- 評価関数を **関数の型**（`Metric<'T>`）で表し、引数として差し替える
- 交差検証のスコアを **`seq` 式** で書き、取り出した分だけ学習と評価を行う
- 同じ正解と予測を ML.NET の評価に渡し、自作の指標と **突き合わせる**

## 11.2 正解率だけでは足りない理由

タイタニック号の乗客データ（`Survived.csv`）では、乗客の約 6 割が亡くなっています。「全員死亡」と予測するだけで、正解率は約 6 割になります。けれども、このモデルは生存者を 1 人も見つけられません。

生存を **正例** として、予測の当たり外れを 4 つに分けて数えたものが **混同行列** です。

| | 生存と予測 | 死亡と予測 |
|--|-----------|-----------|
| 実際は生存 | TP（真陽性） | FN（偽陰性） |
| 実際は死亡 | FP（偽陽性） | TN（真陰性） |

混同行列から、次の指標を求めます。

- **適合率** = TP / (TP + FP): 生存と予測したうち、本当に生存していた割合
- **再現率** = TP / (TP + FN): 本当に生存していたうち、生存と予測できた割合
- **F 値**: 適合率と再現率の調和平均。どちらかが低いと大きく下がる

「全員死亡」のモデルは、TP が 0 なので再現率が 0 になります。

## 11.3 TODO リストの作成

**TODO リスト**:

- [ ] 混同行列を数える
- [ ] 適合率・再現率・F 値を求める
  - [ ] 分母が 0 のときは 0 にする
- [ ] 回帰の評価指標（MSE・RMSE・MAE）を求める
- [ ] K 分割交差検証の分け方を作る
- [ ] 評価関数を差し替えて交差検証する
  - [ ] 必要な分だけ学習する
- [ ] ML.NET の評価と突き合わせる
- [ ] 実データで評価する

## 11.4 混同行列を数える

```fsharp
// tests/MachineLearning.Tests/Chapter11/MetricsTest.fs
[<Fact>]
let ``正例と負例の予測の当たり外れを数える`` () =
    let actual = [ 1; 1; 1; 0; 0 ]
    let predicted = [ 1; 1; 0; 1; 0 ]

    Assert.Equal({ TP = 2; FP = 1; FN = 1; TN = 1 }, confusionMatrix 1 actual predicted)

[<Fact>]
let ``どちらのラベルを正例とするかで数え方が変わる`` () =
    let actual = [ 1; 1; 1; 0; 0; 0 ]
    let predicted = [ 1; 0; 0; 0; 0; 1 ]

    Assert.Equal({ TP = 2; FP = 2; FN = 1; TN = 1 }, confusionMatrix 0 actual predicted)

[<Fact>]
let ``正解と予測の件数が違えばエラーになる`` () =
    Assert.Throws<ArgumentException>(fun () -> confusionMatrix 1 [ 1; 0; 1 ] [ 1; 0 ] |> ignore)
    |> ignore
```

```fsharp
// src/MachineLearning/Chapter11/Metrics.fs
/// 混同行列。正例についての当たり外れの件数
type ConfusionMatrix = { TP: int; FP: int; FN: int; TN: int }

/// 正解と予測を「実際は正例か、正例と予測したか」の組にして、組ごとに数える
let confusionMatrix (positive: 'T) (actual: 'T list) (predicted: 'T list) : ConfusionMatrix =
    let outcomes = List.map2 (fun a p -> a = positive, p = positive) actual predicted

    let count outcome =
        outcomes |> List.filter ((=) outcome) |> List.length

    {
        TP = count (true, true)
        FP = count (false, true)
        FN = count (true, false)
        TN = count (false, false)
    }
```

- 正解と予測を `(実際は正例か, 正例と予測したか)` という `bool` の組にします。4 つの場合は、組の値そのものです
- `((=) outcome)` は、演算子 `=` を関数として使い、`outcome` を先に渡したものです。「`outcome` と等しいか」を調べる関数になります
- ラベルの型は `'T` です。文字列の `"1"` でも、整数の `1` でも、判別共用体でも数えられます
- 件数の違う 2 つのリストを渡すと、`List.map2` が `ArgumentException` を投げます。TypeScript 版では、件数の確認を自分で書きました。F# 版では、標準ライブラリの関数がその約束を守ってくれます

## 11.5 適合率・再現率・F 値

```fsharp
let cm = { TP = 3; FP = 1; FN = 2; TN = 4 }

[<Fact>]
let ``適合率は正例と予測したうち本当に正例だった割合`` () = Assert.Equal(0.75, precision cm, 12)

[<Fact>]
let ``再現率は本当の正例のうち正例と予測できた割合`` () = Assert.Equal(0.6, recall cm, 12)

[<Fact>]
let ``正例を 1 件も当てられなければ適合率と再現率と F 値は 0`` () =
    let missed = { TP = 0; FP = 0; FN = 3; TN = 5 }

    Assert.Equal((0.0, 0.0, 0.0), (precision missed, recall missed, f1Score missed))
```

```fsharp
/// 割り算。分母が 0 なら 0 を返す
let private ratio (numerator: float) (denominator: float) : float =
    if denominator = 0.0 then 0.0 else numerator / denominator

/// 適合率。正例と予測したうち、本当に正例だった割合
let precision (cm: ConfusionMatrix) : float = ratio (float cm.TP) (float (cm.TP + cm.FP))

/// 再現率。本当の正例のうち、正例と予測できた割合
let recall (cm: ConfusionMatrix) : float = ratio (float cm.TP) (float (cm.TP + cm.FN))

/// F 値。適合率と再現率の調和平均
let f1Score (cm: ConfusionMatrix) : float =
    let p = precision cm
    let r = recall cm
    ratio (2.0 * p * r) (p + r)
```

- 正例と 1 件も予測しないと、適合率の分母（TP + FP）が 0 になります。`float` の `0.0 / 0.0` は例外にならず `NaN`（非数）になり、そのまま平均などに混ざると結果がすべて `NaN` になります。`ratio` で分母が 0 のときは 0 を返し、scikit-learn の既定と同じ扱いにします
- 3 つの指標を 1 つのテストで確かめるときは、組 `(0.0, 0.0, 0.0)` にしてまとめて比べています

## 11.6 回帰の評価指標

回帰では、予測と正解の差（誤差）の大きさを測ります。

```fsharp
/// 平均二乗誤差（MSE）
let meanSquaredError (actual: float list) (predicted: float list) : float =
    List.map2 (fun a p -> (p - a) * (p - a)) actual predicted |> List.average

/// 平均二乗誤差の平方根（RMSE）。正解と同じ単位になる
let rootMeanSquaredError (actual: float list) (predicted: float list) : float =
    meanSquaredError actual predicted |> sqrt

/// 平均絶対誤差（MAE）
let meanAbsoluteError (actual: float list) (predicted: float list) : float =
    List.map2 (fun a p -> abs (p - a)) actual predicted |> List.average
```

2 乗してから平均する RMSE は、大きく外れた予測を重く数えます。テストで確かめます。

```fsharp
[<Fact>]
let ``大きく外れた予測があると RMSE は MAE より大きく増える`` () =
    let actual = [ 3.0; 5.0; 8.0; 10.0 ]
    let predicted = [ 2.0; 5.0; 10.0; 30.0 ]

    Assert.Equal(sqrt 101.25, rootMeanSquaredError actual predicted, 12)
    Assert.Equal(5.75, meanAbsoluteError actual predicted, 12)
```

1 件だけ 20 外れた予測があると、MAE は 5.75 ですが、RMSE は約 10.06 になります。外れ値に敏感な評価をしたいときは RMSE を、そうでないときは MAE を選びます。

## 11.7 K 分割交差検証

### なぜ分割を入れ替えるのか

訓練データとテストデータに 1 回だけ分けて評価すると、たまたまどの行がテストデータに入ったかで結果が揺れます。**K 分割交差検証** は、データを K 個のまとまりに分け、それぞれを 1 回ずつテストデータにして K 回評価し、その平均をとります。すべての行が、ちょうど 1 回だけテストデータになります。

```fsharp
// tests/MachineLearning.Tests/Chapter11/CrossValidationTest.fs
let testSizes (folds: Fold list) : int list =
    folds |> List.map (fun fold -> fold.Test.Length)

[<Fact>]
let ``データを k 個のテストデータにほぼ均等に分ける`` () =
    Assert.Equal<int list>([ 4; 3; 3 ], kFold 3 0 10 |> testSizes)

[<Fact>]
let ``件数と分割数が変わってもほぼ均等に分ける`` () =
    Assert.Equal<int list>([ 4; 3 ], kFold 2 0 7 |> testSizes)

[<Fact>]
let ``どの行もちょうど一度だけテストデータになる`` () =
    let folds = kFold 3 0 10

    Assert.Equal<int list>([ 0..9 ], folds |> List.collect (fun fold -> fold.Test) |> List.sort)

[<Fact>]
let ``各分割の訓練データはテストデータ以外のすべての行`` () =
    for fold in kFold 3 0 10 do
        Assert.Empty(Set.intersect (set fold.Train) (set fold.Test))
        Assert.Equal<Set<int>>(set [ 0..9 ], set (fold.Train @ fold.Test))
```

同じシードなら同じ分け方になり、違うシードなら違う分け方になることもテストします（完成したテストファイルにあります）。

```fsharp
// src/MachineLearning/Chapter11/CrossValidation.fs
/// 1 回分の分け方。訓練データとテストデータの行番号
type Fold = { Train: int list; Test: int list }

/// 行番号をシードで並べ替えてから nSplits 個に分け、それぞれをテストデータ、残りを訓練データにする
let kFold (nSplits: int) (seed: int) (nSamples: int) : Fold list =
    let positions = [ 0 .. nSamples - 1 ] |> shuffle seed

    positions
    |> List.splitInto nSplits
    |> List.map (fun test ->
        {
            Train = positions |> List.except test
            Test = test
        })
```

- 並べ替えには、第 2 章の `shuffle` をそのまま使います
- `List.splitInto` は、リストをほぼ同じ長さの `nSplits` 個に分けます。割り切れないときは、先頭のまとまりから 1 件ずつ多くなります（10 件を 3 つなら 4・3・3）
- `List.except` は、指定した要素を取り除いたリストを返します
- 分け方は **行番号** で表します。特徴量や正解ラベルの型によらずに、同じ分け方を使い回せます

## 11.8 評価関数を関数として渡す

### モデルと評価関数を関数の型で表す

交差検証の手順は、「分割ごとに、訓練データで学習し、テストデータを予測して採点する」です。学習の方法（モデル）と採点の方法（評価関数）を引数で受け取れば、どのモデルとどの指標の組み合わせにも同じ関数が使えます。

```fsharp
// src/MachineLearning/Chapter11/Metrics.fs
/// 評価関数。正解と予測のリストを受け取り、スコアを返す
type Metric<'T> = 'T list -> 'T list -> float

// src/MachineLearning/Chapter11/CrossValidation.fs
/// モデル。訓練データ（特徴量と正解）を受け取り、予測する関数を返す関数
type Model<'T> = Map<string, float> list -> 'T list -> (Map<string, float> list -> 'T list)
```

- `type Metric<'T> = ...` は、関数の型に名前を付ける **型の省略形** です。新しい型を作るのではなく、`'T list -> 'T list -> float` という関数の型を短く書けるようにします
- `Model<'T>` は、「学習して、予測する関数を返す関数」です。第 3 章の `fit` と `predict` をつなげた形です。TypeScript 版では `fit`・`predict` の 2 つのメソッドを持つインターフェースにしましたが、F# では 1 つの関数の型で表せます
- 第 1 章の `accuracy` も `'T list -> 'T list -> float` なので、そのまま `Metric<'T>` として使えます（引数の順は予測・正解ですが、正解率はどちらの順でも同じ値になります）

### 交差検証を seq 式で書く

```fsharp
/// 行番号のリストで、リストから要素を取り出す
let private pick (rows: int list) (items: 'A list) : 'A list =
    let array = List.toArray items
    rows |> List.map (fun row -> array[row])

/// 分割ごとに、訓練データで学習し、テストデータの予測を評価関数で採点する。
/// スコアは取り出すときに初めて計算する（取り出した分だけ学習する）
let crossValidate
    (model: Model<'T>)
    (metric: Metric<'T>)
    (folds: Fold list)
    (x: Map<string, float> list)
    (t: 'T list)
    : float seq =
    seq {
        for fold in folds do
            let predict = model (pick fold.Train x) (pick fold.Train t)
            yield metric (pick fold.Test t) (predict (pick fold.Test x))
    }
```

- `seq { ... }` は **シーケンス式** です。`yield` で 1 つずつ値を返すシーケンス（`seq<float>`）を作ります。中身は、値を取り出すときに初めて実行されます（**遅延評価**）
- `pick` は、行番号の順に要素を取り出します。リストの `[i]` は先頭からたどるので、いったん配列にしてから取り出します

テスト用に、訓練データの正解の平均を常に予測するモデルを作り、評価関数を差し替えて確かめます。

```fsharp
/// 訓練データの正解の平均値を常に予測するテスト用のモデル
let meanModel: Model<float> =
    fun _ t ->
        let mean = List.average t
        List.map (fun _ -> mean)

let folds =
    [ { Train = [ 0; 1 ]; Test = [ 2; 3 ] }; { Train = [ 2; 3 ]; Test = [ 0; 1 ] } ]

[<Fact>]
let ``分割ごとに訓練データで学習してテストデータを評価する`` () =
    Assert.Equal<float seq>([ 2.0; 2.0 ], crossValidate meanModel meanAbsoluteError folds x t)

[<Fact>]
let ``評価関数を差し替えると別の指標で評価する`` () =
    Assert.Equal<float seq>([ 4.25; 4.25 ], crossValidate meanModel meanSquaredError folds x t)
```

### 必要な分だけ学習する

遅延評価の効果を、学習した回数を数えるモデルで確かめます。

```fsharp
[<Fact>]
let ``最初の分割のスコアだけを取り出すなら学習は 1 回で済む`` () =
    let mutable trained = 0

    let counting: Model<float> =
        fun x t ->
            trained <- trained + 1
            meanModel x t

    crossValidate counting meanAbsoluteError folds x t |> Seq.head |> ignore

    Assert.Equal(1, trained)
```

`Seq.head` で最初のスコアだけを取り出すと、学習は 1 回しか行われません。一方、同じシーケンスから 2 回すべてのスコアを取り出すと、学習も 2 回ずつ（合わせて 4 回）行われます。シーケンスは結果を覚えていないためです。`Seq.cache` を通すと、1 回目に計算した値を覚えておき、2 回目は学習しません。この 2 つの振る舞いもテストに残しています。

- `let mutable` は、書き換えられる変数です。テストの中で「何回呼ばれたか」を数えるためだけに使っています
- TypeScript 版ではジェネレーター関数（`function*`）で同じことをしました。F# ではシーケンス式が言語に組み込まれています

### 混同行列の指標を評価関数に変える

適合率などは混同行列から求めるので、形は `ConfusionMatrix -> float` です。これを、正解と予測から求める `Metric<'T>` に変える関数を用意します。

```fsharp
/// 混同行列から求める指標と正例のラベルから、正解と予測から求める評価関数を作る
let classificationMetric (score: ConfusionMatrix -> float) (positive: 'T) : Metric<'T> =
    fun actual predicted -> confusionMatrix positive actual predicted |> score
```

`classificationMetric precision "1"` は、「生存（`"1"`）を正例とした適合率」を求める評価関数になります。関数を受け取って関数を返す **高階関数** で、指標と正例の組み合わせを自由に作れます。

## 11.9 ML.NET の評価と突き合わせる

自作の指標が正しいかを、ML.NET の評価で確かめます。ML.NET の評価は、正解（`Label`）・予測（`PredictedLabel`）・得点（`Score`）の列を持つデータを受け取ります。同じ正解と予測を渡して、結果を比べます。

```fsharp
// tests/MachineLearning.Tests/Chapter11/MlNetEvaluationTest.fs
let actual = [ "1"; "1"; "1"; "0"; "0"; "0"; "0"; "1" ]
let predicted = [ "1"; "1"; "0"; "1"; "0"; "0"; "0"; "0" ]

[<Fact>]
let ``2 値分類の適合率・再現率・F 値が ML.NET の評価と一致する`` () =
    let cm = confusionMatrix "1" actual predicted

    let library = evaluateBinary "1" actual predicted

    Assert.Equal(precision cm, library.PositivePrecision, 12)
    Assert.Equal(recall cm, library.PositiveRecall, 12)
    Assert.Equal(f1Score cm, library.F1Score, 12)

[<Fact>]
let ``回帰の MAE・RMSE が ML.NET の評価と一致する`` () =
    let actual = [ 1.0; 2.0; 4.0; 8.0 ]
    let predicted = [ 1.5; 2.0; 3.0; 10.0 ]

    let library = evaluateRegression actual predicted

    Assert.Equal(meanAbsoluteError actual predicted, library.MeanAbsoluteError, 9)
    Assert.Equal(rootMeanSquaredError actual predicted, library.RootMeanSquaredError, 9)
```

```text
error FS0039: 名前空間 'MlNetEvaluation' が定義されていません。
```

```fsharp
// src/MachineLearning/Chapter11/MlNetEvaluation.fs
module MachineLearning.Chapter11.MlNetEvaluation

open Microsoft.ML
open Microsoft.ML.Data

/// 2 値分類の評価に渡す 1 行。ML.NET は正解（Label）・予測（PredictedLabel）・得点（Score）の列を読む
[<CLIMutable>]
type BinaryOutcome =
    {
        Label: bool
        PredictedLabel: bool
        Score: float32
    }

/// 回帰の評価に渡す 1 行。予測は Score の列に入れる
[<CLIMutable>]
type RegressionOutcome = { Label: float32; Score: float32 }

/// 正解と予測を ML.NET の 2 値分類の評価に渡す。得点を持たない予測なので、確率を使わない評価（NonCalibrated）にする
let evaluateBinary (positive: 'T) (actual: 'T list) (predicted: 'T list) : BinaryClassificationMetrics =
    let context = MLContext(seed = 0)

    let rows =
        List.map2
            (fun a p ->
                {
                    Label = (a = positive)
                    PredictedLabel = (p = positive)
                    Score = if p = positive then 1.0f else -1.0f
                })
            actual
            predicted

    context.BinaryClassification.EvaluateNonCalibrated(context.Data.LoadFromEnumerable rows)
```

- 第 3 章と同じく、ML.NET に渡す行は `[<CLIMutable>]` のレコードにします
- 自作の決定木はラベルしか返さず、確率（得点）を持ちません。確率を使わない評価 `EvaluateNonCalibrated` を使い、得点には予測に合わせて 1 か −1 を入れます
- 回帰の評価も同じ形です（`Regression.Evaluate`。予測は `Score` の列に入れます）。レコードのフィールド名 `Label` が 2 つの型にあるので、`RegressionOutcome.Label = ...` のように型の名前を付けて、どちらのレコードかを示しています

```text
テストの実行の概要: 成功!
```

適合率・再現率・F 値は小数第 12 位まで、MAE・RMSE は小数第 9 位まで一致しました。回帰の桁が少ないのは、ML.NET が値を `float32`（単精度）で受け取るためです。

ML.NET には交差検証の関数（`CrossValidate`）もありますが、行の分け方は ML.NET が自分で決めます。自作の `kFold` と同じ分け方を渡す手段が無いので、交差検証の平均そのものは突き合わせず、1 回分の予測の採点が一致することを確かめるにとどめます。

## 11.10 実データで評価する

### CSV を読み込む

Survived と cinema を、型プロバイダで読み込みます。

```fsharp
// src/MachineLearning/Chapter11/Datasets.fs
/// 型プロバイダが列の名前と型を知るためのサンプル（架空の値）
[<Literal>]
let SurvivedSample =
    "PassengerId,Survived,Pclass,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked\n1,0,3,male,,0,0,T,0.5,,S"

/// 年齢には空欄があるので float option として読む
type SurvivedCsv = CsvProvider<SurvivedSample, Schema="Age=float option">
```

- `Schema="Age=float option"` のように `列名=型` と書くと、指定した列だけの型を決められます。第 2 章では全列の型を順に書きましたが、列が多い CSV ではこちらが便利です

### 簡略化した前処理

この章の関心は評価なので、前処理は他の版と同じく簡略化します。

```fsharp
/// 客室クラス・年齢・男性かどうかを特徴量にし、生存（"0" か "1"）を正解ラベルにする
let prepareSurvived (csv: SurvivedCsv) : Map<string, float> list * string list =
    let rows = csv.Rows |> Seq.toList

    let features =
        rows
        |> List.map (fun row ->
            Map.ofList
                [
                    "Pclass", Some(float row.Pclass)
                    "Age", row.Age
                    "male", Some(if row.Sex = "male" then 1.0 else 0.0)
                ])

    fillMissing (columnMeans features) features, rows |> List.map (fun row -> string row.Survived)
```

- 欠損値の補完には、第 2 章の `fillMissing` と `columnMeans` をそのまま使います
- ここでは交差検証の前に、データ全体の平均で補完しています。厳密には、分割ごとに訓練データの平均で補完すべきです（第 2 章で見たデータリーク）。他の版と同じく、評価の仕組みに集中するための簡略化です

### モデルを Model にする

決定木は、第 10 章の `ofModel` で、第 3 章の `fit` と `predict` をつないで `Model<string>` にします。線形回帰は、FSharp.Stats の最小二乗法を `Model<float>` にします。

```fsharp
// src/MachineLearning/Chapter11/Models.fs
/// 行の Map を、列名の順（Map のキーの順）に並べた値のリストにする
let private valuesOf (row: Map<string, float>) : float list = row |> Map.values |> Seq.toList

/// 最小二乗法の線形回帰（FSharp.Stats の LinearRegression.fit）
let linearRegression: Model<float> =
    fun x t ->
        let coefficients = LinearRegression.fit (matrix (x |> List.map valuesOf), vector t)

        List.map (valuesOf >> vector >> coefficients.Predict)
```

`valuesOf >> vector >> coefficients.Predict` は、「値のリストにする」「ベクトルにする」「予測する」の 3 つの関数を合成したものです。最初は `List.map (fun row -> coefficients.Predict(vector (valuesOf row)))` と書いていましたが、FSharpLint が FL0035（ラムダ式を関数の合成に置き換えられる）を指摘したので、合成に直しました。

### 交差検証の実験

```fsharp
// src/MachineLearning/Chapter11/Experiments.fs
/// 評価関数ごとに、K 分割交差検証のスコアの平均を求めて、評価関数の名前と組にする
let evaluate
    (nSplits: int)
    (seed: int)
    (model: Model<'T>)
    (metrics: (string * Metric<'T>) list)
    (x: Map<string, float> list)
    (t: 'T list)
    : (string * float) list =
    let folds = kFold nSplits seed x.Length

    metrics
    |> List.map (fun (name, metric) -> name, crossValidate model metric folds x t |> Seq.average)

let SurvivedMetrics: (string * Metric<string>) list =
    [
        "正解率", (fun actual predicted -> accuracy predicted actual)
        "適合率", classificationMetric precision Survived
        "再現率", classificationMetric recall Survived
        "F値", classificationMetric f1Score Survived
    ]

let CinemaMetrics: (string * Metric<float>) list =
    [ "RMSE", rootMeanSquaredError; "MAE", meanAbsoluteError ]

/// 深さ 2 の決定木を第 11 章のモデルにする
let decisionTree: Model<string> =
    ofModel (DecisionTree.fit (Some TreeDepth)) DecisionTree.predict
```

- 評価関数は `(名前, 評価関数)` の組のリストで渡します。関数も値なので、リストに入れられます
- 分け方（`folds`）は 1 回だけ作り、すべての評価関数で同じ分け方を使います

```bash
dotnet run --project src/MachineLearning -- chapter11
```

```text
Survived（決定木、5 分割交差検証の平均）
  正解率: 0.7800
  適合率: 0.8114
  再現率: 0.5736
  F値: 0.6587
cinema（線形回帰、5 分割交差検証の平均）
  RMSE: 392.75
  MAE: 314.71
```

Survived の決定木は、正解率 0.78 に対して再現率が 0.57 です。生存と予測したときの的中率（適合率 0.81）は高いものの、生存者の 4 割あまりを見逃しています。正解率だけを見ていては気づけない性質です。

分割に使う乱数生成器が他の版と違うので、数値は他の版と一致しません。

### 実データのテスト

表示をまるごと比べるテストを残します（学習データが無ければスキップします）。第 11 章のテストは 32 件です。

## 11.11 Notebook で探索する

Notebook は `apps/fsharp/notebooks/chapter11_evaluation_exploration.ipynb` にあります。Polyglot Notebooks が廃止されていることは、[第 2 章の 2.10 節](02-data-preprocessing-and-triangulation.md) を参照してください。

```fsharp
let split = splitTrainTest 0.3 0 x t
let predicted = decisionTree split.XTrain split.TTrain split.XTest
let cm = confusionMatrix Survived split.TTest predicted

Chart.Heatmap(
    zData = [ [ cm.TN; cm.FP ]; [ cm.FN; cm.TP ] ],
    X = [ "死亡と予測"; "生存と予測" ],
    Y = [ "実際は死亡"; "実際は生存" ]
)
|> Chart.withTitle "混同行列（テストデータ）"
```

訓練データとテストデータに 7 対 3 で分けたときの、テストデータの混同行列は次のとおりです。

| | 生存と予測 | 死亡と予測 |
|--|-----------|-----------|
| 実際は生存 | TP 73 | FN 29 |
| 実際は死亡 | FP 30 | TN 136 |

適合率 0.709、再現率 0.716、F 値 0.712 でした。

交差検証の 5 つの分割ごとのスコアを散布図にすると、指標によって揺れの大きさが違うことが分かります。

| 指標 | 最小 | 最大 |
|------|------|------|
| 正解率 | 0.753 | 0.798 |
| 適合率 | 0.729 | 0.892 |
| 再現率 | 0.415 | 0.746 |
| F 値 | 0.551 | 0.746 |

正解率は分割によって 0.05 程度しか変わりませんが、再現率は 0.4 から 0.75 まで大きく揺れます。1 回だけの分割で評価すると、再現率は偶然に大きく左右されます。交差検証で平均をとる理由がここにあります。

Kotlin 版の Notebook にある ROC 曲線は、F# 版では扱いません。第 3 章の自作の決定木はラベルしか返さず、ROC 曲線に必要な「生存の確率」を持たないためです。

グラフの画像は、第 2 章と同じ理由で記事に載せていません。

<details>
<summary>この章の完成コード（src/MachineLearning/Chapter11/CrossValidation.fs）</summary>

```fsharp
module MachineLearning.Chapter11.CrossValidation

open MachineLearning.Chapter02.Random
open MachineLearning.Chapter11.Metrics

/// 1 回分の分け方。訓練データとテストデータの行番号
type Fold = { Train: int list; Test: int list }

/// 行番号をシードで並べ替えてから nSplits 個に分け、それぞれをテストデータ、残りを訓練データにする
let kFold (nSplits: int) (seed: int) (nSamples: int) : Fold list =
    let positions = [ 0 .. nSamples - 1 ] |> shuffle seed

    positions
    |> List.splitInto nSplits
    |> List.map (fun test ->
        {
            Train = positions |> List.except test
            Test = test
        })

/// モデル。訓練データ（特徴量と正解）を受け取り、予測する関数を返す関数
type Model<'T> = Map<string, float> list -> 'T list -> (Map<string, float> list -> 'T list)

/// 行番号のリストで、リストから要素を取り出す
let private pick (rows: int list) (items: 'A list) : 'A list =
    let array = List.toArray items
    rows |> List.map (fun row -> array[row])

/// 分割ごとに、訓練データで学習し、テストデータの予測を評価関数で採点する。
/// スコアは取り出すときに初めて計算する（取り出した分だけ学習する）
let crossValidate
    (model: Model<'T>)
    (metric: Metric<'T>)
    (folds: Fold list)
    (x: Map<string, float> list)
    (t: 'T list)
    : float seq =
    seq {
        for fold in folds do
            let predict = model (pick fold.Train x) (pick fold.Train t)
            yield metric (pick fold.Test t) (predict (pick fold.Test x))
    }
```

</details>

## 11.12 まとめ

この章では、正解率だけでは見えない性質を評価指標で測り、交差検証で評価を安定させました。

1. **混同行列と評価指標** — 正解と予測を `bool` の組にして数え、適合率・再現率・F 値を求めた。分母が 0 のときは `NaN` にせず 0 を返した
2. **関数の型** — `Metric<'T>` と `Model<'T>` に名前を付け、評価関数とモデルを引数で差し替えた。混同行列の指標は高階関数で評価関数に変えた
3. **シーケンス式と遅延評価** — 交差検証のスコアを `seq` 式で書き、取り出した分だけ学習した。`Seq.cache` で結果を覚えさせた
4. **ML.NET との突き合わせ** — 同じ正解と予測を ML.NET の評価に渡し、自作の指標が一致することを確かめた
5. **1 回の分割の危うさ** — 再現率は分割によって 0.4 から 0.75 まで揺れた。交差検証の平均で、偶然に左右されにくい評価にした

次の章では、線形回帰に罰則を加える正則化で、モデルが訓練データに合わせすぎることを防ぎます。
