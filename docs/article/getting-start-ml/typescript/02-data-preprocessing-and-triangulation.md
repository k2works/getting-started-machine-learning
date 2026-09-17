---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "欠損値を含む iris データを csv-parse で型付きのレコードに読み込み、訓練データの平均値で補完する。シード付きの乱数生成器（mulberry32）とシャッフルを自作し、ジェネリクスの分割を TDD で実装する。"
tags: [article,getting-start-ml,typescript]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T10:08:36Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

第 1 章では、欠損のないきれいなデータを読み込み、人間が書いたルールで判定しました。現実のデータには、空欄（欠損値）が混ざっています。

この章では、アヤメ（iris）のデータを題材に、欠損値を補完し、データを **訓練データ** と **テストデータ** に分けるところまでを TDD で実装します。

[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md)・[Kotlin 版の第 2 章](../kotlin/02-data-preprocessing-and-triangulation.md) と同じ TODO リストで進めます。ただし TypeScript 版には、pandas や Kotlin DataFrame に当たるデータフレームのライブラリを使いません。1 行を型付きのレコード（オブジェクト）で表し、欠損値を `number | null` で表します。また、JavaScript の `Math.random` はシードを指定できないので、**シード付きの乱数生成器** を自分で作ります。

## 2.2 題材とデータ

### iris.csv

`iris.csv` には、アヤメの花 150 件の測定値と品種が記録されています。

| 列 | 意味 | TypeScript 版での型 |
|----|------|-------------------|
| がく片長さ | がく片の長さ | `number \| null` |
| がく片幅 | がく片の幅 | `number \| null` |
| 花弁長さ | 花弁の長さ | `number \| null` |
| 花弁幅 | 花弁の幅 | `number \| null` |
| 種類 | 品種（3 種類が 50 件ずつ） | `string` |

特徴量の 4 列には合わせて 7 件の欠損値があります。

### 訓練データとテストデータ

モデルの良し悪しは、学習に使っていないデータでどれだけ当たるかで測ります。そこで、150 件を 7 対 3 の 105 件（訓練データ）と 45 件（テストデータ）に分けます。欠損値を補完する平均値は **訓練データだけ** から求め、その値で両方を補完します。データ全体の平均値を使うと、テストデータの情報が学習側に漏れるためです（データリーク）。

### Python 版・Kotlin 版と数値が変わる理由

分け方の手順（シード付きでシャッフルし、テストデータの件数を切り上げる）は Python 版・Kotlin 版と同じですが、乱数生成器が違うので、どの行がテストデータに入るかは一致しません。件数は同じ 105 件と 45 件ですが、補完に使う平均値や、第 3 章以降の正解率などの数値は変わります。

## 2.3 開発環境の準備

CSV の読み込みに [csv-parse](https://csv.js.org/parse/) 7.0.2 を使います。

```bash
npm install csv-parse@7.0.2
```

`.npmrc` の `save-exact=true` により、`package.json` には範囲ではなく正確な版が書かれます。

```json
  "dependencies": {
    "csv-parse": "7.0.2"
  }
```

第 1 章では CSV を自分で分割しましたが、値に引用符や改行が含まれる CSV まで正しく読むのは簡単ではありません。この章からはライブラリに任せます。選定の理由は [ADR 003](../../../adr/003-typescript-ml-libraries.md) を参照してください。

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] iris.csv を読み込む
  - [ ] BOM 付き CSV の列名を BOM なしのプロパティ名にし、数値の列を数値にする
  - [ ] 空欄を欠損値（null）として読み込む
- [ ] 列ごとの欠損値の数を数える
- [ ] 列ごとの平均値を求める
- [ ] 欠損値を補完する
  - [ ] 列ごとに指定した値で補完する
  - [ ] 元の配列は変更しない
- [ ] 特徴量と正解ラベルに分ける
- [ ] シード付きの乱数生成器を作る
- [ ] 訓練データとテストデータに分ける
  - [ ] テストデータの割合どおりの件数に分ける
  - [ ] すべての行を重複なくどちらかに入れる
  - [ ] 特徴量と正解ラベルの対応を保つ
  - [ ] 同じシードなら同じ分け方になる
  - [ ] シードが違えば違う分け方になる
- [ ] 訓練データの平均値で両方を補完する
- [ ] 実データで前処理の結果を表示する

## 2.5 CSV を型付きのレコードに読み込む

### 学習用テストで csv-parse の振る舞いを確かめる

csv-parse が BOM や空欄をどう扱うかは、使う前には分かりません。ライブラリの振る舞いを確かめるテスト（学習用テスト）を書きます。

> 学習用テスト
>
> 外部のソフトウェアのテストを書くべきだろうか——そのソフトウェアに対して新しいことを初めて行おうとした段階で書いてみよう。
>
> — テスト駆動開発

