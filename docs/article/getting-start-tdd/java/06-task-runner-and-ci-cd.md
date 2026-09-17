---
type: Article
title: "第 6 章: タスクランナーと CI/CD"
description: "前章では静的コード解析ツールとコードカバレッジを導入しました。"
tags: [article, getting-start-tdd, java]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 6 章: タスクランナーと CI/CD

## 6.1 はじめに

前章では静的コード解析ツールとコードカバレッジを導入しました。テストの実行、静的解析、フォーマットチェック、カバレッジ計測と、様々なコマンドを使えるようになりましたが、毎回それぞれのコマンドを覚えて実行するのは面倒です。

この章では **タスクランナー** を使ってこれらのタスクをまとめて実行できるようにし、さらに **自動化** によってファイル変更時にテストが自動実行される環境を構築します。

## 6.2 タスクランナー

### タスクランナーとは

> タスクランナーとは、アプリケーションのビルドなど、一定の手順で行う作業をコマンド一つで実行できるように予めタスクとして定義したものです。
>
> — Gradle Documentation

Java のタスクランナーは Gradle です。`build.gradle` にカスタムタスクを定義することで、複数の作業をコマンド一つで実行できます。

### カスタムタスクの定義

`build.gradle` に以下のカスタムタスクを追加します。

#### TDD 用テストタスク

```groovy
task tdd(type: Test) {
    useJUnitPlatform()
    testLogging {
        events "passed", "skipped", "failed"
        exceptionFormat "full"
    }
    outputs.upToDateWhen { false }
}
```

`outputs.upToDateWhen { false }` により、コードに変更がなくても毎回テストを実行します。

#### 品質チェックタスク

```groovy
task qualityCheck {
    dependsOn 'checkstyleMain', 'checkstyleTest', 'pmdMain', 'pmdTest', 'spotbugsMain', 'spotbugsTest'
    description 'Run all quality checks'
    group 'verification'
}
```

#### 全チェックタスク

```groovy
task fullCheck {
    dependsOn 'test', 'qualityCheck', 'jacocoTestReport'
    description 'Run all tests and quality checks'
    group 'verification'
}
```

### タスクの確認と実行

登録されたタスクを確認します。

```bash
$ ./gradlew tasks --group verification

Verification tasks
------------------
check - Runs all checks.
checkstyleMain - Run Checkstyle analysis for main classes.
checkstyleTest - Run Checkstyle analysis for test classes.
fullCheck - Run all tests and quality checks
pmdMain - Run PMD analysis for main classes.
pmdTest - Run PMD analysis for test classes.
qualityCheck - Run all quality checks
spotbugsMain - Run SpotBugs analysis for main classes.
spotbugsTest - Run SpotBugs analysis for test classes.
test - Runs the unit tests.
tdd - Runs the unit tests.
```

各タスクを実行します。

```bash
# TDD 用テスト
$ ./gradlew tdd

# 品質チェック
$ ./gradlew qualityCheck

# 全チェック（テスト + 品質チェック + カバレッジ）
$ ./gradlew fullCheck
```

## 6.3 タスクの自動化

### Gradle Continuous Build

良いコードを書くためのタスクをまとめることができました。さらに自動で実行できるようにしましょう。Gradle の **Continuous Build** 機能を使うと、ファイルの変更を監視して自動的にテストを実行してくれます。

```bash
$ ./gradlew test --continuous
```

このコマンドを実行すると、ソースファイルを保存するたびにテストが自動実行されます。

### 自動テストの効果

自動テストの効果を確認するために、わざとコードを壊してみましょう。

```java
// わざとテストを失敗させる変更
if (number % FIZZ_NUMBER == 0 && number % BUZZ_NUMBER == 0) {
    return "FizzBuzzBuzz";  // わざと間違えてみる
}
```

変更を保存すると、即座にテストが実行されて失敗が検出されます。コードを元に戻すと、テストが再び通ります。

### IDE の Auto Test 機能

IntelliJ IDEA や VS Code 等の IDE の **Auto Test** 機能を使うことで、ファイル変更時に自動でテストが実行されるように設定することも可能です。

## 6.4 GitHub Actions による CI/CD

### CI/CD とは

ローカルでの自動化に加えて、リポジトリにプッシュするたびに自動でテストと品質チェックを実行する仕組みが **CI/CD（継続的インテグレーション / 継続的デリバリー）** です。

> 継続的インテグレーション（CI）は、チームメンバーが頻繁にコードを統合するプラクティスです。各統合はビルドとテストの自動化によって検証されます。

GitHub が提供する **GitHub Actions** を使って CI パイプラインを構築しましょう。

