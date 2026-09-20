//! クラスの重みを付けた決定木。第 3 章の決定木に 1 件ごとの重みを足したもの。
//!
//! 第 3 章の `Tree` を使い回さないのは、ラベルが整数であることと、JSON に保存するために
//! `Serialize`・`Deserialize` を導出する必要があるため（他の章の型は変えない）。

use serde::{Deserialize, Serialize};

use crate::chapter02::{Error, Features, Result};

/// クラスの重みの付け方。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ClassWeight {
    /// 重みを付けない（すべて 1）。
    None,
    /// クラスの件数に反比例する重みを付ける。
    Balanced,
}

impl ClassWeight {
    /// 表示に使う名前。
    pub fn name(self) -> &'static str {
        match self {
            ClassWeight::None => "none",
            ClassWeight::Balanced => "balanced",
        }
    }
}

/// 重み付きの決定木。葉か節のどちらか。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum Tree {
    /// 予測するラベルを持つ葉。
    Leaf { label: i32 },
    /// 分割と左右の部分木を持つ節。
    Node {
        feature: String,
        threshold: f64,
        left: Box<Tree>,
        right: Box<Tree>,
    },
}

/// 分割の候補。
#[derive(Debug, Clone, PartialEq)]
struct Split {
    feature: String,
    threshold: f64,
    impurity: f64,
}

/// ラベルごとの重みの合計を、ラベルが先に現れた順に返す。
fn weight_sums(labels: &[i32], weights: &[f64]) -> Vec<(i32, f64)> {
    let mut sums: Vec<(i32, f64)> = Vec::new();

    for (label, weight) in labels.iter().zip(weights) {
        match sums.iter_mut().find(|(name, _)| name == label) {
            Some((_, sum)) => *sum += weight,
            None => sums.push((*label, *weight)),
        }
    }

    sums
}

/// 重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。
pub fn weighted_gini(labels: &[i32], weights: &[f64]) -> f64 {
    let total: f64 = weights.iter().sum();

    if total == 0.0 {
        return 0.0;
    }

    1.0 - weight_sums(labels, weights)
        .iter()
        .map(|(_, weight)| (weight / total).powi(2))
        .sum::<f64>()
}

/// クラスの件数に反比例する重み（件数 ÷（クラスの数 × そのクラスの件数））を 1 件ごとに求める。
pub fn balanced_weights(t: &[i32]) -> Vec<f64> {
    let ones = vec![1.0; t.len()];
    let counts = weight_sums(t, &ones);

    #[allow(clippy::cast_precision_loss)]
    let total = t.len() as f64;
    #[allow(clippy::cast_precision_loss)]
    let classes = counts.len() as f64;

    t.iter()
        .map(|label| {
            let count = counts
                .iter()
                .find(|(name, _)| name == label)
                .map_or(1.0, |(_, count)| *count);

            total / (classes * count)
        })
        .collect()
}

/// クラスの重みの付け方から、1 件ごとの重みを求める。
pub fn weights_of(t: &[i32], class_weight: ClassWeight) -> Vec<f64> {
    match class_weight {
        ClassWeight::None => vec![1.0; t.len()],
        ClassWeight::Balanced => balanced_weights(t),
    }
}

/// 重みの合計が最も大きいラベル。同じなら先に現れたラベルを選ぶ。
fn weighted_majority(labels: &[i32], weights: &[f64]) -> i32 {
    let mut best = labels.first().copied().unwrap_or_default();
    let mut best_weight = 0.0;

    for (label, weight) in weight_sums(labels, weights) {
        if weight > best_weight {
            best = label;
            best_weight = weight;
        }
    }

    best
}

/// 特徴量の値で並べ替えた、値・ラベル・重みの組。
fn sort_by_feature(
    x: &[Features],
    t: &[i32],
    w: &[f64],
    feature: &str,
) -> Result<Vec<(f64, i32, f64)>> {
    let mut sorted = Vec::with_capacity(x.len());

    for ((features, label), weight) in x.iter().zip(t).zip(w) {
        sorted.push((features.value(feature)?, *label, *weight));
    }

    sorted.sort_by(|a, b| a.0.partial_cmp(&b.0).expect("値が NaN でないこと"));

    Ok(sorted)
}

