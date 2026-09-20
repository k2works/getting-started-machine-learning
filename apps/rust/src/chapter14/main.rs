//! 第 14 章の実行。卸売業者の顧客を支出額でクラスタリングし、
//! エルボー法の SSE とクラスタごとの特徴を表示する。

use std::io::Write;

use crate::dataset;

use super::Result;
use super::kmeans::{self, DEFAULT_N_INIT};
use super::linfakmeans::LinfaKMeans;
use super::spending::{ClusterSummary, Spending};

/// 乱数のシード。
const SEED: u64 = 0;
/// エルボー法で試すクラスタ数の上限。
const MAX_CLUSTERS: usize = 10;
/// 特徴を調べるクラスタ数。
const N_CLUSTERS: usize = 5;

/// クラスタ数ごとの SSE と、クラスタごとの件数・平均支出額を表示する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let x = Spending::load(&dataset::current().join("Wholesale.csv"))?;
    let columns = x
        .first()
        .map_or_else(Vec::new, |features| features.columns.clone());
    let points = Spending::standardize(&x)?;

    writeln!(
        out,
        "データ件数: {}（支出額 {} 列）",
        x.len(),
        columns.len()
    )?;
    writeln!(
        out,
        "クラスタ数ごとの SSE（初期中心 {DEFAULT_N_INIT} 通りの最小値）:"
    )?;
    writeln!(out, "クラスタ数\t自作\tlinfa（k-means++）")?;

    let cluster_counts: Vec<usize> = (1..=MAX_CLUSTERS).collect();

    for (n, sse) in kmeans::sse_by_cluster_count(&points, &cluster_counts, SEED, DEFAULT_N_INIT)? {
        let theirs = LinfaKMeans::fit(&points, n, SEED, DEFAULT_N_INIT)?;

        writeln!(out, "{n}\t{sse:.2}\t{:.2}", theirs.sse)?;
    }

    let result = kmeans::fit_with_restarts(&points, N_CLUSTERS, SEED, DEFAULT_N_INIT)?;

    writeln!(out)?;
    writeln!(
        out,
        "クラスタ数 {N_CLUSTERS} のクラスタごとの件数と平均支出額:"
    )?;
    writeln!(out, "クラスタ\t件数\t{}", columns.join("\t"))?;

    for summary in Spending::summarize_clusters(&x, &result.labels)? {
        writeln!(out, "{}", format_summary(&summary))?;
    }

    Ok(())
}

/// クラスタの要約を、タブで区切った 1 行にする。
fn format_summary(summary: &ClusterSummary) -> String {
    let means: Vec<String> = summary
        .means
        .iter()
        .map(|mean| format!("{mean:.0}"))
        .collect();

    format!(
        "{}\t{}\t{}",
        summary.cluster,
        summary.count,
        means.join("\t")
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 要約はタブで区切った一行になる() {
        let summary = ClusterSummary {
            cluster: 2,
            count: 7,
            means: vec![1234.5, 6.4],
        };

        assert_eq!(format_summary(&summary), "2\t7\t1234\t6");
    }
}
