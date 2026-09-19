---
type: Article
title: "第 12 章: 正則化とモデル選択"
description: "リッジ回帰を中心化した正規方程式で、ラッソ回帰を座標降下法で自作し、不変なレコードで正則化の強さごとの実験結果を記録して検証データで選ぶ。FSharp.Stats 0.6.0 のリッジ回帰が使えないこと、ML.NET の SDCA の L2 の尺度、型プロバイダの AssumeMissingValues を学習用テストで確かめる。"
tags: [article,getting-start-ml,fsharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T08:17:03Z }
---

# 第 12 章: 正則化とモデル選択

## 12.1 はじめに

第 7 章の線形回帰は、訓練データの誤差をできるだけ小さくする係数を求めました。特徴量を増やすと、訓練データにはよく当てはまるのに、新しいデータには当たらない **過学習** が起きやすくなります。

この章では、係数の大きさに罰則を加えて過学習を抑える **正則化** を扱います。

- **リッジ回帰**: 係数の 2 乗の和に罰則を加える。係数が全体に小さくなる
- **ラッソ回帰**: 係数の絶対値の和に罰則を加える。役に立たない特徴量の係数がちょうど 0 になる

罰則の強さ `alpha` は、訓練データでは決められません。**検証データ** を別に取り分けて選びます。

[Python 版の第 12 章](../python/12-regularization-and-model-selection.md)・[Kotlin 版の第 12 章](../kotlin/12-regularization-and-model-selection.md)・[TypeScript 版の第 12 章](../typescript/12-regularization-and-model-selection.md) と同じ題材で進めます。F# 版では、次の 3 点に注目してください。

- 第 7 章の行列（`float[][]`）と連立方程式の解法を、書き換えずに **別のモジュールで拡張** する
- 実験の結果を **不変なレコード** で記録し、`with` で一部を変えても元の記録が変わらないことを確かめる
- ライブラリの振る舞いを **学習用テスト** で確かめる。FSharp.Stats 0.6.0 のリッジ回帰は使えず、ML.NET の罰則は尺度が違った

## 12.2 題材とデータ

`Boston.csv` は、ボストンの地区ごとの住宅価格（`PRICE`）と、その地区の特徴を記録したデータです。この章では、部屋数（`RM`）・生徒と教師の比率（`PTRATIO`）・低所得者の割合（`LSTAT`）の 3 列を使います。

3 列を標準化し、2 次の項（2 乗と積）を加えて 9 個の特徴量にします。特徴量を増やして過学習しやすくし、正則化の効果を見えやすくするためです。

データは、テストデータ（3 割）を先に取り分け、残りを訓練データと検証データ（残りの 3 割）に分けます。

## 12.3 TODO リストの作成

**TODO リスト**:

- [ ] リッジ回帰を求める
  - [ ] alpha が 0 なら最小二乗法と同じになる
  - [ ] alpha を大きくすると係数が小さくなる
- [ ] ラッソ回帰を求める
  - [ ] 役に立たない特徴量の係数がちょうど 0 になる
- [ ] alpha ごとの実験結果を記録し、検証データで選ぶ
- [ ] ライブラリと突き合わせる
- [ ] Boston のデータを準備する（外れ値の除去・2 次の項・標準化・3 つへの分割）
- [ ] 実データで実験する

## 12.4 リッジ回帰

### 第 7 章の行列を拡張する

リッジ回帰の正規方程式は、第 7 章の最小二乗法の式に `alpha × 単位行列` を足したものです。

(XᵀX + αI) w = Xᵀt

第 7 章の `Matrix` モジュールには、掛け算・転置・連立方程式の解法はありますが、足し算・定数倍・単位行列はありません。第 7 章のファイルは書き換えず、第 12 章に `MatrixOperations` を作って足します。

