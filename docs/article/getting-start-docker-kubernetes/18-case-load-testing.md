---
type: Article
title: "第 18 章 ケーススタディの負荷テスト"
description: "第 12 章「12.3 負荷テスト」では、Python 製の負荷テストツール Locust をコンテナで動かす考え方を学びました。"
tags: [article, getting-start-docker-kubernetes]
status: stable
generated: { by: human:kakimomokuri, at: 2026-06-26T00:29:42Z }
---

# 第 18 章 ケーススタディの負荷テスト

## はじめに

第 12 章「12.3 負荷テスト」では、Python 製の負荷テストツール **Locust** をコンテナで動かす考え方を学びました。負荷生成ツール・シナリオ・テスト対象をすべてコンテナとして扱うことで、負荷テスト環境を「コードから一括で構築し、終わったら破棄する」ことができます。

この章では、その考え方を第 13〜16 章の 4 ケース（同一ドメイン = Cargo Tracker の 4 アーキテクチャ）に適用します。各ケースに Locust の負荷テスト一式を用意し、**Docker Compose 版**と **Kubernetes 版**の 2 通りで実行できるようにしました。

実装はすべて各ケースの [`loadtest/`](https://github.com/k2works/getting-started-docker-kubernetes/tree/main/apps/case-studies) ディレクトリにあります。

| ケース | アーキテクチャ | loadtest ディレクトリ |
|--------|--------------|----------------------|
| case-1 | モノリス | `apps/case-studies/case-1-monolith/loadtest/` |
| case-2 | イベント駆動 MS | `apps/case-studies/case-2-event-driven/loadtest/` |
| case-3 | ES/CQRS（Axon） | `apps/case-studies/case-3-escqrs-axon/loadtest/` |
| case-4 | ES/CQRS（Kafka） | `apps/case-studies/case-4-escqrs-kafka/loadtest/` |

---

## 18.1 構成

各ケースの `loadtest/` には次のファイルが入っています。シナリオ（`locustfile.py`）は Compose 版・Kubernetes 版で共有します。

| ファイル | 役割 |
|---------|------|
| `locustfile.py` | 負荷シナリオ（仮想ユーザーの振る舞い） |
| `compose.loadtest.yaml` | Docker Compose 版 Locust の起動定義（Web UI: 8089） |
| `kustomization.yaml` | Kubernetes 版 Locust の Kustomize 定義 |
| `k8s-locust.yaml` | Kubernetes 版 Locust の Deployment / Service |
| `README.md` | 実行手順・対象エンドポイント |

### 現行 Locust API を使う

第 12 章で引用したサンプル（`senario.py`）は、Locust 旧 API（`HttpLocust` / `TaskSet`）で書かれていました。これらは現行の Locust では廃止されているため、本章のシナリオは**現行 API（`HttpUser` / `@task` / `between`）**と公式イメージ `locustio/locust` で記述しています。

```python
from locust import HttpUser, between, task


class CargoTrackerUser(HttpUser):
    # 各タスクの実行間隔（記事の min_wait / max_wait に相当）
    wait_time = between(1, 3)

    @task(5)
    def bookings(self):
        self.client.get("/api/v1/bookings", name="/api/v1/bookings")
```

旧 API の `min_wait` / `max_wait`（ミリ秒）は、現行では `wait_time = between(min, max)`（秒）に置き換わっています。`HttpLocust` は `HttpUser`、`task_set` を持つ `TaskSet` は `HttpUser` に直接 `@task` を書く形になりました。

---

## 18.2 ケースごとの対象エンドポイントと認証

負荷テストで重要なのは「**副作用のない読み取り系（GET）に、認証を通して負荷をかける**」ことです。4 ケースは認証方式が異なるため、シナリオもそれに合わせています。

| ケース | 公開ポート（Compose） | 認証方式 | シナリオの方針 |
|--------|---------------------|---------|--------------|
| case-1 | 18080（app） | セッション + フォームログイン | 認証不要の GET のみ（`/actuator/health`、`/login`、`/public/tracking`） |
| case-2 | 9080（gateway） | JWT（gateway で強制） | ログインで JWT を取得し、Read 系 GET に付与 |
| case-3 | 9081（gateway） | JWT（gateway で強制） | ログインで JWT を取得し、CQRS Query 側へ |
| case-4 | 9082（gateway） | `local` では認可無効 | 匿名で Read Model を GET |

### case-1: 認証不要の GET（セッション認証のため）

モノリス版は Spring Security のフォームログイン（セッション + CSRF）で保護されています。負荷テストでは、確実に匿名アクセスできる GET だけを対象にしています。

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/actuator/health` | ヘルスチェック |
| GET | `/login` | ログイン画面（HTML） |
| GET | `/public/tracking` | 公開追跡の検索画面 |

### case-2 / case-3: JWT ログインしてから GET

両ケースとも gatewayms が JWT 認証を強制します。Locust の `on_start`（仮想ユーザー開始時に 1 回呼ばれる）でログインしてトークンを取得し、以降の GET に `Authorization: Bearer <token>` を付けます。

```python
def on_start(self):
    """ログインして JWT を取得する。失敗時はトークンなしで続行する。"""
    self.token = None
    with self.client.post(
        "/api/v1/auth/login",
        json={"username": USERNAME, "password": PASSWORD},
        name="/api/v1/auth/login",
        catch_response=True,
    ) as res:
        if res.status_code == 200:
            data = res.json() if res.text else {}
            self.token = data.get("token") or data.get("accessToken")
            if self.token:
                res.success()
            else:
                res.failure("token がレスポンスに含まれていません")
        else:
            res.failure(f"ログイン失敗: HTTP {res.status_code}")
```

既定ユーザーはシード済みの `admin` / `password` です。case-3 は CQRS の Query 側（`/api/v1/bookings`、`/api/v1/voyages`、`/api/v1/billing/invoices` など）の Read Model 読み取りに負荷をかけます。

### case-4: 認証なしで Read Model を GET

case-4 は `local`（`local-docker`）プロファイルで起動するため、gateway / 各サービスの Spring Security 認可が無効になります。そのためログイン処理なしで、ページング付きの一覧 API に直接負荷をかけられます。

```python
@task(5)
def bookings(self):
    """予約一覧（Read Model、ページング）。"""
    self.client.get("/api/v1/bookings?page=0&size=20", name="/api/v1/bookings")
```

`name=` を明示しているのは、`?page=0&size=20` のようなクエリ違いを Locust の統計上で同じエンドポイントとして集計するためです。

---

## 18.3 Docker Compose 版

Compose 版は、第 12 章のサンプルと同様に「負荷生成側（Locust）を独立した Compose として用意する」方式です。ただし本ケーススタディは `dev:<case>` でアプリ本体を起動し、gateway/app を**ホストポートに公開**します。そこで Locust 側は、`host.docker.internal` 経由でそのホスト公開ポートへ負荷をかけます（どの Compose ネットワークからでも到達できます）。

出典: `apps/case-studies/case-4-escqrs-kafka/loadtest/compose.loadtest.yaml`

```yaml
name: loadtest-case4

services:
  locust:
    image: locustio/locust:2.32.5
    ports:
      - "8089:8089"
    volumes:
      - ./locustfile.py:/mnt/locust/locustfile.py:ro
    environment:
      LOCUST_LOCUSTFILE: /mnt/locust/locustfile.py
      # 負荷対象。ホストに公開された 9082 番（gatewayms: 9082:8080）へ向ける。
      LOCUST_HOST: ${TARGET_URL:-http://host.docker.internal:9082}
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

ポイントは次のとおりです。

- `image` に公式イメージ `locustio/locust` を使い、`volumes` でシナリオファイルをマウントします。イメージを再ビルドせずにシナリオを差し替えられます。
- `LOCUST_LOCUSTFILE` / `LOCUST_HOST` は、Locust の CLI オプション（`-f` / `--host`）に対応する環境変数です。
- `extra_hosts` で `host.docker.internal` を `host-gateway` に解決し、Linux でもホスト公開ポートへ到達できるようにしています。

### 実行手順（Compose 版）

```bash
# 1. アプリ本体を起動（gateway をホスト 9082 に公開）
npx gulp dev:case4

# 2. Locust を起動（Web UI: http://localhost:8089）
npx gulp loadtest:case4

# 3. ブラウザで同時ユーザー数・増加レートを指定してテスト開始

# 4. 終了
npx gulp loadtest:case4:down
```

CI 向けに、UI なしで一定時間だけ実行して終了するヘッドレス実行も用意しています。

```bash
# 既定: 50 ユーザー / 毎秒 5 ユーザー増 / 1 分間
npx gulp loadtest:case4:headless

# パラメータを環境変数で上書き
USERS=100 SPAWN=10 DURATION=3m npx gulp loadtest:case4:headless
```

---

## 18.4 Kubernetes 版

Kubernetes 版は、第 12 章の「負荷生成側とテスト対象を同一オーケストレータに載せる」考え方をそのまま適用します。**アプリと同じ名前空間に Locust をデプロイ**し、クラスタ内のサービス（`gatewayms` など）へ DNS 名で直接負荷をかけます。`host.docker.internal` やポートフォワードを介さず、より本番に近い経路で計測できます。

シナリオ（`locustfile.py`）は Kustomize の `configMapGenerator` で ConfigMap として配布し、Compose 版と同じファイルを共有します。

出典: `apps/case-studies/case-4-escqrs-kafka/loadtest/kustomization.yaml`

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

namespace: cargo-tracker

configMapGenerator:
  - name: locust-scenario
    files:
      - locustfile.py

resources:
  - k8s-locust.yaml
```

出典: `apps/case-studies/case-4-escqrs-kafka/loadtest/k8s-locust.yaml`（抜粋）

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: locust
spec:
  replicas: 1
  selector:
    matchLabels:
      app: locust
  template:
    metadata:
      labels:
        app: locust
    spec:
      containers:
        - name: locust
          image: locustio/locust:2.32.5
          args:
            - "-f"
            - "/mnt/locust/locustfile.py"
            - "--host"
            - "http://gatewayms:8080"   # クラスタ内サービスへ直接
          ports:
            - name: web
              containerPort: 8089
          volumeMounts:
            - name: scenario
              mountPath: /mnt/locust
      volumes:
        - name: scenario
          configMap:
            name: locust-scenario
```

ポイントは次のとおりです。

- `namespace` をアプリと同じにすることで、`--host http://gatewayms:8080` のように**サービス名だけ**で負荷対象を指定できます（同一名前空間内の DNS 解決）。
- `configMapGenerator` は ConfigMap 名にハッシュ（`locust-scenario-xxxx`）を付けます。Volume の `configMap.name` への参照は Kustomize が自動で書き換えるため、シナリオを変更すると新しいハッシュになり、Pod が確実に最新シナリオで再起動します。
- 負荷対象は、case-1 は `cargo-tracker:80`、case-2〜4 は `gatewayms:8080` です（各サービスのクラスタ内ポート）。

### 実行手順（Kubernetes 版）

```bash
# 1. アプリ本体を Kubernetes にデプロイ
npx gulp k8s:case4

# 2. Locust をデプロイ（同じ名前空間 cargo-tracker へ）
npx gulp loadtest:case4:k8s

# 3. Web UI を port-forward して開く（http://localhost:8089、Ctrl+C で終了）
npx gulp loadtest:case4:k8s:open

# 4. 削除
npx gulp loadtest:case4:k8s:delete
```

なお、アプリの `k8s:<case>:open` タスクは、Locust をデプロイ済みであれば負荷テストの Web UI（`http://localhost:8089`）も Kibana などと一緒に自動で開きます（未デプロイのときは `svc/locust` が見つからずスキップされます）。アプリ・ログ可視化・負荷テスト UI を 1 コマンドでまとめて開けます。

```bash
npx gulp k8s:case4            # アプリをデプロイ
npx gulp loadtest:case4:k8s   # 負荷テストをデプロイ
npx gulp k8s:case4:open       # アプリ + Kibana + Locust(8089) をまとめて開く
```

---

## 18.5 運用タスクとして統合する

これらの負荷テストは、本プロジェクトの運用タスク（Gulp）に `loadtest:<case>` として統合されています。`dev:<case>`（Compose 起動）・`k8s:<case>`（Kubernetes デプロイ）と同じ命名規約で扱えます。

| タスク | 説明 |
|--------|------|
| `loadtest:<case>` | Compose 版 Locust の Web UI を起動 |
| `loadtest:<case>:headless` | Compose 版を UI なしで実行（`USERS` / `SPAWN` / `DURATION` で調整） |
| `loadtest:<case>:down` | Compose 版を停止・破棄 |
| `loadtest:<case>:k8s` | Kubernetes 版をデプロイ |
| `loadtest:<case>:k8s:open` | Kubernetes 版の Web UI を port-forward して開く |
| `loadtest:<case>:k8s:delete` | Kubernetes 版を削除 |
| `loadtest:help` | 一覧とヘルプを表示 |

ES/CQRS のケース（case-3 / case-4）では、書き込み（コマンド）から Read Model への投影が非同期です。シード直後は一覧が空のことがあるため、デプロイ後しばらく待ってから負荷テストを実行してください。

---

## まとめ

- 第 12 章で学んだ Locust の負荷テストを、4 ケース（モノリス / イベント駆動 / ES・CQRS Axon / ES・CQRS Kafka）に適用しました
- シナリオは現行 Locust API（`HttpUser` / `@task` / `between`）で記述し、Compose 版・Kubernetes 版で共有します
- 認証方式の違い（セッション / JWT / 認可無効）に合わせ、副作用のない読み取り系 GET に負荷をかけるシナリオにしました
- Compose 版は `host.docker.internal` 経由でホスト公開ポートへ、Kubernetes 版は同一名前空間からクラスタ内サービスへ、それぞれ負荷をかけます
- 運用タスク（`loadtest:<case>`）として統合し、`dev:<case>` / `k8s:<case>` と同じ流れで「構築 → 負荷テスト → 破棄」を繰り返せます

---

- 前の章: [第 17 章 ケーススタディ実装比較まとめ](17-case-comparison-summary.md)
- シリーズ目次: [Docker/Kubernetes 実践コンテナ解説](index.md)
