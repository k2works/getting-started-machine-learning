---
type: Article
title: "第 10 章: ロジスティック回帰とアンサンブル学習"
description: "ソフトマックスと勾配降下のロジスティック回帰、第 3 章の決定木を再利用したランダムフォレストと特徴量の重要度を TDD で自作し、構造的部分型の Classifier で ml.js と並べて、型アサーションの落とし穴と ml-logistic-regression に切片が無いことを突き止める。"
tags: [article,getting-start-ml,typescript]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-18T00:53:11Z }
---

# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが増えると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。そこで、`interface Classifier` でモデルに共通する操作（`fit` と `predict`）を定義し、どのモデルも同じ関数で評価できるようにします。最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作します。

[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md) と同じ TODO リストで進めます。TypeScript 版では、次の 3 点に注目してください。

- TypeScript の `interface` は、Python の `Protocol` と同じ **構造的部分型** です。[Kotlin 版の第 10 章](../kotlin/10-logistic-regression-and-ensemble.md) では第 3 章の決定木をアダプターで包む必要がありましたが、TypeScript 版ではそのまま共通のインターフェースに入ります
- 型アサーション（`as`）は、型チェックを黙らせるだけで、実行時には何も確かめません。学習前の予測で、`as string` が `undefined` を隠していたことをテストが見つけます
- ライブラリとの突き合わせには、ml.js の [ml-logistic-regression](https://github.com/mljs/logistic-regression) と [ml-random-forest](https://github.com/mljs/random-forest) を使います。ml-logistic-regression には切片の項が無いことを、実データで確かめます

最適化の方法や乱数の使い方が自作と違うので、ライブラリとは予測の完全一致を求めず、正解率を比べます（[ADR 003](../../../adr/003-typescript-ml-libraries.md)）。データは第 2 章・第 3 章と同じ iris を使い、第 2 章の `prepareIris` で前処理します。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 値がすべて同じなら確率は均等になる
  - [ ] 値の差が指数の比になる
  - [ ] 大きな値でもあふれない
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 2 種類・3 種類のラベルを予測する
  - [ ] 学習を繰り返すと損失が小さくなる
- [ ] ランダムフォレストを学習して予測する
  - [ ] 多数決で予測を 1 つに決める
  - [ ] ブートストラップ標本を選ぶ
  - [ ] 第 3 章の決定木を再利用する
- [ ] 特徴量の重要度を計算する
  - [ ] 決定木 1 本の重要度
  - [ ] ランダムフォレストの重要度
- [ ] どのモデルも同じ関数で評価する
  - [ ] 第 3 章の決定木をそのまま共通のインターフェースに合わせる
  - [ ] ml.js のモデルも同じ関数で評価する
- [ ] 実データで ml.js と正解率を突き合わせる

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和 + 切片」を計算し、それを **ソフトマックス関数** で確率に変換します。

```text
スコア(品種 k) = w(k, がく片長さ) × がく片長さ + … + w(k, 花弁幅) × 花弁幅 + b(k)
確率(品種 k)   = exp(スコア(品種 k)) / Σ exp(スコア(品種 j))
```

多クラスへの広げ方には、品種ごとに「その品種か、それ以外か」の 2 値分類器を作る one-vs-rest と、この章のように 1 つのモデルで全品種の確率を同時に求めるソフトマックス（多項ロジスティック回帰）があります。ソフトマックスを選んだのは、3 品種の確率の合計が必ず 1 になり、確率として解釈しやすいからです。10.8 節で使う ml-logistic-regression は one-vs-rest です。

### 仮実装

Kotlin 版と同じく、**1 サンプル分のスコア**（`number[]`）を受け取る関数にします。複数のサンプルは、呼び出し側で `map` します。

```typescript
// test/chapter10/logistic-regression.test.ts
import { describe, expect, it } from "vitest";
import { softmax } from "../../src/chapter10/logistic-regression.ts";

describe("softmax", () => {
  it("値がすべて同じなら確率は均等になる", () => {
    expect(softmax([0, 0, 0, 0])).toEqual([0.25, 0.25, 0.25, 0.25]);
  });
});
```

```text
 FAIL  test/chapter10/logistic-regression.test.ts [ test/chapter10/logistic-regression.test.ts ]
Error: Cannot find module '../../src/chapter10/logistic-regression.ts' imported from test/chapter10/logistic-regression.test.ts
```

均等な確率を返す仮実装で Green にします。

```typescript
// src/chapter10/logistic-regression.ts
export function softmax(z: readonly number[]): number[] {
  return z.map(() => 1 / z.length);
}
```

Kotlin の `DoubleArray` と違い、`toEqual` は配列の中身を比べるので、そのまま期待値と比べられます。

### 三角測量

値の差が `ln 2` なら、確率の比は 2 倍になるはずです。

```typescript
  it("値の差が指数の比になる", () => {
    const [first, second] = softmax([0, Math.log(2)]);

    expect(first).toBeCloseTo(1 / 3, 12);
    expect(second).toBeCloseTo(2 / 3, 12);
  });
```

```text
     × 値の差が指数の比になる 5ms
AssertionError: expected 0.5 to be close to 0.3333333333333333, received difference is 0.16666666666666669, but expected 5e-13
```

`const [first, second] = ...` は分割代入です。`noUncheckedIndexedAccess` を有効にしているので、`first` と `second` の型は `number | undefined` になります。`toBeCloseTo` は `undefined` も受け取れるので、ここでは型の絞り込みは要りません。

定義どおりに一般化します。

```typescript
export function softmax(z: readonly number[]): number[] {
  const exps = z.map((value) => Math.exp(value));
  const total = exps.reduce((sum, value) => sum + value, 0);
  return exps.map((value) => value / total);
}
```

### 大きな値でもあふれない

学習の途中では、スコアが大きな値になることがあります。

```typescript
  it("大きな値でもあふれずに確率を求める", () => {
    expect(softmax([1000, 1000])).toEqual([0.5, 0.5]);
  });
```

```text
     × 大きな値でもあふれずに確率を求める 6ms
AssertionError: expected [ NaN, NaN ] to deeply equal [ 0.5, 0.5 ]
```

`Math.exp(1000)` は `number` で表せる範囲を超えて `Infinity` になり、`Infinity / Infinity` が `NaN`（非数）になりました。Kotlin（JVM）と同じく、JavaScript の数値計算も警告や例外を出さずに `NaN` を返します。境界の値のテストが無ければ気づけません。

ソフトマックスは、すべての値から同じ数を引いても結果が変わりません（分子と分母に同じ `exp(-c)` が掛かるため）。そこで最大値を引いてから `exp` を計算します。

```typescript
export function softmax(z: readonly number[]): number[] {
  const max = Math.max(...z);
  const exps = z.map((value) => Math.exp(value - max));
  const total = exps.reduce((sum, value) => sum + value, 0);
  return exps.map((value) => value / total);
}
```

`Math.max(...z)` の `...` は、配列を引数に展開するスプレッド構文です。

## 10.4 ロジスティック回帰

### 仮実装と三角測量

第 3 章の決定木と同じく `fit` と `predict` を持つクラスにします。1 種類のラベルだけを学習する例は、最初のラベルを返す仮実装で通ります。

```typescript
describe("LogisticRegression", () => {
  it("1 種類のラベルだけを学習するとそのラベルを予測する", () => {
    const x = [{ 花弁幅: 0.1 }, { 花弁幅: 0.2 }];
    const t = ["setosa", "setosa"];

    const model = new LogisticRegression().fit(x, t);

    expect(model.predict([{ 花弁幅: 0.15 }, { 花弁幅: 0.9 }])).toEqual([
      "setosa",
      "setosa",
    ]);
  });
});
```

```text
     × 1 種類のラベルだけを学習するとそのラベルを予測する 2ms
TypeError: LogisticRegression is not a constructor
```

```typescript
export class LogisticRegression<K extends string> {
  private label = "";

  fit(x: readonly Record<K, number>[], t: readonly string[]): this {
    this.label = t[0] ?? "";
    return this;
  }

  predict(x: readonly Record<K, number>[]): string[] {
    return x.map(() => this.label);
  }
}
```

2 種類のラベルを境界の左右で予測する例を加えると、仮実装では通りません。

```typescript
  it("2 種類のラベルを境界の左右で予測する", () => {
    const x = [
      { 花弁幅: 0.1 },
      { 花弁幅: 0.2 },
      { 花弁幅: 0.8 },
      { 花弁幅: 0.9 },
    ];
    const t = ["setosa", "setosa", "virginica", "virginica"];

    const model = new LogisticRegression().fit(x, t);

    expect(model.predict([{ 花弁幅: 0.15 }, { 花弁幅: 0.85 }])).toEqual([
      "setosa",
      "virginica",
    ]);
  });
```

```text
     × 2 種類のラベルを境界の左右で予測する 4ms
AssertionError: expected [ 'setosa', 'setosa' ] to deeply equal [ 'setosa', 'virginica' ]
```

### 勾配降下法で学習する

重みを少しずつ動かして、予測した確率を正解に近づけます。損失（交差エントロピー）を小さくする方向は、「予測した確率 − 正解」から計算できます。正解の品種を 1、それ以外を 0 とした表（one-hot 表現）を作る代わりに、**正解の品種の確率からだけ 1 を引けば**、「確率 − 正解」になります。

最初は Kotlin 版と同じ `weights[特徴量][品種]` の並びで書きました。テストは通りましたが、`noUncheckedIndexedAccess` のもとでは添字で取り出した値がすべて `number | undefined` になるので、`this.weights[f]?.[k] ?? 0` のような式が並び、読みにくいコードになりました。そこで Green のうちに、重みを **品種ごとの配列**（`weights[品種][特徴量]`）に持ち替え、内積（`dot`）と列の取り出し（`column`）を関数にしました。

```typescript
function sum(values: readonly number[]): number {
  return values.reduce((total, value) => total + value, 0);
}

export function softmax(z: readonly number[]): number[] {
  const max = Math.max(...z);
  const exps = z.map((value) => Math.exp(value - max));
  const total = sum(exps);
  return exps.map((value) => value / total);
}

/** 特徴量のレコードを、特徴量の並び順どおりの数値の配列にする */
export function toRows<K extends string>(
  x: readonly Record<K, number>[],
  features: readonly K[],
): number[][] {
  return x.map((row) => features.map((feature) => row[feature]));
}

function dot(a: readonly number[], b: readonly number[]): number {
  return a.reduce((sum, value, i) => sum + value * (b[i] as number), 0);
}

function column(matrix: readonly (readonly number[])[], j: number): number[] {
  return matrix.map((row) => row[j] as number);
}

export interface LogisticRegressionOptions {
  /** 学習率。省略すると 1 */
  learningRate?: number;
  /** 勾配降下法の繰り返し回数。省略すると 5000 */
  epochs?: number;
}

export class LogisticRegression<K extends string> {
  readonly learningRate: number;
  readonly epochs: number;
  features: K[] = [];
  classes: string[] = [];
  // weights[品種][特徴量]
  weights: number[][] = [];
  bias: number[] = [];

  constructor(options: LogisticRegressionOptions = {}) {
    this.learningRate = options.learningRate ?? 1;
    this.epochs = options.epochs ?? 5000;
  }

  private scores(row: readonly number[]): number[] {
    return this.weights.map(
      (weightsOfClass, k) =>
        dot(weightsOfClass, row) + (this.bias[k] as number),
    );
  }

  fit(x: readonly Record<K, number>[], t: readonly string[]): this {
    this.features = Object.keys(x[0] ?? {}) as K[];
    const rows = toRows(x, this.features);
    this.classes = [...new Set(t)].toSorted();
    const targets = t.map((label) => this.classes.indexOf(label));
    this.weights = this.classes.map(() => this.features.map(() => 0));
    this.bias = this.classes.map(() => 0);
    const n = rows.length;
    for (let epoch = 0; epoch < this.epochs; epoch++) {
      // 確率 − 正解（正解の品種だけ 1 を引く）
      const errors = rows.map((row, i) => {
        const error = softmax(this.scores(row));
        const target = targets[i] as number;
        error[target] = (error[target] as number) - 1;
        return error;
      });
      this.weights = this.weights.map((weightsOfClass, k) =>
        weightsOfClass.map(
          (weight, f) =>
            weight -
            (this.learningRate * dot(column(errors, k), column(rows, f))) / n,
        ),
      );
      this.bias = this.bias.map(
        (bias, k) => bias - (this.learningRate * sum(column(errors, k))) / n,
      );
    }
    return this;
  }

  predict(x: readonly Record<K, number>[]): string[] {
    return toRows(x, this.features).map((row) => {
      const scores = this.scores(row);
      return this.classes[scores.indexOf(Math.max(...scores))] as string;
    });
  }
}
```

- `toRows` は、特徴量のレコードを 1 行 1 つの数値の配列に変換します。特徴量の並び順は `fit` で決めた `this.features` に固定するので、`predict` に渡すレコードのプロパティの順番が違っても、同じ列に同じ特徴量が並びます
- 品種ごとの配列にしたので、スコアは「その品種の重みと 1 行の内積 + 切片」の 1 式で書けます。重みの更新も、「品種 k の誤差の列」と「特徴量 f の列」の内積になります
- Python 版の `features @ self.weights`（行列の積）に当たる計算を、`dot` と `column` で 1 要素ずつ組み立てています。1 回の繰り返しで全サンプルの誤差を先に求めてから重みを更新するので、Python 版と同じ **バッチ勾配降下法** です
- `this.weights = this.weights.map(...)` のように、配列を書き換えずに新しい配列を作って置き換えています。Kotlin 版は `weights[f][k] -= ...` と配列を直接書き換えました
- `predict` では確率を計算せず、スコアが最大の品種を選びます。ソフトマックスは大小関係を変えないので、結果は同じです

### 損失の記録と、学習前の予測

3 品種・2 特徴量の例と、損失が下がることと、学習前の予測がエラーになることを確かめます。

```typescript
  it("3 種類のラベルを 2 つの特徴量から予測する", () => {
    const x = [
      { 花弁長さ: 0.1, 花弁幅: 0.1 },
      { 花弁長さ: 0.2, 花弁幅: 0.2 },
      { 花弁長さ: 0.5, 花弁幅: 0.1 },
      { 花弁長さ: 0.6, 花弁幅: 0.2 },
      { 花弁長さ: 0.5, 花弁幅: 0.8 },
      { 花弁長さ: 0.6, 花弁幅: 0.9 },
    ];
    const t = [
      "setosa",
      "setosa",
      "versicolor",
      "versicolor",
      "virginica",
      "virginica",
    ];

    const model = new LogisticRegression().fit(x, t);

    expect(model.predict(x)).toEqual(t);
  });

  it("学習を繰り返すと損失が小さくなる", () => {
    const x = [
      { 花弁幅: 0.1 },
      { 花弁幅: 0.2 },
      { 花弁幅: 0.8 },
      { 花弁幅: 0.9 },
    ];
    const t = ["setosa", "setosa", "virginica", "virginica"];

    const model = new LogisticRegression({ epochs: 100 }).fit(x, t);

    expect(model.losses).toHaveLength(100);
    expect(model.losses.at(-1)).toBeLessThan(model.losses[0] as number);
  });

  it("学習する前に予測するとエラーになる", () => {
    expect(() => new LogisticRegression().predict([{ 花弁幅: 0.1 }])).toThrow(
      "fit で学習してから predict を呼んでください",
    );
  });
```

`at(-1)` は、末尾から数えて 1 つ目（最後の要素）を返す配列のメソッドです。

```text
     × 学習を繰り返すと損失が小さくなる 4ms
     × 学習する前に予測するとエラーになる 3ms
 FAIL  test/chapter10/logistic-regression.test.ts > LogisticRegression > 学習を繰り返すと損失が小さくなる
AssertionError: Target cannot be null or undefined.
 FAIL  test/chapter10/logistic-regression.test.ts > LogisticRegression > 学習する前に予測するとエラーになる
AssertionError: expected [Function] to throw an error
```

`tsc --noEmit` も、`losses` が無いことを知らせます。

```text
test/chapter10/logistic-regression.test.ts(88,18): error TS2339: Property 'losses' does not exist on type 'LogisticRegression<string>'.
test/chapter10/logistic-regression.test.ts(89,18): error TS2339: Property 'losses' does not exist on type 'LogisticRegression<string>'.
test/chapter10/logistic-regression.test.ts(89,52): error TS2339: Property 'losses' does not exist on type 'LogisticRegression<string>'.
```

注目すべきは 2 つ目の失敗です。学習前の予測は、例外にならずに **正常に値を返していました**。学習前は `classes` と `weights` が空なので、`scores` は空の配列、`Math.max()` は `-Infinity`、`indexOf(-Infinity)` は `-1` になり、`this.classes[-1]` は `undefined` です。それを `as string` が文字列として通してしまい、予測は `[undefined]` になっていました。

型アサーション `as string` は、「この値は必ず文字列だ」と型チェッカーに約束するだけで、実行時には何も確かめません。Kotlin 版では空のリストに `maxBy` を呼んで例外になりましたが、TypeScript 版では誤った値が黙って返ります。`as` を書いた箇所は、約束が守られる条件（ここでは「学習済みであること」）を別に保証する必要があります。

損失を記録し、学習前の予測では第 3 章の決定木と同じメッセージのエラーを投げるようにします。確率は損失の計算と誤差の計算の両方で使うので、先に全サンプル分を求めておき、誤差はスプレッド構文（`[...p]`）で写してから書き換えます。

```typescript
const EPSILON = 1e-12;

export function crossEntropy(
  probabilities: readonly (readonly number[])[],
  targets: readonly number[],
): number {
  const logs = probabilities.map((p, i) =>
    Math.log((p[targets[i] as number] as number) + EPSILON),
  );
  return -sum(logs) / probabilities.length;
}
```

```typescript
    const n = rows.length;
    const losses: number[] = [];
    for (let epoch = 0; epoch < this.epochs; epoch++) {
      const probabilities = rows.map((row) => softmax(this.scores(row)));
      losses.push(crossEntropy(probabilities, targets));
      // 確率 − 正解（正解の品種だけ 1 を引く）
      const errors = probabilities.map((p, i) => {
        const error = [...p];
        const target = targets[i] as number;
        error[target] = (error[target] as number) - 1;
        return error;
      });
      // ...（重みと切片の更新は同じ）
    }
    this.losses = losses;
    return this;
  }

  predict(x: readonly Record<K, number>[]): string[] {
    if (this.classes.length === 0) {
      throw new Error("fit で学習してから predict を呼んでください");
    }
    return toRows(x, this.features).map((row) => {
      const scores = this.scores(row);
      return this.classes[scores.indexOf(Math.max(...scores))] as string;
    });
  }
```

- 交差エントロピーは、正解の品種の確率の対数の平均にマイナスを付けたものです。one-hot 表現との積の和を取る Python 版と同じ値になります
- `EPSILON` は、確率が 0 のときに `Math.log(0)` が `-Infinity` にならないように足す小さな値です
- 損失は `losses` に集めてから、学習の最後に `this.losses` に入れます。クラスには `losses: number[] = [];` のプロパティを足しました

## 10.5 ランダムフォレスト

### 仕組み

ランダムフォレストは、次の 2 つの「ばらつき」を持たせた決定木をたくさん作り、予測を多数決で決めます。

1. **ブートストラップ標本**: 訓練データから、同じ件数を重複を許して選び直したデータで木を学習する（バギング）
2. **特徴量の部分集合**: 木ごとに使う特徴量を一部だけに絞る

1 本 1 本の決定木は訓練データの細部を覚えて過学習しがちですが、違うデータ・違う特徴量で学習した木の多数決を取ると、個々の木の癖が打ち消し合います。

この章では、第 3 章の `DecisionTree` を再利用します。そのため、特徴量の絞り込みは「木ごと」に行います。

### 多数決とブートストラップ標本

```typescript
// test/chapter10/random-forest.test.ts
describe("majorityVote", () => {
  it("サンプルごとに最も多い予測を選ぶ", () => {
    const votes = [
      ["setosa", "virginica"],
      ["setosa", "virginica"],
      ["versicolor", "setosa"],
    ];

    expect(majorityVote(votes)).toEqual(["setosa", "virginica"]);
  });
});

describe("bootstrapSample", () => {
  it("元のデータと同じ件数の行番号を重複を許して選ぶ", () => {
    const rows = bootstrapSample(100, createRandom(0));

    expect(rows).toHaveLength(100);
    expect(
      rows.every((row) => Number.isInteger(row) && row >= 0 && row < 100),
    ).toBe(true);
    expect(new Set(rows).size).toBeLessThan(100);
  });

  it("同じシードなら同じ行を選ぶ", () => {
    expect(bootstrapSample(10, createRandom(42))).toEqual(
      bootstrapSample(10, createRandom(42)),
    );
  });
});
```

`votes` は「木ごとの予測のリスト」です。乱数は第 2 章で自作した `createRandom`（mulberry32）を使います。

```text
 FAIL  test/chapter10/random-forest.test.ts [ test/chapter10/random-forest.test.ts ]
Error: Cannot find module '../../src/chapter10/random-forest.ts' imported from test/chapter10/random-forest.test.ts
```

多数決は、第 3 章の葉で使った `majority` と同じ処理です。第 3 章の `majority` はファイルの中だけで使う関数だったので、`export` を 1 つ付けて再利用します。

```typescript
// src/chapter03/decision-tree.ts
export function majority(labels: readonly string[]): string {
```

```typescript
// src/chapter10/random-forest.ts
import { majority } from "../chapter03/decision-tree.ts";

export function majorityVote(votes: readonly (readonly string[])[]): string[] {
  const samples = votes[0] ?? [];
  return samples.map((_, sample) =>
    majority(votes.map((predictions) => predictions[sample] as string)),
  );
}

export function bootstrapSample(size: number, random: () => number): number[] {
  return Array.from({ length: size }, () => Math.floor(random() * size));
}
```

- `majorityVote` は、サンプルの番号ごとに各木の予測を集め（`votes.map((predictions) => predictions[sample])`）、`majority` で最も多いラベルを選びます。同数のときは、先に現れたラベルを選ぶ第 3 章の規則がそのまま使われます
- `Math.floor(random() * size)` は、0 以上 1 未満の乱数から 0 以上 `size` 未満の整数を作ります。乱数生成器を引数で受け取るのは、森全体で 1 つの生成器を使い回し、シード 1 つで全部の木の乱数を再現できるようにするためです

### 森を作る

```typescript
function twoSpecies() {
  const sepalWidths = [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4, 0.5, 0.3];
  const petalWidths = [
    0.1, 0.12, 0.14, 0.16, 0.18, 0.8, 0.82, 0.84, 0.86, 0.88,
  ];
  const x = sepalWidths.map((sepalWidth, i) => ({
    がく片幅: sepalWidth,
    花弁幅: petalWidths[i] as number,
  }));
  const t = [...Array(5).fill("setosa"), ...Array(5).fill("virginica")];
  return { x, t };
}

describe("RandomForest", () => {
  it("指定した数だけ第 3 章の決定木を学習する", () => {
    const { x, t } = twoSpecies();

    const model = new RandomForest({
      nEstimators: 5,
      maxFeatures: 1,
      seed: 0,
    }).fit(x, t);

    expect(model.trees).toHaveLength(5);
  });

  it("各決定木は指定した数の特徴量だけを使う", () => {
    const { x, t } = twoSpecies();

    const model = new RandomForest({
      nEstimators: 5,
      maxFeatures: 1,
      seed: 0,
    }).fit(x, t);

    expect(model.trees.every((tree) => tree.columns.length === 1)).toBe(true);
  });

  it("決定木の多数決で予測する", () => {
    const { x, t } = twoSpecies();

    const model = new RandomForest({
      nEstimators: 25,
      maxFeatures: 2,
      seed: 0,
    }).fit(x, t);

    const newX = [
      { がく片幅: 0.4, 花弁幅: 0.13 },
      { がく片幅: 0.4, 花弁幅: 0.83 },
    ];
    expect(model.predict(newX)).toEqual(["setosa", "virginica"]);
  });

  it("同じシードなら同じ特徴量を選び同じ予測になる", () => {
    const { x, t } = twoSpecies();

    const first = new RandomForest({
      nEstimators: 5,
      maxFeatures: 1,
      seed: 7,
    }).fit(x, t);
    const second = new RandomForest({
      nEstimators: 5,
      maxFeatures: 1,
      seed: 7,
    }).fit(x, t);

    expect(first.trees.map((tree) => tree.columns)).toEqual(
      second.trees.map((tree) => tree.columns),
    );
    expect(first.predict(x)).toEqual(second.predict(x));
  });

  it("学習する前に予測するとエラーになる", () => {
    expect(() => new RandomForest().predict([{ 花弁幅: 0.1 }])).toThrow(
      "fit で学習してから predict を呼んでください",
    );
  });
});
```

`[...Array(5).fill("setosa"), ...]` は、同じ値を 5 個並べた配列をスプレッド構文でつなげています。

```text
     × 指定した数だけ第 3 章の決定木を学習する 5ms
     × 各決定木は指定した数の特徴量だけを使う 1ms
     × 決定木の多数決で予測する 1ms
     × 同じシードなら同じ特徴量を選び同じ予測になる 1ms
     × 学習する前に予測するとエラーになる 4ms
 FAIL  test/chapter10/random-forest.test.ts > RandomForest > 指定した数だけ第 3 章の決定木を学習する
TypeError: RandomForest is not a constructor
```

部品（多数決・ブートストラップ標本・第 2 章のシャッフル・第 3 章の決定木）がそろっているので、組み立てるだけの明白な実装で進めます。

```typescript
import { createRandom, shuffle } from "../chapter02/random.ts";
import { DecisionTree, majority } from "../chapter03/decision-tree.ts";
```

```typescript
export function selectColumns<K extends string>(
  x: readonly Record<K, number>[],
  columns: readonly K[],
): Record<K, number>[] {
  return x.map(
    (row) =>
      Object.fromEntries(
        columns.map((column) => [column, row[column]]),
      ) as Record<K, number>,
  );
}

export interface FittedTree<K extends string> {
  columns: K[];
  rows: number[];
  model: DecisionTree<K>;
}

export interface RandomForestOptions {
  /** 決定木の数。省略すると 10 */
  nEstimators?: number;
  /** 1 本の木が使う特徴量の数。省略すると 2 */
  maxFeatures?: number;
  /** 木の深さの上限。省略すると制限しない */
  maxDepth?: number;
  /** 乱数のシード。省略すると 0 */
  seed?: number;
}

export class RandomForest<K extends string> {
  readonly nEstimators: number;
  readonly maxFeatures: number;
  readonly maxDepth: number | undefined;
  readonly seed: number;
  trees: FittedTree<K>[] = [];

  constructor(options: RandomForestOptions = {}) {
    this.nEstimators = options.nEstimators ?? 10;
    this.maxFeatures = options.maxFeatures ?? 2;
    this.maxDepth = options.maxDepth;
    this.seed = options.seed ?? 0;
  }

  fit(x: readonly Record<K, number>[], t: readonly string[]): this {
    const random = createRandom(this.seed);
    const features = Object.keys(x[0] ?? {}) as K[];
    this.trees = Array.from({ length: this.nEstimators }, () => {
      const rows = bootstrapSample(x.length, random);
      const chosen = new Set(
        shuffle(features, random).slice(0, this.maxFeatures),
      );
      const columns = features.filter((feature) => chosen.has(feature));
      const model = new DecisionTree<K>({ maxDepth: this.maxDepth }).fit(
        selectColumns(
          rows.map((row) => x[row] as Record<K, number>),
          columns,
        ),
        rows.map((row) => t[row] as string),
      );
      return { columns, rows, model };
    });
    return this;
  }

  predict(x: readonly Record<K, number>[]): string[] {
    if (this.trees.length === 0) {
      throw new Error("fit で学習してから predict を呼んでください");
    }
    return majorityVote(
      this.trees.map((tree) =>
        tree.model.predict(selectColumns(x, tree.columns)),
      ),
    );
  }
}
```

- 1 本分の情報（使った列・学習に使った行番号・学習した木）は、`FittedTree` にまとめました。行番号は、特徴量の重要度を計算するときに使います（10.6 節）
- `shuffle(features, random).slice(0, this.maxFeatures)` で、重複なしに列を選びます。`filter` で元の列の順に戻しておくと、同じ組み合わせが同じ並びになります
- `selectColumns` は、指定した列だけを持つレコードを作ります。`Object.fromEntries` は `[キー, 値]` の組の配列からオブジェクトを作る関数で、戻り値の型は `{ [k: string]: number }` です。これを `as Record<K, number>` で型付けしています。実際には `columns` に含まれる列しか持たないので、この `as` は型よりも少ない列しか持たない値を通しています。10.6 節で、この影響が現れます
- オプションはオブジェクトで受け取ります。TypeScript には Kotlin のような名前付き引数が無いので、`new RandomForest({ nEstimators: 5, seed: 0 })` のようにプロパティ名で指定します

第 3 章の `DecisionTree` には、`majority` の `export` 以外は手を入れていません。`fit` と `predict` という小さな公開 API で作ってあったので、部品としてそのまま組み込めました。

## 10.6 特徴量の重要度

### 計算方法

決定木の各節で、

```text
減少量 = その節に届いた件数 × (その節のジニ不純度 − 分割後のジニ不純度)
```

を求め、分割に使った特徴量ごとに合計し、全体が 1 になるように割合にします。第 3 章の `Split` の `impurity` は「分割後のジニ不純度（件数で重み付けした平均）」なので、そのまま使えます。

ただし、第 3 章の木の節は、その節に届いた件数を持っていません。そこで、学習に使ったデータをもう一度木に流して、節ごとに件数とジニ不純度を求めます。

### 決定木 1 本の重要度

```typescript
// test/chapter10/feature-importance.test.ts
describe("treeImportances", () => {
  it("分割しない木はすべての特徴量の重要度が 0", () => {
    const x = [
      { がく片幅: 0.3, 花弁幅: 0.1 },
      { がく片幅: 0.5, 花弁幅: 0.2 },
    ];
    const t = ["setosa", "setosa"];

    expect(treeImportances({ kind: "leaf", label: "setosa" }, x, t)).toEqual({
      がく片幅: 0,
      花弁幅: 0,
    });
  });
});
```

第 3 章の木は判別可能なユニオンなので、葉だけの木は `{ kind: "leaf", label: "setosa" }` というオブジェクトリテラルで書けます。

```text
 FAIL  test/chapter10/feature-importance.test.ts [ test/chapter10/feature-importance.test.ts ]
Error: Cannot find module '../../src/chapter10/feature-importance.ts' imported from test/chapter10/feature-importance.test.ts
```

すべて 0 を返す仮実装で通ります。

```typescript
// src/chapter10/feature-importance.ts
import type { Tree } from "../chapter03/decision-tree.ts";

export function treeImportances<K extends string>(
  tree: Tree<K>,
  x: readonly Record<K, number>[],
  t: readonly string[],
): Record<K, number> {
  const features = Object.keys(x[0] ?? {}) as K[];
  return Object.fromEntries(features.map((feature) => [feature, 0])) as Record<
    K,
    number
  >;
}
```

テストは通りますが、ESLint は使っていない引数 `t` を指摘します。仮実装であることの目印として、次の三角測量で解消します。

```text
  6:3  error  't' is defined but never used  @typescript-eslint/no-unused-vars
```

次に、花弁幅だけで分割する木と、2 つの特徴量で 2 回分割する木を例にします。木はテストの中で第 3 章の決定木に学習させて作るので、学習できなかったときに例外を投げる小さな関数 `fitTree` を用意しました。

```typescript
function fitTree<K extends string>(
  x: readonly Record<K, number>[],
  t: readonly string[],
): Tree<K> {
  const tree = new DecisionTree<K>().fit(x, t).tree;
  if (tree === null) {
    throw new Error("決定木を学習できませんでした");
  }
  return tree;
}
```

```typescript
  it("1 回だけ分割する木は分割に使った特徴量の重要度が 1", () => {
    const x = [
      { がく片幅: 0.3, 花弁幅: 0.1 },
      { がく片幅: 0.5, 花弁幅: 0.2 },
      { がく片幅: 0.4, 花弁幅: 0.8 },
      { がく片幅: 0.6, 花弁幅: 0.9 },
    ];
    const t = ["setosa", "setosa", "virginica", "virginica"];

    expect(treeImportances(fitTree(x, t), x, t)).toEqual({
      がく片幅: 0,
      花弁幅: 1,
    });
  });

  it("分割で減った不純度を件数で重み付けして割合にする", () => {
    const x = [
      { 花弁長さ: 0.1, 花弁幅: 0.1 },
      { 花弁長さ: 0.2, 花弁幅: 0.1 },
      { 花弁長さ: 0.3, 花弁幅: 0.1 },
      { 花弁長さ: 0.8, 花弁幅: 0.2 },
      { 花弁長さ: 0.7, 花弁幅: 0.9 },
      { 花弁長さ: 0.9, 花弁幅: 0.9 },
    ];
    const t = [
      "setosa",
      "setosa",
      "setosa",
      "versicolor",
      "virginica",
      "virginica",
    ];

    const importances = treeImportances(fitTree(x, t), x, t);

    expect(importances.花弁長さ).toBeCloseTo(7 / 11, 12);
    expect(importances.花弁幅).toBeCloseTo(4 / 11, 12);
  });
```

2 つ目の例の期待値は、手で計算して決めました。

| 節 | 件数 | 節のジニ不純度 | 分割 | 分割後のジニ不純度 | 減少量 |
|----|------|--------------|------|-----------------|--------|
| 根 | 6 | 1 − (9 + 1 + 4) / 36 = 11/18 | 花弁長さ ≤ 0.5（setosa 3 件と残り 3 件） | 3/6 × 0 + 3/6 × 4/9 = 2/9 | 6 × (11/18 − 2/9) = 7/3 |
| 右の子 | 3 | 1 − (1 + 4) / 9 = 4/9 | 花弁幅 ≤ 0.55（花弁長さでは分け切れない） | 0 | 3 × 4/9 = 4/3 |

合計 11/3 に対する割合で、花弁長さが 7/11、花弁幅が 4/11 になります。

```text
     × 1 回だけ分割する木は分割に使った特徴量の重要度が 1 9ms
     × 分割で減った不純度を件数で重み付けして割合にする 1ms
AssertionError: expected { 'がく片幅': +0, '花弁幅': +0 } to deeply equal { 'がく片幅': +0, '花弁幅': 1 }
AssertionError: expected +0 to be close to 0.6363636363636364, received difference is 0.6363636363636364, but expected 5e-13
```

木をたどりながら「特徴量と減少量の組」を集め、特徴量ごとに合計して割合に直します。

```typescript
import { type Tree, gini } from "../chapter03/decision-tree.ts";

interface Decrease<K extends string> {
  feature: K;
  amount: number;
}

function impurityDecreases<K extends string>(
  tree: Tree<K>,
  x: readonly Record<K, number>[],
  t: readonly string[],
): Decrease<K>[] {
  switch (tree.kind) {
    case "leaf":
      return [];
    case "node": {
      const { feature, threshold, impurity } = tree.split;
      const goesLeft = x.map((row) => row[feature] <= threshold);
      const left = goesLeft.flatMap((isLeft, i) => (isLeft ? [i] : []));
      const right = goesLeft.flatMap((isLeft, i) => (isLeft ? [] : [i]));
      return [
        { feature, amount: t.length * (gini(t) - impurity) },
        ...impurityDecreases(tree.left, pick(x, left), pick(t, left)),
        ...impurityDecreases(tree.right, pick(x, right), pick(t, right)),
      ];
    }
  }
}

function pick<T>(items: readonly T[], positions: readonly number[]): T[] {
  return positions.map((i) => items[i] as T);
}
```

- データを左右に振り分ける部分は、第 3 章の `buildTree` と同じ形の再帰です。`switch (tree.kind)` で葉と節を場合分けしています
- `switch` の後に `return` がありませんが、`tsc` はエラーにしません。`tree.kind` は `"leaf"` か `"node"` のどちらかで、どちらの場合も `return` するので、関数の終わりに到達しないことを型チェッカーが判断できるからです
- 再帰の結果はスプレッド構文でつなぎ、書き換える変数を持たない形にしました

```typescript
export function treeImportances<K extends string>(
  tree: Tree<K>,
  x: readonly Record<K, number>[],
  t: readonly string[],
): Record<K, number> {
  const features = Object.keys(x[0] ?? {}) as K[];
  const decreases = impurityDecreases(tree, x, t);
  return normalize(
    features,
    features.map((feature) =>
      decreases
        .filter((decrease) => decrease.feature === feature)
        .reduce((sum, decrease) => sum + decrease.amount, 0),
    ),
  );
}

/** 特徴量ごとの値を、合計が 1 になるように割合にする。合計が 0 ならそのまま */
function normalize<K extends string>(
  features: readonly K[],
  totals: readonly number[],
): Record<K, number> {
  const total = totals.reduce((sum, value) => sum + value, 0);
  return Object.fromEntries(
    features.map((feature, i) => {
      const value = totals[i] as number;
      return [feature, total === 0 ? value : value / total];
    }),
  ) as Record<K, number>;
}
```

「合計で割って割合にする」処理は、次のランダムフォレストの重要度でも使うので、`normalize` に切り出しました。JavaScript のオブジェクトは、文字列のキーを追加した順に並ぶので、重要度も列の順に並びます。

### ランダムフォレストの重要度

森の重要度は、木ごとの重要度（学習に使ったブートストラップ標本で計算）の平均を、もう一度割合に直したものです。木が 1 本なら、その木の重要度と一致するはずです。

```typescript
describe("forestImportances", () => {
  it("木が 1 本なら学習に使った行でのその木の重要度と一致する", () => {
    const sepalWidths = [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4];
    const petalWidths = [0.1, 0.12, 0.14, 0.16, 0.8, 0.82, 0.84, 0.86];
    const x = sepalWidths.map((sepalWidth, i) => ({
      がく片幅: sepalWidth,
      花弁幅: petalWidths[i] as number,
    }));
    const t = [...Array(4).fill("setosa"), ...Array(4).fill("virginica")];
    const forest = new RandomForest({
      nEstimators: 1,
      maxFeatures: 2,
      seed: 0,
    }).fit(x, t);
    const [fitted] = forest.trees;
    if (fitted?.model.tree == null) {
      throw new Error("決定木を学習できませんでした");
    }

    const expected = treeImportances(
      fitted.model.tree,
      fitted.rows.map((row) => x[row] as (typeof x)[number]),
      fitted.rows.map((row) => t[row] as string),
    );

    expect(forestImportances(forest, x, t)).toEqual(expected);
  });
});
```

- `fitted?.model.tree == null` は、`fitted` が `undefined` のときと、木が `null`（学習前）のときの両方を 1 つの条件で確かめます。この `if` を通った後は、`fitted.model.tree` が `Tree` 型に絞り込まれます
- `(typeof x)[number]` は、配列 `x` の要素の型です。テストの中で作ったオブジェクトリテラルの型を、名前を付けずに参照しています

```text
     × 木が 1 本なら学習に使った行でのその木の重要度と一致する 4ms
TypeError: forestImportances is not a function
```

```typescript
export function forestImportances<K extends string>(
  forest: RandomForest<K>,
  x: readonly Record<K, number>[],
  t: readonly string[],
): Record<K, number> {
  const features = Object.keys(x[0] ?? {}) as K[];
  const perTree = forest.trees.map(({ columns, rows, model }) => {
    if (model.tree === null) {
      throw new Error("fit で学習してから重要度を求めてください");
    }
    return treeImportances(
      model.tree,
      selectColumns(pick(x, rows), columns),
      pick(t, rows),
    );
  });
  return normalize(
    features,
    features.map(
      (feature) =>
        perTree.reduce(
          (sum, importances) => sum + (importances[feature] ?? 0),
          0,
        ) / forest.trees.length,
    ),
  );
}
```

木ごとの重要度には、その木が使った列しか入っていません。`selectColumns` が使った列だけのレコードを作るので、`treeImportances` の `Object.keys(x[0])` も使った列だけになるからです。ところが、その型は `Record<K, number>` で、すべての列を持つことになっています。10.5 節の `selectColumns` の `as` が、型よりも少ない列しか持たない値を通していたためです。

そのため、使わなかった列の `importances[feature]` は、型の上では `number` なのに、実行時には `undefined` になります。`?? 0` で 0 として平均しています。Kotlin 版では `Map` の `get` が null になりうることを型が表していましたが、TypeScript 版では `as` で型を付けた時点で、その情報が型から消えています。`as` を書くときは、型と実際の値のずれがどこに影響するかを、呼び出し側まで追う必要があります。

## 10.7 モデル共通のインターフェース

### interface で「fit と predict を持つもの」を表す

決定木・ロジスティック回帰・ランダムフォレストは、どれも `fit(x, t)` と `predict(x)` を持っています。これを `interface` で表します。

まず、テスト用の単純なモデル `AlwaysSetosa` で、評価関数の振る舞いを決めます。

```typescript
// test/chapter10/evaluate.test.ts
type PetalWidth = Record<"花弁幅", number>;

class AlwaysSetosa implements Classifier<"花弁幅"> {
  fit(): this {
    return this;
  }

  predict(x: readonly PetalWidth[]): string[] {
    return x.map(() => "setosa");
  }
}

function smallSplit(): TrainTestSplit<PetalWidth, string> {
  return {
    xTrain: [
      { 花弁幅: 0.1 },
      { 花弁幅: 0.2 },
      { 花弁幅: 0.8 },
      { 花弁幅: 0.9 },
    ],
    xTest: [{ 花弁幅: 0.15 }, { 花弁幅: 0.25 }],
    tTrain: ["setosa", "setosa", "virginica", "virginica"],
    tTest: ["setosa", "setosa"],
  };
}

describe("evaluate", () => {
  it("学習させてから訓練データとテストデータの正解率を求める", () => {
    expect(evaluate(new AlwaysSetosa(), smallSplit())).toEqual({
      train: 0.5,
      test: 1,
    });
  });
});
```

`AlwaysSetosa` の `fit` は引数を受け取りません。TypeScript では、インターフェースより **少ない** 引数のメソッドで実装できます。呼び出し側が渡した余分な引数は、無視されるだけだからです。

```text
 FAIL  test/chapter10/evaluate.test.ts [ test/chapter10/evaluate.test.ts ]
Error: Cannot find module '../../src/chapter10/classifier.ts' imported from test/chapter10/evaluate.test.ts
```

```typescript
// src/chapter10/classifier.ts
import { accuracy } from "../chapter01/kinoko-takenoko.ts";
import type { TrainTestSplit } from "../chapter02/iris-preprocessing.ts";

/** fit で学習し、predict でラベルを予測する分類モデル */
export interface Classifier<K extends string> {
  fit(x: readonly Record<K, number>[], t: readonly string[]): this;
  predict(x: readonly Record<K, number>[]): string[];
}

export interface Score {
  train: number;
  test: number;
}

export function evaluate<K extends string>(
  model: Classifier<K>,
  split: TrainTestSplit<Record<K, number>, string>,
): Score {
  model.fit(split.xTrain, split.tTrain);
  return {
    train: accuracy(model.predict(split.xTrain), split.tTrain),
    test: accuracy(model.predict(split.xTest), split.tTest),
  };
}
```

- 正解率は第 1 章の `accuracy`、分割結果は第 2 章の `TrainTestSplit` を再利用しています
- `fit` の戻り値の型 `this` は、「実装したクラス自身の型」を表します。`new AlwaysSetosa().fit()` の結果は `AlwaysSetosa` 型になります

### 3 つのモデルを同じ関数で評価する

次に、第 3 章の決定木と、この章で作った 2 つのモデルを `Classifier<"花弁幅">[]` に入れるテストを書きました。

```typescript
  it("第 3 章の決定木と自作のモデルを同じ関数で評価できる", () => {
    const models: Classifier<"花弁幅">[] = [
      new DecisionTree({ maxDepth: 1 }),
      new LogisticRegression(),
      new RandomForest({ nEstimators: 5, maxFeatures: 1, seed: 0 }),
    ];

    const scores = models.map((model) => evaluate(model, smallSplit()));

    expect(scores).toEqual([
      { train: 1, test: 1 },
      { train: 1, test: 1 },
      { train: 1, test: 1 },
    ]);
  });
```

このテストは、実装を 1 行も足さずに通り、`tsc --noEmit` もエラーを出しませんでした。3 つのクラスはどれも `implements Classifier` と宣言していませんが、同じ形の `fit` と `predict` を持っているので、`Classifier` として型が合います。TypeScript のインターフェースは、形で型を判定する **構造的部分型**（structural subtyping）だからです。

Kotlin 版では、同じテストが `List<Any>` だというコンパイルエラーになり、第 3 章の決定木をアダプターで包みました。TypeScript 版では、Python 版の `Protocol` と同じく、既存のクラスに手を入れずに共通化できます。

| 観点 | Python の `Protocol` | Kotlin の `interface` | TypeScript の `interface` |
|------|--------------------|-----------------------|--------------------------|
| 型が合う条件 | 同じ名前・型のメソッドを持つ（構造的部分型） | 実装を宣言している（名前的部分型） | 同じ名前・型のメソッドを持つ（構造的部分型） |
| 既存のクラス（第 3 章の決定木） | そのまま入れられる | アダプターで包む | そのまま入れられる |
| 型の確認 | mypy を実行したとき | コンパイルのとき | `tsc` を実行したとき（Vitest は型を確かめない） |

形が合わないものは、型チェックで分かります。10.8 節で使う ml-logistic-regression のモデルをそのまま入れてみると、`tsc --noEmit` は次のエラーを出しました。

```typescript
import LogisticRegression from "ml-logistic-regression";
import type { Classifier } from "../../src/chapter10/classifier.ts";

export const models: Classifier<"花弁幅">[] = [new LogisticRegression()];
```

```text
test/chapter10/tmp-structural.test.ts(4,45): error TS2741: Property 'fit' is missing in type 'LogisticRegression' but required in type 'Classifier<"花弁幅">'.
```

ml.js のモデルは `fit` ではなく `train` で学習し、数値の行列と整数のラベルを受け取ります。名前も形も違うので、次の節でアダプターを書きます。確かめたあと、この一時的なファイルは削除しました。

## 10.8 ml.js のモデルを同じインターフェースで使う

### ml.js のモデルを Classifier に包む

ml-logistic-regression と ml-random-forest は、どちらも「数値の行列と 0 始まりの整数のラベルで `train` し、整数のラベルを `predict` で返す」形をしています。第 3 章の `trainMlCart` と同じく、この形を `NumericModel` として、文字列のラベルとの変換を 1 つのアダプター `MlClassifier` にまとめます。

```typescript
// test/chapter10/ml-classifier.test.ts
type Row = Record<"がく片幅" | "花弁幅", number>;

function twoSpeciesSplit(): TrainTestSplit<Row, string> {
  const sepalWidths = [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4, 0.5, 0.3];
  const petalWidths = [
    0.1, 0.12, 0.14, 0.16, 0.18, 0.8, 0.82, 0.84, 0.86, 0.88,
  ];
  return {
    xTrain: sepalWidths.map((sepalWidth, i) => ({
      がく片幅: sepalWidth,
      花弁幅: petalWidths[i] as number,
    })),
    xTest: [
      { がく片幅: 0.4, 花弁幅: 0.13 },
      { がく片幅: 0.4, 花弁幅: 0.83 },
    ],
    tTrain: [...Array(5).fill("setosa"), ...Array(5).fill("virginica")],
    tTest: ["setosa", "virginica"],
  };
}

describe("MlClassifier", () => {
  it("ml.js のロジスティック回帰とランダムフォレストも同じ関数で評価できる", () => {
    const models: Classifier<"がく片幅" | "花弁幅">[] = [
      mlLogisticRegression(),
      mlRandomForest({ nEstimators: 50, maxFeatures: 2, seed: 0 }),
    ];

    const scores = models.map((model) => evaluate(model, twoSpeciesSplit()));

    expect(scores).toEqual([
      { train: 1, test: 1 },
      { train: 1, test: 1 },
    ]);
  });

  it("学習する前に予測するとエラーになる", () => {
    expect(() => mlLogisticRegression().predict([{ 花弁幅: 0.1 }])).toThrow(
      "fit で学習してから predict を呼んでください",
    );
  });

  it("文字列のラベルを 0 始まりの整数にして学習し、予測を文字列に戻す", () => {
    const received: number[][] = [];
    const model = new MlClassifier(() => ({
      train: (_x: number[][], y: number[]) => {
        received.push(y);
      },
      predict: (x: number[][]) => x.map(() => 1),
    }));

    model.fit(
      [{ 花弁幅: 0.1 }, { 花弁幅: 0.9 }, { 花弁幅: 0.2 }],
      ["virginica", "setosa", "virginica"],
    );

    expect(received).toEqual([[1, 0, 1]]);
    expect(model.predict([{ 花弁幅: 0.5 }])).toEqual(["virginica"]);
  });
});
```

3 つ目のテストは、ml.js のモデルの代わりに、受け取ったラベルを記録するだけのオブジェクトリテラルを渡しています。`NumericModel` もインターフェースなので、`train` と `predict` を持つオブジェクトなら、クラスを作らずに渡せます。

```text
 FAIL  test/chapter10/ml-classifier.test.ts [ test/chapter10/ml-classifier.test.ts ]
Error: Cannot find module '../../src/chapter10/ml-classifier.ts' imported from test/chapter10/ml-classifier.test.ts
```

ml-logistic-regression は型定義を同梱しないので、第 3 章の ml-cart と同じく、使う範囲だけを宣言します。

```typescript
// src/types/ml-logistic-regression.d.ts
// ml-logistic-regression 2.0.0 は型定義を同梱しないので、この章で使う範囲だけを宣言する
declare module "ml-logistic-regression" {
  import type { Matrix } from "ml-matrix";

  export interface LogisticRegressionOptions {
    /** 勾配降下法の繰り返し回数。既定値は 50000 */
    numSteps?: number;
    /** 学習率。既定値は 5e-4 */
    learningRate?: number;
  }

  /** 品種ごとの 2 値分類器を組み合わせる（one-vs-rest）ロジスティック回帰。切片の項は無い */
  export default class LogisticRegression {
    constructor(options?: LogisticRegressionOptions);
    /** y は 0 始まりの整数のラベルを並べた列ベクトル */
    train(x: Matrix, y: Matrix): void;
    predict(x: Matrix): number[];
  }
}
```

ml-random-forest は型定義を同梱しています。

```typescript
// src/chapter10/ml-classifier.ts
import LogisticRegression, {
  type LogisticRegressionOptions,
} from "ml-logistic-regression";
import { Matrix } from "ml-matrix";
import { RandomForestClassifier } from "ml-random-forest";
import type { Classifier } from "./classifier.ts";
import { toRows } from "./logistic-regression.ts";

/** 数値の行列と 0 始まりの整数のラベルで学習・予測する ml.js のモデル */
export interface NumericModel {
  train(x: number[][], y: number[]): void;
  predict(x: number[][]): number[];
}

/** ml.js のモデルを、特徴量のレコードと文字列のラベルで使える Classifier に包む */
export class MlClassifier<K extends string> implements Classifier<K> {
  private readonly createModel: () => NumericModel;
  private model: NumericModel | null = null;
  private features: K[] = [];
  private classes: string[] = [];

  constructor(createModel: () => NumericModel) {
    this.createModel = createModel;
  }

  fit(x: readonly Record<K, number>[], t: readonly string[]): this {
    this.features = Object.keys(x[0] ?? {}) as K[];
    this.classes = [...new Set(t)].toSorted();
    const model = this.createModel();
    model.train(
      toRows(x, this.features),
      t.map((label) => this.classes.indexOf(label)),
    );
    this.model = model;
    return this;
  }

  predict(x: readonly Record<K, number>[]): string[] {
    if (this.model === null) {
      throw new Error("fit で学習してから predict を呼んでください");
    }
    return this.model
      .predict(toRows(x, this.features))
      .map((index) => this.classes[index] as string);
  }
}
```

- アダプターはモデルそのものではなく、モデルを作る関数（`createModel`）を受け取ります。`fit` のたびに新しいモデルを作るので、同じアダプターで何度学習しても前回の学習が残りません
- `implements Classifier<K>` は、構造的部分型では必須ではありません。書いておくと、`fit` の形がインターフェースとずれたときに、使う側ではなくこのクラスの定義でエラーになります。Kotlin 版の `override` と同じ役割です
- `tsconfig.json` の `erasableSyntaxOnly` では、コンストラクターの引数にアクセス修飾子を付けてプロパティにする書き方（パラメータープロパティ）が使えないので、`this.createModel = createModel` と代入しています

それぞれのモデルを作る関数は、次のとおりです。

```typescript
export interface MlLogisticRegressionOptions extends LogisticRegressionOptions {
  /** true なら値が 1 の列を足して、切片の代わりにする。省略すると false */
  intercept?: boolean;
}

export function mlLogisticRegression<K extends string>(
  options: MlLogisticRegressionOptions = {},
): MlClassifier<K> {
  const { intercept = false, ...modelOptions } = options;
  const toMatrix = (x: number[][]) =>
    new Matrix(intercept ? x.map((row) => [...row, 1]) : x);
  return new MlClassifier(() => {
    const model = new LogisticRegression(modelOptions);
    return {
      train: (x, y) => model.train(toMatrix(x), Matrix.columnVector(y)),
      predict: (x) => model.predict(toMatrix(x)),
    };
  });
}

export interface MlRandomForestOptions {
  nEstimators: number;
  maxFeatures: number;
  maxDepth?: number;
  seed: number;
}

/**
 * ml-random-forest のランダムフォレスト。自作と条件をそろえるため、木ごとに特徴量を重複なしで選び、
 * 内側の ml-cart の決定木は 1 件になるまで分ける。使わない袋外の予測は計算しない。
 */
export function mlRandomForest<K extends string>(
  options: MlRandomForestOptions,
): MlClassifier<K> {
  return new MlClassifier(
    () =>
      new RandomForestClassifier({
        nEstimators: options.nEstimators,
        maxFeatures: options.maxFeatures,
        seed: options.seed,
        replacement: false,
        noOOB: true,
        treeOptions: {
          gainFunction: "gini",
          minNumSamples: 1,
          gainThreshold: 0,
          maxDepth: options.maxDepth ?? Infinity,
        },
      }),
  );
}
```

`intercept` のオプションと、ml-random-forest に渡している設定の理由は、この後の 2 つの小節で説明します。

### ml-logistic-regression には切片が無い

ml-logistic-regression 2.0.0 のソースを読むと、自作と次の点が違います。

| 項目 | 自作 | ml-logistic-regression |
|------|------|------------------------|
| 多クラスへの広げ方 | ソフトマックス（1 つのモデル） | one-vs-rest（品種ごとの 2 値分類器） |
| 切片 | 品種ごとに `bias` を学習する | **無い**（重みは特徴量の数だけ） |
| 勾配の計算 | 全サンプルの平均 | 全サンプルの合計 |
| 学習率と繰り返し回数の既定値 | 1、5000 回 | 5e-4、50000 回 |

切片が無いと、スコアは「重み × 特徴量」だけになり、特徴量が 0 のところで必ず 0 になります。2 値分類器の境界が原点を通る直線（1 つの特徴量なら 0 の点）に固定されるので、特徴量が正の値だけのデータは、0.5 を境に左右に分けられないはずです。値が 1 の列を足すと、その列の重みが切片の役割をします。これを `intercept` のオプションとして、テストで確かめました。

```typescript
  it("切片が無いと、特徴量が正の値だけのデータを境界の左右に分けられない", () => {
    const x = [
      { 花弁幅: 0.1 },
      { 花弁幅: 0.2 },
      { 花弁幅: 0.8 },
      { 花弁幅: 0.9 },
    ];
    const t = ["setosa", "setosa", "virginica", "virginica"];
    const newX = [{ 花弁幅: 0.15 }, { 花弁幅: 0.85 }];

    const withoutIntercept = mlLogisticRegression().fit(x, t);
    const withIntercept = mlLogisticRegression({ intercept: true }).fit(x, t);

    expect(withoutIntercept.predict(newX)).not.toEqual(["setosa", "virginica"]);
    expect(withIntercept.predict(newX)).toEqual(["setosa", "virginica"]);
  });
```

```text
     × 切片が無いと、特徴量が正の値だけのデータを境界の左右に分けられない 146ms
AssertionError: expected [ 'virginica', 'virginica' ] to deeply equal [ 'setosa', 'virginica' ]
```

```text
test/chapter10/ml-classifier.test.ts(81,50): error TS2353: Object literal may only specify known properties, and 'intercept' does not exist in type 'LogisticRegressionOptions'.
```

Vitest は、まだ無い `intercept` のオプションを黙って無視して実行し、2 つ目の期待で失敗しました。1 つ目の期待（切片が無いと分けられない）は、この時点で成り立っています。`tsc` は、オブジェクトリテラルに型に無いプロパティがあること（余剰プロパティ）をエラーにしました。

`MlLogisticRegressionOptions` で `intercept` を足し、分割代入の残余（`...modelOptions`）で ml-logistic-regression に渡すオプションから取り除いています。`intercept` が `true` なら、`toMatrix` が各行の末尾に 1 を足します。学習と予測の両方で同じ変換を通すので、列の数がずれることはありません。

### ml-random-forest の癖を学習用テストに残す

ml-random-forest 2.1.0 のソースと実行で、次の 4 つを確かめ、学習用テストに残しました。

```typescript
// test/chapter10/ml-random-forest-learning.test.ts
import { RandomForestClassifier } from "ml-random-forest";
import { describe, expect, it } from "vitest";

// ml-random-forest 2.1.0 の振る舞いを確かめる学習用テスト
const x = [
  [0.5, 0.1],
  [0.3, 0.12],
  [0.6, 0.14],
  [0.4, 0.16],
  [0.5, 0.18],
  [0.3, 0.8],
  [0.6, 0.82],
  [0.4, 0.84],
  [0.5, 0.86],
  [0.3, 0.88],
];
const y = [0, 0, 0, 0, 0, 1, 1, 1, 1, 1];

describe("ml-random-forest の RandomForestClassifier", () => {
  it("木が少ないと、袋外の予測が空の行があり学習に失敗する", () => {
    const forest = new RandomForestClassifier({ nEstimators: 2, seed: 0 });

    expect(() => forest.train(x, y)).toThrow("input must not be empty");
  });

  it("袋外の予測を計算しなければ木が少なくても学習できる", () => {
    const forest = new RandomForestClassifier({
      nEstimators: 2,
      seed: 0,
      noOOB: true,
    });

    forest.train(x, y);

    expect(forest.predict([[0.4, 0.13]])).toEqual([0]);
  });

  it("maxFeatures の 1 は 1 個ではなく、すべての特徴量（100%）を表す", () => {
    const forest = new RandomForestClassifier({
      nEstimators: 5,
      maxFeatures: 1,
      seed: 0,
      noOOB: true,
    });

    forest.train(x, y);

    expect(forest.indexes?.every((used) => used.length === 2)).toBe(true);
  });

  it("既定では特徴量を重複を許して選ぶので、同じ列を 2 回使う木がある", () => {
    const forest = new RandomForestClassifier({
      nEstimators: 20,
      maxFeatures: 2,
      seed: 0,
      noOOB: true,
    });

    forest.train(x, y);

    expect(
      forest.indexes?.some((used) => new Set(used).size < used.length),
    ).toBe(true);
  });
});
```

- **袋外の予測**: ブートストラップ標本に選ばれなかった行（袋外、out-of-bag）で、学習と同時に予測を計算します。木が少ないと、どの木でも袋外にならない行が残り、その行の予測の多数決が空の配列に対して呼ばれて失敗します。アダプターでは袋外の予測を使わないので、`noOOB: true` で計算を止めています
- **`maxFeatures` の 1**: `maxFeatures` は、0 より大きく 1 以下なら割合、それより大きな整数なら個数として扱われます。1 は割合の 100% になるので、「特徴量を 1 個だけ使う」森は作れません。JavaScript には整数と小数の区別が無く、`1` と `1.0` は同じ値だからです
- **重複を許した特徴量の選択**: 既定の `replacement: true` では、特徴量を重複を許して選ぶので、`maxFeatures: 2` でも同じ列を 2 回使い、実際には 1 つの特徴量しか使わない木ができます。自作と条件をそろえるため、アダプターでは `replacement: false` にしています

特徴量の選び方は、自作と同じ「木ごと」です。ソースのコメントには「分割ごとに選ぶほうがよいかもしれない」と書かれています。内側の決定木には第 3 章で確かめた ml-cart を使うので、`treeOptions` は第 3 章の `trainMlCart` と同じ設定（1 件になるまで分け、利得の下限を 0 にする）にしました。

ml-random-forest は `featureImportance` という重要度のメソッドも持っていますが、同梱の型定義には含まれていません。ソースを読むと、節の減少量から子の節の減少量を引いた値を合計していて、10.6 節の計算方法とは違います。この章では重要度を自作だけで扱います。

## 10.9 実データで突き合わせる

### 実データのテスト

`prepareIris` で前処理した iris で、自作のモデルと ml.js の正解率を確かめます。値は、実装を実データで動かして得たものをテストで固定しました（Red を経ていません）。

```typescript
// test/chapter10/iris-data.test.ts
const csvFile = join(dataDir(), "iris.csv");

// ml-logistic-regression は既定で 50000 回繰り返すので、1 回の学習に数秒かかる
describe.skipIf(!existsSync(csvFile))(
  "iris.csv の実データ",
  { timeout: 30_000 },
  () => {
    let split: TrainTestSplit<Record<Feature, number>, string>;

    beforeAll(() => {
      split = prepareIris(csvFile, 0.3, 0);
    });

    it("ロジスティック回帰はテストデータの 45 件中 42 件を正しく分類する", () => {
      const score = evaluate(new LogisticRegression(), split);

      expect(score.test).toBeCloseTo(42 / 45, 12);
    });

    it("ランダムフォレストは訓練データを分け切りテストデータの 45 件中 42 件を正しく分類する", () => {
      const score = evaluate(
        new RandomForest({ nEstimators: 100, maxFeatures: 2, seed: 0 }),
        split,
      );

      expect(score.train).toBe(1);
      expect(score.test).toBeCloseTo(42 / 45, 12);
    });

    it("ml-logistic-regression は切片が無いとテストデータの 45 件中 26 件しか正しく分類できない", () => {
      const score = evaluate(mlLogisticRegression(), split);

      expect(score.test).toBeCloseTo(26 / 45, 12);
    });

    it("ml-logistic-regression は値が 1 の列を足すとテストデータの 45 件中 43 件を正しく分類する", () => {
      const score = evaluate(mlLogisticRegression({ intercept: true }), split);

      expect(score.test).toBeCloseTo(43 / 45, 12);
    });

    it("ml-random-forest は訓練データを分け切りテストデータの 45 件中 44 件を正しく分類する", () => {
      const score = evaluate(
        mlRandomForest({ nEstimators: 100, maxFeatures: 2, seed: 0 }),
        split,
      );

      expect(score.train).toBe(1);
      expect(score.test).toBeCloseTo(44 / 45, 12);
    });
```

最初はタイムアウトを指定していませんでした。表示のテスト（次の小節）で ml-logistic-regression を 2 回学習させると、Vitest の既定のタイムアウト 5 秒を超えて失敗しました。

```text
     × 実行するとモデルごとの正解率とランダムフォレストの重要度を表示する 6849ms
Error: Test timed out in 5000ms.
```

ml-logistic-regression は既定で 50000 回繰り返し、1 回ごとに ml-matrix の行列を何個も作るので、iris の訓練データ 105 件でも 1 回の学習に 2 秒ほどかかります。`describe` の 2 つ目の引数 `{ timeout: 30_000 }` で、このグループのテストだけタイムアウトを 30 秒に延ばしました。`30_000` の `_` は、数値を読みやすくする区切りで、値は `30000` と同じです。

### モデルを比べる

`node src/chapter10/main.ts` で、iris のテストデータでの正解率と、ランダムフォレストの特徴量の重要度を表示します。表示のテストを先に書き、`main` が無いことを確かめてから実装しました。

```typescript
    it("実行するとモデルごとの正解率とランダムフォレストの重要度を表示する", () => {
      const lines: string[] = [];

      main((line) => lines.push(line));

      expect(lines).toEqual([
        "モデル\t訓練データ\tテストデータ",
        "決定木（深さ 2）\t0.9238\t0.9778",
        "ロジスティック回帰\t0.8952\t0.9333",
        "ランダムフォレスト（100 本）\t1.0000\t0.9333",
        "ランダムフォレスト（100 本・深さ 2）\t0.9333\t0.9778",
        "ml-logistic-regression（切片なし）\t0.7048\t0.5778",
        "ml-logistic-regression（値が 1 の列を追加）\t0.8857\t0.9556",
        "ml-random-forest（100 本）\t1.0000\t0.9778",
        "",
        "ランダムフォレスト（100 本）の特徴量の重要度:",
        "がく片長さ\t0.2200",
        "がく片幅\t0.1271",
        "花弁長さ\t0.2505",
        "花弁幅\t0.4024",
      ]);
    });
  },
);
```

```text
 FAIL  test/chapter10/iris-data.test.ts [ test/chapter10/iris-data.test.ts ]
Error: Cannot find module '../../src/chapter10/main.ts' imported from test/chapter10/iris-data.test.ts
```

```typescript
// src/chapter10/main.ts
import { join } from "node:path";
import { type Feature, prepareIris } from "../chapter02/iris-preprocessing.ts";
import { DecisionTree } from "../chapter03/decision-tree.ts";
import { dataDir } from "../dataset.ts";
import { type Classifier, evaluate } from "./classifier.ts";
import { forestImportances } from "./feature-importance.ts";
import { LogisticRegression } from "./logistic-regression.ts";
import { mlLogisticRegression, mlRandomForest } from "./ml-classifier.ts";
import { RandomForest } from "./random-forest.ts";

const TEST_SIZE = 0.3;
const SEED = 0;
const N_ESTIMATORS = 100;
const MAX_FEATURES = 2;
const SHALLOW_DEPTH = 2;

export function models(): [string, Classifier<Feature>][] {
  const forest = { nEstimators: N_ESTIMATORS, maxFeatures: MAX_FEATURES };
  return [
    [
      `決定木（深さ ${SHALLOW_DEPTH}）`,
      new DecisionTree({ maxDepth: SHALLOW_DEPTH }),
    ],
    ["ロジスティック回帰", new LogisticRegression()],
    [
      `ランダムフォレスト（${N_ESTIMATORS} 本）`,
      new RandomForest({ ...forest, seed: SEED }),
    ],
    [
      `ランダムフォレスト（${N_ESTIMATORS} 本・深さ ${SHALLOW_DEPTH}）`,
      new RandomForest({ ...forest, maxDepth: SHALLOW_DEPTH, seed: SEED }),
    ],
    ["ml-logistic-regression（切片なし）", mlLogisticRegression()],
    [
      "ml-logistic-regression（値が 1 の列を追加）",
      mlLogisticRegression({ intercept: true }),
    ],
    [
      `ml-random-forest（${N_ESTIMATORS} 本）`,
      mlRandomForest({ ...forest, seed: SEED }),
    ],
  ];
}

export function main(print: (line: string) => void = console.log): void {
  const split = prepareIris(join(dataDir(), "iris.csv"), TEST_SIZE, SEED);
  print("モデル\t訓練データ\tテストデータ");
  for (const [name, model] of models()) {
    const score = evaluate(model, split);
    print(`${name}\t${score.train.toFixed(4)}\t${score.test.toFixed(4)}`);
  }

  const forest = new RandomForest<Feature>({
    nEstimators: N_ESTIMATORS,
    maxFeatures: MAX_FEATURES,
    seed: SEED,
  }).fit(split.xTrain, split.tTrain);
  print("");
  print(`ランダムフォレスト（${N_ESTIMATORS} 本）の特徴量の重要度:`);
  const importances = forestImportances(forest, split.xTrain, split.tTrain);
  for (const [feature, value] of Object.entries(importances)) {
    print(`${feature}\t${value.toFixed(4)}`);
  }
}

// node src/chapter10/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
```

- `models()` の戻り値の型 `[string, Classifier<Feature>][]` は、「名前とモデルの組（タプル）」の配列です。`for (const [name, model] of models())` のループでは、自作のモデルか ml.js かを気にせず `evaluate` を呼べます
- `models()` の中の `new RandomForest(...)` には型引数を書いていません。戻り値の型 `Classifier<Feature>` から、`K` が `Feature` だと推論されるからです

最初は、重要度を計算する森も `new RandomForest({ ... })` と型引数なしで書いていました。`tsc` は次のエラーを出しました。

```text
src/chapter10/main.ts(60,41): error TS2345: Argument of type 'RandomForest<string>' is not assignable to parameter of type 'RandomForest<"がく片長さ" | "がく片幅" | "花弁長さ" | "花弁幅">'.
  Types of property 'trees' are incompatible.
    Type 'FittedTree<string>[]' is not assignable to type 'FittedTree<"がく片長さ" | "がく片幅" | "花弁長さ" | "花弁幅">[]'.
      Type 'FittedTree<string>' is not assignable to type 'FittedTree<"がく片長さ" | "がく片幅" | "花弁長さ" | "花弁幅">'.
        Types of property 'columns' are incompatible.
          Type 'string[]' is not assignable to type '("がく片長さ" | "がく片幅" | "花弁長さ" | "花弁幅")[]'.
            Type 'string' is not assignable to type '"がく片長さ" | "がく片幅" | "花弁長さ" | "花弁幅"'.
```

コンストラクターの引数（オプション）には `K` が現れないので、代入先の型が無いところでは `K` を推論する手がかりが無く、制約の `string` になります。`RandomForest<string>` を `forestImportances` に `Record<Feature, number>` のデータと一緒に渡すと、`K` を 1 つに決められません。`new RandomForest<Feature>({ ... })` と型引数を明示して直しました。Vitest は型を確かめないので、この誤りはテストの実行では見つからず、`tsc` だけが知らせました。

```bash
cd apps/node
node src/chapter10/main.ts
```

```text
モデル	訓練データ	テストデータ
決定木（深さ 2）	0.9238	0.9778
ロジスティック回帰	0.8952	0.9333
ランダムフォレスト（100 本）	1.0000	0.9333
ランダムフォレスト（100 本・深さ 2）	0.9333	0.9778
ml-logistic-regression（切片なし）	0.7048	0.5778
ml-logistic-regression（値が 1 の列を追加）	0.8857	0.9556
ml-random-forest（100 本）	1.0000	0.9778

ランダムフォレスト（100 本）の特徴量の重要度:
がく片長さ	0.2200
がく片幅	0.1271
花弁長さ	0.2505
花弁幅	0.4024
```

結果から読み取れることは次のとおりです。

- **アンサンブルがいつも勝つわけではない**。深さを制限しないランダムフォレスト（0.9333）は、第 3 章の深さ 2 の決定木（0.9778）より低くなりました。木 1 本 1 本が訓練データを分け切るまで深く育ち（訓練データの正解率 1.0000）、iris のような小さなデータでは多数決でも過学習を打ち消し切れていません。木の深さを 2 に制限した森は、決定木と同じ 0.9778 です
- **テストデータ 45 件では、1 件の違いが 0.0222 の差になる**。1 回の分割で測った正解率の小さな差でモデルの優劣を決めるのは危険です。分け方を変えて何度も測る交差検証は第 11 章で扱います
- **ランダムフォレストの重要度は、複数の特徴量に分散する**。木ごとに使える特徴量を 2 つに絞っているので、花弁幅を使えない木は別の特徴量で分割するためです

### ml.js と突き合わせる

**ロジスティック回帰**: 切片の無い ml-logistic-regression は、テストデータで 0.5778（26/45）でした。10.8 節で確かめたとおり、2 値分類器の境界が原点を通るので、特徴量がすべて正の値の iris では品種をうまく分けられません。値が 1 の列を足すと 0.9556（43/45）になり、自作（0.9333）と 1 件差になりました。多クラスへの広げ方（ソフトマックスと one-vs-rest）と、学習率・繰り返し回数が違うので、正解率がそろっても重みの値は一致しません。

**ランダムフォレスト**: 自作と ml-random-forest は、どちらも訓練データを分け切り（1.0000）、テストデータは自作が 0.9333（42/45）、ml-random-forest が 0.9778（44/45）でした。木ごとに特徴量を重複なしで 2 つ選ぶ点、ブートストラップ標本で学習する点、木を 1 件になるまで分ける点はそろえましたが、乱数の生成器（自作は mulberry32、ml-random-forest は random-js の MersenneTwister19937）が違うので、選ばれる行と特徴量が違い、1 本 1 本の木が違います。テストデータ 45 件の 2 件の差は、この違いで説明できる範囲です。

同じ「ロジスティック回帰」という名前でも、切片の有無のような基本的な設定が違えば、正解率は大きく変わります。ライブラリの結果と比べるときは、名前ではなく設定とソースを確かめてから比べます。

テストの実行結果です。

```bash
npx vitest run test/chapter10
```

第 10 章のテストは 36 件すべて通ります。データが無い環境では、実データのテスト 6 件がスキップされ、残りの 30 件が通ります。

## 10.10 Notebook で探索する

TypeScript 版では Notebook による探索と可視化を扱いません。モデルごとの正解率・ロジスティック回帰の損失の推移・モデル別の特徴量の重要度・森の大きさと正解率のグラフは、[Python 版の 10.9 節](../python/10-logistic-regression-and-ensemble.md) か [Kotlin 版の 10.11 節](../kotlin/10-logistic-regression-and-ensemble.md) を参照してください。

## 10.11 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、共通のインターフェースで ml.js と並べて評価しました。

1. **数値として正しい実装** — ソフトマックス関数は、定義どおりでは大きな値で `NaN` になった。JavaScript は警告を出さないので、境界の値のテストで見つけ、最大値を引く方法で直した
2. **配列の持ち方を読みやすさで選ぶ** — `noUncheckedIndexedAccess` のもとで添字だらけになった勾配降下法を、重みを品種ごとの配列に持ち替え、`dot` と `column` で書き直した
3. **型アサーションは実行時に何も確かめない** — 学習前の予測では `as string` が `undefined` を通し、森の重要度では `as Record<K, number>` が型より少ない列を通した。`as` の約束が守られる条件を、テストと実装で別に保証した
4. **構造的部分型** — TypeScript の `interface` は形で型を判定するので、第 3 章の決定木をそのまま `Classifier` として評価できた。形の違う ml.js のモデルは、`MlClassifier` で包んだ
5. **設定とソースを確かめて突き合わせる** — ml-logistic-regression には切片が無く、ml-random-forest は袋外の予測・`maxFeatures` の 1・重複を許した特徴量の選択に癖があった。違いの理由を学習用テストに残した

iris のテストデータ 45 件では、どのモデルも数件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。
