/// 第 7 章の Matrix（float[][]）に、リッジ回帰に必要な演算を足す。第 7 章のモジュールは書き換えない
module MachineLearning.Chapter12.MatrixOperations

open System
open MachineLearning.Chapter07.Matrix

/// 同じ形の行列を要素ごとに足す
let add (a: Matrix) (b: Matrix) : Matrix =
    if a.Length <> b.Length || a[0].Length <> b[0].Length then
        raise (ArgumentException $"{a.Length} 行 {a[0].Length} 列の行列と {b.Length} 行 {b[0].Length} 列の行列は足せません")

    Array.map2 (Array.map2 (+)) a b

/// 行列の各要素に数を掛ける
let scale (k: float) (a: Matrix) : Matrix = a |> Array.map (Array.map ((*) k))

/// size 行 size 列の単位行列
let identity (size: int) : Matrix =
    Array.init size (fun i -> Array.init size (fun j -> if i = j then 1.0 else 0.0))
