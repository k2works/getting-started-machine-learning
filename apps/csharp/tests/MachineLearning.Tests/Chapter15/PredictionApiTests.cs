namespace MachineLearning.Tests.Chapter15;

using System.Net;
using System.Text;
using System.Text.Json;
using MachineLearning.Chapter15;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.TestHost;

public class PredictionApiTests
{
    private const string MovieJson = """{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}""";
    private const string PassengerJson =
        """{"pclass": 1, "sex": "female", "age": null, "sib_sp": 0, "parch": 0, "fare": 50.0, "embarked": "C"}""";

    private static readonly StubStore Stub = StubStore.With(_ => 1200.0, _ => true);

    [Fact(DisplayName = "映画の特徴量を送ると予測した興行収入を返す")]
    public async Task PredictsSales()
    {
        var (status, body) = await PostAsync(Stub, "/cinema/sales", MovieJson);

        Assert.Equal(HttpStatusCode.OK, status);
        Assert.Equal("""{"sales":1200}""", body);
    }

    [Fact(DisplayName = "乗客の特徴量を送ると生存の予測を返す")]
    public async Task PredictsSurvival()
    {
        var (status, body) = await PostAsync(Stub, "/survived", PassengerJson);

        Assert.Equal(HttpStatusCode.OK, status);
        Assert.Equal("""{"survived":true}""", body);
    }

    [Theory(DisplayName = "特徴量が不正なら 422 と理由の一覧を返す")]
    [InlineData("""{"sns1": -1, "sns2": 500, "actor": 3000, "original": 1}""")]
    [InlineData("""{"sns1": "多い", "sns2": 500, "actor": 3000, "original": 1}""")]
    [InlineData("{")]
    public async Task RejectsInvalid(string body)
    {
        var (status, response) = await PostAsync(Stub, "/cinema/sales", body);

        Assert.Equal(HttpStatusCode.UnprocessableContent, status);
        Assert.Contains("detail", response, StringComparison.Ordinal);
    }

    [Fact(DisplayName = "モデルが無ければ 503 と、パスを含まない理由を返す")]
    public async Task MissingModel()
    {
        var (status, body) = await PostAsync(StubStore.Empty(), "/cinema/sales", MovieJson);

        Assert.Equal(HttpStatusCode.ServiceUnavailable, status);
        Assert.Equal("""{"detail":"学習済みモデル cinema が見つかりません"}""", body);
    }

    [Fact(DisplayName = "すべてのモデルを読み込めれば ok を返す")]
    public async Task HealthOk()
    {
        var (status, body) = await GetAsync(Stub, "/health");

        Assert.Equal(HttpStatusCode.OK, status);
        using var json = JsonDocument.Parse(body);
        Assert.Equal("ok", json.RootElement.GetProperty("status").GetString());
        Assert.True(json.RootElement.GetProperty("models").GetProperty("cinema").GetBoolean());
    }

    [Fact(DisplayName = "読み込めないモデルがあれば degraded を返す")]
    public async Task HealthDegraded()
    {
        var (_, body) = await GetAsync(StubStore.Empty(), "/health");

        using var json = JsonDocument.Parse(body);
        Assert.Equal("degraded", json.RootElement.GetProperty("status").GetString());
        Assert.False(json.RootElement.GetProperty("models").GetProperty("survived").GetBoolean());
    }

    [Fact(DisplayName = "知らない経路は 404 を返す")]
    public async Task NotFound()
    {
        var (status, _) = await GetAsync(Stub, "/unknown");

        Assert.Equal(HttpStatusCode.NotFound, status);
    }

    /// <summary>置き場を使う API を、ネットワークを使わないテスト用のサーバーで起動する。</summary>
    private static async Task<HttpClient> ClientAsync(IModelStore store)
    {
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.UseTestServer();
        var app = PredictionApi.CreateWebApplication(builder, store);
        await app.StartAsync();
        return app.GetTestClient();
    }

    private static async Task<(HttpStatusCode Status, string Body)> PostAsync(IModelStore store, string path, string body)
    {
        var client = await ClientAsync(store);
        using var content = new StringContent(body, Encoding.UTF8, "application/json");
        using var response = await client.PostAsync(new Uri(path, UriKind.Relative), content);
        return (response.StatusCode, await response.Content.ReadAsStringAsync());
    }

    private static async Task<(HttpStatusCode Status, string Body)> GetAsync(IModelStore store, string path)
    {
        var client = await ClientAsync(store);
        using var response = await client.GetAsync(new Uri(path, UriKind.Relative));
        return (response.StatusCode, await response.Content.ReadAsStringAsync());
    }
}
