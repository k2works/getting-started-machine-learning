---
type: Article
title: "第 6 章: タスクランナーと CI/CD"
description: "この章では、ローカル開発と CI を同じ実行基盤でそろえる方法を扱います。"
tags: [article, getting-start-tdd, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 6 章: タスクランナーと CI/CD

## 6.1 はじめに

この章では、ローカル開発と CI を同じ実行基盤でそろえる方法を扱います。Nix で環境差分を吸収し、`Makefile` と GitHub Actions で品質チェックを自動化します。

## 6.2 Nix 開発環境による環境管理

本プロジェクトの Elixir 開発環境は Nix で提供されています。次のコマンドで同一ツールチェーンに入れます。

```bash
nix develop .#elixir
```

この方式により、OS 差分やローカルの手動セットアップ差分を最小化できます。CI でも同じ `nix develop .#elixir` を使うため、ローカル再現性が高くなります。

## 6.3 Makefile でのタスク管理

`apps/elixir/Makefile` には主要タスクが定義されています。

- `make test`: `mix test`
- `make fmt`: `mix format`
- `make fmt-check`: `mix format --check-formatted`
- `make lint`: `mix credo --strict`
- `make complexity`: `bash scripts/complexity.sh --threshold 10 lib`
- `make check`: `fmt-check` + `lint` + `complexity` + `test`

開発中は `make check` を最終ゲートにすると、レビュー前の品質をそろえやすくなります。

## 6.4 mix の監視実行と高速テスト

Elixir ではファイル更新検知に Linux 固有の `inotify-tools` は不要です。継続実行したい場合は監視実行を利用します。

```bash
mix test --watch
```

また、変更影響のあるテストだけを優先して回すには `--stale` が有効です。

```bash
mix test --stale
```

- `--watch`: 保存ごとに再実行
- `--stale`: 変更があったモジュールに関連するテスト中心で実行

日常開発は `mix test --stale`、節目では `mix test` や `make check` を回す運用が実践的です。

## 6.5 GitHub Actions での Nix ベース CI/CD

Elixir 用 CI は最初から Nix ベースで構築されています。`.github/workflows/elixir-ci.yml` では次の流れを実行します。

1. `actions/checkout@v4`
2. `cachix/install-nix-action@v30` で Nix を導入
3. `nix develop .#elixir --command ...` で依存取得・Format・Lint・複雑度・テストを実行

例:

```yaml
- name: Install Nix
  uses: cachix/install-nix-action@v30

- name: Run tests
  run: nix develop .#elixir --command bash -c "cd apps/elixir && mix test"
```

重要なのは、ローカルと CI が同じ `nix develop` を起点にしている点です。これにより「ローカルでは通るが CI で失敗する」を減らせます。

## 6.6 まとめ

この章では、`nix develop .#elixir` による環境統一、`Makefile` によるタスク標準化、`mix test --watch` / `mix test --stale` の使い分け、GitHub Actions の Nix ベース CI を確認しました。これで TDD の反復を壊さない自動化基盤が整います。
