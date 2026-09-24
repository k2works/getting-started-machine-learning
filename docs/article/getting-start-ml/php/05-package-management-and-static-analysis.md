---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "composer.json と composer.lock による版の固定、PSR-4 オートロード、require と require-dev、PHP-CS-Fixer の終了コード 8 と declare_strict_types、PHPStan のレベル 9（mixed・list<T>・テストコードの表明）、型を使うと決めた理由、pcov と自作のカバレッジのしきい値。"
tags: [article,getting-start-ml,php]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、何をコミットし、何をコミットしないかを決め、実験を再現するにはシードと版を固定する必要があることを確かめました。この章では、その「版を固定する」仕組みと、コードの品質を機械的に確かめる道具を整えます。

PHP 版で使う道具は次のとおりです。

| 役割 | 道具 | 設定ファイル |
|------|------|------------|
| 依存の宣言・オートロード・スクリプト | Composer | `composer.json` |
| 依存の固定 | Composer（`composer install`） | `composer.lock` |
| テストの実行 | PHPUnit | `phpunit.xml` |
| 整形 | PHP-CS-Fixer | `.php-cs-fixer.dist.php` |
| 静的解析 | PHPStan | `phpstan.neon` |
| カバレッジ | pcov ＋ PHPUnit の clover 出力 | `phpunit.xml`・`tools/coverage-threshold.php` |

ほかの言語版と比べたときの特徴が 3 つあります。

1. **道具がすべて `require-dev` に入る**。Clojure 版は cljfmt と clj-kondo が `deps.edn` の外にある独立したコマンド、Elixir 版は Credo だけが依存で `mix format` は標準でした。PHP では 3 つとも Composer の依存として入り、`vendor/bin/` に置かれます。**プロジェクトを取ってきて `composer install` を打てば、検査の道具まで同じ版がそろいます**
2. **整形と静的解析が完全に分かれている**。Ruby 版は RuboCop 1 つが両方を見ていました。PHP では PHP-CS-Fixer が **書き方の形** を、PHPStan が **型** を見ます。守備範囲が重なりません。2 つのコマンドを両方走らせる必要があります
3. **型検査を使い切る**。Ruby 版は RBS・Steep を使わないと決めましたが、PHP 版は PHPStan のレベル 9（上限の 10 の 1 つ下）を使います。**同じ動的型付けの言語で正反対の選択** をした理由は 5.5 節で説明します

## 5.2 composer.json と composer.lock によるパッケージ管理

### composer.json の全体

Composer の設定は `composer.json` に書きます。拡張子のとおり **ただの JSON** です。Elixir の `mix.exs` が実行される Elixir のコードだったのとは対照的で、Clojure の `deps.edn`（ただのデータ）に近い立ち位置です。条件分岐も計算も書けません。

```json
{
    "name": "getting-started-ml/php",
    "description": "「機械学習から始めるプログラミング入門」PHP 版のサンプル実装",
    "type": "project",
    "license": "MIT",
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
    },
    "scripts": { "...": "5.7 節" },
    "config": {
        "sort-packages": true
    }
}
```

| キー | 意味 |
|------|------|
| `require` | 実行時に必要なもの。**PHP 本体と拡張もここに書く** |
| `require-dev` | 開発時だけ必要なもの。本番のインストール（`--no-dev`）には含まれない |
| `autoload` | クラス名からファイルを探す規則 |
| `autoload-dev` | テストのための規則。`--no-dev` では生成されない |
| `config.sort-packages` | 依存を足したときに名前順に並べ替える。差分を読みやすくする |

`require` に `"php": ">=8.4"` と `"ext-mbstring": "*"` が並んでいるのが PHP らしいところです。**言語の版も拡張も「依存」として同じ場所に書きます。** 第 9 章で Shift_JIS の CSV を読むときに mbstring の `CP932` を使うので、無い環境で `composer install` が止まるように宣言してあります。第 4 章で触れたとおり、**要求を書いておくと、動かしてから気付くのではなく、入れる時点で気付けます**。

### キャレット制約 `^`

依存の版には `^` を使います。Ruby や Elixir の `~>`（悲観的バージョン制約）に当たるものです。

| 書き方 | 満たす版 |
|--------|---------|
| `^2.5` | 2.5.0 以上 3.0.0 未満 |
| `^2.5.1` | 2.5.1 以上 3.0.0 未満 |
| `^0.13` | 0.13.0 以上 **0.14.0 未満** |

`^` は「いちばん左の 0 でない数字を固定する」という規則です。メジャー版が 1 以上なら `^2.5` も `^2.5.1` もメジャー版だけを固定しますが、**0.x では扱いが変わります**。`^0.13` が 0.14 を許さないのは、セマンティックバージョニングで 0.x のマイナー版がメジャー版の役目を果たすからです。Elixir の `~> 0.13` が 1.0 未満をすべて許したのとは **違う** ので、0.x のライブラリを使うときは注意が要ります。

本シリーズはすべて `^` の 2 桁（`^2.5`・`^11.5` など）にしています。厳密な版を書かないのは、`composer.lock` があるからです。**範囲を `composer.json` に、結果を `composer.lock` に** という二段構えが Composer の（そして Bundler・Hex の）やり方です。

