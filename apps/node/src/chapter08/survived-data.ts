import { readFileSync } from "node:fs";
import { parse } from "csv-parse/sync";

export const FEATURES = [
  "Pclass",
  "Sex",
  "Age",
  "SibSp",
  "Parch",
  "Fare",
  "Embarked",
] as const;
export const TARGET = "Survived";

/** 乗客 1 人分の特徴量 */
export interface Passenger {
  Pclass: number;
  Sex: string;
  Age: number | null;
  SibSp: number;
  Parch: number;
  Fare: number;
  Embarked: string | null;
}

export interface SurvivedRow extends Passenger {
  PassengerId: number;
  Survived: number;
  Ticket: string;
  Cabin: string | null;
}

const STRING_COLUMNS: readonly string[] = [
  "Sex",
  "Ticket",
  "Cabin",
  "Embarked",
];

export function loadSurvived(csvFile: string): SurvivedRow[] {
  return parse<SurvivedRow>(readFileSync(csvFile), {
    bom: true,
    columns: true,
    cast: (value, context) => {
      if (context.header) {
        return value;
      }
      if (value === "") {
        return null;
      }
      return STRING_COLUMNS.includes(String(context.column))
        ? value
        : Number(value);
    },
  });
}

export function splitFeaturesAndTarget(rows: readonly SurvivedRow[]): {
  x: Passenger[];
  t: number[];
} {
  return {
    x: rows.map(
      ({
        PassengerId: _id,
        Survived: _survived,
        Ticket: _ticket,
        Cabin: _cabin,
        ...features
      }) => features,
    ),
    t: rows.map((row) => row[TARGET]),
  };
}
