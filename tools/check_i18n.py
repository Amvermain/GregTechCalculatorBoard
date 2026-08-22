#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path

def main():
    root = Path(__file__).resolve().parent.parent
    lang_dir = root / "src" / "main" / "resources" / "assets" / "gtcalcboard" / "lang"
    en_path = lang_dir / "en_us.json"
    ko_path = lang_dir / "ko_kr.json"

    print("=" * 60)
    print("GT Calculator Board - i18n Verification Tool")
    print("=" * 60)

    if not en_path.exists():
        print(f"[ERROR] en_us.json not found at: {en_path}")
        sys.exit(1)
    if not ko_path.exists():
        print(f"[ERROR] ko_kr.json not found at: {ko_path}")
        sys.exit(1)

    try:
        with open(en_path, "r", encoding="utf-8") as f:
            en_data = json.load(f)
    except Exception as e:
        print(f"[ERROR] Failed to parse en_us.json: {e}")
        sys.exit(1)

    try:
        with open(ko_path, "r", encoding="utf-8") as f:
            ko_data = json.load(f)
    except Exception as e:
        print(f"[ERROR] Failed to parse ko_kr.json: {e}")
        sys.exit(1)

    en_keys = set(en_data.keys())
    ko_keys = set(ko_data.keys())

    missing_in_ko = en_keys - ko_keys
    missing_in_en = ko_keys - en_keys

    errors = 0

    print(f"Total keys in en_us.json: {len(en_keys)}")
    print(f"Total keys in ko_kr.json: {len(ko_keys)}")
    print("-" * 60)

    if missing_in_ko:
        print(f"[ERROR] Keys in en_us.json missing in ko_kr.json ({len(missing_in_ko)}):")
        for k in sorted(missing_in_ko):
            print(f"  - {k}: {en_data[k]}")
        errors += len(missing_in_ko)

    if missing_in_en:
        print(f"[ERROR] Keys in ko_kr.json missing in en_us.json ({len(missing_in_en)}):")
        for k in sorted(missing_in_en):
            print(f"  - {k}: {ko_data[k]}")
        errors += len(missing_in_en)

    if not missing_in_ko and not missing_in_en:
        print("[OK] Key parity: All keys exist in both en_us.json and ko_kr.json (100% matched).")

    # Format specifiers check
    token_pattern = re.compile(r"%[0-9]*\.?[0-9]*[sdfx%]")
    
    common_keys = en_keys & ko_keys
    token_mismatches = []

    for k in sorted(common_keys):
        en_val = en_data[k]
        ko_val = ko_data[k]

        en_tokens = token_pattern.findall(en_val)
        ko_tokens = token_pattern.findall(ko_val)

        # Filter out literal escaped %%
        en_tokens = [t for t in en_tokens if t != "%%"]
        ko_tokens = [t for t in ko_tokens if t != "%%"]

        if len(en_tokens) != len(ko_tokens):
            token_mismatches.append((k, en_val, ko_val, en_tokens, ko_tokens))
            errors += 1

    if token_mismatches:
        print(f"\n[ERROR] Format token count mismatches ({len(token_mismatches)}):")
        for k, en_val, ko_val, en_t, ko_t in token_mismatches:
            print(f"  Key: '{k}'")
            print(f"    EN ({len(en_t)} tokens): '{en_val}' -> {en_t}")
            print(f"    KO ({len(ko_t)} tokens): '{ko_val}' -> {ko_t}")
    else:
        print("[OK] Format token consistency: All format tokens (%s, %d, etc.) match perfectly across common keys.")

    print("=" * 60)
    if errors == 0:
        print("RESULT: ALL I18N CHECKS PASSED SUCCESSFULLY! (0 Errors)")
        print("=" * 60)
        sys.exit(0)
    else:
        print(f"RESULT: FAILED WITH {errors} ERROR(S).")
        print("=" * 60)
        sys.exit(1)

if __name__ == "__main__":
    main()
