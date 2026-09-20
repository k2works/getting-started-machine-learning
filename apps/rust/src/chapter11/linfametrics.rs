//! linfa の `confusion_matrix`・`roc`・`area_under_curve` で同じことをして、自作と突き合わせる。

use linfa::dataset::Pr;
use linfa::prelude::*;
use linfa_logistic::LogisticRegression as LinfaBinaryLogistic;
use ndarray::{Array1, Array2};

use crate::chapter02::{Error, Features, Result};

/// linfa の混同行列から取り出したスコア。linfa は f32 で返すので f64 に広げて持つ。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct LinfaScores {
    pub accuracy: f64,
    pub precision: f64,
    pub recall: f64,
    pub f1_score: f64,
}

/// 特徴量を linfa に渡す行列にする。
pub fn records(x: &[Features]) -> Result<Array2<f64>> {
    let columns = x.first().map_or(0, |features| features.values.len());
    let values: Vec<f64> = x
        .iter()
        .flat_map(|features| features.values.iter().copied())
        .collect();

    Array2::from_shape_vec((x.len(), columns), values)
        .map_err(|error| Error::Library(error.to_string()))
}

/// linfa の混同行列から正解率・適合率・再現率・F 値を求める。
///
/// linfa はラベルを並べ替えてから、2 値のときだけ逆順にする。つまり「大きいほうのラベル」が
/// 正例になる。Survived は "0" と "1" なので "1"（生存）が正例で、自作と同じ向きになる。
///
/// **呼ぶ向きに注意する。** `confusion_matrix` は「レシーバを行、引数を列」として数える。
/// linfa のドキュメントの例は `prediction.confusion_matrix(&ground_truth)` と書いているが、
/// その向きで呼ぶと行が予測・列が正解になり、`precision()` と `recall()` が入れ替わる。
/// 適合率を適合率として読みたいので、**正解を** レシーバにして呼ぶ。
pub fn linfa_scores(actual: &[String], predicted: &[String]) -> Result<LinfaScores> {
    let truth = Array1::from(actual.to_vec());
    let prediction = Array1::from(predicted.to_vec());

    let matrix = truth
        .confusion_matrix(&prediction)
        .map_err(|error| Error::Library(error.to_string()))?;

    Ok(LinfaScores {
        accuracy: f64::from(matrix.accuracy()),
        precision: f64::from(matrix.precision()),
        recall: f64::from(matrix.recall()),
        f1_score: f64::from(matrix.f1_score()),
    })
}

/// linfa の `roc` と `area_under_curve` で AUC を求める。
///
/// linfa の ROC は確率を `Pr`（0 以上 1 以下の f32 の別名型）で、正解を `&[bool]` で受け取る。
/// `f64` をそのまま渡せないので、`Pr::new` で包み直す。
pub fn linfa_auc(scores: &[f64], labels: &[bool]) -> Result<f64> {
    if scores.len() != labels.len() {
        return Err(Error::LengthMismatch {
            left: scores.len(),
            right: labels.len(),
        });
    }

    #[allow(clippy::cast_possible_truncation)]
    let probabilities: Vec<Pr> = scores.iter().map(|score| Pr::new(*score as f32)).collect();

    let curve = probabilities
        .as_slice()
        .roc(labels)
        .map_err(|error| Error::Library(error.to_string()))?;

    Ok(f64::from(curve.area_under_curve()))
}

/// linfa-logistic の 2 値ロジスティック回帰で、正例らしさの確率を求める。
///
/// ROC 曲線を描くには、ラベルではなく「正例らしさ」の連続値が要る。linfa は件数の多いほうの
/// クラスを正例にするので、`labels().pos.class` を見て、望む向きと違えば確率を裏返す。
pub fn positive_probabilities(
    x_train: &[Features],
    t_train: &[String],
    x_test: &[Features],
    positive: &str,
) -> Result<Vec<f64>> {
    let dataset = Dataset::new(records(x_train)?, Array1::from(t_train.to_vec()));

    let model = LinfaBinaryLogistic::default()
        .max_iterations(1000)
        .alpha(1e-8)
        .fit(&dataset)
        .map_err(|error| Error::Library(error.to_string()))?;

    let probabilities = model.predict_probabilities(&records(x_test)?);
    let flip = model.labels().pos.class != positive;

    Ok(probabilities
        .iter()
        .map(|probability| {
            if flip {
                1.0 - probability
            } else {
                *probability
            }
        })
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn labels(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    #[test]
    fn linfaの混同行列は大きいほうのラベルを正例にする() {
        // 正解 [1 1 1 0 0]、予測 [1 1 0 0 1]。"1" を正例にすると TP=2, FP=1, FN=1
        let scores = linfa_scores(
            &labels(&["1", "1", "1", "0", "0"]),
            &labels(&["1", "1", "0", "0", "1"]),
        )
        .unwrap();

        assert!((scores.precision - 2.0 / 3.0).abs() < 1e-6);
        assert!((scores.recall - 2.0 / 3.0).abs() < 1e-6);
        assert!((scores.accuracy - 0.6).abs() < 1e-6);
    }

    #[test]
    fn linfaの適合率と再現率は自作と一致する() {
        // 正解 [1 1 1 1 0 0]、予測 [1 1 0 0 0 1]。TP=2, FP=1, FN=2 で適合率と再現率が食い違う
        let actual = labels(&["1", "1", "1", "1", "0", "0"]);
        let predicted = labels(&["1", "1", "0", "0", "0", "1"]);
        let scores = linfa_scores(&actual, &predicted).unwrap();
        let matrix =
            super::super::metrics::ConfusionMatrix::of(&actual, &predicted, &"1".to_string())
                .unwrap();

        assert!((scores.precision - matrix.precision()).abs() < 1e-6);
        assert!((scores.recall - matrix.recall()).abs() < 1e-6);
        assert!((scores.precision - 2.0 / 3.0).abs() < 1e-6);
        assert!((scores.recall - 0.5).abs() < 1e-6);
    }

    #[test]
    fn linfaのaucは完全に分けられれば一になる() {
        let auc = linfa_auc(&[0.9, 0.8, 0.2, 0.1], &[true, true, false, false]).unwrap();

        assert!((auc - 1.0).abs() < 1e-6, "AUC = {auc}");
    }

    #[test]
    fn linfaのaucは自作と同じ値になる() {
        let scores = [0.9, 0.6, 0.4, 0.1];
        let truth = [true, false, true, false];
        let curve = super::super::roc::roc_curve(&scores, &truth).unwrap();

        assert!(
            (linfa_auc(&scores, &truth).unwrap() - super::super::roc::auc(&curve)).abs() < 1e-6
        );
    }

    #[test]
    fn 件数が違えばaucを求められない() {
        assert!(linfa_auc(&[0.9], &[true, false]).is_err());
    }
}
