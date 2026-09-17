# Project engineering rules

- Use JDK 25 and the checked-in Maven Wrapper.
- This is a single deployable Spring Modulith application. Group by bounded context first,
  then domain/application/infrastructure/web. Do not introduce global controller/service/dao layers.
- Keep domain packages free of Spring, JDBC, ORM, web and validation framework dependencies.
- Cross-module dependencies must use explicitly exported named interfaces. Never import another
  module's internals, query its tables, or introduce cross-module foreign keys.
- Put use-case transactions in application services; persist integration events in the same
  transaction as aggregate changes. Consumers must be idempotent.
- Follow Google Java Style. Run `./mvnw spotless:apply` after Java edits.
- Every completed code change MUST pass `./scripts/verify.sh` before committing. This runs
  Google formatting, Google Checkstyle (including warnings and test sources), unit tests,
  module/layer architecture verification and real PostgreSQL integration tests.
- Never bypass the hook with `--no-verify`, skip checks/tests, or reduce rules to make a build pass.
- Run `./scripts/install-hooks.sh` after cloning. Local hooks can be bypassed externally;
  configure the CI Verify job as a required check in the remote repository when one is available.
- Keep credentials in ignored `.env`; never commit local passwords or use the development
  database for integration tests. Tests use isolated temporary schemas in the test database.
- Use stable releases. Prefer the Spring Boot/Modulith BOM versions for transitive libraries;
  check official compatibility and run full verification before upgrading.
