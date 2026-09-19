module MachineLearning.Chapter01.KinokoTakenoko

open System.IO

/// きのこの山派か、たけのこの里派か
type Faction =
    | Kinoko
    | Takenoko

type Person =
    {
        Height: float
        Weight: float
        AgeGroup: int
        Faction: Faction
    }

/// 人物から正解ラベル（派閥）を除いた特徴量
type Features =
    {
        Height: float
        Weight: float
        AgeGroup: int
    }

let parseFaction (value: string) : Faction =
    match value with
    | "きのこ" -> Kinoko
    | "たけのこ" -> Takenoko
    | _ -> failwith $"派閥 {value} は、きのこ・たけのこのどちらでもありません"

let loadPeople (csvFile: string) : Person list =
    match File.ReadAllLines csvFile |> Array.toList with
    | [] -> []
    | headerLine :: lines ->
        let header = headerLine.Split ','

        let indexOf name =
            match Array.tryFindIndex ((=) name) header with
            | Some index -> index
            | None -> failwith $"列 {name} が見つかりません"

        let parse (line: string) =
            let values = line.Split ','
            let column name = values[indexOf name]

            {
                Height = float (column "身長")
                Weight = float (column "体重")
                AgeGroup = int (column "年代")
                Faction = parseFaction (column "派閥")
            }

        lines |> List.filter (fun line -> line.Trim() <> "") |> List.map parse

let splitFeaturesAndLabels (people: Person list) : Features list * Faction list =
    let features =
        people
        |> List.map (fun person ->
            {
                Height = person.Height
                Weight = person.Weight
                AgeGroup = person.AgeGroup
            })

    features, people |> List.map (fun person -> person.Faction)

/// 「20 代ならきのこ派」というルールの年代
[<Literal>]
let KinokoAgeGroup = 20

let predictByRule (features: Features) : Faction =
    if features.AgeGroup = KinokoAgeGroup then
        Kinoko
    else
        Takenoko

let accuracy (predictions: 'T list) (labels: 'T list) : float =
    if predictions.Length <> labels.Length then
        failwith "予測と正解ラベルの件数が違います"

    let correct =
        List.zip predictions labels |> List.filter (fun (p, l) -> p = l) |> List.length

    float correct / float labels.Length
