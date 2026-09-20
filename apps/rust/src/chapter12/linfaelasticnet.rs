//! linfa-elasticnet の `ElasticNet::ridge()`・`lasso()` で同じことをして、自作と突き合わせる。

use linfa::prelude::*;
use linfa_elasticnet::ElasticNet;
use ndarray::{Array1, Array2};

use crate::chapter02::Error as Chapter02Error;

use super::model::RegularizedModel;
use super::ridge::{center, intercept_from};
use super::{Error, Result};

/// linfa の繰り返しの上限。
pub const MAX_ITERATIONS: u32 = 10_000;
/// linfa の収束の判定。
pub const TOLERANCE: f64 = 1e-10;

/// 行を linfa に渡す行列にする。
fn records(rows: &[Vec<f64>]) -> Result<Array2<f64>> {
    let columns = rows.first().map_or(0, Vec::len);
    let values: Vec<f64> = rows.iter().flat_map(|row| row.iter().copied()).collect();

    Array2::from_shape_vec((rows.len(), columns), values)
        .map_err(|error| Error::Chapter02(Chapter02Error::Library(error.to_string())))
}

/// 自作の alpha を linfa の `penalty` に直す。
///
/// linfa-elasticnet の目的関数は `‖t - Xw - c‖² / (2n) + penalty (l1_ratio ‖w‖₁ +
/// (1 - l1_ratio) ‖w‖² / 2)` で、**誤差を 2n で割る**。自作は割らないので、
/// 両辺を 2n 倍すると `‖t - Xw‖² + 2n·penalty·l1_ratio‖w‖₁ + n·penalty(1 - l1_ratio)‖w‖²` に
/// なる。リッジ（l1_ratio = 0）もラッソ（l1_ratio = 1、自作は ½ 倍した目的関数）も、
/// **penalty = alpha / n** で自作と同じ解になる。
#[allow(clippy::cast_precision_loss)]
pub fn penalty_for(alpha: f64, n_samples: usize) -> f64 {
    alpha / n_samples as f64
}

/// linfa で学習して係数と切片を取り出す。
///
/// **linfa は正解の平均しか引かない。** `with_intercept(true)` でも特徴量は中心化されないので、
/// 列の平均が 0 でないデータをそのまま渡すと、切片のぶんまで係数に押し付けられて自作と
/// 大きく食い違う。そこで**こちらで特徴量の平均を引いてから**渡し、切片は平均から組み立て直す。
/// 第 12 章のデータは標準化してあるので実害は小さいが、標準化しない列があると効いてくる。
fn fit(
    columns: &[String],
    rows: &[Vec<f64>],
    t: &[f64],
    params: linfa_elasticnet::ElasticNetParams<f64>,
    alpha: f64,
) -> Result<RegularizedModel> {
    if alpha < 0.0 {
        return Err(Error::NegativeAlpha(alpha));
    }

    let (centered, _, x_means, t_mean) = center(rows, t);
    let dataset = Dataset::new(records(&centered)?, Array1::from(t.to_vec()));

    let model = params
        .penalty(penalty_for(alpha, t.len()))
        .max_iterations(MAX_ITERATIONS)
        .tolerance(TOLERANCE)
        .fit(&dataset)
        .map_err(|error| Error::Chapter02(Chapter02Error::Library(error.to_string())))?;

    let coefficients = model.hyperplane().to_vec();
    let intercept = intercept_from(&coefficients, &x_means, t_mean);

    RegularizedModel::new(columns.to_vec(), coefficients, intercept)
}

/// linfa のリッジ回帰（`l1_ratio` が 0）。
pub fn linfa_ridge(
    columns: &[String],
    rows: &[Vec<f64>],
    t: &[f64],
    alpha: f64,
) -> Result<RegularizedModel> {
    fit(columns, rows, t, ElasticNet::ridge(), alpha)
}

/// linfa のラッソ回帰（`l1_ratio` が 1）。
pub fn linfa_lasso(
    columns: &[String],
    rows: &[Vec<f64>],
    t: &[f64],
    alpha: f64,
) -> Result<RegularizedModel> {
    fit(columns, rows, t, ElasticNet::lasso(), alpha)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::chapter12::{lasso, ridge};

    fn strings(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    /// t = 2a + 3b + 1 で、c は正解に効かない架空のデータ。
    fn sample() -> (Vec<String>, Vec<Vec<f64>>, Vec<f64>) {
        let rows = vec![
            vec![0.0, 0.0, 1.0],
            vec![1.0, 0.0, -1.0],
            vec![0.0, 1.0, 1.0],
            vec![1.0, 2.0, -1.0],
            vec![2.0, 1.0, 1.0],
            vec![2.0, 2.0, -1.0],
        ];
        let t = rows
            .iter()
            .map(|row| 2.0 * row[0] + 3.0 * row[1] + 1.0)
            .collect();

        (strings(&["a", "b", "c"]), rows, t)
    }

    #[test]
    fn 件数で割ればlinfaのリッジ回帰は自作と一致する() {
        let (columns, rows, t) = sample();
        let own = ridge::fit(&columns, &rows, &t, 2.0).unwrap();
        let library = linfa_ridge(&columns, &rows, &t, 2.0).unwrap();

        for (left, right) in own.coefficients.iter().zip(&library.coefficients) {
            assert!((left - right).abs() < 1e-6, "{left} と {right}");
        }

        assert!((own.intercept - library.intercept).abs() < 1e-6);
    }

    #[test]
    fn 件数で割ればlinfaのラッソ回帰は自作と一致する() {
        let (columns, rows, t) = sample();
        let own = lasso::fit(&columns, &rows, &t, 1.0).unwrap();
        let library = linfa_lasso(&columns, &rows, &t, 1.0).unwrap();

        for (left, right) in own.coefficients.iter().zip(&library.coefficients) {
            assert!((left - right).abs() < 1e-6, "{left} と {right}");
        }

        assert!((own.intercept - library.intercept).abs() < 1e-6);
    }

    #[test]
    fn 罰則の読み替えは件数で割るだけ() {
        assert!((penalty_for(10.0, 5) - 2.0).abs() < 1e-12);
    }

    #[test]
    fn 負の罰則は受け付けない() {
        let (columns, rows, t) = sample();

        assert!(linfa_ridge(&columns, &rows, &t, -1.0).is_err());
    }
}
