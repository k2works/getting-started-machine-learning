---
type: Article
title: "第 15 章: 機械学習 API とモジュール設計"
description: "第 7・8 章の学習済みモデルを標準の net/http で予測 API として公開し、層の分離・インターフェース・エラーの変換・入力の検証を Go の書き方で組み立てる。"
tags: [article,getting-start-ml,go]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T12:20:00Z }
---

# 第 15 章: 機械学習 API とモジュール設計

## 15.1 はじめに

シリーズの最後の章です。ここまでに作ったモデルは、テストと `go run ./cmd/chapters chapterNN` の中でしか動きませんでした。この章では、第 7 章の線形回帰（映画の興行収入）と第 8 章のパイプライン（乗客の生存）を **HTTP の予測 API** として公開します。

題材は [Python 版の第 15 章](../python/15-machine-learning-api-and-module-design.md) と同じで、3 つのエンドポイントを作ります。

| メソッド | パス | 役割 |
|---------|------|------|
| `GET` | `/health` | モデルを読み込めるかどうかを返す |
| `POST` | `/cinema/sales` | 映画の特徴量から興行収入を予測する |
| `POST` | `/survived` | 乗客の特徴量から生存を予測する |

Go 版で対比するのは [Java 版](../java/15-machine-learning-api-and-module-design.md)（Javalin・検査例外・sealed interface）と [TypeScript 版](../typescript/15-machine-learning-api-and-module-design.md)（ライブラリが限られる環境）です。Go 版の特徴ははっきりしています。**Web フレームワークを入れません。** 標準ライブラリの `net/http` だけで作ります。Go 1.22 でルーティングにメソッドとパスのパターンが入ったので、この章の要件はフレームワーク無しで足ります（[ADR 008](../../../adr/008-go-ml-libraries.md)）。

依存が標準ライブラリだけで済むかわりに、ほかの言語版がフレームワークに任せていたことを自分で書きます。JSON の読み書き、入力の検証、エラーの HTTP ステータスコードへの変換です。書く量は増えますが、どこで何が起きているかは全部見えます。

## 15.2 層を分ける

### 4 つの層と依存の向き

パッケージ `internal/chapter15` の中を、ファイルで 4 つの層に分けます。Go にはパッケージより細かい可視性の単位が無いので、層の境界は「どのファイルが何を import しているか」で表します。

```text
api.go          プレゼンテーション層   net/http・JSON・ステータスコード
validation.go   プレゼンテーション層   要求の型と検証
service.go      アプリケーション層     置き場からモデルを読んで予測する
domain.go       ドメイン層             Movie・Passenger・モデルの約束
store.go        インフラ層             ファイルへの保存と読み込み
main.go         組み立て               学習・保存・サーバーの起動
```

依存の向きは内側（ドメイン）へ向けます。

```text
api.go ──→ service.go ──→ domain.go ←── store.go
                              ↑
                        （約束だけを知る）
```

`service.go` が知っているのは `ModelStore` という**約束**（インターフェース）だけで、その実装が JSON のファイルなのか、データベースなのか、テストのスタブなのかを知りません。`domain.go` は `net/http` を import しません。これが守れていれば、API の形を変えてもドメインは動き、ドメインのテストはサーバーを起動せずに書けます。

Java 版は同じ分け方をクラスとパッケージで表し、置き場の組み立てを `Main` に置きました。Go でも組み立ては `main.go` に置きます。違うのは、**Go ではインターフェースを実装する側が「実装します」と宣言しない**ことです。`FileModelStore` は `ModelStore` を実装していると書きません。メソッドの形が合っていれば実装したことになります。

### インターフェースは使う側に置く

Go の流儀では、インターフェースは実装する側ではなく**使う側のパッケージ**に置きます。この章では `ModelStore`・`SalesModel`・`SurvivalModel` の 3 つの約束を、使う側である `domain.go` に書きます。

```go
// SalesModel は興行収入を予測する約束。
type SalesModel interface {
	PredictSales(movie Movie) (float64, error)
}

// SurvivalModel は生存を予測する約束。
type SurvivalModel interface {
	Survives(passenger Passenger) (bool, error)
}

// ModelStore は学習済みモデルの置き場の約束。
// 読み込めなければ ErrModelNotFound を包んだエラーを返す。
type ModelStore interface {
	LoadSalesModel() (SalesModel, error)
	LoadSurvivalModel() (SurvivalModel, error)
}
```

