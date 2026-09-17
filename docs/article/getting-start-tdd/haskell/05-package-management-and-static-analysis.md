---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "Haskell 開発では、依存関係の管理とコード品質の維持を仕組み化することが重要です。"
tags: [article, getting-start-tdd, haskell]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

Haskell 開発では、依存関係の管理とコード品質の維持を仕組み化することが重要です。
この章では、Stack と `package.yaml` によるパッケージ管理、HLint による静的解析、GHC コンパイラオプションによる品質管理を扱います。

## 5.2 Stack によるパッケージ管理

### Stack の役割

Stack は Haskell のビルドツール兼パッケージマネージャです。主に次の機能を提供します。

- プロジェクトのビルドとテスト実行
- 依存パッケージの解決とインストール
- GHC コンパイラのバージョン管理
- Stackage スナップショットによる依存関係の一貫性保証

### package.yaml の構成

`apps/haskell/package.yaml` では、プロジェクトの基本情報と依存関係を定義します。

```yaml
name:                fizzbuzz
version:             0.1.0.0
synopsis:            FizzBuzz TDD implementation in Haskell
description:         A FizzBuzz implementation using Test-Driven Development
license:             BSD-3-Clause
author:              k2works
maintainer:          k2works@example.com

dependencies:
  - base >= 4.7 && < 5

ghc-options:
  - -Wall
  - -Wcompat
  - -Widentities
  - -Wincomplete-record-updates
  - -Wincomplete-uni-patterns
  - -Wmissing-export-lists
  - -Wmissing-home-modules
  - -Wpartial-fields
  - -Wredundant-constraints

library:
  source-dirs: src
  exposed-modules:
    - FizzBuzz

executables:
  fizzbuzz:
    main: Main.hs
    source-dirs: app
    dependencies:
      - fizzbuzz

tests:
  fizzbuzz-test:
    main: Spec.hs
    source-dirs: test
    dependencies:
      - fizzbuzz
      - hspec
    build-tools:
      - hspec-discover
```

各セクションの役割は次の通りです。

| セクション | 説明 |
|-----------|------|
| `name` / `version` | パッケージ名とバージョン |
| `dependencies` | 全体で共通の依存パッケージ |
| `ghc-options` | コンパイラに渡す警告オプション |
| `library` | ライブラリとして公開するモジュール |
| `executables` | 実行可能ファイルの定義 |
| `tests` | テストスイートの定義 |

`package.yaml` は hpack 形式で、Stack がビルド時に自動的に `.cabal` ファイルへ変換します。そのため `.cabal` ファイルは `.gitignore` に含めています。

### stack.yaml と LTS リゾルバ

`apps/haskell/stack.yaml` では、使用する Stackage スナップショットを指定します。

```yaml
snapshot: lts-23.20

packages:
- .
```

- `snapshot`: Stackage LTS（Long Term Support）のバージョンを指定します。LTS は GHC バージョンと互換性のあるパッケージ群を一式まとめたものです。
- `packages`: ビルド対象のパッケージディレクトリを指定します。

LTS を固定することで、チーム全員が同じ GHC バージョンと同じパッケージバージョンを使えます。`lts-23.20` は GHC 9.8.4 に対応しています。

### 依存関係の追加方法

テストフレームワーク HSpec を例に、依存関係の追加手順を示します。

**1. package.yaml に依存を追加**

`tests` セクションの `dependencies` に `hspec` を追加します。

```yaml
tests:
  fizzbuzz-test:
    main: Spec.hs
    source-dirs: test
    dependencies:
      - fizzbuzz
      - hspec
    build-tools:
      - hspec-discover
```

`hspec-discover` は `build-tools` に指定します。これにより、テストファイルの自動検出が有効になります。

**2. テストのエントリポイントを設定**

`test/Spec.hs` に hspec-discover のプラグマを記述します。

```haskell
{-# OPTIONS_GHC -F -pgmF hspec-discover #-}
```

この 1 行で、`test/` 配下の `*Spec.hs` ファイルが自動的にテストスイートに組み込まれます。

**3. 依存解決とビルド**

```bash
stack build --test --no-run-tests
```

Stack は `stack.yaml` で指定した LTS スナップショットから `hspec` を解決し、ダウンロードします。LTS に含まれるパッケージは `extra-deps` への追記が不要です。

## 5.3 HLint による静的解析

HLint は Haskell の静的解析ツールで、コードの改善提案を行います。

### 基本的な使い方

```bash
hlint src/ test/ app/
```

このコマンドで `src/`、`test/`、`app/` 配下の `.hs` ファイルを解析します。

### 検出される問題の例

HLint は次のような改善を提案します。

| カテゴリ | 元のコード | 提案 |
|---------|-----------|------|
| 冗長な括弧 | `(f x)` | `f x` |
| リスト操作 | `head xs` | パターンマッチ `(x:_)` |
| 関数合成 | `f (g x)` | `(f . g) x` |
| eta 簡約 | `\x -> f x` | `f` |
| 不要な `do` | `do { return x }` | `return x` |

実行結果の例:

```
src/FizzBuzz.hs:8:3: Suggestion: Use otherwise
Found:
  generate n | True = show n
Perhaps:
  generate n | otherwise = show n

No hints
```

`No hints` と表示されれば、HLint が検出する問題はありません。

