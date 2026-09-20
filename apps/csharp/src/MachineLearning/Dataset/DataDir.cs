namespace MachineLearning.Dataset;

using System.Runtime.CompilerServices;

/// <summary>学習データのディレクトリを求める。</summary>
public static class DataDir
{
    /// <summary>環境変数 ML_DATA_DIR が無ければ apps/data/sukkiri-ml を使う。</summary>
    /// <param name="getenv">環境変数を読む関数。テストでは差し替える。</param>
    public static string From(Func<string, string?> getenv)
    {
        ArgumentNullException.ThrowIfNull(getenv);
        return getenv("ML_DATA_DIR") ?? Path.Combine(SourceDirectory(), "..", "..", "..", "..", "data", "sukkiri-ml");
    }

    /// <summary>実行中のプロセスの環境変数から学習データのディレクトリを求める。</summary>
    public static string Current() => From(Environment.GetEnvironmentVariable);

    /// <summary>このファイルが置かれたディレクトリ。テストは bin/Debug/net10.0 で動くので、既定の場所はここから求める。</summary>
    private static string SourceDirectory([CallerFilePath] string path = "") => Path.GetDirectoryName(path)!;
}
