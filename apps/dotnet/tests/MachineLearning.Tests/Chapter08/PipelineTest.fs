module MachineLearning.Tests.Chapter08.PipelineTest

open Xunit
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter08.SurvivedData
open MachineLearning.Chapter08.WeightedTree
open MachineLearning.Chapter08.Pipeline

/// (Pclass, Sex, Age, SibSp, Parch, Fare, Embarked) の組から乗客を作る
let passenger (pclass, sex, age, sibSp, parch, fare, embarked) : Passenger =
    {
        Pclass = pclass
        Sex = sex
        Age = age
        SibSp = sibSp
        Parch = parch
        Fare = fare
        Embarked = embarked
    }

/// 女性が生存、男性が死亡という単純な規則の訓練データ。2 等客室の女性は 1 人だけで、年齢が欠けている
let trainX =
    [
        1, "female", Some 30.0, 0, 0, 80.0, Some "C"
        2, "female", None, 1, 0, 20.0, Some "S"
        3, "female", Some 22.0, 0, 1, 9.0, None
        3, "female", Some 18.0, 0, 0, 8.0, Some "Q"
        1, "male", Some 45.0, 0, 0, 60.0, Some "S"
        2, "male", None, 0, 0, 13.0, Some "S"
        3, "male", Some 25.0, 1, 0, 7.0, Some "S"
        3, "male", Some 33.0, 0, 0, 8.0, None
    ]
    |> List.map passenger

let trainT = [ 1; 1; 1; 1; 0; 0; 0; 0 ]

let newPassengers =
    [ 2, "female", None, 0, 0, 12.0, None; 1, "male", None, 1, 1, 70.0, Some "C" ]
    |> List.map passenger

let options =
    {
        MaxDepth = Some 3
        ClassWeight = Unweighted
    }

[<Fact>]
let ``欠損値を含むデータで学習して予測できる`` () =
    let pipeline = fitPipeline options trainX trainT

    Assert.Equal<int list>([ 1; 0 ], predict pipeline newPassengers)

[<Fact>]
let ``乗客 1 人分のデータから、生存（1）か死亡（0）かを予測する`` () =
    let pipeline = fitPipeline options trainX trainT

    Assert.Equal(1, predictPassenger pipeline newPassengers[0])
    Assert.Equal(0, predictPassenger pipeline newPassengers[1])

[<Fact>]
let ``正解率と見つけた生存者の数を求める`` () =
    let pipeline = fitPipeline options trainX trainT

    let split =
        {
            XTrain = trainX
            XTest = newPassengers
            TTrain = trainT
            TTest = [ 1; 1 ]
        }

    Assert.Equal(
        {
            TrainAccuracy = 1.0
            TestAccuracy = 0.5
            FoundSurvivors = 1
            Survivors = 2
        },
        evaluate pipeline split
    )
