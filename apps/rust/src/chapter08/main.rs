//! 第 8 章の実行。クラスの重みごとの評価と、保存して読み込んだモデルでの予測を表示する。

use std::io::Write;
use std::path::{Path, PathBuf};

use super::evaluation::{self, Evaluation};
use super::modelfile;
use super::pipeline::Pipeline;
use super::survived;
use super::weightedtree::ClassWeight;
use crate::chapter02::{Result, Table, split_train_test};
use crate::dataset;

/// テストデータの割合。
const TEST_SIZE: f64 = 0.2;
/// 分割の乱数のシード。
const SEED: u64 = 0;
/// 決定木の深さの上限。
const MAX_DEPTH: Option<usize> = Some(5);

/// 学習済みのパイプラインの保存先（apps/rust/model/ は .gitignore の対象）。
pub fn model_file() -> PathBuf {
    Path::new("model").join("survived.json")
}

/// クラスの重みごとの評価結果を表示し、学習済みのパイプラインを保存して読み込む。
pub fn run(out: &mut impl Write) -> Result<()> {
    run_with(out, &model_file())
}

/// 保存先を指定して実行する。テストから一時ディレクトリを渡すために使う。
pub fn run_with(out: &mut impl Write, model_file: &Path) -> Result<()> {
    let rows = Table::load(&dataset::current().join("Survived.csv"))?.rows;
    let t = survived::target(&rows)?;
    let split = split_train_test(&rows, &t, TEST_SIZE, SEED)?;
    let survivors = t
        .iter()
        .filter(|label| **label == survived::SURVIVED)
        .count();

    writeln!(
        out,
        "データ件数: {}（生存 {survivors}, 死亡 {}）",
        rows.len(),
        t.len() - survivors
    )?;
    writeln!(
        out,
        "訓練データ: {} 件, テストデータ: {} 件",
        split.x_train.len(),
        split.x_test.len()
    )?;

    let mut balanced = None;

    for class_weight in [ClassWeight::None, ClassWeight::Balanced] {
        let fitted = Pipeline::build(MAX_DEPTH, class_weight)
            .fit(&survived::features(&split.x_train), &split.t_train)?;
        let result = evaluation::evaluate(&fitted, &split)?;

        print_evaluation(out, class_weight, &result)?;

        if class_weight == ClassWeight::Balanced {
            balanced = Some(fitted);
        }
    }

    let balanced = balanced.expect("balanced のパイプラインがあること");
    modelfile::save(&balanced, model_file)?;

    let loaded = modelfile::load(model_file)?;
    let new_passengers = survived::features(&[
        survived::passenger(&["1", "female", "", "0", "0", "50", "C"])?,
        survived::passenger(&["3", "male", "", "0", "0", "8", "S"])?,
    ]);

    writeln!(
        out,
        "保存したモデル: {}",
        model_file
            .file_name()
            .map_or_else(String::new, |name| name.to_string_lossy().into_owned())
    )?;
    writeln!(
        out,
        "架空の乗客の予測: {:?}",
        loaded.predict(&new_passengers)?
    )?;

    Ok(())
}

/// 評価結果を 1 行で表示する。
fn print_evaluation(
    out: &mut impl Write,
    class_weight: ClassWeight,
    result: &Evaluation,
) -> Result<()> {
    writeln!(
        out,
        "classWeight={}: 訓練 {:.3}, テスト {:.3}, 生存者 {} 人中 {} 人を発見",
        class_weight.name(),
        result.train_accuracy,
        result.test_accuracy,
        result.survivors,
        result.found_survivors
    )?;

    Ok(())
}
