---
type: Article
title: "第15章: ゴシップ好きなバスの運転手"
description: "本章では、「ゴシップ好きなバスの運転手」問題を通じて、これまで学んだ関数型デザインパターンを実践的に適用します。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第15章: ゴシップ好きなバスの運転手

## はじめに

本章では、「ゴシップ好きなバスの運転手」問題を通じて、これまで学んだ関数型デザインパターンを実践的に適用します。この問題は、複数のバス運転手がルートを走りながら情報を交換し、全員が全ての噂を知るまでに何日かかるかを計算するものです。

## 1. 問題の概要

- 複数のバス運転手がいる
- 各運転手は固定のルートを毎日走る
- 運転手は同じ停留所に停まると、お互いの噂を交換する
- 全員が全ての噂を知るまでに何日かかるか？

## 2. データ構造

```rust
/// 停留所
pub type Stop = usize;

/// ルート（停留所の循環リスト）
#[derive(Debug, Clone)]
pub struct Route {
    stops: Vec<Stop>,
    current_index: usize,
}

impl Route {
    pub fn new(stops: Vec<Stop>) -> Route {
        Route {
            stops,
            current_index: 0,
        }
    }

    pub fn current_stop(&self) -> Stop {
        self.stops[self.current_index]
    }

    pub fn advance(&self) -> Route {
        Route {
            stops: self.stops.clone(),
            current_index: (self.current_index + 1) % self.stops.len(),
        }
    }
}

/// 運転手
#[derive(Debug, Clone)]
pub struct Driver {
    pub id: usize,
    pub route: Route,
    pub gossips: HashSet<usize>,  // 知っている噂のID
}

impl Driver {
    pub fn new(id: usize, route: Route) -> Driver {
        let mut gossips = HashSet::new();
        gossips.insert(id);  // 最初は自分の噂だけ知っている
        Driver { id, route, gossips }
    }

    pub fn current_stop(&self) -> Stop {
        self.route.current_stop()
    }

    pub fn advance(&self) -> Driver {
        Driver {
            id: self.id,
            route: self.route.advance(),
            gossips: self.gossips.clone(),
        }
    }

    pub fn share_gossips(&self, other_gossips: &HashSet<usize>) -> Driver {
        Driver {
            id: self.id,
            route: self.route.clone(),
            gossips: self.gossips.union(other_gossips).cloned().collect(),
        }
    }

    pub fn knows_all(&self, total_drivers: usize) -> bool {
        self.gossips.len() == total_drivers
    }
}
```

## 3. シミュレーション

```rust
/// シミュレーション状態
#[derive(Debug, Clone)]
pub struct Simulation {
    pub drivers: Vec<Driver>,
    pub day: usize,
    pub stop: usize,  // 1日あたりの停留所数
}

impl Simulation {
    pub fn new(routes: Vec<Vec<Stop>>) -> Simulation {
        let drivers = routes
            .into_iter()
            .enumerate()
            .map(|(id, stops)| Driver::new(id, Route::new(stops)))
            .collect();

        Simulation {
            drivers,
            day: 1,
            stop: 0,
        }
    }

    /// 同じ停留所にいる運転手間で噂を交換
    fn exchange_gossips(&self) -> Vec<Driver> {
        let mut drivers_by_stop: HashMap<Stop, Vec<usize>> = HashMap::new();

        // 停留所ごとに運転手をグループ化
        for (i, driver) in self.drivers.iter().enumerate() {
            drivers_by_stop
                .entry(driver.current_stop())
                .or_default()
                .push(i);
        }

        // 同じ停留所の運転手間で噂を交換
        let mut new_drivers = self.drivers.clone();
        for driver_indices in drivers_by_stop.values() {
            if driver_indices.len() > 1 {
                // 全ての噂を収集
                let combined_gossips: HashSet<usize> = driver_indices
                    .iter()
                    .flat_map(|&i| self.drivers[i].gossips.iter().cloned())
                    .collect();

                // 各運転手に噂を共有
                for &i in driver_indices {
                    new_drivers[i] = new_drivers[i].share_gossips(&combined_gossips);
                }
            }
        }

        new_drivers
    }

    /// 1停留所進める
    pub fn step(&self) -> Simulation {
        let drivers_after_exchange = self.exchange_gossips();
        let drivers_advanced: Vec<Driver> = drivers_after_exchange
            .into_iter()
            .map(|d| d.advance())
            .collect();

        let new_stop = self.stop + 1;
        let (day, stop) = if new_stop >= 480 {  // 8時間 = 480分（1分1停留所）
            (self.day + 1, 0)
        } else {
            (self.day, new_stop)
        };

        Simulation {
            drivers: drivers_advanced,
            day,
            stop,
        }
    }

    /// 全員が全ての噂を知っているか
    pub fn all_know_everything(&self) -> bool {
        let total = self.drivers.len();
        self.drivers.iter().all(|d| d.knows_all(total))
    }
}

/// シミュレーションを実行
pub fn run_simulation(routes: Vec<Vec<Stop>>, max_days: usize) -> Option<usize> {
    let stops_per_day = 480;
    let max_stops = max_days * stops_per_day;
    let mut sim = Simulation::new(routes);

    for _ in 0..max_stops {
        if sim.all_know_everything() {
            return Some(sim.day);
        }
        sim = sim.step();
    }

    None  // max_days 以内に完了しない
}
```

