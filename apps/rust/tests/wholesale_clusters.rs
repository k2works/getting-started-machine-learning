//! 実データ（Wholesale.csv）で K-means を確かめるテスト。学習データが無ければスキップする。

use getting_started_ml::chapter14::{LinfaKMeans, Spending, kmeans, run};
use getting_started_ml::dataset;
use std::path::PathBuf;

/// 乱数のシード。
const SEED: u64 = 0;

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
fn 実データは四百四十件で支出額は六列() {
    let Some(csv_file) = data_file("Wholesale.csv") else {
        return;
    };

    let x = Spending::load(&csv_file).expect("読み込めること");

    assert_eq!(x.len(), 440);
    assert_eq!(
        x[0].columns,
        [
            "Fresh",
            "Milk",
            "Grocery",
            "Frozen",
            "Detergents_Paper",
            "Delicassen"
        ]
    );
}

#[test]
fn 実データでクラスタ数を増やすと誤差平方和は小さくなる() {
    let Some(csv_file) = data_file("Wholesale.csv") else {
        return;
    };

    let x = Spending::load(&csv_file).expect("読み込めること");
    let points = Spending::standardize(&x).expect("標準化できること");
    let sse = kmeans::sse_by_cluster_count(&points, &[1, 2, 3], SEED, kmeans::DEFAULT_N_INIT)
        .expect("クラスタリングできること");

    // クラスタ数 1 の SSE は、標準化した値の 2 乗和（440 件 × 6 列）になる
    assert!((sse[0].1 - 2640.0).abs() < 1e-6, "{sse:?}");
    assert!(sse[0].1 > sse[1].1);
    assert!(sse[1].1 > sse[2].1);
}

#[test]
fn 実データで自作とlinfaの誤差平方和は同じ桁になる() {
    let Some(csv_file) = data_file("Wholesale.csv") else {
        return;
    };

    let x = Spending::load(&csv_file).expect("読み込めること");
    let points = Spending::standardize(&x).expect("標準化できること");

    let ours = kmeans::fit_with_restarts(&points, 5, SEED, kmeans::DEFAULT_N_INIT)
        .expect("クラスタリングできること");
    let theirs =
        LinfaKMeans::fit(&points, 5, SEED, kmeans::DEFAULT_N_INIT).expect("linfa で学習できること");

    // 初期中心の選び方が違うので値は一致しない。桁が変わらないことだけを確かめる
    assert!(
        (ours.sse / theirs.sse - 1.0).abs() < 0.1,
        "自作 {}, linfa {}",
        ours.sse,
        theirs.sse
    );
}

#[test]
fn 実行するとエルボー法とクラスタごとの特徴を表示する() {
    if data_file("Wholesale.csv").is_none() {
        return;
    }

    let mut out = Vec::new();
    run(&mut out).expect("実行できること");

    // 初期中心の選び方（rand 0.8 のシャッフル）がほかの言語版と違うので、SSE は一致しない
    assert_eq!(
        String::from_utf8(out).expect("UTF-8 であること"),
        "データ件数: 440（支出額 6 列）\n\
         クラスタ数ごとの SSE（初期中心 10 通りの最小値）:\n\
         クラスタ数\t自作\tlinfa（k-means++）\n\
         1\t2640.00\t2640.00\n\
         2\t1954.65\t1954.18\n\
         3\t1614.52\t1614.52\n\
         4\t1334.36\t1325.96\n\
         5\t1085.27\t1062.63\n\
         6\t983.55\t924.53\n\
         7\t890.81\t828.11\n\
         8\t785.54\t750.25\n\
         9\t670.90\t680.12\n\
         10\t618.17\t616.96\n\
         \n\
         クラスタ数 5 のクラスタごとの件数と平均支出額:\n\
         クラスタ\t件数\tFresh\tMilk\tGrocery\tFrozen\tDetergents_Paper\tDelicassen\n\
         1\t265\t8909\t2967\t3804\t2248\t989\t962\n\
         2\t96\t5509\t10556\t16478\t1420\t7199\t1659\n\
         3\t65\t31117\t4260\t5374\t7225\t849\t2286\n\
         0\t10\t15965\t34708\t48537\t3055\t24875\t2943\n\
         4\t4\t52022\t31696\t18491\t29826\t2699\t19656\n"
    );
}
