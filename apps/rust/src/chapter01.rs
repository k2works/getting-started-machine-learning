//! 第 1 章: 人間が決めたルールできのこ派・たけのこ派を判定する。

use crate::dataset;
use std::fmt;
use std::path::Path;

/// 「20 代ならきのこ派」というルールの年代。
const KINOKO_AGE_GROUP: i32 = 20;

/// きのこ派の呼び名。
pub const KINOKO: &str = "きのこ";
/// たけのこ派の呼び名。
pub const TAKENOKO: &str = "たけのこ";

/// 第 1 章で起こりうる失敗。Rust には例外が無いので、失敗は型で表して `Result` で返す。
#[derive(Debug)]
pub enum Error {
    /// CSV を開けない・読めない。
    Io(std::io::Error),
    /// CSV の形が想定と違う。
    Csv(csv::Error),
    /// 列が無い。
    MissingColumn(String),
    /// 数値として読めない。
    NotANumber { column: String, value: String },
    /// 予測と正解ラベルの件数が違う。
    LengthMismatch { predictions: usize, labels: usize },
}

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Error::Io(error) => write!(f, "CSV を読めません: {error}"),
            Error::Csv(error) => write!(f, "CSV を読めません: {error}"),
            Error::MissingColumn(column) => write!(f, "列がありません: {column}"),
            Error::NotANumber { column, value } => {
                write!(f, "{column} を数値として読めません: {value}")
            }
            Error::LengthMismatch {
                predictions,
                labels,
            } => write!(
                f,
                "予測と正解ラベルの件数が違います: {predictions} と {labels}"
            ),
        }
    }
}

impl std::error::Error for Error {}

impl From<std::io::Error> for Error {
    fn from(error: std::io::Error) -> Self {
        Error::Io(error)
    }
}

impl From<csv::Error> for Error {
    fn from(error: csv::Error) -> Self {
        Error::Csv(error)
    }
}

/// この章の結果の型。`?` 演算子で失敗を上へ返せる。
pub type Result<T> = std::result::Result<T, Error>;

/// 学習データ KvsT.csv の 1 行。身長・体重・年代と、正解ラベルの派閥を持つ。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Person {
    pub height: i32,
    pub weight: i32,
    pub age_group: i32,
    pub faction: String,
}

/// 判定の手がかりになる特徴量。正解ラベルを持たない。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Features {
    pub height: i32,
    pub weight: i32,
    pub age_group: i32,
}

/// BOM 付きの UTF-8 の CSV を読み込み、列名で値を取り出して人物のリストにする。
/// csv クレートは BOM を自分で取り除くので、ほかの言語版のような前処理は要らない。
pub fn load_people(csv_file: &Path) -> Result<Vec<Person>> {
    let mut reader = csv::Reader::from_path(csv_file)?;
    let headers = reader.headers()?.clone();

    let mut people = Vec::new();

    for record in reader.records() {
        people.push(to_person(&headers, &record?)?);
    }

    Ok(people)
}

/// 1 行の値を人物にする。列が無い場合と数値でない場合は失敗を返す。
fn to_person(headers: &csv::StringRecord, record: &csv::StringRecord) -> Result<Person> {
    Ok(Person {
        height: number(headers, record, "身長")?,
        weight: number(headers, record, "体重")?,
        age_group: number(headers, record, "年代")?,
        faction: text(headers, record, "派閥")?.to_string(),
    })
}

/// 列名で数値を読む。
fn number(headers: &csv::StringRecord, record: &csv::StringRecord, column: &str) -> Result<i32> {
    let cell = text(headers, record, column)?;

    cell.parse().map_err(|_| Error::NotANumber {
        column: column.to_string(),
        value: cell.to_string(),
    })
}

/// 列名でセルを読む。借用した文字列をそのまま返すので、複製しない。
fn text<'a>(
    headers: &csv::StringRecord,
    record: &'a csv::StringRecord,
    column: &str,
) -> Result<&'a str> {
    headers
        .iter()
        .position(|name| name == column)
        .and_then(|position| record.get(position))
        .ok_or_else(|| Error::MissingColumn(column.to_string()))
}

