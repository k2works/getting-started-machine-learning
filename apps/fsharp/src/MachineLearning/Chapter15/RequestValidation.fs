/// プレゼンテーション層。HTTP の本文（JSON）を検証して、ドメイン層の型にする
module MachineLearning.Chapter15.RequestValidation

open System.Text.Json
open MachineLearning.Chapter15.Domain

/// 検証の結果。正しければ値を、不正なら理由の一覧を持つ
type Validation<'T> = Result<'T, string list>

/// 項目ごとの検証をまとめる計算式。and! でつないだ項目は、どれかが不正でも残りを検証し、理由をすべて集める
type ValidationBuilder() =
    member _.BindReturn(result: Validation<'T>, f: 'T -> 'U) : Validation<'U> = Result.map f result

    member _.MergeSources(left: Validation<'T>, right: Validation<'U>) : Validation<'T * 'U> =
        match left, right with
        | Ok l, Ok r -> Ok(l, r)
        | Error l, Error r -> Error(l @ r)
        | Error errors, Ok _
        | Ok _, Error errors -> Error errors

let validation = ValidationBuilder()

/// 項目の値。無い項目と null は None
let private tryField (name: string) (json: JsonElement) : JsonElement option =
    match json.TryGetProperty name with
    | true, value when value.ValueKind <> JsonValueKind.Null -> Some value
    | _ -> None

let private required (name: string) (value: JsonElement option) : Validation<JsonElement> =
    value |> Option.map Ok |> Option.defaultValue (Error [ $"{name} を指定してください" ])

let private number (name: string) (value: JsonElement) : Validation<float> =
    if value.ValueKind = JsonValueKind.Number then
        Ok(value.GetDouble())
    else
        Error [ $"{name} は数値にしてください" ]

let private integer (name: string) (value: JsonElement) : Validation<int> =
    match value.ValueKind, value.TryGetInt32() with
    | JsonValueKind.Number, (true, n) -> Ok n
    | _ -> Error [ $"{name} は整数にしてください" ]

/// inline にすると、呼び出す場所ごとに float・int など実際の型で展開される（GenericZero はその型の 0）
let inline private notNegative (name: string) (value: 'N) : Validation<'N> =
    if value >= LanguagePrimitives.GenericZero then
        Ok value
    else
        Error [ $"{name} は 0 以上にしてください" ]

/// JSON の値（数値か文字列）を、決められた値の一覧のどれかに対応づける
let private oneOf (name: string) (choices: (string * 'T) list) (value: JsonElement) : Validation<'T> =
    let raw =
        match value.ValueKind with
        | JsonValueKind.String -> value.GetString()
        | _ -> value.GetRawText()

    match choices |> List.tryFind (fun (key, _) -> key = raw) with
    | Some(_, choice) -> Ok choice
    | None ->
        let keys = choices |> List.map fst |> String.concat "、"
        Error [ $"{name} は {keys} のどれかにしてください" ]

let private nonNegativeNumber name json =
    tryField name json
    |> required name
    |> Result.bind (number name)
    |> Result.bind (notNegative name)

let private nonNegativeInteger name json =
    tryField name json
    |> required name
    |> Result.bind (integer name)
    |> Result.bind (notNegative name)

/// 本文を JSON として読み、オブジェクトなら検証の関数に渡す
let private parseWith (validate: JsonElement -> Validation<'T>) (body: string) : Validation<'T> =
    try
        use document = JsonDocument.Parse body

        if document.RootElement.ValueKind = JsonValueKind.Object then
            validate document.RootElement
        else
            Error [ "JSON のオブジェクトにしてください" ]
    with :? JsonException ->
        Error [ "JSON の形式が正しくありません" ]

let parseMovie (body: string) : Validation<Movie> =
    body
    |> parseWith (fun json ->
        validation {
            let! sns1 = nonNegativeNumber "sns1" json
            and! sns2 = nonNegativeNumber "sns2" json
            and! actor = nonNegativeNumber "actor" json

            and! original =
                tryField "original" json
                |> required "original"
                |> Result.bind (oneOf "original" [ "0", false; "1", true ])

            return
                {
                    Sns1 = sns1
                    Sns2 = sns2
                    Actor = actor
                    Original = original
                }
        })

/// 省略できる項目。無ければ None、あれば検証する
let private optional (name: string) (validate: JsonElement -> Validation<'T>) (json: JsonElement) =
    match tryField name json with
    | None -> Ok None
    | Some value -> validate value |> Result.map Some

let parsePassenger (body: string) : Validation<Passenger> =
    body
    |> parseWith (fun json ->
        validation {
            let! pclass =
                tryField "pclass" json
                |> required "pclass"
                |> Result.bind (oneOf "pclass" [ "1", First; "2", Second; "3", Third ])

            and! sex =
                tryField "sex" json
                |> required "sex"
                |> Result.bind (oneOf "sex" [ "male", Male; "female", Female ])

            and! age =
                json
                |> optional "age" (fun value -> number "age" value |> Result.bind (notNegative "age"))

            and! sibSp = nonNegativeInteger "sib_sp" json
            and! parch = nonNegativeInteger "parch" json
            and! fare = nonNegativeNumber "fare" json

            and! embarked =
                json
                |> optional "embarked" (oneOf "embarked" [ "C", Cherbourg; "Q", Queenstown; "S", Southampton ])

            return
                {
                    Pclass = pclass
                    Sex = sex
                    Age = age
                    SibSp = sibSp
                    Parch = parch
                    Fare = fare
                    Embarked = embarked
                }
        })
