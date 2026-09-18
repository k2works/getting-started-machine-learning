import { mkdtempSync } from "node:fs";
import type { AddressInfo } from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { startServer } from "../../src/chapter15/main.ts";

describe("startServer", () => {
  it("起動したサーバーに HTTP でヘルスチェックを送れる", async () => {
    // ポート 0 を渡すと、空いているポートを OS が選ぶ
    const server = startServer(mkdtempSync(join(tmpdir(), "model-")), 0);
    try {
      if (!server.listening) {
        await new Promise((resolve) => server.once("listening", resolve));
      }
      const { port } = server.address() as AddressInfo;

      const response = await fetch(`http://127.0.0.1:${port}/health`);

      expect(await response.json()).toEqual({
        status: "degraded",
        models: { cinema: false, survived: false },
      });
    } finally {
      server.close();
    }
  });
});
