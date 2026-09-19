module MachineLearning.Tests.Chapter09.DummiesTest

open Xunit
open MachineLearning.Chapter09.Dummies

[<Fact>]
let ``先頭を除いたカテゴリを辞書順に返す`` () =
    Assert.Equal<string list>([ "low"; "very_low" ], dummyCategories [ "low"; "high"; "very_low"; "low" ])

[<Fact>]
let ``カテゴリごとに 0 と 1 の列を作る`` () =
    let encode = encodeDummies "CRIME" [ "low"; "very_low" ]

    Assert.Equal<Map<string, float> list>(
        [
            Map.ofList [ "CRIME_low", 1.0; "CRIME_very_low", 0.0 ]
            Map.ofList [ "CRIME_low", 0.0; "CRIME_very_low", 0.0 ]
            Map.ofList [ "CRIME_low", 0.0; "CRIME_very_low", 1.0 ]
        ],
        [ "low"; "high"; "very_low" ] |> List.map encode
    )

[<Fact>]
let ``カテゴリに無い値はすべての列が 0 になる`` () =
    Assert.Equal<Map<string, float>>(
        Map.ofList [ "CRIME_low", 0.0; "CRIME_very_low", 0.0 ],
        encodeDummies "CRIME" [ "low"; "very_low" ] "unknown"
    )
