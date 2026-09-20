//! 正規方程式で線形回帰を学習する。

use ndarray::{Array1, Array2};

use super::matrix::solve;
use crate::chapter02::{Error, Features, Result};

/// 学習した線形回帰のモデル。切片と、列名の付いた係数を持つ。
///
/// Java 版は `LinkedHashMap` で列の順を保ったが、Rust の `HashMap` は順を保たないので、
/// 列名と係数を同じ順のベクタで持って対応させる（`Features` と同じ持ち方）。
#[derive(Debug, Clone, PartialEq)]
pub struct LinearModel {
    pub intercept: f64,
    pub columns: Vec<String>,
    pub coefficients: Vec<f64>,
}

impl LinearModel {
    /// 切片と、同じ順に並んだ列名・係数からモデルを作る。
    pub fn new(
        intercept: f64,
        columns: Vec<String>,
        coefficients: Vec<f64>,
    ) -> Result<LinearModel> {
        if columns.len() != coefficients.len() {
            return Err(Error::LengthMismatch {
                left: columns.len(),
                right: coefficients.len(),
            });
        }

        Ok(LinearModel {
            intercept,
            columns,
            coefficients,
        })
    }

    /// 列名で係数を読む。
    pub fn coefficient(&self, column: &str) -> Result<f64> {
        self.columns
            .iter()
            .position(|name| name == column)
            .map(|index| self.coefficients[index])
            .ok_or_else(|| Error::MissingColumn(column.to_string()))
    }

    /// 1 行分の特徴量の予測値。係数は列名で対応させるので、特徴量の列の並び順は問わない。
    pub fn predict_one(&self, features: &Features) -> Result<f64> {
        let mut sum = self.intercept;

        for (column, coefficient) in self.columns.iter().zip(&self.coefficients) {
            sum += coefficient * features.value(column)?;
        }

        Ok(sum)
    }

    /// 行ごとの予測値。
    pub fn predict(&self, x: &[Features]) -> Result<Vec<f64>> {
        x.iter()
            .map(|features| self.predict_one(features))
            .collect()
    }
}

/// 先頭に 1 の列を足した特徴量の行列（計画行列）を作る。1 の列の係数が切片になる。
pub fn design_matrix(x: &[Features]) -> Result<Array2<f64>> {
    let columns = x.first().map_or(0, |features| features.values.len()) + 1;
    let mut values = Vec::with_capacity(x.len() * columns);

    for features in x {
        values.push(1.0);
        values.extend(features.values.iter().copied());
    }

    Array2::from_shape_vec((x.len(), columns), values)
        .map_err(|error| Error::Library(error.to_string()))
}

/// (Xᵀ X) w = Xᵀ t を解いて、切片と係数を求める。列名は先頭の行の特徴量から取る。
pub fn fit(x: &[Features], t: &[f64]) -> Result<LinearModel> {
    if x.len() != t.len() {
        return Err(Error::LengthMismatch {
            left: x.len(),
            right: t.len(),
        });
    }

    let Some(first) = x.first() else {
        return Err(Error::AllMissing("特徴量".to_string()));
    };

    let design = design_matrix(x)?;
    let target = Array1::from(t.to_vec());
    let transposed = design.t();
    let weights = solve(&transposed.dot(&design), &transposed.dot(&target))?;

    LinearModel::new(
        weights[0],
        first.columns.clone(),
        weights.slice(ndarray::s![1..]).to_vec(),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 列名と値から特徴量を作る。
    fn features(columns: &[&str], values: &[f64]) -> Features {
        Features::new(
            columns.iter().map(|name| name.to_string()).collect(),
            values.to_vec(),
        )
        .unwrap()
    }

    #[test]
    fn 計画行列の先頭は一の列になる() {
        let x = vec![features(&["SNS1"], &[2.0]), features(&["SNS1"], &[3.0])];

        assert_eq!(
            design_matrix(&x).unwrap(),
            ndarray::array![[1.0, 2.0], [1.0, 3.0]]
        );
    }

    #[test]
    fn 直線上の点から切片と係数を求める() {
        // y = 1 + 2x
        let x = vec![
            features(&["SNS1"], &[0.0]),
            features(&["SNS1"], &[1.0]),
            features(&["SNS1"], &[2.0]),
        ];
        let t = vec![1.0, 3.0, 5.0];

        let model = fit(&x, &t).unwrap();

        assert!(
            (model.intercept - 1.0).abs() < 1e-9,
            "切片 = {}",
            model.intercept
        );
        assert!((model.coefficient("SNS1").unwrap() - 2.0).abs() < 1e-9);
    }

    #[test]
    fn 二つの特徴量でも係数を求める() {
        // y = 3 + 2a - b
        let x = vec![
            features(&["a", "b"], &[0.0, 0.0]),
            features(&["a", "b"], &[1.0, 0.0]),
            features(&["a", "b"], &[0.0, 1.0]),
            features(&["a", "b"], &[1.0, 1.0]),
        ];
        let t = vec![3.0, 5.0, 2.0, 4.0];

        let model = fit(&x, &t).unwrap();

        assert!((model.intercept - 3.0).abs() < 1e-9);
        assert!((model.coefficient("a").unwrap() - 2.0).abs() < 1e-9);
        assert!((model.coefficient("b").unwrap() + 1.0).abs() < 1e-9);
    }

    #[test]
    fn 列の並びが違っても同じ予測になる() {
        let model =
            LinearModel::new(1.0, vec!["a".to_string(), "b".to_string()], vec![2.0, -1.0]).unwrap();

        assert!(
            (model
                .predict_one(&features(&["b", "a"], &[1.0, 1.0]))
                .unwrap()
                - 2.0)
                .abs()
                < 1e-9
        );
    }

    #[test]
    fn 列名と係数の数が違えばモデルを作れない() {
        assert_eq!(
            LinearModel::new(0.0, vec!["a".to_string()], vec![1.0, 2.0])
                .unwrap_err()
                .to_string(),
            "件数が違います: 1 と 2"
        );
    }

    #[test]
    fn 知らない列の係数は読めない() {
        let model = LinearModel::new(0.0, vec!["a".to_string()], vec![1.0]).unwrap();

        assert_eq!(
            model.coefficient("b").unwrap_err().to_string(),
            "列がありません: b"
        );
    }

    #[test]
    fn 特徴量と実測値の件数が違えば学習できない() {
        let x = vec![features(&["a"], &[1.0])];

        assert_eq!(
            fit(&x, &[1.0, 2.0]).unwrap_err().to_string(),
            "件数が違います: 1 と 2"
        );
    }
}
