---
type: Article
title: "付録 B さまざまなコンテナオーケストレーション環境"
description: "本編では、ローカル環境（Docker Desktop の Kubernetes や kind など）を使ってコンテナアプリケーションをデプロイする方法を学びました。"
tags: [article, getting-start-docker-kubernetes]
status: stable
generated: { by: human:kakimomokuri, at: 2026-06-26T00:29:42Z }
---

# 付録 B さまざまなコンテナオーケストレーション環境

## はじめに

本編では、ローカル環境（Docker Desktop の Kubernetes や kind など）を使ってコンテナアプリケーションをデプロイする方法を学びました。ローカル環境は学習や開発には最適ですが、本番運用ではノードの冗長化、可用性、スケーラビリティ、ネットワークやストレージの管理など、自前で面倒を見るには負担の大きい要素がいくつもあります。

そこで実際の本番運用では、クラウドベンダーが提供する「マネージドなオーケストレーション環境」を利用するのが一般的です。マネージド環境では、コントロールプレーン（Kubernetes の API サーバーや etcd など）の運用をベンダーに任せられるため、利用者はワーカーノードとアプリケーションの管理に集中できます。

この付録では、ローカルから本番のマネージド環境へ移行するという観点で、次の 5 つの環境を概観します。

- B.1 Google Kubernetes Engine(GKE)
- B.2 Amazon Elastic Kubernetes Service(EKS)
- B.3 Azure Kubernetes Service(AKS)
- B.4 オンプレミス環境での Kubernetes クラスタの構築
- B.5 Amazon Elastic Container Service(ECS)

このうち GKE・EKS・AKS は 3 大クラウドが提供するマネージド Kubernetes であり、本編で書いた Kubernetes マニフェストをほぼそのまま流用できます。一方で ECS は Kubernetes ではない AWS 独自のオーケストレーション環境であり、概念や定義ファイルが異なります。Kubernetes の知識をそのまま活かせるかどうかが、両者を選ぶ際の大きな分かれ目になります。

### マネージド環境の比較

ローカル環境と 3 大クラウドのマネージド Kubernetes、そして非 Kubernetes の ECS を比較すると次のようになります。

| 項目 | ローカル(Docker Desktop / kind) | GKE | EKS | AKS | ECS |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 提供元 | 自分の PC | Google Cloud | AWS | Microsoft Azure | AWS |
| オーケストレーター | Kubernetes | Kubernetes | Kubernetes | Kubernetes | ECS（独自） |
| コントロールプレーン | 自前 | マネージド | マネージド | マネージド | マネージド |
| 主な構築ツール | docker / kind | gcloud / Terraform | eksctl / Terraform | az / Terraform | CDK / Terraform |
| マニフェストの再利用 | ― | 高い | 高い | 高い | 不可（独自定義） |
| ロードバランサ | ― | Cloud Load Balancing | ELB(ALB/NLB) | Azure Load Balancer / Application Gateway | ELB(ALB/NLB) |
| 主な用途 | 学習・開発 | 本番運用 | 本番運用 | 本番運用 | 本番運用 |

各クラウドの Kubernetes は API レベルで互換性があるため、Service や Ingress のような環境依存しやすい部分を除けば、本編のマニフェストはどのクラウドへ持っていってもほぼ動作します。以降の各節では、それぞれの環境の特徴と構築手順を見ていきます。

なお本文中では、本リポジトリ（getting-started-docker-kubernetes）に実在するスクリプトやマニフェストを引用した箇所と、実コードのない一般論・例とを明確に区別します。実コードのない手順には「例」と明記します。

---

## B.1 Google Kubernetes Engine(GKE)

GKE は Google Cloud が提供するマネージド Kubernetes です。Kubernetes は元々 Google 社内のコンテナ基盤 Borg の知見を基に開発された経緯があり、GKE はその本家とも言える環境です。Autopilot モード（ノードの管理まで Google に任せるモード）と Standard モード（ノードを自分で管理するモード）の 2 種類があります。

