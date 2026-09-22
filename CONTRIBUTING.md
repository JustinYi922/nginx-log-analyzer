# Contributing

Thanks for contributing to **nginx-log-analyzer**.

## How to contribute

1. Fork the repository and create a feature branch.
2. Keep changes focused; avoid unrelated refactors in the same PR.
3. Run tests before opening a PR:

```bash
mvn test
```

4. Describe **what** changed and **why** in the pull request.

## Coding notes

- Java 8+, Spring Boot 2.7
- Prefer clear names and small methods over clever one-liners
- Do not commit secrets, real access logs, or personal paths
- UI copy should stay product-neutral (no customer / vendor-specific wording)

## Reporting issues

Please include:

- OS and Java version
- MySQL version (if relevant)
- Steps to reproduce
- Expected vs actual behavior

## License

By contributing, you agree that your contributions will be licensed under the MIT License,
together with the commercial attribution / notification terms described in
[COMMERCIAL.md](COMMERCIAL.md), `LICENSE`, and `NOTICE`.