---
type: Article
title: "第 12 章: エラーハンドリングと型安全性"
description: "前章までに、関数型プログラミングのテクニックを FizzBuzz に適用しました。"
tags: [article, getting-start-tdd, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 12 章: エラーハンドリングと型安全性

## 12.1 はじめに

前章までに、関数型プログラミングのテクニックを FizzBuzz に適用しました。最終章では、**型安全性** を強化し、マジックナンバーを排除した堅牢な設計を完成させます。

## 12.2 None 安全と Optional パターン

Python には Java の `Optional` に直接対応する型はありませんが、`None` と型ヒント `T | None` で同等のパターンを実現できます。

### ファクトリメソッドの改善

従来の `FizzBuzzType.create()` は、未定義タイプに `FizzBuzzTypeNotDefined` を返していました。呼び出し側が `None` の可能性を認識できるように改善します。

```python
@staticmethod
def create(type_: int) -> "FizzBuzzType | None":
    types: dict[int, type[FizzBuzzType]] = {
        1: FizzBuzzType01,
        2: FizzBuzzType02,
        3: FizzBuzzType03,
    }
    cls = types.get(type_)
    return cls() if cls else None
```

## 12.3 列挙型による型安全なファクトリ

マジックナンバー（1, 2, 3）の代わりに `Enum` を使います。

```python
from enum import Enum

class FizzBuzzTypeName(Enum):
    STANDARD = "standard"
    NUMBER_ONLY = "number_only"
    FIZZ_BUZZ_ONLY = "fizz_buzz_only"
```

### 型安全なファクトリメソッド

```python
@staticmethod
def create_from_name(name: FizzBuzzTypeName) -> "FizzBuzzType":
    mapping: dict[FizzBuzzTypeName, type[FizzBuzzType]] = {
        FizzBuzzTypeName.STANDARD: FizzBuzzType01,
        FizzBuzzTypeName.NUMBER_ONLY: FizzBuzzType02,
        FizzBuzzTypeName.FIZZ_BUZZ_ONLY: FizzBuzzType03,
    }
    return mapping[name]()
```

### テスト

```python
def test_列挙型でタイプを生成する(self) -> None:
    type_ = FizzBuzzType.create_from_name(FizzBuzzTypeName.STANDARD)
    assert type_.generate(15) == FizzBuzzValue(15, "FizzBuzz")

def test_存在しないタイプはKeyErrorになる(self) -> None:
    # Enum はそもそも不正な値を許さない
    with pytest.raises(KeyError):
        FizzBuzzTypeName("invalid")
```

## 12.4 match 文によるパターンマッチング

Python 3.10 以降で使える `match` 文を活用します。

```python
def describe(value: FizzBuzzValue) -> str:
    match value.value:
        case "Fizz":
            return f"{value.number} は 3 の倍数"
        case "Buzz":
            return f"{value.number} は 5 の倍数"
        case "FizzBuzz":
            return f"{value.number} は 15 の倍数"
        case _:
            return f"{value.number} はそのまま"
```

## 12.5 エントリポイントの更新

```python
# main.py
from lib.domain.type.fizz_buzz_type import FizzBuzzType
from lib.domain.type.fizz_buzz_type_name import FizzBuzzTypeName
from lib.application.fizz_buzz_list_command import FizzBuzzListCommand

if __name__ == "__main__":
    type_ = FizzBuzzType.create_from_name(FizzBuzzTypeName.STANDARD)
    command = FizzBuzzListCommand(type_)
    result = command.execute()
    for i in range(result.size()):
        print(result.get(i))
```

## 12.6 まとめ

### 第 4 部の振り返り

| 章 | テーマ | 適用した技法 |
|----|--------|------------|
| 10 | 高階関数と関数合成 | `lambda`、リスト内包表記、`filter()`、`functools` |
| 11 | 不変データとパイプライン | 非破壊的 `add()`、ジェネレータ、`itertools` |
| 12 | エラーハンドリングと型安全性 | `Enum`、型安全ファクトリ、`match` 文 |

### 全体の振り返り

```
第 1 部: 手続き的な FizzBuzz（1 クラス）
    ↓
第 2 部: 開発環境と自動化（テスト・Lint・CI）
    ↓
第 3 部: オブジェクト指向設計（ポリモーフィズム・パターン・SOLID）
    ↓
第 4 部: 関数型プログラミング（高階関数・不変データ・型安全性）
```

| 概念 | Java | Python |
|------|------|--------|
| Optional | `Optional<T>` | `T \| None` + 型ヒント |
| 列挙型 | `enum` | `enum.Enum` |
| パターンマッチング | `switch` 式 | `match` 文 |
| 型安全ファクトリ | `enum` + `static` メソッド | `Enum` + `@staticmethod` |
