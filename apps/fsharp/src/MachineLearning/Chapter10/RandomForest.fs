module MachineLearning.Chapter10.RandomForest

open System
open MachineLearning.Chapter02.Random
open MachineLearning.Chapter03

/// 木ごとの予測のリストから、サンプルごとに最も多い予測を選ぶ
let majorityVote (votes: 'L list list) : 'L list =
    votes |> List.transpose |> List.map DecisionTree.majority

/// シードを使って、0 以上 size 未満の行番号を size 個、重複を許して選ぶ
let bootstrapSample (seed: int) (size: int) : int list =
    let random = Random seed
    List.init size (fun _ -> random.Next size)

/// シードで並べ替えた先頭 maxFeatures 個の特徴量を、元の列の順で返す
let chooseFeatures (seed: int) (maxFeatures: int) (features: string list) : string list =
    let chosen = features |> shuffle seed |> List.truncate maxFeatures |> Set.ofList
    features |> List.filter chosen.Contains

/// 学習の設定
type Settings =
    {
        NEstimators: int
        MaxFeatures: int
        MaxDepth: int option
        Seed: int
    }

let defaults =
    {
        NEstimators = 10
        MaxFeatures = 2
        MaxDepth = None
        Seed = 0
    }

/// 1 本分の学習結果。使った列と、ブートストラップ標本の行番号と、学習した木
type FittedTree<'L> =
    {
        Columns: string list
        Rows: int list
        Tree: DecisionTree.Tree<'L>
    }

/// 森のシードから、木ごとに「ブートストラップ標本のシード」と「特徴量を選ぶシード」の組を作る
let private treeSeeds (seed: int) (count: int) : (int * int) list =
    let random = Random seed

    List.init count (fun _ ->
        let rowSeed = random.Next()
        let featureSeed = random.Next()
        rowSeed, featureSeed)

/// 行番号と列名で、1 本分の学習データを取り出す。同じ行番号が重複していれば、その回数だけ行が並ぶ
let sampleOf (rows: int list) (columns: string list) (x: Map<string, float> list) (t: 'L list) =
    let xs = List.toArray x
    let ts = List.toArray t

    let sampleX =
        rows
        |> List.map (fun row -> xs[row] |> Map.filter (fun column _ -> List.contains column columns))

    sampleX, rows |> List.map (fun row -> ts[row])

/// ブートストラップ標本と特徴量の部分集合で、第 3 章の決定木を NEstimators 本学習する
let fit (settings: Settings) (x: Map<string, float> list) (t: 'L list) : FittedTree<'L> list =
    let features = x.Head |> Map.keys |> Seq.toList

    treeSeeds settings.Seed settings.NEstimators
    |> List.map (fun (rowSeed, featureSeed) ->
        let rows = bootstrapSample rowSeed x.Length
        let columns = chooseFeatures featureSeed settings.MaxFeatures features
        let sampleX, sampleT = sampleOf rows columns x t

        {
            Columns = columns
            Rows = rows
            Tree = DecisionTree.fit settings.MaxDepth sampleX sampleT
        })

/// 木ごとに予測して多数決する。第 3 章の木は分割に使った列だけを見るので、列を絞らずに渡せる
let predict (forest: FittedTree<'L> list) (x: Map<string, float> list) : 'L list =
    forest
    |> List.map (fun fitted -> DecisionTree.predict fitted.Tree x)
    |> majorityVote
