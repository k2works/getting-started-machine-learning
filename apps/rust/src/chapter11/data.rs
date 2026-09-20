//! 交差検証に渡す特徴量と正解ラベル。
//!
//! この章では分割の前に全体の平均値で補完する、簡略化した前処理を使う。分割ごとに補完し直すのが
//! 本来だが、交差検証そのものを見せるために手順を短くしている。

use std::path::Path;

use crate::chapter02::{Error, Features, Result, Row, Table, column_means, fill_missing};
use crate::chapter07::cinema;

/// Survived.csv の特徴量の列。
pub const SURVIVED_FEATURES: [&str; 3] = ["Pclass", "Age", "male"];
/// Survived.csv の正解の列。
pub const SURVIVED_TARGET: &str = "Survived";
/// 生存（正例）を表すラベル。
pub const SURVIVED_POSITIVE: &str = "1";

/// 交差検証にかける特徴量と正解ラベル。
#[derive(Debug, Clone, PartialEq)]
pub struct Dataset<T> {
    pub x: Vec<Features>,
    pub t: Vec<T>,
}

/// 欠損値を許さずに数値の列を読む。
fn number(row: &Row, column: &str) -> Result<f64> {
    row.number(column)?
        .ok_or_else(|| Error::NoFillValue(column.to_string()))
}

/// 客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、Survived の文字列を正解ラベルにする。
/// 年齢の欠損値は全体の平均値で補う。
pub fn prepare_survived(table: &Table) -> Result<Dataset<String>> {
    let age_column = vec!["Age".to_string()];
    let age_mean = *column_means(&table.rows, &age_column)?
        .get("Age")
        .ok_or_else(|| Error::MissingColumn("Age".to_string()))?;

    let columns: Vec<String> = SURVIVED_FEATURES
        .iter()
        .map(|name| name.to_string())
        .collect();

    let mut x = Vec::with_capacity(table.rows.len());
    let mut t = Vec::with_capacity(table.rows.len());

    for row in &table.rows {
        let values = vec![
            number(row, "Pclass")?,
            row.number("Age")?.unwrap_or(age_mean),
            f64::from(u8::from(row.text("Sex")? == "male")),
        ];

        x.push(Features::new(columns.clone(), values)?);
        t.push(row.text(SURVIVED_TARGET)?.to_string());
    }

    Ok(Dataset { x, t })
}

/// 第 7 章の 4 列を特徴量に、興行収入を正解にする。欠損値は列ごとの平均値で補う。
pub fn prepare_cinema(table: &Table) -> Result<Dataset<f64>> {
    let columns = cinema::feature_columns();
    let means = column_means(&table.rows, &columns)?;
    let x = fill_missing(&table.rows, &columns, &means)?;

    let mut t = Vec::with_capacity(table.rows.len());

    for row in &table.rows {
        t.push(number(row, cinema::TARGET)?);
    }

    Ok(Dataset { x, t })
}

/// CSV を読んで Survived の特徴量と正解にする。
pub fn load_survived(csv_file: &Path) -> Result<Dataset<String>> {
    prepare_survived(&Table::load(csv_file)?)
}

/// CSV を読んで cinema の特徴量と正解にする。
pub fn load_cinema(csv_file: &Path) -> Result<Dataset<f64>> {
    prepare_cinema(&Table::load(csv_file)?)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    fn row(cells: &[(&str, &str)]) -> Row {
        Row::new(
            cells
                .iter()
                .map(|(key, value)| ((*key).to_string(), (*value).to_string()))
                .collect::<HashMap<String, String>>(),
        )
    }

    /// 年齢が 1 件だけ空欄の架空の表。
    fn survived_table() -> Table {
        Table {
            columns: vec![
                "Survived".to_string(),
                "Pclass".to_string(),
                "Age".to_string(),
                "Sex".to_string(),
            ],
            rows: vec![
                row(&[
                    ("Survived", "1"),
                    ("Pclass", "1"),
                    ("Age", "20"),
                    ("Sex", "female"),
                ]),
                row(&[
                    ("Survived", "0"),
                    ("Pclass", "3"),
                    ("Age", "40"),
                    ("Sex", "male"),
                ]),
                row(&[
                    ("Survived", "0"),
                    ("Pclass", "3"),
                    ("Age", ""),
                    ("Sex", "male"),
                ]),
            ],
        }
    }

    #[test]
    fn 性別は男性なら一になる() {
        let data = prepare_survived(&survived_table()).unwrap();

        assert!((data.x[0].value("male").unwrap() - 0.0).abs() < 1e-12);
        assert!((data.x[1].value("male").unwrap() - 1.0).abs() < 1e-12);
    }

    #[test]
    fn 年齢の欠損値は平均値で補われる() {
        let data = prepare_survived(&survived_table()).unwrap();

        assert!((data.x[2].value("Age").unwrap() - 30.0).abs() < 1e-12);
    }

    #[test]
    fn 正解ラベルは文字列のまま取り出される() {
        let data = prepare_survived(&survived_table()).unwrap();

        assert_eq!(data.t, vec!["1", "0", "0"]);
    }
}
