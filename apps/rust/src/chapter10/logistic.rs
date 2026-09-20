//! ソフトマックスと勾配降下法によるロジスティック回帰。

use crate::chapter02::{Error, Features};

use super::Result;
use super::classifier::Classifier;

/// 既定の学習率。
const DEFAULT_LEARNING_RATE: f64 = 1.0;
/// 既定の繰り返し回数。
const DEFAULT_EPOCHS: usize = 5000;
/// 対数が発散しないように足す、ごく小さい値。
const EPSILON: f64 = 1e-12;

/// ソフトマックスと勾配降下法で学習するロジスティック回帰。
#[derive(Debug, Clone)]
pub struct LogisticRegression {
    learning_rate: f64,
    epochs: usize,
    classes: Vec<String>,
    /// weights[特徴量][品種]
    weights: Vec<Vec<f64>>,
    bias: Vec<f64>,
    losses: Vec<f64>,
}

impl Default for LogisticRegression {
    /// 学習率 1.0、繰り返し 5000 回のロジスティック回帰。
    fn default() -> Self {
        LogisticRegression::new(DEFAULT_LEARNING_RATE, DEFAULT_EPOCHS)
    }
}

impl LogisticRegression {
    /// 学習率と繰り返し回数を指定して作る。
    pub fn new(learning_rate: f64, epochs: usize) -> Self {
        LogisticRegression {
            learning_rate,
            epochs,
            classes: Vec::new(),
            weights: Vec::new(),
            bias: Vec::new(),
            losses: Vec::new(),
        }
    }

    /// 学習した品種の並び（名前の順）。
    pub fn classes(&self) -> &[String] {
        &self.classes
    }

    /// 繰り返しごとの訓練データの損失。
    pub fn losses(&self) -> &[f64] {
        &self.losses
    }

    /// 1 行分の品種ごとのスコアを求める。
    fn scores(&self, row: &[f64]) -> Vec<f64> {
        let mut scores = self.bias.clone();

        for (feature, value) in row.iter().enumerate() {
            for (class, score) in scores.iter_mut().enumerate() {
                *score += value * self.weights[feature][class];
            }
        }

        scores
    }

    /// 重みと切片を、勾配の分だけ動かす。
    fn update(&mut self, rows: &[Vec<f64>], errors: &[Vec<f64>]) {
        #[allow(clippy::cast_precision_loss)]
        let n = rows.len() as f64;

        for class in 0..self.classes.len() {
            for feature in 0..self.weights.len() {
                let gradient: f64 = rows
                    .iter()
                    .zip(errors)
                    .map(|(row, error)| row[feature] * error[class])
                    .sum();

                self.weights[feature][class] -= self.learning_rate * gradient / n;
            }

            let bias_gradient: f64 = errors.iter().map(|error| error[class]).sum();

            self.bias[class] -= self.learning_rate * bias_gradient / n;
        }
    }
}

/// スコアを、合計が 1 になる確率に変換する。
/// 最大値を引いてから exp を求めるので、大きな値でもあふれない。
pub fn softmax(z: &[f64]) -> Vec<f64> {
    let max = z.iter().copied().fold(f64::NEG_INFINITY, f64::max);
    let exps: Vec<f64> = z.iter().map(|value| (value - max).exp()).collect();
    let total: f64 = exps.iter().sum();

    exps.iter().map(|value| value / total).collect()
}

/// 交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。
pub fn cross_entropy(probabilities: &[Vec<f64>], targets: &[usize]) -> f64 {
    if probabilities.is_empty() {
        return 0.0;
    }

    #[allow(clippy::cast_precision_loss)]
    let count = probabilities.len() as f64;

    -probabilities
        .iter()
        .zip(targets)
        .map(|(probability, target)| (probability[*target] + EPSILON).ln())
        .sum::<f64>()
        / count
}

