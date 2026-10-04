"""Modrinth Appで読み込めるModパック（.mrpack）を作る。

使い方（steady-viewフォルダで実行）:
    ./gradlew build
    python tools/build_mrpack.py

できるもの: build/distributions/steadyview-<バージョン>.mrpack

- バージョンは gradle.properties の値（Minecraft、Fabric Loader、Fabric API、Mod本体）に合わせる
- Fabric API・Mod Menu（とその必須の依存Mod）はModrinthに公開されているため、ダウンロード先とハッシュだけをパックに書く（再配布しない）
- Steady View本体はModrinthに公開していないため、jarをパックの overrides/mods に同梱する
"""

import hashlib
import json
import sys
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODRINTH_API = "https://api.modrinth.com/v2"
USER_AGENT = "Taniguchi-EX/steady-view build_mrpack"


def read_properties(path: Path) -> dict[str, str]:
    properties = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    return properties


def fetch_json(url: str):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request) as response:
        return json.load(response)


def modrinth_version(project: str, minecraft_version: str, version_number: str | None = None) -> dict:
    """Modrinthから、指定したバージョン（省略時は26.3向けの最新）の情報を取得する。"""
    query = urllib.parse.urlencode({
        "game_versions": json.dumps([minecraft_version]),
        "loaders": json.dumps(["fabric"]),
    })
    versions = fetch_json(f"{MODRINTH_API}/project/{project}/version?{query}")
    for version in versions:
        if version_number is None or version["version_number"] == version_number:
            return version
    raise SystemExit(f"Modrinthに {project} {version_number or '（最新）'}（{minecraft_version}向け）が見つからない")


def pack_file(version: dict) -> dict:
    """パックの files に書く1件（ダウンロード先とハッシュ）。"""
    primary = next((f for f in version["files"] if f["primary"]), version["files"][0])
    return {
        "path": f"mods/{primary['filename']}",
        "hashes": {"sha1": primary["hashes"]["sha1"], "sha512": primary["hashes"]["sha512"]},
        "env": {"client": "required", "server": "unsupported"},
        "downloads": [primary["url"]],
        "fileSize": primary["size"],
    }


def main() -> None:
    properties = read_properties(ROOT / "gradle.properties")
    mod_version = properties["version"]
    minecraft_version = properties["minecraft_version"]
    loader_version = properties["loader_version"]

    mod_jar = ROOT / "build" / "libs" / f"steadyview-{mod_version}.jar"
    if not mod_jar.exists():
        raise SystemExit(f"{mod_jar} がない。先に ./gradlew build を実行する")

    # パックに入れるModと、それらが必須とするModを集める（バージョンを指定したものは gradle.properties の値）
    # Mod Menu は、「Mod」一覧から設定画面を開くために入れる
    wanted = [("fabric-api", properties["fabric_api_version"]), ("modmenu", properties["modmenu_version"])]
    versions: dict[str, dict] = {}
    while wanted:
        project, version_number = wanted.pop(0)
        version = modrinth_version(project, minecraft_version, version_number)
        if version["project_id"] in versions:
            continue
        versions[version["project_id"]] = version
        for dependency in version["dependencies"]:
            if dependency["dependency_type"] == "required" and dependency["project_id"] not in versions:
                # 必須の依存Modは、バージョンの指定がなければ26.3向けの最新を使う
                wanted.append((dependency["project_id"], None))

    for version in versions.values():
        print(f"  {version['name']}")

    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": mod_version,
        "name": "Steady View",
        "summary": "Fabric 26.3 + Fabric API + Mod Menu + Steady View（酔いやすい人向けの視点操作Mod）",
        "files": [pack_file(version) for version in versions.values()],
        "dependencies": {
            "minecraft": minecraft_version,
            "fabric-loader": loader_version,
        },
    }

    output = ROOT / "build" / "distributions" / f"steadyview-{mod_version}.mrpack"
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as pack:
        pack.writestr("modrinth.index.json", json.dumps(index, ensure_ascii=False, indent=2))
        pack.write(mod_jar, f"overrides/mods/{mod_jar.name}")

    sha1 = hashlib.sha1(output.read_bytes()).hexdigest()
    print(f"作成: {output}（{output.stat().st_size} bytes, sha1 {sha1}）")


if __name__ == "__main__":
    sys.exit(main())
