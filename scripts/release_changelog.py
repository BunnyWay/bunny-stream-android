#!/usr/bin/env python3
"""Turns the Unreleased section of CHANGELOG.md into a dated release section.

Used by .github/workflows/release-changelog.yml when a release is published, and runnable by hand:

    python3 scripts/release_changelog.py release 4.3.0 2026-11-02   # date the section in place
    python3 scripts/release_changelog.py notes 4.3.0                # print that version's entries
    python3 scripts/release_changelog.py unreleased                 # print what Unreleased holds

The changelog follows Keep a Changelog: entries are written by hand under "## [Unreleased]" as
part of each pull request. Releasing moves them under "## [X.Y.Z] - YYYY-MM-DD" and leaves an empty
Unreleased heading for the next ones. Running "release" for a version that already has a section
changes nothing, so a re-run, or a section someone dated by hand, is safe.

Exit codes: 0 done, 1 bad input, 2 nothing to release (Unreleased is empty).
"""

import argparse
import re
import sys

UNRELEASED = "## [Unreleased]"
SECTION = re.compile(r"^## \[", re.MULTILINE)
VERSION = re.compile(r"^\d+\.\d+\.\d+$")
DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


class NothingToRelease(Exception):
    pass


def _section_bounds(text, heading_prefix):
    """Start of the heading line and end of the section (next "## [" or end of file)."""
    match = re.search(r"^" + re.escape(heading_prefix) + r".*$", text, re.MULTILINE)
    if match is None:
        return None
    following = SECTION.search(text, match.end())
    return match.start(), match.end(), following.start() if following else len(text)


def unreleased_body(text):
    bounds = _section_bounds(text, UNRELEASED)
    if bounds is None:
        raise ValueError("CHANGELOG.md has no '## [Unreleased]' heading")
    _, heading_end, end = bounds
    return text[heading_end:end].strip("\n")


def version_body(text, version):
    bounds = _section_bounds(text, "## [%s]" % version)
    if bounds is None:
        return None
    _, heading_end, end = bounds
    return text[heading_end:end].strip("\n")


def release(text, version, date):
    """Returns the changelog with Unreleased moved under a dated heading for [version]."""
    if version_body(text, version) is not None:
        return text
    body = unreleased_body(text)
    if not body.strip():
        raise NothingToRelease("nothing under '## [Unreleased]' to release as %s" % version)
    start, heading_end, end = _section_bounds(text, UNRELEASED)
    dated = "%s\n\n## [%s] - %s\n\n%s\n\n" % (UNRELEASED, version, date, body)
    return text[:start] + dated + text[end:]


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--changelog", default="CHANGELOG.md")
    commands = parser.add_subparsers(dest="command", required=True)
    release_cmd = commands.add_parser("release", help="date the Unreleased section in place")
    release_cmd.add_argument("version")
    release_cmd.add_argument("date")
    notes_cmd = commands.add_parser("notes", help="print the entries of a released version")
    notes_cmd.add_argument("version")
    commands.add_parser("unreleased", help="print the entries under Unreleased")
    args = parser.parse_args(argv)

    with open(args.changelog, encoding="utf-8") as f:
        text = f.read()

    try:
        if args.command == "unreleased":
            print(unreleased_body(text))
            return 0
        if not VERSION.match(args.version):
            print("not an X.Y.Z version: %r" % args.version, file=sys.stderr)
            return 1
        if args.command == "notes":
            body = version_body(text, args.version)
            if body is None:
                print("no '## [%s]' section" % args.version, file=sys.stderr)
                return 1
            print(body)
            return 0
        if not DATE.match(args.date):
            print("not a YYYY-MM-DD date: %r" % args.date, file=sys.stderr)
            return 1
        updated = release(text, args.version, args.date)
    except NothingToRelease as e:
        print(e, file=sys.stderr)
        return 2
    except ValueError as e:
        print(e, file=sys.stderr)
        return 1

    if updated != text:
        with open(args.changelog, "w", encoding="utf-8") as f:
            f.write(updated)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
