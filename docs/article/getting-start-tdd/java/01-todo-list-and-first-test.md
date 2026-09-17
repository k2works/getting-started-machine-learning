---
type: Article
title: "第 1 章: TODO リストと最初のテスト"
description: "プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して TODO リスト を作るところから始めます。"
tags: [article, getting-start-tdd, java]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 1 章: TODO リストと最初のテスト

## 1.1 はじめに

プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して **TODO リスト** を作るところから始めます。

> TODO リスト
>
> 何をテストすべきだろうか——着手する前に、必要になりそうなテストをリストに書き出しておこう。
>
> — テスト駆動開発

## 1.2 仕様の確認

今回取り組む FizzBuzz 問題の仕様は以下の通りです。

```
1 から 100 までの数をプリントするプログラムを書け。
ただし 3 の倍数のときは数の代わりに「Fizz」と、5 の倍数のときは「Buzz」とプリントし、
3 と 5 両方の倍数の場合には「FizzBuzz」とプリントすること。
```

この仕様をそのままプログラムに落とし込むには少しサイズが大きいですね。最初の作業は仕様を **TODO リスト** に分解する作業から着手しましょう。

## 1.3 TODO リストの作成

仕様を分解して TODO リストを作成します。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [ ] 1 を渡したら文字列 "1" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

まず「1 を渡したら文字列 "1" を返す」という、最も小さなタスクから取り掛かります。

## 1.4 テスティングフレームワークの導入

### テストファースト

最初にプログラムする対象を決めたので、早速プロダクトコードを実装……ではなく **テストファースト** で作業を進めましょう。

> テストファースト
>
> いつテストを書くべきだろうか——それはテスト対象のコードを書く前だ。
>
> — テスト駆動開発

今回 Java のテスティングフレームワークには [JUnit 5](https://junit.org/junit5/) を利用します。

### 開発環境のセットアップ

Gradle プロジェクトに JUnit 5 を追加して、テスト環境をセットアップします。

```groovy
// build.gradle
plugins {
    id 'java'
    id 'jacoco'
}

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
}

test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}
```

### 環境確認テスト

環境が正しく設定されていることを確認するため、簡単なテストを書きます。

```java
package tdd.fizzbuzz;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HelloTest {
    @Test
    void test_greeting() {
        assertEquals("hello world", greeting());
    }

    private String greeting() {
        return "hello world";
    }
}
```

テストを実行します。

```bash
$ ./gradlew test
```

```
HelloTest > test_greeting() PASSED

BUILD SUCCESSFUL
```

テストが成功すれば、開発環境のセットアップは完了です。

## 1.5 最初のテストを書く

### アサートファースト

TODO リストの最初の項目「1 を渡したら文字列 "1" を返す」に取り掛かります。

> アサートファースト
>
> いつアサーションを書くべきだろうか——最初に書こう。
>
> — テスト駆動開発

テストコードを日本語で記述します。開発体制にもよりますが、日本人が開発するのであればドキュメントとしての可読性が上がるため、テストコードであれば日本語のメソッド名も有効です。

```java
package tdd.fizzbuzz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FizzBuzzTest {

    private FizzBuzz fizzbuzz;

    @BeforeEach
    void setUp() {
        fizzbuzz = new FizzBuzz();
    }

    @Test
    void test_1を渡したら文字列1を返す() {
        assertEquals("1", fizzbuzz.generate(1));
    }
}
```

### Red: テストを実行して失敗させる

テストを実行します。

```bash
$ ./gradlew test
```

```
FizzBuzzTest > test_1を渡したら文字列1を返す() FAILED
    java.lang.NoClassDefFoundError: tdd/fizzbuzz/FizzBuzz
```

`FizzBuzz` クラスが定義されていないというエラーが出ました。まだ作っていないのですから当然です。

### Green: 仮実装でテストを通す

最初のテストを通すために **仮実装** から始めましょう。

> 仮実装を経て本実装へ
>
> 失敗するテストを書いてから、最初に行う実装はどのようなものだろうか——ベタ書きの値を返そう。
>
> — テスト駆動開発

`FizzBuzz` クラスを定義して、文字列リテラルを返す `generate` メソッドを作成します。

```java
package tdd.fizzbuzz;

public class FizzBuzz {

    public String generate(int number) {
        return "1";
    }
}
```

テストを実行します。

```bash
$ ./gradlew test
```

```
FizzBuzzTest > test_1を渡したら文字列1を返す() PASSED

BUILD SUCCESSFUL
```

テストが通りました！「え？こんなベタ書きのプログラムでいいの？」と思われるかもしれませんが、この細かいステップに今しばらくお付き合いください。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 1.6 まとめ

この章では、TDD の最も基本的なステップを体験しました。

1. **TODO リストの作成** — 仕様を小さなタスクに分解する
2. **テストファースト** — テスト対象のコードを書く前にテストを書く
3. **アサートファースト** — テストの終わりにパスすべきアサーションを最初に書く
4. **仮実装** — 失敗するテストを通すために、ベタ書きの値を返す

次の章では、三角測量によってプログラムを一般化していきます。