```fsharp
// src/MachineLearning/Chapter12/MatrixOperations.fs
/// 第 7 章の Matrix（float[][]）に、リッジ回帰に必要な演算を足す。第 7 章のモジュールは書き換えない
module MachineLearning.Chapter12.MatrixOperations

open System
open MachineLearning.Chapter07.Matrix

/// 同じ形の行列を要素ごとに足す
let add (a: Matrix) (b: Matrix) : Matrix =
    if a.Length <> b.Length || a[0].Length <> b[0].Length then
        raise (
            ArgumentException $"{a.Length} 行 {a[0].Length} 列の行列と {b.Length} 行 {b[0].Length} 列の行列は足せません"
        )

    Array.map2 (Array.map2 (+)) a b

/// 行列の各要素に数を掛ける
let scale (k: float) (a: Matrix) : Matrix = a |> Array.map (Array.map ((*) k))

/// size 行 size 列の単位行列
let identity (size: int) : Matrix =
    Array.init size (fun i -> Array.init size (fun j -> if i = j then 1.0 else 0.0))
```

- `Array.map2 (Array.map2 (+))` は、行どうしを `Array.map2` で組にし、さらに要素どうしを `(+)` で足します。`(+)` は演算子を関数として使う書き方です
- F# のモジュールは、あとから別のファイルで関数を足せます。第 7 章のテストはそのまま通り続けます

### テストと実装

```fsharp
// tests/MachineLearning.Tests/Chapter12/RegularizationTest.fs
[<Fact>]
let ``alpha が 0 なら最小二乗法と同じ係数と切片になる`` () =
    let x = [| [| 1.0 |]; [| 2.0 |]; [| 3.0 |] |]
    let t = [ 3.0; 5.0; 7.0 ]

    let model = fitRidge 0.0 x t

    Assert.Equal(2.0, List.exactlyOne model.Coefficients, 12)
    Assert.Equal(1.0, model.Intercept, 12)

[<Fact>]
let ``alpha を大きくすると係数の絶対値の合計が小さくなる`` () =
    let x, t = randomDataset ()

    let weak = fitRidge 0.1 x t
    let strong = fitRidge 100.0 x t

    Assert.True(List.sumBy abs strong.Coefficients < List.sumBy abs weak.Coefficients)
```

`randomDataset` は、`System.Random 0` で作った 4 列・30 件のテスト用のデータです。係数を決めて正解を作り、小さなノイズを加えています。

```fsharp
// src/MachineLearning/Chapter12/Regularization.fs
/// 正則化した線形回帰の学習結果。係数は特徴量の列の順に並ぶ
type RegularizedModel =
    {
        Coefficients: float list
        Intercept: float
    }

/// 中心化した X と t で (Xᵀ X + alpha I) w = Xᵀ t を解く。切片には罰則をかけない
let fitRidge (alpha: float) (x: Matrix) (t: float list) : RegularizedModel =
    let xMeans = transpose x |> Array.map Array.average
    let tMean = List.average t
    let xc = x |> Array.map (fun row -> Array.map2 (-) row xMeans)
    let tc = t |> List.map (fun value -> value - tMean) |> List.toArray
    let xcT = transpose xc

    let coefficients =
        solve (add (multiply xcT xc) (scale alpha (identity xMeans.Length))) (xcT |> Array.map (dot tc))

    {
        Coefficients = List.ofArray coefficients
        Intercept = tMean - dot xMeans coefficients
    }
```

- 切片に罰則をかけると、正解の平均が大きいだけで予測がゆがみます。特徴量と正解から平均を引いて **中心化** してから係数を求め、切片は最後に平均から求めます
- `transpose x |> Array.map Array.average` は、転置して列を行にし、行ごとの平均を求めることで、列ごとの平均を求めています

## 12.5 ラッソ回帰

ラッソ回帰の罰則（係数の絶対値）は、0 のところで微分できないので、正規方程式のような 1 つの式では解けません。係数を 1 つずつ順番に最適な値へ更新することを繰り返す **座標降下法** で求めます。

```fsharp
[<Fact>]
let ``ラッソ回帰では予測に役立たない特徴量の係数がちょうど 0 になる`` () =
    let x, t = sparseDataset ()

    let model = fitLasso 0.5 x t

    Assert.Equal<string list>(
        [ "noise1"; "noise2" ],
        zeroCoefficientNames model.Coefficients [ "x0"; "x1"; "noise1"; "noise2" ]
    )
```

`sparseDataset` は、正解が `x0` と `x1` だけで決まり、`noise1`・`noise2` は正解と関係しない 50 件のデータです。

