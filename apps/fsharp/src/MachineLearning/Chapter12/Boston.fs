module MachineLearning.Chapter12.Boston

open FSharp.Data
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter07.Matrix

/// 型プロバイダが列の名前と型を知るためのサンプル（架空の値）
[<Literal>]
let BostonSample =
    "CRIME,ZN,INDUS,CHAS,NOX,RM,AGE,DIS,RAD,TAX,PTRATIO,B,LSTAT,PRICE\nlow,0.5,0.5,0,0.5,0.5,0.5,0.5,1,1,0.5,0.5,0.5,0.5"

/// この章で使う列は小数として読む。サンプルには空欄が無いが、使わない列には空欄があるので、
/// AssumeMissingValues で「どの列にも空欄がありうる」ものとして型を作らせる
type BostonCsv =
    CsvProvider<BostonSample, Schema="RM=float,PTRATIO=float,LSTAT=float,PRICE=float", AssumeMissingValues=true>

let FeatureNames = [ "RM"; "PTRATIO"; "LSTAT" ]

[<Literal>]
let Target = "PRICE"

/// z スコアの絶対値がこの値を超える値を持つ行を外れ値とする
[<Literal>]
let OutlierThreshold = 3.0

/// Boston.csv のうち、この章で使う列だけを列名から値への Map にする
let loadBoston (csvFile: string) : Map<string, float> list =
    BostonCsv.Load(csvFile).Rows
    |> Seq.map (fun row -> Map.ofList [ "RM", row.RM; "PTRATIO", row.PTRATIO; "LSTAT", row.LSTAT; Target, row.PRICE ])
    |> Seq.toList

/// 列の平均値と標準偏差。標準偏差は偏差の二乗和を「件数 - ddof」で割って求める
let private meanAndStd (ddof: int) (values: float list) : float * float =
    let mean = List.average values

    let squares = values |> List.sumBy (fun value -> (value - mean) * (value - mean))

    mean, sqrt (squares / float (values.Length - ddof))

/// 列ごとの z スコア（標本標準偏差で割る）の絶対値が threshold を超える値を、1 つでも持つ行を除く
let removeOutliers (columns: string list) (threshold: float) (rows: Map<string, float> list) : Map<string, float> list =
    let stats =
        columns
        |> List.map (fun column -> column, rows |> List.map (fun row -> row[column]) |> meanAndStd 1)

    let isOutlier (row: Map<string, float>) =
        stats
        |> List.exists (fun (column, (mean, std)) -> abs ((row[column] - mean) / std) > threshold)

    rows |> List.filter (isOutlier >> not)

/// 訓練データから求めた標準化の平均値・標準偏差と、2 次の項を作る列の組
type PolynomialScaler =
    {
        Columns: string list
        Stats: (float * float) list
        /// 2 次の項を作る列の番号の組（i <= j）
        Pairs: (int * int) list
        FeatureNames: string list
    }

/// 平均値と、件数で割る標準偏差（母標準偏差）を訓練データから求め、2 次の項の組を決める
let fitPolynomialScaler (columns: string list) (x: Map<string, float> list) : PolynomialScaler =
    let n = columns.Length

    let pairs =
        [
            for i in 0 .. n - 1 do
                for j in i .. n - 1 -> i, j
        ]

    let pairName (i, j) =
        if i = j then
            $"{columns[i]}^2"
        else
            $"{columns[i]} {columns[j]}"

    {
        Columns = columns
        Stats =
            columns
            |> List.map (fun column -> x |> List.map (fun row -> row[column]) |> meanAndStd 0)
        Pairs = pairs
        FeatureNames = columns @ (pairs |> List.map pairName)
    }

/// 標準化した値と、その 2 次の項を並べた行列にする
let transformPolynomial (scaler: PolynomialScaler) (x: Map<string, float> list) : Matrix =
    x
    |> List.map (fun row ->
        let z =
            List.map2 (fun column (mean, std) -> (row[column] - mean) / std) scaler.Columns scaler.Stats
            |> List.toArray

        Array.append z (scaler.Pairs |> List.map (fun (i, j) -> z[i] * z[j]) |> List.toArray))
    |> List.toArray

/// 訓練データ・検証データ・テストデータと、特徴量の名前
type BostonDataset =
    {
        XTrain: Matrix
        TTrain: float list
        XValid: Matrix
        TValid: float list
        XTest: Matrix
        TTest: float list
        FeatureNames: string list
    }

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
