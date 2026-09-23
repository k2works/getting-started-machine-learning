---
type: Article
title: "第 15 章: 機械学習 API とモジュール設計"
description: "第 7・8 章の学習済みモデルを Ring と Jetty で予測 API として公開し、名前空間で層を分ける。ハンドラーが「要求のマップを受け取り応答のマップを返す関数」であることを使って、サーバーを起動しない統合テストを書き、置き場の約束を defprotocol と約束のテストの両方で守る。"
tags: [article,getting-start-ml,clojure]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-23T01:41:50Z }
---

# 第 15 章: 機械学習 API とモジュール設計

## 15.1 はじめに

シリーズの最後の章です。ここまでに作ったモデルは、テストと `clojure -M:run chapterNN` の中でしか動きませんでした。この章では、第 7 章の線形回帰（映画の興行収入）と第 8 章のパイプライン（乗客の生存）を **HTTP の予測 API** として公開します。

題材は [Java 版の第 15 章](../java/15-machine-learning-api-and-module-design.md)・[Ruby 版](../ruby/15-machine-learning-api-and-module-design.md) と同じで、3 つのエンドポイントを作ります。

| メソッド | パス | 役割 |
|---------|------|------|
| `GET` | `/health` | モデルを読み込めるかどうかを返す |
| `POST` | `/cinema/sales` | 映画の特徴量から興行収入を予測する |
| `POST` | `/survived` | 乗客の特徴量から生存を予測する |

