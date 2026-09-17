---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "前章では Conventional Commits によるコミットメッセージの規約を学びました。"
tags: [article, getting-start-tdd, java]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前章では Conventional Commits によるコミットメッセージの規約を学びました。この章では、**パッケージ管理** と **静的コード解析** を導入し、コードの品質を自動でチェックできるようにします。

## 5.2 Gradle による依存関係管理

### Gradle とは

> Gradle とは、Java で記述されたビルドツール・依存関係管理ツールです。Gradle で扱う依存関係を dependencies として管理し、プロジェクトのビルドプロセスを自動化できます。
>
> — Gradle Documentation

第 1 部で JUnit 5 と JaCoCo はすでに導入しています。ここでは、静的コード解析ツールのための依存関係を追加します。

### build.gradle の更新

品質管理ツールを追加した `build.gradle` は以下のようになります。

```groovy
plugins {
    id 'java'
    id 'jacoco'
    id 'checkstyle'
    id 'pmd'
    id 'com.github.spotbugs' version '6.0.7'
}

group = 'tdd.fizzbuzz'
version = '0.1.0-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'

    // SpotBugs 関連
    spotbugsPlugins 'com.h3xstream.findsecbugs:findsecbugs-plugin:1.12.0'
}

test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}

jacoco {
    toolVersion = '0.8.11'
}

pmd {
    ruleSetFiles = files('config/pmd/ruleset.xml')
    ruleSets = []
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = false
        csv.required = false
        html.outputLocation = layout.buildDirectory.dir('jacocoHtml')
    }
}
```

ビルドして依存関係をインストールします。

```bash
$ ./gradlew build
```

## 5.3 静的コード解析

良いコードを書き続けるためには、コードの品質を維持していく必要があります。第 1 部ではテスト駆動開発によりプログラムを動かしながら品質を改善しました。出来上がったコードに対する品質チェックの方法として **静的コード解析** があります。

### Checkstyle

