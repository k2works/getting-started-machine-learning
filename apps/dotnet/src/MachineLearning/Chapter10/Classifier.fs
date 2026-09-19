module MachineLearning.Chapter10.Classifier

open MachineLearning.Chapter01.KinokoTakenoko
open MachineLearning.Chapter02.IrisPreprocessing

/// 分類器。訓練データ（特徴量と正解ラベル）を受け取り、予測する関数を返す関数
type Classifier<'L> = Map<string, float> list -> 'L list -> (Map<string, float> list -> 'L list)

/// 「学習してモデルを返す関数」と「モデルで予測する関数」を組み合わせて分類器にする
let ofModel
    (fit: Map<string, float> list -> 'L list -> 'M)
    (predict: 'M -> Map<string, float> list -> 'L list)
    : Classifier<'L> =
    fun x t -> predict (fit x t)

/// 訓練データとテストデータの正解率
type Score = { Train: float; Test: float }

/// 訓練データで学習させてから、訓練データとテストデータの正解率を求める
let evaluate (classifier: Classifier<'L>) (split: TrainTestSplit<Map<string, float>, 'L>) : Score =
    let predict = classifier split.XTrain split.TTrain

    {
        Train = accuracy (predict split.XTrain) split.TTrain
        Test = accuracy (predict split.XTest) split.TTest
    }
