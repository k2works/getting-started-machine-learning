//! 実データ（Survived.csv）で前処理パイプラインを確かめるテスト。学習データが無ければスキップする。

use getting_started_ml::chapter02::{Table, split_train_test};
use getting_started_ml::chapter08::{
    ClassWeight, Pipeline, accuracy, evaluate, linfacompare, modelfile, run_with, survived,
};
use getting_started_ml::dataset;
use std::path::PathBuf;

/// テストデータの割合。
const TEST_SIZE: f64 = 0.2;
/// 分割の乱数のシード。
const SEED: u64 = 0;
/// 決定木の深さの上限。
const MAX_DEPTH: Option<usize> = Some(5);

/// 学習データのファイルを返す。無ければ None（テストはスキップする）。
fn data_file() -> Option<PathBuf> {
    let csv_file = dataset::current().join("Survived.csv");

    if csv_file.exists() {
        Some(csv_file)
    } else {
        eprintln!("学習データ Survived.csv が配置されていない（gulp data:setup）のでスキップする");

        None
    }
}

/// 実データを分割し、クラスの重みを指定して学習してから評価する。
fn evaluation_of(class_weight: ClassWeight) -> Option<getting_started_ml::chapter08::Evaluation> {
    let csv_file = data_file()?;
    let rows = Table::load(&csv_file).expect("CSV を読めること").rows;
    let t = survived::target(&rows).expect("正解ラベルを読めること");
    let split = split_train_test(&rows, &t, TEST_SIZE, SEED).expect("分割できること");

    let fitted = Pipeline::build(MAX_DEPTH, class_weight)
        .fit(&survived::features(&split.x_train), &split.t_train)
        .expect("学習できること");

    Some(evaluate(&fitted, &split).expect("評価できること"))
}

#[test]
fn 実データは八百九十一件で生存は三百四十二人() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let rows = Table::load(&csv_file).expect("CSV を読めること").rows;
    let t = survived::target(&rows).expect("正解ラベルを読めること");

    assert_eq!(rows.len(), 891);
    assert_eq!(t.iter().filter(|label| **label == 1).count(), 342);
}

#[test]
fn 重みを付けると見つかる生存者が増える() {
    let (Some(none), Some(balanced)) = (
        evaluation_of(ClassWeight::None),
        evaluation_of(ClassWeight::Balanced),
    ) else {
        return;
    };

    assert_eq!(none.survivors, 66);
    assert_eq!(none.found_survivors, 49);
    assert_eq!(balanced.found_survivors, 53);

    // 見つかる生存者は増えるが、全体の正解率はわずかに下がる
    assert!(balanced.test_accuracy < none.test_accuracy);
}

#[test]
fn 実データの正解率は八割を超える() {
    let Some(result) = evaluation_of(ClassWeight::None) else {
        return;
    };

    assert!((result.train_accuracy - 0.853_932_584_269_662_9).abs() < 1e-9);
    assert!((result.test_accuracy - 0.826_815_642_458_100_5).abs() < 1e-9);
}

#[test]
fn 保存して読み込んだモデルは同じ予測をする() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let rows = Table::load(&csv_file).expect("CSV を読めること").rows;
    let t = survived::target(&rows).expect("正解ラベルを読めること");
    let split = split_train_test(&rows, &t, TEST_SIZE, SEED).expect("分割できること");
    let x_test = survived::features(&split.x_test);

    let fitted = Pipeline::build(MAX_DEPTH, ClassWeight::Balanced)
        .fit(&survived::features(&split.x_train), &split.t_train)
        .expect("学習できること");

    let dir = std::env::temp_dir().join("getting-started-ml-survived");
    let model_file = dir.join("survived.json");

    modelfile::save(&fitted, &model_file).expect("保存できること");
    let loaded = modelfile::load(&model_file).expect("読み込めること");

    assert_eq!(
        loaded.predict(&x_test).expect("予測できること"),
        fitted.predict(&x_test).expect("予測できること")
    );

    std::fs::remove_dir_all(&dir).expect("後始末できること");
}

#[test]
fn 実行すると重みごとの評価と架空の乗客の予測を表示する() {
    if data_file().is_none() {
        return;
    }

    let dir = std::env::temp_dir().join("getting-started-ml-survived-run");
    let mut out = Vec::new();

    run_with(&mut out, &dir.join("survived.json")).expect("実行できること");

    // rand の分け方がほかの言語版と違うので、正解率も予測も一致しない
    assert_eq!(
        String::from_utf8(out).expect("UTF-8 であること"),
        "データ件数: 891（生存 342, 死亡 549）\n\
         訓練データ: 712 件, テストデータ: 179 件\n\
         classWeight=none: 訓練 0.854, テスト 0.827, 生存者 66 人中 49 人を発見\n\
         classWeight=balanced: 訓練 0.847, テスト 0.821, 生存者 66 人中 53 人を発見\n\
         保存したモデル: survived.json\n\
         架空の乗客の予測: [1, 0]\n"
    );

    std::fs::remove_dir_all(&dir).expect("後始末できること");
}

#[test]
fn linfaの決定木は実行するたびに結果が変わる() {
    let Some(csv_file) = data_file() else {
        return;
    };

    let rows = Table::load(&csv_file).expect("CSV を読めること").rows;
    let t = survived::target(&rows).expect("正解ラベルを読めること");
    let split = split_train_test(&rows, &t, TEST_SIZE, SEED).expect("分割できること");

    let fitted = Pipeline::build(MAX_DEPTH, ClassWeight::None)
        .fit(&survived::features(&split.x_train), &split.t_train)
        .expect("学習できること");
    let x_train = fitted
        .features(&survived::features(&split.x_train))
        .expect("前処理できること");
    let x_test = fitted
        .features(&survived::features(&split.x_test))
        .expect("前処理できること");

    // linfa-trees は同じ不純度の分割が並ぶと選び方が一定しないので、
    // Survived.csv では実行のたびに正解率が変わる（0.78〜0.81）。値を固定するテストは書けない
    let predictions = linfacompare::predict(&x_train, &split.t_train, &x_test, MAX_DEPTH)
        .expect("予測できること");
    let value = accuracy(&predictions, &split.t_test).expect("評価できること");

    assert!((0.75..0.85).contains(&value), "linfa の正解率 = {value}");
}