本リポジトリには GKE 用の構築スクリプトは含まれていないため、ここで示すコマンドは一般的な手順の「例」です。実際のオプションやリージョン名などは、利用する Google Cloud プロジェクトの状況に合わせて読み替えてください。

### gcloud によるクラスタ作成（例）

GKE のクラスタは `gcloud` CLI で作成します。次は Standard モードでクラスタを作成する例です。

```bash
# プロジェクトとデフォルトのリージョン/ゾーンを設定する（例）
gcloud config set project YOUR_PROJECT_ID
gcloud config set compute/zone asia-northeast1-a

# GKE クラスタを作成する（例）
gcloud container clusters create taskapp-cluster \
  --num-nodes=2 \
  --machine-type=e2-medium \
  --release-channel=regular
```

Autopilot モードを使う場合は、ノード数やマシンタイプを指定せずに次のように作成します。

```bash
# Autopilot モードでクラスタを作成する（例）
gcloud container clusters create-auto taskapp-cluster \
  --region=asia-northeast1
```

### kubeconfig の取得とデプロイ（例）

クラスタを作成したら、`kubectl` から接続するための認証情報（kubeconfig）を取得します。

```bash
# kubectl がクラスタへ接続できるよう認証情報を取得する（例）
gcloud container clusters get-credentials taskapp-cluster

# 接続確認（例）
kubectl get nodes

# 本編で作成したマニフェストを適用する（例）
kubectl apply -f k8s/
```

GKE では、`type: LoadBalancer` の Service を作成すると Google Cloud のロードバランサが自動的にプロビジョニングされ、外部 IP アドレスが払い出されます。本編で学んだ Service や Deployment のマニフェストは基本的にそのまま利用できる点が、マネージド Kubernetes の大きな利点です。

---

## B.2 Amazon Elastic Kubernetes Service(EKS)

EKS は AWS が提供するマネージド Kubernetes です。EKS のクラスタ構築には、AWS 公式が推奨する CLI ツール `eksctl` を使うと宣言的かつ簡潔に記述できます。

本リポジトリには、AWS CloudShell 上で EKS を構築するための実スクリプトが含まれています。ここでは出典として `cloudshell/aws/eks/create-eks.sh` と `cloudshell/aws/eks/setup-tools.sh` を引用しながら解説します。

### eksctl のインストール

`cloudshell/aws/eks/setup-tools.sh` は、eksctl の最新版をダウンロードして配置するスクリプトです。

```bash
#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail

curl -s --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin
```

GitHub のリリースから tarball を取得して `/usr/local/bin` へ展開するだけのシンプルなスクリプトです。`set -o errexit / nounset / pipefail` を冒頭で指定し、途中でエラーが起きたらすぐに止まるようにしている点が堅牢なスクリプトの作法です。

### ClusterConfig によるクラスタ定義

`cloudshell/aws/eks/create-eks.sh` は、eksctl の `ClusterConfig` を動的に生成して EKS クラスタを構築するスクリプトです。スクリプトは `-n`（クラスタ名）と `-r`（リージョン）を引数で受け取り、ヒアドキュメントで `cluster.yaml` を組み立てます。生成される `ClusterConfig` の中核部分は次のとおりです。

```yaml
apiVersion: eksctl.io/v1alpha5
kind: ClusterConfig

metadata:
  name: ${CLUSTER_NAME}
  region: ${DEFAULT_REGION} 
  version: latest

addons:
  - name: vpc-cni
    version: latest
  - name: coredns
    version: latest
  - name: kube-proxy
    version: latest

managedNodeGroups:
  - name: workers 
    labels: { role: workers }
    instanceType: t3.medium
    desiredCapacity: 1
    privateNetworking: true
```

このマニフェストには、EKS クラスタを構成するうえで重要な要素が詰まっています。

