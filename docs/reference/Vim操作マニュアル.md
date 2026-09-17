---
type: Reference
title: "Vim操作マニュアル"
description: "このドキュメントは、本プロジェクトで設定されている .vimrc に基づく操作ガイドです。"
tags: [reference]
status: stable
generated: { by: human:kakimomokuri, at: 2026-04-14T02:45:36Z }
---

# Vim操作マニュアル

このドキュメントは、本プロジェクトで設定されている `.vimrc` に基づく操作ガイドです。

## 基本設定
- **文字コード**: UTF-8 (自動判別: UTF-8, EUC-JP, CP932)
- **改行コード**: UNIX (LF) 優先
- **インデント**: スペース2個 (タブをスペースに展開)
- **検索**: インクリメンタルサーチ、大文字小文字スマート区別、ハイライト表示
- **行番号**: 表示
- **マウス**: 有効

## 基本キーマップ
| キー | 動作 | モード |
|---|---|---|
| `,` | リーダーキー (mapleader) | - |
| `\` | `,` (リーダー) として機能 | - |
| `<C-s>` | ファイル保存 | ノーマル / 挿入 |
| `ESC` `ESC` | 検索ハイライトをオフに切り替え | ノーマル |
| `j` / `k` | 表示行単位で移動 (折り返し対応) | ノーマル |
| `[b` / `]b` | 前 / 次のバッファへ移動 | ノーマル |
| `[B` / `]B` | 最初 / 最後のバッファへ移動 | ノーマル |
| `<C-p>` | 上に移動 | 挿入 |
| `<C-n>` | 下に移動 | 挿入 |
| `<C-b>` | 左に移動 | 挿入 |
| `<C-f>` | 右に移動 | 挿入 |
| `gt` | 次のタブへ移動 | ノーマル |
| `gT` | 前のタブへ移動 | ノーマル |

## ファイル・バッファ操作
| コマンド | 動作 |
|---|---|
| `:tabnew` | 新しいタブを開く |
| `:tabe [ファイル名]` | 指定したファイルを新しいタブで開く |
| `:tabclose` | 現在のタブを閉じる |
| `:e!` | 現在のバッファを破棄してディスクから強制的に再読み込みする |

## ファイルツリー

- `,e` で `NERDTreeToggle`
- `,ef` で現在ファイルのディレクトリを `NERDTreeFind`
- 隠しファイルも表示します

## タグ

- `gutentags` を有効化しています。
- `ctags` は `universal-ctags` を使います。
- `tags` は `./tags;,tags` を読む設定です。
- 保存時や必要時にタグが自動生成されます。

## 開発機能 (Plugins)

### [coc.nvim](https://github.com/neoclide/coc.nvim) (入力補完・LSP)
| キー | 動作 |
|---|---|
| `TAB` | 補完候補の選択 / 次へ |
| `S-TAB` | 補完候補の逆選択 |
| `ENTER` | 補完の確定 |
| `K` | カーソル下のドキュメント表示 |
| `gd` | 定義へジャンプ |
| `gy` | 型定義へジャンプ |
| `gi` | 実装へジャンプ |
| `gr` | 参照元を表示 |
| `[g` / `]g` | 前 / 次の診断メッセージへ移動 |
| `<leader>rn` | シンボルのリネーム |
| `<leader>f` | 選択範囲をフォーマット |
| `<leader>qf` | クイックフィックス実行 |
| `<leader>ac` | カーソル位置でコードアクション実行 |
| `<space>a` | 診断一覧を表示 |
| `<space>o` | アウトライン表示 |
| `<space>c` | コマンド一覧を表示 |

### [vim-fugitive](https://github.com/tpope/vim-fugitive) (Git)
| キー | 動作 |
|---|---|
| `<leader>gs` | `git status` 表示 |
| `<leader>ga` | `git add %` (現在のファイルをアッド) |
| `<leader>gc` | `git commit` |
| `<leader>gp` | `git push` |
| `<leader>gd` | `git diff` |
| `<leader>gb` | `git blame` |

### [vimspector](https://github.com/puremourning/vimspector) (デバッグ)
| キー | 動作 |
|---|---|
| `<leader>dd` | デバッグ開始 |
| `<leader>de` | デバッグ終了 (Reset) |
| `<leader>dc` | 続行 (Continue) |
| `<leader>dt` | ブレークポイント切り替え |
| `<leader>dT` | 全ブレークポイント解除 |
| `<leader>dj` | ステップオーバー |
| `<leader>dl` | ステップイン |
| `<leader>dh` | ステップアウト |
| `<leader>dk` | 再起動 |

### [vim-test](https://github.com/vim-test/vim-test) (テスト実行)
| キー | 動作 |
|---|---|
| `<leader>t` | 最も近いテストを実行 |
| `<leader>T` | 現在のファイルのテストを実行 |
| `<leader>a` | 全テストを実行 (TestSuite) |
| `<leader>l` | 最後に実行したテストを再実行 |
| `<leader>g` | 最後に実行したテストファイルを開く |

### [vim-go](https://github.com/fatih/vim-go) (Go開発)
| キー | 動作 |
|---|---|
| `<leader>b` | ビルド (テストファイルならテスト実行) |
| `<leader>r` | 実行 (go run) |
| `<leader>t` | テスト実行 |
| `<leader>c` | カバレッジ表示切り替え |
| `<C-n>` / `<C-m>` | クイックフィックスの次/前へ移動 |

### [rust.vim](https://github.com/rust-lang/rust.vim) (Rust開発)
- **自動フォーマット**: 保存時に `rustfmt` が自動実行されます。

### [.NET 開発](https://github.com/OmniSharp/omnisharp-vim)
- **OmniSharp**: .NET (C#) 用の開発支援プラグインが導入されています。
- **シンタックスハイライト**: `OrangeT/vim-csharp` により C# 構文がサポートされます。
- **LSP連携**: `nix develop .#dotnet` 環境下で、`coc.nvim` と組み合わせて利用可能です。

