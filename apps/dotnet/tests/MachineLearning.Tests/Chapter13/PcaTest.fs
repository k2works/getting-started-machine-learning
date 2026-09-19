module MachineLearning.Tests.Chapter13.PcaTest

open System
open Xunit
open MachineLearning.Chapter13.Pca

/// 要素ごとに許容誤差を付けて行列を比べる
let assertMatrixEqual (expected: float[][]) (actual: float[][]) =
    Assert.Equal(expected.Length, actual.Length)

    Array.iter2
        (fun (expectedRow: float[]) (actualRow: float[]) ->
            Assert.Equal(expectedRow.Length, actualRow.Length)
            Array.iter2 (fun (e: float) (a: float) -> Assert.Equal(e, a, 1e-9)) expectedRow actualRow)
        expected
        actual

[<Fact>]
let ``2 列の分散と共分散を並べた行列を返す`` () =
    let x = [| [| 1.0; 2.0 |]; [| 3.0; 6.0 |]; [| 5.0; 10.0 |] |]

    assertMatrixEqual [| [| 4.0; 8.0 |]; [| 8.0; 16.0 |] |] (covarianceMatrix x)

[<Fact>]
let ``3 列でも各列の分散と 2 列ずつの共分散を並べる`` () =
    let x = [| [| 1.0; 2.0; 0.0 |]; [| 3.0; 6.0; 1.0 |]; [| 5.0; 10.0; 5.0 |] |]

    assertMatrixEqual [| [| 4.0; 8.0; 5.0 |]; [| 8.0; 16.0; 10.0 |]; [| 5.0; 10.0; 7.0 |] |] (covarianceMatrix x)

let assertVectorEqual (expected: float[]) (actual: float[]) =
    assertMatrixEqual [| expected |] [| actual |]

[<Fact>]
let ``完全に相関する 2 列なら第 1 主成分だけで分散をすべて説明する`` () =
    let x = [| [| 1.0; 2.0 |]; [| 3.0; 6.0 |]; [| 5.0; 10.0 |] |]

    let model = fitPca 2 x

    assertVectorEqual [| 1.0 / sqrt 5.0; 2.0 / sqrt 5.0 |] model.Components[0]
    assertVectorEqual [| 1.0; 0.0 |] model.ExplainedVarianceRatio

/// 平均 0・標準偏差 1 の正規分布に従う乱数（ボックス＝ミュラー法）
let gaussian (random: Random) : float =
    sqrt (-2.0 * log (1.0 - random.NextDouble()))
    * cos (2.0 * Math.PI * random.NextDouble())

/// 2 つの隠れた変数を 4 列に混ぜ、小さなノイズを加えた 40 行の架空のデータ
let mixedDataset () : float[][] =
    let random = Random 0
    let mixing = [| [| 2.0; 0.5 |]; [| 0.3; 1.0 |]; [| 1.0; -1.0 |]; [| 0.0; 0.2 |] |]

    Array.init 40 (fun _ ->
        let hidden = [| gaussian random; gaussian random |]

        mixing |> Array.map (fun weights -> dot weights hidden + gaussian random * 0.1))

[<Fact>]
let ``主成分は寄与率の大きい順に指定した数だけ並ぶ`` () =
    let model = fitPca 3 (mixedDataset ())

    let ratios = model.ExplainedVarianceRatio
    Assert.Equal(3, ratios.Length)
    Assert.Equal<float[]>(Array.sortDescending ratios, ratios)

[<Fact>]
let ``主成分の向きは絶対値が最大の要素が正になるようにそろえる`` () =
    let x =
        [|
            [| 0.0; 0.0; 0.0 |]
            [| 0.0; 2.0; 0.0 |]
            [| 3.0; 0.0; 1.0 |]
            [| 0.0; 3.0; 3.0 |]
        |]

    let model = fitPca 3 x

    for pc in model.Components do
        Assert.True(Array.maxBy abs pc > 0.0, $"%A{pc}")

[<Fact>]
let ``絶対値が最大の要素が正になるように主成分の向きをそろえる`` () =
    let components = [| [| 0.6; -0.8 |]; [| -0.8; 0.6 |] |]

    assertMatrixEqual [| [| -0.6; 0.8 |]; [| 0.8; -0.6 |] |] (normalizeSigns components)

/// 行列の積
let multiply (a: float[][]) (b: float[][]) : float[][] =
    let columns = Array.transpose b
    a |> Array.map (fun row -> columns |> Array.map (dot row))

[<Fact>]
let ``主成分は長さ 1 で互いに直交する`` () =
    let model = fitPca 3 (mixedDataset ())

    let gram = multiply model.Components (Array.transpose model.Components)

    assertMatrixEqual (Array.init 3 (fun i -> Array.init 3 (fun j -> if i = j then 1.0 else 0.0))) gram

[<Fact>]
let ``主成分は分散共分散行列に掛けると分散の倍数になる固有ベクトル`` () =
    let x = mixedDataset ()

    let model = fitPca 3 x

    let covariance = covarianceMatrix x

    Array.iter2
        (fun (pc: float[]) variance ->
            assertVectorEqual (pc |> Array.map (fun v -> v * variance)) (covariance |> Array.map (dot pc)))
        model.Components
        model.ExplainedVariance

[<Fact>]
let ``平均を引いてから主成分の向きに射影する`` () =
    let model =
        {
            Mean = [| 1.0; 2.0 |]
            Components = [| [| 0.6; 0.8 |] |]
            ExplainedVariance = [| 1.0 |]
            ExplainedVarianceRatio = [| 1.0 |]
        }

    assertMatrixEqual [| [| 1.4 |]; [| 0.0 |] |] (transform model [| [| 2.0; 3.0 |]; [| 1.0; 2.0 |] |])

[<Fact>]
let ``累積寄与率がしきい値に届くまでの主成分の数を返す`` () =
    Assert.Equal(2, componentsNeeded 0.75 [| 0.5; 0.25; 0.25 |])

[<Fact>]
let ``しきい値を上げると必要な主成分の数が増える`` () =
    Assert.Equal(3, componentsNeeded 0.8 [| 0.5; 0.25; 0.25 |])

[<Fact>]
let ``係数の絶対値が大きい順に列名と係数を返す`` () =
    let pc = [| 0.1; -0.7; 0.5 |]

    Assert.Equal<(string * float) list>([ "DIS", -0.7; "TAX", 0.5 ], topLoadings 2 [ "ZN"; "DIS"; "TAX" ] pc)