### require と require-dev

開発の道具を `require-dev` に置くと、どれだけ違うかを実測できます。

```bash
composer show | wc -l
composer show --no-dev | wc -l
```

```text
80
19
```

**80 個のうち 61 個が開発の道具です。** 直接書いた依存は実行時 2 つ（`rubix/ml`・`markrogoyski/math-php`）、開発時 3 つ（PHPUnit・PHPStan・PHP-CS-Fixer）しかありません。

実行時に入る 19 個を見ておきます。

```bash
composer show --no-dev
```

```text
amphp/amp                     2.6.5  A non-blocking concurrency framework f...
amphp/byte-stream             1.8.2  A stream abstraction to make working w...
amphp/parallel                1.4.4  Parallel processing component for Amp.
amphp/parser                  1.1.1  A generator parser to make streaming p...
amphp/process                 1.1.9  Asynchronous process manager.
amphp/serialization           1.1.0  Serialization tools for IPC and data s...
amphp/sync                    1.4.2  Mutex, Semaphore, and other synchroniz...
andrewdalpino/okbloomer       1.0.0  An autoscaling Bloom filter with ultra...
joomla/string                 4.0.0  Joomla String Package
markrogoyski/math-php         2.13.0 Math Library for PHP. Features descrip...
psr/log                       3.0.2  Common interface for logging libraries
rubix/ml                      2.6.0  A high-level machine learning and deep...
rubix/tensor                  3.1.0  A library and extension that provides ...
symfony/deprecation-contracts 3.7.1  A generic function and convention to t...
symfony/polyfill-mbstring     1.38.2 Symfony polyfill for the Mbstring exte...
symfony/polyfill-php80        1.37.0 Symfony polyfill backporting some PHP ...
symfony/polyfill-php82        1.38.1 Symfony polyfill backporting some PHP ...
symfony/polyfill-php83        1.41.0 Symfony polyfill backporting some PHP ...
wamania/php-stemmer           4.0.0  Native PHP Stemmer
```

`markrogoyski/math-php` 以外の 18 個は、すべて `rubix/ml` が連れてきたものです。中身を眺めると、Rubix ML が何をする道具かが見えてきます。

| 連れてくるもの | なぜ要るのか |
|--------------|------------|
| `amphp/*`（7 個） | 学習を並列に走らせるため（ランダムフォレストの木を同時に育てるなど。第 10 章） |
| `rubix/tensor` | 行列・ベクトルの計算。**純 PHP で動き、ネイティブ拡張の `tensor` は任意** |
| `andrewdalpino/okbloomer` | ブルームフィルタ。重複の検出に使う |
| `wamania/php-stemmer`・`joomla/string` | 自然言語処理の前処理（語幹の抽出）。本シリーズでは使わない |
| `symfony/polyfill-*`（4 個） | 古い PHP でも新しい関数を使えるようにする。PHP 8.4 では実質何もしない |

**使わない機能のためのライブラリも一緒に入ります。** Rubix ML は「機械学習の道具箱」として広く作られているので、自然言語処理の部品まで付いてきます。Elixir 版の Scholar が 19 個の依存で済んでいたのと比べると、同じ規模ですが中身の性格が違います。`vendor/` をコミットしない理由の 1 つでもあります。

### PSR-4 オートロード

`autoload` の設定は、**クラス名とファイルの対応の規則** です。

```json
"autoload": {
    "psr-4": {
        "GettingStartedMl\\": "src/"
    }
}
```

これで `GettingStartedMl\Chapter01` が `src/Chapter01.php`、`GettingStartedMl\Chapter03\Node` が `src/Chapter03/Node.php` に対応します。名前空間の区切りがディレクトリの区切りに、クラス名がファイル名になる、という 1 対 1 の規則です。

Ruby 版では `require_relative` を書く必要がありました。PHP 版には 1 行もありません。`vendor/autoload.php` を読み込んでおけば、**まだ読み込んでいないクラスが必要になった瞬間に、Composer が生成したオートローダーが規則どおりのファイルを探して読み込みます**。

`autoload-dev` が別になっているのは、テストのクラス（`GettingStartedMl\Tests\`）を本番のインストールに含めないためです。`composer install --no-dev` では、このオートローダーの項目が生成されません。

規則から外れた場所にファイルを置くと、**黙って「クラスが無い」と言われます**。`src/chapter01.php`（小文字）に `Chapter01` を書くと、大文字小文字を区別しないファイルシステムでは動き、区別する Linux の CI では落ちます。第 6 章の CI が Linux で走ることを思い出してください。

### composer.lock

`composer install` を走らせると `composer.lock` が作られます（第 4 章）。依存が実際に何を連れてくるかは `composer show --tree` で見られます。`rubix/ml` の枝だけを抜き出します。

```bash
composer show --tree rubix/ml
```

```text
|--amphp/parallel ^1.3
|  |--amphp/amp ^2
|  |  `--php >=7.1
|  |--amphp/byte-stream ^1.6.1
...
|--andrewdalpino/okbloomer ^1.0
|  `--php >=7.4
|--ext-json *
|--php >=7.4
|--psr/log ^1.1|^2.0|^3.0
|  `--php >=8.0.0
|--rubix/tensor ^3.1
|  `--php >=7.4
|--symfony/polyfill-mbstring ^1.0
|  |--ext-iconv *
|  `--php >=7.2
`--wamania/php-stemmer ^4.0
   |--joomla/string >=2.0.1
   |  |--php ^8.3.0
   |  |--symfony/deprecation-contracts ^2|^3
   |  |  `--php >=8.1
   |  `--symfony/polyfill-mbstring ^1.31.0
