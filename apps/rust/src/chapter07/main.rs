//! 第 7 章の実行。映画の興行収入を線形回帰で予測し、係数と評価指標を表示する。

use std::io::Write;

use super::cinema;
use super::linearregression::{self, LinearModel};
use super::linfareg;
use super::metrics;
use crate::chapter02::{Result, Table};
use crate::dataset;

/// テストデータの割合。
const TEST_SIZE: f64 = 0.2;
/// 分割の乱数のシード。
const SEED: u64 = 0;

/// 映画の興行収入を線形回帰で予測し、係数と評価指標を表示する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let csv_file = dataset::current().join("cinema.csv");
    let table = Table::load(&csv_file)?;
    let cleaned = cinema::remove_outliers(&table)?;
    let split = cinema::prepare(&csv_file, TEST_SIZE, SEED)?;

    let model = linearregression::fit(&split.x_train, &split.t_train)?;
    let library = linfareg::fit(&split.x_train, &split.t_train)?;
    let y = model.predict(&split.x_test)?;

    writeln!(out, "データ件数: {}", table.rows.len())?;
    writeln!(out, "外れ値を除いた件数: {}", cleaned.rows.len())?;
    writeln!(
        out,
        "訓練データ: {} 件, テストデータ: {} 件",
        split.x_train.len(),
        split.x_test.len()
    )?;
    writeln!(out, "切片: {:.2}", model.intercept)?;
    writeln!(out, "係数: {}", format_coefficients(&model))?;
    writeln!(
        out,
        "linfa の切片: {:.2}, 係数: {}",
        library.intercept,
        format_coefficients(&library)
    )?;
    writeln!(
        out,
        "テストデータの評価: R2={:.4}, MAE={:.2}, RMSE={:.2}",
        metrics::r2_score(&split.t_test, &y)?,
        metrics::mean_absolute_error(&split.t_test, &y)?,
        metrics::root_mean_squared_error(&split.t_test, &y)?
    )?;

    Ok(())
}

/// 係数を「列名=値」の並びにする。
fn format_coefficients(model: &LinearModel) -> String {
    model
        .columns
        .iter()
        .zip(&model.coefficients)
        .map(|(column, coefficient)| format!("{column}={coefficient:.4}"))
        .collect::<Vec<String>>()
        .join(", ")
}
