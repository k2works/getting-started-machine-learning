---
type: Article
title: "第 9 章: SOLID 原則とモジュール設計"
description: "前章までに多くのデザインパターンを適用しましたが、すべてのクラスが 1 つのファイルに詰め込まれた状態です。"
tags: [article, getting-start-tdd, node]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第 9 章: SOLID 原則とモジュール設計

## 9.1 はじめに

前章までに多くのデザインパターンを適用しましたが、すべてのクラスが 1 つのファイルに詰め込まれた状態です。この章では **SOLID 原則** の観点からコードを検証し、責務に基づいた **モジュール設計** を行います。

## 9.2 SOLID 原則の検証

### 単一責任原則（SRP: Single Responsibility Principle）

> クラスが変更される理由は一つでなければならない

| クラス | 責務 | 変更理由 |
|--------|------|---------|
| `FizzBuzzValue` | FizzBuzz の結果値を表現 | 値の表現方法が変わるとき |
| `FizzBuzzList` | FizzBuzz の結果コレクションを管理 | コレクション操作が変わるとき |
| `FizzBuzzType01` | タイプ 1 の変換ルール | タイプ 1 のルールが変わるとき |
| `FizzBuzzType02` | タイプ 2 の変換ルール | タイプ 2 のルールが変わるとき |
| `FizzBuzzType03` | タイプ 3 の変換ルール | タイプ 3 のルールが変わるとき |
| `FizzBuzzValueCommand` | 単一値の生成操作 | 値の生成方法が変わるとき |
| `FizzBuzzListCommand` | リストの生成操作 | リストの生成方法が変わるとき |

各クラスが 1 つの責務を持ち、変更理由は 1 つです。SRP を満たしています。

### 開放閉鎖原則（OCP: Open-Closed Principle）

> ソフトウェアエンティティは拡張に対して開いていて、修正に対して閉じている

新しいタイプ（例: タイプ 4）を追加する場合:

1. `FizzBuzzType04` クラスを **新規作成** する
2. `FizzBuzzType.create()` の switch 文に 1 行 **追加** する

既存の `FizzBuzzType01`〜`03` は一切変更しません。OCP を満たしています。

### 依存関係逆転の原則（DIP: Dependency Inversion Principle）

> 上位レベルのモジュールは下位レベルのモジュールに依存してはならない。両方とも抽象に依存すべき

```
FizzBuzzValueCommand ──→ FizzBuzzType (抽象)
FizzBuzzListCommand  ──→ FizzBuzzType (抽象)
                              ↑
                    FizzBuzzType01, Type02, Type03
```

- コマンド（上位）は抽象クラス `FizzBuzzType` に依存
- 具体的な Type01〜03 には直接依存しない
- DIP を満たしています

## 9.3 モジュール設計

### 設計方針

責務に基づいて 3 つのレイヤーに分割します。

| レイヤー | 配置先 | 責務 |
|---------|-------|------|
| **ドメインモデル** | `domain/model/` | 値オブジェクト、コレクション |
| **ドメインタイプ** | `domain/type/` | ビジネスルール（FizzBuzz 変換） |
| **アプリケーション** | `application/` | 操作の実行（コマンド） |

### ディレクトリ構成

```
src/
├── index.ts                          (エントリポイント)
└── fizzbuzz/
    ├── index.ts                      (バレルファイル: 全クラスを re-export)
    ├── domain/
    │   ├── model/
    │   │   ├── fizz-buzz-value.ts    (FizzBuzzValue)
    │   │   └── fizz-buzz-list.ts     (FizzBuzzList)
    │   └── type/
    │       ├── fizz-buzz-type.ts     (FizzBuzzType abstract + create)
    │       ├── fizz-buzz-type-01.ts  (FizzBuzzType01)
    │       ├── fizz-buzz-type-02.ts  (FizzBuzzType02)
    │       └── fizz-buzz-type-03.ts  (FizzBuzzType03)
    └── application/
        ├── fizz-buzz-command.ts      (FizzBuzzCommand interface)
        ├── fizz-buzz-value-command.ts (FizzBuzzValueCommand)
        └── fizz-buzz-list-command.ts  (FizzBuzzListCommand)
```

### 依存関係

```
application/ ──→ domain/type/ ──→ domain/model/
     ↓                ↓                 ↓
  コマンド          タイプ            値・コレクション
```

- `domain/model/` は他のモジュールに依存しない（最も安定）
- `domain/type/` は `domain/model/` のみに依存
- `application/` は `domain/type/` と `domain/model/` に依存

### バレルファイル（index.ts）

TypeScript では **バレルファイル** を使って、モジュールの公開 API を集約します。

```typescript
// src/fizzbuzz/index.ts
export { FizzBuzzValue } from "./domain/model/fizz-buzz-value.js";
export { FizzBuzzList } from "./domain/model/fizz-buzz-list.js";
export { FizzBuzzType } from "./domain/type/fizz-buzz-type.js";
export { FizzBuzzType01 } from "./domain/type/fizz-buzz-type-01.js";
export { FizzBuzzType02 } from "./domain/type/fizz-buzz-type-02.js";
export { FizzBuzzType03 } from "./domain/type/fizz-buzz-type-03.js";
export type { FizzBuzzCommand } from "./application/fizz-buzz-command.js";
export { FizzBuzzValueCommand } from "./application/fizz-buzz-value-command.js";
export { FizzBuzzListCommand } from "./application/fizz-buzz-list-command.js";
```

