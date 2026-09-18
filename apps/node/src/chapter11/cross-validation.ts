import { createRandom, shuffle } from "../chapter02/random.ts";
import type { Metric } from "./metrics.ts";

export interface Fold {
  train: number[];
  test: number[];
}

export function kFold(nSamples: number, nSplits: number, seed: number): Fold[] {
  const positions = shuffle(
    Array.from({ length: nSamples }, (_, i) => i),
    createRandom(seed),
  );
  const sizes = Array.from(
    { length: nSplits },
    (_, i) => Math.floor(nSamples / nSplits) + (i < nSamples % nSplits ? 1 : 0),
  );
  let start = 0;
  const tests = sizes.map((size) => {
    const test = positions.slice(start, start + size);
    start += size;
    return test;
  });
  return tests.map((test) => ({
    train: positions.filter((i) => !test.includes(i)),
    test,
  }));
}

export interface Model<X, T> {
  fit(x: readonly X[], t: readonly T[]): void;
  predict(x: readonly X[]): T[];
}

function pick<T>(items: readonly T[], positions: readonly number[]): T[] {
  return positions.map((i) => items[i] as T);
}

export function* crossValidate<X, T>(
  makeModel: () => Model<X, T>,
  x: readonly X[],
  t: readonly T[],
  folds: readonly Fold[],
  metric: Metric<T>,
): Generator<number> {
  for (const fold of folds) {
    const model = makeModel();
    model.fit(pick(x, fold.train), pick(t, fold.train));
    yield metric(pick(t, fold.test), model.predict(pick(x, fold.test)));
  }
}
