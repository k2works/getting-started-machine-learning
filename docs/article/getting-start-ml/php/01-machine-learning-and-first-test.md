---
type: Article
title: "第 1 章: 機械学習とはじめてのテスト"
description: "機械学習とルールベースの違いを確認し、きのこ派・たけのこ派の判定を PHP の TDD で実装して正解率を測る。readonly class で値を表し、declare(strict_types=1) と PHPStan のレベル 9 で型を検査する書き方を Ruby 版・Python 版と対比する。"
tags: [article,getting-start-ml,php]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 1 章: 機械学習とはじめてのテスト

## 1.1 はじめに

この章では、機械学習とは何かを確認したうえで、テスト駆動開発（TDD）で最初のプログラムを PHP で作ります。題材は「きのこの山派か、たけのこの里派か」を身長・体重・年代から判定する問題です。

この章ではまだ機械学習のアルゴリズムを使いません。人間が考えたルールで判定し、その正解率を測るところまで進みます。「ルールを人間が書く」とはどういうことかを先に体験しておくと、第 3 章で「ルールをデータから学ばせる」ことの意味がはっきりします。

[Python 版の第 1 章](../python/01-machine-learning-and-first-test.md) と同じ題材・同じ TODO リストで進めます。PHP 版では次の 3 つと対比します。1 つは [Python 版](../python/01-machine-learning-and-first-test.md) と [TypeScript 版](../typescript/01-machine-learning-and-first-test.md) で、実行時に効く型と静的解析だけが見る型を分けて使う点です。2 つめは [Ruby 版](../ruby/01-machine-learning-and-first-test.md) で、同じスクリプト言語でありながら**型の道具をどこまで使うかが正反対**である点です（[ADR 013](../../../adr/013-php-ml-libraries.md)）。3 つめは [Elixir 版](../elixir/01-machine-learning-and-first-test.md) で、機械学習ライブラリの成熟度がまったく違う点です。

## 1.2 機械学習とは

### ルールを書くか、データに学ばせるか

従来のプログラミングでは、人間が判定のルールをコードとして書きます。

```php
public static function predictByRule(Features $features): string
{
    return $features->ageGroup === self::KINOKO_AGE_GROUP ? self::KINOKO : self::TAKENOKO;
}
```

この書き方では、ルールの良し悪しは人間の観察力に依存します。特徴量が 3 つなら何とかなりますが、20 個・100 個になると人間には手に負えません。

機械学習は、この「ルール」をデータから自動で作ります。人間が与えるのは「入力（特徴量）」と「正解（ラベル）」の組で、ルールそのものはアルゴリズムが決めます。第 3 章で決定木を実装すると、「年代が 20 ならきのこ」に相当する分岐が、データから自動で決まる様子を見られます。

| | 従来のプログラミング | 機械学習 |
|---|---|---|
| 人間が書くもの | ルール | データと、学習のさせ方 |
| 出力 | 判定結果 | ルール（モデル）と、それを使った判定結果 |
| 得意なこと | 仕様がはっきりしている問題 | 仕様を言葉にしにくい問題 |
| 説明のしやすさ | コードを読めば分かる | モデルによる（決定木は読める、ニューラルネットは難しい） |

### 機械学習のワークフロー

本シリーズを通して、次の流れを繰り返します。

1. **データを集める・読み込む** — この章で CSV を読み込みます
2. **前処理する** — 欠損値を埋め、訓練データとテストデータに分けます（第 2 章）
3. **モデルを学習させる** — 決定木・回帰などを自作し、Rubix ML と突き合わせます（第 3 章以降）
4. **評価する** — 正解率などの指標で測ります（この章で正解率から始めます）
5. **改善する** — 特徴量を作り直し、ハイパーパラメータを調整します（第 9 章以降）

PHP 版には先に断っておくと良いことが 1 つあります。**Rubix ML には決定木もランダムフォレストもあります。** これは当たり前のことに見えますが、直前に書いた [Elixir 版](../elixir/index.md) では Scholar に決定木が無く、自作した決定木がそのまま最終実装になりました。同じ第 3 波の 2 つの版が、ライブラリの成熟度という点で正反対の性格を持っています（[ADR 013](../../../adr/013-php-ml-libraries.md)）。PHP 版ではほぼ全章で「自作してからライブラリと突き合わせる」ことができます。

### 分類と回帰

教師あり学習は、予測するものの型で 2 つに分かれます。

| 種類 | 予測するもの | 例 | 本シリーズで扱う章 |
|------|------------|-----|-----------------|
| 分類 | どのグループに属するか（離散値） | きのこ派／たけのこ派、アヤメの種類、生存／死亡 | 第 1・3・8・10・11 章 |
| 回帰 | 数値（連続値） | 映画の興行収入、住宅価格 | 第 7・9・12 章 |

この章は分類です。正解ラベルが「きのこ」「たけのこ」の 2 つなので、二値分類にあたります。

## 1.3 題材とデータ

### データの入手と配置

