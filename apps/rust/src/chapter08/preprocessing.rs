//! 前処理の部品。欠損値の補完とダミー変数化は linfa-preprocessing に無いので自作する。
//!
//! Java 版はインターフェースと `record` で表したが、学習済みの前処理を JSON に保存するので、
//! trait object（`Box<dyn FittedTransformer>`）ではなく列挙型にする。
//! `Deserialize` は「どの型に戻すか」をコンパイル時に決める必要があり、trait object には戻せない。

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::chapter02::{Error, Result, Row, Table};

/// 学習前の前処理。`fit` で訓練データから必要な値を求める。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Step {
    /// 数値の列の欠損値を、同じグループ（`by` の列の値の組）の中央値で補完する。
    GroupMedian { column: String, by: Vec<String> },
    /// 文字列の列の欠損値を、訓練データで最も多い値（最頻値）で補完する。
    MostFrequent { column: String },
    /// カテゴリ値の列を、最初のカテゴリを除いた 0 と 1 の列（ダミー変数）にする。
    Dummy { columns: Vec<String> },
}

/// 学習済みの前処理。`fit` で求めた値を持ち、どのデータにも同じ変換をする。
///
/// `Vec<(Vec<String>, f64)>` で中央値を持つのは、JSON のオブジェクトのキーが文字列に限られ、
/// グループ（列の値の組）をそのままキーにできないため。並びも保てる。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum FittedStep {
    GroupMedian {
        column: String,
        by: Vec<String>,
        medians: Vec<(Vec<String>, f64)>,
        overall: f64,
    },
    MostFrequent {
        column: String,
        most_frequent: String,
    },
    Dummy {
        dummies: Vec<(String, Vec<String>)>,
    },
}

impl Step {
    /// 訓練データから変換に必要な値を求める。
    pub fn fit(&self, x: &Table) -> Result<FittedStep> {
        match self {
            Step::GroupMedian { column, by } => fit_group_median(x, column, by),
            Step::MostFrequent { column } => fit_most_frequent(x, column),
            Step::Dummy { columns } => fit_dummy(x, columns),
        }
    }
}

impl FittedStep {
    /// 求めた値を使ってデータを変換する。元の表は変えない。
    pub fn transform(&self, x: &Table) -> Result<Table> {
        match self {
            FittedStep::GroupMedian {
                column,
                by,
                medians,
                overall,
            } => fill(x, column, |row| {
                let group = group_of(row, by)?;
                let median = medians
                    .iter()
                    .find(|(key, _)| *key == group)
                    .map_or(*overall, |(_, median)| *median);

                Ok(median.to_string())
            }),
            FittedStep::MostFrequent {
                column,
                most_frequent,
            } => fill(x, column, |_| Ok(most_frequent.clone())),
            FittedStep::Dummy { dummies } => encode(x, dummies),
        }
    }
}

/// 行の属するグループ。`by` の列の値を並べたベクタ。
fn group_of(row: &Row, by: &[String]) -> Result<Vec<String>> {
    by.iter()
        .map(|column| row.text(column).map(str::to_string))
        .collect()
}

/// 中央値。件数が偶数なら中央の 2 つの平均。値が無ければ失敗する。
fn median(values: &[f64]) -> Result<f64> {
    if values.is_empty() {
        return Err(Error::AllMissing("中央値".to_string()));
    }

    let mut sorted = values.to_vec();
    sorted.sort_by(|a, b| a.partial_cmp(b).expect("値が NaN でないこと"));

    let middle = sorted.len() / 2;

    Ok(if sorted.len() % 2 == 1 {
        sorted[middle]
    } else {
        (sorted[middle - 1] + sorted[middle]) / 2.0
    })
}

/// グループごとの中央値と、全体の中央値を求める。グループは先に現れた順に並べる。
fn fit_group_median(x: &Table, column: &str, by: &[String]) -> Result<FittedStep> {
    let mut groups: Vec<(Vec<String>, Vec<f64>)> = Vec::new();
    let mut all = Vec::new();

    for row in &x.rows {
        let Some(value) = row.number(column)? else {
            continue;
        };

        all.push(value);

        let group = group_of(row, by)?;

        match groups.iter_mut().find(|(key, _)| *key == group) {
            Some((_, values)) => values.push(value),
            None => groups.push((group, vec![value])),
        }
    }

    let mut medians = Vec::with_capacity(groups.len());

    for (group, values) in groups {
        medians.push((group, median(&values)?));
    }

    Ok(FittedStep::GroupMedian {
        column: column.to_string(),
        by: by.to_vec(),
        medians,
        overall: median(&all)?,
    })
}

