#!/usr/bin/env python3
import argparse
import pathlib


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--file", required=True, type=pathlib.Path)
    parser.add_argument("--marker", required=True)
    parser.add_argument("--content", required=True)
    args = parser.parse_args()

    file = args.file
    start_marker = f"# LIVTET-INIT: {args.marker}"
    end_marker = f"# LIVTET-INIT: {args.marker} END"
    block = f"{start_marker}\n{args.content}\n{end_marker}"

    if not file.exists():
        file.write_text(f"{block}\n")
        return

    text = file.read_text()

    if start_marker in text:
        before, _, after = text.partition(start_marker)
        _, _, after = after.partition(end_marker)
        text = before + block + after
    else:
        text = text.rstrip() + "\n\n" + block + "\n"

    file.write_text(text)


if __name__ == "__main__":
    main()