利用側は 1 つの import パスですべてのクラスにアクセスできます:

```typescript
import {
  FizzBuzzType,
  FizzBuzzValueCommand,
  FizzBuzzListCommand,
} from "./fizzbuzz/index";
```

## 9.4 エントリポイントの更新

```typescript
// src/index.ts
export {
  FizzBuzzValue,
  FizzBuzzList,
  FizzBuzzType,
  FizzBuzzType01,
  FizzBuzzType02,
  FizzBuzzType03,
  FizzBuzzValueCommand,
  FizzBuzzListCommand,
} from "./fizzbuzz/index.js";
```

## 9.5 テストの再編成

テストもモジュール構造に合わせて分割します。

```
test/
└── fizzbuzz/
    ├── domain/
    │   ├── model/
    │   │   ├── fizz-buzz-value.test.ts
    │   │   └── fizz-buzz-list.test.ts
    │   └── type/
    │       └── fizz-buzz-type.test.ts
    └── application/
        └── fizz-buzz-command.test.ts
```

### fizz-buzz-type.test.ts

```typescript
import { describe, expect, test } from "vitest";
import {
  FizzBuzzType,
  FizzBuzzType01,
  FizzBuzzType02,
  FizzBuzzType03,
} from "../../../../src/fizzbuzz/index";

describe("FizzBuzzType", () => {
  describe("タイプ1の場合", () => {
    const type = FizzBuzzType.create(FizzBuzzType.TYPE_01);

    test("1を渡したら文字列1を返す", () => {
      expect(type.generate(1).toString()).toBe("1");
    });

    test("3を渡したらFizzを返す", () => {
      expect(type.generate(3).toString()).toBe("Fizz");
    });

    test("5を渡したらBuzzを返す", () => {
      expect(type.generate(5).toString()).toBe("Buzz");
    });

    test("15を渡したらFizzBuzzを返す", () => {
      expect(type.generate(15).toString()).toBe("FizzBuzz");
    });
  });

  describe("ファクトリメソッド", () => {
    test("TYPE_01 を指定すると FizzBuzzType01 が返る", () => {
      expect(FizzBuzzType.create(FizzBuzzType.TYPE_01)).toBeInstanceOf(
        FizzBuzzType01,
      );
    });

    test("未定義のタイプを指定するとエラーが発生する", () => {
      expect(() => FizzBuzzType.create(99)).toThrow("未定義のタイプ: 99");
    });
  });
});
```

### fizz-buzz-command.test.ts

```typescript
import { describe, expect, test } from "vitest";
import {
  FizzBuzzListCommand,
  FizzBuzzType,
  FizzBuzzValueCommand,
} from "../../../src/fizzbuzz/index";

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
    expect(result.size).toBe(100);
  });
});
```

### テスト実行結果

```
 ✓ test/fizzbuzz/domain/model/fizz-buzz-value.test.ts (4 tests) 2ms
 ✓ test/fizzbuzz/domain/model/fizz-buzz-list.test.ts (4 tests) 2ms
 ✓ test/fizzbuzz/domain/type/fizz-buzz-type.test.ts (16 tests) 1ms
 ✓ test/fizzbuzz/application/fizz-buzz-command.test.ts (3 tests) 2ms

 Test Files  4 passed (4)
      Tests  27 passed (27)
```

## 9.6 各言語のモジュール設計比較

| 概念 | TypeScript | Java | Python |
|------|-----------|------|--------|
| モジュール単位 | ファイル（ES Modules） | パッケージ | パッケージ |
| 公開制御 | `export` / non-export | `public` / package-private | `__all__` / `_` 規約 |
| バレルファイル | `index.ts` の re-export | パッケージの public class | `__init__.py` |
| 名前空間 | ファイルパス | パッケージ名 | モジュールパス |
| 循環依存対策 | バレルファイルで集約 | パッケージ設計で制御 | `__init__.py` で制御 |

## 9.7 まとめ

第 3 部（章 7〜9）を通じて、手続き的な FizzBuzz を OOP 設計に進化させました。

| 章 | テーマ | 適用したパターン |
|---|--------|---------------|
| 7 | カプセル化とポリモーフィズム | Strategy パターン、ファクトリメソッド |
| 8 | デザインパターンの適用 | Value Object、First-Class Collection、Command パターン |
| 9 | SOLID 原則とモジュール設計 | SRP、OCP、DIP、モジュール分割 |

### Before / After

**Before**（第 2 部終了時）:

```
src/fizzbuzz.ts (1 ファイル、1 クラス)
test/fizzbuzz.test.ts (1 ファイル)
```

**After**（第 3 部終了時）:

```
src/fizzbuzz/
├── domain/model/    (値オブジェクト、コレクション)
├── domain/type/     (ビジネスルール)
└── application/     (コマンド)

test/fizzbuzz/
├── domain/model/    (値・コレクションのテスト)
├── domain/type/     (タイプのテスト)
└── application/     (コマンドのテスト)
```

次の第 4 部では、関数型プログラミングの観点から FizzBuzz を再構成し、高階関数、不変データ、型安全なエラーハンドリングを学びます。