本シリーズの学習データは、書籍『スッキリわかる Python による機械学習入門』（インプレス, 2020）の配布データを使います。配布データは書籍購入者のみ利用できるため、リポジトリには含まれていません。[書籍サポートページ](https://sukkiri.jp/books/sukkiri_ml) から `sukkiri-ml-codes.zip` を入手し、リポジトリの `tmp/` に置いてから、リポジトリのルートで次を実行してください。

```bash
npx gulp data:setup
npx gulp data:check
```

`apps/data/sukkiri-ml/` に学習データが配置されます。このディレクトリは `.gitignore` の対象なので、誤ってコミットされることはありません。すべての言語版が同じデータを参照します。

### KvsT.csv

この章で使うのは `KvsT.csv` です。19 人分のデータが次の 4 列で記録されています。

| 列 | 意味 | 値 |
|----|------|-----|
| 身長 | 身長（cm） | 整数 |
| 体重 | 体重（kg） | 整数 |
| 年代 | 年代 | 10, 20, 30, 40 のいずれか |
| 派閥 | きのこの山派かたけのこの里派か | `きのこ` または `たけのこ` |

「身長」「体重」「年代」が特徴量、「派閥」が正解ラベルです。列名が日本語であることと、ファイルの先頭に BOM（バイトオーダーマーク）が付いていることに注意してください。**PHP の `fgetcsv` は BOM を取り除きません。** 後で確かめます。

## 1.4 TODO リストの作成

仕様をそのままコードにするには大きすぎるので、まず TODO リストに分解します。

> TODO リスト
>
> 何をテストすべきだろうか——着手する前に、必要になりそうなテストをリストに書き出しておこう。
>
> — テスト駆動開発

**TODO リスト**:

- [ ] CSV を読み込む
  - [ ] BOM 付き CSV を列名で読み込む
  - [ ] 数値でない値・列の不足を弾く
- [ ] 特徴量と正解ラベルに分ける
- [ ] ルールで派閥を判定する
  - [ ] 20 代ならきのこ派と判定する
  - [ ] 20 代以外ならたけのこ派と判定する
- [ ] 正解率を計算する
- [ ] 実データで正解率を表示する

## 1.5 開発環境の準備

### プロジェクトの構成

`apps/php/` に Composer のプロジェクトを作ります。

```text
apps/php/
├── composer.json
├── composer.lock
├── phpunit.xml
├── phpstan.neon
├── .php-cs-fixer.dist.php
├── src/
│   ├── Chapter01.php
│   ├── Dataset.php
│   ├── Features.php
│   └── Person.php
├── tests/
│   ├── Chapter01Test.php
│   └── DatasetTest.php
└── tools/
    └── coverage-threshold.php
```

`composer.json` は次のようにします。

```json
{
    "name": "getting-started-ml/php",
    "require": {
        "php": ">=8.4",
        "ext-mbstring": "*",
        "rubix/ml": "^2.5",
        "markrogoyski/math-php": "^2.0"
    },
    "require-dev": {
        "phpunit/phpunit": "^11.5",
        "phpstan/phpstan": "^2.1",
        "friendsofphp/php-cs-fixer": "^3.68"
    },
    "autoload": {
        "psr-4": {
            "GettingStartedMl\\": "src/"
        }
    },
    "autoload-dev": {
        "psr-4": {
            "GettingStartedMl\\Tests\\": "tests/"
        }
    }
}
```

PSR-4 のオートロードを設定すると、`GettingStartedMl\Chapter01` が `src/Chapter01.php` に対応します。名前空間とディレクトリが 1 対 1 に決まるので、Ruby 版の `require` のような読み込みの記述が要りません。

### Nix 環境と、カバレッジドライバの落とし穴

この版では、環境の準備でシリーズ初の出来事がありました。**Nix の PHP 環境にカバレッジドライバが入っておらず、カバレッジをまったく取れなかった**のです。

```bash
php -m | grep -E 'xdebug|pcov'
```

```text
（何も出ない）
```

PHP はカバレッジの計測を言語本体では行わず、**xdebug か pcov という拡張**に任せています。どちらも無いと、PHPUnit は黙ってカバレッジ 0 の報告を出すか、ドライバが無い旨の警告を出します。Elixir の `mix test --cover` や Rust の `cargo llvm-cov` のように「入れれば動く」ものではありません。

そこで `ops/nix/environments/php/shell.nix` に手を入れ、pcov を足した PHP を使うようにしました。

```nix
let
  # カバレッジのために pcov を足した PHP。composer も同じ PHP から取り、
  # php 本体と composer が動く PHP の版がずれないようにする。
  phpWithPcov = packages.php.withExtensions (
    { enabled, all }: enabled ++ [ all.pcov ]
  );
in
packages.mkShell {
  buildInputs = baseShell.buildInputs ++ [
    phpWithPcov
    phpWithPcov.packages.composer
    packages.phpactor
  ];
}
```

xdebug ではなく pcov を選んだのは、カバレッジだけが目的なら pcov のほうが軽いからです（xdebug はステップ実行のデバッガでもあるので、有効にすると実行がかなり遅くなります）。

ついでにもう 1 つ直しました。もとの環境定義は `php`（8.4.16）と `php83Packages.composer`（PHP 8.3.29 の上で動く）を混ぜていて、**php 本体と composer が動く PHP の版が食い違っていました**。[Clojure 版](../clojure/01-machine-learning-and-first-test.md) の「`java` は 25 だが Clojure は JDK 21 で動く」、[Elixir 版](../elixir/01-machine-learning-and-first-test.md) の「`erl` は OTP 28 だが Elixir は OTP 27 で動く」と同じ形です。ただし PHP の場合は `phpWithPcov.packages.composer` と書けば composer を同じ PHP から取れるので、版をそろえて解消できました。

```bash
nix develop .#php
php --version
composer --version
```

```text
PHP 8.4.16 (cli) (built: Dec 16 2025 16:03:34) (NTS)
Composer version 2.9.2 2025-11-19 21:57:25
PHP version 8.4.16 (/nix/store/.../php-with-extensions-8.4.16/bin/php)
```

依存をインストールします。

```bash
cd apps/php
composer install
```

なお、Rubix ML には `tensor` というネイティブ拡張の高速版がありますが、**無くても純 PHP の `rubix/tensor` で動きます**。Nix の中で C の拡張をビルドする必要はありませんでした。

### 最初のテストを走らせる

`phpunit.xml` を用意します。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<phpunit xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:noNamespaceSchemaLocation="vendor/phpunit/phpunit/phpunit.xsd"
         bootstrap="vendor/autoload.php"
         colors="true"
         cacheDirectory="build/.phpunit.cache"
         failOnWarning="true"
         failOnNotice="true"
         failOnDeprecation="true">
    <testsuites>
        <testsuite name="getting-started-ml">
            <directory>tests</directory>
        </testsuite>
    </testsuites>
    <source>
        <include>
            <directory>src</directory>
        </include>
    </source>
</phpunit>
```

`failOnDeprecation="true"` を入れておくのが大事です。PHP は非推奨の書き方を警告して実行を続けるので、放っておくと気づかないまま古い書き方が残ります。実際この章を書いている途中で、次の警告に助けられました。

```text
1 test triggered 1 PHP deprecation:

1) src/Chapter01.php:155
GettingStartedMl\Chapter01::run(): Implicitly marking parameter $csvFile as nullable is deprecated,
the explicit nullable type must be used instead
```

`function run(string $csvFile = null)` は PHP 8.4 から非推奨で、`?string $csvFile = null` と明示しなければなりません。**テストが緑でも、この設定が無ければ黙って見逃していました。**

## 1.6 ルールで派閥を判定する

### データをどう表すか

PHP でデータを表す方法はいくつかあります。連想配列（`['身長' => 170, ...]`）でもよいのですが、この版では **`readonly class`** を使います。

```php
<?php

