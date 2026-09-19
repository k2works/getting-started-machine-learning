module MachineLearning.Chapter08.Transformers

open MachineLearning.Chapter03.DecisionTree

/// グループごとの中央値で補完するために、訓練データから求めた値
type GroupMedians<'G when 'G: comparison> =
    {
        Medians: Map<'G, float>
        /// 訓練データに無いグループのための、全体の中央値
        OverallMedian: float
    }

/// 小さい順に並べて、件数が奇数なら真ん中の値、偶数なら真ん中の 2 つの平均
let median (values: float list) : float =
    let sorted = List.sort values
    let middle = sorted.Length / 2

    if sorted.Length % 2 = 1 then
        sorted[middle]
    else
        (sorted[middle - 1] + sorted[middle]) / 2.0

let fitGroupMedians (groupOf: 'R -> 'G) (valueOf: 'R -> float option) (rows: 'R list) : GroupMedians<'G> =
    let known =
        rows
        |> List.choose (fun row -> valueOf row |> Option.map (fun value -> groupOf row, value))

    {
        Medians =
            known
            |> List.groupBy fst
            |> List.map (fun (group, pairs) -> group, median (List.map snd pairs))
            |> Map.ofList
        OverallMedian = known |> List.map snd |> median
    }

/// 値が欠けていれば、グループの中央値で補う。訓練データに無いグループなら全体の中央値で補う
let imputeGroupMedian (imputer: GroupMedians<'G>) (group: 'G) (value: float option) : float =
    value
    |> Option.orElse (Map.tryFind group imputer.Medians)
    |> Option.defaultValue imputer.OverallMedian

/// 欠けていない値のうち最も多い値。同数なら先に現れた値を選ぶ（第 3 章の majority と同じ）
let mostFrequent (values: 'V option list) : 'V = values |> List.choose id |> majority

/// ダミー変数にするために、訓練データから求めた値
type DummyEncoder =
    {
        /// 列ごとの、ダミー変数にするカテゴリ（並べて最初のカテゴリを除く）
        Categories: Map<string, string list>
    }

let fitDummies (rows: Map<string, string> list) : DummyEncoder =
    let categoriesOf column =
        rows
        |> List.map (fun row -> row[column])
        |> List.distinct
        |> List.sort
        |> List.tail

    {
        Categories =
            rows.Head
            |> Map.keys
            |> Seq.map (fun column -> column, categoriesOf column)
            |> Map.ofSeq
    }

/// カテゴリの列を、「列名_カテゴリ」という名前の 0 と 1 の列にする
let encodeDummies (encoder: DummyEncoder) (row: Map<string, string>) : Map<string, float> =
    encoder.Categories
    |> Map.toList
    |> List.collect (fun (column, categories) ->
        categories
        |> List.map (fun category -> $"{column}_{category}", (if row[column] = category then 1.0 else 0.0)))
    |> Map.ofList
