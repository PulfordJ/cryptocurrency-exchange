FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# Remove broken Yarn repo that ships in the base image (expired GPG key)
RUN rm -f /etc/apt/sources.list.d/yarn.list /usr/share/keyrings/yarnkey.gpg

# JDK 21 + Node.js LTS + Claude Code CLI + supervisor.
RUN curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - \
    && apt-get install -y --no-install-recommends openjdk-21-jdk nodejs supervisor \
    && rm -rf /var/lib/apt/lists/* \
    && npm install -g @anthropic-ai/claude-code

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV JDK_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY supervisord.conf /etc/supervisor/conf.d/crypto-exchange.conf

CMD ["/usr/bin/supervisord", "-n", "-c", "/etc/supervisor/supervisord.conf"]
