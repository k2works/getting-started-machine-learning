---
type: Article
title: "第38章：API 設計とサービス連携"
description: "本章では、基幹業務システムにおける API 設計の原則と、サービス間連携のパターンについて解説します。"
tags: [article, practical-database-design, part5]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-30T08:34:28Z }
---

# 第38章：API 設計とサービス連携

本章では、基幹業務システムにおける API 設計の原則と、サービス間連携のパターンについて解説します。RESTful API の設計、サービス間通信の方式、API ゲートウェイの活用、そしてインテグレーションテストの実践方法を学びます。

---

## 38.1 API 設計の原則

### RESTful API の設計

REST（Representational State Transfer）は、Web API 設計の標準的なアーキテクチャスタイルです。

```plantuml
@startuml
title RESTful API の基本原則

rectangle "REST の制約" as constraints {
    rectangle "クライアント-サーバー" as cs {
        note right
          関心の分離
          独立した進化
        end note
    }

    rectangle "ステートレス" as stateless {
        note right
          各リクエストは独立
          セッション状態を保持しない
        end note
    }

    rectangle "キャッシュ可能" as cacheable {
        note right
          レスポンスにキャッシュ可否を明示
          パフォーマンス向上
        end note
    }

    rectangle "統一インターフェース" as uniform {
        note right
          リソース識別（URI）
          表現によるリソース操作
          自己記述メッセージ
          HATEOAS
        end note
    }

    rectangle "階層化システム" as layered {
        note right
          中間サーバーの追加が可能
          ロードバランサー、キャッシュ等
        end note
    }
}

@enduml
```

#### HTTP メソッドとリソース操作

| HTTP メソッド | 操作 | 冪等性 | 安全性 | 使用例 |
|-------------|-----|-------|-------|-------|
| GET | 取得 | Yes | Yes | リソースの参照 |
| POST | 作成 | No | No | 新規リソース作成 |
| PUT | 置換 | Yes | No | リソース全体の更新 |
| PATCH | 部分更新 | No | No | リソースの一部更新 |
| DELETE | 削除 | Yes | No | リソースの削除 |

### リソース指向設計

API はリソース（名詞）を中心に設計し、操作は HTTP メソッドで表現します。

```plantuml
@startuml
title リソース指向 API 設計

package "販売管理 API" as sales_api {
    rectangle "/orders" as orders {
        rectangle "GET /orders" as get_orders
        rectangle "POST /orders" as post_order
        rectangle "GET /orders/{id}" as get_order
        rectangle "PUT /orders/{id}" as put_order
        rectangle "DELETE /orders/{id}" as delete_order
    }

    rectangle "/orders/{id}/lines" as order_lines {
        rectangle "GET /orders/{id}/lines" as get_lines
        rectangle "POST /orders/{id}/lines" as post_line
    }

    rectangle "/customers" as customers {
        rectangle "GET /customers" as get_customers
        rectangle "POST /customers" as post_customer
        rectangle "GET /customers/{id}" as get_customer
    }

    rectangle "/customers/{id}/orders" as customer_orders {
        rectangle "GET /customers/{id}/orders" as get_customer_orders
    }
}

note bottom of sales_api
  【設計原則】
  ・リソースは名詞（複数形）
  ・操作はHTTPメソッドで表現
  ・階層構造で関連を表現
  ・クエリパラメータでフィルタリング
end note

@enduml
```

#### 基幹業務システムの API エンドポイント設計

```plantuml
@startuml
title 基幹業務システム API エンドポイント

package "販売管理 API" as sales {
    rectangle "/api/v1/sales" as sales_base {
        rectangle "受注: /orders"
        rectangle "出荷: /shipments"
        rectangle "売上: /sales"
        rectangle "請求: /invoices"
        rectangle "入金: /payments"
    }
    rectangle "/api/v1/sales/masters" as sales_masters {
        rectangle "顧客: /customers"
        rectangle "商品: /products"
    }
}

package "財務会計 API" as accounting {
    rectangle "/api/v1/accounting" as acc_base {
        rectangle "仕訳: /journals"
        rectangle "残高: /balances"
        rectangle "試算表: /trial-balances"
    }
    rectangle "/api/v1/accounting/masters" as acc_masters {
        rectangle "勘定科目: /accounts"
        rectangle "補助科目: /sub-accounts"
    }
}

package "生産管理 API" as production {
    rectangle "/api/v1/production" as prod_base {
        rectangle "製造指示: /work-orders"
        rectangle "発注: /purchase-orders"
        rectangle "在庫: /inventory"
    }
    rectangle "/api/v1/production/masters" as prod_masters {
        rectangle "品目: /items"
        rectangle "BOM: /bom"
        rectangle "工程: /processes"
    }
}

@enduml
```