declare(strict_types=1);

namespace GettingStartedMl;

/** 判定の手がかりになる特徴量。正解ラベルを持たない。 */
final readonly class Features
{
    public function __construct(
        public int $height,
        public int $weight,
        public int $ageGroup,
    ) {
    }
}
```

コンストラクタの引数にそのまま `public` を書くと、同じ名前のプロパティが作られます（コンストラクタプロモーション）。`readonly` を付けたクラスは、作ったあとに値を書き換えられません。[Ruby 版](../ruby/01-machine-learning-and-first-test.md) の `Data.define` にあたります。

ファイルの先頭にある `declare(strict_types=1);` が、この版の性格を決める 1 行です。これを書かないと、PHP は `"170"`（文字列）を `int $height` に渡されたとき黙って数値に変換します。書いておくと `TypeError` で落ちます。**つまり PHP の型宣言は、書き方 1 つで「強制する型」にも「努力目標」にもなります。**

[Ruby 版](../ruby/index.md) は RBS・Steep を使わないと決めました。動的型付けの言語で「型の誤りをテストで捕まえる」ことを示すためです。PHP 版は逆に、**型を使い切る**方針をとります（[ADR 013](../../../adr/013-php-ml-libraries.md)）。理由は単純で、PHP では型宣言が言語本体に入っていて、書かないほうが不自然だからです。同じ「動的型付けのスクリプト言語」でも、型の道具がどこまで言語に組み込まれているかで開発の手触りがまるで違う——この対比は第 5 章で改めて扱います。

### Red: まだ無いものを呼ぶ

TDD の最初の一歩は、まだ存在しないものを呼ぶテストです。

> テスト駆動開発は次の 3 つのステップで進む。
>
> 1. レッド: 動作しないテストを 1 つ書く。おそらくは最初、コンパイルできない
> 2. グリーン: そのテストを迅速に動作させる。そのためには罪を犯してもよい
> 3. リファクタリング: テストを通すために発生した重複をすべて除去する
>
> — テスト駆動開発

```php
#[TestDox('二十代はきのこ派と判定する')]
public function test二十代はきのこ派と判定する(): void
{
    $this->assertSame(Chapter01::KINOKO, Chapter01::predictByRule(new Features(170, 60, 20)));
}
```

**PHP のメソッド名には日本語をそのまま書けます。** `test` で始まる名前が PHPUnit にテストと認識されるので、`test二十代はきのこ派と判定する` のように続けて書けます。加えて `#[TestDox]` 属性を付けると、実行結果の一覧が読みやすくなります。

```bash
php vendor/bin/phpunit --testdox
```

```text
Failed asserting that exception of type "Error" matches expected exception "InvalidArgumentException".
Message was: "Class "GettingStartedMl\Chapter01" not found"

ERRORS!
Tests: 10, Assertions: 4, Errors: 6, Failures: 4.
```

クラスが無いので落ちます。これが Red です。

### Green: 最小の実装

```php
final class Chapter01
{
    /** 「20 代ならきのこ派」というルールの年代。 */
    public const int KINOKO_AGE_GROUP = 20;

    /** きのこ派の呼び名。 */
    public const string KINOKO = 'きのこ';

    /** たけのこ派の呼び名。 */
    public const string TAKENOKO = 'たけのこ';

    public static function predictByRule(Features $features): string
    {
        return $features->ageGroup === self::KINOKO_AGE_GROUP ? self::KINOKO : self::TAKENOKO;
    }
}
```

