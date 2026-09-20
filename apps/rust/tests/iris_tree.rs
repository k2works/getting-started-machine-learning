//! 実データ（iris.csv）で決定木を確かめるテスト。学習データが無ければスキップする。

use getting_started_ml::chapter02::prepare_iris;
use getting_started_ml::chapter03::linfatree;
use getting_started_ml::chapter03::{DecisionTree, format, run};
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

/// 予測が正解ラベルと一致した割合を返す。
fn accuracy(predictions: &[String], labels: &[String]) -> f64 {
    let correct = predictions
        .iter()
        .zip(labels)
        .filter(|(prediction, label)| prediction == label)
        .count();

    correct as f64 / labels.len() as f64
}

#[test]
fn 深さ二の決定木はテストデータの四十五件中四十一件を正しく分類する() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let split = prepare_iris(&csv_file, 0.3, 0).expect("前処理できること");

    let mut model = DecisionTree::with_max_depth(2);
    let predictions = model
        .fit(&split.x_train, &split.t_train)
        .expect("学習できること")
        .predict(&split.x_test)
        .expect("予測できること");

    assert!((accuracy(&predictions, &split.t_test) - 41.0 / 45.0).abs() < 1e-12);
}

#[test]
fn 浅い木では自作とlinfaの予測が一致する() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let split = prepare_iris(&csv_file, 0.3, 0).expect("前処理できること");

    // 深さ 1・2 では予測が完全に一致する。
    // 深さ 3 は正解率が同じ（0.9111）でも予測が 2 件分かれ、深さ 4 以上は正解率も分かれる
    for max_depth in [1, 2] {
        let mut model = DecisionTree::with_max_depth(max_depth);
        let ours = model
            .fit(&split.x_train, &split.t_train)
            .expect("学習できること")
            .predict(&split.x_test)
            .expect("予測できること");

        let theirs = linfatree::predict(
            &split.x_train,
            &split.t_train,
            &split.x_test,
            Some(max_depth),
        )
        .expect("linfa で予測できること");

        assert_eq!(ours, theirs, "深さ {max_depth} で一致すること");
    }
}

#[test]
fn 深さ二の決定木は花弁幅で三種類に分かれる() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let split = prepare_iris(&csv_file, 0.3, 0).expect("前処理できること");

    let mut model = DecisionTree::with_max_depth(2);
    model
        .fit(&split.x_train, &split.t_train)
        .expect("学習できること");

    assert_eq!(
        format(model.tree().expect("学習していること")),
        "花弁幅 <= 0.2950\n  Iris-setosa\n花弁幅 > 0.2950\n  花弁幅 <= 0.6900\n    Iris-versicolor\n  花弁幅 > 0.6900\n    Iris-virginica\n"
    );
}

#[test]
fn 実行すると深さごとの正解率と決定木を表示する() {
    if data_file().is_none() {
        return;
    }

    let mut out = Vec::new();
    run(&mut out).expect("実行できること");

    // rand の分け方がほかの言語版と違うので、正解率も木の境界も一致しない
    assert_eq!(
        String::from_utf8(out).expect("UTF-8 であること"),
        "深さ\t訓練データ\tテストデータ\tlinfa\n\
         1\t0.7143\t0.5556\t0.5556\n\
         2\t0.9524\t0.9111\t0.9111\n\
         3\t0.9619\t0.9111\t0.9111\n\
         4\t0.9714\t0.9111\t0.8444\n\
         5\t0.9810\t0.9111\t0.8444\n\
         制限なし\t1.0000\t0.9111\t0.8444\n\
         \n\
         深さ 2 の決定木:\n\
         花弁幅 <= 0.2950\n\
         \x20 Iris-setosa\n\
         花弁幅 > 0.2950\n\
         \x20 花弁幅 <= 0.6900\n\
         \x20   Iris-versicolor\n\
         \x20 花弁幅 > 0.6900\n\
         \x20   Iris-virginica\n"
    );
}