### [Ruby 開発](https://github.com/vim-ruby/vim-ruby)
- **基本設定**: インデントがスペース2個に自動設定されます。
- **LSP連携**: `nix develop .#ruby` 環境下で、`solargraph` を使用した補完が利用可能です。

### [PHP 開発](https://github.com/stanangeloff/php.vim)
- **シンタックスハイライト**: `php.vim` により最新の PHP 構文がサポートされます。
- **リファクタリング**: `phpactor` により、クラスの移動やインターフェースの実装などのリファクタリング支援が受けられます。
- **基本設定**: インデントがスペース4個に自動設定されます。

### [Haskell 開発](https://github.com/neovimhaskell/haskell-vim)
- **シンタックスハイライト**: `neovimhaskell/haskell-vim` により Haskell 構文が高度にサポートされます。
- **LSP連携**: `nix develop .#haskell` 環境下で、`haskell-language-server` が利用可能です。`coc-haskell` 等の導入を推奨します。

### [Java 開発](https://github.com/uiiaoo/java-syntax.vim)
- **シンタックスハイライト**: `uiiaoo/java-syntax.vim` により Java 構文のハイライトが強化されています。
- **LSP連携**: `nix develop .#java` 環境下で、`coc-java` などの CoC 拡張機能を導入することで高度な開発が可能です。

### [Clojure 開発](https://github.com/Olical/conjure)
- **Conjure**: Clojure (および Lisp 系言語) 用の高度な対話型開発環境です。バッファ内のコードを即座に REPL で評価できます。
- **Fireplace**: REPL と連携した開発支援を提供します。
- **シンタックスハイライト**: `clojure.vim` により Clojure 構文がサポートされます。

### [Elixir 開発](https://github.com/elixir-editors/vim-elixir)
- **シンタックスハイライト**: `vim-elixir` により Elixir 構文と `mix` フォーマッタとの連携がサポートされます。
- **LSP連携**: `nix develop .#elixir` 環境下で、`elixir-ls` を使用した高度な開発が可能です。

### [Scala 開発](https://github.com/derekwyatt/vim-scala)
- **シンタックスハイライト**: `vim-scala` により Scala 構文がサポートされます。
- **LSP連携**: `nix develop .#scala` 環境下で、`Metals` を使用した高度な開発が可能です。

### [F# 開発](https://github.com/ionide/ionide-vim)
- **Ionide-vim**: F# 用の高度な開発支援プラグインです。
- **シンタックスハイライト**: `ionide/ionide-vim` により F# 構文がサポートされます。
- **LSP連携**: `nix develop .#dotnet` 環境下で、`ionide-vim` による高度な開発支援（補完、定義ジャンプ等）が利用可能です。

### [CtrlP](https://github.com/ctrlpvim/ctrlp.vim) (ファイル検索・セレクタ)
| キー | 動作 |
|---|---|
| `<C-p>` | ファイル検索 (プロジェクト内) |
| `:CtrlPFunky` | 関数一覧から検索 |
| `:CtrlPCommandLine` | コマンド履歴から検索 |

### その他
- **GitHub Copilot**: `github/copilot.vim` が有効化されています。
- **vim-airline**: ステータスライン、タブラインが表示されます。
- **indentLine**: インデントが可視化されます。
- **vim-trailing-whitespace**: 末尾の余分な空白をハイライトします。
