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
