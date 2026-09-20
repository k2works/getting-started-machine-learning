//! ドメイン層。予測の入力と、モデル・置き場の約束。HTTP にも linfa にも依存しない。

use std::fmt;

use crate::chapter02::{Error as DataError, Features, Row};
use crate::chapter07::LinearModel;
use crate::chapter08::{FittedPipeline, survived};

/// 第 15 章で起こりうる失敗。
#[derive(Debug)]
pub enum Error {
    /// モデルを読み込めない。どのモデルかを持つ。
    ModelNotFound(String),
    /// 保存・読み込みで失敗した。
    Io(std::io::Error),
    /// JSON の読み書きで失敗した。
    Json(serde_json::Error),
    /// 前のの章のデータ処理が失敗した。
    Data(DataError),
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::ModelNotFound(name) => write!(f, "モデルがありません: {name}"),
            Error::Io(error) => write!(f, "モデルを読み書きできません: {error}"),
            Error::Json(error) => write!(f, "モデルの JSON が壊れています: {error}"),
            Error::Data(error) => write!(f, "{error}"),
        }
    }
}

impl std::error::Error for Error {}

impl From<std::io::Error> for Error {
    fn from(error: std::io::Error) -> Self {
        Error::Io(error)
    }
}

impl From<serde_json::Error> for Error {
    fn from(error: serde_json::Error) -> Self {
        Error::Json(error)
    }
}

impl From<DataError> for Error {
    fn from(error: DataError) -> Self {
        Error::Data(error)
    }
}

/// この章の結果の型。
pub type Result<T> = std::result::Result<T, Error>;

/// 映画の特徴量。SNS の評判 2 種類・主演の人気・原作の有無（0 か 1）。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Movie {
    pub sns1: f64,
    pub sns2: f64,
    pub actor: f64,
    pub original: i32,
}

impl Movie {
    /// 第 7 章のモデルに渡す特徴量にする。
    pub fn features(self) -> Result<Features> {
        Ok(Features::new(
            crate::chapter07::cinema::feature_columns(),
            vec![self.sns1, self.sns2, self.actor, f64::from(self.original)],
        )?)
    }
}

/// 乗客の特徴量。空文字列は欠損値として前処理に任せる。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Passenger {
    pub pclass: String,
    pub sex: String,
    pub age: String,
    pub sib_sp: String,
    pub parch: String,
    pub fare: String,
    pub embarked: String,
}

impl Passenger {
    /// 第 8 章のパイプラインに渡す行にする。
    pub fn row(&self) -> Result<Row> {
        Ok(survived::passenger(&[
            &self.pclass,
            &self.sex,
            &self.age,
            &self.sib_sp,
            &self.parch,
            &self.fare,
            &self.embarked,
        ])?)
    }
}

/// 興行収入を予測する約束。
pub trait SalesModel {
    fn predict_sales(&self, movie: Movie) -> Result<f64>;
}

/// 生存を予測する約束。
pub trait SurvivalModel {
    fn survives(&self, passenger: &Passenger) -> Result<bool>;
}

/// 学習済みモデルの置き場の約束。
/// 読み込めなければ `Error::ModelNotFound` を返す。
pub trait ModelStore {
    fn load_sales_model(&self) -> Result<Box<dyn SalesModel>>;
    fn load_survival_model(&self) -> Result<Box<dyn SurvivalModel>>;
}

/// 第 7 章の線形回帰のモデルを `SalesModel` の約束に合わせる。
pub struct LinearSalesModel {
    pub model: LinearModel,
}

impl SalesModel for LinearSalesModel {
    fn predict_sales(&self, movie: Movie) -> Result<f64> {
        Ok(self.model.predict_one(&movie.features()?)?)
    }
}

/// 第 8 章の学習済みパイプラインを `SurvivalModel` の約束に合わせる。
pub struct PipelineSurvivalModel {
    pub pipeline: FittedPipeline,
}

impl SurvivalModel for PipelineSurvivalModel {
    fn survives(&self, passenger: &Passenger) -> Result<bool> {
        let table = survived::features(&[passenger.row()?]);
        let predictions = self.pipeline.predict(&table)?;

        Ok(predictions[0] == survived::SURVIVED)
    }
}