メソッドは 1〜2 個です。Go では小さいインターフェースが好まれます。標準ライブラリの `io.Writer` がメソッド 1 つであるように、約束が小さいほど差し替えやすくなります。この章のテストでも、`ModelStore` が 2 メソッドしかないおかげで、スタブが 10 行で書けます。

### インサイドアウトで進める

作る順番は Java 版と同じく内側からです。ドメイン（値とモデルの約束）→ サービス → 置き場 → API の順に、それぞれテストを先に書きます。API から作ると、まだ無いサービスをモックで埋めることになり、モックの形が設計を決めてしまいます。

## 15.3 TODO リストの作成

```markdown
## ドメインとサービス
- [ ] 映画・乗客の特徴量を型で表す
- [ ] モデルと置き場の約束をインターフェースで書く
- [ ] モデルが無いことを ErrModelNotFound で表す
- [ ] サービスが置き場からモデルを読んで予測する
- [ ] ヘルスチェックがモデルごとの状態を返す

## 置き場
- [ ] 第 7 章の線形回帰を JSON で保存・読み込みする
- [ ] 第 8 章のパイプラインを gob で保存・読み込みする（第 8 章の SavePipeline）
- [ ] ファイルが無ければ ErrModelNotFound を返す

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
- [ ] go run ./cmd/chapters chapter15 でサーバーが起動する
```

## 15.4 ドメインとサービスを作る

### 値を型にする

要求の JSON をそのままモデルに渡さず、いったんドメインの型にします。

```go
// Movie は映画の特徴量。SNS の評判 2 種類・主演の人気・原作の有無（0 か 1）。
type Movie struct {
	SNS1     float64
	SNS2     float64
	Actor    float64
	Original int
}

// Passenger は乗客の特徴量。空文字列は欠損値として前処理に任せる。
type Passenger struct {
	Pclass   string
	Sex      string
	Age      string
	SibSp    string
	Parch    string
	Fare     string
	Embarked string
}
```

`Movie` の型は数値ですが、`Passenger` はすべて文字列です。第 8 章のパイプラインが受け取るのは `chapter02.Row`（セルの文字列）で、**欠損値を空文字列で表す**からです。年齢が分からない乗客の予測を受け付けるには、「値が無い」を型の中に残したまま前処理まで運ぶ必要があります。Java 版は `OptionalDouble` で表しましたが、Go には `Optional` がありません。ポインタ（`*float64`）を使う手もありますが、行き先がセルの文字列である以上、ここで文字列にしてしまうのがいちばん素直です。

ドメインの型は、自分を第 7・8 章の入力に変換する方法を知っています。

```go
// Features は第 7 章のモデルに渡す特徴量にする。
func (m Movie) Features() (chapter02.Features, error) {
	return chapter02.NewFeatures(
		chapter07.CinemaFeatures,
		[]float64{m.SNS1, m.SNS2, m.Actor, float64(m.Original)},
	)
}

// Row は第 8 章のパイプラインに渡す行にする。
func (p Passenger) Row() (chapter02.Row, error) {
	return chapter08.Passenger(p.Pclass, p.Sex, p.Age, p.SibSp, p.Parch, p.Fare, p.Embarked)
}
```

### 番兵のエラーで「モデルが無い」を表す

Java 版は検査例外 `ModelNotFoundException` を使い、失敗の可能性が `throws` として宣言に現れるようにしました。Go に例外はありません。エラーは戻り値で、**種類の判別には番兵のエラー（sentinel error）**を使います。

```go
// ErrModelNotFound はモデルを読み込めないことを表す番兵のエラー。
// 呼び出し側は errors.Is で判別し、API では 503 に変える。
var ErrModelNotFound = errors.New("モデルがありません")

// ModelNotFound は、どのモデルが無いのかを添えたエラーを作る。
func ModelNotFound(name string) error {
	return fmt.Errorf("%w: %s", ErrModelNotFound, name)
}
```

`%w` が肝心です。`%v` で書くとただの文字列になりますが、`%w` で**包む**と、包まれたエラーを `errors.Is` でたどれます。

```go
err := ModelNotFound("cinema")
errors.Is(err, ErrModelNotFound) // true
err.Error()                      // "モデルがありません: cinema"
```

Java 版と比べると、得たものと失ったものがはっきりします。得たのは、関数型との相性です。Java 版はヘルスチェックで「検査例外を投げる関数」を渡せず、自前の関数型インターフェース `Loader` を用意する羽目になりましたが、Go では `error` がただの戻り値なので、そのまま関数に渡せます。失ったのは、**コンパイラの強制**です。`throws` に相当するものが無いので、どのエラーが返りうるかは実装を読むか、ドキュメントのコメントを読むしかありません。だからインターフェースのコメントに「読み込めなければ `ErrModelNotFound` を包んだエラーを返す」と書いて、約束の一部にします。