<details>
<summary>OpenAPI 定義例</summary>

```yaml
openapi: 3.0.3
info:
  title: 販売管理 API
  version: 1.0.0
  description: 基幹業務システム - 販売管理 API

servers:
  - url: https://api.example.com/api/v1/sales
    description: Production server

paths:
  /orders:
    get:
      summary: 受注一覧取得
      operationId: getOrders
      parameters:
        - name: customerId
          in: query
          schema:
            type: string
        - name: status
          in: query
          schema:
            type: string
            enum: [DRAFT, CONFIRMED, SHIPPED, COMPLETED]
        - name: fromDate
          in: query
          schema:
            type: string
            format: date
        - name: toDate
          in: query
          schema:
            type: string
            format: date
        - name: page
          in: query
          schema:
            type: integer
            default: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OrderListResponse'

    post:
      summary: 受注登録
      operationId: createOrder
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateOrderRequest'
      responses:
        '201':
          description: 作成成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OrderResponse'
        '400':
          description: バリデーションエラー
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'

  /orders/{orderId}:
    get:
      summary: 受注詳細取得
      operationId: getOrder
      parameters:
        - name: orderId
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OrderResponse'
        '404':
          description: 受注が見つからない

components:
  schemas:
    OrderResponse:
      type: object
      properties:
        orderId:
          type: string
        customerId:
          type: string
        customerName:
          type: string
        orderDate:
          type: string
          format: date
        status:
          type: string
          enum: [DRAFT, CONFIRMED, SHIPPED, COMPLETED]
        totalAmount:
          type: number
        taxAmount:
          type: number
        lines:
          type: array
          items:
            $ref: '#/components/schemas/OrderLineResponse'
        _links:
          $ref: '#/components/schemas/Links'

    CreateOrderRequest:
      type: object
      required:
        - customerId
        - orderDate
        - lines
      properties:
        customerId:
          type: string
        orderDate:
          type: string
          format: date
        requestedDeliveryDate:
          type: string
          format: date
        lines:
          type: array
          items:
            $ref: '#/components/schemas/CreateOrderLineRequest'
          minItems: 1

    ProblemDetail:
      type: object
      properties:
        type:
          type: string
        title:
          type: string
        status:
          type: integer
        detail:
          type: string
        instance:
          type: string
```

</details>

### バージョニング戦略

API の互換性を維持しながら進化させるためのバージョニング戦略を検討します。

```plantuml
@startuml
title API バージョニング戦略の比較

rectangle "URI パスバージョニング" as uri {
    note right
      /api/v1/orders
      /api/v2/orders

      【メリット】
      ・明確で分かりやすい
      ・キャッシュしやすい

      【デメリット】
      ・URIの変更が必要
    end note
}

rectangle "クエリパラメータ" as query {
    note right
      /api/orders?version=1
      /api/orders?version=2

      【メリット】
      ・URIが変わらない

      【デメリット】
      ・見落としやすい
    end note
}

rectangle "カスタムヘッダー" as header {
    note right
      X-API-Version: 1
      X-API-Version: 2

      【メリット】
      ・URIがクリーン

      【デメリット】
      ・テストしにくい
    end note
}

rectangle "Accept ヘッダー" as accept {
    note right
      Accept: application/vnd.company.v1+json
      Accept: application/vnd.company.v2+json

      【メリット】
      ・HTTP標準に準拠

      【デメリット】
      ・複雑
    end note
}

@enduml
```

#### 推奨：URI パスバージョニング

```plantuml
@startuml
title URI パスバージョニングの運用

rectangle "v1（現行）" as v1 {
    rectangle "/api/v1/orders" as v1_orders
    rectangle "/api/v1/customers" as v1_customers
}

rectangle "v2（新版）" as v2 {
    rectangle "/api/v2/orders" as v2_orders
    rectangle "/api/v2/customers" as v2_customers
}

note bottom of v1
  【v1 → v2 移行】
  1. v2を新規リリース
  2. 並行運用期間
  3. v1を非推奨化（Deprecation）
  4. v1を廃止（Sunset）

  【Deprecation ヘッダー】
  Deprecation: true
  Sunset: Sat, 01 Jan 2025 00:00:00 GMT
end note

@enduml
```