```fsharp
/// 軟閾値関数。z の絶対値を gamma だけ 0 に近づけ、0 を越えるならちょうど 0 にする
let private softThreshold (gamma: float) (z: float) : float =
    if z > gamma then z - gamma
    elif z < -gamma then z + gamma
    else 0.0

/// (1/2n) × 誤差の二乗和 + alpha × 係数の絶対値の和 を、座標降下法で最小化する（ラッソ回帰）
let fitLasso (alpha: float) (x: Matrix) (t: float list) : RegularizedModel =
    let n = float x.Length
    let xMeans = transpose x |> Array.map Array.average
    let tMean = List.average t
    let columns = x |> Array.map (fun row -> Array.map2 (-) row xMeans) |> transpose
    let squaredNorms = columns |> Array.map (fun column -> dot column column / n)
    let weights = Array.create columns.Length 0.0
    // 残差（中心化した t - 現在の予測）。係数を 1 つ変えるたびに、その分だけ更新する
    let residual = t |> List.map (fun value -> value - tMean) |> List.toArray

    for _ in 1..LassoIterations do
        for j in 0 .. columns.Length - 1 do
            let column = columns[j]
            let rho = dot column residual / n + weights[j] * squaredNorms[j]
            let updated = softThreshold alpha rho / squaredNorms[j]

            for i in 0 .. residual.Length - 1 do
                residual[i] <- residual[i] + column[i] * (weights[j] - updated)

            weights[j] <- updated

    {
        Coefficients = List.ofArray weights
        Intercept = tMean - dot xMeans weights
    }
```

- **軟閾値関数** が、係数を「ちょうど 0」にする仕組みです。更新前の値の絶対値が `alpha` 以下なら、係数は 0 になります
- 座標降下法は、同じ係数を何度も更新します。ここでは、関数の中だけで可変な配列（`weights`・`residual`）を使い、外には不変なレコードを返します。第 2 章の `shuffle` と同じ考え方です
- 反復の回数 `LassoIterations` は 1000 に固定しています。この章のデータの大きさでは十分に収束します

## 12.6 実験結果を記録して選ぶ

### 不変なレコードで記録する

`alpha` ごとに、訓練データと検証データの決定係数、係数の絶対値の合計を記録します。

```fsharp
/// 正則化の強さ 1 つ分の実験結果
type Experiment =
    {
        Alpha: float
        TrainScore: float
        ValidationScore: float
        CoefficientAbsSum: float
    }

/// alpha ごとに訓練データで学習し、訓練データと検証データの決定係数、係数の絶対値の合計を記録する
let runRidgeExperiments
    (alphas: float list)
    (xTrain: Matrix)
    (tTrain: float list)
    (xValid: Matrix)
    (tValid: float list)
    : Experiment list =
    alphas
    |> List.map (fun alpha ->
        let model = fitRidge alpha xTrain tTrain

        {
            Alpha = alpha
            TrainScore = r2Score tTrain (predictRegularized model xTrain)
            ValidationScore = r2Score tValid (predictRegularized model xValid)
            CoefficientAbsSum = List.sumBy abs model.Coefficients
        })

/// 検証データの決定係数が最も高い実験。空のリストには ArgumentException を投げる
let bestExperiment (experiments: Experiment list) : Experiment =
    experiments |> List.maxBy (fun e -> e.ValidationScore)
```

- 決定係数は、第 7 章の `r2Score` をそのまま使います
- 実験結果はレコードなので、作った後に書き換えられません。一部だけ変えた結果が欲しいときは、`with` で新しいレコードを作ります

```fsharp
[<Fact>]
let ``with で一部を変えた実験結果を作っても元の実験結果は変わらない`` () =
    let original = experimentsOf [ 1.0 ] |> List.exactlyOne

    let changed = { original with Alpha = 2.0 }

    Assert.Equal(1.0, original.Alpha)
    Assert.Equal(2.0, changed.Alpha)
    Assert.Equal(original.ValidationScore, changed.ValidationScore)
```

実験の記録を後から書き換えられないことは、「どの設定でどの結果が出たか」を取り違えないための安全装置になります。

- `bestExperiment []` は、`List.maxBy` が `ArgumentException` を投げます。「空なら選べない」こともテストで固定しています

## 12.7 ライブラリと突き合わせる

### FSharp.Stats のリッジ回帰は使えなかった

[ADR 004](../../../adr/004-fsharp-ml-libraries.md) では、FSharp.Stats のリッジ回帰と突き合わせる方針でした。学習用テストで確かめると、FSharp.Stats 0.6.0 の `RidgeRegression.fit` は、罰則の行列の大きさをどう変えても例外を投げました。

