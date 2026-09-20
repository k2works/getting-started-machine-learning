//! 第 12 章: 正則化とモデル選択。リッジ回帰とラッソ回帰を自作し、linfa-elasticnet の
//! `ElasticNet::ridge()`・`lasso()` と突き合わせる。

pub mod boston;
pub mod lasso;
pub mod linfaelasticnet;
pub mod model;
pub mod ridge;
pub mod scaler;
pub mod selection;

mod main;

use std::fmt;

pub use boston::{Boston, Dataset};
pub use lasso::fit as fit_lasso;
pub use linfaelasticnet::{linfa_lasso, linfa_ridge};
pub use main::run;
pub use model::RegularizedModel;
pub use ridge::fit as fit_ridge;
pub use scaler::PolynomialScaler;
pub use selection::{Experiment, best_experiment, run_ridge_experiments, zero_coefficient_names};

/// 第 12 章で起こりうる失敗。第 2 章・第 9 章の失敗を包み、この章だけの失敗を足す。
///
/// 章ごとに `enum` を足していくと、上の章の失敗をそのまま持ち上げられる。`?` は `From` が
/// あれば自動で包み直すので、呼び出し側に書き足すことは無い。
#[derive(Debug)]
pub enum Error {
    /// 第 2 章の表・前処理の失敗。
    Chapter02(crate::chapter02::Error),
    /// 第 9 章の標準化・多項式特徴量の失敗。
    Chapter09(crate::chapter09::Error),
    /// 正則化の強さが負。
    NegativeAlpha(f64),
    /// 実験の結果が 1 件も無い。
    NoExperiments,
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::Chapter02(error) => write!(f, "{error}"),
            Error::Chapter09(error) => write!(f, "{error}"),
            Error::NegativeAlpha(alpha) => {
                write!(f, "正則化の強さは 0 以上にしてください: {alpha}")
            }
            Error::NoExperiments => write!(f, "実験の結果が 1 件もありません"),
        }
    }
}

impl std::error::Error for Error {}

impl From<crate::chapter02::Error> for Error {
    fn from(error: crate::chapter02::Error) -> Self {
        Error::Chapter02(error)
    }
}

impl From<crate::chapter09::Error> for Error {
    fn from(error: crate::chapter09::Error) -> Self {
        Error::Chapter09(error)
    }
}

impl From<std::io::Error> for Error {
    fn from(error: std::io::Error) -> Self {
        Error::Chapter02(crate::chapter02::Error::Io(error))
    }
}

/// この章の結果の型。
pub type Result<T> = std::result::Result<T, Error>;
