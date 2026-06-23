FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# Remove broken Yarn repo that ships in the base image (expired GPG key)
RUN rm -f /etc/apt/sources.list.d/yarn.list /usr/share/keyrings/yarnkey.gpg

# Node.js LTS + Claude Code CLI + supervisor.
# The JDK and Gradle are provided reproducibly by Nix (see flake.nix), not installed here.
RUN curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - \
    && apt-get install -y --no-install-recommends nodejs supervisor \
    && rm -rf /var/lib/apt/lists/* \
    && npm install -g @anthropic-ai/claude-code

COPY supervisord.conf /etc/supervisor/conf.d/crypto-exchange.conf

CMD ["/usr/bin/supervisord", "-n", "-c", "/etc/supervisor/supervisord.conf"]
