//! 自転車の利用者数（bike.tsv）と天気（weather.csv）の結合と集計。

use std::collections::HashMap;
use std::path::Path;

use crate::chapter02::{Row, Table};

use super::Result;
use super::delimited::{self, Encoding};

/// 結合のキーになる列。
const KEY: &str = "weather_id";
/// 天気の名前の列。
const WEATHER: &str = "weather";
/// 利用者数の列。
const COUNT: &str = "cnt";

/// タブ区切りの bike.tsv（UTF-8）を読み込む。
pub fn load_bike(tsv_file: &Path) -> Result<Table> {
    delimited::load(tsv_file, Encoding::Utf8, '\t')
}

/// Shift_JIS の weather.csv を読み込む。
pub fn load_weather(csv_file: &Path) -> Result<Table> {
    delimited::load(csv_file, Encoding::ShiftJis, ',')
}

/// 天気 ID をキーにした対応表を引いて、天気の列を加える（内部結合）。
/// 天気の表に無い ID の行は残さない。
pub fn join_weather(bike: &Table, weather: &Table) -> Result<Table> {
    let mut by_id: HashMap<&str, &Row> = HashMap::with_capacity(weather.rows.len());

    for row in &weather.rows {
        by_id.insert(row.text(KEY)?, row);
    }

    let added: Vec<String> = weather
        .columns
        .iter()
        .filter(|column| column.as_str() != KEY)
        .cloned()
        .collect();

    let mut rows = Vec::new();

    for row in &bike.rows {
        let Some(found) = by_id.get(row.text(KEY)?) else {
            continue;
        };

        let mut cells: HashMap<String, String> = HashMap::with_capacity(bike.columns.len());

        for column in &bike.columns {
            cells.insert(column.clone(), row.text(column)?.to_string());
        }

        for column in &added {
            cells.insert(column.clone(), found.text(column)?.to_string());
        }

        rows.push(Row::new(cells));
    }

    let columns = bike.columns.iter().cloned().chain(added).collect();

    Ok(Table { columns, rows })
}

/// 天気ごとの平均利用者数を、多い順に並べて返す。
pub fn mean_count_by_weather(joined: &Table) -> Result<Vec<(String, f64)>> {
    let mut sums: Vec<(String, f64, usize)> = Vec::new();

    for row in &joined.rows {
        let weather = row.text(WEATHER)?.to_string();
        let count = row.number(COUNT)?.unwrap_or(0.0);

        match sums.iter_mut().find(|(name, _, _)| *name == weather) {
            Some(entry) => {
                entry.1 += count;
                entry.2 += 1;
            }
            None => sums.push((weather, count, 1)),
        }
    }

    let mut means: Vec<(String, f64)> = sums
        .into_iter()
        .map(|(name, sum, count)| {
            #[allow(clippy::cast_precision_loss)]
            let mean = sum / count as f64;

            (name, mean)
        })
        .collect();

    means.sort_by(|left, right| right.1.total_cmp(&left.1));

    Ok(means)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn strings(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    fn table(columns: &[&str], rows: &[&[&str]]) -> Table {
        Table {
            columns: strings(columns),
            rows: rows
                .iter()
                .map(|values| {
                    Row::new(
                        columns
                            .iter()
                            .map(|column| (*column).to_string())
                            .zip(values.iter().map(|value| (*value).to_string()))
                            .collect(),
                    )
                })
                .collect(),
        }
    }

    #[test]
    fn 天気の列を加える() {
        let bike = table(&["weather_id", "cnt"], &[&["1", "100"], &["2", "200"]]);
        let weather = table(&["weather_id", "weather"], &[&["1", "晴れ"], &["2", "雨"]]);

        let joined = join_weather(&bike, &weather).unwrap();

        assert_eq!(joined.columns, strings(&["weather_id", "cnt", "weather"]));
        assert_eq!(joined.rows[0].text("weather").unwrap(), "晴れ");
        assert_eq!(joined.rows[1].text("weather").unwrap(), "雨");
    }

    #[test]
    fn 天気の表に無いidの行は残さない() {
        let bike = table(&["weather_id", "cnt"], &[&["1", "100"], &["9", "200"]]);
        let weather = table(&["weather_id", "weather"], &[&["1", "晴れ"]]);

        let joined = join_weather(&bike, &weather).unwrap();

        assert_eq!(joined.rows.len(), 1);
    }

    #[test]
    fn 天気ごとの平均利用者数を多い順に返す() {
        let joined = table(
            &["weather", "cnt"],
            &[&["雨", "100"], &["晴れ", "300"], &["晴れ", "500"]],
        );

        assert_eq!(
            mean_count_by_weather(&joined).unwrap(),
            vec![("晴れ".to_string(), 400.0), ("雨".to_string(), 100.0)]
        );
    }
}
