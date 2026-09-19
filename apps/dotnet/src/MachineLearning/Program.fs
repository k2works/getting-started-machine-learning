module MachineLearning.Program

/// コマンドライン引数の章の名前から、その章の run への対応
let chapters: Map<string, (string -> unit) -> unit> =
    Map.ofList
        [
            "chapter01", Chapter01.Main.run
            "chapter02", Chapter02.Main.run
            "chapter03", Chapter03.Main.run
            "chapter13", Chapter13.Main.run
        ]

[<EntryPoint>]
let main argv =
    match argv with
    | [| name |] when chapters.ContainsKey name ->
        chapters[name](printfn "%s")
        0
    | _ ->
        let names = chapters.Keys |> String.concat " | "
        eprintfn $"使い方: dotnet run --project src/MachineLearning -- ({names})"
        1
