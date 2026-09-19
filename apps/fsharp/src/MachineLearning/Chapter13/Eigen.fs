module MachineLearning.Chapter13.Eigen

/// 固有値と、それに対応する長さ 1 の固有ベクトル
type EigenPair = { Value: float; Vector: float[] }

/// 対角成分以外の 2 乗和がこれ以下になったら、対角行列になったとみなす
[<Literal>]
let Tolerance = 1e-24

/// 回転を繰り返す回数の上限（すべての非対角成分を 1 回ずつ回すのを 1 巡とする）
[<Literal>]
let MaxSweeps = 100

let private identity (n: int) : float[][] =
    Array.init n (fun i -> Array.init n (fun j -> if i = j then 1.0 else 0.0))

let private multiply (a: float[][]) (b: float[][]) : float[][] =
    let columns = Array.transpose b

    a
    |> Array.map (fun row -> columns |> Array.map (fun column -> Array.map2 (*) row column |> Array.sum))

/// 対角成分以外の要素の 2 乗和。0 に近いほど対角行列に近い
let private offDiagonal (a: float[][]) : float =
    seq {
        for i in 0 .. a.Length - 1 do
            for j in 0 .. a.Length - 1 do
                if i <> j then
                    yield a[i][j] * a[i][j]
    }
    |> Seq.sum

/// (p, q) 要素と (q, p) 要素を 0 にする、p・q の 2 つの軸の平面での回転行列
let private rotation (a: float[][]) (p: int) (q: int) : float[][] =
    let theta = (a[q][q] - a[p][p]) / (2.0 * a[p][q])

    let t =
        (if theta >= 0.0 then 1.0 else -1.0) / (abs theta + sqrt (theta * theta + 1.0))

    let c = 1.0 / sqrt (t * t + 1.0)
    let s = t * c

    identity a.Length
    |> Array.mapi (fun i row ->
        row
        |> Array.mapi (fun j value ->
            if (i, j) = (p, p) || (i, j) = (q, q) then c
            elif (i, j) = (p, q) then s
            elif (i, j) = (q, p) then -s
            else value))

/// すべての非対角成分について 1 回ずつ回転する（1 巡）。
/// a は Jᵀ a J に、v は v J に置き換わり、v の列には回転を掛け合わせた結果がたまっていく
let private sweep (a: float[][], v: float[][]) : float[][] * float[][] =
    let n = a.Length

    [
        for p in 0 .. n - 2 do
            for q in p + 1 .. n - 1 do
                p, q
    ]
    |> List.fold
        (fun (a: float[][], v) (p, q) ->
            if a[p][q] = 0.0 then
                a, v
            else
                let j = rotation a p q
                multiply (multiply (Array.transpose j) a) j, multiply v j)
        (a, v)

/// 対角行列に近づくか、上限の回数に達するまで 1 巡を繰り返す
[<TailCall>]
let rec private diagonalize (sweepsLeft: int) (a: float[][], v: float[][]) : float[][] * float[][] =
    if sweepsLeft = 0 || offDiagonal a <= Tolerance then
        a, v
    else
        diagonalize (sweepsLeft - 1) (sweep (a, v))

/// 対称行列の固有値と固有ベクトルを、固有値の大きい順に返す（ヤコビ法）。
/// 回転で対角行列に近づけると、対角成分が固有値、回転を掛け合わせた行列の列が固有ベクトルになる
let symmetricEigen (a: float[][]) : EigenPair list =
    let diagonal, vectors = diagonalize MaxSweeps (a, identity a.Length)
    let columns = Array.transpose vectors

    [ 0 .. a.Length - 1 ]
    |> List.map (fun i ->
        {
            Value = diagonal[i][i]
            Vector = columns[i]
        })
    |> List.sortByDescending (fun pair -> pair.Value)
