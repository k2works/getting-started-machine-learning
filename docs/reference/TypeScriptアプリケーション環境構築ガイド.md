---
type: Reference
title: "TypeScript アプリケーション環境構築ガイド"
description: "本ガイドは、TypeScript アプリケーション開発環境をゼロから構築し、ソフトウェア開発の三種の神器（バージョン管理、テスティング、自動化）を実践するための手順書です。"
tags: [reference]
status: stable
generated: { by: human:kakimomokuri, at: 2026-01-17T02:53:38Z }
stale_after: 2027-02-23T00:00:00Z
---

# TypeScript アプリケーション環境構築ガイド

## 概要

本ガイドは、TypeScript アプリケーション開発環境をゼロから構築し、ソフトウェア開発の三種の神器（バージョン管理、テスティング、自動化）を実践するための手順書です。テスト駆動開発（TDD）により「動作するきれいなコード」を継続的に作成できる開発環境を整備します。

## 前提条件

- Node.js 18 以降がインストールされていること
- npm または yarn がインストールされていること
- Git がインストールされていること
- VS Code または WebStorm がインストールされていること（推奨）

## ソフトウェア開発の三種の神器

### 1. バージョン管理

#### Git の基本設定

```bash
# ユーザー設定
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

# リポジトリの初期化
git init

# .gitignore の作成
echo "node_modules/" >> .gitignore
echo "dist/" >> .gitignore
echo "coverage/" >> .gitignore
echo ".env" >> .gitignore
echo ".DS_Store" >> .gitignore
```

#### コミットメッセージ規約（Conventional Commits）

```text
<タイプ>(<スコープ>): <タイトル>
<空行>
<ボディ>
<空行>
<フッタ>
```

**タイプの種類：**

- `feat`: 新機能の追加
- `fix`: バグ修正
- `docs`: ドキュメント変更のみ
- `style`: コードに影響を与えない変更（フォーマット等）
- `refactor`: 機能追加でもバグ修正でもないコード変更
- `perf`: パフォーマンスを改善するコード変更
- `test`: テストの追加や修正
- `chore`: ビルドプロセスやツール、ライブラリの変更

**例：**
```bash
git commit -m 'feat: ユーザー認証機能を追加'
git commit -m 'fix: ログイン時のバリデーションエラーを修正'
git commit -m 'refactor: FizzBuzz クラスのメソッド抽出'
```

### 2. テスティング

#### プロジェクトの初期化

```bash
# プロジェクトの初期化
npm init -y

# TypeScript の設定
npm install -D typescript @types/node
npx tsc --init
```

#### テストフレームワークのセットアップ

```bash
# Vitest とテスト関連パッケージのインストール
npm install -D vitest @vitest/coverage-v8 @types/node
```

#### package.json の設定

```json
{
  "name": "typescript-app",
  "private": true,
  "version": "0.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "test:watch": "vitest",
    "test:coverage": "vitest run --coverage",
    "lint": "eslint . --ext .ts,.tsx",
    "lint:fix": "eslint . --ext .ts,.tsx --fix",
    "format": "prettier --write .",
    "format:check": "prettier --check .",
    "gulp": "gulp",
    "watch": "gulp watch",
    "guard": "gulp guard",
    "check": "gulp checkAndFix",
    "commit": "git add . && git commit",
    "setup": "npm install && npm run check"
  }
}
```

#### テストの基本構造

```typescript
// src/fizz-buzz.ts
export class FizzBuzz {
  private static readonly MAX_NUMBER = 100

  public static generate(number: number): string {
    const isFizz = number % 3 === 0
    const isBuzz = number % 5 === 0

    if (isFizz && isBuzz) return 'FizzBuzz'
    if (isFizz) return 'Fizz'
    if (isBuzz) return 'Buzz'

    return number.toString()
  }

  public static generateList(): string[] {
    return Array.from({ length: this.MAX_NUMBER }, (_, i) => this.generate(i + 1))
  }
}
```

