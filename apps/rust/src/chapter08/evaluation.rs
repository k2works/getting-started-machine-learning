//! 正解率と、生存者をどれだけ見つけられたかの評価。

use super::pipeline::FittedPipeline;
use super::survived::{self, SURVIVED};
use crate::chapter02::{Error, Result, Row, TrainTestSplit};

/// 訓練データとテストデータの正解率と、テストデータの生存者のうち生存と予測できた人数。
#[derive(Debug, Clone, PartialEq)]
pub struct Evaluation {
    pub train_accuracy: f64,
    pub test_accuracy: f64,
    pub found_survivors: usize,
    pub survivors: usize,
}

/// 予測が正解ラベルと一致した割合を返す。
pub fn accuracy(predictions: &[i32], labels: &[i32]) -> Result<f64> {
    if predictions.len() != labels.len() {
        return Err(Error::LengthMismatch {
            left: predictions.len(),
            right: labels.len(),
        });
    }

    let correct = predictions
        .iter()
        .zip(labels)
        .filter(|(prediction, label)| prediction == label)
        .count();

    #[allow(clippy::cast_precision_loss)]
    let ratio = correct as f64 / labels.len() as f64;

    Ok(ratio)
}

/// 学習済みのパイプラインを、訓練データとテストデータで評価する。
pub fn evaluate(pipeline: &FittedPipeline, split: &TrainTestSplit<Row, i32>) -> Result<Evaluation> {
    let predictions = pipeline.predict(&survived::features(&split.x_test))?;
    let train_predictions = pipeline.predict(&survived::features(&split.x_train))?;

    Ok(Evaluation {
        train_accuracy: accuracy(&train_predictions, &split.t_train)?,
        test_accuracy: accuracy(&predictions, &split.t_test)?,
        found_survivors: predictions
            .iter()
            .zip(&split.t_test)
            .filter(|(prediction, label)| **prediction == SURVIVED && **label == SURVIVED)
            .count(),
        survivors: split
            .t_test
            .iter()
            .filter(|label| **label == SURVIVED)
            .count(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 全部当たれば正解率は一になる() {
        assert!((accuracy(&[1, 0], &[1, 0]).unwrap() - 1.0).abs() < 1e-12);
    }

    #[test]
    fn 半分当たれば正解率は零点五になる() {
        assert!((accuracy(&[1, 1], &[1, 0]).unwrap() - 0.5).abs() < 1e-12);
    }

    #[test]
    fn 予測と正解ラベルの件数が違えば正解率を出せない() {
        assert_eq!(
            accuracy(&[1], &[1, 0]).unwrap_err().to_string(),
            "件数が違います: 1 と 2"
        );
    }
}