---

## 38.2 サービス間通信

### 同期通信（REST / gRPC）

```plantuml
@startuml
title 同期通信パターンの比較

rectangle "REST over HTTP" as rest {
    rectangle "販売サービス" as sales_rest
    rectangle "在庫サービス" as inv_rest

    sales_rest --> inv_rest : HTTP/JSON
}

rectangle "gRPC" as grpc {
    rectangle "販売サービス" as sales_grpc
    rectangle "在庫サービス" as inv_grpc

    sales_grpc --> inv_grpc : HTTP/2 + Protocol Buffers
}

note bottom of rest
  【REST】
  ・テキストベース（JSON）
  ・ブラウザから直接呼び出し可能
  ・広く普及、ツール豊富

  【適用場面】
  ・外部公開API
  ・フロントエンド連携
end note

note bottom of grpc
  【gRPC】
  ・バイナリプロトコル
  ・高速、低レイテンシ
  ・スキーマ定義必須

  【適用場面】
  ・マイクロサービス間
  ・高性能要件
end note

@enduml
```

<details>
<summary>gRPC 定義例</summary>

```protobuf
syntax = "proto3";

package sales.v1;

option java_package = "com.example.sales.grpc";
option java_multiple_files = true;

// 在庫サービス
service InventoryService {
  // 在庫照会
  rpc GetStock(GetStockRequest) returns (StockResponse);

  // 在庫引当
  rpc AllocateStock(AllocateStockRequest) returns (AllocateStockResponse);

  // 引当解除
  rpc ReleaseStock(ReleaseStockRequest) returns (ReleaseStockResponse);
}

message GetStockRequest {
  string product_id = 1;
  string warehouse_id = 2;
}

message StockResponse {
  string product_id = 1;
  string warehouse_id = 2;
  int32 quantity = 3;
  int32 allocated_quantity = 4;
  int32 available_quantity = 5;
}

message AllocateStockRequest {
  string product_id = 1;
  string warehouse_id = 2;
  int32 quantity = 3;
  string order_id = 4;
}

message AllocateStockResponse {
  bool success = 1;
  string allocation_id = 2;
  string error_message = 3;
}
```

</details>

### 非同期通信（メッセージキュー）

```plantuml
@startuml
title 非同期通信パターン

rectangle "販売サービス" as sales

rectangle "メッセージブローカー" as broker {
    queue "受注キュー" as order_queue
    collections "売上トピック" as sales_topic
}

rectangle "在庫サービス" as inventory
rectangle "会計サービス" as accounting
rectangle "通知サービス" as notification

sales --> order_queue : 受注メッセージ
order_queue --> inventory : 在庫引当

sales --> sales_topic : 売上イベント
sales_topic --> accounting : 仕訳生成
sales_topic --> notification : 通知送信
sales_topic --> inventory : 在庫更新

note bottom of broker
  【Point-to-Point】
  ・1対1の通信
  ・キューで実現
  ・負荷分散可能

  【Publish-Subscribe】
  ・1対多の通信
  ・トピックで実現
  ・イベント配信
end note

@enduml
```

<details>
<summary>Java 実装例（Spring AMQP）</summary>

```java
// メッセージ送信
@Service
public class OrderMessagePublisher {
    private final RabbitTemplate rabbitTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(
            "sales.exchange",
            "order.created",
            event
        );
    }

    public void publishSalesCompleted(SalesCompletedEvent event) {
        rabbitTemplate.convertAndSend(
            "sales.topic.exchange",
            "sales.completed",
            event
        );
    }
}

// メッセージ受信
@Component
public class OrderMessageListener {

    @RabbitListener(queues = "inventory.order.queue")
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 在庫引当処理
        log.info("Received order: {}", event.orderId());
        inventoryService.allocate(event);
    }
}

// 設定
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange salesTopicExchange() {
        return new TopicExchange("sales.topic.exchange");
    }

    @Bean
    public Queue accountingQueue() {
        return new Queue("accounting.sales.queue", true);
    }

    @Bean
    public Binding accountingBinding(Queue accountingQueue,
                                      TopicExchange salesTopicExchange) {
        return BindingBuilder.bind(accountingQueue)
            .to(salesTopicExchange)
            .with("sales.*");
    }
}
```