`public const int KINOKO_AGE_GROUP = 20;` のように、**定数にも型を書けます**（PHP 8.3 から）。書かなくても動きますが、書いておくと PHPStan が定数の使われ方まで検査してくれます。

比較に `===` を使っている点にも触れておきます。PHP の `==` は型をまたいで緩く比較するので、`0 == 'きのこ'` のような比較が思わぬ結果になります（PHP 8 でだいぶ改善されましたが、いまだに `'1' == '01'` は真です）。**この版では比較を必ず `===` で書きます。** Ruby の `==`、Elixir の `===` にあたるのがこちらです。

### 三角測量: 20 代以外も確かめる

1 つのテストが通っただけでは、`return self::KINOKO;` と書いても通ってしまいます。もう 1 つ別の角度からテストを足します。

> 三角測量では、2 つ以上の例があるときのみ、一般化を行う。
>
> — テスト駆動開発

```php
#[TestDox('二十代以外はたけのこ派と判定する')]
public function test二十代以外はたけのこ派と判定する(): void
{
    foreach ([10, 30, 40, 50] as $ageGroup) {
        $this->assertSame(Chapter01::TAKENOKO, Chapter01::predictByRule(new Features(170, 60, $ageGroup)));
    }
}
```

[Elixir 版](../elixir/01-machine-learning-and-first-test.md) はここでパターンマッチの関数節を 2 つ並べました。PHP には関数のオーバーロードもパターンマッチもないので、三項演算子 1 つで書きます。代わりに、`Features` という型を引数に要求することで「年代を持たない値」を最初から弾いています。**Elixir が `FunctionClauseError` で実行時に守るところを、PHP は型宣言で守ります。**

## 1.7 失敗を例外で表す

正解率を計算するとき、予測と正解ラベルの件数が違ったらどうするか。この版では**例外を投げます**。

```php
public static function accuracy(array $predictions, array $labels): float
{
    if (count($predictions) !== count($labels)) {
        throw new InvalidArgumentException(
            sprintf('予測と正解ラベルの件数が違います: %d と %d', count($predictions), count($labels)),
        );
    }

    if ($labels === []) {
        throw new InvalidArgumentException('正解ラベルがありません');
    }

    $hits = 0;

    foreach ($labels as $i => $label) {
        if ($predictions[$i] === $label) {
            ++$hits;
        }
    }

    return $hits / count($labels);
}
```

テストはこう書きます。

```php
#[TestDox('件数が違えば正解率を求められない')]
public function test件数が違えば正解率を求められない(): void
{
    $this->expectException(InvalidArgumentException::class);
    $this->expectExceptionMessage('予測と正解ラベルの件数が違います: 1 と 2');

    Chapter01::accuracy([Chapter01::KINOKO], [Chapter01::KINOKO, Chapter01::TAKENOKO]);
}
```

[Rust 版](../rust/01-machine-learning-and-first-test.md) の `Result` や [Go 版](../go/01-machine-learning-and-first-test.md) の `error` とは逆の流儀です。**PHP の型宣言は引数と戻り値には効きますが、「この関数は何を投げるか」は型では表せません。** そこは Ruby 版・Elixir 版と同じで、`@throws` の注釈とテストでしか表せません。型を使い切る方針でも、例外だけは型の外に残ります。

`$hits / count($labels)` が `float` を返す点にも注意してください。PHP の `/` は**整数どうしでも割り切れなければ float を返します**（Python 3 の `/` と同じ）。Ruby の `fdiv`、Java の `(double)` のキャストにあたるものが要りません。

## 1.8 CSV を読み込む

### fgetcsv は BOM を取り除かない

`KvsT.csv` の先頭には BOM が付いています。PHP の標準の `fgetcsv` はこれを取り除きません。確かめてみます。

```php
file_put_contents('bom.csv', "\u{FEFF}身長,体重\n170,60\n");
$h = fopen('bom.csv', 'r');
$row = fgetcsv($h, escape: '');
echo json_encode($row[0]), "\n";
```

```text
"\ufeff\u8eab\u9577"
```

先頭の列名が `"身長"` ではなく `"\u{FEFF}身長"` になっています。このまま `$row['身長']` と引くと、列が無いことになります。[Go 版](../go/01-machine-learning-and-first-test.md)・[Clojure 版](../clojure/01-machine-learning-and-first-test.md)・[Elixir 版](../elixir/01-machine-learning-and-first-test.md) と同じ落とし穴です（[Ruby 版](../ruby/01-machine-learning-and-first-test.md) の `CSV.foreach` と [Python 版](../python/01-machine-learning-and-first-test.md) の pandas は取り除いてくれます）。

自分で取り除きます。

```php
/** UTF-8 の BOM を取り除く。 */
private static function stripBom(string $text): string
{
    return str_starts_with($text, "\u{FEFF}") ? substr($text, 3) : $text;
}
```

`substr($text, 3)` の 3 はバイト数です。**PHP の文字列はバイト列なので、`substr` はバイト単位で切ります。** UTF-8 の BOM は 3 バイトなので、これでちょうど取り除けます。日本語の文字を数えたいときは `mb_substr` を使い分けることになります。この「文字列がバイト列である」性質は第 9 章（Shift_JIS の CSV）でもう一度出てきます。

### escape 引数の話

