//! 実データ（Boston.csv）で主成分分析を確かめるテスト。学習データが無ければスキップする。

use getting_started_ml::chapter13::{BostonPca, linfapca::LinfaPca, pca, run};
use getting_started_ml::dataset;
use std::path::PathBuf;

/// 学習データのファイルを返す。無ければ None（テストはスキップする）。
fn data_file(name: &str) -> Option<PathBuf> {
    let file = dataset::current().join(name);

    if file.exists() {
        Some(file)
    } else {
        eprintln!("学習データ {name} が配置されていない（gulp data:setup）のでスキップする");

        None
    }
}

#[test]
fn 実データは百件十五列になり寄与率の合計は一になる() {
    let Some(csv_file) = data_file("Boston.csv") else {
        return;
    };

    let features = BostonPca::load(&csv_file).expect("前処理できること");
    let x = BostonPca::to_matrix(&features).expect("行列にできること");
    let model = pca::fit(&x, x.ncols()).expect("主成分分析できること");

    assert_eq!(x.nrows(), 100);
    assert_eq!(x.ncols(), 15);
    assert!(
        (model.explained_variance_ratio.iter().sum::<f64>() - 1.0).abs() < 1e-12,
        "{:?}",
        model.explained_variance_ratio
    );
}

#[test]
fn 実データの主成分は長さ一で互いに直交する() {
    let Some(csv_file) = data_file("Boston.csv") else {
        return;
    };

    let features = BostonPca::load(&csv_file).expect("前処理できること");
    let x = BostonPca::to_matrix(&features).expect("行列にできること");
    let model = pca::fit(&x, x.ncols()).expect("主成分分析できること");

    for (left, right) in [(0, 0), (1, 1), (0, 1), (0, 2), (1, 2)] {
        let product: f64 = model
            .components
            .row(left)
            .iter()
            .zip(model.components.row(right))
            .map(|(a, b)| a * b)
            .sum();
        let expected = if left == right { 1.0 } else { 0.0 };

        assert!(
            (product - expected).abs() < 1e-9,
            "PC{} と PC{} の内積が {product}",
            left + 1,
            right + 1
        );
    }
}

#[test]
fn 実データで自作の主成分分析とlinfaが一致する() {
    let Some(csv_file) = data_file("Boston.csv") else {
        return;
    };

    let features = BostonPca::load(&csv_file).expect("前処理できること");
    let x = BostonPca::to_matrix(&features).expect("行列にできること");

    let ours = pca::fit(&x, x.ncols()).expect("主成分分析できること");
    let theirs = LinfaPca::fit(&x, x.ncols()).expect("linfa で主成分分析できること");

    for (index, (ours, theirs)) in ours
        .explained_variance_ratio
        .iter()
        .zip(&theirs.explained_variance_ratio)
        .enumerate()
    {
        assert!(
            (ours - theirs).abs() < 1e-12,
            "PC{} の寄与率: 自作 {ours}, linfa {theirs}",
            index + 1
        );
    }

    for ((row, column), ours) in ours.components.indexed_iter() {
        let theirs = theirs.components[[row, column]];

        assert!(
            (ours - theirs).abs() < 1e-9,
            "PC{} の {column} 列目: 自作 {ours}, linfa {theirs}",
            row + 1
        );
    }
}

#[test]
fn 実行すると寄与率と主成分の意味を表示する() {
    if data_file("Boston.csv").is_none() {
        return;
    }

    let mut out = Vec::new();
    run(&mut out).expect("実行できること");

    // 乱数も分割も使わないので、Java 版・Go 版と同じ寄与率になる
    assert_eq!(
        String::from_utf8(out).expect("UTF-8 であること"),
        "データ件数: 100, 列数: 15\n\
         寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581\n\
         累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）\n\
         第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328\n\
         第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405\n\
         linfa-reduction の寄与率と主成分: 自作と 1e-9 以内で一致\n"
    );
}