```fsharp
// tests/MachineLearning.Tests/Chapter12/LibraryRegularizationTest.fs
[<Fact>]
let ``学習用テスト: FSharp.Stats 0.6.0 の RidgeRegression.fit は lambda の行列の大きさによらず次元が合わない例外を投げる`` () =
    let x, t = randomDataset ()
    let xData = matrix x
    let yData = vector t

    for size in [ 4; 5 ] do
        let lambda = Matrix.identity size

        let error =
            Assert.ThrowsAny<ArgumentException>(fun () ->
                LinearRegression.OLS.Linear.RidgeRegression.fit lambda xData yData |> ignore)

        Assert.StartsWith("the two matrices do not have compatible dimensions", error.Message)
```

特徴量の数（4）に合わせた行列でも、切片の分を足した大きさ（5）でも、同じ例外になりました。このテストは、ライブラリの版を上げて直ったときに失敗して知らせてくれます。

### ML.NET の SDCA と尺度をそろえる

代わりに、ML.NET の回帰の学習器 SDCA（確率的双対座標上昇法）で L2 の罰則をかけ、自作のリッジ回帰と突き合わせます。SDCA の目的関数は「(1/n) × 誤差の二乗和 + (l2 / 2) × 係数の二乗和」で、自作の「誤差の二乗和 + alpha × 係数の二乗和」とは尺度が違います。両辺を比べると、`l2 = 2 × alpha / n` のときに同じ係数になるはずです。これを学習用テストで確かめます。

```fsharp
[<Fact>]
let ``学習用テスト: ML.NET の Sdca の L2Regularization は、自作のリッジ回帰の alpha を件数の半分で割った値に当たる`` () =
    let x, t = randomDataset ()
    let l2 = 0.1

    let library = fitSdca l2 x t
    let mine = fitRidge (float t.Length * l2 / 2.0) x t

    assertClose 1e-3 mine.Coefficients library.Coefficients
    assertClose 1e-3 [ mine.Intercept ] [ library.Intercept ]
```

```fsharp
// src/MachineLearning/Chapter12/MlNetRegularization.fs
/// ML.NET の SDCA で、(1/n) × 誤差の二乗和 + (l2 / 2) × 係数の二乗和 を最小化する。L1 の罰則は 0 にする
let fitSdca (l2: float) (x: Matrix) (t: float list) : RegularizedModel =
    let context = MLContext(seed = 0)
    let schema = SchemaDefinition.Create(typeof<RegressionRow>)
    schema["Features"].ColumnType <- VectorDataViewType(NumberDataViewType.Single, x[0].Length)

    let rows =
        List.map2
            (fun (features: float[]) (label: float) ->
                {
                    Features = Array.map float32 features
                    Label = float32 label
                })
            (List.ofArray x)
            t

    let options =
        SdcaRegressionTrainer.Options(
            L2Regularization = Nullable(float32 l2),
            L1Regularization = Nullable 0.0f,
            ConvergenceTolerance = ConvergenceTolerance,
            MaximumNumberOfIterations = Nullable MaximumNumberOfIterations,
            Shuffle = false,
            NumberOfThreads = Nullable 1
        )

    let trained =
        context.Regression.Trainers
            .Sdca(options)
            .Fit(context.Data.LoadFromEnumerable(rows, schema))

    {
        Coefficients = trained.Model.Weights |> Seq.map float |> Seq.toList
        Intercept = float trained.Model.Bias
    }

/// 自作の fitRidge と同じ alpha でリッジ回帰を学習する。ML.NET の l2 は alpha × 2 / 件数 に当たる
let fitRidgeWithMlNet (alpha: float) (x: Matrix) (t: float list) : RegularizedModel =
    fitSdca (2.0 * alpha / float t.Length) x t
```

- ML.NET のオプションは、C# の「引数なしで作ってからプロパティに値を入れる」形です。F# では、コンストラクターの引数の後ろに `プロパティ = 値` を並べて、作ると同時に設定できます
- `L2Regularization` の型は `Nullable<float32>` です。F# では `Nullable(float32 l2)` と明示して包みます
- SDCA は確率的な方法なので、`Shuffle = false` と `NumberOfThreads = Nullable 1` で順番を固定し、収束の判定を厳しくして、自作の厳密な解と小数第 3 位まで一致させています