### ワークフローファイルの作成

`.github/workflows/java-ci.yml` を作成します。

```yaml
name: Java CI

on:
  push:
    branches: [main, develop]
    paths:
      - 'apps/java/**'
      - 'ops/nix/environments/java/**'
      - 'flake.nix'
      - 'flake.lock'
  pull_request:
    branches: [main]
    paths:
      - 'apps/java/**'
      - 'ops/nix/environments/java/**'
      - 'flake.nix'
      - 'flake.lock'

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout the repository
        uses: actions/checkout@v4

      - name: Install Nix
        uses: cachix/install-nix-action@v30
        with:
          nix_path: nixpkgs=channel:nixos-unstable

      - name: Cache Nix store
        uses: actions/cache@v4
        with:
          path: /tmp/nix-cache
          key: ${{ runner.os }}-nix-java-${{ hashFiles('flake.lock', 'ops/nix/environments/java/shell.nix') }}
          restore-keys: |
            ${{ runner.os }}-nix-java-

      - name: Run tests
        run: nix develop .#java --command bash -c "cd apps/java && ./gradlew test"

      - name: Run quality checks
        run: nix develop .#java --command bash -c "cd apps/java && ./gradlew qualityCheck"

      - name: Generate coverage report
        run: nix develop .#java --command bash -c "cd apps/java && ./gradlew jacocoTestReport"

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: apps/java/build/reports/tests/

      - name: Upload coverage report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: coverage-report
          path: apps/java/build/jacocoHtml/
```

### ワークフローの解説

| 設定項目 | 内容 |
|---------|------|
| トリガー | `main`/`develop` ブランチへの push、`main` への PR |
| パスフィルター | `apps/java/**` および Nix 環境定義の変更時に実行 |
| Nix | `cachix/install-nix-action` でインストール |
| 環境 | `nix develop .#java` でローカルと同一の JDK・Gradle を使用 |
| テスト | `./gradlew test` でユニットテスト実行 |
| 品質チェック | `./gradlew qualityCheck` で静的解析実行 |
| カバレッジ | `./gradlew jacocoTestReport` でレポート生成 |
| アーティファクト | テスト結果とカバレッジレポートを保存 |

Nix を使う最大のメリットは、**ローカル開発環境と CI 環境が完全に同一になる** ことです。JDK や Gradle のバージョン差異による「ローカルでは通るが CI では失敗する」問題を根本的に防げます。

### CI パイプラインの流れ

```
git push
  → GitHub Actions トリガー
    → Nix インストール
      → nix develop .#java（JDK 21 + Gradle 環境）
        → テスト実行
          → 品質チェック（Checkstyle + PMD + SpotBugs）
            → カバレッジレポート生成
              → アーティファクトアップロード
```

これにより、ローカルでのテストを忘れた場合でも、プッシュ時に自動で品質チェックが実行されます。プルリクエストでは全チェックが通らないとマージできないようにすることも可能です。

## 6.5 ソフトウェア開発の三種の神器と CI/CD

ここまでで、ソフトウェア開発の三種の神器がすべて揃いました。

| 神器 | ツール | 用途 |
|------|--------|------|
| バージョン管理 | Git + Conventional Commits | 変更履歴の管理 |
| テスティング | JUnit 5 + JaCoCo | テスト駆動開発 + カバレッジ |
| 自動化 | Gradle タスクランナー + GitHub Actions | 品質チェックの自動実行 + CI/CD |

これらのツールにより、**動作するきれいなコード** を継続的に書き続けることができる環境が整いました。

```
./gradlew test --continuous
```

次の開発からはこのコマンドを最初に実行すれば、コードを書くことに集中できるようになります。

## 6.6 まとめ

第 2 部（第 4〜6 章）を通じて、ソフトウェア開発の三種の神器を整備しました。

| 章 | テーマ | 学んだこと |
|----|--------|-----------|
| 4 | バージョン管理 | Conventional Commits、コミットタイプ |
| 5 | パッケージ管理と静的解析 | Checkstyle、PMD、SpotBugs、JaCoCo |
| 6 | タスクランナーと CI/CD | カスタムタスク、Continuous Build、GitHub Actions |

### TDD 開発ワークフロー

```
1. ./gradlew test --continuous  ← 最初に実行
2. テストを書く（Red）
3. 実装する（Green）
4. リファクタリングする（Refactor）
5. git commit -m 'feat: ...'  ← Conventional Commits
6. 2 に戻る
```

次の第 3 部では、FizzBuzz に追加仕様を導入し、オブジェクト指向設計のテクニックを学びます。
