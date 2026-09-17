---
type: Article
title: "第 11 章: 不変データとパイプライン処理"
description: "前章では高階関数と関数合成を学びました。"
tags: [article, getting-start-tdd, node]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 11 章: 不変データとパイプライン処理

## 11.1 はじめに

前章では高階関数と関数合成を学びました。この章では **不変データ** の原則をさらに深め、**パイプライン処理** でデータを流れるように変換するパターンを学びます。

## 11.2 不変データの原則

### 既存の不変設計

第 3 部で実装した FizzBuzzList は既に不変性を備えています。

| メカニズム | 実装方法 |
|-----------|---------|
| **フィールドの凍結** | `Object.freeze([...list])` |
| **不変な追加** | `add()` は新しいインスタンスを返す |
| **readonly 型** | `readonly FizzBuzzValue[]` で型レベルの保護 |
| **private フィールド** | 外部から直接変更不可 |

### TypeScript の不変性サポート

| 手法 | 用途 |
|------|------|
| `readonly` 修飾子 | プロパティの再代入を防止 |
| `ReadonlyArray<T>` | 配列の変更メソッドを型レベルで禁止 |
| `Object.freeze()` | 実行時にオブジェクトの変更を防止 |
| `as const` | リテラル型の推論と readonly 化 |
| Spread 演算子 | `{...obj}` / `[...arr]` でシャローコピー |

## 11.3 パイプライン処理メソッド

### groupBy: 値の種類ごとにグループ化

```typescript
groupBy(fn: (value: FizzBuzzValue) => string): Map<string, FizzBuzzList> {
  return this._list.reduce((groups, value) => {
    const key = fn(value);
    const group = groups.get(key) ?? new FizzBuzzList();
    groups.set(key, group.add(value));
    return groups;
  }, new Map<string, FizzBuzzList>());
}
```

### countBy: 種類ごとの出現回数をカウント

```typescript
countBy(fn: (value: FizzBuzzValue) => string): Map<string, number> {
  return this._list.reduce((counts, value) => {
    const key = fn(value);
    counts.set(key, (counts.get(key) ?? 0) + 1);
    return counts;
  }, new Map<string, number>());
}
```

### take / first / join

```typescript
take(n: number): FizzBuzzList {
  return new FizzBuzzList(this._list.slice(0, Math.max(0, n)));
}

first(): FizzBuzzValue | undefined {
  return this._list[0];
}

join(separator: string): string {
  return this._list.map((value) => value.toString()).join(separator);
}
```

## 11.4 ジェネレータベースの生成

### ジェネレータ関数

TypeScript の `function*` でジェネレータを定義し、遅延評価でシーケンスを生成します。

```typescript
private static *sequence(
  type: FizzBuzzGenerator,
  count: number,
): Generator<FizzBuzzValue> {
  for (let number = 1; number <= count; number += 1) {
    yield type.generate(number);
  }
}

static generate(type: FizzBuzzGenerator, count: number): FizzBuzzList {
  return new FizzBuzzList([...FizzBuzzList.sequence(type, count)]);
}
```

### 循環参照の回避

`FizzBuzzType` を直接 import すると循環参照が発生するため、構造的型付けで回避します。

```typescript
type FizzBuzzGenerator = {
  generate(number: number): FizzBuzzValue;
};
```

TypeScript のダックタイピングにより、`FizzBuzzType` のインスタンスは `FizzBuzzGenerator` 型を自動的に満たします。

## 11.5 テスト

### パイプライン処理テスト

```typescript
describe("パイプライン処理", () => {
  const type = FizzBuzzType.create(FizzBuzzType.TYPE_01);
  const list = FizzBuzzList.generate(type, 15);

  test("groupBy で値の種類ごとにグループ化する", () => {
    const groups = list.groupBy((v) => {
      if (v.value === "FizzBuzz") return "FizzBuzz";
      if (v.value === "Fizz") return "Fizz";
      if (v.value === "Buzz") return "Buzz";
      return "number";
    });
    expect(groups.get("Fizz")?.size).toBe(4);
    expect(groups.get("Buzz")?.size).toBe(2);
    expect(groups.get("FizzBuzz")?.size).toBe(1);
    expect(groups.get("number")?.size).toBe(8);
  });

  test("countBy で種類ごとの出現回数をカウントする", () => {
    const counts = list.countBy((v) => {
      if (v.value === "FizzBuzz") return "FizzBuzz";
      if (v.value === "Fizz") return "Fizz";
      if (v.value === "Buzz") return "Buzz";
      return "number";
    });
    expect(counts.get("Fizz")).toBe(4);
    expect(counts.get("FizzBuzz")).toBe(1);
  });

  test("take で先頭N件を取得する", () => {
    expect(list.take(3).toStringArray()).toEqual(["1", "2", "Fizz"]);
  });

  test("first で最初の要素を取得する", () => {
    expect(list.first()?.toString()).toBe("1");
  });

  test("join で文字列結合する", () => {
    expect(list.take(5).join(", ")).toBe("1, 2, Fizz, 4, Buzz");
  });

  test("メソッドチェーンでパイプライン処理する", () => {
    const result = list
      .filter((v) => v.value === "Fizz")
      .take(2)
      .join(", ");
    expect(result).toBe("Fizz, Fizz");
  });
});
```

### 静的生成メソッドテスト

```typescript
describe("静的生成メソッド", () => {
  test("generate でリストを生成する", () => {
    const type = FizzBuzzType.create(FizzBuzzType.TYPE_01);
    const list = FizzBuzzList.generate(type, 15);
    expect(list.size).toBe(15);
    expect(list.first()?.toString()).toBe("1");
  });
});
```

### テスト実行結果

```
 ✓ test/fizzbuzz/domain/model/fizz-buzz-list.test.ts (15 tests) 10ms
 ✓ test/fizzbuzz/domain/model/fizz-buzz-value.test.ts (4 tests) 3ms
 ✓ test/fizzbuzz/domain/type/fizz-buzz-type.test.ts (16 tests) 7ms
 ✓ test/fizzbuzz/domain/type/fizz-buzz-util.test.ts (6 tests) 4ms
 ✓ test/fizzbuzz/application/fizz-buzz-command.test.ts (3 tests) 4ms

 Test Files  5 passed (5)
      Tests  44 passed (44)
```

## 11.6 各言語のパイプライン比較

| 概念 | TypeScript | Java | Python |
|------|-----------|------|--------|
| パイプライン | メソッドチェーン | Stream API | ジェネレータ + itertools |
| グループ化 | `Map` + `reduce` | `Collectors.groupingBy()` | `dict` + ループ |
| 遅延評価 | `function*` ジェネレータ | `Stream` の中間操作 | `yield` ジェネレータ |
| 不変コピー | Spread `[...arr]` | `Collections.unmodifiableList()` | `tuple` / `frozenset` |
| 結合 | `array.join()` | `Collectors.joining()` | `str.join()` |

## 11.7 まとめ

この章で学んだこと:

1. **不変データ**: `Object.freeze()`、`readonly`、Spread 演算子で不変性を保証
2. **パイプライン処理**: `groupBy`、`countBy`、`take`、`first`、`join` でデータを流れるように変換
3. **メソッドチェーン**: `.filter().take().join()` の連鎖で宣言的なデータ処理
4. **ジェネレータ**: `function*` と `yield` による遅延評価シーケンス
5. **構造的型付け**: 循環参照を避けるダックタイピング

次の章では、エラーハンドリングと型安全性の観点から、TypeScript の型システムを活用したより安全なコードを学びます。
