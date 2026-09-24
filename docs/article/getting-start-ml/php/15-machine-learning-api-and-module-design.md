---
type: Article
title: "第 15 章: 機械学習 API とモジュール設計"
description: "第 7・8 章の学習済みモデルを、PHP の組み込みサーバーと素のハンドラーで予測 API として公開し、クラスで層を分ける。ハンドラーを「Request を受け取って Response を返すメソッド」にしてサーバーを起動しない統合テストを書き、置き場の約束を interface と約束のテストの両方で守る。"
tags: [article,getting-start-ml,php]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 15 章: 機械学習 API とモジュール設計

## 15.1 はじめに

シリーズの最後の章です。ここまでに作ったモデルは、テストと `php -r` の中でしか動きませんでした。この章では、第 7 章の線形回帰（映画の興行収入）と第 8 章のパイプライン（乗客の生存）を **HTTP の予測 API** として公開します。

題材は [Java 版の第 15 章](../java/15-machine-learning-api-and-module-design.md)・[Clojure 版](../clojure/15-machine-learning-api-and-module-design.md)・[Elixir 版](../elixir/15-machine-learning-api-and-module-design.md) と同じで、3 つのエンドポイントを作ります。

| メソッド | パス | 役割 |
|---------|------|------|
| `GET` | `/health` | モデルを読み込めるかどうかを返す |
| `POST` | `/cinema/sales` | 映画の特徴量から興行収入を予測する |
| `POST` | `/survived` | 乗客の特徴量から生存を予測する |

PHP 版では **Laravel も Symfony も使いません**（[ADR 013](../../../adr/013-php-ml-libraries.md)）。標準の組み込みサーバー（`php -S`）と、素のクラスで書いたハンドラーだけを使います。JSON も `json_encode`／`json_decode` で、依存はひとつも足していません。この章で見せたいのは層の分け方であって、フレームワークの作法ではないからです。

この章で PHP らしいのは次の 4 点です。

1. **約束は interface で書ける** — Elixir 版は behaviour が「モジュールへの約束」なので、置き場を `{モジュール, 状態}` の組にする回り道が要りました。PHP の interface は **値（オブジェクト）への約束** なので、置き場はただのオブジェクトで済みます
2. **偽物は無名クラスでその場に書ける** — Elixir 版は偽物もモジュールになりましたが、PHP には無名クラスがあるので、Clojure の `reify` に近いことができます
3. **型で守れるものは、約束のテストに書かなくてよい** — 「float を返す」という約束は interface が守るので、テストに書くと PHPStan に「常に真である」と言われます。**静的解析がテストの重複を教えてくれました**
4. **組み込みサーバーはプログラムの中から起動しない** — Elixir の Bandit や Clojure の Jetty と違い、`php -S` そのものがサーバーで、要求ごとにスクリプトを最初から走らせます。プロセスに状態が残らないという前提が、そのまま設計に効きます

## 15.2 層を分ける

### クラスと依存の向き

`src/Chapter15/` に、層ごとのクラスを置きます。

| 層 | クラス | 役割 |
|----|--------|------|
| プレゼンテーション | `Api`・`Request`・`Response`・`Validation`・`ValidationException` | HTTP の要求を読み、検証し、応答を組み立てる |
| アプリケーション | `Service` | 置き場からモデルを読んで予測する。HTTP を知らない |
| ドメイン | `Movie`・`Passenger`・`Domain`・`SalesModel`・`SurvivalModel`・`ModelNotFoundException` | 予測の入力・出力と、モデルの約束 |
| インフラ | `FileStore`・`LinearSalesModel`・`PipelineSurvivalModel` | ファイルへの保存と、既存の章のモデルを約束に合わせるアダプター |
| 組み立て | `Chapter15`・`tools/chapter15-server.php` | 学習・保存と、組み込みサーバーからの入り口 |

依存は外から内へ一方向です。`Api` は `Service` を知り、`Service` は `ModelStore` という **interface** だけを知ります。`FileStore` がその interface を実装し、第 7・8 章のモデルを読み込みます。第 7 章の `LinearModel` も第 8 章の `FittedPipeline` も、この章のために 1 行も書き換えていません。**既存の章に手を入れずに済むのがアダプターの役目** です。

### 置き場の約束をどう表すか

```php
interface ModelStore
{
    /** 興行収入のモデルを読み込む。無ければ ModelNotFoundException を投げる。 */
    public function loadSalesModel(): SalesModel;

    /** 生存予測のモデルを読み込む。無ければ ModelNotFoundException を投げる。 */
    public function loadSurvivalModel(): SurvivalModel;
}
```

ここが **Elixir 版といちばん違うところ** です。Elixir で「約束」を表すのは behaviour で、behaviour は **モジュールに対する** 約束です。そのため Elixir 版では置き場そのものを `{モジュール, 状態}` の組で表し、組を受け取って `module.load_sales_model(state)` に振り分ける関数を別に書く必要がありました。

PHP の interface は **値に対する** 約束です。置き場は `new FileStore('model')` というただのオブジェクトで、状態（ディレクトリの名前）はそのオブジェクトが自分で持ちます。振り分けの関数は要りません。

```php
final readonly class FileStore implements ModelStore
{
    public function __construct(private string $modelDir)
    {
    }
    // ...
}
```

Elixir 版の `ModelStore` モジュールには、`@callback` の宣言に加えて、組を分解して呼ぶ関数を自分で書く必要がありました。PHP 版は宣言だけで足り、振り分けは言語がやります。Clojure 版の `defprotocol` に近い書き味です。

モデルそのものにも interface を作りました。

```php
interface SalesModel
{
    public function predict(Movie $movie): float;
}
```

Elixir 版は「モデルは特徴量を受け取って予測を返す関数でよい」として無名関数をそのまま渡していました。PHP にもクロージャはありますが、**クロージャの引数と戻り値の型は PHPDoc でしか書けず、実行時には効きません**。第 10 章で `Predictor` を interface にしたのと同じ理由で、ここも interface にしています。型が言語本体に入っている版では、約束を型で書くほうが自然です。

### interface が保証しないこと

interface が決めるのは **メソッドの名前と引数と戻り値の型** だけです。「読み込めなければ `ModelNotFoundException` を投げる」は書けません。PHP には検査例外がなく、`@throws` は PHPDoc であって PHPStan も既定では強制しないからです。

