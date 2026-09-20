namespace MachineLearning.Chapter08;

/// <summary>クラスの重みの付け方。</summary>
public enum ClassWeight
{
    /// <summary>重みを付けない（すべて 1）</summary>
    None,

    /// <summary>クラスの件数に反比例する重みを付ける</summary>
    Balanced,
}
