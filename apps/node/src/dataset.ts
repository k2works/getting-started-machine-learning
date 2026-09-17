/** 学習データのディレクトリ。環境変数 ML_DATA_DIR が無ければ apps/data/sukkiri-ml を使う。 */
export function dataDir(
  getenv: (name: string) => string | undefined = (name) => process.env[name],
): string {
  return getenv("ML_DATA_DIR") ?? "../data/sukkiri-ml";
}