- `addons`: クラスタにインストールするアドオンを指定します。ここでは Kubernetes の動作に欠かせない 3 つを宣言しています。
    - `vpc-cni`: Pod に AWS VPC のネットワークを割り当てる CNI（Container Network Interface）プラグインです。
    - `coredns`: クラスタ内の DNS（サービスディスカバリ）を担うコンポーネントです。
    - `kube-proxy`: 各ノードで Service への通信を適切な Pod へ転送するコンポーネントです。
- `managedNodeGroups`: ワーカーノードのグループを定義します。`instanceType: t3.medium` のインスタンスを `desiredCapacity: 1` 台起動し、`privateNetworking: true` によってノードをプライベートサブネットに配置します。マネージドノードグループは、ノードのライフサイクル（作成・更新・削除）を AWS が管理してくれる仕組みです。

### クラスタの作成と IAM 権限の付与

クラスタ定義を生成したあと、`create-eks.sh` は実際にクラスタを作成し、IAM ユーザーへ操作権限を付与します。スクリプトの後半部分は次のようになっています。

```bash
AWS_ACCOUNT_ID=`aws sts get-caller-identity | jq -r ".Account"`

# (中略：cluster.yaml と eks-update-kubeconfig.json を生成)

IAM_USER_NAME=gihyo-`head /dev/urandom | tr -dc a-z0-9 | head -c 6`
aws iam create-user --user-name $IAM_USER_NAME
aws iam put-user-policy --user-name $IAM_USER_NAME \
  --policy-name eksUpdateConfigPolicy \
  --policy-document file://eks-update-kubeconfig.json

eksctl create cluster --config-file ./cluster.yaml
eksctl create iamidentitymapping --cluster ${CLUSTER_NAME} \
  --arn arn:aws:iam::${AWS_ACCOUNT_ID}:user/${IAM_USER_NAME} \
  --username ${IAM_USER_NAME} \
  --group system:masters
```

この処理の流れは次のとおりです。

1. `aws sts get-caller-identity` で現在の AWS アカウント ID を取得します。
2. ランダムなサフィックスを付けた IAM ユーザー（`gihyo-xxxxxx`）を作成し、`eks-update-kubeconfig.json` で定義したポリシー（`eks:DescribeCluster` と `eks:ListClusters` を許可するもの）をインラインポリシーとして付与します。これにより、このユーザーは kubeconfig の更新に必要な情報を取得できるようになります。
3. `eksctl create cluster --config-file ./cluster.yaml` で、先ほどの `ClusterConfig` に基づいてクラスタを作成します。VPC やサブネット、ノードグループなどが一括で構築されます。
4. `eksctl create iamidentitymapping` で IAM identity mapping を作成します。これは「AWS の IAM ユーザー」と「Kubernetes 内の権限（グループ）」を結びつける EKS 独自の仕組みです。ここでは作成した IAM ユーザーを `system:masters` グループにマッピングし、クラスタ管理者権限を与えています。

EKS では、AWS の認証（IAM）と Kubernetes の認可（RBAC）が分離しているため、この IAM identity mapping が「誰がクラスタを操作できるか」を決める要となります。EKS を扱ううえで特に押さえておきたいポイントです。

### kubeconfig の更新（例）

クラスタ作成後は、次のコマンドで `kubectl` の接続情報を更新します（一般的な手順の例）。

```bash
# kubectl がクラスタへ接続できるよう kubeconfig を更新する（例）
aws eks update-kubeconfig --name CLUSTER_NAME --region REGION
kubectl get nodes
```

---

## B.3 Azure Kubernetes Service(AKS)

AKS は Microsoft Azure が提供するマネージド Kubernetes です。本リポジトリには、本編のタスク管理アプリ（taskapp）を AKS へデプロイするためのマニフェストが `apps/taskapp/k8s/plain/aks/` に含まれています。ここではそれらを引用しながら、ローカル版（`apps/taskapp/k8s/plain/local/`）との差分を見ていきます。

### ローカル版との差分

