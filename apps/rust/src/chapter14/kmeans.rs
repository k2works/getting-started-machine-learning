//! K-means によるクラスタリング。点の集まりは 1 件を 1 行とする行列で表す。

use ndarray::{Array2, ArrayView1};

use crate::chapter02::shuffle;

use super::{Error, Result};

/// 更新の回数の既定の上限（scikit-learn の KMeans と同じ）。
pub const DEFAULT_MAX_ITERATIONS: usize = 300;
/// 初期中心を試す回数の既定値（scikit-learn の KMeans の n_init と同じ）。
pub const DEFAULT_N_INIT: usize = 10;

/// K-means の結果。
#[derive(Debug, Clone, PartialEq)]
pub struct KMeansResult {
    /// 点ごとのクラスタ番号。
    pub labels: Vec<usize>,
    /// クラスタの中心を 1 行に 1 つずつ並べた行列。
    pub centers: Array2<f64>,
    /// 誤差平方和。
    pub sse: f64,
}

/// 2 点間の距離の 2 乗。
pub fn squared_distance(a: &ArrayView1<f64>, b: &ArrayView1<f64>) -> f64 {
    a.iter()
        .zip(b)
        .map(|(left, right)| (left - right) * (left - right))
        .sum()
}

/// 各点を、最も近い中心のクラスタ番号に割り当てる。
pub fn assign_clusters(points: &Array2<f64>, centers: &Array2<f64>) -> Vec<usize> {
    points
        .rows()
        .into_iter()
        .map(|point| {
            let mut nearest = 0;
            // 中心ごとの距離は 1 回だけ求める。毎回求め直すと、点と中心の数だけ無駄が増える
            let mut shortest = squared_distance(&point, &centers.row(0));

            for cluster in 1..centers.nrows() {
                let distance = squared_distance(&point, &centers.row(cluster));

                if distance < shortest {
                    shortest = distance;
                    nearest = cluster;
                }
            }

            nearest
        })
        .collect()
}

/// クラスタごとに、割り当てられた点の平均を新しい中心にする。
/// 点が 1 つも割り当てられなかったクラスタは、前の中心をそのまま使う。
pub fn update_centers(
    points: &Array2<f64>,
    labels: &[usize],
    previous: &Array2<f64>,
) -> Array2<f64> {
    let mut sums: Array2<f64> = Array2::zeros(previous.raw_dim());
    let mut counts = vec![0_usize; previous.nrows()];

    for (point, label) in points.rows().into_iter().zip(labels) {
        counts[*label] += 1;

        let mut center = sums.row_mut(*label);

        center += &point;
    }

    for (cluster, count) in counts.iter().enumerate() {
        if *count == 0 {
            sums.row_mut(cluster).assign(&previous.row(cluster));
            continue;
        }

        #[allow(clippy::cast_precision_loss)]
        let count = *count as f64;
        let mut center = sums.row_mut(cluster);

        center /= count;
    }

    sums
}

/// 各点と、所属するクラスタの中心との距離の 2 乗の合計（誤差平方和）。
pub fn sum_of_squared_errors(points: &Array2<f64>, labels: &[usize], centers: &Array2<f64>) -> f64 {
    points
        .rows()
        .into_iter()
        .zip(labels)
        .map(|(point, label)| squared_distance(&point, &centers.row(*label)))
        .sum()
}

/// 中心が変わらなくなるか、更新の回数が max_iterations に達するまで、割り当てと中心の更新を繰り返す。
pub fn fit(
    points: &Array2<f64>,
    initial_centers: &Array2<f64>,
    max_iterations: usize,
) -> KMeansResult {
    let mut centers = initial_centers.clone();

    for _ in 0..max_iterations {
        let next = update_centers(points, &assign_clusters(points, &centers), &centers);

        if next == centers {
            break;
        }

        centers = next;
    }

    let labels = assign_clusters(points, &centers);
    let sse = sum_of_squared_errors(points, &labels, &centers);

    KMeansResult {
        labels,
        centers,
        sse,
    }
}

/// シード付きの乱数で点を並べ替え、先頭から n_clusters 個を初期中心にする。
pub fn choose_initial_centers(
    points: &Array2<f64>,
    n_clusters: usize,
    seed: u64,
) -> Result<Array2<f64>> {
    if n_clusters == 0 || n_clusters > points.nrows() {
        return Err(Error::ClusterCount {
            requested: n_clusters,
            points: points.nrows(),
        });
    }

    let indices: Vec<usize> = (0..points.nrows()).collect();
    let mut centers = Array2::zeros((n_clusters, points.ncols()));

    for (cluster, index) in shuffle(&indices, seed).iter().take(n_clusters).enumerate() {
        centers.row_mut(cluster).assign(&points.row(*index));
    }

    Ok(centers)
}

