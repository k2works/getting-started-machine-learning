//! 列ごとの平均と標準偏差（母標準偏差）で、平均 0・標準偏差 1 にそろえる。

use crate::chapter02::Features;

use super::{Error, Result};

/// 列ごとの平均と標準偏差。訓練データで `fit` し、同じ値で訓練データとテストデータの両方を
/// `transform` する。列の順は特徴量の列の順のまま。
#[derive(Debug, Clone, PartialEq)]
pub struct Standardizer {
    pub columns: Vec<String>,
    pub means: Vec<f64>,
    /// 標準偏差。すべて同じ値の列は 1 にして、標準化した値が 0 になるようにする
    pub stds: Vec<f64>,
}

impl Standardizer {
    /// 特徴量のすべての列について、平均と標準偏差を求める。
    pub fn fit(x: &[Features]) -> Result<Standardizer> {
        let first = x.first().ok_or(Error::Empty("特徴量"))?;
        let columns = first.columns.clone();

        let mut means = Vec::with_capacity(columns.len());
        let mut stds = Vec::with_capacity(columns.len());

        for column in &columns {
            let values: Vec<f64> = x
                .iter()
                .map(|features| features.value(column))
                .collect::<crate::chapter02::Result<_>>()?;

            let mean = average(&values);
            let variance = average(
                &values
                    .iter()
                    .map(|value| (value - mean) * (value - mean))
                    .collect::<Vec<f64>>(),
            );
            let std = variance.sqrt();

            means.push(mean);
            stds.push(if std == 0.0 { 1.0 } else { std });
        }

        Ok(Standardizer {
            columns,
            means,
            stds,
        })
    }

    /// 列の平均を返す。
    pub fn mean(&self, column: &str) -> Result<f64> {
        Ok(self.means[self.index(column)?])
    }

    /// 列の標準偏差を返す。
    pub fn std(&self, column: &str) -> Result<f64> {
        Ok(self.stds[self.index(column)?])
    }

    /// 1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。
    pub fn transform_one(&self, features: &Features) -> Result<Features> {
        let values = features
            .columns
            .iter()
            .zip(&features.values)
            .map(|(column, value)| match self.index(column) {
                Ok(index) => (value - self.means[index]) / self.stds[index],
                Err(_) => *value,
            })
            .collect();

        Ok(Features::new(features.columns.clone(), values)?)
    }

    /// 特徴量のリストを標準化する。
    pub fn transform(&self, x: &[Features]) -> Result<Vec<Features>> {
        x.iter()
            .map(|features| self.transform_one(features))
            .collect()
    }

    /// 列の位置を返す。
    fn index(&self, column: &str) -> Result<usize> {
        self.columns
            .iter()
            .position(|name| name == column)
            .ok_or_else(|| {
                Error::Chapter02(crate::chapter02::Error::MissingColumn(column.to_string()))
            })
    }
}

/// 平均を求める。値が 1 件も無ければ 0 を返す。
fn average(values: &[f64]) -> f64 {
    if values.is_empty() {
        return 0.0;
    }

    #[allow(clippy::cast_precision_loss)]
    let count = values.len() as f64;

    values.iter().sum::<f64>() / count
}

#[cfg(test)]
mod tests {
    use super::*;

    /// RM と LSTAT を持つ特徴量を作る。
    fn row(rm: f64, lstat: f64) -> Features {
        Features::new(vec!["RM".to_string(), "LSTAT".to_string()], vec![rm, lstat]).unwrap()
    }

    #[test]
    fn 訓練データから列ごとの平均と標準偏差を求める() {
        let standardizer =
            Standardizer::fit(&[row(1.0, 10.0), row(2.0, 10.0), row(3.0, 40.0)]).unwrap();

        assert!((standardizer.mean("RM").unwrap() - 2.0).abs() < 1e-12);
        assert!((standardizer.mean("LSTAT").unwrap() - 20.0).abs() < 1e-12);
        // 件数（3）で割る標準偏差（母標準偏差）。scikit-learn の StandardScaler と同じ定義
        assert!((standardizer.std("RM").unwrap() - (2.0_f64 / 3.0).sqrt()).abs() < 1e-12);
        assert!((standardizer.std("LSTAT").unwrap() - 200.0_f64.sqrt()).abs() < 1e-12);
    }

    #[test]
    fn 訓練データの平均と標準偏差で別のデータを標準化する() {
        let standardizer = Standardizer::fit(&[row(1.0, 0.0), row(3.0, 0.0)]).unwrap();

        let transformed = standardizer
            .transform(&[row(1.0, 0.0), row(3.0, 0.0)])
            .unwrap();

        assert!((transformed[0].value("RM").unwrap() + 1.0).abs() < 1e-12);
        assert!((transformed[1].value("RM").unwrap() - 1.0).abs() < 1e-12);
    }

    #[test]
    fn すべて同じ値の列は標準化すると0になる() {
        let standardizer = Standardizer::fit(&[row(1.0, 5.0), row(3.0, 5.0)]).unwrap();

        assert!((standardizer.std("LSTAT").unwrap() - 1.0).abs() < 1e-12);
        assert!(
            standardizer.transform(&[row(1.0, 5.0)]).unwrap()[0]
                .value("LSTAT")
                .unwrap()
                .abs()
                < 1e-12
        );
    }

    #[test]
    fn 平均を持たない列はそのまま残す() {
        let standardizer = Standardizer {
            columns: vec!["RM".to_string()],
            means: vec![2.0],
            stds: vec![1.0],
        };

        let transformed = standardizer.transform_one(&row(3.0, 7.0)).unwrap();

        assert!((transformed.value("RM").unwrap() - 1.0).abs() < 1e-12);
        assert!((transformed.value("LSTAT").unwrap() - 7.0).abs() < 1e-12);
    }

    #[test]
    fn 特徴量が1件も無ければ学習できない() {
        assert_eq!(
            Standardizer::fit(&[]).unwrap_err().to_string(),
            "特徴量が 1 件もありません"
        );
    }
}
