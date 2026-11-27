#!/usr/bin/env python3
"""Static checks for the Central Admin JavaFX surface."""

from pathlib import Path
import re
import sys
from xml.etree import ElementTree


DESKTOP = Path(__file__).resolve().parents[1]
VIEW = DESKTOP / "src/main/resources/com/group/usbshield/desktop"
FX_ID = "{http://javafx.com/fxml/1}id"
REMOTE_ASSET = re.compile(r"(?:https?:)?//", re.IGNORECASE)


def check(view):
    if not (view / "desktop.fxml").is_file():
        return ["Desktop FXML is missing"]
    errors = [f"{markup.name}: {error}" for markup in sorted(view.glob("*.fxml")) for error in check_markup(markup)]
    errors.extend(check_css((view / "desktop.css").read_text(encoding="utf-8")))
    return errors


def check_markup(markup):
    errors = []
    root = ElementTree.parse(markup).getroot()
    ids = set()
    for node in root.iter():
        ident = node.get(FX_ID)
        if ident in ids:
            errors.append(f"Duplicate fx:id {ident}")
        if ident is not None:
            ids.add(ident)
        tag = node.tag.rsplit("}", 1)[-1]
        if tag in {"Button", "ToggleButton", "CheckBox", "RadioButton", "MenuButton"}:
            if not any(node.get(name, "").strip() for name in ("text", "accessibleText")):
                errors.append(f"{tag} {ident or node.get('onAction', '')} needs text or accessibleText")
        for attr in ("url", "source", "stylesheets", "image"):
            if REMOTE_ASSET.search(node.get(attr, "")):
                errors.append(f"{tag} {attr} loads a remote asset")
    return errors


def check_css(stylesheet):
    css = re.sub(r"/\*.*?\*/", "", stylesheet, flags=re.DOTALL)
    imports = re.findall(r"@import\s+['\"]([^'\"]+)", css, re.IGNORECASE)
    urls = re.findall(r"url\(\s*['\"]?([^'\")\s]+)", css, re.IGNORECASE)
    errors = []
    if any(REMOTE_ASSET.match(url) for url in imports + urls):
        errors.append("JavaFX stylesheet loads a remote asset")

    local_rules = re.sub(r"@import\b[^;]*;", "", css, flags=re.IGNORECASE)
    rules = re.findall(r"([^{}]+)\{([^{}]*)\}", local_rules)
    if not any(
        ".button:focused" in (selector.strip() for selector in selectors.split(","))
        and re.search(r"(?:^|;)\s*-fx-(?:border-color|background-color|effect)\s*:", declarations)
        for selectors, declarations in rules
    ):
        errors.append("Define a shared .button:focused style and verify visibility in JavaFX")
    return errors


def main():
    try:
        errors = check(VIEW)
    except (OSError, ElementTree.ParseError) as exc:
        errors = [str(exc)]
    for error in errors:
        print(error, file=sys.stderr)
    print("FAIL JavaFX design source" if errors else "PASS JavaFX design source")
    return bool(errors)


if __name__ == "__main__":
    sys.exit(main())
