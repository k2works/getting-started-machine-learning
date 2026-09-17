---
type: Article
title: "第19章: Wa-Tor シミュレーション"
description: "本章では、Wa-Tor（Water Torus）シミュレーションを実装します。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第19章: Wa-Tor シミュレーション

## はじめに

本章では、Wa-Tor（Water Torus）シミュレーションを実装します。これは魚とサメの捕食-被食関係をシミュレートするセルラーオートマトンです。関数型のアプローチで、不変データ構造と純粋関数を使って実装します。

## 1. データ構造

```rust
/// 座標
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct Position {
    pub x: i32,
    pub y: i32,
}

impl Position {
    pub fn new(x: i32, y: i32) -> Position {
        Position { x, y }
    }

    pub fn wrap(&self, width: i32, height: i32) -> Position {
        Position {
            x: ((self.x % width) + width) % width,
            y: ((self.y % height) + height) % height,
        }
    }
}

/// 生物の種類
#[derive(Debug, Clone, PartialEq)]
pub enum CreatureType {
    Fish,
    Shark,
}

/// 生物
#[derive(Debug, Clone)]
pub struct Creature {
    pub creature_type: CreatureType,
    pub position: Position,
    pub energy: i32,           // サメのエネルギー
    pub breed_time: i32,       // 繁殖までの時間
}

impl Creature {
    pub fn fish(position: Position, breed_time: i32) -> Creature {
        Creature {
            creature_type: CreatureType::Fish,
            position,
            energy: 0,
            breed_time,
        }
    }

    pub fn shark(position: Position, energy: i32, breed_time: i32) -> Creature {
        Creature {
            creature_type: CreatureType::Shark,
            position,
            energy,
            breed_time,
        }
    }
}

/// ワールド
#[derive(Debug, Clone)]
pub struct World {
    pub width: i32,
    pub height: i32,
    pub creatures: Vec<Creature>,
    pub fish_breed_time: i32,
    pub shark_breed_time: i32,
    pub shark_energy_gain: i32,
    pub shark_initial_energy: i32,
}
```

## 2. 移動ロジック

```rust
impl World {
    /// 隣接セルを取得
    pub fn neighbors(&self, pos: Position) -> Vec<Position> {
        let directions = vec![
            Position::new(0, -1),  // 上
            Position::new(0, 1),   // 下
            Position::new(-1, 0),  // 左
            Position::new(1, 0),   // 右
        ];

        directions
            .into_iter()
            .map(|d| Position::new(pos.x + d.x, pos.y + d.y).wrap(self.width, self.height))
            .collect()
    }

    /// 空のセルを探す
    pub fn find_empty_neighbors(&self, pos: Position) -> Vec<Position> {
        self.neighbors(pos)
            .into_iter()
            .filter(|p| !self.creatures.iter().any(|c| c.position == *p))
            .collect()
    }

    /// 魚がいるセルを探す
    pub fn find_fish_neighbors(&self, pos: Position) -> Vec<Position> {
        self.neighbors(pos)
            .into_iter()
            .filter(|p| {
                self.creatures
                    .iter()
                    .any(|c| c.position == *p && c.creature_type == CreatureType::Fish)
            })
            .collect()
    }
}
```

## 3. シミュレーションステップ

