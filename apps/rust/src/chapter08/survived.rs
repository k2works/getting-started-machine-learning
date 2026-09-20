//! Survived.csv の特徴量の列と正解ラベルの列。

use std::collections::HashMap;

use crate::chapter02::{Error, Result, Row, Table};

/// モデルに渡す特徴量の列。
pub const FEATURES: [&str; 7] = ["Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked"];

/// 正解ラベルの列（1 が生存、0 が死亡）。
pub const TARGET: &str = "Survived";

/// 生存を表す値。
pub const SURVIVED: i32 = 1;

/// 特徴量の列名をベクタにする。
pub fn feature_columns() -> Vec<String> {
    FEATURES.iter().map(|name| name.to_string()).collect()
}

/// 行を、特徴量の列だけを並べた表にする。行がほかの列を持っていても使わない。
pub fn features(rows: &[Row]) -> Table {
    Table {
        columns: feature_columns(),
        rows: rows.to_vec(),
    }
}

/// 行の Survived 列を、整数の正解ラベルにする。
pub fn target(rows: &[Row]) -> Result<Vec<i32>> {
    rows.iter()
        .map(|row| {
            let cell = row.text(TARGET)?;

            cell.trim().parse().map_err(|_| Error::NotANumber {
                column: TARGET.to_string(),
                value: cell.to_string(),
            })
        })
        .collect()
}

/// 特徴量の列の順に並べた値から、乗客 1 人分の行を作る。空文字列は欠損値。
pub fn passenger(values: &[&str]) -> Result<Row> {
    if values.len() != FEATURES.len() {
        return Err(Error::LengthMismatch {
            left: FEATURES.len(),
            right: values.len(),
        });
    }

    let cells: HashMap<String, String> = FEATURES
        .iter()
        .map(|name| name.to_string())
        .zip(values.iter().map(|value| value.to_string()))
        .collect();

    Ok(Row::new(cells))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 行の正解ラベルを整数にする() {
        let rows = vec![
            Row::new(HashMap::from([(TARGET.to_string(), "1".to_string())])),
            Row::new(HashMap::from([(TARGET.to_string(), "0".to_string())])),
        ];

        assert_eq!(target(&rows).unwrap(), vec![1, 0]);
    }

    #[test]
    fn 正解ラベルが数値でなければ失敗する() {
        let rows = vec![Row::new(HashMap::from([(
            TARGET.to_string(),
            "生存".to_string(),
        )]))];

        assert_eq!(
            target(&rows).unwrap_err().to_string(),
            "Survived を数値として読めません: 生存"
        );
    }

    #[test]
    fn 乗客の行は特徴量の列を持つ() {
        let row = passenger(&["1", "female", "", "0", "0", "50", "C"]).unwrap();

        assert_eq!(row.text("Sex").unwrap(), "female");
        assert!(row.is_missing("Age").unwrap());
    }

    #[test]
    fn 値の数が特徴量の列と違えば行を作れない() {
        assert_eq!(
            passenger(&["1"]).unwrap_err().to_string(),
            "件数が違います: 7 と 1"
        );
    }
}
