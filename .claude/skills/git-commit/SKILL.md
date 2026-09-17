---
name: git-commit
description: Conventional Commits 準拠の Git コミットを作成。意味のある変更単位でステージングし、日本語で明確なコミットメッセージを記述する。「コミットしたい」「変更を保存したい」「コミットメッセージの書き方を知りたい」といった場面で発動する。コミット履歴を意味のある単位で保つことで、変更の追跡とロールバックを容易にする。
---

# Git コミット

意味のある変更単位ごとにコミットを行う。コミット履歴はコードの「変更理由の記録」であり、将来の自分やチームメンバーが変更を追跡するための重要な情報源になる。

## コミット手順

### 1. 変更を確認する

```bash
git status
```

### 2. 変更をステージングする

```bash
git add 対象ファイルやディレクトリ
```

意味のある変更単位ごとにファイルを指定する。無条件にすべての変更を追加しない。

### 3. コミットする

```bash
git commit -m "コミットメッセージ"
```

### 4. コミットを確認する

```bash
git log --oneline
```

## Conventional Commits フォーマット

```
<type>(<scope>): <subject>

<body>

<footer>
```

**type の種類**:

| type | 用途 |
|------|------|
| `feat` | 新機能 |
| `fix` | バグ修正 |
| `docs` | ドキュメントのみの変更 |
| `style` | コードの意味に影響しない変更（空白、フォーマット等） |
| `refactor` | バグ修正や機能追加ではないコード変更 |
| `test` | テストの追加・修正 |
| `chore` | ビルドプロセスやツールの変更 |

## Examples

```bash
git add src/features/user-auth.ts
git commit -m "feat(auth): ユーザー認証機能を追加"

git add src/utils/validation.ts
git commit -m "fix(validation): メールアドレスのバリデーションエラーを修正"

git add README.md docs/setup.md
git commit -m "docs: セットアップ手順を更新"

git add src/services/api.ts
git commit -m "refactor(api): API クライアントの共通処理を抽出"
```

## 注意事項

- コミットメッセージは日本語で記述する。co-author やメッセージに "Claude Code" のキーワードは含めない
- 1 コミット 1 目的: 構造変更と動作変更を同一コミットに含めない
- 小さく頻繁に: 大きな変更は小さなコミットに分割する
- テスト通過後にコミットする。壊れたコードをコミットしない
