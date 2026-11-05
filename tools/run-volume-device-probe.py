import json
import os
from pathlib import Path
import re
import shutil
import sys
import subprocess
import time

out = Path("device-results")
out.mkdir(exist_ok=True)

def adb(*args, check=True):
    p = subprocess.run(["adb", *args], text=True, stdout=subprocess.PIPE,
                       stderr=subprocess.STDOUT, timeout=180)
    if check and p.returncode:
        raise RuntimeError(p.stdout)
    return p.stdout

def collect_host_metadata():
    """Collect only selected tool versions and AVD/image identity fields."""
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or "")
    sdk_available = bool(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")) and sdk.is_dir()
    avd_name = adb("emu", "avd", "name").splitlines()[0].strip()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", avd_name):
        raise RuntimeError("Invalid AVD name")
    avd_root = Path(os.environ.get("ANDROID_AVD_HOME", str(Path.home() / ".android" / "avd")))
    ini = avd_root / (avd_name + ".ini")
    def fields(path, names):
        if not path.is_file():
            return {"_error": f"metadata file missing: {path}"}
        result = {}
        for line in path.read_text(errors="replace").splitlines():
            key, sep, value = line.partition("=")
            if sep and key.strip() in names:
                result[key.strip()] = value.strip()
        return result
    root_fields = fields(ini, {"target", "path"})
    config = Path(root_fields.get("path", str(avd_root / (avd_name + ".avd")))) / "config.ini"
    config_fields = fields(config, {"image.sysdir.1", "image.sysdir.2", "avd.ini.encoding", "abi.type", "tag.id", "hw.gpu.mode"})
    image = config_fields.get("image.sysdir.1")
    source = fields(sdk / image / "source.properties", {"Pkg.Revision", "AndroidVersion.ApiLevel", "AndroidVersion.CodeName", "SystemImage.Abi", "SystemImage.TagId"}) if sdk_available and image else {"_error": "SDK root or image path unknown"}
    versions = {}
    for name in ("emulator", "sdkmanager", "avdmanager"):
        path = shutil.which(name)
        if not path and sdk_available:
            candidate = sdk / ("emulator/emulator" if name == "emulator" else f"cmdline-tools/latest/bin/{name}")
            if candidate.is_file():
                path = str(candidate)
        if not path:
            versions[name] = {"path": "unknown", "version": "unknown", "error": "tool not found in PATH"}
            continue
        try:
            result = subprocess.run([path, "-version" if name == "emulator" else "--version"], text=True,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=60)
            versions[name] = {"path": str(Path(path).resolve()), "returncode": result.returncode,
                              "version": result.stdout.strip() if result.returncode == 0 else "unknown",
                              "error": result.stdout.strip() if result.returncode else None}
        except (OSError, subprocess.SubprocessError) as error:
            versions[name] = {"path": str(Path(path).resolve()), "version": "unknown", "error": str(error)}
    cmdline_sources = {}
    for name in ("sdkmanager", "avdmanager"):
        tool_path = versions[name].get("path")
        if tool_path and tool_path != "unknown":
            cmdline_sources[name] = fields(Path(tool_path).resolve().parent.parent / "source.properties", {"Pkg.Revision"})
        else:
            cmdline_sources[name] = {"_error": "tool path unknown"}
    metadata = {"sourceCommit": os.environ["GITHUB_SHA"], "cmdline_sources": cmdline_sources, "sdk_path": str(sdk.resolve()) if sdk_available else "unknown", "selected_api": os.environ["API_LEVEL"],
                "device_sdk": adb("shell", "getprop", "ro.build.version.sdk").strip(),
                "avd_name": avd_name, "avd_ini": root_fields, "avd_config": config_fields,
                "image_source": source, "tools": versions}
    (out / "emulator-metadata.json").write_text(json.dumps(metadata, indent=2))
    if os.environ["API_LEVEL"] == "37" and root_fields.get("target") == "android-0":
        raise RuntimeError("API 37 AVD target is android-0; stop and investigate before device testing")

def process_snapshot():
    boot = adb("shell", "cat", "/proc/sys/kernel/random/boot_id").strip()
    uptime = float(adb("shell", "cat", "/proc/uptime").split()[0])
    processes = {}
    for name in ("surfaceflinger", "system_server"):
        pid_text = adb("shell", "pidof", name).strip()
        if not pid_text.isdigit():
            raise RuntimeError(f"Cannot uniquely identify {name}: {pid_text}")
        stat = adb("shell", "cat", f"/proc/{pid_text}/stat").strip()
        # /proc stat begins with pid and parenthesized comm; starttime is field 22.
        rest = stat[stat.rfind(")") + 2:].split()
        if len(rest) < 20:
            raise RuntimeError(f"Incomplete /proc stat for {name}")
        processes[name] = {"pid": int(pid_text), "starttime": int(rest[19])}
    return {"boot_id": boot, "uptime": uptime, "processes": processes,
            "logcat_since": adb("shell", "date +'%m-%d %H:%M:%S.000'").strip()}

def window(phase, name):
    if not re.fullmatch(r"[A-Za-z0-9_-]+", name):
        raise RuntimeError("Invalid test window name")
    path = out / ("window-" + name + ".json")
    if phase == "before":
        path.write_text(json.dumps({"before": process_snapshot()}, indent=2))
        return
    data = json.loads(path.read_text())
    before = data["before"]
    after = process_snapshot()
    data["after"] = after
    log = adb("logcat", "-d", "-v", "threadtime", "-T", before["logcat_since"])
    (out / ("window-" + name + "-logcat.txt")).write_text(log)
    issues = []
    if before["boot_id"] != after["boot_id"] or after["uptime"] < before["uptime"]:
        issues.append("device reboot")
    if before["processes"] != after["processes"]:
        issues.append("system process restarted")
    if re.search(r"DeadSystem(?:Runtime)?Exception|Assertion failed: !rcEnc->featureInfo\(\)->hasReadColorBufferDma", log):
        issues.append("DeadSystem or target native graphics assertion")
    data["issues"] = issues
    path.write_text(json.dumps(data, indent=2))
    if issues:
        raise RuntimeError("Unstable device during " + name + ": " + ", ".join(issues))

def install(apk):
    # Installation can race first-boot PackageManager initialization. Retry only
    # installation, never the assertions or deletion/reconciliation operations.
    for attempt in range(1, 4):
        p = subprocess.run(["adb", "install", "--no-streaming", "-r", apk],
                           text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=180)
        with (out / "install.txt").open("a") as f:
            f.write(f"{apk} attempt {attempt}\n{p.stdout}\n")
        if p.returncode == 0:
            return
        if attempt == 3:
            raise RuntimeError(p.stdout)
        time.sleep(5)
        adb("wait-for-device")

def wait_for_primary_storage():
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        volumes = adb("shell", "sm", "list-volumes", "all", check=False)
        with (out / "storage-readiness.txt").open("a") as f:
            f.write(volumes + "\n")
        if re.search(r"emulated;0\s+mounted\b", volumes):
            return
        time.sleep(2)
    raise RuntimeError("Emulator primary storage did not mount before device tests")

def main():
    adb("wait-for-device")
    if adb("shell", "getprop ro.kernel.qemu").strip() != "1":
        raise RuntimeError("This workflow only uses newly-created emulators")
    # Retain the existing probe's three-button navigation setting. Its ability
    # to prevent the observed RegionSampling crash has not been established.
    adb("shell", "cmd", "overlay", "enable-exclusive", "--category",
        "com.android.internal.systemui.navbar.threebutton")
    wait_for_primary_storage()
    install("device-apks/app-debug.apk")
    install("device-apks/app-debug-androidTest.apk")
    wait_for_primary_storage()
    window("before", "volume")
    try:
        log = adb("shell", "am", "instrument", "-w", "-r",
              "-e", "class", "com.tabula.v3.data.repository.VolumeDeletionDeviceTest",
              "-e", "allowSyntheticMediaProbe", "true",
              "com.tabula.v3.test/androidx.test.runner.AndroidJUnitRunner", check=False)
    finally:
        if "log" in locals():
            (out / "instrumentation.txt").write_text(log)
        window("after", "volume")
    observations = adb("exec-out", "run-as", "com.tabula.v3",
                       "cat", "files/volume-observations.jsonl", check=False)
    (out / "volume-observations.jsonl").write_text(observations)
    metadata = {"sdk": adb("shell", "getprop ro.build.version.sdk").strip(),
                "fingerprint": adb("shell", "getprop ro.build.fingerprint").strip(),
                "sourceCommit": os.environ["GITHUB_SHA"], "testedPackage": "com.tabula.v3",
                "scope": "owned synthetic JPEGs; real MediaStore queries and journal reconciliation"}
    (out / "device.json").write_text(json.dumps(metadata, indent=2))
    print(log, flush=True)
    if not re.search(r"OK \(3 tests\)", log) or "FAILURES!!!" in log or "INSTRUMENTATION_FAILED" in log:
        raise RuntimeError("Not all three actual-provider tests passed")
    rows = [json.loads(line) for line in observations.splitlines() if line.strip()]
    if len(rows) != 3:
        raise RuntimeError("Expected three volume observations")
    if any(str(row["sdk"]) != os.environ["API_LEVEL"] for row in rows):
        raise RuntimeError("Unexpected emulator Android version")
    print(json.dumps({"sdk": metadata["sdk"], "tests": 3, "failures": 0, "observations": rows}), flush=True)
    # Preserve all volume checks above; only then exercise the UI regressions.
    subprocess.run(["bash", "tools/run-cloud-ui-probe.sh"], check=True, timeout=720)

if len(sys.argv) > 1:
    if sys.argv[1:] == ["metadata"]:
        collect_host_metadata()
    elif len(sys.argv) == 4 and sys.argv[1] == "window" and sys.argv[2] in ("before", "after"):
        window(sys.argv[2], sys.argv[3])
    else:
        raise SystemExit("Invalid probe arguments")
    raise SystemExit(0)

try:
    collect_host_metadata()
    main()
finally:
    # Keep evidence even if setup or installation fails before instrumentation.
    for name, args in [("logcat.txt", ("logcat", "-d")),
                       ("storage.txt", ("shell", "df", "-h", "/data")),
                       ("properties.txt", ("shell", "getprop"))]:
        try:
            (out / name).write_text(adb(*args, check=False))
        except (RuntimeError, subprocess.SubprocessError) as error:
            (out / name).write_text(str(error))
