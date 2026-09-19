module MachineLearning.Chapter09.Outliers

open MachineLearning.Chapter02.IrisPreprocessing

/// 分位点。値を並べ、位置 (件数 - 1) × q の前後の値を線形補間する
let quantile (values: float list) (q: float) : float =
    let sorted = values |> List.sort |> List.toArray
    let position = float (sorted.Length - 1) * q
    let lower = sorted[int (floor position)]
    let upper = sorted[int (ceil position)]
    lower + (upper - lower) * (position - floor position)

/// 第 1 四分位点・第 3 四分位点から、四分位範囲（IQR）の k 倍より外側にある値を外れ値とする
let iqrOutliersWith (k: float) (values: float list) : bool list =
    let q1 = quantile values 0.25
    let q3 = quantile values 0.75
    let iqr = q3 - q1

    values |> List.map (fun value -> value < q1 - k * iqr || value > q3 + k * iqr)

/// k = 1.5 の四分位範囲による外れ値
let iqrOutliers: float list -> bool list = iqrOutliersWith 1.5

/// 訓練データの正解の外れ値の行を、訓練データから除く。テストデータには手を付けない
let removeTargetOutliers (split: TrainTestSplit<'X, float>) : TrainTestSplit<'X, float> =
    let kept =
        List.zip3 split.XTrain split.TTrain (iqrOutliers split.TTrain)
        |> List.filter (fun (_, _, outlier) -> not outlier)

    { split with
        XTrain = kept |> List.map (fun (x, _, _) -> x)
        TTrain = kept |> List.map (fun (_, t, _) -> t)
    }
