---
type: Article
title: "Part I: 関数型プログラミングの基礎"
description: "本章では、関数型プログラミング（FP）の基本概念を Python で学びます。"
tags: [article, grokkingfp, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-27T04:43:33Z }
---

# Part I: 関数型プログラミングの基礎

本章では、関数型プログラミング（FP）の基本概念を Python で学びます。命令型プログラミングとの違いを理解し、純粋関数の利点を実感することが目標です。

---

## 第1章: 関数型プログラミング入門

### 1.1 命令型 vs 関数型

プログラミングには大きく分けて2つのパラダイムがあります。

**命令型プログラミング**は「どうやるか（HOW）」を記述します。

```python
# 命令型でワードスコアを計算
def calculate_score_imperative(word: str) -> int:
    score = 0
    for _ in word:
        score += 1
    return score
```

**関数型プログラミング**は「何をするか（WHAT）」を記述します。

```python
# 関数型でワードスコアを計算
def word_score(word: str) -> int:
    return len(word)
```

### 1.2 Python の基本構文

Python での関数定義の基本形を見てみましょう。

**ソースファイル**: `app/python/src/grokking_fp/ch01_intro.py`

```python
def increment(x: int) -> int:
    """値を1増加させる。"""
    return x + 1

def get_first_character(s: str) -> str:
    """文字列の最初の文字を取得する。"""
    return s[0]

def word_score(word: str) -> int:
    """単語のスコア（文字数）を返す。"""
    return len(word)
```

### 1.3 関数の構造

```
def increment(x: int) -> int:
    return x + 1

│    │         │       │   └── 関数本体（式）
│    │         │       └────── 戻り値の型
│    │         └────────────── パラメータと型
│    └──────────────────────── 関数名
└───────────────────────────── キーワード
```

Python では型ヒント（Type Hints）を使って、パラメータと戻り値の型を明示できます。

### 1.4 学習ポイント

| 概念 | 命令型 | 関数型 |
|------|--------|--------|
| 焦点 | 手順（How） | 結果（What） |
| 状態 | 変更する | 変換する |
| ループ | for/while | map/filter/reduce |
| データ | ミュータブル | イミュータブル |

---

## 第2章: 純粋関数とテスト

### 2.1 純粋関数とは

純粋関数（Pure Function）は以下の特徴を持つ関数です:

1. **同じ入力には常に同じ出力を返す**
2. **副作用がない**（外部状態を変更しない）

### 2.2 純粋関数の例

**ソースファイル**: `app/python/src/grokking_fp/ch02_pure_functions.py`

```python
# 純粋関数の例
def add(a: int, b: int) -> int:
    """2つの数値を加算する純粋関数。"""
    return a + b

def string_length(s: str) -> int:
    """文字列の長さを返す純粋関数。"""
    return len(s)

def bonus_score(word: str) -> int:
    """文字 'c' を含む場合にボーナスを付与する。"""
    base = len(word)
    return base + 5 if "c" in word.lower() else base
```

**純粋ではない関数の例**:

```python
import random

# 不純な関数 - random.random() は毎回異なる値を返す
def random_part(x: float) -> float:
    return x * random.random()

# 不純な関数 - 外部状態を変更する
counter = 0
def increment_counter() -> int:
    global counter
    counter += 1
    return counter
```

### 2.3 ショッピングカートの例

状態を持つクラスの問題点と、純粋関数による解決を見てみましょう。

#### 問題のあるコード

```python
class ShoppingCartBad:
    def __init__(self):
        self._items: list[str] = []
        self._book_added = False

    def add_item(self, item: str) -> None:
        self._items.append(item)
        if item == "Book":
            self._book_added = True

    def get_discount_percentage(self) -> int:
        return 5 if self._book_added else 0

    def get_items(self) -> list[str]:
        return self._items  # 問題: 内部状態への参照を返している
```

このコードの問題:


1. `get_items()` が内部リストへの参照を返すため、外部から変更可能
2. `_book_added` フラグと実際のアイテムの整合性が崩れる可能性がある

#### 純粋関数による解決

```python
def get_discount_percentage(items: list[str]) -> int:
    """アイテムリストから割引率を計算する純粋関数。"""
    return 5 if "Book" in items else 0

# 使用例
items = ["Apple", "Book"]
discount = get_discount_percentage(items)  # 5
```

この純粋関数版の利点:


- 状態を持たない
- 同じ入力には常に同じ出力
- テストが容易

### 2.4 チップ計算の例

```python
def get_tip_percentage(names: list[str]) -> int:
    """グループの人数からチップ率を計算する純粋関数。"""
    size = len(names)
    if size > 5:
        return 20
    elif size > 0:
        return 10
    else:
        return 0
```

この関数は:


- 6人以上のグループ → 20% のチップ
- 1-5人のグループ → 10% のチップ
- 0人（空リスト） → 0% のチップ

### 2.5 純粋関数のテスト

純粋関数は非常にテストしやすいです。

```python
def increment(x: int) -> int:
    return x + 1

# テストケース
assert increment(6) == 7
assert increment(0) == 1
assert increment(-6) == -5
```

pytest を使ったテスト例:

```python
class TestIncrement:
    def test_positive_number(self) -> None:
        assert increment(5) == 6

    def test_zero(self) -> None:
        assert increment(0) == 1

    def test_negative_number(self) -> None:
        assert increment(-1) == 0
```

### 2.6 文字 'a' を除外するワードスコア

より複雑な例を見てみましょう。