</details>

### サーキットブレーカーパターン

サービス間通信の障害を検知し、障害の連鎖を防ぐパターンです。

```plantuml
@startuml
title サーキットブレーカーの状態遷移

[*] --> Closed : 初期状態

Closed --> Open : 失敗が閾値を超過
Closed --> Closed : 成功

Open --> HalfOpen : タイムアウト後
Open --> Open : リクエスト拒否

HalfOpen --> Closed : 試行成功
HalfOpen --> Open : 試行失敗

note right of Closed
  【Closed】
  通常稼働
  リクエストを通す
end note

note right of Open
  【Open】
  障害検知
  即座にエラー返却
  （Fail Fast）
end note

note right of HalfOpen
  【Half-Open】
  復旧確認中
  限定的に試行
end note

@enduml
```

```plantuml
@startuml
title サーキットブレーカーの動作フロー

|クライアント|
start
:サービス呼び出し;

|サーキットブレーカー|
if (回路状態?) then (Open)
    :即座にフォールバック;
    |クライアント|
    :フォールバック結果;
    stop
else (Closed/HalfOpen)
endif

:下流サービス呼び出し;

|下流サービス|
if (処理成功?) then (yes)
    :正常レスポンス;
    |サーキットブレーカー|
    :成功をカウント;
    :Closedを維持/遷移;
else (no)
    :エラー/タイムアウト;
    |サーキットブレーカー|
    :失敗をカウント;

    if (失敗数 >= 閾値?) then (yes)
        :Openに遷移;
    endif
endif

|クライアント|
:結果を返却;

stop

@enduml
```

<details>
<summary>Java 実装例（Resilience4j）</summary>

```java
// 設定
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)  // 失敗率50%でOpen
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .build();

        return CircuitBreakerRegistry.of(config);
    }
}

// サービス
@Service
public class InventoryServiceClient {
    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;

    public InventoryServiceClient(RestTemplate restTemplate,
                                   CircuitBreakerRegistry registry) {
        this.restTemplate = restTemplate;
        this.circuitBreaker = registry.circuitBreaker("inventoryService");
    }

    public StockResponse getStock(String productId) {
        Supplier<StockResponse> supplier = CircuitBreaker
            .decorateSupplier(circuitBreaker, () -> {
                return restTemplate.getForObject(
                    "/api/v1/inventory/{productId}",
                    StockResponse.class,
                    productId
                );
            });

        return Try.ofSupplier(supplier)
            .recover(CallNotPermittedException.class,
                e -> getFallbackStock(productId))
            .recover(Exception.class,
                e -> getFallbackStock(productId))
            .get();
    }

    private StockResponse getFallbackStock(String productId) {
        // フォールバック: キャッシュから取得または推定値
        return new StockResponse(productId, 0, 0, 0, "UNKNOWN");
    }
}

// アノテーションベース
@Service
public class AccountingServiceClient {

    @CircuitBreaker(name = "accountingService", fallbackMethod = "fallback")
    @Retry(name = "accountingService")
    @TimeLimiter(name = "accountingService")
    public CompletableFuture<JournalResponse> createJournal(
            CreateJournalRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            return restTemplate.postForObject(
                "/api/v1/accounting/journals",
                request,
                JournalResponse.class
            );
        });
    }

    public CompletableFuture<JournalResponse> fallback(
            CreateJournalRequest request, Throwable t) {
        log.warn("Fallback for createJournal: {}", t.getMessage());
        // 仕訳をキューに保存して後で再試行
        pendingJournalQueue.add(request);
        return CompletableFuture.completedFuture(
            JournalResponse.pending(request.getCorrelationId())
        );
    }
}
```

</details>

---

## 38.3 API ゲートウェイ

API ゲートウェイは、クライアントと複数のバックエンドサービスの間に位置し、横断的な関心事を一元的に処理します。

