module MachineLearning.Chapter03.DecisionTree

/// ジニ不純度。ラベルが 1 種類なら 0 で、ばらつくほど大きくなる
let gini (labels: 'L list) : float =
    let total = float labels.Length

    1.0
    - (labels
       |> List.countBy id
       |> List.sumBy (fun (_, count) -> (float count / total) ** 2.0))

/// 「特徴量 Feature の値が Threshold 以下なら左、それより大きければ右」という分け方
type Split =
    {
        Feature: string
        Threshold: float
        /// 分けた後の左右の不純度を件数で重み付けした平均
        Impurity: float
    }

/// 1 つの特徴量について、隣り合う値の中点を境界の候補にして、不純度が最も小さい分け方を返す
let private splitsOf (feature: string) (x: Map<string, float> list) (t: 'L list) : Split list =
    let pairs = List.zip (x |> List.map (fun row -> row[feature])) t |> List.sortBy fst
    let total = float pairs.Length

    [ 1 .. pairs.Length - 1 ]
    |> List.filter (fun i -> fst pairs[i - 1] <> fst pairs[i])
    |> List.map (fun i ->
        let left, right = List.splitAt i pairs

        let impurityOf (part: (float * 'L) list) =
            float part.Length * gini (List.map snd part)

        {
            Feature = feature
            Threshold = (fst pairs[i - 1] + fst pairs[i]) / 2.0
            Impurity = (impurityOf left + impurityOf right) / total
        })

/// 不純度が最も小さくなる分け方。ラベルが 1 種類か、分けられる値が無ければ None
let bestSplit (x: Map<string, float> list) (t: 'L list) : Split option =
    match x with
    | [] -> None
    | _ when gini t = 0.0 -> None
    | first :: _ ->
        first
        |> Map.keys
        |> Seq.toList
        |> List.collect (fun feature -> splitsOf feature x t)
        |> function
            | [] -> None
            | splits -> Some(List.minBy (fun split -> split.Impurity) splits)

/// 決定木。葉は予測するラベル、節は分け方と左右の部分木を持つ
type Tree<'L> =
    | Leaf of 'L
    | Node of Split * Tree<'L> * Tree<'L>

/// 最も多いラベル。同数なら先に現れたラベルを選ぶ
let majority (labels: 'L list) : 'L =
    labels |> List.countBy id |> List.maxBy snd |> fst

/// 深さの上限（None なら制限なし）まで、分け方を選んで再帰的に木を作る
// 左右の部分木を作ってから Node にまとめるので末尾再帰ではない。再帰の深さは木の深さまで
// fsharplint:disable-next-line EnsureTailCallDiagnosticsInRecursiveFunctions
let rec fit (maxDepth: int option) (x: Map<string, float> list) (t: 'L list) : Tree<'L> =
    let split = if maxDepth = Some 0 then None else bestSplit x t

    match split with
    | None -> Leaf(majority t)
    | Some split ->
        let goesLeft (row: Map<string, float>, _) = row[split.Feature] <= split.Threshold
        let left, right = List.zip x t |> List.partition goesLeft
        let childDepth = maxDepth |> Option.map (fun depth -> depth - 1)

        let fitPart part =
            fit childDepth (List.map fst part) (List.map snd part)

        Node(split, fitPart left, fitPart right)

[<TailCall>]
let rec predictOne (tree: Tree<'L>) (row: Map<string, float>) : 'L =
    match tree with
    | Leaf label -> label
    | Node(split, left, right) ->
        if row[split.Feature] <= split.Threshold then
            predictOne left row
        else
            predictOne right row

let predict (tree: Tree<'L>) (rows: Map<string, float> list) : 'L list = rows |> List.map (predictOne tree)

/// 木を、条件ごとに字下げした行のリストにする
// 左右の部分木の行を連結するので末尾再帰ではない。再帰の深さは木の深さまで
// fsharplint:disable-next-line EnsureTailCallDiagnosticsInRecursiveFunctions
let rec formatTree (tree: Tree<'L>) : string list =
    let indent lines =
        lines |> List.map (fun line -> "  " + line)

    match tree with
    | Leaf label -> [ string label ]
    | Node(split, left, right) ->
        [ $"{split.Feature} <= {split.Threshold:F4}" ]
        @ indent (formatTree left)
        @ [ $"{split.Feature} > {split.Threshold:F4}" ]
        @ indent (formatTree right)
