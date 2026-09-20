//! 訓練データで標準化してから 2 次の項を足す変換。第 9 章の道具をそのまま組み合わせる。

use crate::chapter02::Features;
use crate::chapter09::{Standardizer, polynomial};

use super::Result;

/// 標準化と多項式特徴量をつなげた変換。訓練データで `fit` し、同じ平均と標準偏差で
/// 訓練・検証・テストの 3 つを `transform` する。
#[derive(Debug, Clone, PartialEq)]
pub struct PolynomialScaler {
    standardizer: Standardizer,
    columns: Vec<String>,
}

impl PolynomialScaler {
    /// 訓練データの平均と標準偏差を覚える。
    pub fn fit(x: &[Features]) -> Result<PolynomialScaler> {
        let standardizer = Standardizer::fit(x)?;
        let columns = standardizer.columns.clone();

        Ok(PolynomialScaler {
            standardizer,
            columns,
        })
    }

    /// 変換後の列名。元の列、2 乗の列（"RM^2"）、積の列（"RM LSTAT"）の順。
    pub fn feature_names(&self) -> Vec<String> {
        self.columns
            .iter()
            .cloned()
            .chain(
                polynomial::pairs_with_replacement(&self.columns)
                    .iter()
                    .map(polynomial::Pair::name),
            )
            .collect()
    }

    /// 標準化してから 2 次の項を足し、値だけの行にする。
    pub fn transform(&self, x: &[Features]) -> Result<Vec<Vec<f64>>> {
        let standardized = self.standardizer.transform(x)?;
        let expanded = polynomial::expand(&standardized, &self.columns)?;

        Ok(expanded
            .iter()
            .map(|features| features.values.clone())
            .collect())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn strings(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    fn row(a: f64, b: f64) -> Features {
        Features::new(strings(&["a", "b"]), vec![a, b]).unwrap()
    }

    fn sample() -> Vec<Features> {
        vec![row(1.0, 10.0), row(3.0, 30.0), row(5.0, 50.0)]
    }

    #[test]
    fn 列名は元の列と二次の項の順に並ぶ() {
        let scaler = PolynomialScaler::fit(&sample()).unwrap();

        assert_eq!(
            scaler.feature_names(),
            strings(&["a", "b", "a^2", "a b", "b^2"])
        );
    }

    #[test]
    fn 訓練データは平均零に標準化される() {
        let scaler = PolynomialScaler::fit(&sample()).unwrap();
        let rows = scaler.transform(&sample()).unwrap();
        let sum: f64 = rows.iter().map(|row| row[0]).sum();

        assert!(sum.abs() < 1e-12);
    }

    #[test]
    fn 二乗の列は標準化した値の二乗になる() {
        let scaler = PolynomialScaler::fit(&sample()).unwrap();
        let rows = scaler.transform(&sample()).unwrap();

        for row in &rows {
            assert!((row[2] - row[0] * row[0]).abs() < 1e-12);
            assert!((row[3] - row[0] * row[1]).abs() < 1e-12);
        }
    }

    #[test]
    fn 検証データは訓練データの平均と標準偏差で変換される() {
        let scaler = PolynomialScaler::fit(&sample()).unwrap();
        // 訓練データの平均は a=3、標準偏差は sqrt(8/3)
        let rows = scaler.transform(&[row(3.0, 30.0)]).unwrap();

        assert!(rows[0][0].abs() < 1e-12);
    }
}
