# ADR 0002: Single-Module Feature-First Structure

- Status: Accepted
- Date: 2026-08-12

## Context

The repository currently builds one Gradle application module, `:app`. Code is
organized under `app`, `core`, and feature-first `task`, `category`, `calendar`, and
`history` packages. Features then separate presentation, domain, data, navigation,
and dependency-injection responsibilities where those responsibilities exist.

The current product and team size do not need Gradle boundaries solely to mirror
Clean Architecture layer names. Premature extraction would increase configuration,
dependency management, and build maintenance before it creates enforceable value.

## Decision

Retain the single `:app` Gradle module and feature-first package structure. Preserve
the dependency direction inside that module through domain contracts, use cases,
repository implementations, package ownership, and focused tests.

A new module requires at least one of:

- separate ownership;
- enforceable dependency isolation;
- reusable infrastructure;
- isolated build/testing; or
- measured build-time benefit.

Layer names alone are not a reason to add a module. A proposal must identify the
trigger it satisfies, the dependency direction it will enforce, and the ongoing
build and maintenance cost.

## Consequences

- The project remains easy to navigate, configure, and build at its current scale.
- Feature boundaries are visible in packages without multiplying Gradle projects.
- Package boundaries are conventions rather than compiler-enforced module APIs, so
  reviews and tests must catch accidental cross-feature coupling.
- Growth can increase recompilation scope and make ownership less clear; those costs
  must be measured before module extraction is selected as the remedy.

## Rejected Alternatives

- A Gradle module per feature was rejected because the current ownership, isolation,
  and build measurements do not justify it.
- Separate presentation, domain, and data modules were rejected because reproducing
  layer names in Gradle would add dependency and configuration overhead without a
  demonstrated boundary problem.
- Kotlin Multiplatform extraction was rejected because there is no approved iOS or
  shared-runtime product requirement.

## Revisit When

Revisit this decision when at least one stated module trigger is supported by
evidence: ownership has separated, dependency violations need compiler enforcement,
infrastructure has a real reuse consumer, a feature needs isolated build/testing, or
build measurements show a material benefit. Record any replacement in a superseding
ADR with before-and-after evidence.
