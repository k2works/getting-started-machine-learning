---
type: Article
title: "第 15 章: 機械学習 API とモジュール設計"
description: "第 7・8 章の学習済みモデルを Sinatra（Sinatra::Base のモジュール形式）と Puma で予測 API として公開し、ファイルで層を分ける。trait の無い Ruby では、置き場の約束をテストのモジュールにして本物と偽物の両方に走らせる。rack-test で統合テストを書き、Sinatra 4 の Host 検査・404 と 405・例外画面の既定を実測で確かめる。"
tags: [article,getting-start-ml,ruby]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-22T11:43:48Z }
---

# 第 15 章: 機械学習 API とモジュール設計

## 15.1 はじめに

シリーズの最後の章です。ここまでに作ったモデルは、テストと `bundle exec rake 'run[chapterNN]'` の中でしか動きませんでした。この章では、第 7 章の線形回帰（映画の興行収入）と第 8 章のパイプライン（乗客の生存）を **HTTP の予測 API** として公開します。

題材は [Rust 版の第 15 章](../rust/15-machine-learning-api-and-module-design.md) と同じで、3 つのエンドポイントを作ります。

| メソッド | パス | 役割 |
|---------|------|------|
| `GET` | `/health` | モデルを読み込めるかどうかを返す |
| `POST` | `/cinema/sales` | 映画の特徴量から興行収入を予測する |
| `POST` | `/survived` | 乗客の特徴量から生存を予測する |

