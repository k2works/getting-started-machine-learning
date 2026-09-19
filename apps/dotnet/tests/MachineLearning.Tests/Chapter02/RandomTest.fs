module MachineLearning.Tests.Chapter02.RandomTest

open Xunit
open MachineLearning.Chapter02.Random

let items = [ 0..9 ]

[<Fact>]
let ``要素を失わずに並べ替えたリストを返す`` () =
    let shuffled = shuffle 0 items

    Assert.Equal<int list>(items, List.sort shuffled)
    Assert.NotEqual<int list>(items, shuffled)

[<Fact>]
let ``同じシードなら同じ順に並べ替える`` () =
    Assert.Equal<int list>(shuffle 42 items, shuffle 42 items)

[<Fact>]
let ``シードが違えば違う順に並べ替える`` () =
    Assert.NotEqual<int list>(shuffle 0 items, shuffle 1 items)

[<Fact>]
let ``空のリストと 1 要素のリストはそのまま返す`` () =
    Assert.Equal<int list>([], shuffle 0 ([]: int list))
    Assert.Equal<int list>([ 7 ], shuffle 0 [ 7 ])
