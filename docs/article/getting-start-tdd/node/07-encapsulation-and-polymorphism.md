---
type: Article
title: "第 7 章: カプセル化とポリモーフィズム"
description: "第 1 部では FizzBuzz を TDD で実装し、第 2 部では開発環境を整備しました。"
tags: [article, getting-start-tdd, node]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 7 章: カプセル化とポリモーフィズム

## 7.1 はじめに

第 1 部では FizzBuzz を TDD で実装し、第 2 部では開発環境を整備しました。第 3 部では **オブジェクト指向設計** に踏み込み、手続き的なコードをより柔軟で拡張しやすい構造にリファクタリングしていきます。

この章では、**追加仕様** を題材にして **カプセル化** と **ポリモーフィズム** を学びます。

## 7.2 追加仕様

FizzBuzz に 3 つの **タイプ** を導入します。

| タイプ | 仕様 |
|--------|------|
| タイプ 1（通常） | 3 の倍数→Fizz、5 の倍数→Buzz、15 の倍数→FizzBuzz、それ以外→数値 |
| タイプ 2（数値のみ） | すべて数値文字列を返す（Fizz/Buzz 変換なし） |
| タイプ 3（FizzBuzz のみ） | 15 の倍数→FizzBuzz、それ以外→数値 |

**TODO リスト**:

- [ ] タイプ 1: 通常の FizzBuzz（既存の動作）
- [ ] タイプ 2: 数値のみ返す
- [ ] タイプ 3: FizzBuzz のみ返す
- [ ] 未定義のタイプはエラー

## 7.3 手続き的なアプローチ

最初に思いつくのは、`switch` 文でタイプを分岐する手続き的なアプローチです。

```typescript
// 手続き的な実装（アンチパターン）
class FizzBuzz {
  generate(number: number, type: number): string {
    switch (type) {
      case 1:
        if (number % 15 === 0) return "FizzBuzz";
        if (number % 3 === 0) return "Fizz";
        if (number % 5 === 0) return "Buzz";
        return number.toString();
      case 2:
        return number.toString();
      case 3:
        if (number % 15 === 0) return "FizzBuzz";
        return number.toString();
      default:
        throw new Error(`未定義のタイプ: ${type}`);
    }
  }
}
```

この実装には問題があります:

- **単一責任原則の違反**: 1 つのメソッドに複数のアルゴリズムが詰め込まれている
- **開放閉鎖原則の違反**: 新しいタイプを追加するたびに既存のコードを修正する必要がある
- **テストの困難さ**: タイプごとの独立したテストがしにくい

## 7.4 カプセル化

TypeScript では `private readonly` を使ってフィールドをカプセル化します。

```typescript
export class FizzBuzz {
  private readonly _type: FizzBuzzType;

  constructor(type?: FizzBuzzType) {
    this._type = type ?? new FizzBuzzType01();
  }

  get type(): FizzBuzzType {
    return this._type;
  }

  generate(number: number): string {
    return this._type.generate(number);
  }
}
```

### TypeScript のカプセル化メカニズム

| 機能 | TypeScript | Java | Python |
|------|-----------|------|--------|
| アクセス修飾子 | `private`, `protected`, `public` | `private`, `protected`, `public` | `_` 命名規約 |
| 不変フィールド | `readonly` | `final` | `@property`（setter なし） |
| getter | `get prop()` | `getProp()` | `@property` |

- `private readonly _type`: 外部からアクセス不可、再代入不可
- `get type()`: 読み取り専用アクセサ
- コンストラクタでのみ初期化可能

## 7.5 ポリモーフィズム

### 抽象クラスの定義

TypeScript の `abstract class` を使って、タイプの共通インターフェースを定義します。

```typescript
export abstract class FizzBuzzType {
  static readonly TYPE_01 = 1;
  static readonly TYPE_02 = 2;
  static readonly TYPE_03 = 3;

  abstract generate(number: number): string;

  static create(type: number): FizzBuzzType {
    switch (type) {
      case FizzBuzzType.TYPE_01:
        return new FizzBuzzType01();
      case FizzBuzzType.TYPE_02:
        return new FizzBuzzType02();
      case FizzBuzzType.TYPE_03:
        return new FizzBuzzType03();
      default:
        throw new Error(`未定義のタイプ: ${type}`);
    }
  }
}
```

### 具体クラスの実装

```typescript
export class FizzBuzzType01 extends FizzBuzzType {
  generate(number: number): string {
    if (number % 15 === 0) return "FizzBuzz";
    if (number % 3 === 0) return "Fizz";
    if (number % 5 === 0) return "Buzz";
    return number.toString();
  }
}

export class FizzBuzzType02 extends FizzBuzzType {
  generate(number: number): string {
    return number.toString();
  }
}

export class FizzBuzzType03 extends FizzBuzzType {
  generate(number: number): string {
    if (number % 15 === 0) return "FizzBuzz";
    return number.toString();
  }
}
```

### Strategy パターン

この設計は **Strategy パターン** です。`FizzBuzz` クラスが Context、`FizzBuzzType` が Strategy に相当します。

```
FizzBuzz (Context)
  └── FizzBuzzType (Strategy - abstract)
        ├── FizzBuzzType01 (通常の FizzBuzz)
        ├── FizzBuzzType02 (数値のみ)
        └── FizzBuzzType03 (FizzBuzz のみ)
```

**利点**:

- タイプの追加は新しいクラスを作るだけ（開放閉鎖原則）
- 各タイプを独立してテストできる
- 実行時にアルゴリズムを切り替え可能

### FizzBuzz クラスの委譲

