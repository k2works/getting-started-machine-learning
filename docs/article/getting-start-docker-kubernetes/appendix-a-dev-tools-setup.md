---
type: Article
title: "付録 A 開発ツールのセットアップ"
description: "本編で扱った Docker や Kubernetes を快適に学習・開発するには、いくつかの周辺ツールをそろえておくと便利です。"
tags: [article, getting-start-docker-kubernetes]
status: stable
generated: { by: human:kakimomokuri, at: 2026-06-26T00:29:42Z }
---

# 付録 A 開発ツールのセットアップ

## はじめに

本編で扱った Docker や Kubernetes を快適に学習・開発するには、いくつかの周辺ツールをそろえておくと便利です。この付録では、書籍『Docker/Kubernetes 実践コンテナ開発入門（第 2 版）』の付録に沿って、次の 4 つの開発ツールのセットアップ方法を解説します。

- A.1 WSL2 — Windows 上で Linux 環境を動かす基盤
- A.2 asdf — 複数言語・ツールのバージョンを統一管理する
- A.3 kind — Docker の中に Kubernetes クラスタを立ち上げる
- A.4 Rancher Desktop — Docker Desktop の代替となるローカル開発環境

それぞれのツールは「何を解決するのか」という観点から整理します。ツールを導入する目的が明確であれば、自分の環境に合わせて取捨選択できるからです。

なお、本付録には 2 種類の情報が含まれます。1 つは本リポジトリ（`getting-started-docker-kubernetes`）に実在する設定ファイルやスクリプトを引用した内容で、もう 1 つは各ツールの一般的な利用手順です。前者には出典を明示し、後者は「一般的な手順」と区別して記載します。コマンドの実行結果は環境やバージョンによって変わるため、確実でないものは「例」と明記するか省略しています。

---

## A.1 WSL2

### WSL2 は何を解決するか

Docker や Kubernetes のツールチェーンは Linux を前提に作られているものが多く、Windows でそのまま動かそうとすると差異に悩まされがちです。WSL2（Windows Subsystem for Linux 2）は、Windows 上で本物の Linux カーネルを動かす仕組みで、この「Windows と Linux の隔たり」を解決します。

WSL の初代（WSL1）が Linux システムコールを翻訳する方式だったのに対し、WSL2 は軽量な仮想マシン上で実際の Linux カーネルを動作させます。これにより、ファイルシステムの互換性やネットワーク、Docker のようなコンテナ技術との相性が大きく改善されました。

### WSL2 の導入

最近の Windows 10/11 では、管理者権限の PowerShell またはコマンドプロンプトから次のコマンド 1 つで WSL2 と既定のディストリビューション（通常は Ubuntu）をまとめて導入できます。

```bash
wsl --install
```

実行後は再起動を求められることがあります。再起動後、初回起動時に Linux ユーザー名とパスワードの設定を求められます。

インストール済みの状態を確認したい場合は、次のコマンドでバージョンや稼働状態を確認できます。

```bash
# WSL のバージョンと状態を確認する
wsl --status

# インストール済みディストリビューションを一覧表示する
wsl --list --verbose
```

`wsl --list --verbose` の `VERSION` 列が `2` になっていれば WSL2 で動作しています。

### ディストリビューションの導入

既定以外のディストリビューションを使いたい場合は、導入可能な一覧を確認してから指定してインストールします。

```bash
# インストール可能なディストリビューションの一覧を表示する
wsl --list --online

# ディストリビューションを指定してインストールする（例: Ubuntu-22.04）
wsl --install -d Ubuntu-22.04
```

既存のディストリビューションを WSL2 に変換したい場合は、バージョンを明示的に設定します。

```bash
# 既定のバージョンを WSL2 にする
wsl --set-default-version 2

# 個別のディストリビューションを WSL2 に変換する（例）
wsl --set-version Ubuntu-22.04 2
```

### Docker との連携

WSL2 は、コンテナ開発環境との連携において中心的な役割を果たします。代表的な連携方法は次の 2 つです。

1. Docker Desktop の WSL2 バックエンドを利用する方法。Docker Desktop の設定で WSL2 ベースのエンジンを有効化すると、Windows 側にインストールした Docker を WSL2 のディストリビューションから透過的に利用できます。
2. WSL2 のディストリビューション内に直接 Docker Engine をインストールする方法。Docker Desktop を使わず、Linux と同じ手順で Docker をセットアップします。後述の Rancher Desktop も WSL2 を基盤として利用します。

開発時のポイントとして、ソースコードは Windows 側（`/mnt/c/...`）ではなく WSL2 のファイルシステム上（例: `~/projects`）に置くと、ファイル I/O が高速になり、ファイル変更検知（ホットリロード）も安定します。

> 注: 上記コマンドは Windows 環境で一般的に有効なものです。利用可能なディストリビューションや既定の挙動は Windows のバージョン・更新状況によって異なります。

---

## A.2 asdf

### asdf は何を解決するか

