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
    cargo-llvm-cov
    llvmPackages.libllvm
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    # cargo-llvm-cov は rustup の llvm-tools-preview を探すので、nixpkgs の LLVM を教える
    export LLVM_COV="${packages.llvmPackages.libllvm}/bin/llvm-cov"
    export LLVM_PROFDATA="${packages.llvmPackages.libllvm}/bin/llvm-profdata"
    echo "Rust development environment activated"
    echo "  - rustc: $(rustc --version)"
    echo "  - cargo: $(cargo --version)"
    echo "  - cargo-llvm-cov: $(cargo llvm-cov --version)"
  '';
}
