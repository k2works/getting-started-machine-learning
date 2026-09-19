module MachineLearning.Tests.Chapter14.SpendingTest

open System.IO
open Xunit
open MachineLearning.Chapter14.Spending

/// 見出しと 1 行だけの架空の CSV を一時ファイルに書き出す
let writeCsv (rows: string) : string =
    let path = Path.GetTempFileName()

    File.WriteAllText(path, "Channel,Region,Fresh,Milk,Grocery,Frozen,Detergents_Paper,Delicassen\n" + rows)

    path

[<Fact>]
let ``Channel と Region を除いた支出額の列を読み込む`` () =
    let rows = loadSpending (writeCsv "1,2,100,200,300,400,500,600\n")

    Assert.Equal<string list>(
        [ "Fresh"; "Milk"; "Grocery"; "Frozen"; "Detergents_Paper"; "Delicassen" ],
        SpendingColumns
    )

    Assert.Equal<float list>(
        [ 100.0; 200.0; 300.0; 400.0; 500.0; 600.0 ],
        SpendingColumns |> List.map (fun column -> rows.Head[column])
    )

[<Fact>]
let ``列ごとに平均 0 標準偏差 1 の点の配列に変換する`` () =
    let rows =
        [ 10.0, 5.0; 20.0, 5.0; 30.0, 8.0 ]
        |> List.map (fun (fresh, milk) -> Map.ofList [ "Fresh", fresh; "Milk", milk ])

    let points = toStandardizedPoints [ "Fresh"; "Milk" ] rows

    for values in Array.transpose points do
        let mean = Array.average values
        Assert.Equal(0.0, mean, 1e-12)
        Assert.Equal(1.0, sqrt (values |> Array.averageBy (fun v -> (v - mean) ** 2.0)), 1e-12)