### 既存のモデルをアダプターで約束に合わせる

第 7・8 章のモデルには手を入れません。約束に合わせるのは**アダプター**の役割です。

```go
// linearSalesModel は第 7 章の線形回帰のモデルを SalesModel の約束に合わせる。
type linearSalesModel struct {
	model chapter07.LinearModel
}

func (a linearSalesModel) PredictSales(movie Movie) (float64, error) {
	features, err := movie.Features()
	if err != nil {
		return 0, err
	}

	return a.model.Predict(features)
}
```

この型は小文字で始まる非公開の型です。パッケージの外に見せる必要はありません。外から見えるのは `SalesModel` という約束だけです。`pipelineSurvivalModel` も同じ形で、第 8 章の `FittedPipeline` を包み、1 行の表を作って予測し、`chapter08.Survived`（1）と比べて `bool` にします。

### サービスは HTTP を知らない

```go
// PredictionService は置き場からモデルを読み込んで予測する。HTTP には依存しない。
type PredictionService struct {
	store ModelStore
}

// NewPredictionService は置き場を差し込んだサービスを作る。
func NewPredictionService(store ModelStore) *PredictionService {
	return &PredictionService{store: store}
}

// PredictSales は映画の興行収入を予測する。
func (s *PredictionService) PredictSales(movie Movie) (float64, error) {
	model, err := s.store.LoadSalesModel()
	if err != nil {
		return 0, err
	}

	return model.PredictSales(movie)
}
```

ヘルスチェックは、モデルごとに読み込めるかどうかを返します。

```go
// ModelHealth はモデルの名前と、読み込めるかどうか。map ではなくスライスにして並びを固定する。
type ModelHealth struct {
	Name  string
	Ready bool
}

// Health はモデルごとに読み込めるかどうかを返す。
func (s *PredictionService) Health() []ModelHealth {
	_, salesErr := s.store.LoadSalesModel()
	_, survivalErr := s.store.LoadSurvivalModel()

	return []ModelHealth{
		{Name: SalesModelName, Ready: salesErr == nil},
		{Name: SurvivalModelName, Ready: survivalErr == nil},
	}
}
```

戻り値を `map[string]bool` ではなくスライスにしたのは、**Go の map は反復順がランダム**だからです（第 7 章で係数を並べるときにも同じ判断をしました）。Java 版は `LinkedHashMap` で並びを保てますが、Go の map にその選択肢はありません。表示や繰り返しで並びが要るなら、スライスで持つのが Go の作法です。JSON にするときだけ map に直します（JSON のオブジェクトのキーは `encoding/json` が並べ替えてくれます）。

## 15.5 モデルを保存して読み込む

### 2 つのモデル、2 つの保存形式

```go
// salesModelFile は興行収入のモデルのファイル。第 7 章のモデルは数値だけなので JSON にする。
func (s *FileModelStore) salesModelFile() string {
	return filepath.Join(s.ModelDir, SalesModelName+".json")
}

// survivalModelFile は生存予測のモデルのファイル。前処理を含むので第 8 章と同じ gob にする。
func (s *FileModelStore) survivalModelFile() string {
	return filepath.Join(s.ModelDir, SurvivalModelName+".gob")
}
```

線形回帰のモデル（`chapter07.LinearModel`）は切片・列名・係数だけなので、`encoding/json` でそのまま書けます。**公開フィールドだけが JSON になる**ので、モデルのフィールドが大文字で始まっていることが効いてきます。

```go
// SaveSalesModel は線形回帰のモデルを JSON で保存する。
func (s *FileModelStore) SaveSalesModel(model chapter07.LinearModel) error {
	if err := os.MkdirAll(s.ModelDir, 0o750); err != nil {
		return fmt.Errorf("保存先を作れません: %w", err)
	}

	encoded, err := json.Marshal(model)
	if err != nil {
		return fmt.Errorf("モデルを JSON にできません: %w", err)
	}

	if err := os.WriteFile(s.salesModelFile(), encoded, 0o600); err != nil {
		return fmt.Errorf("モデルを保存できません: %w", err)
	}

	return nil
}
```

