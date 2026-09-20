//! 実データ（iris.csv）を使うテスト。学習データが無ければスキップする。

use getting_started_ml::chapter02::{
    Missing, TARGET, Table, column_means, prepare_iris, shuffle, split_features_and_target,
    split_train_test,
};
use getting_started_ml::dataset;
use std::path::PathBuf;

/// 学習データのファイルを返す。無ければ None（テストはスキップする）。
fn data_file() -> Option<PathBuf> {
    let csv_file = dataset::current().join("iris.csv");

    if csv_file.exists() {
        Some(csv_file)
    } else {
        eprintln!("学習データ iris.csv が配置されていない（gulp data:setup）のでスキップする");

        None
    }
}

#[test]
fn 実データの列ごとの欠損値の数を数える() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let table = Table::load(&csv_file).expect("CSV を読めること");

    assert_eq!(table.rows.len(), 150);
    assert_eq!(
        table.count_missing().expect("欠損値を数えられること"),
        vec![
            Missing {
                column: "がく片長さ".to_string(),
                count: 2
            },
            Missing {
                column: "がく片幅".to_string(),
                count: 1
            },
            Missing {
                column: "花弁長さ".to_string(),
                count: 2
            },
            Missing {
                column: "花弁幅".to_string(),
                count: 2
            },
            Missing {
                column: "種類".to_string(),
                count: 0
            },
        ]
    );
}

#[test]
fn 実データを百五件と四十五件に分けて欠損値を補完する() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let split = prepare_iris(&csv_file, 0.3, 0).expect("前処理できること");

    assert_eq!(split.x_train.len(), 105);
    assert_eq!(split.x_test.len(), 45);
    assert_eq!(split.t_train.len(), 105);
    assert_eq!(split.t_test.len(), 45);
}

#[test]
fn 訓練データの平均値は乱数の分け方で決まる() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let table = Table::load(&csv_file).expect("CSV を読めること");
    let (columns, rows, labels) =
        split_features_and_target(&table, TARGET).expect("列を分けられること");
    let split = split_train_test(&rows, &labels, 0.3, 0).expect("分割できること");
    let means = column_means(&split.x_train, &columns).expect("平均値を求められること");

    // rand の StdRng は Java の java.util.Random とも Go の math/rand とも乱数列が違うので、
    // 分かれる行と平均値はほかの言語版と一致しない（件数だけ一致する）
    for (column, want) in [
        ("がく片長さ", 0.406_285_714_285_714_36),
        ("がく片幅", 0.441_346_153_846_153_94),
        ("花弁長さ", 0.467_184_466_019_417_54),
        ("花弁幅", 0.415_384_615_384_615_3),
    ] {
        assert!(
            (means[column] - want).abs() < 1e-12,
            "{column} の平均値 = {}, want {want}",
            means[column]
        );
    }
}

#[test]
fn 並べ替えの並びはほかの言語版と違う() {
    let items: Vec<usize> = (0..10).collect();

    // Java 版は [4 8 9 6 3 5 2 1 7 0]、Go 版は [6 8 2 3 7 5 9 1 0 4]
    assert_eq!(shuffle(&items, 0), vec![9, 3, 6, 4, 8, 1, 5, 2, 0, 7]);
}
