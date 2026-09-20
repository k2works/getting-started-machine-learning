//! 第 11 章の実行。Survived と cinema を K 分割交差検証で評価し、ROC 曲線の AUC も表示する。

use std::io::Write;

use crate::chapter03::DecisionTree;
use crate::chapter07::{mean_absolute_error, root_mean_squared_error};
use crate::dataset;

use super::Result;
use super::crossval::{cross_validate, k_fold, k_fold_sequential, mean};
use super::data::{self, SURVIVED_POSITIVE};
use super::linfacv::linfa_cross_validate_rmse;
use super::linfametrics::{linfa_auc, linfa_scores, positive_probabilities};
use super::metrics::{ConfusionMatrix, Metric, accuracy, classification_metric};
use super::model::{LinearRegressionModel, Model};
use super::roc::{auc, roc_curve};

/// 分割の数。
pub const N_SPLITS: usize = 5;
/// 分割の乱数のシード。
pub const SEED: u64 = 0;
/// 決定木の深さ。
pub const TREE_DEPTH: usize = 2;

/// Survived の評価指標。表示する順に並べる。
pub fn survived_metrics() -> Vec<(String, Metric<String>)> {
    let positive = SURVIVED_POSITIVE.to_string();

    vec![
        ("正解率".to_string(), Box::new(accuracy)),
        (
            "適合率".to_string(),
            classification_metric(ConfusionMatrix::precision, positive.clone()),
        ),
        (
            "再現率".to_string(),
            classification_metric(ConfusionMatrix::recall, positive.clone()),
        ),
        (
            "F値".to_string(),
            classification_metric(ConfusionMatrix::f1_score, positive),
        ),
    ]
}

/// cinema の評価指標。第 7 章の RMSE・MAE をそのまま使う。
pub fn cinema_metrics() -> Vec<(String, Metric<f64>)> {
    vec![
        ("RMSE".to_string(), Box::new(root_mean_squared_error)),
        ("MAE".to_string(), Box::new(mean_absolute_error)),
    ]
}

/// 交差検証の結果を表示する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let data_dir = dataset::current();

    let survived = data::load_survived(&data_dir.join("Survived.csv"))?;
    let folds = k_fold(survived.x.len(), N_SPLITS, SEED)?;

    writeln!(out, "Survived（決定木・深さ {TREE_DEPTH}）")?;
    writeln!(out, "  件数: {}", survived.x.len())?;

    for (name, metric) in survived_metrics() {
        let scores = cross_validate(
            &|| Box::new(DecisionTree::with_max_depth(TREE_DEPTH)),
            &survived.x,
            &survived.t,
            &folds,
            &metric,
        )?;

        writeln!(
            out,
            "  {name}（{N_SPLITS} 分割の平均）: {:.4}",
            mean(&scores)?
        )?;
    }

    // ROC 曲線には「正例らしさ」の連続値が要るので、確率を返すモデルで最初の分割を採点する
    let fold = &folds[0];
    let x_train: Vec<_> = fold.train.iter().map(|i| survived.x[*i].clone()).collect();
    let t_train: Vec<_> = fold.train.iter().map(|i| survived.t[*i].clone()).collect();
    let x_test: Vec<_> = fold.test.iter().map(|i| survived.x[*i].clone()).collect();
    let t_test: Vec<bool> = fold
        .test
        .iter()
        .map(|i| survived.t[*i] == SURVIVED_POSITIVE)
        .collect();

    let probabilities = positive_probabilities(&x_train, &t_train, &x_test, SURVIVED_POSITIVE)?;
    let curve = roc_curve(&probabilities, &t_test)?;

    writeln!(out, "  ROC 曲線の点の数: {}", curve.len())?;
    writeln!(out, "  AUC（自作）: {:.4}", auc(&curve))?;
    writeln!(
        out,
        "  AUC（linfa）: {:.4}",
        linfa_auc(&probabilities, &t_test)?
    )?;

    // 1 つの分割の混同行列を linfa と並べる
    let mut tree = DecisionTree::with_max_depth(TREE_DEPTH);
    Model::fit(&mut tree, &x_train, &t_train)?;
    let predicted = Model::predict(&tree, &x_test)?;
    let t_test_labels: Vec<String> = fold.test.iter().map(|i| survived.t[*i].clone()).collect();
    let matrix = ConfusionMatrix::of(&t_test_labels, &predicted, &SURVIVED_POSITIVE.to_string())?;
    let library = linfa_scores(&t_test_labels, &predicted)?;

    writeln!(
        out,
        "  混同行列（1 つ目の分割）: TP={} FP={} FN={} TN={}",
        matrix.true_positive, matrix.false_positive, matrix.false_negative, matrix.true_negative
    )?;
    writeln!(
        out,
        "  適合率: 自作 {:.4} / linfa {:.4}",
        matrix.precision(),
        library.precision
    )?;

    let cinema = data::load_cinema(&data_dir.join("cinema.csv"))?;
    let sequential = k_fold_sequential(cinema.x.len(), N_SPLITS)?;
    let shuffled = k_fold(cinema.x.len(), N_SPLITS, SEED)?;

    writeln!(out)?;
    writeln!(out, "cinema（線形回帰）")?;
    writeln!(out, "  件数: {}", cinema.x.len())?;

    for (name, metric) in cinema_metrics() {
        let scores = cross_validate(
            &|| Box::new(LinearRegressionModel::new()),
            &cinema.x,
            &cinema.t,
            &shuffled,
            &metric,
        )?;

        writeln!(
            out,
            "  {name}（{N_SPLITS} 分割の平均）: {:.2}",
            mean(&scores)?
        )?;
    }

    let own = cross_validate(
        &|| Box::new(LinearRegressionModel::new()),
        &cinema.x,
        &cinema.t,
        &sequential,
        &cinema_metrics()[0].1,
    )?;

    writeln!(
        out,
        "  RMSE（並べ替えなし）: 自作 {:.2} / linfa {:.2}",
        mean(&own)?,
        linfa_cross_validate_rmse(&cinema.x, &cinema.t, N_SPLITS)?
    )?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 分類の評価指標は四つある() {
        let metrics = survived_metrics();

        assert_eq!(metrics.len(), 4);
        assert_eq!(metrics[0].0, "正解率");
        assert_eq!(metrics[3].0, "F値");
    }

    #[test]
    fn 回帰の評価指標は二つある() {
        let metrics = cinema_metrics();

        assert_eq!(metrics.len(), 2);
        assert_eq!(metrics[0].0, "RMSE");
        assert_eq!(metrics[1].0, "MAE");
    }
}
