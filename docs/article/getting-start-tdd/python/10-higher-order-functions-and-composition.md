---
type: Article
title: "第 10 章: 高階関数と関数合成"
description: "第 3 部でオブジェクト指向設計を完成させました。"
tags: [article, getting-start-tdd, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数と関数合成

## 10.1 はじめに

第 3 部でオブジェクト指向設計を完成させました。第 4 部では、Python の **関数型プログラミング** 機能を FizzBuzz に適用します。この章では、**高階関数** と **関数合成** を導入し、命令的なコードを宣言的に書き換えます。

## 10.2 ラムダ式

Python では `lambda` キーワードで無名関数を定義できます。

```python
# 通常の関数定義
def double(x: int) -> int:
    return x * 2

# ラムダ式
double = lambda x: x * 2
```

## 10.3 組み込み高階関数

Python には `map()`、`filter()`、`sorted()` などの高階関数が組み込まれています。

```python
numbers = [1, 2, 3, 4, 5]

# map: 各要素を変換
doubled = list(map(lambda x: x * 2, numbers))

# filter: 条件に合う要素を抽出
evens = list(filter(lambda x: x % 2 == 0, numbers))

# sorted: カスタムキーでソート
sorted(numbers, key=lambda x: -x)
```

## 10.4 リスト内包表記

Python では、`map()` + `filter()` の代わりに **リスト内包表記** がよく使われます。

```python
# map + filter 相当
result = [x * 2 for x in numbers if x % 2 == 0]
```

## 10.5 FizzBuzzListCommand のリファクタリング

従来のループを関数型スタイルに書き換えます。

### Before（命令的）

```python
def execute(self) -> FizzBuzzList:
    result = FizzBuzzList()
    for i in range(1, self.MAX_NUMBER + 1):
        result.add(self._type.generate(i))
    return result
```

### After（関数型）

```python
def execute(self) -> FizzBuzzList:
    values = [self._type.generate(i) for i in range(1, self.MAX_NUMBER + 1)]
    return FizzBuzzList(values)
```

## 10.6 FizzBuzzList にフィルタリングを追加

特定の条件に合う値だけを抽出するメソッドを追加します。

```python
from collections.abc import Callable

class FizzBuzzList:
    def filter(self, predicate: Callable[[FizzBuzzValue], bool]) -> "FizzBuzzList":
        return FizzBuzzList([v for v in self._values if predicate(v)])
```

### テスト

```python
def test_Fizzだけをフィルタリングする(self) -> None:
    type_ = FizzBuzzType.create(1)
    command = FizzBuzzListCommand(type_)
    result = command.execute()
    fizzes = result.filter(lambda v: v.value == "Fizz")
    assert fizzes.size() > 0
    assert all(fizzes.get(i).value == "Fizz" for i in range(fizzes.size()))
```

## 10.7 関数合成

`functools` モジュールを使って関数を合成します。

```python
from functools import reduce

def compose(*funcs):
    """右から左に関数を合成する。"""
    return reduce(lambda f, g: lambda x: f(g(x)), funcs)
```

### FizzBuzzList での文字列変換

```python
class FizzBuzzList:
    def to_formatted_string(self, separator: str = "\n") -> str:
        return separator.join(repr(v) for v in self._values)
```

## 10.8 まとめ

| 概念 | Java | Python |
|------|------|--------|
| ラムダ式 | `(x) -> x * 2` | `lambda x: x * 2` |
| map | `stream.map()` | `map()` / リスト内包表記 |
| filter | `stream.filter()` | `filter()` / リスト内包表記 |
| reduce | `stream.reduce()` | `functools.reduce()` |
| 関数合成 | `f.andThen(g)` | `compose(f, g)` |

次の章では、不変データとパイプライン処理を適用します。
