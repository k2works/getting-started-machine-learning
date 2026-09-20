//! 欠損値の補完と、訓練データ・テストデータへの分割。

use std::collections::HashMap;

use rand::SeedableRng;
use rand::rngs::StdRng;

use super::Error;
use super::Result;
use super::table::{Row, Table};

/// アヤメのデータの正解ラベルの列。
pub const TARGET: &str = "種類";

/// 欠損値を持たない特徴量。列名と値を同じ順で持つ。
#[derive(Debug, Clone, PartialEq)]
pub struct Features {
    pub columns: Vec<String>,
    pub values: Vec<f64>,
}

impl Features {
    /// 列名と値から特徴量を作る。数が合わなければ失敗を返す。
    pub fn new(columns: Vec<String>, values: Vec<f64>) -> Result<Features> {
        if columns.len() != values.len() {
            return Err(Error::LengthMismatch {
                left: columns.len(),
                right: values.len(),
            });
        }

        Ok(Features { columns, values })
    }

    /// 列名で値を読む。
    pub fn value(&self, column: &str) -> Result<f64> {
        self.columns
            .iter()
            .position(|name| name == column)
            .map(|index| self.values[index])
            .ok_or_else(|| Error::MissingColumn(column.to_string()))
    }
}

/// 訓練データとテストデータ。X は特徴量の型、T は正解ラベルの型。
#[derive(Debug, Clone, PartialEq)]
pub struct TrainTestSplit<X, T> {
    pub x_train: Vec<X>,
    pub x_test: Vec<X>,
    pub t_train: Vec<T>,
    pub t_test: Vec<T>,
}

/// 欠損値を除いて、列ごとの平均値を求める。
pub fn column_means(rows: &[Row], columns: &[String]) -> Result<HashMap<String, f64>> {
    let mut means = HashMap::with_capacity(columns.len());

    for column in columns {
        let mut sum = 0.0;
        let mut count = 0;

        for row in rows {
            if let Some(value) = row.number(column)? {
                sum += value;
                count += 1;
            }
        }

        if count == 0 {
            return Err(Error::AllMissing(column.clone()));
        }

        means.insert(column.clone(), sum / f64::from(count));
    }

    Ok(means)
}

/// 欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変えない。
pub fn fill_missing(
    rows: &[Row],
    columns: &[String],
    values: &HashMap<String, f64>,
) -> Result<Vec<Features>> {
    let mut filled = Vec::with_capacity(rows.len());

    for row in rows {
        let mut numbers = Vec::with_capacity(columns.len());

        for column in columns {
            let value = match row.number(column)? {
                Some(value) => value,
                None => *values
                    .get(column)
                    .ok_or_else(|| Error::NoFillValue(column.clone()))?,
            };

            numbers.push(value);
        }

        filled.push(Features::new(columns.to_vec(), numbers)?);
    }

    Ok(filled)
}

/// 正解ラベルの列を取り出し、残りの列を特徴量の列にする。
pub fn split_features_and_target(
    table: &Table,
    target: &str,
) -> Result<(Vec<String>, Vec<Row>, Vec<String>)> {
    let columns: Vec<String> = table
        .columns
        .iter()
        .filter(|column| column.as_str() != target)
        .cloned()
        .collect();

    let mut labels = Vec::with_capacity(table.rows.len());

    for row in &table.rows {
        labels.push(row.text(target)?.to_string());
    }

    Ok((columns, table.rows.clone(), labels))
}

/// シードを使って Fisher-Yates のシャッフルで並べ替える。元のスライスは変えない。
pub fn shuffle<E: Clone>(items: &[E], seed: u64) -> Vec<E> {
    let mut rng = StdRng::seed_from_u64(seed);
    let mut shuffled = items.to_vec();

    for i in (1..shuffled.len()).rev() {
        let j = next_index(&mut rng, i + 1);
        shuffled.swap(i, j);
    }

    shuffled
}

/// 0 以上 bound 未満の整数を返す。再現できる分割のための擬似乱数で、暗号用途ではない。
fn next_index(rng: &mut StdRng, bound: usize) -> usize {
    use rand::Rng;

    rng.gen_range(0..bound)
}

/// 並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。
pub fn split_train_test<X: Clone, T: Clone>(
    x: &[X],
    t: &[T],
    test_size: f64,
    seed: u64,
) -> Result<TrainTestSplit<X, T>> {
    if x.len() != t.len() {
        return Err(Error::LengthMismatch {
            left: x.len(),
            right: t.len(),
        });
    }

    let pairs: Vec<(X, T)> = x.iter().cloned().zip(t.iter().cloned()).collect();
    let shuffled = shuffle(&pairs, seed);

    #[allow(clippy::cast_precision_loss, clippy::cast_sign_loss)]
    let test_count = (shuffled.len() as f64 * test_size).ceil() as usize;
    let train_count = shuffled.len() - test_count;

    let mut split = TrainTestSplit {
        x_train: Vec::with_capacity(train_count),
        x_test: Vec::with_capacity(test_count),
        t_train: Vec::with_capacity(train_count),
        t_test: Vec::with_capacity(test_count),
    };

    for (index, (features, label)) in shuffled.into_iter().enumerate() {
        if index < train_count {
            split.x_train.push(features);
            split.t_train.push(label);
        } else {
            split.x_test.push(features);
            split.t_test.push(label);
        }
    }

    Ok(split)
}

/// iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。
pub fn prepare_iris(
    csv_file: &std::path::Path,
    test_size: f64,
    seed: u64,
) -> Result<TrainTestSplit<Features, String>> {
    let table = Table::load(csv_file)?;
    let (columns, rows, labels) = split_features_and_target(&table, TARGET)?;
    let split = split_train_test(&rows, &labels, test_size, seed)?;
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

    /// 列名と値から行を作る。
    fn row(pairs: &[(&str, &str)]) -> Row {
        Row::new(
            pairs
                .iter()
                .map(|(name, value)| (name.to_string(), value.to_string()))
                .collect(),
        )
    }

    /// 文字列のスライスを列名のベクタにする。
    fn columns(names: &[&str]) -> Vec<String> {
        names.iter().map(|name| name.to_string()).collect()
    }

    #[test]
    fn 欠損値を除いて列ごとの平均値を求める() {
        let rows = vec![
            row(&[("がく片長さ", "1.0")]),
            row(&[("がく片長さ", "")]),
            row(&[("がく片長さ", "3.0")]),
        ];

        let means = column_means(&rows, &columns(&["がく片長さ"])).unwrap();

        assert_eq!(means["がく片長さ"], 2.0);
    }

    #[test]
    fn 値がすべて空欄なら平均値を求められない() {
        let rows = vec![row(&[("がく片長さ", "")])];

        assert_eq!(
            column_means(&rows, &columns(&["がく片長さ"]))
                .unwrap_err()
                .to_string(),
            "値がすべて空欄です: がく片長さ"
        );
    }

    #[test]
    fn 欠損値を平均値で補完する() {
        let rows = vec![row(&[("がく片長さ", "1.0")]), row(&[("がく片長さ", "")])];
        let means = HashMap::from([("がく片長さ".to_string(), 2.0)]);

        let filled = fill_missing(&rows, &columns(&["がく片長さ"]), &means).unwrap();

        assert_eq!(
            filled,
            vec![
                Features::new(columns(&["がく片長さ"]), vec![1.0]).unwrap(),
                Features::new(columns(&["がく片長さ"]), vec![2.0]).unwrap(),
            ]
        );
    }

    #[test]
    fn 列名で特徴量の値を読む() {
        let features = Features::new(columns(&["がく片長さ", "花弁幅"]), vec![5.1, 0.2]).unwrap();

        assert_eq!(features.value("花弁幅").unwrap(), 0.2);
        assert_eq!(
            features.value("種類").unwrap_err().to_string(),
            "列がありません: 種類"
        );
    }

    #[test]
    fn 列名と値の数が合わなければ特徴量を作れない() {
        assert_eq!(
            Features::new(columns(&["がく片長さ"]), vec![5.1, 0.2])
                .unwrap_err()
                .to_string(),
            "件数が違います: 1 と 2"
        );
    }

    #[test]
    fn 同じシードなら同じ並びになる() {
        let items: Vec<usize> = (0..10).collect();

        assert_eq!(shuffle(&items, 0), shuffle(&items, 0));
        assert_ne!(shuffle(&items, 0), shuffle(&items, 1));
        assert_eq!(shuffle(&items, 0).len(), items.len());
    }

    #[test]
    fn 並べ替えても要素は変わらない() {
        let items: Vec<usize> = (0..10).collect();

        let mut shuffled = shuffle(&items, 0);
        shuffled.sort_unstable();

        assert_eq!(shuffled, items);
    }

    #[test]
    fn テストデータの割合で分ける() {
        let x: Vec<usize> = (0..10).collect();
        let t: Vec<String> = x.iter().map(|value| value.to_string()).collect();

        let split = split_train_test(&x, &t, 0.3, 0).unwrap();

        assert_eq!(split.x_train.len(), 7);
        assert_eq!(split.x_test.len(), 3);
        assert_eq!(split.t_train.len(), 7);
        assert_eq!(split.t_test.len(), 3);
    }

    #[test]
    fn 特徴量と正解ラベルの件数が違えば分けられない() {
        let x: Vec<usize> = vec![1, 2];
        let t: Vec<usize> = vec![1];

        assert_eq!(
            split_train_test(&x, &t, 0.3, 0).unwrap_err().to_string(),
            "件数が違います: 2 と 1"
        );
    }

    #[test]
    fn 正解ラベルの列を取り出して残りを特徴量の列にする() {
        let table = Table {
            columns: columns(&["がく片長さ", "種類"]),
            rows: vec![
                row(&[("がく片長さ", "5.1"), ("種類", "Iris-setosa")]),
                row(&[("がく片長さ", "6.0"), ("種類", "Iris-virginica")]),
            ],
        };

        let (feature_columns, rows, labels) = split_features_and_target(&table, TARGET).unwrap();

        assert_eq!(feature_columns, columns(&["がく片長さ"]));
        assert_eq!(rows.len(), 2);
        assert_eq!(
            labels,
            vec!["Iris-setosa".to_string(), "Iris-virginica".to_string()]
        );
    }
}