AKS 版とローカル版は、ほとんどのマニフェストが共通です。実際にファイルを比較すると、`api.yaml`・`mysql.yaml`・`migrator.yaml` はローカル版と AKS 版で内容が一致しており、Deployment や StatefulSet、Job の定義をそのまま流用できることがわかります。これはマネージド Kubernetes の互換性の高さを示す好例です。

差分が現れるのは `web.yaml` の Ingress 定義です。外部からの入口（Ingress）は環境のロードバランサに依存するため、ここだけ環境ごとに書き換える必要があります。

ローカル版（`apps/taskapp/k8s/plain/local/web.yaml`）の Ingress は、NGINX Ingress Controller を前提としています。

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: web
  labels:
    app: web
spec:
  ingressClassName: nginx
  rules:
    - host: localhost 
      http:
        paths:
          - pathType: Prefix
            path: /
            backend:
              service:
                name: web
                port:
                  number: 80
```

一方、AKS 版（`apps/taskapp/k8s/plain/aks/web.yaml`）の Ingress は、Azure の Application Gateway Ingress Controller を前提としています。

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: web
  labels:
    app: web
spec:
  ingressClassName: azure-application-gateway
  rules:
    - http:
        paths:
          - pathType: Prefix
            path: /
            backend:
              service:
                name: web
                port:
                  number: 80
```

実ファイルで確認できる差分は次の 2 点です。

- `ingressClassName` が `nginx`（ローカル）から `azure-application-gateway`（AKS）へ変わっています。AKS では Azure の Application Gateway をクラスタの Ingress として利用するためです。
- ローカル版にあった `host: localhost` の指定が、AKS 版では削除されています。ローカルでは `localhost` というホスト名でアクセスしますが、AKS では Application Gateway に払い出されるパブリックアドレスでアクセスするためです。

Deployment 本体（コンテナイメージや環境変数など）は両者で完全に共通であり、`ghcr.io/gihyodocker/taskapp-web:v0.1.0` などのイメージ参照もそのままです。つまり「アプリケーションの定義は共通、入口（Ingress）だけ環境に合わせる」という構成になっています。これはマニフェストの保守性を高める良い設計です。

### az によるクラスタ作成（例）

AKS のクラスタは `az` CLI で作成します。次は一般的な手順の「例」です。

```bash
# リソースグループを作成する（例）
az group create --name taskapp-rg --location japaneast

# AKS クラスタを作成する（例）
az aks create \
  --resource-group taskapp-rg \
  --name taskapp-cluster \
  --node-count 2 \
  --node-vm-size Standard_B2s \
  --generate-ssh-keys

# kubectl の接続情報を取得する（例）
az aks get-credentials --resource-group taskapp-rg --name taskapp-cluster

# AKS 用マニフェストを適用する（例）
kubectl apply -f apps/taskapp/k8s/plain/aks/
```

Application Gateway Ingress Controller を利用する場合は、クラスタ作成時にアドオンとして有効化するか、後から有効化する必要があります。その有効化が済んでいることが、上記 AKS 版 `web.yaml` の Ingress が機能する前提となります。

---

## B.4 オンプレミス環境での Kubernetes クラスタの構築

クラウドのマネージドサービスを使わず、自社のデータセンターや自前のサーバー上に Kubernetes クラスタを構築することもできます。オンプレミスでは、セキュリティ要件や既存資産の活用、ランニングコストの観点から自前構築が選ばれることがあります。その代わり、コントロールプレーンの運用やバージョンアップ、ネットワーク・ストレージ・ロードバランサの整備をすべて自分たちで担う必要があります。

本リポジトリにはオンプレミス構築用のスクリプトは含まれていないため、ここで示すのは一般的な構成の「例」です。

### kubeadm によるクラスタ構築（例）

オンプレミスで Kubernetes を構築する標準的なツールが `kubeadm` です。大まかな流れは次のとおりです。

