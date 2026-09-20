//! 実データ（Boston.csv・bike.tsv・weather.csv）で特徴量エンジニアリングを確かめるテスト。
//! 学習データが無ければスキップする。

use getting_started_ml::chapter09::{
    Boston, Encoding, Standardizer, linfascaler, load, outliers, run,
};
use getting_started_ml::dataset;
use std::path::PathBuf;

/// テストデータの割合。
const TEST_SIZE: f64 = 0.3;
/// 分割の乱数のシード。
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
fn 実データを七十件と三十件に分けてダミー変数の列を足す() {
    let Some(csv_file) = data_file("Boston.csv") else {
        return;
    };

    let split = Boston::prepare(&csv_file, TEST_SIZE, SEED).expect("前処理できること");

    assert_eq!(split.x_train.len(), 70);
    assert_eq!(split.x_test.len(), 30);
    assert_eq!(
        split.x_train[0].columns,
        [
            "ZN",
            "INDUS",
            "CHAS",
            "NOX",
            "RM",
            "AGE",
            "DIS",
            "RAD",
            "TAX",
            "PTRATIO",
            "B",
            "LSTAT",
            "CRIME_low",
            "CRIME_very_low",
        ]
    );
}

#[test]
fn 実データで自作の標準化とlinfaの標準化が一致する() {
    let Some(csv_file) = data_file("Boston.csv") else {
        return;
    };

    let split = Boston::prepare(&csv_file, TEST_SIZE, SEED).expect("前処理できること");
    let standardizer = Standardizer::fit(&split.x_train).expect("学習できること");

    let train: Vec<f64> = split
        .x_train
        .iter()
        .map(|features| features.value("RM").expect("RM があること"))
        .collect();
    let test: Vec<f64> = split
        .x_test
        .iter()
        .map(|features| features.value("RM").expect("RM があること"))
        .collect();

    let ours: Vec<f64> = standardizer
        .transform(&split.x_test)
        .expect("標準化できること")
        .iter()
        .map(|features| features.value("RM").expect("RM があること"))
        .collect();
    let theirs = linfascaler::standardize(&train, &test).expect("linfa で標準化できること");

    // linfa の LinearScaler::standard() は母標準偏差（n で割る）なので、自作と一致する
    for (index, (ours, theirs)) in ours.iter().zip(&theirs).enumerate() {
        assert!(
            (ours - theirs).abs() < 1e-12,
            "{index} 件目: 自作 {ours}, linfa {theirs}"
        );
    }
}

#[test]
fn 実データの価格の外れ値は四件ある() {
    let Some(csv_file) = data_file("Boston.csv") else {
        return;
    };

    let split = Boston::prepare(&csv_file, TEST_SIZE, SEED).expect("前処理できること");
    let outliers = outliers::iqr_outliers(&split.t_train, outliers::DEFAULT_K)
        .expect("外れ値を数えられること");

    assert_eq!(outliers.iter().filter(|outlier| **outlier).count(), 4);
}

#[test]
fn weather_csvはutf8として読めない() {
    let Some(csv_file) = data_file("weather.csv") else {
        return;
    };

    let bytes = std::fs::read(&csv_file).expect("読めること");

    // Go 版の japanese デコーダは黙って U+FFFD に置き換えるが、
    // Rust の String::from_utf8 は Err を返す
    assert!(String::from_utf8(bytes).is_err());
    assert!(load(&csv_file, Encoding::Utf8, ',').is_err());
    assert!(load(&csv_file, Encoding::ShiftJis, ',').is_ok());
}

#[test]
fn 実行すると決定係数と天気ごとの平均利用者数を表示する() {
    if data_file("Boston.csv").is_none() || data_file("bike.tsv").is_none() {
        return;
    }

    let mut out = Vec::new();
    run(&mut out).expect("実行できること");

    // rand の分け方がほかの言語版と違うので、決定係数は一致しない（天気の集計は一致する）
    assert_eq!(
        String::from_utf8(out).expect("UTF-8 であること"),
        "訓練データ: 70 件, テストデータ: 30 件\n\
         特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low\n\
         標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00\n\
         決定係数:\n\
         \x20 元の特徴量（3 列）: 訓練 0.6972, テスト 0.5239\n\
         \x20 2 乗の項を追加（6 列）: 訓練 0.8629, テスト 0.6804\n\
         \x20 交互作用の項も追加（9 列）: 訓練 0.8658, テスト 0.6828\n\
         訓練データの PRICE の外れ値: 4 件\n\
         \x20 外れ値を除いて 2 乗の項を追加: 訓練 0.7250, テスト 0.5882\n\
         天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3\n"
    );
}
