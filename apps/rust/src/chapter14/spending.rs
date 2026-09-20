//! 卸売業者の顧客ごとの支出額（Wholesale.csv）。

use std::path::Path;

use ndarray::Array2;

use crate::chapter02::{self, Features, Table};
use crate::chapter09::Standardizer;

use super::{Error, Result};

/// 区分を表す番号で、支出額ではない列。
const CATEGORIES: [&str; 2] = ["Channel", "Region"];

/// 1 つのクラスタの特徴。
#[derive(Debug, Clone, PartialEq)]
pub struct ClusterSummary {
    /// クラスタ番号。
    pub cluster: usize,
    /// 所属する件数。
    pub count: usize,
    /// 列の順のままの、列ごとの平均。
    pub means: Vec<f64>,
}

/// Wholesale.csv の読み込みと要約をまとめた入れ物。
pub struct Spending;

impl Spending {
    /// Channel と Region を除いた支出額の列を読み込む。欠損値があれば失敗にする。
    pub fn load(csv_file: &Path) -> Result<Vec<Features>> {
        let table = Table::load(csv_file)?;
        let columns: Vec<String> = table
            .columns
            .iter()
            .filter(|column| !CATEGORIES.contains(&column.as_str()))
            .cloned()
            .collect();

        table
            .rows
            .iter()
            .map(|row| {
                let values = columns
                    .iter()
                    .map(|column| {
                        row.number(column)?
                            .ok_or_else(|| chapter02::Error::AllMissing(column.clone()))
                    })
                    .collect::<chapter02::Result<Vec<f64>>>()?;

                Ok(Features::new(columns.clone(), values)?)
            })
            .collect()
    }

    /// 第 9 章の標準化（件数で割る標準偏差）で列ごとにそろえ、1 件を 1 行とする行列にする。
    pub fn standardize(x: &[Features]) -> Result<Array2<f64>> {
        let standardized = Standardizer::fit(x)?.transform(x)?;
        let columns = standardized
            .first()
            .map_or(0, |features| features.values.len());
        let values: Vec<f64> = standardized
            .iter()
            .flat_map(|features| features.values.clone())
            .collect();

        Array2::from_shape_vec((standardized.len(), columns), values)
            .map_err(|error| Error::from(chapter02::Error::Library(error.to_string())))
    }

    /// クラスタごとの件数と、元の単位での列ごとの平均を、件数の多い順に並べる。
    pub fn summarize_clusters(x: &[Features], labels: &[usize]) -> Result<Vec<ClusterSummary>> {
        let first = x.first().ok_or(Error::Empty)?;
        let n_clusters = labels.iter().max().map_or(0, |largest| largest + 1);
        let mut sums = vec![vec![0.0; first.values.len()]; n_clusters];
        let mut counts = vec![0_usize; n_clusters];

        for (features, label) in x.iter().zip(labels) {
            counts[*label] += 1;

            for (sum, value) in sums[*label].iter_mut().zip(&features.values) {
                *sum += value;
            }
        }

        let mut summaries: Vec<ClusterSummary> = (0..n_clusters)
            .filter(|cluster| counts[*cluster] > 0)
            .map(|cluster| {
                #[allow(clippy::cast_precision_loss)]
                let count = counts[cluster] as f64;

                ClusterSummary {
                    cluster,
                    count: counts[cluster],
                    means: sums[cluster].iter().map(|sum| sum / count).collect(),
                }
            })
            .collect();

        // 件数の多い順。`Reverse` で降順にする（clippy は `sort_by` より `sort_by_key` を勧める）
        summaries.sort_by_key(|summary| std::cmp::Reverse(summary.count));

        Ok(summaries)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Fresh と Milk を持つ特徴量を作る。
    fn row(fresh: f64, milk: f64) -> Features {
        Features::new(
            vec!["Fresh".to_string(), "Milk".to_string()],
            vec![fresh, milk],
        )
        .unwrap()
    }

    #[test]
    fn 標準化した点は一件を一行とする行列になる() {
        let points = Spending::standardize(&[row(1.0, 10.0), row(3.0, 30.0)]).unwrap();

        assert_eq!(points.nrows(), 2);
        assert_eq!(points.ncols(), 2);
        assert!((points[[0, 0]] + 1.0).abs() < 1e-12);
        assert!((points[[1, 0]] - 1.0).abs() < 1e-12);
    }

    #[test]
    fn クラスタごとの件数と平均を件数の多い順に並べる() {
        let x = [row(1.0, 10.0), row(3.0, 30.0), row(100.0, 200.0)];

        let summaries = Spending::summarize_clusters(&x, &[0, 0, 1]).unwrap();

        assert_eq!(summaries[0].cluster, 0);
        assert_eq!(summaries[0].count, 2);
        assert!((summaries[0].means[0] - 2.0).abs() < 1e-12);
        assert!((summaries[0].means[1] - 20.0).abs() < 1e-12);
        assert_eq!(summaries[1].count, 1);
    }

    #[test]
    fn 点が無ければ要約できない() {
        assert_eq!(
            Spending::summarize_clusters(&[], &[])
                .unwrap_err()
                .to_string(),
            "点が 1 つもありません"
        );
    }
}
