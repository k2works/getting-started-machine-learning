import { describe, expect, it } from "vitest";
import {
  assignClusters,
  bestKMeans,
  chooseInitialCenters,
  kmeans,
  sseByClusterCount,
  sumOfSquaredErrors,
  updateCenters,
} from "../../src/chapter14/kmeans.ts";

describe("assignClusters", () => {
  it("各点を最も近い中心のクラスタに割り当てる", () => {
    const points = [[0], [1], [9], [10]];
    const centers = [[0], [10]];

    expect(assignClusters(points, centers)).toEqual([0, 0, 1, 1]);
  });

  it("2 次元の点をユークリッド距離で最も近い中心に割り当てる", () => {
    const points = [
      [0, 0],
      [5, 4],
      [1, 0],
    ];
    const centers = [
      [5, 5],
      [0, 0],
    ];

    expect(assignClusters(points, centers)).toEqual([1, 0, 1]);
  });
});

describe("updateCenters", () => {
  it("クラスタごとに割り当てられた点の平均を新しい中心にする", () => {
    const points = [
      [0, 0],
      [2, 0],
      [10, 10],
      [10, 12],
    ];
    const previous = [
      [0, 0],
      [0, 0],
    ];

    expect(updateCenters(points, [0, 0, 1, 1], previous)).toEqual([
      [1, 0],
      [10, 11],
    ]);
  });

  it("点が 1 つも割り当てられなかったクラスタは中心を変えない", () => {
    const points = [
      [0, 0],
      [2, 4],
    ];
    const previous = [
      [0, 0],
      [99, 99],
    ];

    expect(updateCenters(points, [0, 0], previous)).toEqual([
      [1, 2],
      [99, 99],
    ]);
  });
});

describe("sumOfSquaredErrors", () => {
  it("各点と所属するクラスタの中心との距離の 2 乗を合計する", () => {
    const points = [
      [0, 0],
      [2, 0],
      [10, 10],
      [10, 12],
    ];
    const centers = [
      [1, 0],
      [10, 11],
    ];

    expect(sumOfSquaredErrors(points, [0, 0, 1, 1], centers)).toBe(4);
  });

  it("中心から離れた点ほど誤差が大きくなる", () => {
    expect(sumOfSquaredErrors([[0], [4]], [0, 0], [[1]])).toBe(10);
  });
});

function twoGroups(): number[][] {
  return [
    [0, 0],
    [0, 1],
    [10, 10],
    [10, 11],
  ];
}

describe("kmeans", () => {
  it("割り当てが変わらなくなるまで割り当てと中心の更新を繰り返す", () => {
    const result = kmeans(twoGroups(), [
      [0, 0],
      [0, 1],
    ]);

    expect(result).toEqual({
      labels: [0, 0, 1, 1],
      centers: [
        [0, 0.5],
        [10, 10.5],
      ],
      sse: 1,
      iterations: 3,
    });
  });

  it("最大反復回数に達したら収束していなくても打ち切る", () => {
    const result = kmeans(
      twoGroups(),
      [
        [0, 0],
        [0, 1],
      ],
      { maxIterations: 1 },
    );

    expect(result.centers).toEqual([
      [0, 0],
      [expect.closeTo(20 / 3, 9), expect.closeTo(22 / 3, 9)],
    ]);
    expect(result.labels).toEqual([0, 0, 1, 1]);
    expect(result.iterations).toBe(1);
  });
});

function numberedPoints(size: number): number[][] {
  return Array.from({ length: size }, (_, i) => [i, i * 2]);
}

describe("chooseInitialCenters", () => {
  it("データの中から重複なくクラスタ数だけ点を選ぶ", () => {
    const points = numberedPoints(10);

    const centers = chooseInitialCenters(points, 3, 0);

    expect(new Set(centers.map((center) => center.join(","))).size).toBe(3);
    for (const center of centers) {
      expect(points).toContainEqual(center);
    }
  });

  it("同じシードなら同じ点を選ぶ", () => {
    const points = numberedPoints(10);

    expect(chooseInitialCenters(points, 3, 42)).toEqual(
      chooseInitialCenters(points, 3, 42),
    );
  });

  it("シードが違えば違う点を選ぶ", () => {
    const points = numberedPoints(10);

    expect(chooseInitialCenters(points, 3, 0)).not.toEqual(
      chooseInitialCenters(points, 3, 1),
    );
  });
});

describe("sseByClusterCount", () => {
  it("クラスタ数ごとにクラスタリングしたときの SSE を求める", () => {
    expect(sseByClusterCount(twoGroups(), [1, 2], 0)).toEqual(
      new Map([
        [1, 201],
        [2, 1],
      ]),
    );
  });

  it("初期中心を変えて繰り返し最小の SSE を使う", () => {
    expect(sseByClusterCount(threePairs(), [3], 8, 10)).toEqual(
      new Map([[3, 1.5]]),
    );
  });
});

function threePairs(): number[][] {
  return [0, 1, 10, 11, 20, 21].map((x) => [x]);
}

describe("bestKMeans", () => {
  it("初期中心によっては局所解に陥る", () => {
    const stuck = kmeans(threePairs(), [[0], [1], [10]]);

    expect(stuck.sse).toBe(101);
  });

  it("複数の初期中心の候補のうち SSE が最小の結果を返す", () => {
    const candidates = [
      [[0], [1], [10]],
      [[0], [10], [20]],
    ];

    const result = bestKMeans(threePairs(), candidates);

    expect(result.sse).toBe(1.5);
    expect(result.centers).toEqual([[0.5], [10.5], [20.5]]);
  });
});