```bash
# 各ノードに kubeadm / kubelet / kubectl をインストール後、
# コントロールプレーンノードでクラスタを初期化する（例）
sudo kubeadm init --pod-network-cidr=10.244.0.0/16

# 一般ユーザーから kubectl を使えるよう kubeconfig を配置する（例）
mkdir -p $HOME/.kube
sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
sudo chown $(id -u):$(id -g) $HOME/.kube/config

# ワーカーノードをクラスタに参加させる（例）
# （kubeadm init が出力する join コマンドを各ワーカーで実行する）
sudo kubeadm join CONTROL_PLANE_IP:6443 --token TOKEN \
  --discovery-token-ca-cert-hash sha256:HASH
```

### CNI とロードバランサの整備（例）

`kubeadm init` 直後のクラスタは、Pod 間ネットワークがまだ構成されておらず、ノードは `NotReady` のままです。Pod 同士が通信できるように、CNI プラグインを別途インストールする必要があります。代表的な CNI には次のようなものがあります（例）。

- Flannel: シンプルで導入が容易なオーバーレイネットワーク。
- Calico: ネットワークポリシーによる細かいアクセス制御に対応。
- Cilium: eBPF を活用した高性能なネットワーキングと可観測性。

```bash
# CNI プラグイン（Calico）を適用する（例）
kubectl apply -f https://raw.githubusercontent.com/projectcalico/calico/CALICO_VERSION/manifests/calico.yaml
```

また、クラウドと違ってオンプレミスには `type: LoadBalancer` の Service に対して外部 IP を払い出す仕組みが標準では存在しません。そこで MetalLB のようなソフトウェアロードバランサを導入し、空き IP アドレスのプールを割り当てることで、`type: LoadBalancer` を機能させます（例）。

```yaml
# MetalLB に払い出させる IP アドレスプールの定義（例）
apiVersion: metallb.io/v1beta1
kind: IPAddressPool
metadata:
  name: default-pool
  namespace: metallb-system
spec:
  addresses:
    - 192.168.10.240-192.168.10.250
```

このように、オンプレミスではマネージド環境が裏側で面倒を見ていた「ネットワーク（CNI）」「ロードバランサ（MetalLB 等）」「ストレージ」を自分で組み立てる必要があります。逆に言えば、これらを理解しておくと、マネージド環境が何をしてくれているのかがより深く理解できます。

---

## B.5 Amazon Elastic Container Service(ECS)

ECS は AWS 独自のコンテナオーケストレーション環境であり、Kubernetes ではありません。そのため Kubernetes のマニフェストはそのまま使えませんが、AWS のサービスと密に統合されており、Fargate（サーバーレスでコンテナを実行する仕組み）と組み合わせれば、ノード（EC2 インスタンス）の管理すら不要になります。

本リポジトリには、ECS 環境を AWS CDK（TypeScript）で構築する実コードが `cloudshell/aws/ecs/gihyo-ecs/` に含まれています。ここではそのスタック定義 `lib/gihyo-ecs-stack.ts` を引用しながら、ECS の中心概念である Cluster / Task Definition / Service を解説します。

### CDK アプリのエントリポイント

`bin/gihyo-ecs.ts` がアプリのエントリポイントで、`GihyoEcsStack` というスタックをインスタンス化しています。

```typescript
#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { GihyoEcsStack } from '../lib/gihyo-ecs-stack';

const app = new cdk.App();
new GihyoEcsStack(app, 'GihyoEcsStack', {
  // ...
});
```

### Cluster の定義

`lib/gihyo-ecs-stack.ts` では、まず VPC を作成したうえで、その VPC 上に ECS クラスタを定義しています。

```typescript
// ECS
const cluster = new ecs.Cluster(this, 'Cluster', {
  vpc: vpc,
});
```

ECS の Cluster は、タスク（コンテナ）を実行する論理的なグループです。Kubernetes のクラスタに相当しますが、ECS ではコントロールプレーンを意識する必要はなく、Fargate を使えばノードの存在すら隠蔽されます。

