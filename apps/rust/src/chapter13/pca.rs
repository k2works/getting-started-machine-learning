//! 主成分分析。分散共分散行列をヤコビ法で固有値分解し、寄与率の大きい順に主成分を並べる。

use ndarray::{Array1, Array2, Axis};

use super::{Error, Result};

/// 対角より外の成分が行列全体の大きさに対してこの割合より小さくなったら、
/// 固有値分解が終わったとみなす。絶対値で比べると、行列が大きいときに収束しない。
const TOLERANCE: f64 = 1e-12;
/// 1 回の掃き出しで対角より外の成分をすべて消す、その繰り返しの上限。
const MAX_SWEEPS: usize = 100;

/// 学習した主成分分析のモデル。
#[derive(Debug, Clone, PartialEq)]
pub struct PcaModel {
    /// 列ごとの平均。
    pub mean: Vec<f64>,
    /// 主成分を 1 行に 1 つずつ、寄与率の大きい順に並べた行列。
    pub components: Array2<f64>,
    /// 主成分ごとの分散（固有値）。
    pub explained_variance: Vec<f64>,
    /// 主成分ごとの寄与率。
    pub explained_variance_ratio: Vec<f64>,
}

/// 主成分の向きに対する 1 つの列の係数。
#[derive(Debug, Clone, PartialEq)]
pub struct Loading {
    pub column: String,
    pub value: f64,
}

/// 列ごとの平均。
pub fn column_means(x: &Array2<f64>) -> Vec<f64> {
    x.mean_axis(Axis(0))
        .map_or_else(Vec::new, |means| means.to_vec())
}

/// 各列から平均を引く（中心化）。
fn center(x: &Array2<f64>, means: &[f64]) -> Array2<f64> {
    let mut centered = x.clone();

    for mut row in centered.rows_mut() {
        for (value, mean) in row.iter_mut().zip(means) {
            *value -= mean;
        }
    }

    centered
}

/// 列ごとの分散と、2 列ずつの共分散を並べた行列。件数から 1 を引いた数で割る。
pub fn covariance_matrix(x: &Array2<f64>) -> Result<Array2<f64>> {
    if x.nrows() < 2 {
        return Err(Error::TooFewRows(x.nrows()));
    }

    let centered = center(x, &column_means(x));

    #[allow(clippy::cast_precision_loss)]
    let divisor = (x.nrows() - 1) as f64;

    Ok(centered.t().dot(&centered) / divisor)
}

/// 対称行列をヤコビ法で固有値分解する。固有ベクトルは列ごとに 1 つ返す。
///
/// ndarray 0.16 には固有値分解が無い（`ndarray-linalg` は LAPACK を要求する）ので、
/// 分散共分散行列に必要なぶん、つまり対称行列の場合だけを自作する。
pub fn jacobi_eigen(matrix: &Array2<f64>) -> Result<(Vec<f64>, Array2<f64>)> {
    let size = matrix.nrows();

    if matrix.ncols() != size {
        return Err(Error::NotSquare {
            rows: size,
            columns: matrix.ncols(),
        });
    }

    let mut work = matrix.clone();
    let mut vectors = Array2::eye(size);
    // 行列の大きさに合わせた「これ以上は消せない」しきい値
    let limit = TOLERANCE * frobenius_norm(matrix).max(f64::MIN_POSITIVE);

    for _ in 0..MAX_SWEEPS {
        if off_diagonal_norm(&work) < limit {
            return Ok((work.diag().to_vec(), vectors));
        }

        for p in 0..size {
            for q in (p + 1)..size {
                rotate(&mut work, &mut vectors, p, q, limit);
            }
        }
    }

    Err(Error::NotConverged(MAX_SWEEPS))
}

/// すべての成分の 2 乗和の平方根。
fn frobenius_norm(matrix: &Array2<f64>) -> f64 {
    matrix.iter().map(|value| value * value).sum::<f64>().sqrt()
}

/// 対角より外の成分の 2 乗和の平方根。
fn off_diagonal_norm(matrix: &Array2<f64>) -> f64 {
    let mut sum = 0.0;

    for ((i, j), value) in matrix.indexed_iter() {
        if i != j {
            sum += value * value;
        }
    }

    sum.sqrt()
}

