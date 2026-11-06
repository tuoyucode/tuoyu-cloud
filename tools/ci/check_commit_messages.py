#!/usr/bin/env python3
"""提交说明格式检查（只检查格式，不要求 DCO）。只用 Python 标准库。

用法（参数原样传给 git rev-list，自动排除 merge 提交）：
  python3 tools/ci/check_commit_messages.py <BASE>..<HEAD>
  python3 tools/ci/check_commit_messages.py <HEAD> --not <其他分支...>

格式（CONTRIBUTING.md 基本规则第 6 条）：
  - 第一行为 “类型: 中文描述”，类型取 feat、fix、docs、refactor、test、chore、build、ci，
    冒号为英文冒号，后面一个空格，描述中要有中文；
  - 有正文时，第二行必须是空行（描述后空一行，再写正文）。
"""

import re
import subprocess
import sys

TYPES = ("feat", "fix", "docs", "refactor", "test", "chore", "build", "ci")
SUBJECT = re.compile(r"^(" + "|".join(TYPES) + r"): \S.*$")
CHINESE = re.compile(r"[㐀-䶿一-鿿豈-﫿]")


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True, encoding="utf-8").stdout


def problems_of(message: str) -> list:
    lines = message.rstrip("\n").split("\n")
    subject = lines[0] if lines else ""
    problems = []
    if not SUBJECT.match(subject):
        hint = ""
        if "：" in subject.split(" ")[0]:
            hint = "（类型后要用英文冒号）"
        elif re.match(r"^\w+(\(.*\))?!?:", subject):
            hint = "（类型只能是 " + "、".join(TYPES) + "，不带范围与感叹号，冒号后一个空格）"
        problems.append("第一行应为 “类型: 中文描述”" + hint)
    elif not CHINESE.search(subject.split(": ", 1)[1]):
        problems.append("描述应使用中文")
    if len(lines) > 1 and lines[1].strip():
        problems.append("描述后要空一行，再写正文")
    return problems


def main(argv: list) -> None:
    if not argv:
        print(__doc__)
        sys.exit(2)
    commits = git("rev-list", "--no-merges", *argv).split()
    failed = 0
    for sha in commits:
        message = git("log", "-1", "--format=%B", sha)
        problems = problems_of(message)
        subject = message.split("\n", 1)[0]
        if problems:
            failed += 1
            print(f"::error title=提交说明格式不符合约定::{sha[:10]} “{subject}”：{'；'.join(problems)}")
        else:
            print(f"通过  {sha[:10]} {subject}")
    print(f"\n共检查 {len(commits)} 个提交（不含 merge 提交），不符合 {failed} 个。")
    if failed:
        print("::error::提交说明格式为 “类型: 中文描述”，类型取 " + "、".join(TYPES)
              + "；描述后空一行再写正文（见 CONTRIBUTING.md 基本规则第 6 条）。可用 git commit --amend 或 git rebase 修改后重新推送")
        sys.exit(1)


if __name__ == "__main__":
    main(sys.argv[1:])
