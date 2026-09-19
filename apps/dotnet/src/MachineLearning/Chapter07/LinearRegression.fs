module MachineLearning.Chapter07.LinearRegression

open MachineLearning.Chapter07.Matrix

/// 線形回帰の学習結果。切片と、特徴量の列名から係数への Map
type LinearModel =
    {
        Intercept: float
        Coefficients: Map<string, float>
    }

/// 行ごとの Map から、features の列の順に値を並べた行列を作る
let toMatrix (features: string list) (rows: Map<string, float> list) : Matrix =
    rows
    |> List.map (fun row -> features |> List.map (fun feature -> row[feature]) |> List.toArray)
    |> List.toArray

/// 正規方程式 (Xᵀ X) w = Xᵀ t を解いて、切片と係数を求める
let fitLinearRegression (x: Map<string, float> list) (t: float list) : LinearModel =
    let features = x.Head |> Map.keys |> Seq.toList
    let design = toMatrix features x |> Array.map (Array.append [| 1.0 |])
    let designT = transpose design

    let weights =
        solve (multiply designT design) (designT |> Array.map (dot (List.toArray t)))

    {
        Intercept = weights[0]
        Coefficients = List.zip features (List.ofArray weights[1..]) |> Map.ofList
    }

/// 係数を持つ列だけを使い、切片 + 係数 × 特徴量の和を行ごとに求める
let predictLinearRegression (model: LinearModel) (x: Map<string, float> list) : float list =
    let features = model.Coefficients |> Map.keys |> Seq.toList
    let weights = model.Coefficients |> Map.values |> Seq.toArray

    toMatrix features x
    |> Array.map (fun row -> model.Intercept + dot row weights)
    |> Array.toList
