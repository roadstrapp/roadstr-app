#!/usr/bin/env python3
import argparse
import pathlib
import shutil


def tile_path(tile_id: int) -> pathlib.Path:
    if tile_id < 0 or tile_id >= 720 * 1440:
        raise ValueError(f"invalid level-2 tile id: {tile_id}")
    groups = f"{tile_id:09d}"
    return pathlib.Path("2", groups[0:3], groups[3:6], groups[6:9] + ".gph")


def copy_tree(source: pathlib.Path, target: pathlib.Path) -> None:
    if source.is_dir():
        shutil.copytree(source, target, dirs_exist_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="Select a Valhalla 0/1 skeleton and level-2 tiles")
    parser.add_argument("--source", required=True, type=pathlib.Path)
    parser.add_argument("--target", required=True, type=pathlib.Path)
    parser.add_argument("--tile-ids", type=pathlib.Path)
    args = parser.parse_args()
    args.target.mkdir(parents=True, exist_ok=True)
    copy_tree(args.source / "0", args.target / "0")
    copy_tree(args.source / "1", args.target / "1")
    if args.tile_ids is None:
        return
    for token in args.tile_ids.read_text(encoding="utf-8").split():
        relative = tile_path(int(token))
        source = args.source / relative
        if not source.is_file():
            raise FileNotFoundError(source)
        target = args.target / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)


if __name__ == "__main__":
    main()
