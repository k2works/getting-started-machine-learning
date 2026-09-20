//! 第 7 章: 線形回帰による数値予測。正規方程式を自作し、linfa-linear と突き合わせる。

pub mod cinema;
pub mod linearregression;
pub mod linfareg;
pub mod matrix;
pub mod metrics;

mod main;

pub use linearregression::{LinearModel, design_matrix, fit};
pub use main::run;
pub use matrix::solve;
pub use metrics::{mean_absolute_error, r2_score, root_mean_squared_error};
