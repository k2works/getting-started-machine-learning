{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    elixir
    erlang
    elixir-ls
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo "Elixir development environment activated"
    echo "  - Elixir: $(elixir --version | grep Elixir)"
    echo "  - Erlang: $(erl -noshell -eval 'io:fwrite("~s~n", [erlang:system_info(otp_release)]), halt().')"
  '';
}
