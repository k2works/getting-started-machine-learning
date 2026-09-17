---
type: Article
title: "第 4 章 複数コンテナ構成でのアプリケーション構築"
description: "前章まででは、単一のコンテナを作り、動かし、イメージとして配布する方法を学びました。"
tags: [article, getting-start-docker-kubernetes]
status: stable
generated: { by: human:kakimomokuri, at: 2026-06-26T00:29:42Z }
---

# 第 4 章 複数コンテナ構成でのアプリケーション構築

![複数コンテナ構成のアプリケーションを説明する 4 コマ漫画](images/chapter-04-comic.png)

*Web、API、データベース、キャッシュ、プロキシを分け、ネットワークと永続化で 1 つのアプリケーションにします。*

## はじめに

前章まででは、単一のコンテナを作り、動かし、イメージとして配布する方法を学びました。しかし、実際の Web アプリケーションは、データベース、API サーバ、Web サーバ、リバースプロキシなど、役割の異なる複数のプロセスが連携して初めて動作します。これらをすべて 1 つのコンテナに詰め込んでしまうと、「1 コンテナ 1 プロセス」というコンテナの原則から外れ、スケールやデプロイ、障害の切り分けが難しくなります。

この章では、シンプルなタスク管理アプリケーション（taskapp）を題材に、役割ごとに分割した複数のコンテナを Docker Compose（`compose.yaml`）で 1 つのシステムとして組み上げる方法を学びます。題材のコードはすべて `taskapp` リポジトリ（本記事では `apps/taskapp` 配下）の実ファイルを引用しています。

この章で扱う内容は次の通りです。

- taskapp 全体のアーキテクチャと各サービスの役割
- MySQL コンテナの構築
- データベースマイグレータの構築
- API サーバ・Web サーバの構築（Go アプリケーション）
- リバースプロキシ（nginx）の構築
- `docker compose up` による全体起動の流れ（`depends_on` / `healthcheck` / `secrets` / `volumes`）
- Tilt による開発体験の向上
- 手動運用の限界と、第 5 章の Kubernetes への橋渡し

### 目次

