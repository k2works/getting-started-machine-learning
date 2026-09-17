---
type: Article
title: "第 4 章: バージョン管理と Conventional Commits"
description: "第 1 部では、TDD の基本サイクルで FizzBuzz プログラムを完成させました。"
tags: [article, getting-start-tdd, python]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 4 章: バージョン管理と Conventional Commits

## 4.1 はじめに

第 1 部では、TDD の基本サイクルで FizzBuzz プログラムを完成させました。第 2 部では、ソフトウェア開発の **三種の神器** を整備し、開発環境を強化していきます。

> ソフトウェア開発の三種の神器
>
> - バージョン管理
> - テスティング
> - 自動化
>
> — 和田卓人

## 4.2 コミットメッセージの重要性

チーム開発において、コミットメッセージの書き方がバラバラだと、変更履歴から「何が変わったか」を追跡するのが困難になります。一貫性のあるルールを設けることで、変更の意図が明確になり、レビューやデバッグが効率化されます。

## 4.3 Conventional Commits

[Conventional Commits](https://www.conventionalcommits.org/) は、コミットメッセージに統一的なフォーマットを与える規約です。

### フォーマット

```
<type>(<scope>): <subject>

<body>

<footer>
```

- **Header**（必須）: `<type>(<scope>): <subject>`（50 文字以内）
- **Body**（任意）: 変更の詳細
- **Footer**（任意）: Breaking Changes、Issue への参照

### コミットタイプ

| タイプ | 説明 | 例 |
|--------|------|-----|
| feat | 新機能の追加 | `feat(fizzbuzz): リスト生成機能を追加` |
| fix | バグ修正 | `fix(fizzbuzz): 15 の判定順序を修正` |
| docs | ドキュメントのみの変更 | `docs: README にセットアップ手順を追加` |
| style | コードの意味に影響しない変更 | `style: フォーマットを適用` |
| refactor | バグ修正でも機能追加でもないコード変更 | `refactor: マジックナンバーを定数化` |
| test | テストの追加・修正 | `test: FizzBuzz の境界値テストを追加` |
| chore | ビルドやツールの変更 | `chore: Ruff の設定を追加` |

### 実践例

第 1 部で作成したコードに適用すると、以下のようなコミット履歴になります。

```bash
# 章 1: 仮実装
$ git commit -m "test(fizzbuzz): 1 を渡したら文字列 1 を返す"

# 章 2: 三角測量
$ git commit -m "test(fizzbuzz): 三角測量で数を文字列に変換する処理を一般化"

# 章 2: 明白な実装
$ git commit -m "feat(fizzbuzz): Fizz/Buzz/FizzBuzz の判定ロジックを実装"

# 章 3: リスト生成とプリント
$ git commit -m "feat(fizzbuzz): 1 から 100 までのリスト生成とプリント機能を追加"
```

## 4.4 TDD とコミットのタイミング

TDD サイクルにおけるコミットの推奨タイミングは以下の通りです。

```
Red（テスト失敗）→ Green（テスト成功）→ コミット → Refactor → コミット
```

- **Green 後**: テストが通った時点でコミットし、動作する状態を記録する
- **Refactor 後**: リファクタリングが完了した時点でコミットし、設計改善を記録する

## 4.5 まとめ

| 項目 | 内容 |
|------|------|
| フォーマット | `<type>(<scope>): <subject>` |
| タイプ | feat, fix, docs, style, refactor, test, chore |
| コミット単位 | 1 コミット = 1 論理的変更 |
| タイミング | Green 後、Refactor 後 |

次の章では、パッケージ管理と静的解析ツール（Ruff、mypy、pytest-cov）を導入します。
