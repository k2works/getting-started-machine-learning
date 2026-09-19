module MachineLearning.Tests.Chapter12.MatrixOperationsTest

open System
open Xunit
open MachineLearning.Chapter07.Matrix
open MachineLearning.Chapter12.MatrixOperations

[<Fact>]
let ``同じ形の行列を要素ごとに足す`` () =
    let a = [| [| 1.0; 2.0 |]; [| 3.0; 4.0 |] |]
    let b = [| [| 10.0; 20.0 |]; [| 30.0; 40.0 |] |]

    Assert.Equal<Matrix>([| [| 11.0; 22.0 |]; [| 33.0; 44.0 |] |], add a b)

[<Fact>]
let ``形が違う行列は足せない`` () =
    let a = [| [| 1.0; 2.0 |] |]
    let b = [| [| 1.0 |]; [| 2.0 |] |]

    let error = Assert.Throws<ArgumentException>(fun () -> add a b |> ignore)

    Assert.Equal("1 行 2 列の行列と 2 行 1 列の行列は足せません", error.Message)

[<Fact>]
let ``数と行列の積は各要素に数を掛ける`` () =
    let a = [| [| 1.0; -2.0 |]; [| 0.5; 4.0 |] |]

    Assert.Equal<Matrix>([| [| 2.0; -4.0 |]; [| 1.0; 8.0 |] |], scale 2.0 a)

[<Fact>]
let ``単位行列は対角成分が 1 でそれ以外が 0 の正方行列`` () =
    Assert.Equal<Matrix>([| [| 1.0; 0.0; 0.0 |]; [| 0.0; 1.0; 0.0 |]; [| 0.0; 0.0; 1.0 |] |], identity 3)
