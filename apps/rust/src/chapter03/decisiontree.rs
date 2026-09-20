//! ジニ不純度で分割する決定木を自作する。

use std::collections::HashMap;
use std::fmt::Write as _;

use crate::chapter02::{Error, Features, Result};

/// 決定木の分割。特徴量の値が境界以下なら左、境界より大きければ右へ進む。
#[derive(Debug, Clone, PartialEq)]
pub struct Split {
    pub feature: String,
    pub threshold: f64,
    pub impurity: f64,
}

/// 決定木。葉か節のどちらか。
/// Rust には判別共用体があるので、Go 版のようなインターフェースの工夫は要らない。
/// 再帰する型なので、部分木は `Box` で包む。
#[derive(Debug, Clone, PartialEq)]
pub enum Tree {
    /// 予測するラベルを持つ葉。
    Leaf { label: String },
    /// 分割と左右の部分木を持つ節。
    Node {
        split: Split,
        left: Box<Tree>,
        right: Box<Tree>,
    },
}

/// ジニ不純度。ラベルが 1 種類なら 0 で、種類が多く均等なほど大きい。
pub fn gini(labels: &[String]) -> f64 {
    if labels.is_empty() {
        return 0.0;
    }

    #[allow(clippy::cast_precision_loss)]
    let total = labels.len() as f64;

    let sum: f64 = counts(labels)
        .iter()
        .map(|(_, count)| {
            #[allow(clippy::cast_precision_loss)]
            let share = *count as f64 / total;

            share * share
        })
        .sum();

    1.0 - sum
}

/// ラベルごとの件数を、最初に現れた順で返す。
fn counts(labels: &[String]) -> Vec<(String, usize)> {
    let mut order: Vec<String> = Vec::new();
    let mut counted: HashMap<&str, usize> = HashMap::new();

    for label in labels {
        if !counted.contains_key(label.as_str()) {
            order.push(label.clone());
        }

        *counted.entry(label.as_str()).or_insert(0) += 1;
    }

    order
        .into_iter()
        .map(|label| {
            let count = counted[label.as_str()];

            (label, count)
        })
        .collect()
}

/// いちばん多いラベルを返す。同数なら先に現れたほうを選ぶ。
///
/// `max_by_key` は同点のとき**最後**の要素を返すので使えない。
/// ほかの言語版と同じ「同数なら先に現れたほう」にするため、厳密な不等号で比べる。
pub fn majority(labels: &[String]) -> String {
    let mut best = String::new();
    let mut best_count = 0;

    for (label, count) in counts(labels) {
        if count > best_count {
            best_count = count;
            best = label;
        }
    }

    best
}

/// 特徴量の値と正解ラベルの組。並べ替えのあいだ対応を保つ。
struct ValueLabel {
    value: f64,
    label: String,
}

/// 左右の不純度の重み付き平均が最も小さくなる分割を返す。
/// 分けられなければ `None`。同じ不純度なら先に見つけた（列の順で前の）分割を選ぶ。
pub fn best_split(x: &[Features], t: &[String]) -> Result<Option<Split>> {
    if x.is_empty() || gini(t) == 0.0 {
        return Ok(None);
    }

    let mut best: Option<Split> = None;

    for feature in &x[0].columns {
        let sorted = sort_by_feature(x, t, feature)?;

        for i in 1..sorted.len() {
            if (sorted[i].value - sorted[i - 1].value).abs() < f64::EPSILON {
                continue;
            }

            let left: Vec<String> = sorted[..i].iter().map(|pair| pair.label.clone()).collect();
            let right: Vec<String> = sorted[i..].iter().map(|pair| pair.label.clone()).collect();

            #[allow(clippy::cast_precision_loss)]
            let impurity = (left.len() as f64 * gini(&left) + right.len() as f64 * gini(&right))
                / sorted.len() as f64;

            if best
                .as_ref()
                .is_none_or(|current| impurity < current.impurity)
            {
                best = Some(Split {
                    feature: feature.clone(),
                    threshold: (sorted[i - 1].value + sorted[i].value) / 2.0,
                    impurity,
                });
            }
        }
    }

    Ok(best)
}

