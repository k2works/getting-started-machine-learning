---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。"
tags: [article, getting-start-tdd, go]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 3 章: 明白な実装とリファクタリング

## 3.1 はじめに

前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。この章では、残りの TODO（リスト生成とプリント）を実装し、学習用テストを活用しながら「動作するきれいなコード」を目指してリファクタリングします。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 3.2 1 から 100 までのリスト生成

### Red: リスト生成のテスト

1 から 100 までの FizzBuzz の結果をスライスとして返す関数をテストします。

```go
func TestGenerateList_1から100までのFizzBuzzを返す(t *testing.T) {
	got := GenerateList(1, 100)

	if len(got) != 100 {
		t.Fatalf("len(GenerateList(1,100)) = %d, want 100", len(got))
	}
	if got[0] != "1" {
		t.Errorf("got[0] = %q, want %q", got[0], "1")
	}
	if got[1] != "2" {
		t.Errorf("got[1] = %q, want %q", got[1], "2")
	}
	if got[2] != "Fizz" {
		t.Errorf("got[2] = %q, want %q", got[2], "Fizz")
	}
	if got[3] != "4" {
		t.Errorf("got[3] = %q, want %q", got[3], "4")
	}
	if got[4] != "Buzz" {
		t.Errorf("got[4] = %q, want %q", got[4], "Buzz")
	}
	if got[14] != "FizzBuzz" {
		t.Errorf("got[14] = %q, want %q", got[14], "FizzBuzz")
	}
}
```

```bash
$ go test ./...
# github.com/k2works/getting-started-tdd/apps/go/fizzbuzz
fizzbuzz/fizzbuzz_test.go:55:9: undefined: GenerateList
FAIL
```

### Green: 明白な実装

Go のスライスと `for` ループを使って、指定範囲の数を FizzBuzz に変換したリストを返します。

```go
// GenerateList は start から end までの FizzBuzz 結果をスライスで返します。
func GenerateList(start, end int) []string {
	results := make([]string, 0, end-start+1)
	for i := start; i <= end; i++ {
		results = append(results, Generate(i))
	}
	return results
}
```

```bash
$ go test ./...
ok  	github.com/k2works/getting-started-tdd/apps/go/fizzbuzz	0.5s
```

`make([]string, 0, end-start+1)` はスライスの初期容量を指定して作成しています。これにより、`append` 時のメモリ再割り当てを避け、パフォーマンスを最適化できます。Ruby の `(1..100).map { |n| generate(n) }` や Python の `[generate(n) for n in range(1, 101)]` に相当する処理を、Go では明示的なループで実装します。

> 明白な実装
>
> シンプルな操作を実現するにはどうすればいいだろうか——そのまま実装しよう。
>
> — テスト駆動開発

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [ ] プリントする

## 3.3 プリント機能

### 学習用テスト

プリント機能は、生成したリストの各要素を標準出力に出力するものです。学習用テストとして、Go の標準出力のキャプチャ方法を確認します。

> 学習用テスト
>
> 外部のソフトウェアのテストを書くべきだろうか——そのソフトウェアに対して新しいことを初めて行おうとした段階で書いてみよう。
>
> — テスト駆動開発

Go では `fmt.Fprint` 系の関数で出力先を `io.Writer` に指定できます。テスト時には `bytes.Buffer` を使って出力をキャプチャします。

```go
// learning_test.go に追加
func TestLearning_fmtFprintln_バッファに出力できる(t *testing.T) {
	var buf bytes.Buffer
	fmt.Fprintln(&buf, "hello")
	got := buf.String()
	want := "hello\n"
	if got != want {
		t.Fatalf("buf.String() = %q, want %q", got, want)
	}
}
```

### Print 関数の実装

`io.Writer` を引数に取ることで、テスト時にはバッファに、本番では標準出力に出力できます。

```go
// Print は FizzBuzz の結果を writer に出力します。
func Print(w io.Writer) {
	for _, s := range GenerateList(1, 100) {
		fmt.Fprintln(w, s)
	}
}
```

テストを書きます。

```go
func TestPrint_FizzBuzzの結果を出力する(t *testing.T) {
	var buf bytes.Buffer
	Print(&buf)
	output := buf.String()

	if !strings.Contains(output, "1\n") {
		t.Error("output should contain '1'")
	}
	if !strings.Contains(output, "Fizz\n") {
		t.Error("output should contain 'Fizz'")
	}
	if !strings.Contains(output, "Buzz\n") {
		t.Error("output should contain 'Buzz'")
	}
	if !strings.Contains(output, "FizzBuzz\n") {
		t.Error("output should contain 'FizzBuzz'")
	}
}
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [x] プリントする

## 3.4 リファクタリング

テスト駆動開発の流れを確認しておきましょう。

> 1. レッド：動作しない、おそらく最初のうちはコンパイルも通らないテストを 1 つ書く。
> 2. グリーン：そのテストを迅速に動作させる。このステップでは罪を犯してもよい。
> 3. リファクタリング：テストを通すために発生した重複をすべて除去する。
>
> レッド・グリーン・リファクタリング。それが TDD のマントラだ。
>
> — テスト駆動開発

### テストコードのリファクタリング

テストコードに重複はないでしょうか？`assertGenerate` ヘルパー関数でアサーションの重複を排除済みです。

現在のテストコード全体を確認します。

```go
// fizzbuzz/fizzbuzz_test.go
package fizzbuzz

