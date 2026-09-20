namespace MachineLearning;

/// <summary>章を選んで実行する入口。使い方: dotnet run --project src/MachineLearning -- chapter01</summary>
public static class Program
{
    private static readonly Dictionary<string, Action<TextWriter>> Chapters = new(StringComparer.Ordinal)
    {
        ["chapter01"] = Chapter01.Program.Run,
        ["chapter02"] = Chapter02.Program.Run,
        ["chapter03"] = Chapter03.Program.Run,
    };

    public static int Main(string[] args)
    {
        ArgumentNullException.ThrowIfNull(args);
        if (args.Length == 1 && Chapters.TryGetValue(args[0], out var run))
        {
            run(Console.Out);
            return 0;
        }

        Console.Error.WriteLine($"使い方: dotnet run --project src/MachineLearning -- ({string.Join(" | ", Chapters.Keys)})");
        return 1;
    }
}