`fgetcsv($handle, escape: '')` の `escape: ''` は、PHP 8.4 で付けた引数です。PHP の CSV 関数には歴史的にバックスラッシュのエスケープ処理があり、**RFC 4180 の CSV とは違う解釈をします**。PHP 8.4 でこの挙動が非推奨になり、`escape: ''` を明示すると標準どおりの解釈になります。名前付き引数で書けるので、どの引数を指定しているかが読んで分かります。

### 読み込みの実装

```php
/**
 * CSV を読み込み、列名で値を取り出して人物のリストにする。
 *
 * PHP の fgetcsv は BOM を取り除かないので、先頭の列名から自分で取り除く。
 *
 * @return list<Person>
 */
public static function loadPeople(string $csvFile): array
{
    $handle = @fopen($csvFile, 'r');

    if ($handle === false) {
        throw new InvalidArgumentException("CSV を開けません: {$csvFile}");
    }

    try {
        $header = fgetcsv($handle, escape: '');

        if ($header === false) {
            throw new InvalidArgumentException("CSV が空です: {$csvFile}");
        }

        /** @var list<string> $columns */
        $columns = array_map(self::stripBom(...), array_map(strval(...), $header));

        $people = [];

        while (($cells = fgetcsv($handle, escape: '')) !== false) {
            // 空行は読み飛ばす。
            if ($cells === [null] || $cells === ['']) {
                continue;
            }

            /** @var array<string, string> $row */
            $row = array_combine($columns, array_map(strval(...), $cells));

            $people[] = new Person(
                self::number($row, '身長'),
                self::number($row, '体重'),
                self::number($row, '年代'),
                self::text($row, '派閥'),
            );
        }

        return $people;
    } finally {
        fclose($handle);
    }
}
```

3 つ、PHP らしい書き方が出てきます。

1 つめは **`self::stripBom(...)` という第一級呼び出し可能記法**です（PHP 8.1 から）。メソッドを値として渡せます。`array_map('self::stripBom', ...)` のように文字列で書いていた時代と違い、綴りを間違えれば静的解析が見つけてくれます。

2 つめは **`try`/`finally` によるファイルの後始末**です。Python の `with`、Ruby のブロック、Go の `defer` にあたるものが PHP には無いので、`finally` に `fclose` を書きます。

3 つめは **`/** @var list<string> $columns *​/` という注釈**です。PHP の型宣言は「配列である」ことまでしか言えず、「文字列の配列である」ことは書けません。そこを PHPDoc の注釈で補い、PHPStan に検査させます。**実行時に効く型（引数の `string`）と、静的解析だけが見る型（`list<string>`）が分かれている**——これが PHP の漸進的な型付けの姿です。[TypeScript 版](../typescript/01-machine-learning-and-first-test.md) は型がすべてコンパイル時に消える、[Python 版](../python/01-machine-learning-and-first-test.md) は型注釈が実行時には検査されない、という違いと並べて読んでください。

`list<string>` は PHPStan の用語で、「0 から連番の添字を持つ配列」を意味します。PHP の配列は連想配列と順序つきリストの両方を兼ねているので、静的解析ではこの 2 つを区別します。

### 列を読む

```php
/**
 * 列名で数値を読む。整数として読めなければ例外を投げる。
 *
 * @param array<string, string> $row
 */
public static function number(array $row, string $column): int
{
    $cell = self::text($row, $column);

    if (filter_var($cell, FILTER_VALIDATE_INT) === false) {
        throw new InvalidArgumentException("{$column} を数値として読めません: {$cell}");
    }

    return (int) $cell;
}

/**
 * 列名でセルを読む。列が無ければ例外を投げる。
 *
 * @param array<string, string> $row
 */
public static function text(array $row, string $column): string
{
    if (!array_key_exists($column, $row)) {
        throw new InvalidArgumentException("列がありません: {$column}");
    }

    return $row[$column];
}
```

数値の判定に `filter_var($cell, FILTER_VALIDATE_INT)` を使っているのには理由があります。PHP には `is_numeric` や `(int)` のキャストもありますが、**`(int) '高い'` は例外を投げずに 0 を返します**。`intval('12abc')` も 12 を返します。黙って間違った値を作るくらいなら、`filter_var` で厳密に検査して例外を投げるほうが安全です。

これは Ruby 版が `Integer(cell, 10)`（読めなければ `ArgumentError`）を使ったのと同じ判断です。**「緩い変換を言語が用意しているとき、あえて厳しいほうを選ぶ」**のが、この版を通しての方針になります。

### テスト

```php
#[TestDox('BOM 付きの CSV を列名で読み込む')]
public function testBom付きのCsvを列名で読み込む(): void
{
    $path = $this->csv("\u{FEFF}身長,体重,年代,派閥\n170,60,20,きのこ\n");

    $this->assertEquals([new Person(170, 60, 20, Chapter01::KINOKO)], Chapter01::loadPeople($path));
}

#[TestDox('数値でない値があれば読み込めない')]
public function test数値でない値があれば読み込めない(): void
{
    $path = $this->csv("身長,体重,年代,派閥\n高い,60,20,きのこ\n");

    $this->expectException(InvalidArgumentException::class);
    $this->expectExceptionMessage('身長 を数値として読めません: 高い');

    Chapter01::loadPeople($path);
}

#[TestDox('列が無ければ読み込めない')]
public function test列が無ければ読み込めない(): void
{
    $path = $this->csv("身長,体重,年代\n170,60,20\n");

    $this->expectException(InvalidArgumentException::class);
    $this->expectExceptionMessage('列がありません: 派閥');

    Chapter01::loadPeople($path);
}
```