```typescript
// test/chapter02/iris-preprocessing.test.ts
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { loadIris } from "../../src/chapter02/iris-preprocessing.ts";

const HEADER = "\uFEFFがく片長さ,がく片幅,花弁長さ,花弁幅,種類\n";

function writeCsv(rows: string): string {
  const csvFile = join(mkdtempSync(join(tmpdir(), "iris-")), "iris.csv");
  writeFileSync(csvFile, HEADER + rows);
  return csvFile;
}

describe("loadIris", () => {
  it("BOM 付き CSV の列名を BOM なしのプロパティ名にし、数値の列を数値にする", () => {
    const rows = loadIris(writeCsv("0.1,0.2,0.3,0.4,Iris-setosa\n"));

    expect(rows).toEqual([
      { がく片長さ: 0.1, がく片幅: 0.2, 花弁長さ: 0.3, 花弁幅: 0.4, 種類: "Iris-setosa" },
    ]);
  });

  it("空欄は欠損値の null として読み込む", () => {
    const rows = loadIris(writeCsv("0.1,,0.3,0.4,Iris-setosa\n"));

    expect(rows[0]?.がく片幅).toBeNull();
  });
});
```

- プロパティ名には、CSV の列名をそのまま使います。`がく片長さ` のような日本語も TypeScript の識別子として使えるので、Python 版・Kotlin 版と同じ列名でコードを書けます
- `rows[0]?.がく片幅` の `?.` は、左辺が `undefined` なら `undefined` を返すオプショナルチェーンです。`noUncheckedIndexedAccess` により `rows[0]` の型は `IrisRow | undefined` なので、そのままではプロパティを参照できません

```text
 FAIL  test/chapter02/iris-preprocessing.test.ts [ test/chapter02/iris-preprocessing.test.ts ]
Error: Cannot find module '../../src/chapter02/iris-preprocessing.ts' imported from test/chapter02/iris-preprocessing.test.ts
```

### 列を型で表す

```typescript
// src/chapter02/iris-preprocessing.ts
import { readFileSync } from "node:fs";
import { parse } from "csv-parse/sync";

export const FEATURES = ["がく片長さ", "がく片幅", "花弁長さ", "花弁幅"] as const;
export type Feature = (typeof FEATURES)[number];
export const TARGET = "種類";

export type IrisRow = Record<Feature, number | null> & { [TARGET]: string };

export function loadIris(csvFile: string): IrisRow[] {
  return parse<IrisRow>(readFileSync(csvFile), {
    bom: true,
    columns: true,
    cast: (value, context) => {
      if (context.column === TARGET) {
        return value;
      }
      return value === "" ? null : Number(value);
    },
  });
}
```

列の型は、値の配列から導いています。

- `as const` を付けると、`FEATURES` の型は `string[]` ではなく、4 つの文字列そのものを並べた読み取り専用の型になります
- `(typeof FEATURES)[number]` は、その配列の要素の型、つまり `"がく片長さ" | "がく片幅" | "花弁長さ" | "花弁幅"` というユニオン型です。列名の一覧を 1 か所に書けば、型も値も同じところから作れます
- `Record<Feature, number | null>` は、4 つの列名をキーに、`number | null` を値に持つオブジェクトの型です。`& { [TARGET]: string }` で品種の列を加えています

csv-parse の設定です。

- `bom: true` で BOM を取り除き、`columns: true` で 1 行目を列名として各行をオブジェクトにします
- `cast` は、値ごとに呼ばれる変換の関数です。品種の列は文字列のまま、空欄は `null`、それ以外は数値にします

### Red: 学習用テストが見つけた振る舞い

ところが、この実装では 2 つのテストがどちらも失敗しました。

```text
     × BOM 付き CSV の列名を BOM なしのプロパティ名にし、数値の列を数値にする 13ms
     × 空欄は欠損値の null として読み込む 3ms
AssertionError: expected [ { NaN: NaN } ] to deeply equal [ { 'がく片長さ': 0.1, 'がく片幅': 0.2, …(3) } ]
AssertionError: expected undefined to be null
```

読み込んだ結果が `{ NaN: NaN }` になっています。`cast` は値の行だけでなく、**列名の行（ヘッダー）にも適用される** ためです。列名の `"がく片長さ"` が `Number("がく片長さ")`、つまり `NaN` に変換され、すべての列が `NaN` という名前になってしまいました。変換の関数に渡される `context` には、ヘッダーの行かどうかを表す `header` があるので、ヘッダーの行は変換しないようにします。

```typescript
      if (context.header || context.column === TARGET) {
        return value;
      }
```

```text
 Test Files  1 passed (1)
      Tests  2 passed (2)
```

ドキュメントを読んだだけでは見落としやすい振る舞いを、学習用テストが最初の実行で見つけてくれました。

### 型は実行時に確かめられていない

`parse<IrisRow>(...)` の `<IrisRow>` は、「戻り値を `IrisRow[]` として扱う」という宣言にすぎません。TypeScript の型は実行時には消えるので、CSV の中身が本当にこの形かどうかは、どこでも確かめられていません。列名を間違えた CSV を読んでも、型チェックは通ってしまいます。

ここでは、読み込みの形を学習用テストで確かめることで補っています。外部から来るデータを実行時に検証する方法は、第 15 章の API で扱います。

## 2.6 平均値で欠損値を補完する

### 列ごとの欠損値の数

```typescript
describe("countMissing", () => {
  it("列ごとの欠損値の数を数える", () => {
    const rows = [
      { がく片長さ: 0.1, がく片幅: 0.2, 種類: "Iris-setosa" },
      { がく片長さ: null, がく片幅: 0.3, 種類: "Iris-setosa" },
      { がく片長さ: null, がく片幅: null, 種類: "Iris-virginica" },
    ];

    expect(countMissing(rows, ["がく片長さ", "がく片幅", "種類"])).toEqual({
      がく片長さ: 2,
      がく片幅: 1,
      種類: 0,
    });
  });
});
```

