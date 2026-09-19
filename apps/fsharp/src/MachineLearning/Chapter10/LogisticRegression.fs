module MachineLearning.Chapter10.LogisticRegression

/// スコアを確率に変換する。最大値を引いてから exp を計算して、大きな値でもあふれないようにする
let softmax (z: float[]) : float[] =
    let max = Array.max z
    let exps = z |> Array.map (fun value -> exp (value - max))
    let total = Array.sum exps
    exps |> Array.map (fun value -> value / total)

/// 確率が 0 のときに log 0 が負の無限大にならないように足す小さな値
[<Literal>]
let private Epsilon = 1e-12

/// 交差エントロピー。正解の品種の確率の対数の平均にマイナスを付けたもの
let crossEntropy (probabilities: float[][]) (targets: int[]) : float =
    -(Array.map2 (fun (p: float[]) target -> log (p[target] + Epsilon)) probabilities targets
      |> Array.average)

/// 学習の設定
type Settings = { LearningRate: float; Epochs: int }

let defaults = { LearningRate = 1.0; Epochs = 5000 }

/// 学習したロジスティック回帰のモデル
type LogisticModel<'L> =
    {
        /// 特徴量の列名（重みの行の順）
        Features: string list
        /// 品種（重みの列の順）
        Classes: 'L list
        /// Weights[特徴量][品種]
        Weights: float[][]
        /// Bias[品種]
        Bias: float[]
        /// 繰り返しごとの損失（交差エントロピー）
        Losses: float list
    }

/// 行の Map を、列名の順に並べた配列にする
let private toRow (features: string list) (row: Map<string, float>) : float[] =
    features |> List.map (fun feature -> row[feature]) |> List.toArray

/// 品種ごとの「特徴量の重み付きの和 + 切片」
let private scores (weights: float[][]) (bias: float[]) (row: float[]) : float[] =
    bias
    |> Array.mapi (fun k b -> b + (row |> Array.mapi (fun f value -> value * weights[f][k]) |> Array.sum))

/// バッチ勾配降下法で重みと切片を学習する
let fit (settings: Settings) (x: Map<string, float> list) (t: 'L list) : LogisticModel<'L> =
    let features =
        match x with
        | [] -> []
        | first :: _ -> first |> Map.keys |> Seq.toList

    let rows = x |> List.map (toRow features) |> List.toArray
    let classes = t |> List.distinct |> List.sort

    let targets =
        t |> List.map (fun label -> List.findIndex ((=) label) classes) |> List.toArray

    let n = float rows.Length

    let step (weights: float[][], bias: float[], losses: float list) _ =
        let probabilities = rows |> Array.map (scores weights bias >> softmax)

        // 確率 − 正解（正解の品種だけ 1 を引く）
        let errors =
            probabilities
            |> Array.mapi (fun i p -> p |> Array.mapi (fun k value -> if k = targets[i] then value - 1.0 else value))

        let gradient f k =
            Array.map2 (fun (row: float[]) (error: float[]) -> row[f] * error[k]) rows errors
            |> Array.sum

        let newWeights =
            weights
            |> Array.mapi (fun f w ->
                w
                |> Array.mapi (fun k value -> value - settings.LearningRate * gradient f k / n))

        let newBias =
            bias
            |> Array.mapi (fun k value ->
                value
                - settings.LearningRate * (errors |> Array.sumBy (fun error -> error[k])) / n)

        newWeights, newBias, crossEntropy probabilities targets :: losses

    let initial =
        Array.init features.Length (fun _ -> Array.zeroCreate classes.Length), Array.zeroCreate classes.Length, []

    let weights, bias, losses = List.fold step initial [ 1 .. settings.Epochs ]

    {
        Features = features
        Classes = classes
        Weights = weights
        Bias = bias
        Losses = List.rev losses
    }

/// スコアが最大の品種を予測する（ソフトマックスは大小関係を変えないので確率は計算しない）
let predict (model: LogisticModel<'L>) (x: Map<string, float> list) : 'L list =
    x
    |> List.map (fun row ->
        scores model.Weights model.Bias (toRow model.Features row)
        |> Array.indexed
        |> Array.maxBy snd
        |> fst
        |> fun k -> model.Classes[k])
