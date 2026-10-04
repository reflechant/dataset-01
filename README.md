# dataset-01

Clojure application using `tech.ml.dataset` compiled into:
- an **Uberjar**
- a small standalone **Native Binary** via GraalVM
- minimal **Docker / Podman Containers** (scratch and distroless)

## Prerequisites

- **Clojure CLI** (v1.12.3 or higher)
- **GraalVM** (Oracle GraalVM or Community Edition with `native-image`, for local compilation)
- **Docker** or **Podman** (for container images and containerized Linux compilation)

## Running Directly

Run via Clojure CLI:

```bash
# Print empty dataset:
clojure -M:run-m

# Load and print data/orders.json:
clojure -M:run-m data/orders.json
```

## Running Tests

Run the test suite:

```bash
clojure -T:build test
```

## 1. Building and Running the Uberjar

Build the standalone Java uberjar:

```bash
clojure -T:build build-uber
# or run tests + build:
clojure -T:build ci

# Execute the jar:
java -jar target/lionrouge/dataset-01-0.1.0-SNAPSHOT.jar data/orders.json
```

## 2. Building and Running the Small Native Binary

### Local Compile
Compile the application into a standalone OS-specific native binary using local GraalVM `native-image`:

```bash
clojure -T:build native

# Run the binary:
./target/dataset-01 data/orders.json
```

### Linux Compile (via Docker / Podman)
Compile a Linux standalone native executable inside a GraalVM container:

```bash
clojure -T:build native-linux

# The output executable is located at:
./target/dataset-01-linux data/orders.json
```

## 3. Packaging and Running Container Images

### Scratch Container (~68MB)
A minimal container image built on `scratch` containing only the statically-linked musl native binary:

```bash
# Build:
clojure -T:build container-scratch

# Run:
podman run --rm -v "$(pwd)/data:/data:ro,z" dataset-01-scratch /data/orders.json
```

### Distroless Container (~90MB)
A minimal container image built on Google's `distroless/base-debian12` with required glibc and zlib dependencies:

```bash
# Build:
clojure -T:build container-distroless

# Run:
podman run --rm -v "$(pwd)/data:/data:ro,z" dataset-01-distroless /data/orders.json
```

## License

Copyright © 2026 Rg

Distributed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0)
