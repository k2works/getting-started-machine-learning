---
type: Article
title: "第 2 章: 仮実装と三角測量"
description: "前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。"
tags: [article, getting-start-tdd, go]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 2 章: 仮実装と三角測量

## 2.1 はじめに

前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。この章では、**三角測量** によってプログラムを一般化し、さらに FizzBuzz のコアロジックを実装していきます。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
  - [ ] 2 を渡したら文字列 "2" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.2 三角測量

1 を渡したら文字列 "1" を返すようにできました。では、2 を渡したらどうなるでしょうか？

### Red: 2 つ目のテストを書く

```go
func TestGenerate_2を渡したら文字列2を返す(t *testing.T) {
	got := Generate(2)
	want := "2"
	if got != want {
		t.Fatalf("Generate(2) = %q, want %q", got, want)
	}
}
```

テストを実行します。

```bash
$ go test ./...
--- FAIL: TestGenerate_2を渡したら文字列2を返す (0.00s)
    fizzbuzz_test.go:14: Generate(2) = "1", want "2"
FAIL
```

テストが失敗しました。文字列 "1" しか返さないプログラムなのですから当然です。

### Green: 一般化する

数値を文字列に変換して返すように修正します。Go では `strconv.Itoa` 関数で整数を文字列に変換できます（学習用テストで確認済み）。

```go
// fizzbuzz/fizzbuzz.go
package fizzbuzz

import "strconv"

// Generate は FizzBuzz の文字列を返します。
func Generate(number int) string {
	return strconv.Itoa(number)
}
```

テストを実行します。

```bash
$ go test ./...
ok  	github.com/k2works/getting-started-tdd/apps/go/fizzbuzz	0.5s
```

テストが通りました！2 つ目のテストによって `Generate` 関数の一般化を実現できました。このようなアプローチを **三角測量** と言います。

> 三角測量
>
> テストから最も慎重に一般化を引き出すやり方はどのようなものだろうか——2 つ以上の例があるときだけ、一般化を行うようにしよう。
>
> — テスト駆動開発

Ruby では `number.to_s`、Python では `str(number)` と書くところを、Go では `strconv.Itoa(number)` を使います。Go は暗黙の型変換を許さない言語設計のため、明示的な変換関数が必要です。

### Refactor: テストヘルパーの抽出

テストコードに重複が見られます。アサーションのパターンが同じなので、ヘルパー関数を抽出しましょう。

```go
func assertGenerate(t *testing.T, input int, want string) {
	t.Helper()
	got := Generate(input)
	if got != want {
		t.Fatalf("Generate(%d) = %q, want %q", input, got, want)
	}
}

func TestGenerate_1を渡したら文字列1を返す(t *testing.T) {
	assertGenerate(t, 1, "1")
}

func TestGenerate_2を渡したら文字列2を返す(t *testing.T) {
	assertGenerate(t, 2, "2")
}
```

`t.Helper()` を呼ぶことで、テスト失敗時のスタックトレースがヘルパー関数ではなく呼び出し元を指すようになります。これは Ruby の Minitest で `assert_equal` が自動的に行っていることを、Go では明示的に指定する必要があるということです。

**TODO リスト**:

- [x] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
  - [x] 2 を渡したら文字列 "2" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.3 3 の倍数 — Fizz

次は「3 の倍数のときは数の代わりに Fizz と返す」に取り掛かります。

### Red: 3 の倍数のテスト

```go
func TestGenerate_3を渡したらFizzを返す(t *testing.T) {
	assertGenerate(t, 3, "Fizz")
}
```

```bash
$ go test ./...
--- FAIL: TestGenerate_3を渡したらFizzを返す (0.00s)
    fizzbuzz_test.go:8: Generate(3) = "3", want "Fizz"
FAIL
```

### Green: 明白な実装

3 の倍数のときは "Fizz" を返すように実装します。Go では `%` 演算子で剰余を求め、`== 0` でゼロかどうかを判定します。