```text
     × 列ごとの欠損値の数を数える 4ms
TypeError: countMissing is not a function
```

```typescript
export function countMissing<K extends string>(
  rows: readonly Record<K, unknown>[],
  columns: readonly K[],
): Record<K, number> {
  return Object.fromEntries(
    columns.map((column) => [column, rows.filter((row) => row[column] === null).length]),
  ) as Record<K, number>;
}
```

- `<K extends string>` は **ジェネリクス** です。`K` は呼び出し側が渡した列名から推論され、テストでは `"がく片長さ" | "がく片幅" | "種類"` になります。存在しない列名を `columns` に書くと、型チェックで誤りになります
- `readonly Record<K, unknown>[]` は、この関数が配列を変更しないことを型で約束します
- `Object.fromEntries` は `[キー, 値]` の組の配列からオブジェクトを作ります。ただし戻り値の型は「文字列をキーに持つ何か」にしかならないので、`as Record<K, number>` で型を指定しています。`as` は型チェックを通すための **型アサーション** で、中身が本当にその型かは確かめません。使う場所を小さく保ちます

### 平均値と補完

補完に使う値は引数で受け取ります。元の配列を変えないことも、テストで約束します。

```typescript
describe("columnMeans", () => {
  it("欠損値を除いて列ごとの平均値を求める", () => {
    const rows = [
      { がく片長さ: 0.1, がく片幅: 0.2 },
      { がく片長さ: null, がく片幅: 0.4 },
      { がく片長さ: 0.3, がく片幅: 0.9 },
    ];

    const means = columnMeans(rows, ["がく片長さ", "がく片幅"]);

    expect(means.がく片長さ).toBeCloseTo(0.2, 12);
    expect(means.がく片幅).toBeCloseTo(0.5, 12);
  });
});

describe("fillMissing", () => {
  it("欠損値を列ごとに指定した値で補完する", () => {
    const rows = [
      { がく片長さ: 0.1, がく片幅: null },
      { がく片長さ: null, がく片幅: 0.4 },
    ];

    const filled = fillMissing(rows, { がく片長さ: 0.2, がく片幅: 0.5 });

    expect(filled).toEqual([
      { がく片長さ: 0.1, がく片幅: 0.5 },
      { がく片長さ: 0.2, がく片幅: 0.4 },
    ]);
  });

  it("元の配列は変更しない", () => {
    const rows = [{ がく片長さ: null }];

    fillMissing(rows, { がく片長さ: 0.2 });

    expect(rows).toEqual([{ がく片長さ: null }]);
  });
});
```

```text
     × 欠損値を除いて列ごとの平均値を求める 4ms
     × 欠損値を列ごとに指定した値で補完する 1ms
     × 元の配列は変更しない 0ms
TypeError: columnMeans is not a function
TypeError: fillMissing is not a function
```

```typescript
export function columnMeans<K extends string>(
  rows: readonly Record<K, number | null>[],
  columns: readonly K[],
): Record<K, number> {
  return Object.fromEntries(
    columns.map((column) => {
      const values = rows.map((row) => row[column]).filter((value) => value !== null);
      return [column, values.reduce((sum, value) => sum + value, 0) / values.length];
    }),
  ) as Record<K, number>;
}

export function fillMissing<K extends string>(
  rows: readonly Record<K, number | null>[],
  values: Record<K, number>,
): Record<K, number>[] {
  return rows.map(
    (row) =>
      Object.fromEntries(
        Object.entries(row).map(([column, value]) => [column, value ?? values[column as K]]),
      ) as Record<K, number>,
  );
}
```

- `.filter((value) => value !== null)` の後の `values` は、`(number | null)[]` ではなく `number[]` になります。TypeScript は、`value !== null` を返すコールバックから「`null` を取り除く」という型の絞り込みを推論します。そのおかげで、続く `reduce` で `sum + value` と足し算できます
- `fillMissing` の戻り値の型は `Record<K, number>[]` です。引数の `number | null` が、補完の後は `number` になることを型で表しています。第 3 章の決定木は `null` を受け付けないので、補完していないデータを渡すと型チェックで誤りになります
- `value ?? values[column as K]` は、`value` が `null` のときだけ補完の値を使います。`Object.entries` はキーを `string` として返すので、ここでも `as K` を使っています
- `map` と `Object.fromEntries` は、どちらも新しい配列・オブジェクトを作ります。元の配列やオブジェクトを書き換えないので、「元の配列は変更しない」のテストも通ります

```text
 Test Files  1 passed (1)
      Tests  6 passed (6)
```

## 2.7 特徴量と正解ラベルに分ける

