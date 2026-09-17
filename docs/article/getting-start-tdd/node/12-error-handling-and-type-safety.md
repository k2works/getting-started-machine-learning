---
type: Article
title: "第 12 章: エラーハンドリングと型安全性"
description: "前章までに関数型のコレクション操作とパイプライン処理を学びました。"
tags: [article, getting-start-tdd, node]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 12 章: エラーハンドリングと型安全性

## 12.1 はじめに

前章までに関数型のコレクション操作とパイプライン処理を学びました。この最終章では、TypeScript の型システムを活用した **型安全なエラーハンドリング** を実装します。

## 12.2 現在の問題点

### 数値ベースのファクトリメソッド

```typescript
// 現在の実装: 数値で型を指定
static create(type: number = FizzBuzzType.TYPE_01): FizzBuzzType {
  switch (type) {
    case FizzBuzzType.TYPE_01: return new FizzBuzzType01();
    // ...
    default: throw new Error(`未定義のタイプ: ${type}`);
  }
}
```

**問題点**:

- `number` 型は任意の数値を受け付ける（型安全でない）
- 無効な値を渡すとランタイムエラーが発生
- コンパイル時にミスを検出できない

## 12.3 FizzBuzzTypeName enum

### 型安全な列挙型

```typescript
export enum FizzBuzzTypeName {
  TYPE_01 = "TYPE_01",
  TYPE_02 = "TYPE_02",
  TYPE_03 = "TYPE_03",
}
```

### 型安全なファクトリメソッド

`tryCreate` は例外を投げず、`undefined` を返すことで安全にエラーを表現します。

```typescript
static tryCreate(typeName: FizzBuzzTypeName): FizzBuzzType | undefined {
  const typeMap: Record<FizzBuzzTypeName, () => FizzBuzzType> = {
    [FizzBuzzTypeName.TYPE_01]: () => new FizzBuzzType01(),
    [FizzBuzzTypeName.TYPE_02]: () => new FizzBuzzType02(),
    [FizzBuzzTypeName.TYPE_03]: () => new FizzBuzzType03(),
  };
  return typeMap[typeName]?.();
}
```

**利点**:

| 観点 | `create(number)` | `tryCreate(FizzBuzzTypeName)` |
|------|-----------------|------------------------------|
| **型安全性** | 任意の数値を受容 | enum 値のみ受容 |
| **エラー処理** | 例外を throw | `undefined` を返却 |
| **コンパイル時チェック** | なし | 不正な値はコンパイルエラー |
| **拡張時** | switch 文を修正 | `Record` に追加 |

### テスト

```typescript
describe("型安全なファクトリメソッド", () => {
  test("FizzBuzzTypeName.TYPE_01 を指定すると FizzBuzzType01 が返る", () => {
    const type = FizzBuzzType.tryCreate(FizzBuzzTypeName.TYPE_01);
    expect(type).toBeInstanceOf(FizzBuzzType01);
  });

  test("FizzBuzzTypeName.TYPE_02 を指定すると FizzBuzzType02 が返る", () => {
    const type = FizzBuzzType.tryCreate(FizzBuzzTypeName.TYPE_02);
    expect(type).toBeInstanceOf(FizzBuzzType02);
  });

  test("FizzBuzzTypeName.TYPE_03 を指定すると FizzBuzzType03 が返る", () => {
    const type = FizzBuzzType.tryCreate(FizzBuzzTypeName.TYPE_03);
    expect(type).toBeInstanceOf(FizzBuzzType03);
  });
});
```

## 12.4 検索メソッドと Union Types

### FizzBuzzList の検索メソッド

`find` は `FizzBuzzValue | undefined` を返す Union Type で、値が見つからない可能性を型で表現します。

```typescript
find(
  predicate: (value: FizzBuzzValue) => boolean,
): FizzBuzzValue | undefined {
  return this._list.find(predicate);
}

some(predicate: (value: FizzBuzzValue) => boolean): boolean {
  return this._list.some(predicate);
}

every(predicate: (value: FizzBuzzValue) => boolean): boolean {
  return this._list.every(predicate);
}
```

### Union Type の活用

TypeScript の Union Type（`T | undefined`）は、Java の `Optional<T>` や Python の `T | None` に相当します。

```typescript
const result = list.find(isFizz);
if (result !== undefined) {
  // ここでは result は FizzBuzzValue 型に絞り込まれる
  console.log(result.value);
}
```

### テスト

```typescript
describe("検索メソッド", () => {
  const list = FizzBuzzList.generate(
    FizzBuzzType.create(FizzBuzzType.TYPE_01),
    15,
  );

  test("find で条件に合う最初の要素を見つける", () => {
    const result = list.find((v) => v.value === "Fizz");
    expect(result?.toString()).toBe("Fizz");
  });

  test("find で見つからない場合 undefined を返す", () => {
    const result = list.find((v) => v.value === "NotExist");
    expect(result).toBeUndefined();
  });

  test("some で条件に合う要素があるか判定する", () => {
    expect(list.some((v) => v.value === "Buzz")).toBe(true);
    expect(list.some((v) => v.value === "NotExist")).toBe(false);
  });

  test("every で全要素が条件を満たすか判定する", () => {
    const numberList = FizzBuzzList.generate(
      FizzBuzzType.create(FizzBuzzType.TYPE_02),
      5,
    );
    expect(numberList.every((v) => !isNaN(Number(v.value)))).toBe(true);
    expect(list.every((v) => !isNaN(Number(v.value)))).toBe(false);
  });
});
```

## 12.5 Type Guards

### カスタム Type Guard

TypeScript の **Type Guard** 関数で、`unknown` 型から安全に型を絞り込みます。