この取り決めは、15.5 節の **約束のテスト** で守ります。interface と約束のテストの二段構えになるところは、Elixir 版（behaviour ＋ 約束のテスト）とまったく同じ形になりました。**型の道具が違っても、型で書けることの限界はそう変わりません。**

## 15.3 TODO リストの作成

- [ ] 要求の JSON を読み、型が合わなければ弾く
- [ ] 必須・0 以上・選択肢の検証をし、理由を並べて返す
- [ ] 映画・乗客の特徴量をクラスで表す
- [ ] 置き場の約束を interface と約束のテストで表す
- [ ] 第 7 章の線形回帰と第 8 章のパイプラインをファイルに保存・読み込みする
- [ ] ファイルが無ければ `ModelNotFoundException` を投げる
- [ ] サービスが置き場からモデルを読んで予測し、ヘルスチェックを返す
- [ ] `POST /cinema/sales`・`POST /survived` が予測を JSON で返す
- [ ] 入力が不正なら 422、モデルが無ければ 503、それ以外は 500
- [ ] 知らないパスは 404、許していないメソッドは 405
- [ ] 第 7・8 章と同じ条件で学習し、組み込みサーバーで API を起動する

## 15.4 要求を読んで検証する

### json_decode は型を決めない

`json_decode($body, true)` は JSON の値をそのまま PHP の配列・数値・文字列にします。Java 版のように「読み込みと同時に型が確かめられる」ことはないので、型の確認は自分で書きます。

```php
public static function readJson(string $body, array $types): array
{
    try {
        $decoded = json_decode($body, true, 512, JSON_THROW_ON_ERROR);
    } catch (JsonException) {
        throw new ValidationException([self::INVALID_JSON]);
    }

    // 配列でなければオブジェクトではない。`{}` は空の配列になるので、そこだけ許す。
    if (!is_array($decoded) || (array_is_list($decoded) && $decoded !== [])) {
        throw new ValidationException([self::INVALID_JSON]);
    }

    foreach ($decoded as $field => $value) {
        if (!self::typed($types[$field] ?? null, $value)) {
            throw new ValidationException([self::INVALID_JSON]);
        }
    }

    return $decoded;
}
```

`JSON_THROW_ON_ERROR` を付けないと、`json_decode` は読めない本文に対して `null` を返します。**`null` は「読めなかった」とも「本文が `null` だった」とも読めてしまう** ので、例外にして区別しました。第 2 章で `(int) '高い'` が 0 を返して落ちなかったのと同じ形の落とし穴です。PHP の標準関数は「失敗を値で返す」ものが多く、この版では毎章それを例外に変えてきました。

`array_is_list($decoded)` の行は PHP 特有の用心です。**PHP には JSON のオブジェクトと配列を区別する型がありません。** `json_decode(..., true)` はどちらも配列にするので、`[1, 2]` が来ても素通りしてしまいます。`array_is_list` で「添字が 0 から連番か」を見て弾いています。

しかもこの判定には、[ADR 013](../../../adr/013-php-ml-libraries.md) に第 3 章の知見として書いた癖が絡みます。**PHP の配列は「数字だけの文字列」の鍵を整数に変える** ので、`{"0": 1}` という JSON は `[1]` という添字の配列になり、`array_is_list` が真になって弾かれます。JSON としては正しいオブジェクトですが、この API の要求としては意味がないので、弾いて構わないと判断しました。**言語の癖が仕様の端に顔を出すところ** です。

型の確認は `match (true)` で書きます。

```php
private static function typed(?string $type, mixed $value): bool
{
    return match (true) {
        $value === null, $type === null => true,
        $type === 'number' => is_int($value) || is_float($value),
        $type === 'integer' => is_int($value),
        default => is_string($value),
    };
}
```

`number` を `is_int($value) || is_float($value)` と書いているのは、**JSON の `200` が PHP では整数になる** からです。`is_float` だけでは `{"sns1": 200}` を弾いてしまいます。逆に `original` を `integer` にしているのは、`1.5` を弾きたいからです。Elixir 版と同じ分け方で、理由も同じでした。

### 理由を集めて、無ければ値を作る

検証の規則は「問題が無ければ `null`、あれば理由」を返す静的メソッドにします。

```php
public static function notNegative(string $field, mixed $value): ?string
{
    return (is_int($value) || is_float($value)) && $value < 0 ? "{$field} は 0 以上にしてください" : null;
}
```

集めた理由が 1 つでもあれば、例外を投げます。

```php
public static function check(array $reasons): void
{
    $errors = array_values(array_filter($reasons, static fn (?string $r): bool => $r !== null));

    if ($errors !== []) {
        throw new ValidationException($errors);
    }
}
```

**Elixir 版との分かれ道はここでした。** Elixir 版は「値と理由のマップ」を返し、理由があるときに値を作らせないために `build` を関数で受け取っていました。PHP には例外があるので、理由があれば投げてしまえば、その先は成功の場合しか書かずに済みます。

```php
public static function movie(array $request): Movie
{
    $sns1 = $request['sns1'] ?? null;
    // ...
    self::check([
        self::required('sns1', $sns1),
        // ...
        self::oneOf('original', $original, self::ORIGINAL_VALUES),
    ]);

    return new Movie(self::num($sns1), self::num($sns2), self::num($actor), self::int($original));
}
```

`array_filter` に `array_values` を重ねているのは、第 2 章で書いたとおり **`array_filter` が添字を保つ** からです。そのままでは `list<string>` になりません。PHPStan のレベル 9 は配列の形まで見るので、ここを忘れると指摘されます。

最後の `self::num($sns1)` は、`check` を通っていれば必ず成功します。それでも書いているのは PHPStan のためで、`$request['sns1'] ?? null` の型は `mixed` だからです。

```php
private static function num(mixed $value): float
{
    return is_int($value) || is_float($value) ? (float) $value : throw new ValidationException([self::INVALID_JSON]);
}
```

**PHP 8 では `throw` が式なので、三項演算子の右側に置けます。** 1 行に収まるので、通らない分岐がカバレッジの穴になりません。Elixir 版が `sns1 / 1` で整数を浮動小数点数に変えたところが、PHP では `(float)` のキャストです。

`$request['sns1'] ?? null` という読み方は、Elixir 版が `Map.merge` で既定値を埋めてから分配束縛した手当てに当たります。PHP の配列は無い鍵を読んでも警告が出るだけ（`??` を付ければそれも消える）なので、行数は増えませんでした。Clojure 版の `{:strs [sns1 …]}` と同じ手軽さです。

## 15.5 置き場の約束をテストで書く