```plantuml
@startuml
title API ゲートウェイアーキテクチャ

rectangle "クライアント" as clients {
    rectangle "Webアプリ" as web
    rectangle "モバイルアプリ" as mobile
    rectangle "外部システム" as external
}

rectangle "API Gateway" as gateway {
    rectangle "認証・認可" as auth
    rectangle "レート制限" as rate
    rectangle "ルーティング" as routing
    rectangle "ログ・監視" as logging
    rectangle "変換・集約" as transform
}

rectangle "バックエンドサービス" as backend {
    rectangle "販売API" as sales
    rectangle "会計API" as accounting
    rectangle "生産API" as production
}

web --> gateway
mobile --> gateway
external --> gateway

gateway --> sales
gateway --> accounting
gateway --> production

note bottom of gateway
  【API Gatewayの責務】
  ・認証・認可の一元化
  ・レート制限
  ・リクエストルーティング
  ・ログ集約
  ・レスポンス変換
  ・API集約
end note

@enduml
```

### 認証・認可の一元化

```plantuml
@startuml
title JWT 認証フロー

|クライアント|
start
:認証リクエスト;
note right
  POST /auth/login
  {username, password}
end note

|認証サービス|
:認証情報検証;
:JWTトークン発行;
note right
  Header: {alg, typ}
  Payload: {sub, roles, exp}
  Signature
end note

|クライアント|
:JWTトークン保存;
:API呼び出し;
note right
  Authorization: Bearer {token}
end note

|API Gateway|
:JWTトークン検証;
if (有効?) then (yes)
    :クレーム抽出;
    :認可チェック;
    if (権限あり?) then (yes)
        |バックエンドサービス|
        :リクエスト処理;
        :レスポンス返却;
    else (no)
        :403 Forbidden;
    endif
else (no)
    :401 Unauthorized;
endif

|クライアント|
:結果受信;

stop

@enduml
```

<details>
<summary>Java 実装例（Spring Security + JWT）</summary>

```java
// JWT フィルター
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && tokenProvider.validateToken(token)) {
            Authentication auth = tokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

// JWT プロバイダー
@Component
public class JwtTokenProvider {
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        return Jwts.builder()
            .setSubject(userDetails.getUsername())
            .claim("roles", userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()))
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
            .signWith(SignatureAlgorithm.HS512, jwtSecret)
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser()
            .setSigningKey(jwtSecret)
            .parseClaimsJws(token)
            .getBody();

        List<String> roles = claims.get("roles", List.class);
        List<GrantedAuthority> authorities = roles.stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(
            principal, token, authorities
        );
    }
}
```

</details>

### レート制限とスロットリング

```plantuml
@startuml
title レート制限アルゴリズム

rectangle "Token Bucket" as token_bucket {
    note right
      ・バケットにトークンを蓄積
      ・リクエスト時にトークン消費
      ・バースト対応可能

      設定例:
      rate: 100/分
      burst: 20
    end note
}

rectangle "Sliding Window" as sliding_window {
    note right
      ・時間枠内のリクエスト数をカウント
      ・枠をスライドして計算
      ・より正確な制限

      設定例:
      window: 1分
      limit: 100
    end note
}

rectangle "Fixed Window" as fixed_window {
    note right
      ・固定時間枠でカウント
      ・シンプルな実装
      ・境界でバーストの可能性

      設定例:
      window: 1分
      limit: 100
    end note
}

@enduml
```

<details>
<summary>Spring Cloud Gateway 設定例</summary>

```yaml
# application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: sales-service
          uri: lb://sales-service
          predicates:
            - Path=/api/v1/sales/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
                key-resolver: "#{@userKeyResolver}"
            - name: CircuitBreaker
              args:
                name: salesCircuitBreaker
                fallbackUri: forward:/fallback/sales

        - id: accounting-service
          uri: lb://accounting-service
          predicates:
            - Path=/api/v1/accounting/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 50
                redis-rate-limiter.burstCapacity: 100

      default-filters:
        - name: Retry
          args:
            retries: 3
            statuses: BAD_GATEWAY,SERVICE_UNAVAILABLE
            methods: GET
            backoff:
              firstBackoff: 100ms
              maxBackoff: 500ms
              factor: 2
```

```java
// Key Resolver（ユーザー単位のレート制限）
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest()
                .getHeaders()
                .getFirst("X-User-Id");
            return Mono.just(userId != null ? userId : "anonymous");
        };
    }

    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> {
            String apiKey = exchange.getRequest()
                .getHeaders()
                .getFirst("X-API-Key");
            return Mono.just(apiKey != null ? apiKey : "default");
        };
    }
}
```

</details>

### ログ集約とモニタリング