生存予測のパイプラインは、前処理（欠損値の補完で求めた中央値・最頻値、ダミー変数の列）と決定木を含みます。JSON にするとインターフェースの具体型が失われるので、第 8 章で作った `SavePipeline`（`encoding/gob` と `gob.Register`）をそのまま使います。**保存の形式はモデルの中身で選ぶ**、ということです。

### ファイルが無いことをエラーの種類に変える

```go
// LoadSalesModel は興行収入のモデルを読み込む。ファイルが無ければ ErrModelNotFound を返す。
func (s *FileModelStore) LoadSalesModel() (SalesModel, error) {
	contents, err := os.ReadFile(s.salesModelFile())
	if err != nil {
		if os.IsNotExist(err) {
			return nil, ModelNotFound(SalesModelName)
		}

		return nil, fmt.Errorf("モデルを読み込めません: %w", err)
	}

	var model chapter07.LinearModel
	if err := json.Unmarshal(contents, &model); err != nil {
		return nil, fmt.Errorf("モデルの JSON が壊れています: %w", err)
	}

	return linearSalesModel{model: model}, nil
}
```

「ファイルが無い」と「読めるが壊れている」を区別します。前者はモデルをまだ学習していないだけなので 503（一時的に使えない）、後者は直さないと動かないので 500 にします。`os.IsNotExist` で判別し、それ以外は `%w` で包んで上へ返します。

この分岐を書かずに全部 `ErrModelNotFound` にしてしまうと、ディスクの権限の問題が「モデルが無い」と表示され、原因を探せなくなります。エラーは、**呼び出し側が判断に使える粒度**で分けます。

## 15.6 net/http でエンドポイントを作る

### httptest でサーバーを起動せずにテストする

API のテストには `net/http/httptest` を使います。ポートを開かずにハンドラーを直接呼べます。

```go
// call は API を呼んで、応答の状態コードと本文を返す。
func call(t *testing.T, handler http.Handler, method, path, body string) (int, string) {
	t.Helper()

	request := httptest.NewRequest(method, path, strings.NewReader(body))
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, request)

	return recorder.Code, recorder.Body.String()
}
```

置き場はスタブに差し替えます。学習データが無くても API の振る舞いを全部テストできます。

```go
// stubStore は置き場のスタブ。学習しなくても API の振る舞いを確かめられる。
type stubStore struct {
	sales    chapter15.SalesModel
	survival chapter15.SurvivalModel
}

func (s stubStore) LoadSalesModel() (chapter15.SalesModel, error) {
	if s.sales == nil {
		return nil, chapter15.ModelNotFound(chapter15.SalesModelName)
	}

	return s.sales, nil
}
```

`stubStore` はどこにも「`ModelStore` を実装します」と書いていません。メソッドの形が合っているので実装になります。テスト用のスタブを、本体のパッケージに何も足さずに `_test.go` の中だけで作れるのは、Go の構造的なインターフェースの利点です。

### Go 1.22 のルーティング

```go
// NewHandler はサービスを使う API を作る。listen はしない。
func NewHandler(service *PredictionService) http.Handler {
	mux := http.NewServeMux()
	// Go 1.22 以降のルーティングは、メソッドとパスをまとめて書ける。
	// 登録していないメソッドには 405 が、登録していないパスには 404 が自動で返る。
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		health(w, service)
	})
	mux.HandleFunc("POST /cinema/sales", func(w http.ResponseWriter, r *http.Request) {
		predictSales(w, r, service)
	})
	mux.HandleFunc("POST /survived", func(w http.ResponseWriter, r *http.Request) {
		predictSurvival(w, r, service)
	})

	return mux
}
```

`NewHandler` は `http.Handler` を返すだけで、`ListenAndServe` は呼びません。**組み立てと起動を分ける**と、テストからは組み立てだけを使えます。Java 版が `Javalin.create(...)` を返して `start` しなかったのと同じ考え方です。

Go 1.22 より前の `ServeMux` はパスしか見なかったので、メソッドの判別はハンドラーの中で `if r.Method != http.MethodPost` と書くか、フレームワークに頼るしかありませんでした。今はパターンに書けて、405 は `ServeMux` が返します。確かめておきます。

```text
$ curl -s -o /dev/null -w "%{http_code}\n" localhost:8015/unknown
404
$ curl -s -o /dev/null -w "%{http_code}\n" localhost:8015/cinema/sales
405
```

（`curl` は本文が無ければ `GET` で送るので、`POST` だけを登録した `/cinema/sales` は 405 になります。）

### 要求の型はポインタで受ける