### 約束のテストを本物と偽物の両方に走らせる

interface が保証しない取り決めを、テストとして書きます。

```php
final class StoreContract
{
    public static function check(ModelStore $withModels, ModelStore $withoutModels): void
    {
        // 約束: モデルがあれば予測するモデルを返し、同じ入力には同じ答えを返す。
        // 「float を返す」「bool を返す」は interface が型で約束しているので、
        // ここで書くと PHPStan に「常に真である」と言われる。型で守れるものは書かない。
        Assert::assertTrue(is_finite($withModels->loadSalesModel()->predict(self::movie())));
        Assert::assertSame(
            $withModels->loadSalesModel()->predict(self::movie()),
            $withModels->loadSalesModel()->predict(self::movie()),
        );
        // ...

        // 約束: モデルが無ければ ModelNotFoundException を投げる
        $loads = [
            Domain::SALES_MODEL => static fn (): mixed => $withoutModels->loadSalesModel(),
            Domain::SURVIVAL_MODEL => static fn (): mixed => $withoutModels->loadSurvivalModel(),
        ];

        foreach ($loads as $model => $load) {
            try {
                $load();
                Assert::fail("モデルが無いのに読み込めました: {$model}");
            } catch (ModelNotFoundException $e) {
                Assert::assertSame($model, $e->model);
                Assert::assertSame("学習済みモデル {$model} が見つかりません", $e->getMessage());
            }
        }
    }
}
```

**PHPUnit の `Assert` のメソッドは static なので、`TestCase` を継承しない普通のクラスに約束を書けます。** Elixir 版が `ExUnit.Assertions` を `import` したのと同じ形です。Java 版の `@Nested` や Ruby 版の「テストのモジュールを `include` する」といった仕掛けは要りませんでした。

本物の置き場のテストから呼び、

```php
public function testファイルの置き場は置き場の約束を満たす(): void
{
    StoreContract::check($this->storeWithModels(), new FileStore($this->tempDir('empty')));
}
```

偽物のテストからも呼びます。

```php
public function test偽物の置き場も置き場の約束を満たす(): void
{
    StoreContract::check(new FakeStore(), new FakeStore(false, false));
}
```

この節でいちばん学びがあったのは、**最初に書いた 2 行が PHPStan に叱られた** ことでした。

```php
Assert::assertIsFloat($withModels->loadSalesModel()->predict(self::movie()));
Assert::assertIsBool($withModels->loadSurvivalModel()->predict(self::passenger()));
```

```text
Call to static method PHPUnit\Framework\Assert::assertIsFloat() with float will always evaluate to true.
  🪪  staticMethod.alreadyNarrowedType
```

`SalesModel::predict()` の戻り値は `float` と宣言してあるので、「float であること」はテストするまでもありません。**型で守れているものをテストに書くと、静的解析がそれを重複だと教えてくれる** わけです。Elixir 版の約束のテストには `assert is_number(...)` が残っていますが、あれは behaviour の `@callback` が実行時に効かないぶん意味のある行でした。同じ約束のテストでも、言語が型をどこまで守るかによって書くべき行が変わります。

代わりに書いたのは「同じ入力には同じ答えを返す」「値が有限である」です。こちらは型では守れません。

### 偽物は無名クラスで書ける

```php
final readonly class FakeStore implements ModelStore
{
    public const float FIXED_SALES = 4321.5;

    public function __construct(
        private bool $hasSales = true,
        private bool $hasSurvival = true,
    ) {
    }

    public function loadSalesModel(): SalesModel
    {
        if (!$this->hasSales) {
            throw new ModelNotFoundException(Domain::SALES_MODEL);
        }

        return new class () implements SalesModel {
            public function predict(Movie $movie): float
            {
                return FakeStore::FIXED_SALES;
            }
        };
    }
    // ...
}
```

返すモデルは **無名クラス** です。Clojure 版の `reify`（その場で約束を満たす無名のオブジェクトを作る）に近く、Elixir 版が「behaviour はモジュールへの約束なので偽物もモジュールになる」と書いた制約は PHP にはありません。クラスを 1 つ足さずに約束を満たす値を作れます。

置き場そのものは名前のあるクラスにしました。2 つのテストから使うからです。差し替えたいのは「モデルがあるかどうか」だけなので、コンストラクタの 2 つの真偽値に追い出しています。

`implements ModelStore` と書いておくと、**メソッドを書き忘れたときと名前を間違えたときにその場で致命的エラーになります。** Elixir 版の `@behaviour`／`@impl true` は警告（`warnings_as_errors` を設定して初めてビルドが止まる）でしたが、PHP の interface は実装漏れを許しません。Ruby 版が「偽物のメソッド名を 1 文字間違えても実行時まで気づかない」と書いた部分が、この版ではいちばん厳しく守られます。

なお `new class () implements SalesModel` の括弧は PHP-CS-Fixer に足されました。`new class implements ...` でも動きますが、整形の規則が括弧をそろえます。

## 15.6 ドメインとサービス

### モデルが無いことを専用の例外で表す

```php
final class ModelNotFoundException extends RuntimeException
{
    public function __construct(public readonly string $model)
    {
        parent::__construct("学習済みモデル {$model} が見つかりません");
    }
}
```

この章のほかの失敗は `InvalidArgumentException` のままですが、これだけは別の型にします。API が 503 に変えるために、ほかの失敗と見分けられなければならないからです。メッセージにファイルのパスを含めないのは、**503 の応答としてそのまま外に出る** ためです。

コンストラクタのプロモーション（`public readonly string $model`）のおかげで、例外を捕まえた側が `$e->model` で「どのモデルが無いか」を読めます。Elixir 版の `defexception [:model]` と同じ使い勝手です。Clojure 版が `ex-info` のデータを `ex-data` で取り出した手数が、ここでは property の読み取りになります。

### 既存のモデルをアダプターで約束に合わせる

第 7 章の `LinearModel` は「列名 → 値の連想配列」を受け取ります。`Movie` を第 7 章の列名に読み替えるのがアダプターの仕事です。

```php
final readonly class LinearSalesModel implements SalesModel
{
    public function __construct(private LinearModel $model)
    {
    }

    public function predict(Movie $movie): float
    {
        return $this->model->predictOne([
            'SNS1' => $movie->sns1,
            'SNS2' => $movie->sns2,
            'actor' => $movie->actor,
            'original' => (float) $movie->original,
        ]);
    }
}
```

