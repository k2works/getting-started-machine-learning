//! 実データ（Survived.csv・cinema.csv）で第 11 章の評価指標と交差検証を確かめるテスト。
//! 学習データが無ければスキップする。

use getting_started_ml::chapter03::DecisionTree;
use getting_started_ml::chapter07::root_mean_squared_error;
use getting_started_ml::chapter11::crossval::mean;
use getting_started_ml::chapter11::linfametrics::positive_probabilities;
use getting_started_ml::chapter11::metrics::classification_metric;
use getting_started_ml::chapter11::model::LinearRegressionModel;
use getting_started_ml::chapter11::{
    self, ConfusionMatrix, auc, cross_validate, data, k_fold, k_fold_sequential,
    linfa_cross_validate_rmse, linfa_scores, roc_curve,
};
use getting_started_ml::dataset;
use std::path::PathBuf;

/// 分割の数。
const N_SPLITS: usize = 5;
/// 分割の乱数のシード。
const SEED: u64 = 0;
/// 決定木の深さ。
const TREE_DEPTH: usize = 2;

/// 学習データのファイルを返す。無ければ None（テストはスキップする）。
fn data_file(name: &str) -> Option<PathBuf> {
    let csv_file = dataset::current().join(name);

    if csv_file.exists() {
        Some(csv_file)
    } else {
        eprintln!("学習データ {name} が配置されていない（gulp data:setup）のでスキップする");

        None
    }
}

#[test]
fn 実データの交差検証で適合率と再現率が食い違う() {
    let Some(csv_file) = data_file("Survived.csv") else {
        return;
    };

    let survived = data::load_survived(&csv_file).expect("前処理できること");
    let folds = k_fold(survived.x.len(), N_SPLITS, SEED).expect("分けられること");

    assert_eq!(survived.x.len(), 891);

    let precision = mean(
        &cross_validate(
            &|| Box::new(DecisionTree::with_max_depth(TREE_DEPTH)),
            &survived.x,
            &survived.t,
            &folds,
            &classification_metric(ConfusionMatrix::precision, "1".to_string()),
        )
        .expect("採点できること"),
    )
    .expect("平均を求められること");

    let recall = mean(
        &cross_validate(
            &|| Box::new(DecisionTree::with_max_depth(TREE_DEPTH)),
            &survived.x,
            &survived.t,
            &folds,
            &classification_metric(ConfusionMatrix::recall, "1".to_string()),
        )
        .expect("採点できること"),
    )
    .expect("平均を求められること");

    // 深さ 2 の決定木は「生存」と言い切るのに慎重で、適合率は高いが再現率は低い
    assert!((precision - 0.8052).abs() < 1e-4, "適合率 = {precision}");
    assert!((recall - 0.5649).abs() < 1e-4, "再現率 = {recall}");
    assert!(precision > recall);
}

#[test]
fn 実データのaucは自作とlinfaで一致する() {
    let Some(csv_file) = data_file("Survived.csv") else {
        return;
    };

    let survived = data::load_survived(&csv_file).expect("前処理できること");
    let folds = k_fold(survived.x.len(), N_SPLITS, SEED).expect("分けられること");
    let fold = &folds[0];

    let pick_x = |positions: &[usize]| -> Vec<_> {
        positions.iter().map(|i| survived.x[*i].clone()).collect()
    };
    let pick_t = |positions: &[usize]| -> Vec<String> {
        positions.iter().map(|i| survived.t[*i].clone()).collect()
    };

    let probabilities = positive_probabilities(
        &pick_x(&fold.train),
        &pick_t(&fold.train),
        &pick_x(&fold.test),
        "1",
    )
    .expect("確率を求められること");
    let truth: Vec<bool> = pick_t(&fold.test)
        .iter()
        .map(|label| label == "1")
        .collect();

    let own = auc(&roc_curve(&probabilities, &truth).expect("曲線を描けること"));
    let library = chapter11::linfa_auc(&probabilities, &truth).expect("linfa が答えること");

    assert!((own - 0.8503).abs() < 1e-4, "AUC = {own}");
    // linfa は f32 で数えるので、一致は f32 の精度で見る
    assert!((own - library).abs() < 1e-6, "自作 {own} / linfa {library}");
}