/// 左右の重み付き不純度の、重みによる平均が最も小さくなる分割を返す。
fn best_split(x: &[Features], t: &[i32], w: &[f64]) -> Result<Option<Split>> {
    if x.is_empty() || weighted_gini(t, w) == 0.0 {
        return Ok(None);
    }

    let mut best: Option<Split> = None;

    for feature in &x[0].columns {
        let sorted = sort_by_feature(x, t, w, feature)?;
        let labels: Vec<i32> = sorted.iter().map(|(_, label, _)| *label).collect();
        let weights: Vec<f64> = sorted.iter().map(|(_, _, weight)| *weight).collect();
        let total: f64 = weights.iter().sum();

        for i in 1..sorted.len() {
            if (sorted[i].0 - sorted[i - 1].0).abs() < f64::EPSILON {
                continue;
            }

            let (left, right) = weights.split_at(i);
            let impurity = (left.iter().sum::<f64>() * weighted_gini(&labels[..i], left)
                + right.iter().sum::<f64>() * weighted_gini(&labels[i..], right))
                / total;

            if best
                .as_ref()
                .is_none_or(|current| impurity < current.impurity)
            {
                best = Some(Split {
                    feature: feature.clone(),
                    threshold: (sorted[i - 1].0 + sorted[i].0) / 2.0,
                    impurity,
                });
            }
        }
    }

    Ok(best)
}

/// 深さの上限まで分割を繰り返して木を作る。`max_depth` が `None` なら上限なし。
pub fn build(x: &[Features], t: &[i32], w: &[f64], max_depth: Option<usize>) -> Result<Tree> {
    let split = if max_depth == Some(0) {
        None
    } else {
        best_split(x, t, w)?
    };

    let Some(split) = split else {
        return Ok(Tree::Leaf {
            label: weighted_majority(t, w),
        });
    };

    let mut left = (Vec::new(), Vec::new(), Vec::new());
    let mut right = (Vec::new(), Vec::new(), Vec::new());

    for ((features, label), weight) in x.iter().zip(t).zip(w) {
        let side = if features.value(&split.feature)? <= split.threshold {
            &mut left
        } else {
            &mut right
        };

        side.0.push(features.clone());
        side.1.push(*label);
        side.2.push(*weight);
    }

    let next_depth = max_depth.map(|depth| depth - 1);

    Ok(Tree::Node {
        left: Box::new(build(&left.0, &left.1, &left.2, next_depth)?),
        right: Box::new(build(&right.0, &right.1, &right.2, next_depth)?),
        feature: split.feature,
        threshold: split.threshold,
    })
}

/// 木をたどって 1 件のラベルを予測する。
pub fn predict_one(tree: &Tree, features: &Features) -> Result<i32> {
    match tree {
        Tree::Leaf { label } => Ok(*label),
        Tree::Node {
            feature,
            threshold,
            left,
            right,
        } => {
            if features.value(feature)? <= *threshold {
                predict_one(left, features)
            } else {
                predict_one(right, features)
            }
        }
    }
}

/// クラスの重みを付けられる決定木の分類器。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DecisionTreeClassifier {
    pub max_depth: Option<usize>,
    pub class_weight: ClassWeight,
}

impl DecisionTreeClassifier {
    /// 深さの上限とクラスの重みを指定した分類器を作る。
    pub fn new(max_depth: Option<usize>, class_weight: ClassWeight) -> Self {
        DecisionTreeClassifier {
            max_depth,
            class_weight,
        }
    }

    /// 訓練データから学習済みの木を作る。
    pub fn fit(&self, x: &[Features], t: &[i32]) -> Result<FittedDecisionTree> {
        if x.len() != t.len() {
            return Err(Error::LengthMismatch {
                left: x.len(),
                right: t.len(),
            });
        }

        let weights = weights_of(t, self.class_weight);

        Ok(FittedDecisionTree {
            root: build(x, t, &weights, self.max_depth)?,
        })
    }
}

