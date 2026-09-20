//! linfa-clustering の `KMeans` でクラスタリングし、自作と同じ SSE で比べる。

use linfa::prelude::*;
use linfa_clustering::KMeans;
use ndarray::Array2;
use rand_xoshiro::Xoshiro256Plus;
use rand_xoshiro::rand_core::SeedableRng;

use crate::chapter02::Error as Chapter02Error;

use super::kmeans::{self, DEFAULT_MAX_ITERATIONS};
use super::{Error, Result};

/// linfa で学習したクラスタリング。
#[derive(Debug, Clone, PartialEq)]
pub struct LinfaKMeans {
    /// クラスタの中心を 1 行に 1 つずつ並べた行列。
    pub centers: Array2<f64>,
    /// 自作と同じ定義（中心からの距離の 2 乗の合計）で求めた誤差平方和。
    pub sse: f64,
}

impl LinfaKMeans {
    /// k-means++ で初期中心を選んで学習する。初期中心は n_runs 通り試す。
    ///
    /// 乱数は `Xoshiro256Plus` を渡す。linfa 0.8 は rand 0.8 の `Rng` を求めるので、
    /// rand 0.9 以降の乱数は「同じ名前の別の型」になって渡せない。
    pub fn fit(
        points: &Array2<f64>,
        n_clusters: usize,
        seed: u64,
        n_runs: usize,
    ) -> Result<LinfaKMeans> {
        if n_clusters == 0 || n_clusters > points.nrows() {
            return Err(Error::ClusterCount {
                requested: n_clusters,
                points: points.nrows(),
            });
        }

        let rng = Xoshiro256Plus::seed_from_u64(seed);
        let dataset = DatasetBase::from(points.clone());
        let model = KMeans::params_with_rng(n_clusters, rng)
            .max_n_iterations(DEFAULT_MAX_ITERATIONS as u64)
            .n_runs(n_runs.max(1))
            .fit(&dataset)
            .map_err(|error| Error::from(Chapter02Error::Library(error.to_string())))?;

        let centers = model.centroids().to_owned();
        let labels = kmeans::assign_clusters(points, &centers);

        Ok(LinfaKMeans {
            sse: kmeans::sum_of_squared_errors(points, &labels, &centers),
            centers,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use ndarray::array;

    /// 原点のまわりと (10, 10) のまわりに分かれた 4 点。
    fn points() -> Array2<f64> {
        array![[0.0, 0.0], [1.0, 1.0], [10.0, 10.0], [11.0, 11.0]]
    }

    #[test]
    fn 二つに分ければ自作と同じ誤差平方和になる() {
        let ours = kmeans::fit_with_restarts(&points(), 2, 0, kmeans::DEFAULT_N_INIT).unwrap();
        let theirs = LinfaKMeans::fit(&points(), 2, 0, kmeans::DEFAULT_N_INIT).unwrap();

        assert!((ours.sse - theirs.sse).abs() < 1e-9, "{ours:?} {theirs:?}");
    }

    #[test]
    fn クラスタ数は点の数までしか選べない() {
        assert!(LinfaKMeans::fit(&points(), 5, 0, 1).is_err());
        assert!(LinfaKMeans::fit(&points(), 0, 0, 1).is_err());
    }
}