#[test]
fn 実データの混同行列は自作とlinfaで一致する() {
    let Some(csv_file) = data_file("Survived.csv") else {
        return;
    };

    let survived = data::load_survived(&csv_file).expect("前処理できること");
    let folds = k_fold(survived.x.len(), N_SPLITS, SEED).expect("分けられること");
    let fold = &folds[0];

    let mut tree = DecisionTree::with_max_depth(TREE_DEPTH);
    let x_train: Vec<_> = fold.train.iter().map(|i| survived.x[*i].clone()).collect();
    let t_train: Vec<String> = fold.train.iter().map(|i| survived.t[*i].clone()).collect();
    let x_test: Vec<_> = fold.test.iter().map(|i| survived.x[*i].clone()).collect();
    let t_test: Vec<String> = fold.test.iter().map(|i| survived.t[*i].clone()).collect();

    tree.fit(&x_train, &t_train).expect("学習できること");

    let predicted = tree.predict(&x_test).expect("予測できること");
    let matrix =
        ConfusionMatrix::of(&t_test, &predicted, &"1".to_string()).expect("数えられること");
    let library = linfa_scores(&t_test, &predicted).expect("linfa が答えること");

    assert_eq!(matrix.true_positive, 34);
    assert_eq!(matrix.false_positive, 9);
    assert_eq!(matrix.false_negative, 30);
    assert_eq!(matrix.true_negative, 106);
    assert!((matrix.precision() - library.precision).abs() < 1e-6);
    assert!((matrix.recall() - library.recall).abs() < 1e-6);
    assert!((matrix.f1_score() - library.f1_score).abs() < 1e-6);
}

#[test]
fn 実データの交差検証は並べ替えなしならlinfaと一致する() {
    let Some(csv_file) = data_file("cinema.csv") else {
        return;
    };

    let cinema = data::load_cinema(&csv_file).expect("前処理できること");
    let folds = k_fold_sequential(cinema.x.len(), N_SPLITS).expect("分けられること");

    let own = mean(
        &cross_validate(
            &|| Box::new(LinearRegressionModel::new()),
            &cinema.x,
            &cinema.t,
            &folds,
            &(Box::new(root_mean_squared_error) as _),
        )
        .expect("採点できること"),
    )
    .expect("平均を求められること");
    let library =
        linfa_cross_validate_rmse(&cinema.x, &cinema.t, N_SPLITS).expect("linfa が答えること");

    assert!((own - 410.42).abs() < 0.01, "RMSE = {own}");
    assert!((own - library).abs() < 1e-6, "自作 {own} / linfa {library}");
}

#[test]
fn 第十一章の実行結果が固定される() {
    if data_file("Survived.csv").is_none() || data_file("cinema.csv").is_none() {
        return;
    }

    let mut out = Vec::new();
    chapter11::run(&mut out).expect("実行できること");

    let text = String::from_utf8(out).expect("UTF-8 であること");

    assert!(text.contains("  正解率（5 分割の平均）: 0.7755"), "{text}");
    assert!(text.contains("  F値（5 分割の平均）: 0.6541"), "{text}");
    assert!(text.contains("  AUC（自作）: 0.8503"), "{text}");
    assert!(text.contains("  AUC（linfa）: 0.8503"), "{text}");
    assert!(
        text.contains("  混同行列（1 つ目の分割）: TP=34 FP=9 FN=30 TN=106"),
        "{text}"
    );
    assert!(
        text.contains("  適合率: 自作 0.7907 / linfa 0.7907"),
        "{text}"
    );
    assert!(text.contains("  RMSE（5 分割の平均）: 409.05"), "{text}");
    assert!(
        text.contains("  RMSE（並べ替えなし）: 自作 410.42 / linfa 410.42"),
        "{text}"
    );
}
