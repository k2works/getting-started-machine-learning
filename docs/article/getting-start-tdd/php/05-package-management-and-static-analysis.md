---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "前章では Conventional Commits によるコミットメッセージの規約を学びました。"
tags: [article, getting-start-tdd, php]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前章では Conventional Commits によるコミットメッセージの規約を学びました。この章では、**パッケージ管理** と **静的コード解析** を導入し、コードの品質を自動でチェックできるようにします。

## 5.2 Composer によるパッケージ管理

### Composer とは

> Composer は PHP で記述されたサードパーティ製のライブラリを管理するためのツールで、Composer で扱うライブラリをパッケージと呼びます。
>
> — PHP における Composer

Java の Gradle、Node の npm（package.json）、Python の uv、Ruby の Bundler（Gemfile）、Go の Go Modules に相当するのが Composer です。

### composer.json の構成

本プロジェクトの `composer.json` は以下のようになっています。

```json
{
    "name": "fizzbuzz/php-tdd",
    "description": "FizzBuzz implementation using TDD in PHP",
    "type": "project",
    "require": {
        "php": "^8.1"
    },
    "require-dev": {
        "phpunit/phpunit": "^10.0"
    },
    "autoload": {
        "psr-4": {
            "App\\": "src/"
        }
    },
    "autoload-dev": {
        "psr-4": {
            "App\\Tests\\": "tests/"
        }
    }
}
```

### PSR-4 オートロード

PHP では `require` や `include` でファイルを読み込む代わりに、**PSR-4 オートロード** を使用します。名前空間とディレクトリ構造を対応させることで、クラスを自動的に読み込めます。

| 名前空間 | ディレクトリ | 用途 |
|----------|-------------|------|
| `App\` | `src/` | プロダクションコード |
| `App\Tests\` | `tests/` | テストコード |

例えば `App\FizzBuzz` クラスは `src/FizzBuzz.php` に、`App\Tests\FizzBuzzTest` は `tests/FizzBuzzTest.php` に配置されます。

### 主要なコマンド

| コマンド | 説明 |
|---------|------|
| `composer init` | プロジェクトを初期化し `composer.json` を作成 |
| `composer install` | `composer.lock` に基づいて依存パッケージをインストール |
| `composer update` | 依存パッケージを最新バージョンに更新 |
| `composer require <package>` | パッケージを追加 |
| `composer require --dev <package>` | 開発用パッケージを追加 |
| `composer dump-autoload` | オートローダーを再生成 |

### 開発用パッケージの追加

これから静的解析ツールを追加していきます。以下のコマンドで開発用パッケージを追加します。

```bash
# PHP_CodeSniffer（コーディング規約チェック）
$ composer require --dev squizlabs/php_codesniffer

