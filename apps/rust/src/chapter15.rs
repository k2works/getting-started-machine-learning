//! 第 15 章: 学習済みのモデルを axum の予測 API として公開する。
//! 層（ドメイン・アプリケーション・インフラ・プレゼンテーション）をファイルで分け、
//! 依存の向きを内側へそろえる。

pub mod api;
pub mod domain;
pub mod service;
pub mod store;
pub mod validation;

mod main;

pub use domain::{Error, ModelStore, Movie, Passenger, Result, SalesModel, SurvivalModel};
pub use main::{MODEL_DIR, PORT, run, train_and_save_models};
pub use service::{ModelHealth, PredictionService};
pub use store::{FileModelStore, SALES_MODEL, SURVIVAL_MODEL};
pub use validation::{MovieRequest, PassengerRequest, Validated};