/// シードを 1 ずつずらして初期中心を n_init 通り選び、SSE が最小の結果を返す。
pub fn fit_with_restarts(
    points: &Array2<f64>,
    n_clusters: usize,
    seed: u64,
    n_init: usize,
) -> Result<KMeansResult> {
    let mut best: Option<KMeansResult> = None;

    for offset in 0..n_init.max(1) {
        let centers = choose_initial_centers(points, n_clusters, seed + offset as u64)?;
        let result = fit(points, &centers, DEFAULT_MAX_ITERATIONS);

        if best.as_ref().is_none_or(|best| result.sse < best.sse) {
            best = Some(result);
        }
    }

    best.ok_or(Error::Empty)
}

/// クラスタ数ごとに、初期中心を n_init 通り試した最小の SSE。
pub fn sse_by_cluster_count(
    points: &Array2<f64>,
    cluster_counts: &[usize],
    seed: u64,
    n_init: usize,
) -> Result<Vec<(usize, f64)>> {
    cluster_counts
        .iter()
        .map(|n| Ok((*n, fit_with_restarts(points, *n, seed, n_init)?.sse)))
        .collect()
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
    fn 距離の二乗は各次元の差の二乗の和() {
        let a = array![0.0, 0.0];
        let b = array![3.0, 4.0];

        assert!((squared_distance(&a.view(), &b.view()) - 25.0).abs() < 1e-12);
    }

    #[test]
    fn 各点は最も近い中心に割り当てられる() {
        let labels = assign_clusters(&points(), &array![[0.0, 0.0], [10.0, 10.0]]);

        assert_eq!(labels, vec![0, 0, 1, 1]);
    }

    #[test]
    fn 距離が同じなら番号の小さいクラスタに割り当てる() {
        let labels = assign_clusters(&array![[0.0, 0.0]], &array![[1.0, 0.0], [-1.0, 0.0]]);

        assert_eq!(labels, vec![0]);
    }

    #[test]
    fn 中心は割り当てられた点の平均になる() {
        let centers = update_centers(&points(), &[0, 0, 1, 1], &array![[0.0, 0.0], [0.0, 0.0]]);

        assert_eq!(centers, array![[0.5, 0.5], [10.5, 10.5]]);
    }

    #[test]
    fn 点が割り当てられなかったクラスタは前の中心を保つ() {
        let previous = array![[0.0, 0.0], [99.0, 99.0]];

        let centers = update_centers(&points(), &[0, 0, 0, 0], &previous);

        assert_eq!(centers.row(1), previous.row(1));
    }

    #[test]
    fn 誤差平方和は中心からの距離の二乗の合計() {
        let sse =
            sum_of_squared_errors(&points(), &[0, 0, 1, 1], &array![[0.5, 0.5], [10.5, 10.5]]);

        // 4 点それぞれ 0.5^2 + 0.5^2 = 0.5
        assert!((sse - 2.0).abs() < 1e-12);
    }

    #[test]
    fn 中心が変わらなくなるまで繰り返す() {
        let result = fit(&points(), &array![[0.0, 0.0], [11.0, 11.0]], 300);

        assert_eq!(result.labels, vec![0, 0, 1, 1]);
        assert_eq!(result.centers, array![[0.5, 0.5], [10.5, 10.5]]);
        assert!((result.sse - 2.0).abs() < 1e-12);
    }

    #[test]
    fn 繰り返しの上限で止める() {
        // 1 回だけ更新すると、中心は平均に移るが収束はしていない
        let result = fit(&points(), &array![[0.0, 0.0], [11.0, 11.0]], 1);

        assert_eq!(result.centers, array![[0.5, 0.5], [10.5, 10.5]]);
    }

    #[test]
    fn 初期中心は点の中から選ぶ() {
        let centers = choose_initial_centers(&points(), 2, 0).unwrap();

        for center in centers.rows() {
            assert!(
                points().rows().into_iter().any(|point| point == center),
                "{center:?} は点の中に無い"
            );
        }
    }

    #[test]
    fn クラスタ数は点の数までしか選べない() {
        assert_eq!(
            choose_initial_centers(&points(), 5, 0)
                .unwrap_err()
                .to_string(),
            "クラスタ数は 1 以上 4 以下にしてください: 5"
        );
        assert!(choose_initial_centers(&points(), 0, 0).is_err());
    }

    #[test]
    fn 初期中心を何通りか試して誤差平方和が最小の結果を返す() {
        let result = fit_with_restarts(&points(), 2, 0, DEFAULT_N_INIT).unwrap();

        assert!((result.sse - 2.0).abs() < 1e-12, "{result:?}");
    }

    #[test]
    fn クラスタ数を増やすと誤差平方和は小さくなる() {
        let sse = sse_by_cluster_count(&points(), &[1, 2, 4], 0, DEFAULT_N_INIT).unwrap();

        assert!(sse[0].1 > sse[1].1);
        assert!(sse[1].1 > sse[2].1);
        // 点の数だけクラスタを作れば、中心は点そのものになる
        assert!(sse[2].1.abs() < 1e-12);
    }
}
