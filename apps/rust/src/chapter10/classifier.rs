//! モデルに共通する操作（`fit` と `predict`）を表すトレイトと、正解率。

use crate::chapter02::{Features, TrainTestSplit};
use crate::chapter03::DecisionTree;

use super::Result;

/// `fit` で学習し、`predict` でラベルを予測する分類器。
pub trait Classifier {
    /// 訓練データで学習する。
    fn fit(&mut self, x: &[Features], t: &[String]) -> Result<()>;

    /// 特徴量ごとのラベルを予測する。
    fn predict(&self, x: &[Features]) -> Result<Vec<String>>;
}

/// 第 3 章の決定木を、変更せずにこの章のトレイトに合わせる。
/// トレイトがこのクレートのものなので、型のほうに手を入れずに後から実装を足せる。
/// Java・C# の `interface` は型の宣言に書く必要があるので、アダプターのクラスが要る。
impl Classifier for DecisionTree {
    fn fit(&mut self, x: &[Features], t: &[String]) -> Result<()> {
        DecisionTree::fit(self, x, t)?;

        Ok(())
    }

    fn predict(&self, x: &[Features]) -> Result<Vec<String>> {
        DecisionTree::predict(self, x)
    }
}

/// 訓練データとテストデータの正解率。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Score {
    pub train: f64,
    pub test: f64,
}

impl Score {
    /// モデルを訓練データで学習させてから、訓練データとテストデータの正解率を求める。
    pub fn evaluate(
        model: &mut dyn Classifier,
        split: &TrainTestSplit<Features, String>,
    ) -> Result<Score> {
        model.fit(&split.x_train, &split.t_train)?;

        Ok(Score {
            train: accuracy(&model.predict(&split.x_train)?, &split.t_train),
            test: accuracy(&model.predict(&split.x_test)?, &split.t_test),
        })
    }
}

/// 予測が正解ラベルと一致した割合を返す。
pub fn accuracy(predictions: &[String], labels: &[String]) -> f64 {
    if labels.is_empty() {
        return 0.0;
    }

    let correct = predictions
        .iter()
        .zip(labels)
        .filter(|(prediction, label)| prediction == label)
        .count();

    #[allow(clippy::cast_precision_loss)]
    let ratio = correct as f64 / labels.len() as f64;

    ratio
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
    fn 一致した割合を返す() {
        assert!(
            (accuracy(&labels(&["a", "b", "a"]), &labels(&["a", "b", "b"])) - 2.0 / 3.0).abs()
                < 1e-12
        );
        assert_eq!(accuracy(&[], &[]), 0.0);
    }

    #[test]
    fn 第3章の決定木をトレイト越しに使う() {
        let x = vec![column(0.2), column(0.3), column(2.3), column(2.5)];
        let t = labels(&["setosa", "setosa", "virginica", "virginica"]);
        let split = TrainTestSplit {
            x_train: x.clone(),
            x_test: x,
            t_train: t.clone(),
            t_test: t,
        };

        let mut model = DecisionTree::with_max_depth(1);
        let score = Score::evaluate(&mut model, &split).unwrap();

        assert!((score.train - 1.0).abs() < 1e-12);
        assert!((score.test - 1.0).abs() < 1e-12);
    }

    #[test]
    fn 学習する前に予測すると失敗する() {
        let model = DecisionTree::unlimited();

        assert_eq!(
            Classifier::predict(&model, &[column(0.2)])
                .unwrap_err()
                .to_string(),
            "学習してから予測してください"
        );
    }
}