第 8 章のパイプラインは、CSV から読んだ「セルの文字列の行」を受け取ります。`Passenger` をその形に戻します。

```php
private static function row(Passenger $passenger): array
{
    return [
        'Pclass' => (string) $passenger->pclass,
        'Sex' => $passenger->sex,
        'Age' => self::cell($passenger->age),
        // ...
        'Embarked' => $passenger->embarked ?? '',
    ];
}
```

**分からない値を空欄にすると、第 8 章の前処理が学習のときに求めた中央値・最頻値で補完します。** API の利用者は年齢を知らなくてよく、補完の規則はモデルの側に閉じたままです。第 8 章で前処理をパイプラインに入れて一緒に保存したことが、ここで効いています。

### サービスは HTTP を知らない

```php
final readonly class Service
{
    public function __construct(private ModelStore $store)
    {
    }

    public function predictSales(Movie $movie): float
    {
        return $this->store->loadSalesModel()->predict($movie);
    }

    public function health(): array
    {
        return [
            Domain::SALES_MODEL => $this->ready($this->store->loadSalesModel(...)),
            Domain::SURVIVAL_MODEL => $this->ready($this->store->loadSurvivalModel(...)),
        ];
    }

    private function ready(callable $load): bool
    {
        try {
            $load();

            return true;
        } catch (ModelNotFoundException) {
            return false;
        }
    }
}
```

`$this->store->loadSalesModel(...)` は PHP 8.1 の **第一級クロージャ** の書き方で、「メソッドを呼ばずに、呼べる値として取り出す」ものです。Elixir 版の `&ModelStore.load_sales_model/1` に当たります。

`catch (ModelNotFoundException)` と変数を書かないのも PHP 8 からで、「型だけで捕まえて中身は見ない」ことが読み取れます。**モデルが無い以外の理由（ファイルが壊れているなど）はそのまま外へ投げます。** ヘルスチェックが「読める」と言ってしまうより、500 で落ちるほうが正直だからです。

`health()` が返す配列の並びは `cinema`・`survived` の順です。**PHP の連想配列は挿入した順を保つ** ので、Clojure 版が `array-map` を使い、Elixir 版が「マップに並びは無いが Jason がキーを並べ替えるので結果として合う」と書いた心配が、この版にはありません。`json_encode` はそのままの順で書きます。

## 15.7 モデルをファイルに保存する

置き場は、ディレクトリの中の `cinema.model`・`survived.model` を読み書きします。

```php
public function loadSalesModel(): SalesModel
{
    $path = $this->modelFile(Domain::SALES_MODEL);
    $this->requireFile($path, Domain::SALES_MODEL);
    $contents = @file_get_contents($path);

    if ($contents === false) {
        throw new InvalidArgumentException("モデルを開けません: {$path}");
    }

    // unserialize は読めない内容を例外ではなく警告で知らせるので、@ で抑えて false で判定する。
    // allowed_classes を省くと、ファイルに書かれたどんなクラスでも作られてしまう（第 8 章）。
    $loaded = @unserialize($contents, ['allowed_classes' => [LinearModel::class]]);

    if (
        !is_array($loaded)
        || ($loaded['format'] ?? null) !== self::FORMAT_VERSION
        || !(($loaded['model'] ?? null) instanceof LinearModel)
    ) {
        throw new InvalidArgumentException("モデルとして読めません: {$path}");
    }

    return new LinearSalesModel($loaded['model']);
}
```

第 8 章で書いた用心をそのまま持ち込んでいます。

- **`allowed_classes` は必須** です。省くと、ファイルに書かれたどんなクラスでも `unserialize` が作ってしまいます。ここで読んでよいのは `LinearModel` だけです
- **`unserialize` も `mkdir` も失敗を例外ではなく警告で知らせます。** この版の `phpunit.xml` は `failOnWarning="true"` なので、`@` で抑えて戻り値で判定しないと「壊れたファイルを読む」テストが書けません
- **形式の版を一緒に保存します。** 古いファイルを黙って読んで妙な予測を返すより、「読めません」と落ちるほうが安全です

生存予測のほうは、第 8 章の `saveModel`／`loadModel` をそのまま呼びます。前処理の 3 クラスと木のクラスを `allowed_classes` に並べる仕事は、第 8 章がすでに済ませています。

保存のメソッド（`saveSalesModel`・`saveSurvivalModel`）は **interface に入れていません**。読み込みは API が使いますが、保存は学習のときにしか使わないので、API から見える約束を小さく保つためです。Elixir 版と同じ判断をしました。

ファイルが無いときだけ `ModelNotFoundException` に変えます。

```php
private function requireFile(string $path, string $model): void
{
    if (!is_file($path)) {
        throw new ModelNotFoundException($model);
    }
}
```

**壊れたファイルはこの例外になりません。** 「まだ学習していない」（503 で、時間が経てば直るかもしれない）と「ファイルが壊れている」（500 で、人が直すしかない）は別のことだからです。テストでも分けて確かめています。

```php
public function test壊れたファイルはモデルが無いのとは違う失敗になる(): void
{
    $dir = $this->tempDir('broken');
    mkdir($dir, 0o777, true);
    file_put_contents($dir . '/cinema.model', 'これはモデルではありません');

    $this->expectException(\InvalidArgumentException::class);
    (new FileStore($dir))->loadSalesModel();
}
```

往復で桁が落ちないことも確かめます。

```php
public function test保存した線形回帰のモデルは変わらずに戻る(): void
{
    $model = new LinearModel(6114.5955056944, Chapter07::FEATURE_COLUMNS, [0.1234567890123456, 2.0, 3.0, 0.5]);
    $store = new FileStore($this->tempDir('roundtrip'));
    $store->saveSalesModel($model);

    $direct = (new LinearSalesModel($model))->predict(StoreContract::movie());

    $this->assertSame($direct, $store->loadSalesModel()->predict(StoreContract::movie()));
}
```

`assertSame` は浮動小数点数なら「1 ビットも違わない」を見ます。**PHP の `serialize` は浮動小数点数を `serialize_precision` に従った十進の文字列で書きます。** 既定の `-1` は「読み戻すと同じ値になる最短の表記」を選ぶので、往復しても値が変わりません。Elixir の `:erlang.term_to_binary`（二進でそのまま書く）とは仕組みが違いますが、結果は同じです。Clojure 版の EDN が `pr-str` で同じ保証をしていたのと同じ考え方で、**十進で書いても往復は守れる、ただし設定に依存する** という点だけが違います。

## 15.8 ハンドラーは Request を受け取って Response を返すメソッド

