module MachineLearning.Chapter02.Random

open System

/// シードを使って Fisher–Yates のシャッフルで並べ替えたリストを返す。
/// 同じシードなら同じ順になる。元のリストは変更されない（F# のリストは不変）。
let shuffle (seed: int) (items: 'T list) : 'T list =
    let random = Random seed
    let array = List.toArray items

    for i in array.Length - 1 .. -1 .. 1 do
        let j = random.Next(i + 1)
        let tmp = array[i]
        array[i] <- array[j]
        array[j] <- tmp

    List.ofArray array
