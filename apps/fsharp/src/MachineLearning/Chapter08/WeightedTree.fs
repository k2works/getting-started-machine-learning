module MachineLearning.Chapter08.WeightedTree

open MachineLearning.Chapter03.DecisionTree

/// ラベルごとの重みの合計。ラベルが最初に現れた順に並ぶ
let private sumWeightsByLabel (labels: 'L list) (weights: float list) : ('L * float) list =
    List.zip labels weights
    |> List.groupBy fst
    |> List.map (fun (label, pairs) -> label, List.sumBy snd pairs)

/// 重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める
let weightedGini (labels: 'L list) (weights: float list) : float =
    let total = List.sum weights

    1.0
    - (sumWeightsByLabel labels weights
       |> List.sumBy (fun (_, weight) -> (weight / total) ** 2.0))

/// クラスの重みの付け方
type ClassWeight =
    /// すべての行の重みを 1 にする
    | Unweighted
    /// 1 件の重みを「件数 / (クラスの数 × そのクラスの件数)」にして、クラスごとの重みの合計をそろえる
    | Balanced

/// 決定木の学習の設定
type TreeOptions =
    {
        /// 木の深さの上限。None なら制限しない
        MaxDepth: int option
        ClassWeight: ClassWeight
    }

/// 正解ラベルから、1 件ごとの重みを求める
let classWeights (classWeight: ClassWeight) (t: 'L list) : float list =
    match classWeight with
    | Unweighted -> t |> List.map (fun _ -> 1.0)
    | Balanced ->
        let counts = t |> List.countBy id |> Map.ofList

        t
        |> List.map (fun label -> float t.Length / float (counts.Count * counts[label]))

/// 1 つの特徴量について、隣り合う値の中点を境界の候補にした分け方。不純度は重みの合計で重み付けする
let private splitsOf (feature: string) (x: Map<string, float> list) (t: 'L list) (weights: float list) : Split list =
    let rows =
        List.zip3 (x |> List.map (fun row -> row[feature])) t weights
        |> List.sortBy (fun (value, _, _) -> value)

    let valueAt i =
        let value, _, _ = rows[i]
        value

    let total = List.sum weights

    [ 1 .. rows.Length - 1 ]
    |> List.filter (fun i -> valueAt (i - 1) <> valueAt i)
    |> List.map (fun i ->
        let left, right = List.splitAt i rows

        let impurityOf (part: (float * 'L * float) list) =
            let labels = part |> List.map (fun (_, label, _) -> label)
            let partWeights = part |> List.map (fun (_, _, weight) -> weight)
            List.sum partWeights * weightedGini labels partWeights

        {
            Feature = feature
            Threshold = (valueAt (i - 1) + valueAt i) / 2.0
            Impurity = (impurityOf left + impurityOf right) / total
        })

/// 重み付きの不純度が最も小さくなる分け方。ラベルが 1 種類か、分けられる値が無ければ None
let bestWeightedSplit (x: Map<string, float> list) (t: 'L list) (weights: float list) : Split option =
    match x with
    | [] -> None
    | _ when weightedGini t weights = 0.0 -> None
    | first :: _ ->
        first
        |> Map.keys
        |> Seq.toList
        |> List.collect (fun feature -> splitsOf feature x t weights)
        |> function
            | [] -> None
            | splits -> Some(List.minBy (fun split -> split.Impurity) splits)

/// 重みの合計が最も大きいラベル。同数なら先に現れたラベルを選ぶ
let weightedMajority (labels: 'L list) (weights: float list) : 'L =
    sumWeightsByLabel labels weights |> List.maxBy snd |> fst

/// 第 3 章の fit と同じ手順で、1 件ごとの重みを通して木を作る。木の型は第 3 章の Tree<'L> をそのまま使う
// 左右の部分木を作ってから Node にまとめるので末尾再帰ではない。再帰の深さは木の深さまで
// fsharplint:disable-next-line EnsureTailCallDiagnosticsInRecursiveFunctions
let rec fitWeighted (maxDepth: int option) (x: Map<string, float> list) (t: 'L list) (weights: float list) : Tree<'L> =
    let split =
        if maxDepth = Some 0 then
            None
        else
            bestWeightedSplit x t weights

    match split with
    | None -> Leaf(weightedMajority t weights)
    | Some split ->
        let goesLeft (row: Map<string, float>, _, _) = row[split.Feature] <= split.Threshold
        let left, right = List.zip3 x t weights |> List.partition goesLeft
        let childDepth = maxDepth |> Option.map (fun depth -> depth - 1)

        let fitPart part =
            let partX, partT, partWeights = List.unzip3 part
            fitWeighted childDepth partX partT partWeights

        Node(split, fitPart left, fitPart right)
