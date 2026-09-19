module MachineLearning.Chapter08.SurvivedData

open FSharp.Data

/// 型プロバイダが列の名前と型を知るためのサンプル（架空の値）
[<Literal>]
let SurvivedSample =
    "PassengerId,Survived,Pclass,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked\n1,0,3,male,,0,0,X-1,8.5,,S"

type SurvivedCsv =
    CsvProvider<
        SurvivedSample,
        Schema="int,int,int,string,float option,int,int,string,float,string option,string option"
     >

/// 乗客 1 人分の特徴量。年齢と乗船した港の型を型引数にして、補完の前と後を別の型で表す
type PassengerOf<'Age, 'Embarked> =
    {
        Pclass: int
        Sex: string
        Age: 'Age
        SibSp: int
        Parch: int
        Fare: float
        Embarked: 'Embarked
    }

/// 読み込んだままの乗客。年齢と乗船した港は欠けていることがある
type Passenger = PassengerOf<float option, string option>

/// 欠損値を補完した乗客
type FilledPassenger = PassengerOf<float, string>

type SurvivedRow =
    {
        PassengerId: int
        Survived: int
        Passenger: Passenger
        Ticket: string
        Cabin: string option
    }

let loadSurvived (csvFile: string) : SurvivedRow list =
    SurvivedCsv.Load(csvFile).Rows
    |> Seq.map (fun row ->
        {
            PassengerId = row.PassengerId
            Survived = row.Survived
            Passenger =
                {
                    Pclass = row.Pclass
                    Sex = row.Sex
                    Age = row.Age
                    SibSp = row.SibSp
                    Parch = row.Parch
                    Fare = row.Fare
                    Embarked = row.Embarked
                }
            Ticket = row.Ticket
            Cabin = row.Cabin
        })
    |> Seq.toList

/// 特徴量（乗客）と正解ラベル（Survived 列）に分ける
let splitFeaturesAndTarget (rows: SurvivedRow list) : Passenger list * int list =
    rows |> List.map (fun row -> row.Passenger, row.Survived) |> List.unzip
