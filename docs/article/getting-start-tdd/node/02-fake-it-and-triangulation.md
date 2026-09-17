---
type: Article
title: "第 2 章: 仮実装と三角測量"
description: "前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。"
tags: [article, getting-start-tdd, node]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 2 章: 仮実装と三角測量

## 2.1 はじめに

前章では、FizzBuzz の仕様を TODO リストに分解し、最初のテストを仮実装で通しました。この章では、**三角測量** によってプログラムを一般化し、さらに **明白な実装** で FizzBuzz のコアロジックを完成させます。

**TODO リスト**:

- [ ] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
  - [ ] 2 を渡したら文字列 "2" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.2 三角測量

1 を渡したら文字列 "1" を返すようにできました。では、2 を渡したらどうなるでしょうか？

### Red: 2 つ目のテストを書く

```typescript
test("2を渡したら文字列2を返す", () => {
  expect(fizzbuzz.generate(2)).toBe("2");
});
```

テストを実行します。

```bash
$ npx vitest run
```

```
 FAIL  test/fizzbuzz.test.ts > FizzBuzz
  ✕ 2を渡したら文字列2を返す
    Expected: "2"
    Received: "1"
```

テストが失敗しました。文字列 "1" しか返さないプログラムなのですから当然です。

### Green: 一般化する

数値を文字列に変換して返すように修正します。

```typescript
// src/fizzbuzz.ts
export class FizzBuzz {
  generate(number: number): string {
    return number.toString();
  }
}
```

テストを実行します。

```bash
$ npx vitest run
```

```
 ✓ test/fizzbuzz.test.ts (2 tests) 3ms

 Test Files  1 passed (1)
      Tests  2 passed (2)
```

テストが通りました！2 つ目のテストによって `generate` メソッドの一般化を実現できました。このようなアプローチを **三角測量** と言います。

> 三角測量
>
> テストから最も慎重に一般化を引き出すやり方はどのようなものだろうか——2 つ以上の例があるときだけ、一般化を行うようにしよう。
>
> — テスト駆動開発

**TODO リスト**:

- [x] 数を文字列にして返す
  - [x] 1 を渡したら文字列 "1" を返す
  - [x] 2 を渡したら文字列 "2" を返す
- [ ] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.3 3 の倍数 — Fizz

次は「3 の倍数のときは数の代わりに Fizz と返す」に取り掛かります。

### Red: 3 の倍数のテスト

```typescript
test("3を渡したらFizzを返す", () => {
  expect(fizzbuzz.generate(3)).toBe("Fizz");
});
```

```
 FAIL  test/fizzbuzz.test.ts > FizzBuzz
  ✕ 3を渡したらFizzを返す
    Expected: "Fizz"
    Received: "3"
```

### Green: 明白な実装

3 の倍数の判定は明白なので、直接的に実装します。

> 明白な実装
>
> シンプルな操作を実現するにはどうすればよいだろうか——そのまま実装しよう。
>
> — テスト駆動開発

```typescript
// src/fizzbuzz.ts
export class FizzBuzz {
  generate(number: number): string {
    if (number % 3 === 0) {
      return "Fizz";
    }
    return number.toString();
  }
}
```

```
 ✓ test/fizzbuzz.test.ts (3 tests) 3ms

 Test Files  1 passed (1)
      Tests  3 passed (3)
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [ ] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.4 5 の倍数 — Buzz

### Red

```typescript
test("5を渡したらBuzzを返す", () => {
  expect(fizzbuzz.generate(5)).toBe("Buzz");
});
```

```
 FAIL  test/fizzbuzz.test.ts > FizzBuzz
  ✕ 5を渡したらBuzzを返す
    Expected: "Buzz"
    Received: "5"
```

### Green

```typescript
// src/fizzbuzz.ts
export class FizzBuzz {
  generate(number: number): string {
    if (number % 3 === 0) {
      return "Fizz";
    }
    if (number % 5 === 0) {
      return "Buzz";
    }
    return number.toString();
  }
}
```

```
 ✓ test/fizzbuzz.test.ts (4 tests) 3ms

 Test Files  1 passed (1)
      Tests  4 passed (4)
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [ ] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.5 3 と 5 の倍数 — FizzBuzz

### Red

```typescript
test("15を渡したらFizzBuzzを返す", () => {
  expect(fizzbuzz.generate(15)).toBe("FizzBuzz");
});
```

```
 FAIL  test/fizzbuzz.test.ts > FizzBuzz
  ✕ 15を渡したらFizzBuzzを返す
    Expected: "FizzBuzz"
    Received: "Fizz"
```

現在の実装では 3 の倍数の条件が先に評価されるため、15 を渡すと "Fizz" が返ってきています。

### Green

3 と 5 の両方の倍数を先に判定するように修正します。

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
}
```

```
 ✓ test/fizzbuzz.test.ts (5 tests) 3ms

 Test Files  1 passed (1)
      Tests  5 passed (5)
```

**TODO リスト**:

- [x] 数を文字列にして返す
- [x] 3 の倍数のときは数の代わりに「Fizz」と返す
- [x] 5 の倍数のときは「Buzz」と返す
- [x] 3 と 5 両方の倍数の場合には「FizzBuzz」と返す
- [ ] 1 から 100 までの数
- [ ] プリントする

## 2.6 まとめ

この章では、以下の TDD テクニックを実践しました。

1. **三角測量** — 2 つ以上のテストケースから一般化を導き出す
2. **明白な実装** — ロジックが明確な場合は直接的に実装する
3. **Red-Green サイクル** — テスト失敗（Red）→ 最小限の実装（Green）を繰り返す

FizzBuzz のコアロジック（generate メソッド）が完成しました。次の章では、残りの TODO（リスト生成とプリント）を完成させ、リファクタリングを行います。
