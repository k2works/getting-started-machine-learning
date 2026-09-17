---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "前章では Conventional Commits によるコミットメッセージの規約を学びました。"
tags: [article, getting-start-tdd, go]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前章では Conventional Commits によるコミットメッセージの規約を学びました。この章では、**パッケージ管理** と **静的コード解析** を導入し、コードの品質を自動でチェックできるようにします。

## 5.2 Go Modules によるパッケージ管理

### Go Modules とは

> Go Modules は Go のパッケージ管理システムです。`go.mod` ファイルでプロジェクトの依存関係を管理し、再現可能なビルドを保証します。

Ruby の Bundler（Gemfile）、Java の Gradle、Node の npm（package.json）、Python の uv に相当するのが Go Modules です。

### go.mod の構成

本プロジェクトの `go.mod` は以下のようになっています。

```go
module github.com/k2works/getting-started-tdd/apps/go

go 1.25
```

現時点では外部ライブラリを使用していないため、シンプルな構成です。Go の標準ライブラリ（`testing`、`strconv`、`fmt`、`io` など）はインポートパスの指定だけで利用でき、`go.mod` への追加は不要です。

### 主要なコマンド

| コマンド | 説明 |
|---------|------|
| `go mod init <module>` | モジュールを初期化し `go.mod` を作成 |
| `go mod tidy` | 不要な依存を削除し、足りない依存を追加 |
| `go get <package>` | パッケージを追加またはバージョンを変更 |
| `go mod download` | 依存パッケージをダウンロード |

### 外部パッケージの追加例

もしテスティングライブラリ `testify` を使う場合は以下のようにします。

```bash
$ go get github.com/stretchr/testify
```

`go.mod` に依存関係が追加され、`go.sum` にチェックサムが記録されます。本プロジェクトでは Go 標準の `testing` パッケージのみを使用しているため、外部依存はありません。

### Go Modules の特徴

Go Modules には他の言語のパッケージマネージャと異なるユニークな特徴があります。

- **標準ライブラリの充実** — テスティング、HTTP サーバー、JSON パーサーなど、多くの機能が標準ライブラリに含まれている
- **`go.sum` によるチェックサム検証** — 依存パッケージの改ざんを防止
- **Minimum Version Selection** — 最小バージョン選択アルゴリズムにより、依存解決が決定的

## 5.3 静的コード解析（golangci-lint）

### golangci-lint とは

> golangci-lint は Go のメタリンターです。複数の静的解析ツール（linter）をまとめて実行できるフレームワークで、設定ファイルでどのリンターを有効にするかを制御できます。

Ruby の RuboCop、Java の Checkstyle + PMD、TypeScript の ESLint、Python の Ruff に相当するツールです。

### .golangci.yml の設定

```yaml
version: "2"

run:
  timeout: 5m

linters:
  enable:
    - errcheck
    - govet
    - ineffassign
    - staticcheck
    - unused
    - revive

formatters:
  enable:
    - gofmt

issues:
  max-issues-per-linter: 0
  max-same-issues: 0
```

### 有効化しているリンターの解説

| リンター | 説明 |
|---------|------|
| `errcheck` | 戻り値のエラーチェック漏れを検出 |
| `govet` | Go の `go vet` と同等の静的解析 |
| `ineffassign` | 使われていない代入を検出 |
| `staticcheck` | 高度な静的解析（バグ、パフォーマンス、スタイル） |
| `unused` | 未使用のコード（変数、関数、型）を検出 |
| `revive` | スタイルルールのチェック（パッケージコメントなど） |

### golangci-lint の実行

```bash
# 静的解析を実行
$ golangci-lint run

# 自動修正（gofmt フォーマット）
$ golangci-lint run --fix
```

実行結果の例:

```bash
$ golangci-lint run
# 問題がなければ出力なし
```

Go の静的解析ツールは問題がない場合は何も出力しないのが特徴です。

### golangci-lint v2 の変更点

本プロジェクトでは golangci-lint v2 を使用しています。v1 からの主な変更点は以下の通りです。

| 項目 | v1 | v2 |
|------|----|----|
| 設定ファイル | `version` フィールド不要 | `version: "2"` が必須 |
| `gofmt` | `linters.enable` に配置 | `formatters.enable` に配置 |
| `gosimple` | 独立したリンター | `staticcheck` に統合 |

## 5.4 コードフォーマッター（gofmt）

### gofmt とは

> gofmt は Go 標準のコードフォーマッターです。Go ではフォーマットのスタイルが言語仕様として統一されており、全ての Go コードが同じスタイルになります。

Ruby の RuboCop（フォーマット機能）、Java の Spotless、TypeScript の Prettier、Python の Ruff（フォーマッター）に相当しますが、**Go 標準ツール** であり追加インストール不要という点が大きな特徴です。

### gofmt の実行

```bash
# フォーマット差分を表示
$ gofmt -d .

# 自動フォーマット
$ gofmt -w .
```

