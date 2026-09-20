//! linfa の `cross_validate_single` で交差検証をして、自作の交差検証と突き合わせる。

use linfa::prelude::*;
use linfa_linear::LinearRegression as LinfaLinearRegression;
use ndarray::Array1;

use crate::chapter02::{Error, Features, Result};

use super::linfametrics::records;

/// linfa の交差検証で、分割ごとの RMSE の平均を求める。
///
/// linfa の `cross_validate_single` は**行を並べ替えず**、先頭から順に k 個のかたまりに分ける。
/// また、分割ごとのスコアではなく**平均だけ**を返す（返り値の長さはモデルの数）。自作の
/// 交差検証と突き合わせるときは `k_fold_sequential`（並べ替えなし）のスコアの平均と比べる。
///
/// データを内部で入れ替えながら分割するので、`Dataset` は `mut` で持たなければならない。
/// 「借用している間はほかから触れない」ことをコンパイラが保証するぶん、呼び出し側の書き方が決まる。
pub fn linfa_cross_validate_rmse(x: &[Features], t: &[f64], n_splits: usize) -> Result<f64> {
    let mut dataset = Dataset::new(records(x)?, Array1::from(t.to_vec()));
    let models = vec![LinfaLinearRegression::default()];

    let scores: Array1<f64> = dataset
        .cross_validate_single(n_splits, &models, |prediction, truth| {
            let squared: f64 = prediction
                .iter()
                .zip(truth.iter())
                .map(|(predicted, actual)| (predicted - actual) * (predicted - actual))
                .sum();

            #[allow(clippy::cast_precision_loss)]
            let size = truth.len() as f64;

            Ok((squared / size).sqrt())
        })
        .map_err(|error: linfa_linear::LinearError<f64>| Error::Library(error.to_string()))?;

    scores
        .first()
        .copied()
        .ok_or_else(|| Error::Library("交差検証の結果がありません".to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn column(value: f64) -> Features {
        Features::new(vec!["x".to_string()], vec![value]).unwrap()
    }

    /// t = 2x + 1 の架空のデータ。
    fn line(size: i32) -> (Vec<Features>, Vec<f64>) {
        let values: Vec<f64> = (0..size).map(f64::from).collect();

        (
            values.iter().map(|value| column(*value)).collect(),
            values.iter().map(|value| 2.0 * value + 1.0).collect(),
        )
    }

    #[test]
    fn 直線に乗るデータなら誤差はほぼ零になる() {
        let (x, t) = line(12);
        let score = linfa_cross_validate_rmse(&x, &t, 3).unwrap();

        assert!(score < 1e-3, "RMSE = {score}");
    }

    #[test]
    fn 分割の数を変えても直線なら誤差は小さいまま() {
        let (x, t) = line(12);

        assert!(linfa_cross_validate_rmse(&x, &t, 4).unwrap() < 1e-3);
    }
}
