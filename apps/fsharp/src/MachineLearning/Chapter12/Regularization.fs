module MachineLearning.Chapter12.Regularization

open MachineLearning.Chapter07.Matrix
open MachineLearning.Chapter07.RegressionMetrics
open MachineLearning.Chapter12.MatrixOperations

/// 正則化した線形回帰の学習結果。係数は特徴量の列の順に並ぶ
type RegularizedModel =
    {
        Coefficients: float list
        Intercept: float
    }

/// 中心化した X と t で (Xᵀ X + alpha I) w = Xᵀ t を解く。切片には罰則をかけない
let fitRidge (alpha: float) (x: Matrix) (t: float list) : RegularizedModel =
    let xMeans = transpose x |> Array.map Array.average
    let tMean = List.average t
    let xc = x |> Array.map (fun row -> Array.map2 (-) row xMeans)
    let tc = t |> List.map (fun value -> value - tMean) |> List.toArray
    let xcT = transpose xc

    let coefficients =
        solve (add (multiply xcT xc) (scale alpha (identity xMeans.Length))) (xcT |> Array.map (dot tc))

    {
        Coefficients = List.ofArray coefficients
        Intercept = tMean - dot xMeans coefficients
    }

/// 座標降下法の繰り返しの回数（全係数を 1 回ずつ更新するのを 1 回と数える）
[<Literal>]
let LassoIterations = 1000

/// 軟閾値関数。z の絶対値を gamma だけ 0 に近づけ、0 を越えるならちょうど 0 にする
let private softThreshold (gamma: float) (z: float) : float =
    if z > gamma then z - gamma
    elif z < -gamma then z + gamma
    else 0.0

/// (1/2n) × 誤差の二乗和 + alpha × 係数の絶対値の和 を、座標降下法で最小化する（ラッソ回帰）
let fitLasso (alpha: float) (x: Matrix) (t: float list) : RegularizedModel =
    let n = float x.Length
    let xMeans = transpose x |> Array.map Array.average
    let tMean = List.average t
    let columns = x |> Array.map (fun row -> Array.map2 (-) row xMeans) |> transpose
    let squaredNorms = columns |> Array.map (fun column -> dot column column / n)
    let weights = Array.create columns.Length 0.0
    // 残差（中心化した t - 現在の予測）。係数を 1 つ変えるたびに、その分だけ更新する
    let residual = t |> List.map (fun value -> value - tMean) |> List.toArray

    for _ in 1..LassoIterations do
        for j in 0 .. columns.Length - 1 do
            let column = columns[j]
            let rho = dot column residual / n + weights[j] * squaredNorms[j]
            let updated = softThreshold alpha rho / squaredNorms[j]

            for i in 0 .. residual.Length - 1 do
                residual[i] <- residual[i] + column[i] * (weights[j] - updated)

            weights[j] <- updated

    {
        Coefficients = List.ofArray weights
        Intercept = tMean - dot xMeans weights
    }

/// 特徴量と係数の内積 + 切片を行ごとに求める
let predictRegularized (model: RegularizedModel) (x: Matrix) : float list =
    let coefficients = List.toArray model.Coefficients

    x
    |> Array.map (fun row -> model.Intercept + dot row coefficients)
    |> Array.toList

/// 正則化の強さ 1 つ分の実験結果
type Experiment =
    {
        Alpha: float
        TrainScore: float
        ValidationScore: float
        CoefficientAbsSum: float
    }

/// alpha ごとに訓練データで学習し、訓練データと検証データの決定係数、係数の絶対値の合計を記録する
let runRidgeExperiments
    (alphas: float list)
    (xTrain: Matrix)
    (tTrain: float list)
    (xValid: Matrix)
    (tValid: float list)
    : Experiment list =
    alphas
    |> List.map (fun alpha ->
        let model = fitRidge alpha xTrain tTrain

        {
            Alpha = alpha
            TrainScore = r2Score tTrain (predictRegularized model xTrain)
            ValidationScore = r2Score tValid (predictRegularized model xValid)
            CoefficientAbsSum = List.sumBy abs model.Coefficients
        })

/// 検証データの決定係数が最も高い実験。空のリストには ArgumentException を投げる
let bestExperiment (experiments: Experiment list) : Experiment =
    experiments |> List.maxBy (fun e -> e.ValidationScore)

/// 係数がちょうど 0 の特徴量の名前
let zeroCoefficientNames (coefficients: float list) (featureNames: string list) : string list =
    List.zip featureNames coefficients
    |> List.filter (fun (_, coefficient) -> coefficient = 0.0)
    |> List.map fst
