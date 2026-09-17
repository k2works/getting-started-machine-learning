---
type: Article
title: "第 10 章: 高階関数と関数合成"
tags: [article, getting-start-tdd, php]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数と関数合成

## 10.1 ファーストクラス関数

PHP では関数は**ファーストクラスオブジェクト**です。関数を変数に代入したり、引数として渡したり、戻り値として返したりできます。PHP には 2 つの無名関数構文があります。

### クロージャとアロー関数

| 構文 | 説明 | 外部変数の参照 |
|------|------|--------------|
| `function () { ... }` | クロージャ（無名関数） | `use ($var)` で明示的キャプチャ |
| `fn() => ...` | アロー関数（PHP 7.4+） | 自動キャプチャ（暗黙的） |

```php
// クロージャ（無名関数）
$isFizz = function (FizzBuzzValue $v): bool {
    return $v->getValue() === 'Fizz';
};

// アロー関数（PHP 7.4+）
$isFizz = fn(FizzBuzzValue $v): bool => $v->getValue() === 'Fizz';
```

### テスト: 関数を変数に代入する

```php
public function test_アロー関数でFizzを判定する(): void
{
    $isFizz = fn(FizzBuzzValue $v): bool => $v->getValue() === 'Fizz';

    $value = new FizzBuzzValue(3, 'Fizz');
    $this->assertTrue($isFizz($value));
}
```

### テスト: 関数を引数として渡す（Filter）

```php
public function test_Filterでfizzだけを抽出する(): void
{
    $type = new FizzBuzzType01();
    $command = new FizzBuzzListCommand($type, 15);
    $list = $command->execute();

    $isFizz = fn(FizzBuzzValue $v): bool => $v->getValue() === 'Fizz';
    $filtered = $list->filter($isFizz);

    foreach ($filtered->getValue() as $v) {
        $this->assertSame('Fizz', $v->getValue());
    }
}
```

<details>
<summary>実装コード: filter メソッド</summary>

```php
/**
 * @param callable(FizzBuzzValue): bool $predicate
 */
public function filter(callable $predicate): self
{
    return new self(
        array_values(array_filter($this->value, $predicate))
    );
}
```

</details>

### テスト: Map で値を変換する

```php
public function test_Mapで値を変換する(): void
{
    $values = [
        new FizzBuzzValue(1, '1'),
        new FizzBuzzValue(3, 'Fizz'),
    ];
    $list = new FizzBuzzList($values);

    $toUpper = fn(FizzBuzzValue $v): string => strtoupper($v->getValue());
    $result = $list->map($toUpper);

    $this->assertSame(['1', 'FIZZ'], $result);
}
```

<details>
<summary>実装コード: map メソッド</summary>

```php
/**
 * @template R
 * @param callable(FizzBuzzValue): R $fn
 * @return R[]
 */
public function map(callable $fn): array
{
    return array_map($fn, $this->value);
}
```

</details>

## 10.2 クロージャ

クロージャは外側のスコープの変数をキャプチャした関数です。

### テスト: クロージャで述語関数を生成する

```php
public function test_述語関数を生成して使用する(): void
{
    $makeValuePredicate = fn(string $target): \Closure =>
        fn(FizzBuzzValue $v): bool => $v->getValue() === $target;

    $isFizz = $makeValuePredicate('Fizz');
    $isBuzz = $makeValuePredicate('Buzz');

    $value = new FizzBuzzValue(3, 'Fizz');
    $this->assertTrue($isFizz($value));
    $this->assertFalse($isBuzz($value));
}
```

### PHP のクロージャと `use` キーワード

```php
// アロー関数: 外部変数を自動キャプチャ
$target = 'Fizz';
$predicate = fn(FizzBuzzValue $v): bool => $v->getValue() === $target;

// クロージャ: use で明示的にキャプチャ
$predicate = function (FizzBuzzValue $v) use ($target): bool {
    return $v->getValue() === $target;
};
```

## 10.3 関数合成

PHP には演算子レベルの関数合成構文はありませんが、**高階関数を使って手動で合成**できます。

### テスト: Filter と Map を組み合わせる

```php
public function test_FilterとMapを組み合わせる(): void
{
    $type = new FizzBuzzType01();
    $command = new FizzBuzzListCommand($type, 15);
    $list = $command->execute();

    $isFizz = fn(FizzBuzzValue $v): bool => $v->getValue() === 'Fizz';
    $getValue = fn(FizzBuzzValue $v): string => $v->getValue();

    $result = $list->filter($isFizz)->map($getValue);

    foreach ($result as $s) {
        $this->assertSame('Fizz', $s);
    }
}
```

## 10.4 各言語の高階関数比較

| 機能 | PHP | Go | Java | Ruby | TypeScript |
|------|-----|-----|------|------|------------|
| 関数型 | `callable` / `Closure` | `func(T) R` | `Function<T,R>` | Proc / Lambda | `(t: T) => R` |
| クロージャ | `function () use ($v)` | `func` リテラル | Lambda 式 | ブロック / Lambda | アロー関数 |
| アロー関数 | `fn() =>` | なし | `->` | `->` | `=>` |
| フィルタ | `array_filter` | 手動 `for` ループ | `Stream.filter` | `select` | `Array.filter` |
| マップ | `array_map` | 手動 `for` ループ | `Stream.map` | `map` | `Array.map` |

## 10.5 まとめ

本章では以下を学びました:

- **ファーストクラス関数**: `callable` / `Closure` で関数を変数・引数・戻り値として扱える
- **アロー関数**: `fn() =>` で簡潔な無名関数（PHP 7.4+）
- **クロージャ**: `use` キーワードで外部変数を明示的にキャプチャ
- **array_filter / array_map**: PHP 組み込みの高階関数
- **Filter / Map メソッド**: FizzBuzzList に関数型メソッドを追加