開発を進めると、Go や Node.js といった言語ランタイムに加えて、`kustomize`、`helm`、`tilt` などの CLI ツールを多数使うことになります。これらは「プロジェクトごとに必要なバージョンが違う」「チームメンバー間でバージョンがそろわず、手元では動くのに他の環境で動かない」といった問題を起こしがちです。

asdf は、複数の言語・ツールのバージョンを 1 つの仕組みでまとめて管理するバージョンマネージャです。プロジェクトのルートに置いた `.tool-versions` ファイルでツールとバージョンを宣言しておくことで、チーム全体で同じバージョンを再現できます。

### `.tool-versions` でバージョンを固定する

本リポジトリのサンプルアプリ `taskapp` では、`apps/taskapp/.tool-versions`（出典: `getting-started-docker-kubernetes` リポジトリ）で次のようにツールとバージョンを固定しています。

```text
golang 1.21.6
tilt 0.33.10
kustomize 5.3.0
kubectx 0.9.5
helm 3.13.3
```

このファイルが示すとおり、`taskapp` の開発には Go 本体に加えて、Kubernetes 開発で頻出する次のツール群が必要です。

- `golang` — アプリケーション本体の実装に使う言語ランタイム
- `tilt` — Kubernetes 上での開発を高速化する開発ツール
- `kustomize` — Kubernetes マニフェストを環境差分込みで管理するツール
- `kubectx` — `kubectl` のコンテキスト・名前空間を素早く切り替えるツール
- `helm` — Kubernetes アプリケーションのパッケージマネージャ

別のサンプルである `echo` アプリでは、`apps/echo/.tool-versions`（出典: 同リポジトリ）でより最小限の構成になっており、`kustomize` のみを固定しています。

```text
kustomize 5.3.0
```

このように、`.tool-versions` をプロジェクトごとに置くことで、「そのプロジェクトに必要なツールとバージョン」が明文化され、リポジトリをクローンした人は同じ環境を再現できます。

### asdf のインストールとプラグイン追加

asdf は本体を導入したあと、ツールごとに「プラグイン」を追加して使います。一般的な利用の流れは次のとおりです。

```bash
# プラグインを追加する（例: golang と kustomize）
asdf plugin add golang
asdf plugin add kustomize

# .tool-versions に書かれたバージョンを一括インストールする
asdf install

# ツールのバージョンをグローバルに設定する（例）
asdf global golang 1.21.6
```

`asdf plugin add` で対象ツールのプラグインを登録し、`asdf install` で `.tool-versions` の内容に従ってバージョンをインストールします。`asdf global` は、カレントディレクトリに `.tool-versions` がない場面でのデフォルトバージョンを設定するためのコマンドです（プロジェクト単位で固定したい場合は `asdf local` を使い、`.tool-versions` を更新します）。

### `.tool-versions` から一括セットアップするスクリプト

`taskapp` には、`.tool-versions` を読み取ってプラグイン追加とインストールをまとめて実行するセットアップスクリプトが用意されています。出典は `apps/taskapp/hack/install-tools.sh`（`getting-started-docker-kubernetes` リポジトリ）です。

```bash
#!/usr/bin/env bash

cat .tool-versions | while read line
do
  echo $line | awk '{print $1}' | xargs -I{} asdf plugin add {}
  if [ $? -ne 0 ]; then
    continue
  fi
done

asdf install
```

このスクリプトが行っていることは次のとおりです。

1. `.tool-versions` を 1 行ずつ読み込む。
2. 各行の先頭フィールド（ツール名）を `awk` で取り出し、`asdf plugin add` に渡してプラグインを追加する。
3. プラグイン追加に失敗した行（`$? -ne 0`）はスキップして次の行へ進む。
4. すべてのプラグイン追加が終わったら、最後に `asdf install` で `.tool-versions` に書かれたバージョンを一括インストールする。

つまり、`.tool-versions` を更新してこのスクリプトを実行するだけで、必要なツール群を宣言どおりにそろえられます。バージョン管理の「単一の情報源（Single Source of Truth）」を `.tool-versions` に集約し、手作業のばらつきを排除する典型的なパターンです。

> 注: `.tool-versions` の内容（ツール名・バージョン）は上記のとおり実ファイルからの引用ですが、`asdf plugin add` / `asdf install` / `asdf global` の使い方は asdf の一般的な利用手順です。

---

## A.3 kind

### kind は何を解決するか

kind（Kubernetes IN Docker）は、その名のとおり Docker コンテナをノードに見立てて Kubernetes クラスタを立ち上げるツールです。本来 Kubernetes クラスタの構築には複数のマシンや仮想マシンが必要ですが、kind を使えば Docker さえあれば手元のマシン 1 台でクラスタを起動・破棄できます。

これにより「Kubernetes を学習・検証したいが、本格的なクラスタを用意するのは大変」という問題を解決できます。起動も破棄も速く、CI 環境での Kubernetes テストにも広く使われています。

### クラスタの作成と削除

最も基本的な使い方は次のとおりです（一般的な手順）。

```bash
# 既定設定でクラスタを作成する
kind create cluster

# 名前を付けてクラスタを作成する（例）
kind create cluster --name dev

# 作成済みクラスタの一覧を表示する
kind get clusters

# クラスタを削除する（名前を付けた場合は --name で指定）
kind delete cluster --name dev
```

