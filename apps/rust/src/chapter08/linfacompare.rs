//! 重みを付けない決定木の分類を、linfa-trees と突き合わせる。
//!
//! 欠損値の補完とダミー変数化は linfa-preprocessing に無いので、自作の前処理で作った
//! 特徴量をそのまま linfa に渡す。突き合わせられるのは決定木の部分だけ。

use crate::chapter02::{Features, Result};
use crate::chapter03::linfatree;

/// linfa の決定木で学習して予測し、整数のラベルに戻す。
pub fn predict(
    x_train: &[Features],
    t_train: &[i32],
    x_test: &[Features],
    max_depth: Option<usize>,
) -> Result<Vec<i32>> {
    let labels: Vec<String> = t_train.iter().map(i32::to_string).collect();
    let predicted = linfatree::predict(x_train, &labels, x_test, max_depth)?;

    Ok(predicted
        .iter()
        .map(|label| label.parse().unwrap_or_default())
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::chapter08::weightedtree::{ClassWeight, DecisionTreeClassifier};

    /// 運賃だけの特徴量を作る。
    fn fare(value: f64) -> Features {
        Features::new(vec!["Fare".to_string()], vec![value]).unwrap()
    }

    #[test]
    fn 重みを付けなければ自作とlinfaの予測は一致する() {
        let x: Vec<Features> = [5.0, 6.0, 70.0, 80.0].iter().map(|v| fare(*v)).collect();
        let t = vec![0, 0, 1, 1];

        let ours = DecisionTreeClassifier::new(Some(1), ClassWeight::None)
            .fit(&x, &t)
            .unwrap()
            .predict(&x)
            .unwrap();
        let theirs = predict(&x, &t, &x, Some(1)).unwrap();

        assert_eq!(ours, t);
        assert_eq!(ours, theirs);
    }
}
