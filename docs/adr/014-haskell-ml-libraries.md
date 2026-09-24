---
type: ADR
title: "014 Haskell 版のビルド・テスト・静的解析・線形代数・API ライブラリの選定"
description: "Haskell 版のライブラリに cabal・Hspec・fourmolu・hlint・HPC・hmatrix・cassava・statistics・Scotty を採用し、hmatrix のために Nix の環境に openblas を足す方針を決める。機械学習のアルゴリズムは自作が最終実装になる。"
tags: [adr,getting-start-ml,haskell]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-24T00:00:00Z }
---

# 014 Haskell 版のビルド・テスト・静的解析・線形代数・API ライブラリの選定

「機械学習から始めるプログラミング入門」Haskell 版で使うライブラリを決める。

日付: 2026-09-24

## ステータス

2026-09-24 提案されました

## コンテキスト

[執筆計画](../article/getting-start-ml/outline.md) の「第 3 波の執筆計画」では、Haskell を「型の検査が最も厳しく、ライブラリが最も少ない見込み」として最後に書くとした。対比の相手は Rust 版・F# 版（型でエラーを表す）、F# 版・Clojure 版・Elixir 版（純粋関数）、Rust 版・Scala 版（型クラスによる抽象）である。

B70 のステップ 1 で、使い捨ての cabal プロジェクトを `nix develop .#haskell` の中で動かして次を確かめた。

| 確認したこと | 結果 | 確認方法 |
|------------|------|---------|
| Nix 環境 | GHC 9.10.3、cabal 3.16.0.0、stack 3.7.1 | `nix develop .#haskell` |
| GHC 同梱 | `array`・`bytestring`・`containers`・`text`・`deepseq` はあるが **`vector` は無い** | `ghc-pkg list` |
| 整形・静的解析 | **素の環境に `fourmolu` も `hlint` も無く、手元の `/usr/local/bin` が見えていた** | `which` |
| 検査の終了コード | **fourmolu の `--mode check` は違反があると 100**。hlint は 1 | わざと崩したファイル |
| テスト | Hspec 2.11。**テスト名に日本語を使える**。`hspec-discover` で自動収集できる。**テストスイートの `build-depends` はライブラリと別に書く** | 使い捨てのプロジェクト |
| カバレッジ | HPC（`cabal test --enable-coverage`）。GHC 組み込みで追加の依存が要らない | 同上 |
| 線形代数 | **hmatrix 0.20.2 は BLAS/LAPACK を要求し、素の環境では configure で止まる**（`Missing (or bad) C libraries: blas, lapack`）。**しかもビルドが通っただけでは足りなかった。** 最初に足した `openblas` は nixpkgs の既定が `blas64 = true`（ILP64。整数引数が 64 ビット）で、**32 ビット整数で呼ぶ hmatrix と ABI が噛み合わず、2×2 の連立方程式すら解けなかった**（`linearSolve` が `Nothing` を返し `** On entry to DGESV parameter number 1 had an illegal value`、`<\>` は `code -7`、`pinv` は `code -2`）。`strings libopenblas...dylib \| grep USE64BITINT` で ILP64 でビルドされていることを直接確認した。`blas` と `lapack`（`isILP64 = false`）に差し替えて解決 | 使い捨てのプロジェクトで**実際に呼び出して**確認 |
| **C ライブラリの確認は 3 段ある（1/3 ビルド）** | **`configure` が C ライブラリを見つけられるか。** hmatrix は素の環境では `Missing (or bad) C libraries: blas, lapack` で止まる。**ここを通っただけで採用を決めたのが B70 のステップ 1 の誤りだった** | 使い捨てのプロジェクト |
| **C ライブラリの確認は 3 段ある（2/3 呼び出し）** | **整数幅などの ABI が噛み合っているか。** リンクが通っても実行時に壊れうる。`openblas`（ILP64）と hmatrix（32 ビット整数）の組み合わせでは、ビルドは成功するのに 2×2 の連立方程式すら解けなかった。**2×2 の `DGESV` で「行列の次数が不正」と言われたら、数値の問題ではなく ABI の不一致を疑う** | 第 7〜9 章の執筆中に発覚（2 体のエージェントが独立に同じ診断に到達した） |
| **C ライブラリの確認は 3 段ある（3/3 作り直し）** | **環境定義を直しても、cabal の store に残った成果物は作り直されない。** `LIBRARY_PATH` は環境ごとに変わるのに、**cabal はそれをパッケージのハッシュに含めない**ので `store/ghc-9.10.3-*/hmtrx-0.20.2-*` が「configuration changed」と判定されず、ILP64 の BLAS にリンク済みのものが使われ続ける。store の該当エントリを退避してから作り直す必要がある | 同上 |
| CSV | **cassava は BOM を取り除かない**（Go 版・Clojure 版・Elixir 版・PHP 版と同じ）。`decodeByName` で列名つきに読める | BOM つきのファイルで確認 |
| 統計 | statistics 0.16.5.0。純 Haskell で入る | 使い捨てのプロジェクト |
| 整数 | **`Int` は 64 ビットで溢れると折り返す**（`maxBound + 1` が `minBound`）。PHP のように float に化けない | `cabal repl` |
| 乱数 | `java.util.Random` と同じ線形合同法を素直に書ける。**シード 0 の並びが `[60, 48, 29, 47, 15]` で PHP 版と一致した** | 同上 |
| 直列化 | `Data.Binary` の `encode`／`decode` で往復した | 同上 |
| API | Scotty 0.30 と aeson 2.3.2.0 | ビルドの成功を確認 |

