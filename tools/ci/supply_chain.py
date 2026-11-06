#!/usr/bin/env python3
"""依赖协议与漏洞忽略清单检查（CONTRIBUTING.md 基本规则第 7 条）。只用 Python 标准库。

用法（在仓库根目录执行）：
  python3 tools/ci/supply_chain.py licenses <CycloneDX JSON>   检查依赖协议
  python3 tools/ci/supply_chain.py trivyignore [.trivyignore.yaml]   检查漏洞忽略清单的每一条都写明了原因

协议规则：
  - GPL、AGPL、LGPL（含带 Classpath 例外的 GPL）视为不允许的协议；
  - 一个依赖声明了多个协议（多个 <license> 或 OR 表达式）时，按“可以任选其一”处理：
    只要有一个允许的协议，就可以使用（例如 logback 为 EPL-2.0 / LGPL-2.1 双协议）；
  - 全部协议都不允许时阻断，除非登记在 tools/ci/license-exceptions.txt（每条必须写原因，
    目前只有带 FOSS 例外的 MySQL Connector/J，见 CONTRIBUTING.md 基本规则第 7 条）；
  - 没有声明协议的依赖只给警告，需要人工确认；本项目自己的模块（org.tuoyu）不检查。
"""

import json
import re
import sys
from pathlib import Path

OWN_GROUP = "org.tuoyu"
EXCEPTIONS_FILE = Path("tools/ci/license-exceptions.txt")

GPL_FAMILY = re.compile(
    r"(^|[^a-z])(a|l)?gpl([^a-z]|$)|gnu\s+(affero\s+|lesser\s+|library\s+)?general\s+public",
    re.IGNORECASE,
)


def is_gpl_family(term: str) -> bool:
    return bool(GPL_FAMILY.search(term))


def alternatives(component: dict) -> list:
    """把一个依赖声明的协议展开成“可任选其一”的列表，每一项是一个协议文本（可能含 AND / WITH）"""
    result = []
    for entry in component.get("licenses") or []:
        if "expression" in entry:
            expression = entry["expression"].replace("(", " ").replace(")", " ")
            result.extend(part.strip() for part in re.split(r"\s+OR\s+", expression) if part.strip())
        elif "license" in entry:
            lic = entry["license"]
            text = lic.get("id") or lic.get("name") or ""
            if text:
                result.append(text)
    return result


def coordinate(component: dict) -> str:
    return f"{component.get('group', '')}:{component.get('name', '')}"


def load_exceptions(path: Path) -> dict:
    exceptions = {}
    if not path.is_file():
        return exceptions
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, _, reason = line.partition("|")
        key, reason = key.strip(), reason.strip()
        if not re.fullmatch(r"[\w.\-]+:[\w.\-]+", key) or not reason:
            fail(f"{path}:{number} 格式应为 “groupId:artifactId | 原因”，原因不能为空：{line}")
        exceptions[key] = reason
    return exceptions


def dependency_path(bom: dict, ref: str) -> str:
    """找一条从根模块到该依赖的路径，便于定位是谁引入的"""
    parents = {}
    for dep in bom.get("dependencies") or []:
        for child in dep.get("dependsOn") or []:
            parents.setdefault(child, []).append(dep["ref"])
    path, seen = [ref], {ref}
    current = ref
    while current in parents:
        candidates = [p for p in parents[current] if p not in seen]
        if not candidates:
            break
        current = candidates[0]
        seen.add(current)
        path.append(current)
    names = [re.sub(r"\?.*$", "", p).split("/")[-1] for p in reversed(path)]
    return " -> ".join(names)


def check_licenses(bom_file: str) -> None:
    bom = json.loads(Path(bom_file).read_text(encoding="utf-8"))
    exceptions = load_exceptions(EXCEPTIONS_FILE)
    used_exceptions = set()
    violations, unknown = [], []
    components = bom.get("components") or []

    for component in components:
        if component.get("group") == OWN_GROUP:
            continue
        coord = coordinate(component)
        options = alternatives(component)
        if not options:
            unknown.append(f"{coord}:{component.get('version', '')}")
            continue
        if any(not is_gpl_family(option) for option in options):
            continue
        if coord in exceptions:
            used_exceptions.add(coord)
            print(f"放行（已登记例外）：{coord}:{component.get('version', '')} [{' / '.join(options)}]，原因：{exceptions[coord]}")
            continue
        violations.append((component, options))

    print(f"\n共检查 {len(components)} 个组件（不含本项目模块）。")
    for item in unknown:
        print(f"::warning title=依赖未声明协议::{item} 没有在 pom 中声明协议，请人工确认")
    for stale in sorted(set(exceptions) - used_exceptions):
        print(f"::warning title=协议例外未使用::{EXCEPTIONS_FILE} 中的 {stale} 已不在依赖中（或已有允许的协议），可以删除")

    if violations:
        for component, options in violations:
            print(f"::error title=不允许的依赖协议::{coordinate(component)}:{component.get('version', '')} "
                  f"的协议为 {' / '.join(options)}（GPL、AGPL、LGPL 不允许，见 CONTRIBUTING.md 基本规则第 7 条）。"
                  f"引入路径：{dependency_path(bom, component.get('bom-ref', ''))}")
        fail(f"发现 {len(violations)} 个使用 GPL / AGPL / LGPL 协议的依赖。请替换或排除这些依赖；"
             f"确需例外时先修改 CONTRIBUTING.md 基本规则，再登记到 {EXCEPTIONS_FILE} 并写明原因")
    print("依赖协议检查通过。")


def check_trivyignore(path_text: str) -> None:
    """.trivyignore.yaml 中 vulnerabilities 下的每一条都必须有 id 与 statement（忽略原因）"""
    path = Path(path_text)
    if not path.is_file():
        fail(f"找不到 {path}")
    entries, current, section, item_indent = [], None, None, None
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.rstrip()
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        indent = len(line) - len(line.lstrip())
        if indent == 0:
            section, current, item_indent = line.split(":", 1)[0].strip(), None, None
            continue
        item = re.match(r"^\s*-\s*(\w+)\s*:\s*(.*)$", line)
        if item and (item_indent is None or indent == item_indent):
            # 顶层列表的一项（更深的列表，例如 purls 下的条目，属于当前项）
            item_indent = indent
            current = {"line": number, "section": section}
            entries.append(current)
            current[item.group(1)] = item.group(2).strip()
            continue
        field = re.match(r"^\s+(\w+)\s*:\s*(.*)$", line)
        if field and current is not None and indent > item_indent:
            current.setdefault(field.group(1), field.group(2).strip())
    problems = [e for e in entries if not e.get("id") or not e.get("statement", "").strip("'\" ")]
    for entry in problems:
        print(f"::error title=漏洞忽略清单缺少原因::{path}:{entry['line']} 每一条都要有 id 和 statement（忽略原因）")
    if problems:
        fail(f"{path} 中有 {len(problems)} 条缺少 id 或忽略原因")
    print(f"{path}：{len(entries)} 条忽略记录，均写明了原因。")


def fail(message: str) -> None:
    print(f"::error::{message}")
    sys.exit(1)


def main(argv: list) -> None:
    if len(argv) >= 2 and argv[0] == "licenses":
        check_licenses(argv[1])
    elif argv and argv[0] == "trivyignore":
        check_trivyignore(argv[1] if len(argv) > 1 else ".trivyignore.yaml")
    else:
        print(__doc__)
        sys.exit(2)


if __name__ == "__main__":
    main(sys.argv[1:])