Go の数値の既定値は 0 で、文字列は空文字列です。そのまま JSON をデコードすると、**「値が 0 だった」と「JSON に値が無かった」を区別できません**。Java 版が `int` ではなく `Integer` で受けて `null` を残したのと同じ問題です。Go ではポインタで受けます。

```go
// MovieRequest は興行収入の予測の要求。
// Go の数値は 0 が既定値なので、JSON に値が無かったことを表すためにポインタで受ける。
type MovieRequest struct {
	SNS1     *float64 `json:"sns1"`
	SNS2     *float64 `json:"sns2"`
	Actor    *float64 `json:"actor"`
	Original *int     `json:"original"`
}
```

バッククォートの中はフィールドのタグで、JSON のキーと Go のフィールド名の対応を書きます。`SibSp` を `sib_sp` として受けるのもタグの仕事です（Java 版は `@JsonProperty` でした）。

### 検証の結果を型で表す

Java 版は sealed interface の `Validated` を作り、`switch` のパターンマッチで正しい場合と不正な場合を分けました。Go には sealed interface がありません。第 3 章では非公開メソッドを持つインターフェースで判別共用体を代用しましたが、ここでは**構造体 1 つ**で足ります。

```go
// Validated は検証の結果。Go には sealed interface が無いので、
// 理由の一覧が空かどうかで「正しい」「不正」を表す構造体にする。
type Validated[T any] struct {
	Value  T
	Errors []string
}

// Valid は理由が 1 つも無いかどうか。
func (v Validated[T]) Valid() bool {
	return len(v.Errors) == 0
}
```

判別共用体との違いは、**不正なのに `Value` を読めてしまう**ことです（読むとゼロ値が返ります）。Java 版の `switch` は、`Invalid` の枝で値を取り出そうとするとコンパイルが通りません。Go ではその保証が無いので、呼び出し側の規律で守ります。第 3 章の決定木のように「型スイッチの既定の分岐でエラーを返す」手も使えますが、検証結果のように分岐が 2 つで、しかも片方が「値がある」だけの場合は、構造体のほうが読みやすくなります。**言語に無い道具の代用は、毎回同じ答えにしなくてよい**、ということです。

検証の規則はジェネリクスで書きます。

```go
// notNegative は値が負であれば理由を返す。値が無ければ何も言わない（required の担当）。
func notNegative[N float64 | int](field string, value *N) string {
	if value == nil || *value >= 0 {
		return ""
	}

	return field + " は 0 以上にしてください"
}

// oneOf は値が選択肢に無ければ理由を返す。値が無ければ何も言わない。
func oneOf[E comparable](field string, value *E, allowed []E) string {
	if value == nil || slices.Contains(allowed, *value) {
		return ""
	}
	...
}
```

`[N float64 | int]` は型の**合併**を型引数の制約に書いたものです。`comparable` は `==` で比べられる型という組み込みの制約で、`slices.Contains` がそれを要求します。Java 版の `Checks` は `Object` と `Number` で受けていましたが、Go のジェネリクスなら「数値だけ」「比較できる型だけ」と絞れます。

検証は、規則を並べて空文字列でないものを集めるだけです。

```go
// Validate は検証して、正しければ映画の特徴量にする。
func (r MovieRequest) Validate() (Validated[Movie], error) {
	return validate(
		[]string{
			required("sns1", r.SNS1 != nil),
			required("sns2", r.SNS2 != nil),
			required("actor", r.Actor != nil),
			required("original", r.Original != nil),
			notNegative("sns1", r.SNS1),
			notNegative("sns2", r.SNS2),
			notNegative("actor", r.Actor),
			oneOf("original", r.Original, originalValues),
		},
		func() (Movie, error) {
			return Movie{SNS1: *r.SNS1, SNS2: *r.SNS2, Actor: *r.Actor, Original: *r.Original}, nil
		},
	)
}
```

必須の検査を先に全部並べ、値を組み立てる関数は**理由が 1 つも無いときだけ**呼ばれます。だから `*r.SNS1` のポインタの参照外しが安全です。ここを間違えると `nil` の参照外しでパニックになるので、順番が設計です。

不正な入力では、理由がまとめて返ります。

```text
$ curl -s -X POST localhost:8015/cinema/sales -d '{"sns1": 100}'
{"detail":["sns2 は必須です","actor は必須です","original は必須です"]}

$ curl -s -X POST localhost:8015/cinema/sales \
    -d '{"sns1": -1, "sns2": 2000, "actor": 300, "original": 2}'
{"detail":["sns1 は 0 以上にしてください","original は 0、1 のどれかにしてください"]}
```

