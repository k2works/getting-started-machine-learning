//! 第 3 章の決定木をブートストラップ標本と特徴量の部分集合で学習し、多数決で予測する
//! ランダムフォレスト。linfa には無いので、自作が最終の実装になる（ADR 009）。

use rand::rngs::StdRng;
use rand::{Rng, SeedableRng};

use crate::chapter02::{Error, Features};
use crate::chapter03::DecisionTree;

use super::Result;
use super::classifier::Classifier;

/// ランダムフォレストの 1 本分。使った特徴量の列、ブートストラップ標本の行番号、学習した決定木。
#[derive(Debug, Clone)]
pub struct FittedTree {
    pub columns: Vec<String>,
    pub rows: Vec<usize>,
    pub model: DecisionTree,
}

/// 決定木の森。
#[derive(Debug, Clone)]
pub struct RandomForest {
    n_estimators: usize,
    max_features: usize,
    max_depth: Option<usize>,
    seed: u64,
    trees: Vec<FittedTree>,
}

impl RandomForest {
    /// 深さを制限しない決定木の森。
    pub fn new(n_estimators: usize, max_features: usize, seed: u64) -> Self {
        RandomForest {
            n_estimators,
            max_features,
            max_depth: None,
            seed,
            trees: Vec::new(),
        }
    }

    /// 深さの上限を指定した決定木の森。
    pub fn with_max_depth(
        n_estimators: usize,
        max_features: usize,
        max_depth: usize,
        seed: u64,
    ) -> Self {
        RandomForest {
            max_depth: Some(max_depth),
            ..RandomForest::new(n_estimators, max_features, seed)
        }
    }

    /// 学習した決定木。
    pub fn trees(&self) -> &[FittedTree] {
        &self.trees
    }

    /// 特徴量から、指定した列だけを取り出す。
    pub fn select_columns(x: &[Features], columns: &[String]) -> Result<Vec<Features>> {
        x.iter()
            .map(|features| {
                let values = columns
                    .iter()
                    .map(|column| features.value(column))
                    .collect::<Result<Vec<f64>>>()?;

                Features::new(columns.to_vec(), values)
            })
            .collect()
    }
}

/// サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。
pub fn majority_vote(votes: &[Vec<String>]) -> Vec<String> {
    let Some(first) = votes.first() else {
        return Vec::new();
    };

    (0..first.len())
        .map(|sample| {
            let labels: Vec<String> = votes.iter().map(|vote| vote[sample].clone()).collect();

            crate::chapter03::majority(&labels)
        })
        .collect()
}

/// 0 から size - 1 までの行番号を、重複を許して size 個選ぶ。
/// 再現できる森のための擬似乱数で、暗号用途ではない。
fn bootstrap_sample(size: usize, rng: &mut StdRng) -> Vec<usize> {
    (0..size).map(|_| rng.gen_range(0..size)).collect()
}

/// Fisher-Yates で並べ替えて、先頭から `count` 個を選ぶ。
fn choose(columns: &[String], count: usize, rng: &mut StdRng) -> Vec<String> {
    let mut shuffled = columns.to_vec();

    for i in (1..shuffled.len()).rev() {
        let j = rng.gen_range(0..(i + 1));
        shuffled.swap(i, j);
    }

    let chosen = &shuffled[..count.min(shuffled.len())];

    // 列の順は元のまま残す
    columns
        .iter()
        .filter(|column| chosen.contains(column))
        .cloned()
        .collect()
}

impl Classifier for RandomForest {
    fn fit(&mut self, x: &[Features], t: &[String]) -> Result<()> {
        let first = x.first().ok_or(Error::NotFitted)?;
        let all_columns = first.columns.clone();
        let mut rng = StdRng::seed_from_u64(self.seed);
        let mut trees = Vec::with_capacity(self.n_estimators);

        for _ in 0..self.n_estimators {
            let rows = bootstrap_sample(x.len(), &mut rng);
            let columns = choose(&all_columns, self.max_features, &mut rng);

            let sampled: Vec<Features> = rows.iter().map(|row| x[*row].clone()).collect();
            let sample_x = RandomForest::select_columns(&sampled, &columns)?;
            let sample_t: Vec<String> = rows.iter().map(|row| t[*row].clone()).collect();

            let mut model = match self.max_depth {
                Some(max_depth) => DecisionTree::with_max_depth(max_depth),
                None => DecisionTree::unlimited(),
            };

            DecisionTree::fit(&mut model, &sample_x, &sample_t)?;

            trees.push(FittedTree {
                columns,
                rows,
                model,
            });
        }

        self.trees = trees;

        Ok(())
    }

    fn predict(&self, x: &[Features]) -> Result<Vec<String>> {
        if self.trees.is_empty() {
            return Err(Error::NotFitted);
        }

        let votes = self
            .trees
            .iter()
            .map(|tree| {
                DecisionTree::predict(
                    &tree.model,
                    &RandomForest::select_columns(x, &tree.columns)?,
                )
            })
            .collect::<Result<Vec<Vec<String>>>>()?;

        Ok(majority_vote(&votes))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

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

    #[test]
    fn 多数決で予測を1つに決める() {
        let votes = vec![
            labels(&["a", "b"]),
            labels(&["a", "c"]),
            labels(&["b", "c"]),
        ];

        assert_eq!(majority_vote(&votes), labels(&["a", "c"]));
    }

    #[test]
    fn 同数なら先に現れた予測を選ぶ() {
        let votes = vec![labels(&["b"]), labels(&["a"])];

        assert_eq!(majority_vote(&votes), labels(&["b"]));
    }

    #[test]
    fn ブートストラップ標本は同じ件数で重複を許す() {
        let mut rng = StdRng::seed_from_u64(0);
        let sample = bootstrap_sample(10, &mut rng);

        assert_eq!(sample.len(), 10);
        assert!(sample.iter().all(|index| *index < 10));
        // 重複を許すので、10 件のうち種類は 10 より少なくなる（シード 0 での実測）
        let mut unique = sample.clone();
        unique.sort_unstable();
        unique.dedup();
        assert!(unique.len() < 10);
    }

    #[test]
    fn 特徴量の部分集合は元の列の順を保つ() {
        let mut rng = StdRng::seed_from_u64(0);
        let columns = labels(&["a", "b", "c", "d"]);

        let chosen = choose(&columns, 2, &mut rng);

        assert_eq!(chosen.len(), 2);
        assert!(chosen.windows(2).all(|pair| pair[0] < pair[1]));
    }

    #[test]
    fn 指定した列だけを取り出す() {
        let selected =
            RandomForest::select_columns(&[row(0.2, 1.4)], &["花弁長さ".to_string()]).unwrap();

        assert_eq!(selected[0].columns, vec!["花弁長さ".to_string()]);
        assert_eq!(selected[0].values, vec![1.4]);
    }

    #[test]
    fn 第3章の決定木を束ねて予測する() {
        let x = vec![row(0.2, 1.4), row(0.3, 1.5), row(2.3, 5.5), row(2.5, 5.7)];
        let t = labels(&["setosa", "setosa", "virginica", "virginica"]);

        let mut forest = RandomForest::new(10, 1, 0);
        forest.fit(&x, &t).unwrap();

        assert_eq!(forest.trees().len(), 10);
        assert_eq!(forest.predict(&x).unwrap(), t);
    }

    #[test]
    fn 学習する前に予測すると失敗する() {
        let forest = RandomForest::new(10, 1, 0);

        assert_eq!(
            forest.predict(&[row(0.2, 1.4)]).unwrap_err().to_string(),
            "学習してから予測してください"
        );
    }
}
