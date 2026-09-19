module MachineLearning.Chapter13.StatsPca

open System
open System.IO
open FSharp.Stats
open FSharp.Stats.ML.Unsupervised
open MachineLearning.Chapter13.Pca

/// 標準出力への書き出しを捨てながら f を実行する。PCA.compute は途中の値を標準出力に書き出すため
let private withoutConsoleOutput (f: unit -> 'T) : 'T =
    let original = Console.Out
    Console.SetOut TextWriter.Null

    try
        f ()
    finally
        Console.SetOut original

/// FSharp.Stats の PCA で主成分分析し、自作の fitPca と同じ形の PcaModel にする
let fitPcaWithFSharpStats (nComponents: int) (x: float[][]) : PcaModel =
    let mean = columnMeans x
    let centered = x |> Array.map (fun row -> Array.map2 (-) row mean) |> matrix
    let result = withoutConsoleOutput (fun () -> PCA.compute centered)
    let loadings = result.Loadings
    // Loadings は列が主成分なので、1 行が 1 つの主成分になるように並べ替える
    let components =
        Array.init loadings.NumCols (fun j -> Array.init loadings.NumRows (fun i -> loadings[i, j]))

    {
        Mean = mean
        Components = components |> Array.truncate nComponents |> normalizeSigns
        // VarianceOfComponent は標準偏差なので、2 乗して分散にする
        ExplainedVariance =
            result.VarianceOfComponent
            |> Vector.toArray
            |> Array.truncate nComponents
            |> Array.map (fun std -> std * std)
        ExplainedVarianceRatio =
            result.VarExplainedByComponentIndividual
            |> Vector.toArray
            |> Array.truncate nComponents
    }