```

木の節に `php >=7.1` や `ext-json *` が **依存として並んでいる** ことに注目してください。Composer にとって、PHP 本体も拡張も、ライブラリと同じ「解決すべき制約」です。`rubix/ml` は PHP 7.4 以上、`joomla/string` は PHP 8.3 以上を要求していて、Composer はこれらすべてを同時に満たす解を探します。満たせなければ、どの要求が衝突しているかを表示して止まります。

`symfony/polyfill-mbstring` が 2 回（`rubix/ml` から `^1.0`、`joomla/string` から `^1.31.0`）現れています。Composer は **すべての要求を同時に満たす版を 1 つ選びます**（実測では 1.38.2）。Maven の「いちばん近い宣言が勝つ」とは違い、Hex と同じ制約充足の解決です。「たまたま近い宣言が勝って古い版が入っていた」という事故が起きにくい設計です。

## 5.3 PHP と拡張の版

版を決める場所は、3 つに分かれます。

| 対象 | 決める場所 | 実測値 |
|------|----------|-------|
| PHP（言語） | `composer.json` の `require` に **要求** を書き、実体は Nix の開発環境 | 8.4.16 |
| Composer（ビルドの道具） | Nix の開発環境 | 2.9.2 |
| pcov（カバレッジの拡張） | Nix の開発環境（`php.withExtensions`） | 1.0.11 |

Elixir 版と同じく、`composer.json` に書ける `">=8.4"` は要求であって固定ではありません。8.4 と 8.5 のどちらが使われるかは、環境に入っているものが決めます。

Nix の開発環境に入ると、こう表示されます。

```bash
nix develop .#php
```

```text
PHP development environment activated
  - PHP: PHP 8.4.16 (cli) (built: Dec 16 2025 16:03:34) (NTS)
  - Composer: Composer version 2.9.2 2025-11-19 21:57:25
```

第 1 章で見たとおり、**この 2 行がそろうまでに 1 つ直しました**。もとの環境定義は `php`（8.4.16）と `php83Packages.composer`（PHP 8.3.29 の上で動く）を混ぜていて、php 本体と composer が動く PHP の版が食い違っていました。Clojure 版の「`java` は 25 だが Clojure は JDK 21 で動く」、Elixir 版の「`erl` は OTP 28 だが Elixir は OTP 27 で動く」と同じ形です。

```bash
composer --version
```

```text
Composer version 2.9.2 2025-11-19 21:57:25
PHP version 8.4.16 (/nix/store/.../php-with-extensions-8.4.16/bin/php)
```

**`composer --version` は自分が動いている PHP の版と、その実体の場所まで表示します。** これは親切な設計です。Elixir 版では `elixir --version` の 1 行目を読むという知識が要りましたが、Composer は聞かなくても答えます。「版はシェルではなく走っているプログラムに聞く」という第 5 章共通の教訓を、道具の側が先回りして実践しています。

PHP 版ではもう 1 つ、**拡張の版** が再現の対象に入ります。

```bash
php -m | grep -i pcov
```

```text
pcov
```

pcov が無ければカバレッジは取れません（第 1 章）。Nix の `php.withExtensions` で足したので、`shell.nix` が拡張まで固定していることになります。`composer.json` の `ext-mbstring` と合わせて、**「どの拡張が入っているか」が 2 か所に書かれている** 状態です。要求は `composer.json`、実体は `shell.nix`、という分担です。

## 5.4 コードスタイル — PHP-CS-Fixer

### 設定ファイル

PHP-CS-Fixer の設定は `.php-cs-fixer.dist.php` に書きます。**設定ファイル自体が PHP のコード** で、`Config` オブジェクトを返します。

```php
<?php

declare(strict_types=1);

// PSR-12 を基準に、この版で使う書き方（strict_types の宣言・短い配列・import の整理）を足す。
$finder = PhpCsFixer\Finder::create()
    ->in([__DIR__ . '/src', __DIR__ . '/tests', __DIR__ . '/tools']);

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

`@PSR12` は PHP-FIG が定めた標準のコーディング規約です。Elixir の `mix format` が「言語に標準で付いてきて設定するところが無い」道具だったのとは違い、PHP-CS-Fixer は **何百もの規則から選ぶ** 道具です。そのぶん「何を選んだか」を説明する必要があります。

