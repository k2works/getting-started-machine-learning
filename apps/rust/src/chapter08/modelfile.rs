//! 学習済みのパイプラインを JSON で保存し、読み込む。
//!
//! Java 版はオブジェクトのシリアライズ、Go 版は `encoding/gob` を使ったが、
//! Rust の標準ライブラリにはシリアライズの仕組みが無いので serde を使う。
//! 型に `#[derive(Serialize, Deserialize)]` を書くと、変換のコードがコンパイル時に作られる。

use std::fs;
use std::path::Path;

use super::pipeline::FittedPipeline;
use crate::chapter02::{Error, Result};

/// パイプライン全体（前処理で求めた値とモデル）を JSON で保存する。
pub fn save(pipeline: &FittedPipeline, model_file: &Path) -> Result<()> {
    if let Some(parent) = model_file.parent() {
        fs::create_dir_all(parent).map_err(|error| failed(model_file, &error))?;
    }

    let json = serde_json::to_string_pretty(pipeline)
        .map_err(|error| Error::Library(error.to_string()))?;

    fs::write(model_file, json).map_err(|error| failed(model_file, &error))?;

    Ok(())
}

/// ファイルの読み書きの失敗を、どのファイルかが分かる失敗にする。
/// 第 2 章の `Error::Io` は CSV 向けの文言なので、モデルのファイルには使わない。
fn failed(model_file: &Path, error: &std::io::Error) -> Error {
    Error::Library(format!("{} を扱えません: {error}", model_file.display()))
}

/// 保存したパイプラインを読み込む。JSON の形が違えば失敗する。
///
/// Java 版は読み込むクラスを絞り込む必要があったが、serde は「この型に戻す」と書いてあるので、
/// 知らない形の JSON は型に合わずに失敗する。任意のコードが動くことはない。
pub fn load(model_file: &Path) -> Result<FittedPipeline> {
    let json = fs::read_to_string(model_file).map_err(|error| failed(model_file, &error))?;

    serde_json::from_str(&json).map_err(|error| Error::Library(error.to_string()))
}

#[cfg(test)]
mod tests {
    use super::super::preprocessing::FittedStep;
    use super::super::weightedtree::{FittedDecisionTree, Tree};
    use super::*;

    /// 小さな学習済みパイプラインを作る。
    fn pipeline() -> FittedPipeline {
        FittedPipeline {
            steps: vec![FittedStep::MostFrequent {
                column: "Embarked".to_string(),
                most_frequent: "S".to_string(),
            }],
            model: FittedDecisionTree {
                root: Tree::Node {
                    feature: "Fare".to_string(),
                    threshold: 10.0,
                    left: Box::new(Tree::Leaf { label: 0 }),
                    right: Box::new(Tree::Leaf { label: 1 }),
                },
            },
        }
    }

    #[test]
    fn 保存して読み込むと同じパイプラインになる() {
        let dir = std::env::temp_dir().join("getting-started-ml-chapter08");
        let model_file = dir.join("survived.json");

        save(&pipeline(), &model_file).unwrap();

        assert_eq!(load(&model_file).unwrap(), pipeline());

        fs::remove_dir_all(&dir).unwrap();
    }

    #[test]
    fn 形の違うジェイソンは読み込めない() {
        let dir = std::env::temp_dir().join("getting-started-ml-chapter08-broken");
        let model_file = dir.join("survived.json");

        fs::create_dir_all(&dir).unwrap();
        fs::write(&model_file, "{\"steps\": []}").unwrap();

        assert!(
            load(&model_file)
                .unwrap_err()
                .to_string()
                .starts_with("ライブラリが失敗しました: ")
        );

        fs::remove_dir_all(&dir).unwrap();
    }

    #[test]
    fn 無いファイルは読み込めない() {
        let model_file = std::env::temp_dir().join("getting-started-ml-chapter08-none.json");

        assert!(
            load(&model_file)
                .unwrap_err()
                .to_string()
                .contains("を扱えません: ")
        );
    }
}