### JSON の失敗を 422 に変え、内部の型名を漏らさない

Java 版は Javalin の例外ハンドラーで Jackson の例外を 422 に変えました。Go では、デコードの戻り値をその場で見ます。

```go
// decode は本文を JSON として読む。読めなければ 422 を返して false を返す。
func decode(w http.ResponseWriter, r *http.Request, request any) bool {
	decoder := json.NewDecoder(r.Body)
	if err := decoder.Decode(request); err != nil {
		writeJSON(w, http.StatusUnprocessableEntity, validationErrorResponse{Detail: []string{invalidJSON}})

		return false
	}

	// 本文に 2 つ目の JSON が続いていないかを確かめる。
	if err := decoder.Decode(new(json.RawMessage)); !errors.Is(err, io.EOF) {
		writeJSON(w, http.StatusUnprocessableEntity, validationErrorResponse{Detail: []string{invalidJSON}})

		return false
	}

	return true
}
```

2 回目の `Decode` は、Go の JSON デコーダーの性質への対処です。`json.NewDecoder` は**ストリーム**を読むので、`{"sns1":1}{"sns1":2}` のように JSON が 2 つ続いていても 1 つ目だけ読んで成功します。「本文の残りが `io.EOF` であること」を確かめて、初めて「本文はちょうど 1 つの JSON だった」と言えます。

デコードのエラーのメッセージには `json: cannot unmarshal string into Go struct field MovieRequest.sns1 of type float64` のように**内部の型名が入ります**。そのまま返すと実装の詳細が外に漏れるので、決まった文言に置き換えます。テストでも確かめます。

```go
if strings.Contains(response, "chapter15") || strings.Contains(response, "float64") {
	t.Errorf("本文 = %s, want 内部の型名を含まない", response)
}
```

```text
$ curl -s -X POST localhost:8015/cinema/sales -d '{"sns1": "たくさん"}'
{"detail":["JSON の形式または値の型が正しくありません"]}
```

### エラーをステータスコードに変える

```go
// writeFailure はドメインのエラーを HTTP のステータスコードに変える。
func writeFailure(w http.ResponseWriter, err error) {
	if errors.Is(err, ErrModelNotFound) {
		writeJSON(w, http.StatusServiceUnavailable, errorResponse{Detail: err.Error()})

		return
	}

	writeJSON(w, http.StatusInternalServerError, errorResponse{Detail: "予測できませんでした"})
}
```

`errors.Is` で番兵のエラーを判別します。ここが、`fmt.Errorf` で `%w` を使った理由です。モデルが無いときは名前も返します（どちらのモデルが無いかは運用で知りたい情報です）。それ以外は 500 にして、中身は返しません。

```text
$ curl -s -X POST localhost:8015/cinema/sales -d '{...}'   # モデルを学習する前
{"detail":"モデルがありません: cinema"}
```

Java 版は例外ハンドラーを 1 か所に登録して、ハンドラーの中では例外を投げるだけでした。Go はハンドラーごとに `if err != nil` を書きます。行数は増えますが、**変換が起きる場所がコードに見えます**。フレームワークの例外ハンドラーは、登録し忘れると 500 になって初めて気づく（Java 版でまさにそれが起きました）のに対し、Go では書き忘れがコンパイルエラーか、すぐ見つかる書き漏れになります。

応答を書く部分も自分で書きます。

```go
// writeJSON は応答を JSON で書く。
func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)

	if err := json.NewEncoder(w).Encode(body); err != nil {
		// ここまで来ると状態コードは送信済みなので、記録するしかない。
		log.Printf("応答を書けません: %v", err)
	}
}
```

ヘッダー → 状態コード → 本文の順です。`WriteHeader` の後にヘッダーを足しても効きません。そして本文を書いている途中の失敗は、もう応答を変えられないので記録するしかありません。`errcheck`（第 5 章）が `Encode` の戻り値を見ろと言ってくるので、この判断はコードに残ります。

## 15.7 実データで学習したモデルで動かす

### 学習と保存

第 7・8 章とまったく同じ条件（`testSize` 0.2、シード 0、深さ 5、`balanced` の重み）で学習します。

```go
// TrainAndSaveModels は第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。
func TrainAndSaveModels(dataDir string, store *FileModelStore) error {
	cinema, err := chapter07.PrepareCinema(filepath.Join(dataDir, "cinema.csv"), testSize, seed)
	if err != nil {
		return err
	}

	salesModel, err := chapter07.Fit(cinema.XTrain, cinema.TTrain)
	if err != nil {
		return err
	}

	if err := store.SaveSalesModel(salesModel); err != nil {
		return err
	}
	...
}
```

