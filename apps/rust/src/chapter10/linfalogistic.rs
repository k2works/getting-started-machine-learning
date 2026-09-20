//! linfa-logistic のロジスティック回帰で同じことをして、自作の結果と突き合わせる。

use linfa::prelude::*;
use linfa_logistic::MultiLogisticRegression;
use ndarray::{Array1, Array2};

use crate::chapter02::{Error, Features};

use super::Result;
use super::classifier::Classifier;

/// linfa の既定の繰り返し回数。
pub const DEFAULT_MAX_ITERATIONS: u64 = 100;
/// 正則化を実質的に無くすための、ごく小さい L2 の強さ。
pub const WEAK_PENALTY: f64 = 1e-8;

/// linfa-logistic のロジスティック回帰を、この章のトレイトに合わせる。
#[derive(Debug, Clone)]
pub struct LinfaLogisticRegression {
    max_iterations: u64,
    alpha: f64,
    classes: Vec<String>,
    /// weights[特徴量][品種]
    weights: Vec<Vec<f64>>,
    bias: Vec<f64>,
}

impl Default for LinfaLogisticRegression {
    /// linfa の既定（繰り返し 100 回、L2 の強さ 1.0）。
    fn default() -> Self {
        LinfaLogisticRegression::new(DEFAULT_MAX_ITERATIONS, 1.0)
    }
}

impl LinfaLogisticRegression {
    /// 繰り返し回数と L2 の強さを指定して作る。
    pub fn new(max_iterations: u64, alpha: f64) -> Self {
        LinfaLogisticRegression {
            max_iterations,
            alpha,
            classes: Vec::new(),
            weights: Vec::new(),
            bias: Vec::new(),
        }
    }

    /// 学習した品種の並び。
    pub fn classes(&self) -> &[String] {
        &self.classes
    }
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

impl Classifier for LinfaLogisticRegression {
    fn fit(&mut self, x: &[Features], t: &[String]) -> Result<()> {
        let dataset = Dataset::new(records(x)?, Array1::from(t.to_vec()));

        let model = MultiLogisticRegression::default()
            .max_iterations(self.max_iterations)
            .alpha(self.alpha)
            .fit(&dataset)
            .map_err(|error| Error::Library(error.to_string()))?;

        // 学習した重みを取り出しておくと、自作の重みと並べて見られる
        self.classes = model.classes().to_vec();
        self.weights = model
            .params()
            .rows()
            .into_iter()
            .map(|row| row.to_vec())
            .collect();
        self.bias = model.intercept().to_vec();

        Ok(())
    }

    fn predict(&self, x: &[Features]) -> Result<Vec<String>> {
        if self.classes.is_empty() {
            return Err(Error::NotFitted);
        }

        Ok(x.iter()
            .map(|features| {
                let mut scores = self.bias.clone();

                for (feature, value) in features.values.iter().enumerate() {
                    for (class, score) in scores.iter_mut().enumerate() {
                        *score += value * self.weights[feature][class];
                    }
                }

                let mut best = 0;

                for (index, score) in scores.iter().enumerate() {
                    if *score > scores[best] {
                        best = index;
                    }
                }

                self.classes[best].clone()
            })
            .collect())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn labels(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    fn column(value: f64) -> Features {
        Features::new(vec!["花弁幅".to_string()], vec![value]).unwrap()
    }

    #[test]
    fn linfaのロジスティック回帰で学習して予測する() {
        let x = vec![column(0.2), column(0.3), column(2.3), column(2.5)];
        let t = labels(&["setosa", "setosa", "virginica", "virginica"]);

        let mut model = LinfaLogisticRegression::new(1000, WEAK_PENALTY);
        model.fit(&x, &t).unwrap();

        assert_eq!(model.predict(&x).unwrap(), t);
    }

    #[test]
    fn 学習する前に予測すると失敗する() {
        let model = LinfaLogisticRegression::default();

        assert_eq!(
            model.predict(&[column(0.2)]).unwrap_err().to_string(),
            "学習してから予測してください"
        );
    }
}