`assertEquals` と `assertSame` の使い分けに注意してください。**`assertSame` はオブジェクトの同一性（同じインスタンスか）を見ます**。別々に `new Person(...)` した 2 つは、中身が同じでも `assertSame` では等しくありません。中身の比較には `assertEquals` を使います。Ruby の `Data` や Elixir のマップが値で比較されるのとは違い、PHP のオブジェクトは既定で参照の比較になります（`==` は中身、`===` は同一性）。

一時ファイルは `tearDown` で片付けます。

```php
/** @var list<string> 後始末するファイル */
private array $paths = [];

private function csv(string $contents): string
{
    $path = tempnam(sys_get_temp_dir(), 'kvst') . '.csv';
    file_put_contents($path, $contents);
    $this->paths[] = $path;

    return $path;
}

protected function tearDown(): void
{
    foreach ($this->paths as $path) {
        @unlink($path);
    }

    $this->paths = [];
}
```

## 1.9 特徴量と正解ラベルに分ける

```php
/**
 * 人物のリストを特徴量と正解ラベルに分ける。
 *
 * @param list<Person> $people
 *
 * @return array{list<Features>, list<string>}
 */
public static function splitFeaturesAndLabels(array $people): array
{
    $features = array_map(
        static fn (Person $person): Features => new Features($person->height, $person->weight, $person->ageGroup),
        $people,
    );
    $labels = array_map(static fn (Person $person): string => $person->faction, $people);

    return [$features, $labels];
}
```

戻り値の `array{list<Features>, list<string>}` は PHPStan の記法で、「1 番目が特徴量のリスト、2 番目がラベルのリストである 2 要素の配列」を意味します。PHP にタプルはありませんが、**配列の形を型として書ける**ので、使う側でも型が効きます。

```php
[$x, $t] = Chapter01::splitFeaturesAndLabels($people);
```

分割代入で受け取ります。`$x` が `list<Features>`、`$t` が `list<string>` であることを PHPStan が知っているので、`$x[0]->ageGroup` と書けば補完も検査も効きます。

`static fn (...) => ...` はアロー関数です。`static` を付けると `$this` を取り込まないので、意図しない参照を避けられます。

## 1.10 実データで正解率を表示する

### データが無ければテストを外す

実データは配布物なのでリポジトリに含まれません。CI にも置きません。したがって**学習データが無い環境では、実データを使うテストを外す**必要があります。

PHPUnit では `#[Group]` 属性で印を付け、実行時に除外します。

```php
#[Group('data')]
#[TestDox('実データでルールによる判定の正解率を求める')]
public function test実データでルールによる判定の正解率を求める(): void
{
    if (!Dataset::exists('KvsT.csv')) {
        $this->markTestSkipped('学習データがありません: ' . Dataset::path('KvsT.csv'));
    }

    $people = Chapter01::loadPeople(Dataset::path('KvsT.csv'));
    [$x, $t] = Chapter01::splitFeaturesAndLabels($people);
    $predictions = array_map(Chapter01::predictByRule(...), $x);

    $this->assertEqualsWithDelta(0.7368, Chapter01::accuracy($predictions, $t), 1e-4);
}
```

データの置き場は環境変数で差し替えられるようにします。

```php
final class Dataset
{
    /** 実データの置き場をテストや CI から差し替えるための環境変数の名前。 */
    public const string ENV_NAME = 'ML_DATA_DIR';

    /** 既定の置き場。テストは apps/php で走るので、相対パスで apps/data に届く。 */
    public const string DEFAULT_DIR = '../data/sukkiri-ml';

    /**
     * 学習データのディレクトリを返す。
     *
     * 環境変数はテストで差し替えられるように引数で受け取る。
     *
     * @param array<string, string>|null $env
     */
    public static function dir(?array $env = null): string
    {
        $env ??= getenv();
        $value = $env[self::ENV_NAME] ?? '';

        return $value === '' ? self::DEFAULT_DIR : $value;
    }
}
```

`getenv()` を直接呼ばずに引数で受け取るのは、テストのためです。環境変数を書き換えるテストは他のテストに影響しますが、引数なら値を渡すだけで済みます。

データがある環境とない環境の両方で走らせます。

```bash
php vendor/bin/phpunit --testdox
```

```text
 ✔ 実データでルールによる判定の正解率を求める

OK (20 tests, 30 assertions)
```

```bash
ML_DATA_DIR=/nonexistent php vendor/bin/phpunit --testdox
```

```text
 ↩ 実データでルールによる判定の正解率を求める

OK, but some tests were skipped!
Tests: 20, Assertions: 29, Skipped: 1.
```

`↩` がスキップされたテストです。**スキップされた数がはっきり表示される**ので、「テストが減っていることに気づかない」事故を避けられます。[Elixir 版](../elixir/01-machine-learning-and-first-test.md) の「1 excluded」と同じ考え方です。

`#[Group('data')]` を付けてあるので、そもそも走らせたくない場面では `--exclude-group data` でまとめて外せます。

### 結果を表示する

```php
/** 実データでルールによる判定の正解率を表示する。 */
public static function run(?string $csvFile = null): string
{
    $people = self::loadPeople($csvFile ?? Dataset::path('KvsT.csv'));
    [$x, $t] = self::splitFeaturesAndLabels($people);
    $predictions = array_map(self::predictByRule(...), $x);

    return sprintf(
        "データ件数: %d\nルールによる判定の正解率: %.4f\n",
        count($people),
        self::accuracy($predictions, $t),
    );
}
```

