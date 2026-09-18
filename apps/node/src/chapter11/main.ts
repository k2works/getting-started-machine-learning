import { join } from "node:path";
import { dataDir } from "../dataset.ts";
import { N_SPLITS, evaluateCinema, evaluateSurvived } from "./experiments.ts";

export function main(print: (line: string) => void = console.log): void {
  print(`Survived（決定木、${N_SPLITS} 分割交差検証の平均）`);
  const survived = evaluateSurvived(join(dataDir(), "Survived.csv"));
  for (const [name, score] of Object.entries(survived)) {
    print(`  ${name}: ${score.toFixed(4)}`);
  }
  print(`cinema（線形回帰、${N_SPLITS} 分割交差検証の平均）`);
  const cinema = evaluateCinema(join(dataDir(), "cinema.csv"));
  for (const [name, score] of Object.entries(cinema)) {
    print(`  ${name}: ${score.toFixed(2)}`);
  }
}

// node src/chapter11/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
