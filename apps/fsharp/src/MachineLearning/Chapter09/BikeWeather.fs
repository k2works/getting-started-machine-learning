module MachineLearning.Chapter09.BikeWeather

open System.IO
open System.Text
open FSharp.Data

/// Shift_JIS のエンコーディング。.NET では、コードページのエンコーディングを使う前に一度登録が要る
let shiftJis () : Encoding =
    Encoding.RegisterProvider CodePagesEncodingProvider.Instance
    Encoding.GetEncoding "shift_jis"

/// 型プロバイダが列の名前と型を知るためのサンプル（架空の値）
[<Literal>]
let BikeSample =
    "dteday\tholiday\tweekday\tworkingday\tweather_id\tcnt\n2011-01-01\t0\t6\t0\t2\t985"

type BikeTsv = CsvProvider<BikeSample, Separators="\t">

[<Literal>]
let WeatherSample = "weather_id,weather\n1,sample"

type WeatherCsv = CsvProvider<WeatherSample>

/// 1 日分の利用者数
type BikeDay =
    {
        Day: string
        WeatherId: int
        Count: int
    }

let loadBike (tsvFile: string) : BikeDay list =
    BikeTsv.Load(tsvFile).Rows
    |> Seq.map (fun row ->
        {
            Day = row.Dteday.ToString "yyyy-MM-dd"
            WeatherId = row.Weather_id
            Count = row.Cnt
        })
    |> Seq.toList

/// Shift_JIS の weather.csv を、天気の番号から名前への Map にする
let loadWeather (csvFile: string) : Map<int, string> =
    use reader = new StreamReader(csvFile, shiftJis ())

    WeatherCsv.Load(reader).Rows
    |> Seq.map (fun row -> row.Weather_id, row.Weather)
    |> Map.ofSeq

/// 天気の番号で天気の名前を結合する。名前の無い番号の行は除く
let joinWeather (weather: Map<int, string>) (bike: BikeDay list) : (string * BikeDay) list =
    bike
    |> List.choose (fun day -> Map.tryFind day.WeatherId weather |> Option.map (fun name -> name, day))

/// 天気ごとの平均利用者数を、多い順に並べる
let meanCountByWeather (joined: (string * BikeDay) list) : (string * float) list =
    joined
    |> List.groupBy fst
    |> List.map (fun (weather, days) -> weather, days |> List.averageBy (fun (_, day) -> float day.Count))
    |> List.sortByDescending snd