/// 欠損値でない値のうち、最も多いものを求める。同数なら先に現れたほうを選ぶ。
fn fit_most_frequent(x: &Table, column: &str) -> Result<FittedStep> {
    let mut counts: Vec<(String, usize)> = Vec::new();

    for row in &x.rows {
        if row.is_missing(column)? {
            continue;
        }

        let value = row.text(column)?.to_string();

        match counts.iter_mut().find(|(name, _)| *name == value) {
            Some((_, count)) => *count += 1,
            None => counts.push((value, 1)),
        }
    }

    let most_frequent = counts
        .into_iter()
        .fold(None::<(String, usize)>, |best, candidate| match best {
            Some(best) if best.1 >= candidate.1 => Some(best),
            _ => Some(candidate),
        })
        .map(|(value, _)| value)
        .ok_or_else(|| Error::AllMissing(column.to_string()))?;

    Ok(FittedStep::MostFrequent {
        column: column.to_string(),
        most_frequent,
    })
}

/// 列ごとに、ダミー変数にするカテゴリ（並べ替えて最初のカテゴリを除いたもの）を求める。
fn fit_dummy(x: &Table, columns: &[String]) -> Result<FittedStep> {
    let mut dummies = Vec::with_capacity(columns.len());

    for column in columns {
        let mut categories: Vec<String> = Vec::new();

        for row in &x.rows {
            if row.is_missing(column)? {
                continue;
            }

            let value = row.text(column)?.to_string();

            if !categories.contains(&value) {
                categories.push(value);
            }
        }

        if categories.is_empty() {
            return Err(Error::AllMissing(column.clone()));
        }

        categories.sort();
        categories.remove(0);
        dummies.push((column.clone(), categories));
    }

    Ok(FittedStep::Dummy { dummies })
}

/// 行の値を列名で引ける対応表にする。表の列だけを写す。
fn cells(columns: &[String], row: &Row) -> Result<HashMap<String, String>> {
    columns
        .iter()
        .map(|column| Ok((column.clone(), row.text(column)?.to_string())))
        .collect()
}

/// 欠損値を、行ごとに決めた値で埋める。
fn fill(x: &Table, column: &str, value_of: impl Fn(&Row) -> Result<String>) -> Result<Table> {
    let mut rows = Vec::with_capacity(x.rows.len());

    for row in &x.rows {
        if !row.is_missing(column)? {
            rows.push(row.clone());

            continue;
        }

        let mut filled = cells(&x.columns, row)?;
        filled.insert(column.to_string(), value_of(row)?);
        rows.push(Row::new(filled));
    }

    Ok(Table {
        columns: x.columns.clone(),
        rows,
    })
}

