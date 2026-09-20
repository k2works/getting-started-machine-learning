namespace MachineLearning.Chapter15;

using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;

/// <summary>プレゼンテーション層。Minimal API のエンドポイントと、HTTP の状態コード。</summary>
public static class PredictionApi
{
    /// <summary>ヘルスチェックの応答。</summary>
    public sealed record HealthResponse(string Status, Health Models);

    /// <summary>置き場を使う API を組み立てる。起動はしない。</summary>
    public static WebApplication CreateWebApplication(WebApplicationBuilder builder, IModelStore store)
    {
        ArgumentNullException.ThrowIfNull(builder);
        var service = new PredictionService(store);
        var app = builder.Build();

        app.MapPost("/cinema/sales", async (HttpRequest request) =>
            await PredictAsync(request, RequestValidation.ParseMovie, service.PredictSales).ConfigureAwait(false));
        app.MapPost("/survived", async (HttpRequest request) =>
            await PredictAsync(request, RequestValidation.ParsePassenger, service.PredictSurvival).ConfigureAwait(false));
        app.MapGet("/health", () =>
        {
            var models = service.Health();
            var status = models.Cinema && models.Survived ? "ok" : "degraded";
            return Results.Json(new HealthResponse(status, models));
        });
        app.MapFallback(() => Results.Json(new { detail = "見つかりません" }, statusCode: StatusCodes.Status404NotFound));

        return app;
    }

    /// <summary>本文を検証し、正しければ予測する。不正なら 422 と理由の一覧、モデルが無ければ 503 を返す。</summary>
    private static async Task<IResult> PredictAsync<TInput, TOutput>(
        HttpRequest request,
        Func<string, RequestValidation.Validation<TInput>> parse,
        Func<TInput, PredictionResult<TOutput>> predict)
    {
        using var reader = new StreamReader(request.Body);
        var body = await reader.ReadToEndAsync().ConfigureAwait(false);
        var validation = parse(body);
        if (!validation.IsValid)
        {
            return Results.Json(new { detail = validation.Errors }, statusCode: StatusCodes.Status422UnprocessableEntity);
        }

        return predict(validation.Value!).Match(
            prediction => Results.Json(prediction),
            error => Results.Json(new { detail = error.Describe() }, statusCode: StatusCodes.Status503ServiceUnavailable));
    }
}
