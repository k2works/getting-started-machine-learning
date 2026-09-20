//! ラッソ回帰（L1 正則化）を座標降下法で解く。
//!
//! L1 の罰則は原点で折れているので、リッジ回帰のように行列を解いて終わりにはできない。
//! 係数を 1 つずつ順に動かし、軟しきい値作用素で 0 に寄せる。

use crate::chapter02::Error as Chapter02Error;

use super::model::RegularizedModel;
use super::ridge::{center, intercept_from};
use super::{Error, Result};

/// 繰り返しの上限。
pub const MAX_ITERATIONS: usize = 10_000;
/// 係数の動きがこれより小さくなったら止める。
pub const TOLERANCE: f64 = 1e-10;

/// 軟しきい値作用素。`|value|` が `threshold` 以下なら 0 にし、そうでなければ 0 のほうへ縮める。
pub fn soft_threshold(value: f64, threshold: f64) -> f64 {
    if value > threshold {
        value - threshold
    } else if value < -threshold {
        value + threshold
    } else {
        0.0
    }
}

/// 平均を引いてから座標降下法で `½‖t - Xw‖² + alpha ‖w‖₁` を最小にする。
///
/// リッジ回帰と尺度をそろえるため、**件数で割らない**目的関数にする。
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

    let (x, residuals, x_means, t_mean) = center(rows, t);
    let features = columns.len();

    // 列ごとの 2 乗和。座標降下法の割り算の分母になる
    let norms: Vec<f64> = (0..features)
        .map(|j| x.iter().map(|row| row[j] * row[j]).sum::<f64>())
        .collect();

    let mut weights = vec![0.0; features];
    // 残差 r = t - X w。係数を 1 つ動かすたびに差分で更新する
    let mut r = residuals;

    for _ in 0..MAX_ITERATIONS {
        let mut largest_change: f64 = 0.0;

        for j in 0..features {
            if norms[j] == 0.0 {
                continue;
            }

            let old = weights[j];

            // いったん j 列の寄与を残差に戻す
            if old != 0.0 {
                for (value, row) in r.iter_mut().zip(&x) {
                    *value += old * row[j];
                }
            }

            let correlation: f64 = r.iter().zip(&x).map(|(value, row)| value * row[j]).sum();

            weights[j] = soft_threshold(correlation, alpha) / norms[j];

            if weights[j] != 0.0 {
                for (value, row) in r.iter_mut().zip(&x) {
                    *value -= weights[j] * row[j];
                }
            }

            largest_change = largest_change.max((weights[j] - old).abs());
        }

        if largest_change < TOLERANCE {
            break;
        }
    }

    let intercept = intercept_from(&weights, &x_means, t_mean);

    RegularizedModel::new(columns.to_vec(), weights, intercept)
}

#[cfg(test)]
mod tests {
    use super::*;

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
    fn 軟しきい値作用素は零に寄せる() {
        assert!((soft_threshold(3.0, 1.0) - 2.0).abs() < 1e-12);
        assert!((soft_threshold(-3.0, 1.0) + 2.0).abs() < 1e-12);
        assert!(soft_threshold(0.5, 1.0).abs() < 1e-12);
    }

    #[test]
    fn 罰則が零なら最小二乗法とほぼ同じ解になる() {
        let (columns, rows, t) = sample();
        let model = fit(&columns, &rows, &t, 0.0).unwrap();

        assert!((model.coefficients[0] - 2.0).abs() < 1e-6);
        assert!((model.coefficients[1] - 3.0).abs() < 1e-6);
        assert!((model.intercept - 1.0).abs() < 1e-6);
    }

    #[test]
    fn 罰則を強くすると係数がちょうど零になる() {
        let (columns, rows, t) = sample();
        let model = fit(&columns, &rows, &t, 100.0).unwrap();

        assert_eq!(model.zero_columns(), columns);
    }

    #[test]
    fn 正解に効かない列が先に零になる() {
        let (columns, rows, t) = sample();
        let model = fit(&columns, &rows, &t, 1.0).unwrap();

        assert!(model.zero_columns().contains(&"c".to_string()));
        assert!(!model.zero_columns().contains(&"a".to_string()));
    }

    #[test]
    fn 負の罰則は受け付けない() {
        let (columns, rows, t) = sample();

        assert!(fit(&columns, &rows, &t, -1.0).is_err());
    }
}
