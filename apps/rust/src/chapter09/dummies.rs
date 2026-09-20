//! カテゴリ値の列を、カテゴリごとの 0 と 1 の列（ダミー変数）に変える。

use std::collections::HashMap;

use crate::chapter02::{Row, Table};

/// 欠損値（空欄）を除いたカテゴリを辞書順に並べ、先頭を除いて返す
/// （pandas の `get_dummies(drop_first=True)` と同じ）。
pub fn categories(values: &[String]) -> Vec<String> {
    let mut found: Vec<String> = values
        .iter()
        .map(|value| value.trim())
        .filter(|value| !value.is_empty())
        .map(str::to_string)
        .collect();

    found.sort_unstable();
    found.dedup();

    found.into_iter().skip(1).collect()
}

/// 列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。
/// 値が一致すれば "1"、それ以外は "0"。
pub fn encode(table: &Table, column: &str, categories: &[String]) -> Table {
    let dummy_columns: Vec<String> = categories
        .iter()
        .map(|category| format!("{column}_{category}"))
        .collect();

    let columns: Vec<String> = table
        .columns
        .iter()
        .filter(|name| name.as_str() != column)
        .cloned()
        .chain(dummy_columns.iter().cloned())
        .collect();

    let rows = table
        .rows
        .iter()
        .map(|row| encode_row(row, column, categories, &columns))
        .collect();

    Table { columns, rows }
}

/// 1 行分のダミー変数を作る。元の行は変えず、新しい行を返す。
fn encode_row(row: &Row, column: &str, categories: &[String], columns: &[String]) -> Row {
    let value = row.text(column).unwrap_or_default().trim().to_string();
    let mut cells: HashMap<String, String> = HashMap::with_capacity(columns.len());

    for name in columns {
        if let Some(category) = name.strip_prefix(&format!("{column}_")) {
            let hit = categories.iter().any(|known| known == category) && category == value;

            cells.insert(name.clone(), if hit { "1" } else { "0" }.to_string());
        } else if let Ok(cell) = row.text(name) {
            cells.insert(name.clone(), cell.to_string());
        }
    }

    Row::new(cells)
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 文字列のスライスを `Vec<String>` にする。
    fn strings(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    /// CRIME と RM を持つ表を作る。
    fn crime_table(crimes: &[&str]) -> Table {
        Table {
            columns: strings(&["RM", "CRIME"]),
            rows: crimes
                .iter()
                .map(|crime| {
                    Row::new(HashMap::from([
                        ("RM".to_string(), "6.0".to_string()),
                        ("CRIME".to_string(), (*crime).to_string()),
                    ]))
                })
                .collect(),
        }
    }

    #[test]
    fn 先頭を除いたカテゴリを辞書順に返す() {
        assert_eq!(
            categories(&strings(&["low", "high", "very_low", "low"])),
            strings(&["low", "very_low"])
        );
    }

    #[test]
    fn 欠損値はカテゴリに数えない() {
        assert_eq!(
            categories(&strings(&["low", "", "high"])),
            strings(&["low"])
        );
    }

    #[test]
    fn カテゴリごとに0と1の列を作り元の列を取り除く() {
        let encoded = encode(
            &crime_table(&["low", "high", "very_low"]),
            "CRIME",
            &strings(&["low", "very_low"]),
        );

        assert_eq!(
            encoded.columns,
            strings(&["RM", "CRIME_low", "CRIME_very_low"])
        );

        let low: Vec<&str> = encoded
            .rows
            .iter()
            .map(|row| row.text("CRIME_low").unwrap())
            .collect();
        let very_low: Vec<&str> = encoded
            .rows
            .iter()
            .map(|row| row.text("CRIME_very_low").unwrap())
            .collect();

        assert_eq!(low, vec!["1", "0", "0"]);
        assert_eq!(very_low, vec!["0", "0", "1"]);
    }

    #[test]
    fn カテゴリに無い値はすべての列が0になる() {
        let encoded = encode(&crime_table(&["high"]), "CRIME", &strings(&["low"]));

        assert_eq!(encoded.rows[0].text("CRIME_low").unwrap(), "0");
        assert_eq!(encoded.rows[0].text("RM").unwrap(), "6.0");
    }
}
