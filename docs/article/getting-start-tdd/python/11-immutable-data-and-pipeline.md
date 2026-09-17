---
type: Article
title: "第 11 章: 不変データとパイプライン処理"
description: "前章では高階関数と関数合成を導入しました。"
tags: [article, getting-start-tdd, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 11 章: 不変データとパイプライン処理

## 11.1 はじめに

前章では高階関数と関数合成を導入しました。この章では、**不変データ**（Immutable Data）の原則に基づいてコレクション操作を改善し、**ジェネレータ** を使ったパイプライン処理を実装します。

## 11.2 不変データの原則

Martin Fowler 曰く:「データは決して変更しない。コピーする。」

Python では以下の方法で不変性を実現します:

- `tuple`: 不変リスト
- `frozenset`: 不変集合
- `@dataclass(frozen=True)`: 不変データクラス

## 11.3 FizzBuzzList の不変化

`add()` メソッドを、既存リストを変更する代わりに **新しいリストを返す** ように変更します。

### Before（破壊的変更）

```python
def add(self, value: FizzBuzzValue) -> None:
    self._values.append(value)
```

### After（非破壊的）

```python
def add(self, value: FizzBuzzValue) -> "FizzBuzzList":
    return FizzBuzzList(self._values + [value])
```

### テスト

```python
def test_addは新しいリストを返す(self) -> None:
    lst1 = FizzBuzzList()
    lst2 = lst1.add(FizzBuzzValue(1, "1"))
    assert lst1.size() == 0  # 元のリストは変更されない
    assert lst2.size() == 1
```

## 11.4 ジェネレータによるパイプライン処理

Python のジェネレータを使って、遅延評価のパイプラインを構築します。

```python
from collections.abc import Generator

def fizzbuzz_pipeline(
    type_: FizzBuzzType, count: int
) -> Generator[FizzBuzzValue, None, None]:
    for i in range(1, count + 1):
        yield type_.generate(i)
```

## 11.5 統計情報の集計

`FizzBuzzList` にグルーピングや集計のメソッドを追加します。

```python
class FizzBuzzList:
    def group_by_value(self) -> dict[str, list[FizzBuzzValue]]:
        groups: dict[str, list[FizzBuzzValue]] = {}
        for v in self._values:
            groups.setdefault(v.value, []).append(v)
        return groups

    def statistics(self) -> dict[str, int]:
        groups = self.group_by_value()
        return {key: len(values) for key, values in groups.items()}
```

### テスト

```python
def test_統計情報を取得する(self) -> None:
    type_ = FizzBuzzType.create(1)
    command = FizzBuzzListCommand(type_)
    result = command.execute()
    stats = result.statistics()
    assert stats["Fizz"] > 0
    assert stats["Buzz"] > 0
    assert stats["FizzBuzz"] > 0
```

## 11.6 itertools によるパイプライン処理

Python の `itertools` モジュールは関数型パイプライン処理に便利です。

```python
from itertools import islice, chain

# 最初の 10 個だけ取得
first_10 = list(islice(fizzbuzz_pipeline(type_, 100), 10))

# 複数のパイプラインを結合
combined = chain(
    fizzbuzz_pipeline(FizzBuzzType.create(1), 5),
    fizzbuzz_pipeline(FizzBuzzType.create(2), 5),
)
```

## 11.7 まとめ

| 概念 | Java | Python |
|------|------|--------|
| 不変コレクション | `Collections.unmodifiableList()` | 新しいリストを返すメソッド |
| ストリーム生成 | `IntStream.rangeClosed()` | ジェネレータ / `range()` |
| パイプライン | Stream API | ジェネレータチェーン |
| グルーピング | `Collectors.groupingBy()` | `dict.setdefault()` |
| 集計 | `Collectors.counting()` | `len()` / 内包表記 |

次の章では、エラーハンドリングと型安全性を改善します。
