//! 第 3 章: ジニ不純度で分割する決定木を自作し、linfa の決定木と突き合わせる。

pub mod decisiontree;
pub mod linfatree;

mod main;

pub use decisiontree::{
    DecisionTree, Split, Tree, best_split, build, format, gini, majority, predict_one,
};
pub use main::run;