### 各章で確かめた結果

| 章 | 確かめたこと |
|----|------------|
| 1 | **cassava も BOM を取り除かない**（Go 版・Clojure 版・Elixir 版・PHP 版と同じ）。**`ByteString` のリテラルに日本語を書くと壊れる**——`OverloadedStrings` も `Data.ByteString.Char8` の `unpack` も 1 文字を 1 バイトとして扱う。**実装で 1 回、テストのデータで 1 回、同じ間違いを踏んだ**（型は `ByteString` で合っていて中身の解釈だけが違うので型検査では捕まらない）。列名と値は `Text` で持ち、境界でだけ符号化・復号する。**BOM は「バイト列か文字列か」で書き方が変わる**（`Text` のリテラルでは U+FEFF の 1 文字＝`\65279`、`\239\187\191` は別物）。**テストスイートの `build-depends` はライブラリと別に書く**（ライブラリに書いた依存はテストから見えない）。`ScopedTypeVariables` はパターン中の型注釈に要る。検査の終了コードは **fourmolu 100**・hlint 1・`cabal test` 1・カバレッジ 3（自作）。**素の Nix 環境に fourmolu も hlint も無く手元の `/usr/local/bin` が見えていた**ので環境の側で版を固定した |
| 2 | **`Int` は Java と同じく溢れると折り返す**ので、線形合同法の式をそのまま書ける（PHP 版で必要だった乗算の分割が要らない）。`nextInt 100` の並び `[60, 48, 29, 47, 15]` と `shuffle [0..9] 0` の `[4, 8, 9, 6, 3, 5, 2, 1, 7, 0]`、訓練データのがく片長さの平均（0.4215384615384616）が Java 版・Elixir 版・PHP 版と一致した。**不変のリストでは添字の書き換えができない**ので Fisher-Yates は `Map` に写してから交換する。乱数の状態を引数と戻り値で受け渡すので、**大域の状態を持たない**（PHP 版の `mt_srand` のような取り扱いが要らない）。欠損値は `Maybe` で表す。**テストの中でも `let Right x = ...` は書けない**（`-Wincomplete-uni-patterns` が `-Werror` で止める）ので、`expectRight` で「失敗したらその場で落ちる」ことを明示する。**`Map` はキーの順で並ぶ**ので列の順は別のリストで持ち回る |
| 3 | 深さごとの正解率・深さ 2 の境界（0.2950／0.6500）・テストの正解率 0.9556 が Java 版・Elixir 版・PHP 版と一致した。**決定木のライブラリが無い**ので自作が最終実装。木を `data Tree = Leaf Text \| Branch Split Tree Tree` で表すと**場合分けの網羅をコンパイラが確かめてくれる**（PHP 版の共用型は網羅性までは強制しない）。**`Data.List.maximumBy` は同値なら後ろを返す**（`minimumBy` と `sortOn` は先を残す）ので、多数決は畳み込みを自作した。**`head` は `-Wx-partial` でコンパイルエラーになる**（GHC 9.8 以降）ので、空の場合をパターンで書くことになり、それがそのまま `Left "正解ラベルがありません"` になった。**`Test.Hspec` の `fit`（focused `it`）が自分の `fit` と衝突する**（`import Test.Hspec hiding (fit)` で解決）。`partition` の述語は `Either` を返せないので、`traverse` で判定してから振り分ける（Elixir の `Enum.split_with/2` が 1 行だったところが 3 行）。**第 1 章の `accuracy` が `[Faction]` に固定されていて再利用できなかった**ので、第 1 章の定義そのものを `(Eq a) => [a] -> [a] -> ...` に広げて 1 つに寄せた（同じ計算を 2 か所に増やさずに済んだ。型が邪魔をしたように見えて、どこを抽象すべきかを教えた形） |
| 9 | 決定係数 8 個（0.6056/0.6950、0.7740/0.8628、0.7953/0.8213、0.6717/0.7947）と天気の集計が Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版と一致。**`statistics` の `sqrt . variance` と浮動小数点数として完全に一致した。** ただし **`variance` は n で割る最尤推定、`stdDev` は n−1 で割る標本標準偏差の平方根**で、**名前から定義を推測すると間違える**（自作は n 側で、scikit-learn・Scholar・Rubix ML と同じ。Tribuo とは違う）。`stdDev` と一致しないことも `shouldNotBe` でテストに残した。**Shift_JIS は `mkTextEncoding` が iconv の名前をそのまま受け取るので追加の依存なしで読める**（`base` だけ）。**取り違えは片方向だけ例外**——Shift_JIS を `UTF-8` で読むと `invalid argument (cannot decode byte sequence...)` の例外、`UTF-8//IGNORE` なら黙って落とし、`UTF-8//TRANSLIT` なら U+FFFD に置換、逆に UTF-8 を `CP932` で読むと**例外にならず文字化けする**。Java（例外）・Clojure（U+FFFD）・Elixir（不正バイナリ）・PHP（どちらも静か）とばらばらだった振る舞いが、**Haskell では 1 つの言語の中で名前で選べる**（既定が例外である点が効く） |
| 10 | 自作モデルの正解率 8 個が Java 版・Elixir 版・PHP 版と一致（`shouldBe Right (0.9142857142857143, 0.9111111111111111)` と浮動小数点数の等価で書けた）。**特徴量の重要度 4 個は Elixir 版と一致し、Java 版・Clojure 版・PHP 版とは 2 つずれた**（がく片幅 0.1265 対 0.1271、花弁長さ 0.2713 対 0.2708）。原因は第 3 章の `gini` がラベルの出現数を `M.elems` でたどること——**`Data.Map` は鍵の順、PHP の連想配列と Clojure の小さなマップは挿入順**なので和を積む順が変わり、同点だった分割に 1 ulp の差が付いて選ばれる分割が変わる。Elixir のマップも鍵の項順なので同じ側に落ちた（**PHP 版が「Elixir 版だけずれた」と書いた箇所の、ちょうど裏返し**）。**型クラスは型をそろえない**——`TreeModel` と `ForestModel` を 1 つのリストに並べるには `ExistentialQuantification` の存在型が要る（PHP の interface と Elixir の関数には無かった手間）。一方「予測する側」は関数そのものなので `type Predictor = ...` の 1 行で済み、アダプターが 1 つも要らなかった。**`sum` を書いてよいか毎回考える**——リストの `sum` は右結合になりうるので、学習の計算は `foldl'` を明示（速度ではなく**数値をほかの言語版と合わせるため**）。**例外にならない数が多い**（`exp 1000` は `Infinity`、`log 0` は `-Infinity`、`0/0` は `NaN`）ので softmax の最大値引きと log への微小値加算が必須。逆に `argmax` の初期値に `-Infinity` を素直に書ける。**テストの中でも部分関数が書けない**（`last`・`head` が `-Wx-partial` で止まる）。**損失は毎回は減らなかった**（学習率 1.0・標準化なしで 49 回中 3 回増える）ので、実装ではなく**テストの主張のほうを直した** |
| 7 | 切片 6114.5955056944080・R² 0.6184・MAE 302.20・RMSE 376.14 が Java 版・Elixir 版・PHP 版と一致（PHP 版とは 12〜13 桁）。**同じ実データを 4 通りで解いて比べた。** 自作のガウス・ジョルダン法（正規方程式）・`<\>`（計画行列をそのまま、DGELSS）・`linearSolveLS`（計画行列、DGELS）の 3 つは 12 桁まで一致し、**`<\>` に正規方程式を渡したものだけが 9 桁に落ちる**（6114.5955055106010）。`XᵀX` を作ると条件数が 2 乗になるという教科書どおりのことが、**同じ関数に別の入れ物で渡しただけ**で出た。**自作も正規方程式を解いているのに 12 桁一致している**のは、部分ピボット選択つきの消去法と DGELSS（特異値の打ち切りあり）の違い。残差平方和は 4 つとも下 2 桁しか違わないので、**どれも最小二乗解の底にはいる**（底が平らなので係数のほうが先にぶれる）。Elixir 版では Scholar の `pinv`（SVD）が最小二乗解に届かず係数が分かれ、PHP 版では Rubix ML が正規方程式で解いて一致した。**Haskell 版はその両方を 1 つのライブラリの中で見せる** |
| 8 | 891 件（生存 342／死亡 549）・訓練 712／テスト 179・深さ 5 の正解率と人数・学習した前処理の値・架空の乗客の予測が Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版と一致。**前処理のライブラリが無いのですべて自作**。学習済みパイプラインは `Data.Binary` で往復できる。**`binary` に `Text` の `Binary` インスタンスが無い**ので、孤児インスタンスを避けて保存の形式を手書きした。その結果**「壊れたファイルから任意のものが作られない」という用心が設計に組み込まれた**（PHP の `allowed_classes`・Elixir の `:safe` にあたるものが要らない）。**`decodeOrFail` は「途中まで読めたら成功」**なので、余り（`rest`）が空かまで見る。**前処理の種類を直和型で数え上げると、節の書き漏らしがコンパイルで止まる**（PHP の interface・Elixir の関数節・Clojure の `defmulti` との対比）。**`Test.Hspec` が `fit`（focused `it`）を輸出している**ので、学習の `fit` と衝突する。**あとから `import Data.Bifunctor` を足しただけで、前に書いた 3 か所が `-Wname-shadowing` で壊れた**（束縛名 `first`）ので `start` に変えた |
| 13 | 寄与率（PC1 0.4110、累積 0.8427）・必要な主成分の数 6・主成分の係数が Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版と一致。**hmatrix の `eigSH`（DSYEV）は 15×15 の実データに耐えた**（**PHP 版で MathPHP の固有ベクトルが落ちた場所を通り抜けた**）。自作の Jacobi 法との差は寄与率 5.55e-17、主成分 1.41e-14。自作とライブラリを `fitWith` で差し替えられるようにして突き合わせた。**`m /= transpose m` で対称性が 1 行で確かめられる**（`trustSym` は対称かを確かめず上三角だけを見るので、**Elixir 版の `Nx.LinAlg.eigh` と同じ落とし穴がある**）。**行列の書き換えを「変更の集合」として書く**——Jacobi 法の回転は、更新する成分の `Map` を作って左結合の `M.union` で重ねる形になった。**`Test.Hspec` の `fit` と主成分分析の `fit` が衝突する**（第 3 章と同じ） |
| 14 | 自作の SSE の 10 行・クラスタごとの件数と平均支出額・クラスタ番号が Java 版・Scala 版・Clojure 版・Elixir 版・PHP 版と一致。**K-means のライブラリが無いので自作が最終実装**。**1 か所だけ食い違った**——`Milk` の平均がちょうど 34708.5 で、**Haskell の `printf "%.0f"` も `round` も偶数側に丸める（銀行家の丸め）**ため 34708 になり、ほかの言語版の 34709 とずれた。`roundHalfUp value = floor (value + 0.5)` を書いてそろえた。**PHP 版がまったく同じ場所で同じ判断をしている**（`sprintf('%.0f')` と `round()` で丸めの向きが違う）。**配列の `===` で収束判定が書ける**のは PHP 版と同じで、Haskell も `==` がそのまま使える。**局所解のテストが最初に落ちた**——2 つのかたまりを k=2 で分けるだけなら K-means は自分で直るので、3 つのかたまりと 3 つの中心が要る（そのこと自体を記事に書いた） |

