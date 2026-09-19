module MachineLearning.Chapter11.Datasets

open FSharp.Data
open MachineLearning.Chapter02.IrisPreprocessing

/// 型プロバイダが列の名前と型を知るためのサンプル（架空の値）
[<Literal>]
let SurvivedSample =
    "PassengerId,Survived,Pclass,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked\n1,0,3,male,,0,0,T,0.5,,S"

/// 年齢には空欄があるので float option として読む
type SurvivedCsv = CsvProvider<SurvivedSample, Schema="Age=float option">

/// 客室クラス・年齢・男性かどうかを特徴量にし、生存（"0" か "1"）を正解ラベルにする
let prepareSurvived (csv: SurvivedCsv) : Map<string, float> list * string list =
    let rows = csv.Rows |> Seq.toList

    let features =
        rows
        |> List.map (fun row ->
            Map.ofList
                [
                    "Pclass", Some(float row.Pclass)
                    "Age", row.Age
                    "male", Some(if row.Sex = "male" then 1.0 else 0.0)
                ])

    fillMissing (columnMeans features) features, rows |> List.map (fun row -> string row.Survived)

[<Literal>]
let CinemaSample = "cinema_id,SNS1,SNS2,actor,original,sales\n1,,0.5,,0,0.5"

/// 特徴量の列には空欄があるので float option として読む
type CinemaCsv =
    CsvProvider<
        CinemaSample,
        Schema="SNS1=float option,SNS2=float option,actor=float option,original=float option,sales=float"
     >

let CinemaFeatures = [ "SNS1"; "SNS2"; "actor"; "original" ]

/// SNS1・SNS2・actor・original を特徴量にして欠損値を平均値で補完し、興行収入を正解ラベルにする
let prepareCinema (csv: CinemaCsv) : Map<string, float> list * float list =
    let rows = csv.Rows |> Seq.toList

    let features =
        rows
        |> List.map (fun row ->
            Map.ofList
                [
                    "SNS1", row.SNS1
                    "SNS2", row.SNS2
                    "actor", row.Actor
                    "original", row.Original
                ])

    fillMissing (columnMeans features) features, rows |> List.map (fun row -> row.Sales)
