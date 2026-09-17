#!/usr/bin/env python3
"""Generate the Xiaohongshu images through SiliconFlow's image API.

Every job sends one of the prepared 3:4 canvases (see `tools/make-xhs-canvas.swift`)
as the reference image and asks the model to build the final picture around it:
the screenshot has to survive untouched, the caption is the only thing added.

    swift tools/make-xhs-canvas.swift          # step 1: build the canvases
    export SILICONFLOW_API_KEY=sk-...          # key comes from the environment only
    python3 tools/siliconflow_images.py        # step 2: generate

Useful flags: --only 01-cover,02-home   --dry-run   --no-watermark   --allow-proxy-egress

The generated URLs expire after an hour, so every image is downloaded right
away and written to disk along with a small JSON sidecar holding the seed and
the parameters, which is what makes a good result reproducible.

Safety notes, because this script reads paths and talks to the network:

* Every path that is read or written goes through `_checked` first: the string
  must match a strict allow-list (letters, digits, dot, dash, underscore,
  forward slash), must not contain a `..` segment, and the fully resolved path —
  symlinks included — must still sit inside the repository. Only the value that
  came back from that check is ever handed to the filesystem.
* Job names are reduced to a single path segment before they become file names.
* The API key is read from the environment and never written anywhere.
* Every outbound request goes through `check_url`: http/https only, the API
  endpoint must be exactly one of `ALLOWED_API_HOSTS`, and the host must not be
  localhost, a loopback address, or a private or reserved address. The only way
  to relax that is the explicit `--allow-proxy-egress` flag, which exists because
  this machine's DNS returns 198.18.0.0/15 for public names behind an egress
  proxy; it is off by default and nothing else in the script turns it on.
"""

from __future__ import annotations

import argparse
import base64
import ipaddress
import json
import mimetypes
import os
import pathlib
import re
import socket
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_API_URL = "https://api.siliconflow.cn/v1/images/generations"
ALLOWED_API_HOSTS = {"api.siliconflow.cn"}

# The egress proxy this sandbox routes public traffic through answers DNS with
# addresses from the RFC 2544 benchmarking range. It is not a private network in
# any useful sense, but `ipaddress` classes it as one, so it needs a name.
PROXY_EGRESS_NETWORK = ipaddress.ip_network("198.18.0.0/15")

