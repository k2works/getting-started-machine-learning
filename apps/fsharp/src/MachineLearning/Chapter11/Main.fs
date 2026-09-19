module MachineLearning.Chapter11.Main

open System.IO
open MachineLearning.Dataset
open MachineLearning.Chapter11.Experiments

/// Survived（決定木）と cinema（線形回帰）を、K 分割交差検証の平均で評価して表示する
let run (print: string -> unit) : unit =
    print $"Survived（決定木、{NSplits} 分割交差検証の平均）"

    for name, score in evaluateSurvived (Path.Combine(dataDir (), "Survived.csv")) do
        print $"  {name}: {score:F4}"

    print $"cinema（線形回帰、{NSplits} 分割交差検証の平均）"

    for name, score in evaluateCinema (Path.Combine(dataDir (), "cinema.csv")) do
        print $"  {name}: {score:F2}"
