module MachineLearning.Chapter13.Pca

open MachineLearning.Chapter13.Eigen

/// 学習した主成分分析のモデル。Components の 1 行が 1 つの主成分を表す
type PcaModel =
    {
        Mean: float[]
        Components: float[][]
        ExplainedVariance: float[]
        ExplainedVarianceRatio: float[]
    }

/// 列ごとの平均。x の 1 行が 1 件のデータを表す
let columnMeans (x: float[][]) : float[] =
    x |> Array.transpose |> Array.map Array.average

/// 2 つのベクトルの内積
let dot (a: float[]) (b: float[]) : float = Array.map2 (*) a b |> Array.sum

/// 各行から列の平均を引く（中心化）
let private center (means: float[]) (x: float[][]) : float[][] =
    x |> Array.map (fun row -> Array.map2 (-) row means)

let covarianceMatrix (x: float[][]) : float[][] =
    let columns = x |> center (columnMeans x) |> Array.transpose
    let n = float x.Length

    columns
    |> Array.map (fun a -> columns |> Array.map (fun b -> dot a b / (n - 1.0)))

/// 固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の要素が正になるようにそろえる
let normalizeSigns (components: float[][]) : float[][] =
    components
    |> Array.map (fun pc ->
        let largest = pc |> Array.maxBy abs
        pc |> Array.map (fun value -> value * float (sign largest)))

/// 分散共分散行列の固有値の大きい順に、nComponents 個の主成分を求める
let fitPca (nComponents: int) (x: float[][]) : PcaModel =
    let pairs = symmetricEigen (covarianceMatrix x)
    let total = pairs |> List.sumBy (fun pair -> pair.Value)
    let selected = pairs |> List.truncate nComponents |> List.toArray

    {
        Mean = columnMeans x
        Components = selected |> Array.map (fun pair -> pair.Vector) |> normalizeSigns
        ExplainedVariance = selected |> Array.map (fun pair -> pair.Value)
        ExplainedVarianceRatio = selected |> Array.map (fun pair -> pair.Value / total)
    }

/// 平均を引いてから、主成分ごとの座標（主成分の向きとの内積）に変換する
let transform (model: PcaModel) (x: float[][]) : float[][] =
    x
    |> center model.Mean
    |> Array.map (fun row -> model.Components |> Array.map (dot row))

/// 累積寄与率がしきい値に届くまでに必要な主成分の数
let componentsNeeded (threshold: float) (ratios: float[]) : int =
    ratios |> Array.scan (+) 0.0 |> Array.findIndex (fun sum -> sum >= threshold)

/// 主成分の係数の絶対値が大きい順に、上位 k 個の列名と係数を返す
let topLoadings (k: int) (columns: string list) (pc: float[]) : (string * float) list =
    List.zip columns (List.ofArray pc)
    |> List.sortByDescending (fun (_, value) -> abs value)
    |> List.truncate k