### 要求と応答を値にする

PHP の組み込みサーバーは、要求を `$_SERVER` と `php://input` で渡します。これをハンドラーの中で読むと、テストからサーバーを起動しなければなりません。そこで、ハンドラーが見るところだけを値にします。

```php
final readonly class Request
{
    public function __construct(
        public string $method,
        public string $path,
        public string $body,
    ) {
    }
}
```

こうすると、ハンドラーは **`Request` を受け取って `Response` を返すただのメソッド** になります。Elixir 版の `call/2` が `%Plug.Conn{}` を受け取って `%Plug.Conn{}` を返す関数だったのと同じ形を、値を自分で定義して作りました。Plug のような約束が言語の生態系に無いぶん 2 つのクラスを書きましたが、合わせて 40 行ほどです。

### match で経路を振り分ける

```php
public function handle(Request $request): Response
{
    return match ("{$request->method} {$request->path}") {
        'GET /health' => $this->health(),
        'POST /cinema/sales' => $this->predict(fn (): array => [
            'sales' => $this->service->predictSales(
                Validation::movie(Validation::readJson($request->body, Validation::MOVIE_TYPES)),
            ),
        ]),
        'POST /survived' => $this->predict(fn (): array => [
            'survived' => $this->service->predictSurvival(
                Validation::passenger(Validation::readJson($request->body, Validation::PASSENGER_TYPES)),
            ),
        ]),
        default => $this->unmatched($request),
    };
}
```

ルーターのライブラリは使いません。**`match` は `switch` と違って厳密な比較（`===`）で、フォールスルーもありません。** メソッドとパスを 1 つの文字列にまとめてしまえば、Elixir 版の関数節のパターンマッチにいちばん近い見た目になります。

知らないパスと、知っているパスへの違うメソッドは分けます。

```php
private function unmatched(Request $request): Response
{
    $allow = self::ALLOWED_METHODS[$request->path] ?? null;

    return $allow === null
        ? Response::json(404, ['detail' => '見つかりません'])
        : Response::json(405, ['detail' => '許していないメソッドです'], ['Allow' => $allow]);
}
```

### try/catch でステータスコードに変える

```php
private function predict(callable $run): Response
{
    try {
        return Response::json(200, $run());
    } catch (ValidationException $e) {
        return Response::json(422, ['detail' => $e->reasons]);
    } catch (ModelNotFoundException $e) {
        return Response::json(503, ['detail' => $e->getMessage()]);
    } catch (Throwable) {
        // 例外のメッセージには内部の事情が入るので、応答には出さない
        return Response::json(500, ['detail' => '予測できませんでした']);
    }
}
```

検証も予測もクロージャの中で起きるので、**失敗の種類ごとに `catch` の節を並べるだけ** で振り分けられます。Elixir 版は `with` で成功の道を縦に並べ、`else` と `rescue` の 2 か所で失敗を受けていました。PHP は例外に一本化できるぶん、ここは素直です。

`catch (Throwable)` を最後に置くのは、**例外のメッセージをそのまま外に出さない** ためです。ファイルのパスや行番号が 500 の本文に混ざると、それ自体が情報の漏れになります。

JSON にするところも 1 か所にまとめます。

```php
public static function json(int $status, array $body, array $headers = []): self
{
    return new self(
        $status,
        ['Content-Type' => 'application/json; charset=utf-8', ...$headers],
        json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
    );
}
```

`JSON_UNESCAPED_UNICODE` を付けないと、`json_encode` は日本語を `見つ...` と書きます。動きはしますが、`curl` で読んだときに何が起きているか分からなくなるので、そのまま書かせています。

### サーバーを起動しない統合テスト

ハンドラーがメソッドなので、テストは呼ぶだけです。

```php
private function call(string $method, string $path, string $body, ModelStore $store): array
{
    $response = (new Api(new Service($store)))->handle(new Request($method, $path, $body));
    $decoded = json_decode($response->body, true);
    $this->assertIsArray($decoded);
    $this->assertSame('application/json; charset=utf-8', $response->headers['Content-Type']);

    return [$response->status, $decoded];
}
```

置き場は引数で差し替えます。

```php
public function testモデルが無ければ503を返す(): void
{
    [$status, $body] = $this->call(
        'POST',
        '/cinema/sales',
        '{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}',
        new FakeStore(false, true),
    );

    $this->assertSame(503, $status);
    $this->assertSame(['detail' => '学習済みモデル cinema が見つかりません'], $body);
}
```

Ruby 版の rack-test は「1 つのテストの中で `app` を 1 度しか作らない」という癖があり、置き場を替えるにはテストを分ける必要がありました。PHP 版では `new Api(new Service($store))` を作り直すだけです。**ハンドラーがただのオブジェクトであることが、そのままテストの書きやすさになっています。**

500 のテストだけは偽物ではなく、壊れたファイルを置いた本物の `FileStore` を使いました。「予期しない失敗」を偽物で作ると、偽物のほうが本物より器用になってしまうからです。

## 15.9 実データで学習したモデルで動かす

### 学習と保存

第 7・8 章と同じ条件（`testSize` 0.2、シード 0、深さ 5、`balanced` の重み）で学習します。

```php
public static function trainAndSaveModels(string $dataDir, FileStore $store): void
{
    $cinema = Chapter07::prepareCinema($dataDir . '/cinema.csv', self::TEST_SIZE, self::SEED);
    $store->saveSalesModel(
        Chapter07::fit($cinema['xTrain'], $cinema['tTrain'], Chapter07::FEATURE_COLUMNS),
    );

    $rows = Chapter02::loadTable($dataDir . '/Survived.csv')->rows;
    $split = Chapter02::splitTrainTest($rows, Chapter08::targetLabels($rows), self::TEST_SIZE, self::SEED);
    $store->saveSurvivalModel(Chapter08::fit(
        Chapter08::featuresTable($split['xTrain']),
        $split['tTrain'],
        self::MAX_DEPTH,
        WeightedTree::BALANCED,
    ));
}
```

既存の章の公開されたメソッドを呼んでいるだけで、第 7 章にも第 8 章にも手を入れていません。

### 予測値はほかの言語版と一致するか

実データで学習したモデルをつなぐテストは、既存の章と同じく `#[Group('data')]` を付けて、学習データが無ければ外れるようにします。期待値は、最初に Java 版・Scala 版・Clojure 版の値を書いて走らせ、実際に測った値と突き合わせました。

