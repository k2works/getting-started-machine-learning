//! 混同行列と、そこから求める分類の評価指標。

use crate::chapter02::{Error, Result};

/// 正解と予測から 1 つのスコアを求める評価関数。
///
/// Java 版は `@FunctionalInterface` を 1 つ宣言してメソッド参照を渡したが、Rust では
/// 「呼べるもの」を `Box<dyn Fn ...>` で持つ。正例のラベルを捕まえた閉包も同じ型に入る。
pub type Metric<T> = Box<dyn Fn(&[T], &[T]) -> Result<f64>>;

/// 2 値分類の混同行列。正例（見つけたいほう）を決めて、予測の当たり外れを 4 つに分けて数える。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ConfusionMatrix {
    /// 実際は正例で、正例と予測した件数（真陽性）。
    pub true_positive: usize,
    /// 実際は負例で、正例と予測した件数（偽陽性）。
    pub false_positive: usize,
    /// 実際は正例で、負例と予測した件数（偽陰性）。
    pub false_negative: usize,
    /// 実際は負例で、負例と予測した件数（真陰性）。
    pub true_negative: usize,
}

/// 件数が同じでなければ失敗を返す。短いほうに合わせて黙って切り詰めない。
fn require_same_size<T>(actual: &[T], predicted: &[T]) -> Result<()> {
    if actual.len() != predicted.len() {
        return Err(Error::LengthMismatch {
            left: actual.len(),
            right: predicted.len(),
        });
    }

    Ok(())
}

/// 件数を f64 にする。評価指標の割り算に使う。
#[allow(clippy::cast_precision_loss)]
fn count(value: usize) -> f64 {
    value as f64
}

/// 分母が 0 なら 0 を返す割り算。
fn ratio(numerator: f64, denominator: f64) -> f64 {
    if denominator == 0.0 {
        0.0
    } else {
        numerator / denominator
    }
}

impl ConfusionMatrix {
    /// 正解と予測を 1 件ずつ比べて数える。`positive` と等しいラベルを正例、それ以外を負例とする。
    pub fn of<T: PartialEq>(
        actual: &[T],
        predicted: &[T],
        positive: &T,
    ) -> Result<ConfusionMatrix> {
        require_same_size(actual, predicted)?;

        let mut matrix = ConfusionMatrix {
            true_positive: 0,
            false_positive: 0,
            false_negative: 0,
            true_negative: 0,
        };

        for (truth, prediction) in actual.iter().zip(predicted) {
            // 「実際が正例か」「予測が正例か」の 2 つの bool の組を match で 4 通り全部書く。
            // どれか 1 つを書き忘れるとコンパイルが通らないので、数え落としが起きない
            match (truth == positive, prediction == positive) {
                (true, true) => matrix.true_positive += 1,
                (false, true) => matrix.false_positive += 1,
                (true, false) => matrix.false_negative += 1,
                (false, false) => matrix.true_negative += 1,
            }
        }

        Ok(matrix)
    }

    /// 適合率。正例と予測したうち、本当に正例だった割合。
    pub fn precision(&self) -> f64 {
        ratio(
            count(self.true_positive),
            count(self.true_positive + self.false_positive),
        )
    }

    /// 再現率。本当の正例のうち、正例と予測できた割合。
    pub fn recall(&self) -> f64 {
        ratio(
            count(self.true_positive),
            count(self.true_positive + self.false_negative),
        )
    }

    /// F 値。適合率と再現率の調和平均。
    pub fn f1_score(&self) -> f64 {
        let precision = self.precision();
        let recall = self.recall();

        ratio(2.0 * precision * recall, precision + recall)
    }

    /// 正解率。対角の 2 つを全体で割った値。
    pub fn accuracy(&self) -> f64 {
        ratio(
            count(self.true_positive + self.true_negative),
            count(
                self.true_positive + self.false_positive + self.false_negative + self.true_negative,
            ),
        )
    }
}

/// 正解率。正解と予測が一致した割合。
pub fn accuracy<T: PartialEq>(actual: &[T], predicted: &[T]) -> Result<f64> {
    require_same_size(actual, predicted)?;

    if actual.is_empty() {
        return Ok(0.0);
    }

    let hits = actual
        .iter()
        .zip(predicted)
        .filter(|(truth, prediction)| truth == prediction)
        .count();

    Ok(count(hits) / count(actual.len()))
}

/// 混同行列から求める指標を、正例を決めて評価関数に変える。
///
/// 返す閉包は `positive` を持ち去る（move する）ので、呼び出し側の変数より長生きできる。
pub fn classification_metric<T: PartialEq + Clone + 'static>(
    score: fn(&ConfusionMatrix) -> f64,
    positive: T,
) -> Metric<T> {
    Box::new(move |actual, predicted| {
        Ok(score(&ConfusionMatrix::of(actual, predicted, &positive)?))
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn labels(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    /// 正解 [1 1 1 0 0]、予測 [1 1 0 0 1] の混同行列。TP=2, FN=1, TN=1, FP=1。
    fn sample() -> ConfusionMatrix {
        ConfusionMatrix::of(
            &labels(&["1", "1", "1", "0", "0"]),
            &labels(&["1", "1", "0", "0", "1"]),
            &"1".to_string(),
        )
        .unwrap()
    }

    #[test]
    fn 混同行列は四つの数を数える() {
        let matrix = sample();

        assert_eq!(matrix.true_positive, 2);
        assert_eq!(matrix.false_negative, 1);
        assert_eq!(matrix.false_positive, 1);
        assert_eq!(matrix.true_negative, 1);
    }

    #[test]
    fn 適合率と再現率とf値を求める() {
        let matrix = sample();

        assert!((matrix.precision() - 2.0 / 3.0).abs() < 1e-12);
        assert!((matrix.recall() - 2.0 / 3.0).abs() < 1e-12);
        assert!((matrix.f1_score() - 2.0 / 3.0).abs() < 1e-12);
        assert!((matrix.accuracy() - 3.0 / 5.0).abs() < 1e-12);
    }

    #[test]
    fn 正例と予測した件数が零なら適合率は零になる() {
        let matrix =
            ConfusionMatrix::of(&labels(&["1", "0"]), &labels(&["0", "0"]), &"1".to_string())
                .unwrap();

        assert!(matrix.precision().abs() < 1e-12);
        assert!(matrix.f1_score().abs() < 1e-12);
    }

    #[test]
    fn 件数が違えば失敗する() {
        let error = ConfusionMatrix::of(&labels(&["1"]), &labels(&["1", "0"]), &"1".to_string())
            .unwrap_err();

        assert_eq!(error.to_string(), "件数が違います: 1 と 2");
    }

    #[test]
    fn 正解率は一致した割合になる() {
        let score = accuracy(
            &labels(&["1", "1", "0", "0"]),
            &labels(&["1", "0", "0", "0"]),
        )
        .unwrap();

        assert!((score - 0.75).abs() < 1e-12);
    }

    #[test]
    fn 評価関数にすると正解と予測から直に採点できる() {
        let metric = classification_metric(ConfusionMatrix::precision, "1".to_string());
        let score = metric(
            &labels(&["1", "1", "1", "0", "0"]),
            &labels(&["1", "1", "0", "0", "1"]),
        )
        .unwrap();

        assert!((score - 2.0 / 3.0).abs() < 1e-12);
    }
}
