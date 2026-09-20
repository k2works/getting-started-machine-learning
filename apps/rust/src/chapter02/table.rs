//! CSV の表と行。セルの文字列を列名で引く。

use std::collections::HashMap;
use std::path::Path;

use super::Error;
use super::Result;

/// CSV の 1 行。セルの文字列を列名で引けるように持つ。空欄は欠損値として扱う。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Row {
    cells: HashMap<String, String>,
}

impl Row {
    /// セルの対応表から行を作る。
    pub fn new(cells: HashMap<String, String>) -> Self {
        Row { cells }
    }

    /// 数値の列を読む。空欄なら `None` を返す。
    /// Go 版は「値と ok」の多値返却で表したが、Rust には `Option` があるので素直に書ける。
    pub fn number(&self, column: &str) -> Result<Option<f64>> {
        let cell = self.text(column)?;

        if cell.trim().is_empty() {
            return Ok(None);
        }

        cell.trim()
            .parse()
            .map(Some)
            .map_err(|_| Error::NotANumber {
                column: column.to_string(),
                value: cell.to_string(),
            })
    }

    /// 文字列の列を読む。列が無ければ失敗を返す。
    pub fn text(&self, column: &str) -> Result<&str> {
        self.cells
            .get(column)
            .map(String::as_str)
            .ok_or_else(|| Error::MissingColumn(column.to_string()))
    }

    /// セルが空欄かどうかを返す。
    pub fn is_missing(&self, column: &str) -> Result<bool> {
        Ok(self.text(column)?.trim().is_empty())
    }
}

/// CSV の表。列の順と行を持つ。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Table {
    pub columns: Vec<String>,
    pub rows: Vec<Row>,
}

/// 列ごとの欠損値の数。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Missing {
    pub column: String,
    pub count: usize,
}

impl Table {
    /// CSV を読み込んで表にする。csv クレートが BOM を取り除くので、自分で消さなくてよい。
    pub fn load(csv_file: &Path) -> Result<Table> {
        let mut reader = csv::Reader::from_path(csv_file)?;
        let columns: Vec<String> = reader.headers()?.iter().map(str::to_string).collect();

        let mut rows = Vec::new();

        for record in reader.records() {
            let record = record?;
            let cells = columns
                .iter()
                .cloned()
                .zip(record.iter().map(str::to_string))
                .collect();

            rows.push(Row::new(cells));
        }

        Ok(Table { columns, rows })
    }

    /// 列ごとに欠損値の数を数える。列の順は表の列の順のまま。
    pub fn count_missing(&self) -> Result<Vec<Missing>> {
        let mut counts = Vec::with_capacity(self.columns.len());

        for column in &self.columns {
            let mut count = 0;

            for row in &self.rows {
                if row.is_missing(column)? {
                    count += 1;
                }
            }

            counts.push(Missing {
                column: column.clone(),
                count,
            });
        }

        Ok(counts)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 列名と値から行を作る。
    pub(super) fn row(pairs: &[(&str, &str)]) -> Row {
        Row::new(
            pairs
                .iter()
                .map(|(name, value)| (name.to_string(), value.to_string()))
                .collect(),
        )
    }

    #[test]
    fn 数値の列を読む() {
        let row = row(&[("がく片長さ", "5.1")]);

        assert_eq!(row.number("がく片長さ").unwrap(), Some(5.1));
    }

    #[test]
    fn 空欄の列は欠損値になる() {
        let row = row(&[("がく片長さ", "")]);

        assert_eq!(row.number("がく片長さ").unwrap(), None);
        assert!(row.is_missing("がく片長さ").unwrap());
    }

    #[test]
    fn 数値として読めない列は失敗する() {
        let row = row(&[("がく片長さ", "たくさん")]);

        assert_eq!(
            row.number("がく片長さ").unwrap_err().to_string(),
            "がく片長さ を数値として読めません: たくさん"
        );
    }

    #[test]
    fn 列が無ければ失敗する() {
        let row = row(&[("がく片長さ", "5.1")]);

        assert_eq!(
            row.text("花弁幅").unwrap_err().to_string(),
            "列がありません: 花弁幅"
        );
    }

    #[test]
    fn 文字列の列を読む() {
        let row = row(&[("種類", "Iris-setosa")]);

        assert_eq!(row.text("種類").unwrap(), "Iris-setosa");
    }

    #[test]
    fn 列ごとに欠損値を数える() {
        let table = Table {
            columns: vec!["がく片長さ".to_string(), "種類".to_string()],
            rows: vec![
                row(&[("がく片長さ", "5.1"), ("種類", "Iris-setosa")]),
                row(&[("がく片長さ", ""), ("種類", "Iris-setosa")]),
                row(&[("がく片長さ", ""), ("種類", "Iris-virginica")]),
            ],
        };

        assert_eq!(
            table.count_missing().unwrap(),
            vec![
                Missing {
                    column: "がく片長さ".to_string(),
                    count: 2
                },
                Missing {
                    column: "種類".to_string(),
                    count: 0
                },
            ]
        );
    }
}
