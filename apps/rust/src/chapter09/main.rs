//! 第 9 章の実行。特徴量の組ごとの決定係数と、天気ごとの平均利用者数を表示する。

use std::io::Write;

use crate::chapter02::{Features, TrainTestSplit};
use crate::dataset;

use super::boston::{Boston, Scores};
use super::polynomial::{Pair, pairs_with_replacement};
use super::standardizer::Standardizer;
use super::{Result, bikeweather, outliers};

/// テストデータの割合。
const TEST_SIZE: f64 = 0.3;
/// 分割の乱数のシード。
const SEED: u64 = 0;
/// 多項式特徴量を作る元の列。
const COLUMNS: [&str; 3] = ["RM", "LSTAT", "PTRATIO"];
/// 2 乗の項。
const SQUARES: [&str; 3] = ["RM^2", "LSTAT^2", "PTRATIO^2"];

/// 特徴量の組の名前と、使う項。
pub fn feature_sets() -> Vec<(String, Vec<String>)> {
    let columns = strings(&COLUMNS);
    let interactions: Vec<String> = pairs_with_replacement(&columns)
        .iter()
        .map(Pair::name)
        .collect();

    vec![
        ("元の特徴量".to_string(), columns.clone()),
        (
            "2 乗の項を追加".to_string(),
            concat(&columns, &strings(&SQUARES)),
        ),
        (
            "交互作用の項も追加".to_string(),
            concat(&columns, &interactions),
        ),
    ]
}

/// 特徴量の組ごとの決定係数と、天気ごとの平均利用者数を表示する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let data_dir = dataset::current();
    let split = Boston::prepare(&data_dir.join("Boston.csv"), TEST_SIZE, SEED)?;
    let columns = strings(&COLUMNS);

    writeln!(
        out,
        "訓練データ: {} 件, テストデータ: {} 件",
        split.x_train.len(),
        split.x_test.len()
    )?;
    writeln!(out, "特徴量の列: {}", split.x_train[0].columns.join(", "))?;

    // 標準化した訓練データにもう一度 fit して、平均 0・標準偏差 1 になったことを確かめる
    let check = Standardizer::fit(&Standardizer::fit(&split.x_train)?.transform(&split.x_train)?)?;

    writeln!(
        out,
        "標準化した訓練データの RM: 平均 {:.2}, 標準偏差 {:.2}",
        zeroed(check.mean("RM")?),
        zeroed(check.std("RM")?)
    )?;

    writeln!(out, "決定係数:")?;

    for (name, terms) in feature_sets() {
        let scores = Boston::score_feature_set(&split, &columns, &terms)?;

        writeln!(
            out,
            "  {name}（{} 列）: {}",
            terms.len(),
            format_scores(&scores)
        )?;
    }

    let outlier_count = outliers::iqr_outliers(&split.t_train, outliers::DEFAULT_K)?
        .iter()
        .filter(|outlier| **outlier)
        .count();

    writeln!(out, "訓練データの PRICE の外れ値: {outlier_count} 件")?;

    let removed = removed_scores(&split, &columns)?;

    writeln!(
        out,
        "  外れ値を除いて 2 乗の項を追加: {}",
        format_scores(&removed)
    )?;

    let joined = bikeweather::join_weather(
        &bikeweather::load_bike(&data_dir.join("bike.tsv"))?,
        &bikeweather::load_weather(&data_dir.join("weather.csv"))?,
    )?;

    let means: Vec<String> = bikeweather::mean_count_by_weather(&joined)?
        .iter()
        .map(|(weather, mean)| format!("{weather}={mean:.1}"))
        .collect();

    writeln!(out, "天気ごとの平均利用者数: {}", means.join(", "))?;

    Ok(())
}

/// 外れ値を除いてから、2 乗の項を加えた決定係数を求める。
fn removed_scores(split: &TrainTestSplit<Features, f64>, columns: &[String]) -> Result<Scores> {
    let removed = outliers::remove_target_outliers(split)?;

    Boston::score_feature_set(&removed, columns, &concat(columns, &strings(&SQUARES)))
}

/// 文字列のスライスを `Vec<String>` にする。
fn strings(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| (*value).to_string()).collect()
}

/// 2 つの列名のリストをつなげる。
fn concat(first: &[String], second: &[String]) -> Vec<String> {
    first.iter().chain(second).cloned().collect()
}

/// 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす。
fn zeroed(value: f64) -> f64 {
    if value.abs() < 1e-9 { 0.0 } else { value }
}

/// 決定係数を 1 行にまとめる。
fn format_scores(scores: &Scores) -> String {
    format!(
        "訓練 {:.4}, テスト {:.4}",
        zeroed(scores.train),
        zeroed(scores.test)
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 特徴量の組は3つある() {
        let sets = feature_sets();

        assert_eq!(sets.len(), 3);
        assert_eq!(sets[0].1.len(), 3);
        assert_eq!(sets[1].1.len(), 6);
        assert_eq!(sets[2].1.len(), 9);
    }

    #[test]
    fn ごく小さい値は0として表示する() {
        assert!(zeroed(-1e-15).is_sign_positive());
        assert!((zeroed(-0.5) + 0.5).abs() < 1e-12);
    }
}
