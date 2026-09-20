//! 回帰の評価指標。`t` は実測値、`y` は予測値。第 11・12 章でも使う。

use crate::chapter02::{Error, Result};

/// 実測値と予測値の差を返す。件数が違えば失敗する。
fn residuals(t: &[f64], y: &[f64]) -> Result<Vec<f64>> {
    if t.len() != y.len() {
        return Err(Error::LengthMismatch {
            left: t.len(),
            right: y.len(),
        });
    }

    Ok(t.iter()
        .zip(y)
        .map(|(actual, predicted)| actual - predicted)
        .collect())
}

/// 件数を f64 にする。評価指標の割り算に使う。
#[allow(clippy::cast_precision_loss)]
fn count(values: &[f64]) -> f64 {
    values.len() as f64
}

/// 平均絶対誤差（MAE）。誤差の絶対値の平均。
pub fn mean_absolute_error(t: &[f64], y: &[f64]) -> Result<f64> {
    let residuals = residuals(t, y)?;
    let sum: f64 = residuals.iter().map(|residual| residual.abs()).sum();

    Ok(sum / count(&residuals))
}

/// 平均二乗誤差の平方根（RMSE）。
pub fn root_mean_squared_error(t: &[f64], y: &[f64]) -> Result<f64> {
    let residuals = residuals(t, y)?;

    Ok((sum_of_squares(&residuals) / count(&residuals)).sqrt())
}

/// 決定係数（R²）。1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。
pub fn r2_score(t: &[f64], y: &[f64]) -> Result<f64> {
    let residual = sum_of_squares(&residuals(t, y)?);
    let mean: f64 = t.iter().sum::<f64>() / count(t);
    let deviations: Vec<f64> = t.iter().map(|value| value - mean).collect();

    Ok(1.0 - residual / sum_of_squares(&deviations))
}

/// 2 乗の合計。
fn sum_of_squares(values: &[f64]) -> f64 {
    values.iter().map(|value| value * value).sum()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 誤差が無ければ平均絶対誤差は零になる() {
        assert!(mean_absolute_error(&[1.0, 2.0], &[1.0, 2.0]).unwrap().abs() < 1e-12);
    }

    #[test]
    fn 平均絶対誤差は誤差の絶対値の平均になる() {
        let mae = mean_absolute_error(&[1.0, 2.0], &[2.0, 4.0]).unwrap();

        assert!((mae - 1.5).abs() < 1e-12, "MAE = {mae}");
    }

    #[test]
    fn 二乗平均平方根誤差は大きい誤差を重く見る() {
        let rmse = root_mean_squared_error(&[1.0, 2.0], &[2.0, 4.0]).unwrap();

        assert!(
            (rmse - (5.0f64 / 2.0).sqrt()).abs() < 1e-12,
            "RMSE = {rmse}"
        );
    }

    #[test]
    fn 完全に当たれば決定係数は一になる() {
        let r2 = r2_score(&[1.0, 2.0, 3.0], &[1.0, 2.0, 3.0]).unwrap();

        assert!((r2 - 1.0).abs() < 1e-12, "R2 = {r2}");
    }

    #[test]
    fn 平均を答え続けると決定係数は零になる() {
        let r2 = r2_score(&[1.0, 2.0, 3.0], &[2.0, 2.0, 2.0]).unwrap();

        assert!(r2.abs() < 1e-12, "R2 = {r2}");
    }

    #[test]
    fn 実測値と予測値の件数が違えば評価できない() {
        assert_eq!(
            r2_score(&[1.0], &[1.0, 2.0]).unwrap_err().to_string(),
            "件数が違います: 1 と 2"
        );
    }
}
