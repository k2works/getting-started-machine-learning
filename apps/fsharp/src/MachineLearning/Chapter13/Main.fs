module MachineLearning.Chapter13.Main

open System.IO
open MachineLearning.Dataset
open MachineLearning.Chapter13.BostonStandardized
open MachineLearning.Chapter13.Pca
open MachineLearning.Chapter13.StatsPca

[<Literal>]
let Threshold = 0.8

[<Literal>]
let TopK = 3

[<Literal>]
let ComponentsToExplain = 2

/// FSharp.Stats の結果と一致したとみなす差の上限
[<Literal>]
let Tolerance = 1e-9

let private within (a: float) (b: float) : bool = abs (a - b) <= Tolerance

/// 2 つの配列を先頭から組にして、agree が真になる組の数を数える
let private countAgreed (agree: 'T -> 'T -> bool) (a: 'T[]) (b: 'T[]) : int =
    Array.map2 agree a b |> Array.filter id |> Array.length

let private formatLoadings (loadings: (string * float) list) : string =
    loadings
    |> List.map (fun (column, value) -> $"{column} {value:F3}")
    |> String.concat ", "

/// Boston.csv を標準化して主成分分析し、寄与率と主成分への影響が大きい列、FSharp.Stats との一致数を表示する
let run (print: string -> unit) : unit =
    let table = loadStandardizedBoston (Path.Combine(dataDir (), "Boston.csv"))
    let model = fitPca table.Columns.Length table.X
    let ratios = model.ExplainedVarianceRatio
    let needed = componentsNeeded Threshold ratios
    let shown = ratios |> Array.truncate needed
    print $"データ件数: {table.X.Length}, 列数: {table.Columns.Length}"

    shown
    |> Array.mapi (fun i ratio -> $"PC{i + 1} {ratio:F4}")
    |> String.concat ", "
    |> fun text -> print $"寄与率: {text}"

    print $"累積寄与率が {Threshold} に届く主成分の数: {needed}（累積寄与率 {Array.sum shown:F4}）"

    for i in 0 .. ComponentsToExplain - 1 do
        let loadings = topLoadings TopK table.Columns model.Components[i]
        print $"第 {i + 1} 主成分で影響の大きい列: {formatLoadings loadings}"

    let library = fitPcaWithFSharpStats table.Columns.Length table.X
    let ratiosAgreed = countAgreed within ratios library.ExplainedVarianceRatio

    let componentsAgreed =
        countAgreed (Array.forall2 within) model.Components library.Components

    print
        $"FSharp.Stats の PCA と一致した数（許容誤差 {Tolerance}）: 寄与率 {ratiosAgreed}/{ratios.Length}, 主成分 {componentsAgreed}/{model.Components.Length}"
