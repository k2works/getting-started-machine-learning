//! 第 9 章: 特徴量エンジニアリング。ダミー変数・標準化・多項式特徴量・外れ値・表の結合。

pub mod bikeweather;
pub mod boston;
pub mod delimited;
pub mod dummies;
pub mod linear;
pub mod linfascaler;
pub mod outliers;
pub mod polynomial;
pub mod standardizer;

mod main;

use std::fmt;

pub use bikeweather::{join_weather, load_bike, load_weather, mean_count_by_weather};
pub use boston::{Boston, Scores};
pub use delimited::{Encoding, load};
pub use dummies::{categories, encode};
pub use linear::{LinearModel, r_squared};
pub use main::run;
pub use outliers::{iqr_outliers, quantile, remove_target_outliers};
pub use polynomial::{Pair, expand, pairs_with_replacement, select};
pub use standardizer::Standardizer;

/// 第 9 章で起こりうる失敗。第 2 章の失敗を包み、この章だけの失敗を足す。
#[derive(Debug)]
pub enum Error {
    /// 第 2 章の表・前処理の失敗。
    Chapter02(crate::chapter02::Error),
    /// 指定した文字コードで読めない。
    Decode {
        file: String,
        encoding: &'static str,
    },
    /// 正規方程式を解けない（列が互いに独立でない）。
    Singular,
    /// 値が 1 件も無い。
    Empty(&'static str),
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::Chapter02(error) => write!(f, "{error}"),
            Error::Decode { file, encoding } => {
                write!(f, "{file} を {encoding} として読めません")
            }
            Error::Singular => write!(
                f,
                "特徴量の列が互いに独立でないため、正規方程式を解けません"
            ),
            Error::Empty(what) => write!(f, "{what}が 1 件もありません"),
        }
    }
}

impl std::error::Error for Error {}

impl From<crate::chapter02::Error> for Error {
    fn from(error: crate::chapter02::Error) -> Self {
        Error::Chapter02(error)
    }
}

impl From<std::io::Error> for Error {
    fn from(error: std::io::Error) -> Self {
        Error::Chapter02(crate::chapter02::Error::Io(error))
    }
}

/// この章の結果の型。
pub type Result<T> = std::result::Result<T, Error>;
