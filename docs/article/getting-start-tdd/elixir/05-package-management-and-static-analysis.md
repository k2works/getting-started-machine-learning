---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "この章では、Elixir の依存管理とコード品質チェックを扱います。"
tags: [article, getting-start-tdd, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

この章では、Elixir の依存管理とコード品質チェックを扱います。`Mix`、`Hex`、`Credo`、`mix format`、複雑度チェックを組み合わせ、TDD の継続速度と品質を両立します。

## 5.2 Mix による依存管理

Elixir の依存関係は `mix.exs` の `deps/0` で定義します。

```elixir
# apps/elixir/mix.exs

defp deps do
  [
    {:credo, "~> 1.7", only: [:dev, :test], runtime: false}
  ]
end
```

依存追加後は `mix deps.get` で取得します。

```bash
cd apps/elixir
mix deps.get
```

`only: [:dev, :test]` を使うと、開発時だけ必要なツール依存を本番実行から分離できます。

## 5.3 Hex パッケージマネージャ

`Hex` は Elixir の公式パッケージレジストリです。`mix deps.get` は `Hex` から依存を解決し、`mix.lock` にバージョンを固定します。

日常的に使う操作は次のとおりです。

```bash
mix deps.get        # 依存取得
mix deps.update all # 依存更新
mix deps.tree       # 依存ツリー確認
```

チーム開発では `mix.lock` をコミットし、依存の再現性を確保します。

## 5.4 Credo による静的解析

`Credo` は Elixir の静的解析ツールです。本プロジェクトでは厳格モードを使います。

```bash
mix credo --strict
```

`--strict` は警告を増やし、早い段階で設計上の問題や可読性低下を検出できます。`apps/elixir/Makefile` でも `lint` タスクとして定義されています。

## 5.5 mix format と .formatter.exs

コードフォーマットは `mix format` で統一します。

```bash
mix format
mix format --check-formatted
```

`apps/elixir/.formatter.exs` では対象ファイルを次のように設定しています。

```elixir
[
  inputs: ["{mix,.formatter}.exs", "{config,lib,test}/**/*.{ex,exs}"]
]
```

CI では `--check-formatted` を使い、未整形コードの混入を防ぎます。

## 5.6 コード複雑度チェック

本プロジェクトでは `scripts/complexity.sh` で循環複雑度を簡易計測します。

```bash
bash scripts/complexity.sh --threshold 10 lib
```

- `--threshold 10`: 閾値を 10 に設定
- `lib`: チェック対象ディレクトリ

このチェックを `make complexity` や CI に組み込むことで、関数の肥大化を継続的に抑制できます。

## 5.7 まとめ

この章では、`mix.exs` の `deps` を起点にした依存管理、`Hex` によるパッケージ解決、`Credo` と `mix format` による静的品質担保、`scripts/complexity.sh` による複雑度管理を確認しました。次章では、これらを `Makefile` と CI/CD に統合します。
