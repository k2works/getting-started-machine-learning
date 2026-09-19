module MachineLearning.Chapter09.Standardizer

/// 訓練データから求めた、列ごとの平均と標準偏差（件数で割る母標準偏差）
type Standardizer =
    {
        Means: Map<string, float>
        Stds: Map<string, float>
    }

/// 指定した列の平均と標準偏差を、訓練データから求める
let fitStandardizer (columns: string list) (train: Map<string, float> list) : Standardizer =
    let statsOf column =
        let values = train |> List.map (fun row -> row[column])
        let mean = List.average values
        mean, sqrt (values |> List.averageBy (fun value -> (value - mean) ** 2.0))

    let stats = columns |> List.map (fun column -> column, statsOf column)

    {
        Means = stats |> List.map (fun (column, (mean, _)) -> column, mean) |> Map.ofList
        Stds = stats |> List.map (fun (column, (_, std)) -> column, std) |> Map.ofList
    }

/// 標準化する列だけを (値 - 平均) / 標準偏差 にする。標準偏差が 0 の列は 0 にする。ほかの列はそのまま残す
let transformStandardized (standardizer: Standardizer) (rows: Map<string, float> list) : Map<string, float> list =
    let standardize column value =
        match Map.tryFind column standardizer.Means with
        | None -> value
        | Some _ when standardizer.Stds[column] = 0.0 -> 0.0
        | Some mean -> (value - mean) / standardizer.Stds[column]

    rows |> List.map (Map.map standardize)