**結果は「ほぼ一致、完全一致ではない」でした。**

| 言語版 | 予測値（`sns1=200, sns2=500, actor=3000, original=1`） |
|-------|--------------------------------------------|
| Java・Scala・Clojure | 7730.457421687023 |
| Elixir | 7730.457421687016 |
| **PHP** | **7730.457421687019** |

差は Java 版に対して約 4e-12 です。第 3 波の 2 つの版は、**どちらも JVM の言語版とはずれ、しかもお互いにもずれました**（PHP 版と Elixir 版の差は約 3e-12）。

原因は分割ではありません。**どの行が訓練データに入るかは完全に一致しています。** 第 2 章で `java.util.Random` と同じ 48 ビットの線形合同法を自作し、`shuffle(0..9, 0)` の並びが Java 版・Scala 版・Clojure 版・Elixir 版と一致することを確かめてあるからです。係数を求める手順（正規方程式 `(Xᵀ X) w = Xᵀ t` を解く）も同じです。

違うのは **解く実装** だけです。PHP 版は MathPHP の LU 分解、Elixir 版は `Nx.LinAlg.solve/2`、Java・Scala・Clojure 版はそれぞれの行列ライブラリを使っています。ガウスの消去法のピボットの選び方や、積を足し合わせる順が変われば、最後の 1〜2 桁は動きます。

[第 7 章](07-linear-regression.md) では、切片が `6114.5955056944` 対 `6114.5955056945` と 12〜13 桁まで一致し、表示の桁（小数第 2 位・第 4 位）では完全に一致していました。**その 13 桁目のずれが、8 章あとの予測値で 12 桁目のずれとして顔を出した** わけです。予測は係数に映画の特徴量（最大 3000）を掛けて足すので、誤差も一緒に拡大されます。桁の一致をうたうときは、どこまでを保証するのかを書いた側が意識していないと、あとの章で足をすくわれます。

テストには両方を書きました。1e-6 の許容でほかの言語版と突き合わせ、PHP 版の値そのものも固定します。

```php
// Java 版・Scala 版・Clojure 版と同じ分割・同じ手順なので、予測値もほぼ一致する。
// 完全には一致せず、PHP 版は 7730.457421687019（Elixir 版は …016、差は 1e-11 未満）。
// 正規方程式を解くのが MathPHP の LU 分解か Java の行列ライブラリかの違いで、丸めの順が変わる。
$this->assertEqualsWithDelta(self::JVM_SALES, $service->predictSales(StoreContract::movie()), 1.0e-6);
$this->assertSame(self::PHP_SALES, $service->predictSales(StoreContract::movie()));
```

一方、**保存と読み込みで桁が落ちないことは、実データでも完全な一致で確かめられます**。

```php
#[Group('data')]
public function test保存と読み込みを挟んでも予測値は変わらない(): void
{
    $split = Chapter07::prepareCinema(Dataset::path('cinema.csv'), 0.2, 0);
    $model = Chapter07::fit($split['xTrain'], $split['tTrain'], Chapter07::FEATURE_COLUMNS);
    $direct = (new LinearSalesModel($model))->predict(StoreContract::movie());

    $store = new FileStore($this->tempDir('roundtrip-data'));
    $store->saveSalesModel($model);

    $this->assertSame($direct, $store->loadSalesModel()->predict(StoreContract::movie()));
}
```

**「ほかの言語版と一致しない」と「自分の保存が値を壊す」は別のことです。** 前者は許容を決めて突き合わせ、後者は 1 ビットの一致を要求する、と分けて書けるのがテストの効きどころでした。

### 組み込みサーバーで待ち受ける

ここが PHP 版でいちばん形の変わったところです。

**Elixir の `Bandit.start_link/1` や Clojure の `run-jetty` と違い、PHP の組み込みサーバーはプログラムの中から起動するものではありません。** `php -S` そのものがサーバーで、要求ごとに指定されたスクリプトを最初から走らせます。そのため、学習・保存と待ち受けが 2 つのコマンドに分かれます。

```php
public static function run(?string $modelDir = null): string
{
    $store = new FileStore($modelDir ?? self::MODEL_DIR);
    self::trainAndSaveModels(Dataset::dir(), $store);
    $health = (new Service($store))->health();
    // ... ヘルスと起動のしかたを文字列にして返す
}
```

要求を受けるスクリプトは `tools/chapter15-server.php` です。

```php
$modelDir = getenv('ML_MODEL_DIR');
$store = new FileStore($modelDir === false || $modelDir === '' ? Chapter15::MODEL_DIR : $modelDir);

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$uri = $_SERVER['REQUEST_URI'] ?? '/';
$path = parse_url(is_string($uri) ? $uri : '/', PHP_URL_PATH);
$body = file_get_contents('php://input');

$response = (new Api(new Service($store)))->handle(new Request(
    is_string($method) ? $method : 'GET',
    is_string($path) ? $path : '/',
    $body === false ? '' : $body,
));

http_response_code($response->status);

foreach ($response->headers as $name => $value) {
    header("{$name}: {$value}");
}

echo $response->body;
```

**`$_SERVER` の中身は PHPStan から見れば `mixed`** なので、`is_string` で絞ってから `Request` に詰めます。ここが「外から来る値」と「型の付いた値」の境界で、境界を 1 か所に集めておくと、内側のクラスはすべて型どおりに書けます。

**プロセスに状態が残らない** ので、置き場は要求ごとにファイルからモデルを読み直します。Elixir 版や Clojure 版は起動時に読んだものを持ち回れますが、PHP 版にはその選択肢がありません。ただし、この章の設計では困りませんでした。`Service` が `ModelStore` から毎回読む形にしてあるからです。**「読み込みは毎回」という前提が、たまたま PHP の実行モデルと合っていた** ことになります（実測では、第 8 章のパイプラインを読み込んで 1 件予測するまでが 0.12 ミリ秒、第 7 章の線形回帰が 0.07 ミリ秒でした）。

学習して保存します。

```text
$ php -r 'require "vendor/autoload.php"; echo GettingStartedMl\Chapter15::run();'
モデル cinema: true
モデル survived: true
php -S 127.0.0.1:8015 tools/chapter15-server.php で待ち受けます（http://127.0.0.1:8015）
```

待ち受けます。`127.0.0.1` を指定して、自分のマシンからだけ接続できるようにします。

```text
$ php -S 127.0.0.1:8015 tools/chapter15-server.php
[Thu Sep 24 11:44:53 2026] PHP 8.4.16 Development Server (http://127.0.0.1:8015) started
```

