#!/bin/bash
set -e

echo "========================================"
echo " Setting up Crypto Exchange Mock"
echo "========================================"

# Persist JAVA_HOME from the nix devShell into /etc/environment so that the
# VS Code Java extension (and any non-login process) can find the JDK without
# needing an active `nix develop` shell.
echo "JAVA_HOME=${JAVA_HOME}" >> /etc/environment

# Auto-activate the nix devShell in every new terminal so tools like ./gradlew
# work without manually running `nix develop`.
echo 'eval "$(nix print-dev-env 2>/dev/null)"' >> /root/.bashrc

# Warm the Gradle cache and verify the project compiles (tests run separately).
./gradlew --no-daemon build -x test

echo ""
echo "========================================"
echo " Ready!"
echo "========================================"
echo ""
echo "Quick start (inside 'nix develop'):"
echo "  ./gradlew test       # run the integration test suite"
echo "  ./gradlew bootRun    # run the mock exchange"
echo ""
echo "Endpoints:"
echo "  REST + WebSocket : http://localhost:8080  (e.g. GET /api/symbols)"
echo "  FIX 4.4 acceptor : tcp://localhost:9876"
echo ""
echo "The mock is also started automatically by supervisord on port 8080/9876."
echo "Or use Claude Code:  claude"
echo ""
