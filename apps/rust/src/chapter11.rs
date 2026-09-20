//! 第 11 章: 評価指標と交差検証。混同行列・適合率・再現率・F 値・ROC 曲線・K 分割交差検証を
//! 自作し、linfa の `confusion_matrix`・`roc`・`area_under_curve`・`cross_validate_single` と
//! 突き合わせる。

pub mod crossval;
pub mod data;
pub mod linfacv;
pub mod linfametrics;
pub mod metrics;
pub mod model;
pub mod roc;

mod main;

pub use crossval::{Fold, cross_validate, k_fold, k_fold_sequential};
pub use data::{Dataset, prepare_cinema, prepare_survived};
pub use linfacv::linfa_cross_validate_rmse;
pub use linfametrics::{LinfaScores, linfa_auc, linfa_scores};
pub use main::run;
pub use metrics::{ConfusionMatrix, Metric, accuracy, classification_metric};
pub use model::{LinearRegressionModel, Model};
pub use roc::{RocPoint, auc, roc_curve};

// 評価指標は第 2 章の失敗の型をそのまま使う。この章で新しく起こる失敗は「正解と予測の件数が
// 違う」（`Error::LengthMismatch`）と「分割の数が正しくない」（`Error::Library`）だけ
pub use crate::chapter02::{Error, Result};
