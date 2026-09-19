module MachineLearning.Tests.Chapter15.PredictionServiceTest

open Xunit
open MachineLearning.Chapter15.Domain
open MachineLearning.Chapter15.PredictionService
open MachineLearning.Tests.Chapter15.Stubs

[<Fact>]
let ``映画の特徴量から興行収入を予測する`` () =
    Assert.Equal(Ok { Sales = 1200.0 }, predictSales stubModelStore movie)

[<Fact>]
let ``モデルが無ければ ModelNotFound の失敗を返す`` () =
    Assert.Equal(Error(ModelNotFound "cinema"), predictSales emptyModelStore movie)

[<Fact>]
let ``エラーの説明にはモデルの名前を含め、ファイルのパスを含めない`` () =
    Assert.Equal("学習済みモデル cinema が見つかりません", describe (ModelNotFound "cinema"))

[<Fact>]
let ``生存と判定されれば生存と予測する`` () =
    Assert.Equal(Ok { Survived = true }, predictSurvival stubModelStore passenger)

[<Fact>]
let ``死亡と判定されれば死亡と予測する`` () =
    let male =
        { passenger with
            Pclass = Third
            Sex = Male
            Age = Some 30.0
            Fare = 8.0
            Embarked = Some Southampton
        }

    Assert.Equal(Ok { Survived = false }, predictSurvival stubModelStore male)

[<Fact>]
let ``モデルを読み込めればそれぞれ true を返す`` () =
    Assert.Equal({ Cinema = true; Survived = true }, health stubModelStore)

[<Fact>]
let ``モデルが無ければそれぞれ false を返す`` () =
    Assert.Equal({ Cinema = false; Survived = false }, health emptyModelStore)