```plantuml
@startuml
title 分散トレーシングとログ集約

rectangle "クライアント" as client

rectangle "API Gateway" as gateway {
    rectangle "トレースID生成" as trace_gen
}

rectangle "サービス群" as services {
    rectangle "販売サービス" as sales
    rectangle "在庫サービス" as inventory
    rectangle "会計サービス" as accounting
}

rectangle "観測基盤" as observability {
    database "ログ集約\n(ELK/Loki)" as logs
    database "メトリクス\n(Prometheus)" as metrics
    database "トレース\n(Jaeger/Zipkin)" as traces
}

rectangle "ダッシュボード" as dashboard {
    rectangle "Grafana" as grafana
    rectangle "Kibana" as kibana
}

client --> gateway : リクエスト
gateway --> sales : traceId
sales --> inventory : traceId
sales --> accounting : traceId

gateway --> logs
sales --> logs
inventory --> logs
accounting --> logs

gateway --> metrics
sales --> metrics
inventory --> metrics
accounting --> metrics

gateway --> traces
sales --> traces
inventory --> traces
accounting --> traces

logs --> kibana
metrics --> grafana
traces --> grafana

@enduml
```

<details>
<summary>Java 実装例（Micrometer + OpenTelemetry）</summary>

```java
// トレーシング設定
@Configuration
public class TracingConfig {

    @Bean
    public Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("sales-service");
    }
}

// カスタムメトリクス
@Component
public class OrderMetrics {
    private final MeterRegistry meterRegistry;
    private final Counter orderCreatedCounter;
    private final Timer orderProcessingTimer;

    public OrderMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.orderCreatedCounter = Counter.builder("orders.created")
            .description("Number of orders created")
            .tag("service", "sales")
            .register(meterRegistry);

        this.orderProcessingTimer = Timer.builder("orders.processing.time")
            .description("Order processing time")
            .tag("service", "sales")
            .register(meterRegistry);
    }

    public void recordOrderCreated(String status) {
        orderCreatedCounter.increment();
        meterRegistry.counter("orders.created.by.status",
            "status", status).increment();
    }

    public void recordProcessingTime(long durationMs) {
        orderProcessingTimer.record(Duration.ofMillis(durationMs));
    }
}

// 構造化ログ
@Slf4j
@Service
public class OrderService {

    public Order createOrder(CreateOrderRequest request) {
        MDC.put("orderId", request.getOrderId());
        MDC.put("customerId", request.getCustomerId());

        try {
            log.info("Creating order", kv("action", "create_order_start"));

            Order order = processOrder(request);

            log.info("Order created successfully",
                kv("action", "create_order_complete"),
                kv("totalAmount", order.getTotalAmount()));

            return order;
        } catch (Exception e) {
            log.error("Failed to create order",
                kv("action", "create_order_failed"),
                kv("error", e.getMessage()), e);
            throw e;
        } finally {
            MDC.clear();
        }
    }
}
```

</details>

---

## 38.4 API インテグレーションテスト

### テストコンテナによる統合テスト環境

```plantuml
@startuml
title テストコンテナアーキテクチャ

rectangle "テスト実行環境" as test_env {
    rectangle "JUnit 5" as junit
    rectangle "テストクラス" as test_class
}

rectangle "Testcontainers" as testcontainers {
    rectangle "PostgreSQL\nコンテナ" as postgres
    rectangle "RabbitMQ\nコンテナ" as rabbitmq
    rectangle "Redis\nコンテナ" as redis
}

rectangle "アプリケーション" as app {
    rectangle "Spring Boot\nアプリケーション" as spring
}

test_class --> spring : HTTP リクエスト
spring --> postgres : データアクセス
spring --> rabbitmq : メッセージング
spring --> redis : キャッシュ

junit --> testcontainers : コンテナ起動
testcontainers --> postgres
testcontainers --> rabbitmq
testcontainers --> redis

note bottom of testcontainers
  【Testcontainersの利点】
  ・本番同等の環境でテスト
  ・テスト間の分離
  ・CI/CDでの再現性
  ・自動クリーンアップ
end note

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// テストコンテナ設定
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // テストデータのクリーンアップ
        cleanupTestData();
    }

    protected void cleanupTestData() {
        jdbcTemplate.execute("TRUNCATE TABLE 受注明細 CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE 受注 CASCADE");
    }
}
```

</details>

### REST API エンドポイントのテスト

