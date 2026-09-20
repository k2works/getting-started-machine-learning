//! 主成分分析のために、ボストンの住宅価格（Boston.csv）のすべての列を前処理する。

use std::path::Path;

use ndarray::Array2;

use crate::chapter02::{self, Features, Table, column_means, fill_missing};
use crate::chapter09::{Standardizer, dummies};

use super::{Error, Result};

/// Boston.csv の前処理をまとめた入れ物。
pub struct BostonPca;

impl BostonPca {
    /// カテゴリ値の列。
    pub const CATEGORY: &'static str = "CRIME";

    /// CRIME をダミー変数にし、欠損値を列の平均値で補完してから、すべての列を標準化する。
    pub fn standardize(table: &Table) -> Result<Vec<Features>> {
        let crimes: Vec<String> = table
            .rows
            .iter()
            .map(|row| row.text(Self::CATEGORY).map(str::to_string))
            .collect::<chapter02::Result<_>>()?;

        let encoded = dummies::encode(table, Self::CATEGORY, &dummies::categories(&crimes));
        let means = column_means(&encoded.rows, &encoded.columns)?;
        let filled = fill_missing(&encoded.rows, &encoded.columns, &means)?;

        Ok(Standardizer::fit(&filled)?.transform(&filled)?)
    }

    /// CSV を読み込んで前処理する。
    pub fn load(csv_file: &Path) -> Result<Vec<Features>> {
        Self::standardize(&Table::load(csv_file)?)
    }

    /// 特徴量のリストを、1 件を 1 行とする行列にする。
    pub fn to_matrix(x: &[Features]) -> Result<Array2<f64>> {
        let columns = x.first().map_or(0, |features| features.values.len());
        let values: Vec<f64> = x
            .iter()
            .flat_map(|features| features.values.clone())
            .collect();

        Array2::from_shape_vec((x.len(), columns), values)
            .map_err(|error| Error::from(chapter02::Error::Library(error.to_string())))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::chapter02::Row;
    use std::collections::HashMap;

    /// CRIME・RM・PRICE を持つ表を作る。
    fn table(rows: &[(&str, &str, &str)]) -> Table {
        Table {
            columns: ["CRIME", "RM", "PRICE"]
                .iter()
                .map(|column| (*column).to_string())
                .collect(),
            rows: rows
                .iter()
                .map(|(crime, rm, price)| {
                    Row::new(HashMap::from([
                        ("CRIME".to_string(), (*crime).to_string()),
                        ("RM".to_string(), (*rm).to_string()),
                        ("PRICE".to_string(), (*price).to_string()),
                    ]))
                })
                .collect(),
        }
    }

    #[test]
    fn カテゴリ値はダミー変数の列になり正解の列も残る() {
        let features = BostonPca::standardize(&table(&[
            ("high", "6.0", "20.0"),
            ("low", "5.0", "10.0"),
            ("low", "7.0", "30.0"),
        ]))
        .unwrap();

        // PRICE も主成分分析の対象にするので、列から外さない
        assert_eq!(features[0].columns, ["RM", "PRICE", "CRIME_low"]);
    }

    #[test]
    fn 欠損値は列の平均値で補う() {
        let features = BostonPca::standardize(&table(&[
            ("high", "6.0", "20.0"),
            ("low", "", "10.0"),
            ("low", "8.0", "30.0"),
        ]))
        .unwrap();

        // RM の平均は 7.0 なので、補った行は標準化すると 0 になる
        assert!(features[1].value("RM").unwrap().abs() < 1e-12);
    }

    #[test]
    fn 特徴量は一件を一行とする行列になる() {
        let features = BostonPca::standardize(&table(&[
            ("high", "6.0", "20.0"),
            ("low", "5.0", "10.0"),
            ("low", "7.0", "30.0"),
        ]))
        .unwrap();

        let matrix = BostonPca::to_matrix(&features).unwrap();

        assert_eq!(matrix.nrows(), 3);
        assert_eq!(matrix.ncols(), 3);
    }
}
