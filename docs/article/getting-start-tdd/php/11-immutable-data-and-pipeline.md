---
type: Article
title: "第 11 章: 不変データとパイプライン処理"
tags: [article, getting-start-tdd, php]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 11 章: 不変データとパイプライン処理

## 11.1 不変データ設計の確認

PHP 8.1+ の **readonly プロパティ**により、言語レベルで不変性を保証できます。

### FizzBuzzValue の不変設計

第 8 章で作成した `FizzBuzzValue` は既に不変です:

```php
final class FizzBuzzValue
{
    public function __construct(
        private readonly int $number,    // 変更不可
        private readonly string $value,  // 変更不可
    ) {
    }
}
```

- **readonly** で一度設定した値を変更不可に
- **private** で外部からの直接アクセスを禁止
- setter メソッドがない

### FizzBuzzList の不変設計

```php
final class FizzBuzzList
{
    private readonly array $value;  // 変更不可
}
```

- `filter` や `map` は**新しいインスタンス/配列**を返す（元のリストは変更しない）
- `readonly` で配列の再代入を禁止

### テスト: Filter が元のリストを変更しないことを確認する

```php
public function test_filterは元のリストを変更しない(): void
{
    $type = new FizzBuzzType01();
    $command = new FizzBuzzListCommand($type, 15);
    $original = $command->execute();
    $originalCount = $original->count();

    $isFizz = fn(FizzBuzzValue $v): bool => $v->getValue() === 'Fizz';
    $original->filter($isFizz);

    $this->assertSame($originalCount, $original->count());
}
```

## 11.2 パイプラインメソッド

FizzBuzzList にパイプライン処理のためのメソッドを追加します。

### テスト: groupByValue -- 値でグルーピングする

```php
public function test_groupByValueで値でグルーピングする(): void
{
    $type = new FizzBuzzType01();
    $command = new FizzBuzzListCommand($type, 15);
    $list = $command->execute();

    $grouped = $list->groupByValue();

    $this->assertArrayHasKey('Fizz', $grouped);
    $this->assertArrayHasKey('Buzz', $grouped);
    $this->assertArrayHasKey('FizzBuzz', $grouped);
}
```

<details>
<summary>実装コード: groupByValue</summary>

```php
/**
 * @return array<string, FizzBuzzValue[]>
 */
public function groupByValue(): array
{
    $result = [];
    foreach ($this->value as $v) {
        $result[$v->getValue()][] = $v;
    }
    return $result;
}
```

</details>

### テスト: countByValue -- 値ごとの出現回数を数える

```php
public function test_countByValueで値ごとの出現回数を数える(): void
{
    $type = new FizzBuzzType01();
    $command = new FizzBuzzListCommand($type, 15);
    $list = $command->execute();

    $counts = $list->countByValue();

    $this->assertSame(1, $counts['FizzBuzz']);
}
```

<details>
<summary>実装コード: countByValue</summary>

```php
/**
 * @return array<string, int>
 */
public function countByValue(): array
{
    $result = [];
    foreach ($this->value as $v) {
        $key = $v->getValue();
        $result[$key] = ($result[$key] ?? 0) + 1;
    }
    return $result;
}
```

</details>

### テスト: take -- 先頭 N 件を取得する

```php
public function test_takeで先頭N件を取得する(): void
{
    $type = new FizzBuzzType01();
    $command = new FizzBuzzListCommand($type, 15);
    $list = $command->execute();

    $taken = $list->take(5);

    $this->assertSame(5, $taken->count());
}
```

<details>
<summary>実装コード: take</summary>

```php
public function take(int $n): self
{
    return new self(array_slice($this->value, 0, $n));
}
```

</details>

### テスト: join -- 要素を文字列で結合する

```php
public function test_joinで要素を文字列で結合する(): void
{
    $values = [
        new FizzBuzzValue(1, '1'),
        new FizzBuzzValue(2, '2'),
        new FizzBuzzValue(3, 'Fizz'),
    ];
    $list = new FizzBuzzList($values);

    $result = $list->join(', ');

    $this->assertSame('1, 2, Fizz', $result);
}
```

<details>
<summary>実装コード: join</summary>

```php
public function join(string $separator): string
{
    return implode($separator, $this->toStringArray());
}
```

</details>

## 11.3 メソッドチェーンによるパイプライン

`filter` と `take` は `FizzBuzzList` を返すため、**メソッドチェーン**でパイプラインを構築できます。

### テスト: メソッドチェーンで Fizz を 3 件取得して結合する

```php
public function test_メソッドチェーンでパイプラインを構築する(): void
{
    $type = new FizzBuzzType01();
    $command = new FizzBuzzListCommand($type);
    $list = $command->execute();

    $result = $list
        ->filter(fn(FizzBuzzValue $v): bool => $v->getValue() === 'Fizz')
        ->take(3)
        ->join(', ');

    $this->assertSame('Fizz, Fizz, Fizz', $result);
}
```

### array_reduce パターン

PHP の `array_reduce` を使った畳み込み:

### テスト: reduce で数値の合計を計算する

```php
public function test_reduceで数値の合計を計算する(): void
{
    $values = [
        new FizzBuzzValue(1, '1'),
        new FizzBuzzValue(2, '2'),
        new FizzBuzzValue(3, 'Fizz'),
    ];
    $list = new FizzBuzzList($values);

    $sum = $list->reduce(0, fn(int $acc, FizzBuzzValue $v): int => $acc + $v->getNumber());

    $this->assertSame(6, $sum);
}
```

<details>
<summary>実装コード: reduce</summary>

```php
/**
 * @template R
 * @param R $initial
 * @param callable(R, FizzBuzzValue): R $fn
 * @return R
 */
public function reduce(mixed $initial, callable $fn): mixed
{
    return array_reduce($this->value, $fn, $initial);
}
```

</details>

## 11.4 各言語のパイプライン比較

| 機能 | PHP | Go | Java | Ruby | TypeScript |
|------|-----|-----|------|------|------------|
| パイプライン構文 | メソッドチェーン | メソッドチェーン | Stream API | メソッドチェーン | メソッドチェーン |
| グルーピング | 手動 `foreach` | 手動 `map` 構築 | `Collectors.groupingBy` | `group_by` | `reduce` |
| 結合 | `implode` | `strings.Join` | `Collectors.joining` | `join` | `Array.join` |
| 畳み込み | `array_reduce` | 手動 `Reduce` | `Stream.reduce` | `reduce` / `inject` | `Array.reduce` |
| 遅延評価 | なし（即時評価） | なし（即時評価） | `Stream`（遅延） | `Lazy` | なし |

## 11.5 まとめ

本章では以下を学びました:

- **不変データ設計**: `readonly` プロパティで言語レベルの不変性保証
- **パイプラインメソッド**: groupByValue、countByValue、take、join
- **メソッドチェーン**: filter -> take -> join のパイプライン構築
- **array_reduce**: PHP 組み込みの畳み込み関数