```plantuml
@startuml
title API テストの構造

' ノード間の間隔を調整
skinparam nodesep 50
skinparam ranksep 50

rectangle "== Given（前提条件）\n\n・テストデータ準備\n・認証トークン取得\n・モックの設定" as given

rectangle "== When（操作）\n\n・APIエンドポイント呼び出し\n・リクエストボディ設定\n・ヘッダー設定" as when

rectangle "== Then（検証）\n\n・ステータスコード確認\n・レスポンスボディ検証\n・データベース状態確認\n・イベント発行確認" as then

given --> when
when --> then

@enduml
```

<details>
<summary>Java 実装例</summary>

```java
// 受注API統合テスト
class OrderApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    private String authToken;
    private Customer testCustomer;
    private Product testProduct;

    @BeforeEach
    void setUpTestData() {
        // 認証トークン取得
        authToken = getAuthToken("test-user", "password");

        // テストデータ準備
        testCustomer = customerRepository.save(
            new Customer("CUS-001", "テスト顧客", "test@example.com")
        );
        testProduct = productRepository.save(
            new Product("PRD-001", "テスト商品", new BigDecimal("1000"))
        );
    }

    @Test
    @DisplayName("受注登録: 正常系")
    void createOrder_Success() {
        // Given
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(testCustomer.getCustomerId())
            .orderDate(LocalDate.now())
            .lines(List.of(
                OrderLineRequest.builder()
                    .productId(testProduct.getProductId())
                    .quantity(10)
                    .build()
            ))
            .build();

        // When
        ResponseEntity<OrderResponse> response = restTemplate.exchange(
            "/api/v1/sales/orders",
            HttpMethod.POST,
            new HttpEntity<>(request, createAuthHeaders()),
            OrderResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrderId()).isNotBlank();
        assertThat(response.getBody().getStatus()).isEqualTo("DRAFT");
        assertThat(response.getBody().getTotalAmount())
            .isEqualByComparingTo(new BigDecimal("10000"));

        // データベース状態確認
        Order savedOrder = orderRepository.findById(
            response.getBody().getOrderId()
        ).orElseThrow();
        assertThat(savedOrder.getCustomerId()).isEqualTo(testCustomer.getCustomerId());
        assertThat(savedOrder.getLines()).hasSize(1);
    }

    @Test
    @DisplayName("受注登録: バリデーションエラー")
    void createOrder_ValidationError() {
        // Given - 明細なしのリクエスト
        CreateOrderRequest request = CreateOrderRequest.builder()
            .customerId(testCustomer.getCustomerId())
            .orderDate(LocalDate.now())
            .lines(List.of()) // 空の明細
            .build();

        // When
        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
            "/api/v1/sales/orders",
            HttpMethod.POST,
            new HttpEntity<>(request, createAuthHeaders()),
            ProblemDetail.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getTitle()).isEqualTo("Validation Error");
    }

    @Test
    @DisplayName("受注一覧取得: ページング")
    void getOrders_Paging() {
        // Given - 複数の受注を作成
        for (int i = 0; i < 25; i++) {
            createTestOrder("ORD-" + String.format("%03d", i));
        }

        // When
        ResponseEntity<PagedResponse<OrderSummary>> response = restTemplate.exchange(
            "/api/v1/sales/orders?page=0&size=10",
            HttpMethod.GET,
            new HttpEntity<>(createAuthHeaders()),
            new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getContent()).hasSize(10);
        assertThat(response.getBody().getTotalElements()).isEqualTo(25);
        assertThat(response.getBody().getTotalPages()).isEqualTo(3);
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
```

</details>

### サービス間連携テスト

```plantuml
@startuml
title サービス間連携テストのアプローチ

' ボックスの定義
rectangle "Contract Testing" as contract
note right of contract
  ・Pact / Spring Cloud Contract
  ・プロバイダーとコンシューマーの契約
  ・独立したテスト実行
end note

rectangle "Component Testing" as component
note right of component
  ・WireMockで外部サービスをモック
  ・単一サービスの統合テスト
  ・高速なフィードバック
end note

rectangle "End-to-End Testing" as e2e
note right of e2e
  ・全サービスを起動
  ・Docker Composeで環境構築
  ・本番同等のシナリオテスト
end note

' 配置（上から下に並べる）
contract -[hidden]down-> component
component -[hidden]down-> e2e

@enduml
```