## 12.8 Boston のデータを準備する

### 型プロバイダと AssumeMissingValues

Boston.csv を型プロバイダで読み込みます。使う 4 列は、`Schema` で小数に指定します。

```fsharp
// src/MachineLearning/Chapter12/Boston.fs
/// 型プロバイダが列の名前と型を知るためのサンプル（架空の値）
[<Literal>]
let BostonSample =
    "CRIME,ZN,INDUS,CHAS,NOX,RM,AGE,DIS,RAD,TAX,PTRATIO,B,LSTAT,PRICE\nlow,0.5,0.5,0,0.5,0.5,0.5,0.5,1,1,0.5,0.5,0.5,0.5"
```

最初は `CsvProvider<BostonSample, Schema="RM=float,PTRATIO=float,LSTAT=float,PRICE=float">` と書きました。テストは通りましたが、実データで実行すると次の例外になりました。

```text
Unhandled exception. System.Exception: Couldn't parse row 9 according to schema: RAD is missing
```

この章では使わない `RAD` の列に、空欄がありました。架空のサンプルには空欄が無いので、型プロバイダは「`RAD` は必ず値がある整数の列」と推論し、空欄を読めなかったのです。型プロバイダの推論は、サンプルに現れた値にしか基づかない、ということです。

`AssumeMissingValues=true` を付けると、型プロバイダは、どの列にも空欄がありうるものとして型を作ります。この振る舞いを学習用テストに残します。

```fsharp
/// 学習用テスト: 空欄の無いサンプルから作った型
type StrictCsv = CsvProvider<BostonSample>

[<Fact>]
let ``学習用テスト: サンプルに空欄が無い列に空欄があると、AssumeMissingValues が無ければ読めない`` () =
    let header = "CRIME,ZN,INDUS,CHAS,NOX,RM,AGE,DIS,RAD,TAX,PTRATIO,B,LSTAT,PRICE\n"
    let row = "low,0,8.14,0,0.538,5.95,82,3.99,,307,21,232.6,27.71,13.2"

    let error =
        Assert.ThrowsAny<exn>(fun () -> StrictCsv.Parse(header + row).Rows |> Seq.toList |> ignore)

    Assert.Contains("RAD is missing", error.Message)
    Assert.Equal(13.2, (BostonCsv.Parse(header + row).Rows |> Seq.head).PRICE)
```

テストの行は架空の値です。`Parse` は、ファイルではなく文字列を CSV として読みます。

```fsharp
/// この章で使う列は小数として読む。サンプルには空欄が無いが、使わない列には空欄があるので、
/// AssumeMissingValues で「どの列にも空欄がありうる」ものとして型を作らせる
type BostonCsv =
    CsvProvider<BostonSample, Schema="RM=float,PTRATIO=float,LSTAT=float,PRICE=float", AssumeMissingValues=true>
```

### 外れ値の除去・2 次の項・標準化

```fsharp
/// 列ごとの z スコア（標本標準偏差で割る）の絶対値が threshold を超える値を、1 つでも持つ行を除く
let removeOutliers (columns: string list) (threshold: float) (rows: Map<string, float> list) : Map<string, float> list =
    let stats =
        columns
        |> List.map (fun column -> column, rows |> List.map (fun row -> row[column]) |> meanAndStd 1)

    let isOutlier (row: Map<string, float>) =
        stats
        |> List.exists (fun (column, (mean, std)) -> abs ((row[column] - mean) / std) > threshold)

    rows |> List.filter (isOutlier >> not)
```

- `fun (column, (mean, std)) -> ...` は、引数の組をその場で分解して受け取るパターンです
- 外れ値の判定は他の版と同じく、平均から標準偏差の 3 倍より離れた値としています

2 次の項を作る列の組は、リスト式の 2 重のループで作ります。

```fsharp
    let pairs =
        [
            for i in 0 .. n - 1 do
                for j in i .. n - 1 -> i, j
        ]
```

`j` を `i` から始めるので、`(0, 0)`・`(0, 1)`・`(0, 2)`・`(1, 1)`・… と、同じ組を 2 回作りません。3 列から 6 個の 2 次の項ができ、元の 3 列と合わせて 9 個の特徴量になります。

