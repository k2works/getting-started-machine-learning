module MachineLearning.Chapter14.Spending

open FSharp.Data
open MachineLearning.Chapter13.BostonStandardized

/// 型プロバイダが列の名前と型を知るための、架空の値のサンプル
[<Literal>]
let WholesaleSample =
    "Channel,Region,Fresh,Milk,Grocery,Frozen,Detergents_Paper,Delicassen\n1,1,1,1,1,1,1,1"

type WholesaleCsv = CsvProvider<WholesaleSample>

/// 支出額の列。Channel と Region は区分の番号で大小に意味が無いので使わない
let SpendingColumns =
    [ "Fresh"; "Milk"; "Grocery"; "Frozen"; "Detergents_Paper"; "Delicassen" ]

/// 顧客ごとの支出額を、列名から値への Map で読み込む
let loadSpending (csvFile: string) : Map<string, float> list =
    WholesaleCsv.Load(csvFile).Rows
    |> Seq.map (fun row ->
        Map.ofList
            [
                "Fresh", float row.Fresh
                "Milk", float row.Milk
                "Grocery", float row.Grocery
                "Frozen", float row.Frozen
                "Detergents_Paper", float row.Detergents_Paper
                "Delicassen", float row.Delicassen
            ])
    |> Seq.toList

/// 列ごとに標準化し、1 行を 1 つの点（列の順に値を並べた配列）にする
let toStandardizedPoints (columns: string list) (rows: Map<string, float> list) : float[][] =
    columns
    |> List.map (fun column -> rows |> List.map (fun row -> row[column]) |> List.toArray |> standardize)
    |> List.toArray
    |> Array.transpose

/// 1 つのクラスタの件数と、列ごとの平均
type ClusterSummary =
    {
        Cluster: int
        Count: int
        Means: Map<string, float>
    }

/// クラスタごとの件数と列ごとの平均を、件数の多い順に並べる
let summarizeClusters (columns: string list) (labels: int[]) (rows: Map<string, float> list) : ClusterSummary list =
    List.zip (List.ofArray labels) rows
    |> List.groupBy fst
    |> List.map (fun (cluster, members) ->
        let memberRows = members |> List.map snd

        {
            Cluster = cluster
            Count = memberRows.Length
            Means =
                columns
                |> List.map (fun column -> column, memberRows |> List.averageBy (fun row -> row[column]))
                |> Map.ofList
        })
    |> List.sortByDescending (fun summary -> summary.Count)
