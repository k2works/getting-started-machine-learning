module MachineLearning.Chapter13.BostonStandardized

open FSharp.Data
open MachineLearning.Chapter02.IrisPreprocessing

/// 型プロバイダが列の名前と型を知るための、架空の値のサンプル
[<Literal>]
let BostonSample =
    "CRIME,ZN,INDUS,CHAS,NOX,RM,AGE,DIS,RAD,TAX,PTRATIO,B,LSTAT,PRICE\n"
    + "high,0.1,0.1,0.1,,0.1,0.1,0.1,,0.1,0.1,0.1,0.1,0.1"

/// CRIME は文字列、ほかの列は空欄がありうる数値として読む
type BostonCsv =
    CsvProvider<
        BostonSample,
        Schema="string,float option,float option,float option,float option,float option,float option,float option,float option,float option,float option,float option,float option,float option"
     >

let NumericColumns =
    [
        "ZN"
        "INDUS"
        "CHAS"
        "NOX"
        "RM"
        "AGE"
        "DIS"
        "RAD"
        "TAX"
        "PTRATIO"
        "B"
        "LSTAT"
        "PRICE"
    ]

/// 数値の特徴量（列名から値への Map、空欄は None）とカテゴリの列 CRIME
type BostonRow =
    {
        Features: Map<string, float option>
        Crime: string
    }

/// 列名と、1 行を数値の配列で表したデータ
type NumericTable = { Columns: string list; X: float[][] }

/// 平均 0・標準偏差 1 にそろえる（標準偏差は件数 n で割る）
let standardize (values: float[]) : float[] =
    let mean = Array.average values
    let std = sqrt (values |> Array.averageBy (fun v -> (v - mean) ** 2.0))
    values |> Array.map (fun v -> (v - mean) / std)

/// 欠損値を列の平均値で補完し、CRIME をダミー変数の列に置き換えてから、すべての列を標準化する
let standardizeBoston (columns: string list) (rows: BostonRow list) : NumericTable =
    let features = rows |> List.map (fun row -> row.Features)
    let filled = fillMissing (columnMeans features) features
    let crime = rows |> List.map (fun row -> row.Crime)
    // 名前の順で最初のカテゴリ（high）は、ほかのダミー変数がすべて 0 であることで表せるので除く
    let categories = crime |> List.distinct |> List.sort |> List.tail

    let numeric =
        columns |> List.map (fun column -> filled |> List.map (fun row -> row[column]))

    let dummies =
        categories
        |> List.map (fun category -> crime |> List.map (fun value -> if value = category then 1.0 else 0.0))

    {
        Columns = columns @ categories
        X =
            numeric @ dummies
            |> List.map (List.toArray >> standardize)
            |> List.toArray
            |> Array.transpose
    }

let loadBoston (csvFile: string) : BostonRow list =
    BostonCsv.Load(csvFile).Rows
    |> Seq.map (fun row ->
        {
            Features =
                Map.ofList
                    [
                        "ZN", row.ZN
                        "INDUS", row.INDUS
                        "CHAS", row.CHAS
                        "NOX", row.NOX
                        "RM", row.RM
                        "AGE", row.AGE
                        "DIS", row.DIS
                        "RAD", row.RAD
                        "TAX", row.TAX
                        "PTRATIO", row.PTRATIO
                        "B", row.B
                        "LSTAT", row.LSTAT
                        "PRICE", row.PRICE
                    ]
            Crime = row.CRIME
        })
    |> Seq.toList

let loadStandardizedBoston (csvFile: string) : NumericTable =
    loadBoston csvFile |> standardizeBoston NumericColumns
