//! 分割で減った不純度から、特徴量の重要度を求める。

use crate::chapter02::Features;
use crate::chapter03::{Tree, gini};

use super::Result;
use super::forest::RandomForest;

/// 決定木 1 本の重要度。特徴量の列の順に並べ、合計が 1 になるようにする。
pub fn tree_importances(tree: &Tree, x: &[Features], t: &[String]) -> Result<Vec<(String, f64)>> {
    let columns = x
        .first()
        .map(|features| features.columns.clone())
        .unwrap_or_default();
    let mut totals: Vec<(String, f64)> = columns.into_iter().map(|column| (column, 0.0)).collect();

    accumulate(tree, x, t, &mut totals)?;

    Ok(normalize(totals))
}

/// 木ごとの重要度の平均。木が使わなかった特徴量は、その木では 0 とする。
pub fn forest_importances(
    forest: &RandomForest,
    x: &[Features],
    t: &[String],
) -> Result<Vec<(String, f64)>> {
    let columns = x
        .first()
        .map(|features| features.columns.clone())
        .unwrap_or_default();
    let mut totals: Vec<(String, f64)> = columns.into_iter().map(|column| (column, 0.0)).collect();

    #[allow(clippy::cast_precision_loss)]
    let count = forest.trees().len() as f64;

    for fitted in forest.trees() {
        let Some(tree) = fitted.model.tree() else {
            continue;
        };

        let sampled: Vec<Features> = fitted.rows.iter().map(|row| x[*row].clone()).collect();
        let sample_x = RandomForest::select_columns(&sampled, &fitted.columns)?;
        let sample_t: Vec<String> = fitted.rows.iter().map(|row| t[*row].clone()).collect();

        // 木ごとに正規化してから平均する
        for (feature, value) in tree_importances(tree, &sample_x, &sample_t)? {
            if let Some(entry) = totals.iter_mut().find(|(name, _)| *name == feature) {
                entry.1 += value / count;
            }
        }
    }

    Ok(normalize(totals))
}

/// 木をたどって、分割ごとに減った不純度（件数で重み付け）を足し込む。
fn accumulate(
    tree: &Tree,
    x: &[Features],
    t: &[String],
    totals: &mut Vec<(String, f64)>,
) -> Result<()> {
    let Tree::Node { split, left, right } = tree else {
        return Ok(());
    };

    #[allow(clippy::cast_precision_loss)]
    let decrease = t.len() as f64 * (gini(t) - split.impurity);

    if let Some(entry) = totals.iter_mut().find(|(name, _)| *name == split.feature) {
        entry.1 += decrease;
    }

    let mut left_x = Vec::new();
    let mut left_t = Vec::new();
    let mut right_x = Vec::new();
    let mut right_t = Vec::new();

    for (features, label) in x.iter().zip(t) {
        if features.value(&split.feature)? <= split.threshold {
            left_x.push(features.clone());
            left_t.push(label.clone());
        } else {
            right_x.push(features.clone());
            right_t.push(label.clone());
        }
    }

    accumulate(left, &left_x, &left_t, totals)?;
    accumulate(right, &right_x, &right_t, totals)
}

/// 合計が 1 になるように割る。合計が 0 ならそのまま返す。
fn normalize(totals: Vec<(String, f64)>) -> Vec<(String, f64)> {
    let total: f64 = totals.iter().map(|(_, value)| value).sum();

    if total == 0.0 {
        return totals;
    }

    totals
        .into_iter()
        .map(|(feature, value)| (feature, value / total))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::chapter03::DecisionTree;
    use crate::chapter10::classifier::Classifier;

    fn labels(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    fn row(width: f64, length: f64) -> Features {
        Features::new(
            vec!["花弁幅".to_string(), "花弁長さ".to_string()],
            vec![width, length],
        )
        .unwrap()
    }

    /// 花弁幅だけで分かれるデータ。
    fn separable() -> (Vec<Features>, Vec<String>) {
        (
            vec![row(0.2, 1.0), row(0.3, 1.0), row(2.3, 1.0), row(2.5, 1.0)],
            labels(&["setosa", "setosa", "virginica", "virginica"]),
        )
    }

    #[test]
    fn 使った特徴量だけが重要度を持つ() {
        let (x, t) = separable();
        let mut model = DecisionTree::with_max_depth(1);
        Classifier::fit(&mut model, &x, &t).unwrap();

        let importances = tree_importances(model.tree().unwrap(), &x, &t).unwrap();

        assert_eq!(importances[0].0, "花弁幅");
        assert!((importances[0].1 - 1.0).abs() < 1e-12);
        assert!(importances[1].1.abs() < 1e-12);
    }

    #[test]
    fn 重要度の合計は1になる() {
        let (x, t) = separable();
        let mut forest = crate::chapter10::RandomForest::new(5, 2, 0);
        Classifier::fit(&mut forest, &x, &t).unwrap();

        let importances = forest_importances(&forest, &x, &t).unwrap();
        let total: f64 = importances.iter().map(|(_, value)| value).sum();

        assert!((total - 1.0).abs() < 1e-12);
        assert_eq!(importances.len(), 2);
    }

    #[test]
    fn 葉だけの木は重要度を持たない() {
        let x = vec![row(0.2, 1.0), row(0.3, 1.0)];
        let t = labels(&["setosa", "setosa"]);
        let mut model = DecisionTree::unlimited();
        Classifier::fit(&mut model, &x, &t).unwrap();

        let importances = tree_importances(model.tree().unwrap(), &x, &t).unwrap();

        assert!(importances.iter().all(|(_, value)| value.abs() < 1e-12));
    }
}