### 統合テスト

スタブのテストとは別に、実データで学習したモデルをつなぐテストを書きます。データが無ければスキップします。

```go
// trainedStore は実データで学習して一時ディレクトリに保存した置き場を返す。
func trainedStore(t *testing.T) *chapter15.FileModelStore {
	t.Helper()

	store := chapter15.NewFileModelStore(t.TempDir())
	if err := chapter15.TrainAndSaveModels(requireData(t), store); err != nil {
		t.Fatalf("TrainAndSaveModels() でエラー: %v", err)
	}

	return store
}
```

`t.TempDir()` はテストごとの一時ディレクトリを作り、テストが終わると消します。保存先をテストから差し替えられるように、`FileModelStore` がディレクトリを持つ設計にしておいたのが効きます。

ここでは `httptest.NewServer` を使って、**実際にポートを開いて** HTTP で呼びます。`httptest.NewRequest` がハンドラーを直接呼ぶのに対し、こちらはネットワーク越しなので、`http.Post` のクライアント側まで含めて確かめられます。

```go
handler := chapter15.NewHandler(chapter15.NewPredictionService(trainedStore(t)))
server := httptest.NewServer(handler)

t.Cleanup(server.Close)

response, err := http.Post(
	server.URL+"/cinema/sales",
	"application/json",
	strings.NewReader(`{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}`),
)
```

期待値は、書く前に**実際に動かして測りました**。最初は Java 版の値を書いてテストが落ち、Go の分け方の違い（第 2 章）がここまで効いていることを確認できました。

```text
--- FAIL: TestTrainedModels/保存したモデルを読み込んで予測できる (0.14s)
    trainedmodels_test.go:56: 興行収入 = 7984.316555463484, want 8306.043795298237
```

### 学習してサーバーを起動する

```go
server := &http.Server{
	Addr:              fmt.Sprintf("127.0.0.1:%d", Port),
	Handler:           NewHandler(service),
	ReadHeaderTimeout: readHeaderTimeout,
}

return server.ListenAndServe()
```

`http.ListenAndServe(addr, handler)` という 1 行の書き方もありますが、それだと**時間切れの設定が既定のまま（無制限）**になります。ヘッダーを少しずつ送り続けて接続を占有する Slowloris という攻撃に対して、`ReadHeaderTimeout` は最小限の備えです。`gosec` の `G112` が指摘する項目でもあります（第 5 章のとおり `golangci-lint` の既定では `gosec` は動きませんが、指摘される理由が分かっていれば最初から書けます）。

実行するとモデルを学習・保存してから待ち受けます。

```text
$ go run ./cmd/chapters chapter15
モデル cinema: true
モデル survived: true
http://localhost:8015 で待ち受けます
```

別の端末から呼びます。

```text
$ curl -s localhost:8015/health
{"status":"ok","models":{"cinema":true,"survived":true}}

$ curl -s -X POST localhost:8015/cinema/sales \
    -d '{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}'
{"sales":7984.316555463484}

$ curl -s -X POST localhost:8015/survived \
    -d '{"pclass": 1, "sex": "female", "sib_sp": 0, "parch": 0, "fare": 80}'
{"survived":true}
```

年齢と乗船港を省略しても動きます。空文字列（欠損値）のまま第 8 章のパイプラインに渡り、訓練データから求めた中央値・最頻値で補完されるからです。前処理をモデルと一緒に保存しておくと、**予測するときも学習のときと同じ手順**が適用されます。第 8 章でパイプラインごと保存した理由が、ここで回収されます。

### テストの実行結果

```text
$ go test ./internal/chapter15/... -cover
ok  	.../internal/chapter15	1.342s	coverage: 57.6% of statements

$ ML_DATA_DIR=$PWD/../data/sukkiri-ml go test ./internal/chapter15/... -cover
ok  	.../internal/chapter15	1.635s	coverage: 77.7% of statements
```

学習データが無くても API のテストは全部走ります（第 4 章で見たとおり、`go test` はパッケージのディレクトリで走るので、実データを使うには `ML_DATA_DIR` を渡します）。スタブのテストと統合テストを分けておくと、CI ではデータ無しで API の振る舞いを守れます。

## 15.8 まとめ

この章では、第 7・8 章のモデルを標準ライブラリだけで HTTP API にしました。

