import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  type TrainTestSplit,
  countMissing,
} from "../../src/chapter02/iris-preprocessing.ts";
import {
  joinWeather,
  loadBike,
  loadWeather,
  meanCountByWeather,
} from "../../src/chapter09/bike-weather.ts";
import { prepareBoston, scoreFeatureSet } from "../../src/chapter09/boston.ts";
import { dummyCategories, encodeDummies } from "../../src/chapter09/dummies.ts";
import { fitLinearRegression } from "../../src/chapter09/linear-model.ts";
import {
  iqrOutliers,
  quantile,
  removeTargetOutliers,
} from "../../src/chapter09/outliers.ts";
import { polynomialFeatures } from "../../src/chapter09/polynomial-features.ts";
import { Standardizer } from "../../src/chapter09/standardizer.ts";

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

  it("カテゴリに無い値はすべての列が 0 になる", () => {
    const encoded = encodeDummies([{ CRIME: "unknown" }], "CRIME", [
      "low",
      "very_low",
    ]);

    expect(encoded).toEqual([{ CRIME_low: 0, CRIME_very_low: 0 }]);
  });
});

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

  it("訓練データの平均と標準偏差で別のデータを標準化する", () => {
    const standardizer = new Standardizer({ RM: 2 }, { RM: 0.5 });
    const other = [{ RM: 1 }, { RM: 2 }, { RM: 4 }];

    expect(standardizer.transform(other)).toEqual([
      { RM: -2 },
      { RM: 0 },
      { RM: 4 },
    ]);
  });

  it("すべて同じ値の列は 0 にする", () => {
    const rows = [{ CHAS: 1 }, { CHAS: 1 }, { CHAS: 1 }];

    const standardized = Standardizer.fit(rows, ["CHAS"]).transform(rows);

    expect(standardized).toEqual([{ CHAS: 0 }, { CHAS: 0 }, { CHAS: 0 }]);
  });
});

describe("polynomialFeatures", () => {
  it("1 列なら元の列と 2 乗の列を返す", () => {
    const rows = [{ RM: 2 }, { RM: 3 }];

    expect(polynomialFeatures(rows, ["RM"])).toEqual([
      { RM: 2, "RM^2": 4 },
      { RM: 3, "RM^2": 9 },
    ]);
  });

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
});

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

  it("第 1 四分位数から IQR の 1.5 倍より小さい値も外れ値とする", () => {
    expect(iqrOutliers([-100, 1, 2, 3, 4])).toEqual([
      true,
      false,
      false,
      false,
      false,
    ]);
  });
});

describe("quantile", () => {
  it("四分位数の位置が値の間にあれば前後の値から線形補間する", () => {
    expect(quantile([4, 1, 3, 2], 0.25)).toBeCloseTo(1.75, 12);
  });

  it("数値として小さい順に並べてから位置を求める", () => {
    expect(quantile([10, 9, 1], 0.5)).toBe(9);
  });
});

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
});

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
