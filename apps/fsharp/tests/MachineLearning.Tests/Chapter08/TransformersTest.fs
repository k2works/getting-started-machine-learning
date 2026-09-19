module MachineLearning.Tests.Chapter08.TransformersTest

open Xunit
open MachineLearning.Chapter08.Transformers

/// (グループ, 年齢) の組のリストを、学習した中央値で補完した年齢のリストにする
let imputeAll (imputer: GroupMedians<'G>) (rows: ('G * float option) list) : float list =
    rows |> List.map (fun (group, age) -> imputeGroupMedian imputer group age)

[<Fact>]
let ``学習用テスト: 組を Map のキーにすると、別に作った同じ値の組で引ける`` () =
    let medians = Map.ofList [ (1, "female"), 45.0 ]

    Assert.Equal(Some 45.0, Map.tryFind (1, "female") medians)

[<Fact>]
let ``同じグループの中央値で欠損値を補完する`` () =
    let rows =
        [
            (1, "female"), Some 20.0
            (1, "female"), Some 30.0
            (1, "female"), Some 70.0
            (1, "female"), None
        ]

    let imputer = fitGroupMedians fst snd rows

    Assert.Equal<float list>([ 20.0; 30.0; 70.0; 30.0 ], imputeAll imputer rows)

[<Fact>]
let ``グループごとに異なる中央値で補完する`` () =
    let rows =
        [
            (1, "female"), Some 40.0
            (1, "female"), Some 50.0
            (1, "female"), None
            (3, "male"), Some 10.0
            (3, "male"), Some 20.0
            (3, "male"), None
        ]

    let imputer = fitGroupMedians fst snd rows

    Assert.Equal<float list>([ 40.0; 50.0; 45.0; 10.0; 20.0; 15.0 ], imputeAll imputer rows)

[<Fact>]
let ``訓練データで求めた中央値を別のデータの補完に使う`` () =
    let train = [ (2, "male"), Some 30.0; (2, "male"), Some 34.0 ]

    let imputer = fitGroupMedians fst snd train

    Assert.Equal(32.0, imputeGroupMedian imputer (2, "male") None)

[<Fact>]
let ``訓練データに無いグループは全体の中央値で補完する`` () =
    let train =
        [ (1, "female"), Some 30.0; (1, "female"), Some 40.0; (3, "male"), Some 20.0 ]

    let imputer = fitGroupMedians fst snd train

    Assert.Equal(30.0, imputeGroupMedian imputer (2, "female") None)

[<Fact>]
let ``値がすべて欠けたグループは全体の中央値で補完する`` () =
    let rows =
        [ (1, "female"), Some 30.0; (1, "female"), Some 40.0; (2, "female"), None ]

    let imputer = fitGroupMedians fst snd rows

    Assert.Equal(35.0, imputeGroupMedian imputer (2, "female") None)

[<Fact>]
let ``最も多い値を求め、欠損値をその値で補完する`` () =
    let train = [ Some "S"; Some "C"; Some "S"; None ]

    let embarked = mostFrequent train

    Assert.Equal<string list>([ "S"; "Q" ], [ None; Some "Q" ] |> List.map (Option.defaultValue embarked))

[<Fact>]
let ``最も多い値が同数なら先に現れた値を選ぶ`` () =
    Assert.Equal("C", mostFrequent [ Some "C"; Some "S"; None; Some "S"; Some "C" ])

[<Fact>]
let ``2 値のカテゴリを、最初のカテゴリを除いた 0 と 1 の列にする`` () =
    let rows =
        [
            Map.ofList [ "Sex", "female" ]
            Map.ofList [ "Sex", "male" ]
            Map.ofList [ "Sex", "male" ]
        ]

    let encoder = fitDummies rows

    Assert.Equal<Map<string, float> list>(
        [
            Map.ofList [ "Sex_male", 0.0 ]
            Map.ofList [ "Sex_male", 1.0 ]
            Map.ofList [ "Sex_male", 1.0 ]
        ],
        rows |> List.map (encodeDummies encoder)
    )

[<Fact>]
let ``別のデータにも訓練データと同じ列を作る`` () =
    let train =
        [ "C"; "Q"; "S" ] |> List.map (fun port -> Map.ofList [ "Embarked", port ])

    let encoder = fitDummies train

    Assert.Equal<Map<string, float>>(
        Map.ofList [ "Embarked_Q", 0.0; "Embarked_S", 1.0 ],
        encodeDummies encoder (Map.ofList [ "Embarked", "S" ])
    )