## 4. DSL によるルート定義

```rust
/// ルートビルダー
pub struct RouteBuilder {
    routes: Vec<Vec<Stop>>,
}

impl RouteBuilder {
    pub fn new() -> RouteBuilder {
        RouteBuilder { routes: Vec::new() }
    }

    pub fn driver(mut self, stops: Vec<Stop>) -> RouteBuilder {
        self.routes.push(stops);
        self
    }

    pub fn build(self) -> Vec<Vec<Stop>> {
        self.routes
    }
}

/// DSL マクロ
macro_rules! routes {
    ($($stops:expr),* $(,)?) => {
        RouteBuilder::new()
            $(.driver($stops))*
            .build()
    };
}

// 使用例
let routes = routes![
    vec![1, 2, 3],
    vec![2, 3, 4],
    vec![3, 4, 5],
];
```

## 5. テスト例

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_simple_exchange() {
        // 2人の運転手が同じ停留所を共有
        let routes = vec![
            vec![1, 2, 3],
            vec![2, 3, 4],
        ];
        let result = run_simulation(routes, 10);
        assert!(result.is_some());
    }

    #[test]
    fn test_no_common_stop() {
        // 共通の停留所がない
        let routes = vec![
            vec![1, 2, 3],
            vec![4, 5, 6],
        ];
        let result = run_simulation(routes, 10);
        assert!(result.is_none());
    }

    #[test]
    fn test_three_drivers() {
        let routes = vec![
            vec![3, 1, 2, 3],
            vec![3, 2, 3, 1],
            vec![4, 2, 3, 4, 5],
        ];
        let result = run_simulation(routes, 10);
        assert_eq!(result, Some(2));
    }
}
```

## 6. パターンの適用

この問題では以下のパターンが適用されています：

1. **不変データ構造**: Driver, Route, Simulation は全て不変
2. **関数合成**: step() を繰り返し適用
3. **DSL**: routes! マクロによる宣言的な定義
4. **イテレータ**: 効率的なデータ処理

## まとめ

本章では、「ゴシップ好きなバスの運転手」問題を通じて：

1. 不変データ構造による状態管理
2. 純粋関数による状態遷移
3. DSL による宣言的な問題定義
4. イテレータを使った効率的な処理

を学びました。関数型のアプローチにより、テストしやすく、理解しやすいコードが実現できます。

## 参考コード

- ソースコード: `apps/rust/part6/src/chapter15.rs`

## 次章予告

次章では、**給与計算システム**を通じて、より複雑なビジネスロジックの実装を学びます。
