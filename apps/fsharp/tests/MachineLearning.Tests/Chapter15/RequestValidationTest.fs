module MachineLearning.Tests.Chapter15.RequestValidationTest

open Xunit
open MachineLearning.Chapter15.Domain
open MachineLearning.Chapter15.RequestValidation
open MachineLearning.Tests.Chapter15.Stubs

let movieJson = """{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}"""

/// 乗客の JSON の項目と値（JSON の書き方のまま）
let passengerFields =
    [
        "pclass", "1"
        "sex", "\"female\""
        "age", "null"
        "sib_sp", "0"
        "parch", "0"
        "fare", "50"
        "embarked", "\"C\""
    ]

/// 項目と値の組から JSON のオブジェクトを組み立てる
let toJson (fields: (string * string) list) : string =
    fields
    |> List.map (fun (name, value) -> $"\"{name}\": {value}")
    |> String.concat ", "
    |> sprintf "{%s}"

/// 1 つの項目だけ値を差し替えた乗客の JSON
let passengerJsonWith (field: string) (value: string) : string =
    passengerFields
    |> List.map (fun (name, original) -> name, (if name = field then value else original))
    |> toJson

let passengerJson = toJson passengerFields

[<Fact>]
let ``映画の JSON を Movie にする`` () =
    Assert.Equal(Ok movie, parseMovie movieJson)

[<Fact>]
let ``JSON として読めなければその理由を返す`` () =
    Assert.Equal(Error [ "JSON の形式が正しくありません" ], parseMovie "{")

[<Theory>]
[<InlineData("""{"sns1": -1, "sns2": 500, "actor": 3000, "original": 1}""", "sns1 は 0 以上にしてください")>]
[<InlineData("""{"sns1": 200, "sns2": 500, "actor": "多い", "original": 1}""", "actor は数値にしてください")>]
[<InlineData("""{"sns1": 200, "sns2": 500, "actor": 3000, "original": 2}""", "original は 0、1 のどれかにしてください")>]
[<InlineData("""{"sns1": 200, "actor": 3000, "original": 1}""", "sns2 を指定してください")>]
let ``映画の特徴量が不正ならその理由を返す`` (json: string, reason: string) =
    Assert.Equal(Error [ reason ], parseMovie json)

[<Fact>]
let ``乗客の JSON を Passenger にする`` () =
    Assert.Equal(Ok passenger, parsePassenger passengerJson)

[<Fact>]
let ``年齢と乗船港は省略できる`` () =
    let json = """{"pclass": 3, "sex": "male", "sib_sp": 1, "parch": 2, "fare": 8}"""

    let expected =
        {
            Pclass = Third
            Sex = Male
            Age = None
            SibSp = 1
            Parch = 2
            Fare = 8.0
            Embarked = None
        }

    Assert.Equal(Ok expected, parsePassenger json)

[<Theory>]
[<InlineData("pclass", "4", "pclass は 1、2、3 のどれかにしてください")>]
[<InlineData("sex", "\"unknown\"", "sex は male、female のどれかにしてください")>]
[<InlineData("fare", "-1", "fare は 0 以上にしてください")>]
[<InlineData("embarked", "\"X\"", "embarked は C、Q、S のどれかにしてください")>]
[<InlineData("sib_sp", "0.5", "sib_sp は整数にしてください")>]
let ``乗客の特徴量が不正ならその理由を返す`` (field: string, value: string, reason: string) =
    Assert.Equal(Error [ reason ], parsePassenger (passengerJsonWith field value))

[<Fact>]
let ``不正な理由をまとめて返す`` () =
    let json = """{"pclass": 4, "sex": "female", "sib_sp": 0, "parch": 0, "fare": -1}"""

    Assert.Equal(Error [ "pclass は 1、2、3 のどれかにしてください"; "fare は 0 以上にしてください" ], parsePassenger json)
