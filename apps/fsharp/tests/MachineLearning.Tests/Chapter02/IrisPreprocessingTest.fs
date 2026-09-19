module MachineLearning.Tests.Chapter02.IrisPreprocessingTest

open System.IO
open System.Text
open FSharp.Data
open Xunit
open MachineLearning.Chapter02.IrisPreprocessing

/// BOM 付きの UTF-8 で、ヘッダーと rows を書いた一時ファイルのパスを返す
let writeCsv (rows: string) : string =
    let csvFile =
        Path.Combine(Directory.CreateTempSubdirectory("iris-").FullName, "iris.csv")

    File.WriteAllText(csvFile, "がく片長さ,がく片幅,花弁長さ,花弁幅,種類\n" + rows, UTF8Encoding(true))
    csvFile

let features (values: (string * float) list) : Map<string, float> = Map.ofList values

/// 学習用テスト: Schema を指定しないときに型プロバイダが推論する型
type NoSchemaCsv = CsvProvider<IrisSample>

[<Fact>]
let ``学習用テスト: Schema を指定しないと、空欄の無い列は decimal、空欄のある列は string と推論される`` () =
    let row = NoSchemaCsv.GetSample().Rows |> Seq.head

    Assert.IsType<decimal>(box row.がく片長さ) |> ignore
    Assert.IsType<string>(box row.花弁長さ) |> ignore
    Assert.Equal("", row.花弁長さ)

[<Fact>]
let ``BOM 付き CSV を読み込み、空欄を None にする`` () =
    let csvFile = writeCsv "0.2,0.6,,0.1,Iris-setosa\n"

    let rows = loadIris csvFile

    Assert.Equal<IrisRow list>(
        [
            {
                Features = Map.ofList [ "がく片長さ", Some 0.2; "がく片幅", Some 0.6; "花弁長さ", None; "花弁幅", Some 0.1 ]
                Species = "Iris-setosa"
            }
        ],
        rows
    )

[<Fact>]
let ``列ごとに欠損値を数える`` () =
    let rows =
        [
            Map.ofList [ "a", Some 1.0; "b", None ]
            Map.ofList [ "a", None; "b", None ]
            Map.ofList [ "a", Some 3.0; "b", Some 2.0 ]
        ]

    Assert.Equal<Map<string, int>>(Map.ofList [ "a", 1; "b", 2 ], countMissing rows)

[<Fact>]
let ``列ごとに欠損値を除いた平均を求める`` () =
    let rows =
        [
            Map.ofList [ "a", Some 1.0; "b", None ]
            Map.ofList [ "a", None; "b", Some 4.0 ]
            Map.ofList [ "a", Some 3.0; "b", Some 2.0 ]
        ]

    Assert.Equal<Map<string, float>>(Map.ofList [ "a", 2.0; "b", 3.0 ], columnMeans rows)

[<Fact>]
let ``欠損値を列ごとの値で補完する`` () =
    let rows =
        [
            Map.ofList [ "a", Some 1.0; "b", None ]
            Map.ofList [ "a", None; "b", Some 4.0 ]
        ]

    let filled = fillMissing (Map.ofList [ "a", 9.0; "b", 8.0 ]) rows

    Assert.Equal<Map<string, float> list>([ features [ "a", 1.0; "b", 8.0 ]; features [ "a", 9.0; "b", 4.0 ] ], filled)

[<Fact>]
let ``テストデータの割合を切り上げた件数で訓練データとテストデータに分ける`` () =
    let x = [ 0..9 ]
    let t = x |> List.map (fun i -> $"label{i}")

    let split = splitTrainTest 0.3 0 x t

    Assert.Equal(7, split.XTrain.Length)
    Assert.Equal(3, split.XTest.Length)

[<Fact>]
let ``件数と割合が変わっても割合どおりの件数に分ける`` () =
    let x = [ 0..19 ]

    let split = splitTrainTest 0.25 0 x x

    Assert.Equal(15, split.XTrain.Length)
    Assert.Equal(5, split.XTest.Length)

[<Fact>]
let ``すべての行を重複なく訓練データとテストデータのどちらかに入れる`` () =
    let x = [ 0..9 ]

    let split = splitTrainTest 0.3 0 x x

    Assert.Equal<int list>(x, List.sort (split.XTrain @ split.XTest))

[<Fact>]
let ``分けた後も特徴量と正解ラベルの組み合わせを保つ`` () =
    let x = [ 0..9 ]
    let t = x |> List.map (fun i -> $"label{i}")

    let split = splitTrainTest 0.3 0 x t

    Assert.Equal<string list>(split.XTrain |> List.map (fun i -> $"label{i}"), split.TTrain)
    Assert.Equal<string list>(split.XTest |> List.map (fun i -> $"label{i}"), split.TTest)

[<Fact>]
let ``同じシードなら同じ分け方になる`` () =
    let x = [ 0..9 ]

    Assert.Equal(splitTrainTest 0.3 42 x x, splitTrainTest 0.3 42 x x)

[<Fact>]
let ``訓練データの平均で訓練データとテストデータの欠損値を補完する`` () =
    // 1・2 行目のがく片長さを欠損にし、残りは互いに違う値にする
    let observed = [ 3..10 ] |> List.map (fun i -> float (i * i) + 0.5)

    let rows =
        [ ""; "" ] @ (observed |> List.map string)
        |> List.map (fun length -> $"{length},0.5,0.3,0.1,Iris-setosa")
        |> String.concat "\n"

    let split = prepareIris (writeCsv rows) 0.3 0

    let lengths (rows: Map<string, float> list) =
        rows |> List.map (fun row -> row["がく片長さ"])

    let isObserved value = List.contains value observed
    let filled = lengths (split.XTrain @ split.XTest) |> List.filter (isObserved >> not)
    let trainMean = lengths split.XTrain |> List.filter isObserved |> List.average

    Assert.Equal(2, filled.Length)
    Assert.All(filled, (fun value -> Assert.Equal(trainMean, value, 12)))
