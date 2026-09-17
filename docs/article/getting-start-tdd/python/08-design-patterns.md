---
type: Article
title: "第 8 章: デザインパターンの適用"
description: "前章では、カプセル化とポリモーフィズムで if/elif の連鎖を排除しました。"
tags: [article, getting-start-tdd, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 8 章: デザインパターンの適用

## 8.1 はじめに

前章では、カプセル化とポリモーフィズムで `if/elif` の連鎖を排除しました。この章では、さらに **デザインパターン** を適用して設計を洗練させます。

適用するパターン:

1. **Value Object** — 値をオブジェクトとして表現
2. **First-Class Collection** — コレクションをドメインオブジェクト化
3. **Command パターン** — 操作をオブジェクト化
4. **Null Object パターン** — 例外の代わりにデフォルト動作を提供

## 8.2 Value Object（値オブジェクト）

FizzBuzz の結果を「数」と「変換後の文字列」のペアとして保持する値オブジェクトを作成します。

```python
class FizzBuzzValue:
    def __init__(self, number: int, value: str) -> None:
        self._number = number
        self._value = value

    @property
    def number(self) -> int:
        return self._number

    @property
    def value(self) -> str:
        return self._value

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, FizzBuzzValue):
            return NotImplemented
        return self._number == other._number and self._value == other._value

    def __hash__(self) -> int:
        return hash((self._number, self._value))

    def __repr__(self) -> str:
        return f"{self._number}:{self._value}"
```

### テスト

```python
class TestFizzBuzzValue:
    def test_値を保持する(self):
        value = FizzBuzzValue(1, "1")
        assert value.number == 1
        assert value.value == "1"

    def test_同じ値は等しい(self):
        assert FizzBuzzValue(1, "1") == FizzBuzzValue(1, "1")

    def test_文字列表現(self):
        assert repr(FizzBuzzValue(3, "Fizz")) == "3:Fizz"
```

### FizzBuzzType の更新

`generate` の戻り値を `str` から `FizzBuzzValue` に変更します。

```python
class FizzBuzzType01(FizzBuzzType):
    def generate(self, number: int) -> FizzBuzzValue:
        if self._is_fizz_buzz(number):
            return FizzBuzzValue(number, "FizzBuzz")
        if self._is_fizz(number):
            return FizzBuzzValue(number, "Fizz")
        if self._is_buzz(number):
            return FizzBuzzValue(number, "Buzz")
        return FizzBuzzValue(number, str(number))
```

## 8.3 First-Class Collection

FizzBuzz の結果リストを専用クラスで包みます。

```python
class FizzBuzzList:
    def __init__(self, values: list[FizzBuzzValue] | None = None) -> None:
        self._values: list[FizzBuzzValue] = list(values) if values else []

    def add(self, value: FizzBuzzValue) -> None:
        self._values.append(value)

    def get(self, index: int) -> FizzBuzzValue:
        return self._values[index]

    def size(self) -> int:
        return len(self._values)

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, FizzBuzzList):
            return NotImplemented
        return self._values == other._values

    def __hash__(self) -> int:
        return hash(tuple(self._values))

    def __repr__(self) -> str:
        return "[" + ", ".join(repr(v) for v in self._values) + "]"
```

## 8.4 Command パターン

操作をオブジェクトとしてカプセル化します。

```python
from abc import ABC, abstractmethod

class FizzBuzzCommand(ABC):
    @abstractmethod
    def execute(self) -> FizzBuzzValue | FizzBuzzList:
        pass

class FizzBuzzValueCommand(FizzBuzzCommand):
    def __init__(self, type_: FizzBuzzType, number: int) -> None:
        self._type = type_
        self._number = number

    def execute(self) -> FizzBuzzValue:
        return self._type.generate(self._number)

class FizzBuzzListCommand(FizzBuzzCommand):
    MAX_NUMBER: int = 100

    def __init__(self, type_: FizzBuzzType) -> None:
        self._type = type_

    def execute(self) -> FizzBuzzList:
        result = FizzBuzzList()
        for i in range(1, self.MAX_NUMBER + 1):
            result.add(self._type.generate(i))
        return result
```

## 8.5 Null Object パターン

未定義のタイプに対して例外ではなくデフォルト動作を返します。

```python
class FizzBuzzTypeNotDefined(FizzBuzzType):
    def generate(self, number: int) -> FizzBuzzValue:
        return FizzBuzzValue(number, "")
```

ファクトリメソッドを更新します。

```python
@staticmethod
def create(type_: int) -> "FizzBuzzType":
    types = {
        1: FizzBuzzType01,
        2: FizzBuzzType02,
        3: FizzBuzzType03,
    }
    return types.get(type_, FizzBuzzTypeNotDefined)()
```

## 8.6 まとめ

| パターン | クラス | 効果 |
|----------|--------|------|
| Value Object | `FizzBuzzValue` | 値の意味を明示し、等価性を保証 |
| First-Class Collection | `FizzBuzzList` | コレクション操作を一元化 |
| Command | `FizzBuzzValueCommand` / `FizzBuzzListCommand` | 操作と実行を分離 |
| Null Object | `FizzBuzzTypeNotDefined` | 例外の代わりにデフォルト動作 |
| Factory Method | `FizzBuzzType.create()` | 生成ロジックの一元化 |

次の章では、SOLID 原則を確認しながらモジュール設計を行います。
