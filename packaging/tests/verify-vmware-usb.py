#!/usr/bin/env python3
"""Guided read-only USB acceptance on the Ubuntu VMware guest."""

import argparse
from dataclasses import dataclass
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time


FIELDS = re.compile(r'(?:^|\s)([a-z-]+)\s+("(?:\\.|[^"\\])*"|\{[^}]*\}|\S+)')
USB_ID = re.compile(r"[0-9a-f]{4}:[0-9a-f]{4}")
PORT = re.compile(r"\d+-\d+(?:\.\d+)*")
MANAGED_LABEL = "usbshield-managed"


class CheckFailed(Exception):
    """A prerequisite, identity or observation did not meet the check contract."""


@dataclass(frozen=True)
class Device:
    runtime_id: str
    state: str
    usb_id: str
    hash: str
    port: str

    def same_usbguard_identity(self, other):
        return (self.runtime_id, self.hash, self.port) == (other.runtime_id, other.hash, other.port)


@dataclass(frozen=True)
class KernelState:
    address: tuple[int, int]
    authorized: str
    storage_visible: bool

    def matches(self, state):
        return (self.authorized, self.storage_visible) == (("1", True) if state == "allow" else ("0", False))


@dataclass(frozen=True)
class Attachment:
    device: Device
    kernel_address: tuple[int, int]


def field_values(body):
    fields = {}
    for key, value in FIELDS.findall(body):
        fields[key] = shlex.split(value)[0] if value.startswith('"') else value
    return fields


def usbguard(command):
    result = subprocess.run(
        ["sudo", "-n", "usbguard", command], capture_output=True, text=True, timeout=10, check=False
    )
    if result.returncode:
        raise CheckFailed(f"USBGuard {command} thất bại. Kiểm tra dịch vụ và phiên sudo.")
    return result.stdout


def select_device(output, usb_id, device_hash=None):
    candidates = []
    for line in output.splitlines():
        match = re.fullmatch(r"\s*(\d+):\s+(allow|block|reject)\s+(.*)", line)
        if match is None:
            continue
        runtime_id, state, body = match.groups()
        fields = field_values(body)
        if fields.get("id", "").lower() != usb_id or (device_hash and fields.get("hash") != device_hash):
            continue
        interfaces = fields.get("with-interface", "").strip("{}").split()
        if not any(re.fullmatch(r"08:[\da-fA-F*]+:[\da-fA-F*]+", interface) for interface in interfaces):
            raise CheckFailed("Thiết bị trùng VID:PID/HASH nhưng thiếu interface Mass Storage.")
        port = fields.get("via-port", "")
        if not fields.get("hash") or not PORT.fullmatch(port):
            raise CheckFailed("Thiếu HASH hoặc cổng USB hợp lệ để đối chiếu với Linux.")
        candidates.append(Device(runtime_id, state, usb_id, fields["hash"], port))
    if len(candidates) > 1:
        raise CheckFailed("Có nhiều USB trùng định danh. Tách các thiết bị trùng trước khi test.")
    return candidates[0] if candidates else None


def has_managed_allow(output, device_hash):
    for line in output.splitlines():
        match = re.fullmatch(r"\s*\d+:\s+allow\s+(.*)", line)
        if match is not None:
            fields = field_values(match[1])
            if fields.get("hash") == device_hash and fields.get("label") == MANAGED_LABEL:
                return True
    return False


def kernel_observation(device, sysfs=Path("/sys")):
    usb = sysfs / "bus" / "usb" / "devices" / device.port

    def address():
        return tuple(int((usb / name).read_text().strip()) for name in ("busnum", "devnum"))

    try:
        root = usb.resolve(strict=True)
        before = address()
        usb_id = ":".join((usb / name).read_text().strip().lower() for name in ("idVendor", "idProduct"))
        authorized = (usb / "authorized").read_text().strip()
        if usb_id != device.usb_id or authorized not in {"0", "1"}:
            raise CheckFailed("Sysfs không khớp thiết bị USBGuard đã chọn.")
        block_paths = (block.resolve() for block in (sysfs / "class" / "block").iterdir())
        storage_visible = any(path.is_relative_to(root) and path.exists() for path in block_paths)
        if address() != before:
            raise CheckFailed("Địa chỉ kernel của USB đã đổi trong lúc lấy mẫu.")
        return KernelState(before, authorized, storage_visible)
    except FileNotFoundError:
        return None
    except ValueError as exc:
        raise CheckFailed("Địa chỉ USB trong sysfs không hợp lệ.") from exc


def observed_attachment(device, state, sysfs=Path("/sys")):
    if device is None or device.state != state:
        return None
    kernel = kernel_observation(device, sysfs)
    return Attachment(device, kernel.address) if kernel and kernel.matches(state) else None


