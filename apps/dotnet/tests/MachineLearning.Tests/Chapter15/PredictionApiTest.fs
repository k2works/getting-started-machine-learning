module MachineLearning.Tests.Chapter15.PredictionApiTest

open System.Net
open System.Net.Http
open System.Text
open System.Threading.Tasks
open Microsoft.AspNetCore.Builder
open Microsoft.AspNetCore.TestHost
open Xunit
open MachineLearning.Chapter15.Domain
open MachineLearning.Chapter15.PredictionApi
open MachineLearning.Tests.Chapter15.Stubs

/// 置き場を使う API を、ネットワークを使わないテスト用のサーバーで起動し、クライアントを返す
let clientWith (store: ModelStore) : Task<HttpClient> =
    task {
        let builder = WebApplication.CreateBuilder()
        builder.WebHost.UseTestServer() |> ignore
        let app = createWebApplication builder store
        do! app.StartAsync()
        return app.GetTestClient()
    }

/// 要求を送り、状態コードと本文の組を返す
let send (store: ModelStore) (request: HttpRequestMessage) : Task<HttpStatusCode * string> =
    task {
        let! client = clientWith store
        use! response = client.SendAsync request
        let! body = response.Content.ReadAsStringAsync()
        return response.StatusCode, body
    }

let postJson (store: ModelStore) (path: string) (body: string) : Task<HttpStatusCode * string> =
    let request = new HttpRequestMessage(HttpMethod.Post, path)
    request.Content <- new StringContent(body, Encoding.UTF8, "application/json")
    send store request

let get (store: ModelStore) (path: string) : Task<HttpStatusCode * string> =
    send store (new HttpRequestMessage(HttpMethod.Get, path))

let movieJson = """{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}"""

let passengerJson =
    """{"pclass": 1, "sex": "female", "age": null, "sib_sp": 0, "parch": 0, "fare": 50, "embarked": "C"}"""

[<Fact>]
let ``映画の特徴量を送ると予測した興行収入を返す`` () =
    task {
        let! actual = postJson stubModelStore "/cinema/sales" movieJson
        Assert.Equal((HttpStatusCode.OK, """{"sales":1200}"""), actual)
    }

[<Fact>]
let ``特徴量が不正なら 422 と理由を返す`` () =
    task {
        let body = """{"sns1": -1, "sns2": 500, "actor": 3000, "original": 1}"""
        let! actual = postJson stubModelStore "/cinema/sales" body
        Assert.Equal((HttpStatusCode.UnprocessableEntity, """{"detail":["sns1 は 0 以上にしてください"]}"""), actual)
    }

[<Fact>]
let ``JSON として読めなければ 422 を返す`` () =
    task {
        let! actual = postJson stubModelStore "/cinema/sales" "{"
        Assert.Equal((HttpStatusCode.UnprocessableEntity, """{"detail":["JSON の形式が正しくありません"]}"""), actual)
    }

[<Fact>]
let ``興行収入のモデルが無ければ 503 を返す`` () =
    task {
        let! actual = postJson emptyModelStore "/cinema/sales" movieJson
        Assert.Equal((HttpStatusCode.ServiceUnavailable, """{"detail":"学習済みモデル cinema が見つかりません"}"""), actual)
    }

[<Fact>]
let ``乗客の特徴量を送ると生存の予測を返す`` () =
    task {
        let! actual = postJson stubModelStore "/survived" passengerJson
        Assert.Equal((HttpStatusCode.OK, """{"survived":true}"""), actual)
    }

[<Fact>]
let ``生存のモデルが無ければ 503 を返す`` () =
    task {
        let! actual = postJson emptyModelStore "/survived" passengerJson
        Assert.Equal(HttpStatusCode.ServiceUnavailable, fst actual)
    }

[<Fact>]
let ``すべてのモデルを読み込めれば ok を返す`` () =
    task {
        let! actual = get stubModelStore "/health"
        Assert.Equal((HttpStatusCode.OK, """{"status":"ok","models":{"cinema":true,"survived":true}}"""), actual)
    }

[<Fact>]
let ``読み込めないモデルがあれば degraded を返す`` () =
    task {
        let! actual = get emptyModelStore "/health"

        Assert.Equal(
            (HttpStatusCode.OK, """{"status":"degraded","models":{"cinema":false,"survived":false}}"""),
            actual
        )
    }

[<Fact>]
let ``予測中に例外が起きたら内部の情報を出さずに 500 を返す`` () =
    task {
        let failingStore =
            { stubModelStore with
                LoadSalesModel = fun () -> Ok(fun _ -> failwith "内部の詳細")
            }

        let! actual = postJson failingStore "/cinema/sales" movieJson
        Assert.Equal((HttpStatusCode.InternalServerError, """{"detail":"予測中にエラーが発生しました"}"""), actual)
    }

[<Fact>]
let ``無い経路には 404 を返す`` () =
    task {
        let! actual = get stubModelStore "/unknown"
        Assert.Equal(HttpStatusCode.NotFound, fst actual)
    }
