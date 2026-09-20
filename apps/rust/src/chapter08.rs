//! 第 8 章: 実践的な分類と前処理パイプライン。
//!
//! 欠損値の補完（グループ中央値・最頻値）とダミー変数化は linfa-preprocessing に無いので、
//! 自作が最終的な実装になる（ADR 009）。決定木の分類だけは linfa-trees と突き合わせる。

pub mod evaluation;
pub mod linfacompare;
pub mod modelfile;
pub mod pipeline;
pub mod preprocessing;
pub mod survived;
pub mod weightedtree;

mod main;

pub use evaluation::{Evaluation, accuracy, evaluate};
pub use main::{model_file, run, run_with};
pub use pipeline::{FittedPipeline, Pipeline};
pub use preprocessing::{FittedStep, Step};
pub use weightedtree::{ClassWeight, DecisionTreeClassifier, FittedDecisionTree, Tree};
