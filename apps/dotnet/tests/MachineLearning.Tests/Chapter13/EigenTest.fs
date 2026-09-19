module MachineLearning.Tests.Chapter13.EigenTest

open Xunit
open MachineLearning.Chapter13.Eigen

let assertVectorEqual (expected: float[]) (actual: float[]) =
    Assert.Equal(expected.Length, actual.Length)
    Array.iter2 (fun (e: float) (a: float) -> Assert.Equal(e, a, 1e-9)) expected actual

[<Fact>]
let ``対角行列なら対角成分が固有値で、大きい順に並ぶ`` () =
    let diagonal = [| [| 1.0; 0.0; 0.0 |]; [| 0.0; 5.0; 0.0 |]; [| 0.0; 0.0; 3.0 |] |]

    let pairs = symmetricEigen diagonal

    assertVectorEqual [| 5.0; 3.0; 1.0 |] (pairs |> List.map (fun pair -> pair.Value) |> List.toArray)
    assertVectorEqual [| 0.0; 1.0; 0.0 |] pairs[0].Vector

/// 行列とベクトルの積
let multiply (a: float[][]) (v: float[]) : float[] =
    a |> Array.map (fun row -> Array.map2 (*) row v |> Array.sum)

[<Fact>]
let ``対角成分以外が 0 でない行列でも、掛けると固有値倍になるベクトルを求める`` () =
    let symmetric = [| [| 2.0; 1.0 |]; [| 1.0; 2.0 |] |]

    let pairs = symmetricEigen symmetric

    assertVectorEqual [| 3.0; 1.0 |] (pairs |> List.map (fun pair -> pair.Value) |> List.toArray)

    for pair in pairs do
        assertVectorEqual (pair.Vector |> Array.map (fun v -> pair.Value * v)) (multiply symmetric pair.Vector)
