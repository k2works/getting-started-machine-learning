namespace MachineLearning.Tests.Chapter15;

using MachineLearning.Chapter15;

public class RequestValidationTests
{
    private const string MovieJson = """{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}""";
    private const string PassengerJson =
        """{"pclass": 1, "sex": "female", "age": null, "sib_sp": 0, "parch": 0, "fare": 50.0, "embarked": "C"}""";

    [Fact(DisplayName = "正しい JSON を映画の特徴量にする")]
    public void ParsesMovie()
    {
        var result = RequestValidation.ParseMovie(MovieJson);

        Assert.True(result.IsValid);
        Assert.Equal(new Movie(200, 500, 3000, true), result.Value);
    }

    [Fact(DisplayName = "年齢と乗船港は省略できる")]
    public void OptionalFields()
    {
        var result = RequestValidation.ParsePassenger(
            """{"pclass": 3, "sex": "male", "sib_sp": 0, "parch": 0, "fare": 8.0}""");

        Assert.True(result.IsValid);
        Assert.Null(result.Value!.Age);
        Assert.Null(result.Value.Embarked);
    }

    [Fact(DisplayName = "null の年齢は省略と同じに扱う")]
    public void NullAge()
    {
        var result = RequestValidation.ParsePassenger(PassengerJson);

        Assert.True(result.IsValid);
        Assert.Null(result.Value!.Age);
        Assert.Equal(Embarked.Cherbourg, result.Value.Embarked);
    }

    [Theory(DisplayName = "不正な項目の理由を返す")]
    [InlineData("""{"sns1": -1, "sns2": 500, "actor": 3000, "original": 1}""", "sns1 は 0 以上にしてください")]
    [InlineData("""{"sns1": "多い", "sns2": 500, "actor": 3000, "original": 1}""", "sns1 は数値にしてください")]
    [InlineData("""{"sns1": 200, "sns2": 500, "actor": 3000, "original": 2}""", "original は 0、1 のどれかにしてください")]
    [InlineData("""{"sns2": 500, "actor": 3000, "original": 1}""", "sns1 を指定してください")]
    public void RejectsInvalidMovie(string body, string expected)
    {
        var result = RequestValidation.ParseMovie(body);

        Assert.False(result.IsValid);
        Assert.Contains(expected, result.Errors);
    }

    [Fact(DisplayName = "不正な項目が複数あれば理由をすべて集める")]
    public void CollectsAllErrors()
    {
        var result = RequestValidation.ParseMovie("""{"sns1": -1, "sns2": -2, "actor": 3000, "original": 1}""");

        Assert.Equal(["sns1 は 0 以上にしてください", "sns2 は 0 以上にしてください"], result.Errors);
    }

    [Theory(DisplayName = "乗客の不正な項目の理由を返す")]
    [InlineData("pclass", "4", "pclass は 1、2、3 のどれかにしてください")]
    [InlineData("sex", "\"unknown\"", "sex は male、female のどれかにしてください")]
    [InlineData("embarked", "\"X\"", "embarked は C、Q、S のどれかにしてください")]
    [InlineData("fare", "-5.0", "fare は 0 以上にしてください")]
    public void RejectsInvalidPassenger(string field, string value, string expected)
    {
        var body = PassengerJson.Replace($"\"{field}\": ", $"\"{field}\": {value}#", StringComparison.Ordinal);
        body = System.Text.RegularExpressions.Regex.Replace(body, @"#[^,}]*", string.Empty);

        var result = RequestValidation.ParsePassenger(body);

        Assert.False(result.IsValid);
        Assert.Contains(expected, result.Errors);
    }

    [Theory(DisplayName = "JSON として読めなければ理由を返す")]
    [InlineData("{", "JSON の形式が正しくありません")]
    [InlineData("[1, 2]", "JSON のオブジェクトにしてください")]
    public void RejectsBrokenJson(string body, string expected) =>
        Assert.Equal([expected], RequestValidation.ParseMovie(body).Errors);
}