```typescript
// src/fizz-buzz.test.ts
import { describe, it, expect } from 'vitest'
import { FizzBuzz } from './fizz-buzz'

describe('FizzBuzz', () => {
  describe('三の倍数の場合', () => {
    it('3を渡したら文字列Fizzを返す', () => {
      expect(FizzBuzz.generate(3)).toBe('Fizz')
    })
  })

  describe('五の倍数の場合', () => {
    it('5を渡したら文字列Buzzを返す', () => {
      expect(FizzBuzz.generate(5)).toBe('Buzz')
    })
  })

  describe('三と五の倍数の場合', () => {
    it('15を渡したら文字列FizzBuzzを返す', () => {
      expect(FizzBuzz.generate(15)).toBe('FizzBuzz')
    })
  })
})
```

#### TDD のサイクル

```bash
# Red: 失敗するテストを書く
npm run test

# Green: テストを通す最小限の実装
# コードを修正

# Refactor: リファクタリング
npm run test

# コミット
git add .
git commit -m 'feat: FizzBuzz 基本機能を実装'
```

### 3. 自動化

#### 静的コード解析（ESLint）

```bash
# ESLint のインストール
npm install -D eslint @typescript-eslint/parser @typescript-eslint/eslint-plugin eslint-config-prettier eslint-plugin-prettier
```

#### eslint.config.js の設定

```javascript
export default [
  {
    files: ['**/*.ts', '**/*.tsx'],
    languageOptions: {
      parser: '@typescript-eslint/parser',
      parserOptions: {
        ecmaVersion: 2020,
        sourceType: 'module'
      }
    },
    plugins: {
      '@typescript-eslint': require('@typescript-eslint/eslint-plugin'),
      'prettier': require('eslint-plugin-prettier')
    },
    rules: {
      // 基本的なルール
      '@typescript-eslint/no-unused-vars': 'error',
      '@typescript-eslint/no-explicit-any': 'warn',
      
      // 循環的複雑度の制限（7以下を推奨）
      'complexity': ['error', { max: 7 }],
      
      // Prettier 統合
      'prettier/prettier': 'error'
    }
  },
  {
    files: ['**/*.test.{ts,tsx}'],
    rules: {
      '@typescript-eslint/no-unused-expressions': 'off',
      '@typescript-eslint/no-unused-vars': 'warn'
    }
  }
]
```

#### コードフォーマッタ（Prettier）

```bash
# Prettier のインストール
npm install -D prettier eslint-config-prettier eslint-plugin-prettier
```

#### .prettierrc の設定

```json
{
  "semi": false,
  "singleQuote": true,
  "tabWidth": 2,
  "trailingComma": "es5",
  "printWidth": 100,
  "endOfLine": "lf"
}
```

#### コードカバレッジ

Vitest に組み込まれたカバレッジ機能を使用：

```bash
# カバレッジの実行
npm run test:coverage
```

#### vitest.config.ts の設定

```typescript
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    coverage: {
      reporter: ['text', 'html', 'json'],
      reportsDirectory: 'coverage',
      exclude: [
        'node_modules/**',
        'dist/**',
        '**/*.test.ts',
        '**/*.config.js',
        '**/*.config.ts',
        'src/main.ts'
      ],
      all: true,
      thresholds: {
        global: {
          branches: 80,
          functions: 80,
          lines: 80,
          statements: 80
        }
      }
    }
  }
})
```

#### タスクランナー（Gulp）

```bash
# Gulp のインストール
npm install -D gulp gulp-shell
```

#### gulpfile.js の設定