1. **層の分離** — ドメイン・アプリケーション・インフラ・プレゼンテーションをファイルで分け、依存の向きを内側にそろえた。インターフェースは使う側（ドメイン）に置き、実装は「実装します」と宣言しない
2. **番兵のエラー** — 「モデルが無い」を `ErrModelNotFound` で表し、`fmt.Errorf` の `%w` で包んで `errors.Is` で判別した。Java 版の検査例外と違ってコンパイラは強制しないので、インターフェースのコメントを約束の一部にした
3. **アダプター** — 第 7・8 章のモデルには手を入れず、非公開の型で約束に合わせた。保存形式はモデルの中身で選び、数値だけの線形回帰は JSON、前処理を含むパイプラインは第 8 章の gob にした
4. **入力の検証** — 数値の既定値が 0 である Go では、欠落を表すためにポインタで受ける。検証結果は sealed interface ではなく構造体 1 つで表し、規則はジェネリクス（`float64 | int`・`comparable`）で書いた
5. **フレームワーク無しの API** — Go 1.22 のルーティングで 404・405 を `ServeMux` に任せ、JSON のデコード・検証・エラーの変換・応答の書き出しは自分で書いた。ストリームのデコーダーが 2 つ目の JSON を見逃すこと、エラーのメッセージに内部の型名が入ることは、書いてみて初めて分かる
6. **テストの粒度** — スタブで学習データ無しに全部のエンドポイントを確かめ、実データの統合テストは `httptest.NewServer` でネットワーク越しに呼び、データが無ければスキップした

### シリーズの振り返り

第 1 章の「20 代ならきのこ派」という手書きのルールから、Go 版でも次の順に進んできました。

| 部 | 章 | 学んだこと | Go 版で効いた言語の性質 |
|----|----|-----------|------------------------|
| 第 1 部 | 第 1〜3 章 | データを読み込み、前処理し、決定木をデータから学ばせる基本サイクル | 構造体と `map[string]string`、`error` の戻り値、ジェネリクス、非公開メソッドのインターフェース |
| 第 2 部 | 第 4〜6 章 | その過程を支えるバージョン管理・静的解析・CI | Go Modules、`gofmt`・`go vet`・`golangci-lint`、`go test -cover` |
| 第 3 部 | 第 7〜9 章 | 回帰と、現実のデータの前処理 | gonum の `mat`、`stat` との突き合わせ、`golang.org/x/text` |
| 第 4 部 | 第 10〜12 章 | 複数のモデルの比較と評価 | インターフェースによる共通化、スライスとクロージャ |
| 第 5 部 | 第 13〜15 章 | 正解の無いデータの扱いと、モデルを API として届けるまで | `encoding/gob`、`encoding/json`、`net/http` |

Go 版の一貫した特徴は、**依存の少なさ**でした。使った外部ライブラリは gonum と `golang.org/x/text` の 2 つだけで、決定木・ランダムフォレスト・ロジスティック回帰・K-means・正則化は自作が最終実装です（[ADR 008](../../../adr/008-go-ml-libraries.md)）。ライブラリが揃っている Python 版や、Tribuo・ML.NET と突き合わせられた Java 版・C# 版と比べると、「ライブラリが無いときにどう作るか」を示す章が多くなりました。これは [TypeScript 版](../typescript/index.md) と同じ立場です。

そして、Go にはほかの言語版が当たり前に使っていた道具がありません。例外も、判別共用体も、`Optional` も、継承もありません。無いものは毎回、別の道具で代用してきました。例外は `error` の戻り値と番兵のエラーで、判別共用体は非公開メソッドのインターフェース（第 3 章）と構造体（第 15 章）で、`Optional` はポインタと空文字列で。**同じ問題に毎回同じ代用を当てはめないこと**が、Go で読みやすさを保つコツだと分かりました。

### 次の言語へ

Go 版で、第 2 波の 4 言語目が終わりました。残るは Rust です（[執筆計画](../outline.md)）。

Go 版で最後まで残った不一致は、乱数でした。`math/rand` は Java の `java.util.Random` とも .NET の `Random` とも乱数列が違うので、訓練データとテストデータの分かれ方が違い、正解率も係数も、ほかの言語版と一致しません（件数だけは一致します）。同じアルゴリズムを同じデータに適用しても、**擬似乱数の実装が違えば結果は揃わない**。多言語で同じ題材を書いてみて、いちばんはっきり見えた事実でした。数値の再現性を求めるなら、乱数の生成方法までそろえる必要があります。
