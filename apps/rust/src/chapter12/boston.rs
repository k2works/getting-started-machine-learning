//! ボストンの住宅価格（Boston.csv）を、外れ値を除いて訓練・検証・テストの 3 つに分ける。

use std::path::Path;

use crate::chapter02::{self, Features, Row, Table, split_train_test};

use super::Result;
use super::scaler::PolynomialScaler;

/// 訓練・検証・テストの特徴量と正解。
#[derive(Debug, Clone, PartialEq)]
pub struct Dataset {
    pub x_train: Vec<Vec<f64>>,
    pub t_train: Vec<f64>,
    pub x_valid: Vec<Vec<f64>>,
    pub t_valid: Vec<f64>,
    pub x_test: Vec<Vec<f64>>,
    pub t_test: Vec<f64>,
    pub feature_names: Vec<String>,
    /// 外れ値を除いたあとの件数。
    pub kept: usize,
    /// 外れ値として除いた件数。
    pub removed: usize,
}

/// Boston.csv の前処理をまとめた入れ物。
pub struct Boston;

/// 欠損値を許さずに数値の列を読む。
fn number(row: &Row, column: &str) -> chapter02::Result<f64> {
    row.number(column)?
        .ok_or_else(|| chapter02::Error::NoFillValue(column.to_string()))
}

impl Boston {
    /// 特徴量の列。
    pub const FEATURES: [&'static str; 3] = ["RM", "PTRATIO", "LSTAT"];
    /// 正解の列。
    pub const TARGET: &'static str = "PRICE";
    /// z スコアの絶対値がこの値を超える行を外れ値とする。
    pub const OUTLIER_THRESHOLD: f64 = 3.0;

    /// 特徴量の列名をベクタにする。
    pub fn feature_columns() -> Vec<String> {
        Self::FEATURES.iter().map(|name| name.to_string()).collect()
    }

    /// 列ごとの z スコア（標準偏差は件数 n - 1 で割る標本標準偏差）の絶対値が
    /// `threshold` を超える値を 1 つでも持つ行を除く。
    pub fn remove_outliers(table: &Table, columns: &[String], threshold: f64) -> Result<Table> {
        let mut means = Vec::with_capacity(columns.len());
        let mut stds = Vec::with_capacity(columns.len());

        for column in columns {
            let values: Vec<f64> = table
                .rows
                .iter()
                .map(|row| number(row, column))
                .collect::<chapter02::Result<_>>()?;

            #[allow(clippy::cast_precision_loss)]
            let size = values.len() as f64;
            let mean = values.iter().sum::<f64>() / size;
            let variance = values
                .iter()
                .map(|value| (value - mean) * (value - mean))
                .sum::<f64>()
                / (size - 1.0);

            means.push(mean);
            stds.push(variance.sqrt());
        }

        let mut rows = Vec::with_capacity(table.rows.len());

        for row in &table.rows {
            let mut outlier = false;

            for (index, column) in columns.iter().enumerate() {
                if ((number(row, column)? - means[index]) / stds[index]).abs() > threshold {
                    outlier = true;
                }
            }

            if !outlier {
                rows.push(row.clone());
            }
        }

        Ok(Table {
            columns: table.columns.clone(),
            rows,
        })
    }

    /// 外れ値を除き、全体を訓練用とテスト用に、訓練用をさらに訓練データと検証データに分ける。
    ///
    /// 標準化の平均と標準偏差は訓練データだけから求め、3 つとも同じ値で変換する。
    pub fn prepare(
        csv_file: &Path,
        test_size: f64,
        validation_size: f64,
        seed: u64,
    ) -> Result<Dataset> {
        let table = Table::load(csv_file)?;
        let columns = Self::feature_columns();

        let mut all = columns.clone();
        all.push(Self::TARGET.to_string());

        let kept_table = Self::remove_outliers(&table, &all, Self::OUTLIER_THRESHOLD)?;
        let removed = table.rows.len() - kept_table.rows.len();

        let mut x = Vec::with_capacity(kept_table.rows.len());
        let mut t = Vec::with_capacity(kept_table.rows.len());

        for row in &kept_table.rows {
            let values = columns
                .iter()
                .map(|column| number(row, column))
                .collect::<chapter02::Result<Vec<f64>>>()?;

            x.push(Features::new(columns.clone(), values)?);
            t.push(number(row, Self::TARGET)?);
        }

        let outer = split_train_test(&x, &t, test_size, seed)?;
        let inner = split_train_test(&outer.x_train, &outer.t_train, validation_size, seed)?;

        let scaler = PolynomialScaler::fit(&inner.x_train)?;

        Ok(Dataset {
            x_train: scaler.transform(&inner.x_train)?,
            t_train: inner.t_train,
            x_valid: scaler.transform(&inner.x_test)?,
            t_valid: inner.t_test,
            x_test: scaler.transform(&outer.x_test)?,
            t_test: outer.t_test,
            feature_names: scaler.feature_names(),
            kept: kept_table.rows.len(),
            removed,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    fn strings(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    fn row(rm: &str, price: &str) -> Row {
        Row::new(
            [
                ("RM".to_string(), rm.to_string()),
                ("PRICE".to_string(), price.to_string()),
            ]
            .into_iter()
            .collect::<HashMap<String, String>>(),
        )
    }

    /// RM がほとんど 6 前後で、1 行だけ極端に大きい架空の表。
    fn table() -> Table {
        let mut rows: Vec<Row> = (0..20).map(|_| row("6", "20")).collect();
        rows.push(row("60", "20"));

        Table {
            columns: strings(&["RM", "PRICE"]),
            rows,
        }
    }

    #[test]
    fn 極端に離れた行が外れ値として除かれる() {
        let kept = Boston::remove_outliers(&table(), &strings(&["RM"]), 3.0).unwrap();

        assert_eq!(kept.rows.len(), 20);
    }

    #[test]
    fn 閾値を上げれば除かれない() {
        let kept = Boston::remove_outliers(&table(), &strings(&["RM"]), 10.0).unwrap();

        assert_eq!(kept.rows.len(), 21);
    }

    #[test]
    fn 特徴量の列は三つある() {
        assert_eq!(
            Boston::feature_columns(),
            strings(&["RM", "PTRATIO", "LSTAT"])
        );
    }
}
