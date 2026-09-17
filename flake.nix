{
  description = "Development environments managed with Nix for claude-code-booster assets";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        packages = nixpkgs.legacyPackages.${system};
      in
      {
        devShells = {
          default = import ./ops/nix/shells/shell.nix { inherit packages; };
          node = import ./ops/nix/environments/node/shell.nix { inherit packages; };
          python = import ./ops/nix/environments/python/shell.nix { inherit packages; };
          go = import ./ops/nix/environments/go/shell.nix { inherit packages; };
          rust = import ./ops/nix/environments/rust/shell.nix { inherit packages; };
          dotnet = import ./ops/nix/environments/dotnet/shell.nix { inherit packages; };
          java = import ./ops/nix/environments/java/shell.nix { inherit packages; };
          haskell = import ./ops/nix/environments/haskell/shell.nix { inherit packages; };
          ruby = import ./ops/nix/environments/ruby/shell.nix { inherit packages; };
          php = import ./ops/nix/environments/php/shell.nix { inherit packages; };
          clojure = import ./ops/nix/environments/clojure/shell.nix { inherit packages; };
          elixir = import ./ops/nix/environments/elixir/shell.nix { inherit packages; };
          scala = import ./ops/nix/environments/scala/shell.nix { inherit packages; };
        };
      }
    );
}
