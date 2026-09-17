---
type: Article
title: "第 2 章: 仮実装と三角測量"
description: "前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。"
tags: [article, getting-start-tdd, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 2 章: 仮実装と三角測量

## 2.1 はじめに

前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。この章では、**三角測量** によってプログラムを一般化し、さらに **明白な実装** で FizzBuzz のコアロジックを完成させます。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
  - [ ] 2 を渡したら文字列 "2" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.2 三角測量

1 を渡したら文字列 "1" を返すようにできました。では、2 を渡したらどうなるでしょうか？

### Red: 2 つ目のテストを書く

```python
def test_2を渡したら文字列2を返す(self):
    assert self.fizzbuzz.generate(2) == "2"
```

テストを実行します。

```bash
$ uv run pytest -v
```

```
test/test_fizzbuzz.py::TestFizzBuzz::test_1を渡したら文字列1を返す PASSED
test/test_fizzbuzz.py::TestFizzBuzz::test_2を渡したら文字列2を返す FAILED

E       AssertionError: assert '1' == '2'
```

テストが失敗しました。文字列 "1" しか返さないプログラムなのですから当然です。

### Green: 一般化する

数値を文字列に変換して返すように修正します。

```python
# lib/fizzbuzz.py
class FizzBuzz:
    def generate(self, number: int) -> str:
        return str(number)
```

テストを実行します。

```bash
$ uv run pytest -v
```

```
test/test_fizzbuzz.py::TestFizzBuzz::test_1を渡したら文字列1を返す PASSED
test/test_fizzbuzz.py::TestFizzBuzz::test_2を渡したら文字列2を返す PASSED

2 passed
```

テストが通りました！2 つ目のテストによって `generate` メソッドの一般化を実現できました。このようなアプローチを **三角測量** と言います。

> 三角測量
>
> テストから最も慎重に一般化を引き出すやり方はどのようなものだろうか——2 つ以上の例があるときだけ、一般化を行うようにしよう。
>
> — テスト駆動開発

**TODO リスト**:

- [x] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
  - [x] 2 を渡したら文字列 "2" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.3 3 の倍数 — Fizz

次は「3 の倍数のときは数の代わりに Fizz と返す」に取り掛かります。

### Red: 3 の倍数のテスト

```python
def test_3を渡したら文字列Fizzを返す(self):
    assert self.fizzbuzz.generate(3) == "Fizz"
```

```
test/test_fizzbuzz.py::TestFizzBuzz::test_3を渡したら文字列Fizzを返す FAILED

E       AssertionError: assert '3' == 'Fizz'
```

### Green: 明白な実装

3 の倍数の判定は明白なので、直接的に実装します。

> 明白な実装
>
> シンプルな操作を実現するにはどうすればよいだろうか——そのまま実装しよう。
>
> — テスト駆動開発

```python
# lib/fizzbuzz.py
class FizzBuzz:
    def generate(self, number: int) -> str:
        if number % 3 == 0:
            return "Fizz"
        return str(number)
```

```
test/test_fizzbuzz.py::TestFizzBuzz::test_1を渡したら文字列1を返す PASSED
test/test_fizzbuzz.py::TestFizzBuzz::test_2を渡したら文字列2を返す PASSED
test/test_fizzbuzz.py::TestFizzBuzz::test_3を渡したら文字列Fizzを返す PASSED

3 passed
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.4 5 の倍数 — Buzz

### Red

```python
def test_5を渡したら文字列Buzzを返す(self):
    assert self.fizzbuzz.generate(5) == "Buzz"
```

```
test/test_fizzbuzz.py::TestFizzBuzz::test_5を渡したら文字列Buzzを返す FAILED

E       AssertionError: assert '5' == 'Buzz'
```

### Green

```python
# lib/fizzbuzz.py
class FizzBuzz:
    def generate(self, number: int) -> str:
        if number % 3 == 0:
            return "Fizz"
        if number % 5 == 0:
            return "Buzz"
        return str(number)
```

```
test/test_fizzbuzz.py::TestFizzBuzz::test_1を渡したら文字列1を返す PASSED
test/test_fizzbuzz.py::TestFizzBuzz::test_2を渡したら文字列2を返す PASSED
test/test_fizzbuzz.py::TestFizzBuzz::test_3を渡したら文字列Fizzを返す PASSED
test/test_fizzbuzz.py::TestFizzBuzz::test_5を渡したら文字列Buzzを返す PASSED

4 passed
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.5 3 と 5 の倍数 — FizzBuzz

### Red

```python
def test_15を渡したら文字列FizzBuzzを返す(self):
    assert self.fizzbuzz.generate(15) == "FizzBuzz"
```

```
test/test_fizzbuzz.py::TestFizzBuzz::test_15を渡したら文字列FizzBuzzを返す FAILED

E       AssertionError: assert 'Fizz' == 'FizzBuzz'
```

現在の実装では 3 の倍数の条件が先に評価されるため、15 を渡すと "Fizz" が返ってきています。

### Green

3 と 5 の両方の倍数を先に判定するように修正します。

```python
# lib/fizzbuzz.py
class FizzBuzz:
    def generate(self, number: int) -> str:
        if number % 15 == 0:
            return "FizzBuzz"
        if number % 3 == 0:
            return "Fizz"
        if number % 5 == 0:
            return "Buzz"
        return str(number)
```

```
test/test_fizzbuzz.py::TestFizzBuzz::test_1を渡したら文字列1を返す PASSED
test/test_fizzbuzz.py::TestFizzBuzz::test_2を渡したら文字列2を返す PASSED
test/test_fizzbuzz.py::TestFizzBuzz::test_3を渡したら文字列Fizzを返す PASSED
test/test_fizzbuzz.py::TestFizzBuzz::test_5を渡したら文字列Buzzを返す PASSED
test/test_fizzbuzz.py::TestFizzBuzz::test_15を渡したら文字列FizzBuzzを返す PASSED

5 passed
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.6 まとめ

この章では、以下の TDD テクニックを実践しました。

1. **三角測量** — 2 つ以上のテストケースから一般化を導き出す
2. **明白な実装** — ロジックが明確な場合は直接的に実装する
3. **Red-Green サイクル** — テスト失敗（Red）→ 最小限の実装（Green）を繰り返す

FizzBuzz のコアロジック（generate メソッド）が完成しました。次の章では、残りの TODO（リスト生成とプリント）を完成させ、リファクタリングを行います。
