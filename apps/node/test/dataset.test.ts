import { describe, expect, it } from "vitest";
import { dataDir } from "../src/dataset.ts";

describe("dataDir", () => {
  it("環境変数 ML_DATA_DIR が指定されていればそのディレクトリを返す", () => {
    const env: Record<string, string> = { ML_DATA_DIR: "/tmp/ml-data" };

    expect(dataDir((name) => env[name])).toBe("/tmp/ml-data");
  });

  it("環境変数が無ければ apps の data ディレクトリを返す", () => {
    expect(dataDir(() => undefined)).toBe("../data/sukkiri-ml");
  });
});
