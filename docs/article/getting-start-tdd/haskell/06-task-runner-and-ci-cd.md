---
type: Article
title: "第 6 章: タスクランナーと CI/CD"
description: "TDD を継続するには、テスト・静的解析・複雑度チェックを毎回確実に実行できる仕組みが必要です。"
tags: [article, getting-start-tdd, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 6 章: タスクランナーと CI/CD

## 6.1 はじめに

TDD を継続するには、テスト・静的解析・複雑度チェックを毎回確実に実行できる仕組みが必要です。
この章では、Makefile によるタスク自動化、Nix 開発環境、GitHub Actions を使って開発タスクを自動化します。

## 6.2 Nix による開発環境

本プロジェクトでは Nix Flakes で開発環境を宣言的に管理しています。
`ops/nix/environments/haskell/shell.nix` に Haskell 環境の定義があります。

```nix
{ packages ? import <nixpkgs> { } }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inputsFrom = [ baseShell ];
  buildInputs = with packages; [
    ghc
    stack
    cabal-install
    haskell-language-server
    hlint
  ];

  shellHook = baseShell.shellHook + ''
    echo "Welcome to the Haskell development environment!"
    ghc --version
    cabal --version
    stack --version
  '';
}
```

環境の起動:

```bash
nix develop .#haskell
```

これにより、GHC、Stack、Cabal、HLS（Haskell Language Server）、HLint が揃った環境に入れます。
チーム全員が同じツールバージョンを使えるため、「自分のマシンでは動く」問題を防げます。

### Nix 環境に含まれるツール

| ツール | 用途 |
|--------|------|
| `ghc` | Haskell コンパイラ |
| `stack` | ビルドツール・パッケージマネージャ |
| `cabal-install` | Cabal ビルドツール |
| `haskell-language-server` | エディタ統合用 LSP サーバ |
| `hlint` | 静的解析ツール |

## 6.3 Makefile によるタスク管理

`apps/haskell/Makefile` には、日常的に使うタスクが定義されています。

```makefile
.PHONY: all build test lint complexity check clean

all: check

build:
	stack build

test:
	stack test

lint:
	hlint src/ test/ app/ -h .hlint.yaml

complexity:
	bash scripts/complexity.sh --threshold 10 src

check: lint complexity test

clean:
	stack clean
```

### 各タスクの説明

| タスク | コマンド | 説明 |
|--------|---------|------|
| `build` | `stack build` | プロジェクトのビルド |
| `test` | `stack test` | HSpec テストスイートの実行 |
| `lint` | `hlint src/ test/ app/` | HLint による静的解析 |
| `complexity` | `bash scripts/complexity.sh` | 循環複雑度チェック |
| `check` | `lint` + `complexity` + `test` | 品質ゲートの一括実行 |
| `clean` | `stack clean` | ビルド成果物の削除 |

この構成の狙いは、`make check` 1 つで品質ゲートをまとめて実行できる点です。

### タスクの実行

Nix 環境内で実行するのが前提です。

```bash
# Nix 環境に入ってから実行
nix develop .#haskell
cd apps/haskell
make check

# または Nix 環境外からワンライナーで実行
nix develop .#haskell --command bash -c "cd apps/haskell && make check"
```

`make check` の実行順序は次の通りです。

1. **lint** -- HLint がコードスタイルと潜在的問題をチェック
2. **complexity** -- カスタムスクリプトが循環複雑度を検証
3. **test** -- HSpec がテストスイートを実行

いずれかのステップが失敗すると、以降のステップは実行されません。

### Stack の監視実行

Stack には `--file-watch` オプションがあり、ファイル変更を監視して自動的にテストを再実行できます。

```bash
stack test --file-watch
```

TDD の Red → Green を高速に回すときに有効です。ファイルを保存するたびにテストが自動実行されるため、即座にフィードバックを得られます。

## 6.4 GitHub Actions による CI/CD

`.github/workflows/haskell-ci.yml` では、Nix を活用して CI を実行しています。

```yaml
name: Haskell CI

on:
  push:
    paths:
      - "apps/haskell/**"
      - ".github/workflows/haskell-ci.yml"
  pull_request:
    paths:
      - "apps/haskell/**"
      - ".github/workflows/haskell-ci.yml"

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: cachix/install-nix-action@v30
        with:
          nix_path: nixpkgs=channel:nixos-unstable

      - name: Cache Nix store
        uses: actions/cache@v4
        with:
          path: /tmp/nix-cache
          key: ${{ runner.os }}-nix-haskell-${{ hashFiles('flake.lock', 'ops/nix/environments/haskell/shell.nix') }}
          restore-keys: |
            ${{ runner.os }}-nix-haskell-

      - name: Cache Stack
        uses: actions/cache@v4
        with:
          path: |
            ~/.stack
            apps/haskell/.stack-work
          key: ${{ runner.os }}-stack-${{ hashFiles('apps/haskell/stack.yaml', 'apps/haskell/package.yaml') }}
          restore-keys: |
            ${{ runner.os }}-stack-

      - name: Build dependencies
        run: nix develop .#haskell --command bash -c "cd apps/haskell && stack build --only-dependencies --test --no-run-tests"

      - name: Lint (HLint)
        run: nix develop .#haskell --command bash -c "cd apps/haskell && hlint src/ test/ app/"

      - name: Complexity check
        run: nix develop .#haskell --command bash -c "cd apps/haskell && bash scripts/complexity.sh --threshold 10 src"

      - name: Test
        run: nix develop .#haskell --command bash -c "cd apps/haskell && stack test"
```

### ワークフローの構成

CI パイプラインは次の 4 ステップで構成されています。

| ステップ | 内容 |
|---------|------|
| Build dependencies | 依存パッケージのビルド（テスト実行なし） |
| Lint (HLint) | 静的解析による品質チェック |
| Complexity check | 循環複雑度の検証 |
| Test | HSpec テストスイートの実行 |

### トリガー条件

```yaml
on:
  push:
    paths:
      - "apps/haskell/**"
      - ".github/workflows/haskell-ci.yml"
  pull_request:
    paths:
      - "apps/haskell/**"
      - ".github/workflows/haskell-ci.yml"
```

`paths` フィルタにより、Haskell 関連ファイルまたはワークフロー定義に変更があった場合のみ CI が実行されます。他の言語のコード変更では実行されないため、CI リソースを節約できます。

### キャッシュ戦略

CI では 2 種類のキャッシュを使用しています。

**Nix ストアキャッシュ**

```yaml
- name: Cache Nix store
  uses: actions/cache@v4
  with:
    path: /tmp/nix-cache
    key: ${{ runner.os }}-nix-haskell-${{ hashFiles('flake.lock', 'ops/nix/environments/haskell/shell.nix') }}
```

`flake.lock` と `shell.nix` のハッシュをキーにすることで、Nix 環境が変わらない限りキャッシュが再利用されます。

**Stack キャッシュ**

```yaml
- name: Cache Stack
  uses: actions/cache@v4
  with:
    path: |
      ~/.stack
      apps/haskell/.stack-work
    key: ${{ runner.os }}-stack-${{ hashFiles('apps/haskell/stack.yaml', 'apps/haskell/package.yaml') }}
```

`stack.yaml` と `package.yaml` のハッシュをキーにすることで、依存関係が変わらない限りビルド済みパッケージが再利用されます。`~/.stack` にはグローバルなパッケージデータベース、`apps/haskell/.stack-work` にはプロジェクト固有のビルド成果物が保存されます。

### Nix を CI で使う利点

- **環境の再現性**: ローカルと CI で同じ Nix 環境を使うため、環境差異による失敗がない
- **ツールバージョンの固定**: `flake.lock` でパッケージバージョンが固定される
- **キャッシュの効率化**: `flake.lock` と `shell.nix` のハッシュでキャッシュキーを生成し、変更時のみ再構築
- **統一的なパターン**: Scala、Clojure など他の言語と同じ CI パターンを使える

## 6.5 品質ゲートの統合

`make check` は、ローカル開発と CI の両方で使える品質ゲートです。

```makefile
check: lint complexity test
```

この構成により、以下の品質基準を自動的に検証します。

| チェック | ツール | 基準 |
|---------|--------|------|
| 静的解析 | HLint | コードスタイルの指摘がゼロ |
| 複雑度 | complexity.sh | 全関数が閾値 10 以下 |
| テスト | HSpec | 全テストが通過 |

ローカルで `make check` を通してからプッシュすることで、CI での失敗を事前に防げます。

## 6.6 開発ワークフロー

Red-Green-Refactor を自動化で支える基本フローは次の通りです。

1. **環境起動**: `nix develop .#haskell` で開発環境に入る
2. **Red**: 失敗するテストを追加し、`make test` で失敗を確認
3. **Green**: 最小実装で `make test` を通過
4. **Refactor**: `make check` で静的解析・複雑度・テストを一括確認
5. **Push**: GitHub Actions で Nix 環境内の同じチェックを再実行

ローカルと CI で同じ Nix 環境・同じコマンドを使うことで、環境差分による失敗を防げます。

```bash
# 典型的な開発セッション
nix develop .#haskell
cd apps/haskell

# ファイル監視モードでテスト
stack test --file-watch

# （別ターミナルで）コードを編集 → 自動でテスト実行

# プッシュ前に品質ゲートを通す
make check

# コミットしてプッシュ
git add src/FizzBuzz.hs test/FizzBuzz/FizzBuzzSpec.hs
git commit -m "feat(fizzbuzz): generateList を実装"
git push
```

## 6.7 まとめ

この章では、Haskell 開発の自動化基盤を整理しました。

- Nix Flakes で GHC、Stack、HLint を含む開発環境を宣言的に管理し、再現性を確保する
- Makefile で日常タスク（`build`, `test`, `lint`, `complexity`, `check`, `clean`）を短いコマンドに集約する
- `make check` で HLint + 複雑度チェック + テストの品質ゲートを一括実行する
- GitHub Actions で Nix 環境を使い、ローカルと同一の品質ゲートを適用する
- Stack キャッシュと Nix キャッシュで CI の実行時間を短縮する

第 2 部の環境整備が完了したので、次章からは型クラスと代数的データ型に進みます。
