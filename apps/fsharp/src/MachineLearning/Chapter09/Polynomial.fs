module MachineLearning.Chapter09.Polynomial

/// 同じ要素どうしの組も含めて、要素の組を重複なく作る（[a; b] なら (a, a)・(a, b)・(b, b)）
let pairsWithReplacement (items: 'T list) : ('T * 'T) list =
    items
    |> List.mapi (fun i left -> items |> List.skip i |> List.map (fun right -> left, right))
    |> List.concat

/// 2 次の項の名前。同じ列なら「列^2」、違う列なら「列 列」
let termName (left: string) (right: string) : string =
    if left = right then $"{left}^2" else $"{left} {right}"

/// 指定した列と、その列の 2 次の項（2 乗と積）だけを持つ行にする
let polynomialFeatures (columns: string list) (rows: Map<string, float> list) : Map<string, float> list =
    let pairs = pairsWithReplacement columns

    rows
    |> List.map (fun row ->
        (columns |> List.map (fun column -> column, row[column]))
        @ (pairs
           |> List.map (fun (left, right) -> termName left right, row[left] * row[right]))
        |> Map.ofList)
