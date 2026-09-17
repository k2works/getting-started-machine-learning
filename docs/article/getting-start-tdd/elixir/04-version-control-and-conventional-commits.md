---
type: Article
title: "第 4 章: バージョン管理と Conventional Commits"
description: "この章では、Elixir プロジェクトを安全に育てるための Git の基本操作と、Conventional Commits の実践方法を学びます。"
tags: [article, getting-start-tdd, elixir]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 4 章: バージョン管理と Conventional Commits

## 4.1 はじめに

この章では、Elixir プロジェクトを安全に育てるための Git の基本操作と、Conventional Commits の実践方法を学びます。TDD では小さな変更を頻繁にコミットするため、履歴の読みやすさが品質に直結します。

## 4.2 Git の基本操作

まずは作業の最小ループを押さえます。

```bash
# 変更確認
git status

# 差分確認
git diff

# ステージング
git add lib/fizz_buzz.ex test/fizz_buzz_test.exs

# コミット
git commit -m "test: add test for fizzbuzz list generation"

# 履歴確認
git log --oneline --decorate -n 10
```

TDD では次の粒度でコミットすると履歴が明確です。

- Red: 失敗するテスト追加
- Green: テストを通す最小実装
- Refactor: 振る舞いを変えない整理

## 4.3 Conventional Commits のルール

Conventional Commits は、コミットメッセージを `type: summary` 形式で統一する規約です。

代表的な `type` は次のとおりです。

- `feat`: ユーザーに見える機能追加
- `fix`: バグ修正
- `docs`: ドキュメント変更
- `refactor`: 振る舞いを変えない内部改善
- `test`: テスト追加・修正
- `chore`: ビルドや設定などの雑務変更

例:

```text
feat: add generate_list/0 for 1..100 fizzbuzz output
fix: handle 15 multiples before 3 and 5 checks
docs: add chapter 4 for git workflow
test: add cases for fizz and buzz boundaries
refactor: simplify generate/1 with cond ordering
chore: update ci cache key for nix flake
```

## 4.4 Elixir プロジェクトでのコミット例

`apps/elixir` の FizzBuzz 実装を例に、実際のコミット単位を示します。

```bash
# Red
git add test/fizz_buzz_test.exs
git commit -m "test: add failing test for generate_list/0"

# Green
git add lib/fizz_buzz.ex
git commit -m "feat: implement generate_list/0 with enum map"

# Refactor
git add lib/fizz_buzz.ex test/fizz_buzz_test.exs
git commit -m "refactor: improve readability of fizzbuzz conditions"
```

ポイントは、1 コミット 1 意図です。`feat` と `refactor` を同一コミットに混ぜないことで、レビューしやすくなります。

## 4.5 .gitignore の設定

Elixir ではビルド成果物と依存パッケージを Git 管理対象から除外します。`apps/elixir/.gitignore` には少なくとも次を含めます。

```gitignore
/_build/
/deps/
.elixir_ls/
```

- `/_build/`: `mix compile` の生成物
- `/deps/`: `mix deps.get` で取得される依存コード
- `.elixir_ls/`: Elixir Language Server の作業ディレクトリ

これらを除外すると、環境依存ファイルの混入を防げます。

## 4.6 まとめ

この章では、Git の基本操作、Conventional Commits の主要ルール、Elixir プロジェクトでの具体的なコミット例、`.gitignore` の重要設定を確認しました。以降の章では、この履歴運用を前提に品質チェックと自動化を強化します。
