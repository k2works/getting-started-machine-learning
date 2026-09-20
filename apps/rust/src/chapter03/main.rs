//! 第 3 章の実行。深さごとの正解率と、深さ 2 の決定木を表示する。

use std::io::Write;

use super::decisiontree::{DecisionTree, format};
use super::linfatree;
use crate::chapter02::{Features, Result, TrainTestSplit, prepare_iris};
use crate::dataset;

/// テストデータの割合。
const TEST_SIZE: f64 = 0.3;
/// 分割の乱数のシード。
const SEED: u64 = 0;
/// 最後に木そのものを表示する深さ。
const TREE_DEPTH_TO_SHOW: usize = 2;
/// 正解率を比べる深さ。
const MAX_DEPTHS: [usize; 5] = [1, 2, 3, 4, 5];

/// 深さごとの正解率と、深さ 2 の決定木を表示する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let split = prepare_iris(&dataset::current().join("iris.csv"), TEST_SIZE, SEED)?;

    writeln!(out, "深さ\t訓練データ\tテストデータ\tlinfa")?;

    for max_depth in MAX_DEPTHS {
        print_accuracy(
            out,
            &max_depth.to_string(),
            DecisionTree::with_max_depth(max_depth),
            Some(max_depth),
            &split,
        )?;
    }

    print_accuracy(out, "制限なし", DecisionTree::unlimited(), None, &split)?;

    let mut shallow = DecisionTree::with_max_depth(TREE_DEPTH_TO_SHOW);
    shallow.fit(&split.x_train, &split.t_train)?;

    writeln!(out)?;
    writeln!(out, "深さ {TREE_DEPTH_TO_SHOW} の決定木:")?;
    write!(out, "{}", format(shallow.tree().expect("学習していること")))?;

    Ok(())
}

/// 自作と linfa で学習し、正解率を 1 行で表示する。
fn print_accuracy(
    out: &mut impl Write,
    label: &str,
    mut model: DecisionTree,
    max_depth: Option<usize>,
    split: &TrainTestSplit<Features, String>,
) -> Result<()> {
    model.fit(&split.x_train, &split.t_train)?;

    let train = accuracy(&model.predict(&split.x_train)?, &split.t_train);
    let test = accuracy(&model.predict(&split.x_test)?, &split.t_test);
    let library = accuracy(
        &linfatree::predict(&split.x_train, &split.t_train, &split.x_test, max_depth)?,
        &split.t_test,
    );

    writeln!(out, "{label}\t{train:.4}\t{test:.4}\t{library:.4}")?;

    Ok(())
}

/// 予測が正解ラベルと一致した割合を返す。
fn accuracy(predictions: &[String], labels: &[String]) -> f64 {
    let correct = predictions
        .iter()
        .zip(labels)
        .filter(|(prediction, label)| prediction == label)
        .count();

    #[allow(clippy::cast_precision_loss)]
    let ratio = correct as f64 / labels.len() as f64;

    ratio
}
