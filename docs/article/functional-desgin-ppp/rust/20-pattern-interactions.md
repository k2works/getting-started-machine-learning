---
type: Article
title: "第20章: パターン間の相互作用"
description: "本章では、これまで学んだデザインパターンを組み合わせて、より複雑なシステムを構築する方法を学びます。"
tags: [article, functional-desgin-ppp, rust]
status: stable
generated: { by: human:kakimomokuri, at: 2026-03-24T05:22:28Z }
---

# 第20章: パターン間の相互作用

## はじめに

本章では、これまで学んだデザインパターンを組み合わせて、より複雑なシステムを構築する方法を学びます。パターンの組み合わせにより、柔軟で拡張性の高いアーキテクチャを実現します。

## 1. Composite + Decorator

図形に装飾を追加しながら、階層構造を維持します。

```rust
/// 図形トレイト
pub trait Shape: CloneShape {
    fn draw(&self) -> String;
    fn area(&self) -> f64;
    fn translate(&self, dx: f64, dy: f64) -> Box<dyn Shape>;
    fn scale(&self, factor: f64) -> Box<dyn Shape>;
}

/// 円
#[derive(Clone)]
pub struct Circle {
    pub x: f64,
    pub y: f64,
    pub radius: f64,
}

impl Shape for Circle {
    fn draw(&self) -> String {
        format!("Circle at ({}, {}) with radius {}", self.x, self.y, self.radius)
    }

    fn area(&self) -> f64 {
        std::f64::consts::PI * self.radius * self.radius
    }
    // ...
}

/// 色付きデコレータ
pub struct ColoredShape {
    pub shape: Box<dyn Shape>,
    pub color: String,
}

impl Shape for ColoredShape {
    fn draw(&self) -> String {
        format!("[Color: {}] {}", self.color, self.shape.draw())
    }

    fn area(&self) -> f64 {
        self.shape.area()
    }
    // ...
}

/// 境界線デコレータ
pub struct BorderedShape {
    pub shape: Box<dyn Shape>,
    pub border_width: f64,
}

impl Shape for BorderedShape {
    fn draw(&self) -> String {
        format!("[Border: {}px] {}", self.border_width, self.shape.draw())
    }
    // ...
}

/// 複合図形（Composite）
pub struct CompositeShape {
    pub shapes: Vec<Box<dyn Shape>>,
}

impl Shape for CompositeShape {
    fn draw(&self) -> String {
        let drawings: Vec<String> = self.shapes.iter().map(|s| s.draw()).collect();
        format!("Composite [{}]", drawings.join(", "))
    }

    fn area(&self) -> f64 {
        self.shapes.iter().map(|s| s.area()).sum()
    }
    // ...
}
```

### 使用例

```rust
// 色付きの円
let colored_circle = ColoredShape {
    shape: Box::new(Circle { x: 0.0, y: 0.0, radius: 5.0 }),
    color: "red".to_string(),
};

// 境界線付きの四角形
let bordered_rect = BorderedShape {
    shape: Box::new(Rectangle { x: 10.0, y: 10.0, width: 20.0, height: 15.0 }),
    border_width: 2.0,
};

// 複合図形
let composite = CompositeShape {
    shapes: vec![
        Box::new(colored_circle),
        Box::new(bordered_rect),
    ],
};
```

## 2. Command + Observer

コマンドの実行を監視し、履歴を管理します。

```rust
/// コマンドトレイト
pub trait Command: CloneCommand {
    fn execute(&self, canvas: &Canvas) -> Canvas;
    fn undo(&self, canvas: &Canvas) -> Canvas;
}

/// キャンバスイベント
#[derive(Debug, Clone)]
pub enum CanvasEvent {
    CommandExecuted { command_name: String },
    CommandUndone { command_name: String },
    CommandRedone { command_name: String },
}

/// オブザーバートレイト
pub trait CanvasObserver {
    fn on_event(&mut self, event: &CanvasEvent);
}

/// Observable キャンバス
pub struct ObservableCanvas {
    pub canvas: Canvas,
    pub observers: Vec<Box<dyn CanvasObserver>>,
    pub history: Vec<Box<dyn Command>>,
    pub history_index: usize,
}

impl ObservableCanvas {
    pub fn execute(&mut self, command: Box<dyn Command>) {
        self.canvas = command.execute(&self.canvas);
        
        // 履歴を更新
        self.history.truncate(self.history_index);
        self.history.push(command.clone_box());
        self.history_index += 1;

        // オブザーバーに通知
        let event = CanvasEvent::CommandExecuted {
            command_name: "Command".to_string(),
        };
        self.notify(&event);
    }

    pub fn undo(&mut self) -> bool {
        if self.history_index > 0 {
            self.history_index -= 1;
            let command = &self.history[self.history_index];
            self.canvas = command.undo(&self.canvas);

            let event = CanvasEvent::CommandUndone {
                command_name: "Command".to_string(),
            };
            self.notify(&event);
            true
        } else {
            false
        }
    }

    fn notify(&mut self, event: &CanvasEvent) {
        for observer in &mut self.observers {
            observer.on_event(event);
        }
    }
}
```

