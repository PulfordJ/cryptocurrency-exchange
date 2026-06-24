{
  description = "Mock cryptocurrency exchange + Java integration test suite (REST, WebSocket, FIX)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
        jdk = pkgs.jdk21;
      in
      {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            jdk
            gradle
            websocat
            curl
          ];

          env = {
            JAVA_HOME = jdk.home;
          };

          shellHook = ''
            echo "Crypto Exchange Mock dev shell"
            echo "  Java:   $(java -version 2>&1 | head -1)"
            echo "  Gradle: $(gradle --version 2>/dev/null | grep '^Gradle' | head -1)"
            echo ""
            echo "  Build & test:  ./gradlew test"
            echo "  Run the mock:  ./gradlew bootRun   (REST/WS :8080, FIX :9876)"
          '';
        };
      });
}
