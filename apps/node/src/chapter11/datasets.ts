import { readFileSync } from "node:fs";
import { parse } from "csv-parse/sync";
import { columnMeans, fillMissing } from "../chapter02/iris-preprocessing.ts";

export interface SurvivedRow {
  Survived: string;
  Pclass: number;
  Sex: string;
  Age: number | null;
}

export const SURVIVED_FEATURES = ["Pclass", "Age", "male"] as const;
export type SurvivedFeature = (typeof SURVIVED_FEATURES)[number];

export function prepareSurvived(rows: readonly SurvivedRow[]): {
  x: Record<SurvivedFeature, number>[];
  t: string[];
} {
  const features = rows.map((row) => ({
    Pclass: row.Pclass,
    Age: row.Age,
    male: row.Sex === "male" ? 1 : 0,
  }));
  return {
    x: fillMissing(features, columnMeans(features, SURVIVED_FEATURES)),
    t: rows.map((row) => row.Survived),
  };
}

export const CINEMA_FEATURES = ["SNS1", "SNS2", "actor", "original"] as const;
export type CinemaFeature = (typeof CINEMA_FEATURES)[number];

export type CinemaRow = Record<CinemaFeature, number | null> & {
  cinema_id: number;
  sales: number;
};

export function prepareCinema(rows: readonly CinemaRow[]): {
  x: Record<CinemaFeature, number>[];
  t: number[];
} {
  const features = rows.map(
    ({ cinema_id: _id, sales: _sales, ...features }) => features,
  );
  return {
    x: fillMissing(features, columnMeans(features, CINEMA_FEATURES)),
    t: rows.map((row) => row.sales),
  };
}

/** 指定した列だけを数値（空欄は null）にして CSV を読み込む */
function loadCsv<T>(csvFile: string, numericColumns: readonly string[]): T[] {
  return parse<T>(readFileSync(csvFile), {
    bom: true,
    columns: true,
    cast: (value, context) => {
      if (context.header || !numericColumns.includes(String(context.column))) {
        return value;
      }
      return value === "" ? null : Number(value);
    },
  });
}

export function loadSurvived(csvFile: string): SurvivedRow[] {
  return loadCsv(csvFile, ["Pclass", "Age"]);
}

export function loadCinema(csvFile: string): CinemaRow[] {
  return loadCsv(csvFile, ["cinema_id", ...CINEMA_FEATURES, "sales"]);
}
