module MachineLearning.Tests.Chapter11.DatasetsTest

open Xunit
open MachineLearning.Chapter11.Datasets

[<Fact>]
let ``客室クラスと年齢と男性かどうかを特徴量にし生存を正解ラベルにする`` () =
    let csv =
        SurvivedCsv.Parse(
            "PassengerId,Survived,Pclass,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked\n"
            + "1,0,3,male,30,0,0,T1,8.0,,S\n"
            + "2,1,1,female,40,1,0,T2,60.0,C1,C"
        )

    let x, t = prepareSurvived csv

    Assert.Equal<Map<string, float> list>(
        [
            Map.ofList [ "Pclass", 3.0; "Age", 30.0; "male", 1.0 ]
            Map.ofList [ "Pclass", 1.0; "Age", 40.0; "male", 0.0 ]
        ],
        x
    )

    Assert.Equal<string list>([ "0"; "1" ], t)

[<Fact>]
let ``年齢の欠損値を年齢の平均値で補完する`` () =
    let csv =
        SurvivedCsv.Parse(
            "PassengerId,Survived,Pclass,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked\n"
            + "1,0,3,male,20,0,0,T1,8.0,,S\n"
            + "2,1,1,female,,0,0,T2,60.0,,S\n"
            + "3,1,2,female,40,0,0,T3,20.0,,S"
        )

    let x, _ = prepareSurvived csv

    Assert.Equal<float list>([ 20.0; 30.0; 40.0 ], x |> List.map (fun row -> row["Age"]))

[<Fact>]
let ``興行収入を正解ラベルにし特徴量の欠損値を平均値で補完する`` () =
    let csv =
        CinemaCsv.Parse(
            "cinema_id,SNS1,SNS2,actor,original,sales\n"
            + "101,100,500,,0,9000\n"
            + "102,,600,20.5,1,9500\n"
            + "103,300,700,40.5,0,10000"
        )

    let x, t = prepareCinema csv

    Assert.Equal<Map<string, float> list>(
        [
            Map.ofList [ "SNS1", 100.0; "SNS2", 500.0; "actor", 30.5; "original", 0.0 ]
            Map.ofList [ "SNS1", 200.0; "SNS2", 600.0; "actor", 20.5; "original", 1.0 ]
            Map.ofList [ "SNS1", 300.0; "SNS2", 700.0; "actor", 40.5; "original", 0.0 ]
        ],
        x
    )

    Assert.Equal<float list>([ 9000.0; 9500.0; 10000.0 ], t)
