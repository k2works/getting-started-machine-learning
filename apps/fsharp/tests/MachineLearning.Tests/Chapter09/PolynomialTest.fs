module MachineLearning.Tests.Chapter09.PolynomialTest

open Xunit
open MachineLearning.Chapter09.Polynomial

[<Fact>]
let ``同じ列の組も含めて、列の組を重複なく作る`` () =
    Assert.Equal<(string * string) list>([ "a", "a"; "a", "b"; "b", "b" ], pairsWithReplacement [ "a"; "b" ])

[<Fact>]
let ``2 乗の項は ^2、積の項は空白でつないだ名前にする`` () =
    Assert.Equal<string list>([ "RM^2"; "RM LSTAT" ], [ termName "RM" "RM"; termName "RM" "LSTAT" ])

[<Fact>]
let ``元の列と 2 次の項の列を持つ行を作る`` () =
    let rows = [ Map.ofList [ "a", 2.0; "b", 3.0; "c", 9.0 ] ]

    Assert.Equal<Map<string, float> list>(
        [ Map.ofList [ "a", 2.0; "b", 3.0; "a^2", 4.0; "a b", 6.0; "b^2", 9.0 ] ],
        polynomialFeatures [ "a"; "b" ] rows
    )