1. [4.1 Web アプリケーションの構成](#41-web-アプリケーションの構成)
2. [4.2 MySQL の構築](#42-mysql-の構築)
3. [4.3 データベースマイグレータの構築](#43-データベースマイグレータの構築)
4. [4.4 API サーバと Web サーバの構築](#44-api-サーバと-web-サーバの構築)
5. [4.5 リバースプロキシの構築](#45-リバースプロキシの構築)
6. [4.6 複数コンテナ構成でタスクアプリを実行する](#46-複数コンテナ構成でタスクアプリを実行する)
7. [4.7 Tilt で複数コンテナ構成の開発体験を向上させる](#47-tilt-で複数コンテナ構成の開発体験を向上させる)
8. [4.8 コンテナオーケストレーションの基礎を経て](#48-コンテナオーケストレーションの基礎を経て)

---

## 4.1 Web アプリケーションの構成

taskapp は、ULID をキーに持つタスク（`task`）を BACKLOG / PROGRESS / DONE の 3 状態で管理する、シンプルなタスク管理アプリケーションです。アプリケーションは「役割」ごとに 6 つのサービス（コンテナ）に分割されています。

### 全体アーキテクチャ

ブラウザからのリクエストは、外側のリバースプロキシ（nginx-web）から順に内側へ流れ、最終的に MySQL に到達します。マイグレータ（migrator）はアプリケーションの起動経路には含まれず、初回にスキーマを整えるための初期化ジョブとして一度だけ実行されます。

```plantuml
@startuml
title taskapp の複数コンテナ構成

actor ブラウザ as Browser

component "nginx-web\n(リバースプロキシ)\n:9280→80" as NginxWeb
component "web\n(Web サーバ / Go)\n:8280" as Web
component "nginx-api\n(リバースプロキシ)\n:9180→80" as NginxApi
component "api\n(API サーバ / Go)\n:8180" as Api
database "mysql\n(MySQL 8.0)\n:3306" as Mysql
component "migrator\n(初期化ジョブ)" as Migrator

Browser --> NginxWeb : HTTP
NginxWeb --> Web : proxy_pass
Web --> NginxApi : --api-address
NginxApi --> Api : proxy_pass
Api --> Mysql : SQL
Migrator ..> Mysql : migrate up\n(初回のみ)

note right of NginxWeb
  静的ファイル(/assets/)は
  nginx-web が直接配信
end note
@enduml
```

### 各サービスの役割

`apps/taskapp/compose.yaml` で定義されている 6 つのサービスの役割を整理します。

| サービス | 役割 | 公開ポート（ホスト→コンテナ） |
| :--- | :--- | :--- |
| `mysql` | データの永続化を担う MySQL データベース | 3306→3306 |
| `migrator` | スキーマ作成・初期データ投入を行う初期化ジョブ | なし |
| `api` | タスクの CRUD を提供する API サーバ（Go） | なし（内部 8180） |
| `nginx-api` | api への振り分けを行うリバースプロキシ | 9180→80 |
| `web` | 画面を生成する Web サーバ（Go） | なし（内部 8280） |
| `nginx-web` | web への振り分けと静的ファイル配信を行うリバースプロキシ | 9280→80 |

この「役割ごとにコンテナを分ける」という設計が、複数コンテナ構成の出発点です。各コンテナは単一の責務を持ち、それぞれ独立してビルド・起動・スケールできます。以降の節では、内側のデータベースから外側のリバースプロキシへ向かって、1 つずつコンテナを構築していきます。

---

## 4.2 MySQL の構築

最初に、システムの一番奥にあるデータストア、MySQL コンテナを構築します。MySQL は公式イメージがそのまま使えますが、taskapp ではスロークエリログの設定を追加するため、独自の Dockerfile でイメージをビルドしています。

### Dockerfile

`apps/taskapp/containers/mysql/Dockerfile` は、公式の `mysql:8.0.33` をベースに、設定ファイルをコピーするだけのシンプルな構成です。

```dockerfile
FROM mysql:8.0.33

COPY ./etc/mysql/conf.d /etc/mysql/conf.d
```

MySQL 公式イメージは、`/etc/mysql/conf.d` 配下に置かれた `.cnf` ファイルを起動時に読み込みます。この仕組みを利用して、設定ファイルを後から「足す」だけでチューニングできるようにしています。

### スロークエリログの設定

`apps/taskapp/containers/mysql/etc/mysql/conf.d/slowlog.cnf` で、実行に時間のかかったクエリを記録するスロークエリログを有効化しています。

```ini
[mysqld]
slow_query_log = on
slow_query_log_file = /var/log/mysql/mysql-slow.log
long_query_time = 1
log_queries_not_using_indexes = on
```

各設定の意味は次の通りです。

- `slow_query_log = on` — スロークエリログを有効にします。
- `slow_query_log_file` — ログの出力先ファイルを指定します。
- `long_query_time = 1` — 実行に 1 秒以上かかったクエリを「遅い」とみなして記録します。
- `log_queries_not_using_indexes = on` — インデックスを使わずに実行されたクエリも記録します。

開発段階からスロークエリログを有効にしておくことで、後述する `idx_status` インデックスのような性能改善の必要性に早く気付けます。「動く」だけでなく「変更を楽に安全にできる」状態を保つための、ささやかですが重要な仕込みです。

### 環境変数とデータの永続化（compose.yaml の抜粋）

`apps/taskapp/compose.yaml` での `mysql` サービスの定義は次の通りです。

```yaml
  mysql:
    build:
      context: ./containers/mysql
    environment:
      MYSQL_ROOT_PASSWORD_FILE: /run/secrets/mysql_root_password
      MYSQL_DATABASE: taskapp
      MYSQL_USER: taskapp_user
      MYSQL_PASSWORD_FILE: /run/secrets/mysql_user_password
    secrets:
      - mysql_root_password
      - mysql_user_password
    volumes:
      - mysql_data:/var/lib/mysql
    ports:
      - "3306:3306"
```

ここで注目すべき点が 3 つあります。

1. **パスワードをファイルで渡す** — `MYSQL_ROOT_PASSWORD` に直接値を書くのではなく、`MYSQL_ROOT_PASSWORD_FILE` で `/run/secrets/...` のパスを指定しています。パスワードの実体は後述の `secrets` で安全に注入されます。
2. **データの永続化** — `mysql_data` という名前付きボリュームを `/var/lib/mysql` にマウントしています。これにより、コンテナを作り直してもデータベースの中身が消えません。
3. **データベースとユーザーの自動作成** — `MYSQL_DATABASE` と `MYSQL_USER` を指定することで、初回起動時に `taskapp` データベースと `taskapp_user` ユーザーが自動的に作られます。

`secrets` と `volumes` の全体定義は [4.6](#46-複数コンテナ構成でタスクアプリを実行する) でまとめて扱います。

---

## 4.3 データベースマイグレータの構築

MySQL コンテナが起動しただけでは、まだ空のデータベースがあるにすぎません。アプリケーションが必要とするテーブルやインデックスを用意するのが、マイグレータ（migrator）の役割です。マイグレータは常駐するサービスではなく、起動して仕事を終えたら停止する「初期化ジョブ」として動きます。

### Dockerfile

`apps/taskapp/containers/migrator/Dockerfile` は、Go のイメージをベースに、MySQL クライアントとマイグレーションツール `golang-migrate` をインストールしています。

```dockerfile
FROM golang:1.21.6

WORKDIR /migrator

RUN apt update
RUN apt install -y default-mysql-client
RUN go install -tags 'mysql' github.com/golang-migrate/migrate/v4/cmd/migrate@v4.17.0

COPY . .
```

- `default-mysql-client` — MySQL の起動待ち確認（後述の `migrate.sh`）に使う `mysql` コマンドを導入します。
- `golang-migrate` — バージョン番号付きの SQL ファイルを順に適用する、デファクトスタンダードのマイグレーションツールです。
- `COPY . .` — `history/` 配下のマイグレーション SQL や `migrate.sh` をイメージに取り込みます。

### マイグレーション SQL（history/）

マイグレーションは `apps/taskapp/containers/migrator/history/` に、`<連番>_<名前>.up.sql` / `.down.sql` のペアで配置されています。`up` は適用（前進）、`down` は取り消し（後退）を表します。

まず `1001_init.up.sql` で、アプリケーションの中心となる `task` テーブルを作成します。

```sql
CREATE TABLE task
(
    `id`      CHAR(26)     NOT NULL COMMENT 'ULID 26bytes',
    `title`   VARCHAR(191) NOT NULL COMMENT 'タイトル',
    `content` TEXT         NOT NULL COMMENT '内容',
    `status`  ENUM('BACKLOG', 'PROGRESS', 'DONE') NOT NULL COMMENT 'ステータス',
    `created` DATETIME     NOT NULL COMMENT '作成時間',
    `updated` DATETIME     NOT NULL COMMENT '更新時間',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`id` は ULID（26 文字）を主キーとし、`status` はタスクの状態を表す ENUM 型です。対応する `1001_init.down.sql` は、このテーブルを削除します。

```sql
DROP TABLE IF EXISTS `task`;
```

次の `1002_index_status.up.sql` では、状態での絞り込みを高速化するため `status` 列にインデックスを追加します。4.2 で有効化したスロークエリログの `log_queries_not_using_indexes` が、このインデックスの必要性に気付くきっかけになります。

```sql
ALTER TABLE task ADD INDEX idx_status (`status`);
```

`1002_index_status.down.sql` はこのインデックスを取り消します。

```sql
ALTER TABLE task DROP INDEX idx_status;
```

最後に `1003_test_data.up.sql` で、開発・動作確認用のテストデータを投入します。

```sql
INSERT INTO task (id, title, content, status, created, updated)
VALUES ('01H4QEZ39FBP67SS9V042ZJ5H1', 'Dockerのインストール', 'Docker Desktopでのローカル開発環境準備', 'DONE', NOW(), NOW()),
       ('01H4QEZ39FZVW6Y6HVQDHQ192K', 'asdfのインストール', 'asdfでのツールの管理', 'DONE', NOW(), NOW()),
       ('01H4QEZ39F0MCJERZ7BFHSG92E', 'Kubernetesの検証', 'Kubernetesをどのように導入するか', 'PROGRESS', NOW(), NOW()),
       -- ... 以下、テストデータが続く（apps/taskapp/containers/migrator/history/1003_test_data.up.sql 参照）
       ('01H4QEZ39FKNA78DZQ6CGSCJJM', 'OrbStackの検証', 'OrbStackでの開発環境を検証', 'BACKLOG', NOW(), NOW());
```

このように、スキーマの変更を「足し算」できる SQL ファイルの積み重ねとして管理することで、データベースの変更履歴がコードとして残り、誰がいつ実行しても同じ状態を再現できます。

### migrate.sh

マイグレーションの実行手順は `apps/taskapp/containers/migrator/migrate.sh` にまとめられています。

```bash
#!/usr/bin/env bash

set -o errexit
set -o nounset
set -o pipefail

if [ "$#" -ne 6 ]; then
  echo "usage: $0 <db_host> <db_port> <db_name> <username> <password> <command>"
  exit 1
fi

db_host=$1
db_port=$2
db_name=$3
db_username=$4

if [ -e "$5" ]; then
  db_password=`cat $5`
else
  db_password=$5
fi

command=$6

echo "Waiting for MySQL to start..."
until mysql -h $db_host -P $db_port -u $db_username -p$db_password -e "show databases;" &> /dev/null; do
  >&2 echo "MySQL is unavailable - sleeping"
  sleep 1
done
echo "MySQL is up - executing command"

migrate -path ./history -database mysql://$db_username:$db_password@tcp\($db_host:$db_port\)/$db_name $command
```

このスクリプトには、初期化ジョブを安全に実行するための工夫が詰まっています。

- `set -o errexit / nounset / pipefail` — エラーや未定義変数があれば即座に失敗させ、問題を握りつぶしません。
- **パスワードのファイル対応** — 第 5 引数がファイルパスとして存在すれば、その中身をパスワードとして読み込みます（`/run/secrets/...` を渡すケース）。これにより 4.6 の `secrets` と連携できます。
- **MySQL の起動待ち** — `until mysql ... ; do sleep 1; done` のループで、MySQL が応答するまで待ってからマイグレーションを実行します。コンテナの起動順序だけでは「起動済み」と「受付可能」のズレが残るため、このリトライが信頼性を支えます。
- `migrate -path ./history` — `history/` 配下の SQL を連番順に適用します。最後の `$command`（`up` など）で前進・後退を切り替えます。

`compose.yaml` 側では、この `migrate.sh` を `up` コマンドで呼び出しています（抜粋）。

```yaml
  migrator:
    build:
      context: ./containers/migrator
    depends_on:
      - mysql
    environment:
      DB_HOST: mysql
      DB_NAME: taskapp
      DB_PORT: "3306"
      DB_USERNAME: taskapp_user
    command: >
        sh -c '
            bash /migrator/migrate.sh $$DB_HOST $$DB_PORT $$DB_NAME $$DB_USERNAME /run/secrets/mysql_user_password up
        '
    secrets:
      - mysql_user_password
```

`DB_HOST: mysql` のように、接続先をサービス名で指定している点に注目してください。Docker Compose は、同じネットワーク上のサービス名を名前解決できるため、IP アドレスを知らなくても `mysql` という名前で MySQL コンテナに接続できます。これが複数コンテナ間連携の基本です。

---

## 4.4 API サーバと Web サーバの構築

データベースの準備ができたら、その上で動くアプリケーション本体、API サーバと Web サーバを構築します。taskapp はモノレポ構成で、Go で書かれた複数のアプリケーションを 1 つのコードベースから個別のバイナリとしてビルドします。

### Go アプリケーションの構成

エントリポイントは `apps/taskapp/cmd/` 配下に、`api` / `web` / `tools` の 3 つが用意されています。いずれも共通の CLI フレームワーク（`pkg/cli`）を使い、サブコマンドを登録する形になっています。

API サーバ（`apps/taskapp/cmd/api/main.go`）は、`server` と `config` のサブコマンドを持ちます。

```go
package main

import (
	"log"

	"github.com/gihyodocker/taskapp/pkg/app/api/cmd/config"
	"github.com/gihyodocker/taskapp/pkg/app/api/cmd/server"
	"github.com/gihyodocker/taskapp/pkg/cli"
)

func main() {
	c := cli.NewCLI("taskapp-api", "The API application of taskapp")
	c.AddCommands(
		server.NewCommand(),
		config.NewCommand(),
	)
	if err := c.Execute(); err != nil {
		log.Fatal(err)
	}
}
```

Web サーバ（`apps/taskapp/cmd/web/main.go`）は `server` サブコマンドのみを持つ、よりシンプルな構成です。

```go
package main

import (
	"log"

	"github.com/gihyodocker/taskapp/pkg/app/web/cmd/server"
	"github.com/gihyodocker/taskapp/pkg/cli"
)

func main() {
	c := cli.NewCLI("taskapp-web", "The web application of taskapp")
	c.AddCommands(
		server.NewCommand(),
	)
	if err := c.Execute(); err != nil {
		log.Fatal(err)
	}
}
```

なお、`apps/taskapp/cmd/tools/main.go` は MySQL 関連のユーティリティをまとめた補助ツールで、開発時のメンテナンス作業に使います。

```go
package main

import (
	"log"

	"github.com/gihyodocker/taskapp/pkg/app/tools/cmd/mysql"
	"github.com/gihyodocker/taskapp/pkg/cli"
)

func main() {
	c := cli.NewCLI("taskapp-tools", "The utility tools of taskapp")
	c.AddCommands(
		mysql.NewCommand(),
	)
	if err := c.Execute(); err != nil {
		log.Fatal(err)
	}
}
```

### API サーバの Dockerfile

`apps/taskapp/containers/api/Dockerfile` は、Go のソースをコンテナ内でビルドして実行する構成です。

```dockerfile
FROM golang:1.21.6

WORKDIR /go/src/github.com/gihyodocker/taskapp

COPY ./cmd ./cmd
COPY ./pkg ./pkg
COPY go.mod .
COPY go.sum .
COPY Makefile .

RUN make mod
RUN make vendor
RUN make build-api

ENTRYPOINT ["./bin/api"]
```

必要なソース（`cmd` / `pkg` / `go.mod` / `go.sum` / `Makefile`）だけをコピーし、`make` 経由で依存解決とビルドを行い、生成した `./bin/api` を `ENTRYPOINT` に設定しています。`compose.yaml` の `command` で `server --config-file=...` を渡すことで、`ENTRYPOINT`（バイナリ）＋ `command`（引数）の形で API サーバが起動します。

### Web サーバの Dockerfile

`apps/taskapp/containers/web/Dockerfile` は API とほぼ同じですが、画面用の静的ファイルである `assets` を追加でコピーする点が異なります。

```dockerfile
FROM golang:1.21.6

WORKDIR /go/src/github.com/gihyodocker/taskapp

COPY ./cmd ./cmd
COPY ./pkg ./pkg
COPY go.mod .
COPY go.sum .
COPY Makefile .
COPY ./assets ./assets

RUN make mod
RUN make vendor
RUN make build-web

ENTRYPOINT ["./bin/web"]
```

この `assets` は後ほど名前付きボリューム経由で nginx-web と共有され、静的ファイルを nginx 側から直接配信できるようにします（[4.5](#45-リバースプロキシの構築) / [4.6](#46-複数コンテナ構成でタスクアプリを実行する) 参照）。

### サービス間の接続

`compose.yaml` における api / web の定義（抜粋）を見ると、サービス同士がサービス名で接続していることがわかります。

```yaml
  api:
    build:
      context: .
      dockerfile: ./containers/api/Dockerfile
    depends_on:
      - mysql
    command:
      - "server"
      - "--config-file=/run/secrets/api_config"
    secrets:
      - api_config

  web:
    build:
      context: .
      dockerfile: ./containers/web/Dockerfile
    depends_on:
      - nginx-api
    command:
      - "server"
      - "--api-address=http://nginx-api:80"
    volumes:
      - assets_data:/go/src/github.com/gihyodocker/taskapp/assets
```

ここで重要なのは、web が API に直接つながるのではなく、`--api-address=http://nginx-api:80` のように **nginx-api（リバースプロキシ）経由で接続している** 点です。アプリケーションは「具体的な API インスタンス」ではなく「プロキシという窓口」を相手にするため、API 側を増減させてもクライアントの設定を変える必要がありません。この間接化が、次節のリバースプロキシの存在意義につながります。

なお、api は接続情報などを `--config-file=/run/secrets/api_config` から読み込みます。設定ファイルもパスワードと同様に `secrets` として安全に注入されます。

---

## 4.5 リバースプロキシの構築

taskapp には、`nginx-api` と `nginx-web` という 2 つのリバースプロキシがあります。リバースプロキシは、クライアントとアプリケーションサーバの間に立ち、リクエストを適切なバックエンドへ振り分ける役割を担います。これにより、バックエンドの台数や配置を隠蔽し、ロードバランスやアクセスログの一元管理が可能になります。

### Dockerfile

`apps/taskapp/containers/nginx-api/Dockerfile` と `apps/taskapp/containers/nginx-web/Dockerfile` は同一で、公式 nginx イメージに設定ファイルをコピーし、不要なデフォルト設定を削除するだけです。

```dockerfile
FROM nginx:1.25.1

COPY ./etc/nginx /etc/nginx
RUN rm /etc/nginx/conf.d/default.conf
```

### テンプレートによる設定

nginx 公式イメージには、起動時に `/etc/nginx/templates/*.template` 内の `${VAR}` を環境変数で置換し、`/etc/nginx/conf.d/` に展開する仕組みがあります。taskapp はこれを活用し、設定を「ログ定義」「アップストリーム定義」「バーチャルホスト定義」の 3 つのテンプレートに分けています。ファイル名の連番（10 / 20 / 30）は展開・読み込み順を表します。

#### 10-log.conf.template（ログ定義）

`apps/taskapp/containers/nginx-api/etc/nginx/templates/10-log.conf.template`（nginx-web 側も同一）では、構造化された JSON 形式のアクセスログを定義しています。

```nginx
log_format json escape=json '{'
    '"time": "$time_local",'
    '"remote_addr": "$remote_addr",'
    '"host": "$host",'
    '"status": "$status",'
    '"request_method": "$request_method",'
    '"request_uri": "$request_uri",'
    '"request_time": "$request_time",'
    '"upstream_response_time": "$upstream_response_time",'
    '"http_user_agent": "$http_user_agent"'
    // ... 実際のフィールドはより多い（テンプレート全文を参照）
'}';
```

ログを JSON で出力しておくと、ログ収集基盤での解析やフィルタリングが容易になります。`upstream_response_time` を含めることで、バックエンドの応答性能も追跡できます。

#### 20-upstream.conf.template（アップストリーム定義）

`apps/taskapp/containers/nginx-api/etc/nginx/templates/20-upstream.conf.template`（nginx-web 側も同一）では、振り分け先のバックエンドを環境変数で定義しています。

```nginx
upstream backend {
   server ${BACKEND_HOST} max_fails=${BACKEND_MAX_FAILS} fail_timeout=${BACKEND_FAIL_TIMEOUT};
}
```

`max_fails` と `fail_timeout` により、バックエンドが連続して失敗した場合に一時的に振り分けから外す、簡易的なヘルスチェックとフェイルオーバーが効きます。`${BACKEND_HOST}` は `compose.yaml` の環境変数（nginx-api なら `api:8180`、nginx-web なら `web:8280`）で注入されます。

#### 30-vhost.conf.template（バーチャルホスト定義）

API 用の `apps/taskapp/containers/nginx-api/etc/nginx/templates/30-vhost.conf.template` は、すべてのリクエストを `backend`（= api）へ転送します。

```nginx
server {
    listen ${NGINX_PORT};
    server_name ${SERVER_NAME};

    location / {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        access_log /dev/stdout json;
        error_log  /dev/stderr;
    }
}
```

一方、Web 用の `apps/taskapp/containers/nginx-web/etc/nginx/templates/30-vhost.conf.template` には、静的ファイルを nginx が直接配信する `location /assets/` が追加されています。

```nginx
server {
    listen ${NGINX_PORT};
    server_name ${SERVER_NAME};

    location /assets/ {
        alias ${ASSETS_DIR}/;
        access_log /dev/stdout json;
        error_log  /dev/stderr;
    }

    location / {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $remote_addr;
        access_log /dev/stdout json;
        error_log  /dev/stderr;
    }
}
```

`/assets/` 以下へのリクエストは Web サーバ（Go）まで届かせず、nginx-web が `${ASSETS_DIR}`（= 共有ボリューム `assets_data`）から直接返します。画像や CSS のような静的ファイルはアプリケーションを通さない方が高速で、アプリケーションサーバの負荷も下がります。アクセスログ・エラーログは標準出力・標準エラー（`/dev/stdout` / `/dev/stderr`）に流しているため、`docker compose logs` でそのまま確認できます。

---

## 4.6 複数コンテナ構成でタスクアプリを実行する

ここまでで 6 つのコンテナの中身が揃いました。最後に、それらを 1 つのシステムとして束ねる `apps/taskapp/compose.yaml` の全体像を確認し、`docker compose up` で起動する流れを追います。

### compose.yaml の全体

```yaml
version: '3.9'
services:

  mysql:
    build:
      context: ./containers/mysql
    environment:
      MYSQL_ROOT_PASSWORD_FILE: /run/secrets/mysql_root_password
      MYSQL_DATABASE: taskapp
      MYSQL_USER: taskapp_user
      MYSQL_PASSWORD_FILE: /run/secrets/mysql_user_password
    secrets:
      - mysql_root_password
      - mysql_user_password
    volumes:
      - mysql_data:/var/lib/mysql
    ports:
      - "3306:3306"

  migrator:
    build:
      context: ./containers/migrator
    depends_on:
      - mysql
    environment:
      DB_HOST: mysql
      DB_NAME: taskapp
      DB_PORT: "3306"
      DB_USERNAME: taskapp_user
    command: >
        sh -c '
            bash /migrator/migrate.sh $$DB_HOST $$DB_PORT $$DB_NAME $$DB_USERNAME /run/secrets/mysql_user_password up
        '
    secrets:
      - mysql_user_password

  api:
    build:
      context: .
      dockerfile: ./containers/api/Dockerfile
    depends_on:
      - mysql
    healthcheck:
      test: "curl -f http://localhost:8180/healthz || exit 1"
      interval: 10s
      timeout: 10s
      retries: 3
      start_period: 30s
    command:
      - "server"
      - "--config-file=/run/secrets/api_config"
    secrets:
      - api_config

  nginx-api:
    build:
      context: ./containers/nginx-api
    depends_on:
      api:
        condition: service_healthy
    healthcheck:
      test: "curl -H 'Host: api' -f http://localhost:80/healthz || exit 1"
      interval: 10s
      timeout: 10s
      retries: 3
      start_period: 30s
    environment:
      NGINX_PORT: 80
      SERVER_NAME: api
      BACKEND_HOST: api:8180
      BACKEND_MAX_FAILS: 3
      BACKEND_FAIL_TIMEOUT: 10s
    ports:
      - "9180:80"

  web:
    build:
      context: .
      dockerfile: ./containers/web/Dockerfile
    depends_on:
      - nginx-api
    healthcheck:
      test: "curl -f http://localhost:8280/healthz || exit 1"
      interval: 10s
      timeout: 10s
      retries: 3
      start_period: 30s
    command:
      - "server"
      - "--api-address=http://nginx-api:80"
    volumes:
      - assets_data:/go/src/github.com/gihyodocker/taskapp/assets

  nginx-web:
    build:
      context: ./containers/nginx-web
    depends_on:
      web:
        condition: service_healthy
    healthcheck:
      test: "curl -f http://localhost:80/healthz || exit 1"
      interval: 10s
      timeout: 10s
      retries: 3
      start_period: 30s
    environment:
      NGINX_PORT: 80
      SERVER_NAME: localhost
      ASSETS_DIR: /var/www/assets
      BACKEND_HOST: web:8280
      BACKEND_MAX_FAILS: 3
      BACKEND_FAIL_TIMEOUT: 10s
    ports:
      - "9280:80"
    volumes:
      - assets_data:/var/www/assets

secrets:
  mysql_root_password:
    file: ./secrets/mysql_root_password
  mysql_user_password:
    file: ./secrets/mysql_user_password
  api_config:
    file: ./api-config.yaml

volumes:
  mysql_data:
  assets_data:
```

### depends_on と healthcheck で起動順序を制御する

複数コンテナ構成で厄介なのが「起動順序」です。MySQL が受付可能になる前に api が接続しようとすると失敗します。taskapp はこの問題を `depends_on` と `healthcheck` の組み合わせで解決しています。

- 単純な `depends_on:`（リスト形式）は「コンテナの起動」までしか待ちません。`migrator` と `api` は `mysql` に対してこの形を使い、実際の受付可能の保証は `migrate.sh` のリトライや api 側の接続リトライに委ねています。
- 一方、`nginx-api` は次のように **ヘルスチェックの成功** を待ってから起動します。

```yaml
  nginx-api:
    depends_on:
      api:
        condition: service_healthy
```

api 側には、応答可能かを確認する `healthcheck` が定義されています。

```yaml
    healthcheck:
      test: "curl -f http://localhost:8180/healthz || exit 1"
      interval: 10s
      timeout: 10s
      retries: 3
      start_period: 30s
```

`start_period: 30s` は起動直後の猶予期間で、この間の失敗はリトライ回数に数えません。`/healthz` への `curl` が成功して初めて「healthy」と判定され、`nginx-api` の起動が始まります。同様に web は nginx-api の healthy を、nginx-web は web の healthy を待ちます。こうして「内側から外側へ、準備ができたものから順に」起動する連鎖が組み上がります。

```plantuml
@startuml
title 起動順序の連鎖（depends_on / healthcheck）

mysql -down-> migrator : 起動後
mysql -down-> api : 起動後\n(接続リトライ)
api -down-> nginx-api : service_healthy
nginx-api -down-> web : 起動後
web -down-> nginx-web : service_healthy
@enduml
```

### secrets で機密情報を安全に渡す

パスワードや設定ファイルは、イメージや環境変数に直接埋め込むと漏洩リスクが高まります。taskapp は Compose の `secrets` を使い、ホスト上のファイルをコンテナ内の `/run/secrets/` にマウントします。

```yaml
secrets:
  mysql_root_password:
    file: ./secrets/mysql_root_password
  mysql_user_password:
    file: ./secrets/mysql_user_password
  api_config:
    file: ./api-config.yaml
```

各サービスは必要な secret だけを参照します。たとえば mysql は 2 つのパスワード、migrator は `mysql_user_password`、api は `api_config` を参照します。MySQL の `..._PASSWORD_FILE` や migrate.sh の「ファイルなら中身を読む」処理は、この secrets と噛み合うように設計されています。なお、`./secrets/...` や `./api-config.yaml` といったファイルの実体は環境ごとに用意するものであり、リポジトリには含めず別途配置します。

### volumes でデータと静的ファイルを扱う

ボリュームは 2 種類使われています。

- `mysql_data` — MySQL のデータを永続化します（コンテナを作り直しても消えない）。
- `assets_data` — web がビルド時に持つ `assets` を、nginx-web と共有するために使います。web 側は `/go/src/.../assets`、nginx-web 側は `/var/www/assets` にマウントし、同じボリュームを介して静的ファイルを受け渡します。これが [4.5](#45-リバースプロキシの構築) の `location /assets/` を成立させる仕組みです。

### 起動と動作確認

すべての準備が整ったら、`taskapp` ディレクトリで次のコマンドを実行します（出力は環境により異なります）。

```bash
# イメージをビルドして全サービスを起動する（例）
$ docker compose up --build

# 起動状況を別ターミナルで確認する（例）
$ docker compose ps
```

起動が完了すると、外側のリバースプロキシ nginx-web がホストの 9280 番ポートで待ち受けています。ブラウザで `http://localhost:9280` を開くと、4.3 で投入したテストデータを含むタスク一覧が表示されます。リクエストは `nginx-web → web → nginx-api → api → mysql` の順に流れ、これまで構築してきた 6 つのコンテナが 1 つのアプリケーションとして連携していることが確認できます。停止と後始末は次の通りです。

```bash
# 停止のみ（ボリュームは残る）
$ docker compose down

# ボリュームも含めて削除する（データを完全に消す）
$ docker compose down -v
```

---

## 4.7 Tilt で複数コンテナ構成の開発体験を向上させる

`docker compose up` だけでも開発はできますが、ソースを変更するたびに手動で再ビルド・再起動するのは手間です。taskapp では、開発体験を向上させるツールとして Tilt を併用しています。

### Tiltfile

`apps/taskapp/Tiltfile` は非常に簡潔で、既存の `compose.yaml` をそのまま取り込む構成になっています。

```python
config.define_string_list("to-run", args=True)
cfg = config.parse()

docker_compose("./compose.yaml")
```

`docker_compose("./compose.yaml")` の一行で、Compose で定義した 6 サービスをすべて Tilt の管理下に置けます。既存の Compose 定義を捨てずに、その上に開発支援を重ねられるのが利点です。`config.define_string_list("to-run", ...)` は、起動対象のサービスを引数で絞り込めるようにするための定義です。

### Tilt の利点

Tilt を `tilt up` で起動すると、次のような開発支援が得られます。

- **ライブリロード** — ソースコードの変更を検知し、対象サービスだけを自動で再ビルド・再起動します。手動の `docker compose up --build` を繰り返す必要がなくなります。
- **Web ダッシュボード** — ブラウザ上の UI で、各サービスの起動状態・ビルド状況・ログを一覧できます。どのコンテナでエラーが起きているかを素早く把握できます。
- **ログの集約** — 6 つのコンテナのログがサービスごとに整理されて表示され、問題の切り分けが容易になります。

複数コンテナ構成では、変更のたびに全体を作り直すと待ち時間が積み上がります。Tilt によって「変更したものだけを素早く反映する」フィードバックループを短くすることは、まさに「変更を楽にできる」開発環境を整えることに直結します。

---

## 4.8 コンテナオーケストレーションの基礎を経て

この章では、6 つのコンテナを Docker Compose で 1 つのアプリケーションとして組み上げました。役割ごとの分割、サービス名による接続、`depends_on` / `healthcheck` による起動順序の制御、`secrets` / `volumes` による機密情報とデータの扱い、そして Tilt による開発体験の向上まで、複数コンテナ構成の基礎を一通り体験しました。

しかし、ここで構築したのはあくまで「1 台のマシン上で、開発者が手動で立ち上げる」構成です。実運用を見据えると、Docker Compose による手動運用には次のような限界があります。

- **スケール** — アクセスが増えても、api や web のインスタンス数を自動で増減できません。負荷に応じた水平スケールは手作業になります。
- **自己修復** — コンテナが異常終了したとき、Compose は健全な状態へ自動で戻してはくれません。誰かが気付いて再起動する必要があります。
- **ロードバランス** — nginx の `upstream` で簡易的な振り分けはできますが、複数台のホストにまたがってインスタンスを分散配置し、その増減に追随する仕組みはありません。
- **マルチホスト** — `compose.yaml` は基本的に 1 ホストを前提とします。複数サーバへコンテナを分散して配置する運用には向きません。

```plantuml
@startuml
title Docker Compose（手動運用）の限界

rectangle "1 ホスト上の Docker Compose" {
  component "api ×1" as A
  component "web ×1" as W
}

note bottom of A
  ・負荷が増えても自動でスケールしない
  ・落ちても自動で復旧しない
  ・複数ホストへ分散できない
end note
@enduml
```

これらの課題、すなわち**宣言的な望ましい状態の維持、自動スケール、自己修復、ロードバランス、マルチホストへの分散配置**を引き受けるのが、コンテナオーケストレーションツールである Kubernetes です。本章で組み上げた taskapp の各コンテナ（mysql / migrator / api / nginx-api / web / nginx-web）は、次章でそのまま Kubernetes 上のリソースへとマッピングされていきます。Compose で「何が」「どう連携するか」を理解した今、その知識は Kubernetes を学ぶ確かな足場になります。

次章では、いよいよ Kubernetes に入り、ここで作ったアプリケーションをオーケストレーションする方法を学んでいきましょう。

---

## まとめ

この章では、複数コンテナで構成される Web アプリケーション taskapp を、Docker Compose で組み上げる方法を学びました。

1. **役割ごとの分割** — mysql / migrator / api / nginx-api / web / nginx-web の 6 サービスに分け、それぞれを単一責務のコンテナとして構築しました。
2. **MySQL の構築** — 公式イメージに設定を足し、スロークエリログ（`slowlog.cnf`）を有効化しました。
3. **マイグレータ** — `golang-migrate` と `migrate.sh`、`history/` の `up` / `down` SQL によって、スキーマ変更をコードとして管理する初期化ジョブを構築しました。
4. **API / Web サーバ** — Go のモノレポからバイナリをビルドし、web は nginx-api 経由で api に接続することで疎結合を保ちました。
5. **リバースプロキシ** — nginx のテンプレート（10-log / 20-upstream / 30-vhost）で、JSON ログ・アップストリーム・静的ファイル配信を構成しました。
6. **全体の起動** — `compose.yaml` の `depends_on` / `healthcheck` / `secrets` / `volumes` を使い、内側から外側へ安全に起動する仕組みを作りました。
7. **Tilt** — 既存の Compose 定義を取り込み、ライブリロードとダッシュボードで開発のフィードバックループを短縮しました。
8. **手動運用の限界** — スケール・自己修復・ロードバランス・マルチホストの課題を整理し、次章の Kubernetes への橋渡しを行いました。

本章で扱ったコードはすべて `taskapp` リポジトリ（`compose.yaml`、`Tiltfile`、`containers/`、`cmd/` 配下）の実ファイルです。複数コンテナ構成の「型」を手で組み上げた経験は、コンテナオーケストレーションを理解するうえで欠かせない土台になります。

---

- 前の章: [第 3 章 実用的なコンテナの構築とデプロイ](03-practical-container-build-deploy.md)
- 次の章: [第 5 章 Kubernetes 入門](05-kubernetes-introduction.md)
