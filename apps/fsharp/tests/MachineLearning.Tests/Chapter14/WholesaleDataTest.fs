module MachineLearning.Tests.Chapter14.WholesaleDataTest

open System.IO
open Xunit
open MachineLearning.Dataset
open MachineLearning.Chapter14.KMeans
open MachineLearning.Chapter14.Spending

let csvFile = Path.Combine(dataDir (), "Wholesale.csv")

/// 学習データが無ければテストをスキップする
let requireData () =
    Assert.SkipUnless(File.Exists csvFile, "学習データ Wholesale.csv が配置されていない（gulp data:setup）")

[<Fact>]
let ``標準化したデータのクラスタ数 1 の SSE は件数と列数の積になる`` () =
    requireData ()
    let points = loadSpending csvFile |> toStandardizedPoints SpendingColumns

    let result = kmeansWithRestarts 1 1 0 points

    Assert.Equal(440.0 * 6.0, result.Sse, 6)

[<Fact>]
let ``実行すると SSE・ライブラリとの比較・クラスタごとの件数と平均支出額を表示する`` () =
    requireData ()
    let lines = ResizeArray<string>()

    MachineLearning.Chapter14.Main.run lines.Add

    Assert.Equal<string seq>(
        [
            "データ件数: 440（支出額 6 列）"
            "クラスタ数ごとの SSE（初期中心 10 通りの最小値）:"
            "クラスタ数\t自作\tML.NET（k-means++）"
            "1\t2640.00\t2640.00"
            "2\t1954.18\t1954.80"
            "3\t1610.17\t1627.76"
            "4\t1345.47\t1312.60"
            "5\t1085.27\t1060.26"
            "6\t947.20\t924.69"
            "7\t865.72\t822.82"
            "8\t752.61\t746.01"
            "9\t666.15\t666.67"
            "10\t605.62\t605.19"
            "同じ初期中心で FSharp.Stats と結果が一致した数: 100 / 100"
            ""
            "クラスタ数 5 のクラスタごとの件数と平均支出額:"
            "クラスタ\t件数\tFresh\tMilk\tGrocery\tFrozen\tDetergents_Paper\tDelicassen"
            "4\t265\t8909\t2967\t3804\t2248\t989\t962"
            "0\t96\t5509\t10556\t16478\t1420\t7199\t1659"
            "1\t65\t31117\t4260\t5374\t7225\t849\t2286"
            "2\t10\t15965\t34708\t48537\t3055\t24875\t2943"
            "3\t4\t52022\t31696\t18491\t29826\t2699\t19656"
        ],
        lines
    )
