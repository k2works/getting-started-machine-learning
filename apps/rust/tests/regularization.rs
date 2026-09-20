//! 実データ（Boston.csv）で第 12 章の正則化とモデル選択を確かめるテスト。
//! 学習データが無ければスキップする。

use getting_started_ml::chapter07::r2_score;
use getting_started_ml::chapter12::{
    self, Boston, best_experiment, fit_lasso, fit_ridge, linfa_lasso, linfa_ridge,
    run_ridge_experiments,
};
use getting_started_ml::dataset;
use std::path::PathBuf;

/// テストデータの割合。
const TEST_SIZE: f64 = 0.3;
/// 検証データの割合。
const VALIDATION_SIZE: f64 = 0.3;
/// 分割の乱数のシード。
const SEED: u64 = 0;
/// 試す正則化の強さ。
const ALPHAS: [f64; 5] = [0.0, 0.1, 1.0, 10.0, 100.0];

/// 学習データのファイルを返す。無ければ None（テストはスキップする）。
fn data_file() -> Option<PathBuf> {
    let csv_file = dataset::current().join("Boston.csv");

    if csv_file.exists() {
        Some(csv_file)
    } else {
        eprintln!("学習データ Boston.csv が配置されていない（gulp data:setup）のでスキップする");

        None
    }
}

/// 前処理した実データ。
fn prepared() -> Option<chapter12::Dataset> {
    let csv_file = data_file()?;

    Some(Boston::prepare(&csv_file, TEST_SIZE, VALIDATION_SIZE, SEED).expect("前処理できること"))
}

#[test]
fn 実データは外れ値を二件除いて九十八件になる() {
    let Some(data) = prepared() else {
        return;
    };

    assert_eq!(data.kept, 98);
    assert_eq!(data.removed, 2);
    assert_eq!(data.t_train.len(), 47);
    assert_eq!(data.t_valid.len(), 21);
    assert_eq!(data.t_test.len(), 30);
    assert_eq!(data.feature_names.len(), 9);
    assert_eq!(data.feature_names[3], "RM^2");
}

#[test]
fn 罰則を強くすると係数の絶対値の合計が減る() {
    let Some(data) = prepared() else {
        return;
    };

    let experiments = run_ridge_experiments(
        &data.feature_names,
        &data.x_train,
        &data.t_train,
        &data.x_valid,
        &data.t_valid,
        &ALPHAS,
    )
    .expect("実験できること");

    for pair in experiments.windows(2) {
        assert!(
            pair[0].coefficient_abs_sum > pair[1].coefficient_abs_sum,
            "{:?}",
            pair
        );
    }

    assert!((experiments[0].coefficient_abs_sum - 12.057).abs() < 0.001);
    assert!((experiments[4].coefficient_abs_sum - 6.785).abs() < 0.001);
}

#[test]
fn 検証データで選んだリッジ回帰はテストデータで線形回帰に勝つ() {
    let Some(data) = prepared() else {
        return;
    };

    let experiments = run_ridge_experiments(
        &data.feature_names,
        &data.x_train,
        &data.t_train,
        &data.x_valid,
        &data.t_valid,
        &ALPHAS,
    )
    .expect("実験できること");
    let best = best_experiment(&experiments).expect("選べること");

    assert!((best.alpha - 1.0).abs() < 1e-12);

    let linear =
        fit_ridge(&data.feature_names, &data.x_train, &data.t_train, 0.0).expect("学習できること");
    let ridge = fit_ridge(
        &data.feature_names,
        &data.x_train,
        &data.t_train,
        best.alpha,
    )
    .expect("学習できること");

    let linear_score = r2_score(&data.t_test, &linear.predict(&data.x_test).unwrap()).unwrap();
    let ridge_score = r2_score(&data.t_test, &ridge.predict(&data.x_test).unwrap()).unwrap();

    assert!((linear_score - 0.4728).abs() < 1e-4, "{linear_score}");
    assert!((ridge_score - 0.5019).abs() < 1e-4, "{ridge_score}");
    assert!(ridge_score > linear_score);
}

#[test]
fn 実データでもリッジ回帰はlinfaと一致する() {
    let Some(data) = prepared() else {
        return;
    };

    let own =
        fit_ridge(&data.feature_names, &data.x_train, &data.t_train, 1.0).expect("学習できること");
    let library = linfa_ridge(&data.feature_names, &data.x_train, &data.t_train, 1.0)
        .expect("linfa が学習できること");

    for (left, right) in own.coefficients.iter().zip(&library.coefficients) {
        assert!((left - right).abs() < 1e-9, "{left} と {right}");
    }

    assert!((own.intercept - library.intercept).abs() < 1e-9);
}

#[test]
fn 実データでもラッソ回帰はlinfaと同じ特徴量を零にする() {
    let Some(data) = prepared() else {
        return;
    };

    let own =
        fit_lasso(&data.feature_names, &data.x_train, &data.t_train, 10.0).expect("学習できること");
    let library = linfa_lasso(&data.feature_names, &data.x_train, &data.t_train, 10.0)
        .expect("linfa が学習できること");

    assert_eq!(
        own.zero_columns(),
        vec!["RM LSTAT", "PTRATIO^2", "PTRATIO LSTAT"]
    );
    assert_eq!(own.zero_columns(), library.zero_columns());

    for (left, right) in own.coefficients.iter().zip(&library.coefficients) {
        assert!((left - right).abs() < 1e-6, "{left} と {right}");
    }
}

#[test]
fn 第十二章の実行結果が固定される() {
    if data_file().is_none() {
        return;
    }

    let mut out = Vec::new();
    chapter12::run(&mut out).expect("実行できること");

    let text = String::from_utf8(out).expect("UTF-8 であること");

    assert!(
        text.contains("データ件数: 98（外れ値 2 件を除外）"),
        "{text}"
    );
    assert!(
        text.contains("訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件"),
        "{text}"
    );
    assert!(text.contains("0.0\t0.9123\t0.7861\t12.057"), "{text}");
    assert!(text.contains("100.0\t0.7227\t0.6596\t6.785"), "{text}");
    assert!(text.contains("検証データで選んだ alpha: 1.0"), "{text}");
    assert!(
        text.contains("テストデータの決定係数: 線形回帰 0.4728, リッジ回帰 0.5019"),
        "{text}"
    );
    assert!(
        text.contains("  alpha=10: 自作 [RM LSTAT, PTRATIO^2, PTRATIO LSTAT] / linfa [RM LSTAT, PTRATIO^2, PTRATIO LSTAT]"),
        "{text}"
    );
}
