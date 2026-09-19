module MachineLearning.Tests.Chapter09.BikeWeatherTest

open System.IO
open System.Text
open Xunit
open MachineLearning.Chapter09.BikeWeather

[<Fact>]
let ``Shift_JIS の CSV を読み込み、天気の番号から名前への Map にする`` () =
    let csvFile = Path.GetTempFileName()
    File.WriteAllText(csvFile, "weather_id,weather\n1,晴れ\n2,曇り\n", shiftJis ())

    Assert.Equal<Map<int, string>>(Map.ofList [ 1, "晴れ"; 2, "曇り" ], loadWeather csvFile)

[<Fact>]
let ``学習用テスト: Shift_JIS のファイルを UTF-8 として読むと文字化けする`` () =
    let csvFile = Path.GetTempFileName()
    File.WriteAllText(csvFile, "晴れ", shiftJis ())

    Assert.NotEqual<string>("晴れ", File.ReadAllText(csvFile, Encoding.UTF8))

let bike =
    [
        {
            Day = "2011-01-01"
            WeatherId = 1
            Count = 100
        }
        {
            Day = "2011-01-02"
            WeatherId = 2
            Count = 50
        }
        {
            Day = "2011-01-03"
            WeatherId = 1
            Count = 300
        }
        {
            Day = "2011-01-04"
            WeatherId = 9
            Count = 10
        }
    ]

[<Fact>]
let ``天気の番号で天気の名前を結合し、名前が無い行は除く`` () =
    let joined = joinWeather (Map.ofList [ 1, "晴れ"; 2, "曇り" ]) bike

    Assert.Equal<(string * int) list>([ "晴れ", 100; "曇り", 50; "晴れ", 300 ], joined |> List.map (fun (w, b) -> w, b.Count))

[<Fact>]
let ``天気ごとの平均利用者数を、多い順に並べる`` () =
    let joined = joinWeather (Map.ofList [ 1, "晴れ"; 2, "曇り" ]) bike

    Assert.Equal<(string * float) list>([ "晴れ", 200.0; "曇り", 50.0 ], meanCountByWeather joined)
