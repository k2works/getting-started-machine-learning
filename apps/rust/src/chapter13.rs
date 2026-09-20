//! 第 13 章: 主成分分析による次元削減。

pub mod boston;
pub mod linfapca;
pub mod pca;

mod main;

use std::fmt;

pub use boston::BostonPca;
pub use main::run;
pub use pca::{
    Loading, PcaModel, column_means, components_needed, covariance_matrix, fit, jacobi_eigen,
    normalize_signs, top_loadings, transform,
};

/// 第 13 章で起こりうる失敗。第 9 章までの失敗を包み、固有値分解の失敗を足す。
#[derive(Debug)]
pub enum Error {
    /// 第 9 章までの前処理の失敗。
    Chapter09(crate::chapter09::Error),
    /// 分散を求めるにはデータが少なすぎる。
    TooFewRows(usize),
    /// 固有値分解にかけた行列が正方行列でない。
    NotSquare { rows: usize, columns: usize },
    /// 決めた回数だけ繰り返しても固有値分解が収束しない。
    NotConverged(usize),
    /// 主成分の数が列の数に合わない。
    ComponentCount { requested: usize, columns: usize },
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::Chapter09(error) => write!(f, "{error}"),
            Error::TooFewRows(rows) => {
                write!(f, "主成分分析には 2 件以上のデータが必要です（{rows} 件）")
            }
            Error::NotSquare { rows, columns } => {
                write!(f, "正方行列ではありません: {rows} 行 {columns} 列")
            }
            Error::NotConverged(sweeps) => {
                write!(f, "{sweeps} 回繰り返しても固有値分解が収束しませんでした")
            }
            Error::ComponentCount { requested, columns } => write!(
                f,
                "主成分の数は 1 以上 {columns} 以下にしてください: {requested}"
            ),
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