### Task Definition の定義

ECS で「どのコンテナをどう動かすか」を定義するのが Task Definition です。次のコードでは、Fargate 用の Task Definition を作成し、`nginx` と `echo` の 2 つのコンテナを 1 つのタスクにまとめています。

```typescript
// ECS TaskDefinition
const echoTaskDefinition = new ecs.FargateTaskDefinition(this, 'TaskDefinitionEcho', {
  cpu: 256,
  memoryLimitMiB: 512,
});
// "nginx" container
const nginxContainer = echoTaskDefinition.addContainer("EchoConNginx", {
  containerName: "nginx",
  image: ecs.ContainerImage.fromRegistry('ghcr.io/gihyodocker/simple-nginx-proxy:v0.0.1'),
  logging: ecs.LogDrivers.awsLogs({
    streamPrefix: 'echo-nginx',
    logRetention: log.RetentionDays.ONE_MONTH,
  }),
  environment: {
    NGINX_PORT: "80",
    SERVER_NAME: "localhost",
    BACKEND_HOST: "localhost:8080",
    BACKEND_MAX_FAILS: "3",
    BACKEND_FAIL_TIMEOUT: "10s",
  },
});
nginxContainer.addPortMappings({
  containerPort: 80,
  hostPort: 80
})
// "echo" container
const echoContainer = echoTaskDefinition.addContainer("EchoConEcho", {
  containerName: "echo",
  image: ecs.ContainerImage.fromRegistry('ghcr.io/gihyodocker/echo:v0.0.1-9-gfe27471-slim'),
  logging: ecs.LogDrivers.awsLogs({
    streamPrefix: 'echo-nginx',
    logRetention: log.RetentionDays.ONE_MONTH,
  }),
});
echoContainer.addPortMappings({
  containerPort: 8080,
  hostPort: 8080,
})
```

ここでのポイントは次のとおりです。

- `FargateTaskDefinition` で CPU・メモリを指定しています。タスク単位でリソースを割り当てる点が ECS の特徴です。
- `addContainer` で 1 つのタスクに複数のコンテナ（`nginx` と `echo`）を同居させています。`nginx` がリバースプロキシとなり、`localhost:8080` の `echo` コンテナへ転送する構成です。同じタスク内のコンテナは `localhost` で通信できます。
- `LogDrivers.awsLogs` により、各コンテナのログを CloudWatch Logs へ転送しています。

### Service の定義

Task Definition は「設計図」にすぎません。それを実際に「何個、どう動かし続けるか」を定義するのが Service です。

```typescript
// ECS Service
const service = new ecs.FargateService(this, 'ServiceEcho', {
  cluster,
  taskDefinition: echoTaskDefinition,
  desiredCount: 1,
  assignPublicIp: true,
  securityGroups: [
    securityGroupApp,
  ]
});
service.attachToApplicationTargetGroup(targetGroup);
```

`FargateService` は、指定した Cluster 上で `echoTaskDefinition` のタスクを `desiredCount: 1`（1 つ）だけ常時起動し続けます。タスクが落ちれば自動で再起動され、台数を増やせばスケールアウトします。さらに `attachToApplicationTargetGroup` により、このスタックで作成した ALB（Application Load Balancer）のターゲットグループへ紐づけ、外部からの HTTP リクエストをタスクへ振り分けています。

### Kubernetes との対応関係

ECS の概念は、Kubernetes の概念と次のように対応づけて理解するとわかりやすいでしょう。

| ECS の概念 | おおよそ対応する Kubernetes の概念 | 役割 |
| :--- | :--- | :--- |
| Cluster | Cluster | コンテナを実行する論理的なグループ |
| Task Definition | Pod のテンプレート（Deployment の `template`） | コンテナ群・リソース・環境変数などの設計図 |
| Task | Pod | 実際に起動するコンテナ群の実行単位 |
| Service | Deployment + Service | タスクを指定数だけ維持し、LB へ振り分ける |
| Fargate | （ノードレス実行） | サーバー管理不要でタスクを実行する基盤 |

