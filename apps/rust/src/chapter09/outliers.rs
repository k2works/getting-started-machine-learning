//! 四分位範囲（IQR）による外れ値の検出。

use crate::chapter02::{Features, TrainTestSplit};

use super::{Error, Result};

/// 外れ値とみなす、四分位数から IQR の何倍離れているか。
pub const DEFAULT_K: f64 = 1.5;

/// 第 1 四分位数の位置。
const FIRST_QUARTILE: f64 = 0.25;
/// 第 3 四分位数の位置。
const THIRD_QUARTILE: f64 = 0.75;

/// 分位数を求める。位置が値の間にあれば前後の値から線形補間する
/// （pandas の `quantile` の既定と同じ）。
pub fn quantile(values: &[f64], q: f64) -> Result<f64> {
    if values.is_empty() {
        return Err(Error::Empty("値"));
    }

    let mut sorted = values.to_vec();
    sorted.sort_by(f64::total_cmp);

    #[allow(clippy::cast_precision_loss)]
    let position = (sorted.len() - 1) as f64 * q;

    #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)]
    let lower = position.floor() as usize;
    #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)]
    let upper = position.ceil() as usize;

    #[allow(clippy::cast_precision_loss)]
    let fraction = position - lower as f64;

    Ok(sorted[lower] + (sorted[upper] - sorted[lower]) * fraction)
}

/// 第 1 四分位数から IQR の k 倍より小さい値と、第 3 四分位数から k 倍より大きい値を外れ値とする。
pub fn iqr_outliers(values: &[f64], k: f64) -> Result<Vec<bool>> {
    let q1 = quantile(values, FIRST_QUARTILE)?;
    let q3 = quantile(values, THIRD_QUARTILE)?;
    let iqr = q3 - q1;

    Ok(values
        .iter()
        .map(|value| *value < q1 - k * iqr || *value > q3 + k * iqr)
        .collect())
}

/// 訓練データから、正解の値が外れ値の行を取り除く。テストデータはそのまま残す。
pub fn remove_target_outliers(
    split: &TrainTestSplit<Features, f64>,
) -> Result<TrainTestSplit<Features, f64>> {
    let outliers = iqr_outliers(&split.t_train, DEFAULT_K)?;

    let kept: Vec<usize> = outliers
        .iter()
        .enumerate()
        .filter(|(_, outlier)| !**outlier)
        .map(|(index, _)| index)
        .collect();

    Ok(TrainTestSplit {
        x_train: kept
            .iter()
            .map(|index| split.x_train[*index].clone())
            .collect(),
        x_test: split.x_test.clone(),
        t_train: kept.iter().map(|index| split.t_train[*index]).collect(),
        t_test: split.t_test.clone(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 分位数を線形補間で求める() {
        let values = vec![1.0, 2.0, 3.0, 4.0];

        // 位置 = 3 * 0.25 = 0.75 なので、1 と 2 の間を 0.75 の割合で補間する
        assert!((quantile(&values, 0.25).unwrap() - 1.75).abs() < 1e-12);
        assert!((quantile(&values, 0.75).unwrap() - 3.25).abs() < 1e-12);
        assert!((quantile(&values, 0.5).unwrap() - 2.5).abs() < 1e-12);
    }

    #[test]
    fn 値が無ければ分位数を求められない() {
        assert_eq!(
            quantile(&[], 0.5).unwrap_err().to_string(),
            "値が 1 件もありません"
        );
    }

    #[test]
    fn 四分位範囲から離れた値を外れ値とする() {
        let values = vec![1.0, 2.0, 3.0, 4.0, 100.0];

        assert_eq!(
            iqr_outliers(&values, DEFAULT_K).unwrap(),
            vec![false, false, false, false, true]
        );
    }

    #[test]
    fn 外れ値の無いデータでは何も検出しない() {
        let values = vec![1.0, 2.0, 3.0, 4.0];

        assert_eq!(
            iqr_outliers(&values, DEFAULT_K).unwrap(),
            vec![false, false, false, false]
        );
    }

    #[test]
    fn 訓練データから外れ値の行だけを取り除く() {
        let features = |value: f64| Features::new(vec!["RM".to_string()], vec![value]).unwrap();
        let split = TrainTestSplit {
            x_train: vec![
                features(1.0),
                features(2.0),
                features(3.0),
                features(4.0),
                features(5.0),
            ],
            x_test: vec![features(9.0)],
            t_train: vec![1.0, 2.0, 3.0, 4.0, 100.0],
            t_test: vec![100.0],
        };

        let removed = remove_target_outliers(&split).unwrap();

        assert_eq!(removed.t_train, vec![1.0, 2.0, 3.0, 4.0]);
        assert_eq!(removed.x_train.len(), 4);
        // テストデータは外れ値を残したまま
        assert_eq!(removed.t_test, vec![100.0]);
    }
}