# PHPStan（静的解析）
$ composer require --dev phpstan/phpstan
```

追加後の `composer.json` は以下のようになります。

```json
{
    "require-dev": {
        "phpunit/phpunit": "^10.0",
        "squizlabs/php_codesniffer": "^3.0",
        "phpstan/phpstan": "^2.0"
    }
}
```

### Composer の特徴

Composer には他の言語のパッケージマネージャと共通する重要な特徴があります。

- **`composer.lock` による再現性** — 依存パッケージのバージョンを固定し、チーム全員が同じ環境で開発できる
- **PSR-4 オートロード** — 名前空間とディレクトリの対応で自動クラスロード
- **`vendor/` ディレクトリ** — Node の `node_modules/`、Python の `.venv/` に相当（`.gitignore` に追加）

## 5.3 PHP_CodeSniffer（phpcs）による静的解析

### PHP_CodeSniffer とは

> PHP_CodeSniffer は PHP のコードがコーディング規約に準拠しているかをチェックし、自動修正する機能を持つツールです。

Ruby の RuboCop、Java の Checkstyle、TypeScript の ESLint、Python の Ruff、Go の golangci-lint に相当するツールです。

### 設定ファイル（phpcs.xml）

プロジェクトルートに `phpcs.xml` を作成します。

```xml
<?xml version="1.0"?>
<ruleset name="FizzBuzz PHP Coding Standard">
    <description>PSR-12 based coding standard for FizzBuzz project</description>

    <!-- チェック対象 -->
    <file>src</file>
    <file>tests</file>

    <!-- PSR-12 コーディング規約をベースに -->
    <rule ref="PSR12"/>

    <!-- 除外パターン -->
    <exclude-pattern>vendor/*</exclude-pattern>
</ruleset>
```

### 実行してみる

```bash
# コーディング規約チェック
$ vendor/bin/phpcs
..........

# 自動修正
$ vendor/bin/phpcbf
```

`phpcs` がチェック、`phpcbf` が自動修正を行います。PSR-12 に準拠したコードであれば、エラーは報告されません。

### コードのフォーマットを崩してみる

PHP_CodeSniffer の動作を確認するために、わざとフォーマットを崩してみましょう。

```php
<?php
declare(strict_types=1);
namespace App;

final class FizzBuzz
{
    public function generate(int $number): string
    {
            if ($number % 15 === 0) {    // インデントが崩れている
            return 'FizzBuzz';
        }
        return (string) $number;
    }
}
```

```bash
$ vendor/bin/phpcs
FILE: src/FizzBuzz.php
----------------------------------------------------------------------
FOUND 1 ERROR AFFECTING 1 LINE
----------------------------------------------------------------------
 9 | ERROR | [x] Line indented incorrectly; expected 8 spaces, found 12
----------------------------------------------------------------------
```

エラーが検出されました。`phpcbf` で自動修正できます。

```bash
$ vendor/bin/phpcbf
PHPCBF RESULT SUMMARY
----------------------------------------------------------------------
FILE                          FIXED  REMAINING
----------------------------------------------------------------------
src/FizzBuzz.php              1      0
----------------------------------------------------------------------
```

## 5.4 PHPStan による静的型解析

### PHPStan とは

> PHPStan は PHP のコードを実行せずに型エラーやバグを検出する静的解析ツールです。PHP は動的型付け言語ですが、PHPStan を使うことで TypeScript の `tsc` や Python の `mypy`、Go のコンパイラに近い型安全性を得られます。

### 設定ファイル（phpstan.neon）

プロジェクトルートに `phpstan.neon` を作成します。

```yaml
parameters:
    level: max
    paths:
        - src
```

### 解析レベル

PHPStan は 0〜9 のレベルと `max`（最高レベル）で解析の厳しさを段階的に設定できます。

| レベル | チェック内容 |
|--------|-------------|
| 0 | 基本的なチェック（未定義変数、未定義メソッド） |
| 1 | 未知のクラス、関数のチェック |
| 2-4 | PHPDoc の整合性、型の一致 |
| 5-6 | メソッドの戻り値型、引数型のチェック |
| 7-8 | Union 型、共変/反変のチェック |
| 9/max | 最も厳密なチェック（mixed 型の禁止など） |

本プロジェクトでは `level: max` を使用して最高レベルの型安全性を目指します。

### 実行してみる

```bash
$ vendor/bin/phpstan analyse
 2/2 [▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓] 100%

 [OK] No errors
```

型宣言（`int`、`string`、`array`、`void`）を適切に使用しているため、エラーは検出されません。

### PHPStan と PHP の型宣言

PHP 7.0 以降、関数やメソッドに型宣言を追加できます。第 1 部で書いたコードでは既に型宣言を使用しています。

```php
<?php
declare(strict_types=1);

final class FizzBuzz
{
    // 引数型: int, 戻り値型: string
    public function generate(int $number): string { ... }

    // 戻り値型: array, PHPDoc で要素型を指定
    /** @return string[] */
    public function generateList(): array { ... }

    // 戻り値型: void（戻り値なし）
    public function printFizzBuzz(): void { ... }
}
```

`declare(strict_types=1)` を宣言することで、PHP の型チェックが厳密モードになります。例えば `generate('3')` のように文字列を渡すと、暗黙の型変換ではなく `TypeError` が発生します。

## 5.5 PHPMD によるコード複雑性チェック

### PHPMD とは

> PHPMD（PHP Mess Detector）は PHP コードの潜在的な問題を検出するツールです。循環的複雑度、メソッドの長さ、パラメータ数などのメトリクスに基づいて、リファクタリングが必要な箇所を指摘します。

Java の PMD、Python の Ruff（McCabe 複雑度）、TypeScript の ESLint（`complexity` ルール）、Ruby の RuboCop（`Metrics/CyclomaticComplexity`）に相当するツールです。

### インストール

```bash
$ composer require --dev phpmd/phpmd
```

### 循環的複雑度

> 循環的複雑度（サイクロマティック複雑度）とは、コードがどれぐらい複雑であるかをメソッド単位で数値にして表す指標です。

| 複雑度の範囲 | 意味 |
|-------------|------|
| 1〜10 | 低複雑度: 管理しやすく、問題なし |
| 11〜20 | 中程度の複雑度: リファクタリングを検討 |
| 21〜50 | 高複雑度: リファクタリングが強く推奨される |
| 51 以上 | 非常に高い複雑度: コードを分割する必要がある |

### 設定ファイル（phpmd.xml）

プロジェクトルートに `phpmd.xml` を作成します。

```xml
<?xml version="1.0"?>
<ruleset name="FizzBuzz PHPMD Rules"
         xmlns="http://pmd.sf.net/ruleset/1.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://pmd.sf.net/ruleset/1.0.0
                             http://pmd.sf.net/ruleset_xml_schema.xsd"
         xsi:noNamespaceSchemaLocation="http://pmd.sf.net/ruleset_xml_schema.xsd">
    <description>FizzBuzz project PHPMD rules</description>

    <!-- 循環的複雑度: 7 以下に制限 -->
    <rule ref="rulesets/codesize.xml/CyclomaticComplexity">
        <properties>
            <property name="reportLevel" value="7" />
        </properties>
    </rule>

    <!-- NPath 複雑度 -->
    <rule ref="rulesets/codesize.xml/NPathComplexity" />

    <!-- メソッドの行数: 30 行以下 -->
    <rule ref="rulesets/codesize.xml/ExcessiveMethodLength">
        <properties>
            <property name="maximum" value="30" />
        </properties>
    </rule>

    <!-- パラメータ数: 5 以下 -->
    <rule ref="rulesets/codesize.xml/ExcessiveParameterList">
        <properties>
            <property name="maximum" value="5" />
        </properties>
    </rule>

    <!-- 未使用のプライベートフィールド -->
    <rule ref="rulesets/unusedcode.xml" />