標準化の平均値と標準偏差は、**訓練データだけ** から求め、検証データとテストデータにも同じ値で適用します（第 2 章のデータリークと同じ理由です）。

```fsharp
/// 外れ値を除き、テストデータを分けてから、残りを訓練データと検証データに分ける。
/// 標準化と 2 次の項は、訓練データの平均値と標準偏差で 3 つすべてに適用する
let prepareBoston (csvFile: string) (testSize: float) (validationSize: float) (seed: int) : BostonDataset =
    let rows =
        loadBoston csvFile
        |> removeOutliers (FeatureNames @ [ Target ]) OutlierThreshold

    let x = rows |> List.map (Map.remove Target)
    let t = rows |> List.map (fun row -> row[Target])
    let outer = splitTrainTest testSize seed x t
    let inner = splitTrainTest validationSize seed outer.XTrain outer.TTrain
    let scaler = fitPolynomialScaler FeatureNames inner.XTrain

    {
        XTrain = transformPolynomial scaler inner.XTrain
        TTrain = inner.TTrain
        XValid = transformPolynomial scaler inner.XTest
        TValid = inner.TTest
        XTest = transformPolynomial scaler outer.XTest
        TTest = outer.TTest
        FeatureNames = scaler.FeatureNames
    }
```

分割は、第 2 章の `splitTrainTest` を 2 回使います。1 回目でテストデータを取り分け、2 回目で残りを訓練データと検証データに分けます。

## 12.9 実データで実験する

```bash
dotnet run --project src/MachineLearning -- chapter12
```

```text
データ件数: 98（外れ値 2 件を除外）
訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件
特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2
alpha	訓練 R²	検証 R²	係数の絶対値の合計
0	0.8914	-0.4803	12.509
0.1	0.8914	-0.4445	12.400
1	0.8908	-0.2447	11.718
10	0.8819	-0.0167	9.991
100	0.7769	-0.1583	6.406
検証データで選んだ alpha: 10
テストデータの決定係数: 線形回帰 0.9040, リッジ回帰 0.8827
ML.NET（SDCA）のリッジ回帰のテストデータの決定係数: 0.8828
ラッソ回帰（alpha=1）で係数が 0 になった特徴量: RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2

シード	選んだ alpha	線形回帰	リッジ回帰
0	10	0.9040	0.8827
1	10	0.5551	0.4040
2	1	0.7012	0.7040
3	0	0.7460	0.7460
4	100	0.2219	0.4841
```

### 結果を読む

- `alpha` を大きくすると、係数の絶対値の合計は 12.5 から 6.4 まで小さくなり、訓練データの決定係数は下がっていきます。罰則が係数を抑えています
- 検証データの決定係数は、どの `alpha` でも負です。決定係数が負とは、「検証データの平均値を常に予測する」よりも外れている、という意味です。シード 0 の分け方では、21 件の検証データに訓練データと傾向の違う地区が集まっています。それでも `alpha = 10` で −0.48 から −0.02 まで改善しており、正則化が訓練データへの合わせすぎを抑えていることが分かります
- ML.NET の SDCA で同じ `alpha` を換算して学習すると、テストデータの決定係数は 0.8828 で、自作の 0.8827 とほぼ一致しました
- ラッソ回帰の `alpha = 1` では、9 個の特徴量のうち 4 個の係数がちょうど 0 になりました。残ったのは元の 3 列と `RM^2`・`RM PTRATIO` です

### 分け方で結論が変わる

シード 0 では、テストデータの決定係数はリッジ回帰（0.8827）より線形回帰（0.9040）のほうが高くなりました。シードを変えると、リッジ回帰が勝つ分け方も負ける分け方もあります。100 件ほどのデータでは、1 回の分割の結果だけで「正則化は効く・効かない」とは言えません。Notebook では、シードを 20 通りに増やして比べます。

他の版とは乱数生成器が違うので、分け方も数値も一致しません。ラッソ回帰の `alpha` も、他の版と値が違います。F# 版の `alpha` は価格そのものの尺度の罰則で、TypeScript 版（ml-regression-lasso）の `lambda` は、特徴量と正解をどちらも標準化した尺度の罰則だからです。F# 版では、係数が 0 になる様子が見える `alpha = 1` を選びました（`alpha = 0.05` では 0 になる係数はありませんでした）。

