//! linfa-reduction の `Pca` で主成分分析し、自作と突き合わせる。

use linfa::prelude::*;
use linfa_reduction::Pca;
use ndarray::Array2;

use crate::chapter02::Error as Chapter02Error;

use super::pca::normalize_signs;
use super::{Error, Result};

/// linfa が求めた主成分。自作の `PcaModel` と同じ並び（1 行に 1 つの主成分）にそろえる。
#[derive(Debug, Clone, PartialEq)]
pub struct LinfaPca {
    /// 主成分ごとの寄与率。
    pub explained_variance_ratio: Vec<f64>,
    /// 主成分を 1 行に 1 つずつ並べた行列。符号は自作と同じ向きにそろえてある。
    pub components: Array2<f64>,
}

impl LinfaPca {
    /// linfa-reduction で主成分分析する。linfa は正解ラベルを持たないデータセットも受け取れる。
    ///
    /// `explained_variance_ratio()` は「選んだ主成分の分散の合計」で割った値なので、
    /// 自作（すべての固有値の合計で割る）と比べるときは n_components に列の数を渡す。
    pub fn fit(records: &Array2<f64>, n_components: usize) -> Result<LinfaPca> {
        if n_components == 0 || n_components > records.ncols() {
            return Err(Error::ComponentCount {
                requested: n_components,
                columns: records.ncols(),
            });
        }

        let dataset = DatasetBase::from(records.clone());
        let model = Pca::params(n_components)
            .fit(&dataset)
            .map_err(|error| Error::from(Chapter02Error::Library(error.to_string())))?;

        Ok(LinfaPca {
            explained_variance_ratio: model.explained_variance_ratio().to_vec(),
            // 固有ベクトルの符号は解法によって変わるので、自作と同じ規則でそろえてから比べる
            components: normalize_signs(&model.components().to_owned()),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use ndarray::array;

    #[test]
    fn 完全に相関する二列の第一主成分は四十五度の向きになる() {
        let x = array![[1.0, 1.0], [2.0, 2.0], [3.0, 3.0], [4.0, 4.0]];

        let model = LinfaPca::fit(&x, 2).unwrap();

        let root = 1.0 / 2.0_f64.sqrt();
        assert!((model.components[[0, 0]] - root).abs() < 1e-9, "{model:?}");
        assert!((model.components[[0, 1]] - root).abs() < 1e-9, "{model:?}");
    }

    #[test]
    fn 主成分が一つしか残らないと寄与率が数値にならない() {
        // linfa 0.8 は打ち切り特異値分解を使うので、ばらつきが 1 方向しか無いデータでは
        // 2 つ求めようとしても主成分は 1 つしか返らない。さらに explained_variance は
        // 分散を「返った主成分の数 - 1」で割るので、1 つだけだと 0 で割って NaN になる
        let x = array![[1.0, 1.0], [2.0, 2.0], [3.0, 3.0], [4.0, 4.0]];

        let model = LinfaPca::fit(&x, 2).unwrap();

        assert_eq!(model.components.nrows(), 1);
        assert!(model.explained_variance_ratio[0].is_nan(), "{model:?}");
    }

    #[test]
    fn 自作の主成分分析と寄与率が一致する() {
        let x = array![
            [2.5, 2.4, 1.0],
            [0.5, 0.7, 2.0],
            [2.2, 2.9, 1.5],
            [1.9, 2.2, 0.5],
            [3.1, 3.0, 2.5],
        ];

        let ours = super::super::pca::fit(&x, 3).unwrap();
        let theirs = LinfaPca::fit(&x, 3).unwrap();

        for (index, (ours, theirs)) in ours
            .explained_variance_ratio
            .iter()
            .zip(&theirs.explained_variance_ratio)
            .enumerate()
        {
            assert!(
                (ours - theirs).abs() < 1e-9,
                "PC{}: 自作 {ours}, linfa {theirs}",
                index + 1
            );
        }

        for ((row, column), ours) in ours.components.indexed_iter() {
            let theirs = theirs.components[[row, column]];

            assert!(
                (ours - theirs).abs() < 1e-9,
                "PC{} の {column} 列目: 自作 {ours}, linfa {theirs}",
                row + 1
            );
        }
    }

    #[test]
    fn 主成分の数は列の数までしか選べない() {
        let x = array![[1.0, 1.0], [2.0, 3.0]];

        assert!(LinfaPca::fit(&x, 3).is_err());
    }
}
