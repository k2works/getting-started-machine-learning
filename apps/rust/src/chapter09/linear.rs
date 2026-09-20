//! 正規方程式をコレスキー分解で解く、最小の線形回帰。

use ndarray::{Array1, Array2};

use super::{Error, Result};

/// 線形回帰のモデル。切片と、特徴量の列ごとの係数を持つ。
#[derive(Debug, Clone, PartialEq)]
pub struct LinearModel {
    pub intercept: f64,
    pub weights: Vec<f64>,
}

impl LinearModel {
    /// 先頭に 1 の列を加えた計画行列 X で、正規方程式 XᵀX β = Xᵀt を解く。
    pub fn fit(rows: &[Vec<f64>], t: &[f64]) -> Result<LinearModel> {
        let first = rows.first().ok_or(Error::Empty("特徴量"))?;
        let columns = first.len() + 1;

        let mut design = Array2::<f64>::zeros((rows.len(), columns));

        for (i, row) in rows.iter().enumerate() {
            design[[i, 0]] = 1.0;

            for (j, value) in row.iter().enumerate() {
                design[[i, j + 1]] = *value;
            }
        }

        let transposed = design.t();
        let normal = transposed.dot(&design);
        let right = transposed.dot(&Array1::from(t.to_vec()));
        let beta = solve_cholesky(&normal, &right)?;

        Ok(LinearModel {
            intercept: beta[0],
            weights: beta[1..].to_vec(),
        })
    }

    /// 1 行の特徴量から予測する。
    pub fn predict_one(&self, row: &[f64]) -> f64 {
        self.intercept
            + self
                .weights
                .iter()
                .zip(row)
                .map(|(weight, value)| weight * value)
                .sum::<f64>()
    }

    /// 行ごとに予測する。
    pub fn predict(&self, rows: &[Vec<f64>]) -> Vec<f64> {
        rows.iter().map(|row| self.predict_one(row)).collect()
    }
}

/// 対称正定値の連立方程式 A x = b を、コレスキー分解 A = L Lᵀ で解く。
/// 対角が 0 以下になれば「列が互いに独立でない」として失敗を返す。
fn solve_cholesky(a: &Array2<f64>, b: &Array1<f64>) -> Result<Vec<f64>> {
    let n = a.nrows();
    let mut l = Array2::<f64>::zeros((n, n));

    for i in 0..n {
        for j in 0..=i {
            let sum: f64 = (0..j).map(|k| l[[i, k]] * l[[j, k]]).sum();

            if i == j {
                let diagonal = a[[i, i]] - sum;

                if diagonal <= 0.0 {
                    return Err(Error::Singular);
                }

                l[[i, j]] = diagonal.sqrt();
            } else {
                l[[i, j]] = (a[[i, j]] - sum) / l[[j, j]];
            }
        }
    }

    // L y = b を前進代入で解く
    let mut y = vec![0.0; n];

    for i in 0..n {
        let sum: f64 = (0..i).map(|k| l[[i, k]] * y[k]).sum();

        y[i] = (b[i] - sum) / l[[i, i]];
    }

    // Lᵀ x = y を後退代入で解く
    let mut x = vec![0.0; n];

    for i in (0..n).rev() {
        let sum: f64 = ((i + 1)..n).map(|k| l[[k, i]] * x[k]).sum();

        x[i] = (y[i] - sum) / l[[i, i]];
    }

    Ok(x)
}

/// 決定係数。1 から、残差の 2 乗和を平均との差の 2 乗和で割った値を引く。
pub fn r_squared(actual: &[f64], predicted: &[f64]) -> Result<f64> {
    if actual.is_empty() {
        return Err(Error::Empty("値"));
    }

    #[allow(clippy::cast_precision_loss)]
    let mean = actual.iter().sum::<f64>() / actual.len() as f64;

    let residual: f64 = actual
        .iter()
        .zip(predicted)
        .map(|(value, guess)| (value - guess) * (value - guess))
        .sum();
    let total: f64 = actual
        .iter()
        .map(|value| (value - mean) * (value - mean))
        .sum();

    Ok(1.0 - residual / total)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 直線に乗る点を当てる() {
        // y = 2x + 1
        let rows = vec![vec![0.0], vec![1.0], vec![2.0]];
        let t = vec![1.0, 3.0, 5.0];

        let model = LinearModel::fit(&rows, &t).unwrap();

        assert!((model.intercept - 1.0).abs() < 1e-9);
        assert!((model.weights[0] - 2.0).abs() < 1e-9);
        assert!((r_squared(&t, &model.predict(&rows)).unwrap() - 1.0).abs() < 1e-9);
    }

    #[test]
    fn 列が2つでも解ける() {
        // y = 3a - b + 2
        let rows = vec![
            vec![1.0, 0.0],
            vec![0.0, 1.0],
            vec![1.0, 1.0],
            vec![2.0, 3.0],
        ];
        let t: Vec<f64> = rows.iter().map(|row| 3.0 * row[0] - row[1] + 2.0).collect();

        let model = LinearModel::fit(&rows, &t).unwrap();

        assert!((model.weights[0] - 3.0).abs() < 1e-9);
        assert!((model.weights[1] + 1.0).abs() < 1e-9);
    }

    #[test]
    fn 同じ値の列が2つあれば解けない() {
        let rows = vec![vec![1.0, 1.0], vec![2.0, 2.0], vec![3.0, 3.0]];
        let t = vec![1.0, 2.0, 3.0];

        assert_eq!(
            LinearModel::fit(&rows, &t).unwrap_err().to_string(),
            "特徴量の列が互いに独立でないため、正規方程式を解けません"
        );
    }

    #[test]
    fn 平均で予測すると決定係数は0になる() {
        let actual = vec![1.0, 2.0, 3.0];
        let predicted = vec![2.0, 2.0, 2.0];

        assert!(r_squared(&actual, &predicted).unwrap().abs() < 1e-12);
    }
}
