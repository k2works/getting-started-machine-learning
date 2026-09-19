module MachineLearning.Chapter09.BostonFeatures

open FSharp.Data
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter07.LinearRegression
open MachineLearning.Chapter07.RegressionMetrics
open MachineLearning.Chapter09.Dummies
open MachineLearning.Chapter09.Polynomial
open MachineLearning.Chapter09.Standardizer

[<Literal>]
let Target = "PRICE"

[<Literal>]
let Category = "CRIME"

/// 空欄なら None、そうでなければ数値
let private parseOptional (text: string) : float option =
    if text = "" then None else Some(float text)

/// Boston.csv を、型を持たない CSV として読む。CRIME をダミー変数にし、PRICE を正解にする。
/// 特徴量の名前は CSV の列の順（ダミー変数は最後）に並べて返す
let loadBostonFeatures (csvFile: string) : string list * (Map<string, float option> * float) list =
    let csv = CsvFile.Load(csvFile).Cache()
    let headers = csv.Headers |> Option.defaultValue [||] |> List.ofArray

    let numericColumns =
        headers |> List.filter (fun header -> header <> Category && header <> Target)

    let rows = csv.Rows |> Seq.toList

    let categories =
        rows |> List.map (fun row -> row.GetColumn Category) |> dummyCategories

    let toFeatures (row: CsvRow) =
        let numeric =
            numericColumns
            |> List.map (fun column -> column, parseOptional (row.GetColumn column))

        let dummies =
            encodeDummies Category categories (row.GetColumn Category)
            |> Map.toList
            |> List.map (fun (name, value) -> name, Some value)

        Map.ofList (numeric @ dummies), float (row.GetColumn Target)

    numericColumns
    @ (categories |> List.map (fun category -> $"{Category}_{category}")),
    rows |> List.map toFeatures

/// 読み込み、訓練データとテストデータに分け、訓練データの平均で欠損値を補完する
let prepareBoston
    (csvFile: string)
    (testSize: float)
    (seed: int)
    : string list * TrainTestSplit<Map<string, float>, float> =
    let names, rows = loadBostonFeatures csvFile

    let split =
        splitTrainTest testSize seed (rows |> List.map fst) (rows |> List.map snd)

    let means = columnMeans split.XTrain

    names,
    {
        XTrain = fillMissing means split.XTrain
        XTest = fillMissing means split.XTest
        TTrain = split.TTrain
        TTest = split.TTest
    }

/// columns から 2 次の項を作り、terms の列だけを訓練データの平均と標準偏差で標準化して線形回帰し、
/// 訓練データとテストデータの決定係数を返す
let scoreFeatureSet
    (split: TrainTestSplit<Map<string, float>, float>)
    (columns: string list)
    (terms: string list)
    : float * float =
    let select rows =
        polynomialFeatures columns rows
        |> List.map (Map.filter (fun name _ -> List.contains name terms))

    let train = select split.XTrain
    let standardizer = fitStandardizer terms train
    let xTrain = transformStandardized standardizer train
    let xTest = select split.XTest |> transformStandardized standardizer
    let model = fitLinearRegression xTrain split.TTrain

    r2Score split.TTrain (predictLinearRegression model xTrain),
    r2Score split.TTest (predictLinearRegression model xTest)