`echo` せずに文字列を返している点が大事です。**返り値ならテストで確かめられます。**

```php
#[TestDox('件数と正解率を表示する')]
public function test件数と正解率を表示する(): void
{
    $path = $this->csv("身長,体重,年代,派閥\n170,60,20,きのこ\n160,50,30,きのこ\n");

    $this->assertSame("データ件数: 2\nルールによる判定の正解率: 0.5000\n", Chapter01::run($path));
}
```

実データで動かします。

```bash
php -r 'require "vendor/autoload.php"; echo GettingStartedMl\Chapter01::run();'
```

```text
データ件数: 19
ルールによる判定の正解率: 0.7368
```

**0.7368 は [Python 版](../python/01-machine-learning-and-first-test.md) をはじめとするほかの 12 言語版とすべて一致します。** 19 人のうち 14 人を正しく判定できた、ということです。人間が考えた素朴なルールでも 7 割は当たります。第 3 章で決定木を学習させると、この値がどう変わるかを見ます。

## 1.11 リファクタリング

### 整形

PHP-CS-Fixer で整形を検査します。基準は PSR-12 に、この版で使う書き方を足したものです。

```php
return (new PhpCsFixer\Config())
    ->setRiskyAllowed(true)
    ->setRules([
        '@PSR12' => true,
        'declare_strict_types' => true,
        'array_syntax' => ['syntax' => 'short'],
        'ordered_imports' => ['sort_algorithm' => 'alpha'],
        'no_unused_imports' => true,
        'single_quote' => true,
        'trailing_comma_in_multiline' => true,
    ])
    ->setFinder($finder);
```

`declare_strict_types` を規則に入れておくと、**書き忘れたファイルを整形の検査が見つけてくれます**。1 ファイルでも書き忘れると、そこだけ型が緩くなってしまうので、人の注意力ではなく道具に守らせます。

```bash
php vendor/bin/php-cs-fixer check --diff
```

```text
Found 0 of 7 files that can be fixed in 0.132 seconds, 18.00 MB memory used
```

**違反があるときの終了コードは 8 です**（1 ではありません）。わざと崩したファイルを置いて確かめました。Elixir の Credo が 4 だったのと同じで、終了コードの癖は壊してみるまで分かりません。

### 静的解析

PHPStan をレベル 9 で走らせます。上限は 10 ですが、10 は `mixed` の扱いがさらに厳しく、本シリーズの題材では実りが少ないので 1 つ下にしました。

```neon
parameters:
    # 上限は 10（PHPStan 2.2）。その 1 つ下の 9 で、配列の形まで検査させる（ADR 013 の「型は使う」）。
    level: 9
    paths:
        - src
        - tests
        - tools
```

```bash
php vendor/bin/phpstan analyse --no-progress
```

```text
 [OK] No errors
```

レベル 9 は、`mixed` 型の値を検査なしで使うことまで禁じます。PHP の関数は `false` を返したり `mixed` を返したりするものが多いので、このレベルで通すには `fgetcsv` の戻り値をきちんと調べる必要があります。**先ほどの `if ($header === false)` や `array_map(strval(...), $header)` は、レベル 9 を通すために書いたコードでもあります。** 型の検査が、そのまま「起こりうる失敗を数え上げる」作業になっています。

テストコードも検査の対象に入れています。ここで 1 つ注意があります。**レベル 8 以上では `assertTrue(true)` のような無意味な表明も指摘されます**（「常に真である」）。テストの書き方を静的解析に合わせる必要があるということで、これは型を使い切る方針の代償です。

違反があるときの終了コードは 1 です。

### カバレッジ——しきい値は自分で判定する

pcov を入れたので、カバレッジを取れます。

```bash
php vendor/bin/phpunit --coverage-clover build/clover.xml
```

ここで PHP のもう 1 つの落とし穴に当たります。**PHPUnit には「カバレッジが N% を下回ったら失敗させる」機能がありません。** Elixir の `mix test --cover` には既定で 90% のしきい値があり、Rust の `cargo llvm-cov` にもオプションがあります。PHP では自分で判定します。

```php
<?php

declare(strict_types=1);

// PHPUnit には最低カバレッジのしきい値の機能が無いので、clover の XML を読んで自分で判定する（ADR 013）。
// 使い方: php tools/coverage-threshold.php build/clover.xml 90

$path = $argv[1] ?? 'build/clover.xml';
$threshold = (float) ($argv[2] ?? '90');

$xml = simplexml_load_file($path);

if ($xml === false || !isset($xml->project->metrics)) {
    fwrite(STDERR, "カバレッジの結果を読めません: {$path}\n");
    exit(1);
}

$metrics = $xml->project->metrics;
$statements = (int) $metrics['statements'];
$covered = (int) $metrics['coveredstatements'];

$rate = 100.0 * $covered / $statements;

printf("行カバレッジ: %.2f%% (%d/%d), しきい値: %.2f%%\n", $rate, $covered, $statements, $threshold);

if ($rate < $threshold) {
    fwrite(STDERR, sprintf("カバレッジがしきい値を下回りました: %.2f%% < %.2f%%\n", $rate, $threshold));
    exit(3);
}
```

