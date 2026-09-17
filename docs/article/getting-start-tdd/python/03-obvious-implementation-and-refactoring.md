---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。"
tags: [article, getting-start-tdd, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 3 章: 明白な実装とリファクタリング

## 3.1 はじめに

前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。この章では、残りの TODO（リスト生成とプリント）を実装し、学習用テストを活用しながら「動作するきれいなコード」を目指してリファクタリングします。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 3.2 1 から 100 までのリスト生成

### Red: リスト生成のテスト

1 から 100 までの FizzBuzz の結果をリストとして返すメソッドをテストします。

```python
def test_1から100までのFizzBuzzを生成する(self):
    result = self.fizzbuzz.generate_list(100)

    assert len(result) == 100
    assert result[0] == "1"
    assert result[1] == "2"
    assert result[2] == "Fizz"
    assert result[3] == "4"
    assert result[4] == "Buzz"
    assert result[5] == "Fizz"
    assert result[14] == "FizzBuzz"
    assert result[99] == "Buzz"
```

```
test/test_fizzbuzz.py::TestFizzBuzz::test_1から100までのFizzBuzzを生成する FAILED

E       AttributeError: 'FizzBuzz' object has no attribute 'generate_list'
```

### Green: 明白な実装

Python のリスト内包表記を使って簡潔に実装します。

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

    def generate_list(self, count: int) -> list[str]:
        return [self.generate(i) for i in range(1, count + 1)]
```

```
test/test_fizzbuzz.py::TestFizzBuzz::test_1から100までのFizzBuzzを生成する PASSED

6 passed
```

Python ではリスト内包表記（`[expr for x in iterable]`）を使うことで、Java の `for` ループ + `ArrayList` よりも簡潔にリストを生成できます。

## 3.3 プリント機能

### 学習用テスト

プリント機能のテストには、標準出力のキャプチャが必要です。pytest では `capsys` フィクスチャを使って標準出力をキャプチャできます。学習用テストとして試してみましょう。

> 学習用テスト
>
> 外部のソフトウェアのテストを書くべきだろうか——そのソフトウェアに対して新しいことを初めて行おうとした段階で書いてみよう。
>
> — テスト駆動開発

```python
def test_プリントする(self, capsys):
    self.fizzbuzz.print_fizzbuzz(15)
    captured = capsys.readouterr()
    lines = captured.out.strip().split("\n")

    assert len(lines) == 15
    assert lines[0] == "1"
    assert lines[2] == "Fizz"
    assert lines[4] == "Buzz"
    assert lines[14] == "FizzBuzz"
```

### 実装

```python
def print_fizzbuzz(self, count: int) -> None:
    result = self.generate_list(count)
    for item in result:
        print(item)
```

### Main モジュールの作成

実行用のエントリポイントを作成します。

```python
# main.py
from lib.fizzbuzz import FizzBuzz

if __name__ == "__main__":
    fizzbuzz = FizzBuzz()
    fizzbuzz.print_fizzbuzz(100)
```

```bash
$ uv run python main.py | head -20
1
2
Fizz
4
Buzz
Fizz
7
8
Fizz
Buzz
11
Fizz
13
14
FizzBuzz
16
17
Fizz
19
Buzz
```

## 3.4 動作するきれいなコード

ここまでの実装で、すべての TODO が完了しました。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [x] プリントする

TDD では「動作するきれいなコード（Clean code that works）」を目指します。これはソフトウェア開発の三種の神器と組み合わせて実現します。

> ソフトウェア開発の三種の神器
>
> - バージョン管理
> - テスティング
> - 自動化
>
> — 和田卓人

現在のコードは動作していますが、テスト駆動開発のプロセスを振り返ってみましょう。

### TDD サイクルの振り返り

1. **仮実装** — `return "1"` でテストを通す
2. **三角測量** — 2 つ目のテストで `str(number)` に一般化
3. **明白な実装** — 3 の倍数、5 の倍数、15 の倍数の判定を直接実装
4. **追加実装** — リスト生成、プリント機能を追加

各ステップでテストが先にあり、テストが通ることを確認してから次のステップに進みました。これが TDD の基本サイクルです。

## 3.5 まとめ

第 1 部（章 1〜3）を通じて、TDD の基本サイクルを体験しました。

### 学んだ TDD テクニック

| テクニック | 説明 | 使用場面 |
|-----------|------|---------|
| テストファースト | テスト対象のコードを書く前にテストを書く | 常に |
| アサートファースト | アサーションを最初に書く | テスト作成時 |
| 仮実装 | ベタ書きの値を返す | 最初の実装 |
| 三角測量 | 2 つ以上の例から一般化を導く | 実装の方向性が不明確なとき |
| 明白な実装 | ロジックが明確な場合は直接実装 | ロジックが自明なとき |
| 学習用テスト | 外部ソフトウェアの使い方を学ぶためのテスト | 新しい API を使うとき |

### Red-Green-Refactor

```
Red（テスト失敗）→ Green（テスト成功）→ Refactor（リファクタリング）
```

このサイクルを小さく素早く繰り返すことで、品質を維持しながら着実に機能を追加できます。

次の第 2 部では、開発環境の整備（バージョン管理、パッケージ管理、CI/CD）について解説します。
