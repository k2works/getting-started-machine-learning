---
type: Article
title: "第 3 章: 明白な実装とリファクタリング"
description: "前章では、三角測量と明白な実装で FizzBuzz のコアロジックを完成させました。"
tags: [article, getting-start-tdd, node]
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

1 から 100 までの FizzBuzz の結果を配列として返すメソッドをテストします。

```typescript
test("1から100までのFizzBuzzを生成する", () => {
  const result = fizzbuzz.generateList(100);

  expect(result).toHaveLength(100);
  expect(result[0]).toBe("1");
  expect(result[1]).toBe("2");
  expect(result[2]).toBe("Fizz");
  expect(result[3]).toBe("4");
  expect(result[4]).toBe("Buzz");
  expect(result[14]).toBe("FizzBuzz");
  expect(result[99]).toBe("Buzz");
});
```

```
 FAIL  test/fizzbuzz.test.ts > FizzBuzz
  ✕ 1から100までのFizzBuzzを生成する
    TypeError: fizzbuzz.generateList is not a function
```

### Green: 明白な実装

`Array.from` を使って、1 から `count` までの数を FizzBuzz に変換した配列を生成します。

```typescript
// src/fizzbuzz.ts
export class FizzBuzz {
  generate(number: number): string {
    if (number % 15 === 0) {
      return "FizzBuzz";
    }
    if (number % 3 === 0) {
      return "Fizz";
    }
    if (number % 5 === 0) {
      return "Buzz";
    }
    return number.toString();
  }

  generateList(count: number): string[] {
    return Array.from({ length: count }, (_, index) =>
      this.generate(index + 1),
    );
  }
}
```

```
 ✓ test/fizzbuzz.test.ts (6 tests) 5ms

 Test Files  1 passed (1)
      Tests  6 passed (6)
```

Java では `for` ループと `ArrayList` を使いましたが、TypeScript では `Array.from` を使うことで、より宣言的にリストを生成できます。

## 3.3 プリント機能

### 学習用テスト

プリント機能のテストには、標準出力のキャプチャが必要です。学習用テストとして、`console.log` のスパイを試します。

> 学習用テスト
>
> 外部のソフトウェアのテストを書くべきだろうか——そのソフトウェアに対して新しいことを初めて行おうとした段階で書いてみよう。
>
> — テスト駆動開発

```typescript
test("プリントする", () => {
  fizzbuzz.printFizzBuzz(15);
  // コンソール出力を確認（学習用テスト）
  // 実際のアプリケーションではエントリポイントから呼び出す
});
```

### 実装

`generateList` を利用して、各要素をコンソールに出力します。

```typescript
printFizzBuzz(count: number): void {
  this.generateList(count).forEach((value) => {
    console.log(value);
  });
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
2. **三角測量** — 2 つ目のテストで `number.toString()` に一般化
3. **明白な実装** — 3 の倍数、5 の倍数、15 の倍数の判定を直接実装
4. **追加実装** — リスト生成（`Array.from`）、プリント機能を追加

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
