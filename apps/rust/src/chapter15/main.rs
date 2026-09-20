//! 組み立てと起動。モデルを学習して保存し、予測 API を起動する。

use std::io::Write;
use std::path::Path;
use std::sync::Arc;

use super::api;
use super::domain::Result;
use super::service::PredictionService;
use super::store::FileModelStore;
use crate::chapter02::{Table, split_train_test};
use crate::chapter07::{self, cinema};
use crate::chapter08::{ClassWeight, Pipeline, survived};
use crate::dataset;

/// テストデータの割合と乱数のシード。第 7・8 章と同じ条件にする。
const TEST_SIZE: f64 = 0.2;
const SEED: u64 = 0;
/// 生存予測の決定木の深さ。
const MAX_DEPTH: usize = 5;
/// API を待ち受けるポート。
pub const PORT: u16 = 8015;
/// 学習済みモデルの保存先（apps/rust/model/ は .gitignore の対象）。
pub const MODEL_DIR: &str = "model";

/// 第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。
pub fn train_and_save_models(data_dir: &Path, store: &FileModelStore) -> Result<()> {
    let cinema_split = cinema::prepare(&data_dir.join("cinema.csv"), TEST_SIZE, SEED)?;
    let sales_model = chapter07::fit(&cinema_split.x_train, &cinema_split.t_train)?;
    store.save_sales_model(&sales_model)?;

    let table = Table::load(&data_dir.join("Survived.csv"))?;
    let labels = survived::target(&table.rows)?;
    let split = split_train_test(&table.rows, &labels, TEST_SIZE, SEED)?;
    let pipeline = Pipeline::build(Some(MAX_DEPTH), ClassWeight::Balanced)
        .fit(&survived::features(&split.x_train), &split.t_train)?;
    store.save_survival_model(&pipeline)?;

    Ok(())
}

/// モデルを学習して保存し、予測 API を起動する。
pub fn run(out: &mut impl Write) -> Result<()> {
    let store = FileModelStore::new(MODEL_DIR);
    train_and_save_models(&dataset::current(), &store)?;

    let service = Arc::new(PredictionService::new(Box::new(store)));

    for model in service.health() {
        writeln!(out, "モデル {}: {}", model.name, model.ready)?;
    }

    writeln!(out, "http://localhost:{PORT} で待ち受けます")?;

    serve(service)
}

/// tokio の実行時を作って待ち受ける。`run` は同期の関数なので、ここで非同期の世界に入る。
fn serve(service: api::SharedService) -> Result<()> {
    let runtime = tokio::runtime::Runtime::new()?;

    runtime.block_on(async move {
        let listener = tokio::net::TcpListener::bind(("127.0.0.1", PORT)).await?;

        axum::serve(listener, api::router(service)).await
    })?;

    Ok(())
}
