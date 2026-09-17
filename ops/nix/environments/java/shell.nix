{ packages ? import <nixpkgs> {} }:
let
  baseShell = import ../../shells/shell.nix { inherit packages; };
in
packages.mkShell {
  inherit (baseShell) pure;
  buildInputs = baseShell.buildInputs ++ (with packages; [
    jdk
    maven
    gradle
  ]);
  shellHook = ''
    ${baseShell.shellHook}
    echo "Java development environment activated"
    echo "  - JDK: $(javac -version 2>&1)"
    echo "  - Maven: $(mvn -version | head -n 1)"
    echo "  - Gradle: $(gradle -version | grep Gradle)"
  '';
}
