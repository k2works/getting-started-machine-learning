import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { DecisionTree, gini } from "../../src/chapter03/decision-tree.ts";
import {
  balancedWeights,
  fitDecisionTree,
  predictDecisionTree,
  weightedGini,
} from "../../src/chapter08/decision-tree-classifier.ts";
import { evaluate } from "../../src/chapter08/evaluation.ts";
import {
  fitPipeline,
  loadModel,
  predict,
  predictPassenger,
  saveModel,
} from "../../src/chapter08/pipeline.ts";
import {
  FEATURES,
  loadSurvived,
  type Passenger,
  splitFeaturesAndTarget,
} from "../../src/chapter08/survived-data.ts";
import {
  encodeDummies,
  fitDummyEncoder,
  fitGroupMedianImputer,
  fitMostFrequentImputer,
  imputeGroupMedian,
  imputeMostFrequent,
} from "../../src/chapter08/transformers.ts";

const HEADER =
  "\uFEFFPassengerId,Survived,Pclass,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked\n";

function writeCsv(rows: string): string {
  const directory = mkdtempSync(join(tmpdir(), "survived-"));
  const csvFile = join(directory, "survived.csv");
  writeFileSync(csvFile, HEADER + rows);
  return csvFile;
}

describe("loadSurvived", () => {
  it("BOM 付き CSV を読み込み、数値の列を数値に、空欄を null にする", () => {
    const rows = loadSurvived(writeCsv("1,0,3,male,,0,0,X-1,8.5,,S\n"));

    expect(rows[0]?.Pclass).toBe(3);
    expect(rows[0]?.Fare).toBe(8.5);
    expect(rows[0]?.Age).toBeNull();
    expect(rows[0]?.Cabin).toBeNull();
  });

  it("文字列の列は文字列のまま読み込む", () => {
    const rows = loadSurvived(writeCsv("1,0,3,male,,0,0,X-1,8.5,,S\n"));

    expect(rows[0]?.Sex).toBe("male");
    expect(rows[0]?.Embarked).toBe("S");
  });
});

describe("Map のキー（学習用テスト）", () => {
  it("配列のキーは要素の値ではなく、同じ配列かどうかで比べる", () => {
    const medians = new Map([[[1, "female"], 45]]);

    expect(medians.get([1, "female"])).toBeUndefined();
  });

  it("JSON の文字列にしたキーなら要素の値で引ける", () => {
    const medians = new Map([[JSON.stringify([1, "female"]), 45]]);

    expect(medians.get(JSON.stringify([1, "female"]))).toBe(45);
  });

  it("Map を JSON の文字列にすると中身が失われる", () => {
    const medians = new Map([['[1,"female"]', 45]]);

    expect(JSON.stringify({ medians })).toBe('{"medians":{}}');
  });
});

describe("GroupMedianImputer", () => {
  it("同じグループの中央値で欠損値を補完する", () => {
    const x = [
      { Pclass: 1, Sex: "female", Age: 20 },
      { Pclass: 1, Sex: "female", Age: 30 },
      { Pclass: 1, Sex: "female", Age: 70 },
      { Pclass: 1, Sex: "female", Age: null },
    ];

    const imputer = fitGroupMedianImputer(x, "Age", ["Pclass", "Sex"]);

    const filled = imputeGroupMedian(x, imputer);
    expect(filled.map((row) => row.Age)).toEqual([20, 30, 70, 30]);
  });

  it("グループごとに異なる中央値で補完する", () => {
    const x = [
      { Pclass: 1, Sex: "female", Age: 40 },
      { Pclass: 1, Sex: "female", Age: 50 },
      { Pclass: 1, Sex: "female", Age: null },
      { Pclass: 3, Sex: "male", Age: 10 },
      { Pclass: 3, Sex: "male", Age: 20 },
      { Pclass: 3, Sex: "male", Age: null },
    ];

    const imputer = fitGroupMedianImputer(x, "Age", ["Pclass", "Sex"]);

    const filled = imputeGroupMedian(x, imputer);
    expect(filled.map((row) => row.Age)).toEqual([40, 50, 45, 10, 20, 15]);
  });

  it("訓練データで求めた中央値を別のデータの補完に使う", () => {
    const train = [
      { Pclass: 2, Sex: "male", Age: 30 },
      { Pclass: 2, Sex: "male", Age: 34 },
    ];
    const other = [{ Pclass: 2, Sex: "male", Age: null }];

    const imputer = fitGroupMedianImputer(train, "Age", ["Pclass", "Sex"]);

    expect(imputeGroupMedian(other, imputer)[0]?.Age).toBe(32);
  });

  it("訓練データに無いグループは全体の中央値で補完する", () => {
    const train = [
      { Pclass: 1, Sex: "female", Age: 30 },
      { Pclass: 1, Sex: "female", Age: 40 },
      { Pclass: 3, Sex: "male", Age: 20 },
    ];
    const other = [{ Pclass: 2, Sex: "female", Age: null }];

    const imputer = fitGroupMedianImputer(train, "Age", ["Pclass", "Sex"]);

    expect(imputeGroupMedian(other, imputer)[0]?.Age).toBe(30);
  });

  it("年齢がすべて欠けたグループは全体の中央値で補完する", () => {
    const x = [
      { Pclass: 1, Sex: "female", Age: 30 },
      { Pclass: 1, Sex: "female", Age: 40 },
      { Pclass: 2, Sex: "female", Age: null },
    ];

    const imputer = fitGroupMedianImputer(x, "Age", ["Pclass", "Sex"]);

    expect(imputeGroupMedian(x, imputer)[2]?.Age).toBe(35);
  });

  it("元のデータは変更しない", () => {
    const x = [
      { Pclass: 1, Sex: "male", Age: 30 },
      { Pclass: 1, Sex: "male", Age: null },
    ];

    imputeGroupMedian(x, fitGroupMedianImputer(x, "Age", ["Pclass", "Sex"]));

    expect(x[1]?.Age).toBeNull();
  });
});

