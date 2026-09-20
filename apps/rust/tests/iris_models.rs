//! 実データ（iris.csv）でロジスティック回帰とランダムフォレストを確かめるテスト。
//! 学習データが無ければスキップする。

use getting_started_ml::chapter02::{Features, TrainTestSplit, prepare_iris};
use getting_started_ml::chapter10::{
    Classifier, LinfaLogisticRegression, LogisticRegression, RandomForest, Score,
    forest_importances, linfalogistic::WEAK_PENALTY, run,
};
use getting_started_ml::dataset;

/// テストデータの割合。
const TEST_SIZE: f64 = 0.3;
/// 分割の乱数のシード。
const SEED: u64 = 0;

/// 前処理したアヤメのデータを返す。学習データが無ければ None（テストはスキップする）。
fn iris_split() -> Option<TrainTestSplit<Features, String>> {
    let csv_file = dataset::current().join("iris.csv");

    if !csv_file.exists() {
        eprintln!("学習データ iris.csv が配置されていない（gulp data:setup）のでスキップする");

        return None;
    }

    Some(prepare_iris(&csv_file, TEST_SIZE, SEED).expect("前処理できること"))
}

#[test]
fn 自作のロジスティック回帰の正解率は繰り返しを増やしても変わらない() {
    let Some(split) = iris_split() else {
        return;
    };

    for epochs in [1000, 5000, 20000] {
        let mut model = LogisticRegression::new(1.0, epochs);
        let score = Score::evaluate(&mut model, &split).expect("評価できること");

        assert!(
            (score.train - 0.933_333_333_333_333_3).abs() < 1e-12,
            "{epochs} 回"
        );
        assert!(
            (score.test - 0.888_888_888_888_888_8).abs() < 1e-12,
            "{epochs} 回"
        );
    }
}

#[test]
fn 正則化を外すと自作とlinfaの予測が完全に一致する() {
    let Some(split) = iris_split() else {
        return;
    };

    let mut ours = LogisticRegression::default();
    ours.fit(&split.x_train, &split.t_train)
        .expect("学習できること");

    let mut theirs = LinfaLogisticRegression::new(1000, WEAK_PENALTY);
    theirs
        .fit(&split.x_train, &split.t_train)
        .expect("学習できること");

    assert_eq!(
        ours.predict(&split.x_test).expect("予測できること"),
        theirs.predict(&split.x_test).expect("予測できること")
    );
}

#[test]
fn linfaの既定のl2は正解率を変える() {
    let Some(split) = iris_split() else {
        return;
    };

    // linfa の MultiLogisticRegression は既定で L2 の強さ（alpha）が 1.0。
    // 正則化の分だけ訓練データに合わせず、この分割ではテストデータの正解率が上がる
    let mut model = LinfaLogisticRegression::default();
    let score = Score::evaluate(&mut model, &split).expect("評価できること");

    assert!(
        (score.train - 0.914_285_714_285_714_3).abs() < 1e-12,
        "{score:?}"
    );
    assert!(
        (score.test - 0.933_333_333_333_333_3).abs() < 1e-12,
        "{score:?}"
    );
}

#[test]
fn 森の木を深く育てるとテストデータの正解率が下がる() {
    let Some(split) = iris_split() else {
        return;
    };

    let mut unlimited = RandomForest::new(100, 2, SEED);
    let deep = Score::evaluate(&mut unlimited, &split).expect("評価できること");

    let mut shallow = RandomForest::with_max_depth(100, 2, 2, SEED);
    let shallow_score = Score::evaluate(&mut shallow, &split).expect("評価できること");

    assert!((deep.train - 1.0).abs() < 1e-12);
    assert!(
        deep.test < shallow_score.test,
        "{deep:?} < {shallow_score:?}"
    );
}

#[test]
fn 特徴量の重要度は花弁幅が最も大きい() {
    let Some(split) = iris_split() else {
        return;
    };

    let mut forest = RandomForest::new(100, 2, SEED);
    forest
        .fit(&split.x_train, &split.t_train)
        .expect("学習できること");

    let importances = forest_importances(&forest, &split.x_train, &split.t_train)
        .expect("重要度を求められること");

    let total: f64 = importances.iter().map(|(_, value)| value).sum();
    let best = importances
        .iter()
        .max_by(|left, right| left.1.total_cmp(&right.1))
        .expect("特徴量があること");

    assert!((total - 1.0).abs() < 1e-12);
    assert_eq!(best.0, "花弁幅");
}

#[test]
fn 実行するとモデルごとの正解率と重要度を表示する() {
    if iris_split().is_none() {
        return;
    }

    let mut out = Vec::new();
    run(&mut out).expect("実行できること");

    // rand の分け方がほかの言語版と違うので、正解率も重要度も一致しない
    assert_eq!(
        String::from_utf8(out).expect("UTF-8 であること"),
        "モデル\t訓練データ\tテストデータ\n\
         決定木（深さ 2）\t0.9524\t0.9111\n\
         ロジスティック回帰\t0.9333\t0.8889\n\
         ランダムフォレスト（100 本）\t1.0000\t0.8889\n\
         ランダムフォレスト（100 本・深さ 2）\t0.9429\t0.9111\n\
         linfa ロジスティック回帰\t0.9333\t0.8889\n\
         \n\
         ランダムフォレスト（100 本）の特徴量の重要度:\n\
         がく片長さ\t0.1761\n\
         がく片幅\t0.1432\n\
         花弁長さ\t0.2092\n\
         花弁幅\t0.4715\n"
    );
}