| 規則 | 選んだ理由 |
|------|----------|
| `@PSR12` | 業界の標準。議論を起こさないための土台 |
| `declare_strict_types` | **書き忘れを道具に見つけさせる**（後述） |
| `array_syntax: short` | `array(...)` ではなく `[...]` に統一する |
| `ordered_imports` | `use` の並び順を名前順に固定し、差分を読みやすくする |
| `no_unused_imports` | 使っていない `use` を消す。**PHPStan が見ないところ**を補う |
| `single_quote` | 変数を埋め込まない文字列は `'...'` にする |
| `trailing_comma_in_multiline` | 複数行の配列・引数の末尾にカンマを付ける。行を足したときの差分が 1 行で済む |

`setRiskyAllowed(true)` が要るのは、`declare_strict_types` が **振る舞いを変えうる** 規則だからです。PHP-CS-Fixer は、整形だけの規則と、実行結果が変わりうる規則を区別しています。`declare(strict_types=1)` を足すと、それまで暗黙に変換されていた引数が `TypeError` になるかもしれません。「危ういと分かったうえで使う」という明示が `setRiskyAllowed` です。

それでも入れているのは、**1 ファイルでも書き忘れると、そこだけ型が緩くなる** からです。PHP の `strict_types` はファイル単位の宣言で、書かなければ `'170'` が `int` の引数に通ります。人の注意力ではなく道具に守らせます。

### 実行と終了コード

```bash
# 検査する（CI 向け）
php vendor/bin/php-cs-fixer check --diff

# 整形する
php vendor/bin/php-cs-fixer fix
```

`check` は、整形されていないファイルがあると失敗します。わざと崩したファイルを置いて実行すると、こうなりました。

```php
<?php

namespace GettingStartedMl;

final class Violation
{
    public static function f( int $x ): int
    {
        return $x+1;
    }
}
```

```bash
php vendor/bin/php-cs-fixer check --diff
echo "EXIT=$?"
```

```text
   1) src/Violation.php
      ---------- begin diff ----------
@@ -1,11 +1,13 @@
 <?php
 
+declare(strict_types=1);
+
 namespace GettingStartedMl;
 
 final class Violation
 {
-    public static function f( int $x ): int
+    public static function f(int $x): int
     {
-        return $x+1;
+        return $x + 1;
     }
}

      ----------- end diff -----------


Found 1 of 55 files that can be fixed in 1.142 seconds, 38.00 MB memory used
EXIT=8
```

3 つとも捕まりました。引数の丸かっこの中の空白、`+` の前後の空白、そして **`declare(strict_types=1)` の書き忘れ** です。差分は統一 diff の形式で、前後の変わらない行も一緒に表示されます。`fix` を走らせれば、そのとおりに直ります。

**終了コードは 8 です。** 0 でも 1 でもありません。PHP-CS-Fixer は、失敗の種類ごとに違うビットを立てた値を返します。

| 値 | 意味 |
|----|------|
| 1 | 一般的なエラー |
| 4 | 整形の途中で例外が起きた |
| 8 | **整形が必要なファイルがあった**（`check` のとき） |
| 16 | 設定ファイルにエラーがある |
| 32 | 規則が非推奨 |

Elixir の Credo が指摘の分類ごとに 1・2・4・8・16 を返したのと同じ設計です。「終了コードが 0 でなければ失敗」と扱っている限り問題は起きませんが、**「終了コードが 1 かどうか」で判定する仕組みに入れると、整形の失敗を見落とします**。第 6 章で、4 つの検査の終了コードを並べて確かめます。

## 5.5 静的解析 — PHPStan

### 整形と静的解析は重ならない

Elixir 版では、静的解析が「コンパイラ（機械的に決まるもの）」と「Credo（好みが入るもの）」に分かれていました。PHP の分かれ方は違います。

| 道具 | 見るもの | 失敗の終了コード |
|------|---------|----------------|
| PHP-CS-Fixer | 空白・改行・`use` の並び・`declare(strict_types=1)` の有無・使っていない `use` | 8 |
| PHPStan | 型の食い違い・存在しないメソッドの呼び出し・到達しないコード・`mixed` の乱用 | 1 |

**線は「文字の並びを見るか、意味を見るか」です。** そして、この 2 つは **重なりません**。上で PHP-CS-Fixer が捕まえた `declare(strict_types=1)` の書き忘れに、PHPStan は何も言いません。型の宣言としては正しいコードだからです。逆に、PHPStan が捕まえる型の食い違いを PHP-CS-Fixer は見ません。

Ruby 版の RuboCop は 1 つで両方を見ていました。PHP では **2 つとも走らせないと穴が開きます**。

### レベル 9 — `mixed` を使わせない

PHPStan の設定は `phpstan.neon` に書きます。NEON は YAML に似た Composer 周辺の設定形式です。

```neon
parameters:
    # 上限は 10（PHPStan 2.2）。その 1 つ下の 9 で、配列の形まで検査させる（ADR 013 の「型は使う」）。
    level: 9
    paths:
        - src
        - tests
        - tools
```

PHPStan のレベルは 0 から始まり、上に行くほど厳しくなります。上限は版によって変わるので、実測で確かめました。この版（PHPStan 2.2.15）では `level: 11` を指定すると「設定ファイルが見つからない」というエラーになり、10 までが有効でした。本シリーズが使っているのは **9** です。10 は「暗黙の `mixed` と、`mixed` と明示的に書いたものを区別する」レベルです。

