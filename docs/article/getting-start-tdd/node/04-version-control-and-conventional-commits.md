---
type: Article
title: "第 4 章: バージョン管理と Conventional Commits"
description: "前章までで、TDD の基本サイクルを通じて FizzBuzz プログラムを完成させました。"
tags: [article, getting-start-tdd, node]
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
$ git commit -m 'fix: 15 の倍数の判定ロジックを修正'

# リファクタリング
$ git commit -m 'refactor: generate メソッドの条件分岐を整理'

# テストの追加
$ git commit -m 'test: FizzBuzz の境界値テストを追加'

# ビルド設定の変更
$ git commit -m 'chore: Vitest と TypeScript の設定を更新'
```

## 4.4 TDD とコミットのタイミング

TDD の Red-Green-Refactor サイクルにおいて、コミットする適切なタイミングは以下の通りです。

```
Red（テスト作成）→ Green（テスト成功）→ コミット → Refactor（リファクタリング）→ コミット
```

1. **Green の後** — テストが通った時点でコミット（`feat:` や `fix:`）
2. **Refactor の後** — リファクタリングが完了しテストがパスした時点でコミット（`refactor:`）

### 実際のコミット例

FizzBuzz の開発過程では、以下のようなコミット履歴になります。

```bash
$ git log --oneline

abc1234 refactor: generate メソッドの条件順序を最適化
def5678 feat: 3 と 5 の倍数で FizzBuzz を返す機能を追加
ghi9012 feat: 5 の倍数で Buzz を返す機能を追加
jkl3456 feat: 3 の倍数で Fizz を返す機能を追加
mno7890 feat: 数を文字列に変換する generate メソッドを実装
pqr1234 test: FizzBuzz の最初のテストを作成
stu5678 chore: Vitest + TypeScript プロジェクトを初期化
```

各コミットが小さく、明確な目的を持っていることがわかります。

## 4.5 まとめ

この章では、以下の内容を学びました。

1. **Conventional Commits** — コミットメッセージの統一フォーマット
2. **コミットのタイプ** — feat、fix、refactor、test、chore などの使い分け
3. **TDD とコミットのタイミング** — Green 後と Refactor 後にコミット

次の章では、パッケージ管理ツールと静的コード解析を導入し、コードの品質を自動でチェックできるようにします。
