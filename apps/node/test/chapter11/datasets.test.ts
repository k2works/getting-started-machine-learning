import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterAll, describe, expect, it } from "vitest";
import {
  loadCinema,
  loadSurvived,
  prepareCinema,
  prepareSurvived,
} from "../../src/chapter11/datasets.ts";

describe("CSV の読み込み", () => {
  const dir = mkdtempSync(join(tmpdir(), "chapter11-"));

  afterAll(() => {
    rmSync(dir, { recursive: true });
  });

  it("Survived の数値の列を数値にし空欄を null にする", () => {
    const csvFile = join(dir, "survived.csv");
    writeFileSync(
      csvFile,
      "\uFEFFPassengerId,Survived,Pclass,Sex,Age,Fare\n1,0,3,male,30,8\n2,1,1,female,,60\n",
    );

    expect(loadSurvived(csvFile)).toEqual([
      {
        PassengerId: "1",
        Survived: "0",
        Pclass: 3,
        Sex: "male",
        Age: 30,
        Fare: "8",
      },
      {
        PassengerId: "2",
        Survived: "1",
        Pclass: 1,
        Sex: "female",
        Age: null,
        Fare: "60",
      },
    ]);
  });

  it("cinema のすべての列を数値にし空欄を null にする", () => {
    const csvFile = join(dir, "cinema.csv");
    writeFileSync(
      csvFile,
      "cinema_id,SNS1,SNS2,actor,original,sales\n101,100,500,,0,9000\n",
    );

    expect(loadCinema(csvFile)).toEqual([
      {
        cinema_id: 101,
        SNS1: 100,
        SNS2: 500,
        actor: null,
        original: 0,
        sales: 9000,
      },
    ]);
  });
});

describe("prepareSurvived", () => {
  it("客室クラスと年齢と男性かどうかを特徴量にし生存を正解ラベルにする", () => {
    const rows = [
      { Survived: "0", Pclass: 3, Sex: "male", Age: 30 },
      { Survived: "1", Pclass: 1, Sex: "female", Age: 40 },
    ];

    const { x, t } = prepareSurvived(rows);

    expect(x).toEqual([
      { Pclass: 3, Age: 30, male: 1 },
      { Pclass: 1, Age: 40, male: 0 },
    ]);
    expect(t).toEqual(["0", "1"]);
  });

  it("年齢の欠損値を年齢の平均値で補完する", () => {
    const rows = [
      { Survived: "0", Pclass: 3, Sex: "male", Age: 20 },
      { Survived: "1", Pclass: 1, Sex: "female", Age: null },
      { Survived: "1", Pclass: 2, Sex: "female", Age: 40 },
    ];

    const { x } = prepareSurvived(rows);

    expect(x.map((row) => row.Age)).toEqual([20, 30, 40]);
  });
});

describe("prepareCinema", () => {
  it("興行収入を正解ラベルにし特徴量の欠損値を平均値で補完する", () => {
    const rows = [
      {
        cinema_id: 101,
        SNS1: 100,
        SNS2: 500,
        actor: null,
        original: 0,
        sales: 9000,
      },
      {
        cinema_id: 102,
        SNS1: null,
        SNS2: 600,
        actor: 20,
        original: 1,
        sales: 9500,
      },
      {
        cinema_id: 103,
        SNS1: 300,
        SNS2: 700,
        actor: 40,
        original: 0,
        sales: 10000,
      },
    ];

    const { x, t } = prepareCinema(rows);

    expect(x).toEqual([
      { SNS1: 100, SNS2: 500, actor: 30, original: 0 },
      { SNS1: 200, SNS2: 600, actor: 20, original: 1 },
      { SNS1: 300, SNS2: 700, actor: 40, original: 0 },
    ]);
    expect(t).toEqual([9000, 9500, 10000]);
  });
});
