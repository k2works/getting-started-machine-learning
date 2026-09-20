//! 2 次の多項式特徴量（2 乗の項と交互作用の項）を作る。

use crate::chapter02::Features;

use super::Result;

/// 2 つの列の組。左と右が同じなら 2 乗の項を表す。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Pair {
    pub left: String,
    pub right: String,
}

impl Pair {
    /// 項の名前。scikit-learn の `get_feature_names_out` と同じ形（"RM^2"・"RM LSTAT"）にする。
    pub fn name(&self) -> String {
        if self.left == self.right {
            format!("{}^2", self.left)
        } else {
            format!("{} {}", self.left, self.right)
        }
    }
}

/// 重複を許して 2 つの列を選ぶ組を、scikit-learn の `PolynomialFeatures` と同じ順に並べる。
pub fn pairs_with_replacement(columns: &[String]) -> Vec<Pair> {
    let mut pairs = Vec::new();

    for (i, left) in columns.iter().enumerate() {
        for right in columns.iter().skip(i) {
            pairs.push(Pair {
                left: left.clone(),
                right: right.clone(),
            });
        }
    }

    pairs
}

/// 指定した列の後ろに、2 乗の項と交互作用の項を加える。
pub fn expand(x: &[Features], columns: &[String]) -> Result<Vec<Features>> {
    let pairs = pairs_with_replacement(columns);
    let names: Vec<String> = columns
        .iter()
        .cloned()
        .chain(pairs.iter().map(Pair::name))
        .collect();

    x.iter()
        .map(|features| {
            let mut values = Vec::with_capacity(names.len());

            for column in columns {
                values.push(features.value(column)?);
            }

            for pair in &pairs {
                values.push(features.value(&pair.left)? * features.value(&pair.right)?);
            }

            Ok(Features::new(names.clone(), values)?)
        })
        .collect()
}

/// 指定した列だけを、その順に選ぶ。
pub fn select(x: &[Features], columns: &[String]) -> Result<Vec<Features>> {
    x.iter()
        .map(|features| {
            let values = columns
                .iter()
                .map(|column| features.value(column))
                .collect::<crate::chapter02::Result<Vec<f64>>>()?;

            Ok(Features::new(columns.to_vec(), values)?)
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn strings(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    fn row(rm: f64, lstat: f64) -> Features {
        Features::new(strings(&["RM", "LSTAT"]), vec![rm, lstat]).unwrap()
    }

    #[test]
    fn 重複を許して2つの列を選ぶ組を作る() {
        let names: Vec<String> = pairs_with_replacement(&strings(&["RM", "LSTAT"]))
            .iter()
            .map(Pair::name)
            .collect();

        assert_eq!(names, strings(&["RM^2", "RM LSTAT", "LSTAT^2"]));
    }

    #[test]
    fn 二乗の項と交互作用の項を加える() {
        let expanded = expand(&[row(2.0, 3.0)], &strings(&["RM", "LSTAT"])).unwrap();

        assert_eq!(
            expanded[0].columns,
            strings(&["RM", "LSTAT", "RM^2", "RM LSTAT", "LSTAT^2"])
        );
        assert_eq!(expanded[0].values, vec![2.0, 3.0, 4.0, 6.0, 9.0]);
    }

    #[test]
    fn 指定した列だけをその順に選ぶ() {
        let expanded = expand(&[row(2.0, 3.0)], &strings(&["RM", "LSTAT"])).unwrap();

        let selected = select(&expanded, &strings(&["LSTAT^2", "RM"])).unwrap();

        assert_eq!(selected[0].columns, strings(&["LSTAT^2", "RM"]));
        assert_eq!(selected[0].values, vec![9.0, 2.0]);
    }

    #[test]
    fn 無い列は選べない() {
        assert_eq!(
            select(&[row(2.0, 3.0)], &strings(&["RM^2"]))
                .unwrap_err()
                .to_string(),
            "列がありません: RM^2"
        );
    }
}
