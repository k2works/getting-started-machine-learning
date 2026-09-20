//! 映画の興行収入のデータ（cinema.csv）の前処理。

use std::path::Path;

use crate::chapter02::{
    Error, Features, Result, Row, Table, TrainTestSplit, column_means, fill_missing,
    split_train_test,
};

/// 特徴量の列。
pub const FEATURES: [&str; 4] = ["SNS1", "SNS2", "actor", "original"];

/// 正解ラベル（興行収入）の列。
pub const TARGET: &str = "sales";

/// SNS2 がこの値を超え、かつ興行収入が `OUTLIER_SALES` 未満の映画を外れ値とする。
const OUTLIER_SNS2: f64 = 1000.0;
const OUTLIER_SALES: f64 = 8500.0;

/// 特徴量の列名をベクタにする。
pub fn feature_columns() -> Vec<String> {
    FEATURES.iter().map(|name| name.to_string()).collect()
}

/// 欠損値を許さずに数値の列を読む。
fn number(row: &Row, column: &str) -> Result<f64> {
    row.number(column)?
        .ok_or_else(|| Error::NoFillValue(column.to_string()))
}

/// 外れ値かどうかを判定する。
fn is_outlier(row: &Row) -> Result<bool> {
    Ok(number(row, "SNS2")? > OUTLIER_SNS2 && number(row, TARGET)? < OUTLIER_SALES)
}

/// 外れ値の行を除いた表を返す。元の表は変えない。
pub fn remove_outliers(table: &Table) -> Result<Table> {
    let mut rows = Vec::with_capacity(table.rows.len());

    for row in &table.rows {
        if !is_outlier(row)? {
            rows.push(row.clone());
        }
    }

    Ok(Table {
        columns: table.columns.clone(),
        rows,
    })
}

/// 外れ値を除き、分割してから訓練データの平均値で両方の欠損値を補完する。
pub fn prepare(
    csv_file: &Path,
    test_size: f64,
    seed: u64,
) -> Result<TrainTestSplit<Features, f64>> {
    let table = remove_outliers(&Table::load(csv_file)?)?;
    let columns = feature_columns();

    let mut t = Vec::with_capacity(table.rows.len());

    for row in &table.rows {
        t.push(number(row, TARGET)?);
    }

    let split = split_train_test(&table.rows, &t, test_size, seed)?;
    let means = column_means(&split.x_train, &columns)?;

    Ok(TrainTestSplit {
        x_train: fill_missing(&split.x_train, &columns, &means)?,
        x_test: fill_missing(&split.x_test, &columns, &means)?,
        t_train: split.t_train,
        t_test: split.t_test,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    /// 列名と値から行を作る。
    fn row(pairs: &[(&str, &str)]) -> Row {
        Row::new(
            pairs
                .iter()
                .map(|(name, value)| (name.to_string(), value.to_string()))
                .collect::<HashMap<String, String>>(),
        )
    }

    /// SNS2 と sales だけを持つ表を作る。
    fn table(pairs: &[(&str, &str)]) -> Table {
        Table {
            columns: vec!["SNS2".to_string(), TARGET.to_string()],
            rows: pairs
                .iter()
                .map(|(sns2, sales)| row(&[("SNS2", sns2), (TARGET, sales)]))
                .collect(),
        }
    }

    #[test]
    fn 話題になったのに売れなかった映画を外れ値として除く() {
        let table = table(&[("1200", "8000"), ("1200", "9000"), ("800", "8000")]);

        let cleaned = remove_outliers(&table).unwrap();

        assert_eq!(cleaned.rows.len(), 2);
        assert_eq!(cleaned.rows[0].text(TARGET).unwrap(), "9000");
        assert_eq!(cleaned.rows[1].text(TARGET).unwrap(), "8000");
    }

    #[test]
    fn 境界の値は外れ値にしない() {
        let table = table(&[("1000", "8000"), ("1200", "8500")]);

        assert_eq!(remove_outliers(&table).unwrap().rows.len(), 2);
    }

    #[test]
    fn 外れ値の判定に使う列が空欄なら失敗する() {
        let table = table(&[("", "8000")]);

        assert_eq!(
            remove_outliers(&table).unwrap_err().to_string(),
            "補完する値がありません: SNS2"
        );
    }
}
