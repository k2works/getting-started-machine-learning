//! linfa-preprocessing の `LinearScaler::standard()` で標準化し、自作と突き合わせる。

use linfa::prelude::*;
use linfa_preprocessing::linear_scaling::LinearScaler;
use ndarray::{Array1, Array2};

use crate::chapter02::Error as Chapter02Error;

use super::Result;

/// 訓練データの値から平均と標準偏差を求め、別の値を標準化する。
/// linfa には 1 列だけを渡す API が無いので、1 列の行列にしてから渡す。
pub fn standardize(train: &[f64], values: &[f64]) -> Result<Vec<f64>> {
    let scaler = LinearScaler::standard()
        .fit(&dataset(train)?)
        .map_err(|error| Chapter02Error::Library(error.to_string()))?;

    let scaled = scaler.transform(dataset(values)?);

    Ok(scaled.records().column(0).to_vec())
}

/// 1 列の行列に正解ラベルの入れ物を付けて、linfa のデータセットにする。
fn dataset(values: &[f64]) -> Result<DatasetBase<Array2<f64>, Array1<()>>> {
    let records = Array2::from_shape_vec((values.len(), 1), values.to_vec())
        .map_err(|error| Chapter02Error::Library(error.to_string()))?;

    Ok(DatasetBase::from(records))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn linfaの標準化は母標準偏差で割る() {
        // 平均 2.5、母標準偏差 sqrt(1.25)。標本標準偏差なら sqrt(5/3) になる
        let scaled = standardize(&[1.0, 2.0, 3.0, 4.0], &[1.0]).unwrap();

        let population = (1.0 - 2.5) / 1.25_f64.sqrt();

        assert!((scaled[0] - population).abs() < 1e-12, "{scaled:?}");
    }

    #[test]
    fn 訓練データの平均と標準偏差で別のデータを標準化する() {
        let scaled = standardize(&[1.0, 3.0], &[1.0, 2.0, 3.0]).unwrap();

        assert!((scaled[0] + 1.0).abs() < 1e-12);
        assert!(scaled[1].abs() < 1e-12);
        assert!((scaled[2] - 1.0).abs() < 1e-12);
    }
}
