module MachineLearning.Chapter10.FeatureImportance

open MachineLearning.Chapter03.DecisionTree
open MachineLearning.Chapter10.RandomForest

/// 学習に使ったデータをもう一度木に流して、節ごとに「分割に使った特徴量と、件数で重み付けした不純度の減少量」を集める
let rec private impurityDecreases (tree: Tree<'L>) (x: Map<string, float> list) (t: 'L list) : (string * float) list =
    match tree with
    | Leaf _ -> []
    | Node(split, left, right) ->
        let goesLeft (row: Map<string, float>, _) = row[split.Feature] <= split.Threshold
        let leftPart, rightPart = List.zip x t |> List.partition goesLeft

        let decreasesOf subtree part =
            impurityDecreases subtree (List.map fst part) (List.map snd part)

        (split.Feature, float t.Length * (gini t - split.Impurity))
        :: decreasesOf left leftPart
        @ decreasesOf right rightPart

/// 合計が 1 になるように割合にする。合計が 0 ならそのまま返す
let private normalize (totals: Map<string, float>) : Map<string, float> =
    let total = totals |> Map.values |> Seq.sum

    if total = 0.0 then
        totals
    else
        totals |> Map.map (fun _ value -> value / total)

/// 決定木 1 本の特徴量の重要度
let treeImportances (tree: Tree<'L>) (x: Map<string, float> list) (t: 'L list) : Map<string, float> =
    let decreases = impurityDecreases tree x t

    x.Head
    |> Map.map (fun feature _ ->
        decreases
        |> List.filter (fun (splitFeature, _) -> splitFeature = feature)
        |> List.sumBy snd)
    |> normalize

/// ランダムフォレストの特徴量の重要度。木ごとの重要度（学習に使ったブートストラップ標本で計算）を平均し、割合にする
let forestImportances (forest: FittedTree<'L> list) (x: Map<string, float> list) (t: 'L list) : Map<string, float> =
    let perTree =
        forest
        |> List.map (fun fitted ->
            let sampleX, sampleT = sampleOf fitted.Rows fitted.Columns x t
            treeImportances fitted.Tree sampleX sampleT)

    x.Head
    |> Map.map (fun feature _ -> perTree |> List.averageBy (Map.tryFind feature >> Option.defaultValue 0.0))
    |> normalize
