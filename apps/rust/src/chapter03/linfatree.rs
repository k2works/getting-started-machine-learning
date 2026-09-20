//! linfa の決定木で同じことをして、自作の結果と突き合わせる。

use linfa::prelude::*;
use linfa_trees::DecisionTree as LinfaDecisionTree;
use ndarray::{Array1, Array2};

use crate::chapter02::{Error, Features, Result};

/// linfa の決定木で学習して予測する。ラベルは文字列のままでは渡せないので、
/// 出現順に番号を振ってから渡し、予測の結果を文字列に戻す。
pub fn predict(
    x_train: &[Features],
    t_train: &[String],
    x_test: &[Features],
    max_depth: Option<usize>,
) -> Result<Vec<String>> {
    let (encoded, classes) = encode(t_train);
    let dataset = Dataset::new(records(x_train)?, Array1::from(encoded));

    let model = LinfaDecisionTree::params()
        .max_depth(max_depth)
        .fit(&dataset)
        .map_err(|error| Error::Library(error.to_string()))?;

    let predicted = model.predict(&records(x_test)?);

    Ok(predicted
        .iter()
        .map(|index| classes[*index].clone())
        .collect())
}

/// 特徴量を linfa に渡す行列にする。
fn records(x: &[Features]) -> Result<Array2<f64>> {
    let columns = x.first().map_or(0, |features| features.values.len());
    let values: Vec<f64> = x
        .iter()
        .flat_map(|features| features.values.iter().copied())
        .collect();

    Array2::from_shape_vec((x.len(), columns), values)
        .map_err(|error| Error::Library(error.to_string()))
}

/// ラベルに出現順の番号を振る。戻り値は（番号の列, 番号から文字列への対応）。
fn encode(t: &[String]) -> (Vec<usize>, Vec<String>) {
    let mut classes: Vec<String> = Vec::new();
    let mut encoded = Vec::with_capacity(t.len());

    for label in t {
        let index = match classes.iter().position(|name| name == label) {
            Some(index) => index,
            None => {
                classes.push(label.clone());

                classes.len() - 1
            }
        };

        encoded.push(index);
    }

    (encoded, classes)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn features(values: &[f64]) -> Features {
        Features::new(vec!["花弁幅".to_string()], values.to_vec()).unwrap()
    }

    fn labels(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    #[test]
    fn ラベルに出現順の番号を振る() {
        let (encoded, classes) = encode(&labels(&["b", "a", "b"]));

        assert_eq!(encoded, vec![0, 1, 0]);
        assert_eq!(classes, labels(&["b", "a"]));
    }

    #[test]
    fn linfaの決定木で学習して予測する() {
        let x: Vec<Features> = [0.2, 0.3, 2.3, 2.5]
            .iter()
            .map(|value| features(&[*value]))
            .collect();
        let t = labels(&["setosa", "setosa", "virginica", "virginica"]);

        let predictions = predict(&x, &t, &x, Some(1)).unwrap();

        assert_eq!(predictions, t);
    }
}
