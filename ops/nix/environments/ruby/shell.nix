{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    ruby
    rubyPackages_3_3.solargraph
    bundler
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    # solargraph の依存の gem が RUBYLIB に並ぶと、bundle exec が Gemfile.lock と違う版
    # （rubocop の parser など）を先に読み込む。solargraph は RUBYLIB が無くても動くので外す
    unset RUBYLIB
    echo "Ruby development environment activated"
    echo "  - Ruby: $(ruby --version | head -n 1)"
    echo "  - Bundler: $(bundle --version)"
    echo "  - Solargraph: $(solargraph --version)"
  '';
}
