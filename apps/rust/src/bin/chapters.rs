//! 章を選んで実行する。使い方: cargo run --bin chapters -- chapter01

use getting_started_ml::{chapter01, chapter02, chapter03};
use std::io::{self, Write};
use std::process::ExitCode;

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().collect();
    let stdout = io::stdout();
    let mut out = stdout.lock();

    let result = match args.get(1).map(String::as_str) {
        Some("chapter01") => chapter01::run(&mut out).map_err(|e| e.to_string()),
        Some("chapter02") => chapter02::run(&mut out).map_err(|e| e.to_string()),
        Some("chapter03") => chapter03::run(&mut out).map_err(|e| e.to_string()),
        _ => {
            let _ = writeln!(
                io::stderr(),
                "使い方: cargo run --bin chapters -- (chapter01 | chapter02 | chapter03)"
            );

            return ExitCode::FAILURE;
        }
    };

    if let Err(error) = result {
        let _ = writeln!(io::stderr(), "{error}");

        return ExitCode::FAILURE;
    }

    ExitCode::SUCCESS
}
