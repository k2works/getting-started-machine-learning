module MachineLearning.Dataset

open System
open System.IO

/// 学習データのディレクトリ。環境変数 ML_DATA_DIR が無ければ apps/data/sukkiri-ml を使う。
/// dotnet test はテストの出力先（bin/Debug/net10.0）で動くので、既定の場所はこのファイルの場所から求める。
let dataDirFrom (getenv: string -> string option) : string =
    getenv "ML_DATA_DIR"
    |> Option.defaultValue (Path.Combine(__SOURCE_DIRECTORY__, "..", "..", "..", "data", "sukkiri-ml"))

/// 環境変数を読んで学習データのディレクトリを返す。
let dataDir () : string =
    dataDirFrom (Environment.GetEnvironmentVariable >> Option.ofObj)
