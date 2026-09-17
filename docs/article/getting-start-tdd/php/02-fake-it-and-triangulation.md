---
type: Article
title: "第 2 章: 仮実装と三角測量"
description: "前章では、最初のテストを仮実装で通しました。"
tags: [article, getting-start-tdd, php]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 2 章: 仮実装と三角測量

## 2.1 はじめに

前章では、最初のテストを仮実装で通しました。この章では、2 つ目のテストケースを追加して **三角測量** を行い、プログラムを一般化します。さらに、3 の倍数・5 の倍数・15 の倍数のケースにも取り組みます。

## 2.2 三角測量

> 三角測量
>
> テストから最も慎重に一般化を引き出すやり方はどのようなものだろうか——2 つ以上の例があるときだけ、一般化を行うようにしよう。
>
> — テスト駆動開発

### Red: 2 つ目のテスト

2 を渡したら文字列 "2" を返すテストを追加します。

```php
public function test_2を渡したら文字列2を返す(): void
{
    $fizzbuzz = new FizzBuzz();
    $this->assertSame('2', $fizzbuzz->generate(2));
}
```

テストを実行すると失敗します。

```bash
$ vendor/bin/phpunit
FizzBuzz
 ✔ 1を渡したら文字列1を返す
 ✘ 2を渡したら文字列2を返す
   Failed asserting that two strings are identical.
   Expected: '2'
   Actual  : '1'
```

### Green: 一般化

仮実装を一般化して、数値を文字列に変換するようにします。

```php
public function generate(int $number): string
{
    return (string) $number;
}
```

PHP では `(string)` キャストで整数を文字列に変換できます。Go の `strconv.Itoa()` や Python の `str()` に相当します。

```bash
$ vendor/bin/phpunit
FizzBuzz
 ✔ 1を渡したら文字列1を返す
 ✔ 2を渡したら文字列2を返す

OK (2 tests, 2 assertions)
```

2 つのテストが通りました。三角測量により、仮実装が一般的な実装に変わりました。

## 2.3 Fizz のテスト

**TODO リスト**:

- [x] 数を文字列にして返す
- [ ] **3 の倍数のときは数の代わりに「Fizz」と返す**
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

### Red: 3 の倍数のテスト

```php
public function test_3を渡したら文字列Fizzを返す(): void
{
    $fizzbuzz = new FizzBuzz();
    $this->assertSame('Fizz', $fizzbuzz->generate(3));
}
```

テストを実行すると失敗します（期待値は "Fizz"、実際は "3"）。

### Green: 仮実装

まずは仮実装でテストを通します。

```php
public function generate(int $number): string
{
    if ($number === 3) {
        return 'Fizz';
    }
    return (string) $number;
}
```

```bash
$ vendor/bin/phpunit
OK (3 tests, 3 assertions)
```

## 2.4 Buzz のテスト

### Red: 5 の倍数のテスト

```php
public function test_5を渡したら文字列Buzzを返す(): void
{
    $fizzbuzz = new FizzBuzz();
    $this->assertSame('Buzz', $fizzbuzz->generate(5));
}
```

### Green: 仮実装

```php
public function generate(int $number): string
{
    if ($number === 3) {
        return 'Fizz';
    }
    if ($number === 5) {
        return 'Buzz';
    }
    return (string) $number;
}
```

```bash
$ vendor/bin/phpunit
OK (4 tests, 4 assertions)
```

## 2.5 FizzBuzz のテスト

### Red: 15 の倍数のテスト

```php
public function test_15を渡したら文字列FizzBuzzを返す(): void
{
    $fizzbuzz = new FizzBuzz();
    $this->assertSame('FizzBuzz', $fizzbuzz->generate(15));
}
```

### Green: 仮実装

15 の場合を最初にチェックする必要があります（3 の倍数かつ 5 の倍数のため）。

```php
public function generate(int $number): string
{
    if ($number === 15) {
        return 'FizzBuzz';
    }
    if ($number === 3) {
        return 'Fizz';
    }
    if ($number === 5) {
        return 'Buzz';
    }
    return (string) $number;
}
```

```bash
$ vendor/bin/phpunit
OK (5 tests, 5 assertions)
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す（仮実装: 3 のみ）
- [ ] 5 の倍数のときは「Buzz」と返す（仮実装: 5 のみ）
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す（仮実装: 15 のみ）
- [ ] 1 から 100 までの数
- [ ] プリントする

ここまでの作業をコミットしておきましょう。

```bash
$ git add .
$ git commit -m 'test: FizzBuzz の仮実装を完了'
```

## 2.6 まとめ

この章では以下のことを学びました。

- **三角測量** で 2 つ以上の例から実装を一般化する手法
- **仮実装** で特定の値に対してのみ正しく動作するコードを書く手法
- PHP の `(string)` キャストによる型変換
- PHP の `===` による厳密な比較（型と値の両方を比較）
- 条件分岐の順序が結果に影響すること（15 の倍数を先にチェック）

次章では、仮実装を **明白な実装** にリファクタリングし、さらに配列を返す機能とプリント機能を追加します。
