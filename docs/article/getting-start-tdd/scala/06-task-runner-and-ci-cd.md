---
type: Article
title: "第 6 章: タスクランナーと CI/CD"
description: "TDD を継続するには、テスト・整形・静的解析を毎回確実に実行できる仕組みが必要です。"
tags: [article, getting-start-tdd, scala]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 6 章: タスクランナーと CI/CD

## 6.1 はじめに

TDD を継続するには、テスト・整形・静的解析を毎回確実に実行できる仕組みが必要です。
この章では、`Makefile`、`sbt` タスク、Nix 開発環境、GitHub Actions を使って開発タスクを自動化します。

## 6.2 Nix による開発環境

本プロジェクトでは Nix Flakes で開発環境を宣言的に管理しています。
`ops/nix/environments/scala/shell.nix` に Scala 環境の定義があります。

```nix
{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    scala_3
    sbt
    metals
    scala-cli
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo "Scala development environment activated"
  '';
}
```

環境の起動:

```bash
nix develop .#scala
```

これにより、Scala 3、sbt、Metals、scala-cli が揃った環境に入れます。
チーム全員が同じツールバージョンを使えるため、「自分のマシンでは動く」問題を防げます。

## 6.3 Makefile によるタスク管理

`apps/scala/Makefile` には、日常的に使うタスクが定義されています。

```makefile
.PHONY: test fmt fmt-check lint complexity check build run clean

test:
	sbt test

fmt:
	sbt scalafmt

fmt-check:
	sbt scalafmtCheck

lint:
	sbt compile

complexity:
	bash scripts/complexity.sh --threshold 10 src

check: fmt-check lint complexity test
```

この構成の狙いは、`make check` 1 つで品質ゲートをまとめて実行できる点です。

- `test`: 単体テスト実行
- `fmt` / `fmt-check`: フォーマット適用と検証
- `lint`: コンパイルによる静的チェック
- `complexity`: コード複雑度チェック
- `check`: CI と同等のローカル検証

Nix 環境内で実行するのが前提です:

```bash
nix develop .#scala --command make check
```

## 6.4 sbt のカスタムタスク

`sbt` では `build.sbt` に独自タスクを追加できます。
たとえば、テストと整形確認をまとめたタスクは次のように定義できます。

```scala
lazy val verify = taskKey[Unit]("Run format check and tests")

verify := {
  (Compile / compile).value
  (Test / test).value
}
```

また、`~` prefix を使うとファイル変更を監視して自動実行できます。

```bash
nix develop .#scala --command sbt ~test
```

TDD の Red → Green を高速に回すときに有効です。

## 6.5 GitHub Actions による CI/CD

`.github/workflows/scala-ci.yml` では、Nix を活用して CI を実行しています。

```yaml
name: Scala CI

on:
  push:
    branches: [main, develop]
    paths:
      - "apps/scala/**"
      - ".github/workflows/scala-ci.yml"
  pull_request:
    branches: [main]
    paths:
      - "apps/scala/**"

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout the repository
        uses: actions/checkout@v4

      - name: Install Nix
        uses: cachix/install-nix-action@v30
        with:
          nix_path: nixpkgs=channel:nixos-unstable

      - name: Cache Nix store
        uses: actions/cache@v4
        with:
          path: /tmp/nix-cache
          key: ${{ runner.os }}-nix-scala-${{ hashFiles('flake.lock', 'ops/nix/environments/scala/shell.nix') }}
          restore-keys: |
            ${{ runner.os }}-nix-scala-

      - name: Check formatting
        run: nix develop .#scala --command bash -c "cd apps/scala && sbt scalafmtCheck"

      - name: Compile (lint)
        run: nix develop .#scala --command bash -c "cd apps/scala && sbt compile"

      - name: Run complexity check
        run: nix develop .#scala --command bash -c "cd apps/scala && bash scripts/complexity.sh --threshold 10 src"

      - name: Run tests
        run: nix develop .#scala --command bash -c "cd apps/scala && sbt test"
```

### Nix を CI で使う利点

- **環境の再現性**: ローカルと CI で同じ Nix 環境を使うため、環境差異による失敗がない
- **ツールバージョンの固定**: `flake.lock` でパッケージバージョンが固定される
- **キャッシュの効率化**: `flake.lock` と `shell.nix` のハッシュでキャッシュキーを生成し、変更時のみ再構築
- **統一的なパターン**: Clojure、.NET など他の言語と同じ CI パターンを使える

## 6.6 開発ワークフロー

Red-Green-Refactor を自動化で支える基本フローは次の通りです。

1. 環境起動: `nix develop .#scala` で開発環境に入る
2. Red: 失敗するテストを追加し、`make test` で失敗を確認
3. Green: 最小実装で `make test` を通過
4. Refactor: `make check` で整形・コンパイル・複雑度・テストを一括確認
5. Push: GitHub Actions で Nix 環境内の同じチェックを再実行

ローカルと CI で同じ Nix 環境・同じコマンドを使うことで、環境差分による失敗を防げます。

## 6.7 まとめ

この章では、Scala 開発の自動化基盤を整理しました。

- Nix Flakes で開発環境を宣言的に管理し、再現性を確保する
- `Makefile` で日常タスクを短いコマンドに集約する
- `sbt` のタスクと監視実行で開発サイクルを高速化する
- GitHub Actions で Nix 環境を使い、ローカルと同一の品質ゲートを適用する

第 2 部の環境整備が完了したので、次章からはオブジェクト指向設計に進みます。
