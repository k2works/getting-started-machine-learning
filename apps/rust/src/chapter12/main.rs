//! 第 12 章の実行。正則化の強さごとにリッジ回帰を学習し、検証データで選んだモデルと
//! ラッソ回帰の結果を表示する。

use std::io::Write;

use crate::chapter07::r2_score;
use crate::dataset;

use super::Result;
use super::boston::Boston;
use super::linfaelasticnet::{linfa_lasso, linfa_ridge};
use super::selection::{best_experiment, run_ridge_experiments};
use super::{lasso, ridge};

/// テストデータの割合。
pub const TEST_SIZE: f64 = 0.3;
/// 検証データの割合（訓練用のうち）。
pub const VALIDATION_SIZE: f64 = 0.3;
/// 分割の乱数のシード。
pub const SEED: u64 = 0;
/// 試す正則化の強さ。
pub const ALPHAS: [f64; 5] = [0.0, 0.1, 1.0, 10.0, 100.0];
/// ラッソ回帰で試す正則化の強さ。
pub const LASSO_ALPHAS: [f64; 4] = [1.0, 10.0, 50.0, 100.0];

/// 正則化とモデル選択の結果を表示する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let csv_file = dataset::current().join("Boston.csv");
    let data = Boston::prepare(&csv_file, TEST_SIZE, VALIDATION_SIZE, SEED)?;

    writeln!(
        out,
        "データ件数: {}（外れ値 {} 件を除外）",
        data.kept, data.removed
    )?;
    writeln!(
        out,
        "訓練データ: {} 件, 検証データ: {} 件, テストデータ: {} 件",
        data.t_train.len(),
        data.t_valid.len(),
        data.t_test.len()
    )?;
    writeln!(out, "特徴量: {}", data.feature_names.join(", "))?;

    let experiments = run_ridge_experiments(
        &data.feature_names,
        &data.x_train,
        &data.t_train,
        &data.x_valid,
        &data.t_valid,
        &ALPHAS,
    )?;

    writeln!(out, "alpha\t訓練 R²\t検証 R²\t係数の絶対値の合計")?;

    for experiment in &experiments {
        writeln!(
            out,
            "{:.1}\t{:.4}\t{:.4}\t{:.3}",
            experiment.alpha,
            experiment.train_score,
            experiment.validation_score,
            experiment.coefficient_abs_sum
        )?;
    }

    let best = best_experiment(&experiments)?;

    writeln!(out, "検証データで選んだ alpha: {:.1}", best.alpha)?;

    let linear = ridge::fit(&data.feature_names, &data.x_train, &data.t_train, 0.0)?;
    let best_ridge = ridge::fit(
        &data.feature_names,
        &data.x_train,
        &data.t_train,
        best.alpha,
    )?;

    writeln!(
        out,
        "テストデータの決定係数: 線形回帰 {:.4}, リッジ回帰 {:.4}",
        r2_score(&data.t_test, &linear.predict(&data.x_test)?)?,
        r2_score(&data.t_test, &best_ridge.predict(&data.x_test)?)?
    )?;

    // 同じ alpha を linfa に渡して、係数の差が浮動小数点の誤差に収まることを確かめる
    let library_ridge = linfa_ridge(
        &data.feature_names,
        &data.x_train,
        &data.t_train,
        best.alpha,
    )?;

    writeln!(
        out,
        "リッジ回帰の係数の最大の差（自作と linfa）: {:.2e}",
        max_difference(&best_ridge.coefficients, &library_ridge.coefficients)
    )?;

    writeln!(out, "ラッソ回帰（alpha ごとに 0 になった特徴量）")?;

    let mut largest: f64 = 0.0;

    for alpha in LASSO_ALPHAS {
        let own = lasso::fit(&data.feature_names, &data.x_train, &data.t_train, alpha)?;
        let library = linfa_lasso(&data.feature_names, &data.x_train, &data.t_train, alpha)?;

        largest = largest.max(max_difference(&own.coefficients, &library.coefficients));

        writeln!(
            out,
            "  alpha={alpha}: 自作 [{}] / linfa [{}]",
            own.zero_columns().join(", "),
            library.zero_columns().join(", ")
        )?;
    }

    writeln!(
        out,
        "ラッソ回帰の係数の最大の差（自作と linfa）: {largest:.2e}"
    )?;

    Ok(())
}

/// 2 つの係数の並びの、同じ位置どうしの差の最大値。
pub fn max_difference(left: &[f64], right: &[f64]) -> f64 {
    left.iter()
        .zip(right)
        .map(|(a, b)| (a - b).abs())
        .fold(0.0, f64::max)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 試す正則化の強さは五つある() {
        assert_eq!(ALPHAS.len(), 5);
        assert!((ALPHAS[0]).abs() < 1e-12);
        assert!((ALPHAS[4] - 100.0).abs() < 1e-12);
    }

    #[test]
    fn 係数の差の最大値を求める() {
        assert!((max_difference(&[1.0, 2.0], &[1.5, 2.0]) - 0.5).abs() < 1e-12);
    }

    #[test]
    fn 係数が同じなら差は零になる() {
        assert!(max_difference(&[1.0, 2.0], &[1.0, 2.0]).abs() < 1e-12);
    }
}