describe("MostFrequentImputer", () => {
  it("訓練データで最も多い値で欠損値を補完する", () => {
    const train = [
      { Embarked: "S" },
      { Embarked: "C" },
      { Embarked: "S" },
      { Embarked: null },
    ];
    const other = [{ Embarked: null }, { Embarked: "Q" }];

    const imputer = fitMostFrequentImputer(train, "Embarked");

    const filled = imputeMostFrequent(other, imputer);
    expect(filled.map((row) => row.Embarked)).toEqual(["S", "Q"]);
  });
});

describe("DummyEncoder", () => {
  it("2 値のカテゴリを、最初のカテゴリを除いた 0 と 1 の列にする", () => {
    const x = [
      { Pclass: 1, Sex: "female" },
      { Pclass: 3, Sex: "male" },
      { Pclass: 2, Sex: "male" },
    ];

    const encoder = fitDummyEncoder(x, ["Sex"]);

    expect(encodeDummies(x, encoder)).toEqual([
      { Pclass: 1, Sex_male: 0 },
      { Pclass: 3, Sex_male: 1 },
      { Pclass: 2, Sex_male: 1 },
    ]);
  });

  it("別のデータにも訓練データと同じ列を作る", () => {
    const train = [{ Embarked: "C" }, { Embarked: "Q" }, { Embarked: "S" }];
    const other = [{ Embarked: "S" }, { Embarked: "S" }];

    const encoder = fitDummyEncoder(train, ["Embarked"]);

    expect(encodeDummies(other, encoder)).toEqual([
      { Embarked_Q: 0, Embarked_S: 1 },
      { Embarked_Q: 0, Embarked_S: 1 },
    ]);
  });
});

describe("weightedGini", () => {
  it("重みがすべて 1 なら第 3 章のジニ不純度と同じ値になる", () => {
    const labels = [0, 1, 1];

    expect(weightedGini(labels, [1, 1, 1])).toBe(gini(labels.map(String)));
  });

  it("重みの大きいラベルほど多いものとして不純度を計算する", () => {
    expect(weightedGini([0, 1], [1, 3])).toBeCloseTo(0.375, 12);
  });
});

describe("balancedWeights", () => {
  it("少ないクラスほど 1 件の重みを大きくし、クラスごとの重みの合計をそろえる", () => {
    const weights = balancedWeights([0, 0, 0, 1]);

    expect(weights).toEqual([4 / 6, 4 / 6, 4 / 6, 2]);
    expect(weights[3]).toBeCloseTo(3 * (4 / 6), 12);
  });
});

describe("fitDecisionTree", () => {
  const x = [
    { Fare: 8, Age: 30 },
    { Fare: 9, Age: 22 },
    { Fare: 13, Age: 18 },
    { Fare: 20, Age: 45 },
    { Fare: 60, Age: 25 },
    { Fare: 80, Age: 33 },
  ];
  const t = [0, 0, 1, 0, 1, 1];

  it.each([1, 2, undefined])(
    "重み付けなしなら第 3 章の決定木と同じ予測をする（深さ %s）",
    (maxDepth) => {
      const expected = new DecisionTree({ maxDepth })
        .fit(x, t.map(String))
        .predict(x);

      const tree = fitDecisionTree(x, t, { maxDepth, classWeight: "none" });

      expect(predictDecisionTree(tree, x).map(String)).toEqual(expected);
    },
  );

  it("balanced にすると少ないクラスが混ざった葉でも少ないクラスを予測する", () => {
    const fares = [1, 1, 1, 1, 2, 2, 2].map((Fare) => ({ Fare }));
    const survived = [0, 0, 0, 0, 0, 0, 1];
    const newX = [{ Fare: 1 }, { Fare: 2 }];

    const none = fitDecisionTree(fares, survived, {
      maxDepth: 1,
      classWeight: "none",
    });
    const balanced = fitDecisionTree(fares, survived, {
      maxDepth: 1,
      classWeight: "balanced",
    });

    expect(predictDecisionTree(none, newX)).toEqual([0, 0]);
    expect(predictDecisionTree(balanced, newX)).toEqual([0, 1]);
  });
});

