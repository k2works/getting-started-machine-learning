//! 第 15 章の統合テスト。実データで学習したモデルをつなぐ。データが無ければスキップする。

use std::path::PathBuf;
use std::sync::Arc;

use axum::body::Body;
use axum::http::{Request, StatusCode};
use getting_started_ml::chapter15::{
    FileModelStore, Movie, Passenger, PredictionService, api, train_and_save_models,
};
use getting_started_ml::dataset;
use tower::ServiceExt;

/// 学習データのディレクトリを返す。無ければ None（テストはスキップする）。
fn data_dir() -> Option<PathBuf> {
    let dir = dataset::current();

    if dir.join("cinema.csv").exists() && dir.join("Survived.csv").exists() {
        Some(dir)
    } else {
        eprintln!("学習データが配置されていない（gulp data:setup）のでスキップする");

        None
    }
}

/// 実データで学習して一時ディレクトリに保存した置き場を返す。
fn trained_store(dir: &std::path::Path, model_dir: PathBuf) -> FileModelStore {
    let store = FileModelStore::new(model_dir);
    train_and_save_models(dir, &store).expect("学習して保存できること");

    store
}

#[test]
fn 保存したモデルを読み込んで予測できる() {
    let Some(dir) = data_dir() else {
        return;
    };

    let temp = tempdir();
    let service = PredictionService::new(Box::new(trained_store(&dir, temp.clone())));

    let sales = service
        .predict_sales(Movie {
            sns1: 100.0,
            sns2: 2000.0,
            actor: 300.0,
            original: 1,
        })
        .expect("予測できること");

    assert!(
        (sales - 7_615.295_978_481_879).abs() < 1e-6,
        "興行収入 = {sales}"
    );

    let survived = service
        .predict_survival(&Passenger {
            pclass: "1".to_string(),
            sex: "female".to_string(),
            age: "30".to_string(),
            sib_sp: "0".to_string(),
            parch: "0".to_string(),
            fare: "80".to_string(),
            embarked: "S".to_string(),
        })
        .expect("予測できること");

    assert!(survived, "1 等級の女性は生存と予測されること");

    std::fs::remove_dir_all(temp).ok();
}

#[test]
fn 学習していなければモデルを読み込めない() {
    let temp = tempdir();
    let service = PredictionService::new(Box::new(FileModelStore::new(temp.clone())));

    for model in service.health() {
        assert!(!model.ready, "モデル {} は読み込めないこと", model.name);
    }

    std::fs::remove_dir_all(temp).ok();
}

#[tokio::test]
async fn 学習したモデルで_api_が動く() {
    let Some(dir) = data_dir() else {
        return;
    };

    let temp = tempdir();
    let service = Arc::new(PredictionService::new(Box::new(trained_store(
        &dir,
        temp.clone(),
    ))));

    let request = Request::builder()
        .method("POST")
        .uri("/cinema/sales")
        .header("content-type", "application/json")
        .body(Body::from(
            r#"{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}"#,
        ))
        .expect("要求を作れること");

    let response = api::router(service)
        .oneshot(request)
        .await
        .expect("応答が返ること");

    assert_eq!(response.status(), StatusCode::OK);

    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .expect("本文を読めること");
    let body = String::from_utf8(bytes.to_vec()).expect("UTF-8 であること");

    assert!(body.starts_with(r#"{"sales":"#), "本文 = {body}");

    std::fs::remove_dir_all(temp).ok();
}

/// テストごとの一時ディレクトリを作る。
fn tempdir() -> PathBuf {
    let dir = std::env::temp_dir().join(format!(
        "getting-started-ml-{}-{}",
        std::process::id(),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .expect("時刻を取れること")
            .as_nanos()
    ));
    std::fs::create_dir_all(&dir).expect("一時ディレクトリを作れること");

    dir
}