```typescript
export function isFizzBuzzValue(value: unknown): value is FizzBuzzValue {
  return value instanceof FizzBuzzValue;
}

export function isFizzBuzzList(value: unknown): value is FizzBuzzList {
  return value instanceof FizzBuzzList;
}
```

### 使用例

```typescript
function processResult(result: unknown): string {
  if (isFizzBuzzValue(result)) {
    return result.toString(); // FizzBuzzValue として安全にアクセス
  }
  if (isFizzBuzzList(result)) {
    return result.join(", "); // FizzBuzzList として安全にアクセス
  }
  return "unknown";
}
```

### テスト

```typescript
describe("型ガード", () => {
  test("isFizzBuzzValue は FizzBuzzValue インスタンスで true を返す", () => {
    expect(isFizzBuzzValue(new FizzBuzzValue("Fizz", 3))).toBe(true);
  });

  test("isFizzBuzzValue は非インスタンスで false を返す", () => {
    expect(isFizzBuzzValue("Fizz")).toBe(false);
    expect(isFizzBuzzValue(null)).toBe(false);
  });
});
```

## 12.6 エントリポイントの更新

```typescript
// src/index.ts
export {
  FizzBuzzValue,
  FizzBuzzList,
  FizzBuzzType,
  FizzBuzzTypeName,
  FizzBuzzType01,
  FizzBuzzType02,
  FizzBuzzType03,
  isFizzBuzzList,
  isFizzBuzzValue,
  FizzBuzzValueCommand,
  FizzBuzzListCommand,
} from "./fizzbuzz/index.js";
```

### テスト実行結果

```
 ✓ test/fizzbuzz/domain/model/fizz-buzz-value.test.ts (4 tests) 3ms
 ✓ test/fizzbuzz/application/fizz-buzz-command.test.ts (3 tests) 5ms
 ✓ test/fizzbuzz/domain/type/fizz-buzz-util.test.ts (8 tests) 5ms
 ✓ test/fizzbuzz/domain/type/fizz-buzz-type.test.ts (19 tests) 7ms
 ✓ test/fizzbuzz/domain/model/fizz-buzz-list.test.ts (19 tests) 10ms

 Test Files  5 passed (5)
      Tests  53 passed (53)
```

## 12.7 各言語のエラーハンドリング比較

| 概念 | TypeScript | Java | Python |
|------|-----------|------|--------|
| Null 安全 | `T \| undefined`、`?` 演算子 | `Optional<T>` | `T \| None`、型ヒント |
| 列挙型 | `enum` | `enum` | `enum.Enum` |
| 型ガード | `value is T` 述語 | `instanceof` | `isinstance()` |
| パターンマッチ | （TC39 提案中） | switch 式 | `match` 文（3.10+） |
| 型安全ファクトリ | `Record<Enum, () => T>` | switch 式 + enum | `dict[Enum, Callable]` |

## 12.8 第 4 部のまとめ

第 4 部（章 10〜12）を通じて、OOP 設計のコードに関数型プログラミングのパターンを適用しました。

| 章 | テーマ | 適用した概念 |
|---|--------|-----------|
| 10 | 高階関数と関数合成 | map/filter/reduce、compose/pipe、述語関数 |
| 11 | 不変データとパイプライン | groupBy/countBy/take/join、ジェネレータ、構造的型付け |
| 12 | エラーハンドリングと型安全性 | enum ファクトリ、Union Types、Type Guards |

### 全 12 章の旅

| 部 | 章 | テーマ |
|---|---|--------|
| **第 1 部** | 1-3 | TDD の基本サイクル（Red-Green-Refactor） |
| **第 2 部** | 4-6 | 開発環境と自動化（Git、ESLint、CI/CD） |
| **第 3 部** | 7-9 | OOP 設計（Strategy、Command、SOLID、モジュール設計） |
| **第 4 部** | 10-12 | 関数型プログラミング（高階関数、パイプライン、型安全性） |

### 最終的なディレクトリ構成

```
src/fizzbuzz/
├── index.ts                          (バレルファイル)
├── domain/
│   ├── model/
│   │   ├── fizz-buzz-value.ts       (値オブジェクト)
│   │   └── fizz-buzz-list.ts        (ファーストクラスコレクション + FP メソッド)
│   └── type/
│       ├── fizz-buzz-type.ts        (抽象クラス + ファクトリ)
│       ├── fizz-buzz-type-name.ts   (型安全 enum)
│       ├── fizz-buzz-type-01.ts     (タイプ 1)
│       ├── fizz-buzz-type-02.ts     (タイプ 2)
│       ├── fizz-buzz-type-03.ts     (タイプ 3)
│       └── fizz-buzz-util.ts        (関数合成 + 述語 + Type Guards)
└── application/
    ├── fizz-buzz-command.ts         (コマンドインターフェース)
    ├── fizz-buzz-value-command.ts   (単一値コマンド)
    └── fizz-buzz-list-command.ts    (リストコマンド)

test/fizzbuzz/
├── domain/
│   ├── model/
│   │   ├── fizz-buzz-value.test.ts  (4 tests)
│   │   └── fizz-buzz-list.test.ts   (19 tests)
│   └── type/
│       ├── fizz-buzz-type.test.ts   (19 tests)
│       └── fizz-buzz-util.test.ts   (8 tests)
└── application/
    └── fizz-buzz-command.test.ts    (3 tests)
```

**テスト合計: 53 テスト、5 ファイル、全パス**

FizzBuzz という単純な問題を題材に、TDD の基本から OOP 設計、関数型プログラミング、型安全性まで、段階的にソフトウェア設計の本質を学びました。
