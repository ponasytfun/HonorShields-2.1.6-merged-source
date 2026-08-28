#!/usr/bin/env python3
"""Offline verifier/release packager for the pre-cached Minecraft 26.2 workspace.

The normal supported build remains ``./gradlew build`` with Java 25. This helper
exists so the source can still be signature-checked and packaged in an offline
Java 17 sandbox: it lowers only copies of cached dependency class-file headers,
compiles every current source file, and overlays those classes onto the
already-remapped 1.0.0 JAR. Compile-only API stubs are never placed in the
release JAR.
"""

from __future__ import annotations

import shutil
import subprocess
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def gradle_property(name: str) -> str:
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip()
    raise KeyError(f"Missing Gradle property: {name}")


# Large rewritten Minecraft dependency JARs are temporary compile inputs. Keep
# them on the local temporary filesystem so workspace synchronization cannot
# truncate their central directories mid-build.
WORK = Path("/tmp/honorshields-manual-build")
PATCHED = WORK / "patched"
STUB_CLASSES = WORK / "stub-classes"
MOD_CLASSES = WORK / "mod-classes"
DIST = ROOT / "dist"
VERSION = gradle_property("mod_version")
MOD_PACKAGE = "com/honorablesmp/honorshields/"
OUTPUT = DIST / f"HonorShields-{VERSION}-Minecraft-26.2.jar"

COMMON = ROOT / ".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-043a8b3edf/26.2/minecraft-common-043a8b3edf-26.2.jar"
CLIENT = ROOT / ".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-043a8b3edf/26.2/minecraft-clientOnly-043a8b3edf-26.2.jar"
BASE_MOD = ROOT.parent / "HonorShields/build/libs/honorshields-1.0.0.jar"

SOURCES = sorted(
    path
    for source_root in (ROOT / "src/main/java", ROOT / "src/client/java")
    for path in source_root.rglob("*.java")
)


def run(command: list[str]) -> None:
    print("+", " ".join(command))
    subprocess.run(command, cwd=ROOT, check=True)


def lower_class_headers(source: Path, target: Path) -> None:
    if not source.is_file():
        raise FileNotFoundError(source)
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(source) as source_zip, zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as target_zip:
        for info in source_zip.infolist():
            data = source_zip.read(info.filename)
            if info.filename.endswith(".class") and data[:4] == b"\xca\xfe\xba\xbe":
                major = int.from_bytes(data[6:8], "big")
                if major > 61:
                    data = data[:6] + (61).to_bytes(2, "big") + data[8:]
            # Recreate the entry from its name instead of reusing the source
            # ZipInfo. Minecraft's large common JAR carries source offsets that
            # can produce an incomplete central directory when reused.
            target_zip.writestr(info.filename, data)


def compile_sources() -> None:
    if WORK.exists():
        shutil.rmtree(WORK)
    PATCHED.mkdir(parents=True)
    STUB_CLASSES.mkdir(parents=True)
    MOD_CLASSES.mkdir(parents=True)

    patched_common = PATCHED / "minecraft-common.jar"
    patched_client = PATCHED / "minecraft-client.jar"
    patched_mod = PATCHED / "honorshields-base.jar"
    lower_class_headers(COMMON, patched_common)
    lower_class_headers(CLIENT, patched_client)
    lower_class_headers(BASE_MOD, patched_mod)

    dependency_path = ":".join(map(str, [patched_common, patched_client, patched_mod]))
    stub_sources = sorted(str(path) for path in (ROOT / "tools/compile-stubs/src").rglob("*.java"))
    run([
        "java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-source", "17", "-target", "17",
        "-proc:none", "-classpath", dependency_path, "-d", str(STUB_CLASSES), *stub_sources,
    ])

    classpath = dependency_path + ":" + str(STUB_CLASSES)
    run([
        "java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-source", "17", "-target", "17",
        "-proc:none", "-Xlint:all,-processing,-classfile", "-classpath", classpath,
        "-d", str(MOD_CLASSES), *(str(source) for source in SOURCES),
    ])


def package_release() -> None:
    DIST.mkdir(exist_ok=True)
    entries: dict[str, bytes] = {}
    with zipfile.ZipFile(BASE_MOD) as base_zip:
        for info in base_zip.infolist():
            # Never retain stale mod bytecode from the bootstrap artifact. The
            # release must contain one coherent compilation of the current
            # source tree, not a mixture of old and new class files.
            if not info.is_dir() and not (info.filename.startswith(MOD_PACKAGE) and info.filename.endswith(".class")):
                entries[info.filename] = base_zip.read(info.filename)

    for path in MOD_CLASSES.rglob("*.class"):
        entries[path.relative_to(MOD_CLASSES).as_posix()] = path.read_bytes()

    resources = ROOT / "src/main/resources"
    for path in resources.rglob("*"):
        if path.is_file():
            name = path.relative_to(resources).as_posix()
            data = path.read_bytes()
            if name == "fabric.mod.json":
                data = data.replace(b"${version}", VERSION.encode("ascii"))
            entries[name] = data

    # The base artifact predates the newer client classes. Regenerate this
    # Loom attribute so dedicated servers know every class that belongs only to
    # the split client source set, including compiler-generated nested classes.
    manifest_name = "META-INF/MANIFEST.MF"
    client_entries = sorted(
        name for name in entries
        if name.startswith("com/honorablesmp/honorshields/client/") and name.endswith(".class")
    )
    entries[manifest_name] = update_manifest_attribute(
        entries[manifest_name], "Fabric-Loom-Client-Only-Entries", ";".join(client_entries)
    )

    with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as output_zip:
        for name in sorted(entries):
            output_zip.writestr(name, entries[name])
    print(f"Built {OUTPUT} ({OUTPUT.stat().st_size:,} bytes)")


def update_manifest_attribute(data: bytes, key: str, value: str) -> bytes:
    """Replace one manifest attribute and emit specification-compliant folds."""
    unfolded: list[str] = []
    for line in data.decode("utf-8").replace("\r\n", "\n").split("\n"):
        if line.startswith(" ") and unfolded:
            unfolded[-1] += line[1:]
        elif line:
            unfolded.append(line)

    replacement = f"{key}: {value}"
    prefix = f"{key}:"
    for index, line in enumerate(unfolded):
        if line.startswith(prefix):
            unfolded[index] = replacement
            break
    else:
        unfolded.append(replacement)

    folded: list[str] = []
    for line in unfolded:
        folded.append(line[:70])
        remaining = line[70:]
        while remaining:
            folded.append(" " + remaining[:69])
            remaining = remaining[69:]
    return ("\r\n".join(folded) + "\r\n\r\n").encode("utf-8")


def main() -> int:
    try:
        compile_sources()
        package_release()
    except (FileNotFoundError, subprocess.CalledProcessError) as error:
        print(f"manual build failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
