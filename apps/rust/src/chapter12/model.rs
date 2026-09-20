//! 正則化した線形回帰のモデル。列名の付いた係数と切片を持つ。

use crate::chapter02::Error as Chapter02Error;

use super::{Error, Result};

/// 正則化した線形回帰のモデル。
#[derive(Debug, Clone, PartialEq)]
pub struct RegularizedModel {
    pub columns: Vec<String>,
    pub coefficients: Vec<f64>,
    pub intercept: f64,
}

impl RegularizedModel {
    /// 列名と係数の数が合っていることを確かめてから作る。
    pub fn new(
        columns: Vec<String>,
        coefficients: Vec<f64>,
        intercept: f64,
    ) -> Result<RegularizedModel> {
        if columns.len() != coefficients.len() {
            return Err(Error::Chapter02(Chapter02Error::LengthMismatch {
                left: columns.len(),
                right: coefficients.len(),
            }));
        }

        Ok(RegularizedModel {
            columns,
            coefficients,
            intercept,
        })
    }

    /// 1 行分の予測値。値の並びは学習したときの列の並びと同じでなければならない。
    pub fn predict_one(&self, row: &[f64]) -> Result<f64> {
        if row.len() != self.coefficients.len() {
            return Err(Error::Chapter02(Chapter02Error::LengthMismatch {
                left: row.len(),
                right: self.coefficients.len(),
            }));
        }

        Ok(self.intercept
            + row
                .iter()
                .zip(&self.coefficients)
                .map(|(value, coefficient)| value * coefficient)
                .sum::<f64>())
    }

    /// 行ごとの予測値。
    pub fn predict(&self, rows: &[Vec<f64>]) -> Result<Vec<f64>> {
        rows.iter().map(|row| self.predict_one(row)).collect()
    }

    /// 係数の絶対値の合計。正則化が強いほど小さくなる。
    pub fn coefficient_abs_sum(&self) -> f64 {
        self.coefficients.iter().map(|value| value.abs()).sum()
    }

    /// 係数がちょうど 0 になった列の名前を、列の順に返す。
    pub fn zero_columns(&self) -> Vec<String> {
        self.columns
            .iter()
            .zip(&self.coefficients)
            .filter(|(_, coefficient)| **coefficient == 0.0)
            .map(|(column, _)| column.clone())
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn strings(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    #[test]
    fn 係数と切片から予測する() {
        let model = RegularizedModel::new(strings(&["a", "b"]), vec![2.0, -1.0], 3.0).unwrap();

        assert!((model.predict_one(&[1.0, 1.0]).unwrap() - 4.0).abs() < 1e-12);
    }

    #[test]
    fn 列名と係数の数が違えば作れない() {
        let error = RegularizedModel::new(strings(&["a"]), vec![1.0, 2.0], 0.0).unwrap_err();

        assert_eq!(error.to_string(), "件数が違います: 1 と 2");
    }

    #[test]
    fn 列数が合わない行は予測できない() {
        let model = RegularizedModel::new(strings(&["a", "b"]), vec![1.0, 1.0], 0.0).unwrap();

        assert!(model.predict_one(&[1.0]).is_err());
    }

    #[test]
    fn 係数の絶対値の合計を求める() {
        let model = RegularizedModel::new(strings(&["a", "b"]), vec![2.0, -3.0], 0.0).unwrap();

        assert!((model.coefficient_abs_sum() - 5.0).abs() < 1e-12);
    }

    #[test]
    fn 係数が零の列の名前を返す() {
        let model =
            RegularizedModel::new(strings(&["a", "b", "c"]), vec![1.0, 0.0, 0.0], 0.0).unwrap();

        assert_eq!(model.zero_columns(), strings(&["b", "c"]));
    }
}
