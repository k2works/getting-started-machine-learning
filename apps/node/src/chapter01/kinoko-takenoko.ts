import { readFileSync } from "node:fs";

const BOM = "\uFEFF";

export interface Person {
  height: number;
  weight: number;
  ageGroup: number;
  faction: string;
}

export function loadPeople(csvFile: string): Person[] {
  const text = readFileSync(csvFile, "utf-8");
  const [headerLine = "", ...lines] = (
    text.startsWith(BOM) ? text.slice(BOM.length) : text
  ).split("\n");
  const header = headerLine.split(",");
  const column = (values: string[], name: string): string => {
    const value = values[header.indexOf(name)];
    if (value === undefined) {
      throw new Error(`列 ${name} が見つかりません`);
    }
    return value;
  };
  return lines
    .filter((line) => line.trim() !== "")
    .map((line) => {
      const values = line.split(",");
      return {
        height: Number(column(values, "身長")),
        weight: Number(column(values, "体重")),
        ageGroup: Number(column(values, "年代")),
        faction: column(values, "派閥"),
      };
    });
}

export type Features = Omit<Person, "faction">;

export function splitFeaturesAndLabels(people: Person[]): {
  features: Features[];
  labels: string[];
} {
  return {
    features: people.map(({ height, weight, ageGroup }) => ({
      height,
      weight,
      ageGroup,
    })),
    labels: people.map((person) => person.faction),
  };
}

/** 「20 代ならきのこ派」というルールの年代 */
const KINOKO_AGE_GROUP = 20;

export function predictByRule(features: Features): string {
  return features.ageGroup === KINOKO_AGE_GROUP ? "きのこ" : "たけのこ";
}

export function accuracy(predictions: string[], labels: string[]): number {
  if (predictions.length !== labels.length) {
    throw new Error("予測と正解ラベルの件数が違います");
  }
  const correct = predictions.filter(
    (prediction, i) => prediction === labels[i],
  ).length;
  return correct / labels.length;
}
