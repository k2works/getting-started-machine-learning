module MachineLearning.Chapter07.Matrix

open System

/// 行列。行の配列の配列で表す（型の別名なので、float[][] とそのまま入れ替えられる）
type Matrix = float[][]

/// 2 つのベクトルの対応する要素を掛けて足す（内積）
let dot (u: float[]) (v: float[]) : float = Array.map2 (*) u v |> Array.sum

/// 行と列を入れ替える
let transpose (a: Matrix) : Matrix = Array.transpose a

let multiply (a: Matrix) (b: Matrix) : Matrix =
    if a[0].Length <> b.Length then
        raise (ArgumentException $"左の行列の列数 {a[0].Length} と右の行列の行数 {b.Length} が違います")

    let columns = transpose b
    a |> Array.map (fun row -> columns |> Array.map (dot row))

/// 連立方程式 a x = b の解 x を求める
let solve (a: Matrix) (b: float[]) : float[] =
    let n = a.Length
    // 右辺 b を右に並べた拡大係数行列。引数の a と b を書き換えないように、新しい配列を作る
    let augmented = Array.init n (fun i -> Array.append a[i] [| b[i] |])

    // 前進消去: 対角成分より下を 0 にする
    for pivot in 0 .. n - 1 do
        // 部分ピボット選択: この列で絶対値が最も大きい行を対角の位置に入れ替える
        let largest = [ pivot .. n - 1 ] |> List.maxBy (fun i -> abs (augmented[i][pivot]))
        let row = augmented[pivot]
        augmented[pivot] <- augmented[largest]
        augmented[largest] <- row

        for i in pivot + 1 .. n - 1 do
            let factor = augmented[i][pivot] / augmented[pivot][pivot]

            for j in pivot..n do
                augmented[i][j] <- augmented[i][j] - factor * augmented[pivot][j]

    // 後退代入: 下の行から解を 1 つずつ決める
    let x = Array.create n 0.0

    for i in n - 1 .. -1 .. 0 do
        let known = List.sumBy (fun j -> augmented[i][j] * x[j]) [ i + 1 .. n - 1 ]
        x[i] <- (augmented[i][n] - known) / augmented[i][i]

    x