```javascript
import { watch, series } from 'gulp'
import shell from 'gulp-shell'

// 基本タスク
export const test = shell.task(['npm run test'])
export const coverage = shell.task(['npm run test:coverage'])
export const lint = shell.task(['npm run lint'])
export const lintFix = shell.task(['npm run lint:fix'])
export const format = shell.task(['npm run format'])
export const formatCheck = shell.task(['npm run format:check'])
export const build = shell.task(['npm run build'])
export const dev = shell.task(['npm run dev'])

// 複合タスク
export const checkAndFix = series(lintFix, format, test)

// ファイル監視タスク（Guard機能）
export function guard() {
  console.log('🔍 Guard is watching for file changes...')
  console.log('Files will be automatically linted, formatted, and tested on change.')
  watch('src/**/*.ts', series(lintFix, format, test))
  watch('**/*.test.ts', series(test))
}

// ファイル監視タスク
export function watchFiles() {
  watch('src/**/*.ts', series(formatCheck, lint, test))
  watch('**/*.test.ts', series(test))
}

// デフォルトタスク
export default series(checkAndFix, guard)

// ウォッチタスクのエイリアス
export { watchFiles as watch }
```

## 開発ワークフロー

### 1. 環境のセットアップ

```bash
# プロジェクトの初期化
npm init -y

# 依存関係のインストール
npm install

# 初期チェック実行
npm run setup
```

### 2. 開発の開始

```bash
# Guard を起動（自動テスト・リント・フォーマット）
npm run guard

# 別ターミナルで開発サーバーを起動
npm run dev
```

### 3. TDD サイクルの実践

1. **Red**: 失敗するテストを作成
   ```bash
   # テストファイル作成・編集
   # Guard が自動でテスト実行し、失敗を確認
   ```

2. **Green**: テストを通す最小限の実装
   ```bash
   # 実装コードを作成
   # Guard が自動でテスト実行し、成功を確認
   ```

3. **Refactor**: コードをリファクタリング
   ```bash
   # コードを改善
   # Guard が自動でリント・フォーマット・テスト実行
   ```

4. **Commit**: 作業をコミット
   ```bash
   git add .
   git commit -m 'feat: 新機能を実装'
   ```

### 4. 品質チェック

```bash
# 全体的な品質チェック
npm run check

# カバレッジ確認
npm run test:coverage

# 静的解析
npm run lint

# フォーマットチェック
npm run format:check
```

## ベストプラクティス

### 1. テスト戦略

- **単体テスト**: 各機能の動作を検証
- **統合テスト**: 複数のコンポーネント間の協調を検証
- **E2E テスト**: ユーザーの視点から全体的な動作を検証

### 2. コード品質

- **循環的複雑度**: 7以下を維持
- **テストカバレッジ**: 80%以上を目標
- **ESLint ルール**: プロジェクトに応じてカスタマイズ

### 3. 継続的改善

- **定期的なリファクタリング**: 技術的負債の蓄積を防ぐ
- **コードレビュー**: チーム全体のコード品質向上
- **ツールのアップデート**: セキュリティと性能の維持

## トラブルシューティング

### よくある問題と解決法

#### 1. テストが実行されない

```bash
# node_modules を再インストール
rm -rf node_modules package-lock.json
npm install

# キャッシュをクリア
npm cache clean --force
```

#### 2. ESLint エラーが発生

```bash
# 自動修正を試行
npm run lint:fix

# 設定ファイルを確認
cat eslint.config.js
```

#### 3. TypeScript コンパイルエラー

```bash
# TypeScript 設定を確認
cat tsconfig.json

# 型定義を再インストール
npm install -D @types/node
```

## 参考資料

- [Conventional Commits](https://www.conventionalcommits.org/ja/)
- [Vitest 公式ドキュメント](https://vitest.dev/)
- [ESLint 公式ドキュメント](https://eslint.org/)
- [Prettier 公式ドキュメント](https://prettier.io/)
- [TypeScript 公式ドキュメント](https://www.typescriptlang.org/)

このガイドに従って環境を構築することで、効率的で品質の高い TypeScript アプリケーション開発が可能になります。
