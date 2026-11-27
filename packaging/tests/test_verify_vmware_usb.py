import argparse
from contextlib import redirect_stdout
import io
from pathlib import Path
import runpy
import tempfile
import unittest
from unittest.mock import patch


PROBE = runpy.run_path(str(Path(__file__).with_name("verify-vmware-usb.py")))
VID_PID = "0951:1665"


def usb_line(runtime=14, state="block", hash_value="wanted", port="2-1"):
    return (f'{runtime}: {state} id {VID_PID} serial "" name "USB name hash other" '
            f'hash "{hash_value}" parent-hash "parent" via-port "{port}" '
            'with-interface { 03:01:01 08:06:50 } with-connect-type "hotplug"')


def rule(hash_value="wanted"):
    return f'12: allow hash "{hash_value}" label "usbshield-managed"'


class ProbeTest(unittest.TestCase):
    def test_selects_one_exact_hash_and_port(self):
        lines = "\n".join((usb_line(), usb_line(15, hash_value="unrelated", port="2-2")))
        match = PROBE["select_device"](lines, VID_PID, "wanted")
        self.assertEqual(("14", "block", "wanted", "2-1"),
                         (match.runtime_id, match.state, match.hash, match.port))
        with self.assertRaises(PROBE["CheckFailed"]):
            PROBE["select_device"](lines, VID_PID)
        self.assertIsNone(PROBE["select_device"](lines, VID_PID, "missing"))

    def test_requires_marker_and_exact_managed_hash(self):
        lines = '\n'.join((rule("other"), '20: allow hash "wanted" label "foreign"', rule("wanted-extra")))
        self.assertFalse(PROBE["has_managed_allow"](lines, "wanted"))
        self.assertTrue(PROBE["has_managed_allow"](lines + "\n" + rule(), "wanted"))

    def test_kernel_requires_authorization_and_storage_from_the_same_port(self):
        with tempfile.TemporaryDirectory() as directory:
            sysfs = Path(directory)
            usb = sysfs / "bus/usb/devices/2-1"
            usb.mkdir(parents=True)
            for name, value in {"idVendor": "0951", "idProduct": "1665", "authorized": "1",
                                "busnum": "2", "devnum": "4"}.items():
                (usb / name).write_text(value, encoding="ascii")
            block = sysfs / "class/block"
            block.mkdir(parents=True)
            unrelated = block / "sda"
            unrelated.mkdir()
            target = block / "sdb"
            target_disk = usb / "storage-interface/block/sdb"
            target_disk.mkdir(parents=True)
            native_resolve = Path.resolve

            def resolve(path, strict=False):
                return native_resolve(target_disk if path == target else path, strict=strict)

            selected = PROBE["select_device"](usb_line(state="allow"), VID_PID)
            blocked = PROBE["select_device"](usb_line(), VID_PID)
            with patch.object(Path, "resolve", resolve):
                self.assertIsNone(PROBE["observed_attachment"](selected, "allow", sysfs))
                target.mkdir()
                attachment = PROBE["observed_attachment"](selected, "allow", sysfs)
                self.assertEqual((2, 4), attachment.kernel_address)
                (usb / "authorized").write_text("0", encoding="ascii")
                self.assertIsNone(PROBE["observed_attachment"](blocked, "block", sysfs))
                target.rmdir()
                self.assertIsNotNone(PROBE["observed_attachment"](blocked, "block", sysfs))
                (usb / "authorized").unlink()
                self.assertIsNone(PROBE["observed_attachment"](blocked, "block", sysfs))

    def run_flow(self, mode, phases):
        by_phase = iter(phases)
        state = None

        def set_phase(_message):
            nonlocal state
            state = next(by_phase)

        def fake_usbguard(command):
            return state[0 if command == "list-devices" else 1]

        def kernel(device, _sysfs=None):
            allowed = state[2] == "allow"
            address = (2, state[3]) if len(state) > 3 else (2, int(device.runtime_id))
            return PROBE["KernelState"](address, "1" if allowed else "0", allowed)

        namespace = PROBE["guided_check"].__globals__
        output = io.StringIO()
        with patch.dict(namespace, prompt=set_phase, usbguard=fake_usbguard, kernel_observation=kernel):
            with patch("subprocess.run"), redirect_stdout(output):
                PROBE["guided_check"](argparse.Namespace(usb_id=VID_PID, hash=None, timeout=1, revoke=mode))
        return output.getvalue()

    def test_guided_replug_and_direct(self):
        allow = usb_line(runtime=15, state="allow")
        start = [("", "", None), (usb_line(), "", "block"), (usb_line(), rule(), "block"),
                 ("", rule(), None), (allow, rule(), "allow")]
        replug = [(allow, "", "allow"), ("", "", None), (usb_line(runtime=16), "", "block")]
        direct = [(usb_line(runtime=15), "", "block")]
        self.assertIn("USB cắm lại sau Revoke bị", self.run_flow("replug", start + replug))
        self.assertIn("Revoke trực tiếp trên cùng instance", self.run_flow("direct", start + direct))
        with self.assertRaisesRegex(PROBE["CheckFailed"], "đổi instance"):
            self.run_flow("direct", start + [(usb_line(runtime=16), "", "block")])
        with self.assertRaisesRegex(PROBE["CheckFailed"], "đổi địa chỉ kernel"):
            self.run_flow("direct", start + [(usb_line(runtime=15), "", "block", 16)])

    def test_slow_success_after_deadline_is_failure(self):
        with patch("time.monotonic", side_effect=[0, 0, 2, 2]):
            with self.assertRaisesRegex(PROBE["CheckFailed"], "Hết thời gian"):
                PROBE["wait_for"]("test", lambda: True, 1)


if __name__ == "__main__":
    unittest.main()
