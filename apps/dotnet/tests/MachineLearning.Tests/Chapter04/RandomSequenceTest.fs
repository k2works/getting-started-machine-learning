/// System.Random のシード付きの乱数列を固定する学習用テスト。
/// .NET を上げてこのテストが失敗したら、分割に入る行と記事の数値が変わる。
module MachineLearning.Tests.Chapter04.RandomSequenceTest

open System
open Xunit
open MachineLearning.Chapter02.Random

[<Fact>]
let ``シード 0 の乱数列は .NET 8・9・10 で同じ値になる`` () =
    let random = Random 0

    let values = [ random.Next(); random.Next(); random.Next() ]

    Assert.Equal<int list>([ 1559595546; 1755192844; 1649316166 ], values)

[<Fact>]
let ``シード 0 で 0 から 9 を並べ替えた順は第 2 章で記録した順と同じ`` () =
    Assert.Equal<int list>([ 0; 4; 5; 8; 2; 1; 3; 6; 9; 7 ], shuffle 0 [ 0..9 ])

[<Fact>]
let ``シードを渡さなければ乱数列は Random を作るたびに変わる`` () =
    let draw (random: Random) = List.init 10 (fun _ -> random.Next())

    Assert.NotEqual<int list>(draw (Random()), draw (Random()))
