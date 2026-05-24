# futures-kotlin in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Ffutures--kotlin-blue.svg)](https://github.com/KotlinMania/futures-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/futures-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/futures-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/futures-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/futures-kotlin/actions)

This is a Kotlin Multiplatform line-by-line transliteration port of [`rust-lang/futures-rs`](https://github.com/rust-lang/futures-rs).

**Original Project:** This port is based on [`rust-lang/futures-rs`](https://github.com/rust-lang/futures-rs). All design credit and project intent belong to the upstream authors; this repository is a faithful port to Kotlin Multiplatform with no behavioural changes intended.

### Porting status

This is an **in-progress port**. The goal is feature parity with the upstream Rust crate while providing a native Kotlin Multiplatform API. Every Kotlin file carries a `// port-lint: source <path>` header naming its upstream Rust counterpart so the AST-distance tool can track provenance.

---

## Upstream README — `rust-lang/futures-rs`

> The text below is reproduced and lightly edited from [`https://github.com/rust-lang/futures-rs`](https://github.com/rust-lang/futures-rs). It is the upstream project's own description and remains under the upstream authors' authorship; links have been rewritten to absolute upstream URLs so they continue to resolve from this repository.

<p align="center">
  <img alt="futures-rs" src="https://raw.githubusercontent.com/rust-lang/futures-rs/gh-pages/assets/images/futures-rs-logo.svg?sanitize=true" width="400">
</p>

<p align="center">
  Zero-cost asynchronous programming in Rust
</p>

<p align="center">
  <a href="https://github.com/rust-lang/futures-rs/actions?query=branch%3Amaster">
    <img alt="Build Status" src="https://img.shields.io/github/actions/workflow/status/rust-lang/futures-rs/ci.yml?branch=master">
  </a>

  <a href="https://crates.io/crates/futures">
    <img alt="crates.io" src="https://img.shields.io/crates/v/futures.svg">
  </a>
</p>

<p align="center">
  <a href="https://docs.rs/futures">
    Documentation
  </a> | <a href="https://rust-lang.github.io/futures-rs/">
    Website
  </a>
</p>

`futures-rs` is a library providing the foundations for asynchronous programming in Rust.
It includes key trait definitions like `Stream`, as well as utilities like `join!`,
`select!`, and various futures combinator methods which enable expressive asynchronous
control flow.

## Usage

Add this to your `Cargo.toml`:

```toml
[dependencies]
futures = "0.3"
```

The current `futures` requires Rust 1.71 or later.

### Feature `std`

Futures-rs works without the standard library, such as in bare metal environments.
However, it has a significantly reduced API surface. To use futures-rs in
a `#[no_std]` environment, use:

```toml
[dependencies]
futures = { version = "0.3", default-features = false }
```

## License

Licensed under either of [Apache License, Version 2.0](https://github.com/rust-lang/futures-rs/blob/HEAD/LICENSE-APACHE) or
[MIT license](https://github.com/rust-lang/futures-rs/blob/HEAD/LICENSE-MIT) at your option.

Unless you explicitly state otherwise, any contribution intentionally submitted
for inclusion in the work by you, as defined in the Apache-2.0 license, shall
be dual licensed as above, without any additional terms or conditions.

---

## About this Kotlin port

### Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:futures-kotlin:0.1.1")
}
```

### Building

```bash
./gradlew build
./gradlew test
```

### Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

### Porting guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for translator discipline, port-lint header convention, and Rust → Kotlin idiom mapping.

### License

This Kotlin port is distributed under the same MIT license as the upstream [`rust-lang/futures-rs`](https://github.com/rust-lang/futures-rs). See [LICENSE](LICENSE) (and any sibling `LICENSE-*` / `NOTICE` files mirrored from upstream) for the full text.

Original work copyrighted by the futures-rs authors.  
Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

### Acknowledgments

Thanks to the [`rust-lang/futures-rs`](https://github.com/rust-lang/futures-rs) maintainers and contributors for the original Rust implementation. This port reproduces their work in Kotlin Multiplatform; bug reports about upstream design or behavior should go to the upstream repository.
