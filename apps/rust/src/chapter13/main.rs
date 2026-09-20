//! 第 13 章の実行。ボストンの住宅価格の 15 列を主成分分析し、寄与率と主成分の意味を表示する。

use std::io::Write;

use crate::dataset;

use super::boston::BostonPca;
use super::linfapca::LinfaPca;
use super::pca;
use super::{Loading, Result};

/// 累積寄与率のしきい値。
const THRESHOLD: f64 = 0.8;
/// 主成分ごとに表示する列の数。
const TOP_K: usize = 3;
/// 意味を調べる主成分の数。
const COMPONENTS_TO_EXPLAIN: usize = 2;
/// linfa と一致しているとみなす差。
const AGREEMENT: f64 = 1e-9;

/// 寄与率・必要な主成分の数・主成分への影響が大きい列を表示する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let features = BostonPca::load(&dataset::current().join("Boston.csv"))?;
    let columns = features
        .first()
        .map_or_else(Vec::new, |first| first.columns.clone());
    let x = BostonPca::to_matrix(&features)?;

    let model = pca::fit(&x, columns.len())?;
    let ratios = &model.explained_variance_ratio;
    let needed = pca::components_needed(ratios, THRESHOLD);
    let cumulative: f64 = ratios.iter().take(needed).sum();

    writeln!(out, "データ件数: {}, 列数: {}", x.nrows(), columns.len())?;
    writeln!(out, "寄与率: {}", format_ratios(ratios, needed))?;
    writeln!(
        out,
        "累積寄与率が {THRESHOLD} に届く主成分の数: {needed}（累積寄与率 {cumulative:.4}）"
    )?;

    for index in 0..COMPONENTS_TO_EXPLAIN {
        let loadings = pca::top_loadings(&model.components.row(index).to_owned(), &columns, TOP_K);

        writeln!(
            out,
            "第 {} 主成分で影響の大きい列: {}",
            index + 1,
            format_loadings(&loadings)
        )?;
    }

    let theirs = LinfaPca::fit(&x, columns.len())?;

    let difference = max_difference(ratios, &theirs.explained_variance_ratio).max(max_difference(
        model.components.as_slice().unwrap_or(&[]),
        theirs.components.as_slice().unwrap_or(&[]),
    ));

    writeln!(
        out,
        "linfa-reduction の寄与率と主成分: {}",
        if difference < AGREEMENT {
            format!("自作と {AGREEMENT:.0e} 以内で一致")
        } else {
            format!("自作との差が {difference:.2e}")
        }
    )?;

    Ok(())
}

/// 寄与率を先頭から count 個だけ 1 行にまとめる。
fn format_ratios(ratios: &[f64], count: usize) -> String {
    ratios
        .iter()
        .take(count)
        .enumerate()
        .map(|(index, ratio)| format!("PC{} {ratio:.4}", index + 1))
        .collect::<Vec<String>>()
        .join(", ")
}

/// 列名と係数を 1 行にまとめる。
fn format_loadings(loadings: &[Loading]) -> String {
    loadings
        .iter()
        .map(|loading| format!("{} {:.3}", loading.column, loading.value))
        .collect::<Vec<String>>()
        .join(", ")
}

/// 2 つの数列の差の絶対値の最大。
fn max_difference(ours: &[f64], theirs: &[f64]) -> f64 {
    ours.iter()
        .zip(theirs)
        .map(|(ours, theirs)| (ours - theirs).abs())
        .fold(0.0_f64, f64::max)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 寄与率は指定した数だけ並べる() {
        assert_eq!(format_ratios(&[0.5, 0.3, 0.2], 2), "PC1 0.5000, PC2 0.3000");
    }

    #[test]
    fn 係数は小数第三位まで表示する() {
        let loadings = vec![
            Loading {
                column: "RM".to_string(),
                value: 0.1234,
            },
            Loading {
                column: "LSTAT".to_string(),
                value: -0.5,
            },
        ];

        assert_eq!(format_loadings(&loadings), "RM 0.123, LSTAT -0.500");
    }

    #[test]
    fn 差の最大は絶対値で比べる() {
        assert!((max_difference(&[1.0, 2.0], &[1.5, 1.0]) - 1.0).abs() < 1e-12);
        assert!(max_difference(&[], &[]).abs() < 1e-12);
    }
}