```rust
impl World {
    /// 魚を移動
    fn move_fish(&self, fish: &Creature, rng: &mut impl Rng) -> (Option<Creature>, Option<Creature>) {
        let empty = self.find_empty_neighbors(fish.position);

        if empty.is_empty() {
            // 移動できない
            let new_fish = Creature {
                breed_time: fish.breed_time - 1,
                ..fish.clone()
            };
            (Some(new_fish), None)
        } else {
            // ランダムに移動
            let new_pos = empty[rng.gen_range(0..empty.len())];

            if fish.breed_time <= 1 {
                // 繁殖
                let parent = Creature::fish(new_pos, self.fish_breed_time);
                let child = Creature::fish(fish.position, self.fish_breed_time);
                (Some(parent), Some(child))
            } else {
                let new_fish = Creature {
                    position: new_pos,
                    breed_time: fish.breed_time - 1,
                    ..fish.clone()
                };
                (Some(new_fish), None)
            }
        }
    }

    /// サメを移動
    fn move_shark(&self, shark: &Creature, rng: &mut impl Rng) -> (Option<Creature>, Option<Creature>, Option<Position>) {
        let fish_neighbors = self.find_fish_neighbors(shark.position);
        let empty_neighbors = self.find_empty_neighbors(shark.position);

        // エネルギーがなくなったら死亡
        if shark.energy <= 0 {
            return (None, None, None);
        }

        if !fish_neighbors.is_empty() {
            // 魚を食べる
            let target = fish_neighbors[rng.gen_range(0..fish_neighbors.len())];
            let new_energy = shark.energy + self.shark_energy_gain;

            if shark.breed_time <= 1 {
                // 繁殖
                let parent = Creature::shark(target, new_energy - 1, self.shark_breed_time);
                let child = Creature::shark(shark.position, self.shark_initial_energy, self.shark_breed_time);
                (Some(parent), Some(child), Some(target))
            } else {
                let new_shark = Creature {
                    position: target,
                    energy: new_energy - 1,
                    breed_time: shark.breed_time - 1,
                    ..shark.clone()
                };
                (Some(new_shark), None, Some(target))
            }
        } else if !empty_neighbors.is_empty() {
            // 空のセルに移動
            let new_pos = empty_neighbors[rng.gen_range(0..empty_neighbors.len())];

            if shark.breed_time <= 1 {
                let parent = Creature::shark(new_pos, shark.energy - 1, self.shark_breed_time);
                let child = Creature::shark(shark.position, self.shark_initial_energy, self.shark_breed_time);
                (Some(parent), Some(child), None)
            } else {
                let new_shark = Creature {
                    position: new_pos,
                    energy: shark.energy - 1,
                    breed_time: shark.breed_time - 1,
                    ..shark.clone()
                };
                (Some(new_shark), None, None)
            }
        } else {
            // 移動できない
            let new_shark = Creature {
                energy: shark.energy - 1,
                breed_time: shark.breed_time - 1,
                ..shark.clone()
            };
            (Some(new_shark), None, None)
        }
    }

    /// 1ステップ実行
    pub fn step(&self, rng: &mut impl Rng) -> World {
        // 実装省略（全生物を順番に処理）
        self.clone()
    }
}
```

## 4. シミュレーション実行

```rust
/// シミュレーション結果
#[derive(Debug, Clone)]
pub struct SimulationStats {
    pub step: usize,
    pub fish_count: usize,
    pub shark_count: usize,
}

/// シミュレーションを実行
pub fn run_simulation(
    initial_world: World,
    steps: usize,
    mut rng: impl Rng,
) -> Vec<SimulationStats> {
    let mut world = initial_world;
    let mut stats = Vec::new();

    for step in 0..steps {
        let fish_count = world.creatures
            .iter()
            .filter(|c| c.creature_type == CreatureType::Fish)
            .count();
        let shark_count = world.creatures
            .iter()
            .filter(|c| c.creature_type == CreatureType::Shark)
            .count();

        stats.push(SimulationStats {
            step,
            fish_count,
            shark_count,
        });

        world = world.step(&mut rng);
    }

    stats
}
```

## 5. 可視化

```rust
impl World {
    /// ASCII アートで表示
    pub fn to_ascii(&self) -> String {
        let mut grid = vec![vec!['.'; self.width as usize]; self.height as usize];

        for creature in &self.creatures {
            let ch = match creature.creature_type {
                CreatureType::Fish => 'f',
                CreatureType::Shark => 'S',
            };
            grid[creature.position.y as usize][creature.position.x as usize] = ch;
        }

        grid.into_iter()
            .map(|row| row.into_iter().collect::<String>())
            .collect::<Vec<_>>()
            .join("\n")
    }
}
```

## 6. パターンの適用

1. **不変データ**: World, Creature は不変
2. **純粋関数**: step() は新しい World を返す
3. **ADT**: CreatureType による生物種別
4. **イテレータ**: 効率的な集計処理

## まとめ

本章では、Wa-Tor シミュレーションを通じて：

1. 不変データ構造によるシミュレーション状態管理
2. 純粋関数による状態遷移
3. ADT による生物種別の表現
4. イテレータによる効率的なデータ処理

を学びました。関数型のアプローチにより、再現性があり、テストしやすいシミュレーションが実現できます。

## 参考コード

- ソースコード: `apps/rust/part6/src/chapter19.rs`

## 次章予告

次章では、**パターン間の相互作用**について学びます。複数のパターンを組み合わせた設計を探ります。