<details>
<summary>Java 実装例（WireMock）</summary>

```java
// WireMockを使用したサービス間連携テスト
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
class OrderServiceWithInventoryTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${wiremock.server.port}")
    private int wireMockPort;

    @BeforeEach
    void setUp() {
        // 在庫サービスのモック設定
        stubFor(get(urlPathMatching("/api/v1/inventory/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "productId": "PRD-001",
                        "quantity": 100,
                        "allocatedQuantity": 0,
                        "availableQuantity": 100
                    }
                    """)));

        stubFor(post(urlPathMatching("/api/v1/inventory/allocate"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "success": true,
                        "allocationId": "ALLOC-001"
                    }
                    """)));
    }

    @Test
    @DisplayName("受注確定: 在庫引当成功")
    void confirmOrder_InventoryAllocated() {
        // Given
        String orderId = createDraftOrder();

        // When
        ResponseEntity<OrderResponse> response = restTemplate.exchange(
            "/api/v1/sales/orders/{orderId}/confirm",
            HttpMethod.POST,
            new HttpEntity<>(createAuthHeaders()),
            OrderResponse.class,
            orderId
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo("CONFIRMED");

        // 在庫サービスへの呼び出しを検証
        verify(postRequestedFor(urlPathEqualTo("/api/v1/inventory/allocate"))
            .withRequestBody(matchingJsonPath("$.productId", equalTo("PRD-001")))
            .withRequestBody(matchingJsonPath("$.quantity", equalTo("10"))));
    }

    @Test
    @DisplayName("受注確定: 在庫不足")
    void confirmOrder_InsufficientStock() {
        // Given - 在庫不足のレスポンスを設定
        stubFor(post(urlPathMatching("/api/v1/inventory/allocate"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("""
                    {
                        "success": false,
                        "errorMessage": "Insufficient stock"
                    }
                    """)));

        String orderId = createDraftOrder();

        // When
        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
            "/api/v1/sales/orders/{orderId}/confirm",
            HttpMethod.POST,
            new HttpEntity<>(createAuthHeaders()),
            ProblemDetail.class,
            orderId
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getDetail()).contains("在庫不足");
    }

    @Test
    @DisplayName("受注確定: 在庫サービスタイムアウト")
    void confirmOrder_InventoryServiceTimeout() {
        // Given - タイムアウトをシミュレート
        stubFor(post(urlPathMatching("/api/v1/inventory/allocate"))
            .willReturn(aResponse()
                .withFixedDelay(5000) // 5秒遅延
                .withStatus(200)));

        String orderId = createDraftOrder();

        // When
        ResponseEntity<ProblemDetail> response = restTemplate.exchange(
            "/api/v1/sales/orders/{orderId}/confirm",
            HttpMethod.POST,
            new HttpEntity<>(createAuthHeaders()),
            ProblemDetail.class,
            orderId
        );

        // Then - サーキットブレーカーによるフォールバック
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
```

</details>

---

## 38.5 まとめ

本章では、API 設計とサービス連携のパターンについて解説しました。

### 学んだこと

1. **API 設計の原則**

   - RESTful API の制約と設計原則
   - リソース指向設計（名詞ベース、HTTP メソッド）
   - バージョニング戦略（URI パス推奨）

2. **サービス間通信**

   - 同期通信（REST、gRPC）の使い分け
   - 非同期通信（メッセージキュー）
   - サーキットブレーカーによる障害対策

3. **API ゲートウェイ**

   - 認証・認可の一元化（JWT）
   - レート制限とスロットリング
   - ログ集約とモニタリング

4. **API インテグレーションテスト**

   - テストコンテナによる統合テスト環境
   - REST API エンドポイントのテスト
   - サービス間連携テスト（WireMock）

### API 設計チェックリスト

- [ ] リソースは名詞（複数形）で命名されているか
- [ ] HTTP メソッドが正しく使用されているか
- [ ] エラーレスポンスは Problem Detail 形式か
- [ ] バージョニング戦略が決定されているか
- [ ] 認証・認可が適切に実装されているか
- [ ] レート制限が設定されているか
- [ ] サーキットブレーカーが導入されているか
- [ ] 統合テストが整備されているか

### 次章の予告

第39章では、データ連携の実装パターンについて解説します。バッチ連携、リアルタイム連携、連携テーブルの設計など、具体的な実装パターンを学びます。