## 3. Strategy + Factory

ファクトリが戦略に基づいてオブジェクトを生成します。

```rust
/// レンダリング戦略
pub trait RenderStrategy {
    fn render(&self, shape: &dyn Shape) -> String;
}

pub struct TextRenderer;
pub struct JsonRenderer;
pub struct SvgRenderer;

impl RenderStrategy for TextRenderer {
    fn render(&self, shape: &dyn Shape) -> String {
        shape.draw()
    }
}

impl RenderStrategy for JsonRenderer {
    fn render(&self, shape: &dyn Shape) -> String {
        format!(r#"{{"type":"shape","area":{}}}"#, shape.area())
    }
}

/// 図形ファクトリトレイト
pub trait ShapeFactory {
    fn create_circle(&self, x: f64, y: f64, radius: f64) -> Box<dyn Shape>;
    fn create_rectangle(&self, x: f64, y: f64, width: f64, height: f64) -> Box<dyn Shape>;
}

/// 標準ファクトリ
pub struct SimpleShapeFactory;

impl ShapeFactory for SimpleShapeFactory {
    fn create_circle(&self, x: f64, y: f64, radius: f64) -> Box<dyn Shape> {
        Box::new(Circle { x, y, radius })
    }
    // ...
}

/// 色付きファクトリ
pub struct ColoredShapeFactory {
    pub color: String,
}

impl ShapeFactory for ColoredShapeFactory {
    fn create_circle(&self, x: f64, y: f64, radius: f64) -> Box<dyn Shape> {
        Box::new(ColoredShape {
            shape: Box::new(Circle { x, y, radius }),
            color: self.color.clone(),
        })
    }
    // ...
}
```

## 4. DSL による宣言的な構築

```rust
/// 図形ビルダー DSL
pub mod dsl {
    use super::*;

    pub fn circle(x: f64, y: f64, radius: f64) -> Box<dyn Shape> {
        Box::new(Circle { x, y, radius })
    }

    pub fn rectangle(x: f64, y: f64, width: f64, height: f64) -> Box<dyn Shape> {
        Box::new(Rectangle { x, y, width, height })
    }

    pub fn colored(shape: Box<dyn Shape>, color: &str) -> Box<dyn Shape> {
        Box::new(ColoredShape {
            shape,
            color: color.to_string(),
        })
    }

    pub fn bordered(shape: Box<dyn Shape>, width: f64) -> Box<dyn Shape> {
        Box::new(BorderedShape {
            shape,
            border_width: width,
        })
    }

    pub fn composite(shapes: Vec<Box<dyn Shape>>) -> Box<dyn Shape> {
        Box::new(CompositeShape { shapes })
    }
}

// 使用例
let scene = composite(vec![
    colored(circle(0.0, 0.0, 10.0), "red"),
    bordered(rectangle(20.0, 20.0, 30.0, 20.0), 2.0),
]);
```

## 5. パターンの組み合わせの利点

1. **柔軟性**: 各パターンの利点を組み合わせ
2. **拡張性**: 新しい機能を追加しやすい
3. **テスト容易性**: 各コンポーネントを独立してテスト
4. **分離**: 関心事の分離が明確

## まとめ

本章では、パターン間の相互作用について学びました：

1. **Composite + Decorator**: 階層構造と装飾の組み合わせ
2. **Command + Observer**: 操作の監視と履歴管理
3. **Strategy + Factory**: 戦略に基づくオブジェクト生成
4. **DSL**: 宣言的な構築

## 参考コード

- ソースコード: `apps/rust/part7/src/chapter20.rs`

## 次章予告

次章では、**ベストプラクティス**について学びます。関数型デザインの実践的なガイドラインを探ります。
