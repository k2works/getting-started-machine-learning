//! 第 2 章の実行。アヤメのデータの前処理の結果を表示する。

use std::io::Write;

use super::Result;
use super::preprocessing::prepare_iris;
use super::table::Table;
use crate::dataset;

/// テストデータの割合。
const TEST_SIZE: f64 = 0.3;
/// 分割の乱数のシード。
const SEED: u64 = 0;

/// アヤメのデータの前処理の結果を表示する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let csv_file = dataset::current().join("iris.csv");
    let table = Table::load(&csv_file)?;
    let counts = table.count_missing()?;
    let split = prepare_iris(&csv_file, TEST_SIZE, SEED)?;

    let formatted: Vec<String> = counts
        .iter()
        .map(|missing| format!("{}={}", missing.column, missing.count))
        .collect();

    writeln!(out, "データ件数: {}", table.rows.len())?;
    writeln!(out, "欠損値の数: {}", formatted.join(", "))?;
    writeln!(
        out,
        "訓練データ: {} 件, テストデータ: {} 件",
        split.x_train.len(),
        split.x_test.len()
    )?;
    writeln!(out, "特徴量: {}", split.x_train[0].columns.join(", "))?;

    Ok(())
}