この設定には、もともと「最高レベル」というコメントが付いていました。**実測して初めて、それが正確でないと分かりました**（9 は上限ではありません）。いまのコメントは実測に合わせて直したものです。設定に書いたコメントも、コードと同じように実測で確かめないと古くなります。

**レベル 9 で何が増えるのか** を、同じファイルを 2 つのレベルで検査して確かめました。

```php
<?php

declare(strict_types=1);

namespace GettingStartedMl;

final class Violation
{
    /** JSON の中の身長を数に直す。 */
    public static function height(string $json): float
    {
        $decoded = json_decode($json, true);

        return (float) $decoded['身長'];
    }
}
```

```bash
php vendor/bin/phpstan analyse --no-progress --level 8 src/Violation.php
```

```text
 [OK] No errors
```

```bash
php vendor/bin/phpstan analyse --no-progress --level 9 src/Violation.php
```

```text
 ------ --------------------------------------- 
  Line   Violation.php                          
 ------ --------------------------------------- 
  14     Cannot access offset '身長' on mixed.  
         🪪  offsetAccess.nonOffsetAccessible   
         at src/Violation.php:14                
  14     Cannot cast mixed to float.            
         🪪  cast.double                        
         at src/Violation.php:14                
 ------ --------------------------------------- 


 [ERROR] Found 2 errors
```

**レベル 8 は通し、レベル 9 は 2 つ指摘しました。** `json_decode` の戻り値は `mixed` です。配列かもしれないし、文字列かもしれないし、`null` かもしれません。レベル 9 は「`mixed` の値を、中身を確かめずに使ってはいけない」という規則を足します。

これは PHP にとって大きな線引きです。PHP の標準関数には `mixed` を返すもの、失敗すると `false` を返すものが山ほどあります。`json_decode`・`fgetcsv`・`simplexml_load_file`・`getenv`——本シリーズが使うものだけでもこれだけあります。レベル 9 を通すには、**そのすべてで「失敗したとき」を書く** ことになります。

第 1 章の CSV の読み込みに `if ($header === false)` があったのも、`tools/coverage-threshold.php` に `if ($xml === false || !isset($xml->project->metrics))` があったのも、このためです。**型の検査が、そのまま「起こりうる失敗を数え上げる」作業になっています。** 例外処理を書き忘れないための仕組みとして働いている、と言い換えてもよいでしょう。

### `list<T>` と `array<K, V>` の区別

PHP の配列は、連番の配列と連想配列の両方を兼ねています。PHPStan はこれを型として区別します。

| 型 | 意味 |
|----|------|
| `array<int, string>` | 整数をキーとする配列。**キアは飛んでいてもよい** |
| `list<string>` | 0 から始まる連番のキーを持つ配列。**穴が無い** |
| `array<string, float>` | 文字列をキーとする連想配列 |
| `array{list<Features>, list<string>}` | 2 要素のタプル。分解代入で受ける |

この区別が効く場面を実測しました。第 2 章の「正解ラベルの列を除いた列の名前を返す」という処理です。

```php
/**
 * 正解ラベルの列を除いた列の名前を返す。
 *
 * @param list<string> $columns
 *
 * @return list<string>
 */
public static function featureColumns(array $columns, string $targetColumn): array
{
    return array_filter($columns, static fn (string $c): bool => $c !== $targetColumn);
}
```

```text
 ------ ------------------------------------------------------------------- 
  Line   Violation.php                                                      
 ------ ------------------------------------------------------------------- 
  18     Method GettingStartedMl\Violation::featureColumns() should return  
         list<string> but returns array<int<0, max>, string>.               
         💡  array<int<0, max>, string> might not be a list.                
 ------ ------------------------------------------------------------------- 
```

**`array_filter` はキーを保ちます。** 真ん中の要素を落とすと `[0 => 'a', 2 => 'c']` になり、1 が抜けます。連番だと思って `$columns[1]` を読んだら、そこには別の列があるか、何も無いかです。PHPStan は「`int<0, max>` をキーとする配列ではあるが、list とは限らない」と正確に言っています。

直し方は `array_values()` で包み直すことです。実際のコードはこうなっています。

```php
$columns = array_values(
    array_filter($table->columns, static fn (string $c): bool => $c !== $targetColumn),
);
```

`array_values` はキーを 0 から振り直します。**実行時の振る舞いを変えるコードを、型の検査に言われて足した** わけです。そしてこれは、型のためだけの飾りではありません。この配列は第 2 章で特徴量の列の順序として使われるので、穴が空いていると後の章で実際に壊れます。**静的解析が、実行してみないと分からなかったはずのバグを先に見つけています。**

同じ区別は `@param` にも要ります。`array $row` と書くだけではレベル 9 を通りません。

```text
  10     Method GettingStartedMl\Violation::height() has parameter $row with
         no value type specified in iterable type array.
         🪪  missingType.iterableValue
```

「配列の中に何が入っているか」を書け、という指摘です。`@param array<string, string> $row` と書けば通ります。

### テストコードも検査の対象にする