/// 指定した列の値で安定に並べ替える。
fn sort_by_feature(x: &[Features], t: &[String], feature: &str) -> Result<Vec<ValueLabel>> {
    let mut pairs = Vec::with_capacity(x.len());

    for (features, label) in x.iter().zip(t) {
        pairs.push(ValueLabel {
            value: features.value(feature)?,
            label: label.clone(),
        });
    }

    // f64 は全順序ではないので partial_cmp を使う。値が NaN でないことは前処理で保証している
    pairs.sort_by(|a, b| a.value.partial_cmp(&b.value).expect("値が NaN でないこと"));

    Ok(pairs)
}

/// 深さの上限まで分割を繰り返して木を作る。`max_depth` が `None` なら上限なし。
pub fn build(x: &[Features], t: &[String], max_depth: Option<usize>) -> Result<Tree> {
    if max_depth == Some(0) {
        return Ok(Tree::Leaf { label: majority(t) });
    }

    let Some(split) = best_split(x, t)? else {
        return Ok(Tree::Leaf { label: majority(t) });
    };

    let mut left_x = Vec::new();
    let mut left_t = Vec::new();
    let mut right_x = Vec::new();
    let mut right_t = Vec::new();

    for (features, label) in x.iter().zip(t) {
        if goes_left(&split, features)? {
            left_x.push(features.clone());
            left_t.push(label.clone());
        } else {
            right_x.push(features.clone());
            right_t.push(label.clone());
        }
    }

    let next_depth = max_depth.map(|depth| depth - 1);

    Ok(Tree::Node {
        left: Box::new(build(&left_x, &left_t, next_depth)?),
        right: Box::new(build(&right_x, &right_t, next_depth)?),
        split,
    })
}

/// 分割の境界以下なら左へ進む。
fn goes_left(split: &Split, features: &Features) -> Result<bool> {
    Ok(features.value(&split.feature)? <= split.threshold)
}

/// 木をたどって 1 件のラベルを予測する。
pub fn predict_one(tree: &Tree, features: &Features) -> Result<String> {
    match tree {
        Tree::Leaf { label } => Ok(label.clone()),
        Tree::Node { split, left, right } => {
            if goes_left(split, features)? {
                predict_one(left, features)
            } else {
                predict_one(right, features)
            }
        }
    }
}

/// 木を字下げ付きの文字列にする。
pub fn format(tree: &Tree) -> String {
    let mut out = String::new();
    write_tree(&mut out, tree, "");

    out
}

/// 木を再帰的に書き出す。
fn write_tree(out: &mut String, tree: &Tree, indent: &str) {
    match tree {
        Tree::Leaf { label } => {
            let _ = writeln!(out, "{indent}{label}");
        }
        Tree::Node { split, left, right } => {
            let deeper = format!("{indent}  ");

            let _ = writeln!(out, "{indent}{} <= {:.4}", split.feature, split.threshold);
            write_tree(out, left, &deeper);

            let _ = writeln!(out, "{indent}{} > {:.4}", split.feature, split.threshold);
            write_tree(out, right, &deeper);
        }
    }
}

/// 自作の決定木の分類器。`fit` で学習してから `predict` で予測する。
#[derive(Debug, Clone)]
pub struct DecisionTree {
    max_depth: Option<usize>,
    tree: Option<Tree>,
}

impl DecisionTree {
    /// 深さを制限しない決定木を作る。
    pub fn unlimited() -> Self {
        DecisionTree {
            max_depth: None,
            tree: None,
        }
    }

    /// 深さの上限を指定した決定木を作る。
    pub fn with_max_depth(max_depth: usize) -> Self {
        DecisionTree {
            max_depth: Some(max_depth),
            tree: None,
        }
    }

    /// 訓練データから木を作る。
    pub fn fit(&mut self, x: &[Features], t: &[String]) -> Result<&mut Self> {
        self.tree = Some(build(x, t, self.max_depth)?);

        Ok(self)
    }