Clojure 版では [Ring](https://github.com/ring-clojure/ring) 1.12 と Jetty（`ring/ring-jetty-adapter`）、JSON は [Cheshire](https://github.com/dakrone/cheshire) 5.13 を使います（[ADR 011](../../../adr/011-clojure-ml-libraries.md)）。**経路の振り分けのライブラリ（reitit・compojure）は使いません。** この章で見せたいのは、Ring の約束そのものだからです。

> Ring のハンドラーは、要求を表すマップを受け取り、応答を表すマップを返す **ただの関数** である。

フレームワークのクラスを継承することも、注釈を書くことも、DSL を覚えることもありません。関数なので、テストはその関数を呼ぶだけで済み、サーバーを起動する必要がありません。Java 版が Javalin の仕組み、Ruby 版が rack-test を使った部分が、Clojure では何も足さずに済みます。

この章で Clojure らしいのは次の 4 点です。

1. **ハンドラーは関数、要求も応答もマップ** — 統合テストは `((api/handler store) {:request-method :get :uri "/health"})` と書ける。`ring/ring-mock` も要らない
2. **置き場の約束は `defprotocol` + 約束のテスト** — 型で書けるのは「メソッドの名前と引数の数」まで。「無ければ例外を投げる」という取り決めはテストで守る
3. **モデルは関数でよい** — 第 10 章で分類器を関数にしたのと同じく、`SalesModel`・`SurvivalModel` にあたる型は作らず、`(fn [movie] 数値)` を返す
4. **保存は EDN のまま** — 第 8 章で `pr-str` と `clojure.edn/read-string` の往復が成立しているので、第 7 章のモデルも第 8 章のパイプラインも、既存の章に手を入れずに保存できる

## 15.2 層を分ける

### 名前空間と依存の向き

`src/getting_started_ml/chapter15/` の中を、名前空間で層に分けます。

```text
domain.clj      ドメイン層             Movie・Passenger・モデルのアダプター・ModelStore の約束
service.clj     アプリケーション層     置き場からモデルを読んで予測する
store.clj       インフラ層             EDN による保存と読み込み
validation.clj  プレゼンテーション層   要求の JSON の読み取りと検証
api.clj         プレゼンテーション層   Ring のハンドラー・ステータスコードへの変換
chapter15.clj   組み立て               学習・保存・サーバーの起動
```

依存の向きは Java 版・Rust 版と同じく内側（ドメイン）へ向けます。

```text
api.clj ──→ service.clj ──→ domain.clj ←── store.clj
                                 ↑
                    （ModelStore の約束だけを知る）
```

Clojure の `ns` はコンパイル時に循環を検出するので、**この向きは実際に守られていることを処理系が保証します**。`domain.clj` から `api.clj` を参照しようとすると `Cyclic load dependency` で落ちます。名前空間の依存はパッケージの依存より粒度が細かく、層の逆流を防ぐ道具としてはかなり強いほうです。

### 置き場の約束をどう表すか

Java 版は `interface ModelStore`、Rust 版は `trait ModelStore` でした。Clojure で「約束」を表す道具は 3 つあります。

| 書き方 | 長所 | 短所 |
|-------|------|------|
| 関数を引数で渡す（第 10 章の分類器） | 何も宣言しない。いちばん軽い | 2 つ以上の操作をまとめると引数が増える |
| 関数を入れたマップを渡す | まとめられる。値なので `=` で比べられる | 名前が付かない。綴りの間違いが `nil` を呼ぶ形で出る |
| `defprotocol` | 名前が付く。`reify` で偽物を書ける。`satisfies?` で確かめられる | JVM のインターフェースを作るので、値としては扱えない |

ここは **`defprotocol`** にしました。操作が 2 つあり、「置き場」という名前に意味があり、テストで偽物を差し替えたいからです。

```clojure
(defprotocol ModelStore
  "学習済みモデルの置き場の約束。読み込めなければ model-not-found の例外を投げる。"
  (load-sales-model [store] "映画の特徴量から興行収入を返す関数を読み込む。")
  (load-survival-model [store] "乗客の特徴量から生存するかどうかを返す関数を読み込む。"))
```

一方、**モデルそのものには protocol を作りませんでした**。Java 版の `SalesModel`・`SurvivalModel` は `@FunctionalInterface` で、実質「関数 1 つ」です。第 10 章で分類器を関数にしたのと同じ理由で、Clojure では包む型を置かずに `(fn [movie] 数値)` をそのまま返します。

この使い分けの基準は「操作がいくつあるか」です。1 つなら関数、複数で名前が要るなら protocol。

### protocol が保証しないこと

`defprotocol` が決めるのは **メソッドの名前と引数の数** だけです。次の取り決めは型では書けません。

- 読み込めなければ `model-not-found` の例外を投げる（`nil` を返すのではない）
- 返る関数は、映画のマップを受け取って数値を返す

Rust の `trait` でも戻り値の型までしか書けませんが、Rust なら `Result<Box<dyn SalesModel>>` と書いた時点で「失敗しうる」ことが呼び出し側に強制されます。Clojure では、偽物が `nil` を返すように変わっても、API のテストは偽物に合わせて緑のままです。そこで、約束そのものをテストにします（15.5 節）。

## 15.3 TODO リストの作成

**TODO リスト**:

- [ ] 要求の JSON を読み、型が合わなければ弾く
- [ ] 必須・0 以上・選択肢の検証をし、理由を並べて返す
- [ ] 映画・乗客の特徴量をマップで表す
- [ ] 置き場の約束を `defprotocol` と約束のテストで表す
- [ ] 第 7 章の線形回帰と第 8 章のパイプラインを EDN で保存・読み込みする
- [ ] ファイルが無ければ「モデルが無い」の例外を投げる
- [ ] サービスが置き場からモデルを読んで予測し、ヘルスチェックを返す
- [ ] `POST /cinema/sales`・`POST /survived` が予測を JSON で返す
- [ ] 入力が不正なら 422、モデルが無ければ 503、それ以外は 500
- [ ] 知らないパスは 404、許していないメソッドは 405
- [ ] 第 7・8 章と同じ条件で学習し、API を起動する（`clojure -M:run chapter15`）

## 15.4 要求を読んで検証する

### Cheshire は型を決めない

Java 版は要求を `record MovieRequest(Double sns1, …)` で受け、Jackson が読み込みと同時に型を確かめました。`{"sns1": "たくさん"}` はレコードにならず、そこで 422 にできます。

Cheshire の `parse-string` は、JSON の値をそのまま Clojure のマップ・数値・文字列にします。`"たくさん"` も文字列として読めてしまうので、**型の確認を自分で書きます**。列ごとの型を表にしました。

```clojure
(def movie-types
  "興行収入の予測の要求の列と型。原作の有無は整数で受け取る（1.5 を弾くため）。"
  {"sns1" :number "sns2" :number "actor" :number "original" :integer})

(def passenger-types
  "生存の予測の要求の列と型。年齢と乗船港は省略できる。"
  {"pclass" :integer "sex" :string "age" :number "sib_sp" :integer
   "parch" :integer "fare" :number "embarked" :string})
```

型をキーワードで表し、確認は `case` で分けます。Clojure には `Number` のようなクラスで書く手もありますが、`number?`・`integer?`・`string?` という述語のほうが読み手に近いので、名前を付け直しました。

```clojure
(defn- typed?
  "値が型に合うかどうか。null と、表に無い列（型が nil）は問わない。"
  [type value]
  (case type
    nil true
    :number (or (nil? value) (number? value))
    :integer (or (nil? value) (integer? value))
    :string (or (nil? value) (string? value))))

(defn read-json
  "本文を JSON のオブジェクトとして読み、列の型を確かめる。読めないか型が合わなければ nil を返す。"
  [body types]
  (try
    (let [request (json/parse-string body)]
      (when (and (map? request)
                 (every? (fn [[field value]] (typed? (get types field) value)) request))
        request))
    (catch JsonProcessingException _ nil)))
```

`original` を `:integer` にしたのは、`1.5` を 422 にするためです。Clojure の `integer?` は `Long` と `BigInt` に真を返し、`1.5`（`Double`）には偽を返すので、Java 版の `Integer` と同じ扱いになります。

捕まえる例外は `com.fasterxml.jackson.core.JsonProcessingException` です。Cheshire は Jackson の薄い包みなので、壊れた JSON では Jackson の例外がそのまま出てきます。`(catch Exception _ nil)` と広く捕まえると、あとで `read-json` に別の不具合を入れたときに気づけなくなるので、狭く捕まえます。

```clojure
(testing "JSON として読めないか型が合わなければ nil を返す"
  (doseq [body ["{\"sns1\": \"たくさん\"}" "{\"original\": 1.5}" "{ここは JSON ではない" "" "[1, 2]"]]
    (is (nil? (v/read-json body v/movie-types)) body)))
```

`""`（空の本文）と `[1, 2]`（JSON としては正しい配列）も弾きます。`(json/parse-string "")` は例外を投げずに `nil` を返すので、`map?` の確認が効いています。

### 理由を集めて、無ければ値を作る

検証の規則は Java 版の `Checks` と同じ形です。規則は「理由の文字列か、問題なしの `nil`」を返します。

```clojure
(defn required
  "必須の列が空なら理由を返す。"
  [field value]
  (when (nil? value) (str field " は必須です")))

(defn not-negative
  "負の数なら理由を返す。"
  [field value]
  (when (and (some? value) (neg? value)) (str field " は 0 以上にしてください")))

(defn one-of
  "選択肢の外の値なら理由を返す。選択肢は並べ替えて表示する。"
  [field value allowed]
  (when (and (some? value) (not (contains? (set allowed) value)))
    (str field " は " (str/join "、" (map str (sort allowed))) " のどれかにしてください")))
```

`when` は条件が偽なら `nil` を返すので、「問題なしは `nil`」がそのまま書けます。Java 版が `return null;` を明示したところです。

結果はマップで表します。

```clojure
(defn validate
  "理由（nil は問題なし）を集め、1 つも無ければ f で値を作る。
   Java 版の sealed interface Validated と違い、値と理由を両方持つマップで表す。"
  [reasons f]
  (let [errors (vec (remove nil? reasons))]
    {:value (when (empty? errors) (f)) :errors errors}))
```

Java 版は `sealed interface Validated<T>` に `Valid` と `Invalid` の 2 つのレコードを置き、`switch` の枝で値を取り出せないことをコンパイラが保証しました。Clojure には代数的データ型が無いので、Ruby 版と同じく **値と理由の一覧を両方持つマップ** にして、`valid?` で見分けます。不正な要求の `:value` が `nil` であることは、テストで固定するしかありません。

```clojure
(testing "不正な要求には値が無い"
  (is (nil? (:value (v/movie {})))))
```

`f` を関数で受け取るのは、**理由があるときに値を作らせないため** です。`(when (empty? errors) (f))` の位置で初めて呼ばれるので、`(double sns1)` が `nil` に対して走ることはありません。Java 版の `Supplier<T>` と同じ狙いで、Clojure では `#(...)` を渡すだけです。

```clojure
(defn movie
  "検証して、正しければ映画の特徴量にする。"
  [request]
  (let [{:strs [sns1 sns2 actor original]} request]
    (validate [(required "sns1" sns1)
               (required "sns2" sns2)
               (required "actor" actor)
               (required "original" original)
               (not-negative "sns1" sns1)
               (not-negative "sns2" sns2)
               (not-negative "actor" actor)
               (one-of "original" original original-values)]
              #(hash-map :sns1 (double sns1) :sns2 (double sns2)
                         :actor (double actor) :original (long original)))))
```

`{:strs [sns1 sns2 actor original]}` は **文字列のキーの分配束縛** です。JSON から読んだマップのキーは文字列なので、`:keys` ではなく `:strs` を使います。`(get request "sns1")` を 4 回書く代わりになりました。

ただし `sib_sp` だけは分配束縛が使えません。`:strs` はキーの名前をそのまま局所変数の名前にするので、`sib_sp` というアンダースコアを含む名前（Clojure ではダッシュを使う）になってしまいます。ここだけ `get` で読みます。

```clojure
(let [{:strs [pclass sex age parch fare embarked]} request
      sib-sp (get request "sib_sp")]
```

JSON の名前（`sib_sp`）とドメインの名前（`:sib-sp`）が違うのを、境界の 1 か所に閉じ込めた形です。Java 版が `@JsonProperty("sib_sp")` を書いた場所にあたります。

## 15.5 置き場の約束をテストで書く

### 約束のテストを本物と偽物の両方に走らせる

`defprotocol` が保証しない取り決めを、テストとして書きます。Ruby 版が「テストのモジュール」にしたところを、Clojure では **`is` を並べたただの関数** にします。

```clojure
(defn check
  "置き場の約束を確かめる。with はモデルを 2 つ読み込める置き場、without はどちらも無い置き場。"
  [with without]
  (testing "約束: モデルがあれば予測する関数を返す"
    (is (satisfies? domain/ModelStore with))
    (is (number? ((domain/load-sales-model with) movie)))
    (is (contains? #{true false} ((domain/load-survival-model with) passenger))))
  (testing "約束: モデルが無ければ ModelNotFound を投げる"
    (doseq [[load model] [[domain/load-sales-model domain/sales-model]
                          [domain/load-survival-model domain/survival-model]]]
      (let [thrown (try (load without) (catch clojure.lang.ExceptionInfo e e))]
        (is (instance? clojure.lang.ExceptionInfo thrown) model)
        (is (domain/model-not-found? thrown) model)
        (is (= model (:model (ex-data thrown))))))))
```

`clojure.test` の `is` は `deftest` の外でも動きます。走っているテストに結果を報告するだけなので、**約束を関数にまとめて、複数の `deftest` から呼べます**。Minitest のモジュールの `include` や JUnit の `@Nested` のような仕組みは要りません。

本物の置き場のテストから呼び、

```clojure
(deftest ファイルの置き場は置き場の約束を満たす
  (contract/check (store-with-models) (store-without-models)))
```

偽物のテストからも呼びます。

```clojure
(deftest 偽物の置き場も置き場の約束を満たす
  (contract/check (fakes/fake-store {:sales true :survival true})
                  (fakes/fake-store {:sales false :survival false})))
```

これで、偽物が例外の代わりに `nil` を返すように変わると、約束のテストが落ちます。

`(is (satisfies? domain/ModelStore with))` の 1 行は、Clojure でしか書けない確認です。`satisfies?` は「この値がこの protocol を実装しているか」を実行時に答えます。Rust の `impl ModelStore for StubStore` をコンパイラが検査するのと同じことを、テストの実行で確かめる形です。

### 偽物は reify で書く

```clojure
(defn fake-store
  "モデルがあるかどうかを差し替えられる置き場。reify で約束をその場で満たす。"
  [{:keys [sales survival]}]
  (reify domain/ModelStore
    (load-sales-model [_]
      (if sales
        (fn [_movie] fixed-sales)
        (throw (domain/model-not-found domain/sales-model))))
    (load-survival-model [_]
      (if survival
        (fn [_passenger] true)
        (throw (domain/model-not-found domain/survival-model))))))
```

`reify` は、その場で protocol を実装した無名のオブジェクトを作ります。クラスもレコードも定義しません。Java 版が `class StubModelStore implements ModelStore { … }` と書いたものが、関数の中の式になります。

**メソッドの名前を間違えるとコンパイル時に落ちる** のが `reify` の効きどころです。`load-sale-model` と書けば `Can't define method not in interfaces` で止まります。Ruby 版が「偽物のメソッド名を 1 文字間違えても実行時まで気づかない」と書いた部分は、Clojure では protocol のおかげで先に分かります。

偽物は `test/getting_started_ml/chapter15/fakes.clj` に置きました。名前が `-test` で終わらないので、test-runner はテストとしては走らせず、使うテストが `:require` で読み込みます。

## 15.6 ドメインとサービス

### モデルが無いことを ex-info で表す

Java 版は `class ModelNotFoundException extends Exception` を作りました。Clojure では **例外のクラスを作らず、`ex-info` にデータを載せます**。

```clojure
(defn model-not-found
  "学習済みモデルが無いことを表す例外。メッセージにファイルのパスを含めない。"
  [model]
  (ex-info (str "学習済みモデル " model " が見つかりません")
           {:type ::model-not-found :model model}))

(defn model-not-found?
  "例外が「モデルが無い」かどうかを返す。API はこれを 503 に変える。"
  [e]
  (= ::model-not-found (:type (ex-data e))))
```

`ex-info` が作るのは `clojure.lang.ExceptionInfo` という 1 つのクラスで、種類はデータ（`ex-data`）で区別します。`::model-not-found` は **名前空間つきのキーワード** で、展開すると `:getting-started-ml.chapter15.domain/model-not-found` になります。ほかの名前空間が同じ名前を使っても衝突しません。

`catch` の節では種類を選べない（どれも `ExceptionInfo` なので）ので、捕まえてから `model-not-found?` で振り分けます。Java の例外の階層に比べると手数が 1 つ増えますが、そのかわり **例外の種類を増やすのにクラスを増やさずに済みます**。

メッセージにファイルのパスを入れないのは Java 版と同じ判断です。メッセージは 503 の応答としてそのまま外に出るので、サーバーの中の事情を漏らしません。

### 既存のモデルをアダプターで約束に合わせる

第 7・8 章のモデルには手を入れません。約束（`映画 → 数値`・`乗客 → 真偽値`）に合わせる関数を書くだけです。

```clojure
(defn linear-sales-model
  "第 7 章の線形回帰のモデルを、興行収入のモデルの約束（映画 → 数値）に合わせる。"
  [model]
  (fn [movie]
    (chapter07/predict-one model {:SNS1 (:sns1 movie)
                                  :SNS2 (:sns2 movie)
                                  :actor (:actor movie)
                                  :original (:original movie)})))
```

Java 版の `record LinearSalesModel(LinearModel model) implements SalesModel` に当たるものが、**関数を返す関数** になりました。`record` の宣言も `implements` も要りません。第 7 章の `predict-one` は列名をキーにしたマップを受け取るので、API の名前（`sns1`）から CSV の列名（`:SNS1`）への読み替えを、この 1 か所で行います。

生存予測のほうは、乗客を「CSV と同じセルの文字列の行」に戻します。

```clojure
(defn- passenger-row
  "乗客を、第 8 章のパイプラインが読む CSV と同じセルの文字列の行にする。
   分からない値は空欄にすると、学習のときに求めた中央値・最頻値で補完される。"
  [{:keys [pclass sex age sib-sp parch fare embarked]}]
  {:Pclass (str pclass)
   :Sex sex
   :Age (if age (str age) "")
   :SibSp (str sib-sp)
   :Parch (str parch)
   :Fare (str fare)
   :Embarked (or embarked "")})
```

年齢と乗船港が分からないときに空文字列を入れるのが要点です。第 8 章のパイプラインは欠損値として扱い、**訓練データから求めた中央値・最頻値で補完します**。前処理をモデルと一緒に保存しておくと、予測のときも学習と同じ手順が適用されるということが、ここで効いてきます。

### サービスは HTTP を知らない

```clojure
(defn predict-sales
  "映画の特徴量から興行収入を予測する。"
  [store movie]
  ((domain/load-sales-model store) movie))
```

置き場からモデル（＝関数）を読み込み、すぐ呼びます。括弧が二重になるのは、「関数を返す関数」を扱っているからです。

ヘルスチェックは、読み込めるかどうかだけを返します。

```clojure
(defn- ready?
  "モデルを読み込めるかどうか。読み込めない理由がほかにあれば、そのまま投げる。"
  [load store]
  (try
    (load store)
    true
    (catch clojure.lang.ExceptionInfo e
      (if (domain/model-not-found? e) false (throw e)))))

(defn health
  "モデルごとに、読み込めるかどうかを返す。
   マップは 9 要素以上で順を保たないが、ここは 2 つなので array-map で並びを固定できる。"
  [store]
  (array-map domain/sales-model (ready? domain/load-sales-model store)
             domain/survival-model (ready? domain/load-survival-model store)))
```

`(catch Exception _ false)` と書かないのが大事なところです。ファイルの権限の問題や壊れた EDN も「モデルがありません」と表示されてしまい、原因を探せなくなります。`model-not-found?` でなければそのまま投げ、API が 500 にします。

`array-map` は **並びを保つマップ** です。この章で何度か触れてきたとおり、Clojure のリテラルのマップは要素が 9 個以上になるとハッシュマップになり、並びが崩れます。`array-map` を使えば小さなマップの並びは保たれますが、`assoc` を重ねて 9 個を超えるとやはり崩れるので、「小さくて、これ以上増やさない」ことが分かっている場所にだけ使います。ここは 2 つなので安全です。

並びはテストで固定しました。JSON の `models` の並びが `cinema`・`survived` になるかどうかは、これで決まります。

```clojure
(deftest ヘルスチェックの並びはモデルの順のまま
  (is (= ["cinema" "survived"]
         (keys (service/health (fakes/fake-store {:sales true :survival true}))))))
```

このサービスは状態を持ちません。予測のたびに置き場からモデルを読むので、同時に複数の要求が来ても困りません。Rust 版が `+ Send + Sync` を型で要求した部分を、Clojure では「そもそも書き換えるものが無い」ことで済ませています。

## 15.7 モデルを EDN で保存する

第 8 章で、学習済みのパイプラインが `pr-str` と `clojure.edn/read-string` の往復で戻ることを確かめてあります。第 7 章の線形回帰のモデルも `{:intercept 6114.6 :coefficients [[:SNS1 1.38] …]}` というマップとベクタなので、同じやり方で保存できます。

```clojure
(defrecord FileModelStore [model-dir]
  domain/ModelStore
  (load-sales-model [_]
    (domain/linear-sales-model
     (read-model model-dir domain/sales-model #(edn/read-string (slurp %)))))
  (load-survival-model [_]
    (domain/pipeline-survival-model
     (read-model model-dir domain/survival-model chapter08/load-model))))
```

`defrecord` は protocol を実装できるマップです。`(:model-dir store)` でフィールドを読めるので、保存の関数はレコードの外に普通の関数として置けます。

```clojure
(defn save-sales-model
  "第 7 章の線形回帰のモデルを EDN で保存する。"
  [store model]
  (let [file (model-file (:model-dir store) domain/sales-model)]
    (io/make-parents file)
    (spit file (pr-str model))))
```

**保存は protocol に入れませんでした。** 読み込みは API が使いますが、保存は学習のときにしか使いません。API から見える約束を小さく保つほうが、偽物を書くのも楽になります（Java 版の `FileModelStore` が `saveSalesModel` を `ModelStore` の外に置いたのと同じ判断です）。

ファイルが無いことは、読む前に確かめて例外に変えます。

```clojure
(defn- read-model
  "ファイルを読む。無ければ「モデルが無い」に変える。"
  [model-dir model read-fn]
  (let [file (model-file model-dir model)]
    (when-not (.exists (io/file file))
      (throw (domain/model-not-found model)))
    (read-fn file)))
```

Ruby 版は `Errno::ENOENT` を捕まえましたが、Clojure では `slurp` が投げるのも第 8 章の `load-model` が投げるのも `FileNotFoundException` で、しかも `chapter08/load-model` は形式が違えば `IllegalArgumentException` も投げます。捕まえて振り分けるより、**先に `.exists` で確かめるほうが読みやすい** と判断しました。ファイルが消えるのと読むのが同時に起きる競合はありますが、そのときは 500 になるだけです。

保存と読み込みは、手で作った小さなモデルでテストします。切片 10、係数 1・2・3・4 のモデルを保存して、予測値を手で計算した値と比べました。

```clojure
(deftest 保存した線形回帰で予測できる
  ;; 10 + 1×200 + 2×500 + 3×3000 + 4×1
  (is (< (abs (- 10214.0 ((domain/load-sales-model (store-with-models)) contract/movie))) 1e-9)))
```

生存予測のほうは、「女性が生存し、男性が死亡する」4 件の作り物のデータでパイプラインを学習します。実データも `gulp data:setup` も要らないので、**この章のテストは学習データが無くても走ります**。

```clojure
(def ^:private survival-rows
  "女性が生存し、男性が死亡する 4 件の作り物のデータ。"
  [{:Pclass "1" :Sex "female" :Age "30" :SibSp "0" :Parch "0" :Fare "80" :Embarked "C"}
   {:Pclass "3" :Sex "male" :Age "40" :SibSp "0" :Parch "0" :Fare "8" :Embarked "S"}
   {:Pclass "2" :Sex "female" :Age "20" :SibSp "1" :Parch "0" :Fare "30" :Embarked "S"}
   {:Pclass "3" :Sex "male" :Age "25" :SibSp "0" :Parch "0" :Fare "10" :Embarked "S"}])
```

## 15.8 Ring のハンドラーは関数

### 要求も応答もマップ

Ring の約束は短く書けます。

- ハンドラーは 1 引数の関数
- 引数は要求のマップ（`:request-method`・`:uri`・`:body`・`:headers` …）
- 戻り値は応答のマップ（`:status`・`:headers`・`:body`）

これだけです。Javalin の `Context`、Sinatra の `request`／`response` に当たるものは、**素のマップ** です。

```clojure
(defn- json-response
  "JSON の応答のマップを作る。"
  ([status body] (json-response status body {}))
  ([status body headers]
   {:status status
    :headers (assoc headers "Content-Type" "application/json; charset=utf-8")
    :body (json/generate-string body)}))
```

### 経路の振り分けは cond で書く

```clojure
(defn handler
  "置き場を使う Ring のハンドラーを作る。返るのは要求のマップを受け取る関数。"
  [store]
  (fn [request]
    (let [method (:request-method request)
          uri (:uri request)]
      (cond
        (= [:get "/health"] [method uri])
        (health store)

        (= [:post "/cinema/sales"] [method uri])
        (predict request validation/movie-types validation/movie
                 #(hash-map :sales (service/predict-sales store %)))

        (= [:post "/survived"] [method uri])
        (predict request validation/passenger-types validation/passenger
                 #(hash-map :survived (service/predict-survival store %)))

        (contains? allowed-methods uri)
        (json-response 405 {:detail "許していないメソッドです"}
                       {"Allow" (get allowed-methods uri)})

        :else
        (json-response 404 {:detail "見つかりません"})))))
```

メソッドとパスの組をベクタにして `=` で比べています。Clojure のベクタは値なので、`[:get "/health"]` がそのまま照合の対象になります。パスが 3 つしかないうちは、これで十分読めます。

reitit や compojure を入れれば、パスの変数（`/cinema/:id`）やミドルウェアの合成が楽になります。この章はそこまで要らないので入れませんでした（[ADR 011](../../../adr/011-clojure-ml-libraries.md) の代替案の表）。**Ring のハンドラーが関数であることを、包まずに見せたい** というのがこの章の意図です。

405 のために、パスごとに許しているメソッドの表を持ちます。

```clojure
(def ^:private allowed-methods
  "パスごとに許しているメソッド。知っているパスに違うメソッドが来たら 405 にする。"
  {"/health" "GET" "/cinema/sales" "POST" "/survived" "POST"})
```

Ruby 版の Sinatra が「ルートが無ければ一律 404」だったのと同じ事情で、経路の振り分けを自分で書くなら 405 も自分で書きます。ルートを足したら表も直す必要がある点は、二重に書いている負担です。ここは経路の数が少ないうちに限った割り切りで、増えるならライブラリを入れる判断に変わります。

### 例外をステータスコードに変える

```clojure
(defn- predict
  "本文を読んで検証し、正しければ f で予測する。失敗はステータスコードに変える。"
  [request types validate-request f]
  (try
    (let [parsed (validation/read-json (slurp (:body request)) types)]
      (if (nil? parsed)
        (rejected [invalid-json])
        (let [validated (validate-request parsed)]
          (if (validation/valid? validated)
            (json-response 200 (f (:value validated)))
            (rejected (:errors validated))))))
    (catch clojure.lang.ExceptionInfo e
      (if (domain/model-not-found? e)
        (json-response 503 {:detail (ex-message e)})
        (json-response 500 {:detail "予測できませんでした"})))
    ;; 例外のメッセージには内部の事情が入るので、応答には出さない
    (catch Exception _ (json-response 500 {:detail "予測できませんでした"}))))
```

`catch` の節を 2 つ書いているのは、15.6 節で見たとおり **`ex-info` の例外が全部同じクラス** だからです。`ExceptionInfo` を先に捕まえて `model-not-found?` で振り分け、それ以外の `Exception` を 500 にします。順序が逆だと `Exception` が先に捕まえ、モデルが無いのに 500 になります（`ExceptionInfo` は `RuntimeException` の下なので、`catch` の順は Java と同じく上から照合されます）。

500 のときに例外のメッセージを返さないことは、テストで固定しました。

```clojure
(deftest 予測が失敗すれば五百を返す
  ;; 置き場が約束と違う例外を投げる。応答には内部の事情を出さない
  (let [broken (reify domain/ModelStore
                 (load-sales-model [_] (throw (RuntimeException. "内部の秘密")))
                 (load-survival-model [_] (throw (RuntimeException. "内部の秘密"))))
        response ((api/handler broken)
                  (request :post "/cinema/sales"
                           "{\"sns1\": 200, \"sns2\": 500, \"actor\": 3000, \"original\": 1}"))]
    (is (= 500 (:status response)))
    (is (= "{\"detail\":\"予測できませんでした\"}" (:body response)))))
```

約束を破る置き場を、テストの中で `reify` で 3 行作れます。Java 版なら `class BrokenStore implements ModelStore` を別に書くところです。

### サーバーを起動しない統合テスト

ハンドラーは関数なので、テストは呼ぶだけです。

```clojure
(defn- request
  "Ring の要求のマップを作る。本文は入力ストリームで渡す（Jetty が渡すものと同じ形）。"
  ([method uri] {:request-method method :uri uri})
  ([method uri body]
   (assoc (request method uri)
          :body (java.io.ByteArrayInputStream. (.getBytes ^String body "UTF-8")))))

(defn- call
  "偽物の置き場を使うハンドラーを、1 回の要求で呼ぶ。"
  [store-options request-map]
  ((api/handler (fakes/fake-store store-options)) request-map))
```

`ring/ring-mock` という要求のマップを作るライブラリもありますが、**この章では入れませんでした**。作るのは 2 種類のマップだけで、しかも使わないほうが「要求はただのマップである」ことがテストの中に見えるからです。

`:body` を入力ストリームにしているのは、Jetty のアダプターが渡すのがストリームだからです。ここを文字列にしてしまうと、テストは通るのに本番で `slurp` の結果が変わる、ということになりかねません（`slurp` は文字列も読めてしまうので気づきにくい）。**偽物は本物と同じ形にする** という原則を、`:body` にも当てはめました。

応答も素のマップなので、そのまま比べられます。

```clojure
(deftest ヘルスチェック
  (testing "すべて読み込めれば ok を返す"
    (let [response (call ready (request :get "/health"))]
      (is (= 200 (:status response)))
      (is (= "application/json; charset=utf-8" (get-in response [:headers "Content-Type"])))
      (is (= "{\"status\":\"ok\",\"models\":{\"cinema\":true,\"survived\":true}}" (:body response)))))
  (testing "読み込めないモデルがあれば degraded を返す"
    (is (= "{\"status\":\"degraded\",\"models\":{\"cinema\":true,\"survived\":false}}"
           (:body (call {:sales true :survival false} (request :get "/health")))))))
```

Ruby 版の rack-test は「1 つのテストの中で `app` を 1 度しか作らない」という癖があり、置き場を替えるにはテストを分ける必要がありました。Clojure では `(api/handler store)` を呼ぶたびに新しい関数ができるので、1 つの `deftest` の中で置き場を替えられます。**ハンドラーが関数であることが、そのままテストの書きやすさになっています。**

## 15.9 実データで学習したモデルで動かす

### 学習と保存

第 7・8 章と同じ条件（`test-size` 0.2、シード 0、深さ 5、`balanced` の重み）で学習します。

```clojure
(defn train-and-save-models
  "第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。"
  [data-dir store]
  (let [cinema (chapter07/prepare-cinema (str data-dir "/cinema.csv") test-size seed)]
    (store/save-sales-model store (chapter07/fit (:x-train cinema) (:t-train cinema)
                                                 chapter07/feature-columns)))
  (store/save-survival-model store (survival-pipeline (str data-dir "/Survived.csv"))))
```

### 統合テスト

実データで学習したモデルをつなぐテストは、既存の章と同じく、データが無ければ早く戻ります。期待値は、最初に別の言語版の値を書いてテストを落とし、実際に測った値に直しました。

```text
FAIL in (実データで学習したモデルを保存して予測できる) (chapter15_test.clj:28)
expected: (< (abs (- 7615.295978481879 (service/predict-sales store contract/movie))) 1.0E-6)
  actual: (not (< 23.21973959377283 1.0E-6))
```

```clojure
(deftest 実データで学習したモデルを保存して予測できる
  (when-let [store (trained-store)]
    ;; Java 版・Scala 版と同じ分割・同じ手順なので、予測値も一致する
    (is (< (abs (- 7730.457421687023 (service/predict-sales store contract/movie))) 1e-6))
    (is (true? (service/predict-survival store contract/passenger)))))
```

**7730.457421687023 は、Java 版・Scala 版の第 15 章と完全に一致しました。** 第 7 章で切片・係数まで一致していたので当然の帰結ですが、保存と読み込みを挟んでも桁が落ちないことの確認になります。EDN は `Double` を `pr-str` で書くときに **読み戻すと同じ値になる表記** を選ぶので、往復で情報が減りません。

Ruby 版（7272.90…）・Kotlin 版（7830.41…）・Python 版と値が違うのは、`java.util.Random` の乱数列とシャッフルの手順が言語版ごとに違い、訓練データに入る行が違うからです。

### Jetty で待ち受ける

```clojure
(defn run
  "モデルを学習して保存し、予測 API を起動する。Ctrl-C で止まるまで戻らない。"
  ([] (run model-dir))
  ([model-dir]
   (let [store (store/file-model-store model-dir)]
     (train-and-save-models (dataset/dir) store)
     (doseq [[model ready] (service/health store)]
       (println (str "モデル " model ": " ready)))
     (println (str "http://" host ":" port " で待ち受けます"))
     (flush)
     (jetty/run-jetty (api/handler store) {:host host :port port :join? true}))))
```

`run-jetty` に渡すのは **ハンドラーの関数と設定のマップだけ** です。`:join? true` で、サーバーが止まるまでこの呼び出しから戻りません。`:host "127.0.0.1"` にして、自分のマシンからだけ接続できるようにしています。

`(flush)` は動かしてみてから足しました。出力をファイルにリダイレクトして起動すると標準出力がバッファされ、サーバーを止めるまで起動のメッセージが出てきません。待ち受けに入る前に書き出します（Ruby 版の `out.flush` と同じ対処です）。

実行するとモデルを学習・保存してから待ち受けます（学習データの置き場は環境変数 `ML_DATA_DIR` で指定できます）。

```text
$ clojure -M:run chapter15
SLF4J: No SLF4J providers were found.
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See https://www.slf4j.org/codes.html#noProviders for further details.
モデル cinema: true
モデル survived: true
http://127.0.0.1:8015 で待ち受けます
```

**SLF4J の 3 行は Jetty が出しています。** Jetty はログの出力先に SLF4J を使いますが、実装（Logback など）が入っていないので「何も出さない実装を使う」という警告になります。この章ではサーバーのログが要らないので、依存を増やさずそのままにしました。ログを見たい運用に移すときは、`ch.qos.logback/logback-classic` を足す場面です。

別の端末から呼びます。

```text
$ curl -s http://127.0.0.1:8015/health
{"status":"ok","models":{"cinema":true,"survived":true}}

$ curl -s -X POST http://127.0.0.1:8015/cinema/sales \
    -H "Content-Type: application/json" \
    -d '{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}'
{"sales":7730.457421687023}

$ curl -s -X POST http://127.0.0.1:8015/survived \
    -H "Content-Type: application/json" \
    -d '{"pclass": 1, "sex": "female", "sib_sp": 0, "parch": 0, "fare": 50.0}'
{"survived":true}

$ curl -s -X POST http://127.0.0.1:8015/survived \
    -H "Content-Type: application/json" \
    -d '{"pclass": 3, "sex": "male", "sib_sp": 0, "parch": 0, "fare": 8.0, "embarked": "S"}'
{"survived":false}
```

1 等客室の女性は生存、3 等客室の男性は死亡という予測です。**どちらも年齢を送っていません。** 空欄のまま第 8 章のパイプラインに渡り、訓練データから求めた中央値で補完されています。

不正な入力は 422 です。

```text
$ curl -s -X POST http://127.0.0.1:8015/cinema/sales \
    -H "Content-Type: application/json" \
    -d '{"sns1": -1, "sns2": 2000, "actor": 300, "original": 2}'
{"detail":["sns1 は 0 以上にしてください","original は 0、1 のどれかにしてください"]}

$ curl -s -X POST http://127.0.0.1:8015/cinema/sales \
    -H "Content-Type: application/json" \
    -d '{"sns1": "たくさん"}'
{"detail":["JSON の形式または値の型が正しくありません"]}
```

知らないパスは 404、許していないメソッドは 405 です。

```text
$ curl -s -i http://127.0.0.1:8015/unknown | head -5
HTTP/1.1 404 Not Found
Date: Wed, 23 Sep 2026 01:37:45 GMT
Content-Type: application/json;charset=utf-8
Transfer-Encoding: chunked
Server: Jetty(11.0.21)

$ curl -s -i http://127.0.0.1:8015/cinema/sales | head -8
HTTP/1.1 405 Method Not Allowed
Date: Wed, 23 Sep 2026 01:37:45 GMT
Allow: POST
Content-Type: application/json;charset=utf-8
Transfer-Encoding: chunked
Server: Jetty(11.0.21)

{"detail":"許していないメソッドです"}
```

ここで 1 つ気づくことがあります。ハンドラーが返したのは `"application/json; charset=utf-8"`（セミコロンの後に空白）でしたが、**実際に届いたのは `application/json;charset=utf-8`（空白なし）** です。Jetty が `Content-Type` を解釈して組み立て直しています。統合テストでハンドラーの戻り値を見ているだけでは分からない差で、`curl -i` で初めて分かりました。

意味は同じなのでそのままにしましたが、**「ハンドラーの応答」と「クライアントに届く応答」は同じではない** ことは、関数として直接呼ぶテストの限界として覚えておく価値があります。ヘッダーの正規化・`Transfer-Encoding`・接続の扱いは、サーバーが決めます。

## 15.10 品質チェック

```bash
cljfmt check src test
clj-kondo --lint src test --fail-level warning
clojure -M:test
clojure -M:coverage
```

学習データがある状態では 134 tests・339 assertions・0 failures、学習データを外すと 134 tests・278 assertions・0 failures でした。

| 学習データ | tests | assertions | failures |
|-----------|-------|-----------|---------|
| あり | 134 | 339 | 0 |
| なし | 134 | 278 | 0 |

この章の名前空間ごとのカバレッジ（学習データあり）は次のとおりです。

| 名前空間 | % Forms | % Lines |
|---------|---------|---------|
| `getting-started-ml.chapter15` | 47.17 | 71.43 |
| `getting-started-ml.chapter15.api` | 97.12 | 97.78 |
| `getting-started-ml.chapter15.domain` | 96.00 | 100.00 |
| `getting-started-ml.chapter15.service` | 94.87 | 100.00 |
| `getting-started-ml.chapter15.store` | 100.00 | 100.00 |
| `getting-started-ml.chapter15.validation` | 96.76 | 100.00 |

層の名前空間はどれも行で 97% 以上です。低いのは組み立ての `chapter15.clj` で、**サーバーを起動する `run` をテストしていない** からです。ここは `curl` で確かめました。Ruby 版でも同じ形（層は 100%、起動の関数だけ低い）になっていて、層を分けた結果として読めます。

clj-kondo には 1 つも指摘されませんでした（`errors: 0, warnings: 0`）。第 5 章で環境定義に足した道具が、最後の章まで同じ基準で効いています。

## 15.11 まとめ

この章では、第 7・8 章のモデルを Ring の HTTP API にしました。Clojure に固有の論点は次のとおりです。

1. **ハンドラーは関数、要求も応答もマップ** — フレームワークの型を覚えずに済み、統合テストは関数を呼ぶだけ。`ring-mock` すら要らない。そのかわり、Jetty がヘッダーを組み立て直すような「サーバーが決めること」は関数のテストでは見えないので、`curl -i` で確かめる
2. **約束は defprotocol と約束のテストの二段構え** — protocol はメソッドの名前と引数の数を守り、`reify` の偽物は名前を間違えればコンパイル時に落ちる。「無ければ例外を投げる」は型では書けないので、本物と偽物の両方に走らせる関数（`store-contract/check`）で守る。`clojure.test` の `is` が `deftest` の外でも動くので、約束はただの関数でよい
3. **操作が 1 つなら関数、複数なら protocol** — モデル（予測 1 つ）は関数、置き場（2 つの操作）は protocol にした。Java 版の `@FunctionalInterface` 2 つ・`interface` 1 つが、関数 2 つ・protocol 1 つになる
4. **例外の種類はクラスではなくデータ** — `ex-info` に `::model-not-found` を載せ、`catch` で捕まえてから振り分ける。クラスを増やさずに種類を増やせるが、`catch` の節では選べないので手数が 1 つ増える
5. **EDN なら既存の章に手を入れずに保存できる** — 第 7 章のモデルも第 8 章のパイプラインもマップとベクタなので、注釈も derive も要らない。`Double` の往復で桁も落ちず、予測値は Java 版・Scala 版と完全に一致した
6. **名前空間の循環はコンパイル時に落ちる** — 層の依存の向き（api → service → domain ← store）は、規約ではなく処理系が守っている

**TODO リスト（この章の完了時点）**:

- [x] 要求の JSON を読み、型が合わなければ弾く
- [x] 必須・0 以上・選択肢の検証をし、理由を並べて返す
- [x] 映画・乗客の特徴量をマップで表す
- [x] 置き場の約束を `defprotocol` と約束のテストで表す
- [x] 第 7 章の線形回帰と第 8 章のパイプラインを EDN で保存・読み込みする
- [x] ファイルが無ければ「モデルが無い」の例外を投げる
- [x] サービスが置き場からモデルを読んで予測し、ヘルスチェックを返す
- [x] `POST /cinema/sales`・`POST /survived` が予測を JSON で返す
- [x] 入力が不正なら 422、モデルが無ければ 503、それ以外は 500
- [x] 知らないパスは 404、許していないメソッドは 405
- [x] 第 7・8 章と同じ条件で学習し、API を起動する（`clojure -M:run chapter15`）

これで Clojure 版は、第 1 章の「20 代ならきのこ派」という手書きのルールから、学習したモデルを HTTP で届けるところまでたどり着きました。最後まで支えになったのは、**値がただのマップとベクタである** ことです。表も、モデルも、学習済みのパイプラインも、要求も応答も、同じ道具で作り、`=` で比べ、`pr-str` で保存できました。型を宣言しない言語で安全網になったのはテストですが、その安全網をここまで安く張れたのは、比べるものがどれも値だったからです。
