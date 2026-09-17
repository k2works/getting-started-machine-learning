---
type: Article
title: "第 6 章: タスクランナーと CI/CD"
description: "前章では静的コード解析ツールとコードカバレッジを導入しました。"
tags: [article, getting-start-tdd, go]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 6 章: タスクランナーと CI/CD

## 6.1 はじめに

前章では静的コード解析ツールとコードカバレッジを導入しました。テストの実行、静的解析、フォーマットチェック、カバレッジ計測と、様々なコマンドを使えるようになりましたが、毎回それぞれのコマンドを覚えて実行するのは面倒です。

この章では **タスクランナー** を使ってこれらのタスクをまとめて実行できるようにし、さらに **CI/CD** パイプラインを構築します。

## 6.2 Makefile によるタスク管理

### Makefile とは

> Makefile は Unix 系の定番ビルド/タスク管理ツールである make の設定ファイルです。ターゲット（タスク名）と依存関係、実行コマンドを定義し、`make <ターゲット>` で実行できます。

Ruby の Rake、Java の Gradle、Node の npm scripts、Python の tox に相当します。Go のプロジェクトでは伝統的に Makefile がタスクランナーとして使われています。

### Makefile の定義

```makefile
.PHONY: test vet lint fmt coverage check build run clean

test:
	go test -v ./...

vet:
	go vet ./...

lint:
	golangci-lint run

fmt:
	gofmt -w .

coverage:
	go test -coverprofile=coverage.out ./...
	go tool cover -func=coverage.out

check: fmt vet lint test

build:
	go build -o bin/fizzbuzz .

run:
	go run .

clean:
	go clean
	rm -rf bin/
	rm -f coverage.out coverage.html
```

### ターゲット一覧の確認

```bash
$ make help
# Makefile にはビルトインの help がないため、
# 定義済みターゲットは Makefile を参照してください
```

### 主要ターゲットの解説

| ターゲット | 説明 |
|-----------|------|
| `make test` | テストを実行（`-v` で詳細表示） |
| `make vet` | `go vet` による静的解析 |
| `make lint` | golangci-lint による静的解析 |
| `make fmt` | gofmt によるコードフォーマット |
| `make coverage` | テストカバレッジを計測 |
| `make check` | 全品質チェックを一括実行（fmt + vet + lint + test） |
| `make build` | バイナリをビルド |
| `make run` | プログラムを実行 |
| `make clean` | ビルド成果物をクリーンアップ |

`check` ターゲットは `fmt`、`vet`、`lint`、`test` を依存ターゲットとして定義しており、全てが成功した場合のみ完了します。

```bash
# 品質チェックの一括実行
$ make check
```

## 6.3 ファイル監視による自動実行