[Checkstyle](https://checkstyle.sourceforge.io/) はコーディング規約のチェックツールです。

```bash
$ ./gradlew checkstyleMain
```

### PMD

[PMD](https://pmd.github.io/) は潜在的なバグやデッドコードを検出する静的解析ツールです。

```bash
$ ./gradlew pmdMain
```

### SpotBugs

[SpotBugs](https://spotbugs.github.io/) はバグパターンを検出するツールです。セキュリティ脆弱性の検出には FindSecBugs プラグインを使用します。

```bash
$ ./gradlew spotbugsMain
```

### Checkstyle 設定ファイル

プロジェクト固有のコーディング規約を定義する `config/checkstyle/checkstyle.xml` を作成します。

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
        "https://checkstyle.org/dtds/configuration_1_3.dtd">

<module name="Checker">
    <property name="charset" value="UTF-8"/>

    <!-- 抑制ルール -->
    <module name="SuppressionFilter">
        <property name="file" value="${config_loc}/suppressions.xml"/>
    </module>

    <!-- ファイルレベルのチェック -->
    <module name="FileTabCharacter">
        <property name="eachLine" value="true"/>
    </module>

    <!-- TreeWalker による AST 解析 -->
    <module name="TreeWalker">
        <!-- インデント -->
        <module name="Indentation">
            <property name="basicOffset" value="4"/>
        </module>

        <!-- 命名規則 -->
        <module name="TypeName"/>
        <module name="MethodName"/>
        <module name="LocalVariableName"/>
        <module name="MemberName"/>
        <module name="ConstantName"/>

        <!-- その他 -->
        <module name="EmptyStatement"/>
        <module name="EqualsHashCode"/>
        <module name="MagicNumber">
            <property name="ignoreHashCodeMethod" value="true"/>
            <property name="ignoreAnnotation" value="true"/>
        </module>
    </module>
</module>
```

## 5.4 マジックナンバーのリファクタリング

Checkstyle の実行で、マジックナンバーの使用が指摘されます。**定数の抽出** リファクタリングで対応しましょう。

### FizzBuzz.java のリファクタリング

3 と 5 というマジックナンバーを定数に抽出します。

#### Before

```java
public String generate(int number) {
    if (number % 3 == 0 && number % 5 == 0) {
        return "FizzBuzz";
    } else if (number % 3 == 0) {
        return "Fizz";
    } else if (number % 5 == 0) {
        return "Buzz";
    }
    return String.valueOf(number);
}
```

#### After

```java
private static final int FIZZ_NUMBER = 3;
private static final int BUZZ_NUMBER = 5;

public String generate(int number) {
    if (number % FIZZ_NUMBER == 0 && number % BUZZ_NUMBER == 0) {
        return "FizzBuzz";
    } else if (number % FIZZ_NUMBER == 0) {
        return "Fizz";
    } else if (number % BUZZ_NUMBER == 0) {
        return "Buzz";
    }
    return String.valueOf(number);
}
```

### Main.java のリファクタリング

100 というマジックナンバーも定数に抽出します。

```java
public class Main {
    private static final int MAX_NUMBER = 100;

    public static void main(String[] args) {
        FizzBuzz fizzBuzz = new FizzBuzz();
        fizzBuzz.printFizzBuzz(MAX_NUMBER);
    }
}
```

### テストコードの抑制ルール

テストコードではマジックナンバーや日本語メソッド名を許容するため、`config/checkstyle/suppressions.xml` で抑制ルールを設定します。

```xml
<suppressions>
    <suppress files=".*Test\.java$" checks="MagicNumber"/>
    <suppress files=".*Test\.java$" checks="MethodName"/>
</suppressions>
```

リファクタリング後もテストが通ることを確認します。

```
BUILD SUCCESSFUL
8 tests passed
```

## 5.5 コードフォーマット

良いコードであるためにはフォーマットも大切な要素です。

> 優れたソースコードは「目に優しい」ものでなければいけない。
>
> — リーダブルコード

Checkstyle の設定でインデントチェックを有効にしているため、フォーマットの崩れも自動で検出できます。

```bash
$ ./gradlew checkstyleMain
```

## 5.6 コードカバレッジ

静的コード解析による品質の確認はできました。動的なテストに関しては **コードカバレッジ** を確認する必要があります。

> コード網羅率（Code coverage）は、ソフトウェアテストで用いられる尺度の 1 つである。プログラムのソースコードがテストされた割合を意味する。
>
> — ウィキペディア

JaCoCo のカバレッジレポートを生成します。

```bash
$ ./gradlew jacocoTestReport
```

テスト実行後に `build/jacocoHtml` フォルダが作成されます。その中の `index.html` を開くとカバレッジ状況を確認できます。

## 5.7 コード複雑度のチェック

静的コード解析では、コーディング規約やバグパターンだけでなく、**コードの複雑度** もチェックできます。PMD のカスタムルールセットを使って、メソッドの複雑度を制限しましょう。

### 循環的複雑度（Cyclomatic Complexity）

> 循環的複雑度（サイクロマティック複雑度）とは、ソフトウェア測定法の一つであり、コードがどれぐらい複雑であるかをメソッド単位で数値にして表す指標。

本プロジェクトでは、循環的複雑度を **7 以下** に制限しています。

| 複雑度の範囲 | 意味 |
|-------------|------|
| 1〜10 | 低複雑度：管理しやすく、問題なし |
| 11〜20 | 中程度の複雑度：リファクタリングを検討 |
| 21〜50 | 高複雑度：リファクタリングが強く推奨される |
| 51 以上 | 非常に高い複雑度：コードを分割する必要がある |

### 認知的複雑度（Cognitive Complexity）

> 認知的複雑度（Cognitive Complexity）は、プログラムを読む人の認知負荷を測るための指標。コードの構造が「どれだけ頭を使う必要があるか」を定量的に評価する。循環的複雑度とは異なり、制御構造のネストやコードの流れの読みやすさに重点を置いている。

本プロジェクトでは、認知的複雑度を **4 以下** に制限しています。

| 複雑度の範囲 | 意味 |
|-------------|------|
| 0〜4 | 理解が非常に容易：リファクタリング不要 |
| 5〜14 | 中程度の難易度：改善が必要な場合もある |
| 15 以上 | 理解が困難：コードの簡素化を検討するべき |

### PMD ルールセットの設定

`config/pmd/ruleset.xml` にコード複雑度のルールを定義します。

```xml
<?xml version="1.0"?>
<ruleset name="Custom Rules"
         xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0
             https://pmd.sourceforge.io/ruleset_2_0_0.xsd">

    <description>プロジェクト用 PMD ルールセット</description>

    <!-- 基本ルール -->
    <rule ref="category/java/bestpractices.xml">
        <exclude name="JUnitTestContainsTooManyAsserts"/>
    </rule>
    <rule ref="category/java/errorprone.xml"/>
    <rule ref="category/java/codestyle.xml">
        <exclude name="AtLeastOneConstructor"/>
        <exclude name="OnlyOneReturn"/>
        <exclude name="LocalVariableCouldBeFinal"/>
        <exclude name="MethodArgumentCouldBeFinal"/>
        <exclude name="ShortVariable"/>
        <exclude name="LongVariable"/>
        <exclude name="ShortMethodName"/>
        <exclude name="ShortClassName"/>
        <exclude name="CommentDefaultAccessModifier"/>
    </rule>

    <!-- コード複雑度 -->
    <rule ref="category/java/design.xml/CyclomaticComplexity">
        <properties>
            <property name="classReportLevel" value="80"/>
            <property name="methodReportLevel" value="7"/>
        </properties>
    </rule>
    <rule ref="category/java/design.xml/CognitiveComplexity">
        <properties>
            <property name="reportLevel" value="4"/>
        </properties>
    </rule>
    <rule ref="category/java/design.xml/NPathComplexity">
        <properties>
            <property name="reportLevel" value="200"/>
        </properties>
    </rule>
</ruleset>
```

`build.gradle` に PMD のルールセットファイルを参照する設定を追加します。

```groovy
pmd {
    ruleSetFiles = files('config/pmd/ruleset.xml')
    ruleSets = []
}
```

### 複雑度チェックの実行

```bash
$ ./gradlew pmdMain
```

### 複雑度チェックの効果

コード複雑度の制限により、以下の効果が得られます。

- **可読性向上** — 小さなメソッドは理解しやすい
- **保守性向上** — 変更の影響範囲が限定される
- **テスト容易性** — 個別機能のテストが簡単
- **自動品質管理** — 複雑なコードの混入を自動防止

現在の FizzBuzz の `generate` メソッドは循環的複雑度が 4 で、制限値 7 以内に収まっています。第 3 部でオブジェクト指向設計を進める際も、この制限を意識してコードを書いていきます。

## 5.8 まとめ

この章では、パッケージ管理と静的コード解析を導入しました。

| ツール | 用途 | コマンド |
|--------|------|---------|
| Gradle | 依存関係管理・ビルド | `./gradlew build` |
| Checkstyle | コーディング規約チェック | `./gradlew checkstyleMain` |
| PMD | 静的コード解析・複雑度チェック | `./gradlew pmdMain` |
| SpotBugs | バグパターン検出 | `./gradlew spotbugsMain` |
| JaCoCo | コードカバレッジ | `./gradlew jacocoTestReport` |

次の章では、これらのタスクをまとめて実行できるタスクランナーと、自動化について解説します。