</ruleset>
```

`reportLevel: 7` は他言語（Python の `max-complexity = 7`、TypeScript の `complexity: ["error", { max: 7 }]`）と同じ基準です。

### PHPMD のルールセット

| ルールセット | チェック内容 |
|-------------|------------|
| `codesize.xml` | 循環的複雑度、NPath 複雑度、メソッド長、パラメータ数 |
| `unusedcode.xml` | 未使用の変数、パラメータ、プライベートフィールド |
| `naming.xml` | 変数名・メソッド名の長さ |
| `design.xml` | 結合度、深いネスト、goto 文の使用 |
| `cleancode.xml` | 静的アクセス、else 式、boolean 引数 |

### 実行してみる

```bash
$ vendor/bin/phpmd src text phpmd.xml
```

エラーが報告されなければ、すべてのメソッドの複雑度が基準値以下です。現在の `FizzBuzzType01::generate()` メソッドの循環的複雑度は **4** で、基準値 7 を十分に下回っています。

### Composer scripts への追加

```json
{
    "scripts": {
        "complexity": "vendor/bin/phpmd src text phpmd.xml",
        "check": ["@lint", "@analyse", "@complexity", "@test"]
    }
}
```

`composer check` で lint、静的解析、複雑度チェック、テストをまとめて実行できます。

### 他言語との比較

| 言語 | 複雑度チェックツール | 設定 |
|------|---------------------|------|
| PHP | PHPMD | `reportLevel: 7` |
| Java | PMD | `CyclomaticComplexity` |
| Python | Ruff（McCabe） | `max-complexity = 7` |
| TypeScript | ESLint | `complexity: ["error", { max: 7 }]` |
| Ruby | RuboCop | `Metrics/CyclomaticComplexity` |
| Go | golangci-lint（gocyclo） | デフォルト設定 |

## 5.6 コードカバレッジ

### PHPUnit のカバレッジ機能

静的コード解析による品質の確認ができました。動的なテストに関しても **コードカバレッジ** を確認しましょう。

> コード網羅率（Code coverage）は、ソフトウェアテストで用いられる尺度の 1 つである。プログラムのソースコードがテストされた割合を意味する。
>
> — ウィキペディア

PHPUnit には内蔵のカバレッジ機能があります。

```bash
# テキスト形式でカバレッジ表示
$ vendor/bin/phpunit --coverage-text

# HTML 形式でカバレッジ出力
$ vendor/bin/phpunit --coverage-html coverage
```

!!! note "カバレッジに必要な PHP 拡張"
    PHPUnit のカバレッジ機能を使うには、`xdebug` または `pcov` 拡張が必要です。Nix 環境では `nix develop .#php` で利用可能です。

### カバレッジ結果

```
Code Coverage Report:
  Classes: 100.00% (1/1)
  Methods: 100.00% (3/3)
  Lines:   100.00% (8/8)
```

第 1 部で TDD を実践しているため、テストカバレッジは 100% を達成しています。TDD で開発するとテストが先に書かれるため、自然と高いカバレッジが得られます。

## 5.7 まとめ

この章では以下を導入しました。

| ツール | 役割 | 他言語の対応ツール |
|--------|------|-------------------|
| Composer | パッケージ管理 | npm, Bundler, Go Modules, Gradle |
| PHP_CodeSniffer（phpcs） | コーディング規約チェック | ESLint, RuboCop, golangci-lint |
| PHP_CodeSniffer（phpcbf） | コード自動修正 | Prettier, RuboCop --auto-correct, gofmt |
| PHPStan | 静的型解析 | tsc, mypy, go vet |
| PHPMD | コード複雑性チェック | PMD, Ruff McCabe, ESLint complexity |
| PHPUnit --coverage | カバレッジ計測 | c8, SimpleCov, go test -cover |

次章では、これらのツールを **タスクランナー**（Composer scripts）でまとめて実行できるようにし、**CI/CD** パイプラインを構築します。
