---
type: Article
title: "第 7 章: カプセル化とポリモーフィズム"
description: "第 1〜2 部で FizzBuzz の基本実装と開発環境を整えました。"
tags: [article, getting-start-tdd, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 7 章: カプセル化とポリモーフィズム

## 7.1 はじめに

第 1〜2 部で FizzBuzz の基本実装と開発環境を整えました。第 3 部では、手続き的なコードをオブジェクト指向設計にリファクタリングしていきます。この章では、FizzBuzz に **追加仕様** を導入し、**カプセル化** と **ポリモーフィズム** を適用します。

## 7.2 追加仕様

FizzBuzz プログラムに以下の 3 つのタイプを追加します。

| タイプ | 動作 |
|--------|------|
| タイプ 1 | 通常の FizzBuzz（3 の倍数→Fizz、5 の倍数→Buzz、両方→FizzBuzz） |
| タイプ 2 | 数をそのまま文字列で返す |
| タイプ 3 | 3 と 5 の倍数のときだけ FizzBuzz、それ以外は数を返す |

**TODO リスト**:

- [ ] タイプ 1: 通常の FizzBuzz
- [ ] タイプ 2: 数をそのまま返す
- [ ] タイプ 3: FizzBuzz のみ
- [ ] それ以外のタイプは例外を送出する

## 7.3 手続き的アプローチ

まず、テストから書きます。タイプをコンストラクタで受け取る設計にします。

```python
class TestFizzBuzz:
    class TestType1:
        def setup_method(self):
            self.fizzbuzz = FizzBuzz(1)

        def test_3と5の倍数の場合FizzBuzzを返す(self):
            assert self.fizzbuzz.generate(15) == "FizzBuzz"

        def test_3の倍数の場合Fizzを返す(self):
            assert self.fizzbuzz.generate(3) == "Fizz"

        def test_5の倍数の場合Buzzを返す(self):
            assert self.fizzbuzz.generate(5) == "Buzz"

        def test_その他の場合数を文字列で返す(self):
            assert self.fizzbuzz.generate(1) == "1"

    class TestType2:
        def setup_method(self):
            self.fizzbuzz = FizzBuzz(2)

        def test_数をそのまま文字列で返す(self):
            assert self.fizzbuzz.generate(3) == "3"

    class TestType3:
        def setup_method(self):
            self.fizzbuzz = FizzBuzz(3)

        def test_3と5の倍数の場合FizzBuzzを返す(self):
            assert self.fizzbuzz.generate(15) == "FizzBuzz"

        def test_その他の場合数を文字列で返す(self):
            assert self.fizzbuzz.generate(3) == "3"
```

手続き的な実装は `if/elif` の連鎖になります。

```python
class FizzBuzz:
    def __init__(self, type_: int) -> None:
        self._type = type_

    def generate(self, number: int) -> str:
        if self._type == 1:
            if number % 15 == 0:
                return "FizzBuzz"
            if number % 3 == 0:
                return "Fizz"
            if number % 5 == 0:
                return "Buzz"
            return str(number)
        elif self._type == 2:
            return str(number)
        elif self._type == 3:
            if number % 15 == 0:
                return "FizzBuzz"
            return str(number)
        else:
            raise ValueError("未定義のタイプです")
```

このコードには問題があります。タイプが増えるたびに `if/elif` が肥大化し、変更の影響範囲が広がります。

## 7.4 カプセル化

### @property によるフィールドアクセス制御

Python では `@property` デコレータを使ってフィールドのアクセスを制御します。setter を定義しないことで **読み取り専用** にできます。

```python
class FizzBuzz:
    def __init__(self, type_: int) -> None:
        self._type = type_

    @property
    def type(self) -> int:
        return self._type
```

Java の `private final` + getter に相当します。外部から `fizzbuzz.type` で読み取れますが、`fizzbuzz.type = 2` のような代入はエラーになります。

## 7.5 ポリモーフィズム

### 抽象基底クラスの導入

`if/elif` の連鎖をポリモーフィズムで置き換えます。Python では `abc.ABC` と `@abstractmethod` を使います。

```python
from abc import ABC, abstractmethod

class FizzBuzzType(ABC):
    @abstractmethod
    def generate(self, number: int) -> str:
        pass

    def _is_fizz(self, number: int) -> bool:
        return number % 3 == 0

    def _is_buzz(self, number: int) -> bool:
        return number % 5 == 0

    def _is_fizz_buzz(self, number: int) -> bool:
        return number % 15 == 0
```

### 具象クラスの作成

```python
class FizzBuzzType01(FizzBuzzType):
    def generate(self, number: int) -> str:
        if self._is_fizz_buzz(number):
            return "FizzBuzz"
        if self._is_fizz(number):
            return "Fizz"
        if self._is_buzz(number):
            return "Buzz"
        return str(number)

class FizzBuzzType02(FizzBuzzType):
    def generate(self, number: int) -> str:
        return str(number)

class FizzBuzzType03(FizzBuzzType):
    def generate(self, number: int) -> str:
        if self._is_fizz_buzz(number):
            return "FizzBuzz"
        return str(number)
```

### ファクトリメソッド

タイプの生成を一箇所にまとめます。

```python
class FizzBuzzType(ABC):
    @staticmethod
    def create(type_: int) -> "FizzBuzzType":
        if type_ == 1:
            return FizzBuzzType01()
        elif type_ == 2:
            return FizzBuzzType02()
        elif type_ == 3:
            return FizzBuzzType03()
        else:
            raise ValueError("未定義のタイプです")
```

## 7.6 テストの更新

リファクタリング後のテストは、ファクトリメソッド経由でタイプを生成します。

```python
class TestFizzBuzzType:
    class TestType01:
        def setup_method(self):
            self.type = FizzBuzzType.create(1)

        def test_FizzBuzzを返す(self):
            assert self.type.generate(15) == "FizzBuzz"

        def test_Fizzを返す(self):
            assert self.type.generate(3) == "Fizz"

        def test_Buzzを返す(self):
            assert self.type.generate(5) == "Buzz"

        def test_数を文字列で返す(self):
            assert self.type.generate(1) == "1"
```

## 7.7 まとめ

| 概念 | Java | Python |
|------|------|--------|
| フィールド隠蔽 | `private final` | `_` 接頭辞 + `@property` |
| 抽象クラス | `abstract class` | `abc.ABC` + `@abstractmethod` |
| ファクトリメソッド | `static` メソッド | `@staticmethod` |
| 条件分岐の排除 | ポリモーフィズム | ポリモーフィズム |

次の章では、Value Object や Command パターンなどのデザインパターンを適用します。
