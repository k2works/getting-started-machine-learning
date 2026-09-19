module MachineLearning.Tests.Chapter09.OutliersTest

open Xunit
open MachineLearning.Chapter02.IrisPreprocessing
open MachineLearning.Chapter09.Outliers

[<Fact>]
let ``分位点は並べた値の間を線形補間する`` () =
    let values = [ 4.0; 1.0; 3.0; 2.0 ]

    Assert.Equal<float list>([ 1.0; 1.75; 2.5; 4.0 ], [ 0.0; 0.25; 0.5; 1.0 ] |> List.map (quantile values))

[<Fact>]
let ``四分位範囲の 1.5 倍より外側の値を外れ値とする`` () =
    let values = [ 1.0; 2.0; 3.0; 4.0; 100.0 ]

    Assert.Equal<bool list>([ false; false; false; false; true ], iqrOutliers values)

[<Fact>]
let ``訓練データだけから正解の外れ値の行を除く`` () =
    let split =
        {
            XTrain = [ 1; 2; 3; 4; 5 ]
            XTest = [ 6 ]
            TTrain = [ 1.0; 2.0; 3.0; 4.0; 100.0 ]
            TTest = [ 1000.0 ]
        }

    let removed = removeTargetOutliers split

    Assert.Equal<int list>([ 1; 2; 3; 4 ], removed.XTrain)
    Assert.Equal<float list>([ 1.0; 2.0; 3.0; 4.0 ], removed.TTrain)
    Assert.Equal<float list>([ 1000.0 ], removed.TTest)
