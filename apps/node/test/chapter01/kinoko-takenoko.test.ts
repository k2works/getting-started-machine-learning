import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  accuracy,
  loadPeople,
  predictByRule,
  splitFeaturesAndLabels,
} from "../../src/chapter01/kinoko-takenoko.ts";

const HEADER = "\uFEFF身長,体重,年代,派閥\n";

function writeCsv(rows: string): string {
  const csvFile = join(mkdtempSync(join(tmpdir(), "kvst-")), "kvst.csv");
  writeFileSync(csvFile, HEADER + rows);
  return csvFile;
}

describe("loadPeople", () => {
  it("BOM 付き CSV を読み込んで人物の配列を返す", () => {
    const csvFile = writeCsv("165,58,30,きのこ\n");

    const people = loadPeople(csvFile);

    expect(people).toEqual([
      { height: 165, weight: 58, ageGroup: 30, faction: "きのこ" },
    ]);
  });

  it("複数行の CSV を読み込んで行の順に人物の配列を返す", () => {
    const csvFile = writeCsv("161,52,20,きのこ\n183,74,50,たけのこ\n");

    const people = loadPeople(csvFile);

    expect(people).toEqual([
      { height: 161, weight: 52, ageGroup: 20, faction: "きのこ" },
      { height: 183, weight: 74, ageGroup: 50, faction: "たけのこ" },
    ]);
  });
});

describe("splitFeaturesAndLabels", () => {
  it("人物の配列を特徴量と正解ラベルに分ける", () => {
    const people = [
      { height: 161, weight: 52, ageGroup: 20, faction: "きのこ" },
      { height: 183, weight: 74, ageGroup: 50, faction: "たけのこ" },
    ];

    const { features, labels } = splitFeaturesAndLabels(people);

    expect(features).toEqual([
      { height: 161, weight: 52, ageGroup: 20 },
      { height: 183, weight: 74, ageGroup: 50 },
    ]);
    expect(labels).toEqual(["きのこ", "たけのこ"]);
  });
});

describe("predictByRule", () => {
  it("20 代ならきのこ派と判定する", () => {
    const features = { height: 161, weight: 52, ageGroup: 20 };

    expect(predictByRule(features)).toBe("きのこ");
  });

  it("20 代以外ならたけのこ派と判定する", () => {
    const features = { height: 183, weight: 74, ageGroup: 50 };

    expect(predictByRule(features)).toBe("たけのこ");
  });
});

describe("accuracy", () => {
  it("すべての予測が正解なら正解率は 1", () => {
    expect(accuracy(["きのこ", "たけのこ"], ["きのこ", "たけのこ"])).toBe(1);
  });

  it("4 件中 3 件の予測が正解なら正解率は 0.75", () => {
    const predictions = ["きのこ", "きのこ", "たけのこ", "たけのこ"];
    const labels = ["きのこ", "たけのこ", "たけのこ", "たけのこ"];

    expect(accuracy(predictions, labels)).toBe(0.75);
  });

  it("予測と正解ラベルの件数が違えばエラーになる", () => {
    expect(() => accuracy(["きのこ"], ["きのこ", "たけのこ"])).toThrow(
      "予測と正解ラベルの件数が違います",
    );
  });
});
