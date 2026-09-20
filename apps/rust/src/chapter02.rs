//! 第 2 章: データの前処理。表の読み込み・欠損値の補完・訓練データとテストデータへの分割。

pub mod preprocessing;
pub mod table;

mod main;

use std::fmt;

pub use main::run;
pub use preprocessing::{
    Features, TARGET, TrainTestSplit, column_means, fill_missing, prepare_iris, shuffle,
    split_features_and_target, split_train_test,
};
pub use table::{Missing, Row, Table};

/// 第 2 章で起こりうる失敗。
#[derive(Debug)]
pub enum Error {
    /// CSV を開けない・読めない。
    Io(std::io::Error),
    /// CSV の形が想定と違う。
    Csv(csv::Error),
    /// 列が無い。
    MissingColumn(String),
    /// 数値として読めない。
    NotANumber { column: String, value: String },
    /// 件数が合わない。
    LengthMismatch { left: usize, right: usize },
    /// 列の値がすべて空欄で、平均値を求められない。
    AllMissing(String),
    /// 補完する値が渡されていない。
    NoFillValue(String),
    /// 学習する前に予測しようとした。
    NotFitted,
    /// ライブラリ（linfa・ndarray）が失敗した。
    Library(String),
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::Io(error) => write!(f, "CSV を読めません: {error}"),
            Error::Csv(error) => write!(f, "CSV を読めません: {error}"),
            Error::MissingColumn(column) => write!(f, "列がありません: {column}"),
            Error::NotANumber { column, value } => {
                write!(f, "{column} を数値として読めません: {value}")
            }
            Error::LengthMismatch { left, right } => write!(f, "件数が違います: {left} と {right}"),
            Error::AllMissing(column) => write!(f, "値がすべて空欄です: {column}"),
            Error::NoFillValue(column) => write!(f, "補完する値がありません: {column}"),
            Error::NotFitted => write!(f, "学習してから予測してください"),
            Error::Library(message) => write!(f, "ライブラリが失敗しました: {message}"),
        }
    }
}

impl std::error::Error for Error {}

impl From<std::io::Error> for Error {
    fn from(error: std::io::Error) -> Self {
        Error::Io(error)
    }
}

impl From<csv::Error> for Error {
    fn from(error: csv::Error) -> Self {
        Error::Csv(error)
    }
}

/// この章の結果の型。
pub type Result<T> = std::result::Result<T, Error>;
