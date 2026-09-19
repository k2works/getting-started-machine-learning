module MachineLearning.Chapter08.Pipeline

open MachineLearning.Chapter01.KinokoTakenoko
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter03.DecisionTree
open MachineLearning.Chapter08.SurvivedData
open MachineLearning.Chapter08.Transformers
open MachineLearning.Chapter08.WeightedTree

/// 学習済みの前処理とモデル。予測するときは、ここに覚えた値だけを使う
type FittedPipeline =
    {
        Age: GroupMedians<int * string>
        Embarked: string
        Dummies: DummyEncoder
        Tree: Tree<int>
    }

/// 年齢を補完する。乗船した港の型 'E はそのまま引き継ぐ
let fillAge (imputer: GroupMedians<int * string>) (passenger: PassengerOf<float option, 'E>) : PassengerOf<float, 'E> =
    {
        Pclass = passenger.Pclass
        Sex = passenger.Sex
        Age = imputeGroupMedian imputer (passenger.Pclass, passenger.Sex) passenger.Age
        SibSp = passenger.SibSp
        Parch = passenger.Parch
        Fare = passenger.Fare
        Embarked = passenger.Embarked
    }

/// 乗船した港を補完する。年齢の型 'A はそのまま引き継ぐ
let fillEmbarked (embarked: string) (passenger: PassengerOf<'A, string option>) : PassengerOf<'A, string> =
    {
        Pclass = passenger.Pclass
        Sex = passenger.Sex
        Age = passenger.Age
        SibSp = passenger.SibSp
        Parch = passenger.Parch
        Fare = passenger.Fare
        Embarked = passenger.Embarked |> Option.defaultValue embarked
    }

let private categoriesOf (passenger: FilledPassenger) : Map<string, string> =
    Map.ofList [ "Sex", passenger.Sex; "Embarked", passenger.Embarked ]

/// 補完した乗客を、数値の列とダミー変数の列からなる特徴量にする
let toFeatures (dummies: DummyEncoder) (passenger: FilledPassenger) : Map<string, float> =
    let numbers =
        [
            "Pclass", float passenger.Pclass
            "Age", passenger.Age
            "SibSp", float passenger.SibSp
            "Parch", float passenger.Parch
            "Fare", passenger.Fare
        ]

    Map.ofList (numbers @ Map.toList (encodeDummies dummies (categoriesOf passenger)))

/// 学習済みの前処理を関数の合成でつないだ、乗客を決定木に渡せる特徴量にする関数
let transform (pipeline: FittedPipeline) : Passenger -> Map<string, float> =
    fillAge pipeline.Age
    >> fillEmbarked pipeline.Embarked
    >> toFeatures pipeline.Dummies

/// 前処理を順に学習・変換してから、決定木を学習する
let fitPipeline (options: TreeOptions) (x: Passenger list) (t: int list) : FittedPipeline =
    let age = fitGroupMedians (fun p -> p.Pclass, p.Sex) (fun p -> p.Age) x
    let ageFilled = x |> List.map (fillAge age)
    let embarked = ageFilled |> List.map (fun p -> p.Embarked) |> mostFrequent
    let filled = ageFilled |> List.map (fillEmbarked embarked)
    let dummies = filled |> List.map categoriesOf |> fitDummies
    let features = filled |> List.map (toFeatures dummies)

    {
        Age = age
        Embarked = embarked
        Dummies = dummies
        Tree = fitWeighted options.MaxDepth features t (classWeights options.ClassWeight t)
    }

/// 乗客 1 人分のデータから、生存（1）か死亡（0）かを予測する
let predictPassenger (pipeline: FittedPipeline) : Passenger -> int =
    transform pipeline >> predictOne pipeline.Tree

let predict (pipeline: FittedPipeline) (x: Passenger list) : int list =
    x |> List.map (predictPassenger pipeline)

type Evaluation =
    {
        TrainAccuracy: float
        TestAccuracy: float
        /// 実際の生存者のうち、生存と予測できた人数
        FoundSurvivors: int
        /// 実際の生存者の人数
        Survivors: int
    }

let evaluate (pipeline: FittedPipeline) (split: TrainTestSplit<Passenger, int>) : Evaluation =
    let predictions = predict pipeline split.XTest

    {
        TrainAccuracy = accuracy (predict pipeline split.XTrain) split.TTrain
        TestAccuracy = accuracy predictions split.TTest
        FoundSurvivors =
            List.zip predictions split.TTest
            |> List.filter (fun pair -> pair = (1, 1))
            |> List.length
        Survivors = split.TTest |> List.filter ((=) 1) |> List.length
    }
