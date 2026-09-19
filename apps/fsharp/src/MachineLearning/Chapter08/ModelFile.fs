module MachineLearning.Chapter08.ModelFile

open System.IO
open System.Text.Json
open MachineLearning.Chapter03.DecisionTree
open MachineLearning.Chapter08.Transformers
open MachineLearning.Chapter08.Pipeline

/// 決定木を JSON にするための形。葉なら Label だけ、節なら Split・Left・Right だけを持つ
type TreeDto =
    {
        Label: int option
        Split: Split option
        Left: TreeDto option
        Right: TreeDto option
    }

/// 客室クラスと性別のグループの、年齢の中央値
type AgeMedianDto =
    {
        Pclass: int
        Sex: string
        Median: float
    }

/// 学習済みのパイプラインを JSON にするための形
type PipelineDto =
    {
        AgeMedians: AgeMedianDto list
        OverallAgeMedian: float
        Embarked: string
        Dummies: DummyEncoder
        Tree: TreeDto
    }

// 左右の部分木を変換してからレコードにまとめるので末尾再帰ではない。再帰の深さは木の深さまで
// fsharplint:disable-next-line EnsureTailCallDiagnosticsInRecursiveFunctions
let rec private treeToDto (tree: Tree<int>) : TreeDto =
    match tree with
    | Leaf label ->
        {
            Label = Some label
            Split = None
            Left = None
            Right = None
        }
    | Node(split, left, right) ->
        {
            Label = None
            Split = Some split
            Left = Some(treeToDto left)
            Right = Some(treeToDto right)
        }

// 左右の部分木を変換してから結果をまとめるので末尾再帰ではない。再帰の深さは木の深さまで
// fsharplint:disable-next-line EnsureTailCallDiagnosticsInRecursiveFunctions
let rec private treeOfDto (dto: TreeDto) : Result<Tree<int>, string> =
    match dto with
    | {
          Label = Some label
          Split = None
          Left = None
          Right = None
      } -> Ok(Leaf label)
    | {
          Label = None
          Split = Some split
          Left = Some left
          Right = Some right
      } ->
        match treeOfDto left, treeOfDto right with
        | Ok left, Ok right -> Ok(Node(split, left, right))
        | Error message, _
        | _, Error message -> Error message
    | _ -> Error "葉でも節でもない木があります"

let private toDto (pipeline: FittedPipeline) : PipelineDto =
    {
        AgeMedians =
            pipeline.Age.Medians
            |> Map.toList
            |> List.map (fun ((pclass, sex), median) ->
                {
                    Pclass = pclass
                    Sex = sex
                    Median = median
                })
        OverallAgeMedian = pipeline.Age.OverallMedian
        Embarked = pipeline.Embarked
        Dummies = pipeline.Dummies
        Tree = treeToDto pipeline.Tree
    }

let private ofDto (dto: PipelineDto) : Result<FittedPipeline, string> =
    treeOfDto dto.Tree
    |> Result.map (fun tree ->
        {
            Age =
                {
                    Medians = dto.AgeMedians |> List.map (fun m -> (m.Pclass, m.Sex), m.Median) |> Map.ofList
                    OverallMedian = dto.OverallAgeMedian
                }
            Embarked = dto.Embarked
            Dummies = dto.Dummies
            Tree = tree
        })

/// 学習済みのパイプラインを JSON で保存する。保存先のディレクトリが無ければ作る
let saveModel (modelFile: string) (pipeline: FittedPipeline) : unit =
    Directory.CreateDirectory(Path.GetDirectoryName modelFile) |> ignore
    File.WriteAllText(modelFile, JsonSerializer.Serialize(toDto pipeline))

/// 読み込むときの設定。JSON に無い項目があれば、null で埋めずに JsonException にする
let private readOptions =
    JsonSerializerOptions(RespectRequiredConstructorParameters = true)

/// 保存したパイプラインを読み込む。形が違えば Error を返す
let loadModel (modelFile: string) : Result<FittedPipeline, string> =
    try
        JsonSerializer.Deserialize<PipelineDto>(File.ReadAllText modelFile, readOptions)
        |> ofDto
    with :? JsonException as e ->
        Error e.Message