import (
	"bytes"
	"strings"
	"testing"
)

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

func TestGenerate_3を渡したらFizzを返す(t *testing.T) {
	assertGenerate(t, 3, "Fizz")
}

func TestGenerate_6を渡したらFizzを返す(t *testing.T) {
	assertGenerate(t, 6, "Fizz")
}

func TestGenerate_5を渡したらBuzzを返す(t *testing.T) {
	assertGenerate(t, 5, "Buzz")
}

func TestGenerate_10を渡したらBuzzを返す(t *testing.T) {
	assertGenerate(t, 10, "Buzz")
}

func TestGenerate_15を渡したらFizzBuzzを返す(t *testing.T) {
	assertGenerate(t, 15, "FizzBuzz")
}

func TestGenerateList_1から100までのFizzBuzzを返す(t *testing.T) {
	got := GenerateList(1, 100)

	if len(got) != 100 {
		t.Fatalf("len(GenerateList(1,100)) = %d, want 100", len(got))
	}
	if got[0] != "1" {
		t.Errorf("got[0] = %q, want %q", got[0], "1")
	}
	if got[2] != "Fizz" {
		t.Errorf("got[2] = %q, want %q", got[2], "Fizz")
	}
	if got[4] != "Buzz" {
		t.Errorf("got[4] = %q, want %q", got[4], "Buzz")
	}
	if got[14] != "FizzBuzz" {
		t.Errorf("got[14] = %q, want %q", got[14], "FizzBuzz")
	}
}

func TestPrint_FizzBuzzの結果を出力する(t *testing.T) {
	var buf bytes.Buffer
	Print(&buf)
	output := buf.String()

	if !strings.Contains(output, "1\n") {
		t.Error("output should contain '1'")
	}
	if !strings.Contains(output, "Fizz\n") {
		t.Error("output should contain 'Fizz'")
	}
	if !strings.Contains(output, "Buzz\n") {
		t.Error("output should contain 'Buzz'")
	}
	if !strings.Contains(output, "FizzBuzz\n") {
		t.Error("output should contain 'FizzBuzz'")
	}
}
```

各テスト関数は独立しており、1 テスト 1 概念の原則に従っています。

### プロダクションコードの確認

```go
// fizzbuzz/fizzbuzz.go
package fizzbuzz

import (
	"fmt"
	"io"
	"strconv"
)

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

// GenerateList は start から end までの FizzBuzz 結果をスライスで返します。
func GenerateList(start, end int) []string {
	results := make([]string, 0, end-start+1)
	for i := start; i <= end; i++ {
		results = append(results, Generate(i))
	}
	return results
}

// Print は FizzBuzz の結果を writer に出力します。
func Print(w io.Writer) {
	for _, s := range GenerateList(1, 100) {
		fmt.Fprintln(w, s)
	}
}
```

プロダクションコードは十分にシンプルで、リファクタリングの必要はありません。Go の式なし `switch` 文は、Ruby のガード節スタイル（`return ... if`）と同様に読みやすく簡潔です。

## 3.5 他言語との比較

| 概念 | Java | Python | TypeScript | Ruby | Go |
|------|------|--------|-----------|------|------|
| テストフレームワーク | JUnit 5 | pytest | Vitest | Minitest | testing（標準） |
| テスト実行 | `./gradlew test` | `pytest` | `npx vitest` | `bundle exec rake test` | `go test ./...` |
| 文字列変換 | `String.valueOf(n)` | `str(n)` | `n.toString()` | `n.to_s` | `strconv.Itoa(n)` |
| 剰余判定 | `n % 3 == 0` | `n % 3 == 0` | `n % 3 === 0` | `(n % 3).zero?` | `n%3 == 0` |
| リスト生成 | `IntStream.rangeClosed` | `[f(n) for n in range]` | `Array.from({length})` | `(1..100).map { }` | `for` + `append` |
| 出力テスト | `System.setOut` | `capsys` fixture | `vi.spyOn` | `StringIO` | `bytes.Buffer` |

## 3.6 まとめ

この章では以下のことを学びました。

- **明白な実装** でシンプルな操作をそのまま実装する手法
- Go のスライスと `make` による容量指定付きスライス生成
- `io.Writer` インターフェースを活用したテスタブルな出力設計
- `bytes.Buffer` を使った標準出力のキャプチャ（学習用テスト）
- **リファクタリング** でテストを通すために発生した重複を除去する考え方
- Red-Green-Refactor サイクルの完了

第 1 部の 3 章を通じて、TDD の基本サイクル（仮実装 → 三角測量 → 明白な実装 → リファクタリング）を一通り体験しました。次の第 2 部では、開発環境の自動化（バージョン管理、パッケージ管理、CI/CD）に進みます。