/// 学習済みの決定木。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct FittedDecisionTree {
    pub root: Tree,
}

impl FittedDecisionTree {
    /// 特徴量ごとのラベルを予測する。
    pub fn predict(&self, x: &[Features]) -> Result<Vec<i32>> {
        x.iter()
            .map(|features| predict_one(&self.root, features))
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 1 列だけの特徴量を作る。
    fn column(value: f64) -> Features {
        Features::new(vec!["Fare".to_string()], vec![value]).unwrap()
    }

    /// 運賃で生死が分かれる、偏ったデータ。
    fn imbalanced() -> (Vec<Features>, Vec<i32>) {
        (
            vec![
                column(5.0),
                column(6.0),
                column(7.0),
                column(8.0),
                column(80.0),
            ],
            vec![0, 0, 0, 0, 1],
        )
    }

    #[test]
    fn 重みが等しければ普通のジニ不純度になる() {
        let gini = weighted_gini(&[0, 1], &[1.0, 1.0]);

        assert!((gini - 0.5).abs() < 1e-12, "gini = {gini}");
    }

    #[test]
    fn 重みが偏ると不純度も偏る() {
        let gini = weighted_gini(&[0, 1], &[3.0, 1.0]);

        assert!((gini - 0.375).abs() < 1e-12, "gini = {gini}");
    }

    #[test]
    fn 少数派のクラスに大きな重みが付く() {
        let weights = balanced_weights(&[0, 0, 0, 1]);

        assert!((weights[0] - 4.0 / 6.0).abs() < 1e-12, "{weights:?}");
        assert!((weights[3] - 2.0).abs() < 1e-12, "{weights:?}");
    }

    #[test]
    fn 重みを付けなければすべて一になる() {
        assert_eq!(weights_of(&[0, 1], ClassWeight::None), vec![1.0, 1.0]);
    }

    #[test]
    fn 深さ零なら重みの大きいラベルの葉になる() {
        let (x, t) = imbalanced();

        let tree = DecisionTreeClassifier::new(Some(0), ClassWeight::None)
            .fit(&x, &t)
            .unwrap();

        assert_eq!(tree.root, Tree::Leaf { label: 0 });
    }

    #[test]
    fn 重みを付けると少数派を答える葉になる() {
        let (x, t) = imbalanced();

        let tree = DecisionTreeClassifier::new(Some(0), ClassWeight::Balanced)
            .fit(&x, &t)
            .unwrap();

        // balanced では 1 件の生存（重み 2.5）が 4 件の死亡（重み 0.625 × 4）と同じ重みになり、
        // 先に現れた 0 が選ばれる。重みを 1 件だけ増やせば 1 が選ばれる
        assert_eq!(tree.root, Tree::Leaf { label: 0 });

        let weighted = build(&x, &t, &[0.5, 0.5, 0.5, 0.5, 2.5], Some(0)).unwrap();

        assert_eq!(weighted, Tree::Leaf { label: 1 });
    }

    #[test]
    fn 深さを制限しなければ訓練データを全部当てる() {
        let (x, t) = imbalanced();

        let tree = DecisionTreeClassifier::new(None, ClassWeight::None)
            .fit(&x, &t)
            .unwrap();

        assert_eq!(tree.predict(&x).unwrap(), t);
    }

    #[test]
    fn 特徴量と正解ラベルの件数が違えば学習できない() {
        let x = vec![column(1.0)];

        assert_eq!(
            DecisionTreeClassifier::new(None, ClassWeight::None)
                .fit(&x, &[0, 1])
                .unwrap_err()
                .to_string(),
            "件数が違います: 1 と 2"
        );
    }

    #[test]
    fn クラスの重みの名前を表示できる() {
        assert_eq!(ClassWeight::None.name(), "none");
        assert_eq!(ClassWeight::Balanced.name(), "balanced");
    }
}
