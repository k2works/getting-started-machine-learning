namespace MachineLearning.Chapter15;

/// <summary>
/// 予測の結果。F# の Result&lt;'T, PredictionError&gt; に当たる型を、C# では自分で作る。
/// 成功と失敗を抽象レコードと sealed な派生で表し、switch 式で分けて扱う。
/// </summary>
/// <typeparam name="T">成功したときの値の型</typeparam>
public abstract record PredictionResult<T>
{
    internal PredictionResult()
    {
    }

    /// <summary>成功なら値を、失敗なら理由を渡して、どちらも同じ型に変える。</summary>
    public TResult Match<TResult>(Func<T, TResult> onSuccess, Func<PredictionError, TResult> onError)
    {
        ArgumentNullException.ThrowIfNull(onSuccess);
        ArgumentNullException.ThrowIfNull(onError);
        return this switch
        {
            Success<T> success => onSuccess(success.Value),
            Failure<T> failure => onError(failure.Error),
            _ => throw new ArgumentException($"知らない結果です: {this}"),
        };
    }

    /// <summary>成功のときだけ値を変える。</summary>
    public PredictionResult<TResult> Map<TResult>(Func<T, TResult> map) =>
        this.Match<PredictionResult<TResult>>(
            value => new Success<TResult>(map(value)),
            error => new Failure<TResult>(error));

    /// <summary>成功かどうか。</summary>
    public bool IsSuccess => this is Success<T>;
}

/// <summary>成功した結果。</summary>
public sealed record Success<T>(T Value) : PredictionResult<T>;

/// <summary>失敗した結果。</summary>
public sealed record Failure<T>(PredictionError Error) : PredictionResult<T>;
