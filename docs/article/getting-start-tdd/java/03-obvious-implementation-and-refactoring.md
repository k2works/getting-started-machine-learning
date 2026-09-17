---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。"
tags: [article, getting-start-tdd, java]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 3 章: 明白な実装とリファクタリング

## 3.1 はじめに

前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。この章では、残りの TODO（リスト生成とプリント）を実装し、学習用テストを活用しながら「動作するきれいなコード」を目指してリファクタリングします。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 3.2 1 から 100 までのリスト生成

### Red: リスト生成のテスト

1 から 100 までの FizzBuzz の結果をリストとして返すメソッドをテストします。

```java
@Test
void test_1から100までのFizzBuzzを生成する() {
    List<String> result = fizzbuzz.generateList(100);

    assertEquals(100, result.size());
    assertEquals("1", result.get(0));
    assertEquals("2", result.get(1));
    assertEquals("Fizz", result.get(2));
    assertEquals("4", result.get(3));
    assertEquals("Buzz", result.get(4));
    assertEquals("Fizz", result.get(5));
    assertEquals("FizzBuzz", result.get(14));
    assertEquals("Buzz", result.get(99));
}
```

```
FizzBuzzTest > test_1から100までのFizzBuzzを生成する() FAILED
    java.lang.NoSuchMethodError: tdd.fizzbuzz.FizzBuzz.generateList(int)
```

### Green: 明白な実装

```java
package tdd.fizzbuzz;

import java.util.ArrayList;
import java.util.List;

public class FizzBuzz {

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

    public List<String> generateList(int count) {
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            result.add(generate(i));
        }
        return result;
    }
}
```

```
FizzBuzzTest > test_1から100までのFizzBuzzを生成する() PASSED

BUILD SUCCESSFUL
```

## 3.3 プリント機能

### 学習用テスト

プリント機能のテストには、標準出力のキャプチャが必要です。学習用テストとして、`System.out` のリダイレクトを試します。

> 学習用テスト
>
> 外部のソフトウェアのテストを書くべきだろうか——そのソフトウェアに対して新しいことを初めて行おうとした段階で書いてみよう。
>
> — テスト駆動開発

```java
@Test
void test_プリントする() {
    fizzbuzz.printFizzBuzz(15);
    // コンソール出力を確認（学習用テスト）
    // 実際のアプリケーションでは Main クラスから呼び出す
}
```

### 実装

```java
public void printFizzBuzz(int count) {
    List<String> result = generateList(count);
    for (String s : result) {
        System.out.println(s);
    }
}
```

### Main クラスの作成

実行用の Main クラスを作成します。

```java
package tdd.fizzbuzz;

public class Main {
    public static void main(String[] args) {
        FizzBuzz fizzBuzz = new FizzBuzz();
        fizzBuzz.printFizzBuzz(100);
    }
}
```

## 3.4 動作するきれいなコード

ここまでの実装で、すべての TODO が完了しました。

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [x] 1 から 100 までの数
- [x] プリントする

TDD では「動作するきれいなコード（Clean code that works）」を目指します。これはソフトウェア開発の三種の神器と組み合わせて実現します。

> ソフトウェア開発の三種の神器
>
> - バージョン管理
> - テスティング
> - 自動化
>
> — 和田卓人

現在のコードは動作していますが、テスト駆動開発のプロセスを振り返ってみましょう。

### TDD サイクルの振り返り

1. **仮実装** — `return "1"` でテストを通す
2. **三角測量** — 2 つ目のテストで `String.valueOf(number)` に一般化
3. **明白な実装** — 3 の倍数、5 の倍数、15 の倍数の判定を直接実装
4. **追加実装** — リスト生成、プリント機能を追加

各ステップでテストが先にあり、テストが通ることを確認してから次のステップに進みました。これが TDD の基本サイクルです。

## 3.5 まとめ

第 1 部（章 1〜3）を通じて、TDD の基本サイクルを体験しました。

### 学んだ TDD テクニック

| テクニック | 説明 | 使用場面 |
|-----------|------|---------|
| テストファースト | テスト対象のコードを書く前にテストを書く | 常に |
| アサートファースト | アサーションを最初に書く | テスト作成時 |
| 仮実装 | ベタ書きの値を返す | 最初の実装 |
| 三角測量 | 2 つ以上の例から一般化を導く | 実装の方向性が不明確なとき |
| 明白な実装 | ロジックが明確な場合は直接実装 | ロジックが自明なとき |
| 学習用テスト | 外部ソフトウェアの使い方を学ぶためのテスト | 新しい API を使うとき |

### Red-Green-Refactor

```
Red（テスト失敗）→ Green（テスト成功）→ Refactor（リファクタリング）
```

このサイクルを小さく素早く繰り返すことで、品質を維持しながら着実に機能を追加できます。

次の第 2 部では、開発環境の整備（バージョン管理、パッケージ管理、CI/CD）について解説します。