/// (p, q) の成分が 0 になるように回転し、固有ベクトルにも同じ回転をかける。
fn rotate(work: &mut Array2<f64>, vectors: &mut Array2<f64>, p: usize, q: usize, limit: f64) {
    let apq = work[[p, q]];

    // すでに十分小さい成分は回さない。しきい値は収束の判定より細かくする
    if apq.abs() < limit * f64::EPSILON {
        return;
    }

    // tan(2θ) = 2 a_pq / (a_pp - a_qq) を解いて、cos と sin を求める
    let theta = 0.5 * (2.0 * apq).atan2(work[[p, p]] - work[[q, q]]);
    let (sin, cos) = theta.sin_cos();
    let size = work.nrows();

    for k in 0..size {
        let (kp, kq) = (work[[k, p]], work[[k, q]]);

        work[[k, p]] = cos * kp + sin * kq;
        work[[k, q]] = -sin * kp + cos * kq;
    }

    for k in 0..size {
        let (pk, qk) = (work[[p, k]], work[[q, k]]);

        work[[p, k]] = cos * pk + sin * qk;
        work[[q, k]] = -sin * pk + cos * qk;
    }

    for k in 0..size {
        let (kp, kq) = (vectors[[k, p]], vectors[[k, q]]);

        vectors[[k, p]] = cos * kp + sin * kq;
        vectors[[k, q]] = -sin * kp + cos * kq;
    }
}

/// 固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の要素が正になるようにそろえる。
pub fn normalize_signs(components: &Array2<f64>) -> Array2<f64> {
    let mut normalized = components.clone();

    for mut row in normalized.rows_mut() {
        let largest = row.iter().copied().fold(0.0_f64, |largest, value| {
            if value.abs() > largest.abs() {
                value
            } else {
                largest
            }
        });

        if largest < 0.0 {
            row.map_inplace(|value| *value = -*value);
        }
    }

    normalized
}

/// 分散共分散行列を固有値分解し、寄与率の大きい順に n_components 個の主成分を求める。
pub fn fit(x: &Array2<f64>, n_components: usize) -> Result<PcaModel> {
    let covariance = covariance_matrix(x)?;
    let (eigenvalues, eigenvectors) = jacobi_eigen(&covariance)?;

    if n_components == 0 || n_components > eigenvalues.len() {
        return Err(Error::ComponentCount {
            requested: n_components,
            columns: eigenvalues.len(),
        });
    }

    // 固有値の大きい順に並べ替える。値が同じときは元の順を保つ
    let mut order: Vec<usize> = (0..eigenvalues.len()).collect();
    order.sort_by(|left, right| eigenvalues[*right].total_cmp(&eigenvalues[*left]));

    let total: f64 = eigenvalues.iter().sum();
    let mut components = Array2::zeros((n_components, eigenvalues.len()));

    for (row, index) in order.iter().take(n_components).enumerate() {
        components.row_mut(row).assign(&eigenvectors.column(*index));
    }

    let explained_variance: Vec<f64> = order
        .iter()
        .take(n_components)
        .map(|index| eigenvalues[*index])
        .collect();

    Ok(PcaModel {
        mean: column_means(x),
        components: normalize_signs(&components),
        explained_variance_ratio: explained_variance
            .iter()
            .map(|variance| variance / total)
            .collect(),
        explained_variance,
    })
}

/// 平均を引いてから、データを主成分の向きに射影する。
pub fn transform(model: &PcaModel, x: &Array2<f64>) -> Array2<f64> {
    center(x, &model.mean).dot(&model.components.t())
}

/// 累積寄与率がしきい値に届くまでの主成分の数。
pub fn components_needed(ratios: &[f64], threshold: f64) -> usize {
    let mut cumulative = 0.0;

    for (index, ratio) in ratios.iter().enumerate() {
        cumulative += ratio;

        if cumulative >= threshold {
            return index + 1;
        }
    }

    ratios.len()
}

/// 主成分の係数の絶対値が大きい順に、列名と係数を k 個返す。
pub fn top_loadings(component: &Array1<f64>, columns: &[String], k: usize) -> Vec<Loading> {
    let mut loadings: Vec<Loading> = columns
        .iter()
        .zip(component)
        .map(|(column, value)| Loading {
            column: column.clone(),
            value: *value,
        })
        .collect();

    loadings.sort_by(|left, right| right.value.abs().total_cmp(&left.value.abs()));
    loadings.truncate(k);

    loadings
}

#[cfg(test)]
mod tests {
    use super::*;
    use ndarray::array;

    #[test]
    fn 分散共分散行列は件数から一を引いた数で割る() {
        // 1 列目の分散は ((-1)^2 + 0 + 1^2) / 2 = 1
        let x = array![[1.0, 2.0], [2.0, 4.0], [3.0, 6.0]];

        let covariance = covariance_matrix(&x).unwrap();

        assert!((covariance[[0, 0]] - 1.0).abs() < 1e-12);
        assert!((covariance[[0, 1]] - 2.0).abs() < 1e-12);
        assert!((covariance[[1, 1]] - 4.0).abs() < 1e-12);
    }

