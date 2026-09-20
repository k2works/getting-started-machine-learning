//! 学習データのディレクトリを求める。

use std::path::{Path, PathBuf};

/// 環境変数の名前。実データの置き場をテストや CI から差し替えるために使う。
pub const DATA_DIR_ENV: &str = "ML_DATA_DIR";

/// 学習データのディレクトリを返す。`ML_DATA_DIR` が無ければ `../data/sukkiri-ml` を使う。
/// 環境変数の読み出しは、テストで差し替えられるように引数で受け取る。
pub fn from(getenv: impl Fn(&str) -> Option<String>) -> PathBuf {
    match getenv(DATA_DIR_ENV) {
        Some(value) if !value.is_empty() => PathBuf::from(value),
        _ => Path::new("..").join("data").join("sukkiri-ml"),
    }
}

/// 実行中のプロセスの環境変数から学習データのディレクトリを返す。
pub fn current() -> PathBuf {
    from(|name| std::env::var(name).ok())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn 環境変数があればその値を使う() {
        let dir = from(|_| Some("/tmp/data".to_string()));

        assert_eq!(dir, PathBuf::from("/tmp/data"));
    }

    #[test]
    fn 環境変数が無ければ既定の場所を使う() {
        let dir = from(|_| None);

        assert_eq!(dir, Path::new("..").join("data").join("sukkiri-ml"));
    }

    #[test]
    fn 環境変数が空文字列なら既定の場所を使う() {
        let dir = from(|_| Some(String::new()));

        assert_eq!(dir, Path::new("..").join("data").join("sukkiri-ml"));
    }
}
