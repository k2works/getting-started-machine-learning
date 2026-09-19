module MachineLearning.Chapter12.Main

open System.IO
open MachineLearning.Dataset
open MachineLearning.Chapter07.RegressionMetrics
open MachineLearning.Chapter12.Boston
open MachineLearning.Chapter12.MlNetRegularization
open MachineLearning.Chapter12.Regularization

[<Literal>]
let TestSize = 0.3

[<Literal>]
let ValidationSize = 0.3

[<Literal>]
let Seed = 0

let Alphas = [ 0.0; 0.1; 1.0; 10.0; 100.0 ]

[<Literal>]
let LassoAlpha = 1.0

let SeedsToCompare = [ 0..4 ]

/// 検証データで選んだ alpha の実験と、線形回帰（alpha = 0）・選んだリッジ回帰のテストデータの決定係数
type Comparison =
    {
        Experiments: Experiment list
        Best: Experiment
        LinearScore: float
        RidgeScore: float
    }

/// 検証データで alpha を選び、線形回帰と選んだリッジ回帰をテストデータで 1 回だけ評価する
let compareOnTestData (dataset: BostonDataset) : Comparison =
    let experiments =
        runRidgeExperiments Alphas dataset.XTrain dataset.TTrain dataset.XValid dataset.TValid

    let best = bestExperiment experiments

    let score alpha =
        fitRidge alpha dataset.XTrain dataset.TTrain
        |> fun model -> predictRegularized model dataset.XTest
        |> r2Score dataset.TTest

    {
        Experiments = experiments
        Best = best
        LinearScore = score 0.0
        RidgeScore = score best.Alpha
    }

/// Boston.csv で正則化の強さを変えた実験と、ラッソ回帰・ML.NET の結果、シードごとの比較を表示する
let run (print: string -> unit) : unit =
    let csvFile = Path.Combine(dataDir (), "Boston.csv")
    let rows = loadBoston csvFile
    let kept = rows |> removeOutliers (FeatureNames @ [ Target ]) OutlierThreshold
    let dataset = prepareBoston csvFile TestSize ValidationSize Seed
    print $"データ件数: {kept.Length}（外れ値 {rows.Length - kept.Length} 件を除外）"
    print $"訓練データ: {dataset.TTrain.Length} 件, 検証データ: {dataset.TValid.Length} 件, テストデータ: {dataset.TTest.Length} 件"
    print $"""特徴量: {String.concat ", " dataset.FeatureNames}"""

    let comparison = compareOnTestData dataset
    print "alpha\t訓練 R²\t検証 R²\t係数の絶対値の合計"

    for e in comparison.Experiments do
        print $"{e.Alpha}\t{e.TrainScore:F4}\t{e.ValidationScore:F4}\t{e.CoefficientAbsSum:F3}"

    print $"検証データで選んだ alpha: {comparison.Best.Alpha}"
    print $"テストデータの決定係数: 線形回帰 {comparison.LinearScore:F4}, リッジ回帰 {comparison.RidgeScore:F4}"

    let mlNet =
        fitRidgeWithMlNet comparison.Best.Alpha dataset.XTrain dataset.TTrain
        |> fun model -> predictRegularized model dataset.XTest
        |> r2Score dataset.TTest

    print $"ML.NET（SDCA）のリッジ回帰のテストデータの決定係数: {mlNet:F4}"

    let lasso = fitLasso LassoAlpha dataset.XTrain dataset.TTrain
    let zeros = zeroCoefficientNames lasso.Coefficients dataset.FeatureNames
    print $"""ラッソ回帰（alpha={LassoAlpha}）で係数が 0 になった特徴量: {String.concat ", " zeros}"""

    print ""
    print "シード\t選んだ alpha\t線形回帰\tリッジ回帰"

    for seed in SeedsToCompare do
        let c = compareOnTestData (prepareBoston csvFile TestSize ValidationSize seed)
        print $"{seed}\t{c.Best.Alpha}\t{c.LinearScore:F4}\t{c.RidgeScore:F4}"
