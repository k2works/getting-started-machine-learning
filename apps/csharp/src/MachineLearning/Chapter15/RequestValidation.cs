namespace MachineLearning.Chapter15;

using System.Text.Json;

/// <summary>
/// プレゼンテーション層。HTTP の本文（JSON）を検証して、ドメイン層の型にする。
/// 不正な項目が複数あれば、理由をすべて集めて返す。
/// </summary>
public static class RequestValidation
{
    /// <summary>検証の結果。正しければ値を、不正なら理由の一覧を持つ。</summary>
    public sealed record Validation<T>(T? Value, IReadOnlyList<string> Errors)
    {
        public bool IsValid => this.Errors.Count == 0;
    }

    /// <summary>本文を映画の特徴量にする。</summary>
    public static Validation<Movie> ParseMovie(string body) =>
        ParseWith(body, json =>
        {
            var errors = new List<string>();
            var sns1 = NonNegativeNumber(json, "sns1", errors);
            var sns2 = NonNegativeNumber(json, "sns2", errors);
            var actor = NonNegativeNumber(json, "actor", errors);
            var original = OneOf(json, "original", new Dictionary<string, bool>(StringComparer.Ordinal) { ["0"] = false, ["1"] = true }, errors);
            return errors.Count == 0
                ? Valid(new Movie(sns1!.Value, sns2!.Value, actor!.Value, original!.Value))
                : Invalid<Movie>(errors);
        });

    /// <summary>本文を乗客の特徴量にする。年齢と乗船港は省略できる。</summary>
    public static Validation<Passenger> ParsePassenger(string body) =>
        ParseWith(body, json =>
        {
            var errors = new List<string>();
            var pclass = OneOf(json, "pclass", new Dictionary<string, Pclass>(StringComparer.Ordinal)
            {
                ["1"] = Pclass.First,
                ["2"] = Pclass.Second,
                ["3"] = Pclass.Third,
            }, errors);
            var sex = OneOf(json, "sex", new Dictionary<string, Sex>(StringComparer.Ordinal)
            {
                ["male"] = Sex.Male,
                ["female"] = Sex.Female,
            }, errors);
            var age = OptionalNonNegativeNumber(json, "age", errors);
            var sibSp = NonNegativeInteger(json, "sib_sp", errors);
            var parch = NonNegativeInteger(json, "parch", errors);
            var fare = NonNegativeNumber(json, "fare", errors);
            var embarked = OptionalOneOf(json, "embarked", new Dictionary<string, Embarked>(StringComparer.Ordinal)
            {
                ["C"] = Embarked.Cherbourg,
                ["Q"] = Embarked.Queenstown,
                ["S"] = Embarked.Southampton,
            }, errors);
            return errors.Count == 0
                ? Valid(new Passenger(pclass!.Value, sex!.Value, age, sibSp!.Value, parch!.Value, fare!.Value, embarked))
                : Invalid<Passenger>(errors);
        });

    private static Validation<T> Valid<T>(T value) => new(value, []);

    private static Validation<T> Invalid<T>(IReadOnlyList<string> errors) => new(default, errors);

    private static Validation<T> ParseWith<T>(string body, Func<JsonElement, Validation<T>> validate)
    {
        try
        {
            using var document = JsonDocument.Parse(body);
            return document.RootElement.ValueKind == JsonValueKind.Object
                ? validate(document.RootElement)
                : Invalid<T>(["JSON のオブジェクトにしてください"]);
        }
        catch (JsonException)
        {
            return Invalid<T>(["JSON の形式が正しくありません"]);
        }
    }

    /// <summary>項目の値。無い項目と null は null を返す。</summary>
    private static JsonElement? Field(JsonElement json, string name) =>
        json.TryGetProperty(name, out var value) && value.ValueKind != JsonValueKind.Null ? value : null;

    private static double? NonNegativeNumber(JsonElement json, string name, List<string> errors)
    {
        var value = Field(json, name);
        if (value is null)
        {
            errors.Add($"{name} を指定してください");
            return null;
        }

        return NonNegative(name, Number(name, value.Value, errors), errors);
    }

    private static double? OptionalNonNegativeNumber(JsonElement json, string name, List<string> errors)
    {
        var value = Field(json, name);
        return value is null ? null : NonNegative(name, Number(name, value.Value, errors), errors);
    }

    private static int? NonNegativeInteger(JsonElement json, string name, List<string> errors)
    {
        var value = Field(json, name);
        if (value is null)
        {
            errors.Add($"{name} を指定してください");
            return null;
        }

        if (value.Value.ValueKind != JsonValueKind.Number || !value.Value.TryGetInt32(out var number))
        {
            errors.Add($"{name} は整数にしてください");
            return null;
        }

        if (number < 0)
        {
            errors.Add($"{name} は 0 以上にしてください");
            return null;
        }

        return number;
    }

    private static double? Number(string name, JsonElement value, List<string> errors)
    {
        if (value.ValueKind != JsonValueKind.Number)
        {
            errors.Add($"{name} は数値にしてください");
            return null;
        }

        return value.GetDouble();
    }

    private static double? NonNegative(string name, double? value, List<string> errors)
    {
        if (value is < 0)
        {
            errors.Add($"{name} は 0 以上にしてください");
            return null;
        }

        return value;
    }

    private static TValue? OneOf<TValue>(
        JsonElement json, string name, IReadOnlyDictionary<string, TValue> choices, List<string> errors)
        where TValue : struct
    {
        var value = Field(json, name);
        if (value is null)
        {
            errors.Add($"{name} を指定してください");
            return null;
        }

        return OptionalOneOf(json, name, choices, errors);
    }

    /// <summary>JSON の値（数値か文字列）を、決められた値の一覧のどれかに対応づける。</summary>
    private static TValue? OptionalOneOf<TValue>(
        JsonElement json, string name, IReadOnlyDictionary<string, TValue> choices, List<string> errors)
        where TValue : struct
    {
        var value = Field(json, name);
        if (value is null)
        {
            return null;
        }

        var raw = value.Value.ValueKind == JsonValueKind.String ? value.Value.GetString() : value.Value.GetRawText();
        if (raw is not null && choices.TryGetValue(raw, out var choice))
        {
            return choice;
        }

        errors.Add($"{name} は {string.Join("、", choices.Keys)} のどれかにしてください");
        return null;
    }
}