```typescript
describe("splitFeaturesAndTarget", () => {
  it("特徴量の列と正解ラベルの列に分ける", () => {
    const rows = [
      { がく片長さ: 0.1, がく片幅: 0.2, 花弁長さ: 0.3, 花弁幅: 0.4, 種類: "Iris-setosa" },
      { がく片長さ: 0.5, がく片幅: null, 花弁長さ: 0.7, 花弁幅: 0.8, 種類: "Iris-virginica" },
    ];

    const { x, t } = splitFeaturesAndTarget(rows);

    expect(x).toEqual([
      { がく片長さ: 0.1, がく片幅: 0.2, 花弁長さ: 0.3, 花弁幅: 0.4 },
      { がく片長さ: 0.5, がく片幅: null, 花弁長さ: 0.7, 花弁幅: 0.8 },
    ]);
    expect(t).toEqual(["Iris-setosa", "Iris-virginica"]);
  });
});
```

```text
     × 特徴量の列と正解ラベルの列に分ける 4ms
TypeError: splitFeaturesAndTarget is not a function
```

```typescript
export function splitFeaturesAndTarget(rows: readonly IrisRow[]): {
  x: Record<Feature, number | null>[];
  t: string[];
} {
  return {
    x: rows.map(({ [TARGET]: _target, ...features }) => features),
    t: rows.map((row) => row[TARGET]),
  };
}
```

`({ [TARGET]: _target, ...features }) => features` は、各行から品種の列を取り除き、残りの列を `features` に集めます。`[TARGET]` は、定数 `TARGET` の値（`"種類"`）をプロパティ名に使う書き方です。

テストは通りましたが、ESLint が次の誤りを報告しました。

```text
  61:30  error  '_target' is defined but never used  @typescript-eslint/no-unused-vars
```

`_target` は取り除くためだけに受け取った変数なので、使わないのは意図どおりです。typescript-eslint の `no-unused-vars` には、残りの要素（`...features`）を使うために取り除いた変数を対象外にする `ignoreRestSiblings` という設定があります。`eslint.config.mjs` に追加しました。

```javascript
  {
    rules: {
      // 分割代入で一部のプロパティを取り除き、残りを使う書き方を許す
      "@typescript-eslint/no-unused-vars": [
        "error",
        { ignoreRestSiblings: true },
      ],
    },
  },
```

## 2.8 訓練データとテストデータに分ける

### シード付きの乱数生成器

データをシャッフルするには乱数が必要です。JavaScript の `Math.random` はシードを指定できないので、実行のたびに分け方が変わってしまいます。そこで、シードから決まった乱数列を作る **疑似乱数生成器** を作ります。

まず、「同じシードなら同じ乱数列」というテストを書きます。

```typescript
// test/chapter02/random.test.ts
import { describe, expect, it } from "vitest";
import { createRandom } from "../../src/chapter02/random.ts";

function take(random: () => number, count: number): number[] {
  return Array.from({ length: count }, () => random());
}

describe("createRandom", () => {
  it("同じシードなら同じ乱数列を返す", () => {
    expect(take(createRandom(42), 5)).toEqual(take(createRandom(42), 5));
  });
});
```

`createRandom` は、呼ぶたびに次の乱数を返す **関数を返す関数** にします。`take` は、その関数を指定した回数呼んで配列にするテスト用の関数です。

```text
 FAIL  test/chapter02/random.test.ts [ test/chapter02/random.test.ts ]
Error: Cannot find module '../../src/chapter02/random.ts' imported from test/chapter02/random.test.ts
```

仮実装で、常に 0 を返す関数を返します。

```typescript
// src/chapter02/random.ts
export function createRandom(seed: number): () => number {
  return () => 0;
}
```

同じ値を返し続けるので、「同じシードなら同じ乱数列」のテストは通ってしまいます。三角測量として、「シードが違えば違う乱数列」と「0 以上 1 未満の値」のテストを加えます。

```typescript
  it("シードが違えば違う乱数列を返す", () => {
    expect(take(createRandom(0), 5)).not.toEqual(take(createRandom(1), 5));
  });

  it("0 以上 1 未満の値を返す", () => {
    const values = take(createRandom(7), 1000);

    expect(values.every((value) => value >= 0 && value < 1)).toBe(true);
  });
```

```text
     × シードが違えば違う乱数列を返す 5ms
AssertionError: expected [ +0, +0, +0, +0, +0 ] to not deeply equal [ +0, +0, +0, +0, +0 ]
      Tests  1 failed | 2 passed (3)
```

32 ビットの状態を持つ **mulberry32** というアルゴリズムで実装します。

```typescript
/**
 * シード付きの疑似乱数生成器（mulberry32）。0 以上 1 未満の値を返す関数を作る。
 * 暗号の用途には使えない。
 */
export function createRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let z = state;
    z = Math.imul(z ^ (z >>> 15), z | 1);
    z ^= z + Math.imul(z ^ (z >>> 7), z | 61);
    return ((z ^ (z >>> 14)) >>> 0) / 2 ** 32;
  };
}
```

- 返す関数は、外側の変数 `state` を覚えています（**クロージャ**）。呼ぶたびに `state` を一定の数だけ進め、ビット演算でかき混ぜて 0 以上 1 未満の値にします
- JavaScript の数値は浮動小数点数ですが、`>>> 0` は値を 32 ビットの符号なし整数に、`Math.imul` は 32 ビットの整数どうしの掛け算にそろえます。これで、C 言語などの 32 ビット整数の演算と同じ結果になります
- 最後に `2 ** 32` で割り、32 ビットの整数を 0 以上 1 未満の値にします
- 同じシードから同じ乱数列を作れるということは、シードが分かれば続きの値を予測できるということです。暗号やパスワードの生成には使えません

