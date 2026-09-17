---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "この章では、Python プロジェクトにパッケージ管理ツールと静的解析ツールを導入し、コードの品質を自動的にチェックする仕組みを整えます。"
tags: [article, getting-start-tdd, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

この章では、Python プロジェクトにパッケージ管理ツールと静的解析ツールを導入し、コードの品質を自動的にチェックする仕組みを整えます。

## 5.2 uv によるパッケージ管理

[uv](https://docs.astral.sh/uv/) は、高速な Python パッケージマネージャです。pip や poetry を置き換え、依存関係の管理と仮想環境の操作を統合的に行えます。

### pyproject.toml の設定

開発に必要なツールを追加します。

```toml
# pyproject.toml
[project]
name = "fizzbuzz"
version = "0.1.0"
description = "FizzBuzz TDD project for Python"
requires-python = ">=3.11"

[tool.pytest.ini_options]
testpaths = ["test"]
pythonpath = ["."]
addopts = "--cov=lib --cov-report=term-missing"

[tool.coverage.run]
source = ["lib"]

[tool.coverage.report]
exclude_lines = [
    "pragma: no cover",
    "def __repr__",
    "raise NotImplementedError",
    "if __name__ == .__main__.",
]

[tool.mypy]
python_version = "3.11"
warn_return_any = true
warn_unused_configs = true
disallow_untyped_defs = true

[dependency-groups]
dev = [
    "pytest>=8.0",
    "pytest-cov>=6.0",
    "ruff>=0.8",
    "mypy>=1.13",
]
```

```bash
$ uv sync
```

## 5.3 静的コード解析 — Ruff

[Ruff](https://docs.astral.sh/ruff/) は、Python 用の高速なリンター兼フォーマッターです。flake8、pylint、black を 1 つのツールに統合しています。

### Ruff の設定

```toml
# .ruff.toml
line-length = 88
target-version = "py311"

[lint]
select = [
    "E",   # pycodestyle errors
    "W",   # pycodestyle warnings
    "F",   # pyflakes
    "I",   # isort
    "B",   # flake8-bugbear
    "C4",  # flake8-comprehensions
    "UP",  # pyupgrade
]
ignore = []

[format]
quote-style = "double"
indent-style = "space"

[lint.per-file-ignores]
"test/**/*.py" = ["E501"]

[lint.mccabe]
max-complexity = 7
```

### リンターの実行

```bash
# コードチェック
$ uv run ruff check .

# 自動修正
$ uv run ruff check . --fix
```

### フォーマッターの実行

```bash
# フォーマットチェック
$ uv run ruff format --check .

# 自動フォーマット
$ uv run ruff format .
```

## 5.4 コード複雑度のチェック

静的コード解析では、コーディング規約やバグパターンだけでなく、**コードの複雑度** もチェックできます。Ruff には McCabe 複雑度チェッカーが組み込まれており、メソッドの複雑度を制限できます。

### 循環的複雑度（Cyclomatic Complexity）

> 循環的複雑度（サイクロマティック複雑度）とは、ソフトウェア測定法の一つであり、コードがどれぐらい複雑であるかをメソッド単位で数値にして表す指標。

本プロジェクトでは、循環的複雑度を **7 以下** に制限しています。

| 複雑度の範囲 | 意味 |
|-------------|------|
| 1〜10 | 低複雑度：管理しやすく、問題なし |
| 11〜20 | 中程度の複雑度：リファクタリングを検討 |
| 21〜50 | 高複雑度：リファクタリングが強く推奨される |
| 51 以上 | 非常に高い複雑度：コードを分割する必要がある |

### 認知的複雑度（Cognitive Complexity）

> 認知的複雑度（Cognitive Complexity）は、プログラムを読む人の認知負荷を測るための指標。コードの構造が「どれだけ頭を使う必要があるか」を定量的に評価する。循環的複雑度とは異なり、制御構造のネストやコードの流れの読みやすさに重点を置いている。

| 複雑度の範囲 | 意味 |
|-------------|------|
| 0〜4 | 理解が非常に容易：リファクタリング不要 |
| 5〜14 | 中程度の難易度：改善が必要な場合もある |
| 15 以上 | 理解が困難：コードの簡素化を検討するべき |

### Ruff の McCabe 設定

`.ruff.toml` の `[lint.mccabe]` セクションで循環的複雑度の上限を設定します。

```toml
# .ruff.toml（抜粋）
[lint]
select = [
    "E",   # pycodestyle errors
    "W",   # pycodestyle warnings
    "F",   # pyflakes
    "I",   # isort
    "B",   # flake8-bugbear
    "C4",  # flake8-comprehensions
    "UP",  # pyupgrade
    "C90", # mccabe（循環的複雑度）
]

[lint.mccabe]
max-complexity = 7
```

`select` に `"C90"` を追加すると、McCabe 複雑度チェックが有効になります。`max-complexity = 7` で、循環的複雑度が 7 を超える関数に対して警告が出ます。

### 複雑度チェックの実行

```bash
$ uv run ruff check .
```

### 複雑度チェックの効果

コード複雑度の制限により、以下の効果が得られます。

- **可読性向上** — 小さな関数は理解しやすい
- **保守性向上** — 変更の影響範囲が限定される
- **テスト容易性** — 個別機能のテストが簡単
- **自動品質管理** — 複雑なコードの混入を自動防止

現在の FizzBuzz の `generate` メソッドは循環的複雑度が 4 で、制限値 7 以内に収まっています。第 3 部でオブジェクト指向設計を進める際も、この制限を意識してコードを書いていきます。

## 5.5 マジックナンバーのリファクタリング

第 1 部で作成した FizzBuzz のコードには、`3`、`5`、`15` というマジックナンバーが含まれています。定数として抽出しましょう。

### リファクタリング前

```python
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

### リファクタリング後

```python
class FizzBuzz:
    MAX_NUMBER: int = 100
    _FIZZ_NUMBER: int = 3
    _BUZZ_NUMBER: int = 5
    _FIZZ_BUZZ_NUMBER: int = 15

    def generate(self, number: int) -> str:
        """数を FizzBuzz ルールに従って文字列に変換する。"""
        if number % self._FIZZ_BUZZ_NUMBER == 0:
            return "FizzBuzz"
        if number % self._FIZZ_NUMBER == 0:
            return "Fizz"
        if number % self._BUZZ_NUMBER == 0:
            return "Buzz"
        return str(number)

    def generate_list(self, count: int) -> list[str]:
        """1 から count までの FizzBuzz リストを生成する。"""
        return [self.generate(i) for i in range(1, count + 1)]

    def print_fizzbuzz(self, count: int) -> None:
        """FizzBuzz の結果をプリントする。"""
        result = self.generate_list(count)
        for item in result:
            print(item)
```

リファクタリング後もテストがすべて通ることを確認します。

```bash
$ uv run pytest -v
```

## 5.6 コードカバレッジ — pytest-cov

[pytest-cov](https://pytest-cov.readthedocs.io/) は、pytest と連携してコードカバレッジを計測するプラグインです。

```bash
$ uv run pytest --cov=lib --cov-report=term-missing
```

```
Name                  Stmts   Miss  Cover   Missing
-----------------------------------------------------
lib/__init__.py           0      0   100%
lib/fizzbuzz.py          14      0   100%
-----------------------------------------------------
TOTAL                    14      0   100%
```

## 5.7 型チェック — mypy

[mypy](https://mypy-lang.org/) は、Python の型ヒントに基づいて静的型チェックを行うツールです。

```bash
$ uv run mypy lib test
```

```
Success: no issues found
```

Python は動的型付け言語ですが、型ヒントと mypy を組み合わせることで、コンパイル言語に近い型安全性を実現できます。

## 5.8 まとめ

| ツール | 用途 | コマンド |
|--------|------|---------|
| uv | パッケージ管理 | `uv sync` |
| Ruff | リンター + フォーマッター | `uv run ruff check .` / `uv run ruff format .` |
| pytest-cov | コードカバレッジ | `uv run pytest --cov=lib` |
| mypy | 静的型チェック | `uv run mypy lib test` |

次の章では、タスクランナー（tox）と CI/CD パイプラインを構築します。
