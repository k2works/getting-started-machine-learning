//! ボストンの住宅価格（Boston.csv）の前処理と、特徴量の組ごとの決定係数。

use std::path::Path;

use crate::chapter02::{
    self, Features, Table, TrainTestSplit, column_means, fill_missing, split_features_and_target,
    split_train_test,
};

use super::linear::{LinearModel, r_squared};
use super::standardizer::Standardizer;
use super::{Result, dummies, polynomial};

/// 訓練データとテストデータの決定係数。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Scores {
    pub train: f64,
    pub test: f64,
}

/// Boston.csv の前処理と評価をまとめた入れ物。
pub struct Boston;

impl Boston {
    /// 正解の列。
    pub const TARGET: &'static str = "PRICE";
    /// カテゴリ値の列。
    pub const CATEGORY: &'static str = "CRIME";

    /// CRIME をダミー変数にし、分割してから訓練データの平均値で両方の欠損値を補完する。
    pub fn prepare(
        csv_file: &Path,
        test_size: f64,
        seed: u64,
    ) -> Result<TrainTestSplit<Features, f64>> {
        let table = Table::load(csv_file)?;

        let crimes: Vec<String> = table
            .rows
            .iter()
            .map(|row| row.text(Self::CATEGORY).map(str::to_string))
            .collect::<chapter02::Result<_>>()?;

        let encoded = dummies::encode(&table, Self::CATEGORY, &dummies::categories(&crimes));
        let (columns, rows, labels) = split_features_and_target(&encoded, Self::TARGET)?;

        let prices: Vec<f64> = labels
            .iter()
            .map(|label| {
                label
                    .trim()
                    .parse::<f64>()
                    .map_err(|_| chapter02::Error::NotANumber {
                        column: Self::TARGET.to_string(),
                        value: label.clone(),
                    })
            })
            .collect::<chapter02::Result<_>>()?;

        let split = split_train_test(&rows, &prices, test_size, seed)?;
        let means = column_means(&split.x_train, &columns)?;

        Ok(TrainTestSplit {
            x_train: fill_missing(&split.x_train, &columns, &means)?,
            x_test: fill_missing(&split.x_test, &columns, &means)?,
            t_train: split.t_train,
            t_test: split.t_test,
        })
    }

    /// 列から多項式特徴量を作って項を選び、訓練データで標準化してから線形回帰で学習し、
    /// 訓練データとテストデータの決定係数を求める。
    pub fn score_feature_set(
        split: &TrainTestSplit<Features, f64>,
        columns: &[String],
        terms: &[String],
    ) -> Result<Scores> {
        let train = polynomial::select(&polynomial::expand(&split.x_train, columns)?, terms)?;
        let test = polynomial::select(&polynomial::expand(&split.x_test, columns)?, terms)?;

        let standardizer = Standardizer::fit(&train)?;
        let x_train = to_rows(&standardizer.transform(&train)?);
        let x_test = to_rows(&standardizer.transform(&test)?);

        let model = LinearModel::fit(&x_train, &split.t_train)?;

        Ok(Scores {
            train: r_squared(&split.t_train, &model.predict(&x_train))?,
            test: r_squared(&split.t_test, &model.predict(&x_test))?,
        })
    }
}

/// 特徴量を、値だけの行のリストにする。
fn to_rows(x: &[Features]) -> Vec<Vec<f64>> {
    x.iter().map(|features| features.values.clone()).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn strings(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    /// 価格が 3 × RM² + 1 の架空のデータ。
    fn squared_split() -> TrainTestSplit<Features, f64> {
        let make = |rm: f64| Features::new(vec!["RM".to_string()], vec![rm]).unwrap();
        let price = |rm: f64| 3.0 * rm * rm + 1.0;
        let train: Vec<f64> = vec![1.0, 2.0, 3.0, 4.0, 5.0];
        let test: Vec<f64> = vec![6.0, 7.0];

        TrainTestSplit {
            x_train: train.iter().map(|rm| make(*rm)).collect(),
            x_test: test.iter().map(|rm| make(*rm)).collect(),
            t_train: train.iter().map(|rm| price(*rm)).collect(),
            t_test: test.iter().map(|rm| price(*rm)).collect(),
        }
    }

    #[test]
    fn 二乗の項が無いと当てきれない() {
        let scores =
            Boston::score_feature_set(&squared_split(), &strings(&["RM"]), &strings(&["RM"]))
                .unwrap();

        assert!(scores.train < 0.99, "{scores:?}");
    }

    #[test]
    fn 二乗の項を加えると決定係数が一になる() {
        let scores = Boston::score_feature_set(
            &squared_split(),
            &strings(&["RM"]),
            &strings(&["RM", "RM^2"]),
        )
        .unwrap();

        assert!((scores.train - 1.0).abs() < 1e-9, "{scores:?}");
        assert!((scores.test - 1.0).abs() < 1e-9, "{scores:?}");
    }
}