```go
// fizzbuzz/fizzbuzz.go
package fizzbuzz

import "strconv"

// Generate は FizzBuzz の文字列を返します。
func Generate(number int) string {
	if number%3 == 0 {
		return "Fizz"
	}
	return strconv.Itoa(number)
}
```

```bash
$ go test ./...
ok  	github.com/k2works/getting-started-tdd/apps/go/fizzbuzz	0.5s
```

三角測量として 6 のテストも追加して確認します。

```go
func TestGenerate_6を渡したらFizzを返す(t *testing.T) {
	assertGenerate(t, 6, "Fizz")
}
```

```bash
$ go test ./...
ok  	github.com/k2works/getting-started-tdd/apps/go/fizzbuzz	0.5s
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.4 5 の倍数 — Buzz

### Red: 5 の倍数のテスト

```go
func TestGenerate_5を渡したらBuzzを返す(t *testing.T) {
	assertGenerate(t, 5, "Buzz")
}
```

```bash
$ go test ./...
--- FAIL: TestGenerate_5を渡したらBuzzを返す (0.00s)
    fizzbuzz_test.go:8: Generate(5) = "5", want "Buzz"
FAIL
```

### Green: Buzz の実装

```go
func Generate(number int) string {
	if number%3 == 0 {
		return "Fizz"
	}
	if number%5 == 0 {
		return "Buzz"
	}
	return strconv.Itoa(number)
}
```

```bash
$ go test ./...
ok  	github.com/k2works/getting-started-tdd/apps/go/fizzbuzz	0.5s
```

三角測量として 10 のテストも追加します。

```go
func TestGenerate_10を渡したらBuzzを返す(t *testing.T) {
	assertGenerate(t, 10, "Buzz")
}
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.5 15 の倍数 — FizzBuzz

### Red: 15 の倍数のテスト

```go
func TestGenerate_15を渡したらFizzBuzzを返す(t *testing.T) {
	assertGenerate(t, 15, "FizzBuzz")
}
```

```bash
$ go test ./...
--- FAIL: TestGenerate_15を渡したらFizzBuzzを返す (0.00s)
    fizzbuzz_test.go:8: Generate(15) = "Fizz", want "FizzBuzz"
FAIL
```

15 は 3 の倍数でもあるため、"Fizz" が返されてしまいました。3 と 5 の両方の倍数の判定を先に行う必要があります。

### Green: FizzBuzz の実装

Go の `switch` 文を使って、条件分岐を整理します。

```go
// fizzbuzz/fizzbuzz.go
package fizzbuzz

import "strconv"

// Generate は FizzBuzz の文字列を返します。
func Generate(number int) string {
	switch {
	case number%15 == 0:
		return "FizzBuzz"
	case number%3 == 0:
		return "Fizz"
	case number%5 == 0:
		return "Buzz"
	default:
		return strconv.Itoa(number)
	}
}
```

```bash
$ go test ./...
ok  	github.com/k2works/getting-started-tdd/apps/go/fizzbuzz	0.5s
```

Go の `switch` 文は式なしで使うことができ、各 `case` に条件式を書けます。Ruby のガード節（`return ... if`）や Java の `if-else if-else` チェーンに相当しますが、Go ではフォールスルーしない（`break` が不要）ため、より安全に条件分岐を記述できます。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.6 まとめ

この章では以下のことを学びました。

- **三角測量** で 2 つ以上の例を使ってプログラムを一般化する手法
- Go の `strconv.Itoa` 関数による整数から文字列への変換
- Go の `%` 演算子による剰余判定
- Go の式なし `switch` 文による条件分岐
- `t.Helper()` によるテストヘルパー関数の作成
- Red-Green-Refactor サイクルを繰り返してコアロジックを段階的に構築する方法

次章では、残りの TODO（リスト生成とプリント）を実装し、リファクタリングで「動作するきれいなコード」を目指します。
