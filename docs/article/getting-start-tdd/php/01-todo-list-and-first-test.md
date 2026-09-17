---
type: Article
title: "第 1 章: TODO リストと最初のテスト"
description: "プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して TODO リスト を作るところから始めます。"
tags: [article, getting-start-tdd, php]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 1 章: TODO リストと最初のテスト

## 1.1 はじめに

プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して **TODO リスト** を作るところから始めます。

> TODO リスト
>
> 何をテストすべきだろうか——着手する前に、必要になりそうなテストをリストに書き出しておこう。
>
> — テスト駆動開発

## 1.2 仕様の確認

今回取り組む FizzBuzz 問題の仕様は以下の通りです。

```
1 から 100 までの数をプリントするプログラムを書け。
ただし 3 の倍数のときは数の代わりに「Fizz」と、5 の倍数のときは「Buzz」とプリントし、
3 と 5 両方の倍数の場合には「FizzBuzz」とプリントすること。
```

この仕様をそのままプログラムに落とし込むには少しサイズが大きいですね。最初の作業は仕様を **TODO リスト** に分解する作業から着手しましょう。

## 1.3 TODO リストの作成

仕様を分解して TODO リストを作成します。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [ ] 1 を渡したら文字列 "1" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

まず「1 を渡したら文字列 "1" を返す」という、最も小さなタスクから取り掛かります。

## 1.4 テスティングフレームワークの導入

### テストファースト

最初にプログラムする対象を決めたので、早速プロダクトコードを実装……ではなく **テストファースト** で作業を進めましょう。

> テストファースト
>
> いつテストを書くべきだろうか——それはテスト対象のコードを書く前だ。
>
> — テスト駆動開発

今回 PHP のテスティングフレームワークには [PHPUnit](https://phpunit.de/) を利用します。PHPUnit は PHP のデファクトスタンダードなテスティングフレームワークで、Java の JUnit にインスパイアされた xUnit 系のフレームワークです。

### 開発環境のセットアップ

Composer でプロジェクトを初期化し、PHPUnit をインストールします。

```bash
# プロジェクトの初期化
$ cd apps/php
$ composer init --name="fizzbuzz/php-tdd" --type="project" --require="php:^8.1" --no-interaction
$ composer require --dev phpunit/phpunit
```

PSR-4 オートロードを設定した `composer.json` を用意します。

```json
{
    "autoload": {
        "psr-4": {
            "App\\": "src/"
        }
    },
    "autoload-dev": {
        "psr-4": {
            "App\\Tests\\": "tests/"
        }
    }
}
```

PHPUnit の設定ファイル `phpunit.xml` を作成します。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<phpunit xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:noNamespaceSchemaLocation="vendor/phpunit/phpunit/phpunit.xsd"
         bootstrap="vendor/autoload.php"
         colors="true"
         testdox="true">
    <testsuites>
        <testsuite name="FizzBuzz Tests">
            <directory>tests</directory>
        </testsuite>
    </testsuites>
</phpunit>
```

### 環境確認テスト

環境が正しく設定されていることを確認するため、学習用テストを書きます。

```php
<?php
// tests/LearningTest.php

namespace App\Tests;

use PHPUnit\Framework\TestCase;

class LearningTest extends TestCase
{
    public function test_PHPの文字列キャストを確認する(): void
    {
        $this->assertSame('42', (string) 42);
    }

    public function test_PHPのintval関数を確認する(): void
    {
        $this->assertSame(42, intval('42'));
    }
}
```

テストを実行します。

```bash
$ vendor/bin/phpunit
OK (2 tests, 2 assertions)
```

テストが通りました。PHPUnit が正常に動作することが確認できました。PHP では `assertSame` は値と型の両方を比較します（`===` 相当）。Go の `if got != want` パターンや Ruby の `assert_equal` に相当します。

## 1.5 仮実装

テスト環境の準備ができたので、TODO リストの最初の作業に取り掛かりましょう。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - **1 を渡したら文字列 "1" を返す**
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

まずはアサーションを最初に書きましょう。

> アサートファースト
>
> いつアサーションを書くべきだろうか——最初に書こう。
>
> — テスト駆動開発

### Red: 最初のテスト

FizzBuzz のテストを書きます。

```php
<?php
// tests/FizzBuzzTest.php

namespace App\Tests;

use PHPUnit\Framework\TestCase;
use App\FizzBuzz;

class FizzBuzzTest extends TestCase
{
    public function test_1を渡したら文字列1を返す(): void
    {
        $fizzbuzz = new FizzBuzz();
        $this->assertSame('1', $fizzbuzz->generate(1));
    }
}
```

テストを実行します。

```bash
$ vendor/bin/phpunit
Error: Class "App\FizzBuzz" not found
```

`FizzBuzz` クラスが定義されていないためエラーになりました。PHP は実行時にクラスを解決するため、Go のようなコンパイルエラーではなく実行時エラーになります。

### Green: 仮実装

テストを通すために **仮実装** から始めます。

> 仮実装を経て本実装へ
>
> 失敗するテストを書いてから、最初に行う実装はどのようなものだろうか——ベタ書きの値を返そう。
>
> — テスト駆動開発

`FizzBuzz` クラスを定義して、文字列リテラルを返します。

```php
<?php
// src/FizzBuzz.php

namespace App;

class FizzBuzz
{
    public function generate(int $number): string
    {
        return '1';
    }
}
```

テストを実行します。

```bash
$ vendor/bin/phpunit
FizzBuzz
 ✔ 1を渡したら文字列1を返す

OK (1 test, 1 assertion)
```

テストが通りました。こんなベタ書きのプログラムでいいの？と思われるかもしれませんが、この細かいステップに今しばらくお付き合いください。

PHP では `int $number` と `string` の型宣言（Type Declaration）を使うことで、引数と戻り値の型を明示できます。これは Go の静的型付けほど厳密ではありませんが、PHPStan などの静的解析ツールと組み合わせることで型安全性を向上できます。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

ここまでの作業をバージョン管理システムにコミットしておきましょう。

```bash
$ git add .
$ git commit -m 'test: 数を文字列にして返す'
```

## 1.6 まとめ

この章では以下のことを学びました。

- **TODO リスト** で仕様をプログラミング対象に分解する方法
- **テストファースト** で最初にテストを書く考え方
- PHPUnit を使ったテスト実行環境のセットアップ
- Composer による PSR-4 オートロードの設定
- **仮実装** でベタ書きの値を返してテストを通す手法
- **アサートファースト** でテストの終わりから書き始めるアプローチ
- PHP の型宣言（int, string）による型安全性の向上

次章では、2 つ目のテストケースを追加して **三角測量** を行い、プログラムを一般化していきます。