```python
def word_score_no_a(word: str) -> int:
    """文字 'a' を除外してワードスコアを計算する。"""
    return len(word.replace("a", "").replace("A", ""))

# テスト
assert word_score_no_a("Scala") == 3    # "Scl" → 3文字
assert word_score_no_a("function") == 8  # 'a' なし → 8文字
assert word_score_no_a("") == 0          # 空文字 → 0文字
```

### 2.7 参照透過性

純粋関数は**参照透過性（Referential Transparency）**を持ちます。

> 式をその評価結果で置き換えても、プログラムの意味が変わらないこと

```python
# 参照透過性の例
score1 = word_score("Scala")
score2 = word_score("Scala")
# score1 と score2 は常に同じ値（5）

# 以下の2つは同等
total1 = word_score("Scala") + word_score("Java")
total2 = 5 + 4  # word_score の結果で置き換え可能
```

### 2.8 高階関数の基本

Python は関数を値として扱えます（第一級関数）。

```python
from typing import Callable

def apply_twice(f: Callable[[int], int], x: int) -> int:
    """関数を2回適用する。"""
    return f(f(x))

# 使用例
result = apply_twice(lambda x: x + 1, 5)  # 7

def compose(
    f: Callable[[int], int],
    g: Callable[[int], int]
) -> Callable[[int], int]:
    """2つの関数を合成する。compose(f, g)(x) = f(g(x))"""
    return lambda x: f(g(x))

# 使用例
double = lambda x: x * 2
add_one = lambda x: x + 1
double_then_add = compose(add_one, double)
print(double_then_add(5))  # 11: (5 * 2) + 1
```

### 2.9 型ヒントとイミュータビリティ

Python では `Final` を使って定数を宣言できます。

```python
from typing import Final

MAX_SCORE: Final[int] = 100
MIN_SCORE: Final[int] = 0

def clamp(value: int, min_val: int, max_val: int) -> int:
    """値を指定された範囲内に収める。"""
    return max(min_val, min(value, max_val))

def get_bounded_score(score: int) -> int:
    """スコアを有効な範囲内に収める。"""
    return clamp(score, MIN_SCORE, MAX_SCORE)
```

---

## まとめ

### Part I で学んだこと

1. **関数型プログラミング**は「何をするか」を宣言的に記述する
2. **純粋関数**は同じ入力に対して常に同じ出力を返す
3. **副作用**を避けることでコードの予測可能性が向上する
4. **純粋関数**はテストが非常に簡単
5. **参照透過性**により、コードの理解と推論が容易になる
6. **型ヒント**を使うことで、コードの意図が明確になる

### Scala との対比

| 概念 | Scala | Python |
|------|-------|--------|
| 関数定義 | `def f(x: Int): Int = x + 1` | `def f(x: int) -> int: return x + 1` |
| 型宣言 | 必須（推論可） | オプション（型ヒント） |
| イミュータブル変数 | `val` | `Final`（慣習） |
| ラムダ | `x => x + 1` | `lambda x: x + 1` |
| 文字列補間 | `s"Hello, $name"` | `f"Hello, {name}"` |

### 次のステップ

Part II では、以下のトピックを学びます:

- イミュータブルなデータ操作（tuple、frozenset）
- イテレータと高階関数（map、filter、reduce）
- ジェネレータと itertools

---

## 演習問題

### 問題 1: 純粋関数の識別

以下の関数のうち、純粋関数はどれですか?

```python
# A
def double(x: int) -> int:
    return x * 2

# B
counter = 0
def increment_counter() -> int:
    global counter
    counter += 1
    return counter

# C
def greet(name: str) -> str:
    return f"Hello, {name}!"

# D
import time
def current_time() -> float:
    return time.time()
```

<details>
<summary>解答</summary>

**A と C は純粋関数**です。

- A: 同じ入力に対して常に同じ出力を返し、副作用がない
- B: 外部変数 `counter` を変更する副作用がある（不純）
- C: 同じ入力に対して常に同じ出力を返し、副作用がない
- D: 呼び出すたびに異なる値を返す（不純）

</details>

### 問題 2: 純粋関数への書き換え

以下の不純な関数を純粋関数に書き換えてください。

```python
class Counter:
    def __init__(self):
        self.value = 0

    def increment(self) -> int:
        self.value += 1
        return self.value
```

<details>
<summary>解答</summary>

```python
def increment(value: int) -> int:
    return value + 1

# 使用例
v1 = 0
v2 = increment(v1)  # 1
v3 = increment(v2)  # 2
```

状態を外部に持ち、関数は値を受け取って新しい値を返すだけにします。

</details>

### 問題 3: テストを書く

以下の関数に対する pytest テストケースを考えてください。

```python
def is_even(n: int) -> bool:
    return n % 2 == 0
```

<details>
<summary>解答</summary>

```python
class TestIsEven:
    def test_even_positive(self) -> None:
        assert is_even(0) is True
        assert is_even(2) is True
        assert is_even(4) is True

    def test_odd_positive(self) -> None:
        assert is_even(1) is False
        assert is_even(3) is False

    def test_negative_even(self) -> None:
        assert is_even(-2) is True

    def test_negative_odd(self) -> None:
        assert is_even(-3) is False
```

</details>

---

## 参考リンク

- [Python 型ヒント](https://docs.python.org/3/library/typing.html)
- [PEP 484 – Type Hints](https://peps.python.org/pep-0484/)
- [pytest ドキュメント](https://docs.pytest.org/)
