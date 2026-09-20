//! リッジ回帰（L2 正則化）を閉形式で解く。

use ndarray::{Array1, Array2};

use crate::chapter02::Error as Chapter02Error;
use crate::chapter07::solve;

use super::model::RegularizedModel;
use super::{Error, Result};

/// 列ごとの平均。
pub fn column_means(rows: &[Vec<f64>]) -> Vec<f64> {
    let columns = rows.first().map_or(0, Vec::len);

    #[allow(clippy::cast_precision_loss)]
    let size = rows.len() as f64;

    (0..columns)
        .map(|j| rows.iter().map(|row| row[j]).sum::<f64>() / size)
        .collect()
}

/// 値の平均。
pub fn average(values: &[f64]) -> f64 {
    #[allow(clippy::cast_precision_loss)]
    let size = values.len() as f64;

    values.iter().sum::<f64>() / size
}

/// 行と正解から平均を引く。切片に罰則をかけないための下ごしらえ。
pub fn center(rows: &[Vec<f64>], t: &[f64]) -> (Vec<Vec<f64>>, Vec<f64>, Vec<f64>, f64) {
    let x_means = column_means(rows);
    let t_mean = average(t);

    let centered = rows
        .iter()
        .map(|row| {
            row.iter()
                .zip(&x_means)
                .map(|(value, mean)| value - mean)
                .collect()
        })
        .collect();

    let residuals = t.iter().map(|value| value - t_mean).collect();

    (centered, residuals, x_means, t_mean)
}

/// 切片を平均から求める。中心化した解に、引いた平均を戻す。
pub fn intercept_from(coefficients: &[f64], x_means: &[f64], t_mean: f64) -> f64 {
    t_mean
        - coefficients
            .iter()
            .zip(x_means)
            .map(|(coefficient, mean)| coefficient * mean)
            .sum::<f64>()
}

/// 行を ndarray の行列にする。
fn matrix(rows: &[Vec<f64>]) -> Result<Array2<f64>> {
    let columns = rows.first().map_or(0, Vec::len);
    let values: Vec<f64> = rows.iter().flat_map(|row| row.iter().copied()).collect();

    Array2::from_shape_vec((rows.len(), columns), values)
        .map_err(|error| Error::Chapter02(Chapter02Error::Library(error.to_string())))
}

/// 平均を引いてから `(XᵀX + alpha I) w = Xᵀ t` を解き、切片を平均から求める。
///
/// 目的関数は `‖t - Xw‖² + alpha ‖w‖²` で、**件数で割らない**。alpha が 0 なら
/// 第 7 章の最小二乗法と同じ解になる。
pub fn fit(
    columns: &[String],
    rows: &[Vec<f64>],
    t: &[f64],
    alpha: f64,
) -> Result<RegularizedModel> {
    if alpha < 0.0 {
        return Err(Error::NegativeAlpha(alpha));
    }

    if rows.len() != t.len() {
        return Err(Error::Chapter02(Chapter02Error::LengthMismatch {
            left: rows.len(),
            right: t.len(),
        }));
    }

    let (centered, residuals, x_means, t_mean) = center(rows, t);
    let x = matrix(&centered)?;
    let transposed = x.t();

    // XᵀX に alpha を対角に足す。対角を大きくするほど解が小さいほうへ引き戻される
    let mut normal = transposed.dot(&x);

    for index in 0..normal.nrows() {
        normal[[index, index]] += alpha;
    }

    let right = transposed.dot(&Array1::from(residuals));
    let coefficients = solve(&normal, &right)?.to_vec();
    let intercept = intercept_from(&coefficients, &x_means, t_mean);

    RegularizedModel::new(columns.to_vec(), coefficients, intercept)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn strings(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    /// t = 2a + 3b + 1 の架空のデータ。
    fn sample() -> (Vec<String>, Vec<Vec<f64>>, Vec<f64>) {
        let rows = vec![
            vec![0.0, 0.0],
            vec![1.0, 0.0],
            vec![0.0, 1.0],
            vec![1.0, 2.0],
            vec![2.0, 1.0],
        ];
        let t = rows
            .iter()
            .map(|row| 2.0 * row[0] + 3.0 * row[1] + 1.0)
            .collect();

        (strings(&["a", "b"]), rows, t)
    }

    #[test]
    fn 罰則が零なら最小二乗法と同じ解になる() {
        let (columns, rows, t) = sample();
        let model = fit(&columns, &rows, &t, 0.0).unwrap();

        assert!((model.coefficients[0] - 2.0).abs() < 1e-9);
        assert!((model.coefficients[1] - 3.0).abs() < 1e-9);
        assert!((model.intercept - 1.0).abs() < 1e-9);
    }

    #[test]
    fn 罰則を強くすると係数が小さくなる() {
        let (columns, rows, t) = sample();
        let weak = fit(&columns, &rows, &t, 1.0).unwrap();
        let strong = fit(&columns, &rows, &t, 100.0).unwrap();

        assert!(strong.coefficient_abs_sum() < weak.coefficient_abs_sum());
    }

    #[test]
    fn 罰則を強くしても切片は正解の平均に近いまま() {
        let (columns, rows, t) = sample();
        let model = fit(&columns, &rows, &t, 1e9).unwrap();

        assert!((model.intercept - average(&t)).abs() < 1e-6);
    }

    #[test]
    fn 負の罰則は受け付けない() {
        let (columns, rows, t) = sample();
        let error = fit(&columns, &rows, &t, -1.0).unwrap_err();

        assert_eq!(error.to_string(), "正則化の強さは 0 以上にしてください: -1");
    }

    #[test]
    fn 件数が違えば学習できない() {
        let (columns, rows, _) = sample();

        assert!(fit(&columns, &rows, &[1.0], 1.0).is_err());
    }
}
