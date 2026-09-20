//! 第 14 章: K-means によるクラスタリング。

pub mod kmeans;
pub mod linfakmeans;
pub mod spending;

mod main;

use std::fmt;

pub use kmeans::{
    KMeansResult, assign_clusters, choose_initial_centers, fit, fit_with_restarts,
    squared_distance, sse_by_cluster_count, sum_of_squared_errors, update_centers,
};
pub use linfakmeans::LinfaKMeans;
pub use main::run;
pub use spending::{ClusterSummary, Spending};

/// 第 14 章で起こりうる失敗。第 9 章までの失敗を包み、この章だけの失敗を足す。
#[derive(Debug)]
pub enum Error {
    /// 第 9 章までの前処理の失敗。
    Chapter09(crate::chapter09::Error),
    /// クラスタ数が点の数に合わない。
    ClusterCount { requested: usize, points: usize },
    /// 点が 1 つも無い。
    Empty,
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::Chapter09(error) => write!(f, "{error}"),
            Error::ClusterCount { requested, points } => write!(
                f,
                "クラスタ数は 1 以上 {points} 以下にしてください: {requested}"
            ),
            Error::Empty => write!(f, "点が 1 つもありません"),
        }
    }
}

impl std::error::Error for Error {}

impl From<crate::chapter09::Error> for Error {
    fn from(error: crate::chapter09::Error) -> Self {
        Error::Chapter09(error)
    }
}

impl From<crate::chapter02::Error> for Error {
    fn from(error: crate::chapter02::Error) -> Self {
        Error::Chapter09(crate::chapter09::Error::Chapter02(error))
    }
}

impl From<std::io::Error> for Error {
    fn from(error: std::io::Error) -> Self {
        Error::from(crate::chapter02::Error::Io(error))
    }
}

/// この章の結果の型。
pub type Result<T> = std::result::Result<T, Error>;
