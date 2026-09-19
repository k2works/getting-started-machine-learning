module MachineLearning.Tests.Chapter11.CrossValidationTest

open Xunit
open MachineLearning.Chapter11.Metrics
open MachineLearning.Chapter11.CrossValidation

let testSizes (folds: Fold list) : int list =
    folds |> List.map (fun fold -> fold.Test.Length)

[<Fact>]
let ``データを k 個のテストデータにほぼ均等に分ける`` () =
    Assert.Equal<int list>([ 4; 3; 3 ], kFold 3 0 10 |> testSizes)

[<Fact>]
let ``件数と分割数が変わってもほぼ均等に分ける`` () =
    Assert.Equal<int list>([ 4; 3 ], kFold 2 0 7 |> testSizes)

[<Fact>]
let ``どの行もちょうど一度だけテストデータになる`` () =
    let folds = kFold 3 0 10

    Assert.Equal<int list>([ 0..9 ], folds |> List.collect (fun fold -> fold.Test) |> List.sort)

[<Fact>]
let ``各分割の訓練データはテストデータ以外のすべての行`` () =
    for fold in kFold 3 0 10 do
        Assert.Empty(Set.intersect (set fold.Train) (set fold.Test))
        Assert.Equal<Set<int>>(set [ 0..9 ], set (fold.Train @ fold.Test))

[<Fact>]
let ``同じシードなら同じ分け方になる`` () =
    Assert.Equal<Fold list>(kFold 3 42 10, kFold 3 42 10)

[<Fact>]
let ``シードが違えば違う分け方になる`` () =
    Assert.NotEqual<Fold list>(kFold 3 0 10, kFold 3 1 10)

/// 訓練データの正解の平均値を常に予測するテスト用のモデル
let meanModel: Model<float> =
    fun _ t ->
        let mean = List.average t
        List.map (fun _ -> mean)

let x =
    [ 10.0; 20.0; 30.0; 40.0 ]
    |> List.map (fun value -> Map.ofList [ "feature", value ])

let t = [ 1.0; 2.0; 3.0; 4.0 ]

let folds =
    [ { Train = [ 0; 1 ]; Test = [ 2; 3 ] }; { Train = [ 2; 3 ]; Test = [ 0; 1 ] } ]

[<Fact>]
let ``分割ごとに訓練データで学習してテストデータを評価する`` () =
    Assert.Equal<float seq>([ 2.0; 2.0 ], crossValidate meanModel meanAbsoluteError folds x t)

[<Fact>]
let ``評価関数を差し替えると別の指標で評価する`` () =
    Assert.Equal<float seq>([ 4.25; 4.25 ], crossValidate meanModel meanSquaredError folds x t)

[<Fact>]
let ``最初の分割のスコアだけを取り出すなら学習は 1 回で済む`` () =
    let mutable trained = 0

    let counting: Model<float> =
        fun x t ->
            trained <- trained + 1
            meanModel x t

    crossValidate counting meanAbsoluteError folds x t |> Seq.head |> ignore

    Assert.Equal(1, trained)

[<Fact>]
let ``シーケンスからスコアを取り出すたびに学習し直す`` () =
    let mutable trained = 0

    let counting: Model<float> =
        fun x t ->
            trained <- trained + 1
            meanModel x t

    let scores = crossValidate counting meanAbsoluteError folds x t

    scores |> Seq.toList |> ignore
    scores |> Seq.toList |> ignore

    Assert.Equal(4, trained)

[<Fact>]
let ``Seq.cache で覚えておけば 2 回目は学習しない`` () =
    let mutable trained = 0

    let counting: Model<float> =
        fun x t ->
            trained <- trained + 1
            meanModel x t

    let scores = crossValidate counting meanAbsoluteError folds x t |> Seq.cache

    scores |> Seq.toList |> ignore
    scores |> Seq.toList |> ignore

    Assert.Equal(2, trained)
