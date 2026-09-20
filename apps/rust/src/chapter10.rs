//! 第 10 章: ロジスティック回帰とアンサンブル学習（ランダムフォレスト）。

pub mod classifier;
pub mod forest;
pub mod importance;
pub mod linfalogistic;
pub mod logistic;

mod main;

pub use classifier::{Classifier, Score, accuracy};
pub use forest::{FittedTree, RandomForest, majority_vote};
pub use importance::{forest_importances, tree_importances};
pub use linfalogistic::LinfaLogisticRegression;
pub use logistic::{LogisticRegression, cross_entropy, softmax};
pub use main::run;

// 第 2 章の失敗の型をそのまま使う。この章で新しく起こる失敗は「学習する前に予測した」
// （`Error::NotFitted`）と「linfa が失敗した」（`Error::Library`）だけで、どちらもすでにある
pub use crate::chapter02::{Error, Result};