`phpstan.neon` の `paths` には `tests` と `tools` も入れています。テストコードを検査から外すプロジェクトは多いのですが、外すと **テストの中で型を間違えても誰も教えてくれません**。

ただし代償があります。

```php
public function test二倍になる(): void
{
    $values = Violation::twice([1.0, 2.0]);
    $this->assertIsFloat($values[0]);
}
```

```text
  15     Call to method PHPUnit\Framework\Assert::assertIsFloat() with float
         will always evaluate to true.
         🪪  method.alreadyNarrowedType
         💡  Because the type is coming from a PHPDoc, you can turn off this
         check by setting treatPhpDocTypesAsCertain: false in your phpstan.neon.
```

**「常に真である」と指摘されます。** `twice()` が `list<float>` を返すと宣言しているのだから、その要素が `float` なのは検査するまでもない、という理屈です。`assertIsArray`・`assertIsString`・`assertNotNull` なども同じように指摘されます。

これは正しい指摘です。型の表明は **静的解析の仕事** であって、テストの仕事ではありません。テストが確かめるべきなのは「型」ではなく「値」です。

```php
// 指摘される: 型を確かめている
$this->assertIsFloat($values[0]);

// 指摘されない: 値を確かめている
$this->assertSame([2.0, 4.0], $values);
```

型を使い切ると決めた以上、**テストの書き方もそれに合わせます**。動的型付けの言語のテストには「まず型を確かめる」という習慣がありますが、静的解析がその役を引き受けたなら、テストはもう 1 段先を確かめるべきです。ADR に「テストの書き方をそれに合わせる」と書いたのは、この意味です。

### 型を使う——Ruby 版と正反対の選択

Elixir 版と Ruby 版は、型検査の道具を **使わない** と決めました。「型を宣言しない言語で、テストが唯一の安全網になることを示す」ためです。PHP 版は正反対の選択をしています。理由は 2 つあります。

1 つめは、**PHP の型が言語本体に入っている** ことです。

```php
public static function predictByRule(Features $features): string
```

引数の `Features` と戻り値の `string` は、PHPDoc のコメントではなく **言語の構文** です。`declare(strict_types=1)` があれば実行時に検査され、違えば `TypeError` で止まります。Ruby で同じことをするには RBS という別のファイルを書き、Steep という別の道具を走らせる必要があります。PHP では **書かないほうが不自然** です。

2 つめは、**二層構造そのものが PHP の性格だ** ということです。

| 層 | 書き方 | いつ効くか | 誰が見るか |
|----|-------|----------|----------|
| 言語の型 | `string $name`・`: float` | **実行時** | PHP のランタイムと PHPStan |
| PHPDoc の型 | `@param list<string> $columns` | 実行時には効かない | **PHPStan だけ** |

PHP の言語の型には `list<string>` を書けません。書けるのは `array` までです。「0 から始まる連番の文字列の配列」を表すには、コメントに書いて静的解析に読ませるしかありません。**実行時に効く型と、静的解析だけが見る型が、同じコードの中に同居しています。**

この二層構造は、[Python 版](../python/index.md) の型ヒント（実行時には効かず mypy が見る）や [TypeScript 版](../typescript/index.md)（実行時には消える）と似ていますが、**PHP は下の層が実行時に効く** ところが違います。3 つの言語版を並べると、漸進的な型付けの実装が 3 通りあることが見えてきます。

| 言語版 | 実行時に効く型 | 静的解析だけの型 |
|-------|--------------|----------------|
| Python 版 | 無し（型ヒントは注釈） | 型ヒント全部（mypy） |
| TypeScript 版 | 無し（コンパイル時に消える） | 型注釈全部 |
| **PHP 版** | **引数・戻り値・プロパティ・定数の型宣言** | **PHPDoc の総称型（`list<T>`・`array{...}`）** |
| Ruby 版 | 無し | **使わないと決めた** |

Ruby 版と PHP 版を並べたことで、「動的型付けの言語」というくくりが大ざっぱすぎることが見えます。**型の道具が言語本体にどこまで入っているかで、書くコードの手触りが変わります。**

### 指摘を抑制しない

PHPStan には指摘を抑制する仕組みがいくつもあります。`@phpstan-ignore` のコメント、ベースライン（既存の指摘をまとめて無視する仕組み）、`treatPhpDocTypesAsCertain: false` の設定。本シリーズでは、抑制ではなく **直す** ことにしています。抑制を許すと、抑制されたコードが増えていき、検査の意味が薄れていきます。とくにベースラインは、厳しいレベルを後から既存のコードに導入するときの現実的な手段ですが、**最初からレベル 9 で始めれば要りません**。第 4 章で「品質チェックは守るコードが少ないうちに入れる」と書いたのは、このためでもあります。

余談として、PHPStan 2.x には面白い仕掛けがあります。環境変数を見て **AI エージェントが実行していると判断したとき** だけ、指摘の前にこんな文章を出します。

```text
The error usually indicates a real bug or incorrect type in the code. Fix the underlying cause, do not just make the error go away.
Do not add `@phpstan-ignore` comments, `@phpstan-ignore-next-line` comments, or baseline entries to suppress the error.
Do not use assert() or inline @var PHPDoc tag to override PHPStan's inferred type.
Do not add type casts just to silence errors.
```

