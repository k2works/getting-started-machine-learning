//! 前処理とモデルをつなぐパイプライン。

use serde::{Deserialize, Serialize};

use super::preprocessing::{FittedStep, Step};
use super::survived;
use super::weightedtree::{ClassWeight, DecisionTreeClassifier, FittedDecisionTree};
use crate::chapter02::{Error, Features, Result, Table};

/// 学習前のパイプライン。前処理を順に `fit`・`transform` してから、モデルを学習する。
#[derive(Debug, Clone, PartialEq)]
pub struct Pipeline {
    pub steps: Vec<Step>,
    pub model: DecisionTreeClassifier,
}

/// 学習済みのパイプライン。予測するときは前処理の `transform` だけを使う。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct FittedPipeline {
    pub steps: Vec<FittedStep>,
    pub model: FittedDecisionTree,
}

impl Pipeline {
    /// Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に、決定木で分類する。
    pub fn build(max_depth: Option<usize>, class_weight: ClassWeight) -> Pipeline {
        Pipeline {
            steps: vec![
                Step::GroupMedian {
                    column: "Age".to_string(),
                    by: vec!["Pclass".to_string(), "Sex".to_string()],
                },
                Step::MostFrequent {
                    column: "Embarked".to_string(),
                },
                Step::Dummy {
                    columns: vec!["Sex".to_string(), "Embarked".to_string()],
                },
            ],
            model: DecisionTreeClassifier::new(max_depth, class_weight),
        }
    }

    /// 訓練データで前処理とモデルを学習する。前処理は、前の前処理で変換したデータで `fit` する。
    pub fn fit(&self, x: &Table, t: &[i32]) -> Result<FittedPipeline> {
        let mut fitted = Vec::with_capacity(self.steps.len());
        let mut prepared = x.clone();

        for step in &self.steps {
            let step = step.fit(&prepared)?;
            prepared = step.transform(&prepared)?;
            fitted.push(step);
        }

        Ok(FittedPipeline {
            model: self.model.fit(&to_features(&prepared)?, t)?,
            steps: fitted,
        })
    }
}

impl FittedPipeline {
    /// 学習済みの前処理を順に適用する。
    pub fn transform(&self, x: &Table) -> Result<Table> {
        let mut prepared = x.clone();

        for step in &self.steps {
            prepared = step.transform(&prepared)?;
        }

        Ok(prepared)
    }

    /// 前処理をして、モデルに渡す特徴量にする。
    pub fn features(&self, x: &Table) -> Result<Vec<Features>> {
        to_features(&self.transform(x)?)
    }

    /// 前処理をしてから、1 行ごとのラベル（1 が生存、0 が死亡）を予測する。
    pub fn predict(&self, x: &Table) -> Result<Vec<i32>> {
        self.model.predict(&self.features(x)?)
    }
}

/// 前処理の済んだ表を特徴量にする。欠損値が残っていれば失敗する。
pub fn to_features(x: &Table) -> Result<Vec<Features>> {
    let mut features = Vec::with_capacity(x.rows.len());

    for row in &x.rows {
        let mut values = Vec::with_capacity(x.columns.len());

        for column in &x.columns {
            let value = row
                .number(column)?
                .ok_or_else(|| Error::NoFillValue(column.clone()))?;

            values.push(value);
        }

        features.push(Features::new(x.columns.clone(), values)?);
    }

    Ok(features)
}

/// 行から、特徴量の表と正解ラベルを作る。
pub fn split_features_and_target(rows: &[crate::chapter02::Row]) -> Result<(Table, Vec<i32>)> {
    Ok((survived::features(rows), survived::target(rows)?))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::chapter02::Row;
    use std::collections::HashMap;

    /// 乗客 1 人分の行を作る。値は特徴量の列の順（欠損値は空文字列）に、末尾は生死。
    fn passenger(values: &[&str], survived: &str) -> Row {
        let mut cells: HashMap<String, String> = super::survived::FEATURES
            .iter()
            .map(|name| name.to_string())
            .zip(values.iter().map(|value| value.to_string()))
            .collect();

        cells.insert(super::survived::TARGET.to_string(), survived.to_string());

        Row::new(cells)
    }

    /// 架空の乗客のデータ。運賃が高い女性は生存、安い男性は死亡。
    fn passengers() -> Vec<Row> {
        vec![
            passenger(&["1", "female", "30", "0", "0", "80", "C"], "1"),
            passenger(&["1", "female", "", "0", "0", "90", "S"], "1"),
            passenger(&["3", "male", "20", "0", "0", "8", ""], "0"),
            passenger(&["3", "male", "40", "0", "0", "7", "S"], "0"),
        ]
    }

    #[test]
    fn パイプラインは前処理をしてから学習する() {
        let rows = passengers();
        let (x, t) = split_features_and_target(&rows).unwrap();

        let fitted = Pipeline::build(Some(2), ClassWeight::None)
            .fit(&x, &t)
            .unwrap();

        assert_eq!(fitted.predict(&x).unwrap(), t);
    }

    #[test]
    fn パイプラインは欠損値もダミー変数も残さない() {
        let rows = passengers();
        let (x, t) = split_features_and_target(&rows).unwrap();

        let fitted = Pipeline::build(Some(2), ClassWeight::None)
            .fit(&x, &t)
            .unwrap();
        let prepared = fitted.transform(&x).unwrap();

        assert!(!prepared.columns.contains(&"Sex".to_string()));
        assert!(prepared.columns.contains(&"Sex_male".to_string()));
        assert!(
            prepared
                .rows
                .iter()
                .all(|row| !row.is_missing("Age").unwrap())
        );
    }

    #[test]
    fn 学習した前処理は新しいデータにも同じ変換をする() {
        let rows = passengers();
        let (x, t) = split_features_and_target(&rows).unwrap();

        let fitted = Pipeline::build(Some(2), ClassWeight::None)
            .fit(&x, &t)
            .unwrap();

        let new = survived::features(&[survived::passenger(&[
            "1", "female", "", "0", "0", "85", "C",
        ])
        .unwrap()]);

        assert_eq!(fitted.predict(&new).unwrap(), vec![1]);
    }

    #[test]
    fn 欠損値が残った表は特徴量にできない() {
        let x = Table {
            columns: vec!["Age".to_string()],
            rows: vec![Row::new(HashMap::from([(
                "Age".to_string(),
                String::new(),
            )]))],
        };

        assert_eq!(
            to_features(&x).unwrap_err().to_string(),
            "補完する値がありません: Age"
        );
    }
}
