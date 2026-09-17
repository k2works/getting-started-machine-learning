---
type: Article
title: "第 1 章: TODO リストと最初のテスト"
description: "プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して TODO リスト を作るところから始めます。"
tags: [article, getting-start-tdd, ruby]
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

今回 Ruby のテスティングフレームワークには [Minitest](https://github.com/minitest/minitest) を利用します。Minitest は Ruby に標準で同梱されているテスティングフレームワークです。

### 開発環境のセットアップ

Bundler でプロジェクトを初期化し、テスト環境をセットアップします。

```ruby
# Gemfile
source 'https://rubygems.org'

group :development, :test do
  gem 'minitest', '~> 5.25'
  gem 'minitest-reporters', '~> 1.7'
  gem 'rake', '~> 13.2'
end
```

```bash
# Gem のインストール
$ bundle install
```

テストヘルパーを作成します。

```ruby
# test/test_helper.rb
require 'minitest/autorun'
require 'minitest/reporters'
Minitest::Reporters.use!
```

### 環境確認テスト

環境が正しく設定されていることを確認するため、簡単なテストを書きます。

```ruby
# test/fizz_buzz_test.rb
require_relative 'test_helper'

class HelloTest < Minitest::Test
  def test_greeting
    assert_equal 'hello world', greeting
  end
end

def greeting
  'hello world'
end
```

テストを実行します。

```bash
$ bundle exec rake test
Started with run options --seed 47255

  1/1: [==========] 100% Time: 00:00:00

Finished in 0.00090s
1 tests, 1 assertions, 0 failures, 0 errors, 0 skips
```

テストが通りました。テスティングフレームワークが正常に動作することが確認できました。

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

セットアップ用のテストを削除して、FizzBuzz のテストを書きます。

```ruby
# test/fizz_buzz_test.rb
require_relative 'test_helper'
require_relative '../lib/fizz_buzz'

class FizzBuzzTest < Minitest::Test
  def setup
    @fizzbuzz = FizzBuzz
  end

  def test_1を渡したら文字列1を返す
    assert_equal '1', @fizzbuzz.generate(1)
  end
end
```

テストを実行します。

```bash
$ bundle exec rake test
ERROR["test_1を渡したら文字列1を返す", FizzBuzzTest]
 NameError: uninitialized constant FizzBuzzTest::FizzBuzz
```

`FizzBuzz` が定義されていないためエラーになりました。まだ作っていないのですから当然です。

### Green: 仮実装

テストを通すために **仮実装** から始めます。

> 仮実装を経て本実装へ
>
> 失敗するテストを書いてから、最初に行う実装はどのようなものだろうか——ベタ書きの値を返そう。
>
> — テスト駆動開発

`FizzBuzz` クラスを定義して、文字列リテラルを返す `generate` クラスメソッドを作成します。

```ruby
# lib/fizz_buzz.rb
class FizzBuzz
  def self.generate(number)
    '1'
  end
end
```

テストを実行します。

```bash
$ bundle exec rake test
Started with run options --seed 60122

  1/1: [==========] 100% Time: 00:00:00

Finished in 0.00094s
1 tests, 1 assertions, 0 failures, 0 errors, 0 skips
```

テストが通りました。こんなベタ書きのプログラムでいいの？と思われるかもしれませんが、この細かいステップに今しばらくお付き合いください。

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
- Minitest を使った Ruby のテスト実行環境のセットアップ
- **仮実装** でベタ書きの値を返してテストを通す手法
- **アサートファースト** でテストの終わりから書き始めるアプローチ

次章では、2 つ目のテストケースを追加して **三角測量** を行い、プログラムを一般化していきます。