clover の XML は `project/metrics` に全体の集計を持っているので、2 つの属性を読むだけで済みます。

```bash
php tools/coverage-threshold.php build/clover.xml 90
```

```text
行カバレッジ: 100.00% (61/61), しきい値: 90.00%
```

わざとテストされないコードを足して、しきい値割れを確かめました。

```text
行カバレッジ: 87.14% (61/70), しきい値: 90.00%
カバレッジがしきい値を下回りました: 87.14% < 90.00%
```

終了コードは 3 にしました。**この数字に決まりはありません。**「しきい値は言語の標準ではなくプロジェクトの約束である」ことが、PHP ではむき出しになっています。この話は第 6 章（タスクランナーと CI/CD）で改めて扱います。

なお `<source><include><directory>src</directory></include></source>` を設定してあるので、**一度も読み込まれなかったファイルもカバレッジの集計に入ります**。これが無いと「テストしていないファイルは分母にも入らない」ため、カバレッジが実態より高く出ます。

### まとめて検査する

4 つの検査を `composer.json` のスクリプトにまとめます。

```json
"scripts": {
    "format": "php-cs-fixer check --diff",
    "lint": "phpstan analyse --no-progress",
    "test": "phpunit --exclude-group data",
    "coverage": [
        "phpunit --coverage-clover build/clover.xml",
        "php tools/coverage-threshold.php build/clover.xml 90"
    ],
    "check": [
        "@format",
        "@lint",
        "@coverage"
    ]
}
```

リポジトリのルートからは次で走ります。

```bash
npx gulp apps:check:php
```

```text
Found 0 of 7 files that can be fixed in 0.132 seconds, 18.00 MB memory used
 [OK] No errors
PHPUnit 11.5.56 by Sebastian Bergmann and contributors.

Runtime:       PHP 8.4.16 with PCOV 1.0.11

....................                                              20 / 20 (100%)

OK (20 tests, 30 assertions)

行カバレッジ: 100.00% (61/61), しきい値: 90.00%
```

4 つとも、わざと壊して落ちることを確かめました。

| 検査 | 違反したときの終了コード |
|------|----------------------|
| PHP-CS-Fixer（整形） | **8** |
| PHPStan（静的解析） | 1 |
| PHPUnit（テスト） | 1 |
| カバレッジのしきい値（自作） | 3（自分で決めた） |

同じ内容を CI（`.github/workflows/php-ci.yml`）でも走らせます。詳しくは第 6 章で扱います。

## 1.12 まとめ

この章では、ルールによる判定と正解率を TDD で実装し、実データで 0.7368 という値を得ました。PHP に固有の論点は次のとおりです。

1. **型を使い切る** — `declare(strict_types=1)` で実行時に効かせ、PHPStan のレベル 9（上限は 10）で配列の形まで検査する。[Ruby 版](../ruby/index.md) が RBS・Steep を使わないと決めたのと正反対の選択。同じ動的型付けの言語でも、型の道具が言語本体にどこまで入っているかで手触りが変わる
2. **実行時の型と静的解析の型が分かれている** — 引数の `string` は実行時に効き、`list<string>` は PHPStan だけが見る。この二層構造が PHP の漸進的な型付けの姿
3. **値は `readonly class` で表す** — コンストラクタプロモーションで 1 か所に書ける。ただし**比較は既定で参照の同一性**なので、テストでは `assertEquals`（中身）と `assertSame`（同一性）を使い分ける
4. **緩い変換をあえて避ける** — `(int) '高い'` は 0 を返して落ちない。`filter_var(..., FILTER_VALIDATE_INT)` で厳密に検査する。比較も `==` ではなく `===` で書く
5. **文字列はバイト列である** — BOM を `substr($text, 3)` で取り除けるのはそのため。`fgetcsv` は BOM を取り除かない
6. **`escape: ''` を明示する** — PHP の CSV 関数は歴史的に RFC 4180 と違う解釈をする。PHP 8.4 からは明示すれば標準どおりになる
7. **カバレッジは拡張がないと取れない** — xdebug か pcov を入れる必要があり、素の環境には入っていなかった。**しかも PHPUnit には最低カバレッジのしきい値の機能が無い**ので、clover の XML を読んで自分で判定する
8. **非推奨の警告を失敗にする** — `failOnDeprecation="true"` が無ければ、`string $csvFile = null` の書き方が非推奨になっていることに気づけなかった
9. **終了コードの癖は壊してみるまで分からない** — PHP-CS-Fixer は **8**、PHPStan は 1、PHPUnit は 1

**TODO リスト（この章の完了時点）**:

- [x] CSV を読み込む
- [x] 特徴量と正解ラベルに分ける
- [x] ルールで派閥を判定する
- [x] 正解率を計算する
- [x] 実データで正解率を表示する

次の章では、アヤメのデータを読み込み、欠損値を補完して、訓練データとテストデータに分けます。ここで PHP 版も [Elixir 版](../elixir/02-data-preprocessing-and-triangulation.md) と同じく**乱数生成器そのものを自作します**。`mt_rand` はほかの言語版と並びが合わないためです。`java.util.Random` と同じ 48 ビットの線形合同法を書くと、分割の結果が [Java 版](../java/index.md)・[Scala 版](../scala/index.md)・[Clojure 版](../clojure/index.md)・[Elixir 版](../elixir/index.md) と一致します。

PHP 版のほかの章は [PHP 版のトップ](index.md) から辿れます。