## 決定

| 用途 | 採用 | バージョン | ライセンス | 初出 |
|------|------|-----------|-----------|------|
| 言語・ビルド | GHC と cabal（`*.cabal`・`cabal.project`） | GHC 9.10・cabal 3.16 | BSD-3-Clause | 第 1 章 |
| テスト | Hspec（`hspec-discover` で収集）。実データのテストはデータが無ければスキップする | 2.11 | MIT | 第 1 章 |
| 整形 | fourmolu（`--mode check`。**違反の終了コードは 100**） | 0.19 | BSD-3-Clause | 第 1 章（記事での解説は第 5 章） |
| 静的解析 | hlint と GHC の `-Wall`（警告をエラーにする） | hlint 3.10 | BSD-3-Clause | 第 1 章（記事での解説は第 5 章） |
| カバレッジ | HPC（`cabal test --enable-coverage`） | GHC 組み込み | BSD-3-Clause | 第 1 章（記事での解説は第 5 章） |
| データの表現 | レコード（`data Person = Person { ... }`）と `Map`・リスト | — | — | 第 1 章 |
| CSV | cassava（BOM は自分で取り除く） | 0.5 | BSD-3-Clause | 第 1 章 |
| 乱数 | **`java.util.Random` と同じ線形合同法を自作する** | — | — | 第 2 章 |
| 線形代数 | **hmatrix（`blas`・`lapack` を環境に足す。`openblas` は ILP64 なので使えない）** | 0.20.2 | BSD-3-Clause | 第 7 章 |
| 統計 | statistics | 0.16 | BSD-2-Clause | 第 9 章 |
| 直列化 | `Data.Binary`（学習済みモデル）、aeson（API の JSON） | binary 0.8・aeson 2.3 | BSD-3-Clause | 第 8 章 |
| API | Scotty | 0.30 | BSD-3-Clause | 第 15 章 |

