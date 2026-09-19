package chapter15;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import java.util.List;
import java.util.Map;

/** 予測 API。HTTP の要求を検証してサービスに渡し、ドメインの例外を HTTP のステータスコードに変える。 */
public final class PredictionApi {
  private static final String INVALID_JSON = "JSON の形式または値の型が正しくありません";

  private PredictionApi() {}

  /** 応答の本文 */
  record SalesResponse(double sales) {}

  record SurvivalResponse(boolean survived) {}

  record HealthResponse(String status, Map<String, Boolean> models) {}

  record ErrorResponse(String detail) {}

  record ValidationErrorResponse(List<String> detail) {}

  /** サービスを使う API を作る。start はしない。 */
  public static Javalin create(PredictionService service) {
    return Javalin.create(
        config -> {
          config.jsonMapper(new JavalinJackson(new ObjectMapper(), false));
          // JSON として読めない・型が合わない入力（bodyAsClass が Jackson の例外を投げる）。例外のメッセージには内部の型名が含まれるので、応答には出さない
          config.routes.exception(
              JacksonException.class,
              (e, ctx) ->
                  ctx.status(HttpStatus.UNPROCESSABLE_CONTENT)
                      .json(new ValidationErrorResponse(List.of(INVALID_JSON))));
          // ドメインの例外を HTTP のステータスコードに変える
          config.routes.exception(
              ModelNotFoundException.class,
              (e, ctx) ->
                  ctx.status(HttpStatus.SERVICE_UNAVAILABLE)
                      .json(new ErrorResponse(e.getMessage())));
          config.routes.get("/health", ctx -> health(ctx, service));
          config.routes.post("/cinema/sales", ctx -> predictSales(ctx, service));
          config.routes.post("/survived", ctx -> predictSurvival(ctx, service));
        });
  }

  private static void health(Context ctx, PredictionService service) {
    Map<String, Boolean> models = service.health();
    String status = models.values().stream().allMatch(Boolean::booleanValue) ? "ok" : "degraded";
    ctx.json(new HealthResponse(status, models));
  }

  private static void predictSales(Context ctx, PredictionService service)
      throws ModelNotFoundException {
    switch (ctx.bodyAsClass(MovieRequest.class).validate()) {
      case Validated.Invalid<Movie> invalid -> rejected(ctx, invalid.errors());
      case Validated.Valid<Movie> valid ->
          ctx.json(new SalesResponse(service.predictSales(valid.value()).sales()));
    }
  }

  private static void predictSurvival(Context ctx, PredictionService service)
      throws ModelNotFoundException {
    switch (ctx.bodyAsClass(PassengerRequest.class).validate()) {
      case Validated.Invalid<Passenger> invalid -> rejected(ctx, invalid.errors());
      case Validated.Valid<Passenger> valid ->
          ctx.json(new SurvivalResponse(service.predictSurvival(valid.value()).survived()));
    }
  }

  private static void rejected(Context ctx, List<String> errors) {
    ctx.status(HttpStatus.UNPROCESSABLE_CONTENT).json(new ValidationErrorResponse(errors));
  }
}
