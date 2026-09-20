//! 実データ（cinema.csv）で線形回帰を確かめるテスト。学習データが無ければスキップする。

use getting_started_ml::chapter02::Table;
use getting_started_ml::chapter07::{cinema, linearregression, linfareg, metrics, run};
use getting_started_ml::dataset;
use std::path::PathBuf;

/// テストデータの割合。
const TEST_SIZE: f64 = 0.2;
/// 分割の乱数のシード。
const SEED: u64 = 0;

/// 学習データのファイルを返す。無ければ None（テストはスキップする）。
fn data_file() -> Option<PathBuf> {
    let csv_file = dataset::current().join("cinema.csv");

    if csv_file.exists() {
        Some(csv_file)
    } else {
        eprintln!("学習データ cinema.csv が配置されていない（gulp data:setup）のでスキップする");

        None
    }
}

#[test]
fn 実データの外れ値は一件だけ除かれる() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let table = Table::load(&csv_file).expect("CSV を読めること");

    assert_eq!(table.rows.len(), 100);
    assert_eq!(
        cinema::remove_outliers(&table)
            .expect("外れ値を除けること")
            .rows
            .len(),
        99
    );
}

#[test]
fn 実データを七十九件と二十件に分ける() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let split = cinema::prepare(&csv_file, TEST_SIZE, SEED).expect("前処理できること");

    assert_eq!(split.x_train.len(), 79);
    assert_eq!(split.x_test.len(), 20);
}

#[test]
fn 自作とlinfaの係数は実データでも一致する() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let split = cinema::prepare(&csv_file, TEST_SIZE, SEED).expect("前処理できること");
    let ours = linearregression::fit(&split.x_train, &split.t_train).expect("学習できること");
    let theirs = linfareg::fit(&split.x_train, &split.t_train).expect("linfa で学習できること");

    assert!(
        (ours.intercept - theirs.intercept).abs() < 1e-6,
        "切片 {} と {}",
        ours.intercept,
        theirs.intercept
    );

    for column in cinema::FEATURES {
        let ours = ours.coefficient(column).expect("係数があること");
        let theirs = theirs.coefficient(column).expect("係数があること");

        assert!(
            (ours - theirs).abs() < 1e-6,
            "{column} の係数 {ours} と {theirs}"
        );
    }
}

#[test]
fn テストデータの決定係数は零点八を超える() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let split = cinema::prepare(&csv_file, TEST_SIZE, SEED).expect("前処理できること");
    let model = linearregression::fit(&split.x_train, &split.t_train).expect("学習できること");
    let y = model.predict(&split.x_test).expect("予測できること");

    let r2 = metrics::r2_score(&split.t_test, &y).expect("評価できること");

    assert!((r2 - 0.806_772_930_964_951_1).abs() < 1e-9, "R2 = {r2}");
}

#[test]
fn 実行すると係数と評価指標を表示する() {
    if data_file().is_none() {
        return;
    }

    let mut out = Vec::new();
    run(&mut out).expect("実行できること");

    // rand の分け方がほかの言語版と違うので、係数も評価指標も一致しない
    assert_eq!(
        String::from_utf8(out).expect("UTF-8 であること"),
        "データ件数: 100\n\
         外れ値を除いた件数: 99\n\
         訓練データ: 79 件, テストデータ: 20 件\n\
         切片: 6118.29\n\
         係数: SNS1=1.1354, SNS2=0.5438, actor=0.2980, original=206.5224\n\
         linfa の切片: 6118.29, 係数: SNS1=1.1354, SNS2=0.5438, actor=0.2980, original=206.5224\n\
         テストデータの評価: R2=0.8068, MAE=305.15, RMSE=380.11\n"
    );
}
