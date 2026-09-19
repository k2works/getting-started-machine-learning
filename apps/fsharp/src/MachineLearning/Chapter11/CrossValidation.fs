module MachineLearning.Chapter11.CrossValidation

open MachineLearning.Chapter02.Random
open MachineLearning.Chapter11.Metrics

/// 1 回分の分け方。訓練データとテストデータの行番号
type Fold = { Train: int list; Test: int list }

/// 行番号をシードで並べ替えてから nSplits 個に分け、それぞれをテストデータ、残りを訓練データにする
let kFold (nSplits: int) (seed: int) (nSamples: int) : Fold list =
    let positions = [ 0 .. nSamples - 1 ] |> shuffle seed

    positions
    |> List.splitInto nSplits
    |> List.map (fun test ->
        {
            Train = positions |> List.except test
            Test = test
        })

/// モデル。訓練データ（特徴量と正解）を受け取り、予測する関数を返す関数
type Model<'T> = Map<string, float> list -> 'T list -> (Map<string, float> list -> 'T list)

/// 行番号のリストで、リストから要素を取り出す
let private pick (rows: int list) (items: 'A list) : 'A list =
    let array = List.toArray items
    rows |> List.map (fun row -> array[row])

/// 分割ごとに、訓練データで学習し、テストデータの予測を評価関数で採点する。
/// スコアは取り出すときに初めて計算する（取り出した分だけ学習する）
let crossValidate
    (model: Model<'T>)
    (metric: Metric<'T>)
    (folds: Fold list)
    (x: Map<string, float> list)
    (t: 'T list)
    : float seq =
    seq {
        for fold in folds do
            let predict = model (pick fold.Train x) (pick fold.Train t)
            yield metric (pick fold.Test t) (predict (pick fold.Test x))
    }
