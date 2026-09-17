import { existsSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  accuracy,
  loadPeople,
  predictByRule,
  splitFeaturesAndLabels,
} from "../../src/chapter01/kinoko-takenoko.ts";
import { main } from "../../src/chapter01/main.ts";
import { dataDir } from "../../src/dataset.ts";

const csvFile = join(dataDir(), "KvsT.csv");

describe.skipIf(!existsSync(csvFile))("KvsT.csv の実データ", () => {
  it("実データから 19 人分を読み込む", () => {
    expect(loadPeople(csvFile)).toHaveLength(19);
  });

  it("ルールによる判定の正解率を実データで計算する", () => {
    const { features, labels } = splitFeaturesAndLabels(loadPeople(csvFile));

    const predictions = features.map(predictByRule);

    expect(accuracy(predictions, labels)).toBeCloseTo(14 / 19, 12);
  });

  it("実行するとデータ件数と正解率を表示する", () => {
    const lines: string[] = [];

    main((line) => lines.push(line));

    expect(lines).toEqual([
      "データ件数: 19",
      "ルールによる判定の正解率: 0.7368",
    ]);
  });
});
