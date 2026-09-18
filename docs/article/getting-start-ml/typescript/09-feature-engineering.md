---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・IQR による外れ値の検出・タブ区切りと Shift_JIS の表の結合を TDD で自作し、ml-matrix の標準化が不偏標準偏差で割ることを学習用テストで確かめ、型チェックとテストがそれぞれ見つけた問題から役割分担を学ぶ。"
tags: [article,getting-start-ml,typescript]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-18T00:53:48Z }
---

# 第 9 章: 特徴量エンジニアリング

## 9.1 はじめに

第 7 章と第 8 章では、データの列をほぼそのままモデルに渡しました。しかし、モデルの性能はアルゴリズムよりも「どんな特徴量を渡すか」で大きく変わることがあります。元のデータから、モデルが学びやすい特徴量を作り出す作業を **特徴量エンジニアリング** と呼びます。

この章では、ボストンの住宅価格データを題材に、次の 5 つの技法を TDD で自作します。

- カテゴリ値をダミー変数（0 と 1 の列）にする
- 特徴量を標準化する
- 2 乗の項と交互作用の項（多項式特徴量）を作る
- 外れ値を検出する
- 別の表を結合して特徴量を増やす

標準化は、行列のライブラリ [ml-matrix](https://github.com/mljs/matrix) の `center`・`scale` と突き合わせます。最後に、作った特徴量で線形回帰の決定係数がどう変わるかを実データで測ります。

[Python 版の第 9 章](../python/09-feature-engineering.md)・[Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) と同じ TODO リストで進めます。TypeScript 版では、ジェネリクスと型の絞り込みがレコードの変換にどう効くか、`sort` の既定の比べ方、Node.js で動いても型チェックで止まる標準ライブラリの関数に注目してください。

## 9.2 題材とデータ

学習データの入手と配置は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。この章では 3 つのファイルを使います。

### Boston.csv

ボストン近郊の地域ごとの住宅価格のデータです。100 件、14 列あります。

| 列 | 意味 | 欠損 |
|----|------|------|
| CRIME | 犯罪率の水準（`high`・`low`・`very_low`） | なし |
| ZN・INDUS・AGE・DIS・B | 地域の環境を表す指標 | なし |
| CHAS・TAX | 川沿いかどうか・税率 | なし |
| NOX | 窒素酸化物の濃度 | 1 件 |
| RAD | 高速道路への近さ | 1 件 |
| RM | 住居あたりの平均部屋数 | なし |
| PTRATIO | 生徒と教師の人数比 | なし |
| LSTAT | 低所得者の割合（%） | なし |
| PRICE | 住宅価格（目的変数） | なし |

CRIME は数値ではないので、このままでは線形回帰に渡せません。

第 2 章と同じく csv-parse で読み込み、CRIME だけを文字列のまま、ほかの列を数値（空欄は `null`）にします。Kotlin 版では、整数だけの列と小数の列が別の型（`Int?` と `Double?`）として読み込まれ、それが 9.9 節で問題になりました。JavaScript の数値は `number` の 1 種類だけなので、TypeScript 版ではこの区別がありません。

### bike.tsv と weather.csv

`bike.tsv` は、ある自転車シェアサービスの日ごとの利用者数（`cnt`）と天気 ID（`weather_id`）などを記録した 731 件のデータです。区切り文字がカンマではなく **タブ** です。

`weather.csv` は天気 ID と天気の名前（晴れ・曇り・雨）の対応表で、3 件です。文字コードが UTF-8 ではなく **Shift_JIS** です。

## 9.3 TODO リストの作成

**TODO リスト**:

- [ ] カテゴリ値をダミー変数にする
  - [ ] 先頭を除いたカテゴリを辞書順に求める
  - [ ] カテゴリごとに 0 と 1 の列を作る
  - [ ] カテゴリに無い値はすべての列を 0 にする
- [ ] 特徴量を標準化する
  - [ ] 訓練データから平均と標準偏差を求める
  - [ ] 訓練データの平均と標準偏差で別のデータを標準化する
  - [ ] ml-matrix の `center`・`scale` と突き合わせる
- [ ] 多項式特徴量を作る
  - [ ] 2 乗の項を加える
  - [ ] 2 つの列の積（交互作用の項）を加える
- [ ] IQR で外れ値を検出する
- [ ] タブ区切り・Shift_JIS のファイルを読み込んで結合する
- [ ] 特徴量の組み合わせごとに決定係数を測る

## 9.4 カテゴリ値をダミー変数にする

### カテゴリを求める

CRIME のような文字列の列は、カテゴリごとに「そのカテゴリなら 1、そうでなければ 0」の列に置き換えます。これを **ダミー変数** と呼びます。

カテゴリが 3 つなら、ダミー変数は 2 列で足ります。`low` でも `very_low` でもなければ `high` だと分かるからです。3 列すべて作ると、どれか 1 列が残りの列から計算できてしまい（多重共線性）、線形回帰の係数が求まらなくなります。そこで、辞書順で先頭のカテゴリを除きます。

TDD でテストを 1 つずつ足していく間は、関数をすべて `src/chapter09/feature-engineering.ts` に書きます。技法ごとのファイルに分けるのは 9.11 節です。

```typescript
// test/chapter09/feature-engineering.test.ts
describe("dummyCategories", () => {
  it("先頭を除いたカテゴリを辞書順に返す", () => {
    const crime = ["low", "high", "very_low", "low"];

    expect(dummyCategories(crime)).toEqual(["low", "very_low"]);
  });
});

describe("encodeDummies", () => {
  it("カテゴリごとに 0 と 1 の列を作り元の列を取り除く", () => {
    const rows = [
      { CRIME: "low", RM: 6.1 },
      { CRIME: "high", RM: 5.2 },
      { CRIME: "very_low", RM: 7.3 },
    ];

    const encoded = encodeDummies(rows, "CRIME", ["low", "very_low"]);

    expect(encoded).toEqual([
      { RM: 6.1, CRIME_low: 1, CRIME_very_low: 0 },
      { RM: 5.2, CRIME_low: 0, CRIME_very_low: 0 },
      { RM: 7.3, CRIME_low: 0, CRIME_very_low: 1 },
    ]);
  });
});
```

```text
 FAIL  test/chapter09/feature-engineering.test.ts [ test/chapter09/feature-engineering.test.ts ]
Error: Cannot find module '../../src/chapter09/feature-engineering.ts' imported from test/chapter09/feature-engineering.test.ts
```

どちらもやることが明らかなので、明白な実装で進めます。

```typescript
export function dummyCategories(values: readonly (string | null)[]): string[] {
  return [...new Set(values.filter((value) => value !== null))].sort().slice(1);
}

export function encodeDummies<R extends Record<C, unknown>, C extends string>(
  rows: readonly R[],
  column: C,
  categories: readonly string[],
): (Omit<R, C> & Record<string, number>)[] {
  return rows.map((row) => {
    const { [column]: value, ...rest } = row;
    const flags = Object.fromEntries(
      categories.map((category) => [
        `${column}_${category}`,
        value === category ? 1 : 0,
      ]),
    );
    return { ...rest, ...flags };
  });
}
```

- `values.filter((value) => value !== null)` の結果は、`(string | null)[]` ではなく `string[]` と推論されます。TypeScript 5.5 以降は、`value !== null` のような条件から **型の絞り込みを行う関数**（型述語）を自動で推論するからです
- `new Set(...)` で重複を除き、スプレッド構文 `[...]` で配列に戻してから並べ替えます
- `encodeDummies` の型引数 `C` は列名、`R` は行の型です。`R extends Record<C, unknown>` は「行に列 `C` があること」を表します。存在しない列名を渡すと型チェックで止まります
- `const { [column]: value, ...rest } = row` は、列 `column` の値を `value` に取り出し、残りの列を `rest` に集めます。`rest` の型は `Omit<R, C>`（`R` から列 `C` を除いた型）と推論されます

`toEqual` はプロパティの並び順を比べません。列の並び順は、9.6 節の多項式特徴量のテストで `Object.keys` を使って確かめます。

### カテゴリを引数で受け取る理由

`encodeDummies` がカテゴリの一覧を自分で求めずに引数で受け取るのは、訓練データとテストデータで **同じ列** を作るためです。テストデータに訓練データで見たことのない値が来ても、列は増えずにすべて 0 になるべきです。

```typescript
  it("カテゴリに無い値はすべての列が 0 になる", () => {
    const encoded = encodeDummies([{ CRIME: "unknown" }], "CRIME", [
      "low",
      "very_low",
    ]);

    expect(encoded).toEqual([{ CRIME_low: 0, CRIME_very_low: 0 }]);
  });
```

このテストは実装を変えずに通ります。

```text
      Tests  3 passed (3)
```

## 9.5 特徴量を標準化する

### 標準化とは

Boston データの列は、単位も大きさもばらばらです。RM（部屋数）は 6 前後、TAX（税率）は数百です。**標準化** は、各列から平均を引いて標準偏差で割り、どの列も平均 0・標準偏差 1 にそろえる変換です。

$$z = \frac{x - \text{平均}}{\text{標準偏差}}$$

標準化で大事なのは、平均と標準偏差を **訓練データだけから求め**、その値でテストデータも変換することです。テストデータの平均を使うと、本来は未知であるはずのテストデータの情報がモデルの準備に漏れてしまいます。そこで、求める処理（`fit`）と変換する処理（`transform`）を分けた `Standardizer` クラスを作ります。

### 訓練データから平均と標準偏差を求める

```typescript
describe("Standardizer", () => {
  it("訓練データから列ごとの平均と標準偏差を求める", () => {
    const rows = [
      { RM: 1, LSTAT: 10 },
      { RM: 2, LSTAT: 10 },
      { RM: 3, LSTAT: 40 },
    ];

    const standardizer = Standardizer.fit(rows, ["RM", "LSTAT"]);

    expect(standardizer.means).toEqual({ RM: 2, LSTAT: 20 });
    expect(standardizer.stds.RM).toBeCloseTo(Math.sqrt(2 / 3), 12);
    expect(standardizer.stds.LSTAT).toBeCloseTo(Math.sqrt(200), 12);
  });
});
```

```text
     × 訓練データから列ごとの平均と標準偏差を求める
TypeError: Cannot read properties of undefined (reading 'fit')
      Tests  1 failed | 3 passed (4)
```

モジュールに無い名前を import すると、Vitest の実行では `undefined` になります。`Standardizer` が `undefined` なので、`.fit` を読もうとして失敗しました。

RM の平均は 2 で、平均からの差の 2 乗は 1・0・1 です。その平均 2/3 の平方根が標準偏差です。平均は第 2 章の `columnMeans` をそのまま使えます。

```typescript
import { columnMeans } from "../chapter02/iris-preprocessing.ts";

export class Standardizer<K extends string> {
  readonly means: Record<K, number>;
  readonly stds: Record<K, number>;

  constructor(means: Record<K, number>, stds: Record<K, number>) {
    this.means = means;
    this.stds = stds;
  }

  static fit<K extends string>(
    rows: readonly Record<K, number>[],
    columns: readonly K[],
  ): Standardizer<K> {
    const means = columnMeans(rows, columns);
    const stds = Object.fromEntries(
      columns.map((column) => {
        const squares = rows.map((row) => (row[column] - means[column]) ** 2);
        const variance =
          squares.reduce((sum, square) => sum + square, 0) / rows.length;
        return [column, Math.sqrt(variance)];
      }),
    ) as Record<K, number>;
    return new Standardizer(means, stds);
  }
}
```

- `static` なメソッドは、`Standardizer.fit(rows, columns)` のようにクラス名から呼べます。Python 版の `@classmethod`、Kotlin 版の `companion object` に当たる書き方で、「データから求めて作る」という生成の手段に名前を付けています
- 第 3 章で見たとおり、本リポジトリは `erasableSyntaxOnly` を有効にしているので、コンストラクターの引数にそのまま `readonly` を付けてプロパティにする書き方（パラメータープロパティ）は使えません。プロパティを宣言し、コンストラクターで代入します
- `Object.fromEntries` の戻り値は `{ [k: string]: number }` なので、`as Record<K, number>` で列名の型を付け直しています。`columns` の各列について 1 つずつ組を作っているので、この型の主張は正しいと言えます
- 分散は差の 2 乗の合計を **件数** で割っています。件数 − 1 で割る不偏分散との違いは、この後 ml-matrix と突き合わせるときに問題になります

### 別のデータを標準化する

`transform` は、`fit` で求めた平均と標準偏差をそのまま使って変換します。テストでは、平均 2・標準偏差 0.5 の `Standardizer` を `new` で直接作って確かめます。

```typescript
  it("訓練データの平均と標準偏差で別のデータを標準化する", () => {
    const standardizer = new Standardizer({ RM: 2 }, { RM: 0.5 });
    const other = [{ RM: 1 }, { RM: 2 }, { RM: 4 }];

    expect(standardizer.transform(other)).toEqual([
      { RM: -2 },
      { RM: 0 },
      { RM: 4 },
    ]);
  });
```

```text
     × 訓練データの平均と標準偏差で別のデータを標準化する
TypeError: standardizer.transform is not a function
      Tests  1 failed | 4 passed (5)
```

型チェック（`tsc --noEmit`）では、同じ誤りが型の誤りとして報告されます。

```text
test/chapter09/feature-engineering.test.ts(62,25): error TS2339: Property 'transform' does not exist on type 'Standardizer<"RM">'.
```

`new Standardizer({ RM: 2 }, { RM: 0.5 })` の型引数 `K` は、渡したオブジェクトから `"RM"` と推論されています。

```typescript
  transform<R extends Record<K, number>>(rows: readonly R[]): R[] {
    const columns = Object.keys(this.means) as K[];
    return rows.map((row) => {
      const standardized = { ...row };
      for (const column of columns) {
        standardized[column] = ((row[column] - this.means[column]) /
          this.stds[column]) as R[K];
      }
      return standardized;
    });
  }
```

`transform` は、標準化する列 `K` 以外の列を持つ行も受け取れるように、行の型を型引数 `R` にしています。標準化した後も同じ型 `R` の行が返るので、呼び出し側は残りの列もそのまま使えます。

最初は `as R[K]` を付けずに書きましたが、型チェックが次のエラーを出しました。

```text
src/chapter09/feature-engineering.ts(40,9): error TS2322: Type 'number' is not assignable to type 'R[K]'.
  'number' is assignable to the constraint of type 'R[K]', but 'R[K]' could be instantiated with a different subtype of constraint 'number'.
```

`R` は `Record<K, number>` の部分型なら何でもよいので、`{ RM: 1 }` のように列の型が数値のリテラル型 `1` の行も渡せます。その場合に 1.5 のような値を入れると型が崩れる、というのがこのエラーの意味です。実際には、ここで渡す行はどれも列の型が `number` なので、`as R[K]` で「この値は列の型に合う」と主張しています。

### すべて同じ値の列

CHAS（川沿いかどうか）のように 0 と 1 しかない列は、訓練データの取り方によってはすべて同じ値になります。すると標準偏差が 0 になり、0 での割り算が起きます。

```typescript
  it("すべて同じ値の列は 0 にする", () => {
    const rows = [{ CHAS: 1 }, { CHAS: 1 }, { CHAS: 1 }];

    const standardized = Standardizer.fit(rows, ["CHAS"]).transform(rows);

    expect(standardized).toEqual([{ CHAS: 0 }, { CHAS: 0 }, { CHAS: 0 }]);
  });
```

```text
AssertionError: expected [ { CHAS: NaN }, { CHAS: NaN }, …(1) ] to deeply equal [ { CHAS: +0 }, { CHAS: +0 }, …(1) ]
      Tests  1 failed | 5 passed (6)
```

JavaScript では 0 を 0 で割ると、例外にならずに NaN（非数）になります。Kotlin の `Double` と同じ振る舞いで、JavaScript には整数の型が無いので、Kotlin の `0 / 0` のように例外を投げる場合もありません。黙って NaN が混ざると、後の学習で原因の分かりにくい結果になります。標準偏差が 0 のときは 1 で割るようにします。

```typescript
        const std = Math.sqrt(variance);
        return [column, std === 0 ? 1 : std];
```

### ml-matrix の標準化と突き合わせる

JavaScript の機械学習ライブラリには、scikit-learn の `StandardScaler` に当たる標準化の部品がありません。ml-matrix の行列 `Matrix` には、列ごとに平均を引く `center` と、列ごとに標準偏差で割る `scale` があるので、この 2 つで標準化できるはずです。

```typescript
// test/chapter09/ml-matrix-standardization.test.ts
import { Matrix } from "ml-matrix";
import { describe, expect, it } from "vitest";
import { Standardizer } from "../../src/chapter09/standardizer.ts";

// ml-matrix の center と scale で、1 列の値を列ごとに標準化する
function mlMatrixStandardize(values: readonly number[]): number[] {
  return new Matrix(values.map((value) => [value]))
    .center("column")
    .scale("column")
    .to1DArray();
}
```

import 先の `standardizer.ts` は、9.11 節でファイルを分けた後のものです。`center` と `scale` は行列そのものを書き換え（in-place）、同じ行列を返すので、メソッドをつなげて書けます。最初は、自作の `Standardizer` と同じ値になると考えて、次のテストを書きました。

```typescript
describe("ml-matrix の標準化", () => {
  const train = [5.5, 6.0, 7.5, 6.5];

  it("自作の Standardizer と同じ値になる", () => {
    const rows = train.map((value) => ({ RM: value }));

    const standardized = Standardizer.fit(rows, ["RM"])
      .transform(rows)
      .map((row) => row.RM);

    expect(standardized).toEqual(
      mlMatrixStandardize(train).map((value) => expect.closeTo(value, 12)),
    );
  });
});
```

```text
 FAIL  test/chapter09/ml-matrix-standardization.test.ts > ml-matrix の標準化 > 自作の Standardizer と同じ値になる
AssertionError: expected [ -1.1832159566199232, …(3) ] to deeply equal [ NumberCloseTo{…}, …(3) ]

- Expected
+ Received

  [
-   NumberCloseTo -1.02469507659596 (12 digits),
-   NumberCloseTo -0.43915503282683993 (12 digits),
-   NumberCloseTo 1.3174650984805198 (12 digits),
-   NumberCloseTo 0.14638501094227999 (12 digits),
+   -1.1832159566199232,
+   -0.50709255283711,
+   1.52127765851133,
+   0.1690308509457033,
  ]
```

値が一致しません。比を取ると 1.0247 ÷ 1.1832 ≒ 0.866 で、これは √(3/4) です。訓練データは 4 件なので、「件数 − 1」と「件数」の比の平方根に当たります。ml-matrix は、自作とは違って **不偏標準偏差**（差の 2 乗の合計を件数 − 1 で割る）で割っていると考えられます。Kotlin 版で Tribuo の `MeanStdDevTransformation` と突き合わせたときと同じ食い違いです。

一致を前提にしたテストを、確かめた事実を記録するテストに書き直しました。

```typescript
function closeTo(values: readonly number[]): unknown[] {
  return values.map((value) => expect.closeTo(value, 12));
}

describe("ml-matrix の標準化", () => {
  const train = [5.5, 6.0, 7.5, 6.5];
  const rows = train.map((value) => ({ RM: value }));

  it("ml-matrix の scale は件数から 1 を引いて割る標準偏差を使う", () => {
    const matrix = new Matrix(rows.map((row) => [row.RM]));
    const mean = matrix.mean("column")[0] as number;
    const sampleStd = matrix.standardDeviation("column")[0] as number;

    expect(mlMatrixStandardize(train)).toEqual(
      closeTo(train.map((value) => (value - mean) / sampleStd)),
    );
  });

  it("自作の標準化に件数から決まる係数を掛けると ml-matrix の値になる", () => {
    const standardized = Standardizer.fit(rows, ["RM"]).transform(rows);
    const ratio = Math.sqrt((train.length - 1) / train.length);

    expect(standardized.map((row) => row.RM * ratio)).toEqual(
      closeTo(mlMatrixStandardize(train)),
    );
  });
});
```

`matrix.standardDeviation("column")` も、既定では不偏標準偏差を返します（`{ unbiased: false }` を渡すと件数で割ります）。どちらのテストも通りました。

| ライブラリ | 標準偏差の既定 |
|-----------|--------------|
| 自作の `Standardizer`（scikit-learn の `StandardScaler` と同じ） | 件数で割る |
| ml-matrix の `scale`・`standardDeviation` | 件数 − 1 で割る |
| Kotlin DataFrame の `std`・pandas の `std`・Tribuo（Kotlin 版で確認） | 件数 − 1 で割る |

件数が十分に多ければ 2 つの差は小さくなりますが、Boston の訓練データ 70 件では約 0.7% の差になります。どちらが正しいというものではありません。ライブラリを置き換えるときは、同じ名前の処理でも定義が同じとは限らないので、小さなデータで値を突き合わせてから使います。

この章では、標準化を ml-matrix に置き換えず、自作の `Standardizer` を最終的な実装にします（[ADR 003](../../../adr/003-typescript-ml-libraries.md) の方針）。ml-matrix は列名の無い数値の行列を扱うので、列名付きのレコードとの変換が必要になることと、標準偏差の定義が scikit-learn と違うことが理由です。

## 9.6 多項式特徴量を作る

### 2 乗の項

線形回帰は「特徴量 × 係数」の足し算でしか予測できません。価格が部屋数の 2 乗に比例して増えるような曲線の関係は、部屋数の列だけでは表せません。そこで、部屋数の 2 乗の列を特徴量として加えます。モデルは線形のままでも、曲線の関係を表せるようになります。

```typescript
describe("polynomialFeatures", () => {
  it("1 列なら元の列と 2 乗の列を返す", () => {
    const rows = [{ RM: 2 }, { RM: 3 }];

    expect(polynomialFeatures(rows, ["RM"])).toEqual([
      { RM: 2, "RM^2": 4 },
      { RM: 3, "RM^2": 9 },
    ]);
  });
});
```

```text
     × 1 列なら元の列と 2 乗の列を返す
TypeError: polynomialFeatures is not a function
      Tests  1 failed | 8 passed (9)
```

`RM^2` のように識別子に使えない文字を含む列名は、`"RM^2"` と引用符で囲んでプロパティ名にします。2 乗の列を加えるだけの実装から始めます。

```typescript
export function polynomialFeatures<K extends string>(
  rows: readonly Record<K, number>[],
  columns: readonly K[],
): Record<string, number>[] {
  return rows.map((row) => {
    const features: Record<string, number> = {};
    for (const column of columns) {
      features[column] = row[column];
    }
    for (const column of columns) {
      features[`${column}^2`] = row[column] * row[column];
    }
    return features;
  });
}
```

作る列の名前は実行時に決まるので、戻り値の行は `Record<string, number>` にしています。

### 三角測量: 交互作用の項

2 列を渡したときは、2 乗の項に加えて、2 つの列の積（**交互作用の項**）も作ります。「部屋数が多く、かつ低所得者の割合が低い」ような組み合わせの効果を表すためです。列の並び順も、`Object.keys` で確かめます。JavaScript のオブジェクトは、整数に見えないプロパティ名を追加した順に並べて返します。

```typescript
  it("2 列なら 2 乗の列と 2 つの列の積の列を加える", () => {
    const rows = [
      { RM: 2, LSTAT: 5 },
      { RM: 3, LSTAT: 7 },
    ];

    const features = polynomialFeatures(rows, ["RM", "LSTAT"]);

    expect(Object.keys(features[0] ?? {})).toEqual([
      "RM",
      "LSTAT",
      "RM^2",
      "RM LSTAT",
      "LSTAT^2",
    ]);
    expect(features).toEqual([
      { RM: 2, LSTAT: 5, "RM^2": 4, "RM LSTAT": 10, "LSTAT^2": 25 },
      { RM: 3, LSTAT: 7, "RM^2": 9, "RM LSTAT": 21, "LSTAT^2": 49 },
    ]);
  });
```

`features[0]` の型は、`noUncheckedIndexedAccess` によって `Record<string, number> | undefined` です。`Object.keys` は `undefined` を受け取れないので、`?? {}` で空のオブジェクトに置き換えています。

```text
AssertionError: expected [ 'RM', 'LSTAT', 'RM^2', 'LSTAT^2' ] to deeply equal [ 'RM', 'LSTAT', 'RM^2', …(2) ]

- Expected
+ Received

  [
    "RM",
    "LSTAT",
    "RM^2",
-   "RM LSTAT",
    "LSTAT^2",
  ]
      Tests  1 failed | 9 passed (10)
```

2 乗の項と交互作用の項は、「列の組を、同じ列を 2 回選ぶことも許して選ぶ」ことで一度に作れます。Python 版では標準ライブラリの `itertools.combinations_with_replacement` を使いましたが、JavaScript の標準ライブラリには無いので、ジェネリック関数として自作します。

```typescript
    for (const [left, right] of pairsWithReplacement(columns)) {
      features[termName(left, right)] = row[left] * row[right];
    }
    return features;
  });
}

export function pairsWithReplacement<T>(items: readonly T[]): [T, T][] {
  return items.flatMap((left, i) =>
    items.slice(i).map((right): [T, T] => [left, right]),
  );
}

export function termName(left: string, right: string): string {
  return left === right ? `${left}^2` : `${left} ${right}`;
}
```

- `pairsWithReplacement` は、`i ≤ j` となる位置の組をすべて作ります。`["RM", "LSTAT"]` なら `[RM, RM]`・`[RM, LSTAT]`・`[LSTAT, LSTAT]` の順です。`<T>` の型引数を持たせたので、文字列以外の配列にも使えます
- `flatMap` は、各要素から作った配列を 1 つの配列につなげます
- `for (const [left, right] of ...)` は、2 要素の配列を 2 つの変数に分解して受け取る書き方です

`[T, T]` は、要素が 2 つだと決まった配列を表す **タプル型** です。最初は `.map((right) => [left, right])` と書きましたが、型チェックが次のエラーを出しました。

```text
src/chapter09/feature-engineering.ts(82,3): error TS2322: Type 'T[][]' is not assignable to type '[T, T][]'.
  Type 'T[]' is not assignable to type '[T, T]'.
    Target requires 2 element(s) but source may have fewer.
```

配列リテラル `[left, right]` の型は、何も指定しなければ長さの決まらない `T[]` と推論されます。アロー関数の戻り値の型に `: [T, T]` を書いて、タプルとして扱わせました。タプルにしておくと、`for (const [left, right] of ...)` で取り出した `left` と `right` はどちらも `T` 型になり、`undefined` の可能性を考えずに使えます。

列名を `RM^2`・`RM LSTAT` という形にしたのは、Python 版で突き合わせた scikit-learn の `PolynomialFeatures` の `get_feature_names_out()` と同じ名前にするためです。3 列のときの並びも確かめておきます。

```typescript
  it("3 列なら scikit-learn の PolynomialFeatures と同じ並びで 9 列を作る", () => {
    const rows = [
      { RM: 5.5, LSTAT: 12, PTRATIO: 18 },
      { RM: 6, LSTAT: 4, PTRATIO: 15 },
    ];

    const features = polynomialFeatures(rows, ["RM", "LSTAT", "PTRATIO"]);

    expect(Object.keys(features[0] ?? {})).toEqual([
      "RM",
      "LSTAT",
      "PTRATIO",
      "RM^2",
      "RM LSTAT",
      "RM PTRATIO",
      "LSTAT^2",
      "LSTAT PTRATIO",
      "PTRATIO^2",
    ]);
  });
```

3 列から作られる列は、元の 3 列・2 乗の 3 列・交互作用の 3 列の合計 9 列です。列の数は元の列数の 2 乗に近い速さで増えるので、むやみに作ると学習データの件数に対して特徴量が多くなりすぎます。この影響は 9.9 節で実測します。

## 9.7 外れ値を検出する

他の値から大きく離れた値を **外れ値** と呼びます。ここでは、箱ひげ図でも使われる **IQR（四分位範囲）** による方法を実装します。値を小さい順に並べて 25% の位置の値を第 1 四分位数（Q1）、75% の位置の値を第 3 四分位数（Q3）とし、その差 IQR = Q3 − Q1 を求めます。Q1 − 1.5 × IQR より小さい値と、Q3 + 1.5 × IQR より大きい値を外れ値とみなします。

まず上側の外れ値のテストを書きます。`[1, 2, 3, 4, 100]` なら Q1 = 2、Q3 = 4、IQR = 2 なので、4 + 1.5 × 2 = 7 を超える 100 が外れ値です。

```typescript
describe("iqrOutliers", () => {
  it("第 3 四分位数から IQR の 1.5 倍より大きい値を外れ値とする", () => {
    expect(iqrOutliers([1, 2, 3, 4, 100])).toEqual([
      false,
      false,
      false,
      false,
      true,
    ]);
  });
});
```

```text
     × 第 3 四分位数から IQR の 1.5 倍より大きい値を外れ値とする
TypeError: iqrOutliers is not a function
      Tests  1 failed | 11 passed (12)
```

JavaScript の標準ライブラリには四分位数を求める関数が無いので、pandas の `quantile` の既定と同じく、位置が値と値の間に来たら前後の値から線形補間する `quantile` を作ります。

```typescript
export function quantile(values: readonly number[], q: number): number {
  const sorted = values.toSorted((a, b) => a - b);
  const position = (sorted.length - 1) * q;
  const lower = sorted[Math.floor(position)] as number;
  const upper = sorted[Math.ceil(position)] as number;
  return lower + (upper - lower) * (position - Math.floor(position));
}

export function iqrOutliers(values: readonly number[], k = 1.5): boolean[] {
  const q1 = quantile(values, 0.25);
  const q3 = quantile(values, 0.75);
  return values.map((value) => value > q3 + k * (q3 - q1));
}
```

- `toSorted` は、元の配列を変えずに並べ替えた新しい配列を返します（ES2023）。`sort` は元の配列そのものを並べ替えるので、引数の `readonly number[]` には使えません
- `sorted[位置]` の型は、`noUncheckedIndexedAccess` によって `number | undefined` です。位置は 0 から `length - 1` の範囲に収まるので、`as number` で `undefined` にならないと主張しています
- `k = 1.5` は既定値付きの引数で、型は既定値から `number` と推論されます

### 数値として並べ替える

`toSorted` に渡した `(a, b) => a - b` は、2 つの値の大小を数値の差で返す比較関数です。これを省くと、JavaScript は値を **文字列に変換して** 辞書順に並べます。

```typescript
  it("数値として小さい順に並べてから位置を求める", () => {
    expect(quantile([10, 9, 1], 0.5)).toBe(9);
  });
```

比較関数を付けて書いたので、このテストは最初から通りました。比較関数を消すとテストが失敗することも確かめました。

```text
     × 数値として小さい順に並べてから位置を求める
AssertionError: expected 10 to be 9 // Object.is equality
```

`[10, 9, 1]` を文字列として並べると `"1"`・`"10"`・`"9"` の順になり、真ん中の値が 10 になるからです。比較関数を省いた `values.toSorted()` は、型チェックも通ります。`number[]` を文字列として並べることは型の誤りではないので、テストでしか見つけられません。一方、9.4 節の `dummyCategories` はカテゴリの名前（文字列）を辞書順に並べたいので、比較関数を省いた `sort()` で正しく動きます。

値の間に位置が来る場合は、次のテストで確かめています。

```typescript
describe("quantile", () => {
  it("四分位数の位置が値の間にあれば前後の値から線形補間する", () => {
    expect(quantile([4, 1, 3, 2], 0.25)).toBeCloseTo(1.75, 12);
  });
```

### 下側の外れ値

下側の外れ値のテストで三角測量します。

```typescript
  it("第 1 四分位数から IQR の 1.5 倍より小さい値も外れ値とする", () => {
    expect(iqrOutliers([-100, 1, 2, 3, 4])).toEqual([
      true,
      false,
      false,
      false,
      false,
    ]);
  });
```

```text
AssertionError: expected [ false, false, false, false, false ] to deeply equal [ true, false, false, false, false ]
      Tests  1 failed | 14 passed (15)
```

```typescript
  const iqr = q3 - q1;
  return values.map((value) => value < q1 - k * iqr || value > q3 + k * iqr);
```

戻り値は、元の値と同じ順の `boolean[]` です。Python 版の真偽値の Series に当たり、どの行が外れ値かを位置で対応させて使います。

## 9.8 表を結合して特徴量を増やす

### タブ区切りと Shift_JIS

自転車の利用者数の表には天気 ID しかなく、それが晴れなのか雨なのかは別の表にあります。2 つの表を天気 ID で **結合** すれば、天気の名前を特徴量として使えます。まず読み込みです。

```typescript
function writeTempFile(name: string, content: string | Uint8Array): string {
  const file = join(mkdtempSync(join(tmpdir(), "chapter09-")), name);
  writeFileSync(file, content);
  return file;
}

describe("loadBike と loadWeather", () => {
  it("タブ区切りのファイルを読み込む", () => {
    const tsvFile = writeTempFile(
      "bike.tsv",
      "dteday\tweather_id\tcnt\n2030-04-01\t1\t120\n",
    );

    expect(loadBike(tsvFile)).toEqual([
      { dteday: "2030-04-01", weather_id: 1, cnt: 120 },
    ]);
  });

  it("Shift_JIS のファイルを読み込む", () => {
    // Node.js は Shift_JIS で書き出せないので、「晴れ」の Shift_JIS のバイト列を直接書く
    const hare = Buffer.from([0x90, 0xb0, 0x82, 0xea]);
    const csvFile = writeTempFile(
      "weather.csv",
      Buffer.concat([
        Buffer.from("weather_id,weather\n1,"),
        hare,
        Buffer.from("\n"),
      ]),
    );

    expect(loadWeather(csvFile)).toEqual([{ weather_id: 1, weather: "晴れ" }]);
  });
});
```

Node.js の `TextDecoder` は Shift_JIS のバイト列を読めますが、文字列を書き出す `TextEncoder` は UTF-8 にしか対応していません。そこで、テストのファイルは「晴れ」を Shift_JIS で表したバイト列（`0x90 0xB0 0x82 0xEA`）を直接並べて作ります。`Buffer` は Node.js のバイト列の型で、`Buffer.concat` でつなげられます。

```text
     × タブ区切りのファイルを読み込む
     × Shift_JIS のファイルを読み込む
TypeError: loadBike is not a function
TypeError: loadWeather is not a function
      Tests  2 failed | 15 passed (17)
```

行の型を `interface` で定義し、csv-parse の `delimiter` にタブを指定します。天気の表は、まず文字コードを指定せずに読み込んでみます。

```typescript
export interface BikeRow {
  dteday: string;
  weather_id: number;
  cnt: number;
}

export interface WeatherRow {
  weather_id: number;
  weather: string;
}

export function loadBike(tsvFile: string): BikeRow[] {
  return parse<BikeRow>(readFileSync(tsvFile), {
    columns: true,
    delimiter: "\t",
    cast: (value, context) =>
      context.header || context.column === "dteday" ? value : Number(value),
  });
}

export function loadWeather(csvFile: string): WeatherRow[] {
  return parse<WeatherRow>(readFileSync(csvFile), {
    columns: true,
    cast: (value, context) =>
      context.header || context.column === "weather" ? value : Number(value),
  });
}
```

`cast` は第 2 章と同じく、列名（`context.header`）と文字列の列はそのまま、ほかの列を数値にします。`bike.tsv` には `holiday` などの列もありますが、`BikeRow` にはこの章で使う列だけを書いています。

```text
AssertionError: expected [ { weather_id: 1, …(1) } ] to deeply equal [ { weather_id: 1, weather: '晴れ' } ]

- Expected
+ Received

  [
    {
-     "weather": "晴れ",
+     "weather": "����",
      "weather_id": 1,
    },
  ]
      Tests  1 failed | 16 passed (17)
```

Python 版では、pandas が `UnicodeDecodeError` という例外で失敗しました。csv-parse は、バイト列を受け取ると UTF-8 として解釈し、解釈できないバイトを置換文字 `�`（U+FFFD）に置き換えて読み込みを続けます。Kotlin 版と同じく、**エラーにならずに文字化けしたデータが入ってくる** ので、テストが無ければ気付くのが遅れます。テストのデータを実データと同じ Shift_JIS で書き出していたので、この問題を捕まえられました。`TextDecoder` で Shift_JIS として文字列に直してから、csv-parse に渡します。

```typescript
export function loadWeather(csvFile: string): WeatherRow[] {
  const text = new TextDecoder("shift_jis").decode(readFileSync(csvFile));
  return parse<WeatherRow>(text, {
```

### 結合と集計

```typescript
describe("joinWeather", () => {
  it("天気 ID で天気の名前を結合する", () => {
    const bike = [
      { weather_id: 2, cnt: 80 },
      { weather_id: 1, cnt: 120 },
    ];
    const weather = [
      { weather_id: 1, weather: "晴れ" },
      { weather_id: 2, weather: "曇り" },
    ];

    expect(joinWeather(bike, weather)).toEqual([
      { weather_id: 2, cnt: 80, weather: "曇り" },
      { weather_id: 1, cnt: 120, weather: "晴れ" },
    ]);
  });

  it("天気の表に無い天気 ID の行は残さない", () => {
    const bike = [
      { weather_id: 1, cnt: 120 },
      { weather_id: 9, cnt: 30 },
    ];
    const weather = [{ weather_id: 1, weather: "晴れ" }];

    expect(joinWeather(bike, weather).map((row) => row.cnt)).toEqual([120]);
  });
});

describe("meanCountByWeather", () => {
  it("天気ごとの平均利用者数を多い順に求める", () => {
    const joined = [
      { weather: "雨", cnt: 20 },
      { weather: "晴れ", cnt: 100 },
      { weather: "晴れ", cnt: 140 },
    ];

    expect([...meanCountByWeather(joined)]).toEqual([
      ["晴れ", 120],
      ["雨", 20],
    ]);
  });
});
```

```text
     × 天気 ID で天気の名前を結合する
     × 天気の表に無い天気 ID の行は残さない
     × 天気ごとの平均利用者数を多い順に求める
TypeError: joinWeather is not a function
TypeError: joinWeather is not a function
TypeError: meanCountByWeather is not a function or its return value is not iterable
      Tests  3 failed | 17 passed (20)
```

データフレームのライブラリを使わないので、結合も集計も配列と `Map` で書きます。集計には、Node.js 21 から使える `Map.groupBy`（キーごとに要素をまとめる関数）を使ってみました。

```typescript
export function meanCountByWeather(
  joined: readonly { weather: string; cnt: number }[],
): Map<string, number> {
  const groups = Map.groupBy(joined, (row) => row.weather);
  const means = [...groups].map(([weather, rows]): [string, number] => [
    weather,
    rows.reduce((sum, row) => sum + row.cnt, 0) / rows.length,
  ]);
  return new Map(means.toSorted(([, a], [, b]) => b - a));
}
```

Vitest ではテストが 20 件すべて通りましたが、型チェックが失敗しました。

```text
src/chapter09/feature-engineering.ts(151,22): error TS2550: Property 'groupBy' does not exist on type 'MapConstructor'. Do you need to change your target library? Try changing the 'lib' compiler option to 'es2024' or later.
src/chapter09/feature-engineering.ts(151,39): error TS7006: Parameter 'row' implicitly has an 'any' type.
src/chapter09/feature-engineering.ts(154,18): error TS7006: Parameter 'sum' implicitly has an 'any' type.
src/chapter09/feature-engineering.ts(154,23): error TS7006: Parameter 'row' implicitly has an 'any' type.
```

`Map.groupBy` は ES2024 で標準になった関数で、Node.js 22 には入っていますが、本リポジトリの `tsconfig.json` は `lib` を `ES2023` にしているので、型チェックは存在しない関数として扱います。第 3 章で見たとおり、Vitest は型を取り除いて実行するだけなので、この食い違いに気付きません。`lib` を上げる代わりに、`Map` にまとめる処理を自分で書きました。`lib` は実行環境（Node.js 22）で使える機能に合わせて決めるもので、1 つの関数のために上げるより、プロジェクト全体で見直すときに変えるほうが安全だからです。

```typescript
export function joinWeather<B extends { weather_id: number }>(
  bike: readonly B[],
  weather: readonly WeatherRow[],
): (B & { weather: string })[] {
  const names = new Map(weather.map((row) => [row.weather_id, row.weather]));
  return bike.flatMap((row) => {
    const name = names.get(row.weather_id);
    return name === undefined ? [] : [{ ...row, weather: name }];
  });
}

export function meanCountByWeather(
  joined: readonly { weather: string; cnt: number }[],
): Map<string, number> {
  const groups = new Map<string, number[]>();
  for (const row of joined) {
    groups.set(row.weather, [...(groups.get(row.weather) ?? []), row.cnt]);
  }
  const means = [...groups].map(([weather, counts]): [string, number] => [
    weather,
    counts.reduce((sum, count) => sum + count, 0) / counts.length,
  ]);
  return new Map(means.toSorted(([, a], [, b]) => b - a));
}
```

- `joinWeather` は **内部結合** で、両方の表にある天気 ID の行だけを残します。天気 ID から名前を引く `Map` を作り、名前が見つからない行は `flatMap` で空の配列を返して取り除きます。行を消したくない場合（左外部結合）は、名前を `null` にした行を返します。どちらを選ぶかは、結合の前後で件数が変わってよいかどうかで決めます。実データでは、結合の前後とも 731 件でした
- 型引数 `B extends { weather_id: number }` は、「`weather_id` を持つ行なら何でもよい」ことを表します。戻り値の型 `B & { weather: string }` は、元の行の列に `weather` を加えた型です。テストのように `{ weather_id, cnt }` だけの行を渡すと、戻り値の行からは `cnt` と `weather` を型付きで取り出せます
- `Map` は要素を入れた順を保つので、平均の大きい順に並べた配列から作った `Map` は、その順番で取り出せます。テストでは `[...map]` で `[キー, 値]` の配列に戻して順番まで比べています
- `([, a], [, b]) => b - a` は、組の 1 つ目を読み飛ばし、2 つ目（平均）だけを取り出して大きい順に比べます

## 9.9 特徴量の効果を測る

### Boston データの前処理

ここまでの部品と、第 2 章の分割・欠損値補完の関数を組み合わせて、Boston データを前処理します。価格は数値なので、第 2 章で型引数を持たせた `TrainTestSplit<X, T>` の `T` を `number` にして表します。

```typescript
function totalMissing(rows: readonly Record<string, number>[]): number {
  const columns = Object.keys(rows[0] ?? {});
  return Object.values(countMissing(rows, columns)).reduce(
    (total, count) => total + count,
    0,
  );
}

describe("prepareBoston", () => {
  it("ダミー変数化と欠損値の補完をして特徴量と価格に分ける", () => {
    const csvFile = writeTempFile(
      "boston.csv",
      "CRIME,RM,NOX,PRICE\n" +
        "low,6.0,,20.0\n" +
        "high,5.0,0.5,15.0\n" +
        "very_low,7.0,0.4,30.0\n" +
        "low,6.5,0.6,25.0\n",
    );

    const split = prepareBoston(csvFile, 0.5, 0);

    const columns = ["RM", "NOX", "CRIME_low", "CRIME_very_low"];
    expect(Object.keys(split.xTrain[0] ?? {})).toEqual(columns);
    expect(Object.keys(split.xTest[0] ?? {})).toEqual(columns);
    expect(totalMissing(split.xTrain)).toBe(0);
    expect(totalMissing(split.xTest)).toBe(0);
    expect([split.tTrain.length, split.tTest.length]).toEqual([2, 2]);
  });
});
```

```text
     × ダミー変数化と欠損値の補完をして特徴量と価格に分ける
TypeError: prepareBoston is not a function
      Tests  1 failed | 20 passed (21)
```

最初は、CSV の 1 行を `Record<string, string | number | null>`（どの列も文字列・数値・欠損のどれか）として書きました。

```typescript
type BostonRow = Record<string, string | number | null>;
```

Vitest ではテストが通りましたが、型チェックが `encodeDummies` の呼び出しで止まりました。

```text
src/chapter09/feature-engineering.ts(191,33): error TS2345: Argument of type 'BostonRow[]' is not assignable to parameter of type 'readonly Record<"CRIME", unknown>[]'.
  Property 'CRIME' is missing in type 'BostonRow' but required in type 'Record<"CRIME", unknown>'.
```

`encodeDummies` は、9.4 節で「行に列 `C` があること」を型で要求するように書きました。`Record<string, ...>` は「どんな列名でもよい」という型で、CRIME の列が **必ずある** ことまでは表していません。そこで、「CRIME は文字列で必ずあり、ほかの列は文字列・数値・欠損のどれか」という交差型にしました。

```typescript
const TARGET = "PRICE";
const CATEGORY = "CRIME";

type BostonRow = Record<typeof CATEGORY, string> &
  Record<string, string | number | null>;

function loadBoston(csvFile: string): BostonRow[] {
  return parse<BostonRow>(readFileSync(csvFile), {
    columns: true,
    cast: (value, context) => {
      if (context.header || context.column === CATEGORY) {
        return value;
      }
      return value === "" ? null : Number(value);
    },
  });
}

export function prepareBoston(
  csvFile: string,
  testSize: number,
  seed: number,
): TrainTestSplit<Record<string, number>, number> {
  const rows = loadBoston(csvFile);
  const categories = dummyCategories(rows.map((row) => row[CATEGORY]));
  // CRIME 以外の列は数値か欠損（null）なので、ダミー変数化した後の行はすべて数値の列になる
  const encoded = encodeDummies(rows, CATEGORY, categories) as Record<
    string,
    number | null
  >[];
  const x = encoded.map(({ [TARGET]: _price, ...features }) => features);
  const t = encoded.map((row) => Number(row[TARGET]));
  const split = splitTrainTest(x, t, testSize, seed);
  const means = columnMeans(split.xTrain, Object.keys(x[0] ?? {}));
  return {
    ...split,
    xTrain: fillMissing(split.xTrain, means),
    xTest: fillMissing(split.xTest, means),
  };
}
```

- `typeof CATEGORY` は、定数 `CATEGORY` の型、つまりリテラル型 `"CRIME"` です。`row[CATEGORY]` の型は `string` になるので、`dummyCategories` にそのまま渡せます
- `encodeDummies` の戻り値の型は、CRIME を除いても「文字列・数値・欠損のどれか」の列が残る型です。CRIME 以外に文字列の列が無いことは CSV の中身で決まり、型からは分からないので、コメントを付けて `as` で主張しています。外部から来るデータの形は、型ではなくテストと実データで確かめます
- `({ [TARGET]: _price, ...features }) => features` は、9.4 節と同じ分割代入で価格の列を取り除きます。使わない変数の `_price` は、第 2 章で設定した ESLint の `ignoreRestSiblings` によって警告されません

欠損値を埋める平均は訓練データだけから求めますが、ダミー変数のカテゴリの一覧はデータ全体から求めています。カテゴリの一覧は「CRIME がどんな値を取りうるか」というデータの定義で、価格の情報を含まないからです。訓練データだけから求めると、4 件のテストデータのように訓練データに現れなかったカテゴリの列が作られず、テストデータと列がそろわなくなります。

### Kotlin 版で起きた型の問題は起きない

Kotlin 版では、欠損のある整数の列（RAD）に平均値の小数を入れようとして、実データで初めて例外が起きました。同じ状況のテストを TypeScript 版にも書きました。

```typescript
  it("整数の列に欠損値があっても平均値で補完する", () => {
    const csvFile = writeTempFile(
      "boston.csv",
      "CRIME,RAD,PRICE\n" +
        "low,1,20.0\n" +
        "high,,15.0\n" +
        "very_low,4,30.0\n" +
        "low,2,25.0\n",
    );

    const split = prepareBoston(csvFile, 0.5, 0);

    expect(totalMissing(split.xTrain)).toBe(0);
    expect(totalMissing(split.xTest)).toBe(0);
  });
```

このテストは、実装を変えずに最初から通りました。JavaScript の数値は整数も小数も同じ `number` なので、整数の列に小数を入れても問題になりません。同じ題材でも、言語の型の仕組みによって落とし穴の場所が変わります。TypeScript 版で型が問題になったのは、上で見た「列が必ずあるか」のほうでした。

### 特徴量の組み合わせごとに決定係数を測る

多項式特徴量の中から使う列（`terms`）を選び、標準化してから線形回帰で学習し、訓練データとテストデータの決定係数を返す関数を作ります。

テストでは、価格が部屋数の 2 次式（3 × RM² + 1）になっている架空のデータを使います。

```typescript
function quadraticSplit(): TrainTestSplit<Record<string, number>, number> {
  const price = ({ RM }: { RM: number }): number => 3 * RM * RM + 1;
  const xTrain = [
    { RM: 1, LSTAT: 9 },
    { RM: 2, LSTAT: 7 },
    { RM: 3, LSTAT: 8 },
    { RM: 4, LSTAT: 6 },
  ];
  const xTest = [
    { RM: 5, LSTAT: 5 },
    { RM: 6, LSTAT: 4 },
  ];
  return {
    xTrain,
    xTest,
    tTrain: xTrain.map(price),
    tTest: xTest.map(price),
  };
}

describe("scoreFeatureSet", () => {
  it("2 乗の項が無いと 2 次式の価格を当てきれない", () => {
    const [trainScore] = scoreFeatureSet(quadraticSplit(), ["RM"], ["RM"]);

    expect(trainScore).toBeLessThan(1);
  });

  it("2 乗の項を加えると 2 次式の価格を当てられる", () => {
    const [trainScore, testScore] = scoreFeatureSet(
      quadraticSplit(),
      ["RM"],
      ["RM", "RM^2"],
    );

    expect(trainScore).toBeCloseTo(1, 9);
    expect(testScore).toBeCloseTo(1, 9);
  });
});
```

- `price` は、引数のオブジェクトから `RM` だけを分割代入で取り出す関数です。`xTrain.map(price)` のように、関数をそのまま渡せます
- `const [trainScore] = ...` は、戻り値の組（タプル）の 1 つ目だけを受け取ります

```text
     × 2 乗の項が無いと 2 次式の価格を当てきれない
     × 2 乗の項を加えると 2 次式の価格を当てられる
TypeError: scoreFeatureSet is not a function or its return value is not iterable
TypeError: scoreFeatureSet is not a function or its return value is not iterable
      Tests  2 failed | 22 passed (24)
```

線形回帰は、切片と係数をまとめたベクトル β について、**正規方程式** XᵀX β = Xᵀt を解いて求めます（X は先頭に 1 の列を足した特徴量の行列、t は正解の価格）。行列の計算そのものを自作するのは第 7 章の主題なので、この章では特徴量の効果を測ることに集中し、行列の計算は ml-matrix に任せます。決定係数 R² = 1 − 残差の 2 乗和 ÷ 平均からの差の 2 乗和も求めます。

```typescript
export class LinearModel {
  readonly intercept: number;
  readonly weights: number[];

  constructor(intercept: number, weights: number[]) {
    this.intercept = intercept;
    this.weights = weights;
  }

  predict(rows: readonly (readonly number[])[]): number[] {
    return rows.map((row) =>
      row.reduce(
        (sum, value, i) => sum + (this.weights[i] as number) * value,
        this.intercept,
      ),
    );
  }
}

export function fitLinearRegression(
  rows: readonly (readonly number[])[],
  t: readonly number[],
): LinearModel {
  const design = new Matrix(rows.map((row) => [1, ...row]));
  const transposed = design.transpose();
  const cholesky = new CholeskyDecomposition(transposed.mmul(design));
  if (!cholesky.isPositiveDefinite()) {
    throw new Error("特徴量の列が互いに独立でないため、正規方程式を解けません");
  }
  const [intercept = 0, ...weights] = cholesky
    .solve(transposed.mmul(Matrix.columnVector([...t])))
    .to1DArray();
  return new LinearModel(intercept, weights);
}

export function rSquared(
  actual: readonly number[],
  predicted: readonly number[],
): number {
  const mean = actual.reduce((sum, value) => sum + value, 0) / actual.length;
  const residual = actual.reduce(
    (sum, value, i) => sum + (value - (predicted[i] as number)) ** 2,
    0,
  );
  const total = actual.reduce((sum, value) => sum + (value - mean) ** 2, 0);
  return 1 - residual / total;
}

function toRows(
  rows: readonly Record<string, number>[],
  columns: readonly string[],
): number[][] {
  return rows.map((row) => columns.map((column) => row[column] as number));
}

export function scoreFeatureSet(
  split: TrainTestSplit<Record<string, number>, number>,
  columns: readonly string[],
  terms: readonly string[],
): [number, number] {
  const train = polynomialFeatures(split.xTrain, columns);
  const test = polynomialFeatures(split.xTest, columns);
  const standardizer = Standardizer.fit(train, terms);
  const xTrain = toRows(standardizer.transform(train), terms);
  const xTest = toRows(standardizer.transform(test), terms);
  const model = fitLinearRegression(xTrain, split.tTrain);
  return [
    rSquared(split.tTrain, model.predict(xTrain)),
    rSquared(split.tTest, model.predict(xTest)),
  ];
}
```

- `[1, ...row]` は、行の先頭に切片のための 1 を足した配列を作ります
- XᵀX は対称で、列が互いに独立なら正定値なので、**コレスキー分解** で解けます。ml-matrix の `CholeskyDecomposition` は、分解できたかを `isPositiveDefinite()` で返すので、分解できなければ理由の分かる例外にしています
- `mmul` は行列の積、`Matrix.columnVector` は配列から列ベクトル（1 列の行列）を作ります。`columnVector` は書き換え可能な配列を受け取るので、`readonly` の `t` を `[...t]` で複製して渡しています
- `const [intercept = 0, ...weights] = ...` は、先頭を切片、残りを係数として取り出します。`= 0` は、配列が空だったときの既定値です。`noUncheckedIndexedAccess` の下では、先頭の要素も `number | undefined` になるので、既定値を付けて `number` にしています
- `toRows` は、`terms` に並べた列の順で、レコードを数値の配列に変換します。多項式特徴量から使う列を選ぶ処理も兼ねています
- 戻り値の型 `[number, number]` は、訓練データとテストデータの決定係数の組を表すタプル型です

```text
      Tests  24 passed (24)
```

RM だけでは直線しか引けないので決定係数は 1 に届きません。RM² を加えると、訓練データにも、学習に使っていない RM = 5・6 のテストデータにも完全に当てはまります。

2 つのテストだけでは、切片と係数そのものが正しいかは確かめていません。答えが分かっている式（t = 2 + 3x₁ − x₂）で係数を確かめ、列が互いに独立でない場合の例外も確かめておきます。

```typescript
describe("fitLinearRegression", () => {
  it("正規方程式を解いて切片と係数を求める", () => {
    const rows = [
      [1, 0],
      [0, 1],
      [1, 1],
      [2, 3],
    ];
    const t = rows.map(([x1 = 0, x2 = 0]) => 2 + 3 * x1 - x2);

    const model = fitLinearRegression(rows, t);

    expect(model.intercept).toBeCloseTo(2, 9);
    expect(model.weights).toEqual([
      expect.closeTo(3, 9),
      expect.closeTo(-1, 9),
    ]);
  });

  it("同じ値の列が 2 つあると正規方程式を解けない", () => {
    const rows = [
      [1, 1],
      [2, 2],
      [3, 3],
    ];

    expect(() => fitLinearRegression(rows, [1, 2, 3])).toThrow(
      "特徴量の列が互いに独立でないため、正規方程式を解けません",
    );
  });
});
```

どちらも実装を変えずに通りました。

### 外れ値を除いて学習する

外れ値の影響も測れるように、訓練データから価格が外れ値の行を除く関数を作ります。テストデータは実際に予測する対象なので、除きません。

```typescript
describe("removeTargetOutliers", () => {
  it("訓練データから価格が外れ値の行を取り除きテストデータは残す", () => {
    const split = {
      xTrain: [{ RM: 5 }, { RM: 6 }, { RM: 6.5 }, { RM: 7 }, { RM: 8 }],
      xTest: [{ RM: 9 }],
      tTrain: [1, 2, 3, 4, 100],
      tTest: [500],
    };

    const removed = removeTargetOutliers(split);

    expect(removed.xTrain).toEqual([
      { RM: 5 },
      { RM: 6 },
      { RM: 6.5 },
      { RM: 7 },
    ]);
    expect(removed.tTrain).toEqual([1, 2, 3, 4]);
    expect(removed.xTest).toEqual([{ RM: 9 }]);
    expect(removed.tTest).toEqual([500]);
  });
});
```

```text
     × 訓練データから価格が外れ値の行を取り除きテストデータは残す
TypeError: removeTargetOutliers is not a function
      Tests  1 failed | 25 passed (26)
```

```typescript
export function removeTargetOutliers<X>(
  split: TrainTestSplit<X, number>,
): TrainTestSplit<X, number> {
  const outliers = iqrOutliers(split.tTrain);
  return {
    ...split,
    xTrain: split.xTrain.filter((_, i) => !outliers[i]),
    tTrain: split.tTrain.filter((_, i) => !outliers[i]),
  };
}
```

`filter` のコールバックは 2 つ目の引数に要素の位置を受け取るので、外れ値の判定と同じ位置の行を、特徴量と価格の両方から取り除けます。`{ ...split, ... }` で、テストデータはそのまま残します。特徴量の型を型引数 `X` にしたので、テストのように `{ RM: number }` だけの行でも、Boston の `Record<string, number>` の行でも使えます。

### 実データで測る

特徴量には、Python 版・Kotlin 版と同じ RM・LSTAT・PTRATIO を使います。Python 版の訓練データで価格との相関係数が大きかった 3 列です。比べやすいように、TypeScript 版でも同じ列にそろえます。

```typescript
// src/chapter09/main.ts
export const COLUMNS = ["RM", "LSTAT", "PTRATIO"];
export const SQUARES = ["RM^2", "LSTAT^2", "PTRATIO^2"];
export const FEATURE_SETS = new Map([
  ["元の特徴量", COLUMNS],
  ["2 乗の項を追加", [...COLUMNS, ...SQUARES]],
  [
    "交互作用の項も追加",
    [
      ...COLUMNS,
      ...pairsWithReplacement(COLUMNS).map(([left, right]) =>
        termName(left, right),
      ),
    ],
  ],
]);
```

「交互作用の項も追加」の列名は、`pairsWithReplacement` と `termName` から組み立てています。列名の組み立て方を 1 か所にとどめるためです。`Map` は入れた順を保つので、表示の順番が定義の順になります。

実データのテストと `main` は、実データで出力を確かめながら書いたので、表示のテストでは次の 1 か所だけが Red になりました。

```text
 FAIL  test/chapter09/boston-data.test.ts > Boston.csv・bike.tsv・weather.csv の実データ > 実行すると特徴量エンジニアリングの結果を表示する
- Expected
+ Received

    [
      "訓練データ: 70 件, テストデータ: 30 件",
      "特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low",
  -   "標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00",
  +   "標準化した訓練データの RM: 平均 -0.00, 標準偏差 1.00",
      "決定係数:",
      Tests  1 failed | 3 passed (4)
```

標準化した RM の平均は、理論上は 0 ですが、浮動小数点の誤差でごく小さな負の値になります。`toFixed(2)` は、負の値を小数点以下 2 桁に丸めても符号を残すので `-0.00` と表示されます。Kotlin 版と同じく、ごく小さい値は 0 とみなしてから表示します。

```typescript
// 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす
const ZERO_TOLERANCE = 1e-9;
```

```typescript
function format(value: number, digits: number): string {
  return (Math.abs(value) < ZERO_TOLERANCE ? 0 : value).toFixed(digits);
}
```

```bash
node src/chapter09/main.ts
```

```text
訓練データ: 70 件, テストデータ: 30 件
特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
決定係数:
  元の特徴量（3 列）: 訓練 0.7859, テスト 0.1363
  2 乗の項を追加（6 列）: 訓練 0.8574, テスト 0.6205
  交互作用の項も追加（9 列）: 訓練 0.8764, テスト -0.3782
訓練データの PRICE の外れ値: 6 件
  外れ値を除いて 2 乗の項を追加: 訓練 0.7014, テスト 0.5599
天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
```

結果を表にまとめます。

| 特徴量 | 列数 | 訓練データ | テストデータ |
|--------|------|-----------|------------|
| 元の特徴量 | 3 | 0.7859 | 0.1363 |
| 2 乗の項を追加 | 6 | 0.8574 | **0.6205** |
| 交互作用の項も追加 | 9 | 0.8764 | −0.3782 |
| 外れ値を除いて 2 乗の項を追加 | 6 | 0.7014 | 0.5599 |

この表から、次のことが読み取れます。

- **2 乗の項は効いた**: テストデータの決定係数が 0.1363 から 0.6205 に上がりました。元の特徴量では、訓練データ 0.7859 に対してテストデータ 0.1363 と大きく離れていました。価格と部屋数・低所得者の割合の関係が直線ではなく曲線であることを、2 乗の項が捉えています（散布図は Python 版・Kotlin 版の 9.10 節で確認できます）
- **交互作用の項は逆効果だった**: 訓練データの決定係数は 0.8764 とさらに上がったのに、テストデータでは −0.3782 と負になりました。決定係数が負になるのは、予測の誤差が「テストデータの平均値をいつも答える」よりも大きいときです。訓練データ 70 件に対して 9 列は多く、訓練データの偶然のばらつきまで覚えてしまった **過学習** の状態です
- **外れ値を除いても良くならなかった**: IQR で検出した 6 件を除くと、テストデータの決定係数は 0.6205 から 0.5599 に下がりました。価格の高い地域は「測定の誤り」ではなく「実際に高い」データなので、除くとモデルは高価格帯を学べなくなり、テストデータに含まれる高価格帯の予測を外すようになります

3 つの言語の版のテストデータの決定係数を並べます。分割の手順は同じですが、乱数の生成器が違うので、訓練データとテストデータに入った行が版ごとに違います。

| 特徴量 | Python 版 | Kotlin 版 | TypeScript 版 |
|--------|----------|----------|--------------|
| 元の特徴量 | 0.5848 | 0.6457 | 0.1363 |
| 2 乗の項を追加 | 0.7283 | 0.7975 | 0.6205 |
| 交互作用の項も追加 | 0.5799 | 0.7995 | −0.3782 |
| 外れ値を除いて 2 乗の項を追加 | 0.6268 | 0.7786 | 0.5599 |

「2 乗の項で上がる」「外れ値を除くと下がる」は 3 つの版で同じ向きでした。一方、交互作用の項は、Python 版と TypeScript 版では下がり、Kotlin 版ではわずかに上がりました。70 件の訓練データに 9 列という「多すぎるかもしれない」状態では、どの行で学習し、どの行で評価したかによって結論が変わりうる、ということです。

外れ値の **検出** は機械的にできますが、除くかどうかはデータの意味を見て決める必要があります。そして、特徴量を増やすかどうかは、必ず学習に使っていないテストデータの評価で判断します。分け方で結論が揺れる場合は、1 回の分割の結果だけで決めず、第 11 章の交差検証で複数の分け方の平均を見ます。

実データのテストでは、この結果を固定しています。

```typescript
// test/chapter09/boston-data.test.ts
const bostonCsv = join(dataDir(), "Boston.csv");
const bikeTsv = join(dataDir(), "bike.tsv");
const weatherCsv = join(dataDir(), "weather.csv");
const hasData = [bostonCsv, bikeTsv, weatherCsv].every((file) =>
  existsSync(file),
);
```

```typescript
describe.skipIf(!hasData)(
  "Boston.csv・bike.tsv・weather.csv の実データ",
  () => {
    let split: TrainTestSplit<Record<string, number>, number>;

    beforeAll(() => {
      split = prepareBoston(bostonCsv, 0.3, 0);
    });

    it("実データを 70 件と 30 件に分けて欠損値を補完する", () => {
      expect([split.xTrain.length, split.xTest.length]).toEqual([70, 30]);
      expect(totalMissing(split.xTrain)).toBe(0);
      expect(totalMissing(split.xTest)).toBe(0);
    });

    it("2 乗の項を加えるとテストデータの決定係数が上がる", () => {
      const [, base] = scoreFeatureSet(split, COLUMNS, COLUMNS);
      const [, squares] = scoreFeatureSet(split, COLUMNS, [
        ...COLUMNS,
        ...SQUARES,
      ]);

      expect(base).toBeCloseTo(0.1363, 4);
      expect(squares).toBeCloseTo(0.6205, 4);
    });

    it("天気ごとの平均利用者数を求める", () => {
      const joined = joinWeather(loadBike(bikeTsv), loadWeather(weatherCsv));

      const means = meanCountByWeather(joined);

      expect(joined).toHaveLength(731);
      expect([...means.keys()]).toEqual(["晴れ", "曇り", "雨"]);
      expect([...means.values()].map((mean) => mean.toFixed(1))).toEqual([
        "4876.8",
        "4052.7",
        "1803.3",
      ]);
    });
```

- `every` は、3 つのファイルがすべてあるかを確かめます。1 つでも無ければ、グループの中のテストはすべてスキップされます
- 第 3 章で見たとおり、`describe` のコールバックはスキップされるときも実行されるので、ファイルの読み込みは `beforeAll` の中で行います。`let split` の型は、代入より前に宣言するので明示しています
- `const [, base] = ...` は、組の 1 つ目を読み飛ばして 2 つ目だけを受け取ります

表示の `main` を確かめるテストもあります（完成コードを参照）。

```bash
npx vitest run test/chapter09
```

第 9 章のテストは 31 件すべて通ります。学習データが無い環境では、実データのテスト 4 件がスキップされ、27 件が通ります。

```text
 Test Files  2 passed | 1 skipped (3)
      Tests  27 passed | 4 skipped (31)
```

## 9.10 Notebook による探索と可視化

TypeScript 版では Notebook による探索と可視化を扱いません。標準化の前後の分布、特徴量と価格の散布図、外れ値の箱ひげ図、天気ごとの利用者数のグラフは、[Python 版の 9.10 節](../python/09-feature-engineering.md) か [Kotlin 版の 9.10 節](../kotlin/09-feature-engineering.md) を参照してください。

## 9.11 リファクタリング

TDD でテストを 1 つずつ足していく間、関数はすべて `feature-engineering.ts` の末尾に追記していきました。テストが揃った時点で、このファイルは 290 行を超え、5 つの技法と線形回帰が 1 つのファイルに並んでいました。Kotlin 版では detekt が関数の数の多さを指摘しましたが、本リポジトリの ESLint の設定（`typescript-eslint` の推奨設定）には、ファイルの大きさや関数の数を数える規則がありません。道具が指摘しなくても、1 つのファイルが複数の理由で変わる状態は変更しにくいので、技法ごとにファイルを分けました。

| ファイル | 中身 | 依存するライブラリ |
|---------|------|------------------|
| `dummies.ts` | `dummyCategories`・`encodeDummies` | — |
| `standardizer.ts` | `Standardizer` | — |
| `polynomial-features.ts` | `polynomialFeatures`・`pairsWithReplacement`・`termName` | — |
| `outliers.ts` | `quantile`・`iqrOutliers`・`removeTargetOutliers` | — |
| `bike-weather.ts` | `loadBike`・`loadWeather`・`joinWeather`・`meanCountByWeather` | csv-parse |
| `linear-model.ts` | `LinearModel`・`fitLinearRegression`・`rSquared` | ml-matrix |
| `boston.ts` | `prepareBoston`・`scoreFeatureSet` | csv-parse |
| `statistics.ts` | `mean`（下記） | — |

Kotlin では、同じパッケージのファイルどうしは import なしで互いの関数を呼べるので、ファイルを分けても呼び出し側は変わりませんでした。TypeScript（ES モジュール）では、使う名前をファイルごとに import するので、呼び出し側の import を書き換える必要があります。`feature-engineering.ts` を消した直後に型チェックを実行すると、書き換えが必要な場所がすべて分かります。

```text
test/chapter09/boston-data.test.ts(15,8): error TS2307: Cannot find module '../../src/chapter09/feature-engineering.ts' or its corresponding type declarations.
test/chapter09/feature-engineering.test.ts(24,8): error TS2307: Cannot find module '../../src/chapter09/feature-engineering.ts' or its corresponding type declarations.
test/chapter09/feature-engineering.test.ts(235,44): error TS7006: Parameter 'row' implicitly has an 'any' type.
test/chapter09/ml-matrix-standardization.test.ts(3,30): error TS2307: Cannot find module '../../src/chapter09/feature-engineering.ts' or its corresponding type declarations.
test/chapter09/ml-matrix-standardization.test.ts(35,30): error TS7006: Parameter 'row' implicitly has an 'any' type.
```

`TS7006` は、import できなかった関数の戻り値の型が分からないことから連鎖したエラーです。import を書き換えると、すべて消えました。

ファイルを分けたあと、重複を 3 つ取り除きました。どの変更の後も、テストが通ることを確かめています。

**平均の計算をまとめる**: 「合計を件数で割る」計算が、標準化の分散・決定係数・天気ごとの平均の 3 か所にありました。Kotlin の `average()` や Python の `mean()` に当たる関数は JavaScript の標準ライブラリに無いので、`statistics.ts` に `mean` を作って使います。

```typescript
export function mean(values: readonly number[]): number {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}
```

**文字列の列を残す `cast` をまとめる**: `loadBike` と `loadWeather` の `cast` は、文字列のまま残す列の名前だけが違う同じ関数でした。列名を受け取って `cast` 用の関数を返す `numbersExcept` にしました。戻り値の型 `CastingFunction` は csv-parse が公開している型です。

```typescript
// 列名と textColumn の列は文字列のまま、それ以外の列は数値にする
function numbersExcept(textColumn: string): CastingFunction {
  return (value, context) =>
    context.header || context.column === textColumn ? value : Number(value);
}
```

**標準化した後の平均と標準偏差を `Standardizer.fit` で求める**: `main` では、標準化した RM の平均と標準偏差を表示するために、平均と標準偏差の計算をもう一度書いていました。`Standardizer.fit` が求める `means` と `stds` がまさにその値なので、標準化した行にもう一度 `fit` を使います。

```typescript
  // 標準化した後の平均と標準偏差も、Standardizer.fit で求める
  const standardized = Standardizer.fit(split.xTrain, ["RM"]).transform(
    split.xTrain,
  );
  const rm = Standardizer.fit(standardized, ["RM"]);
```

最後に、テストの架空のデータ `quadraticSplit` を、配列の位置で RM と LSTAT を組み合わせる書き方から、行をそのまま並べる書き方に直しました。

<details>
<summary>この章の完成コード（src/chapter09/）</summary>

```typescript
// dummies.ts
export function dummyCategories(values: readonly (string | null)[]): string[] {
  return [...new Set(values.filter((value) => value !== null))].sort().slice(1);
}

export function encodeDummies<R extends Record<C, unknown>, C extends string>(
  rows: readonly R[],
  column: C,
  categories: readonly string[],
): (Omit<R, C> & Record<string, number>)[] {
  return rows.map((row) => {
    const { [column]: value, ...rest } = row;
    const flags = Object.fromEntries(
      categories.map((category) => [
        `${column}_${category}`,
        value === category ? 1 : 0,
      ]),
    );
    return { ...rest, ...flags };
  });
}
```

```typescript
// statistics.ts
export function mean(values: readonly number[]): number {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}
```

```typescript
// standardizer.ts
import { columnMeans } from "../chapter02/iris-preprocessing.ts";
import { mean } from "./statistics.ts";

export class Standardizer<K extends string> {
  readonly means: Record<K, number>;
  readonly stds: Record<K, number>;

  constructor(means: Record<K, number>, stds: Record<K, number>) {
    this.means = means;
    this.stds = stds;
  }

  transform<R extends Record<K, number>>(rows: readonly R[]): R[] {
    const columns = Object.keys(this.means) as K[];
    return rows.map((row) => {
      const standardized = { ...row };
      for (const column of columns) {
        standardized[column] = ((row[column] - this.means[column]) /
          this.stds[column]) as R[K];
      }
      return standardized;
    });
  }

  static fit<K extends string>(
    rows: readonly Record<K, number>[],
    columns: readonly K[],
  ): Standardizer<K> {
    const means = columnMeans(rows, columns);
    const stds = Object.fromEntries(
      columns.map((column) => {
        const std = Math.sqrt(
          mean(rows.map((row) => (row[column] - means[column]) ** 2)),
        );
        return [column, std === 0 ? 1 : std];
      }),
    ) as Record<K, number>;
    return new Standardizer(means, stds);
  }
}
```

```typescript
// polynomial-features.ts
export function polynomialFeatures<K extends string>(
  rows: readonly Record<K, number>[],
  columns: readonly K[],
): Record<string, number>[] {
  return rows.map((row) => {
    const features: Record<string, number> = {};
    for (const column of columns) {
      features[column] = row[column];
    }
    for (const [left, right] of pairsWithReplacement(columns)) {
      features[termName(left, right)] = row[left] * row[right];
    }
    return features;
  });
}

export function pairsWithReplacement<T>(items: readonly T[]): [T, T][] {
  return items.flatMap((left, i) =>
    items.slice(i).map((right): [T, T] => [left, right]),
  );
}

export function termName(left: string, right: string): string {
  return left === right ? `${left}^2` : `${left} ${right}`;
}
```

```typescript
// outliers.ts
import type { TrainTestSplit } from "../chapter02/iris-preprocessing.ts";

export function quantile(values: readonly number[], q: number): number {
  const sorted = values.toSorted((a, b) => a - b);
  const position = (sorted.length - 1) * q;
  const lower = sorted[Math.floor(position)] as number;
  const upper = sorted[Math.ceil(position)] as number;
  return lower + (upper - lower) * (position - Math.floor(position));
}

export function iqrOutliers(values: readonly number[], k = 1.5): boolean[] {
  const q1 = quantile(values, 0.25);
  const q3 = quantile(values, 0.75);
  const iqr = q3 - q1;
  return values.map((value) => value < q1 - k * iqr || value > q3 + k * iqr);
}

export function removeTargetOutliers<X>(
  split: TrainTestSplit<X, number>,
): TrainTestSplit<X, number> {
  const outliers = iqrOutliers(split.tTrain);
  return {
    ...split,
    xTrain: split.xTrain.filter((_, i) => !outliers[i]),
    tTrain: split.tTrain.filter((_, i) => !outliers[i]),
  };
}
```

```typescript
// bike-weather.ts
import { readFileSync } from "node:fs";
import { type CastingFunction, parse } from "csv-parse/sync";
import { mean } from "./statistics.ts";

export interface BikeRow {
  dteday: string;
  weather_id: number;
  cnt: number;
}

export interface WeatherRow {
  weather_id: number;
  weather: string;
}

// 列名と textColumn の列は文字列のまま、それ以外の列は数値にする
function numbersExcept(textColumn: string): CastingFunction {
  return (value, context) =>
    context.header || context.column === textColumn ? value : Number(value);
}

export function loadBike(tsvFile: string): BikeRow[] {
  return parse<BikeRow>(readFileSync(tsvFile), {
    columns: true,
    delimiter: "\t",
    cast: numbersExcept("dteday"),
  });
}

export function loadWeather(csvFile: string): WeatherRow[] {
  const text = new TextDecoder("shift_jis").decode(readFileSync(csvFile));
  return parse<WeatherRow>(text, {
    columns: true,
    cast: numbersExcept("weather"),
  });
}

export function joinWeather<B extends { weather_id: number }>(
  bike: readonly B[],
  weather: readonly WeatherRow[],
): (B & { weather: string })[] {
  const names = new Map(weather.map((row) => [row.weather_id, row.weather]));
  return bike.flatMap((row) => {
    const name = names.get(row.weather_id);
    return name === undefined ? [] : [{ ...row, weather: name }];
  });
}

export function meanCountByWeather(
  joined: readonly { weather: string; cnt: number }[],
): Map<string, number> {
  const groups = new Map<string, number[]>();
  for (const row of joined) {
    groups.set(row.weather, [...(groups.get(row.weather) ?? []), row.cnt]);
  }
  const means = [...groups].map(([weather, counts]): [string, number] => [
    weather,
    mean(counts),
  ]);
  return new Map(means.toSorted(([, a], [, b]) => b - a));
}
```

```typescript
// linear-model.ts
import { CholeskyDecomposition, Matrix } from "ml-matrix";
import { mean } from "./statistics.ts";

export class LinearModel {
  readonly intercept: number;
  readonly weights: number[];

  constructor(intercept: number, weights: number[]) {
    this.intercept = intercept;
    this.weights = weights;
  }

  predict(rows: readonly (readonly number[])[]): number[] {
    return rows.map((row) =>
      row.reduce(
        (sum, value, i) => sum + (this.weights[i] as number) * value,
        this.intercept,
      ),
    );
  }
}

export function fitLinearRegression(
  rows: readonly (readonly number[])[],
  t: readonly number[],
): LinearModel {
  const design = new Matrix(rows.map((row) => [1, ...row]));
  const transposed = design.transpose();
  const cholesky = new CholeskyDecomposition(transposed.mmul(design));
  if (!cholesky.isPositiveDefinite()) {
    throw new Error("特徴量の列が互いに独立でないため、正規方程式を解けません");
  }
  const [intercept = 0, ...weights] = cholesky
    .solve(transposed.mmul(Matrix.columnVector([...t])))
    .to1DArray();
  return new LinearModel(intercept, weights);
}

export function rSquared(
  actual: readonly number[],
  predicted: readonly number[],
): number {
  const average = mean(actual);
  const residual = actual.reduce(
    (sum, value, i) => sum + (value - (predicted[i] as number)) ** 2,
    0,
  );
  const total = actual.reduce((sum, value) => sum + (value - average) ** 2, 0);
  return 1 - residual / total;
}
```

```typescript
// boston.ts
import { readFileSync } from "node:fs";
import { parse } from "csv-parse/sync";
import {
  type TrainTestSplit,
  columnMeans,
  fillMissing,
  splitTrainTest,
} from "../chapter02/iris-preprocessing.ts";
import { dummyCategories, encodeDummies } from "./dummies.ts";
import { fitLinearRegression, rSquared } from "./linear-model.ts";
import { polynomialFeatures } from "./polynomial-features.ts";
import { Standardizer } from "./standardizer.ts";

const TARGET = "PRICE";
const CATEGORY = "CRIME";

type BostonRow = Record<typeof CATEGORY, string> &
  Record<string, string | number | null>;

function loadBoston(csvFile: string): BostonRow[] {
  return parse<BostonRow>(readFileSync(csvFile), {
    columns: true,
    cast: (value, context) => {
      if (context.header || context.column === CATEGORY) {
        return value;
      }
      return value === "" ? null : Number(value);
    },
  });
}

export function prepareBoston(
  csvFile: string,
  testSize: number,
  seed: number,
): TrainTestSplit<Record<string, number>, number> {
  const rows = loadBoston(csvFile);
  const categories = dummyCategories(rows.map((row) => row[CATEGORY]));
  // CRIME 以外の列は数値か欠損（null）なので、ダミー変数化した後の行はすべて数値の列になる
  const encoded = encodeDummies(rows, CATEGORY, categories) as Record<
    string,
    number | null
  >[];
  const x = encoded.map(({ [TARGET]: _price, ...features }) => features);
  const t = encoded.map((row) => Number(row[TARGET]));
  const split = splitTrainTest(x, t, testSize, seed);
  const means = columnMeans(split.xTrain, Object.keys(x[0] ?? {}));
  return {
    ...split,
    xTrain: fillMissing(split.xTrain, means),
    xTest: fillMissing(split.xTest, means),
  };
}

function toRows(
  rows: readonly Record<string, number>[],
  columns: readonly string[],
): number[][] {
  return rows.map((row) => columns.map((column) => row[column] as number));
}

export function scoreFeatureSet(
  split: TrainTestSplit<Record<string, number>, number>,
  columns: readonly string[],
  terms: readonly string[],
): [number, number] {
  const train = polynomialFeatures(split.xTrain, columns);
  const test = polynomialFeatures(split.xTest, columns);
  const standardizer = Standardizer.fit(train, terms);
  const xTrain = toRows(standardizer.transform(train), terms);
  const xTest = toRows(standardizer.transform(test), terms);
  const model = fitLinearRegression(xTrain, split.tTrain);
  return [
    rSquared(split.tTrain, model.predict(xTrain)),
    rSquared(split.tTest, model.predict(xTest)),
  ];
}
```

```typescript
// main.ts
import { join } from "node:path";
import { dataDir } from "../dataset.ts";
import {
  joinWeather,
  loadBike,
  loadWeather,
  meanCountByWeather,
} from "./bike-weather.ts";
import { prepareBoston, scoreFeatureSet } from "./boston.ts";
import { iqrOutliers, removeTargetOutliers } from "./outliers.ts";
import { pairsWithReplacement, termName } from "./polynomial-features.ts";
import { Standardizer } from "./standardizer.ts";

const TEST_SIZE = 0.3;
const SEED = 0;
// 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす
const ZERO_TOLERANCE = 1e-9;
const SCORE_DIGITS = 4;
const MEAN_DIGITS = 2;
const COUNT_DIGITS = 1;

export const COLUMNS = ["RM", "LSTAT", "PTRATIO"];
export const SQUARES = ["RM^2", "LSTAT^2", "PTRATIO^2"];
export const FEATURE_SETS = new Map([
  ["元の特徴量", COLUMNS],
  ["2 乗の項を追加", [...COLUMNS, ...SQUARES]],
  [
    "交互作用の項も追加",
    [
      ...COLUMNS,
      ...pairsWithReplacement(COLUMNS).map(([left, right]) =>
        termName(left, right),
      ),
    ],
  ],
]);

function format(value: number, digits: number): string {
  return (Math.abs(value) < ZERO_TOLERANCE ? 0 : value).toFixed(digits);
}

function formatScores([train, test]: [number, number]): string {
  return `訓練 ${format(train, SCORE_DIGITS)}, テスト ${format(test, SCORE_DIGITS)}`;
}

export function main(print: (line: string) => void = console.log): void {
  const split = prepareBoston(join(dataDir(), "Boston.csv"), TEST_SIZE, SEED);
  print(
    `訓練データ: ${split.xTrain.length} 件, テストデータ: ${split.xTest.length} 件`,
  );
  print(`特徴量の列: ${Object.keys(split.xTrain[0] ?? {}).join(", ")}`);

  // 標準化した後の平均と標準偏差も、Standardizer.fit で求める
  const standardized = Standardizer.fit(split.xTrain, ["RM"]).transform(
    split.xTrain,
  );
  const rm = Standardizer.fit(standardized, ["RM"]);
  print(
    `標準化した訓練データの RM: 平均 ${format(rm.means.RM, MEAN_DIGITS)}, 標準偏差 ${format(rm.stds.RM, MEAN_DIGITS)}`,
  );

  print("決定係数:");
  for (const [name, terms] of FEATURE_SETS) {
    print(
      `  ${name}（${terms.length} 列）: ${formatScores(scoreFeatureSet(split, COLUMNS, terms))}`,
    );
  }

  const outliers = iqrOutliers(split.tTrain).filter((outlier) => outlier);
  print(`訓練データの PRICE の外れ値: ${outliers.length} 件`);
  const removed = scoreFeatureSet(removeTargetOutliers(split), COLUMNS, [
    ...COLUMNS,
    ...SQUARES,
  ]);
  print(`  外れ値を除いて 2 乗の項を追加: ${formatScores(removed)}`);

  const joined = joinWeather(
    loadBike(join(dataDir(), "bike.tsv")),
    loadWeather(join(dataDir(), "weather.csv")),
  );
  const means = [...meanCountByWeather(joined)]
    .map(([weather, count]) => `${weather}=${format(count, COUNT_DIGITS)}`)
    .join(", ");
  print(`天気ごとの平均利用者数: ${means}`);
}

// node src/chapter09/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
```

</details>

## 9.12 まとめ

この章では、特徴量エンジニアリングの 5 つの技法を TDD で自作し、標準化を ml-matrix と突き合わせました。

| 技法 | 自作した関数・クラス | 突き合わせたライブラリ | 落とし穴 |
|------|------------------|-------------------|---------|
| ダミー変数 | `dummyCategories`・`encodeDummies` | — | 訓練データとテストデータで列をそろえる |
| 標準化 | `Standardizer` | ml-matrix の `center`・`scale` | 標準偏差の定義がライブラリで違う、分散 0 の列で NaN、テストデータの平均を使わない |
| 多項式特徴量 | `polynomialFeatures`・`pairsWithReplacement` | — | 列数が急に増えて過学習しやすい |
| 外れ値の検出 | `quantile`・`iqrOutliers` | — | 比較関数を省くと数値を文字列として並べる、除くかどうかはデータの意味で決める |
| 表の結合 | `loadBike`・`loadWeather`・`joinWeather` | — | 区切り文字と文字コード（間違えても例外にならず文字化けする）、内部結合で消える行 |

実データでは、2 乗の項を加えるとテストデータの決定係数が 0.1363 から 0.6205 に上がりました。交互作用の項まで加えると −0.3782 と負になり、外れ値を除くと 0.5599 に下がりました。交互作用の項の効果は、版ごとに訓練データに入った行が違うだけで向きが変わりました。特徴量を作るのは手段で、その効果は学習に使っていないデータで測って判断します。

TypeScript 版ならではの学びもありました。

1. **ジェネリクスで行の型を保つ** — `encodeDummies` は `Omit<R, C>`、`transform` は `R`、`joinWeather` は `B & { weather: string }` を返し、変換の前後で列の型を失わない。ただし `R[K]` への代入や、CSV の中身で決まる列の型は、`as` で主張する必要があった
2. **型チェックとテストの役割分担** — `Map.groupBy`（`lib` の不足）や「列が必ずあるか」は型チェックだけが見つけ、比較関数を省いた `toSorted()` や Shift_JIS の文字化けはテストだけが見つけた
3. **言語の数値の型** — `number` には整数と小数の区別が無いので、Kotlin 版で起きた欠損のある整数の列の問題は起きなかった。一方で、0 での割り算は例外にならず NaN になる

次の章では、分類のモデルを増やし、ロジスティック回帰と、第 3 章の決定木を組み合わせたランダムフォレストを実装します。