    /// 学習した木を返す。学習する前は `None`。
    pub fn tree(&self) -> Option<&Tree> {
        self.tree.as_ref()
    }

    /// 特徴量ごとのラベルを予測する。
    pub fn predict(&self, x: &[Features]) -> Result<Vec<String>> {
        let tree = self.tree.as_ref().ok_or(Error::NotFitted)?;

        x.iter()
            .map(|features| predict_one(tree, features))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 1 列だけの特徴量を作る。
    fn column(value: f64) -> Features {
        Features::new(vec!["花弁幅".to_string()], vec![value]).unwrap()
    }

    /// 文字列のスライスをラベルのベクタにする。
    fn labels(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    /// 3 種類に分かれる小さなデータ。
    fn three_species() -> (Vec<Features>, Vec<String>) {
        (
            vec![
                column(0.2),
                column(0.3),
                column(1.3),
                column(1.5),
                column(2.3),
                column(2.5),
            ],
            labels(&[
                "setosa",
                "setosa",
                "versicolor",
                "versicolor",
                "virginica",
                "virginica",
            ]),
        )
    }

    #[test]
    fn 一種類だけならジニ不純度は零になる() {
        assert!((gini(&labels(&["setosa", "setosa"]))).abs() < 1e-12);
    }

    #[test]
    fn 二種類が半々ならジニ不純度は零点五になる() {
        assert!((gini(&labels(&["setosa", "virginica"])) - 0.5).abs() < 1e-12);
    }

    #[test]
    fn 三種類が均等ならジニ不純度は三分の二になる() {
        let value = gini(&labels(&["setosa", "versicolor", "virginica"]));

        assert!((value - 2.0 / 3.0).abs() < 1e-12);
    }

    #[test]
    fn いちばん多いラベルを返す() {
        assert_eq!(
            majority(&labels(&["setosa", "virginica", "setosa"])),
            "setosa"
        );
    }

    #[test]
    fn 同数なら先に現れたラベルを返す() {
        assert_eq!(majority(&labels(&["virginica", "setosa"])), "virginica");
    }

    #[test]
    fn 分けられないときは分割を返さない() {
        let x = vec![column(0.2), column(0.3)];
        let t = labels(&["setosa", "setosa"]);

        assert_eq!(best_split(&x, &t).unwrap(), None);
    }

    #[test]
    fn 不純度がいちばん小さくなる分割を選ぶ() {
        let (x, t) = three_species();

        let split = best_split(&x, &t).unwrap().expect("分割が見つかること");

        assert_eq!(split.feature, "花弁幅");
        assert!((split.threshold - 0.8).abs() < 1e-12);
    }

    #[test]
    fn 深さを制限しなければ訓練データを全部当てる() {
        let (x, t) = three_species();

        let mut model = DecisionTree::unlimited();
        let predictions = model.fit(&x, &t).unwrap().predict(&x).unwrap();

        assert_eq!(predictions, t);
    }

    #[test]
    fn 深さ一なら二つの葉になる() {
        let (x, t) = three_species();

        let mut model = DecisionTree::with_max_depth(1);
        model.fit(&x, &t).unwrap();

        match model.tree().expect("学習していること") {
            Tree::Node { left, right, .. } => {
                assert!(matches!(**left, Tree::Leaf { .. }));
                assert!(matches!(**right, Tree::Leaf { .. }));
            }
            Tree::Leaf { .. } => panic!("節になること"),
        }
    }

    #[test]
    fn 学習する前は予測できない() {
        let model = DecisionTree::with_max_depth(1);

        assert_eq!(
            model.predict(&[column(0.2)]).unwrap_err().to_string(),
            "学習してから予測してください"
        );
    }

    #[test]
    fn 木を字下げ付きの文字列にする() {
        let (x, t) = three_species();

        let mut model = DecisionTree::with_max_depth(1);
        model.fit(&x, &t).unwrap();

        assert_eq!(
            format(model.tree().unwrap()),
            "花弁幅 <= 0.8000\n  setosa\n花弁幅 > 0.8000\n  versicolor\n"
        );
    }
}
