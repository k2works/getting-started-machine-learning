module MachineLearning.Tests.Chapter14.KMeansTest

open Xunit
open MachineLearning.Chapter14.KMeans

[<Fact>]
let ``各点を最も近い中心のクラスタに割り当てる`` () =
    let points = [| [| 0.0 |]; [| 1.0 |]; [| 9.0 |]; [| 10.0 |] |]
    let centers = [| [| 0.0 |]; [| 10.0 |] |]

    Assert.Equal<int[]>([| 0; 0; 1; 1 |], assignClusters centers points)

[<Fact>]
let ``2 次元の点をユークリッド距離で最も近い中心に割り当てる`` () =
    let points = [| [| 0.0; 0.0 |]; [| 5.0; 4.0 |]; [| 1.0; 0.0 |] |]
    let centers = [| [| 5.0; 5.0 |]; [| 0.0; 0.0 |] |]

    Assert.Equal<int[]>([| 1; 0; 1 |], assignClusters centers points)

[<Fact>]
let ``クラスタごとに割り当てられた点の平均を新しい中心にする`` () =
    let points =
        [| [| 0.0; 0.0 |]; [| 2.0; 0.0 |]; [| 10.0; 10.0 |]; [| 10.0; 12.0 |] |]

    let previous = [| [| 0.0; 0.0 |]; [| 0.0; 0.0 |] |]

    Assert.Equal<Point[]>([| [| 1.0; 0.0 |]; [| 10.0; 11.0 |] |], updateCenters points [| 0; 0; 1; 1 |] previous)

[<Fact>]
let ``点が 1 つも割り当てられなかったクラスタは中心を変えない`` () =
    let points = [| [| 0.0; 0.0 |]; [| 2.0; 4.0 |] |]
    let previous = [| [| 0.0; 0.0 |]; [| 99.0; 99.0 |] |]

    Assert.Equal<Point[]>([| [| 1.0; 2.0 |]; [| 99.0; 99.0 |] |], updateCenters points [| 0; 0 |] previous)

[<Fact>]
let ``各点と所属するクラスタの中心との距離の 2 乗を合計する`` () =
    let points =
        [| [| 0.0; 0.0 |]; [| 2.0; 0.0 |]; [| 10.0; 10.0 |]; [| 10.0; 12.0 |] |]

    let centers = [| [| 1.0; 0.0 |]; [| 10.0; 11.0 |] |]

    Assert.Equal(4.0, sumOfSquaredErrors points [| 0; 0; 1; 1 |] centers)

[<Fact>]
let ``中心から離れた点ほど誤差が大きくなる`` () =
    Assert.Equal(10.0, sumOfSquaredErrors [| [| 0.0 |]; [| 4.0 |] |] [| 0; 0 |] [| [| 1.0 |] |])

let twoGroups: Point[] =
    [| [| 0.0; 0.0 |]; [| 0.0; 1.0 |]; [| 10.0; 10.0 |]; [| 10.0; 11.0 |] |]

[<Fact>]
let ``割り当てが変わらなくなるまで割り当てと中心の更新を繰り返す`` () =
    let result = kmeans [| [| 0.0; 0.0 |]; [| 0.0; 1.0 |] |] twoGroups

    Assert.Equal(
        {
            Labels = [| 0; 0; 1; 1 |]
            Centers = [| [| 0.0; 0.5 |]; [| 10.0; 10.5 |] |]
            Sse = 1.0
        },
        result
    )

/// 要素ごとに許容誤差を付けて点の配列を比べる
let assertPointsEqual (expected: Point[]) (actual: Point[]) =
    Assert.Equal(expected.Length, actual.Length)

    Array.iter2
        (fun (e: Point) (a: Point) -> Array.iter2 (fun (x: float) (y: float) -> Assert.Equal(x, y, 1e-9)) e a)
        expected
        actual

[<Fact>]
let ``最大反復回数に達したら収束していなくても打ち切る`` () =
    let result = kmeansWith 1 [| [| 0.0; 0.0 |]; [| 0.0; 1.0 |] |] twoGroups

    assertPointsEqual [| [| 0.0; 0.0 |]; [| 20.0 / 3.0; 22.0 / 3.0 |] |] result.Centers
    Assert.Equal<int[]>([| 0; 0; 1; 1 |], result.Labels)

let numberedPoints (size: int) : Point[] =
    Array.init size (fun i -> [| float i; float i * 2.0 |])

[<Fact>]
let ``データの中から重複なくクラスタ数だけ点を選ぶ`` () =
    let points = numberedPoints 10

    let centers = chooseInitialCenters 3 0 points

    Assert.Equal(3, centers |> Array.distinct |> Array.length)
    Assert.All(centers, fun center -> Assert.Contains(center, points))

[<Fact>]
let ``同じシードなら同じ点を選ぶ`` () =
    let points = numberedPoints 10

    Assert.Equal<Point[]>(chooseInitialCenters 3 42 points, chooseInitialCenters 3 42 points)

[<Fact>]
let ``シードが違えば違う点を選ぶ`` () =
    let points = numberedPoints 10

    Assert.NotEqual<Point[]>(chooseInitialCenters 3 0 points, chooseInitialCenters 3 1 points)

[<Fact>]
let ``クラスタ数ごとにクラスタリングしたときの SSE を求める`` () =
    Assert.Equal<(int * float) list>([ 1, 201.0; 2, 1.0 ], sseByClusterCount 10 0 [ 1; 2 ] twoGroups)

let threePairs: Point[] =
    [| 0.0; 1.0; 10.0; 11.0; 20.0; 21.0 |] |> Array.map Array.singleton

[<Fact>]
let ``初期中心によっては局所解に陥る`` () =
    let stuck = kmeans [| [| 0.0 |]; [| 1.0 |]; [| 10.0 |] |] threePairs

    Assert.Equal(101.0, stuck.Sse)

[<Fact>]
let ``複数の初期中心の候補のうち SSE が最小の結果を返す`` () =
    let candidates =
        [
            [| [| 0.0 |]; [| 1.0 |]; [| 10.0 |] |]
            [| [| 0.0 |]; [| 10.0 |]; [| 20.0 |] |]
        ]

    let result = bestKMeans candidates threePairs

    Assert.Equal(1.5, result.Sse)
    Assert.Equal<Point[]>([| [| 0.5 |]; [| 10.5 |]; [| 20.5 |] |], result.Centers)

[<Fact>]
let ``初期中心を変えて繰り返し最小の SSE を使う`` () =
    Assert.Equal<(int * float) list>([ 3, 1.5 ], sseByClusterCount 10 0 [ 3 ] threePairs)