人が実行したときには出ません。「エラーを消すのではなく原因を直せ」「抑制のコメントを足すな」「黙らせるための型変換を足すな」。**道具の作者が、抑制の誘惑がどこにあるかをよく知っている** ことが伝わってきます。書かれている内容は、人が読んでも同じように正しいものです。

## 5.6 コードカバレッジ — pcov と自作のしきい値

### カバレッジには拡張が要る

カバレッジは、テストがコードのどこを通ったかの割合です。第 1 章で見たとおり、**PHP はカバレッジの計測を言語本体では行いません**。xdebug か pcov という拡張に任せています。Elixir の `mix test --cover`（Erlang の `:cover` が標準）や Ruby の SimpleCov（純 Ruby のライブラリ）とは事情が違います。

```bash
php vendor/bin/phpunit --coverage-clover build/clover.xml
```

```text
Runtime:       PHP 8.4.16 with PCOV 1.0.11
```

PHPUnit は起動時に、どのカバレッジドライバを使っているかを表示します。**この行が `PHP 8.4.16` だけなら、カバレッジは取れていません。** 出力を読む習慣が、そのまま設定が効いていることの確認になります。

`phpunit.xml` の `<source>` の設定にも意味があります。

```xml
<source>
    <include>
        <directory>src</directory>
    </include>
</source>
```

これが無いと、**一度も読み込まれなかったファイルが分母に入りません**。テストを 1 行も書いていないクラスは、カバレッジの計算から消えてしまい、数字が実態より高く出ます。「テストしていないものほど数字に出ない」という、いちばん困る性質です。

### しきい値は自分で判定する

ここで PHP のもう 1 つの落とし穴に当たります。**PHPUnit には「カバレッジが N% を下回ったら失敗させる」機能がありません。**

| 言語版 | しきい値の仕組み |
|-------|----------------|
| Elixir 版 | `mix test --cover` に組み込み（既定 90%。ただし `summary:` の中に書かないと黙って無視される） |
| Ruby 版 | SimpleCov の `minimum_coverage` |
| Rust 版 | `cargo llvm-cov --fail-under-lines` |
| **PHP 版** | **無い。自分で書く** |

そこで、clover の XML を読んで判定する短いスクリプトを `tools/coverage-threshold.php` に置きました。

```php
<?php

declare(strict_types=1);

// PHPUnit には最低カバレッジのしきい値の機能が無いので、clover の XML を読んで自分で判定する（ADR 013）。
// 使い方: php tools/coverage-threshold.php build/clover.xml 90

$path = $argv[1] ?? 'build/clover.xml';
$threshold = (float) ($argv[2] ?? '90');

if (!is_file($path)) {
    fwrite(STDERR, "カバレッジの結果がありません: {$path}\n");
    exit(1);
}

$xml = simplexml_load_file($path);

if ($xml === false || !isset($xml->project->metrics)) {
    fwrite(STDERR, "カバレッジの結果を読めません: {$path}\n");
    exit(1);
}

$metrics = $xml->project->metrics;
$statements = (int) $metrics['statements'];
$covered = (int) $metrics['coveredstatements'];

if ($statements === 0) {
    fwrite(STDERR, "実行できる行がありません: {$path}\n");
    exit(1);
}

$rate = 100.0 * $covered / $statements;

printf("行カバレッジ: %.2f%% (%d/%d), しきい値: %.2f%%\n", $rate, $covered, $statements, $threshold);

if ($rate < $threshold) {
    fwrite(STDERR, sprintf("カバレッジがしきい値を下回りました: %.2f%% < %.2f%%\n", $rate, $threshold));
    exit(3);
}
```

clover の XML は `project/metrics` に全体の集計を持っているので、`statements` と `coveredstatements` の 2 つの属性を読むだけで済みます。**30 行で足ります。**

このスクリプト自身が `phpstan.neon` の `paths` に入っていることに注目してください（`tools`）。`simplexml_load_file` は失敗すると `false` を返し、成功しても `SimpleXMLElement` という「何でも生える」オブジェクトを返します。レベル 9 を通すために、`false` の検査と `isset` の検査を書くことになりました。**自作の道具も本体と同じ検査にかける** という方針です。

`$statements === 0` の検査は、0 除算を避けるためです。これも「型の検査が起こりうる失敗を数え上げる」例の 1 つで、レベル 9 が無ければ書き忘れていたかもしれません。

わざとしきい値を高くして、落ちることを確かめました。

```bash
php tools/coverage-threshold.php build/clover.xml 99.9
echo "EXIT=$?"
```

```text
行カバレッジ: 98.91% (1358/1373), しきい値: 99.90%
カバレッジがしきい値を下回りました: 98.91% < 99.90%
EXIT=3
```

ファイルが無いときも確かめました。

```bash
php tools/coverage-threshold.php build/no-such.xml 90
echo "EXIT=$?"
```

```text
カバレッジの結果がありません: build/no-such.xml
EXIT=1
```