### 実データのテスト

表示をまるごと比べるテストと、3 つへの分割の件数のテストを残しています。第 12 章のテストは 26 件です。

## 12.10 Notebook で探索する

Notebook は `apps/fsharp/notebooks/chapter12_regularization_exploration.ipynb` にあります。Polyglot Notebooks が廃止されていることは、[第 2 章の 2.10 節](02-data-preprocessing-and-triangulation.md) を参照してください。

```fsharp
let alphas = [ for k in -2.0 .. 0.25 .. 3.0 -> 10.0 ** k ]
let models = alphas |> List.map (fun alpha -> fitRidge alpha boston.XTrain boston.TTrain)

boston.FeatureNames
|> List.mapi (fun i name ->
    Chart.Line(x = (alphas |> List.map log10), y = (models |> List.map (fun m -> m.Coefficients[i])), Name = name))
|> Chart.combine
|> Chart.withTitle "alpha と係数の変化"
|> Chart.withXAxisStyle "log10(alpha)"
|> Chart.withYAxisStyle "係数"
```

- `[ for k in -2.0 .. 0.25 .. 3.0 -> 10.0 ** k ]` は、0.01 から 1000 までを対数で等間隔に並べた 21 個の `alpha` です
- `List.mapi` は、要素と一緒にその位置（`i`）を渡します。特徴量ごとに、`alpha` を変えたときの係数の変化を 1 本の線にします

`alpha` を大きくすると、どの特徴量の係数も 0 に近づいていきます。訓練データの決定係数は下がり続けますが、検証データの決定係数は `alpha` が 10 前後で最も高くなります。

ラッソ回帰では、`alpha` を大きくするほど 0 になる係数が増えます。

| alpha | 0 になった係数の数 |
|-------|------------------|
| 0.05 | 0 |
| 0.1 | 0 |
| 0.2 | 1 |
| 0.5 | 3 |
| 1 | 4 |
| 2 | 6 |
| 5 | 8 |

シードを 0 から 19 までの 20 通りに変えて、テストデータの決定係数を比べました（リッジ回帰の `alpha` は 10 に固定）。

| | 線形回帰 | リッジ回帰 |
|--|--------|-----------|
| 20 通りの平均 | 0.501 | 0.567 |
| 20 通りの最小 | 0.053 | 0.307 |
| リッジ回帰が上回った回数 | — | 20 回中 13 回 |

平均ではリッジ回帰が上回り、とくに最も悪い場合の決定係数が大きく改善しています（0.05 から 0.31）。正則化は、「平均を少し上げる」ことよりも、「たまたま悪い分け方で大きく外す」ことを防ぐ効果が大きいと読めます。

グラフの画像は、第 2 章と同じ理由で記事に載せていません。

<details>
<summary>この章の完成コード（src/MachineLearning/Chapter12/Regularization.fs）</summary>

