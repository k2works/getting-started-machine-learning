---
type: Article
title: "第 1 章: TODO リストと最初のテスト"
description: "プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して TODO リスト を作るところから始めます。"
tags: [article, getting-start-tdd, go]
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

今回 Go のテスティングフレームワークには標準の [testing](https://pkg.go.dev/testing) パッケージを利用します。Go では外部ライブラリを追加することなく、標準パッケージだけでテストを書くことができます。

### 開発環境のセットアップ

Go Modules でプロジェクトを初期化し、テスト環境をセットアップします。

```bash
# プロジェクトの初期化
$ mkdir -p apps/go/fizzbuzz
$ cd apps/go
$ go mod init github.com/k2works/getting-started-tdd/apps/go
```

Go のテストは `_test.go` というサフィックスを持つファイルに書きます。テスト関数は `Test` で始まり、`*testing.T` を引数に取ります。

### 環境確認テスト

環境が正しく設定されていることを確認するため、学習用テストを書きます。

```go
// fizzbuzz/learning_test.go
package fizzbuzz

import (
	"strconv"
	"testing"
)

// 学習用テスト: strconv.Itoa の使い方確認。
func TestLearning_strconvItoa_整数を文字列へ変換できる(t *testing.T) {
	got := strconv.Itoa(42)
	want := "42"
	if got != want {
		t.Fatalf("strconv.Itoa(42) = %q, want %q", got, want)
	}
}
```

テストを実行します。

```bash
$ go test ./...
ok  	github.com/k2works/getting-started-tdd/apps/go/fizzbuzz	0.5s
```

テストが通りました。Go の `testing` パッケージが正常に動作することが確認できました。Ruby の Minitest や Python の pytest のようなアサーションメソッドはありませんが、`if` 文と `t.Fatalf` で同等のテストが書けます。

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

```go
// fizzbuzz/fizzbuzz_test.go
package fizzbuzz

import "testing"

func TestGenerate_1を渡したら文字列1を返す(t *testing.T) {
	got := Generate(1)
	want := "1"
	if got != want {
		t.Fatalf("Generate(1) = %q, want %q", got, want)
	}
}
```

テストを実行します。

```bash
$ go test ./...
# github.com/k2works/getting-started-tdd/apps/go/fizzbuzz
fizzbuzz/fizzbuzz_test.go:6:9: undefined: Generate
FAIL
```

`Generate` 関数が定義されていないためコンパイルエラーになりました。Go は静的型付け言語なので、Ruby と異なり未定義の関数はコンパイル時に検出されます。

### Green: 仮実装

テストを通すために **仮実装** から始めます。

> 仮実装を経て本実装へ
>
> 失敗するテストを書いてから、最初に行う実装はどのようなものだろうか——ベタ書きの値を返そう。
>
> — テスト駆動開発

`Generate` 関数を定義して、文字列リテラルを返します。

```go
// fizzbuzz/fizzbuzz.go
package fizzbuzz

// Generate は FizzBuzz の文字列を返します。
func Generate(number int) string {
	return "1"
}
```

テストを実行します。

```bash
$ go test ./...
ok  	github.com/k2works/getting-started-tdd/apps/go/fizzbuzz	0.5s
```

テストが通りました。こんなベタ書きのプログラムでいいの？と思われるかもしれませんが、この細かいステップに今しばらくお付き合いください。

Go では関数名が大文字で始まる `Generate` はパッケージ外からアクセス可能な公開関数です。小文字で始まる関数は非公開になります。これは Ruby の `public`/`private` や Java のアクセス修飾子に相当する、Go 独自のカプセル化の仕組みです。

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
- Go の標準 `testing` パッケージを使ったテスト実行環境のセットアップ
- **仮実装** でベタ書きの値を返してテストを通す手法
- **アサートファースト** でテストの終わりから書き始めるアプローチ
- Go の大文字/小文字による公開/非公開の仕組み

次章では、2 つ目のテストケースを追加して **三角測量** を行い、プログラムを一般化していきます。