describe("splitFeaturesAndTarget", () => {
  it("特徴量の列と Survived 列に分ける", () => {
    const rows = [
      {
        PassengerId: 1,
        Survived: 1,
        Pclass: 2,
        Sex: "female",
        Age: 28,
        SibSp: 0,
        Parch: 1,
        Ticket: "X-2",
        Fare: 15,
        Cabin: null,
        Embarked: "C",
      },
    ];

    const { x, t } = splitFeaturesAndTarget(rows);

    expect(Object.keys(x[0] ?? {})).toEqual([...FEATURES]);
    expect(t).toEqual([1]);
  });
});

type PassengerValues = [
  Pclass: number,
  Sex: string,
  Age: number | null,
  SibSp: number,
  Parch: number,
  Fare: number,
  Embarked: string | null,
];

function passengers(...rows: PassengerValues[]): Passenger[] {
  return rows.map(([Pclass, Sex, Age, SibSp, Parch, Fare, Embarked]) => ({
    Pclass,
    Sex,
    Age,
    SibSp,
    Parch,
    Fare,
    Embarked,
  }));
}

function trainingData(): { x: Passenger[]; t: number[] } {
  const x = passengers(
    [1, "female", 30, 0, 0, 80, "C"],
    [2, "female", null, 1, 0, 20, "S"],
    [3, "female", 22, 0, 1, 9, null],
    [3, "female", 18, 0, 0, 8, "Q"],
    [1, "male", 45, 0, 0, 60, "S"],
    [2, "male", null, 0, 0, 13, "S"],
    [3, "male", 25, 1, 0, 7, "S"],
    [3, "male", 33, 0, 0, 8, null],
  );
  return { x, t: [1, 1, 1, 1, 0, 0, 0, 0] };
}

function newPassengers(): Passenger[] {
  return passengers(
    [2, "female", null, 0, 0, 12, null],
    [1, "male", null, 1, 1, 70, "C"],
  );
}

describe("fitPipeline", () => {
  it("欠損値を含むデータで学習して予測できる", () => {
    const { x, t } = trainingData();

    const pipeline = fitPipeline(x, t, { maxDepth: 3, classWeight: "none" });

    expect(predict(pipeline, newPassengers())).toEqual([1, 0]);
  });
});

describe("saveModel と loadModel", () => {
  it("保存したパイプラインを読み込むと同じ予測をする", () => {
    const { x, t } = trainingData();
    const pipeline = fitPipeline(x, t, { maxDepth: 3, classWeight: "none" });
    const directory = mkdtempSync(join(tmpdir(), "model-"));
    const modelFile = join(directory, "model", "survived.json");

    saveModel(pipeline, modelFile);
    const loaded = loadModel(modelFile);

    expect(predict(loaded, newPassengers())).toEqual([1, 0]);
  });

  it("パイプラインの形をしていないファイルは読み込まない", () => {
    const directory = mkdtempSync(join(tmpdir(), "model-"));
    const modelFile = join(directory, "unknown.json");
    writeFileSync(modelFile, JSON.stringify({ tree: { kind: "leaf" } }));

    expect(() => loadModel(modelFile)).toThrow();
  });
});

describe("predictPassenger", () => {
  it("乗客 1 人分のデータから、生存（1）か死亡（0）かを予測する", () => {
    const { x, t } = trainingData();
    const pipeline = fitPipeline(x, t, { maxDepth: 3, classWeight: "none" });
    const [female, male] = newPassengers() as [Passenger, Passenger];

    expect(predictPassenger(pipeline, female)).toBe(1);
    expect(predictPassenger(pipeline, male)).toBe(0);
  });
});

describe("evaluate", () => {
  it("正解率と見つけた生存者の数を求める", () => {
    const { x, t } = trainingData();
    const split = {
      xTrain: x,
      xTest: newPassengers(),
      tTrain: t,
      tTest: [1, 1],
    };
    const pipeline = fitPipeline(x, t, { maxDepth: 3, classWeight: "none" });

    expect(evaluate(pipeline, split)).toEqual({
      trainAccuracy: 1,
      testAccuracy: 0.5,
      foundSurvivors: 1,
      survivors: 2,
    });
  });
});
