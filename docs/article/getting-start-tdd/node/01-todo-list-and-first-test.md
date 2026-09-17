---
type: Article
title: "第 1 章: TODO リストと最初のテスト"
description: "プログラムを作成するにあたって、まず何をすればよいでしょうか？私たちは、仕様を確認して TODO リスト を作るところから始めます。"
tags: [article, getting-start-tdd, node]
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

今回 TypeScript のテスティングフレームワークには [Vitest](https://vitest.dev/) を利用します。Vitest は Vite をベースにした高速なテストフレームワークで、TypeScript を追加設定なしでサポートします。

### 開発環境のセットアップ

npm プロジェクトに Vitest と TypeScript を追加して、テスト環境をセットアップします。

```json
// package.json
{
  "name": "fizzbuzz",
  "version": "0.1.0",
  "description": "FizzBuzz TDD project for Node (JS/TS)",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "vitest run",
    "test:watch": "vitest",
    "test:coverage": "vitest run --coverage"
  },
  "devDependencies": {
    "typescript": "^5.7",
    "vitest": "^3.2"
  }
}
```

`"type": "module"` を指定することで、ES Modules を使用します。

TypeScript の設定ファイルを作成します。

```json
// tsconfig.json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ES2022",
    "moduleResolution": "bundler",
    "lib": ["ES2022"],
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true
  },
  "include": ["src/**/*.ts", "test/**/*.ts"],
  "exclude": ["node_modules", "dist"]
}
```

依存パッケージをインストールします。

```bash
$ npm install
```

### 環境確認テスト

環境が正しく設定されていることを確認するため、簡単なテストを書きます。

```typescript
// test/hello.test.ts
import { describe, test, expect } from "vitest";

describe("Hello", () => {
  test("greeting", () => {
    const greeting = (): string => "hello world";
    expect(greeting()).toBe("hello world");
  });
});
```

Vitest では `describe`、`test`、`expect` を `vitest` から明示的にインポートします。

テストを実行します。

```bash
$ npx vitest run
```

```
 ✓ test/hello.test.ts (1 test) 1ms

 Test Files  1 passed (1)
      Tests  1 passed (1)
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

テストコードを日本語で記述します。Vitest では `test` 関数の第 1 引数にテスト名を自由に書けるため、日本語のテスト名を使うことでドキュメントとしての可読性が上がります。

```typescript
// test/fizzbuzz.test.ts
import { describe, test, expect, beforeEach } from "vitest";
import { FizzBuzz } from "../src/fizzbuzz";

describe("FizzBuzz", () => {
  let fizzbuzz: FizzBuzz;

  beforeEach(() => {
    fizzbuzz = new FizzBuzz();
  });

  test("1を渡したら文字列1を返す", () => {
    expect(fizzbuzz.generate(1)).toBe("1");
  });
});
```

### Red: テストを実行して失敗させる

テストを実行します。

```bash
$ npx vitest run
```

```
 FAIL  test/fizzbuzz.test.ts
  FizzBuzz
    ✕ 1を渡したら文字列1を返す
      Error: Failed to resolve import "../src/fizzbuzz"
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

```typescript
// src/fizzbuzz.ts
export class FizzBuzz {
  generate(number: number): string {
    return "1";
  }
}
```

テストを実行します。

```bash
$ npx vitest run
```

```
 ✓ test/fizzbuzz.test.ts (1 test) 2ms

 Test Files  1 passed (1)
      Tests  1 passed (1)
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
