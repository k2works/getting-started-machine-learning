//! 正則化の強さごとに実験し、検証データでモデルを選ぶ。

use crate::chapter07::r2_score;

use super::ridge;
use super::{Error, Result};

/// 正則化の強さ 1 つ分の実験結果。
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Experiment {
    pub alpha: f64,
    pub train_score: f64,
    pub validation_score: f64,
    pub coefficient_abs_sum: f64,
}

/// alpha ごとにリッジ回帰を訓練データで学習し、訓練データと検証データの決定係数を記録する。
pub fn run_ridge_experiments(
    columns: &[String],
    x_train: &[Vec<f64>],
    t_train: &[f64],
    x_valid: &[Vec<f64>],
    t_valid: &[f64],
    alphas: &[f64],
) -> Result<Vec<Experiment>> {
    alphas
        .iter()
        .map(|alpha| {
            let model = ridge::fit(columns, x_train, t_train, *alpha)?;

            Ok(Experiment {
                alpha: *alpha,
                train_score: r2_score(t_train, &model.predict(x_train)?)?,
                validation_score: r2_score(t_valid, &model.predict(x_valid)?)?,
                coefficient_abs_sum: model.coefficient_abs_sum(),
            })
        })
        .collect()
}

/// 検証データの決定係数が最も高い実験。同じ値なら先の実験を選ぶ。
pub fn best_experiment(experiments: &[Experiment]) -> Result<Experiment> {
    experiments
        .iter()
        .copied()
        .reduce(|best, experiment| {
            if experiment.validation_score > best.validation_score {
                experiment
            } else {
                best
            }
        })
        .ok_or(Error::NoExperiments)
}

/// 係数がちょうど 0 になった特徴量の名前を、列の順に返す。
pub fn zero_coefficient_names(coefficients: &[f64], columns: &[String]) -> Result<Vec<String>> {
    if coefficients.len() != columns.len() {
        return Err(Error::Chapter02(crate::chapter02::Error::LengthMismatch {
            left: coefficients.len(),
            right: columns.len(),
        }));
    }

    Ok(columns
        .iter()
        .zip(coefficients)
        .filter(|(_, coefficient)| **coefficient == 0.0)
        .map(|(column, _)| column.clone())
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn experiment(alpha: f64, validation_score: f64) -> Experiment {
        Experiment {
            alpha,
            train_score: 0.9,
            validation_score,
            coefficient_abs_sum: 1.0,
        }
    }

    fn strings(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    #[test]
    fn 検証データの決定係数が最も高い実験を選ぶ() {
        let experiments = vec![
            experiment(0.0, 0.5),
            experiment(1.0, 0.8),
            experiment(10.0, 0.7),
        ];

        assert!((best_experiment(&experiments).unwrap().alpha - 1.0).abs() < 1e-12);
    }

    #[test]
    fn 同じ値なら先の実験を選ぶ() {
        let experiments = vec![experiment(0.1, 0.8), experiment(1.0, 0.8)];

        assert!((best_experiment(&experiments).unwrap().alpha - 0.1).abs() < 1e-12);
    }

    #[test]
    fn 実験が無ければ選べない() {
        let error = best_experiment(&[]).unwrap_err();

        assert_eq!(error.to_string(), "実験の結果が 1 件もありません");
    }

    #[test]
    fn 係数が零の列の名前を返す() {
        let names = zero_coefficient_names(&[1.0, 0.0, 0.0], &strings(&["a", "b", "c"])).unwrap();

        assert_eq!(names, strings(&["b", "c"]));
    }

    #[test]
    fn 係数と列名の数が違えば失敗する() {
        assert!(zero_coefficient_names(&[1.0], &strings(&["a", "b"])).is_err());
    }

    #[test]
    fn 罰則を強くすると訓練データの決定係数は下がる() {
        // t = 2a + 3b + 1 の架空のデータ
        let columns = strings(&["a", "b"]);
        let rows = vec![
            vec![0.0, 0.0],
            vec![1.0, 0.0],
            vec![0.0, 1.0],
            vec![1.0, 2.0],
            vec![2.0, 1.0],
        ];
        let t: Vec<f64> = rows
            .iter()
            .map(|row| 2.0 * row[0] + 3.0 * row[1] + 1.0)
            .collect();

        let experiments =
            run_ridge_experiments(&columns, &rows, &t, &rows, &t, &[0.0, 1.0, 100.0]).unwrap();

        assert_eq!(experiments.len(), 3);
        assert!(experiments[0].train_score > experiments[1].train_score);
        assert!(experiments[1].train_score > experiments[2].train_score);
        assert!(experiments[0].coefficient_abs_sum > experiments[2].coefficient_abs_sum);
    }
}