`kind create cluster` を実行すると、kind が Docker コンテナとしてノードを起動し、`kubectl` の接続先（kubeconfig のコンテキスト）も自動的に設定します。作成後はそのまま `kubectl get nodes` などでクラスタを操作できます。

### kind-config.yaml によるマルチノード構成

既定では単一ノード（コントロールプレーン兼ワーカー）のクラスタになりますが、設定ファイルを渡すことで複数ノードのクラスタを構成できます。次は 1 つのコントロールプレーンと 2 つのワーカーノードを持つ構成の例です（一般的な例）。

```text
# kind-config.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
  - role: worker
  - role: worker
```

この設定ファイルを指定してクラスタを作成します。

```bash
# 設定ファイルを指定してマルチノードクラスタを作成する
kind create cluster --name multi --config kind-config.yaml
```

複数ノード構成にすると、Pod のスケジューリングやノード間の挙動を、本番に近い形でローカル検証できます。Kubernetes の基本概念については本編の第 5 章も参照してください。

> 注: 上記の kind-config.yaml と各コマンドは kind の一般的な使い方を示した例です。実際の `apiVersion` やノード設定は kind のバージョンによって異なる場合があります。

---

## A.4 Rancher Desktop

### Rancher Desktop は何を解決するか

Rancher Desktop は、ローカルでコンテナと Kubernetes をまとめて動かせるデスクトップアプリケーションです。Docker Desktop の代替として利用でき、ライセンス面や軽量さを理由に選ばれることがあります。Windows・macOS・Linux に対応し、GUI からバージョンやランタイムを切り替えられます。

「Docker Desktop に依存せずに、ローカルでコンテナと Kubernetes の両方を手軽に扱いたい」というニーズを解決するのが Rancher Desktop です。

### コンテナランタイムの切り替え（containerd / dockerd）

Rancher Desktop の特徴の 1 つは、コンテナランタイムを切り替えられる点です。設定画面から次の 2 種類を選択できます。

- containerd — Kubernetes が標準的に利用するコンテナランタイム。Rancher Desktop では `nerdctl` という Docker 互換 CLI を通じてイメージのビルドやコンテナの実行を行います。
- dockerd（Moby） — 従来の Docker と同じランタイム。`docker` コマンドや `docker-compose` をそのまま使いたい場合に選択します。

既存の `docker` コマンドや本編で扱った操作をそのまま使いたい場合は dockerd を、Kubernetes 寄りの運用に合わせたい場合は containerd を選ぶ、といった使い分けができます。

### 内蔵 Kubernetes（k3s）

Rancher Desktop には軽量な Kubernetes ディストリビューションである k3s が内蔵されています。アプリの設定で Kubernetes を有効化すると、追加のインストールなしでローカル Kubernetes クラスタが起動し、`kubectl` で操作できるようになります。利用する Kubernetes のバージョンも GUI から選択できます。

Windows では、Rancher Desktop は WSL2 を基盤として動作します（A.1 を参照）。そのため、WSL2 が有効になっていることが前提になります。kind がクラスタを都度作成・破棄するのに対し、Rancher Desktop の k3s は「常駐するローカルクラスタ」として使える点が異なります。用途に応じて、kind と Rancher Desktop を使い分けるとよいでしょう。

> 注: ランタイムの切り替えや Kubernetes 有効化は Rancher Desktop の GUI から行う操作であり、画面構成はバージョンによって異なります。ここではコマンド出力の掲載は省略します。

---

## まとめ

本付録では、Docker / Kubernetes を学習・開発するための周辺ツールを 4 つ紹介しました。それぞれが解決する課題を整理すると次のとおりです。

- WSL2 — Windows 上で Linux 環境を動かし、Linux 前提のツールチェーンや Docker をスムーズに利用できるようにする。
- asdf — `.tool-versions` で言語・ツールのバージョンを宣言的に固定し、チーム全体で同じ環境を再現する。本リポジトリの `apps/taskapp/.tool-versions` と `apps/taskapp/hack/install-tools.sh` がその実例である。
- kind — Docker さえあれば手元でマルチノードの Kubernetes クラスタを起動・破棄でき、学習や CI に適する。
- Rancher Desktop — Docker Desktop の代替として、containerd/dockerd の切り替えと内蔵 k3s により、ローカルでコンテナと Kubernetes をまとめて扱える。

これらのツールは排他的なものではなく、目的に応じて組み合わせて使えます。たとえば「Windows で WSL2 を基盤に Rancher Desktop を動かし、プロジェクトのツールは asdf で固定し、検証用クラスタは kind で立てる」といった構成も成り立ちます。自分の開発スタイルと、本編で学んだコンテナ・Kubernetes の知識に合わせて、必要なツールを選んでください。

### 関連リンク

- [付録 B さまざまなコンテナオーケストレーション環境](appendix-b-orchestration-environments.md)
- [第 1 章 コンテナと Docker の基礎](01-container-and-docker-basics.md)
- [第 5 章 Kubernetes 入門](05-kubernetes-introduction.md)
