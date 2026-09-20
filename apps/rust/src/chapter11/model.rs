//! 交差検証で学習と予測を繰り返すモデル。正解ラベルの型 `T` をジェネリクスにして、
//! 分類（`String`）と回帰（`f64`）の両方を同じ交差検証にかける。

use crate::chapter02::{Error, Features, Result};
use crate::chapter03::DecisionTree;
use crate::chapter07::{LinearModel, fit};

/// 訓練データで学習し、特徴量からラベルを予測するモデル。
pub trait Model<T> {
    /// 訓練データで学習する。
    fn fit(&mut self, x: &[Features], t: &[T]) -> Result<()>;

    /// 特徴量ごとのラベルを予測する。
    fn predict(&self, x: &[Features]) -> Result<Vec<T>>;
}

/// 第 3 章の決定木を、変更せずにこの章のトレイトに合わせる。
///
/// トレイトがこのクレートのものなので、`DecisionTree` に手を入れずに実装を足せる。
/// Java 版・C# 版では `implements` を型の宣言に書けないので、アダプターのクラスが要った。
impl Model<String> for DecisionTree {
    fn fit(&mut self, x: &[Features], t: &[String]) -> Result<()> {
        DecisionTree::fit(self, x, t)?;

        Ok(())
    }

    fn predict(&self, x: &[Features]) -> Result<Vec<String>> {
        DecisionTree::predict(self, x)
    }
}

/// 第 7 章の正規方程式による線形回帰を、この章のモデルとして使う。
///
/// 第 7 章の `fit` は学習したモデルを返す関数なので、学習の前後を `Option` で表す型で包む。
/// 「学習する前に予測した」はコンパイル時には防げないので、`Error::NotFitted` で返す。
#[derive(Debug, Clone, Default)]
pub struct LinearRegressionModel {
    model: Option<LinearModel>,
}

impl LinearRegressionModel {
    /// 学習していないモデルを作る。
    pub fn new() -> Self {
        LinearRegressionModel::default()
    }

    /// 学習した結果。学習前は `None`。
    pub fn model(&self) -> Option<&LinearModel> {
        self.model.as_ref()
    }
}

impl Model<f64> for LinearRegressionModel {
    fn fit(&mut self, x: &[Features], t: &[f64]) -> Result<()> {
        self.model = Some(fit(x, t)?);

        Ok(())
    }

    fn predict(&self, x: &[Features]) -> Result<Vec<f64>> {
        self.model.as_ref().ok_or(Error::NotFitted)?.predict(x)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn labels(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    fn column(value: f64) -> Features {
        Features::new(vec!["x".to_string()], vec![value]).unwrap()
    }

    #[test]
    fn 決定木をモデルとして学習して予測する() {
        let x = vec![column(0.0), column(1.0), column(10.0), column(11.0)];
        let t = labels(&["低", "低", "高", "高"]);
        let mut model: Box<dyn Model<String>> = Box::new(DecisionTree::unlimited());

        model.fit(&x, &t).unwrap();

        assert_eq!(model.predict(&x).unwrap(), t);
    }

    #[test]
    fn 線形回帰をモデルとして学習して予測する() {
        // t = 2x + 1 の架空のデータ
        let x = vec![column(0.0), column(1.0), column(2.0), column(3.0)];
        let t = vec![1.0, 3.0, 5.0, 7.0];
        let mut model = LinearRegressionModel::new();

        model.fit(&x, &t).unwrap();

        for (predicted, actual) in model.predict(&x).unwrap().iter().zip(&t) {
            assert!((predicted - actual).abs() < 1e-9);
        }
    }

    #[test]
    fn 学習する前に予測すると失敗する() {
        let model = LinearRegressionModel::new();

        assert_eq!(
            Model::predict(&model, &[column(1.0)])
                .unwrap_err()
                .to_string(),
            "学習してから予測してください"
        );
    }
}