- **hmatrix を使う。** 素の環境では BLAS/LAPACK が無くて止まるので、`blas` と `lapack` を Nix の環境定義に足し、`LIBRARY_PATH` と `PKG_CONFIG_PATH` を `shellHook` で通す。**`openblas` ではいけない**（nixpkgs の既定が ILP64 で、32 ビット整数で呼ぶ hmatrix と ABI が噛み合わない）。Elixir 版で EXLA（巨大な XLA のバイナリ）を避けたのとは判断が違う。**BLAS/LAPACK は数値計算の標準的な土台で、Nix でも軽く入る**ためである。これにより第 7・12・13 章でライブラリと突き合わせられ、[Elixir 版](../article/getting-start-ml/elixir/index.md) の決定木のように「突き合わせる相手がいない」状態を避けられる
- **機械学習のアルゴリズムは自作が最終実装になる。** Haskell には scikit-learn にあたるものが無い（`hlearn` は保守が止まっている）。決定木・ランダムフォレスト・ロジスティック回帰・K-means・主成分分析はすべて自作する。**突き合わせられるのは線形代数の層（正規方程式・固有値分解）だけ**で、これは Elixir 版（Scholar に決定木が無い）よりさらに狭い
- **乱数は `java.util.Random` と同じ線形合同法を自作する。** `System.Random` はほかの言語版と並びが合わない。**Haskell の `Int` は溢れると折り返す**ので、PHP 版で必要だった乗算の分割が要らず、仕様をそのまま書ける
- **失敗は型で表す。** `Either String a` で失敗を、`Maybe a` で欠損を表す。例外を投げる Ruby 版・Elixir 版・PHP 版とは正反対で、[Rust 版](../article/getting-start-ml/rust/index.md)・[F# 版](../article/getting-start-ml/fsharp/index.md) と同じ流儀になる
- **`-Wall` を警告ではなくエラーにする。** 網羅していないパターンマッチを、テストではなくコンパイルで捕まえる
- **Stack ではなく cabal を使う。** Nix の環境が GHC を与えているので、Stack の「GHC を自分で管理する」働きが要らない
- **各章は「自作してから、突き合わせられる部分だけ hmatrix と突き合わせる」構成にする。** 突き合わせられない章では、そのことを明記して自作の妥当性をテストで示す

### 章ごとのライブラリへの置き換え方針

| 章 | 置き換え | 方針 |
|----|---------|------|
| 3 | **無し（決定木のライブラリが無い）** | 自作のジニ不純度の決定木が最終実装 |
| 4〜6 | 無し | 道具立ての章。cabal・fourmolu・hlint・HPC・Nix の環境定義を扱う |
| 7 | hmatrix（正規方程式を解く） | 自作のガウス・ジョルダン法と hmatrix の `<\>` を突き合わせる |
| 8 | 無し | 前処理も決定木も自作 |
| 9 | statistics（平均・標準偏差） | 自作の標準化と突き合わせる |
| 10 | **無し** | ロジスティック回帰もランダムフォレストも自作 |
| 11 | **無し** | 評価指標・交差検証も自作 |
| 12 | hmatrix（リッジ回帰の正規方程式） | ラッソは座標降下法を自作 |
| 13 | hmatrix（`eigSH` による固有値分解） | 自作の Jacobi 法と突き合わせる。**この章がいちばんライブラリの恩恵を受ける** |
| 14 | 無し | K-means は自作 |
| 15 | Scotty・aeson | 予測 API |

### 検討した代替案

| 代替案 | 採用しなかった理由 |
|--------|------------------|
| 線形代数も自作する（hmatrix を使わない） | 「ライブラリが少ない言語」という位置づけには合うが、**突き合わせる相手が 1 つも無くなる**。第 13 章の 15×15 の固有値分解を自作だけで済ませると、正しさを確かめる方法がテストの中の小さな行列に限られる。hmatrix はビルドできることを確認済みで、Nix でも軽く入る |
| Stack | Nix が GHC を与えているので、Stack の GHC 管理が二重になる |
| hlearn（機械学習） | 保守が止まっており、現在の GHC でビルドできない |
| `System.Random`（乱数） | ほかの言語版と並びが合わない。線形合同法を自作すれば JVM の言語版・Elixir 版・PHP 版と一致する |
| 例外（`throwIO`）で失敗を表す | Haskell で純粋関数が失敗を返すなら `Either` が自然。型でエラーを表す軸が Rust 版・F# 版との対比になる |
| ormolu（整形） | fourmolu は ormolu の派生で、設定を変えられる。どちらでもよいが、設定を書ける側にした |

## 影響

- 良い影響: 型でエラーを表す流儀が、Rust 版・F# 版との三者比較になる。例外で表す Ruby 版・Elixir 版・PHP 版との対比も取れる
- 良い影響: 副作用が `IO` として型に現れるので、「データの読み込みだけが `IO` で、前処理から学習までは純粋関数」という構造を型で示せる
- 良い影響: `-Wall` をエラーにすることで、網羅していないパターンマッチをコンパイルで捕まえられる
- 悪い影響: **hmatrix のために C ライブラリ（BLAS/LAPACK）への依存が環境に増える。** 素の環境では configure で止まるうえ、**整数幅（LP64／ILP64）まで合わせないと実行時に壊れる**。記事の環境構築の節に明記する必要がある
- 悪い影響: **機械学習のアルゴリズムに突き合わせる相手がほとんど無い。** 線形代数の層だけが hmatrix と比べられ、第 3・8・10・11・14 章は自作だけで完結する。これは Elixir 版よりさらに狭い範囲である
- 悪い影響: 整形と静的解析の道具が素の Nix 環境に無く、環境定義に足す必要があった（PHP 版の pcov と同じ形で、第 3 波で 2 回続いた）