```fsharp
module MachineLearning.Chapter12.Regularization

open MachineLearning.Chapter07.Matrix
open MachineLearning.Chapter07.RegressionMetrics
open MachineLearning.Chapter12.MatrixOperations

/// 正則化した線形回帰の学習結果。係数は特徴量の列の順に並ぶ
type RegularizedModel =
    {
        Coefficients: float list
        Intercept: float
    }

/// 中心化した X と t で (Xᵀ X + alpha I) w = Xᵀ t を解く。切片には罰則をかけない
let fitRidge (alpha: float) (x: Matrix) (t: float list) : RegularizedModel =
    let xMeans = transpose x |> Array.map Array.average
    let tMean = List.average t
    let xc = x |> Array.map (fun row -> Array.map2 (-) row xMeans)
    let tc = t |> List.map (fun value -> value - tMean) |> List.toArray
    let xcT = transpose xc

    let coefficients =
        solve (add (multiply xcT xc) (scale alpha (identity xMeans.Length))) (xcT |> Array.map (dot tc))

    {
        Coefficients = List.ofArray coefficients
        Intercept = tMean - dot xMeans coefficients
    }

/// 座標降下法の繰り返しの回数（全係数を 1 回ずつ更新するのを 1 回と数える）
[<Literal>]
let LassoIterations = 1000

/// 軟閾値関数。z の絶対値を gamma だけ 0 に近づけ、0 を越えるならちょうど 0 にする
let private softThreshold (gamma: float) (z: float) : float =
    if z > gamma then z - gamma
    elif z < -gamma then z + gamma
    else 0.0

/// (1/2n) × 誤差の二乗和 + alpha × 係数の絶対値の和 を、座標降下法で最小化する（ラッソ回帰）
let fitLasso (alpha: float) (x: Matrix) (t: float list) : RegularizedModel =
    let n = float x.Length
    let xMeans = transpose x |> Array.map Array.average
    let tMean = List.average t
    let columns = x |> Array.map (fun row -> Array.map2 (-) row xMeans) |> transpose
    let squaredNorms = columns |> Array.map (fun column -> dot column column / n)
    let weights = Array.create columns.Length 0.0
    // 残差（中心化した t - 現在の予測）。係数を 1 つ変えるたびに、その分だけ更新する
    let residual = t |> List.map (fun value -> value - tMean) |> List.toArray

    for _ in 1..LassoIterations do
        for j in 0 .. columns.Length - 1 do
            let column = columns[j]
            let rho = dot column residual / n + weights[j] * squaredNorms[j]
            let updated = softThreshold alpha rho / squaredNorms[j]

            for i in 0 .. residual.Length - 1 do
                residual[i] <- residual[i] + column[i] * (weights[j] - updated)

            weights[j] <- updated

    {
        Coefficients = List.ofArray weights
        Intercept = tMean - dot xMeans weights
    }

/// 特徴量と係数の内積 + 切片を行ごとに求める
let predictRegularized (model: RegularizedModel) (x: Matrix) : float list =
    let coefficients = List.toArray model.Coefficients
    x |> Array.map (fun row -> model.Intercept + dot row coefficients) |> Array.toList

/// 正則化の強さ 1 つ分の実験結果
type Experiment =
    {
        Alpha: float
        TrainScore: float
        ValidationScore: float
        CoefficientAbsSum: float
    }

/// alpha ごとに訓練データで学習し、訓練データと検証データの決定係数、係数の絶対値の合計を記録する
let runRidgeExperiments
    (alphas: float list)
    (xTrain: Matrix)
    (tTrain: float list)
    (xValid: Matrix)
    (tValid: float list)
    : Experiment list =
    alphas
    |> List.map (fun alpha ->
        let model = fitRidge alpha xTrain tTrain

        {
            Alpha = alpha
            TrainScore = r2Score tTrain (predictRegularized model xTrain)
            ValidationScore = r2Score tValid (predictRegularized model xValid)
            CoefficientAbsSum = List.sumBy abs model.Coefficients
        })

/// 検証データの決定係数が最も高い実験。空のリストには ArgumentException を投げる
let bestExperiment (experiments: Experiment list) : Experiment =
    experiments |> List.maxBy (fun e -> e.ValidationScore)

/// 係数がちょうど 0 の特徴量の名前
let zeroCoefficientNames (coefficients: float list) (featureNames: string list) : string list =
    List.zip featureNames coefficients
    |> List.filter (fun (_, coefficient) -> coefficient = 0.0)
    |> List.map fst
```

</details>

## 12.11 まとめ

この章では、係数に罰則を加える正則化で過学習を抑え、罰則の強さを検証データで選びました。

1. **モジュールを別のファイルで拡張する** — 第 7 章の行列の演算を書き換えず、足し算・定数倍・単位行列を第 12 章のモジュールに足した
2. **リッジ回帰とラッソ回帰** — 中心化した正規方程式と、軟閾値関数を使う座標降下法で自作した。ラッソ回帰では係数がちょうど 0 になった
3. **不変なレコードで記録する** — 実験結果を `Experiment` に記録し、`with` で一部を変えても元の記録が変わらないことを確かめた
4. **学習用テストでライブラリを確かめる** — FSharp.Stats 0.6.0 のリッジ回帰が使えないこと、ML.NET の SDCA の罰則が `2 × alpha / 件数` に当たること、型プロバイダがサンプルに現れない空欄を読めないこと（`AssumeMissingValues`）を見つけた
5. **分け方で結論が変わる** — 1 回の分割では線形回帰が勝つこともあった。20 通りの分け方で比べると、正則化は悪い場合を大きく改善した

次の章では、特徴量の数そのものを減らす主成分分析を扱います。
