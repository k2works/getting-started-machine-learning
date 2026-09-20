//! アプリケーション層。置き場からモデルを読み込んで予測する。HTTP を知らない。

use super::domain::{ModelStore, Movie, Passenger, Result};
use super::store::{SALES_MODEL, SURVIVAL_MODEL};

/// モデルの名前と、読み込めるかどうか。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ModelHealth {
    pub name: &'static str,
    pub ready: bool,
}

/// 予測サービス。置き場は約束（trait）で受け取るので、テストではスタブに差し替えられる。
pub struct PredictionService {
    store: Box<dyn ModelStore + Send + Sync>,
}

impl PredictionService {
    /// 置き場を差し込んだサービスを作る。
    pub fn new(store: Box<dyn ModelStore + Send + Sync>) -> Self {
        PredictionService { store }
    }

    /// 映画の興行収入を予測する。
    pub fn predict_sales(&self, movie: Movie) -> Result<f64> {
        self.store.load_sales_model()?.predict_sales(movie)
    }

    /// 乗客が生存するかを予測する。
    pub fn predict_survival(&self, passenger: &Passenger) -> Result<bool> {
        self.store.load_survival_model()?.survives(passenger)
    }

    /// モデルごとに読み込めるかどうかを返す。
    pub fn health(&self) -> Vec<ModelHealth> {
        vec![
            ModelHealth {
                name: SALES_MODEL,
                ready: self.store.load_sales_model().is_ok(),
            },
            ModelHealth {
                name: SURVIVAL_MODEL,
                ready: self.store.load_survival_model().is_ok(),
            },
        ]
    }
}
