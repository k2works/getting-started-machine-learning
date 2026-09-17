# 機械学習から始める Python 入門

Python は機械学習で最も広く使われている言語です。pandas・NumPy・scikit-learn といったライブラリがそろい、データの読み込みからモデルの学習・評価・API 化までを 1 つの言語で書けます。本シリーズでは、機械学習のアルゴリズムをまず TDD で自作し、次に scikit-learn に置き換えて結果を突き合わせながら、Python の書き方とエコシステムを学びます。

Python 版は本シリーズの参照実装であり、Kotlin 版とともに Jupyter Lab によるデータの探索と可視化も扱います。

## 特徴

- **動的型付けと型ヒント**: 型を書かずに試せる手軽さと、型ヒントと mypy による静的チェックを両立できる
- **データクラス**: `@dataclass` でデータを表す型を簡潔に定義できる
- **豊富な機械学習ライブラリ**: pandas・NumPy・scikit-learn などが成熟している
- **対話的な探索**: Jupyter Lab でデータを探索し、分かったことをテストと本番コードに移せる

## 開発環境

| ツール | 用途 |
|--------|------|
| [uv](https://docs.astral.sh/uv/) | パッケージマネージャー |
| [pytest](https://docs.pytest.org/) | テスティングフレームワーク |
| [pytest-cov](https://pytest-cov.readthedocs.io/) | コードカバレッジ |
| [Ruff](https://docs.astral.sh/ruff/) | リンター + フォーマッター |
| [mypy](https://mypy-lang.org/) | 静的型チェック |
| [tox](https://tox.wiki/) | タスクランナー |

## ライブラリ

| ライブラリ | 用途 | 初出 |
|-----------|------|------|
| [pandas](https://pandas.pydata.org/) | データフレーム | 第 2 章 |
| [NumPy](https://numpy.org/) | 行列演算 | 第 2 章 |
| [scikit-learn](https://scikit-learn.org/) | 機械学習（自作との突き合わせ） | 第 2 章 |
| [JupyterLab](https://jupyter.org/)・[matplotlib](https://matplotlib.org/)・[seaborn](https://seaborn.pydata.org/) | データの探索と可視化 | 第 2 章 |
| [nbstripout](https://github.com/kynan/nbstripout) | Notebook の出力セルの削除 | 第 6 章 |
| [joblib](https://joblib.readthedocs.io/) | モデルの保存と読み込み | 第 8 章 |
| [FastAPI](https://fastapi.tiangolo.com/)・[uvicorn](https://www.uvicorn.org/) | 予測 API | 第 15 章 |

選定の理由は [ADR 001](../../../adr/001-python-ml-libraries.md) を参照してください。

## サンプルコード

サンプルコードは `apps/python/` にあります。学習データの配置方法は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。

```bash
cd apps/python
uv sync
uv run pytest
```

各章の実装は `lib/chapterNN/`、テストは `test/chapterNN/`、探索用の Notebook は `notebooks/` にあります。`uv run python -m lib.chapterNN` で章ごとの結果を表示できます。

## 章構成

### 第 1 部: 機械学習と TDD の基本サイクル

| 章 | テーマ |
|----|--------|
| [第 1 章](01-machine-learning-and-first-test.md) | 機械学習とはじめてのテスト |
| [第 2 章](02-data-preprocessing-and-triangulation.md) | データの前処理と三角測量 |
| [第 3 章](03-decision-tree-and-obvious-implementation.md) | 決定木による分類と明白な実装 |

### 第 2 部: 開発環境と自動化

| 章 | テーマ |
|----|--------|
| [第 4 章](04-version-control-and-data-management.md) | バージョン管理とデータ管理 |
| [第 5 章](05-package-management-and-static-analysis.md) | パッケージ管理と静的解析 |
| [第 6 章](06-task-runner-and-ci-cd.md) | タスクランナーと CI/CD |

### 第 3 部: 回帰と実践的な前処理

| 章 | テーマ |
|----|--------|
| [第 7 章](07-linear-regression.md) | 線形回帰による数値予測 |
| [第 8 章](08-classification-and-preprocessing-pipeline.md) | 実践的な分類と前処理パイプライン |
| [第 9 章](09-feature-engineering.md) | 特徴量エンジニアリング |

### 第 4 部: モデルの改善と評価

| 章 | テーマ |
|----|--------|
| [第 10 章](10-logistic-regression-and-ensemble.md) | ロジスティック回帰とアンサンブル学習 |
| [第 11 章](11-evaluation-metrics-and-cross-validation.md) | 評価指標と交差検証 |
| [第 12 章](12-regularization-and-model-selection.md) | 正則化とモデル選択 |

### 第 5 部: 教師なし学習と実運用

| 章 | テーマ |
|----|--------|
| [第 13 章](13-principal-component-analysis.md) | 主成分分析による次元削減 |
| [第 14 章](14-k-means-clustering.md) | K-means によるクラスタリング |
| [第 15 章](15-machine-learning-api-and-module-design.md) | 機械学習 API とモジュール設計 |

### 付録

| 付録 | テーマ |
|------|--------|
| [付録 A](appendix-a-bank-exercise.md) | 総合演習（Bank） |
