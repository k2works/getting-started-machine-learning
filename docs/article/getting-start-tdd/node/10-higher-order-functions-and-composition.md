---
type: Article
title: "第 10 章: 高階関数と関数合成"
description: "第 3 部ではオブジェクト指向設計でコードを構造化しました。"
tags: [article, getting-start-tdd, node]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 10 章: 高階関数と関数合成

## 10.1 はじめに

第 3 部ではオブジェクト指向設計でコードを構造化しました。第 4 部では **関数型プログラミング** の観点から FizzBuzz を再構成し、JavaScript/TypeScript の関数型機能を学びます。

この章では、**高階関数**（Higher-Order Functions）と **関数合成**（Function Composition）を使って、コレクション操作をより宣言的に記述します。

## 10.2 アロー関数と高階関数

### アロー関数

TypeScript/JavaScript のアロー関数は、関数型プログラミングの基本です。

```typescript
// 従来の関数
function double(n: number): number {
  return n * 2;
}

// アロー関数
const double = (n: number): number => n * 2;
```

### 高階関数とは

**高階関数** とは、関数を引数に取るか、関数を返す関数です。JavaScript の配列メソッド `map`、`filter`、`reduce` は代表的な高階関数です。

## 10.3 FizzBuzzList への関数型メソッドの追加

### map: 各要素を変換

```typescript
map(fn: (value: FizzBuzzValue) => FizzBuzzValue): FizzBuzzList {
  return new FizzBuzzList(this._list.map(fn));
}
```

**特徴**: 元のリストを変更せず、新しいリストを返します（不変性）。

### filter: 条件に合う要素のみ抽出

```typescript
filter(predicate: (value: FizzBuzzValue) => boolean): FizzBuzzList {
  return new FizzBuzzList(this._list.filter(predicate));
}
```

### reduce: 畳み込み

```typescript
reduce<T>(fn: (acc: T, value: FizzBuzzValue) => T, initial: T): T {
  return this._list.reduce(fn, initial);
}
```

### テスト

```typescript
describe("関数型メソッド", () => {
  const baseList = new FizzBuzzList([
    new FizzBuzzValue("1", 1),
    new FizzBuzzValue("Fizz", 3),
    new FizzBuzzValue("Buzz", 5),
    new FizzBuzzValue("FizzBuzz", 15),
  ]);

  test("map で値を変換する", () => {
    const mapped = baseList.map(
      (v) => new FizzBuzzValue(v.toString().toUpperCase(), v.number),
    );
    expect(mapped.toStringArray()).toEqual(["1", "FIZZ", "BUZZ", "FIZZBUZZ"]);
    // 元のリストは変更されない
    expect(baseList.toStringArray()).toEqual(["1", "Fizz", "Buzz", "FizzBuzz"]);
  });

  test("filter で Fizz のみ抽出できる", () => {
    const filtered = baseList.filter((v) => v.value === "Fizz");
    expect(filtered.toStringArray()).toEqual(["Fizz"]);
  });

  test("reduce で文字列結合できる", () => {
    const reduced = baseList.reduce(
      (acc, v) => `${acc}${acc.length > 0 ? "," : ""}${v.toString()}`,
      "",
    );
    expect(reduced).toBe("1,Fizz,Buzz,FizzBuzz");
  });
});
```

## 10.4 FizzBuzzListCommand の関数的リファクタリング

### Before: for ループ

```typescript
execute(): FizzBuzzList {
  let list = new FizzBuzzList();
  for (let i = 1; i <= this._count; i++) {
    list = list.add(this._type.generate(i));
  }
  return list;
}
```

### After: Array.from + reduce

```typescript
execute(): FizzBuzzList {
  return Array.from({ length: this._count }, (_, i) => i + 1).reduce(
    (list, num) => list.add(this._type.generate(num)),
    new FizzBuzzList(),
  );
}
```

**変更点**:

- `for` ループを `Array.from` + `reduce` に置き換え
- 宣言的な記述に変更（「何を」するかが明確）
- 既存のテストはすべてそのまま通過

## 10.5 関数合成ユーティリティ

### compose と pipe

```typescript
// compose: 右から左に合成 f(g(x))
export const compose =
  <T>(...fns: ((arg: T) => T)[]): ((arg: T) => T) =>
  (arg: T) =>
    fns.reduceRight((acc, fn) => fn(acc), arg);

// pipe: 左から右に合成 g(f(x))
export const pipe =
  <T>(...fns: ((arg: T) => T)[]): ((arg: T) => T) =>
  (arg: T) =>
    fns.reduce((acc, fn) => fn(acc), arg);
```

### フィルタ述語関数

カリー化されたフィルタ関数で、再利用可能な述語を定義します。

```typescript
export const isFizz = (v: FizzBuzzValue): boolean => v.value === "Fizz";
export const isBuzz = (v: FizzBuzzValue): boolean => v.value === "Buzz";
export const isFizzBuzz = (v: FizzBuzzValue): boolean => v.value === "FizzBuzz";
export const isNumber = (v: FizzBuzzValue): boolean => !isNaN(Number(v.value));
```

### テスト

```typescript
describe("関数合成", () => {
  test("compose は右から左に適用する", () => {
    const double = (n: number): number => n * 2;
    const increment = (n: number): number => n + 1;
    const fn = compose(double, increment);
    expect(fn(3)).toBe(8); // double(increment(3)) = double(4) = 8
  });

  test("pipe は左から右に適用する", () => {
    const double = (n: number): number => n * 2;
    const increment = (n: number): number => n + 1;
    const fn = pipe(double, increment);
    expect(fn(3)).toBe(7); // increment(double(3)) = increment(6) = 7
  });
});

describe("フィルタ関数", () => {
  test("isFizz は Fizz のとき true を返す", () => {
    expect(isFizz(new FizzBuzzValue("Fizz", 3))).toBe(true);
    expect(isFizz(new FizzBuzzValue("Buzz", 5))).toBe(false);
  });
});
```

## 10.6 各言語の高階関数比較

| 概念 | TypeScript | Java | Python |
|------|-----------|------|--------|
| ラムダ式 | `(x) => x * 2` | `x -> x * 2` | `lambda x: x * 2` |
| map | `array.map(fn)` | `stream.map(fn)` | `map(fn, list)` / 内包表記 |
| filter | `array.filter(fn)` | `stream.filter(fn)` | `filter(fn, list)` / 内包表記 |
| reduce | `array.reduce(fn, init)` | `stream.reduce(init, fn)` | `functools.reduce(fn, list, init)` |
| 関数合成 | `compose(f, g)` / `pipe(f, g)` | `f.andThen(g)` / `f.compose(g)` | `functools.reduce` で実装 |
| 述語関数 | `const isFizz = (v) => ...` | `Predicate<T>` インターフェース | `def is_fizz(v): ...` |

## 10.7 まとめ

この章で学んだこと:

1. **アロー関数**: 簡潔な関数リテラルで高階関数を活用
2. **map/filter/reduce**: コレクションの宣言的操作
3. **関数合成**: `compose`（右→左）と `pipe`（左→右）で関数を組み合わせ
4. **述語関数**: 再利用可能なフィルタ条件を関数として定義

次の章では、不変データの原則をさらに深め、パイプライン処理でデータを変換するパターンを学びます。
