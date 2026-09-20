//! インフラ層。学習済みモデルをファイルに保存し、読み込む。

use std::path::{Path, PathBuf};

use super::domain::{
    Error, LinearSalesModel, ModelStore, PipelineSurvivalModel, Result, SalesModel, SurvivalModel,
};
use crate::chapter07::LinearModel;
use crate::chapter08::{FittedPipeline, modelfile};

/// 興行収入のモデルの名前。
pub const SALES_MODEL: &str = "cinema";
/// 生存予測のモデルの名前。
pub const SURVIVAL_MODEL: &str = "survived";

/// 学習済みモデルをディレクトリのファイルに保存し、読み込む置き場。
pub struct FileModelStore {
    model_dir: PathBuf,
}

impl FileModelStore {
    /// 保存先のディレクトリを指定した置き場を作る。
    pub fn new(model_dir: impl Into<PathBuf>) -> Self {
        FileModelStore {
            model_dir: model_dir.into(),
        }
    }

    /// 興行収入のモデルのファイル。数値だけなので JSON にする。
    fn sales_model_file(&self) -> PathBuf {
        self.model_dir.join(format!("{SALES_MODEL}.json"))
    }

    /// 生存予測のモデルのファイル。前処理を含むので第 8 章と同じ形式にする。
    fn survival_model_file(&self) -> PathBuf {
        self.model_dir.join(format!("{SURVIVAL_MODEL}.json"))
    }

    /// 線形回帰のモデルを JSON で保存する。
    pub fn save_sales_model(&self, model: &LinearModel) -> Result<()> {
        std::fs::create_dir_all(&self.model_dir)?;
        std::fs::write(self.sales_model_file(), serde_json::to_vec(model)?)?;

        Ok(())
    }

    /// 学習済みパイプラインを保存する（第 8 章の `modelfile::save`）。
    pub fn save_survival_model(&self, pipeline: &FittedPipeline) -> Result<()> {
        std::fs::create_dir_all(&self.model_dir)?;
        modelfile::save(pipeline, &self.survival_model_file())?;

        Ok(())
    }
}

/// ファイルが無いことを「モデルがない」に変える。それ以外の失敗はそのまま返す。
fn read_model(file: &Path, name: &str) -> Result<Vec<u8>> {
    match std::fs::read(file) {
        Ok(contents) => Ok(contents),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            Err(Error::ModelNotFound(name.to_string()))
        }
        Err(error) => Err(Error::Io(error)),
    }
}

impl ModelStore for FileModelStore {
    fn load_sales_model(&self) -> Result<Box<dyn SalesModel>> {
        let contents = read_model(&self.sales_model_file(), SALES_MODEL)?;
        let model: LinearModel = serde_json::from_slice(&contents)?;

        Ok(Box::new(LinearSalesModel { model }))
    }

    fn load_survival_model(&self) -> Result<Box<dyn SurvivalModel>> {
        let file = self.survival_model_file();

        if !file.exists() {
            return Err(Error::ModelNotFound(SURVIVAL_MODEL.to_string()));
        }

        Ok(Box::new(PipelineSurvivalModel {
            pipeline: modelfile::load(&file)?,
        }))
    }
}
