// notebooks/ の Notebook の出力セルを検査・削除し、画面なしで実行する。
//
// 使い方（apps/dotnet で実行する）:
//     dotnet fsi tools/notebooks.fsx verify   出力セルが残っている Notebook があれば失敗する
//     dotnet fsi tools/notebooks.fsx strip    出力セルを消す
//     dotnet fsi tools/notebooks.fsx execute  すべて実行する（結果は bin/notebooks に書き出す。学習データと Jupyter が必要）

open System
open System.Diagnostics
open System.IO
open System.Text
open System.Text.Encodings.Web
open System.Text.Json
open System.Text.Json.Nodes

let notebookDir =
    Path.GetFullPath(Path.Combine(__SOURCE_DIRECTORY__, "..", "notebooks"))

let notebooks () : string list =
    Directory.GetFiles(notebookDir, "*.ipynb") |> Array.sort |> Array.toList

/// 実行したときに書き込まれるセルのメタデータ（nbstripout が既定で消すもの）
let executionMetadata = [ "execution"; "ExecuteTime"; "collapsed"; "scrolled" ]

let codeCells (notebook: JsonNode) : JsonObject list =
    notebook["cells"].AsArray()
    |> Seq.map (fun cell -> cell.AsObject())
    |> Seq.filter (fun cell -> cell["cell_type"].GetValue<string>() = "code")
    |> Seq.toList

/// 出力・実行番号・実行の記録のどれかが残っていれば、実行済みのセルとみなす
let isExecuted (cell: JsonObject) : bool =
    cell["outputs"].AsArray().Count > 0
    || not (isNull cell["execution_count"])
    || executionMetadata
       |> List.exists (fun key -> cell["metadata"].AsObject().ContainsKey key)

let clear (cell: JsonObject) : unit =
    cell["outputs"] <- JsonArray()
    cell["execution_count"] <- null
    let metadata = cell["metadata"].AsObject()
    executionMetadata |> List.iter (metadata.Remove >> ignore)

let load (path: string) : JsonNode = File.ReadAllText path |> JsonNode.Parse

let isDirty (path: string) : bool =
    load path |> codeCells |> List.exists isExecuted

/// Jupyter と同じ書式（字下げ 1、LF、日本語をエスケープしない、末尾に改行）で書き出す
let jsonOptions =
    JsonSerializerOptions(
        WriteIndented = true,
        IndentSize = 1,
        NewLine = "\n",
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping
    )

let save (path: string) (notebook: JsonNode) : unit =
    File.WriteAllText(path, notebook.ToJsonString jsonOptions + "\n", UTF8Encoding false)

let verify () : int =
    match notebooks () |> List.filter isDirty with
    | [] -> 0
    | dirty ->
        let names = dirty |> List.map Path.GetFileName |> String.concat ", "
        eprintfn $"出力セルが残っている Notebook: {names}（dotnet fsi tools/notebooks.fsx strip で消す）"
        1

let strip () : int =
    for path in notebooks () |> List.filter isDirty do
        let notebook = load path
        codeCells notebook |> List.iter clear
        save path notebook
        printfn $"出力セルを消した: {Path.GetFileName path}"

    0

/// 環境変数 JUPYTER で jupyter のコマンドを指定できる（既定は PATH の jupyter）
let execute () : int =
    let jupyter =
        Environment.GetEnvironmentVariable "JUPYTER"
        |> Option.ofObj
        |> Option.defaultValue "jupyter"

    let outputDir =
        Path.GetFullPath(Path.Combine(__SOURCE_DIRECTORY__, "..", "bin", "notebooks"))

    let run (path: string) =
        printfn $"実行: {Path.GetFileName path}"
        let startInfo = ProcessStartInfo(jupyter, WorkingDirectory = notebookDir)

        [
            "nbconvert"
            "--to"
            "notebook"
            "--execute"
            "--output-dir"
            outputDir
            "--ExecutePreprocessor.timeout=300"
            Path.GetFileName path
        ]
        |> List.iter startInfo.ArgumentList.Add

        use proc = Process.Start startInfo
        proc.WaitForExit()
        proc.ExitCode

    notebooks ()
    |> List.fold (fun code path -> if code = 0 then run path else code) 0

let commands = Map.ofList [ "verify", verify; "strip", strip; "execute", execute ]

match fsi.CommandLineArgs |> Array.tail with
| [| name |] when commands.ContainsKey name -> exit (commands[name]())
| _ ->
    eprintfn "使い方: dotnet fsi tools/notebooks.fsx (verify | strip | execute)"
    exit 2