### Go のフォーマットの特徴

Go ではフォーマットスタイルに関する議論が不要です。

- **タブ** でインデント（スペースではない）
- **波括弧** は同じ行に開く
- **import 文** は自動でグループ化・ソート

```go
// Go の標準フォーマット — 全員がこのスタイル
func Generate(number int) string {
	switch {
	case number%15 == 0:
		return "FizzBuzz"
	case number%3 == 0:
		return "Fizz"
	case number%5 == 0:
		return "Buzz"
	default:
		return strconv.Itoa(number)
	}
}
```

> Go のフォーマットに関しては「gofmt のスタイルは誰のお気に入りでもないが、gofmt はみんなのお気に入りだ」という格言があります。

## 5.5 コードカバレッジ

### Go の組み込みカバレッジ

Go にはコードカバレッジ機能が **標準で組み込まれて** います。外部ツールのインストールは不要です。

Ruby の SimpleCov、Java の JaCoCo、TypeScript の @vitest/coverage-v8、Python の pytest-cov に相当しますが、Go では標準の `go test` コマンドだけで計測できます。

### カバレッジの計測

```bash
# カバレッジプロファイルを生成
$ go test -coverprofile=coverage.out ./...

# 関数ごとのカバレッジを表示
$ go tool cover -func=coverage.out
```

実行結果の例:

```bash
$ go test -coverprofile=coverage.out ./...
ok  	github.com/k2works/getting-started-tdd/apps/go/fizzbuzz	0.002s	coverage: 100.0% of statements

$ go tool cover -func=coverage.out
github.com/k2works/getting-started-tdd/apps/go/fizzbuzz/fizzbuzz.go:11:	Generate	100.0%
github.com/k2works/getting-started-tdd/apps/go/fizzbuzz/fizzbuzz.go:25:	GenerateList	100.0%
github.com/k2works/getting-started-tdd/apps/go/fizzbuzz/fizzbuzz.go:34:	Print		100.0%
total:									(statements)	100.0%
```

### HTML カバレッジレポート

```bash
# HTML レポートを生成
$ go tool cover -html=coverage.out -o coverage.html
```

`coverage.html` をブラウザで開くと、コードのどの部分がテストでカバーされているかを視覚的に確認できます。

## 5.6 コード複雑度のチェック

### 循環的複雑度

golangci-lint の `gocyclo` リンターを使うと循環的複雑度をチェックできます。

> 循環的複雑度（サイクロマティック複雑度）とは、コードがどれぐらい複雑であるかをメソッド単位で数値にして表す指標です。

| 複雑度の範囲 | 意味 |
|-------------|------|
| 1〜10 | 低複雑度：管理しやすく、問題なし |
| 11〜20 | 中程度の複雑度：リファクタリングを検討 |
| 21〜50 | 高複雑度：リファクタリングが強く推奨される |
| 51 以上 | 非常に高い複雑度：コードを分割する必要がある |

現在の `Generate` 関数は switch 文で 4 つの分岐があり、循環的複雑度は **4** です。これは低複雑度に分類され、問題ありません。

### Go vet

Go 標準の `go vet` も重要な静的解析ツールです。

```bash
$ go vet ./...
```

`go vet` は以下のような問題を検出します。

- Printf 系関数の書式文字列とフォーマット引数の不一致
- 不正なビルドタグ
- 構造体リテラルでのフィールド名の省略

## 5.7 品質チェックの一括実行

すべての品質チェックを一括で実行するには、Makefile の `check` ターゲットを使用します（次章で設定します）。

```bash
$ make check
```

### 各言語の品質ツール比較

| 用途 | Go | Ruby | Java | TypeScript | Python |
|------|-----|------|------|-----------|--------|
| パッケージ管理 | Go Modules | Bundler | Gradle | npm | uv |
| テスト | testing（標準） | Minitest | JUnit 5 | Vitest | pytest |
| 静的解析 | golangci-lint | RuboCop | Checkstyle + PMD | ESLint | Ruff |
| フォーマッター | gofmt（標準） | RuboCop | Checkstyle | Prettier | Ruff |
| カバレッジ | go test（標準） | SimpleCov | JaCoCo | @vitest/coverage-v8 | pytest-cov |

Go の特徴として、テスト・フォーマッター・カバレッジが **標準ツールに含まれている** 点が挙げられます。外部ツールが必要なのは静的解析（golangci-lint）のみです。

## 5.8 まとめ

この章では、以下の品質管理ツールを確認・導入しました。

1. **Go Modules** — パッケージ管理と依存関係の管理（`go.mod` + `go.sum`）
2. **golangci-lint** — 静的コード解析（`.golangci.yml` で設定）
3. **gofmt** — コードフォーマット（Go 標準ツール）
4. **go test -cover** — コードカバレッジの計測（Go 標準ツール）

次の章では、タスクランナーを導入してこれらの品質チェックを自動化し、CI/CD パイプラインを構築します。
