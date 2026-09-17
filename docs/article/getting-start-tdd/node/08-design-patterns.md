---
type: Article
title: "第 8 章: デザインパターンの適用"
description: "前章ではカプセル化とポリモーフィズムを使って、手続き的な条件分岐をクラス階層に置き換えました。"
tags: [article, getting-start-tdd, node]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 8 章: デザインパターンの適用

## 8.1 はじめに

前章ではカプセル化とポリモーフィズムを使って、手続き的な条件分岐をクラス階層に置き換えました。この章では、さらに多くの **デザインパターン** を適用して、コードの表現力と安全性を向上させます。

## 8.2 値オブジェクト（Value Object）

### 問題: プリミティブ型の使用

現在 `generate()` は `string` を返しています。しかし、FizzBuzz の結果には「変換前の数値」と「変換後の文字列」の 2 つの情報が含まれます。プリミティブ型では、このドメイン知識が表現できません。

### 解決: FizzBuzzValue クラス

```typescript
export class FizzBuzzValue {
  private readonly _value: string;
  private readonly _number: number;

  constructor(value: string, number: number) {
    this._value = value;
    this._number = number;
  }

  get value(): string {
    return this._value;
  }

  get number(): number {
    return this._number;
  }

  equals(other: FizzBuzzValue): boolean {
    if (!(other instanceof FizzBuzzValue)) {
      return false;
    }
    return this._value === other._value && this._number === other._number;
  }

  toString(): string {
    return this._value;
  }
}
```

### 値オブジェクトの特徴

| 特徴 | 実現方法 |
|------|---------|
| **不変性** | `private readonly` フィールド |
| **等価性** | `equals()` メソッドで値による比較 |
| **自己記述性** | `toString()` で文字列表現 |
| **副作用なし** | setter を持たない |

### テスト

```typescript
describe("FizzBuzzValue", () => {
  test("値と数値を保持する", () => {
    const value = new FizzBuzzValue("Fizz", 3);
    expect(value.value).toBe("Fizz");
    expect(value.number).toBe(3);
  });

  test("toString は値を返す", () => {
    const value = new FizzBuzzValue("Buzz", 5);
    expect(value.toString()).toBe("Buzz");
  });

  test("同じ値と数値の場合 equals は true", () => {
    const v1 = new FizzBuzzValue("Fizz", 3);
    const v2 = new FizzBuzzValue("Fizz", 3);
    expect(v1.equals(v2)).toBe(true);
  });

  test("異なる値の場合 equals は false", () => {
    const v1 = new FizzBuzzValue("Fizz", 3);
    const v2 = new FizzBuzzValue("Buzz", 5);
    expect(v1.equals(v2)).toBe(false);
  });
});
```

### FizzBuzzType の更新

generate メソッドの戻り値を `string` から `FizzBuzzValue` に変更します。

```typescript
export class FizzBuzzType01 extends FizzBuzzType {
  generate(number: number): FizzBuzzValue {
    if (number % 15 === 0) return new FizzBuzzValue("FizzBuzz", number);
    if (number % 3 === 0) return new FizzBuzzValue("Fizz", number);
    if (number % 5 === 0) return new FizzBuzzValue("Buzz", number);
    return new FizzBuzzValue(number.toString(), number);
  }
}
```

## 8.3 ファーストクラスコレクション（First-Class Collection）

### 問題: 生の配列の使用

`generateList()` が `string[]` を返すと、コレクションに対する操作が外部に散らばります。

### 解決: FizzBuzzList クラス

```typescript
import { FizzBuzzValue } from "./fizz-buzz-value";

export class FizzBuzzList {
  private readonly _list: readonly FizzBuzzValue[];

  constructor(list: FizzBuzzValue[] = []) {
    this._list = Object.freeze([...list]);
  }

  add(value: FizzBuzzValue): FizzBuzzList {
    return new FizzBuzzList([...this._list, value]);
  }

  get value(): readonly FizzBuzzValue[] {
    return this._list;
  }

  get size(): number {
    return this._list.length;
  }

  toStringArray(): string[] {
    return this._list.map((v) => v.toString());
  }

  *[Symbol.iterator](): Iterator<FizzBuzzValue> {
    for (const value of this._list) {
      yield value;
    }
  }
}
```

### ファーストクラスコレクションの特徴

| 特徴 | 実現方法 |
|------|---------|
| **不変性** | `Object.freeze()` で凍結、`add()` は新インスタンスを返す |
| **カプセル化** | コレクション操作をクラス内に集約 |
| **イテレータ** | `Symbol.iterator` で `for...of` 対応 |
| **型安全** | `FizzBuzzValue` のみ格納可能 |

### テスト

```typescript
describe("FizzBuzzList", () => {
  test("空リストを生成できる", () => {
    const list = new FizzBuzzList();
    expect(list.size).toBe(0);
  });

  test("add で新しいリストを返す（不変）", () => {
    const list = new FizzBuzzList();
    const newList = list.add(new FizzBuzzValue("1", 1));
    expect(list.size).toBe(0);
    expect(newList.size).toBe(1);
  });

  test("toStringArray で文字列配列を返す", () => {
    let list = new FizzBuzzList();
    list = list.add(new FizzBuzzValue("1", 1));
    list = list.add(new FizzBuzzValue("2", 2));
    list = list.add(new FizzBuzzValue("Fizz", 3));
    expect(list.toStringArray()).toEqual(["1", "2", "Fizz"]);
  });

  test("イテレータで反復できる", () => {
    let list = new FizzBuzzList();
    list = list.add(new FizzBuzzValue("Fizz", 3));
    list = list.add(new FizzBuzzValue("Buzz", 5));

    const values: string[] = [];
    for (const v of list) {
      values.push(v.toString());
    }
    expect(values).toEqual(["Fizz", "Buzz"]);
  });
});
```

