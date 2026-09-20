namespace MachineLearning.Chapter08;

using System.Text.Json;

/// <summary>学習済みのパイプライン（前処理で求めた値とモデル）を JSON で保存し、読み込む。</summary>
public static class ModelFiles
{
    /// <summary>
    /// 読み込むときの設定。コンストラクターの引数が JSON に無ければ、null で埋めずに JsonException にする。
    /// </summary>
    private static readonly JsonSerializerOptions Options = new() { RespectRequiredConstructorParameters = true };

    /// <summary>学習済みのパイプラインを JSON で保存する。保存先のディレクトリが無ければ作る。</summary>
    public static void Save(FittedPipeline pipeline, string modelFile)
    {
        var directory = Path.GetDirectoryName(Path.GetFullPath(modelFile));
        if (!string.IsNullOrEmpty(directory))
        {
            Directory.CreateDirectory(directory);
        }

        File.WriteAllText(modelFile, JsonSerializer.Serialize(pipeline, Options));
    }

    /// <summary>保存したパイプラインを読み込む。JSON の形が違えば <see cref="JsonException"/> で失敗する。</summary>
    public static FittedPipeline Load(string modelFile) =>
        JsonSerializer.Deserialize<FittedPipeline>(File.ReadAllText(modelFile), Options)
        ?? throw new JsonException("パイプラインを読み込めません");
}
