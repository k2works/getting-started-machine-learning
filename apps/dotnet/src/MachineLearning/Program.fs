module MachineLearning.Program

[<EntryPoint>]
let main argv =
    match argv with
    | [| "chapter01" |] ->
        Chapter01.Main.run (printfn "%s")
        0
    | _ ->
        eprintfn "使い方: dotnet run --project src/MachineLearning -- chapter01"
        1
