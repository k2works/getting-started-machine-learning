import { join } from "node:path";
import { dataDir } from "../dataset.ts";
import { loadStandardizedBoston } from "./boston-standardized.ts";
import { componentsNeeded, fitPca, topLoadings } from "./pca.ts";

const THRESHOLD = 0.8;
const TOP_K = 3;
const COMPONENTS_TO_EXPLAIN = 2;

function formatLoadings(loadings: [string, number][]): string {
  return loadings
    .map(([column, value]) => `${column} ${value.toFixed(3)}`)
    .join(", ");
}

export function main(print: (line: string) => void = console.log): void {
  const { columns, x } = loadStandardizedBoston(join(dataDir(), "Boston.csv"));
  const model = fitPca(x, columns.length);
  const ratios = model.explainedVarianceRatio;
  const needed = componentsNeeded(ratios, THRESHOLD);
  const shown = ratios.slice(0, needed);
  print(`データ件数: ${x.length}, 列数: ${columns.length}`);
  print(
    "寄与率: " + shown.map((r, i) => `PC${i + 1} ${r.toFixed(4)}`).join(", "),
  );
  const cumulative = shown.reduce((sum, r) => sum + r, 0);
  print(
    `累積寄与率が ${THRESHOLD} に届く主成分の数: ${needed}（累積寄与率 ${cumulative.toFixed(4)}）`,
  );
  model.components.slice(0, COMPONENTS_TO_EXPLAIN).forEach((component, i) => {
    const loadings = topLoadings(component, columns, TOP_K);
    print(`第 ${i + 1} 主成分で影響の大きい列: ${formatLoadings(loadings)}`);
  });
}

// node src/chapter13/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
