import { readFileSync } from "node:fs";
import { type CastingFunction, parse } from "csv-parse/sync";
import { mean } from "./statistics.ts";

export interface BikeRow {
  dteday: string;
  weather_id: number;
  cnt: number;
}

export interface WeatherRow {
  weather_id: number;
  weather: string;
}

// 列名と textColumn の列は文字列のまま、それ以外の列は数値にする
function numbersExcept(textColumn: string): CastingFunction {
  return (value, context) =>
    context.header || context.column === textColumn ? value : Number(value);
}

export function loadBike(tsvFile: string): BikeRow[] {
  return parse<BikeRow>(readFileSync(tsvFile), {
    columns: true,
    delimiter: "\t",
    cast: numbersExcept("dteday"),
  });
}

export function loadWeather(csvFile: string): WeatherRow[] {
  const text = new TextDecoder("shift_jis").decode(readFileSync(csvFile));
  return parse<WeatherRow>(text, {
    columns: true,
    cast: numbersExcept("weather"),
  });
}

export function joinWeather<B extends { weather_id: number }>(
  bike: readonly B[],
  weather: readonly WeatherRow[],
): (B & { weather: string })[] {
  const names = new Map(weather.map((row) => [row.weather_id, row.weather]));
  return bike.flatMap((row) => {
    const name = names.get(row.weather_id);
    return name === undefined ? [] : [{ ...row, weather: name }];
  });
}

export function meanCountByWeather(
  joined: readonly { weather: string; cnt: number }[],
): Map<string, number> {
  const groups = new Map<string, number[]>();
  for (const row of joined) {
    groups.set(row.weather, [...(groups.get(row.weather) ?? []), row.cnt]);
  }
  const means = [...groups].map(([weather, counts]): [string, number] => [
    weather,
    mean(counts),
  ]);
  return new Map(means.toSorted(([, a], [, b]) => b - a));
}
