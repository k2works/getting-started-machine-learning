module MachineLearning.Chapter09.Dummies

/// ダミー変数にするカテゴリ。重複を除いて辞書順に並べ、先頭のカテゴリを除く
let dummyCategories (values: string list) : string list =
    values |> List.distinct |> List.sort |> List.tail

/// カテゴリの値を、「列名_カテゴリ」という名前の 0 と 1 の列にする。カテゴリに無い値はすべて 0 にする
let encodeDummies (column: string) (categories: string list) (value: string) : Map<string, float> =
    categories
    |> List.map (fun category -> $"{column}_{category}", (if value = category then 1.0 else 0.0))
    |> Map.ofList