## 8.4 コマンドパターン（Command Pattern）

### 問題: 操作の直接実行

`FizzBuzz` クラスが「値の生成」と「リストの生成」という複数の操作を直接持っていました。

### 解決: FizzBuzzCommand

操作をオブジェクトとして表現します。

```typescript
export interface FizzBuzzCommand {
  execute(): FizzBuzzValue | FizzBuzzList;
}
```

### FizzBuzzValueCommand

```typescript
export class FizzBuzzValueCommand implements FizzBuzzCommand {
  private readonly _type: FizzBuzzType;
  private readonly _number: number;

  constructor(type: FizzBuzzType, number: number) {
    this._type = type;
    this._number = number;
  }

  execute(): FizzBuzzValue {
    return this._type.generate(this._number);
  }
}
```

### FizzBuzzListCommand

```typescript
export class FizzBuzzListCommand implements FizzBuzzCommand {
  private readonly _type: FizzBuzzType;
  private readonly _count: number;

  constructor(type: FizzBuzzType, count: number) {
    this._type = type;
    this._count = count;
  }

  execute(): FizzBuzzList {
    let list = new FizzBuzzList();
    for (let i = 1; i <= this._count; i++) {
      list = list.add(this._type.generate(i));
    }
    return list;
  }
}
```

### テスト

```typescript
describe("FizzBuzzCommand", () => {
  test("FizzBuzzValueCommand は単一の値を生成する", () => {
    const type = FizzBuzzType.create(FizzBuzzType.TYPE_01);
    const command = new FizzBuzzValueCommand(type, 3);
    const result = command.execute();
    expect(result.toString()).toBe("Fizz");
  });

  test("FizzBuzzListCommand はリストを生成する", () => {
    const type = FizzBuzzType.create(FizzBuzzType.TYPE_01);
    const command = new FizzBuzzListCommand(type, 100);
    const result = command.execute();
    const arr = result.toStringArray();

    expect(result.size).toBe(100);
    expect(arr[2]).toBe("Fizz");
    expect(arr[4]).toBe("Buzz");
    expect(arr[14]).toBe("FizzBuzz");
  });
});
```

### コマンドパターンの利点

- **操作の具象化**: 「何をするか」をオブジェクトで表現
- **パラメータの保持**: 実行に必要な情報をコマンド内に保持
- **実行の分離**: 操作の「定義」と「実行」を分離
- **拡張性**: 新しい操作は新しいコマンドクラスを追加するだけ

## 8.5 リファクタリング後のクラス構造

### 適用したデザインパターン一覧

| パターン | クラス | 役割 |
|---------|--------|------|
| **Value Object** | `FizzBuzzValue` | 不変の値を表現 |
| **First-Class Collection** | `FizzBuzzList` | コレクション操作のカプセル化 |
| **Strategy** | `FizzBuzzType` + サブクラス | アルゴリズムの交換 |
| **Factory Method** | `FizzBuzzType.create()` | インスタンス生成の集約 |
| **Command** | `FizzBuzzCommand` + 実装クラス | 操作のオブジェクト化 |

### テスト実行結果

```
 ✓ test/fizzbuzz.test.ts (27 tests) 21ms
   ✓ FizzBuzz > タイプ1の場合 (6 tests)
   ✓ FizzBuzz > タイプ2の場合 (3 tests)
   ✓ FizzBuzz > タイプ3の場合 (3 tests)
   ✓ FizzBuzz > ファクトリメソッド (4 tests)
   ✓ FizzBuzz > FizzBuzzValue (4 tests)
   ✓ FizzBuzz > FizzBuzzList (4 tests)
   ✓ FizzBuzz > FizzBuzzCommand (3 tests)

 Test Files  1 passed (1)
      Tests  27 passed (27)
```

## 8.6 各言語のデザインパターン比較

| パターン | TypeScript | Java | Python |
|---------|-----------|------|--------|
| Value Object | `readonly` + `equals()` | `final` + `equals()` / `hashCode()` | `__eq__()` / `__hash__()` |
| Collection | `Object.freeze()` + `Symbol.iterator` | `Collections.unmodifiableList()` | `tuple` / `frozenset` |
| Interface | `interface` | `interface` | `Protocol` / `ABC` |
| Command | `implements FizzBuzzCommand` | `implements FizzBuzzCommand` | ABC + `@abstractmethod` |

## 8.7 まとめ

この章で学んだこと:

1. **値オブジェクト**: プリミティブ型をドメイン固有のオブジェクトで置き換え、型安全性と表現力を向上
2. **ファーストクラスコレクション**: 配列操作をカプセル化し、不変性を保証
3. **コマンドパターン**: 操作をオブジェクトとして表現し、実行の柔軟性を確保
4. **ファクトリメソッド**: インスタンス生成ロジックを集約

次の章では、SOLID 原則の観点からコードを検証し、モジュール構造に再編成します。