```text
 Test Files  1 passed (1)
      Tests  3 passed (3)
```

### 参照実装と突き合わせる

テストが通っても、ビット演算の書き間違いで「それらしいが別の」乱数列になっている可能性は残ります。mulberry32 のよく知られた JavaScript の実装（[bryc/code の PRNGs.md](https://github.com/bryc/code/blob/master/jshash/PRNGs.md)）と、8 通りのシードで 1 万個ずつ値を比べ、すべて一致することを確かめました。参照実装が返す最初の 3 つの値を、テストとして残します。

```typescript
  it("mulberry32 の参照実装と同じ値を返す", () => {
    expect(take(createRandom(0), 3)).toEqual([
      0.26642920868471265, 0.0003297457005828619, 0.2232720274478197,
    ]);
  });
```

### シャッフル

乱数を使って配列を並べ替える関数を作ります。

```typescript
describe("shuffle", () => {
  it("要素を失わずに並べ替えた新しい配列を返す", () => {
    const items = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];

    const shuffled = shuffle(items, createRandom(0));

    expect(shuffled.toSorted((a, b) => a - b)).toEqual(items);
    expect(shuffled).not.toEqual(items);
    expect(items).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
  });
});
```

```text
     × 要素を失わずに並べ替えた新しい配列を返す 2ms
TypeError: shuffle is not a function
```

```typescript
/** Fisher–Yates のシャッフル。元の配列は変更せず、並べ替えた新しい配列を返す。 */
export function shuffle<T>(items: readonly T[], random: () => number): T[] {
  const result = [...items];
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [result[i], result[j]] = [result[j] as T, result[i] as T];
  }
  return result;
}
```

- **Fisher–Yates のシャッフル** は、末尾から順に「それより前のどこか」と入れ替えていく方法で、すべての並べ方が同じ確率で出ます
- `[...items]` でコピーしてから並べ替えるので、引数の配列は変わりません
- `toSorted` は、元の配列を変えずに並べ替えた新しい配列を返します（ES2023）
- `result[j] as T` の `as T` は、`noUncheckedIndexedAccess` のためです。添字で取り出した値は `T | undefined` になり、`as` を外すと型チェックで次の誤りになります。`i` と `j` は配列の範囲内なので、ここでは型アサーションで「`undefined` ではない」と伝えています

```text
src/chapter02/random.ts(21,6): error TS2322: Type 'T | undefined' is not assignable to type 'T'.
  'T' could be instantiated with an arbitrary type which could be unrelated to 'T | undefined'.
```

### 分割結果を表す型と仮実装

分割は、特徴量の型 `X` と正解ラベルの型 `T` を型引数に持つジェネリクスにします。第 7 章からは、正解ラベルが文字列ではなく数値（価格や興行収入）になるためです。

```typescript
function numberedDataset(size: number): { x: { x: number }[]; t: string[] } {
  const numbers = Array.from({ length: size }, (_, i) => i);
  return { x: numbers.map((i) => ({ x: i })), t: numbers.map((i) => `label${i}`) };
}

describe("splitTrainTest", () => {
  it("テストデータの割合どおりの件数に分ける", () => {
    const { x, t } = numberedDataset(10);

    const split = splitTrainTest(x, t, 0.3, 0);

    expect([split.xTrain.length, split.xTest.length]).toEqual([7, 3]);
    expect([split.tTrain.length, split.tTest.length]).toEqual([7, 3]);
  });
});
```

```text
     × テストデータの割合どおりの件数に分ける 3ms
TypeError: splitTrainTest is not a function
```

```typescript
export interface TrainTestSplit<X, T> {
  xTrain: X[];
  xTest: X[];
  tTrain: T[];
  tTest: T[];
}

export function splitTrainTest<X, T>(
  x: readonly X[],
  t: readonly T[],
  testSize: number,
  seed: number,
): TrainTestSplit<X, T> {
  return { xTrain: x.slice(0, 7), xTest: x.slice(7), tTrain: t.slice(0, 7), tTest: t.slice(7) };
}
```

### 三角測量: 件数を一般化する

```typescript
  it("件数が変わってもテストデータの割合どおりに分ける", () => {
    const { x, t } = numberedDataset(20);

    const split = splitTrainTest(x, t, 0.25, 0);

    expect([split.xTrain.length, split.xTest.length]).toEqual([15, 5]);
    expect([split.tTrain.length, split.tTest.length]).toEqual([15, 5]);
  });
```

```text
     × 件数が変わってもテストデータの割合どおりに分ける 7ms
AssertionError: expected [ 7, 13 ] to deeply equal [ 15, 5 ]
```

```typescript
  const nTrain = x.length - Math.ceil(x.length * testSize);
  return {
    xTrain: x.slice(0, nTrain),
    xTest: x.slice(nTrain),
    tTrain: t.slice(0, nTrain),
    tTest: t.slice(nTrain),
  };
```

### 三角測量: 並び順に頼らない分け方にする

```typescript
  it("すべての行を重複なく訓練データとテストデータのどちらかに入れる", () => {
    const { x, t } = numberedDataset(10);

    const split = splitTrainTest(x, t, 0.3, 0);

    const numbers = [...split.xTrain, ...split.xTest].map((row) => row.x);
    expect(numbers.toSorted((a, b) => a - b)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
  });

  it("特徴量と正解ラベルの対応を保ったまま分ける", () => {
    const { x, t } = numberedDataset(10);

    const split = splitTrainTest(x, t, 0.3, 0);

    expect(split.xTrain.map((row) => `label${row.x}`)).toEqual(split.tTrain);
    expect(split.xTest.map((row) => `label${row.x}`)).toEqual(split.tTest);
  });

  it("同じシードなら同じ分け方になる", () => {
    const { x, t } = numberedDataset(10);

    const first = splitTrainTest(x, t, 0.3, 42);
    const second = splitTrainTest(x, t, 0.3, 42);

    expect(first.tTest).toEqual(second.tTest);
  });

  it("シードが違えば違う分け方になる", () => {
    const { x, t } = numberedDataset(10);

    const first = splitTrainTest(x, t, 0.3, 0);
    const second = splitTrainTest(x, t, 0.3, 1);

    expect(first.tTest).not.toEqual(second.tTest);
  });
```

```text
     × シードが違えば違う分け方になる 7ms
AssertionError: expected [ 'label7', 'label8', 'label9' ] to not deeply equal [ 'label7', 'label8', 'label9' ]
      Tests  1 failed | 16 passed (17)
```

Python 版・Kotlin 版と同じく、先頭から順に分けるだけでは最後のテストだけが失敗します。

### Green: シード付きの乱数で並べ替える

```typescript
function pick<T>(items: readonly T[], positions: readonly number[]): T[] {
  return positions.map((i) => items[i] as T);
}

export function splitTrainTest<X, T>(
  x: readonly X[],
  t: readonly T[],
  testSize: number,
  seed: number,
): TrainTestSplit<X, T> {
  const positions = shuffle(
    x.map((_, i) => i),
    createRandom(seed),
  );
  const nTrain = x.length - Math.ceil(x.length * testSize);
  const train = positions.slice(0, nTrain);
  const test = positions.slice(nTrain);
  return {
    xTrain: pick(x, train),
    xTest: pick(x, test),
    tTrain: pick(t, train),
    tTest: pick(t, test),
  };
}
```

- 行そのものではなく、行の位置（0〜9）をシャッフルします。同じ位置の並びで特徴量と正解ラベルを取り出すので、対応が崩れません
- `pick` は、位置の配列の順に要素を取り出す関数です

```text
 Test Files  2 passed (2)
      Tests  18 passed (18)
```

## 2.9 前処理をまとめる

```typescript
describe("prepareIris", () => {
  it("訓練データとテストデータのどちらにも欠損値が残らない", () => {
    const csvFile = writeCsv(
      "0.1,,0.3,0.4,Iris-setosa\n" +
        "0.2,0.3,,0.5,Iris-setosa\n" +
        ",0.4,0.5,0.6,Iris-virginica\n" +
        "0.4,0.5,0.6,,Iris-virginica\n",
    );

    const split = prepareIris(csvFile, 0.5, 0);

    expect(Object.values(countMissing(split.xTrain, FEATURES))).toEqual([0, 0, 0, 0]);
    expect(Object.values(countMissing(split.xTest, FEATURES))).toEqual([0, 0, 0, 0]);
  });
});
```

```text
     × 訓練データとテストデータのどちらにも欠損値が残らない 4ms
TypeError: prepareIris is not a function
```

```typescript
export function prepareIris(
  csvFile: string,
  testSize: number,
  seed: number,
): TrainTestSplit<Record<Feature, number>, string> {
  const { x, t } = splitFeaturesAndTarget(loadIris(csvFile));
  const split = splitTrainTest(x, t, testSize, seed);
  const means = columnMeans(split.xTrain, FEATURES);
  return {
    ...split,
    xTrain: fillMissing(split.xTrain, means),
    xTest: fillMissing(split.xTest, means),
  };
}
```

- 戻り値の型 `TrainTestSplit<Record<Feature, number>, string>` は、「特徴量に欠損値が残っておらず、正解ラベルは文字列」の分割を表します
- `{ ...split, xTrain: ..., xTest: ... }` は、`split` のプロパティを写したうえで `xTrain` と `xTest` だけを差し替えた新しいオブジェクトを作ります。Kotlin 版の data class の `copy` に当たります

## 2.10 実データで前処理の結果を表示する

### 実データのテスト

表示のテストも含めて、実データのテストを先に書きました。

```typescript
// test/chapter02/iris-data.test.ts
import { existsSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  FEATURES,
  TARGET,
  countMissing,
  loadIris,
  prepareIris,
} from "../../src/chapter02/iris-preprocessing.ts";
import { main } from "../../src/chapter02/main.ts";
import { dataDir } from "../../src/dataset.ts";

const csvFile = join(dataDir(), "iris.csv");

describe.skipIf(!existsSync(csvFile))("iris.csv の実データ", () => {
  it("実データの列ごとの欠損値の数を数える", () => {
    expect(countMissing(loadIris(csvFile), [...FEATURES, TARGET])).toEqual({
      がく片長さ: 2,
      がく片幅: 1,
      花弁長さ: 2,
      花弁幅: 2,
      種類: 0,
    });
  });

  it("実データを 105 件と 45 件に分けて欠損値を補完する", () => {
    const split = prepareIris(csvFile, 0.3, 0);

    expect([split.xTrain.length, split.xTest.length]).toEqual([105, 45]);
    expect(Object.values(countMissing(split.xTrain, FEATURES))).toEqual([0, 0, 0, 0]);
    expect(Object.values(countMissing(split.xTest, FEATURES))).toEqual([0, 0, 0, 0]);
  });

  it("実行すると前処理の結果を表示する", () => {
    const lines: string[] = [];

    main((line) => lines.push(line));

    expect(lines).toEqual([
      "データ件数: 150",
      "欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0",
      "訓練データ: 105 件, テストデータ: 45 件",
      "補完後の欠損値の数: 訓練データ 0, テストデータ 0",
    ]);
  });
});
```

`[...FEATURES, TARGET]` は、特徴量の列名の配列の後ろに品種の列名を加えた新しい配列です。

```text
 FAIL  test/chapter02/iris-data.test.ts [ test/chapter02/iris-data.test.ts ]
Error: Cannot find module '../../src/chapter02/main.ts' imported from test/chapter02/iris-data.test.ts
```

### 結果を表示する

```typescript
// src/chapter02/main.ts
import { join } from "node:path";
import { dataDir } from "../dataset.ts";
import {
  FEATURES,
  TARGET,
  countMissing,
  loadIris,
  prepareIris,
} from "./iris-preprocessing.ts";

const TEST_SIZE = 0.3;
const SEED = 0;

function sum(counts: Record<string, number>): number {
  return Object.values(counts).reduce((total, count) => total + count, 0);
}

export function main(print: (line: string) => void = console.log): void {
  const csvFile = join(dataDir(), "iris.csv");
  const rows = loadIris(csvFile);
  const missing = countMissing(rows, [...FEATURES, TARGET]);
  const split = prepareIris(csvFile, TEST_SIZE, SEED);
  print(`データ件数: ${rows.length}`);
  print(
    `欠損値の数: ${Object.entries(missing)
      .map(([column, count]) => `${column}=${count}`)
      .join(", ")}`,
  );
  print(
    `訓練データ: ${split.xTrain.length} 件, テストデータ: ${split.xTest.length} 件`,
  );
  print(
    `補完後の欠損値の数: 訓練データ ${sum(countMissing(split.xTrain, FEATURES))}, テストデータ ${sum(countMissing(split.xTest, FEATURES))}`,
  );
}

// node src/chapter02/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
```

```bash
node src/chapter02/main.ts
```

```text
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
補完後の欠損値の数: 訓練データ 0, テストデータ 0
```

```bash
npm test
```

第 2 章のテストは 22 件すべて通ります。データが無い環境では、実データのテスト 3 件がスキップされます。

```bash
ML_DATA_DIR=/nonexistent npx vitest run
```

```text
 Test Files  5 passed | 2 skipped (7)
      Tests  30 passed | 6 skipped (36)
```

第 1 章と合わせた件数です。スキップされた 6 件は、第 1 章と第 2 章の実データのテスト 3 件ずつです。

## 2.11 リファクタリング

### 重複した組み立てをまとめる

`countMissing` と `columnMeans` は、どちらも「列ごとに値を求め、`Object.fromEntries` でオブジェクトに組み立て、`as` で型を付ける」という同じ形をしていました。この組み立てを `byColumn` にまとめ、型アサーションを 1 か所に減らします。

```typescript
function byColumn<K extends string>(
  columns: readonly K[],
  valueOf: (column: K) => number,
): Record<K, number> {
  return Object.fromEntries(
    columns.map((column) => [column, valueOf(column)]),
  ) as Record<K, number>;
}

export function countMissing<K extends string>(
  rows: readonly Record<K, unknown>[],
  columns: readonly K[],
): Record<K, number> {
  return byColumn(
    columns,
    (column) => rows.filter((row) => row[column] === null).length,
  );
}

export function columnMeans<K extends string>(
  rows: readonly Record<K, number | null>[],
  columns: readonly K[],
): Record<K, number> {
  return byColumn(columns, (column) => {
    const values = rows
      .map((row) => row[column])
      .filter((value) => value !== null);
    return values.reduce((sum, value) => sum + value, 0) / values.length;
  });
}
```

### 表示の意図を名前にする

`main` の最後の行は、`sum(countMissing(..., FEATURES))` が 2 回並んで読みにくくなっていました。「補完後に残った欠損値の合計」に名前を付けます。

```typescript
function totalMissing(rows: readonly Record<Feature, number>[]): number {
  return Object.values(countMissing(rows, FEATURES)).reduce(
    (total, count) => total + count,
    0,
  );
}
```

```typescript
  print(
    `補完後の欠損値の数: 訓練データ ${totalMissing(split.xTrain)}, テストデータ ${totalMissing(split.xTest)}`,
  );
```

型の名前 `Feature` を読み込むときは、`import { FEATURES, type Feature, ... }` のように `type` を付けます。`tsconfig.json` の `verbatimModuleSyntax` により、型だけの読み込みを明示する必要があるためです。Node.js の型除去は、`type` の付いた読み込みを取り除いて実行します。

```bash
npm run format
npm run check
```

```text
 Test Files  7 passed (7)
      Tests  36 passed (36)
```

## 2.12 Notebook で探索する

TypeScript 版では Notebook による探索と可視化を扱いません。欠損値の分布や品種ごとの特徴量の違いは、[Python 版の 2.12 節](../python/02-data-preprocessing-and-triangulation.md) か [Kotlin 版の 2.12 節](../kotlin/02-data-preprocessing-and-triangulation.md) を参照してください。

<details>
<summary>この章の完成コード（src/chapter02/iris-preprocessing.ts）</summary>

```typescript
import { readFileSync } from "node:fs";
import { parse } from "csv-parse/sync";
import { createRandom, shuffle } from "./random.ts";

export const FEATURES = [
  "がく片長さ",
  "がく片幅",
  "花弁長さ",
  "花弁幅",
] as const;
export type Feature = (typeof FEATURES)[number];
export const TARGET = "種類";

export type IrisRow = Record<Feature, number | null> & { [TARGET]: string };

export function loadIris(csvFile: string): IrisRow[] {
  return parse<IrisRow>(readFileSync(csvFile), {
    bom: true,
    columns: true,
    cast: (value, context) => {
      if (context.header || context.column === TARGET) {
        return value;
      }
      return value === "" ? null : Number(value);
    },
  });
}

function byColumn<K extends string>(
  columns: readonly K[],
  valueOf: (column: K) => number,
): Record<K, number> {
  return Object.fromEntries(
    columns.map((column) => [column, valueOf(column)]),
  ) as Record<K, number>;
}

export function countMissing<K extends string>(
  rows: readonly Record<K, unknown>[],
  columns: readonly K[],
): Record<K, number> {
  return byColumn(
    columns,
    (column) => rows.filter((row) => row[column] === null).length,
  );
}

export function columnMeans<K extends string>(
  rows: readonly Record<K, number | null>[],
  columns: readonly K[],
): Record<K, number> {
  return byColumn(columns, (column) => {
    const values = rows
      .map((row) => row[column])
      .filter((value) => value !== null);
    return values.reduce((sum, value) => sum + value, 0) / values.length;
  });
}

export function fillMissing<K extends string>(
  rows: readonly Record<K, number | null>[],
  values: Record<K, number>,
): Record<K, number>[] {
  return rows.map(
    (row) =>
      Object.fromEntries(
        Object.entries(row).map(([column, value]) => [
          column,
          value ?? values[column as K],
        ]),
      ) as Record<K, number>,
  );
}

export function splitFeaturesAndTarget(rows: readonly IrisRow[]): {
  x: Record<Feature, number | null>[];
  t: string[];
} {
  return {
    x: rows.map(({ [TARGET]: _target, ...features }) => features),
    t: rows.map((row) => row[TARGET]),
  };
}

export interface TrainTestSplit<X, T> {
  xTrain: X[];
  xTest: X[];
  tTrain: T[];
  tTest: T[];
}

function pick<T>(items: readonly T[], positions: readonly number[]): T[] {
  return positions.map((i) => items[i] as T);
}

export function splitTrainTest<X, T>(
  x: readonly X[],
  t: readonly T[],
  testSize: number,
  seed: number,
): TrainTestSplit<X, T> {
  const positions = shuffle(
    x.map((_, i) => i),
    createRandom(seed),
  );
  const nTrain = x.length - Math.ceil(x.length * testSize);
  const train = positions.slice(0, nTrain);
  const test = positions.slice(nTrain);
  return {
    xTrain: pick(x, train),
    xTest: pick(x, test),
    tTrain: pick(t, train),
    tTest: pick(t, test),
  };
}

export function prepareIris(
  csvFile: string,
  testSize: number,
  seed: number,
): TrainTestSplit<Record<Feature, number>, string> {
  const { x, t } = splitFeaturesAndTarget(loadIris(csvFile));
  const split = splitTrainTest(x, t, testSize, seed);
  const means = columnMeans(split.xTrain, FEATURES);
  return {
    ...split,
    xTrain: fillMissing(split.xTrain, means),
    xTest: fillMissing(split.xTest, means),
  };
}
```

</details>

## 2.13 まとめ

この章では、欠損値を含むデータを型付きのレコードに読み込み、訓練データとテストデータに分けました。

1. **学習用テスト** — csv-parse の `cast` がヘッダーの行にも適用されることを、最初の実行で見つけた
2. **型で列を表す** — `as const` の配列から列名のユニオン型を作り、`Record` とジェネリクスで「補完の前は `number | null`、後は `number`」を型に表した
3. **型の絞り込みと型アサーション** — `filter` で `null` を取り除くと型も絞り込まれる。`Object.fromEntries` や添字アクセスのように型が失われる場所では `as` を使い、使う場所を小さく保った
4. **シード付きの乱数** — mulberry32 と Fisher–Yates のシャッフルを TDD で作り、参照実装の値と突き合わせた
5. **三角測量** — 件数と分け方の性質のテストを重ね、シード付きのシャッフルに一般化した

次の章では、ジニ不純度を使う決定木を自作し、JavaScript の機械学習ライブラリ ml-cart の決定木と結果を突き合わせます。