別の端末から呼びます。

```text
$ curl -s http://127.0.0.1:8015/health
{"status":"ok","models":{"cinema":true,"survived":true}}

$ curl -s -X POST http://127.0.0.1:8015/cinema/sales \
    -H "Content-Type: application/json" \
    -d '{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}'
{"sales":7730.457421687019}

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
$ curl -s -i http://127.0.0.1:8015/unknown
HTTP/1.1 404 Not Found
Host: 127.0.0.1:8015
Date: Thu, 24 Sep 2026 02:44:54 GMT
Connection: close
X-Powered-By: PHP/8.4.16
Content-Type: application/json; charset=utf-8

{"detail":"見つかりません"}

$ curl -s -i http://127.0.0.1:8015/cinema/sales
HTTP/1.1 405 Method Not Allowed
Host: 127.0.0.1:8015
Date: Thu, 24 Sep 2026 02:44:54 GMT
Connection: close
X-Powered-By: PHP/8.4.16
Content-Type: application/json; charset=utf-8
Allow: POST

{"detail":"許していないメソッドです"}
```

ここで 2 つ気づくことがあります。

1 つめは、**自分が書いていない `X-Powered-By` が付いている** ことです。PHP が既定で足すヘッダーで（`expose_php` の設定で消せます）、本番なら消す類のものです。`Host` と `Date` と `Connection` も組み込みサーバーが足しています。

2 つめは、**ヘッダーの名前が送ったとおりの大文字小文字で届く** ことです。Elixir 版は Bandit が `content-type` と小文字に正規化していました。同じコードの見た目でも、届く応答はサーバーが決めます。

**「ハンドラーの応答」と「クライアントに届く応答」は同じではありません。** これは、関数やメソッドとして直接呼ぶ統合テストの限界です。ヘッダーの追加・正規化・接続の扱いはサーバーの仕事なので、1 度は本物を起動して `curl -i` で見ておく価値があります。

## 15.10 品質チェック

```bash
npx gulp apps:check:php
```

PHP-CS-Fixer → PHPStan（レベル 9）→ PHPUnit → カバレッジの判定が順に走ります。

この章のテストは 35 件（`#[Group('data')]` の 3 件を含む）です。学習データが無い環境では、その 3 件が `markTestSkipped` で外れて 32 件が走ります。PHP 版の全体では 353 件・0 失敗、行カバレッジは 99.00%（2388/2412）でした。

この章のクラスごとのカバレッジは次のとおりです。

| クラス | カバレッジ |
|--------|-----------|
| `Chapter15` | 100.00% |
| `Chapter15\Api` | 100.00% |
| `Chapter15\Validation` | 100.00% |
| `Chapter15\Service` | 100.00% |
| `Chapter15\Response` | 100.00% |
| `Chapter15\LinearSalesModel` | 100.00% |
| `Chapter15\PipelineSurvivalModel` | 100.00% |
| `Chapter15\FileStore` | 91.67% |

`FileStore` だけ 100% に届いていません。残っているのは `file_get_contents` が `false` を返す場合と、`mkdir` に失敗する場合です。どちらも「読めるファイルなのに開けない」「作れないディレクトリ」を作らないと通らず、テストのために環境を壊すことになります。第 8 章と同じ判断で、そこは残しました。

Elixir 版では組み立ての `Chapter15` が 50% でした（サーバーを起動する `run/1` をテストできないため）。PHP 版で `Chapter15` が 100% になったのは、**サーバーの起動がコードではなくコマンド（`php -S`）だから** です。`run()` は学習して保存して文字列を返すだけなので、そのままテストできます。**実行モデルの違いが、テストできる範囲の違いとして出た** 例です。

なお PHPStan は、15.5 節で書いたとおり `assertIsFloat` の重複を見つけました。指摘は `@phpstan-ignore` や baseline で黙らせず、テストのほうを直して解決しています。

## 15.11 まとめ

この章では、第 7・8 章のモデルを素の PHP で HTTP API にしました。PHP に固有の論点は次のとおりです。

1. **約束は interface で書ける。値への約束なので置き場はただのオブジェクト** — Elixir 版が behaviour（モジュールへの約束）のために `{モジュール, 状態}` の組を持ち回り、振り分けの関数を 30 行書いたところが、`implements ModelStore` の 1 語で済む。偽物も無名クラスでその場に書けて、Clojure の `reify` に近い
2. **型で守れるものは、約束のテストに書かなくてよい** — 「float を返す」を `assertIsFloat` で確かめたら PHPStan が「常に真である」と指摘した。型と静的解析が、テストの重複を教えてくれる。代わりに書くべきは「同じ入力には同じ答え」「無ければ例外」といった、型では書けない取り決め
3. **失敗は例外に一本化できる** — 検証の理由を持つ `ValidationException`、モデルが無い `ModelNotFoundException`、それ以外の `Throwable` を `catch` の節に並べるだけでステータスコードに変わる。Elixir 版が `with` の `else` と `rescue` の 2 か所で受けた失敗が、1 か所にまとまる
4. **JSON のオブジェクトと配列を PHP は区別しない** — `json_decode(..., true)` はどちらも配列にするので、`array_is_list` で弾く。しかも「数字だけの文字列」の鍵が整数に変わるという第 3 章の癖が、この判定に絡んでくる
5. **組み込みサーバーはプログラムから起動しない** — `php -S` が要求ごとにスクリプトを最初から走らせる。プロセスに状態が残らないので、置き場は毎回ファイルから読む。結果として組み立てのクラスが 100% テストできた（Elixir 版の `Chapter15` は 50%）
6. **`serialize` は十進で書くが、往復で 1 ビットも落ちない** — `serialize_precision` の既定（`-1`）が「読み戻すと同じ値になる最短の表記」を選ぶ。ただし **ほかの言語版との一致は 4e-12 の差が残った**（MathPHP の LU 分解と Java の行列ライブラリで、正規方程式を解く丸めの順が違う）

**TODO リスト（この章の完了時点）**:

- [x] 要求の JSON を読み、型が合わなければ弾く
- [x] 必須・0 以上・選択肢の検証をし、理由を並べて返す
- [x] 映画・乗客の特徴量をクラスで表す
- [x] 置き場の約束を interface と約束のテストで表す
- [x] 第 7 章の線形回帰と第 8 章のパイプラインをファイルに保存・読み込みする
- [x] ファイルが無ければ `ModelNotFoundException` を投げる
- [x] サービスが置き場からモデルを読んで予測し、ヘルスチェックを返す
- [x] `POST /cinema/sales`・`POST /survived` が予測を JSON で返す
- [x] 入力が不正なら 422、モデルが無ければ 503、それ以外は 500
- [x] 知らないパスは 404、許していないメソッドは 405
- [x] 第 7・8 章と同じ条件で学習し、組み込みサーバーで API を起動する

