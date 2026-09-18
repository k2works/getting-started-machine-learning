import { existsSync } from "node:fs";
import { join } from "node:path";
import { beforeAll, describe, expect, it } from "vitest";
import { type Point, sseByClusterCount } from "../../src/chapter14/kmeans.ts";
import { main } from "../../src/chapter14/main.ts";
import {
  loadSpending,
  SPENDING_COLUMNS,
  standardize,
  type Spending,
} from "../../src/chapter14/spending.ts";
import { dataDir } from "../../src/dataset.ts";

const csvFile = join(dataDir(), "Wholesale.csv");

describe.skipIf(!existsSync(csvFile))("Wholesale.csv の実データ", () => {
  let rows: Spending[];
  let points: Point[];

  beforeAll(() => {
    rows = loadSpending(csvFile);
    points = standardize(rows, SPENDING_COLUMNS);
  });

  it("440 件の支出額 6 列を読み込む", () => {
    expect([rows.length, Object.keys(rows[0] as Spending).length]).toEqual([
      440, 6,
    ]);
  });

  it("標準化したデータのクラスタ数 1 の SSE は件数と列数の積になる", () => {
    const sse = sseByClusterCount(points, [1], 0);

    expect(sse.get(1)).toBeCloseTo(440 * 6, 6);
  });

  it("クラスタ数 1〜9 では、クラスタ数を増やすほど SSE が小さくなる", () => {
    const sse = [
      ...sseByClusterCount(points, [1, 2, 3, 4, 5, 6, 7, 8, 9], 0).values(),
    ];

    sse.slice(1).forEach((after, i) => {
      expect(after).toBeLessThan(sse[i] as number);
    });
  });

  it("初期中心 10 通りでは、クラスタ数 10 が局所解に止まり SSE がクラスタ数 9 より大きい", () => {
    const sse = sseByClusterCount(points, [9, 10], 0);

    expect(sse.get(10)).toBeGreaterThan(sse.get(9) as number);
  });

  it("初期中心を 20 通りに増やすと、クラスタ数 10 の SSE がクラスタ数 9 より小さくなる", () => {
    const sse = sseByClusterCount(points, [9, 10], 0, 20);

    expect(sse.get(10)).toBeLessThan(sse.get(9) as number);
  });

  it("実行すると SSE・ml-kmeans との一致・クラスタごとの件数と平均支出額を表示する", () => {
    const lines: string[] = [];

    main((line) => lines.push(line));

    expect(lines).toEqual([
      "データ件数: 440（支出額 6 列）",
      "クラスタ数ごとの SSE（初期中心 10 通りの最小値）:",
      "クラスタ数\t自作\tml-kmeans（k-means++）",
      "1\t2640.00\t2640.00",
      "2\t1954.18\t1954.65",
      "3\t1608.43\t1614.50",
      "4\t1333.17\t1325.98",
      "5\t1070.46\t1058.77",
      "6\t989.84\t918.42",
      "7\t891.23\t827.49",
      "8\t777.72\t742.61",
      "9\t666.15\t661.47",
      "10\t705.77\t597.87",
      "同じ初期中心で ml-kmeans と結果が一致した数: 100 / 100",
      "",
      "クラスタ数 5 のクラスタごとの件数と平均支出額:",
      "クラスタ\t件数\tFresh\tMilk\tGrocery\tFrozen\tDetergents_Paper\tDelicassen",
      "1\t277\t9203\t2969\t3773\t2594\t967\t983",
      "3\t96\t5509\t10556\t16478\t1420\t7199\t1659",
      "2\t54\t36043\t5007\t6118\t6736\t1006\t2593",
      "4\t11\t16911\t34864\t46126\t3245\t23008\t4177",
      "0\t2\t34782\t30367\t16898\t48702\t756\t26776",
    ]);
  });
});
