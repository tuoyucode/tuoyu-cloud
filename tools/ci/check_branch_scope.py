#!/usr/bin/env python3
"""检查改动是否放在了正确的分支上：框架在 dev / master，业务模块在长期分支 business（见 CONTRIBUTING.md“分支”）。

用法：
    python check_branch_scope.py <目标分支> [--base <框架分支>] [--ref <要检查的提交>]

- 目标分支为 dev 或 master：仓库中不能有 business: 登记的路径；
- 目标分支为 business 或以 business/、business- 开头：相对框架分支（默认 origin/dev，没有时用 dev）的改动，
  只能落在 business: 与 integration: 登记的路径内；
- 其他分支（例如从 dev 开的功能分支）：不检查，合并到 dev 时由 dev 的检查兜底。

范围登记在与本脚本同目录的 business-scope.txt。--ref 用于不切换分支检查其他提交，默认检查当前检出的内容（HEAD）。
"""

import argparse
import fnmatch
import subprocess
import sys
from pathlib import Path

FRAMEWORK_BRANCHES = {"dev", "master"}
SCOPE_FILE = Path(__file__).with_name("business-scope.txt")


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True, encoding="utf-8").stdout


def load_scope():
    business, integration = [], []
    for raw in SCOPE_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        kind, _, pattern = line.partition(":")
        pattern = pattern.strip()
        if kind.strip() == "business" and pattern:
            business.append(pattern)
        elif kind.strip() == "integration" and pattern:
            integration.append(pattern)
        else:
            sys.exit(f"{SCOPE_FILE.name} 中无法识别的一行：{raw}")
    return business, integration


def matches(path, patterns):
    for pattern in patterns:
        # 以 / 结尾的表示目录（可以含 * 通配）
        if pattern.endswith("/"):
            if fnmatch.fnmatchcase(path, pattern + "*"):
                return True
        elif fnmatch.fnmatchcase(path, pattern):
            return True
    return False


def is_business_branch(branch):
    return branch == "business" or branch.startswith(("business/", "business-"))


def resolve_base(base):
    if base:
        return base
    for candidate in ("origin/dev", "dev"):
        if subprocess.run(["git", "rev-parse", "--verify", "--quiet", candidate], capture_output=True).returncode == 0:
            return candidate
    sys.exit("找不到框架分支 origin/dev 或 dev，请用 --base 指定")


def main():
    # 输出中文，统一用 UTF-8（CI 与 Git Bash 都按 UTF-8 显示）
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="检查框架与业务代码是否放在正确的分支上")
    parser.add_argument("branch", help="目标分支：push 时为当前分支，PR 时为合并的目标分支")
    parser.add_argument("--base", help="业务分支对比的框架分支，默认 origin/dev，没有时用 dev")
    parser.add_argument("--ref", default="HEAD", help="要检查的提交，默认 HEAD")
    args = parser.parse_args()

    business, integration = load_scope()
    branch = args.branch.removeprefix("refs/heads/")

    if branch in FRAMEWORK_BRANCHES:
        files = git("ls-tree", "-r", "--name-only", args.ref).splitlines()
        found = [f for f in files if matches(f, business)]
        if found:
            print(f"{branch} 只放框架代码，下列业务文件应放在 business 分支（见 CONTRIBUTING.md“分支”）：")
            print("\n".join(f"  {f}" for f in found[:50]))
            if len(found) > 50:
                print(f"  ……共 {len(found)} 个")
            return 1
        print(f"{branch}：没有业务代码")
        return 0

    if is_business_branch(branch):
        base = resolve_base(args.base)
        changed = git("diff", "--name-only", f"{base}...{args.ref}").splitlines()
        outside = [f for f in changed if not matches(f, business) and not matches(f, integration)]
        if outside:
            print(f"{branch} 相对 {base} 改动了框架文件。框架改动先提交到 dev，再在 {branch} 上合并 dev；"
                  "确实是接入业务模块所需的，在 dev 上把文件登记到 business-scope.txt 的 integration:（见 CONTRIBUTING.md“分支”）：")
            print("\n".join(f"  {f}" for f in outside))
            return 1
        print(f"{branch}：相对 {base} 的 {len(changed)} 个改动都在业务范围内")
        return 0

    print(f"{branch}：不是 dev、master 或业务分支，不检查")
    return 0


if __name__ == "__main__":
    sys.exit(main())
