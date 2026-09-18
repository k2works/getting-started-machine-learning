function sum(values: readonly number[]): number {
  return values.reduce((total, value) => total + value, 0);
}

export function softmax(z: readonly number[]): number[] {
  const max = Math.max(...z);
  const exps = z.map((value) => Math.exp(value - max));
  const total = sum(exps);
  return exps.map((value) => value / total);
}

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
  losses: number[] = [];

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
}
