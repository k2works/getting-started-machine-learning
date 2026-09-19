module MachineLearning.Tests.DatasetTest

open System.IO
open Xunit
open MachineLearning.Dataset

[<Fact>]
let ``環境変数 ML_DATA_DIR があればそのディレクトリを使う`` () =
    let getenv name =
        if name = "ML_DATA_DIR" then Some "/data/ml" else None

    Assert.Equal("/data/ml", dataDirFrom getenv)

[<Fact>]
let ``環境変数が無ければ apps/data/sukkiri-ml を使う`` () =
    let dir = dataDirFrom (fun _ -> None) |> Path.GetFullPath

    Assert.EndsWith(Path.Combine("apps", "data", "sukkiri-ml"), dir)
