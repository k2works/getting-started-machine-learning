//! プレゼンテーション層の入口。要求の JSON を受け取り、検証してドメインの値にする。

use serde::Deserialize;

use super::domain::{Movie, Passenger};

/// 検証の結果。理由の一覧が空なら正しい。
#[derive(Debug, Clone, PartialEq)]
pub enum Validated<T> {
    Valid(T),
    Invalid(Vec<String>),
}

/// 理由（`None` は問題なし）を集め、1 つも無ければ値を作る。
fn validate<T>(reasons: Vec<Option<String>>, value: impl FnOnce() -> T) -> Validated<T> {
    let errors: Vec<String> = reasons.into_iter().flatten().collect();

    if errors.is_empty() {
        Validated::Valid(value())
    } else {
        Validated::Invalid(errors)
    }
}

/// 値が JSON に無ければ理由を返す。
fn required<T>(field: &str, value: Option<&T>) -> Option<String> {
    if value.is_some() {
        None
    } else {
        Some(format!("{field} は必須です"))
    }
}

/// 値が負であれば理由を返す。値が無ければ何も言わない（`required` の担当）。
fn not_negative(field: &str, value: Option<f64>) -> Option<String> {
    match value {
        Some(value) if value < 0.0 => Some(format!("{field} は 0 以上にしてください")),
        _ => None,
    }
}

/// 値が選択肢に無ければ理由を返す。値が無ければ何も言わない。
fn one_of<T: PartialEq + std::fmt::Display>(
    field: &str,
    value: Option<&T>,
    allowed: &[T],
) -> Option<String> {
    match value {
        Some(value) if !allowed.contains(value) => {
            let choices: Vec<String> = allowed.iter().map(ToString::to_string).collect();

            Some(format!(
                "{field} は {} のどれかにしてください",
                choices.join("、")
            ))
        }
        _ => None,
    }
}

/// 興行収入の予測の要求。
/// Rust の `Option` は「JSON に値が無かった」をそのまま表せる（Go 版のポインタに当たる）。
#[derive(Debug, Deserialize)]
pub struct MovieRequest {
    pub sns1: Option<f64>,
    pub sns2: Option<f64>,
    pub actor: Option<f64>,
    pub original: Option<i32>,
}

impl MovieRequest {
    /// 検証して、正しければ映画の特徴量にする。
    pub fn validate(&self) -> Validated<Movie> {
        validate(
            vec![
                required("sns1", self.sns1.as_ref()),
                required("sns2", self.sns2.as_ref()),
                required("actor", self.actor.as_ref()),
                required("original", self.original.as_ref()),
                not_negative("sns1", self.sns1),
                not_negative("sns2", self.sns2),
                not_negative("actor", self.actor),
                one_of("original", self.original.as_ref(), &[0, 1]),
            ],
            || Movie {
                sns1: self.sns1.unwrap_or_default(),
                sns2: self.sns2.unwrap_or_default(),
                actor: self.actor.unwrap_or_default(),
                original: self.original.unwrap_or_default(),
            },
        )
    }
}

/// 生存の予測の要求。年齢と乗船港は省略できる。
#[derive(Debug, Deserialize)]
pub struct PassengerRequest {
    pub pclass: Option<i32>,
    pub sex: Option<String>,
    pub age: Option<f64>,
    pub sib_sp: Option<i32>,
    pub parch: Option<i32>,
    pub fare: Option<f64>,
    pub embarked: Option<String>,
}

