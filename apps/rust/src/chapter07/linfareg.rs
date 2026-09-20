//! linfa-linear の線形回帰で同じことをして、自作の結果と突き合わせる。

use linfa::prelude::*;
use linfa_linear::LinearRegression as LinfaLinearRegression;
use ndarray::{Array1, Array2};

use super::linearregression::LinearModel;
use crate::chapter02::{Error, Features, Result};

/// linfa-linear で学習し、自作と同じ `LinearModel` にして返す。
pub fn fit(x: &[Features], t: &[f64]) -> Result<LinearModel> {
    let Some(first) = x.first() else {
        return Err(Error::AllMissing("特徴量".to_string()));
    };

    let dataset = Dataset::new(records(x)?, Array1::from(t.to_vec()));
    let model = LinfaLinearRegression::default()
        .fit(&dataset)
        .map_err(|error| Error::Library(error.to_string()))?;

    LinearModel::new(
        model.intercept(),
        first.columns.clone(),
        model.params().to_vec(),
    )
}

/// 特徴量を linfa に渡す行列にする。切片は linfa が自分で足すので、1 の列は入れない。
fn records(x: &[Features]) -> Result<Array2<f64>> {
    let columns = x.first().map_or(0, |features| features.values.len());
    let values: Vec<f64> = x
        .iter()
        .flat_map(|features| features.values.iter().copied())
        .collect();

    Array2::from_shape_vec((x.len(), columns), values)
        .map_err(|error| Error::Library(error.to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn features(columns: &[&str], values: &[f64]) -> Features {
        Features::new(
            columns.iter().map(|name| name.to_string()).collect(),
            values.to_vec(),
        )
        .unwrap()
    }

    #[test]
    fn linfaの線形回帰も直線の切片と係数を求める() {
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
    fn 自作とlinfaの係数はほぼ一致する() {
        let x = vec![
            features(&["a", "b"], &[0.0, 0.0]),
            features(&["a", "b"], &[1.0, 0.0]),
            features(&["a", "b"], &[0.0, 1.0]),
            features(&["a", "b"], &[1.0, 2.0]),
        ];
        let t = vec![3.0, 5.0, 2.2, 3.1];

        let ours = super::super::linearregression::fit(&x, &t).unwrap();
        let theirs = fit(&x, &t).unwrap();

        assert!((ours.intercept - theirs.intercept).abs() < 1e-6);

        for column in ["a", "b"] {
            assert!(
                (ours.coefficient(column).unwrap() - theirs.coefficient(column).unwrap()).abs()
                    < 1e-6,
                "{column} の係数が違う"
            );
        }
    }
}