**この 2 つの終了コードに決まりはありません。** 自分で決めたものです。3 を選んだのは、Elixir の `mix test --cover` がしきい値割れに 3 を返すのに合わせたからで、それ以上の根拠はありません。

だからこそ、この章でいちばん大事なことが言えます。**しきい値は言語の標準ではなく、プロジェクトの約束です。** Elixir では既定が 90% で、書き方を間違えると黙ってその既定が使われました（そして書いた 70 は捨てられました）。PHP には既定がありません。無いぶん、しきい値がどこから来た数字なのかを説明する責任が、まるごとプロジェクトの側にあります。この話は第 6 章で、4 つの検査の終了コードと合わせて改めて扱います。

### 数字の読み方

カバレッジは、合格・不合格を決める指標ではなく、**テストが通っていない場所を探すための地図** として使うのが本筋です。それでも本シリーズがしきい値 90% を入れているのは、「下がったら気付く」ための下限としてです。

しきい値を決めるときの注意は Elixir 版と同じです。**学習データが無い条件で通る値にしておかなければなりません。** 実データのテストは `markTestSkipped` で飛ばされるので、データが無い環境では `run()` のような表示の処理が通りません。データがある環境の数字でしきい値を決めると、「データが無いと落ちる CI」になってしまいます。

## 5.7 検査の 4 つを 1 行にする

ここまでの 4 つを、`composer.json` の `scripts` にまとめます。

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

Composer のスクリプトには、Elixir の Mix に無かった 2 つの便利さがあります。

1. **`vendor/bin` に道が通る**。`php-cs-fixer` と書くだけで `vendor/bin/php-cs-fixer` が走ります
2. **`@名前` でほかのスクリプトを呼べる**。`check` は 3 つのスクリプトを順に呼ぶだけの定義になります

配列で書いたスクリプトは **前が失敗したらそこで止まります**。`&&` でつないだのと同じ振る舞いです。順番にも意味があります。

1. **`format`** — いちばん速く、直し方も機械的。まずここで形をそろえる
2. **`lint`** — 形がそろったコードに対して、型を見る
3. **`coverage`** — テストを走らせ、カバレッジを測り、しきい値を判定する

`test`（`--exclude-group data`）と `coverage`（グループを外さない）を分けてあるのは、第 4 章で見たとおりです。TDD の内側のループでは `composer test` を、検査としては `composer check` を使います。

整形は検査の側に入れています（`fix` ではなく `check`）。CI で勝手にコードを書き換えるのは避け、CI は「整形されていない」と教えるだけにします。整形するのは手元の人間の仕事です。

第 6 章で見るように、この並びは Gulp のタスクと CI の両方に同じ順で書かれています。そして 4 つが本当に落ちることを、1 つずつわざと壊して確かめます。

## 5.8 まとめ

この章では、版を固定する仕組みと、品質を機械的に確かめる道具を整えました。

1. **composer.json と composer.lock** — `composer.json` はただの JSON で、依存にはキャレット制約 `^` を書く。**`^0.13` が 0.14 を許さない**のは Elixir の `~> 0.13` と違うところ。PHP 本体も拡張も同じ `require` に並ぶ
2. **`require-dev` の重み** — 実行時は 19 個、開発の道具を入れると 80 個。直接書いた 5 つに対して、Rubix ML が自然言語処理の部品まで連れてくる
3. **PSR-4 オートロード** — クラス名とファイルの対応を規則で決めるので、`require` を 1 行も書かない。規則から外れると、大文字小文字を区別する CI でだけ落ちる
4. **整形と静的解析は重ならない** — `declare(strict_types=1)` の書き忘れは PHP-CS-Fixer が捕まえ、PHPStan は何も言わない。2 つとも走らせないと穴が開く。PHP-CS-Fixer の終了コードは **8**
5. **レベル 9 は `mixed` を許さない** — 同じコードがレベル 8 では通り、レベル 9 で 2 つ指摘された。`json_decode`・`fgetcsv` のような `mixed` を返す関数の「失敗したとき」を全部書くことになる。型の検査が、起こりうる失敗を数え上げる作業になる
6. **`list<T>` と `array<K, V>`** — `array_filter` はキーを保つので list ではない。`array_values()` で包み直すという**実行時の振る舞いを変える修正**を、静的解析に言われて足した
7. **テストコードも検査する** — そのかわり `assertIsFloat` のような型の表明は「常に真」と指摘される。テストは型ではなく値を確かめる形に書き換える
8. **型を使い切る** — Ruby 版が RBS・Steep を使わないと決めたのと正反対。PHP は型宣言が言語本体に入っていて、しかも **実行時に効く型と静的解析だけが見る型の二層構造**になっている。Python 版・TypeScript 版と並べると、漸進的な型付けの実装が 3 通りあることが見える
9. **しきい値は自作する** — PHPUnit に機能が無い。clover の XML の `project/metrics` を読む 30 行で済む。終了コード 3 に決まりは無く、**しきい値は言語の標準ではなくプロジェクトの約束である**

次の章では、これらの検査を 1 つのコマンドにまとめ、GitHub Actions で自動的に実行する仕組みを作ります。そして 4 つの検査が本当に効いていることを、1 つずつ壊して確かめます。
