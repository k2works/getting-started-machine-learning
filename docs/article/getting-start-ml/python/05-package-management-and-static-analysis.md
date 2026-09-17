---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "uv と uv.lock・.python-version による環境の固定、Ruff による検査と整形、mypy による型チェック、pytest-cov によるカバレッジ計測を学ぶ。"
tags: [article,getting-start-ml,python]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T02:47:20Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、実験を再現するには乱数のシードに加えて、ライブラリと Python のバージョンを固定する必要があると述べました。この章では、それを担う **パッケージ管理** と、コードを実行せずに問題を見つける **静的解析** を扱います。

| 道具 | 役割 | 本章の節 |
|------|------|---------|
| [uv](https://docs.astral.sh/uv/) | 依存ライブラリと Python のバージョンを管理する | 5.2、5.3 |
| [Ruff](https://docs.astral.sh/ruff/) | リンターとフォーマッター | 5.4 |
| [mypy](https://mypy-lang.org/) | 型チェック | 5.5 |
| [pytest-cov](https://pytest-cov.readthedocs.io/) | テストのカバレッジ計測 | 5.6 |

本章のバージョンは、執筆時点の `uv.lock` に記録されたものです（uv 0.7.14、Ruff 0.16.8、mypy 2.3.1）。

## 5.2 uv によるパッケージ管理

### pyproject.toml

Python のプロジェクトの設定は `apps/python/pyproject.toml` にまとめています。第 2 章までに機械学習のライブラリを追加した時点の内容は次のとおりです。

```toml
[project]
name = "getting-started-ml"
version = "0.1.0"
description = "機械学習から始めるプログラミング入門（Python 版）"
requires-python = ">=3.12"
dependencies = [
    "fastapi>=0.141.1",
    "joblib>=1.6.0",
    "numpy>=2.5.3",
    "pandas>=3.0.5",
    "scikit-learn>=1.9.1",
    "uvicorn>=0.53.0",
]

# 手元・CI・Nix 環境のどこでも同じ Python（.python-version）を使うため、uv が管理する Python に限定する
[tool.uv]
python-preference = "only-managed"

[tool.pytest.ini_options]
testpaths = ["test"]
pythonpath = ["."]

[tool.coverage.run]
source = ["lib"]

[tool.coverage.report]
exclude_lines = [
    "pragma: no cover",
    "if __name__ == .__main__.",
    "if TYPE_CHECKING:",
]

[tool.mypy]
python_version = "3.12"
warn_return_any = true
warn_unused_configs = true
disallow_untyped_defs = true

# 型情報（py.typed・スタブ）を持たないライブラリ
[[tool.mypy.overrides]]
module = ["sklearn.*", "joblib.*", "seaborn.*", "nbformat.*"]
ignore_missing_imports = true

[dependency-groups]
dev = [
    "pytest>=8.0",
    "pytest-cov>=6.0",
    "ruff>=0.8",
    "mypy>=1.13",
    "tox>=4.0",
    "pandas-stubs>=3.0.5.260914",
    "matplotlib>=3.11.2",
    "seaborn>=0.13.2",
    "jupyterlab>=4.6.3",
    "nbstripout>=0.9.1",
    "nbconvert>=7.17.1",
    "ipykernel>=7.3.0",
    "httpx>=0.28.1",
]
```

### 本番依存と開発依存

依存ライブラリは 2 か所に分けて書いています。

| 置き場所 | 中身 | 判断の基準 |
|---------|------|----------|
| `[project]` の `dependencies` | pandas・NumPy・scikit-learn・joblib・FastAPI・uvicorn | 学習・予測・API として **動かすときに必要** なもの |
| `[dependency-groups]` の `dev` | pytest・Ruff・mypy・tox・可視化・JupyterLab など | **開発するときだけ必要** なもの |

可視化ライブラリ（matplotlib・seaborn）と JupyterLab は、データを探索する Notebook でしか使わないので開発依存にしています。第 15 章でモデルを API として動かすときには不要だからです。どのライブラリをなぜ選んだかは [ADR 001](../../../adr/001-python-ml-libraries.md) に記録しています。

### ライブラリを追加する

ライブラリは `uv add` で追加します。`pyproject.toml` への追記、依存関係の解決、`uv.lock` の更新、仮想環境へのインストールがまとめて行われます。本リポジトリでは次のコマンドで追加しました。

```bash
uv add pandas numpy scikit-learn joblib fastapi
uv add --dev pandas-stubs matplotlib seaborn jupyterlab nbstripout nbconvert ipykernel httpx
uv add uvicorn
```

直接の依存関係は `uv tree` で確認できます。

```bash
uv tree --depth 1 --frozen
```

```text
getting-started-ml v0.1.0
├── fastapi v0.141.1
├── joblib v1.6.0
├── numpy v2.5.3
├── pandas v3.0.5
├── scikit-learn v1.9.1
├── uvicorn v0.53.0
├── httpx v0.28.1 (group: dev)
├── ipykernel v7.3.0 (group: dev)
├── jupyterlab v4.6.3 (group: dev)
├── matplotlib v3.11.2 (group: dev)
├── mypy v2.3.1 (group: dev)
├── nbconvert v7.17.1 (group: dev)
├── nbstripout v0.9.1 (group: dev)
├── pandas-stubs v3.0.5.260914 (group: dev)
├── pytest v9.1.1 (group: dev)
├── pytest-cov v7.1.0 (group: dev)
├── ruff v0.16.8 (group: dev)
├── seaborn v0.13.2 (group: dev)
└── tox v4.61.4 (group: dev)
```

### uv.lock で環境を再現する

`pyproject.toml` の `pandas>=3.0.5` は「3.0.5 以上」という **範囲** です。範囲だけでは、インストールした日によって入るバージョンが変わり、同じコードでも結果や動作が変わりえます。

`uv.lock` には、依存関係の依存関係まで含めて、実際に解決された **正確なバージョン** が記録されます。`uv.lock` をコミットしておけば、読者も CI も次のコマンドで同じ環境を作れます。

```bash
cd apps/python
uv sync
```

機械学習では、ライブラリのバージョンアップでアルゴリズムの既定値や乱数の使い方が変わり、同じシードでも結果が変わることがあります。`uv.lock` は、第 4 章で固定したシードと組み合わせて初めて再現性を保証します。

## 5.3 Python のバージョンを固定する

Python 自体のバージョンも固定します。`apps/python/.python-version` に使うバージョンを書きます。

```text
3.12
```

さらに `pyproject.toml` の `[tool.uv]` に `python-preference = "only-managed"` を指定し、OS や Nix が用意した Python ではなく、uv が自分でダウンロードして管理する Python だけを使うようにしています。

```bash
uv run python --version
```

```text
Python 3.12.11
```

手元の Windows には別のバージョンの Python もインストールされていますが、プロジェクトでは uv が管理する 3.12 が使われます。この設定を入れた経緯（CI で起きた問題）は第 6 章で説明します。

## 5.4 静的コード解析 — Ruff

### Ruff の設定

Ruff はリンターとフォーマッターを 1 つにまとめたツールです。設定は `apps/python/.ruff.toml` に書いています。

```toml
line-length = 88
target-version = "py312"

[lint]
select = [
    "E",
    "W",
    "F",
    "I",
    "B",
    "C4",
    "UP",
    "C90",
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

| ルール | 内容 |
|--------|------|
| `E`・`W` | pycodestyle（コードスタイル） |
| `F` | Pyflakes（未使用の import や変数など） |
| `I` | isort（import の並び順） |
| `B` | flake8-bugbear（バグになりやすい書き方） |
| `C4` | flake8-comprehensions（内包表記の改善） |
| `UP` | pyupgrade（新しい Python の書き方への更新） |
| `C90` | mccabe（循環的複雑度） |

テストは日本語の関数名が長くなりやすいので、`test/` 以下では行の長さ（`E501`）を検査しません。`max-complexity = 7` は、1 つの関数の分岐が多くなりすぎたら分割を促す設定です。機械学習の前処理は `if` が増えやすいので、第 8 章の前処理パイプラインではこの制約が小さな関数に分ける動機になります。

### リンターの実行

問題を含むコードに Ruff を実行すると、場所と理由、修正方法を表示します。次のコードを `apps/python/.demo_ch05/scores.py` として保存して試します（試したあとは削除してください）。

```python
import os
import sys


def average(values):
    return sum(values) / len(values)


def label(score: float) -> str:
    if score > 0.5:
        return 1
    return "low"
```

```bash
uv run ruff check .demo_ch05/scores.py
```

```text
F401 [*] `os` imported but unused
 --> .demo_ch05\scores.py:1:8
  |
1 | import os
  |        ^^
2 | import sys
  |
help: Remove unused import: `os`
  |
  - import os
1 | import sys
  |

F401 [*] `sys` imported but unused
 --> .demo_ch05\scores.py:2:8
  |
1 | import os
2 | import sys
  |        ^^^
help: Remove unused import: `sys`
  |
1 | import os
  - import sys
2 |
  |

Found 2 errors.
[*] 2 fixable with the `--fix` option.
```

`[*]` が付いた問題は自動で直せます。

```bash
uv run ruff check --fix .demo_ch05/scores.py
```

プロジェクト全体を検査するときは、パスの代わりに `.` を指定します。

Ruff は型の誤り（`label` が `str` ではなく `1` を返していること）は検出しません。それは 5.5 節の mypy の役割です。

### フォーマッターの実行

```bash
# 整形が必要なファイルがあれば失敗する（CI 向け）
uv run ruff format --check .

# 整形する
uv run ruff format .
```

第 1 章のコード（`lib/chapter01` と `test/chapter01`）に `ruff check` と `ruff format --check` を実行した結果です。

```text
All checks passed!
5 files already formatted
```

### 日本語と行の長さ

Ruff は行の長さを、日本語などの全角文字を 2 文字分として数えます。次の行は文字数では 88 を超えていませんが、表示幅では超えています。

```python
    # 列ごとの欠損値の数を「列名=件数」の形式で、カンマ区切りの 1 行の文字列にまとめて返す
```

```text
E501 Line too long (90 > 88)
```

日本語のコメントや docstring が長くなったら、表示幅を基準に折り返してください。

### Notebook も検査する

Ruff は `.ipynb` ファイルのセルも検査します。第 2 章の Notebook を作った直後に `ruff check` を実行したところ、次の問題が見つかりました。

```text
I001 [*] Import block is un-sorted or un-formatted
  --> notebooks\chapter02_iris_exploration.ipynb:cell 2:5:1
F401 [*] `matplotlib.pyplot` imported but unused
 --> notebooks\chapter02_iris_exploration.ipynb:cell 2:5:29
```

Notebook は試行錯誤の場所なので未使用の import が残りがちです。Notebook も本番コードと同じ基準で検査しておくと、探索したコードをテストと本番コードに移すときの手直しが減ります。

## 5.5 型チェック — mypy

### mypy の設定

`pyproject.toml` の `[tool.mypy]` で、すべての関数に型注釈を求める（`disallow_untyped_defs = true`）設定にしています。

5.4 節の `scores.py` に mypy を実行すると、Ruff が見逃した型の誤りが見つかります。

```bash
uv run mypy .demo_ch05/scores.py
```

```text
.demo_ch05\scores.py:5: error: Function is missing a type annotation  [no-untyped-def]
.demo_ch05\scores.py:11: error: Incompatible return value type (got "int", expected "str")  [return-value]
Found 2 errors in 1 file (checked 1 source file)
```

プロジェクトでは、`lib`・`test`・`tools` をまとめて検査します。

```bash
uv run mypy lib test tools
```

### pandas と型

pandas の型は `pandas-stubs` が提供します。pandas のメソッドの戻り値の型は、見た目より緩いことがあります。第 2 章で欠損値の数を表示する関数を書いたとき、`count_missing(df).to_dict()` を `dict[str, int]` を受け取る関数に渡して、mypy に指摘されました。同じ問題を小さく再現したのが次のコードです。

```python
def format_counts(counts: dict[str, int]) -> str:
    return ", ".join(f"{column}={count}" for column, count in counts.items())


def summary(df: pd.DataFrame) -> str:
    return format_counts(df.isna().sum().to_dict())
```

```text
error: Argument 1 to "format_counts" has incompatible type "dict[Hashable, Any]"; expected "dict[str, int]"  [arg-type]
```

`Series.to_dict()` のキーは列名とは限らない（任意のハッシュ可能な値）ため、型は `dict[Hashable, Any]` になります。実行すると動くコードですが、型の上では `str` をキーとする辞書だと保証できません。第 2 章では、辞書に変換せず `Series` のまま受け取るように直しました。

```python
def format_counts(counts: pd.Series) -> str:
    return ", ".join(f"{column}={count}" for column, count in counts.items())
```

### 型情報を持たないライブラリ

scikit-learn・joblib・seaborn は型情報（`py.typed` やスタブ）を配布していないため、そのままでは mypy が import をエラーにします。`[[tool.mypy.overrides]]` で、対象のモジュールに限って `ignore_missing_imports = true` にしています。

```toml
# 型情報（py.typed・スタブ）を持たないライブラリ
[[tool.mypy.overrides]]
module = ["sklearn.*", "joblib.*", "seaborn.*", "nbformat.*"]
ignore_missing_imports = true
```

プロジェクト全体で `ignore_missing_imports` を有効にすると、import の書き間違いまで見逃すようになります。対象を限定するのはそのためです。なお、一部のディレクトリだけを検査すると、そのディレクトリで使われていないモジュールについて `note: unused section(s)` と表示されますが、エラーではありません。

## 5.6 コードカバレッジ — pytest-cov

テストがプロダクションコードのどこを実行したかを pytest-cov で計測します。計測対象は `pyproject.toml` の `[tool.coverage.run]` で `lib` に限定し、`if __name__ == "__main__":` のように計測する意味のない行は `[tool.coverage.report]` で除外しています。

```bash
uv run pytest --cov=lib --cov-report=term-missing
```

第 1 章の範囲だけを計測した結果です。

```bash
uv run pytest --cov=lib.chapter01 --cov=lib.dataset --cov-report=term-missing test/chapter01 test/test_dataset.py
```

```text
Name                               Stmts   Miss  Cover   Missing
----------------------------------------------------------------
lib\chapter01\__init__.py              0      0   100%
lib\chapter01\__main__.py              8      0   100%
lib\chapter01\kinoko_takenoko.py      28      0   100%
lib\dataset.py                         8      0   100%
----------------------------------------------------------------
TOTAL                                 44      0   100%
============================= 12 passed in 0.11s ==============================
```

`Missing` 列には、テストで実行されなかった行番号が表示されます。

注意点が 1 つあります。実データを使うテスト（`requires_data`）は、データが無い環境ではスキップされるので、その環境ではカバレッジが下がります。第 1 章の `__main__.py` は実データのテストでしか実行されないため、データが無い環境（`ML_DATA_DIR` に存在しないディレクトリを指定）で同じコマンドを実行すると、次のようになります。

```text
Name                               Stmts   Miss  Cover   Missing
----------------------------------------------------------------
lib\chapter01\__init__.py              0      0   100%
lib\chapter01\__main__.py              8      5    38%   11-15
lib\chapter01\kinoko_takenoko.py      28      0   100%
lib\dataset.py                         8      0   100%
----------------------------------------------------------------
TOTAL                                 44      5    89%
======================== 9 passed, 3 skipped in 0.12s =========================
```

カバレッジの数値を比べるときは、データのある環境で計測したものかを確認してください。

## 5.7 まとめ

この章では、再現できる環境と、実行せずに問題を見つける仕組みを整えました。

1. **uv** — 本番依存と開発依存を分けて `pyproject.toml` に書き、`uv.lock` で正確なバージョンを固定する
2. **Python のバージョン** — `.python-version` と `python-preference = "only-managed"` で、どの環境でも同じ Python を使う
3. **Ruff** — リンターとフォーマッターで、スタイル・未使用の import・複雑度を検査する。Notebook も対象にする
4. **mypy** — すべての関数に型注釈を求め、pandas の緩い型にも注意する。型情報の無いライブラリは対象を限定して除外する
5. **pytest-cov** — カバレッジを計測する。実データのテストがスキップされる環境では数値が下がることに注意する

次の章では、これらのコマンドを tox でまとめ、GitHub Actions で自動実行します。