def wait_for(description, predicate, timeout):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        observation = predicate()
        remaining = until - time.monotonic()
        if observation and remaining >= 0:
            print(f"[PASS] {description}", flush=True)
            return observation
        if remaining > 0:
            time.sleep(min(0.5, remaining))
    raise CheckFailed(f"Hết thời gian chờ {description}.")


def prompt(message):
    input(message + " Nhấn Enter để bắt đầu quan sát.")


def guided_check(args):
    subprocess.run(["sudo", "-v"], check=True)

    def device(device_hash=None):
        return select_device(usbguard("list-devices"), args.usb_id, device_hash)

    prompt("Ngắt USB mẫu khỏi guest trong VMware. Giữ Central và Agent lab đang chạy.")
    wait_for("USB mẫu đã rút", lambda: device(args.hash) is None, args.timeout)
    prompt("Cắm USB mẫu vào đúng Ubuntu guest, trước khi cấp Allow.")

    initial = wait_for("USB ban đầu bị block, chưa được kernel authorize và không có thiết bị khối",
                       lambda: observed_attachment(device(args.hash), "block"), args.timeout)
    target_hash = initial.device.hash
    if has_managed_allow(usbguard("list-rules"), target_hash):
        raise CheckFailed("USB mẫu đã có managed Allow. Dùng USB synthetic chưa được cấp quyền.")

    def matched():
        return device(target_hash)

    def rule_and_state(original, rule_present, state):
        managed_allow = has_managed_allow(usbguard("list-rules"), target_hash)
        current = matched()
        if current is None or not original.device.same_usbguard_identity(current):
            raise CheckFailed("USB đã biến mất hoặc đổi instance trong bước yêu cầu giữ nguyên kết nối.")
        kernel = kernel_observation(current)
        if kernel is None or kernel.address != original.kernel_address:
            raise CheckFailed("USB đã mất hoặc đổi địa chỉ kernel trong bước yêu cầu giữ nguyên kết nối.")
        return managed_allow == rule_present and current.state == state and kernel.matches(state)

    prompt("Trong Desktop, cấp Allow cho USB mẫu tại endpoint này. Giữ USB đang cắm.")
    wait_for("managed Allow đã xuất hiện và instance cũ vẫn bị block",
             lambda: rule_and_state(initial, True, "block"), args.timeout)
    prompt("Rút USB mẫu khỏi Ubuntu guest.")
    wait_for("instance ban đầu đã rút", lambda: matched() is None, args.timeout)
    prompt("Cắm lại cùng USB vào Ubuntu guest.")

    attached = wait_for("sau khi cắm lại, USB được authorize và có thiết bị khối",
                        lambda: observed_attachment(matched(), "allow"), args.timeout)
    if args.revoke == "direct":
        prompt("Giữ nguyên kết nối. Nhấn Enter rồi bấm Revoke trong Desktop để script theo dõi cùng instance.")
        wait_for("Revoke trực tiếp trên cùng instance, kernel deauthorize và không còn thiết bị khối",
                 lambda: rule_and_state(attached, False, "block"), args.timeout)
    else:
        prompt("Thu hồi Allow trong Desktop. Giữ nguyên USB đang cắm.")
        wait_for("đã bỏ managed Allow và USB đang cắm vẫn được authorize theo baseline",
                 lambda: rule_and_state(attached, False, "allow"), args.timeout)
        prompt("Rút USB mẫu khỏi Ubuntu guest.")
        wait_for("instance sau Revoke đã rút", lambda: matched() is None, args.timeout)
        prompt("Cắm lại cùng USB vào Ubuntu guest.")
        wait_for("USB cắm lại sau Revoke bị deauthorize và không có thiết bị khối",
                 lambda: observed_attachment(matched(), "block"), args.timeout)

    print("[MANUAL] Đối chiếu desired/applied, event và thông báo trên Central/Ubuntu theo hướng dẫn lab.")
    print("[PASS] Các quan sát USBGuard và kernel của luồng USB đã chọn.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--usb-id", required=True, type=str.lower, metavar="VID:PID")
    parser.add_argument("--hash", help="exact USBGuard HASH when VID:PID is shared")
    parser.add_argument("--revoke", choices=("replug", "direct"), default="replug")
    parser.add_argument("--timeout", type=int, default=90, metavar="SECONDS")
    args = parser.parse_args()
    if not USB_ID.fullmatch(args.usb_id) or not 1 <= args.timeout <= 300:
        parser.error("VID:PID must use hex and timeout must be between 1 and 300 seconds")
    if sys.platform != "linux":
        parser.error("run on an Ubuntu guest with VMware USB passthrough")
    try:
        guided_check(args)
        return 0
    except (CheckFailed, EOFError, OSError, subprocess.SubprocessError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
