module MachineLearning.Chapter12.MlNetRegularization

open System
open Microsoft.ML
open Microsoft.ML.Data
open Microsoft.ML.Trainers
open MachineLearning.Chapter07.Matrix
open MachineLearning.Chapter12.Regularization

/// ML.NET に渡す 1 行。特徴量も正解も float32 で渡す
[<CLIMutable>]
type RegressionRow = { Features: float32[]; Label: float32 }

/// 自作と突き合わせられるように、十分に収束させ、順番を固定して 1 スレッドで学習する
[<Literal>]
let ConvergenceTolerance = 1e-7f

[<Literal>]
let MaximumNumberOfIterations = 10000

/// ML.NET の SDCA で、(1/n) × 誤差の二乗和 + (l2 / 2) × 係数の二乗和 を最小化する。L1 の罰則は 0 にする
let fitSdca (l2: float) (x: Matrix) (t: float list) : RegularizedModel =
    let context = MLContext(seed = 0)
    let schema = SchemaDefinition.Create(typeof<RegressionRow>)
    schema["Features"].ColumnType <- VectorDataViewType(NumberDataViewType.Single, x[0].Length)

    let rows =
        List.map2
            (fun (features: float[]) (label: float) ->
                {
                    Features = Array.map float32 features
                    Label = float32 label
                })
            (List.ofArray x)
            t

    let options =
        SdcaRegressionTrainer.Options(
            L2Regularization = Nullable(float32 l2),
            L1Regularization = Nullable 0.0f,
            ConvergenceTolerance = ConvergenceTolerance,
            MaximumNumberOfIterations = Nullable MaximumNumberOfIterations,
            Shuffle = false,
            NumberOfThreads = Nullable 1
        )

    let trained =
        context.Regression.Trainers.Sdca(options).Fit(context.Data.LoadFromEnumerable(rows, schema))

    {
        Coefficients = trained.Model.Weights |> Seq.map float |> Seq.toList
        Intercept = float trained.Model.Bias
    }

/// 自作の fitRidge と同じ alpha でリッジ回帰を学習する。ML.NET の l2 は alpha × 2 / 件数 に当たる
let fitRidgeWithMlNet (alpha: float) (x: Matrix) (t: float list) : RegularizedModel =
    fitSdca (2.0 * alpha / float t.Length) x t
