---
type: Article
title: "第 4 章: バージョン管理と Conventional Commits"
description: "ソフトウェア開発では、変更履歴を安全に管理し、チームで追跡可能にする仕組みが重要です。"
tags: [article, getting-start-tdd, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 4 章: バージョン管理と Conventional Commits

## 4.1 はじめに

ソフトウェア開発では、変更履歴を安全に管理し、チームで追跡可能にする仕組みが重要です。
Git による版管理とコミットメッセージ規約を整えることで、TDD の小さな変更を確実に積み上げられます。
この章では、Haskell プロジェクトにおける Git の基本操作と Conventional Commits の実践方法を学びます。

## 4.2 Git によるバージョン管理

Haskell プロジェクト（`apps/haskell`）では、次の基本フローで変更を記録します。

```bash
cd apps/haskell
git init
git add .
git commit -m "chore(haskell): initialize Stack project"
```

`git init` でリポジトリを初期化し、`git add` でステージング、`git commit` で履歴を確定します。
TDD では Red → Green → Refactor の小さな単位でコミットすると、変更意図を追いやすくなります。

### 基本操作のループ

日常的な作業は次のコマンドの繰り返しです。

```bash
# 変更確認
git status

# 差分確認
git diff

# ステージング
git add src/FizzBuzz.hs test/FizzBuzz/FizzBuzzSpec.hs

# コミット
git commit -m "test(fizzbuzz): 15 の倍数に対する失敗テストを追加"

# 履歴確認
git log --oneline --decorate -n 10
```

### .gitignore の設定

`apps/haskell/.gitignore` には、ビルド成果物と中間ファイルを含めます。

```gitignore
.stack-work/
dist-newstyle/
*.cabal
*.hi
*.o
*.dyn_hi
*.dyn_o
.hpc/
.hsenv/
.HTF/
```

最低限として、次の項目は必ず除外します。

- `.stack-work/` -- Stack のビルド成果物とキャッシュ
- `dist-newstyle/` -- Cabal v2 ビルドの成果物
- `*.hi` / `*.o` -- GHC が生成するインターフェースファイルとオブジェクトファイル
- `*.dyn_hi` / `*.dyn_o` -- 動的リンク用の中間ファイル

`*.cabal` は `package.yaml` から自動生成されるため、Stack プロジェクトでは除外するのが一般的です。

## 4.3 Conventional Commits

Conventional Commits は、Angular Convention に由来するコミットメッセージ規約です。
基本フォーマットは次の通りです。

```text
<type>(<scope>): <subject>
```

- `type`: 変更種別
- `scope`: 変更対象（任意）
- `subject`: 変更内容の要約（命令形・簡潔）

### type の種類

| type | 用途 |
|------|------|
| `feat` | 新機能の追加 |
| `fix` | バグ修正 |
| `docs` | ドキュメント変更 |
| `style` | 振る舞いに影響しない整形 |
| `refactor` | 振る舞いを変えない構造改善 |
| `test` | テスト追加・修正 |
| `chore` | ビルドや設定などの雑務 |

### scope と subject の書き方

- `scope` はモジュール単位で短く書きます（例: `haskell`, `fizzbuzz`, `ci`）。
- `subject` は 50 文字前後を目安に、何をしたかを明確に書きます。
- 末尾の句点は付けません。

### コミットメッセージの例

```text
test(fizzbuzz): 15 の倍数に対する失敗テストを追加
feat(fizzbuzz): generateList で 1 から n までのリストを生成
refactor(fizzbuzz): ガード式の条件順序を整理
docs(haskell): パッケージ管理と静的解析の章を追加
chore(ci): Haskell CI ワークフローを追加
```

## 4.4 TDD でのコミット戦略

TDD の各段階でコミットすることで、変更の意図を明確に保てます。

### Red -- 失敗するテストを追加

```bash
git add test/FizzBuzz/FizzBuzzSpec.hs
git commit -m "test(fizzbuzz): generateList の失敗テストを追加"
```

テストファイルのみをコミットし、テストが失敗することを確認した状態を記録します。

### Green -- テストを通す最小実装

```bash
git add src/FizzBuzz.hs
git commit -m "feat(fizzbuzz): generateList を実装"
```

実装ファイルのみをコミットし、全テストが通過する状態を記録します。

### Refactor -- 振る舞いを変えない整理

```bash
git add src/FizzBuzz.hs test/FizzBuzz/FizzBuzzSpec.hs
git commit -m "refactor(fizzbuzz): パターンマッチの可読性を向上"
```

テストが通り続けていることを確認した上で、コードの改善をコミットします。

ポイントは、1 コミット 1 意図です。`feat` と `refactor` を同一コミットに混ぜないことで、レビューしやすくなります。

## 4.5 ブランチ戦略

### Git Flow の基本概念

Git Flow では、主に次のブランチを使います。

- `main`: リリース済みの安定コード
- `develop`: 開発統合ブランチ
- `feature/*`: 機能開発ブランチ
- `release/*`: リリース準備ブランチ
- `hotfix/*`: 緊急修正ブランチ

学習用プロジェクトでは、まず `main` と `feature/*` の運用から始めると実践しやすいです。

### feature ブランチの作成と運用

```bash
git switch main
git pull origin main
git switch -c feature/haskell-chapter-04
```

運用ポイントは次の通りです。

- 1 ブランチ 1 テーマに絞ります。
- Red / Green / Refactor の区切りで小さくコミットします。
- 完了後に Pull Request を作成し、レビュー後に `main` へマージします。

## 4.6 まとめ

この章では、Haskell の TDD 開発を支える版管理の基礎を整理しました。

- Git の基本操作で変更履歴を安全に管理する
- `.gitignore` で `.stack-work/` や `*.hi` などの不要ファイルを除外する
- Conventional Commits で履歴を読みやすくする
- TDD の Red → Green → Refactor の各段階でコミットする
- `feature` ブランチ運用で変更を分離する

次章では、Stack と HLint を中心にパッケージ管理と静的解析を整備します。
