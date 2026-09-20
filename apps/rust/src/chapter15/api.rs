//! プレゼンテーション層。axum のルーターと、エラーの HTTP ステータスコードへの変換。

use std::sync::Arc;

use axum::Json;
use axum::extract::{Request, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post};
use axum::{RequestExt, Router};
use serde::Serialize;

use super::domain::Error;
use super::service::PredictionService;
use super::validation::{MovieRequest, PassengerRequest, Validated};

/// JSON として読めない・型が合わない入力に返す理由。
/// serde のエラーには内部の型名が含まれるので、応答には出さない。
const INVALID_JSON: &str = "JSON の形式または値の型が正しくありません";

/// サービスをハンドラーで共有するための状態。
pub type SharedService = Arc<PredictionService>;

/// 興行収入の予測の応答。
#[derive(Serialize)]
struct SalesResponse {
    sales: f64,
}

/// 生存の予測の応答。
#[derive(Serialize)]
struct SurvivalResponse {
    survived: bool,
}

/// ヘルスチェックの応答。
#[derive(Serialize)]
struct HealthResponse {
    status: &'static str,
    models: std::collections::BTreeMap<&'static str, bool>,
}

/// 1 つの理由を返すエラーの応答。
#[derive(Serialize)]
struct ErrorResponse {
    detail: String,
}

/// 検証の理由の一覧を返すエラーの応答。
#[derive(Serialize)]
struct ValidationErrorResponse {
    detail: Vec<String>,
}

/// サービスを使う API を作る。listen はしない。
pub fn router(service: SharedService) -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/cinema/sales", post(predict_sales))
        .route("/survived", post(predict_survival))
        .with_state(service)
}

/// モデルごとに読み込めるかどうかを返す。
async fn health(State(service): State<SharedService>) -> Response {
    let mut models = std::collections::BTreeMap::new();
    let mut status = "ok";

    for model in service.health() {
        models.insert(model.name, model.ready);

        if !model.ready {
            status = "degraded";
        }
    }

    (StatusCode::OK, Json(HealthResponse { status, models })).into_response()
}

/// 映画の興行収入を予測する。
async fn predict_sales(State(service): State<SharedService>, request: Request) -> Response {
    let Some(request) = read_json::<MovieRequest>(request).await else {
        return invalid_json();
    };

    match request.validate() {
        Validated::Invalid(errors) => rejected(errors),
        Validated::Valid(movie) => match service.predict_sales(movie) {
            Ok(sales) => (StatusCode::OK, Json(SalesResponse { sales })).into_response(),
            Err(error) => failure(&error),
        },
    }
}

/// 乗客が生存するかを予測する。
async fn predict_survival(State(service): State<SharedService>, request: Request) -> Response {
    let Some(request) = read_json::<PassengerRequest>(request).await else {
        return invalid_json();
    };

    match request.validate() {
        Validated::Invalid(errors) => rejected(errors),
        Validated::Valid(passenger) => match service.predict_survival(&passenger) {
            Ok(survived) => (StatusCode::OK, Json(SurvivalResponse { survived })).into_response(),
            Err(error) => failure(&error),
        },
    }
}

/// 本文を JSON として読む。読めなければ `None`（axum の既定の 400 を使わず、自分で 422 を返す）。
async fn read_json<T>(request: Request) -> Option<T>
where
    T: serde::de::DeserializeOwned + Send + 'static,
{
    request
        .extract::<Json<T>, _>()
        .await
        .ok()
        .map(|Json(value)| value)
}

/// JSON として読めなかったときの応答。
fn invalid_json() -> Response {
    rejected(vec![INVALID_JSON.to_string()])
}

/// 検証で弾いたときの応答。
fn rejected(errors: Vec<String>) -> Response {
    (
        StatusCode::UNPROCESSABLE_ENTITY,
        Json(ValidationErrorResponse { detail: errors }),
    )
        .into_response()
}

/// ドメインのエラーを HTTP のステータスコードに変える。
fn failure(error: &Error) -> Response {
    match error {
        Error::ModelNotFound(_) => (
            StatusCode::SERVICE_UNAVAILABLE,
            Json(ErrorResponse {
                detail: error.to_string(),
            }),
        )
            .into_response(),
        _ => (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(ErrorResponse {
                detail: "予測できませんでした".to_string(),
            }),
        )
            .into_response(),
    }
}
