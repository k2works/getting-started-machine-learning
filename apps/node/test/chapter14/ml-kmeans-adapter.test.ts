import { kmeans as mlKMeans } from "ml-kmeans";
import { describe, expect, it } from "vitest";
import { kmeans } from "../../src/chapter14/kmeans.ts";
import {
  mlKMeansBestSse,
  trainMlKMeans,
} from "../../src/chapter14/ml-kmeans-adapter.ts";

function twoGroups(): number[][] {
  return [
    [0, 0],
    [0, 1],
    [10, 10],
    [10, 11],
  ];
}

describe("trainMlKMeans", () => {
  it("同じ初期中心なら割り当て・中心・SSE・反復回数が自作と一致する", () => {
    const initialCenters = [
      [0, 0],
      [0, 1],
    ];

    expect(trainMlKMeans(twoGroups(), initialCenters)).toEqual(
      kmeans(twoGroups(), initialCenters),
    );
  });

  it("局所解に陥る初期中心なら ml-kmeans も同じ局所解になる", () => {
    const points = [0, 1, 10, 11, 20, 21].map((x) => [x]);

    expect(trainMlKMeans(points, [[0], [1], [10]]).sse).toBe(101);
  });
});

describe("mlKMeansBestSse", () => {
  it("k-means++ でもシードを変えて繰り返し最小の SSE を使える", () => {
    const points = [0, 1, 10, 11, 20, 21].map((x) => [x]);

    expect(mlKMeansBestSse(points, 3, 0, 10)).toBe(1.5);
  });
});

describe("ml-kmeans の癖", () => {
  it("反復を打ち切ると、クラスタ番号は更新前の中心への割り当てのまま返る", () => {
    const result = mlKMeans(twoGroups(), 2, {
      initialization: [
        [0, 0],
        [0, 1],
      ],
      maxIterations: 1,
    });

    expect(result.clusters).toEqual([0, 1, 1, 1]);
    expect(result.centroids).toEqual([
      [0, 0],
      [expect.closeTo(20 / 3, 9), expect.closeTo(22 / 3, 9)],
    ]);
  });

  it("maxIterations に 0 を渡すと上限なしになる", () => {
    const result = mlKMeans(twoGroups(), 2, {
      initialization: [
        [0, 0],
        [0, 1],
      ],
      maxIterations: 0,
    });

    expect(result.iterations).toBe(3);
  });
});
