//! 第 10 章の実行。モデルごとの正解率と、ランダムフォレストの特徴量の重要度を表示する。

use std::io::Write;

use crate::chapter02::prepare_iris;
use crate::chapter03::DecisionTree;
use crate::dataset;

use super::Result;
use super::classifier::{Classifier, Score};
use super::forest::RandomForest;
use super::importance::forest_importances;
use super::linfalogistic::{LinfaLogisticRegression, WEAK_PENALTY};
use super::logistic::LogisticRegression;

/// テストデータの割合。
const TEST_SIZE: f64 = 0.3;
/// 分割の乱数のシード。
const SEED: u64 = 0;
/// 森の木の本数。
const N_ESTIMATORS: usize = 100;
/// 木ごとに使う特徴量の数。
const MAX_FEATURES: usize = 2;
/// 浅い木の深さ。
const SHALLOW_DEPTH: usize = 2;
/// linfa のロジスティック回帰の繰り返し回数。
const LINFA_ITERATIONS: u64 = 1000;

/// 名前とモデル。表示する順に並べる。
pub fn models() -> Vec<(String, Box<dyn Classifier>)> {
    vec![
        (
            format!("決定木（深さ {SHALLOW_DEPTH}）"),
            Box::new(DecisionTree::with_max_depth(SHALLOW_DEPTH)),
        ),
        (
            "ロジスティック回帰".to_string(),
            Box::new(LogisticRegression::default()),
        ),
        (
            format!("ランダムフォレスト（{N_ESTIMATORS} 本）"),
            Box::new(RandomForest::new(N_ESTIMATORS, MAX_FEATURES, SEED)),
        ),
        (
            format!("ランダムフォレスト（{N_ESTIMATORS} 本・深さ {SHALLOW_DEPTH}）"),
            Box::new(RandomForest::with_max_depth(
                N_ESTIMATORS,
                MAX_FEATURES,
                SHALLOW_DEPTH,
                SEED,
            )),
        ),
        (
            "linfa ロジスティック回帰".to_string(),
            Box::new(LinfaLogisticRegression::new(LINFA_ITERATIONS, WEAK_PENALTY)),
        ),
    ]
}

/// モデルごとの正解率と、ランダムフォレストの特徴量の重要度を表示する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let split = prepare_iris(&dataset::current().join("iris.csv"), TEST_SIZE, SEED)?;

    writeln!(out, "モデル\t訓練データ\tテストデータ")?;

    for (name, mut model) in models() {
        let score = Score::evaluate(model.as_mut(), &split)?;

        writeln!(out, "{name}\t{:.4}\t{:.4}", score.train, score.test)?;
    }

    let mut forest = RandomForest::new(N_ESTIMATORS, MAX_FEATURES, SEED);
    forest.fit(&split.x_train, &split.t_train)?;

    writeln!(out)?;
    writeln!(
        out,
        "ランダムフォレスト（{N_ESTIMATORS} 本）の特徴量の重要度:"
    )?;

    for (feature, value) in forest_importances(&forest, &split.x_train, &split.t_train)? {
        writeln!(out, "{feature}\t{value:.4}")?;
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn モデルは5つある() {
        let models = models();

        assert_eq!(models.len(), 5);
        assert_eq!(models[0].0, "決定木（深さ 2）");
        assert_eq!(models[4].0, "linfa ロジスティック回帰");
    }
}
