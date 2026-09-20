namespace MachineLearning.Chapter15;

using MachineLearning.Dataset;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;

/// <summary>モデルを学習して保存し、予測 API を起動する。</summary>
public static class Program
{
    /// <summary>学習済みモデルの保存先（apps/csharp/model/ は .gitignore の対象）</summary>
    public const string ModelDirectory = "model";

    /// <summary>API が待ち受けるポート</summary>
    public const int Port = 8015;

    public static void Run(TextWriter output) => TrainAndReport(output, ModelDirectory);

    /// <summary>保存先を指定して学習し、結果を表示する。テストから一時ディレクトリを渡すために使う。</summary>
    public static void TrainAndReport(TextWriter output, string modelDirectory)
    {
        ArgumentNullException.ThrowIfNull(output);
        Training.TrainAndSaveModels(DataDir.Current(), new FileModelStore(modelDirectory));
        output.WriteLine(
            $"学習済みモデルを保存しました: {FileModelStore.SalesModelName}.json, {FileModelStore.SurvivalModelName}.json");
        output.WriteLine($"API を起動します: http://127.0.0.1:{Port}");
    }

    /// <summary>学習してから API を起動する（手元で試すとき用）。</summary>
    public static void RunServer(string modelDirectory, int port)
    {
        TrainAndReport(Console.Out, modelDirectory);
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.UseUrls($"http://127.0.0.1:{port}");
        PredictionApi.CreateWebApplication(builder, new FileModelStore(modelDirectory)).Run();
    }
}
