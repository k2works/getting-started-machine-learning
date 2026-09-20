---
type: Article
title: "第 15 章: 機械学習 API とモジュール設計"
description: "第 7・8 章の学習済みモデルを axum で予測 API として公開し、trait による層の分離・Result のエラー変換・所有権を意識した状態の共有を Rust の書き方で組み立てる。"
tags: [article,getting-start-ml,rust]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T15:10:00Z }
---

# 第 15 章: 機械学習 API とモジュール設計

## 15.1 はじめに

シリーズの最後の章です。ここまでに作ったモデルは、テストと `cargo run --bin chapters -- chapterNN` の中でしか動きませんでした。この章では、第 7 章の線形回帰（映画の興行収入）と第 8 章のパイプライン（乗客の生存）を **HTTP の予測 API** として公開します。

題材は [Python 版の第 15 章](../python/15-machine-learning-api-and-module-design.md) と同じで、3 つのエンドポイントを作ります。

| メソッド | パス | 役割 |
|---------|------|------|
| `GET` | `/health` | モデルを読み込めるかどうかを返す |
| `POST` | `/cinema/sales` | 映画の特徴量から興行収入を予測する |
| `POST` | `/survived` | 乗客の特徴量から生存を予測する |

Rust 版では [axum](https://docs.rs/axum/) 0.8 と [tokio](https://tokio.rs/) を使います。[Go 版](../go/15-machine-learning-api-and-module-design.md) は標準ライブラリだけで書きましたが、**Rust の標準ライブラリに HTTP サーバーはありません**。ここは Java 版（Javalin）・C# 版（ASP.NET Core）と同じく、フレームワークを入れる場面です（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。

この章で Rust らしいのは次の 3 点です。

1. **trait で層を分ける** — 置き場もモデルも `trait` で約束し、実装を差し替える。`Box<dyn Trait>` を使うと、動的なディスパッチと所有権の話が出てくる
2. **`Result` を HTTP のステータスコードに変える** — 例外のハンドラーではなく、`match` で明示的に変換する
3. **状態を複数のハンドラーで共有する** — 非同期のハンドラーは同時に走るので、`Arc` で包み、`Send + Sync` を型で要求する

## 15.2 層を分ける

### 5 つのファイルと依存の向き

`src/chapter15/` の中を、ファイルで層に分けます。

```text
domain.rs       ドメイン層             Movie・Passenger・モデルと置き場の trait・Error
service.rs      アプリケーション層     置き場からモデルを読んで予測する
store.rs        インフラ層             ファイルへの保存と読み込み
validation.rs   プレゼンテーション層   要求の型と検証
api.rs          プレゼンテーション層   axum のルーター・ステータスコードへの変換
main.rs         組み立て               学習・保存・サーバーの起動
```

依存の向きは内側（ドメイン）へ向けます。

```text
api.rs ──→ service.rs ──→ domain.rs ←── store.rs
                              ↑
                        （trait だけを知る）
```

`service.rs` が知っているのは `ModelStore` という**約束**（trait）だけで、その実装が JSON のファイルなのか、データベースなのか、テストのスタブなのかを知りません。`domain.rs` は axum を `use` しません。

Go 版と同じ分け方ですが、**Rust では trait の実装が明示的**です。Go の構造的なインターフェース（メソッドの形が合えば実装したことになる）と違い、`impl ModelStore for FileModelStore` と書きます。書く量は増えますが、「この型はこの約束を満たすつもりだ」という意図がコードに残ります。

### 約束を trait で書く

```rust
/// 興行収入を予測する約束。
pub trait SalesModel {
    fn predict_sales(&self, movie: Movie) -> Result<f64>;
}

/// 生存を予測する約束。
pub trait SurvivalModel {
    fn survives(&self, passenger: &Passenger) -> Result<bool>;
}

/// 学習済みモデルの置き場の約束。
/// 読み込めなければ `Error::ModelNotFound` を返す。
pub trait ModelStore {
    fn load_sales_model(&self) -> Result<Box<dyn SalesModel>>;
    fn load_survival_model(&self) -> Result<Box<dyn SurvivalModel>>;
}
```

`Box<dyn SalesModel>` が「`SalesModel` を実装した何かへのポインタ」です。`dyn` は動的なディスパッチ（呼び出し先を実行時に決める）を表し、`Box` はそれをヒープに置いて所有します。

なぜ `Box` が要るのでしょうか。返す型が実装によって違う（ファイルの置き場なら `LinearSalesModel`、テストなら `FixedSales`）からです。戻り値の型は 1 つに決まっていなければならないので、**具体的な型を隠す**必要があります。`impl Trait` は「1 つの具体型に決まる」場合にしか使えないので、ここでは使えません。

Go の `interface` は値の中にポインタと型情報を持つので `Box` に当たるものが見えませんが、やっていることは同じです。Java の `interface` 型の参照も同じです。**Rust だけが、費用（ヒープの確保と間接呼び出し）を型に書かせます。**

### 引数が `self` を借りるか、値を取るか

`predict_sales(&self, movie: Movie)` と `survives(&self, passenger: &Passenger)` で、引数の受け取り方が違います。

- `Movie` は `f64` が 3 つと `i32` が 1 つの小さな型で、`Copy` を derive しているので、値で渡しても複製されるだけです
- `Passenger` は `String` を 7 つ持つので、値で渡すと所有権が移り、呼び出し側で使えなくなります。**読むだけなので借ります**

この判断は第 1 章から繰り返してきたものです。API の層まで一貫しています。

## 15.3 TODO リストの作成

```markdown
## ドメインとサービス
- [ ] 映画・乗客の特徴量を型で表す
- [ ] モデルと置き場の約束を trait で書く
- [ ] モデルが無いことを Error::ModelNotFound で表す
- [ ] サービスが置き場からモデルを読んで予測する
- [ ] ヘルスチェックがモデルごとの状態を返す

## 置き場
- [ ] 第 7 章の線形回帰を JSON で保存・読み込みする
- [ ] 第 8 章のパイプラインを保存・読み込みする
- [ ] ファイルが無ければ ModelNotFound を返す

## API
- [ ] POST /cinema/sales が興行収入を返す
- [ ] POST /survived が生存を返す
- [ ] 年齢と乗船港を省略できる
- [ ] 入力が不正なら 422 と理由の一覧を返す
- [ ] JSON として読めなければ 422 を返し、内部の型名を漏らさない
- [ ] モデルが無ければ 503 を返す
- [ ] GET /health がモデルごとの状態を返す
- [ ] 知らないパスは 404、許していないメソッドは 405

## 実データ
- [ ] 第 7・8 章と同じ条件で学習して保存する
- [ ] 学習したモデルで API が動く（統合テスト）
- [ ] cargo run --bin chapters -- chapter15 でサーバーが起動する
```

## 15.4 ドメインとサービスを作る

### 失敗を enum で表す

第 1 章・第 2 章と同じ形です。この章では、下の層のエラーを包む点が増えます。

```rust
/// 第 15 章で起こりうる失敗。
#[derive(Debug)]
pub enum Error {
    /// モデルを読み込めない。どのモデルかを持つ。
    ModelNotFound(String),
    /// 保存・読み込みで失敗した。
    Io(std::io::Error),
    /// JSON の読み書きで失敗した。
    Json(serde_json::Error),
    /// 前の章のデータ処理が失敗した。
    Data(DataError),
}
```

`From` を実装しておくと、`?` が自動で変換してくれます。

```rust
impl From<DataError> for Error {
    fn from(error: DataError) -> Self {
        Error::Data(error)
    }
}
```

Go 版は番兵のエラー（`ErrModelNotFound`）と `errors.Is` で種類を判別しましたが、Rust では `enum` のバリアントそのものが種類です。`match` で分岐でき、**バリアントを足したら変換の `match` がコンパイルエラーになる**ので、書き忘れが残りません。

### 既存のモデルをアダプターで約束に合わせる

第 7・8 章のモデルには手を入れません（後述する 1 行の derive を除く）。

```rust
/// 第 7 章の線形回帰のモデルを `SalesModel` の約束に合わせる。
pub struct LinearSalesModel {
    pub model: LinearModel,
}

impl SalesModel for LinearSalesModel {
    fn predict_sales(&self, movie: Movie) -> Result<f64> {
        Ok(self.model.predict_one(&movie.features()?)?)
    }
}
```

`Ok(...?)` の `?` が 2 回出てきます。内側は `movie.features()` の失敗、外側は `predict_one` の失敗で、どちらも第 2 章の `Error` なので `From` で変換されます。

### サービスは HTTP を知らない

```rust
/// 予測サービス。置き場は約束（trait）で受け取るので、テストではスタブに差し替えられる。
pub struct PredictionService {
    store: Box<dyn ModelStore + Send + Sync>,
}
```

`+ Send + Sync` が Rust らしいところです。

- `Send` は「別のスレッドへ move できる」
- `Sync` は「複数のスレッドから同時に参照してよい」

axum のハンドラーは非同期に、複数のスレッドで同時に走ります。だから、共有する状態はこの 2 つを満たさなければなりません。**満たさない型を入れようとすると、コンパイルが通りません。** Java 版・Go 版では「スレッドセーフにしておく」という約束をコメントや設計で守りましたが、Rust では型が守ります。

ヘルスチェックは、モデルごとに読み込めるかどうかを返します。

```rust
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
```

`Result` の `is_ok()` で「読み込めたか」だけを見ます。Go 版が `err == nil` を書いた場所です。戻り値を `Vec` にして並びを固定しているのも Go 版と同じ理由ですが、Rust の `HashMap` も反復順が保証されないので事情は同じです（応答の JSON では `BTreeMap` を使って並べます）。

## 15.5 モデルを保存して読み込む

### serde の derive を 1 行足す

第 7 章の `LinearModel` を JSON にするには、`Serialize` と `Deserialize` が要ります。

```rust
/// 第 15 章で JSON として保存するので、serde の変換も derive する。
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct LinearModel {
    pub intercept: f64,
    pub columns: Vec<String>,
    pub coefficients: Vec<f64>,
}
```

derive を 1 行足すだけで、構造体と JSON を相互に変換できます。Java 版は Jackson がリフレクションで読み取り、C# 版は `System.Text.Json` がソース生成器を使いましたが、**Rust では手続きマクロがコンパイル時にコードを生成します**。実行時のリフレクションが無いぶん速く、失敗はコンパイル時に分かります。

第 8 章で見たとおり、**serde は trait object（`Box<dyn Trait>`）を保存できません**。だから第 8 章の学習済み前処理は `enum` で表しました。この章のモデルはそのまま使えます。

### ファイルが無いことをエラーの種類に変える

```rust
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
```

ガード付きの `match` で、「ファイルが無い」と「読めるが別の理由で失敗した」を分けます。前者はモデルをまだ学習していないだけなので 503（一時的に使えない）、後者は 500 にします。この区別を怠ると、ディスクの権限の問題が「モデルが無い」と表示され、原因を探せなくなります。

`std::io::ErrorKind` で種類を見るのは、Go 版の `os.IsNotExist` に当たります。

## 15.6 axum でエンドポイントを作る

### ルーターと状態

```rust
/// サービスをハンドラーで共有するための状態。
pub type SharedService = Arc<PredictionService>;

/// サービスを使う API を作る。listen はしない。
pub fn router(service: SharedService) -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/cinema/sales", post(predict_sales))
        .route("/survived", post(predict_survival))
        .with_state(service)
}
```

`Arc` は「複数の持ち主で共有する参照カウント付きのポインタ」です。ハンドラーは同時に走り、それぞれがサービスへの参照を持つので、**誰が最後に手放すかをコンパイル時に決められません**。`Arc` は実行時に数えて、0 になったら解放します。`Rc` ではなく `Arc` なのは、スレッドをまたぐからです（`Rc` は `Send` ではないので、ここに入れようとするとコンパイルエラーになります）。

`with_state` で渡した値は、ハンドラーの引数 `State(service): State<SharedService>` で受け取れます。axum の「抽出器（extractor）」という仕組みで、引数の型が「要求から何を取り出すか」を表します。

`router` は `Router` を返すだけで、待ち受けは始めません。**組み立てと起動を分ける**と、テストからは組み立てだけを使えます。

### tower の `oneshot` でサーバーを起動せずにテストする

```rust
/// API を呼んで、状態コードと本文を返す。tower の `oneshot` でサーバーを起動せずに呼ぶ。
async fn call(method: &str, path: &str, body: &str, sales: bool, survival: bool)
    -> (StatusCode, String)
{
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
    ...
}
```

axum のルーターは [tower](https://docs.rs/tower/) の `Service` なので、`oneshot` で 1 回だけ呼べます。ポートを開かずに、ルーティング・抽出・ハンドラー・応答の組み立てまで全部通ります。Go 版の `httptest.NewRequest`、Java 版の `JavalinTest` に当たります。

テストの関数には `#[tokio::test]` を付けます。非同期のテストを走らせるために実行時が要るからです。

```rust
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
```

`r#"..."#` は生文字列で、中の `"` をエスケープしなくて済みます。JSON を書くときに重宝します。

置き場はスタブに差し替えます。

```rust
impl ModelStore for StubStore {
    fn load_sales_model(&self) -> Result<Box<dyn SalesModel>> {
        if self.sales {
            Ok(Box::new(FixedSales))
        } else {
            Err(Error::ModelNotFound(SALES_MODEL.to_string()))
        }
    }
    ...
}
```

`impl ModelStore for StubStore` と明示的に書く必要があるのが Go 版との違いですが、`tests/` の中だけで完結するのは同じです。

### 要求の型は `Option` で受ける

Go 版はポインタで「JSON に値が無かった」を表しましたが、**Rust には `Option` があります**。

```rust
/// 興行収入の予測の要求。
/// Rust の `Option` は「JSON に値が無かった」をそのまま表せる（Go 版のポインタに当たる）。
#[derive(Debug, Deserialize)]
pub struct MovieRequest {
    pub sns1: Option<f64>,
    pub sns2: Option<f64>,
    pub actor: Option<f64>,
    pub original: Option<i32>,
}
```

serde は、フィールドが `Option<T>` なら「JSON に無ければ `None`」と解釈します。ポインタを使う Go 版、ボックス化した型を使う Java 版と比べて、**いちばん直接的に意図を書けます**。

### 検証の結果を enum で表す

```rust
/// 検証の結果。理由の一覧が空なら正しい。
#[derive(Debug, Clone, PartialEq)]
pub enum Validated<T> {
    Valid(T),
    Invalid(Vec<String>),
}
```

Go 版は sealed interface が無いので構造体 1 つで表しましたが、Rust では `enum` で素直に書けます。Java 版の sealed interface、F# 版・Scala 版の判別共用体と同じ構造です。**`match` で場合分けすると、`Invalid` の枝から値を取り出せないことがコンパイル時に保証されます。**

```rust
match request.validate() {
    Validated::Invalid(errors) => rejected(errors),
    Validated::Valid(movie) => match service.predict_sales(movie) {
        Ok(sales) => (StatusCode::OK, Json(SalesResponse { sales })).into_response(),
        Err(error) => failure(&error),
    },
}
```

検証の規則は、第 1 章から続く形です。

```rust
/// 値が選択肢に無ければ理由を返す。値が無ければ何も言わない。
fn one_of<T: PartialEq + std::fmt::Display>(
    field: &str,
    value: Option<&T>,
    allowed: &[T],
) -> Option<String> {
    match value {
        Some(value) if !allowed.contains(value) => {
            let choices: Vec<String> = allowed.iter().map(ToString::to_string).collect();

            Some(format!("{field} は {} のどれかにしてください", choices.join("、")))
        }
        _ => None,
    }
}
```

型引数の制約 `T: PartialEq + std::fmt::Display` が「比べられて、表示できる型」を表します。Go 版のジェネリクスでは `comparable` という組み込みの制約しかありませんでしたが、Rust では**自分で定義した trait も制約にできます**。

`Option<String>` を返すのは「理由が無ければ `None`」という意味です。集めるときは `flatten()` で `None` を落とします。

```rust
let errors: Vec<String> = reasons.into_iter().flatten().collect();
```

Go 版が「空文字列なら問題なし」という約束で書いた部分が、Rust では型に現れます。

### JSON の失敗を 422 に変える

axum の `Json` 抽出器は、JSON として読めないと**既定で 400 を返します**。この API では 422（内容は読めたが処理できない）に統一したいので、自分で拾います。

```rust
/// 本文を JSON として読む。読めなければ `None`（axum の既定の 400 を使わず、自分で 422 を返す）。
async fn read_json<T>(request: Request) -> Option<T>
where
    T: serde::de::DeserializeOwned + Send + 'static,
{
    request.extract::<Json<T>, _>().await.ok().map(|Json(value)| value)
}
```

ハンドラーの引数を `Json<MovieRequest>` にすると axum が自動で 400 を返してしまうので、引数は生の `Request` で受け取り、**自分で `extract` します**。`.ok()` で `Result` を `Option` に落とし、失敗の中身は捨てます。serde のエラーには `invalid type: string "たくさん", expected f64` のように内部の型名が入るので、応答には出しません。

```rust
/// JSON として読めなかったときの応答。
fn invalid_json() -> Response {
    rejected(vec![INVALID_JSON.to_string()])
}
```

テストで確かめます。

```rust
assert!(!response.contains("f64"), "本文 = {response}");
assert!(!response.contains("MovieRequest"), "本文 = {response}");
```

### エラーをステータスコードに変える

```rust
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
```

`(StatusCode, Json<T>)` というタプルが、そのまま応答になります。axum の `IntoResponse` trait が、タプル・`Json`・文字列など多くの型に実装されているからです。`into_response()` で型をそろえて返します。

Java 版は例外ハンドラーを 1 か所に登録しましたが、Rust では**変換が起きる場所がコードに見えます**。Go 版と同じ考え方です。

## 15.7 実データで学習したモデルで動かす

### 学習と保存

第 7・8 章とまったく同じ条件（`test_size` 0.2、シード 0、深さ 5、`balanced` の重み）で学習します。

```rust
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
```

`?` が 6 回出てきます。Go 版の同じ関数は `if err != nil` が 6 回で 18 行でした。

### 統合テスト

実データで学習したモデルをつなぐテストは、データが無ければスキップします（第 1 章と同じ、早期に戻る形です）。

```rust
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
    ...
}
```

期待値は、書く前に**実際に動かして測りました**。最初は当てずっぽうの値を書いてテストが落ち、実測に直しています。

```text
thread '保存したモデルを読み込んで予測できる' panicked at tests/prediction_models.rs:53:5:
興行収入 = 7615.295978481879
```

数値のリテラルに `_` が入っているのは clippy の指示です。`7615.295_978_481_879` と書くと「digits grouped inconsistently by underscores」と言われ、整数部にも区切りを入れて `7_615.295_978_481_879` にせよと直し方まで教えてくれます。

### 同期の関数から非同期の世界に入る

各章の `run` は `fn run(out: &mut impl Write) -> Result<()>` という同期の関数です。axum は非同期なので、ここで実行時を作ります。

```rust
/// tokio の実行時を作って待ち受ける。`run` は同期の関数なので、ここで非同期の世界に入る。
fn serve(service: api::SharedService) -> Result<()> {
    let runtime = tokio::runtime::Runtime::new()?;

    runtime.block_on(async move {
        let listener = tokio::net::TcpListener::bind(("127.0.0.1", PORT)).await?;

        axum::serve(listener, api::router(service)).await
    })?;

    Ok(())
}
```

`#[tokio::main]` を `main` に付ける書き方が一般的ですが、それだと**この章だけのために `main` 全体が非同期になります**。ほかの章は同期のままでよいので、必要な場所で `Runtime::new()` と `block_on` を使いました。`async move` のブロックは、`service` の所有権をクロージャの中へ移します。

実行するとモデルを学習・保存してから待ち受けます。

```text
$ cargo run --bin chapters -- chapter15
モデル cinema: true
モデル survived: true
http://localhost:8015 で待ち受けます
```

別の端末から呼びます。

```text
$ curl -s localhost:8015/health
{"status":"ok","models":{"cinema":true,"survived":true}}

$ curl -s -X POST localhost:8015/cinema/sales \
    -H 'content-type: application/json' \
    -d '{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}'
{"sales":7615.295978481879}

$ curl -s -X POST localhost:8015/survived \
    -H 'content-type: application/json' \
    -d '{"pclass": 1, "sex": "female", "sib_sp": 0, "parch": 0, "fare": 80}'
{"survived":true}

$ curl -s -X POST localhost:8015/cinema/sales \
    -H 'content-type: application/json' \
    -d '{"sns1": -1, "sns2": 2000, "actor": 300, "original": 2}'
{"detail":["sns1 は 0 以上にしてください","original は 0、1 のどれかにしてください"]}

$ curl -s -o /dev/null -w "%{http_code}\n" localhost:8015/unknown
404
$ curl -s -o /dev/null -w "%{http_code}\n" localhost:8015/cinema/sales
405
```

404 と 405 は axum の `Router` が返します。Go 版の `ServeMux`（Go 1.22 以降）と同じです。

年齢と乗船港を省略しても動きます。空文字列（欠損値）のまま第 8 章のパイプラインに渡り、訓練データから求めた中央値・最頻値で補完されるからです。**前処理をモデルと一緒に保存しておくと、予測のときも学習と同じ手順が適用されます。**

### テストの実行結果

```text
$ cargo llvm-cov --summary-only
chapter15/api.rs            99    6  93.94%   12   0  100.00%    75   9  88.00%
chapter15/domain.rs         70   27  61.43%    8   3   62.50%    41  13  68.29%
chapter15/main.rs           84   52  38.10%    4   3   25.00%    31  19  38.71%
chapter15/service.rs        22    1  95.45%    4   0  100.00%    20   0 100.00%
chapter15/store.rs          73    9  87.67%    8   0  100.00%    41   1  97.56%
chapter15/validation.rs    264    0 100.00%   14   0  100.00%   158   0 100.00%
TOTAL                     8155  623  92.36%  680  46   93.24%  4253 201  95.27%
```

`validation.rs` が 100% なのは、検証がすべて純粋な関数で、スタブも実データも要らないからです。`main.rs` が低いのは、サーバーの起動部分をテストしていないためです（ここは `curl` で確かめました）。

学習データを外すと全体が 92.36% から 82.75% に下がりますが、**`api.rs` は 93.94% のまま変わりません**。スタブの置き場を差し込んだおかげで、API の振る舞いは学習データ無しで全部確かめられます。層を分けた効果が数字に出ています。

## 15.8 まとめ

この章では、第 7・8 章のモデルを axum の HTTP API にしました。

1. **trait で層を分ける** — 置き場もモデルも trait で約束し、`Box<dyn Trait>` で実装を隠した。Go の構造的なインターフェースと違い `impl Trait for Type` を明示的に書くが、意図がコードに残る
2. **`Send + Sync` を型で要求する** — 非同期のハンドラーは同時に走るので、共有する状態は `Arc<PredictionService>` にし、置き場に `+ Send + Sync` を課した。スレッドセーフを設計の約束ではなくコンパイラに守らせる
3. **`Option` と `enum` が素直に効く** — 要求の欠落は `Option`、検証の結果は `enum Validated<T>` でそのまま書ける。Go 版がポインタと構造体で代用した部分
4. **フレームワークの既定を上書きする** — axum の `Json` 抽出器は既定で 400 を返すので、生の `Request` から自分で `extract` して 422 に統一した。serde のエラーには内部の型名が入るので応答に出さない
5. **serde の derive 1 行でモデルを保存できる** — リフレクションではなく手続きマクロがコンパイル時に生成する。ただし trait object は保存できないので、第 8 章の前処理は `enum` で表した
6. **テストの粒度** — スタブで学習データ無しに全エンドポイントを確かめ（`api.rs` は 93.94%）、実データの統合テストは別に分けてスキップできるようにした

### シリーズの振り返り

第 1 章の「20 代ならきのこ派」という手書きのルールから、Rust 版でも次の順に進んできました。

| 部 | 章 | 学んだこと | Rust 版で効いた言語の性質 |
|----|----|-----------|--------------------------|
| 第 1 部 | 第 1〜3 章 | データを読み込み、前処理し、決定木をデータから学ばせる基本サイクル | `Result` と `?`、`Option`、`enum` と `Box` による再帰、`match` の網羅性 |
| 第 2 部 | 第 4〜6 章 | その過程を支えるバージョン管理・静的解析・CI | Cargo と `Cargo.lock`、rustfmt・clippy、cargo-llvm-cov |
| 第 3 部 | 第 7〜9 章 | 回帰と、現実のデータの前処理 | ndarray、linfa との突き合わせ、所有権と借用 |
| 第 4 部 | 第 10〜12 章 | 複数のモデルの比較と評価 | trait による共通化、イテレータ |
| 第 5 部 | 第 13〜15 章 | 正解の無いデータの扱いと、モデルを API として届けるまで | serde、`Arc` と `Send + Sync`、axum |

Rust 版を書く前の見込みは外れました。「Rust は機械学習のライブラリが限られる」と考えて [Go 版](../go/index.md)・[TypeScript 版](../typescript/index.md) と同じ立場に置く予定でしたが、**linfa は決定木からリッジ・ラッソ・交差検証まで揃っていました**（[ADR 009](../../../adr/009-rust-ml-libraries.md)）。結果として、[Java 版](../java/index.md)（Tribuo）・[C# 版](../csharp/index.md)（ML.NET）と同じ「自作してからライブラリと突き合わせる」流れをほぼ全章で書けました。

そして、突き合わせたからこそ分かったことがあります。**linfa-trees は Survived.csv のようなデータで非決定的**で、同じ入力でも実行ごとに正解率が揺れます（第 8 章）。自作の決定木のほうが再現性が高いという逆転が起きました。ライブラリは「自分で書くより正しい」とは限りません。**突き合わせる相手がいるからこそ、違いに気づけます。**

Rust に固有の負担もありました。クレートの版が型を分けること（ndarray 0.16 と 0.17、rand 0.8 と 0.9）、`clone()` をどこで呼ぶかを毎回決めること、`Box<dyn Trait>` と `Arc` を明示的に書くこと。どれも、ほかの言語なら考えずに済む判断です。そのかわり、**書けたものは並行に動かしても壊れません**。

### シリーズの終わりに

Rust 版で、第 2 波の 5 言語（Java・C#・Scala・Go・Rust）が終わりました。第 1 波の 4 言語（Python・Kotlin・TypeScript・F#）と合わせて 9 言語です（[執筆計画](../outline.md)）。

同じ題材を 9 つの言語で書いて、いちばんはっきり見えたのは**乱数**でした。Python・Java・.NET・Go・Rust の擬似乱数はどれも実装が違い、同じシード・同じ手順で並べ替えても、訓練データとテストデータに入る行が違います。件数だけは一致しますが、正解率も係数も揃いません。アルゴリズムを揃えても、**乱数生成器まで揃えなければ数値は再現しない**。多言語で同じことを書いてみて、これがいちばんの収穫でした。

そして乱数を使わない章——第 13 章の主成分分析——では、言語をまたいで値がぴたりと一致します。数学は同じ、実装の都合だけが違う。その境目を見られたことが、このシリーズを 9 言語で書いた意味だと思います。