Ruby 版では [Sinatra](https://sinatrarb.com/) 4.2 と [Puma](https://puma.io/) 8.0 を使います（[ADR 010](../../../adr/010-ruby-ml-libraries.md)）。Ruby 3.0 から標準ライブラリに HTTP サーバー（WEBrick）は無いので、ここは Rust 版（axum）・Java 版（Javalin）と同じく、ライブラリを入れる場面です。

この章で Ruby らしいのは次の 3 点です。

1. **約束をテストで書く** — Ruby には trait もインターフェースも無い。置き場の約束を「テストのモジュール」にして、本物の置き場とテスト用の偽物の両方に `include` する
2. **例外を HTTP のステータスコードに変える** — `rescue` の節でモデルが無いこと（503）とそれ以外（500）を分ける
3. **フレームワークの既定を知る** — Sinatra 4 は開発環境で Host ヘッダーを検査し、例外の画面を HTML で返し、許していないメソッドにも 404 を返す。どれも実測で確かめてから上書きする

## 15.2 層を分ける

### ファイルと依存の向き

`lib/getting_started_ml/chapter15/` の中を、ファイルで層に分けます。

```text
domain.rb       ドメイン層             Movie・Passenger・モデルのアダプター・ModelNotFound
service.rb      アプリケーション層     置き場からモデルを読んで予測する
store.rb        インフラ層             Marshal による保存と読み込み
validation.rb   プレゼンテーション層   要求の JSON の読み取りと検証
api.rb          プレゼンテーション層   Sinatra のルート・ステータスコードへの変換
chapter15.rb    組み立て               学習・保存・サーバーの起動
```

依存の向きは Rust 版と同じく内側（ドメイン）へ向けます。

```text
api.rb ──→ service.rb ──→ domain.rb ←── store.rb
                              ↑
                    （約束のメソッド名だけを知る）
```

`service.rb` は置き場のクラスを `require` しません。受け取った置き場に `load_sales_model` と `load_survival_model` を呼ぶだけです。`domain.rb` は Sinatra も Marshal も知りません。

### 約束を書く場所が無い

Rust 版は約束を trait で書きました。

```rust
pub trait ModelStore {
    fn load_sales_model(&self) -> Result<Box<dyn SalesModel>>;
    fn load_survival_model(&self) -> Result<Box<dyn SurvivalModel>>;
}
```

Ruby には trait に当たる構文がありません。第 8 章の前処理と同じく、**約束はメソッドの名前と戻り値の取り決めだけ** です。まずはコメントに書きます。

```ruby
  # 第 15 章のドメイン層。予測の入力と、モデル・置き場の約束。HTTP にも Marshal にも依存しない。
  #
  # Ruby には trait もインターフェースも無いので、約束はメソッドの名前と戻り値の取り決めだけで表す。
  #
  # - 興行収入のモデル: predict_sales(movie) で数値を返す
  # - 生存予測のモデル: survives?(passenger) で真偽値を返す
  # - モデルの置き場: load_sales_model・load_survival_model でモデルを返す。無ければ ModelNotFound を投げる
  #
  # 約束を守っているかは、置き場の実装とテスト用の偽物に同じテスト（test/support/model_store_contract.rb）を
  # 走らせて確かめる。
```

コメントは誰も検査しません。Rust ならコンパイラが「`impl ModelStore for StubStore` に `load_survival_model` が無い」と教えてくれますが、Ruby では偽物のメソッド名を 1 文字間違えても、テストは偽物の側で `NoMethodError` になるまで気づきません。もっと困るのは、**偽物だけが約束からずれていく** ことです。本物が「無ければ `ModelNotFound` を投げる」のに偽物が `nil` を返していたら、API のテストは偽物に合わせて緑になり、本物では動きません。

そこで、約束そのものをテストにします（15.5 節）。

## 15.3 TODO リストの作成

**TODO リスト**:

- [ ] 要求の JSON を読み、型が合わなければ弾く
- [ ] 必須・0 以上・選択肢の検証をし、理由を並べて返す
- [ ] 映画・乗客の特徴量を `Data` で表す
- [ ] 置き場の約束をテストのモジュールにする
- [ ] 第 7 章の線形回帰と第 8 章のパイプラインを Marshal で保存・読み込みする
- [ ] ファイルが無ければ `ModelNotFound` を投げる
- [ ] サービスが置き場からモデルを読んで予測し、ヘルスチェックを返す
- [ ] `POST /cinema/sales`・`POST /survived` が予測を JSON で返す
- [ ] 入力が不正なら 422、モデルが無ければ 503、それ以外は 500
- [ ] 知らないパスは 404、許していないメソッドは 405
- [ ] 第 7・8 章と同じ条件で学習し、API を起動する（`rake 'run[chapter15]'`）

## 15.4 要求を読んで検証する

### JSON.parse は型を決めない

Rust 版は要求を `Option<f64>` のフィールドを持つ構造体で受け、serde が読み込みと同時に型を確かめました。`{"sns1": "たくさん"}` は構造体にならず、そこで 422 にできます。

Ruby の `JSON.parse` は、JSON の値をそのまま `Hash`・`Integer`・`Float`・`String` にします。`"たくさん"` も文字列として読めてしまうので、**型の確認を自分で書きます**。列ごとの型を表にしました。

```ruby
      # 興行収入の予測の要求の列と型。原作の有無は整数で受け取る。
      MOVIE_TYPES = { "sns1" => Numeric, "sns2" => Numeric, "actor" => Numeric, "original" => Integer }.freeze

      # 生存の予測の要求の列と型。
      PASSENGER_TYPES = { "pclass" => Integer, "sex" => String, "age" => Numeric, "sib_sp" => Integer,
                          "parch" => Integer, "fare" => Numeric, "embarked" => String }.freeze
```

`Numeric` は `Integer` と `Float` の共通の親なので、`100` も `2000.5` も通ります。`original` を `Integer` にしたのは、`1.5` を 422 にするためです（Rust 版の `Option<i32>` と同じ扱い）。

```ruby
      # 本文を JSON のオブジェクトとして読み、列の型を確かめる。読めないか型が合わなければ nil を返す。
      # null は「値が無い」とみなし、表に無い列は無視する。
      def read_json(body, types)
        request = JSON.parse(body)
        return nil unless request.is_a?(Hash)

        request.all? { |field, value| typed?(types[field], value) } ? request : nil
      rescue JSON::ParserError
        nil
      end

      # 値が型に合うかどうか。null と、表に無い列（型が nil）は問わない。
      def typed?(type, value)
        type.nil? || value.nil? || value.is_a?(type)
      end
```

最初は `types.fetch(field, BasicObject) === value` と 1 行で書きましたが、RuboCop の `Style/CaseEquality` に `===` を避けるよう言われました。`===` はクラスに対しては `is_a?` の意味になりますが、正規表現なら一致、範囲なら包含と、受け手で意味が変わります。読み手に意図が伝わる `is_a?` に直し、「表に無い列」と「null」を問わないことも名前を付けて分けました。

`[1, 2]` のような配列も JSON としては正しいので、`Hash` かどうかも確かめます。テストは Rust 版の 3 つの入力に 2 つを足しました。

```ruby
  def test_JSON_として読めないか型が合わなければ_nil_を返す
    ['{"sns1": "たくさん"}', '{"original": 1.5}', "{ここは JSON ではない", "", "[1, 2]"].each do |body|
      assert_nil V.read_json(body, V::MOVIE_TYPES), body
    end
  end
```

### 理由を集めて、無ければ値を作る

検証の規則は Rust 版と同じ形です。規則は「理由の文字列か、問題なしの `nil`」を返し、`compact` で `nil` を落とします。Rust 版の `Option<String>` と `flatten()` に当たります。

```ruby
      # 理由（nil は問題なし）を集め、1 つも無ければブロックで値を作る。
      def validate(reasons)
        errors = reasons.compact

        Validated.new(value: errors.empty? ? yield : nil, errors:)
      end
```

結果は `Data` で表します。Rust 版は `enum Validated<T> { Valid(T), Invalid(Vec<String>) }` でしたが、Ruby には値を持つ列挙型が無いので、値と理由の一覧を両方持ち、`valid?` で見分けます。

```ruby
      # 検証の結果。理由の一覧が空なら正しく、value にドメインの値が入る。
      Validated = Data.define(:value, :errors) do
        def valid?
          errors.empty?
        end
      end
```

Rust の `match` なら `Invalid` の枝で値を取り出せないことがコンパイル時に保証されますが、Ruby では不正な要求の `value` が `nil` であることをテストで固定するしかありません。

```ruby
  def test_不正な要求には値が無い
    assert_nil V.movie({}).value
  end
```

乗客の年齢と乗船港は省略できます。省略された列は空文字列にして、第 8 章のパイプラインの欠損値として扱わせます。`nil.to_s` が `""` になるのを使いました。

```ruby
      # 検証して、正しければ乗客の特徴量にする。省略された列は空文字列（欠損値）にする。
      def passenger(request)
        validate(passenger_reasons(request)) do
          Passenger.new(**PASSENGER_TYPES.keys.to_h { |field| [field.to_sym, request[field].to_s] })
        end
      end
```

## 15.5 置き場の約束をテストで書く

### 約束のテストを本物と偽物に include する

Rust 版の trait に当たるものを、**テストのモジュール** として書きます。

```ruby
# モデルの置き場の約束を、テストとして書いたもの。Rust 版の trait ModelStore に当たる。
#
# Ruby は「約束を満たすつもりだ」と宣言できないので、置き場の実装とテスト用の偽物の両方に
# このモジュールを include して、同じテストを走らせる。include する側は次の 2 つを定義する。
#
# - store_with_models: 2 つのモデルを読み込める置き場
# - store_without_models: どちらのモデルも無い置き場
module ModelStoreContract
  C = GettingStartedMl::Chapter15

  CONTRACT_MOVIE = C::Movie.new(sns1: 100, sns2: 2000, actor: 300, original: 1)
  CONTRACT_PASSENGER = C::Passenger.new(pclass: "1", sex: "female", age: "", sib_sp: "0", parch: "0", fare: "80",
                                        embarked: "")

  def test_約束_モデルがあれば予測できるモデルを返す
    store = store_with_models

    assert_kind_of Numeric, store.load_sales_model.predict_sales(CONTRACT_MOVIE)
    assert_includes [true, false], store.load_survival_model.survives?(CONTRACT_PASSENGER)
  end

  def test_約束_モデルが無ければ_ModelNotFound_を投げる
    store = store_without_models

    assert_equal C::SALES_MODEL, assert_raises(C::ModelNotFound) { store.load_sales_model }.model_name
    assert_equal C::SURVIVAL_MODEL, assert_raises(C::ModelNotFound) { store.load_survival_model }.model_name
  end
end
```

Minitest は `test_` で始まるメソッドをテストとして走らせるので、モジュールに書いたメソッドは `include` したクラスのテストになります。本物の置き場のテストでは、一時ディレクトリにモデルを保存したものと、空のディレクトリを渡します。

```ruby
class Chapter15StoreTest < Minitest::Test
  include ModelStoreContract
  ...
  def store_with_models
    C::FileModelStore.new(@dir).tap do |store|
      store.save_sales_model(sales_model)
      store.save_survival_model(survival_pipeline)
    end
  end

  def store_without_models
    C::FileModelStore.new(File.join(@dir, "empty"))
  end
```

偽物は、予測サービスのテストで同じ約束を満たしていることを確かめます。

```ruby
class Chapter15ServiceTest < Minitest::Test
  include ModelStoreContract
  ...
  # 偽物も置き場の約束を満たしていることを、本物と同じテストで確かめる。
  def store_with_models
    Fake.new(sales: true, survival: true)
  end

  def store_without_models
    Fake.new(sales: false, survival: false)
  end
```

これで、偽物が `ModelNotFound` の代わりに `nil` を返すように変わると、約束のテストが落ちます。Rust の `impl ModelStore for StubStore` をコンパイラが検査するのと同じことを、**テストの実行で** 確かめる形です。確かめられるのは約束のテストに書いた範囲だけで、書き忘れた約束は守られません。Rust の型よりは弱く、そのかわり「無ければ例外を投げる」のような型では書けない約束も書けます。

### 偽物は test/ に置く

偽物は `test/support/chapter15_fakes.rb` に置きました。Rust 版が `tests/prediction_api.rs` の中に `StubStore` を書いたのと同じ位置で、`lib/` からは見えません。

```ruby
  # モデルがあるかどうかを差し替えられる置き場。
  FakeModelStore = Data.define(:sales, :survival) do
    def load_sales_model
      raise C::ModelNotFound, C::SALES_MODEL unless sales

      FixedSales.new(sales: 4321.5)
    end

    def load_survival_model
      raise C::ModelNotFound, C::SURVIVAL_MODEL unless survival

      FixedSurvival.new(survived: true)
    end
  end
```

Rust 版は `StubStore` を `Box::new` で包んで渡しましたが、Ruby の偽物は何も宣言せずに渡せます。親クラスもモジュールも要りません。`Rakefile` のテストの対象は `test/**/*_test.rb` なので、`support/` のファイルはテストとしては走らず、使うテストが `require_relative` で読み込みます。

## 15.6 ドメインとサービス

### モデルが無いことを例外のクラスで表す

Rust 版は `enum Error` のバリアント `ModelNotFound(String)` で種類を表しました。Ruby では例外のクラスにします。

```ruby
    # モデルを読み込めない。モデルをまだ学習していないだけなので、API は 503 を返す。
    class ModelNotFound < StandardError
      attr_reader :model_name

      def initialize(model_name)
        @model_name = model_name
        super("モデルがありません: #{model_name}")
      end
    end
```

`raise ModelNotFound, "cinema"` と書くと、Ruby は `ModelNotFound.new("cinema")` を呼んで投げます。第 2 引数がメッセージではなくコンストラクタの引数になるので、モデルの名前を渡せば文言は組み立ててくれます。

### 既存のモデルをアダプターで約束に合わせる

第 7・8 章のモデルには手を入れません。Rust 版と同じく、約束に合わせる薄い型をかぶせます。

```ruby
    # 第 7 章の線形回帰のモデルを、興行収入のモデルの約束に合わせる。
    LinearSalesModel = Data.define(:model) do
      def predict_sales(movie)
        model.predict_one(movie.features)
      end
    end

    # 第 8 章の学習済みパイプラインを、生存予測のモデルの約束に合わせる。
    PipelineSurvivalModel = Data.define(:pipeline) do
      def survives?(passenger)
        pipeline.predict(Chapter08::Survived.features([passenger.row])).first == Chapter08::Survived::SURVIVED
      end
    end
```

Rust 版のメソッド名は `survives` でしたが、Ruby では真偽値を返すメソッドに `?` を付けます。サービスの側も最初は Rust 版にならって `predict_survival` と書き、RuboCop の `Naming/PredicateMethod` に「真偽値を返すなら名前を `?` で終えよ」と言われて `survives?` に直しました。

### サービスは HTTP を知らない

```ruby
    # アプリケーション層。置き場からモデルを読み込んで予測する。HTTP を知らない。
    #
    # 置き場は約束（load_sales_model・load_survival_model）を満たすものなら何でもよいので、
    # テストでは偽物を渡せる。型の宣言は無く、渡したものが約束を満たすかは呼んだときに分かる。
    class PredictionService
      def initialize(store)
        @store = store
      end
      ...
      # モデルごとに読み込めるかどうかを返す。読み込めない理由は問わない。
      def health
        [ModelHealth.new(name: SALES_MODEL, ready: ready? { @store.load_sales_model }),
         ModelHealth.new(name: SURVIVAL_MODEL, ready: ready? { @store.load_survival_model })]
      end

      private

      def ready?
        yield
        true
      rescue StandardError
        false
      end
```

Rust 版の `is_ok()` に当たるのが `ready?` です。ブロックが例外を投げずに終われば `true` にします。Rust 版の `PredictionService` は置き場に `+ Send + Sync` を課し、同時に走るハンドラーから安全に共有できることを型で要求しました。Ruby にはその仕組みが無いので、このサービスは **状態を書き換えない** ことで同時に使われても困らないようにしています。`@store` は作ったときに 1 度だけ代入し、予測のたびにファイルからモデルを読みます。

## 15.7 モデルを Marshal で保存する

第 8 章で、学習済みのパイプラインは `Marshal` でそのまま保存できることを確かめました。第 7 章の `LinearModel` も `Data` なので、何も足さずに保存できます。Rust 版が `LinearModel` に serde の derive を 1 行足したのと違い、**既存の章のコードに手を入れずに済みました**。

```ruby
      # 線形回帰のモデルを読み込み、興行収入のモデルの約束に合わせて返す。
      def load_sales_model
        model = missing_as_not_found(SALES_MODEL) do
          Marshal.load(File.binread(sales_model_file)) # rubocop:disable Security/MarshalLoad
        end
        raise TypeError, "学習済みの線形回帰ではありません: #{model.class}" unless model.is_a?(Chapter07::LinearModel)

        LinearSalesModel.new(model:)
      end
```

生存予測のほうは第 8 章の `ModelFile.load` を使います。`Marshal.load` は第 8 章と同じく、自分で保存したファイルだけを読む前提で、その行だけ RuboCop の `Security/MarshalLoad` を抑止しました。読み込んだものが線形回帰でなければ `TypeError` にします。

ファイルが無いことは、`Errno::ENOENT` を捕まえて `ModelNotFound` に変えます。

```ruby
      # ファイルが無いことを「モデルがない」に変える。それ以外の失敗はそのまま投げる。
      def missing_as_not_found(name)
        yield
      rescue Errno::ENOENT
        raise ModelNotFound, name
      end
```

Rust 版の `error.kind() == std::io::ErrorKind::NotFound` のガードに当たります。ファイルが無いのはモデルをまだ学習していないだけなので 503、読めるが壊れているなら 500 です。`rescue StandardError` にしてしまうと、ファイルの権限の問題も「モデルがありません」と表示され、原因を探せなくなります。

保存と読み込みは、手で作った小さなモデルでテストします。線形回帰は切片 10、係数 1・2・3・4 のモデルを保存して、予測値を手で計算した値と比べました。

```ruby
  def test_保存した線形回帰で予測できる
    # 10 + 1×100 + 2×2000 + 3×300 + 4×1
    assert_in_delta 5014.0, store_with_models.load_sales_model.predict_sales(CONTRACT_MOVIE), 1e-9
  end
```

## 15.8 Sinatra でエンドポイントを作る

### モジュール形式で書き、サービスを差し込む

Sinatra には、ファイルの最上位に `get "/" do ... end` と書くクラシック形式と、`Sinatra::Base` を継承するモジュール形式があります。クラシック形式は `Object` にメソッドを足すので、ライブラリの中ではモジュール形式を使います。

```ruby
    class Api < Sinatra::Base
      ...
      # 予測サービスを差し込んだアプリケーションを作る。Sinatra::Base.new は Rack のミドルウェアで包んで返す。
      def initialize(app = nil, service:)
        super(app)
        @service = service
      end
```

Rust 版は `router(service)` がサービスを状態として持つ `Router` を返しました。Sinatra では `initialize` にキーワード引数を足し、インスタンス変数に入れます。`Api.new(service:)` が返すのは `Api` のインスタンスではなく、ミドルウェアで包んだ `Sinatra::Wrapper` です。それでも `Api.new` に渡した引数は `initialize` まで届きます。

Sinatra はリクエストごとに `Api` を複製してルートを実行するので、`@service` は複製にも写ります。サービスの状態を書き換えないようにしたのはこのためでもあります。

### rack-test でサーバーを起動せずにテストする

[rack-test](https://github.com/rack/rack-test) は、Rack のアプリケーションをポートを開かずに呼びます。Rust 版の tower の `oneshot` に当たります。

```ruby
class Chapter15ApiTest < Minitest::Test
  include Rack::Test::Methods

  C = GettingStartedMl::Chapter15

  def setup
    @store = Chapter15Fakes::FakeModelStore.new(sales: true, survival: true)
  end

  # rack-test が呼ぶ Rack アプリケーション。サーバーは起動しない。
  def app
    C::Api.new(service: C::PredictionService.new(@store))
  end

  def post_json(path, body)
    post path, body, "CONTENT_TYPE" => "application/json"
  end

  def test_興行収入を予測して_JSON_で返す
    post_json "/cinema/sales", '{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}'

    assert_equal 200, last_response.status
    assert_equal "application/json", last_response.media_type
    assert_equal '{"sales":4321.5}', last_response.body
  end
```

`include Rack::Test::Methods` で `get`・`post`・`last_response` が使えるようになり、`app` メソッドが返すものを呼びます。

### 詰まった点 1: Host not permitted

Sinatra 4.1 から、開発環境では **Host ヘッダーを検査する** ようになりました。許されるのは `localhost` や IP アドレスなどで、rack-test が送る既定の Host は `example.org` です。設定を足さないクラスを rack-test で呼ぶと、ルートに届く前に弾かれます。

```text
plain GET /x: 403 "Host not permitted"
```

環境は `APP_ENV`・`RACK_ENV` が無ければ `:development` です。テストのために環境変数を変えるより、アプリケーション側で検査を切りました。この API は `127.0.0.1` でしか待ち受けないので、Host の検査に頼る場面がありません。

```ruby
      # 開発時の例外画面と、Host ヘッダーの検査（Sinatra 4.1 から開発環境で既定になった）を切る。
      # 例外は各ルートで自分でステータスコードに変える。
      set :show_exceptions, false
      set :raise_errors, false
      set :dump_errors, false
      set :host_authorization, { permitted_hosts: [] }
```

`permitted_hosts` を空にすると、すべての Host を許します。

### 詰まった点 2: 開発環境の例外画面は中身を漏らす

同じく開発環境では、ルートで例外が起きると **例外の詳細を載せた HTML** を 500 で返します（`show_exceptions`）。`raise "内部の秘密"` と書いたルートを呼ぶと、応答の本文に `内部の秘密` が入っていました。

```text
nohost GET /boom: 500 true
```

Rust 版が serde のエラーの文言（内部の型名を含む）を応答に出さなかったのと同じ理由で、上の設定で例外画面を切り、例外は各ルートで自分で変換します。

### 例外をステータスコードに変える

```ruby
      # 本文を読んで検証し、正しければブロックで予測する。失敗はステータスコードに変える。
      def predict(types, validator)
        request_json = Validation.read_json(request.body.read, types)
        return rejected([INVALID_JSON]) if request_json.nil?

        validated = validator.call(request_json)
        return rejected(validated.errors) unless validated.valid?

        JSON.generate(yield(validated.value))
      rescue ModelNotFound => e
        failure(503, e.message)
      rescue StandardError
        failure(500, "予測できませんでした")
      end
```

Rust 版の `match` による `failure` と同じく、**変換が起きる場所をコードに見せます**。Sinatra には `error ModelNotFound do ... end` という例外ハンドラーもありますが、それだと検証（422）と予測（503・500）の分岐がファイルのあちこちに散ります。`rescue` は上から順に照合されるので、`ModelNotFound` を先に書きます。逆にすると `StandardError` が先に捕まえ、モデルが無いのに 500 になります。

ルートは型の表と検証のメソッドを渡すだけになります。

```ruby
      post "/cinema/sales" do
        predict(Validation::MOVIE_TYPES, Validation.method(:movie)) do |movie|
          { sales: @service.predict_sales(movie) }
        end
      end
```

`Validation.method(:movie)` は、モジュールのメソッドを取り出した `Method` オブジェクトで、`call` で呼べます。

### 詰まった点 3: 許していないメソッドも 404

Rust 版のテストをそのまま移すと、`GET /cinema/sales` は 405 ではなく 404 になりました。axum の `Router` はパスが合ってメソッドが違えば 405 を返しますが、**Sinatra はメソッドとパスの組でルートを探し、見つからなければ一律に 404** です。

```text
nohost GET /x: 404
```

`POST /x` だけを定義したクラスに `GET /x` を送った結果です。`not_found` のブロックで、知っているパスなら 405 に変えました。

```ruby
      # ルートが無いときは、パスを知っていれば 405、知らなければ 404 にする。
      not_found do
        allowed = ALLOWED_METHODS[request.path_info]
        return JSON.generate({ detail: "見つかりません" }) if allowed.nil?

        status 405
        headers "Allow" => allowed
        JSON.generate({ detail: "許していないメソッドです" })
      end
```

405 の応答には、許しているメソッドを `Allow` ヘッダーで返します。パスとメソッドの表（`ALLOWED_METHODS`）をルートと別に持つので、ルートを足したら表も直す必要があります。二重に書いている点は、Sinatra の上に立つ限りの負担です。

### 詰まった点 4: rack-test は app を 1 度しか作らない

Rust 版のヘルスチェックのテストは、1 つのテストの中でスタブを替えて 2 回呼びました。同じように `@store` を替えて `get "/health"` を 2 回呼ぶと、2 回目も `"status":"ok"` が返りました。rack-test は最初の要求で `app` を呼んでセッションを作り、**同じテストの中ではそれを使い回す** からです。テストを 2 つに分けました。

```ruby
  # rack-test は 1 つのテストの中で app を 1 度しか作らないので、置き場を替えるならテストを分ける
  def test_読み込めないモデルがあればヘルスチェックは_degraded_を返す
    @store = Chapter15Fakes::FakeModelStore.new(sales: true, survival: false)
    get "/health"

    assert_equal '{"status":"degraded","models":{"cinema":true,"survived":false}}', last_response.body
  end
```

Ruby の `Hash` は挿入の順を保つので、`models` の並びは `health` が返した順（`cinema`・`survived`）になります。Rust 版が `HashMap` ではなく `BTreeMap` で並びを固定した部分は、何もしなくても揃いました。

## 15.9 実データで学習したモデルで動かす

### 学習と保存

第 7・8 章と同じ条件（`test_size` 0.2、シード 0、深さ 5、`balanced` の重み）で学習します。

```ruby
    # 第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。
    def self.train_and_save_models(data_dir, store)
      split = Chapter07::Cinema.prepare(File.join(data_dir, "cinema.csv"), test_size: TEST_SIZE, seed: SEED)
      store.save_sales_model(Chapter07.fit(split.x_train, split.t_train))
      store.save_survival_model(survival_pipeline(File.join(data_dir, "Survived.csv")))
    end
```

### 統合テスト

実データで学習したモデルをつなぐテストは、既存の章と同じく、データが無ければ `skip` します。期待値は、最初に `0.0` を書いてテストを落とし、実際に動かして測った値に直しました。

```text
Expected |0.0 - 7272.902816073627| (7272.902816073627) to be <= 1.0e-06.
```

```ruby
  def test_保存したモデルを読み込んで予測できる
    service = trained_service
    movie = C::Movie.new(sns1: 100, sns2: 2000, actor: 300, original: 1)

    # Ruby の Random の分け方がほかの言語版と違うので、予測値もほかの言語版と一致しない
    assert_in_delta 7272.902816073627, service.predict_sales(movie), 1e-6
```

同じ映画の予測が、Rust 版では 7615.295978481879 でした。第 7 章で見たとおり、訓練データに入る行が言語ごとに違うからです。

### Puma で待ち受ける

```ruby
    # モデルを学習して保存し、予測 API を起動する。Ctrl-C で止まるまで戻らない。
    def self.run(out = $stdout)
      store = FileModelStore.new(MODEL_DIR)
      train_and_save_models(Dataset.dir, store)
      service = PredictionService.new(store)

      service.health.each { |model| out.puts "モデル #{model.name}: #{model.ready}" }
      out.puts "http://localhost:#{PORT} で待ち受けます"
      out.flush
      serve(Api.new(service:))
    end

    # Puma で待ち受ける。rackup の Handler が、Rack のアプリケーションとサーバーをつなぐ。
    def self.serve(app)
      require "rackup"

      Rackup::Handler.get("puma").run(app, Host: "127.0.0.1", Port: PORT, Silent: true)
    end
```

Rust 版は同期の `run` から `tokio` の実行時を作って非同期の世界に入りましたが、Puma はスレッドでリクエストをさばくので、`run` から普通に呼ぶだけです。`Rackup::Handler.get("puma")` は、Puma の gem が rackup に登録したハンドラーを名前で探します。

`out.flush` は、動かしてみてから足しました。出力をファイルにリダイレクトして起動すると、`$stdout` はバッファされ、**サーバーを止めるまで起動のメッセージが出てきません**。待ち受けに入る前に書き出します。

実行するとモデルを学習・保存してから待ち受けます（学習データの置き場は環境変数 `ML_DATA_DIR` で指定できます）。

```text
$ bundle exec rake 'run[chapter15]'
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
{"sales":7272.902816073627}

$ curl -s -X POST localhost:8015/survived \
    -H 'content-type: application/json' \
    -d '{"pclass": 1, "sex": "female", "sib_sp": 0, "parch": 0, "fare": 80}'
{"survived":true}

$ curl -s -X POST localhost:8015/cinema/sales \
    -H 'content-type: application/json' \
    -d '{"sns1": -1, "sns2": 2000, "actor": 300, "original": 2}'
{"detail":["sns1 は 0 以上にしてください","original は 0、1 のどれかにしてください"]}

$ curl -s -X POST localhost:8015/cinema/sales \
    -H 'content-type: application/json' \
    -d '{"sns1": "たくさん"}'
{"detail":["JSON の形式または値の型が正しくありません"]}

$ curl -s -o /dev/null -w "%{http_code}\n" localhost:8015/unknown
404
$ curl -s -o /dev/null -w "%{http_code}\n" localhost:8015/cinema/sales
405
```

405 の応答のヘッダーを見ると、`allow: POST` のほかに `x-cascade: pass` が付いていました。Sinatra がルートを見つけられなかった印で、`not_found` のブロックで応答を作り直しても残ります。

```text
$ curl -s -i localhost:8015/cinema/sales
HTTP/1.1 405 Method Not Allowed
content-type: application/json
x-cascade: pass
allow: POST
x-content-type-options: nosniff
content-length: 49

{"detail":"許していないメソッドです"}
```

年齢と乗船港を省略しても動きます。空文字列（欠損値）のまま第 8 章のパイプラインに渡り、訓練データから求めた中央値・最頻値で補完されるからです。**前処理をモデルと一緒に保存しておくと、予測のときも学習と同じ手順が適用されます。**

## 15.10 品質チェック

```bash
bundle exec rake check
```

この章の実装を終えた時点で、学習データがある状態では 152 runs・271 assertions・0 skips、行カバレッジは 707 / 730 行（96.84%）でした。学習データを外すと 152 runs・228 assertions・20 skips、662 / 730 行（90.68%）です。

| 学習データ | runs | assertions | skips | 行カバレッジ |
|-----------|------|-----------|-------|------------|
| あり | 152 | 271 | 0 | 96.84% |
| なし | 152 | 228 | 20 | 90.68% |

この章のファイルごとの行カバレッジは次のとおりです。

| ファイル | 学習データあり | 学習データなし |
|---------|--------------|--------------|
| `chapter15/api.rb` | 46 / 46（100%） | 46 / 46（100%） |
| `chapter15/domain.rb` | 19 / 19（100%） | 19 / 19（100%） |
| `chapter15/service.rb` | 18 / 18（100%） | 18 / 18（100%） |
| `chapter15/store.rb` | 29 / 29（100%） | 29 / 29（100%） |
| `chapter15/validation.rb` | 39 / 39（100%） | 39 / 39（100%） |
| `chapter15.rb` | 22 / 31（70.97%） | 16 / 31（51.61%） |

学習データを外しても、**`api.rb`・`store.rb` を含む層のファイルは 100% のまま** です。偽物の置き場と手で作った小さなモデルで、API の振る舞いも保存と読み込みも確かめられるからです。下がるのは学習の手順を組み立てる `chapter15.rb` だけで、サーバーを起動する `run`・`serve` はテストせず、`curl` で確かめました。

この章で RuboCop に言われたことは次のとおりです。

| ルール | 場所 | 直し方 |
|------|------|------|
| `Style/CaseEquality` | `read_json` の `===` | `is_a?` を使う `typed?` に分けた |
| `Naming/PredicateMethod` | `PredictionService#predict_survival` | 真偽値を返すので `survives?` に改名した |
| `Layout/LineLength` | 統合テストの期待値の行 | 映画を変数に取り出した |
| `Security/MarshalLoad` | `FileModelStore#load_sales_model` | 自分で保存したファイルだけを読む前提で、その行だけ抑止した（第 8 章と同じ） |

## 15.11 まとめ

この章では、第 7・8 章のモデルを Sinatra の HTTP API にしました。Ruby に固有の論点は次のとおりです。

1. **約束をテストのモジュールにする** — trait の無い Ruby では、置き場の約束を `ModelStoreContract` というテストにして、本物の置き場と偽物の両方に `include` した。偽物だけが約束からずれることを、コンパイラの代わりにテストの実行で防ぐ
2. **偽物は test/ に置き、何も宣言せずに渡す** — `Data.define` で作った偽物に親クラスは要らない。Rust の `impl ModelStore for StubStore` に当たる宣言が無いぶん、約束のテストが要る
3. **型の確認を自分で書く** — `JSON.parse` は型を決めないので、列ごとの型の表で確かめ、合わなければ 422 にした。`===` ではなく `is_a?` で意図を書く
4. **例外は rescue の順で分ける** — `ModelNotFound` は 503、それ以外は 500。`rescue` は上から照合されるので、狭い例外を先に書く
5. **Sinatra の既定を実測で確かめて上書きする** — 開発環境の Host 検査（rack-test の `example.org` を 403 で弾く）、例外の詳細を載せた HTML の 500、許していないメソッドへの 404。どれも設定か `not_found` で直した
6. **Marshal なら既存の章に手を入れずに保存できる** — Rust 版は `LinearModel` に serde の derive を足したが、Ruby は `Data` をそのまま保存できる。読み込みは信頼できるファイルだけ

**TODO リスト（この章の完了時点）**:

- [x] 要求の JSON を読み、型が合わなければ弾く
- [x] 必須・0 以上・選択肢の検証をし、理由を並べて返す
- [x] 映画・乗客の特徴量を `Data` で表す
- [x] 置き場の約束をテストのモジュールにする
- [x] 第 7 章の線形回帰と第 8 章のパイプラインを Marshal で保存・読み込みする
- [x] ファイルが無ければ `ModelNotFound` を投げる
- [x] サービスが置き場からモデルを読んで予測し、ヘルスチェックを返す
- [x] `POST /cinema/sales`・`POST /survived` が予測を JSON で返す
- [x] 入力が不正なら 422、モデルが無ければ 503、それ以外は 500
- [x] 知らないパスは 404、許していないメソッドは 405
- [x] 第 7・8 章と同じ条件で学習し、API を起動する（`rake 'run[chapter15]'`）

これで Ruby 版は、最初の「20 代ならきのこ派」という手書きのルールから、学習したモデルを HTTP で届けるところまでたどり着きました。型を書かない言語で最後まで支えになったのは、第 1 章から続けてきたテストです。この章では、そのテストが約束の置き場にもなりました。
