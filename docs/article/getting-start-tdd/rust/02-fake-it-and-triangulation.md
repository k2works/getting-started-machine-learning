---
type: Article
title: "第 2 章: 仮実装と三角測量"
description: "前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。"
tags: [article, getting-start-tdd, rust]
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

```rust
#[test]
fn test_2を渡したら文字列2を返す() {
    assert_eq!("2", generate(2));
}
```

テストを実行します。

```bash
$ cargo test
test tests::test_1を渡したら文字列1を返す ... ok
test tests::test_2を渡したら文字列2を返す ... FAILED

assertion `left == right` failed
  left: "2"
 right: "1"
```

テストが失敗しました。文字列 "1" しか返さないプログラムなのですから当然です。

### Green: 一般化する

数値を文字列に変換して返すように修正します。Rust では `i32` 型に実装された `to_string()` メソッドで整数を文字列に変換できます。

```rust
pub fn generate(number: i32) -> String {
    number.to_string()
}
```

テストを実行します。

```bash
$ cargo test
test tests::test_1を渡したら文字列1を返す ... ok
test tests::test_2を渡したら文字列2を返す ... ok

test result: ok. 2 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out
```

テストが通りました！2 つ目のテストによって `generate` 関数の一般化を実現できました。このようなアプローチを **三角測量** と言います。

> 三角測量
>
> テストから最も慎重に一般化を引き出すやり方はどのようなものだろうか——2 つ以上の例があるときだけ、一般化を行うようにしよう。
>
> — テスト駆動開発

Go では `strconv.Itoa(number)` と書くところを、Rust では `number.to_string()` を使います。Rust の `to_string()` は `Display` トレイトを実装した任意の型で利用できるため、Go よりも汎用的です。

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

```rust
#[test]
fn test_3を渡したらfizzを返す() {
    assert_eq!("Fizz", generate(3));
}
```

```bash
$ cargo test
test tests::test_3を渡したらfizzを返す ... FAILED

assertion `left == right` failed
  left: "Fizz"
 right: "3"
```

### Green: 明白な実装

3 の倍数のときは "Fizz" を返すように実装します。Rust では `%` 演算子で剰余を求め、`== 0` でゼロかどうかを判定します。

```rust
pub fn generate(number: i32) -> String {
    if number % 3 == 0 {
        return "Fizz".to_string();
    }
    number.to_string()
}
```

```bash
$ cargo test
test result: ok. 3 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out
```

三角測量として 6 のテストも追加して確認します。

```rust
#[test]
fn test_6を渡したらfizzを返す() {
    assert_eq!("Fizz", generate(6));
}
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

```rust
#[test]
fn test_5を渡したらbuzzを返す() {
    assert_eq!("Buzz", generate(5));
}
```

```bash
$ cargo test
test tests::test_5を渡したらbuzzを返す ... FAILED

assertion `left == right` failed
  left: "Buzz"
 right: "5"
```

### Green: Buzz の実装

```rust
pub fn generate(number: i32) -> String {
    if number % 3 == 0 {
        return "Fizz".to_string();
    }
    if number % 5 == 0 {
        return "Buzz".to_string();
    }
    number.to_string()
}
```

```bash
$ cargo test
test result: ok. 5 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out
```

三角測量として 10 のテストも追加します。

```rust
#[test]
fn test_10を渡したらbuzzを返す() {
    assert_eq!("Buzz", generate(10));
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

```rust
#[test]
fn test_15を渡したらfizzbuzzを返す() {
    assert_eq!("FizzBuzz", generate(15));
}
```

```bash
$ cargo test
test tests::test_15を渡したらfizzbuzzを返す ... FAILED

assertion `left == right` failed
  left: "FizzBuzz"
 right: "Fizz"
```

15 は 3 の倍数でもあるため、"Fizz" が返されてしまいました。3 と 5 の両方の倍数の判定を先に行う必要があります。

### Green: FizzBuzz の実装

Rust の `match` 式を使って、条件分岐を整理します。

```rust
pub fn generate(number: i32) -> String {
    match (number % 3, number % 5) {
        (0, 0) => "FizzBuzz".to_string(),
        (0, _) => "Fizz".to_string(),
        (_, 0) => "Buzz".to_string(),
        _ => number.to_string(),
    }
}
```

```bash
$ cargo test
test result: ok. 7 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out
```

Rust の `match` 式はパターンマッチングを使った条件分岐です。タプル `(number % 3, number % 5)` に対してパターンを照合します。Go の式なし `switch` や Java の `if-else if` チェーンよりも、Rust の `match` は網羅性をコンパイラがチェックしてくれるため、より安全です。

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
- Rust の `to_string()` メソッドによる整数から文字列への変換
- Rust の `%` 演算子による剰余判定
- Rust の `match` 式によるパターンマッチング（タプルパターン）
- Red-Green-Refactor サイクルを繰り返してコアロジックを段階的に構築する方法

次章では、残りの TODO（リスト生成とプリント）を実装し、リファクタリングで「動作するきれいなコード」を目指します。
