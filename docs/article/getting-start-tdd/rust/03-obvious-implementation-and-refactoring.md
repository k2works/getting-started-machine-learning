---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。"
tags: [article, getting-start-tdd, rust]
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

1 から 100 までの FizzBuzz の結果を `Vec<String>` として返す関数をテストします。

```rust
#[test]
fn test_generate_list_1から100までのfizzbuzzを返す() {
    let result = generate_list(1, 100);
    assert_eq!(100, result.len());
    assert_eq!("1", result[0]);
    assert_eq!("2", result[1]);
    assert_eq!("Fizz", result[2]);
    assert_eq!("4", result[3]);
    assert_eq!("Buzz", result[4]);
    assert_eq!("FizzBuzz", result[14]);
}
```

```bash
$ cargo test
error[E0425]: cannot find function `generate_list` in this scope
```

### Green: 明白な実装

Rust のイテレータと `map` を使って、指定範囲の数を FizzBuzz に変換したリストを返します。

```rust
pub fn generate_list(start: i32, end: i32) -> Vec<String> {
    (start..=end).map(generate).collect()
}
```

```bash
$ cargo test
test result: ok. 8 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out
```

`(start..=end)` は Rust の範囲式（inclusive range）で、`start` から `end` までのイテレータを生成します。`.map(generate)` で各要素に `generate` 関数を適用し、`.collect()` で `Vec<String>` に変換します。Go の明示的な `for` ループ + `append` に比べ、Rust では関数型スタイルでより宣言的に書けます。

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

プリント機能は、生成したリストの各要素を出力するものです。学習用テストとして、Rust の `Write` トレイトを使った出力のキャプチャ方法を確認します。

> 学習用テスト
>
> 外部のソフトウェアのテストを書くべきだろうか——そのソフトウェアに対して新しいことを初めて行おうとした段階で書いてみよう。
>
> — テスト駆動開発

Rust では `std::io::Write` トレイトを実装した `Vec<u8>` をバッファとして使い、出力をキャプチャできます。

```rust
#[test]
fn test_learning_write_バッファに出力できる() {
    use std::io::Write;
    let mut buf = Vec::new();
    writeln!(buf, "hello").unwrap();
    assert_eq!("hello\n", String::from_utf8(buf).unwrap());
}
```

### Print 関数の実装

`std::io::Write` トレイトを引数に取ることで、テスト時にはバッファに、本番では標準出力に出力できます。

```rust
use std::io::Write;

pub fn print_fizzbuzz(writer: &mut dyn Write) {
    for s in generate_list(1, 100) {
        writeln!(writer, "{}", s).unwrap();
    }
}
```

テストを書きます。

```rust
#[test]
fn test_print_fizzbuzzの結果を出力する() {
    let mut buf = Vec::new();
    print_fizzbuzz(&mut buf);
    let output = String::from_utf8(buf).unwrap();
    assert!(output.contains("1\n"));
    assert!(output.contains("Fizz\n"));
    assert!(output.contains("Buzz\n"));
    assert!(output.contains("FizzBuzz\n"));
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

Rust のテストモジュールシステムを使って、テストをカテゴリごとに構造化します。

```rust
#[cfg(test)]
mod tests {
    use super::*;

    mod その他の場合 {
        use super::*;

        #[test]
        fn test_1を渡したら文字列1を返す() {
            assert_eq!("1", generate(1));
        }

        #[test]
        fn test_2を渡したら文字列2を返す() {
            assert_eq!("2", generate(2));
        }
    }

    mod 三の倍数の場合 {
        use super::*;

        #[test]
        fn test_3を渡したらfizzを返す() {
            assert_eq!("Fizz", generate(3));
        }

        #[test]
        fn test_6を渡したらfizzを返す() {
            assert_eq!("Fizz", generate(6));
        }
    }

    mod 五の倍数の場合 {
        use super::*;

        #[test]
        fn test_5を渡したらbuzzを返す() {
            assert_eq!("Buzz", generate(5));
        }

        #[test]
        fn test_10を渡したらbuzzを返す() {
            assert_eq!("Buzz", generate(10));
        }
    }

    mod 三と五の倍数の場合 {
        use super::*;

        #[test]
        fn test_15を渡したらfizzbuzzを返す() {
            assert_eq!("FizzBuzz", generate(15));
        }
    }
}
```

Rust のネストされた `mod` はテストの論理的グルーピングに活用でき、`cargo test` の出力に `tests::三の倍数の場合::test_3を渡したらfizzを返す` のように階層が表示されます。

## 3.5 他言語との比較

| 概念 | Java | Python | TypeScript | Ruby | Go | Rust |
|------|------|--------|-----------|------|------|------|
| テストフレームワーク | JUnit 5 | pytest | Vitest | Minitest | testing（標準） | cargo test（標準） |
| テスト実行 | `./gradlew test` | `pytest` | `npx vitest` | `bundle exec rake test` | `go test ./...` | `cargo test` |
| 文字列変換 | `String.valueOf(n)` | `str(n)` | `n.toString()` | `n.to_s` | `strconv.Itoa(n)` | `n.to_string()` |
| 剰余判定 | `n % 3 == 0` | `n % 3 == 0` | `n % 3 === 0` | `(n % 3).zero?` | `n%3 == 0` | `n % 3 == 0` |
| リスト生成 | `IntStream.rangeClosed` | `[f(n) for n in range]` | `Array.from({length})` | `(1..100).map { }` | `for` + `append` | `(1..=100).map(f).collect()` |
| 出力テスト | `System.setOut` | `capsys` fixture | `vi.spyOn` | `StringIO` | `bytes.Buffer` | `Vec<u8>` + `Write` |

## 3.6 まとめ

この章では以下のことを学びました。

- **明白な実装** でシンプルな操作をそのまま実装する手法
- Rust のイテレータと `map`、`collect` によるリスト生成
- `std::io::Write` トレイトを活用したテスタブルな出力設計
- `Vec<u8>` を使った出力のキャプチャ（学習用テスト）
- **リファクタリング** でテストモジュールを構造化する考え方
- Red-Green-Refactor サイクルの完了

第 1 部の 3 章を通じて、TDD の基本サイクル（仮実装 → 三角測量 → 明白な実装 → リファクタリング）を一通り体験しました。次の第 2 部では、開発環境の自動化（バージョン管理、パッケージ管理、CI/CD）に進みます。