### .hlint.yaml による設定カスタマイズ

`apps/haskell/.hlint.yaml` でルールのカスタマイズが可能です。

```yaml
# HLint configuration
# https://github.com/ndmitchell/hlint

- ignore: {name: "Redundant bracket"}
- ignore: {name: "Use head", within: ["FizzBuzz.FizzBuzzSpec"]}
```

主な設定項目は次の通りです。

| 設定 | 説明 |
|------|------|
| `ignore` | 特定のルールを無効化 |
| `warn` | 特定のルールを警告として追加 |
| `error` | 特定のルールをエラーとして追加 |
| `within` | 適用範囲をモジュール単位で限定 |

テストコードでは `!!` 演算子（リストのインデックスアクセス）を使うことがあるため、`Use head` ルールをテストモジュール内で無効化しています。

## 5.4 コード複雑度チェック

コードの循環複雑度（Cyclomatic Complexity）は、関数内の分岐数に基づいてコードの複雑さを数値化する指標です。複雑度が高い関数はテストが困難で、バグが混入しやすくなります。

### 循環複雑度の計算ルール

| ルール | 説明 |
|--------|------|
| 基本複雑度 | 各関数の初期値は 1 |
| ガード式 `\|` | 各ガードごとに +1 |
| `if` 式 | 条件分岐ごとに +1 |
| `case ... of` | case 式の出現ごとに +1 |
| パターンマッチ分岐 | コンストラクタパターンごとに +1 |

一般的な目安として、1 関数あたり複雑度 **10 以下** が推奨されます。

### カスタム複雑度チェッカー

Haskell には標準的な複雑度チェックツールがないため、`scripts/complexity.sh` にカスタムスクリプトを用意しています。

```bash
# 実行（デフォルト閾値: 10）
bash scripts/complexity.sh --threshold 10 src

# 閾値を指定して実行
bash scripts/complexity.sh --threshold 7 src
```

実行結果の例:

```
Checking Haskell complexity (threshold: 10)...

Total functions checked: 2
Violations: 0
PASSED: All functions within complexity threshold
```

### 複雑度が高い場合の対処

複雑度が閾値を超えた場合は、以下のリファクタリングを検討します。

1. **関数分割**: 長いガード式を小さな関数に分解
2. **パターンマッチの活用**: 条件分岐を代数的データ型のパターンマッチに置き換え
3. **高階関数の利用**: `if` / ループ的な再帰をコレクション操作（`map` / `filter`）に置き換え
4. **ガード条件の抽出**: 複雑な条件を `where` 節の補助関数として抽出

## 5.5 GHC コンパイラオプションによる品質管理

`package.yaml` の `ghc-options` セクションで、コンパイル時の警告を設定しています。

```yaml
ghc-options:
  - -Wall
  - -Wcompat
  - -Widentities
  - -Wincomplete-record-updates
  - -Wincomplete-uni-patterns
  - -Wmissing-export-lists
  - -Wmissing-home-modules
  - -Wpartial-fields
  - -Wredundant-constraints
```

各オプションの役割は次の通りです。

| オプション | 説明 |
|-----------|------|
| `-Wall` | 一般的な警告をすべて有効化 |
| `-Wcompat` | 将来の GHC バージョンとの互換性警告 |
| `-Widentities` | 不要な型変換の警告 |
| `-Wincomplete-record-updates` | レコード更新の網羅性チェック |
| `-Wincomplete-uni-patterns` | パターンマッチの網羅性チェック |
| `-Wmissing-export-lists` | エクスポートリスト未指定の警告 |
| `-Wmissing-home-modules` | モジュール依存の整合性チェック |
| `-Wpartial-fields` | 部分的なレコードフィールドの警告 |
| `-Wredundant-constraints` | 不要な型制約の警告 |

特に `-Wall` は Haskell 開発の基本です。型シグネチャの欠落、未使用インポート、パターンマッチの非網羅性など、多くの潜在的問題を検出します。

### 型シグネチャの重要性

`-Wall` を有効にすると、トップレベル関数に型シグネチャがない場合に警告が出ます。

```haskell
-- 型シグネチャなし（警告が出る）
generate n
  | n `mod` 15 == 0 = "FizzBuzz"
  | n `mod` 3 == 0  = "Fizz"
  | n `mod` 5 == 0  = "Buzz"
  | otherwise        = show n

-- 型シグネチャあり（推奨）
generate :: Int -> String
generate n
  | n `mod` 15 == 0 = "FizzBuzz"
  | n `mod` 3 == 0  = "Fizz"
  | n `mod` 5 == 0  = "Buzz"
  | otherwise        = show n
```

型シグネチャを明示することで、意図しない型推論を防ぎ、コードの可読性が向上します。

## 5.6 まとめ

この章では、Haskell プロジェクトの品質基盤を整えました。

- Stack と `package.yaml` で依存関係を宣言的に管理する
- `stack.yaml` の LTS リゾルバでパッケージバージョンを統一する
- HLint で Haskell 固有のイディオムに沿った改善提案を受ける
- `.hlint.yaml` でプロジェクト固有のルールをカスタマイズする
- カスタムスクリプトでコード複雑度をチェックする
- GHC の `-Wall` オプションで型安全性と網羅性を強制する

次章では、これらを Makefile と GitHub Actions で自動実行する方法を扱います。