Go では専用のファイル監視ツールとして [fswatch](https://github.com/emcrisostomo/fswatch) や [air](https://github.com/cosmtrek/air) が利用できます。Makefile にファイル監視ターゲットを追加することで、コード変更時にテストを自動実行できます。

### fswatch を使った監視

```bash
# macOS の場合
$ brew install fswatch

# ファイル変更時にテストを自動実行
$ fswatch -o . | xargs -n1 -I{} make test
```

### TDD サイクルでの活用

ファイル監視を起動した状態で TDD サイクルを回すと、コードを保存するたびにテストが自動実行されます。

```
1. テストを書く（Red）
2. ファイルを保存 → テスト自動実行 → 失敗を確認
3. 実装する（Green）
4. ファイルを保存 → テスト自動実行 → 成功を確認
5. リファクタリング
6. ファイルを保存 → テスト自動実行 → 成功を確認
7. コミット
```

Ruby の Guard、Java の Gradle Continuous Build（`--continuous`）、Node の Gulp（watch 機能）に相当します。

## 6.4 GitHub Actions による CI/CD

プッシュやプルリクエスト時に自動で品質チェックを実行する CI/CD パイプラインを構築します。

### ワークフロー設定

```yaml
# .github/workflows/go-ci.yml
name: Go CI

on:
  push:
    branches: [main, develop]
    paths:
      - "apps/go/**"
      - ".github/workflows/go-ci.yml"
  pull_request:
    branches: [main]
    paths:
      - "apps/go/**"

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
          key: ${{ runner.os }}-nix-go-${{ hashFiles('flake.lock', 'ops/nix/environments/go/shell.nix') }}
          restore-keys: |
            ${{ runner.os }}-nix-go-

      - name: Run gofmt check
        run: nix develop .#go --command bash -c "cd apps/go && test -z \$(gofmt -l .)"

      - name: Run go vet
        run: nix develop .#go --command bash -c "cd apps/go && go vet ./..."

      - name: Run golangci-lint
        run: nix develop .#go --command bash -c "cd apps/go && golangci-lint run"

      - name: Run tests
        run: nix develop .#go --command bash -c "cd apps/go && go test -v ./..."

      - name: Run coverage
        run: nix develop .#go --command bash -c "cd apps/go && go test -coverprofile=coverage.out ./... && go tool cover -func=coverage.out"
```

### ワークフローのポイント

| 設定 | 説明 |
|------|------|
| `paths` フィルター | `apps/go/**` に変更があった場合のみ実行 |
| Nix 環境 | `nix develop .#go` で一貫した環境を保証 |
| キャッシュ | Nix ストアをキャッシュして CI を高速化 |
| ステップ分離 | 各チェックを個別ステップで実行し、失敗箇所を特定しやすく |
| gofmt チェック | フォーマット差分があればエラーで終了 |

### 各言語の CI/CD 比較

| 項目 | Go | Ruby | Java | TypeScript | Python |
|------|-----|------|------|-----------|--------|
| CI ツール | GitHub Actions | GitHub Actions | GitHub Actions | GitHub Actions | GitHub Actions |
| 環境管理 | Nix + Go Modules | Nix + Bundler | Nix + Gradle | Nix + npm | Nix + uv |
| テスト | `go test ./...` | `bundle exec rake test` | `./gradlew test` | `npm test` | `uv run tox -e test` |
| 品質チェック | `make check` | `bundle exec rake check` | `./gradlew fullCheck` | `npm run check` | `uv run tox` |
| タスクランナー | Makefile | Rake | Gradle | Gulp | tox |

## 6.5 開発ワークフローのまとめ

ここまでの設定により、以下の開発ワークフローが確立されました。

### 日常の開発フロー

```
1. テストを書く（Red）
2. make test で確認（失敗）
3. 実装する（Green）
4. make test で確認（成功）
5. リファクタリング
6. make check で品質チェック
7. コミット（Conventional Commits）
8. プッシュ → CI が自動実行
```

### ツール一覧

| カテゴリ | ツール | 用途 |
|---------|--------|------|
| テスト | testing（標準） | テスト実行 |
| カバレッジ | go test -cover（標準） | ステートメントカバレッジ |
| パッケージ管理 | Go Modules | 依存関係管理 |
| 静的解析 | golangci-lint | コード品質チェック |
| フォーマッター | gofmt（標準） | コードフォーマット |
| タスクランナー | Makefile | タスク自動化 |
| CI/CD | GitHub Actions | 継続的インテグレーション |

## 6.6 まとめ

第 2 部（章 4〜6）を通じて、ソフトウェア開発の三種の神器を整備しました。

| 神器 | 導入したもの |
|------|------------|
| バージョン管理 | Git + Conventional Commits |
| テスティング | testing（標準）+ go test -cover |
| 自動化 | golangci-lint + gofmt + Makefile + GitHub Actions |

Go の特徴として、テスト・フォーマッター・カバレッジが **言語標準に含まれている** ため、外部ツールへの依存が最小限で済みます。追加が必要だったのは golangci-lint（静的解析）と GitHub Actions（CI/CD）のみです。

次の第 3 部では、追加仕様を題材にオブジェクト指向設計（カプセル化、ポリモーフィズム、デザインパターン）を学びます。Go はクラスや継承を持たない独自のアプローチで OOP を実現する言語であり、インターフェースと構造体の組み合わせによる設計を探っていきます。
