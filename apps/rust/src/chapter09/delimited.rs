//! 文字コードと区切り文字を指定して、区切り文字で区切ったファイルを表に読み込む。

use std::collections::HashMap;
use std::path::Path;

use crate::chapter02::{Row, Table};

use super::{Error, Result};

/// 読み込むファイルの文字コード。`enum` にすると、呼び出し側は 2 つのどちらかしか渡せない。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Encoding {
    /// UTF-8。
    Utf8,
    /// Shift_JIS。
    ShiftJis,
}

impl Encoding {
    /// encoding_rs の符号化方式を返す。
    fn encoding(self) -> &'static encoding_rs::Encoding {
        match self {
            Encoding::Utf8 => encoding_rs::UTF_8,
            Encoding::ShiftJis => encoding_rs::SHIFT_JIS,
        }
    }

    /// 失敗したときに表示する名前。
    fn name(self) -> &'static str {
        match self {
            Encoding::Utf8 => "UTF-8",
            Encoding::ShiftJis => "Shift_JIS",
        }
    }
}

/// 1 行目を列名として読み込む。指定した文字コードで読めなければ失敗を返す。
pub fn load(file: &Path, encoding: Encoding, delimiter: char) -> Result<Table> {
    let bytes = std::fs::read(file)?;
    let (text, _, had_errors) = encoding.encoding().decode(&bytes);

    if had_errors {
        return Err(Error::Decode {
            file: file.display().to_string(),
            encoding: encoding.name(),
        });
    }

    let mut lines = text.lines().filter(|line| !line.trim().is_empty());
    let columns: Vec<String> = match lines.next() {
        Some(header) => header.split(delimiter).map(str::to_string).collect(),
        None => return Err(Error::Empty("行")),
    };

    let rows = lines
        .map(|line| {
            let cells: HashMap<String, String> = columns
                .iter()
                .cloned()
                .zip(line.split(delimiter).map(str::to_string))
                .collect();

            Row::new(cells)
        })
        .collect();

    Ok(Table { columns, rows })
}

#[cfg(test)]
mod tests {
    use super::*;

    /// 一時ファイルにバイト列を書き、読み込んでから消す。
    fn with_file<T>(name: &str, bytes: &[u8], body: impl Fn(&Path) -> T) -> T {
        let file = std::env::temp_dir().join(name);
        std::fs::write(&file, bytes).unwrap();

        let result = body(&file);
        std::fs::remove_file(&file).unwrap();

        result
    }

    #[test]
    fn タブ区切りのファイルを読み込む() {
        with_file(
            "chapter09_tab.tsv",
            "weather_id\tcnt\n1\t100\n2\t200\n".as_bytes(),
            |file| {
                let table = load(file, Encoding::Utf8, '\t').unwrap();

                assert_eq!(table.columns, vec!["weather_id", "cnt"]);
                assert_eq!(table.rows.len(), 2);
                assert_eq!(table.rows[1].number("cnt").unwrap(), Some(200.0));
            },
        );
    }

    #[test]
    fn shift_jisのファイルを読み込む() {
        // "weather_id,weather\n1,晴れ\n" を Shift_JIS で符号化したバイト列
        let (bytes, _, _) = encoding_rs::SHIFT_JIS.encode("weather_id,weather\n1,晴れ\n");

        with_file("chapter09_sjis.csv", &bytes, |file| {
            let table = load(file, Encoding::ShiftJis, ',').unwrap();

            assert_eq!(table.rows[0].text("weather").unwrap(), "晴れ");
        });
    }

    #[test]
    fn shift_jisのファイルをutf8として読むと失敗する() {
        let (bytes, _, _) = encoding_rs::SHIFT_JIS.encode("weather_id,weather\n1,晴れ\n");

        with_file("chapter09_sjis_as_utf8.csv", &bytes, |file| {
            let error = load(file, Encoding::Utf8, ',').unwrap_err().to_string();

            assert!(error.ends_with(" を UTF-8 として読めません"), "{error}");
        });
    }

    #[test]
    fn 空のファイルは読み込めない() {
        with_file("chapter09_empty.csv", b"", |file| {
            assert_eq!(
                load(file, Encoding::Utf8, ',').unwrap_err().to_string(),
                "行が 1 件もありません"
            );
        });
    }
}
