---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "NuGet の中央パッケージ管理と packages.lock.json、global.json・Nix・ローカルツールによる版の固定、Fantomas による整形、何も検査していなかった FSharpLint の設定の直し方、警告をエラーにする設定、coverlet によるカバレッジ計測を学ぶ。"
tags: [article,getting-start-ml,fsharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-19T05:23:41Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、実験を再現するには乱数のシードに加えて、ライブラリ・.NET SDK・実行環境の版を固定する必要があると述べました。この章では、それを担う NuGet と .NET SDK の仕組みと、コードを実行せずに問題を見つける **静的解析**、テストがコードのどこを通ったかを測る **カバレッジ** を整えます。

| 道具 | 役割 | 本章の節 |
|------|------|---------|
| [NuGet](https://learn.microsoft.com/ja-jp/nuget/)（中央パッケージ管理・`packages.lock.json`） | 依存ライブラリの版を管理し、ロックする | 5.2 |
| `global.json`・Nix・ローカルツール | .NET SDK と開発ツールの版を固定する | 5.3 |
| [Fantomas](https://fsprojects.github.io/fantomas/) | コードの整形 | 5.4 |
| [FSharpLint](https://fsprojects.github.io/FSharpLint/) | コードの問題（命名・再帰など）の検査 | 5.5 |
| F# コンパイラ（`TreatWarningsAsErrors`） | 型チェックと、警告をエラーにする設定 | 5.6 |
| [coverlet](https://github.com/coverlet-coverage/coverlet)（coverlet.MTP） | テストのカバレッジ計測 | 5.7 |

本章のバージョンは、執筆時点の `global.json`・`Directory.Packages.props`・`.config/dotnet-tools.json` に書かれたものです（.NET SDK 10.0.101、FSharp.Core 10.0.101、Fantomas 8.0.0、FSharpLint 0.27.0、coverlet.MTP 10.0.1）。選定の理由は [ADR 004](../../../adr/004-fsharp-ml-libraries.md) を参照してください。

[Python 版の第 5 章](../python/05-package-management-and-static-analysis.md)・[Kotlin 版の第 5 章](../kotlin/05-package-management-and-static-analysis.md)・[TypeScript 版の第 5 章](../typescript/05-package-management-and-static-analysis.md) と比べると、Python の uv・Ruff・mypy・pytest-cov に当たるものを、F# では `dotnet` CLI と NuGet、ローカルツールの Fantomas・FSharpLint で組み立てます。F# 版では、次の 2 点に注目してください。

- **設定が既定に重なるか、置き換えるか** — FSharpLint の設定ファイルは既定の設定を置き換えます。これを知らずに書いた設定のせいで、第 1 章からずっと静的解析が何も検査していなかったことが、この章で分かりました
- **警告をエラーにする** — F# のコンパイラは、パターンマッチの漏れや末尾再帰になっていない再帰を警告で知らせます。`TreatWarningsAsErrors` で、それを見落とせないようにします

## 5.2 NuGet によるパッケージ管理

### プロジェクトファイルと PackageReference

.NET のライブラリは、[NuGet](https://www.nuget.org/) のパッケージとして配布されます。どのパッケージを使うかは、プロジェクトファイル（`.fsproj`）の `PackageReference` に書きます。本体のプロジェクトの `MachineLearning.fsproj` は次のとおりです。

```xml
  <ItemGroup>
    <PackageReference Include="FSharp.Data" />
    <PackageReference Include="FSharp.Stats" />
    <PackageReference Include="Microsoft.ML" />
    <PackageReference Include="Microsoft.ML.FastTree" />
  </ItemGroup>
```

`PackageReference` には版（`Version`）を書いていません。版は、次の `Directory.Packages.props` にまとめて書いています。

### 中央パッケージ管理

`Directory.Packages.props` は、ソリューションのすべてのプロジェクトが使うパッケージの版を 1 か所にまとめるファイルです（**中央パッケージ管理**、Central Package Management）。

```xml
<Project>
  <PropertyGroup>
    <!-- 依存ライブラリの版をこのファイルにまとめる（中央パッケージ管理） -->
    <ManagePackageVersionsCentrally>true</ManagePackageVersionsCentrally>
  </PropertyGroup>
  <ItemGroup>
    <PackageVersion Include="coverlet.MTP" Version="10.0.1" />
    <!-- .NET SDK 10.0.101 に同梱の版 -->
    <PackageVersion Include="FSharp.Core" Version="10.0.101" />
    <PackageVersion Include="FSharp.Data" Version="8.2.0" />
    <PackageVersion Include="FSharp.Stats" Version="0.6.0" />
    <PackageVersion Include="Microsoft.ML" Version="5.0.0" />
    <PackageVersion Include="Microsoft.ML.FastTree" Version="5.0.0" />
    <PackageVersion Include="xunit.v3" Version="4.0.1" />
  </ItemGroup>
</Project>
```

- `ManagePackageVersionsCentrally` を `true` にすると、`PackageReference` に `Version` を書くとエラーになり、版は必ずこのファイルの `PackageVersion` から取られます
- 本体とテストの 2 つのプロジェクトが同じライブラリ（たとえば FSharp.Core）を使っても、版が食い違うことがありません。Kotlin 版のバージョンカタログ（`libs.versions.toml`）に当たる仕組みです

`Directory.Build.props`（第 1 章）と同じく、このファイルも同じディレクトリとその下のプロジェクトに自動で読み込まれます。

### 本番依存と開発依存

本番のコードとテストだけが使うものは、プロジェクトを分けることで区別しています。

| プロジェクト | 参照するパッケージ | 判断の基準 |
|------------|------------------|----------|
| `src/MachineLearning` | FSharp.Data・FSharp.Stats・ML.NET | 章のプログラムを動かすのに必要なもの |
| `tests/MachineLearning.Tests` | xunit.v3・coverlet.MTP（と `src/MachineLearning` のプロジェクト） | テストの実行と計測だけに必要なもの |
| 両方（`Directory.Build.props`） | FSharp.Core | F# のプログラムすべてに必要な標準ライブラリ |
| ローカルツール（`.config/dotnet-tools.json`） | Fantomas・FSharpLint | プログラムとは無関係で、開発者が実行するもの（5.3 節） |

テストのプロジェクトは `ProjectReference` で本体のプロジェクトを参照するので、本体が使うライブラリはテストからも使えます。逆に、xUnit は本体の依存関係には入りません。

### ライブラリを追加する

ライブラリを追加するときは、次の 2 か所に 1 行ずつ書き、`dotnet restore` を実行します。第 7 章以降で使う FSharp.Stats を追加したコミット（`35cf8f8b`）の変更です。

```diff
+    <PackageVersion Include="FSharp.Stats" Version="0.6.0" />
```

```diff
+    <PackageReference Include="FSharp.Stats" />
```

1 つ目が `Directory.Packages.props`、2 つ目が `MachineLearning.fsproj` です。このコミットでは、あわせて 2 つの `packages.lock.json` に合わせて 89 行が加わりました。FSharp.Stats が依存する FSharpAux などのパッケージも記録されるためです。

依存関係は `dotnet list package` で確認できます。`--include-transitive` を付けると、依存関係の依存関係（推移的なパッケージ）も表示されます。

```bash
dotnet list src/MachineLearning package --include-transitive
```

```text
プロジェクト 'MachineLearning' に次のパッケージ参照が含まれています
   [net10.0]: 
   最上位レベル パッケージ                 要求済み       解決済み    
   > FSharp.Core                10.0.101   10.0.101
   > FSharp.Data                8.2.0      8.2.0   
   > FSharp.Stats               0.6.0      0.6.0   
   > Microsoft.ML               5.0.0      5.0.0   
   > Microsoft.ML.FastTree      5.0.0      5.0.0   

   推移的なパッケージ                            解決済み  
   > FSharp.Data.Csv.Core               8.2.0 
   > FSharp.Data.Html.Core              8.2.0 
   > FSharp.Data.Http                   8.2.0 
   > FSharp.Data.Json.Core              8.2.0 
   > FSharp.Data.Runtime.Utilities      8.2.0 
   > FSharp.Data.WorldBank.Core         8.2.0 
   > FSharp.Data.Xml.Core               8.2.0 
   > FSharpAux                          2.0.0 
   > FSharpAux.Core                     2.0.0 
   > FSharpAux.IO                       2.0.0 
   > Microsoft.Bcl.AsyncInterfaces      9.0.4 
   > Microsoft.ML.CpuMath               5.0.0 
   > Microsoft.ML.DataView              5.0.0 
   > Newtonsoft.Json                    13.0.3
   > OptimizedPriorityQueue             5.1.0 
   > System.CodeDom                     9.0.4 
   > System.Numerics.Tensors            9.0.4 
```

直接の依存関係は 5 つですが、推移的なものを含めると 22 のパッケージを使っています。

### packages.lock.json で環境を再現する

`Version="8.2.0"` は、NuGet では「8.2.0 **以上**」という範囲の意味です。NuGet は範囲に当てはまる最も低い版を選ぶので、ふつうは 8.2.0 が選ばれますが、推移的なパッケージの版は、そのパッケージを参照する側の指定と、パッケージソースに何があるかで決まります。Kotlin 版では依存関係に範囲指定が無いことを確かめてロックファイルを使いませんでしたが、F# 版では第 1 章から **ロックファイル** を使っています。

```xml
    <!-- 依存関係の依存関係まで正確な版を packages.lock.json に記録する -->
    <RestorePackagesWithLockFile>true</RestorePackagesWithLockFile>
```

`Directory.Build.props` のこの設定で、`dotnet restore` はプロジェクトごとに `packages.lock.json` を書き出します。

```json
{
  "version": 2,
  "dependencies": {
    "net10.0": {
      "FSharp.Core": {
        "type": "Direct",
        "requested": "[10.0.101, )",
        "resolved": "10.0.101",
        "contentHash": "mrS3J7cLtvXM3u3CmiaFq8oyBdlevzZY1VAAAbFk1gUDQtDEjUOybN5XpQglx5wAgJ0wLdDl6OSSfS7ZDFNcpA=="
      },
      "FSharp.Data": {
        "type": "Direct",
        "requested": "[8.2.0, )",
        "resolved": "8.2.0",
        "contentHash": "U9ymmHeGVsRtKu46UNq6dS4wl3Y/0TpzHx5i0mNhzsX/eY0FTblFq1zrOLcuuRNJAbsznwZcnKzOQKkyHxp8fA==",
        "dependencies": {
          "FSharp.Core": "6.0.1",
          "FSharp.Data.Csv.Core": "8.2.0",
```

- `requested` の `[8.2.0, )` は、「8.2.0 以上、上限なし」という範囲の書き方です。`Version="8.2.0"` が範囲であることが、ここに表れています
- `resolved` が実際に選ばれた版、`contentHash` がパッケージの中身のハッシュです
- `type` は、直接の依存関係なら `Direct`、推移的なものなら `Transitive` です。本体のロックファイルには 17、テストのロックファイルには 42 の `Transitive` が記録されています

`dotnet restore --locked-mode` は、ロックファイルと食い違う依存関係があると、ロックファイルを書き換えずに失敗します。CI ではこのオプションを使い、手元でロックファイルを更新し忘れた変更を見つけます。試しに `Directory.Packages.props` の FSharp.Stats を 0.5.0 に書き換えて実行すると、次のように失敗しました（パスは省略しています）。

```bash
dotnet restore --locked-mode
```

```text
MachineLearning.fsproj : error NU1004: パッケージ参照 FSharp.Stats のバージョンが [0.6.0, ) から [0.5.0, ) に変更されました。パッケージ ロック ファイルはプロジェクトの依存関係と一貫性がないため、ロック モードで復元を実行できません。RestoreLockedMode MSBuild プロパティを無効にするか、明示的な --force-evaluate オプションを渡して復元を実行し、ロック ファイルを更新してください。
MachineLearning.Tests.fsproj : error NU1004: CentralTransitive としてマークされたロック ファイルの依存関係の requestedVersion と、中央のパッケージ管理ファイルに指定されたバージョンが一致しません。ロック ファイル バージョン [0.6.0, )、中央のパッケージ管理バージョン [0.5.0, )。
```

テストのプロジェクトも失敗しているのは、本体を参照しているので、FSharp.Stats を推移的に使っているからです。版を変えるときは、`--locked-mode` を付けずに `dotnet restore` を実行してロックファイルを更新し、`Directory.Packages.props` と 2 つの `packages.lock.json` を一緒にコミットします。

### 依存関係の脆弱性を確かめる

TypeScript 版の `npm audit` と同じく、依存関係に既知の脆弱性があるかを確かめられます。

```bash
dotnet list package --vulnerable --include-transitive
```

```text
指定されたプロジェクト 'MachineLearning' には、現在のソースが指定された脆弱なパッケージはありません。
指定されたプロジェクト 'MachineLearning.Tests' には、現在のソースが指定された脆弱なパッケージはありません。
```

## 5.3 .NET SDK とツールの版を固定する

### global.json

`global.json` は、このディレクトリで `dotnet` のコマンドを実行したときに使う .NET SDK の版を決めます（第 1 章）。

```json
{
  "sdk": {
    "version": "10.0.101",
    "rollForward": "latestPatch"
  },
  "test": {
    "runner": "Microsoft.Testing.Platform"
  }
}
```

- `version` は 10.0.101 です。`rollForward` の `latestPatch` は、「10.0.1xx の中で、入っている最も新しい修正版を使う」という意味です。10.0.101 が無くても 10.0.102 があれば使い、10.0.200 や 11.0 には上げません
- 条件に合う SDK が無ければ、`dotnet` のコマンドはビルドを始める前に失敗します。第 4 章の乱数列のテストと合わせて、SDK の版が変わったことに気づけます

### Nix の .NET SDK

CI と、Nix を使う読者の環境では、`nix develop .#dotnet` で .NET SDK を用意します（`ops/nix/environments/dotnet/shell.nix`）。

```nix
  buildInputs = baseShell.buildInputs ++ (with packages; [
    dotnet-sdk_10
  ]);
```

最初、この環境は `dotnet-sdk` を使っていましたが、本リポジトリの `flake.lock` が固定する nixpkgs では、`dotnet-sdk` は .NET 8 を指していました。`global.json` の 10.0.101 を満たさないので、F# 版のプロジェクトを作る前に `dotnet-sdk_10` に変えています（`03a99d1a`）。CI のログにも、`.NET SDK: 10.0.101` と表示されます。

### ローカルツール

整形の Fantomas と静的解析の FSharpLint は、NuGet で配布される **.NET ツール** です。`.config/dotnet-tools.json` に版を書いておくと、そのディレクトリの中だけで使える **ローカルツール** になります。

```json
{
  "version": 1,
  "isRoot": true,
  "tools": {
    "fantomas": {
      "version": "8.0.0",
      "commands": [
        "fantomas"
      ],
      "rollForward": false
    },
    "dotnet-fsharplint": {
      "version": "0.27.0",
      "commands": [
        "dotnet-fsharplint"
      ],
      "rollForward": false
    }
  }
}
```

```bash
dotnet tool restore
dotnet tool list
```

```text
パッケージ ID               バージョン       コマンド                   マニフェスト
fantomas               8.0.0       fantomas               ...\apps\dotnet\.config\dotnet-tools.json
dotnet-fsharplint      0.27.0      dotnet-fsharplint      ...\apps\dotnet\.config\dotnet-tools.json
```

- `dotnet tool restore` は、ファイルに書かれた版のツールを入れます。マシン全体に入れる（グローバルツール）必要が無いので、読者や CI の環境でも同じ版を使えます
- ツールは `dotnet fantomas`・`dotnet fsharplint` のように、`dotnet` に続けて実行します

### FSharp.Core を nuget.org から取る

第 1 章で `Directory.Build.props` に書いた次の設定は、CI で初めて見つかった問題への対処です。

```xml
    <!-- .NET SDK 同梱の FSharp.Core（library-packs）は nuget.org のものとハッシュが違い、
         packages.lock.json の検証が環境によって失敗するので、nuget.org からだけ取る -->
    <DisableImplicitLibraryPacksFolder>true</DisableImplicitLibraryPacksFolder>
```

.NET SDK には、FSharp.Core のパッケージが同梱されています（手元の Windows では `C:\Program Files\dotnet\sdk\10.0.101\FSharp\library-packs\FSharp.Core.10.0.101.nupkg`）。SDK は、このフォルダーを暗黙のパッケージソースに加えます。手元で作った `packages.lock.json` と、CI（Nix の .NET SDK）での `dotnet restore --locked-mode` が食い違い、最初の CI の実行が次のエラーで失敗しました。

```text
MachineLearning.fsproj : error NU1403: Package content hash validation failed for FSharp.Core.10.0.101. The package is different than the last restore.
```

同じ 10.0.101 という版でも、Nix の SDK に同梱されたパッケージは、ロックファイルに記録したハッシュと中身が一致しなかったのです。ロックファイルは、版だけでなく中身のハッシュまで確かめるので、この違いに気づけました。`DisableImplicitLibraryPacksFolder` で SDK 同梱のフォルダーを使わないようにし、FSharp.Core もほかのパッケージと同じく nuget.org からだけ取るようにすると、CI が成功しました（`63bd9ffb`）。

もう 1 つの `DisableImplicitFSharpCoreReference` と、FSharp.Core を明示して参照する設定の経緯は、第 1 章の 1.5 節で紹介しました。

## 5.4 整形 — Fantomas

### Fantomas の設定

コードの整形には Fantomas を使います。設定は `.editorconfig` に書き、ここでは改行コードだけを既定から変えています。

```ini
root = true

# Fantomas の設定。改行コードを .gitattributes と同じ LF にそろえる
[*.{fs,fsi,fsx}]
end_of_line = lf
```

Windows では、Fantomas は既定で CRLF で書き出します。`.gitattributes` の LF（第 4 章）と食い違うと、手元で整形するたびに全行が変わってしまうので、LF にそろえました（`889e9e3a`）。

それ以外は Fantomas の既定の書式です。既定では 1 行の上限（`max_line_length`）が 120 文字で、それとは別に、`let` の右辺が 80 文字（`fsharp_max_value_binding_width`）を超えると改行します。

### Fantomas の実行

```bash
# 検査する（CI 向け）
dotnet fantomas --check .

# 整形する
dotnet fantomas .
```

第 6 章で作る Notebook の道具（`tools/notebooks.fsx`）を書いた直後に検査すると、整形が必要なファイルとして見つかりました。

```text
! .\tools\notebooks.fsx needs formatting.

1 file needs formatting, 20 already formatted. Run dotnet fantomas . to format it.
```

このときの終了コードは 99 でした。整形が必要なファイルがあると 0 以外で終わるので、CI の失敗として検出できます。整形すると、たとえば次の行が 2 行に分かれました。

```diff
-let notebookDir = Path.GetFullPath(Path.Combine(__SOURCE_DIRECTORY__, "..", "notebooks"))
+let notebookDir =
+    Path.GetFullPath(Path.Combine(__SOURCE_DIRECTORY__, "..", "notebooks"))
```

1 行は 120 文字に収まっていますが、右辺が 80 文字を超えているためです。

`dotnet fantomas .` は、プロジェクトに含まれないスクリプト（`.fsx`）も含めて、ディレクトリの下の F# のファイルをすべて整形します。

## 5.5 静的解析 — FSharpLint

### Fantomas と FSharpLint の役割

Fantomas が「どう書くか（書式）」を揃えるのに対して、FSharpLint は「何を書いたか（コードの中身）」の問題を探します。

| 観点 | Fantomas | FSharpLint |
|------|----------|------------|
| 字下げ・空白・改行の位置 | 整形する | 扱わない（書式のルールは既定で無効） |
| 命名規則（PascalCase・camelCase） | 扱わない | 検査する |
| 再帰関数と末尾呼び出し | 扱わない | `[<TailCall>]` の付け忘れを検査する |
| 冗長な書き方（不要な `new`、`fun x -> x` を `id` にできる、など） | 扱わない | 検査する（`fun x -> x` などはヒントとして） |
| 自動修正 | できる | 本リポジトリの使い方ではしない |

### 「0 warnings」を疑う

第 1 章から、FSharpLint の設定ファイル `fsharplint.json` は次の 3 行でした。

```json
{
    "ignoreFiles": ["**/obj/**"]
}
```

`obj/` には、xUnit が自動で生成するエントリポイントのファイルがあり、FSharpLint が命名規則の警告を出すので、除外したつもりの設定です。実行すると警告は 0 件でした。

```bash
dotnet fsharplint lint MachineLearning.sln
```

```text
Running FSharpLint with 97 rules (0 enabled, 97 disabled)...
...
========== Summary: 0 warnings ==========
```

出力の 1 行目をよく見ると、**97 のルールのうち、有効なものが 0** です。警告が 0 件なのは、コードに問題が無いからではなく、何も検査していなかったからでした。

FSharpLint のドキュメントには、次のように書かれています。

> At this moment in time the configuration requires every rule to be added to your file, rather than a typical approach where you would override just the rules you want to change from their defaults.
>
> — [FSharpLint: Rule Configuration](https://fsprojects.github.io/FSharpLint/how-tos/rule-configuration.html)

設定ファイルは既定の設定に **重なる** のではなく、既定の設定を **置き換えます**。書かなかったルールは無効になります。Kotlin 版の detekt は `buildUponDefaultConfig = true` で既定に重ねられましたが、FSharpLint 0.27.0 にはその仕組みがありません。

設定ファイルを置かずに実行すると、既定の設定が使われます。

```text
Running FSharpLint with 98 rules (42 enabled, 56 disabled)...
```

```text
The 'fit' function has a "rec" keyword, but no [<TailCall>] attribute. Consider adding [<TailCall>] attribute to the function and <WarningsAsErrors>FS3569</WarningsAsErrors> property to project file (but only on .NET 8 and higher).
Error on line 64 starting at column 8
let rec fit (maxDepth: int option) (x: Map<string, float> list) (t: 'L list) : Tree<'L> =
        ^
See https://fsprojects.github.io/FSharpLint/how-tos/rules/FL0085.html
```

```text
Consider changing `_nqyFv2P81` to PascalCase.
Error on line 3 starting at column 36
module internal Xunit.AutoGenerated._nqyFv2P81
                                    ^
See https://fsprojects.github.io/FSharpLint/how-tos/rules/FL0042.html
```

```text
========== Summary: 5 warnings ==========
```

有効なルールは 42 で、警告は 5 件でした。第 3 章の `fit`・`predictOne`・`formatTree` への FL0085 が 3 件と、`obj/` の自動生成ファイルへの FL0042（命名規則）が 2 件です。このとき、終了コードは 127 でした。

### 既定の設定を写して、除外だけを加える

既定の設定は、FSharpLint のパッケージの中の `fsharplint.json` です（ローカルツールを入れた環境では `~/.nuget/packages/dotnet-fsharplint/0.27.0/tools/net8.0/any/fsharplint.json`）。これを `apps/fsharp/fsharplint.json` に写し、先頭の `ignoreFiles` に `obj/` を加えました。

```json
{
    "ignoreFiles": [
        "assemblyinfo.*",
        "obj/"
    ],
    "global": {
        "numIndentationSpaces": 4
    },
```

- ドキュメントによると、`ignoreFiles` の規則は `/` で終わればそのディレクトリの中のすべてのファイルに当てはまります
- 既定の設定を丸ごと写したので、ファイルは 494 行あります。FSharpLint の版を上げるときは、新しい版の既定の設定を写し直し、`ignoreFiles` だけを加え直します

```text
Running FSharpLint with 98 rules (42 enabled, 56 disabled)...
========== Summary: 3 warnings ==========
```

`obj/` への警告が消え、第 3 章のコードへの 3 件が残りました。

### 指摘ごとに対応を決める

FL0085 は、`let rec` の再帰関数に `[<TailCall>]` 属性が無いことを指摘するルールです。`[<TailCall>]` を付けると、F# のコンパイラは、その関数の再帰呼び出しが **末尾呼び出し**（呼び出しの結果をそのまま返し、呼び出しの後に何もしない形）になっているかを検査します。末尾呼び出しの再帰はループに変換されるので、どれだけ深く再帰してもスタックを使い果たしません。

3 つの関数すべてに `[<TailCall>]` を付けてビルドすると、2 つがエラーになりました。

```text
DecisionTree.fs(76,13): error FS3569: メンバーまたは関数 'fit' には 'TailCallAttribute' 属性がありますが、末尾の再帰的な方法では使用されていません。
DecisionTree.fs(102,19): error FS3569: メンバーまたは関数 'formatTree' には 'TailCallAttribute' 属性がありますが、末尾の再帰的な方法では使用されていません。
DecisionTree.fs(104,19): error FS3569: メンバーまたは関数 'formatTree' には 'TailCallAttribute' 属性がありますが、末尾の再帰的な方法では使用されていません。
```

FS3569 は本来は警告ですが、`TreatWarningsAsErrors`（5.6 節）でエラーになっています。コンパイラの判定に沿って、対応を分けました。

**predictOne（属性を付ける）**: 葉に着くまで、左右どちらかの部分木で自分を呼び、その結果をそのまま返します。末尾呼び出しなので、`[<TailCall>]` を付けたままにしました。今後この関数を書き換えて末尾呼び出しでなくなれば、コンパイラが知らせてくれます。

```fsharp
[<TailCall>]
let rec predictOne (tree: Tree<'L>) (row: Map<string, float>) : 'L =
    match tree with
    | Leaf label -> label
    | Node(split, left, right) ->
        if row[split.Feature] <= split.Threshold then
            predictOne left row
        else
            predictOne right row
```

**fit と formatTree（理由を書いて抑える）**: `fit` は左右の部分木を作ってから `Node` にまとめ、`formatTree` は左右の部分木の行を `@` で連結します。どちらも再帰呼び出しの後に仕事が残るので、末尾呼び出しにはなりません。再帰の深さは木の深さまでで、iris のデータでは深さ 5 で訓練データに完全に当てはまりました（第 3 章）。末尾呼び出しに書き換えると読みにくくなるだけなので、この 2 か所は、理由をコメントに書いたうえで、行単位で指摘を抑えました。

```fsharp
/// 深さの上限（None なら制限なし）まで、分け方を選んで再帰的に木を作る
// 左右の部分木を作ってから Node にまとめるので末尾再帰ではない。再帰の深さは木の深さまで
// fsharplint:disable-next-line EnsureTailCallDiagnosticsInRecursiveFunctions
let rec fit (maxDepth: int option) (x: Map<string, float> list) (t: 'L list) : Tree<'L> =
```

- `// fsharplint:disable-next-line <ルール名>` は、次の 1 行にだけ、指定したルールを当てはめないというコメントです
- 設定ファイルでルールそのものを無効にしなかったのは、これから書く再帰関数（第 10 章のランダムフォレストなど）にも、同じ判断を 1 つずつ求めたいからです

```text
Running FSharpLint with 98 rules (42 enabled, 56 disabled)...
========== Summary: 0 warnings ==========
```

今度の「0 warnings」は、42 のルールで検査した結果です。第 3 章の記事の完成コードも、この変更に合わせて更新しました。

## 5.6 型チェックと警告 — F# コンパイラ

Python 版では、型ヒントを mypy で検査しました。F# は静的型付けの言語なので、型チェックはコンパイラが行い、型が合わないコードはそもそもビルドできません。`dotnet test` は、テストの前に必ずビルドするので、型チェックとテストが 1 つのコマンドで続けて行われます（第 1 章）。

F# のコンパイラは、型の誤りのほかに、実行時の問題につながりうる書き方を **警告** で知らせます。本リポジトリでは `Directory.Build.props` で警告をエラーにしています。

```xml
    <!-- コンパイラの警告（パターンマッチの網羅漏れなど）をエラーにする -->
    <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
```

第 1 部と本章で、この設定がエラーにした警告は次のとおりです。

| 警告 | 内容 | 章 |
|------|------|----|
| FS0025 | パターンマッチが不完全（判別共用体の `Node` の場合が無い） | 第 3 章 |
| FS3569 | `[<TailCall>]` を付けた関数が末尾呼び出しになっていない | 本章 |

警告のままだと、ビルドの出力に埋もれて見落としがちです。エラーにしておけば、直すまでテストも実行できません。

一方で、型で守られない部分もあります。

- `Map<string, float>` の `row[feature]` は、キーが無ければ実行時に例外（`KeyNotFoundException`）になります。特徴量の名前の打ち間違いは、コンパイラには分かりません
- ML.NET の `IDataView` は、列の名前と型を実行時に決めます。第 3 章では、特徴量ベクトルの長さを `SchemaDefinition` で実行時に指定しました

こうした場所は、テストで守る必要があります。

## 5.7 コードカバレッジ — coverlet

テストが本体のコードのどこを実行したかを、coverlet で計測します。第 1 章から、テストのプロジェクトに coverlet.MTP（Microsoft.Testing.Platform 向けの coverlet）を入れてあり、`dotnet test` にオプションを付けるだけで計測できます。

```bash
dotnet test --coverlet --coverlet-include '[MachineLearning]*' --coverlet-output-format cobertura --results-directory TestResults
```

- `--coverlet` で計測を有効にし、結果を Cobertura 形式（XML）で `TestResults/` に書き出します
- `--coverlet-include '[MachineLearning]*'` は、計測の対象を `MachineLearning` のアセンブリ（本体）だけに絞ります。`[アセンブリ名]型名` の形で、`*` は任意の文字列です

学習データを配置した環境での結果です（XML の先頭の要素から抜き出しています）。

```text
line-rate="0.925" branch-rate="0.7352"
lines-covered="222" lines-valid="240"
```

行のカバレッジは 92.5%（240 行中 222 行）です。実行されなかった行の多くは `Program.fs`（コマンドラインの入り口）で、テストは各章の `Main.run` を直接呼ぶので、`Program.fs` は実行されません。

学習データが無い環境での結果です。

```bash
ML_DATA_DIR=/nonexistent dotnet test --coverlet --coverlet-include '[MachineLearning]*' --coverlet-output-format cobertura --results-directory TestResults
```

```text
line-rate="0.725" branch-rate="0.5489999999999999"
lines-covered="174" lines-valid="240"
```

データが無いと、実データのテスト 7 件がスキップされ、各章の `Main.run` などが実行されないので、カバレッジが 72.5% に下がります。Python 版・Kotlin 版と同じく、カバレッジの数値を比べるときは、データのある環境で計測したものかを確認してください。

### 計測の対象を絞る理由

`--coverlet-include` を付けずに実行すると、テストはすべて成功し、終了コードも 0 でしたが、`TestResults/` にはカバレッジのファイルが 1 つも書き出されませんでした。エラーのメッセージもありません。実行時間は、絞ったとき（約 3 秒）の 5 倍の約 15 秒でした。

ADR 004 を書いたときに診断ログで調べたところ、coverlet は既定で FSharp.Core などテストが読み込むアセンブリも計測しようとし、Windows では実行中のファイルを書き換えられずに、計測全体が失敗していました。失敗がテストの結果に表れないので、カバレッジのファイルができていることを確かめるまで気づけません。これも「失敗しない」ことを疑うべき場面です。

本リポジトリでは、カバレッジの下限を設けていません。CI には学習データを置けないので、CI で計測したカバレッジは手元より必ず低くなり、下限を決めても手元と CI で意味が変わってしまうためです。

<details>
<summary>この章の完成コード（Directory.Build.props）</summary>

```xml
<Project>
  <PropertyGroup>
    <TargetFramework>net10.0</TargetFramework>
    <!-- コンパイラの警告（パターンマッチの網羅漏れなど）をエラーにする -->
    <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
    <!-- 依存関係の依存関係まで正確な版を packages.lock.json に記録する -->
    <RestorePackagesWithLockFile>true</RestorePackagesWithLockFile>
    <!-- FSharp.Core の版は Directory.Packages.props で固定する -->
    <DisableImplicitFSharpCoreReference>true</DisableImplicitFSharpCoreReference>
    <!-- .NET SDK 同梱の FSharp.Core（library-packs）は nuget.org のものとハッシュが違い、
         packages.lock.json の検証が環境によって失敗するので、nuget.org からだけ取る -->
    <DisableImplicitLibraryPacksFolder>true</DisableImplicitLibraryPacksFolder>
  </PropertyGroup>
  <ItemGroup>
    <PackageReference Include="FSharp.Core" />
  </ItemGroup>
</Project>
```

</details>

<details>
<summary>この章の完成コード（Directory.Packages.props）</summary>

```xml
<Project>
  <PropertyGroup>
    <!-- 依存ライブラリの版をこのファイルにまとめる（中央パッケージ管理） -->
    <ManagePackageVersionsCentrally>true</ManagePackageVersionsCentrally>
  </PropertyGroup>
  <ItemGroup>
    <PackageVersion Include="coverlet.MTP" Version="10.0.1" />
    <!-- .NET SDK 10.0.101 に同梱の版 -->
    <PackageVersion Include="FSharp.Core" Version="10.0.101" />
    <PackageVersion Include="FSharp.Data" Version="8.2.0" />
    <PackageVersion Include="FSharp.Stats" Version="0.6.0" />
    <PackageVersion Include="Microsoft.ML" Version="5.0.0" />
    <PackageVersion Include="Microsoft.ML.FastTree" Version="5.0.0" />
    <PackageVersion Include="xunit.v3" Version="4.0.1" />
  </ItemGroup>
</Project>
```

</details>

## 5.8 まとめ

この章では、再現できるビルドと、実行せずに問題を見つける仕組みを整えました。

1. **NuGet と中央パッケージ管理** — 依存ライブラリの版を `Directory.Packages.props` にまとめ、プロジェクトを分けて本番とテストの依存を区別する。`Version="8.2.0"` は「8.2.0 以上」の範囲なので、`packages.lock.json` で推移的なパッケージまで版とハッシュを固定し、CI では `--locked-mode` で食い違いを検出する
2. **SDK とツールの版** — .NET SDK を `global.json` と Nix の `dotnet-sdk_10` で、Fantomas・FSharpLint をローカルツールで固定する。SDK 同梱の FSharp.Core はハッシュが違ったので、nuget.org からだけ取る
3. **Fantomas** — 書式を検査・整形する。改行コードは `.editorconfig` で LF にそろえる
4. **FSharpLint** — 設定ファイルは既定を置き換えるので、既定の設定を写して除外だけを加える。指摘は 1 つずつ理由を考え、コンパイラに検査させるか、理由を書いて抑えるかを決める。「0 warnings」は、何を検査した結果なのかを確かめる
5. **警告とカバレッジ** — `TreatWarningsAsErrors` で警告を見落とせないようにする。カバレッジは本体のアセンブリに絞って計測し、結果のファイルができていることを確かめる

次の章では、これらのコマンドの役割を `dotnet` CLI と Gulp で分け、Notebook の出力セルを検査する仕組みを加えて、GitHub Actions で自動実行します。
