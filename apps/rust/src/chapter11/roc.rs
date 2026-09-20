//! ROC 曲線と AUC を自作する。閾値を下げながら偽陽性率と真陽性率を追う。

use crate::chapter02::{Error, Result};

/// ROC 曲線の 1 点。`threshold` 以上を正例と予測したときの偽陽性率と真陽性率。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct RocPoint {
    pub threshold: f64,
    /// 偽陽性率（FPR）。負例のうち、誤って正例と予測した割合。
    pub false_positive_rate: f64,
    /// 真陽性率（TPR）。正例のうち、正しく正例と予測できた割合。
    pub true_positive_rate: f64,
}

/// 件数を f64 にする。
#[allow(clippy::cast_precision_loss)]
fn count(value: usize) -> f64 {
    value as f64
}

/// 正例らしさのスコアと正解から ROC 曲線を作る。
///
/// スコアの高い順に 1 件ずつ正例に加えていき、スコアが変わるたびに 1 点を記録する。
/// 先頭は「誰も正例と予測しない」点 (0, 0)、末尾は「全員を正例と予測する」点 (1, 1) になる。
pub fn roc_curve(scores: &[f64], labels: &[bool]) -> Result<Vec<RocPoint>> {
    if scores.len() != labels.len() {
        return Err(Error::LengthMismatch {
            left: scores.len(),
            right: labels.len(),
        });
    }

    let positives = labels.iter().filter(|label| **label).count();
    let negatives = labels.len() - positives;

    if positives == 0 || negatives == 0 {
        return Err(Error::Library(
            "正例と負例が両方ないと ROC 曲線を描けません".to_string(),
        ));
    }

    // スコアの降順。同じスコアなら入力の順のまま（安定ソート）
    let mut order: Vec<usize> = (0..scores.len()).collect();
    order.sort_by(|left, right| scores[*right].total_cmp(&scores[*left]));

    let mut curve = vec![RocPoint {
        threshold: f64::INFINITY,
        false_positive_rate: 0.0,
        true_positive_rate: 0.0,
    }];

    let (mut true_positive, mut false_positive) = (0usize, 0usize);
    let mut previous = f64::INFINITY;

    for index in order {
        // スコアが変わる直前までを 1 つの閾値としてまとめる
        if scores[index] != previous && (true_positive > 0 || false_positive > 0) {
            curve.push(RocPoint {
                threshold: previous,
                false_positive_rate: count(false_positive) / count(negatives),
                true_positive_rate: count(true_positive) / count(positives),
            });
        }

        previous = scores[index];

        if labels[index] {
            true_positive += 1;
        } else {
            false_positive += 1;
        }
    }

    curve.push(RocPoint {
        threshold: previous,
        false_positive_rate: 1.0,
        true_positive_rate: 1.0,
    });

    Ok(curve)
}

/// ROC 曲線の下の面積（AUC）を台形則で求める。
pub fn auc(curve: &[RocPoint]) -> f64 {
    curve
        .windows(2)
        .map(|pair| {
            let width = pair[1].false_positive_rate - pair[0].false_positive_rate;

            width * (pair[0].true_positive_rate + pair[1].true_positive_rate) / 2.0
        })
        .sum()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 完全に分けられればaucは一になる() {
        let curve = roc_curve(&[0.9, 0.8, 0.2, 0.1], &[true, true, false, false]).unwrap();

        assert!((auc(&curve) - 1.0).abs() < 1e-12, "AUC = {}", auc(&curve));
    }

    #[test]
    fn 順が逆ならaucは零になる() {
        let curve = roc_curve(&[0.1, 0.2, 0.8, 0.9], &[true, true, false, false]).unwrap();

        assert!(auc(&curve).abs() < 1e-12, "AUC = {}", auc(&curve));
    }

    #[test]
    fn 混ざっているとaucは中間の値になる() {
        // 正例のスコア 0.9・0.4、負例のスコア 0.6・0.1。組は 4 つで、正例が上なのは 3 つ
        let curve = roc_curve(&[0.9, 0.6, 0.4, 0.1], &[true, false, true, false]).unwrap();

        assert!((auc(&curve) - 0.75).abs() < 1e-12, "AUC = {}", auc(&curve));
    }

    #[test]
    fn 曲線は零から一まで通る() {
        let curve = roc_curve(&[0.9, 0.6, 0.4, 0.1], &[true, false, true, false]).unwrap();

        assert!(curve.first().unwrap().true_positive_rate.abs() < 1e-12);
        assert!((curve.last().unwrap().true_positive_rate - 1.0).abs() < 1e-12);
        assert!((curve.last().unwrap().false_positive_rate - 1.0).abs() < 1e-12);
    }

    #[test]
    fn 正例だけなら曲線を描けない() {
        let error = roc_curve(&[0.9, 0.1], &[true, true]).unwrap_err();

        assert_eq!(
            error.to_string(),
            "ライブラリが失敗しました: 正例と負例が両方ないと ROC 曲線を描けません"
        );
    }

    #[test]
    fn 件数が違えば失敗する() {
        let error = roc_curve(&[0.9], &[true, false]).unwrap_err();

        assert_eq!(error.to_string(), "件数が違います: 1 と 2");
    }
}