```typescript
export class FizzBuzz {
  private readonly _type: FizzBuzzType;

  constructor(type?: FizzBuzzType) {
    this._type = type ?? new FizzBuzzType01();
  }

  get type(): FizzBuzzType {
    return this._type;
  }

  generate(number: number): string {
    return this._type.generate(number);
  }

  generateList(count: number): string[] {
    return Array.from({ length: count }, (_, index) =>
      this._type.generate(index + 1),
    );
  }

  printFizzBuzz(count: number): void {
    this.generateList(count).forEach((value) => {
      console.log(value);
    });
  }
}
```

## 7.6 テストの更新

テストをタイプ別の `describe` ブロックに整理します。

```typescript
import { beforeEach, describe, expect, test } from "vitest";
import {
  FizzBuzz,
  FizzBuzzType,
  FizzBuzzType01,
  FizzBuzzType02,
  FizzBuzzType03,
} from "../src/fizzbuzz";

describe("FizzBuzz", () => {
  describe("タイプ1の場合", () => {
    let fizzbuzz: FizzBuzz;

    beforeEach(() => {
      fizzbuzz = new FizzBuzz(FizzBuzzType.create(FizzBuzzType.TYPE_01));
    });

    test("1を渡したら文字列1を返す", () => {
      expect(fizzbuzz.generate(1)).toBe("1");
    });

    test("3を渡したらFizzを返す", () => {
      expect(fizzbuzz.generate(3)).toBe("Fizz");
    });

    test("5を渡したらBuzzを返す", () => {
      expect(fizzbuzz.generate(5)).toBe("Buzz");
    });

    test("15を渡したらFizzBuzzを返す", () => {
      expect(fizzbuzz.generate(15)).toBe("FizzBuzz");
    });
  });

  describe("タイプ2の場合", () => {
    let fizzbuzz: FizzBuzz;

    beforeEach(() => {
      fizzbuzz = new FizzBuzz(FizzBuzzType.create(FizzBuzzType.TYPE_02));
    });

    test("3を渡したら文字列3を返す", () => {
      expect(fizzbuzz.generate(3)).toBe("3");
    });

    test("15を渡したら文字列15を返す", () => {
      expect(fizzbuzz.generate(15)).toBe("15");
    });
  });

  describe("タイプ3の場合", () => {
    let fizzbuzz: FizzBuzz;

    beforeEach(() => {
      fizzbuzz = new FizzBuzz(FizzBuzzType.create(FizzBuzzType.TYPE_03));
    });

    test("3を渡したら文字列3を返す", () => {
      expect(fizzbuzz.generate(3)).toBe("3");
    });

    test("15を渡したらFizzBuzzを返す", () => {
      expect(fizzbuzz.generate(15)).toBe("FizzBuzz");
    });
  });

  describe("ファクトリメソッド", () => {
    test("TYPE_01 を指定すると FizzBuzzType01 が返る", () => {
      const type = FizzBuzzType.create(FizzBuzzType.TYPE_01);
      expect(type).toBeInstanceOf(FizzBuzzType01);
    });

    test("未定義のタイプを指定するとエラーが発生する", () => {
      expect(() => FizzBuzzType.create(99)).toThrow("未定義のタイプ: 99");
    });
  });

  describe("デフォルトコンストラクタ", () => {
    test("引数なしで生成するとタイプ1として動作する", () => {
      const fizzbuzz = new FizzBuzz();
      expect(fizzbuzz.generate(3)).toBe("Fizz");
      expect(fizzbuzz.generate(15)).toBe("FizzBuzz");
    });
  });
});
```

### テスト実行結果

```
 ✓ test/fizzbuzz.test.ts (19 tests) 18ms
   ✓ FizzBuzz > タイプ1の場合 > 1を渡したら文字列1を返す
   ✓ FizzBuzz > タイプ1の場合 > 3を渡したらFizzを返す
   ✓ FizzBuzz > タイプ1の場合 > 5を渡したらBuzzを返す
   ✓ FizzBuzz > タイプ1の場合 > 15を渡したらFizzBuzzを返す
   ✓ FizzBuzz > タイプ2の場合 > 3を渡したら文字列3を返す
   ✓ FizzBuzz > タイプ2の場合 > 15を渡したら文字列15を返す
   ✓ FizzBuzz > タイプ3の場合 > 3を渡したら文字列3を返す
   ✓ FizzBuzz > タイプ3の場合 > 15を渡したらFizzBuzzを返す
   ✓ FizzBuzz > ファクトリメソッド > 未定義のタイプを指定するとエラー

 Test Files  1 passed (1)
      Tests  19 passed (19)
```

## 7.7 各言語の OOP 比較

| 概念 | TypeScript | Java | Python |
|------|-----------|------|--------|
| 抽象クラス | `abstract class` | `abstract class` | `abc.ABC` + `@abstractmethod` |
| 抽象メソッド | `abstract method()` | `abstract method()` | `@abstractmethod` |
| 継承 | `extends` | `extends` | `class Sub(Base)` |
| アクセス修飾子 | `private`, `protected`, `public` | `private`, `protected`, `public` | `_` 命名規約 |
| 不変フィールド | `readonly` | `final` | `@property`（setter なし） |
| インスタンス判定 | `instanceof` | `instanceof` | `isinstance()` |

## 7.8 まとめ

この章で学んだこと:

1. **カプセル化**: `private readonly` でフィールドを保護し、getter で安全にアクセス
2. **ポリモーフィズム**: `abstract class` と `extends` による型階層で条件分岐を排除
3. **Strategy パターン**: 実行時にアルゴリズムを切り替える設計パターン
4. **ファクトリメソッド**: `FizzBuzzType.create()` による生成の集約

次の章では、値オブジェクト、ファーストクラスコレクション、コマンドパターンなど、さらに多くのデザインパターンを適用していきます。