    #[test]
    fn データが一件だけなら分散を求められない() {
        assert_eq!(
            covariance_matrix(&array![[1.0, 2.0]])
                .unwrap_err()
                .to_string(),
            "主成分分析には 2 件以上のデータが必要です（1 件）"
        );
    }

    #[test]
    fn 対角行列の固有値はそのまま対角に並ぶ() {
        let (values, vectors) = jacobi_eigen(&array![[3.0, 0.0], [0.0, 1.0]]).unwrap();

        assert!((values[0] - 3.0).abs() < 1e-12);
        assert!((values[1] - 1.0).abs() < 1e-12);
        assert_eq!(vectors, Array2::<f64>::eye(2));
    }

    #[test]
    fn 完全に相関する二列の第一主成分は四十五度の向きになる() {
        // 2 列目が 1 列目と同じ値なので、ばらつきはすべて (1, 1) の向きにある
        let x = array![[1.0, 1.0], [2.0, 2.0], [3.0, 3.0], [4.0, 4.0]];

        let model = fit(&x, 2).unwrap();

        let root = 1.0 / 2.0_f64.sqrt();
        assert!((model.components[[0, 0]] - root).abs() < 1e-9, "{model:?}");
        assert!((model.components[[0, 1]] - root).abs() < 1e-9, "{model:?}");
        assert!((model.explained_variance_ratio[0] - 1.0).abs() < 1e-9);
        assert!(model.explained_variance_ratio[1].abs() < 1e-9);
    }

    #[test]
    fn 寄与率は大きい順に並ぶ() {
        // 1 列目のばらつきが 2 列目より大きい
        let x = array![[-2.0, -0.1], [-1.0, 0.1], [1.0, -0.1], [2.0, 0.1]];

        let model = fit(&x, 2).unwrap();

        assert!(model.explained_variance_ratio[0] > model.explained_variance_ratio[1]);
        assert!(
            (model.explained_variance_ratio.iter().sum::<f64>() - 1.0).abs() < 1e-12,
            "{model:?}"
        );
        assert!(model.components[[0, 0]].abs() > 0.99, "{model:?}");
    }

    #[test]
    fn 主成分の数は列の数までしか選べない() {
        let x = array![[1.0, 1.0], [2.0, 3.0]];

        assert!(fit(&x, 3).is_err());
        assert!(fit(&x, 0).is_err());
    }

    #[test]
    fn 符号は絶対値が最大の要素が正になるようにそろえる() {
        let normalized = normalize_signs(&array![[-0.8, 0.6], [0.8, -0.6]]);

        assert!((normalized[[0, 0]] - 0.8).abs() < 1e-12);
        assert!((normalized[[0, 1]] + 0.6).abs() < 1e-12);
        assert!((normalized[[1, 0]] - 0.8).abs() < 1e-12);
    }

    #[test]
    fn 射影した値は平均を引いてから主成分にかけた値になる() {
        let x = array![[1.0, 1.0], [2.0, 2.0], [3.0, 3.0]];
        let model = fit(&x, 1).unwrap();

        let projected = transform(&model, &x);

        // 平均 (2, 2) の点は原点に移る
        assert!(projected[[1, 0]].abs() < 1e-9, "{projected:?}");
        assert!(
            (projected[[0, 0]] + projected[[2, 0]]).abs() < 1e-9,
            "{projected:?}"
        );
    }

    #[test]
    fn 累積寄与率がしきい値に届くまでの数を返す() {
        let ratios = [0.5, 0.3, 0.2];

        assert_eq!(components_needed(&ratios, 0.4), 1);
        assert_eq!(components_needed(&ratios, 0.8), 2);
        assert_eq!(components_needed(&ratios, 0.9), 3);
        // しきい値に届かなければ、すべての主成分を使う
        assert_eq!(components_needed(&ratios, 1.5), 3);
    }

    #[test]
    fn 係数の絶対値が大きい順に列名を返す() {
        let columns = vec!["A".to_string(), "B".to_string(), "C".to_string()];

        let loadings = top_loadings(&array![0.1, -0.9, 0.5], &columns, 2);

        assert_eq!(loadings[0].column, "B");
        assert!((loadings[0].value + 0.9).abs() < 1e-12);
        assert_eq!(loadings[1].column, "C");
    }
}
