---
type: Article
title: "第 4 章: バージョン管理と Conventional Commits"
description: "前章までで、TDD の基本サイクルを通じて FizzBuzz プログラムを完成させました。"
tags: [article, getting-start-tdd, java]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 4 章: バージョン管理と Conventional Commits

## 4.1 はじめに

前章までで、TDD の基本サイクルを通じて FizzBuzz プログラムを完成させました。この章からは「動作するきれいなコード」を書き続けるために必要な **ソフトウェア開発の三種の神器** を整備していきます。

> 今日のソフトウェア開発の世界において絶対になければならない 3 つの技術的な柱があります。三本柱と言ったり、三種の神器と言ったりしていますが、それらは
>
> - バージョン管理
> - テスティング
> - 自動化
>
> の 3 つです。
>
> — 和田卓人

**バージョン管理** と **テスティング** に関しては第 1 部で触れました。本章ではバージョン管理をさらに深掘りし、**コミットメッセージの規約** について解説します。

## 4.2 コミットメッセージの重要性

これまでの作業では、区切りごとにリポジトリにコミットしてきました。しかし、コミットメッセージの書き方に一貫性がないと、後からプロジェクトの履歴を追うのが難しくなります。

チーム開発では特に、誰がいつ何のためにコードを変更したのかを明確にすることが重要です。そこで役立つのが **Conventional Commits** です。

## 4.3 Conventional Commits

本プロジェクトでは [Angular ルール](https://github.com/angular/angular.js/blob/master/DEVELOPERS.md#type) に基づいた **Conventional Commits** の書式を採用します。

### コミットメッセージのフォーマット

```
<タイプ>(<スコープ>): <タイトル>
<空行>
<ボディ>
<空行>
<フッタ>
```

- **ヘッダ** は必須です
- **スコープ** は任意です
- タイトルは **50 文字以内** にしてください（GitHub 上で読みやすくなります）

### コミットのタイプ

| タイプ | 説明 | 使用場面 |
|--------|------|---------|
| `feat` | A new feature（新しい機能） | 新機能の追加 |
| `fix` | A bug fix（バグ修正） | バグの修正 |
| `docs` | Documentation only changes（ドキュメント変更のみ） | README やコメントの更新 |
| `style` | Changes that do not affect the meaning of the code（コードに影響を与えない変更） | フォーマット、セミコロンの追加など |
| `refactor` | A code change that neither fixes a bug nor adds a feature（リファクタリング） | コード構造の改善 |
| `perf` | A code change that improves performance（パフォーマンス改善） | 処理速度の向上 |
| `test` | Adding missing or correcting existing tests（テストの追加・修正） | テストコードの変更 |
| `chore` | Changes to the build process or auxiliary tools（ビルドプロセスや補助ツールの変更） | 設定ファイルの更新 |

### コミットメッセージの例

```bash
# 新機能の追加
$ git commit -m 'feat: FizzBuzz のリスト生成機能を追加'

# バグ修正
$ git commit -m 'fix: 15 の倍数で FizzBuzz が返らない問題を修正'

# リファクタリング
$ git commit -m 'refactor: メソッドの抽出'

# テストの追加
$ git commit -m 'test: 3 の倍数のテストケースを追加'

# ビルド設定の変更
$ git commit -m 'chore: 静的コード解析セットアップ'

# ドキュメントの更新
$ git commit -m 'docs: README にプロジェクト概要を追加'
```

### スコープ付きのコミットメッセージ

スコープを使うと、変更の影響範囲をより明確にできます。

```bash
$ git commit -m 'feat(fizzbuzz): generate メソッドに Fizz 判定を追加'
$ git commit -m 'chore(gradle): JaCoCo プラグインを追加'
$ git commit -m 'test(fizzbuzz): パラメータ化テストに変換'
```

## 4.4 TDD とコミットのタイミング

TDD サイクルでは、以下のタイミングでコミットすることを推奨します。

```
Red（テスト失敗）→ Green（テスト成功）→ Refactor → **Commit**
```

具体的には：

1. **Green の後** — テストが通ったらコミット（`feat` または `fix`）
2. **Refactor の後** — リファクタリングが完了したらコミット（`refactor`）
3. **設定変更の後** — ビルド設定やツール設定を変更したらコミット（`chore`）

小さな変更を頻繁にコミットすることで、問題が発生した際に原因を特定しやすくなります。

## 4.5 まとめ

この章では、バージョン管理の実践として Conventional Commits を学びました。

| 項目 | 内容 |
|------|------|
| コミットメッセージ規約 | Angular ルールに基づく Conventional Commits |
| フォーマット | `<タイプ>(<スコープ>): <タイトル>` |
| タイプ | feat, fix, docs, style, refactor, perf, test, chore |
| コミットタイミング | TDD サイクルの Green/Refactor 完了後 |

次の章では、パッケージ管理と静的コード解析ツールを導入し、コードの品質を自動でチェックできるようにします。