/// 元の列を除き、ダミー変数の列を末尾に足す。
fn encode(x: &Table, dummies: &[(String, Vec<String>)]) -> Result<Table> {
    let mut columns = x.columns.clone();

    for (column, categories) in dummies {
        columns.retain(|name| name != column);

        for category in categories {
            columns.push(format!("{column}_{category}"));
        }
    }

    let mut rows = Vec::with_capacity(x.rows.len());

    for row in &x.rows {
        let mut encoded = cells(&x.columns, row)?;

        for (column, categories) in dummies {
            let value = row.text(column)?.to_string();

            for category in categories {
                let flag = i32::from(&value == category);
                encoded.insert(format!("{column}_{category}"), flag.to_string());
            }
        }

        rows.push(Row::new(encoded));
    }

    Ok(Table { columns, rows })
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
                .collect::<HashMap<String, String>>(),
        )
    }

    /// 列名のベクタを作る。
    fn columns(names: &[&str]) -> Vec<String> {
        names.iter().map(|name| name.to_string()).collect()
    }

    /// 客室等級・性別・年齢を持つ表を作る。
    fn passengers(values: &[(&str, &str, &str)]) -> Table {
        Table {
            columns: columns(&["Pclass", "Sex", "Age"]),
            rows: values
                .iter()
                .map(|(pclass, sex, age)| row(&[("Pclass", pclass), ("Sex", sex), ("Age", age)]))
                .collect(),
        }
    }

    #[test]
    fn 欠損した年齢を全体の中央値で埋める() {
        let x = passengers(&[
            ("1", "female", "10"),
            ("1", "female", "20"),
            ("1", "female", ""),
        ]);

        let fitted = Step::GroupMedian {
            column: "Age".to_string(),
            by: Vec::new(),
        }
        .fit(&x)
        .unwrap();

        let filled = fitted.transform(&x).unwrap();

        assert_eq!(filled.rows[2].number("Age").unwrap(), Some(15.0));
    }

    #[test]
    fn グループごとに違う中央値で埋める() {
        let x = passengers(&[
            ("1", "female", "10"),
            ("1", "female", "20"),
            ("3", "male", "40"),
            ("3", "male", "60"),
            ("3", "male", ""),
            ("1", "female", ""),
        ]);

        let fitted = Step::GroupMedian {
            column: "Age".to_string(),
            by: columns(&["Pclass", "Sex"]),
        }
        .fit(&x)
        .unwrap();

        let filled = fitted.transform(&x).unwrap();

        assert_eq!(filled.rows[4].number("Age").unwrap(), Some(50.0));
        assert_eq!(filled.rows[5].number("Age").unwrap(), Some(15.0));
    }

    #[test]
    fn 訓練データで求めた中央値を別のデータに使う() {
        let train = passengers(&[("1", "female", "10"), ("1", "female", "20")]);
        let test = passengers(&[("1", "female", "")]);

        let filled = Step::GroupMedian {
            column: "Age".to_string(),
            by: columns(&["Pclass", "Sex"]),
        }
        .fit(&train)
        .unwrap()
        .transform(&test)
        .unwrap();

        assert_eq!(filled.rows[0].number("Age").unwrap(), Some(15.0));
    }

    #[test]
    fn 訓練データに無いグループは全体の中央値で埋める() {
        let train = passengers(&[("1", "female", "10"), ("1", "female", "30")]);
        let test = passengers(&[("3", "male", "")]);

        let filled = Step::GroupMedian {
            column: "Age".to_string(),
            by: columns(&["Pclass", "Sex"]),
        }
        .fit(&train)
        .unwrap()
        .transform(&test)
        .unwrap();

        assert_eq!(filled.rows[0].number("Age").unwrap(), Some(20.0));
    }

    #[test]
    fn 値がすべて欠けていれば中央値を求められない() {
        let x = passengers(&[("1", "female", "")]);

        assert_eq!(
            Step::GroupMedian {
                column: "Age".to_string(),
                by: Vec::new(),
            }
            .fit(&x)
            .unwrap_err()
            .to_string(),
            "値がすべて空欄です: 中央値"
        );
    }

    #[test]
    fn 欠損した港を最頻値で埋める() {
        let x = Table {
            columns: columns(&["Embarked"]),
            rows: vec![
                row(&[("Embarked", "S")]),
                row(&[("Embarked", "C")]),
                row(&[("Embarked", "S")]),
                row(&[("Embarked", "")]),
            ],
        };

        let filled = Step::MostFrequent {
            column: "Embarked".to_string(),
        }
        .fit(&x)
        .unwrap()
        .transform(&x)
        .unwrap();

        assert_eq!(filled.rows[3].text("Embarked").unwrap(), "S");
    }

    #[test]
    fn 最頻値が同数なら先に現れた値を選ぶ() {
        let x = Table {
            columns: columns(&["Embarked"]),
            rows: vec![row(&[("Embarked", "C")]), row(&[("Embarked", "S")])],
        };

        assert_eq!(
            Step::MostFrequent {
                column: "Embarked".to_string(),
            }
            .fit(&x)
            .unwrap(),
            FittedStep::MostFrequent {
                column: "Embarked".to_string(),
                most_frequent: "C".to_string(),
            }
        );
    }

    #[test]
    fn カテゴリ値を最初のカテゴリを除いたダミー変数にする() {
        let x = Table {
            columns: columns(&["Sex", "Age"]),
            rows: vec![
                row(&[("Sex", "male"), ("Age", "20")]),
                row(&[("Sex", "female"), ("Age", "30")]),
            ],
        };

        let encoded = Step::Dummy {
            columns: columns(&["Sex"]),
        }
        .fit(&x)
        .unwrap()
        .transform(&x)
        .unwrap();

        assert_eq!(encoded.columns, columns(&["Age", "Sex_male"]));
        assert_eq!(encoded.rows[0].number("Sex_male").unwrap(), Some(1.0));
        assert_eq!(encoded.rows[1].number("Sex_male").unwrap(), Some(0.0));
    }

    #[test]
    fn 別のデータにも訓練データと同じダミー変数の列を作る() {
        let train = Table {
            columns: columns(&["Embarked"]),
            rows: vec![
                row(&[("Embarked", "C")]),
                row(&[("Embarked", "Q")]),
                row(&[("Embarked", "S")]),
            ],
        };
        let test = Table {
            columns: columns(&["Embarked"]),
            rows: vec![row(&[("Embarked", "S")])],
        };

        let encoded = Step::Dummy {
            columns: columns(&["Embarked"]),
        }
        .fit(&train)
        .unwrap()
        .transform(&test)
        .unwrap();

        assert_eq!(encoded.columns, columns(&["Embarked_Q", "Embarked_S"]));
        assert_eq!(encoded.rows[0].number("Embarked_Q").unwrap(), Some(0.0));
        assert_eq!(encoded.rows[0].number("Embarked_S").unwrap(), Some(1.0));
    }
}
