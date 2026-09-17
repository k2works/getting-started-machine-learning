---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "前章では、3・5・15 の各ケースを仮実装で通しました。"
tags: [article, getting-start-tdd, php]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 3 章: 明白な実装とリファクタリング

## 3.1 はじめに

前章では、3・5・15 の各ケースを仮実装で通しました。この章では、仮実装を汎用的なロジックに **リファクタリング** し、「明白な実装」に変換します。さらに、1 から 100 までの配列を返す機能とプリント機能を追加して、FizzBuzz プログラムを完成させます。

## 3.2 明白な実装

> 明白な実装
>
> シンプルな操作をどうやって実装すればよいか——そのまま実装しよう。
>
> — テスト駆動開発

### Red: 一般化のためのテスト

6（3 の倍数だが 3 ではない）を渡すテストを追加して、仮実装の限界を明らかにします。

```php
public function test_6を渡したら文字列Fizzを返す(): void
{
    $fizzbuzz = new FizzBuzz();
    $this->assertSame('Fizz', $fizzbuzz->generate(6));
}
```

テストを実行すると失敗します（期待値は "Fizz"、実際は "6"）。

### Refactor: 倍数チェックの一般化

特定の数値へのハードコーディングを、倍数チェックのルールに変更します。

```php
public function generate(int $number): string
{
    if ($number % 15 === 0) {
        return 'FizzBuzz';
    }
    if ($number % 3 === 0) {
        return 'Fizz';
    }
    if ($number % 5 === 0) {
        return 'Buzz';
    }
    return (string) $number;
}
```

PHP の `%` 演算子（剰余演算子）は Go や Java と同じ挙動です。

```bash
$ vendor/bin/phpunit
OK (6 tests, 6 assertions)
```

さらにテストを追加して確認します。

```php
public function test_10を渡したら文字列Buzzを返す(): void
{
    $fizzbuzz = new FizzBuzz();
    $this->assertSame('Buzz', $fizzbuzz->generate(10));
}

public function test_30を渡したら文字列FizzBuzzを返す(): void
{
    $fizzbuzz = new FizzBuzz();
    $this->assertSame('FizzBuzz', $fizzbuzz->generate(30));
}
```

```bash
$ vendor/bin/phpunit
OK (8 tests, 8 assertions)
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 3.3 配列を返す機能

### Red: generateList のテスト

1 から 100 までの FizzBuzz 結果を配列で返す `generateList` メソッドのテストを書きます。

```php
public function test_1から100までのFizzBuzz配列を返す(): void
{
    $fizzbuzz = new FizzBuzz();
    $result = $fizzbuzz->generateList();

    $this->assertSame('1', $result[0]);
    $this->assertSame('Fizz', $result[2]);
    $this->assertSame('Buzz', $result[4]);
    $this->assertSame('FizzBuzz', $result[14]);
    $this->assertSame('100', $result[99]);
    $this->assertCount(100, $result);
}
```

### Green: generateList の実装

```php
/**
 * @return string[]
 */
public function generateList(): array
{
    $result = [];
    for ($i = 1; $i <= 100; $i++) {
        $result[] = $this->generate($i);
    }
    return $result;
}
```

PHP の `$result[]` は配列の末尾に要素を追加する構文です。Go の `append(results, ...)` に相当します。PHPDoc の `@return string[]` アノテーションで戻り値の型をドキュメント化しています。

```bash
$ vendor/bin/phpunit
OK (9 tests, 7 assertions)
```

## 3.4 プリント機能

### Red: printFizzBuzz のテスト

```php
public function test_FizzBuzzをプリントする(): void
{
    $fizzbuzz = new FizzBuzz();

    ob_start();
    $fizzbuzz->printFizzBuzz();
    $output = ob_get_clean();

    $lines = explode("\n", trim($output));
    $this->assertSame('1', $lines[0]);
    $this->assertSame('Fizz', $lines[2]);
    $this->assertSame('Buzz', $lines[4]);
    $this->assertSame('FizzBuzz', $lines[14]);
    $this->assertCount(100, $lines);
}
```

PHP の `ob_start()` / `ob_get_clean()` はアウトプットバッファリング機能で、標準出力をキャプチャできます。Go のテストで `bytes.Buffer` を使うパターンに相当します。

### Green: printFizzBuzz の実装

```php
public function printFizzBuzz(): void
{
    $list = $this->generateList();
    foreach ($list as $item) {
        echo $item . "\n";
    }
}
```

```bash
$ vendor/bin/phpunit
OK (10 tests, 12 assertions)
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [x] プリントする

すべての TODO が完了しました！

```bash
$ git add .
$ git commit -m 'feat: FizzBuzz の基本実装を完了'
```

## 3.5 完成したコード

<details>
<summary>src/FizzBuzz.php</summary>

```php
<?php

namespace App;

class FizzBuzz
{
    public function generate(int $number): string
    {
        if ($number % 15 === 0) {
            return 'FizzBuzz';
        }
        if ($number % 3 === 0) {
            return 'Fizz';
        }
        if ($number % 5 === 0) {
            return 'Buzz';
        }
        return (string) $number;
    }

    /**
     * @return string[]
     */
    public function generateList(): array
    {
        $result = [];
        for ($i = 1; $i <= 100; $i++) {
            $result[] = $this->generate($i);
        }
        return $result;
    }

    public function printFizzBuzz(): void
    {
        $list = $this->generateList();
        foreach ($list as $item) {
            echo $item . "\n";
        }
    }
}
```

</details>

## 3.6 まとめ

この章では以下のことを学びました。

- **明白な実装** でシンプルな操作を直接実装する手法
- **リファクタリング** で仮実装を汎用的なロジックに変換する方法
- PHP の `%` 演算子による倍数チェック
- PHP の配列操作（`$result[]` による要素追加）
- PHP のアウトプットバッファリング（`ob_start()` / `ob_get_clean()`）による標準出力のテスト
- PHPDoc の `@return string[]` アノテーション

第 1 部では TDD の基本サイクル（Red → Green → Refactor）を体験しました。次の第 2 部では、開発環境の整備と自動化について学びます。
