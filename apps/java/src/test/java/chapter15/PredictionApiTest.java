package chapter15;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PredictionApiTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ModelStore STUB = Stubs.store(movie -> 1200.0, passenger -> true);

  private static ObjectNode movieJson() {
    return MAPPER
        .createObjectNode()
        .put("sns1", 200.0)
        .put("sns2", 500.0)
        .put("actor", 3000.0)
        .put("original", 1);
  }

  private static ObjectNode passengerJson() {
    ObjectNode node =
        MAPPER.createObjectNode().put("pclass", 1).put("sex", "female").putNull("age");
    return node.put("sib_sp", 0).put("parch", 0).put("fare", 50.0).put("embarked", "C");
  }

  private static JsonNode json(Response response) throws Exception {
    return MAPPER.readTree(response.body().string());
  }

  private static JsonNode json(Object value) {
    return MAPPER.valueToTree(value);
  }

  @Nested
  class CinemaSales {
    @Test
    @DisplayName("映画の特徴量を送ると予測した興行収入を返す")
    void predicts() {
      JavalinTest.test(
          PredictionApi.create(new PredictionService(STUB)),
          (server, client) -> {
            Response response = client.post("/cinema/sales", movieJson().toString());

            assertThat(response.code()).isEqualTo(200);
            assertThat(json(response)).isEqualTo(json(Map.of("sales", 1200.0)));
          });
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource(
        delimiter = '|',
        value = {"sns1|-1.0", "actor|\"多い\"", "original|2", "sns2|null"})
    @DisplayName("特徴量が不正なら 422 を返す")
    void rejectsInvalid(String field, String value) throws Exception {
      ObjectNode body = movieJson();
      body.set(field, MAPPER.readTree(value));
      JavalinTest.test(
          PredictionApi.create(new PredictionService(STUB)),
          (server, client) ->
              assertThat(client.post("/cinema/sales", body.toString()).code()).isEqualTo(422));
    }

    @Test
    @DisplayName("モデルが無ければ 503 を返す")
    void missingModel() {
      JavalinTest.test(
          PredictionApi.create(new PredictionService(Stubs.emptyStore())),
          (server, client) -> {
            Response response = client.post("/cinema/sales", movieJson().toString());

            assertThat(response.code()).isEqualTo(503);
            assertThat(json(response)).isEqualTo(json(Map.of("detail", "学習済みモデル cinema が見つかりません")));
          });
    }
  }

  @Nested
  class Survived {
    @Test
    @DisplayName("乗客の特徴量を送ると生存の予測を返す")
    void predicts() {
      JavalinTest.test(
          PredictionApi.create(new PredictionService(STUB)),
          (server, client) -> {
            Response response = client.post("/survived", passengerJson().toString());

            assertThat(response.code()).isEqualTo(200);
            assertThat(json(response)).isEqualTo(json(Map.of("survived", true)));
          });
    }

    @Test
    @DisplayName("年齢と乗船港は省略できる")
    void optionalFields() {
      ObjectNode body = passengerJson();
      body.remove("age");
      body.remove("embarked");
      JavalinTest.test(
          PredictionApi.create(new PredictionService(STUB)),
          (server, client) ->
              assertThat(client.post("/survived", body.toString()).code()).isEqualTo(200));
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource(
        delimiter = '|',
        value = {"pclass|4", "sex|\"unknown\"", "embarked|\"X\"", "fare|-5.0"})
    @DisplayName("特徴量が不正なら 422 を返す")
    void rejectsInvalid(String field, String value) throws Exception {
      ObjectNode body = passengerJson();
      body.set(field, MAPPER.readTree(value));
      JavalinTest.test(
          PredictionApi.create(new PredictionService(STUB)),
          (server, client) ->
              assertThat(client.post("/survived", body.toString()).code()).isEqualTo(422));
    }

    @Test
    @DisplayName("JSON として読めなければ 422 と、内部の型名を含まない理由を返す")
    void rejectsBrokenJson() {
      JavalinTest.test(
          PredictionApi.create(new PredictionService(STUB)),
          (server, client) -> {
            Response response = client.post("/survived", "{");

            assertThat(response.code()).isEqualTo(422);
            assertThat(json(response))
                .isEqualTo(json(Map.of("detail", java.util.List.of("JSON の形式または値の型が正しくありません"))));
          });
    }

    @Test
    @DisplayName("モデルが無ければ 503 を返す")
    void missingModel() {
      JavalinTest.test(
          PredictionApi.create(new PredictionService(Stubs.emptyStore())),
          (server, client) ->
              assertThat(client.post("/survived", passengerJson().toString()).code())
                  .isEqualTo(503));
    }
  }

  @Nested
  class Health {
    @Test
    @DisplayName("すべてのモデルを読み込めれば ok を返す")
    void ok() {
      JavalinTest.test(
          PredictionApi.create(new PredictionService(STUB)),
          (server, client) -> {
            Response response = client.get("/health");

            assertThat(response.code()).isEqualTo(200);
            assertThat(json(response))
                .isEqualTo(
                    json(
                        Map.of(
                            "status", "ok", "models", Map.of("cinema", true, "survived", true))));
          });
    }

    @Test
    @DisplayName("読み込めないモデルがあれば degraded を返す")
    void degraded() {
      JavalinTest.test(
          PredictionApi.create(new PredictionService(Stubs.emptyStore())),
          (server, client) ->
              assertThat(json(client.get("/health")))
                  .isEqualTo(
                      json(
                          Map.of(
                              "status",
                              "degraded",
                              "models",
                              Map.of("cinema", false, "survived", false)))));
    }
  }
}
