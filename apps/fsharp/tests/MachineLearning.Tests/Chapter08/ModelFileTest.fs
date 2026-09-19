module MachineLearning.Tests.Chapter08.ModelFileTest

open System
open System.IO
open System.Text.Json
open System.Text.Json.Nodes
open Xunit
open MachineLearning.Chapter03.DecisionTree
open MachineLearning.Chapter08.Pipeline
open MachineLearning.Chapter08.ModelFile
open MachineLearning.Tests.Chapter08.PipelineTest

type Sample =
    {
        Name: string
        Value: float option
        Items: int list
        Table: Map<string, float>
    }

[<Fact>]
let ``学習用テスト: レコード・option・リスト・文字列がキーの Map は JSON にして戻せる`` () =
    let sample =
        {
            Name = "a"
            Value = None
            Items = [ 1; 2 ]
            Table = Map.ofList [ "x", 0.5 ]
        }

    let json = JsonSerializer.Serialize sample

    Assert.Equal("""{"Name":"a","Value":null,"Items":[1,2],"Table":{"x":0.5}}""", json)
    Assert.Equal(sample, JsonSerializer.Deserialize<Sample> json)

[<Fact>]
let ``学習用テスト: 判別共用体は JSON にできない`` () =
    let tree: Tree<int> = Leaf 1

    Assert.Throws<NotSupportedException>(fun () -> JsonSerializer.Serialize tree |> ignore)
    |> ignore

[<Fact>]
let ``学習用テスト: 組がキーの Map は JSON にできない`` () =
    let medians = Map.ofList [ (1, "female"), 35.0 ]

    Assert.Throws<NotSupportedException>(fun () -> JsonSerializer.Serialize medians |> ignore)
    |> ignore

/// 一時ディレクトリの下の、まだ無いディレクトリの中のファイルのパス
let newModelFile () : string =
    Path.Combine(Directory.CreateTempSubdirectory("model-").FullName, "model", "survived.json")

[<Fact>]
let ``保存したパイプラインを読み込むと同じ予測をする`` () =
    let pipeline = fitPipeline options trainX trainT
    let modelFile = newModelFile ()

    saveModel modelFile pipeline

    match loadModel modelFile with
    | Ok loaded ->
        Assert.Equal(pipeline, loaded)
        Assert.Equal<int list>([ 1; 0 ], predict loaded newPassengers)
    | Error message -> Assert.Fail message

[<Fact>]
let ``パイプラインの形をしていないファイルは読み込まない`` () =
    let modelFile = newModelFile ()
    Directory.CreateDirectory(Path.GetDirectoryName modelFile) |> ignore
    File.WriteAllText(modelFile, """{"Tree":{"Label":1}}""")

    Assert.True(Result.isError (loadModel modelFile))

[<Fact>]
let ``葉でも節でもない木を含むファイルは読み込まない`` () =
    let modelFile = newModelFile ()
    saveModel modelFile (fitPipeline options trainX trainT)
    let json = JsonNode.Parse(File.ReadAllText modelFile)
    json["Tree"] <- JsonNode.Parse("""{"Label":null,"Split":null,"Left":null,"Right":null}""")
    File.WriteAllText(modelFile, json.ToJsonString())

    Assert.Equal(Error "葉でも節でもない木があります", loadModel modelFile)
