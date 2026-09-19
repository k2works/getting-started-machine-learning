module MachineLearning.Tests.Chapter07.MatrixTest

open System
open Xunit
open MachineLearning.Chapter07.Matrix

[<Fact>]
let ``行列の積を求める`` () =
    let a = [| [| 1.0; 2.0 |]; [| 3.0; 4.0 |] |]
    let b = [| [| 5.0; 6.0 |]; [| 7.0; 8.0 |] |]

    Assert.Equal<Matrix>([| [| 19.0; 22.0 |]; [| 43.0; 50.0 |] |], multiply a b)

[<Fact>]
let ``行数と列数が違う行列の積を求める`` () =
    let a = [| [| 1.0; 2.0; 3.0 |]; [| 4.0; 5.0; 6.0 |] |]
    let b = [| [| 1.0 |]; [| 0.0 |]; [| 2.0 |] |]

    Assert.Equal<Matrix>([| [| 7.0 |]; [| 16.0 |] |], multiply a b)

[<Fact>]
let ``左の列数と右の行数が違えば積を求められない`` () =
    let a = [| [| 1.0; 2.0 |] |]

    let error = Assert.Throws<ArgumentException>(fun () -> multiply a a |> ignore)

    Assert.Equal("左の行列の列数 2 と右の行列の行数 1 が違います", error.Message)

[<Fact>]
let ``行と列を入れ替える`` () =
    let a = [| [| 1.0; 2.0; 3.0 |]; [| 4.0; 5.0; 6.0 |] |]

    Assert.Equal<Matrix>([| [| 1.0; 4.0 |]; [| 2.0; 5.0 |]; [| 3.0; 6.0 |] |], transpose a)

/// 要素の数が同じで、要素ごとに小数第 9 位まで一致することを確かめる
let assertValues (expected: float seq) (actual: float seq) =
    Assert.Equal(Seq.length expected, Seq.length actual)
    Seq.iter2 (fun (e: float) (a: float) -> Assert.Equal(e, a, 9)) expected actual

[<Fact>]
let ``連立方程式の解を求める`` () =
    let a = [| [| 2.0; 1.0 |]; [| 1.0; 3.0 |] |]

    assertValues [ 0.8; 1.4 ] (solve a [| 3.0; 5.0 |])

[<Fact>]
let ``3 元の連立方程式の解を求める`` () =
    let a = [| [| 4.0; 1.0; 2.0 |]; [| 1.0; 3.0; 0.0 |]; [| 2.0; 0.0; 5.0 |] |]

    let x = [| 1.0; -2.0; 3.0 |]
    let b = a |> Array.map (dot x)

    assertValues x (solve a b)

[<Fact>]
let ``対角成分が 0 でも行を入れ替えて解を求める`` () =
    let a = [| [| 0.0; 1.0 |]; [| 1.0; 0.0 |] |]

    assertValues [ 3.0; 2.0 ] (solve a [| 2.0; 3.0 |])

[<Fact>]
let ``引数の行列とベクトルを書き換えない`` () =
    let a = [| [| 0.0; 1.0 |]; [| 1.0; 0.0 |] |]
    let b = [| 2.0; 3.0 |]

    solve a b |> ignore

    Assert.Equal<Matrix>([| [| 0.0; 1.0 |]; [| 1.0; 0.0 |] |], a)
    Assert.Equal<float[]>([| 2.0; 3.0 |], b)
