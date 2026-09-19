module MachineLearning.Tests.Chapter13.BostonStandardizedTest

open Xunit
open MachineLearning.Chapter13.BostonStandardized

let bostonLike: BostonRow list =
    [
        "high", Some 5.0, 10.0
        "low", Some 6.0, 20.0
        "very_low", None, 30.0
        "low", Some 7.0, 40.0
    ]
    |> List.map (fun (crime, rm, price) ->
        {
            Features = Map.ofList [ "RM", rm; "PRICE", Some price ]
            Crime = crime
        })

[<Fact>]
let ``CRIME をダミー変数の列に置き換える`` () =
    let table = standardizeBoston [ "RM"; "PRICE" ] bostonLike

    Assert.Equal<string list>([ "RM"; "PRICE"; "low"; "very_low" ], table.Columns)

[<Fact>]
let ``欠損値を補完してから各列を平均 0 と標準偏差 1 にそろえる`` () =
    let table = standardizeBoston [ "RM"; "PRICE" ] bostonLike

    for values in Array.transpose table.X do
        let mean = Array.average values
        let std = sqrt (values |> Array.averageBy (fun v -> (v - mean) ** 2.0))
        Assert.Equal(0.0, mean, 1e-9)
        Assert.Equal(1.0, std, 1e-9)
