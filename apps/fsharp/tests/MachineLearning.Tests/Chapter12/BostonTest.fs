module MachineLearning.Tests.Chapter12.BostonTest

open FSharp.Data
open Xunit
open MachineLearning.Chapter12.Boston
open MachineLearning.Tests.Chapter07.MatrixTest

let byRm (values: float list) : Map<string, float> list =
    values |> List.map (fun value -> Map.ofList [ "RM", value ])

[<Fact>]
let ``訓練データで標準化してから 2 乗の列を加える`` () =
    let x = byRm [ 1.0; 2.0; 3.0 ]

    let scaler = fitPolynomialScaler [ "RM" ] x

    let z = 1.224744871391589
    let rows = transformPolynomial scaler x
    assertValues [ -z; z * z ] rows[0]
    assertValues [ 0.0; 0.0 ] rows[1]
    assertValues [ z; z * z ] rows[2]

[<Fact>]
let ``2 次の項の名前は 2 乗なら ^2、積なら空白でつなぐ`` () =
    let x =
        [
            Map.ofList [ "RM", 1.0; "LSTAT", 2.0 ]
            Map.ofList [ "RM", 3.0; "LSTAT", 5.0 ]
        ]

    let scaler = fitPolynomialScaler [ "RM"; "LSTAT" ] x

    Assert.Equal<string list>([ "RM"; "LSTAT"; "RM^2"; "RM LSTAT"; "LSTAT^2" ], scaler.FeatureNames)

[<Fact>]
let ``標準偏差の 3 倍より平均から離れた値を持つ行を外れ値として除く`` () =
    // 0 が 20 件と 100 が 1 件。100 の z スコアは約 4.4 で、0 の z スコアは約 0.2
    let rows =
        (List.replicate 20 0.0 @ [ 100.0 ])
        |> List.map (fun value -> Map.ofList [ "RM", value; "PRICE", 1.0 ])

    let kept = removeOutliers [ "RM" ] 3.0 rows

    Assert.Equal(20, kept.Length)
    Assert.DoesNotContain(kept, fun row -> row["RM"] = 100.0)

/// 学習用テスト: 空欄の無いサンプルから作った型
type StrictCsv = CsvProvider<BostonSample>

[<Fact>]
let ``学習用テスト: サンプルに空欄が無い列に空欄があると、AssumeMissingValues が無ければ読めない`` () =
    let header =
        "CRIME,ZN,INDUS,CHAS,NOX,RM,AGE,DIS,RAD,TAX,PTRATIO,B,LSTAT,PRICE
"

    let row = "low,0,8.14,0,0.538,5.95,82,3.99,,307,21,232.6,27.71,13.2"

    let error =
        Assert.ThrowsAny<exn>(fun () -> StrictCsv.Parse(header + row).Rows |> Seq.toList |> ignore)

    Assert.Contains("RAD is missing", error.Message)
    Assert.Equal(13.2, (BostonCsv.Parse(header + row).Rows |> Seq.head).PRICE)
