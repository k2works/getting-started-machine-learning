{ packages ? import <nixpkgs> { } }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inputsFrom = [ baseShell ];
  buildInputs = with packages; [
    # なでしこ3 の処理系 gonako（nadesiko3go）は Go で書かれ、go install で入れる。
    # nadesiko3go は go 1.26.0 を要求するが、固定中の nixpkgs の go は 1.25.5
    # （go_1_26 は 1.26rc1）なので、apps/nadesiko3/tools/install-gonako.sh が
    # GOTOOLCHAIN で 1.26 系のツールチェーンに切り替える（ADR 015）。
    go
  ];

  shellHook = baseShell.shellHook + ''
    echo "Welcome to the nadesiko3 (gonako) development environment!"
    go version
  '';
}
