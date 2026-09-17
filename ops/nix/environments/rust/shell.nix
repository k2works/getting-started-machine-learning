{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    rustc
    cargo
    rustfmt
    clippy
    rust-analyzer
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo "Rust development environment activated"
    echo "  - rustc: $(rustc --version)"
    echo "  - cargo: $(cargo --version)"
  '';
}