REPO_ROOT = os.path.realpath(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

SAFE_SEGMENT = re.compile(r"\A[0-9A-Za-z][0-9A-Za-z._-]*\Z")
SAFE_REL_PATH = re.compile(r"\A[0-9A-Za-z][0-9A-Za-z._/-]*\Z")


# --------------------------------------------------------------------------
# File access, repository only.
# --------------------------------------------------------------------------

def _checked(rel_path: str, what: str) -> str:
    """Allow-list the string, then confine the resolved real path to the repo."""
    if not isinstance(rel_path, str) or not SAFE_REL_PATH.match(rel_path):
        raise SystemExit(f"{what} 含不允许的字符，已拒绝：{rel_path!r}")
    if ".." in rel_path.split("/"):
        raise SystemExit(f"{what} 含上级目录引用，已拒绝：{rel_path!r}")
    resolved = os.path.realpath(os.path.join(REPO_ROOT, rel_path))
    if not resolved.startswith(REPO_ROOT + os.sep):
        raise SystemExit(f"{what} 越出仓库范围，已拒绝：{rel_path!r}")
    return resolved


def read_text_in_repo(rel_path: str, what: str) -> str:
    validated = _checked(rel_path, what)
    with open(validated, encoding="utf-8") as fh:
        return fh.read()


def read_bytes_in_repo(rel_path: str, what: str) -> bytes:
    validated = _checked(rel_path, what)
    return pathlib.Path(validated).read_bytes()


def write_bytes_in_repo(rel_path: str, data: bytes, what: str) -> int:
    """Write one file inside the repository, creating its directory if needed."""
    validated = _checked(rel_path, what)
    parent = os.path.dirname(validated)
    if parent and not os.path.isdir(parent):
        os.makedirs(parent, exist_ok=True)
    pathlib.Path(validated).write_bytes(data)
    return len(data)


# --------------------------------------------------------------------------
# Outbound requests: http/https only, never loopback, private or reserved.
# --------------------------------------------------------------------------

def _is_public_ip(ip: ipaddress._BaseAddress, allow_proxy_egress: bool) -> bool:
    if allow_proxy_egress and ip in PROXY_EGRESS_NETWORK:
        return True
    return not (ip.is_private or ip.is_loopback or ip.is_link_local
                or ip.is_multicast or ip.is_reserved or ip.is_unspecified)


def _host_verdict(host: str, allow_proxy_egress: bool) -> tuple[bool, str]:
    host = host.strip().strip("[]").lower().rstrip(".")
    if not host:
        return False, "主机名为空"
    if host == "localhost" or host.endswith((".localhost", ".local", ".internal", ".home.arpa")):
        return False, "指向本机"
    try:
        ip = ipaddress.ip_address(host)
    except ValueError:
        try:
            infos = socket.getaddrinfo(host, None)
        except socket.gaierror as exc:
            return False, f"域名解析失败（{exc.strerror or exc}）"
        if not infos:
            return False, "域名没有解析结果"
        for info in infos:
            resolved = ipaddress.ip_address(info[4][0])
            if not _is_public_ip(resolved, allow_proxy_egress):
                return False, f"解析到私有/保留地址 {resolved}"
        return True, ""
    return (_is_public_ip(ip, allow_proxy_egress), "字面量地址为私有/保留地址")


def check_url(url: str, allowed_hosts: set[str] | None, what: str, allow_proxy_egress: bool) -> str:
    parts = urllib.parse.urlsplit(url)
    if parts.scheme not in ("http", "https"):
        raise SystemExit(f"{what} 只允许 http/https：{url!r}")
    host = (parts.hostname or "").lower()
    if allowed_hosts is not None and host not in allowed_hosts:
        raise SystemExit(f"{what} 的主机 {host!r} 不在允许列表内：{sorted(allowed_hosts)}")
    ok, reason = _host_verdict(host, allow_proxy_egress)
    if not ok:
        raise SystemExit(f"{what} 被拒绝：{host!r} {reason}")
    return url


def as_data_uri(rel_path: str, what: str) -> str:
    """Inline a repository image as base64.

    Remote URLs are refused on purpose: this never fetches an image it was
    handed, so a job file cannot aim the script at an internal address.
    """
    raw = read_bytes_in_repo(rel_path, what)
    mime = mimetypes.guess_type(rel_path)[0] or "image/png"
    return f"data:{mime};base64," + base64.b64encode(raw).decode("ascii")


# --------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------

def request_json(url: str, payload: dict, key: str, timeout: float, watermark: bool) -> dict:
    headers = {
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    if not watermark:
        headers["X-Enable-Watermark"] = "0"
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=body, method="POST", headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_image_bytes(url: str, timeout: float, allow_proxy_egress: bool) -> bytes:
    check_url(url, None, "生成结果的下载地址", allow_proxy_egress)
    req = urllib.request.Request(url, headers={"User-Agent": "silverphone-xhs/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def with_retries(fn, attempts: int, label: str):
    last = None
    for i in range(attempts):
        try:
            return fn()
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", "replace")[:400]
            last = f"HTTP {exc.code} {exc.reason} — {detail}"
            # 4xx other than rate limiting will not fix themselves.
            if exc.code not in (408, 409, 429) and exc.code < 500:
                break
        except (OSError, json.JSONDecodeError) as exc:
            # OSError covers URLError, timeouts and the half-open connections
            # (RemoteDisconnected) that a loaded model endpoint sometimes drops.
            last = f"{type(exc).__name__}: {exc}"
        if i + 1 < attempts:
            wait = 5 * (i + 1)
            print(f"    {label} 第 {i + 1} 次失败（{last}），{wait}s 后重试")
            time.sleep(wait)
    raise SystemExit(f"{label} 失败：{last}")


# --------------------------------------------------------------------------

def resolve_key() -> str:
    key = os.environ.get("SILICONFLOW_API_KEY", "").strip()
    if not key:
        raise SystemExit(
            "没有拿到 API key。先 export SILICONFLOW_API_KEY=sk-... 再运行；"
            "这个脚本不读密钥文件，也不会把 key 写到磁盘上。"
        )
    if not key.startswith("sk-"):
        print("提醒：这个 key 看起来不是 SiliconFlow 的格式（一般以 sk- 开头）")
    return key


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jobs", default="design/xiaohongshu/jobs.json", help="任务文件，仓库内相对路径")
    parser.add_argument("--out", help="输出目录，仓库内相对路径；默认取任务文件里的 output_dir")
    parser.add_argument("--only", help="只跑指定任务，逗号分隔")
    parser.add_argument("--api-url", default=DEFAULT_API_URL)
    parser.add_argument("--timeout", type=float, default=300.0, help="单次请求超时秒数")
    parser.add_argument("--attempts", type=int, default=3, help="每个任务的尝试次数")
    parser.add_argument("--dry-run", action="store_true", help="只打印将要发送的内容，不联网")
    parser.add_argument("--allow-proxy-egress", action="store_true",
                        help="允许 198.18.0.0/15（本机 DNS 把公网域名解析成这个出口代理段时才需要）；默认关闭")
    parser.add_argument("--no-watermark", action="store_true",
                        help="请求不带 AI 水印；生成式内容需要自行做标识，见正文说明")
    args = parser.parse_args()
    proxy_ok = args.allow_proxy_egress

    spec = json.loads(read_text_in_repo(args.jobs, "任务文件"))
    defaults = spec.get("defaults", {})
    out_dir = args.out or spec.get("output_dir", "design/xiaohongshu/generated")
    only = {s.strip() for s in args.only.split(",")} if args.only else None
    jobs = [j for j in spec["jobs"] if only is None or j["name"] in only]
    if not jobs:
        raise SystemExit("没有匹配的任务")

    key = "DRY-RUN" if args.dry_run else resolve_key()
    if not args.dry_run:
        check_url(args.api_url, ALLOWED_API_HOSTS, "接口地址", proxy_ok)
    if proxy_ok:
        print("注意：已开启 --allow-proxy-egress，198.18.0.0/15 被当作公网出口代理")

    print(f"接口：{args.api_url}")
    print(f"输出：{out_dir}")
    print(f"任务：{len(jobs)} 个\n")

    failed = []
    for job in jobs:
        if not SAFE_SEGMENT.match(job["name"]):
            raise SystemExit(f"任务名不能用作文件名：{job['name']!r}")
        name = job["name"]
        params = dict(defaults)
        params.update({k: v for k, v in job.items() if k not in ("name", "image", "prompt", "note")})
        payload = {"prompt": job["prompt"], **params}

        size_mb = 0.0
        if job.get("image"):
            payload["image"] = as_data_uri(job["image"], f"{name} 的参考图")
            size_mb = len(payload["image"]) / 1024 / 1024

        print(f"[{name}] {payload.get('model')}  参考图 {size_mb:.1f} MB（base64）")
        if args.dry_run:
            print(json.dumps({**payload, "image": "<base64 已省略>" if size_mb else None},
                             ensure_ascii=False, indent=2))
            print()
            continue

        started = time.time()
        try:
            result = with_retries(lambda: request_json(args.api_url, payload, key, args.timeout,
                                                       not args.no_watermark),
                                  args.attempts, name)
        except SystemExit as exc:
            print(f"    {exc}")
            failed.append(name)
            continue

        images = result.get("images") or []
        if not images:
            print(f"    返回里没有图片：{json.dumps(result, ensure_ascii=False)[:300]}")
            failed.append(name)
            continue

        entry = images[0]
        if entry.get("url"):
            try:
                blob = with_retries(lambda: fetch_image_bytes(entry["url"], args.timeout, proxy_ok),
                                    args.attempts, name)
            except SystemExit as exc:
                print(f"    {exc}")
                failed.append(name)
                continue
        elif entry.get("b64_json"):
            blob = base64.b64decode(entry["b64_json"])
        else:
            print(f"    不认识的返回结构：{json.dumps(entry, ensure_ascii=False)[:300]}")
            failed.append(name)
            continue

        written = write_bytes_in_repo(f"{out_dir}/{name}.png", blob, f"{name} 的输出图")
        elapsed = time.time() - started
        print(f"    -> {out_dir}/{name}.png  {written / 1024:.0f} KB  {elapsed:.0f}s  seed={result.get('seed')}")

        sidecar = json.dumps({
            "name": name,
            "model": payload.get("model"),
            "prompt": job["prompt"],
            "params": params,
            "source_image": job.get("image"),
            "seed": result.get("seed"),
            "timings": result.get("timings"),
            "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        }, ensure_ascii=False, indent=2).encode("utf-8")
        write_bytes_in_repo(f"{out_dir}/{name}.json", sidecar, f"{name} 的参数记录")

    if failed:
        print("\n失败：" + "、".join(failed))
        return 1
    print("\n全部完成")
    return 0


if __name__ == "__main__":
    sys.exit(main())