## 15.12 PHP 版のまとめ

シリーズを 15 章走りきったので、PHP 版で分かったことをまとめます。

### 型は書いたほうが速かった

[ADR 013](../../../adr/013-php-ml-libraries.md) で、PHP 版は **Ruby 版と逆に「型を使う」** と決めました。`declare(strict_types=1)` で実行時に効く型を付け、PHPStan のレベル 9 で配列の形まで検査させる方針です。

15 章を通して、この選択は正しかったと言えます。とくに効いたのは次の場面でした。

- **`list<array<string, float>>` のような「配列の形」が守られる。** 第 2 章の `array_filter` が添字を保つ件や、第 7 章で行列に渡す前の形など、配列を作り替える場所では毎回 PHPStan が形の崩れを見つけました
- **リファクタリングが安い。** 第 8 章で決定木を「1 件ごとの重みを通す」形に書き直したとき、呼び出し側の直し漏れはコンパイル前に全部出ました
- **テストの重複が分かる。** この章の `assertIsFloat` のように、型が守っているものをテストに書くと指摘されます

一方で、**型では守れないことも 15 章分たまりました**。

- **PHP の配列は「数字だけの文字列」の鍵を整数に変える**（第 3 章）。`@return array<string, int>` と書いても PHPStan は通し、テストで初めて見つかりました
- **整数は溢れると float に化けて静かに精度を失う**（第 2 章）。Java のように折り返さず、Elixir のように多倍長にもなりません
- **`?:` と `if ($value)` は 0.0 を偽とする**（第 2 章）。正規化されたデータでは欠損値の判定に使えません

**型の道具をどれだけ使っても、言語の癖は型では守れません。** そこを埋めたのはテストでした。Ruby 版が「テストだけで守る」と決めたのに対し、PHP 版は「型で守れるところは型で、残りをテストで」という二段構えになりました。テストは減るのではなく、**言語の癖に寄った** ものになります。

### ライブラリの成熟度が記事の重心を動かす

第 3 波の 2 つの版は、意図して正反対の版になりました。

| 観点 | Elixir 版 | PHP 版 |
|------|-----------|--------|
| 機械学習ライブラリ | Scholar（決定木が無い） | Rubix ML（決定木もランダムフォレストもある） |
| 突き合わせ | できない章が多く、自作が最終実装 | ほぼ全章で突き合わせられた |
| 記事の重心 | 「無いものをどう作るか」 | 「あるものとどう比べるか」 |

Rubix ML があったおかげで、PHP 版では「自作してからライブラリと突き合わせる」を毎章できました。そこで繰り返し見えたのは、**「ライブラリにある」と「そのまま比べられる」は別である** ということです。

- 第 3 章の `ClassificationTree` は **既定値のままでは決定的ですらなく**、シードを渡す口もありませんでした。`maxLeafSize`・`minPurityIncrease`・`maxFeatures`・`maxBins` を明示して初めて比べられました
- 第 7 章の `Ridge` は自作とまったく同じ正規方程式を解いていました。違うのは最後の一手（逆行列か LU 分解か）だけで、切片は 12〜13 桁まで一致しました。ただし **`l2Penalty` の既定は 1.0** なので、省くと別のモデルになります
- 第 8 章では `MissingDataImputer`・`OneHotEncoder`・クラスの重みに **必要な口が 3 つ足りず**、そこだけ自作になりました

ライブラリが豊富な版では、**ライブラリを読む力が自作する力と同じくらい要ります**。「使えば同じ答えが出る」わけではないからです。

### 数値の一致は、どこまでを保証するかを決める仕事

第 2 章で `java.util.Random` と同じ線形合同法を自作したので、**どの行が訓練データに入るかは全章で JVM の言語版・Elixir 版と一致しました**。これがあったおかげで、正解率も評価指標も係数も突き合わせられました。

そのうえで、この章の予測値は 12 桁目でずれました。**分割が一致していても、線形代数の実装が違えば最後の桁は動きます。** 第 7 章で「12〜13 桁まで一致した」と書いたときに、それが 8 章あとで 4e-12 の差として現れることまでは見えていませんでした。

対処は難しくありません。**「許容を決めて突き合わせる値」と「1 ビットの一致を要求する値」を分ける** ことです。この章では、ほかの言語版との比較は 1e-6 の許容で、自分の保存と読み込みの往復は `assertSame` で確かめました。前者はアルゴリズムの実装に依存し、後者は自分のコードにしか依存しないからです。

### 道具立ては言語の標準ではなくプロジェクトの約束

PHP 版の道具立てでは、前提が 2 度崩れました。

- **素の Nix 環境にカバレッジドライバが無かった**（第 1 章）。`php.withExtensions` で pcov を足し、composer も同じ PHP から取って版（8.4 と 8.3）をそろえました
- **PHPUnit に最低カバレッジのしきい値の機能が無かった**（第 1 章・第 6 章）。clover の XML を読んで判定する短いスクリプトを自作しました

Elixir の `mix test --cover` にしきい値が最初からあったのとは逆です。ここから引けるのは、**しきい値も品質ゲートも、言語が与えるものではなくプロジェクトが決める約束である** ということでした。言語が持っていれば借りればよく、持っていなければ 30 行書けば済みます。借りられるかどうかで約束の中身を変えるべきではありません。

### 最後に

第 1 章の「20 代ならきのこ派」という手書きのルールから始めて、この章で学習したモデルを HTTP で届けるところまで来ました。

最後まで支えになったのは、**表もモデルも学習済みのパイプラインも、すべて `readonly class` と配列でできていた** ことです。比べるのは `==`、保存するのは `serialize`、型を確かめるのは PHPStan と、道具はずっと同じでした。第 15 章で API を足すときに第 7 章と第 8 章に 1 行も触らずに済んだのは、そこで作った値が最初から「ただの値」だったからです。

**変更を楽に安全にできること。** 15 章を通して効いたのは、賢い抽象ではなく、値が値のままであること、約束が型かテストのどちらかに書いてあること、そして毎章の数値をほかの言語版と突き合わせ続けたことでした。
