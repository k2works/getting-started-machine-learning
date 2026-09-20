//! 実データ（KvsT.csv）を使うテスト。学習データが無ければスキップする。

use getting_started_ml::chapter01::{
    accuracy, load_people, predict_by_rule, split_features_and_labels,
};
use getting_started_ml::dataset;
use std::path::PathBuf;

/// 学習データのファイルを返す。無ければ None（テストはスキップする）。
/// Rust の標準のテストには「スキップ」が無いので、早く戻って理由を表示する。
fn data_file() -> Option<PathBuf> {
    let csv_file = dataset::current().join("KvsT.csv");

    if csv_file.exists() {
        Some(csv_file)
    } else {
        eprintln!("学習データ KvsT.csv が配置されていない（gulp data:setup）のでスキップする");

        None
    }
}

#[test]
fn 実データを読み込んで正解率を求める() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let people = load_people(&csv_file).expect("CSV を読めること");

    assert_eq!(people.len(), 19);

    let (x, t) = split_features_and_labels(&people);
    let predictions: Vec<String> = x
        .iter()
        .map(|features| predict_by_rule(*features).to_string())
        .collect();

    let score = accuracy(&predictions, &t).expect("正解率を求められること");

    assert!(
        (score - 14.0 / 19.0).abs() < 1e-12,
        "正解率 = {score}, want {}",
        14.0 / 19.0
    );
}
