[<Xunit.Collection("標準出力を書き換えるテスト")>]
module MachineLearning.Tests.Chapter13.StatsPcaTest

open Xunit
open MachineLearning.Chapter13.Pca
open MachineLearning.Chapter13.StatsPca
open MachineLearning.Tests.Chapter13.PcaTest

[<Fact>]
let ``FSharp.Stats の PCA も自作と同じ形のモデルを返す`` () =
    let x = [| [| 1.0; 2.0 |]; [| 3.0; 6.0 |]; [| 5.0; 10.0 |] |]

    let model = fitPcaWithFSharpStats 2 x

    assertVectorEqual [| 3.0; 6.0 |] model.Mean
    assertVectorEqual [| 1.0 / sqrt 5.0; 2.0 / sqrt 5.0 |] model.Components[0]
    assertVectorEqual [| 20.0; 0.0 |] model.ExplainedVariance
    assertVectorEqual [| 1.0; 0.0 |] model.ExplainedVarianceRatio

[<Fact>]
let ``FSharp.Stats の PCA は自作と同じ主成分と寄与率を求める`` () =
    let x = mixedDataset ()

    let mine = fitPca 4 x
    let library = fitPcaWithFSharpStats 4 x

    assertMatrixEqual mine.Components library.Components
    assertVectorEqual mine.ExplainedVarianceRatio library.ExplainedVarianceRatio
