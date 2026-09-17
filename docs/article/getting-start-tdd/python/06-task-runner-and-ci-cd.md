---
type: Article
title: "第 6 章: タスクランナーと CI/CD"
description: "前章までに、Ruff（リンター + フォーマッター）、mypy（型チェック）、pytest-cov（カバレッジ）を個別に導入しました。"
tags: [article, getting-start-tdd, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 6 章: タスクランナーと CI/CD

## 6.1 はじめに

前章までに、Ruff（リンター + フォーマッター）、mypy（型チェック）、pytest-cov（カバレッジ）を個別に導入しました。しかし、これらを毎回手動で実行するのは手間がかかります。この章では **タスクランナー** を導入して品質チェックを自動化し、さらに CI/CD パイプラインを構築します。

## 6.2 タスクランナー — tox

[tox](https://tox.wiki/) は、Python プロジェクト向けのタスクランナーです。複数の環境でテストを実行したり、品質チェックを束ねて一括実行したりできます。

### tox.ini の設定

```ini
# tox.ini
[tox]
envlist = test,lint,type
skipsdist = true

[testenv:test]
deps =
    pytest
    pytest-cov
commands =
    pytest --cov=lib --cov-report=term-missing --verbose

[testenv:lint]
deps = ruff
commands =
    ruff check .
    ruff format --check .

[testenv:format]
deps = ruff
commands =
    ruff format .

[testenv:type]
deps =
    mypy
    pytest
commands =
    mypy lib test
```

### タスクの実行

```bash
# 全タスクを一括実行
$ uv run tox

# 個別タスクの実行
$ uv run tox -e test      # テストのみ
$ uv run tox -e lint      # リンティングのみ
$ uv run tox -e type      # 型チェックのみ
$ uv run tox -e format    # フォーマット適用
```

### 全品質チェックの実行例

```bash
$ uv run tox
```

```
test: commands succeeded
lint: commands succeeded
type: commands succeeded
congratulations :)
```

すべてのチェックが成功すれば、コードの品質が保証された状態です。

## 6.3 タスクの自動化

### ファイル監視による自動テスト

pytest には `--watch` オプションはありませんが、`pytest-watch` パッケージを使うか、IDE のファイル保存時自動テスト機能を利用できます。

```bash
# pytest-watch を使う場合
$ uv add --dev pytest-watch
$ uv run ptw
```

ファイルを変更するたびにテストが自動実行され、TDD サイクルが加速します。

## 6.4 GitHub Actions による CI/CD

プッシュやプルリクエスト時に自動で品質チェックを実行する CI/CD パイプラインを構築します。

### ワークフロー設定

```yaml
# .github/workflows/python-ci.yml
name: Python CI

on:
  push:
    branches: [main, develop]
    paths:
      - "apps/python/**"
      - ".github/workflows/python-ci.yml"
  pull_request:
    branches: [main]
    paths:
      - "apps/python/**"

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: apps/python

    steps:
      - uses: actions/checkout@v4

      - name: Install Nix
        uses: cachix/install-nix-action@v30

      - name: Cache Nix store
        uses: actions/cache@v4
        with:
          path: /nix/store
          key: nix-${{ runner.os }}-${{ hashFiles('flake.lock') }}

      - name: Run tests
        run: nix develop .#python --command bash -c "cd apps/python && uv sync && uv run tox -e test"
        working-directory: .

      - name: Run lint
        run: nix develop .#python --command bash -c "cd apps/python && uv run tox -e lint"
        working-directory: .

      - name: Run type check
        run: nix develop .#python --command bash -c "cd apps/python && uv run tox -e type"
        working-directory: .
```

### CI パイプラインの流れ

```
git push → GitHub Actions → Nix 環境構築 → テスト → リンティング → 型チェック
```

## 6.5 三種の神器と CI/CD

第 2 部で整備した開発環境の全体像です。

| 神器 | ツール | 目的 |
|------|--------|------|
| バージョン管理 | Git + Conventional Commits | 変更履歴の追跡 |
| テスティング | pytest + pytest-cov | 品質の保証 |
| 自動化 | tox + GitHub Actions | 反復作業の自動化 |

### 利用可能なコマンド一覧

| コマンド | 説明 |
|---------|------|
| `uv run pytest -v` | テスト実行 |
| `uv run ruff check .` | リンティング |
| `uv run ruff format .` | フォーマット適用 |
| `uv run mypy lib test` | 型チェック |
| `uv run pytest --cov=lib` | カバレッジ計測 |
| `uv run tox` | 全品質チェック一括実行 |
| `uv run tox -e test` | テストのみ |
| `uv run tox -e lint` | リンティングのみ |
| `uv run tox -e type` | 型チェックのみ |

## 6.6 まとめ

| 章 | テーマ | 導入したツール |
|----|--------|--------------|
| 4 | バージョン管理 | Conventional Commits |
| 5 | パッケージ管理と静的解析 | uv、Ruff、mypy、pytest-cov |
| 6 | タスクランナーと CI/CD | tox、GitHub Actions |

### TDD 開発ワークフロー

1. テストを書く（Red）
2. 最小限のコードを実装（Green）
3. `uv run pytest -v` でテストを確認
4. リファクタリング
5. `uv run tox` で全品質チェック
6. `git commit` で変更を記録

次の第 3 部では、オブジェクト指向設計（カプセル化、ポリモーフィズム、デザインパターン）を FizzBuzz に適用していきます。
