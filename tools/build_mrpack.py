"""Modrinth Appで読み込めるModパック（.mrpack）を作る。

使い方（steady-viewフォルダで実行）:
    ./gradlew build
    python tools/build_mrpack.py

できるもの: build/distributions/steadyview-<バージョン>.mrpack

- バージョンは gradle.properties の値（Minecraft、Fabric Loader、Fabric API、Mod本体）に合わせる
- Fabric APIはModrinthに公開されているため、ダウンロード先とハッシュだけをパックに書く（再配布しない）
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


def modrinth_file(project: str, version_number: str, minecraft_version: str) -> dict:
    """Modrinthから、指定したバージョンの主ファイルの情報を取得する。"""
    query = urllib.parse.urlencode({
        "game_versions": json.dumps([minecraft_version]),
        "loaders": json.dumps(["fabric"]),
    })
    versions = fetch_json(f"{MODRINTH_API}/project/{project}/version?{query}")
    for version in versions:
        if version["version_number"] == version_number:
            primary = next((f for f in version["files"] if f["primary"]), version["files"][0])
            return primary
    raise SystemExit(f"Modrinthに {project} {version_number}（{minecraft_version}向け）が見つからない")


def main() -> None:
    properties = read_properties(ROOT / "gradle.properties")
    mod_version = properties["version"]
    minecraft_version = properties["minecraft_version"]
    loader_version = properties["loader_version"]
    fabric_api_version = properties["fabric_api_version"]

    mod_jar = ROOT / "build" / "libs" / f"steadyview-{mod_version}.jar"
    if not mod_jar.exists():
        raise SystemExit(f"{mod_jar} がない。先に ./gradlew build を実行する")

    fabric_api = modrinth_file("fabric-api", fabric_api_version, minecraft_version)
    client_only = {"client": "required", "server": "unsupported"}
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": mod_version,
        "name": "Steady View",
        "summary": "Fabric 26.3 + Fabric API + Steady View（酔いやすい人向けの視点操作Mod）",
        "files": [
            {
                "path": f"mods/{fabric_api['filename']}",
                "hashes": {"sha1": fabric_api["hashes"]["sha1"], "sha512": fabric_api["hashes"]["sha512"]},
                "env": client_only,
                "downloads": [fabric_api["url"]],
                "fileSize": fabric_api["size"],
            }
        ],
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