/// 人物のリストを特徴量と正解ラベルに分ける。
pub fn split_features_and_labels(people: &[Person]) -> (Vec<Features>, Vec<String>) {
    let features = people
        .iter()
        .map(|person| Features {
            height: person.height,
            weight: person.weight,
            age_group: person.age_group,
        })
        .collect();

    let labels = people.iter().map(|person| person.faction.clone()).collect();

    (features, labels)
}

/// 人間が決めたルールで派閥を判定する。
pub fn predict_by_rule(features: Features) -> &'static str {
    if features.age_group == KINOKO_AGE_GROUP {
        KINOKO
    } else {
        TAKENOKO
    }
}

/// 予測が正解ラベルと一致した割合を返す。件数が違えば失敗を返す。
pub fn accuracy(predictions: &[String], labels: &[String]) -> Result<f64> {
    if predictions.len() != labels.len() {
        return Err(Error::LengthMismatch {
            predictions: predictions.len(),
            labels: labels.len(),
        });
    }

    let correct = predictions
        .iter()
        .zip(labels)
        .filter(|(prediction, label)| prediction == label)
        .count();

    Ok(correct as f64 / labels.len() as f64)
}

/// 実データでルールによる判定の正解率を表示する。
pub fn run(out: &mut impl std::io::Write) -> Result<()> {
    let people = load_people(&dataset::current().join("KvsT.csv"))?;
    let (x, t) = split_features_and_labels(&people);

    let predictions: Vec<String> = x
        .iter()
        .map(|features| predict_by_rule(*features).to_string())
        .collect();

    writeln!(out, "データ件数: {}", people.len())?;
    writeln!(
        out,
        "ルールによる判定の正解率: {:.4}",
        accuracy(&predictions, &t)?
    )?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    /// テスト用の特徴量を作る。
    fn features(height: i32, weight: i32, age_group: i32) -> Features {
        Features {
            height,
            weight,
            age_group,
        }
    }

    /// 文字列のスライスを String のベクタにする。
    fn labels(values: &[&str]) -> Vec<String> {
        values.iter().map(|value| value.to_string()).collect()
    }

    #[test]
    fn 二十代はきのこ派と判定する() {
        assert_eq!(predict_by_rule(features(170, 60, 20)), KINOKO);
    }

    #[test]
    fn 二十代以外はたけのこ派と判定する() {
        for age_group in [10, 30, 40, 50] {
            assert_eq!(predict_by_rule(features(170, 60, age_group)), TAKENOKO);
        }
    }

    #[test]
    fn 全部当たれば正解率は一になる() {
        let predictions = labels(&[KINOKO, TAKENOKO]);
        let truth = labels(&[KINOKO, TAKENOKO]);

        assert_eq!(accuracy(&predictions, &truth).unwrap(), 1.0);
    }

    #[test]
    fn 半分当たれば正解率は零点五になる() {
        let predictions = labels(&[KINOKO, KINOKO]);
        let truth = labels(&[KINOKO, TAKENOKO]);

        assert_eq!(accuracy(&predictions, &truth).unwrap(), 0.5);
    }

    #[test]
    fn 件数が違えば正解率を求められない() {
        let predictions = labels(&[KINOKO]);
        let truth = labels(&[KINOKO, TAKENOKO]);

        let error = accuracy(&predictions, &truth).unwrap_err();

        assert_eq!(
            error.to_string(),
            "予測と正解ラベルの件数が違います: 1 と 2"
        );
    }

    #[test]
    fn 人物のリストを特徴量と正解ラベルに分ける() {
        let people = vec![
            Person {
                height: 170,
                weight: 60,
                age_group: 20,
                faction: KINOKO.to_string(),
            },
            Person {
                height: 160,
                weight: 50,
                age_group: 30,
                faction: TAKENOKO.to_string(),
            },
        ];

        let (x, t) = split_features_and_labels(&people);

        assert_eq!(x, vec![features(170, 60, 20), features(160, 50, 30)]);
        assert_eq!(t, labels(&[KINOKO, TAKENOKO]));
    }
}
