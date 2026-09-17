---
type: ADR
title: "ADR-002: キャッシュ戦略"
description: "Redis をセッションと商品マスタのキャッシュに採用する。"
tags: [adr]
status: draft
generated: { by: claude-code/claude-fable-5, at: 2026-08-25T02:53:10Z }
---

# ADR-002: キャッシュ戦略

Redis をセッションと商品マスタのキャッシュに採用する。

## コンテキスト

商品一覧の応答が遅い。

## 決定

Redis を導入する。