impl PassengerRequest {
    /// 検証して、正しければ乗客の特徴量にする。省略された列は空文字列（欠損値）にする。
    pub fn validate(&self) -> Validated<Passenger> {
        let sexes = ["female".to_string(), "male".to_string()];
        let ports = ["C".to_string(), "Q".to_string(), "S".to_string()];

        validate(
            vec![
                required("pclass", self.pclass.as_ref()),
                required("sex", self.sex.as_ref()),
                required("sib_sp", self.sib_sp.as_ref()),
                required("parch", self.parch.as_ref()),
                required("fare", self.fare.as_ref()),
                one_of("pclass", self.pclass.as_ref(), &[1, 2, 3]),
                one_of("sex", self.sex.as_ref(), &sexes),
                not_negative("age", self.age),
                not_negative("sib_sp", self.sib_sp.map(f64::from)),
                not_negative("parch", self.parch.map(f64::from)),
                not_negative("fare", self.fare),
                one_of("embarked", self.embarked.as_ref(), &ports),
            ],
            || Passenger {
                pclass: number_cell(self.pclass.map(f64::from)),
                sex: self.sex.clone().unwrap_or_default(),
                age: number_cell(self.age),
                sib_sp: number_cell(self.sib_sp.map(f64::from)),
                parch: number_cell(self.parch.map(f64::from)),
                fare: number_cell(self.fare),
                embarked: self.embarked.clone().unwrap_or_default(),
            },
        )
    }
}

/// 省略できる数値をセルの文字列にする。省略されていれば空文字列（欠損値）。
fn number_cell(value: Option<f64>) -> String {
    match value {
        Some(value) if value.fract() == 0.0 => format!("{}", value as i64),
        Some(value) => value.to_string(),
        None => String::new(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 必須の値が無ければ理由を並べて返す() {
        let request = MovieRequest {
            sns1: Some(100.0),
            sns2: None,
            actor: None,
            original: None,
        };

        assert_eq!(
            request.validate(),
            Validated::Invalid(vec![
                "sns2 は必須です".to_string(),
                "actor は必須です".to_string(),
                "original は必須です".to_string(),
            ])
        );
    }

    #[test]
    fn 負の値と選択肢にない値を指摘する() {
        let request = MovieRequest {
            sns1: Some(-1.0),
            sns2: Some(2000.0),
            actor: Some(300.0),
            original: Some(2),
        };

        assert_eq!(
            request.validate(),
            Validated::Invalid(vec![
                "sns1 は 0 以上にしてください".to_string(),
                "original は 0、1 のどれかにしてください".to_string(),
            ])
        );
    }

    #[test]
    fn 正しい要求は映画の特徴量になる() {
        let request = MovieRequest {
            sns1: Some(100.0),
            sns2: Some(2000.0),
            actor: Some(300.0),
            original: Some(1),
        };

        assert_eq!(
            request.validate(),
            Validated::Valid(Movie {
                sns1: 100.0,
                sns2: 2000.0,
                actor: 300.0,
                original: 1,
            })
        );
    }

    #[test]
    fn 年齢と乗船港は省略できる() {
        let request = PassengerRequest {
            pclass: Some(3),
            sex: Some("male".to_string()),
            age: None,
            sib_sp: Some(0),
            parch: Some(0),
            fare: Some(7.25),
            embarked: None,
        };

        assert_eq!(
            request.validate(),
            Validated::Valid(Passenger {
                pclass: "3".to_string(),
                sex: "male".to_string(),
                age: String::new(),
                sib_sp: "0".to_string(),
                parch: "0".to_string(),
                fare: "7.25".to_string(),
                embarked: String::new(),
            })
        );
    }

    #[test]
    fn 等級と性別と乗船港の選択肢を確かめる() {
        let request = PassengerRequest {
            pclass: Some(4),
            sex: Some("unknown".to_string()),
            age: None,
            sib_sp: Some(0),
            parch: Some(0),
            fare: Some(10.0),
            embarked: Some("X".to_string()),
        };

        assert_eq!(
            request.validate(),
            Validated::Invalid(vec![
                "pclass は 1、2、3 のどれかにしてください".to_string(),
                "sex は female、male のどれかにしてください".to_string(),
                "embarked は C、Q、S のどれかにしてください".to_string(),
            ])
        );
    }
}
