# Security Policy

## Supported versions

This project is provided as-is under the MIT License. Please use the latest `main` branch when possible.

## Reporting a vulnerability

If you discover a security issue (for example path traversal on import, or unsafe defaults):

1. **Do not** open a public GitHub issue with exploit details.
2. Contact the repository owner privately (GitHub Security Advisories preferred).
3. Include steps to reproduce and impact assessment if possible.

We will try to respond and publish a fix or mitigation guidance.

## Safe usage notes

- Bind the service to localhost or place it behind authentication when exposed on a network.
- Do not commit `~/.nginx-log-analyzer/db.json`, real access logs, or database passwords.
- Treat uploaded logs as sensitive data.