impl Classifier for LogisticRegression {
    /// バッチ勾配降下法で重みと切片を学習する。
    fn fit(&mut self, x: &[Features], t: &[String]) -> Result<()> {
        let first = x.first().ok_or(Error::NotFitted)?;
        let rows: Vec<Vec<f64>> = x.iter().map(|features| features.values.clone()).collect();

        let mut classes: Vec<String> = t.to_vec();
        classes.sort_unstable();
        classes.dedup();

        let targets: Vec<usize> = t
            .iter()
            .map(|label| {
                classes
                    .iter()
                    .position(|name| name == label)
                    .ok_or_else(|| Error::MissingColumn(label.clone()))
            })
            .collect::<Result<_>>()?;

        self.weights = vec![vec![0.0; classes.len()]; first.columns.len()];
        self.bias = vec![0.0; classes.len()];
        self.classes = classes;

        let mut losses = Vec::with_capacity(self.epochs);

        for _ in 0..self.epochs {
            let probabilities: Vec<Vec<f64>> =
                rows.iter().map(|row| softmax(&self.scores(row))).collect();

            losses.push(cross_entropy(&probabilities, &targets));

            // 確率 − 正解（正解の品種だけ 1 を引く）
            let errors: Vec<Vec<f64>> = probabilities
                .iter()
                .zip(&targets)
                .map(|(probability, target)| {
                    let mut error = probability.clone();
                    error[*target] -= 1.0;

                    error
                })
                .collect();

            self.update(&rows, &errors);
        }

        self.losses = losses;

        Ok(())
    }

    /// スコアが最大の品種を予測する。
    fn predict(&self, x: &[Features]) -> Result<Vec<String>> {
        if self.classes.is_empty() {
            return Err(Error::NotFitted);
        }

        Ok(x.iter()
            .map(|features| self.classes[argmax(&self.scores(&features.values))].clone())
            .collect())
    }
}

/// 最大の値の位置を返す。同点なら先に現れたほうを選ぶ。
fn argmax(values: &[f64]) -> usize {
    let mut best = 0;

    for (index, value) in values.iter().enumerate() {
        if *value > values[best] {
            best = index;
        }
    }

    best
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
    fn 値がすべて同じなら確率は均等になる() {
        let probabilities = softmax(&[1.0, 1.0, 1.0]);

        for probability in &probabilities {
            assert!((probability - 1.0 / 3.0).abs() < 1e-12);
        }
    }

    #[test]
    fn 値の差が指数の比になる() {
        let probabilities = softmax(&[0.0, 1.0]);

        assert!((probabilities[1] / probabilities[0] - std::f64::consts::E).abs() < 1e-12);
        assert!((probabilities.iter().sum::<f64>() - 1.0).abs() < 1e-12);
    }

    #[test]
    fn 大きな値でもあふれない() {
        // 定義どおり exp(1000) を求めると inf になり、inf / inf が NaN になる
        assert!(1000.0_f64.exp().is_infinite());

        let probabilities = softmax(&[1000.0, 1001.0]);

        assert!(probabilities.iter().all(|value| value.is_finite()));
        assert!((probabilities.iter().sum::<f64>() - 1.0).abs() < 1e-12);
    }

    #[test]
    fn 正解の確率が1なら交差エントロピーは0になる() {
        assert!(cross_entropy(&[vec![1.0, 0.0]], &[0]).abs() < 1e-9);
        assert!(cross_entropy(&[vec![0.5, 0.5]], &[0]) > 0.69);
    }

    #[test]
    fn 二種類のラベルを予測する() {
        let x = vec![column(0.2), column(0.3), column(2.3), column(2.5)];
        let t = labels(&["setosa", "setosa", "virginica", "virginica"]);

        let mut model = LogisticRegression::default();
        model.fit(&x, &t).unwrap();

        assert_eq!(model.predict(&x).unwrap(), t);
        assert_eq!(model.classes(), labels(&["setosa", "virginica"]));
    }

    #[test]
    fn 三種類のラベルを予測する() {
        let x = vec![column(0.2), column(1.3), column(2.5)];
        let t = labels(&["setosa", "versicolor", "virginica"]);

        let mut model = LogisticRegression::default();
        model.fit(&x, &t).unwrap();

        assert_eq!(model.predict(&x).unwrap(), t);
    }

    #[test]
    fn 学習を繰り返すと損失が小さくなる() {
        let x = vec![column(0.2), column(0.3), column(2.3), column(2.5)];
        let t = labels(&["setosa", "setosa", "virginica", "virginica"]);

        let mut model = LogisticRegression::default();
        model.fit(&x, &t).unwrap();

        let losses = model.losses();

        assert_eq!(losses.len(), 5000);
        assert!(losses[0] > losses[losses.len() - 1]);
    }

    #[test]
    fn 学習する前に予測すると失敗する() {
        let model = LogisticRegression::default();

        assert_eq!(
            model.predict(&[column(0.2)]).unwrap_err().to_string(),
            "学習してから予測してください"
        );
    }
}
