---
type: Article
title: "第 5 章: パッケージ管理と静的解析"
description: "NuGet の中央パッケージ管理と packages.lock.json、手元と CI の両方で解決できる global.json、dotnet format と .editorconfig、.NET アナライザーの水準の選び方、警告をエラーにする設定、coverlet によるカバレッジ計測を、わざと違反を入れて確かめながら学ぶ。"
tags: [article,getting-start-ml,csharp]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T01:40:00Z }
---

# 第 5 章: パッケージ管理と静的解析

## 5.1 はじめに

前の章では、実験を再現するには乱数のシードに加えて、ライブラリ・.NET SDK・実行環境の版を固定する必要があると述べました。この章では、それを担う NuGet と .NET SDK の仕組みと、コードを実行せずに問題を見つける **静的解析**、テストがコードのどこを通ったかを測る **カバレッジ** を整えます。

| 道具 | 役割 | 本章の節 |
|------|------|---------|
| [NuGet](https://learn.microsoft.com/ja-jp/nuget/)（中央パッケージ管理・`packages.lock.json`） | 依存ライブラリの版を管理し、ロックする | 5.2 |
| `global.json`・Nix | .NET SDK の版を固定する | 5.3 |
| [`dotnet format`](https://learn.microsoft.com/ja-jp/dotnet/core/tools/dotnet-format)・`.editorconfig` | コードの整形とコードスタイル | 5.4 |
| [.NET アナライザー](https://learn.microsoft.com/ja-jp/dotnet/fundamentals/code-analysis/overview) | コードの問題（品質・セキュリティなど）の検査 | 5.5 |
| C# コンパイラ（`TreatWarningsAsErrors`） | 型チェックと、警告をエラーにする設定 | 5.6 |
| [coverlet](https://github.com/coverlet-coverage/coverlet)（coverlet.MTP） | テストのカバレッジ計測 | 5.7 |

本章のバージョンは、執筆時点の `global.json`・`Directory.Packages.props` に書かれたものです（.NET SDK 10.0.1xx、Microsoft.ML 5.0.0、xunit.v3 4.0.1、coverlet.MTP 10.0.1）。選定の理由は [ADR 006](../../../adr/006-csharp-ml-libraries.md) を参照してください。

[Python 版の第 5 章](../python/05-package-management-and-static-analysis.md)・[Kotlin 版の第 5 章](../kotlin/05-package-management-and-static-analysis.md)・[TypeScript 版の第 5 章](../typescript/05-package-management-and-static-analysis.md) と比べると、Python の uv・Ruff・mypy・pytest-cov に当たるものを、C# では `dotnet` CLI と NuGet、そして .NET SDK に最初から入っている整形とアナライザーで組み立てます。[F# 版の第 5 章](../fsharp/05-package-management-and-static-analysis.md) とは NuGet と `global.json` の話がそのまま重なりますが、整形と静的解析は道具が違います。C# 版では、次の 2 点に注目してください。

- **道具を増やさずに済む** — F# 版は Fantomas と FSharpLint をローカルツール（`.config/dotnet-tools.json`）として入れましたが、C# では `dotnet format` とアナライザーが SDK に同梱されています。入れるものが無いぶん、**何がどこまで検査しているか** を自分で確かめる必要があります
- **検査が効いていることを確かめる** — 整形・アナライザー・コードスタイルのそれぞれに、わざと違反を入れて、どのコマンドがどのコードで失敗するかを実測します

## 5.2 NuGet によるパッケージ管理

### プロジェクトファイルと PackageReference

.NET のライブラリは、[NuGet](https://www.nuget.org/) のパッケージとして配布されます。どのパッケージを使うかは、プロジェクトファイル（`.csproj`）の `PackageReference` に書きます。本体のプロジェクトの `MachineLearning.csproj` は次のとおりです。

```xml
  <ItemGroup>
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
    <PackageVersion Include="Microsoft.ML" Version="5.0.0" />
    <PackageVersion Include="Microsoft.ML.FastTree" Version="5.0.0" />
    <PackageVersion Include="xunit.v3" Version="4.0.1" />
  </ItemGroup>
</Project>
```

- `ManagePackageVersionsCentrally` を `true` にすると、`PackageReference` に `Version` を書くとエラーになり、版は必ずこのファイルの `PackageVersion` から取られます
- 本体とテストの 2 つのプロジェクトが同じライブラリを使っても、版が食い違うことがありません。Kotlin 版のバージョンカタログ（`libs.versions.toml`）に当たる仕組みです

`Directory.Build.props`（第 1 章）と同じく、このファイルも同じディレクトリとその下のプロジェクトに自動で読み込まれます。C# 版の `Directory.Packages.props` が F# 版より短いのは、FSharp.Core（言語の標準ライブラリ）を自分で参照する必要がないからです。C# の標準ライブラリは .NET のランタイムそのものに含まれていて、NuGet のパッケージではありません。

### 本番依存と開発依存

本番のコードとテストだけが使うものは、プロジェクトを分けることで区別しています。

| プロジェクト | 参照するパッケージ | 判断の基準 |
|------------|------------------|----------|
| `src/MachineLearning` | Microsoft.ML・Microsoft.ML.FastTree | 章のプログラムを動かすのに必要なもの |
| `tests/MachineLearning.Tests` | xunit.v3・coverlet.MTP（と `src/MachineLearning` のプロジェクト） | テストの実行と計測だけに必要なもの |

テストのプロジェクトは `ProjectReference` で本体のプロジェクトを参照するので、本体が使うライブラリはテストからも使えます。逆に、xUnit は本体の依存関係には入りません。F# 版にあった「ローカルツール」の行が C# 版に無いのは、整形も静的解析も SDK 同梱のもので足りるからです（5.4・5.5 節）。

### 依存関係を確かめる

依存関係は `dotnet list package` で確認できます。`--include-transitive` を付けると、依存関係の依存関係（推移的なパッケージ）も表示されます。

```bash
dotnet list src/MachineLearning/MachineLearning.csproj package --include-transitive
```

```text
プロジェクト 'MachineLearning' に次のパッケージ参照が含まれています
   [net10.0]: 
   最上位レベル パッケージ                 要求済み    解決済み 
   > Microsoft.ML               5.0.0   5.0.0
   > Microsoft.ML.FastTree      5.0.0   5.0.0

   推移的なパッケージ                            解決済み  
   > Microsoft.Bcl.AsyncInterfaces      9.0.4 
   > Microsoft.ML.CpuMath               5.0.0 
   > Microsoft.ML.DataView              5.0.0 
   > Newtonsoft.Json                    13.0.3
   > System.CodeDom                     9.0.4 
   > System.Numerics.Tensors            9.0.4 
```

直接の依存関係は 2 つですが、推移的なものを含めると 8 のパッケージを使っています。F# 版が 22 だったのは、FSharp.Core と FSharp.Data・FSharp.Stats の分が加わるためです。

### packages.lock.json で環境を再現する

`Version="5.0.0"` は、NuGet では「5.0.0 **以上**」という範囲の意味です。NuGet は範囲に当てはまる最も低い版を選ぶので、ふつうは 5.0.0 が選ばれますが、推移的なパッケージの版は、そのパッケージを参照する側の指定と、パッケージソースに何があるかで決まります。C# 版では第 1 章から **ロックファイル** を使っています。

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
      "Microsoft.ML": {
        "type": "Direct",
        "requested": "[5.0.0, )",
        "resolved": "5.0.0",
        "contentHash": "clHLX6bjcRpuHDRkXwkm/gSILifT0DOXXzgor4kBhjTkz5G419BCXhZ1xVk/GsMH6koqWwMONjQKzSSCSVZrIQ==",
        "dependencies": {
          "Microsoft.Bcl.AsyncInterfaces": "9.0.4",
          "Microsoft.ML.CpuMath": "5.0.0",
          "Microsoft.ML.DataView": "5.0.0",
          "Newtonsoft.Json": "13.0.3",
          "System.CodeDom": "9.0.4",
          "System.Numerics.Tensors": "9.0.4"
        }
      },
```

- `requested` の `[5.0.0, )` は、「5.0.0 以上、上限なし」という範囲の書き方です。`Version="5.0.0"` が範囲であることが、ここに表れています
- `resolved` が実際に選ばれた版、`contentHash` がパッケージの中身のハッシュです
- `type` は、直接の依存関係なら `Direct`、推移的なものなら `Transitive` です。本体のロックファイルには 6、テストのロックファイルには 31 の `Transitive` が記録されています

`dotnet restore --locked-mode` は、ロックファイルと食い違う依存関係があると、ロックファイルを書き換えずに失敗します。CI ではこのオプションを使い、手元でロックファイルを更新し忘れた変更を見つけます。試しに `Directory.Packages.props` の Microsoft.ML.FastTree を 4.0.2 に書き換えて実行すると、次のように失敗しました（パスは省略しています）。

```bash
dotnet restore --locked-mode
```

```text
MachineLearning.csproj : error NU1004: パッケージ参照 Microsoft.ML.FastTree のバージョンが [5.0.0, ) から [4.0.2, ) に変更されました。パッケージ ロック ファイルはプロジェクトの依存関係と一貫性がないため、ロック モードで復元を実行できません。RestoreLockedMode MSBuild プロパティを無効にするか、明示的な --force-evaluate オプションを渡して復元を実行し、ロック ファイルを更新してください。
MachineLearning.Tests.csproj : error NU1004: CentralTransitive としてマークされたロック ファイルの依存関係の requestedVersion と、中央のパッケージ管理ファイルに指定されたバージョンが一致しません。ロック ファイル バージョン [5.0.0, )、中央のパッケージ管理バージョン [4.0.2, )。
MachineLearning.Tests.csproj : error NU1004: 依存関係が変更されたプロジェクト参照 machinelearning。パッケージ ロック ファイルはプロジェクトの依存関係と一貫性がないため、ロック モードで復元を実行できません。
```

テストのプロジェクトも失敗しているのは、本体を参照しているので、Microsoft.ML.FastTree を推移的に使っているからです。版を変えるときは、`--locked-mode` を付けずに `dotnet restore` を実行してロックファイルを更新し、`Directory.Packages.props` と 2 つの `packages.lock.json` を一緒にコミットします。

### 依存関係の脆弱性を確かめる

TypeScript 版の `npm audit` と同じく、依存関係に既知の脆弱性があるかを確かめられます。

```bash
dotnet list package --vulnerable --include-transitive
```

```text
次のソースが使用されました:
   https://api.nuget.org/v3/index.json

指定されたプロジェクト 'MachineLearning' には、現在のソースが指定された脆弱なパッケージはありません。
指定されたプロジェクト 'MachineLearning.Tests' には、現在のソースが指定された脆弱なパッケージはありません。
```

このコマンドは nuget.org に問い合わせるので、ネットワークが必要です。ロックファイルと違い、CI の必須のステップにはしていません。

## 5.3 .NET SDK の版を固定する

### global.json

`global.json` は、このディレクトリで `dotnet` のコマンドを実行したときに使う .NET SDK の版を決めます（第 1 章）。

```json
{
  "sdk": {
    "version": "10.0.100",
    "rollForward": "latestPatch"
  },
  "test": {
    "runner": "Microsoft.Testing.Platform"
  }
}
```

- `version` は 10.0.100 です。`rollForward` の `latestPatch` は、「10.0.1xx の中で、入っている最も新しい修正版を使う」という意味です。10.0.100 が無くても 10.0.101 があれば使い、10.0.200 や 11.0 には上げません
- 条件に合う SDK が無ければ、`dotnet` のコマンドはビルドを始める前に失敗します。第 4 章の乱数列のテストと合わせて、SDK の版が変わったことに気づけます

F# 版の `global.json` は `version` が 10.0.101 です。C# 版で 10.0.100 にしたのには理由があります。本シリーズを書いている手元の環境に入っている SDK は 10.0.100 で、CI の Nix が用意する SDK は 10.0.101 です。

```text
$ dotnet --list-sdks
8.0.416 [/usr/local/share/dotnet/sdk]
9.0.308 [/usr/local/share/dotnet/sdk]
10.0.100 [/usr/local/share/dotnet/sdk]
```

`version` を 10.0.101 にすると、手元では条件を満たす SDK が無く、`dotnet` のコマンドが動きません。逆に 10.0.100 にしておけば、`latestPatch` の働きで、手元では 10.0.100 が、CI では 10.0.101 が使われます。**「固定する」とは、1 つの版に釘付けにすることとは限りません。** 再現したい範囲（ここでは 10.0.1xx という機能バンドの中）を決めて、その外に出たら失敗させる、というのが `rollForward` の考え方です。

`test.runner` については第 1 章と第 6 章で扱います。この設定を読み取れるのは「作業ディレクトリから上に向かって探した、最も近い `global.json`」なので、`dotnet test` は必ず `apps/csharp` で実行します。

### Nix の .NET SDK

CI と、Nix を使う読者の環境では、`nix develop .#dotnet` で .NET SDK を用意します（`ops/nix/environments/dotnet/shell.nix`）。この環境定義は F# 版と共有しています。

```nix
  buildInputs = baseShell.buildInputs ++ (with packages; [
    dotnet-sdk_10
  ]);
```

CI のログには、この環境が用意した SDK の版が表示されます。

```text
.NET development environment activated
  - .NET SDK: 10.0.101
```

`global.json` の 10.0.100 + `latestPatch` が、この 10.0.101 を受け入れています。

## 5.4 整形とコードスタイル — dotnet format と .editorconfig

### .editorconfig の設定

C# の整形の設定は `.editorconfig` に書きます。Fantomas のような別のツールを入れる必要はなく、`dotnet format`（SDK 同梱）がこのファイルを読みます。

```ini
root = true

[*.{cs,csproj,props,json}]
end_of_line = lf
indent_style = space
indent_size = 4
insert_final_newline = true

[*.cs]
# 整形の崩れは dotnet format と、ビルド時の IDE0055 で検出する
dotnet_diagnostic.IDE0055.severity = warning
csharp_new_line_before_open_brace = all
csharp_indent_case_contents = true
dotnet_sort_system_directives_first = true
```

- `end_of_line = lf` で、どの OS でも改行コードを LF にそろえます。F# 版では Fantomas が Windows で CRLF を書き出す問題を `.editorconfig` で直しましたが、C# 版では最初からこの設定です
- `dotnet_diagnostic.IDE0055.severity = warning` は、`.editorconfig` の整形の規則に反するコードを **ビルドの警告** にする設定です。`Directory.Build.props` の `EnforceCodeStyleInBuild` と組み合わせて、ビルドでも整形を検査します
- `dotnet_sort_system_directives_first` は、`using System...` を先に並べる規則です

### dotnet format の実行

```bash
# 検査する（CI 向け）
dotnet format MachineLearning.sln --verify-no-changes --no-restore

# 整形する
dotnet format MachineLearning.sln --no-restore
```

- `--verify-no-changes` は、書き換えずに、書き換えが必要なら失敗します
- `--no-restore` は、直前に `dotnet restore` を済ませてあるときに復元を省くオプションです

### わざと崩して、検査が効いていることを確かめる

整形の検査は「何も言わない」ことが正常な状態なので、本当に検査しているのかが分かりにくい道具です。わざと崩したファイルを置いて確かめます。

```csharp
namespace MachineLearning;

public static class Violation
{
    public static int Answer( )
    {
          return 42;
    }
}
```

`Answer( )` の括弧の中に余分な空白があり、`return` の字下げが 4 ではなく 10 になっています。

```bash
dotnet format MachineLearning.sln --verify-no-changes --no-restore
echo $?
```

```text
src/MachineLearning/Violation.cs(5,30): error WHITESPACE: 空白の書式設定を修正します。 1 文字を削除します。
src/MachineLearning/Violation.cs(7,9): error WHITESPACE: 空白の書式設定を修正します。 2 文字を削除します。
2
```

- `WHITESPACE` という診断 ID で、何文字削れば直るかまで表示されます
- 終了コードは 2 です。0 以外なので、CI の失敗として検出できます

同じファイルで `dotnet build` も失敗します。

```text
src/MachineLearning/Violation.cs(5,30): error IDE0055: 書式設定を修正 (https://learn.microsoft.com/dotnet/fundamentals/code-analysis/style-rules/ide0055)
src/MachineLearning/Violation.cs(7,9): error IDE0055: 書式設定を修正
```

`IDE0055` は、`.editorconfig` で `warning` にした整形の規則です。本来は警告ですが、`TreatWarningsAsErrors`（5.6 節）でエラーになっています。同じ崩れが、`dotnet format` からは `WHITESPACE`、ビルドからは `IDE0055` という別の名前で報告されるわけです。

### ビルドでは見つからない崩れもある

「ビルドで IDE0055 が出るなら `dotnet format` は要らないのでは」と思うかもしれませんが、そうではありません。第 3 章の `Program.cs` の `using` の並び順を入れ替えて確かめました。

```csharp
using System.Globalization;
using MachineLearning.Chapter01;
using MachineLearning.Chapter02;
using Features = MachineLearning.Chapter02.Features;
using MachineLearning.Dataset;
```

別名の `using`（`using Features = ...`）は、ふつうの `using` より後ろに並べる決まりです。この状態でビルドすると、何も言わずに成功します。

```text
    0 個の警告
    0 エラー
```

`dotnet format` は失敗します。

```text
src/MachineLearning/Chapter03/Program.cs(1,1): error IMPORTS: インポートの順序を修正します。
```

`IDE0055` が見るのは空白の書式で、`using` の並び順（`IMPORTS`）は `dotnet format` だけが検査します。両方を品質チェックに入れておく理由がここにあります。実際、この並び順の崩れは CI で初めて見つかりました。その経緯は第 6 章で扱います。

### solution にプロジェクトが無いと、何も検査しない

`dotnet format` には、もう 1 つ知っておくべき癖があります。対象のソリューションにプロジェクトが登録されていないと、崩れたファイルがあっても **成功します**。

使い捨てのソリューションで確かめました。崩れた `Class1.cs` を含むプロジェクトを作り、ソリューションには登録しないまま検査します。

```bash
dotnet format Demo.sln --verify-no-changes
echo $?
```

```text
0
```

何も表示されず、成功しました。プロジェクトを登録してから同じコマンドを実行すると、今度は指摘されます。

```bash
dotnet sln Demo.sln add app/App.csproj
dotnet format Demo.sln --verify-no-changes
```

```text
app/Class1.cs(2,1): error WHITESPACE: 空白の書式設定を修正します。 \n の挿入
app/Class1.cs(4,30): error WHITESPACE: 空白の書式設定を修正します。 1 文字を削除します。
app/Class1.cs(6,9): error WHITESPACE: 空白の書式設定を修正します。 2 文字を削除します。
```

F# 版の第 5 章では、FSharpLint の設定ファイルが既定を置き換えるせいで、97 のルールがすべて無効のまま「0 warnings」が出ていた、という話がありました。原因は違いますが、現れ方は同じです。**「指摘が無い」は「問題が無い」とは限らず、「検査していない」かもしれません。** 新しくプロジェクトを足したときは、`MachineLearning.sln` に登録されていることを確かめてください。

## 5.5 静的解析 — .NET アナライザー

### dotnet format とアナライザーの役割

`dotnet format` が「どう書くか（書式）」をそろえるのに対して、.NET アナライザーは「何を書いたか（コードの中身）」の問題を探します。

| 観点 | `dotnet format` | .NET アナライザー |
|------|----------------|-----------------|
| 字下げ・空白・改行の位置 | 整形する（`WHITESPACE`） | ビルド時に `IDE0055` として検査する |
| `using` の並び順 | 整形する（`IMPORTS`） | 扱わない |
| 使っていない変数 | 扱わない | コンパイラが `CS0219` で検査する |
| `static` にできるメソッド | 扱わない | `CA1822` で検査する |
| カルチャを指定しない文字列変換 | 扱わない | `CA1304`・`CA1311` で検査する |
| 自動修正 | する | `dotnet format --diagnostics` で一部できる（本リポジトリでは使わない） |

.NET アナライザーは SDK に同梱されていて、`.csproj` に何も書かなくても既定で動きます。設定するのは **水準** だけです。

### 水準を選ぶ

```xml
    <!-- .NET アナライザーの水準。All は公開メソッドの引数の検査まで求めるので Recommended にする（ADR 006） -->
    <AnalysisMode>Recommended</AnalysisMode>
```

`AnalysisMode` は、どこまでのルールを有効にするかの水準です。`Recommended` を選んだ理由を確かめるため、`All` にしてビルドしてみます。

```bash
dotnet build --no-restore --no-incremental -p:AnalysisMode=All
```

同じ場所への重複を除くと、26 件の指摘がありました。

| ルール | 件数 | 内容 |
|-------|-----|------|
| CA1515 | 22 | アセンブリの外から参照されていない型は `internal` にできる |
| CA1062 | 2 | 公開メソッドの引数が null でないことを検証していない |
| CA1819 | 1 | プロパティが配列を返している |
| CA5394 | 1 | `System.Random` は暗号論的に安全な乱数生成器ではない |

```text
DataDir.cs(6,21): error CA1515: アプリケーションの API は通常、アセンブリの外部から参照されていないため、型を内部にできます
DecisionTree.cs(29,41): error CA1062: 外部から参照できるメソッド 'DecisionTree DecisionTree.Fit(IReadOnlyList<Features> x, IReadOnlyList<string> t)' において、検証パラメーター 'x' が使用前に非 null です。適切であれば、引数が null の場合に ArgumentNullException をスローします。
MlNetAdapter.cs(10,20): error CA1819: プロパティは配列を返すことはできません
Preprocessing.cs(55,21): error CA5394: Random は安全でない乱数ジェネレーターです。セキュリティにランダム度が必要な場合に、暗号化によってセキュリティで保護された乱数ジェネレーターを使用します。
```

指摘ごとに、この本のコードにとって妥当かを考えます。

- **CA1515**（22 件）は、「このアセンブリを外部のライブラリとして公開しないなら `internal` にせよ」という指摘です。本シリーズでは、記事で説明する型を `public` で見せたいので、すべて `internal` に変えるのは本末転倒です
- **CA1062** は、公開メソッドの引数を使う前に null を確かめよ、という指摘です。`Directory.Build.props` で `Nullable` を有効にしているので、C# のコンパイラは null になりうる引数を型で警告します。実際、本リポジトリのコードは要所で `ArgumentNullException.ThrowIfNull` を呼んでおり、残った 2 件は null 許容参照型で守られている場所です。すべての公開メソッドの先頭に検査を並べると、記事に載せるコードが本題から離れます
- **CA5394** は、`System.Random` が暗号用途に使えないという指摘です。正しい指摘ですが、ここでの用途はデータの分割で、再現できることこそが目的です（第 4 章）。暗号論的な乱数に変えたら、シードによる再現ができなくなります
- **CA1819** は、ML.NET に渡すためのプロパティが `float[]` を返している箇所です。ML.NET の入力の型は配列を要求するので、変えられません

4 つのうち 3 つは「この本の文脈では受け入れない」という判断になります。`All` にして 26 件を個別に抑え込むより、`Recommended` にして、出てきた指摘は必ず直す、という運用を選びました。抑制のコメントが散らばったコードは、どの指摘が生きているのかが分からなくなり、結局 5.4 節の「検査していない」状態に近づきます。

`Recommended` が何を見ているかは、ADR 006 を書いたときに使い捨てのプロジェクトで確かめました。`CA1304`・`CA1311`（文字列の大文字小文字の変換でカルチャを指定していない）や `CA1822`（インスタンスの状態を使わないメソッドは `static` にできる）が、その水準で報告されるルールです。第 1〜3 章の実装では、こうした指摘を抑制せずに直しています。第 1 章の `Dispose` に `GC.SuppressFinalize(this)` が入っているのも、`CA1816` の指摘に従った結果です。

### わざと違反を入れて確かめる

アナライザーも、効いていることを確かめておきます。次のファイルを `src/MachineLearning/` に置きます。

```csharp
namespace MachineLearning;

public class Violation
{
    public int Answer()
    {
        var unused = 0;
        return 42;
    }
}
```

- `unused` は宣言しただけで使っていません
- `Answer` はインスタンスの状態を使っていないので、`static` にできます

```bash
dotnet build --no-restore
```

```text
src/MachineLearning/Violation.cs(7,13): error CS0219: 変数 'unused' は割り当てられていますが、その値は使用されていません
src/MachineLearning/Violation.cs(5,16): error CA1822: メンバー 'Answer' はインスタンス データにアクセスしないため、static にマークできます (https://learn.microsoft.com/dotnet/fundamentals/code-analysis/quality-rules/ca1822)
    0 個の警告
    2 エラー
```

`CS0219` はコンパイラの警告、`CA1822` はアナライザーの警告です。どちらも `TreatWarningsAsErrors` でエラーになり、「0 個の警告、2 エラー」と表示されています。警告が 0 件なのは、警告がすべてエラーに変わったからです。

これで、4 つの検査が効いていることを実測できました。

| わざとの違反 | 検出するコマンド | 診断 ID |
|------------|---------------|--------|
| 使っていない変数 | `dotnet build` | CS0219（コンパイラ） |
| `static` にできるメソッド | `dotnet build` | CA1822（アナライザー） |
| 崩れた空白 | `dotnet build` | IDE0055（コードスタイル） |
| 崩れた空白・`using` の並び順 | `dotnet format --verify-no-changes` | WHITESPACE・IMPORTS |

確かめ終わったら、`Violation.cs` は削除します。

## 5.6 型チェックと警告 — C# コンパイラ

Python 版では、型ヒントを mypy で検査しました。C# は静的型付けの言語なので、型チェックはコンパイラが行い、型が合わないコードはそもそもビルドできません。`dotnet test` は、テストの前に必ずビルドするので、型チェックとテストが 1 つのコマンドで続けて行われます（第 1 章）。

`Directory.Build.props` の設定のうち、検査に関わるものは次の 3 つです。

```xml
    <Nullable>enable</Nullable>
    <!-- コンパイラとアナライザーの警告をエラーにする -->
    <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
    <!-- .editorconfig のコードスタイル（IDE のルール）をビルドでも検査する -->
    <EnforceCodeStyleInBuild>true</EnforceCodeStyleInBuild>
```

- `Nullable` は **null 許容参照型** を有効にします。`string` は null になりえない型、`string?` は null になりうる型として区別され、null かもしれない値をそのまま使うと警告になります。`DataDir.From` の `Func<string, string?>`（第 4 章）は、環境変数が無ければ null を返すことを型で表しています
- `TreatWarningsAsErrors` は警告をエラーにします。警告のままだとビルドの出力に埋もれて見落としがちですが、エラーにしておけば、直すまでテストも実行できません
- `EnforceCodeStyleInBuild` は、`.editorconfig` のコードスタイルの規則（`IDE****`）をビルドでも検査させます。これが無いと、`IDE0055` は IDE の中だけの指摘になり、CI では効きません

第 1 部と本章で、この設定がエラーにした警告は次のとおりです。

| 警告 | 内容 | 章 |
|------|------|----|
| CA1816 | `Dispose` を書いたのに `GC.SuppressFinalize` を呼んでいない | 第 1 章 |
| CS8509 | `switch` 式が、あり得る場合を網羅していない | 第 3 章 |
| CA1822 | インスタンスの状態を使わないメソッドが `static` でない | 本章（わざとの違反） |
| CS0219 | 使っていない変数 | 本章（わざとの違反） |
| IDE0055 | 整形の崩れ | 本章（わざとの違反） |

CS8509 は、第 3 章の決定木の `switch` 式で実際に効いています。`Tree` は `Leaf` と `Node` の 2 つしか派生を持ちませんが、C# には F# の判別共用体のような「これで全部」を型で表す仕組みがありません（第 3 章）。`_ =>` の場合を消してビルドすると、次のエラーになります。

```text
src/MachineLearning/Chapter03/DecisionTrees.cs(55,14): error CS8509: この switch 式では入力型の可能な値がすべて扱われるわけではありません (すべてが網羅されているわけではありません)。たとえば、パターン '_' がカバーされていません。
```

F# 版では、同じ場面でパターンマッチの漏れが FS0025 として報告されていました。どちらの言語でも、網羅していないことはコンパイラが知らせてくれます。

一方で、型で守られない部分もあります。

- `Row.Number("がく片長さ")` の列名は、ただの文字列です。打ち間違いはコンパイラには分かりません
- ML.NET の `IDataView` は、列の名前と型を実行時に決めます。第 3 章では、特徴量ベクトルの長さを `SchemaDefinition` で実行時に指定しました

こうした場所は、テストで守る必要があります。

## 5.7 コードカバレッジ — coverlet

テストが本体のコードのどこを実行したかを、coverlet で計測します。第 1 章から、テストのプロジェクトに coverlet.MTP（Microsoft.Testing.Platform 向けの coverlet）を入れてあり、`dotnet test` にオプションを付けるだけで計測できます。

```bash
dotnet test --coverlet --coverlet-include '[MachineLearning]*' --coverlet-output-format cobertura --results-directory TestResults
```

- `--coverlet` で計測を有効にし、結果を Cobertura 形式（XML）で `TestResults/` に書き出します
- `--coverlet-include '[MachineLearning]*'` は、計測の対象を `MachineLearning` のアセンブリ（本体）だけに絞ります。`[アセンブリ名]型名` の形で、`*` は任意の文字列です

学習データを配置していない環境での結果です（XML の先頭の要素から抜き出しています）。

```text
line-rate="0.6666" branch-rate="0.8125"
lines-covered="240" lines-valid="360"
branches-covered="65" branches-valid="80"
```

行のカバレッジは 66.66%（360 行中 240 行）です。データが無いので実データのテスト 14 件がスキップされ、各章の `Program.Run` などが実行されないぶん、値が下がっています。実行されなかった行の多くは各章の `Program.Run` と、コマンドラインの入り口の `Program.Main` です。

Python 版・Kotlin 版と同じく、カバレッジの数値を比べるときは、データのある環境で計測したものかを確認してください。同じコードでも、データの有無だけで値が変わります。

### 計測の対象を絞る理由

F# 版の第 5 章では、`--coverlet-include` を付けずに実行すると、テストは成功するのにカバレッジのファイルが 1 つも書き出されない、という現象が報告されています。coverlet が既定でテストの読み込むアセンブリまで計測しようとして失敗し、その失敗がテストの結果に表れないためです。エラーのメッセージも出ないので、カバレッジのファイルができていることを確かめるまで気づけません。C# 版も同じ coverlet を同じオプションで使うので、対象を本体のアセンブリに絞っています。

本リポジトリでは、カバレッジの下限を設けていません。CI には学習データを置けないので、CI で計測したカバレッジは手元より必ず低くなり、下限を決めても手元と CI で意味が変わってしまうためです。その代わり、CI の最後でカバレッジのファイルから値を読み出すようにして、ファイルができていない（＝計測に失敗した）ことを検出できるようにしています（第 6 章）。

<details>
<summary>この章の完成コード（Directory.Build.props）</summary>

```xml
<Project>
  <PropertyGroup>
    <TargetFramework>net10.0</TargetFramework>
    <Nullable>enable</Nullable>
    <ImplicitUsings>enable</ImplicitUsings>
    <LangVersion>latest</LangVersion>
    <InvariantGlobalization>true</InvariantGlobalization>
    <!-- コンパイラとアナライザーの警告をエラーにする -->
    <TreatWarningsAsErrors>true</TreatWarningsAsErrors>
    <!-- .editorconfig のコードスタイル（IDE のルール）をビルドでも検査する -->
    <EnforceCodeStyleInBuild>true</EnforceCodeStyleInBuild>
    <!-- .NET アナライザーの水準。All は公開メソッドの引数の検査まで求めるので Recommended にする（ADR 006） -->
    <AnalysisMode>Recommended</AnalysisMode>
    <!-- 依存関係の依存関係まで正確な版を packages.lock.json に記録する -->
    <RestorePackagesWithLockFile>true</RestorePackagesWithLockFile>
    <!-- dotnet test から Microsoft.Testing.Platform のテストを実行できるようにする -->
    <TestingPlatformDotnetTestSupport>true</TestingPlatformDotnetTestSupport>
  </PropertyGroup>
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
    <PackageVersion Include="Microsoft.ML" Version="5.0.0" />
    <PackageVersion Include="Microsoft.ML.FastTree" Version="5.0.0" />
    <PackageVersion Include="xunit.v3" Version="4.0.1" />
  </ItemGroup>
</Project>
```

</details>

## 5.8 まとめ

この章では、再現できるビルドと、実行せずに問題を見つける仕組みを整えました。

1. **NuGet と中央パッケージ管理** — 依存ライブラリの版を `Directory.Packages.props` にまとめ、プロジェクトを分けて本番とテストの依存を区別する。`Version="5.0.0"` は「5.0.0 以上」の範囲なので、`packages.lock.json` で推移的なパッケージまで版とハッシュを固定し、CI では `--locked-mode` で食い違いを検出する
2. **SDK の版** — `global.json` の `version` と `rollForward` で、再現したい範囲（10.0.1xx）を決める。手元の 10.0.100 と CI の 10.0.101 の両方を受け入れるため、`version` は低いほうに合わせる
3. **dotnet format と .editorconfig** — 整形の設定は `.editorconfig` に書き、SDK 同梱の `dotnet format` で検査する。空白は `IDE0055` としてビルドでも見つかるが、`using` の並び順は `dotnet format` でしか見つからない
4. **.NET アナライザー** — 水準は `AnalysisMode` で選ぶ。`All` の 26 件は多くがこの本の文脈に合わないので、`Recommended` にして出た指摘は必ず直す運用にする。抑制を並べるより、水準を選ぶほうが検査の意味を保てる
5. **検査が効いていることを確かめる** — わざと違反を入れて、CS0219・CA1822・IDE0055・WHITESPACE・IMPORTS のそれぞれがどのコマンドで失敗するかを実測する。プロジェクトを登録していないソリューションでは `dotnet format` が何も検査せずに成功するので、「指摘が無い」ことを疑う
6. **カバレッジ** — 本体のアセンブリに絞って計測し、結果のファイルができていることを確かめる。データの有無で値が変わるので、下限は設けない

次の章では、これらのコマンドの役割を `dotnet` CLI と Gulp で分け、GitHub Actions で自動実行します。
