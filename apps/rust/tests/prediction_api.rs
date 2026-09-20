//! 第 15 章の予測 API のテスト。置き場をスタブに差し替えるので、学習データが無くても走る。

use std::sync::Arc;

use axum::body::Body;
use axum::http::{Request, StatusCode};
use getting_started_ml::chapter15::{
    Error, ModelStore, Movie, Passenger, PredictionService, Result, SALES_MODEL, SURVIVAL_MODEL,
    SalesModel, SurvivalModel, api,
};
use tower::ServiceExt;

/// 置き場のスタブ。モデルがあるかどうかを差し替えられる。
struct StubStore {
    sales: bool,
    survival: bool,
}

/// 決まった値を返す興行収入のモデル。
struct FixedSales;

impl SalesModel for FixedSales {
    fn predict_sales(&self, _movie: Movie) -> Result<f64> {
        Ok(4321.5)
    }
}

/// 決まった値を返す生存予測のモデル。
struct FixedSurvival;

impl SurvivalModel for FixedSurvival {
    fn survives(&self, _passenger: &Passenger) -> Result<bool> {
        Ok(true)
    }
}

impl ModelStore for StubStore {
    fn load_sales_model(&self) -> Result<Box<dyn SalesModel>> {
        if self.sales {
            Ok(Box::new(FixedSales))
        } else {
            Err(Error::ModelNotFound(SALES_MODEL.to_string()))
        }
    }

    fn load_survival_model(&self) -> Result<Box<dyn SurvivalModel>> {
        if self.survival {
            Ok(Box::new(FixedSurvival))
        } else {
            Err(Error::ModelNotFound(SURVIVAL_MODEL.to_string()))
        }
    }
}

/// スタブの置き場を差し込んだ API を作る。
fn router(sales: bool, survival: bool) -> axum::Router {
    api::router(Arc::new(PredictionService::new(Box::new(StubStore {
        sales,
        survival,
    }))))
}

/// API を呼んで、状態コードと本文を返す。tower の `oneshot` でサーバーを起動せずに呼ぶ。
async fn call(
    method: &str,
    path: &str,
    body: &str,
    sales: bool,
    survival: bool,
) -> (StatusCode, String) {
    let request = Request::builder()
        .method(method)
        .uri(path)
        .header("content-type", "application/json")
        .body(Body::from(body.to_string()))
        .expect("要求を作れること");

    let response = router(sales, survival)
        .oneshot(request)
        .await
        .expect("応答が返ること");
    let status = response.status();
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .expect("本文を読めること");

    (
        status,
        String::from_utf8(bytes.to_vec()).expect("UTF-8 であること"),
    )
}

#[tokio::test]
async fn 興行収入を予測して_json_で返す() {
    let (status, body) = call(
        "POST",
        "/cinema/sales",
        r#"{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}"#,
        true,
        true,
    )
    .await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, r#"{"sales":4321.5}"#);
}

#[tokio::test]
async fn 生存を予測して_json_で返す() {
    let (status, body) = call(
        "POST",
        "/survived",
        r#"{"pclass": 1, "sex": "female", "age": 30, "sib_sp": 0, "parch": 0, "fare": 80, "embarked": "S"}"#,
        true,
        true,
    )
    .await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, r#"{"survived":true}"#);
}

#[tokio::test]
async fn 年齢と乗船港は省略できる() {
    let (status, _) = call(
        "POST",
        "/survived",
        r#"{"pclass": 3, "sex": "male", "sib_sp": 0, "parch": 0, "fare": 7.25}"#,
        true,
        true,
    )
    .await;

    assert_eq!(status, StatusCode::OK);
}

#[tokio::test]
async fn 入力が不正なら四百二十二と理由の一覧を返す() {
    let (status, body) = call("POST", "/cinema/sales", r#"{"sns1": 100}"#, true, true).await;

    assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
    assert!(body.contains("sns2 は必須です"), "本文 = {body}");
    assert!(body.contains("original は必須です"), "本文 = {body}");
}

#[tokio::test]
async fn 選択肢にない値を指摘する() {
    let (status, body) = call(
        "POST",
        "/survived",
        r#"{"pclass": 4, "sex": "unknown", "sib_sp": 0, "parch": 0, "fare": 10}"#,
        true,
        true,
    )
    .await;

    assert_eq!(status, StatusCode::UNPROCESSABLE_ENTITY);
    assert!(
        body.contains("pclass は 1、2、3 のどれかにしてください"),
        "本文 = {body}"
    );
    assert!(
        body.contains("sex は female、male のどれかにしてください"),
        "本文 = {body}"
    );
}

#[tokio::test]
async fn json_として読めなければ四百二十二を返し内部の型名を漏らさない() {
    for body in [r#"{"sns1": "たくさん"}"#, "{ここは JSON ではない", ""] {
        let (status, response) = call("POST", "/cinema/sales", body, true, true).await;

        assert_eq!(
            status,
            StatusCode::UNPROCESSABLE_ENTITY,
            "本文 = {response}"
        );
        assert!(
            response.contains("JSON の形式または値の型が正しくありません"),
            "本文 = {response}"
        );
        assert!(!response.contains("f64"), "本文 = {response}");
        assert!(!response.contains("MovieRequest"), "本文 = {response}");
    }
}

#[tokio::test]
async fn モデルが無ければ五百三を返す() {
    let (status, body) = call(
        "POST",
        "/cinema/sales",
        r#"{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}"#,
        false,
        true,
    )
    .await;

    assert_eq!(status, StatusCode::SERVICE_UNAVAILABLE);
    assert!(body.contains("モデルがありません: cinema"), "本文 = {body}");
}

#[tokio::test]
async fn ヘルスチェックはモデルごとの状態を返す() {
    let (status, body) = call("GET", "/health", "", true, true).await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(
        body,
        r#"{"status":"ok","models":{"cinema":true,"survived":true}}"#
    );

    let (_, degraded) = call("GET", "/health", "", true, false).await;

    assert!(
        degraded.contains(r#""status":"degraded""#),
        "本文 = {degraded}"
    );
}

#[tokio::test]
async fn 知らないパスは四百四許していないメソッドは四百五を返す() {
    let (status, _) = call("GET", "/unknown", "", true, true).await;
    assert_eq!(status, StatusCode::NOT_FOUND);

    let (status, _) = call("GET", "/cinema/sales", "", true, true).await;
    assert_eq!(status, StatusCode::METHOD_NOT_ALLOWED);
}