厳密には ECS の Service は Kubernetes の Deployment（レプリカ維持）と Service（負荷分散）の両方の役割を兼ねている点に注意してください。これらの対応関係を押さえておくと、Kubernetes の知識を ECS へ応用しやすくなります。

### PipeCD による ECS デプロイ定義（補足）

本リポジトリの `pipecd-examples/ecs/simple/` には、CDK とは別の形式で ECS を定義するファイルもあります。これは継続的デリバリーツール PipeCD 向けの定義で、Task Definition と Service を YAML で記述します。出典として `pipecd-examples/ecs/simple/taskdef.yaml` の主要部を引用します。

```yaml
family: nginx-service-fam
executionRoleArn: arn:aws:iam::XXXX:role/ecsTaskExecutionRole
containerDefinitions:
  - command: null
    cpu: 100
    image: XXXX.dkr.ecr.ap-northeast-1.amazonaws.com/nginx:1
    memory: 100
    mountPoints: []
    name: web
    portMappings:
      - containerPort: 80
compatibilities:
  - FARGATE
requiresCompatibilities:
  - FARGATE
networkMode: awsvpc
memory: 512
cpu: 256
```

Service 側の定義（`pipecd-examples/ecs/simple/servicedef.yaml`）では、起動するタスク数や起動タイプ、ネットワーク設定を記述します。

```yaml
serviceName: nginx-service
desiredCount: 2
launchType: FARGATE
networkConfiguration:
  awsvpcConfiguration:
    assignPublicIp: ENABLED
    securityGroups:
      - sg-YYYY
    subnets:
      - subnet-YYYY
      - subnet-YYYY
```

このように、ECS の構築方法は AWS CDK で記述する方法（`gihyo-ecs`）と、CD ツール向けに宣言的な YAML で記述する方法（PipeCD）など複数あります。いずれの場合も、Task Definition と Service という ECS の基本概念は共通している点を押さえておきましょう。なお `XXXX` や `sg-YYYY` などはプレースホルダであり、実際の値に置き換える必要があります。

---

## まとめ

この付録では、ローカル環境から本番のマネージド環境へ移行するという観点で、5 つのコンテナオーケストレーション環境を概観しました。

- GKE・EKS・AKS は 3 大クラウドが提供するマネージド Kubernetes であり、本編で学んだマニフェストをほぼそのまま再利用できます。差分が出やすいのは Service や Ingress のような環境依存部分で、AKS の例では Ingress の `ingressClassName` だけを `azure-application-gateway` に変えるだけで済みました。
- EKS では `eksctl` の `ClusterConfig` でクラスタを宣言的に定義し、IAM identity mapping で AWS の認証と Kubernetes の認可を結びつける点が特徴です。
- オンプレミスでは `kubeadm` でクラスタを構築し、CNI（Calico など）やソフトウェアロードバランサ（MetalLB）を自分で整備する必要があります。これはマネージド環境が裏で何をしているかを理解する良い機会にもなります。
- ECS は Kubernetes ではない AWS 独自の環境ですが、Cluster / Task Definition / Service という概念を Kubernetes の Cluster / Pod / Deployment+Service に対応づけて理解すれば、既存の知識を活かせます。

どの環境を選ぶにせよ、本編で身につけたコンテナとオーケストレーションの基礎は共通の土台になります。「変更を楽に安全にできる」状態を保つために、アプリケーションの定義（共通部分）と環境固有の設定（差分部分）を切り分けて管理することが、複数環境を扱ううえでの実践的な指針となります。

---

### 関連リンク

- [付録 A 開発ツールのセットアップ](appendix-a-dev-tools-setup.md)
- [付録 C Docker/Kubernetes の Tips](appendix-c-tips.md)
- [第 6 章 Kubernetes クラスタへのデプロイ](06-kubernetes-deploy-cluster.md)
