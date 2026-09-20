//! K 分割交差検証。行を k 個のかたまりに分け、1 つをテストデータ、残りを訓練データにする。

use crate::chapter02::{Error, Features, Result, shuffle};

use super::metrics::Metric;
use super::model::Model;

/// 交差検証の 1 回分の分け方。行の位置（0 始まり）を訓練データとテストデータに分けて持つ。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Fold {
    pub train: Vec<usize>,
    pub test: Vec<usize>,
}

/// 並べ替えずに、先頭から順に k 個のかたまりに分ける。
///
/// 件数が割り切れないときは、余りを先頭の分割から 1 件ずつ配る。linfa の `cross_validate` は
/// この分け方（並べ替えなし）なので、突き合わせるときはこちらを使う。
pub fn k_fold_sequential(n_samples: usize, n_splits: usize) -> Result<Vec<Fold>> {
    if n_splits < 2 || n_splits > n_samples {
        return Err(Error::Library(format!(
            "分割の数は 2 以上 {n_samples} 以下にしてください: {n_splits}"
        )));
    }

    folds(&(0..n_samples).collect::<Vec<usize>>(), n_splits)
}

/// シード付きの乱数で行を並べ替えてから k 個のかたまりに分ける。
pub fn k_fold(n_samples: usize, n_splits: usize, seed: u64) -> Result<Vec<Fold>> {
    if n_splits < 2 || n_splits > n_samples {
        return Err(Error::Library(format!(
            "分割の数は 2 以上 {n_samples} 以下にしてください: {n_splits}"
        )));
    }

    // 第 2 章の Fisher-Yates を位置のベクタに使い回す
    folds(
        &shuffle(&(0..n_samples).collect::<Vec<usize>>(), seed),
        n_splits,
    )
}

/// 並べた位置を k 個のかたまりに分ける。
fn folds(positions: &[usize], n_splits: usize) -> Result<Vec<Fold>> {
    let mut result = Vec::with_capacity(n_splits);
    let mut from = 0;

    for index in 0..n_splits {
        let size = positions.len() / n_splits + usize::from(index < positions.len() % n_splits);
        let to = from + size;
        let test = positions[from..to].to_vec();
        let train = positions
            .iter()
            .filter(|position| !test.contains(position))
            .copied()
            .collect();

        result.push(Fold { train, test });
        from = to;
    }

    Ok(result)
}

/// 位置の並びで値を選び出す。
fn pick<E: Clone>(values: &[E], positions: &[usize]) -> Vec<E> {
    positions
        .iter()
        .map(|index| values[*index].clone())
        .collect()
}

/// 分割ごとに新しいモデルを作って訓練データで学習し、テストデータの予測を評価関数で採点する。
///
/// Java 版は `Supplier<Model<T>>` を受け取ったが、Rust では「モデルを作る閉包」を
/// `&dyn Fn() -> Box<dyn Model<T>>` で受け取る。分割ごとに新しいモデルを作るので、
/// 前の分割で学習した重みが残らない。
pub fn cross_validate<T: Clone>(
    make_model: &dyn Fn() -> Box<dyn Model<T>>,
    x: &[Features],
    t: &[T],
    folds: &[Fold],
    metric: &Metric<T>,
) -> Result<Vec<f64>> {
    folds
        .iter()
        .map(|fold| {
            let mut model = make_model();

            model.fit(&pick(x, &fold.train), &pick(t, &fold.train))?;

            let predicted = model.predict(&pick(x, &fold.test))?;

            metric(&pick(t, &fold.test), &predicted)
        })
        .collect()
}

/// スコアの平均。
pub fn mean(scores: &[f64]) -> Result<f64> {
    if scores.is_empty() {
        return Err(Error::Library("スコアが 1 件もありません".to_string()));
    }

    #[allow(clippy::cast_precision_loss)]
    let size = scores.len() as f64;

    Ok(scores.iter().sum::<f64>() / size)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 割り切れる件数は同じ大きさに分かれる() {
        let folds = k_fold_sequential(6, 3).unwrap();

        assert_eq!(folds.len(), 3);
        assert_eq!(folds[0].test, vec![0, 1]);
        assert_eq!(folds[1].test, vec![2, 3]);
        assert_eq!(folds[2].test, vec![4, 5]);
        assert_eq!(folds[0].train, vec![2, 3, 4, 5]);
    }

    #[test]
    fn 余りは先頭の分割に一件ずつ配られる() {
        let folds = k_fold_sequential(7, 3).unwrap();

        assert_eq!(folds[0].test.len(), 3);
        assert_eq!(folds[1].test.len(), 2);
        assert_eq!(folds[2].test.len(), 2);
    }

    #[test]
    fn テストデータは重ならず全体を覆う() {
        let folds = k_fold(10, 5, 0).unwrap();
        let mut all: Vec<usize> = folds.iter().flat_map(|fold| fold.test.clone()).collect();
        all.sort_unstable();

        assert_eq!(all, (0..10).collect::<Vec<usize>>());
    }

    #[test]
    fn 訓練データとテストデータは交わらない() {
        for fold in k_fold(10, 5, 0).unwrap() {
            assert_eq!(fold.train.len(), 8);

            for position in &fold.test {
                assert!(!fold.train.contains(position));
            }
        }
    }

    #[test]
    fn 分割の数が少なすぎると失敗する() {
        let error = k_fold(10, 1, 0).unwrap_err();

        assert_eq!(
            error.to_string(),
            "ライブラリが失敗しました: 分割の数は 2 以上 10 以下にしてください: 1"
        );
    }

    #[test]
    fn 分割の数が件数より多いと失敗する() {
        assert!(k_fold_sequential(3, 4).is_err());
    }

    #[test]
    fn スコアが無ければ平均を求められない() {
        assert!(mean(&[]).is_err());
    }
}
