#!/usr/bin/env bash
set -euo pipefail
mkdir -p device-results
capture() {
  adb pull /sdcard/Android/data/com.tabula.v3/files/cloud-ui-failures device-results/ui-failures || true
  adb logcat -d -v threadtime > device-results/ui-logcat.txt || true
  adb shell screencap -p /sdcard/cloud-ui-result.png || true
  adb pull /sdcard/cloud-ui-result.png device-results/ui-screen.png || true
}
adb wait-for-device
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
  echo "This probe only runs on a disposable emulator" >&2
  exit 1
fi
trap capture EXIT
case "${API_LEVEL:?API_LEVEL must identify the workflow device}" in
  36|37) ;;
  *) echo "Unsupported emulator API: $API_LEVEL" >&2; exit 1 ;;
esac
adb shell getprop ro.build.version.sdk | tr -d '\r' | grep -Fx "$API_LEVEL"
status=0
# 预期逐例计数（新增场景须同步声明）：CloudCleanupUiTest 8 例
# （laterCandidate / invalidatedCopy / albumDetailLoadFailureShowsBannerAndRetryRecovers（非空相册） /
#  albumDetailEmptyAlbumLoadFailureShowsErrorAndRetryShowsRealImage（空相册，元数据构造绕开 O2） /
#  albumDetailSlowLoadSwitchShowsNewAlbumContentAndCancelIsSafe（切图集/取消，debug 慢加载接缝） /
#  albumDetailLateResultAfterSwitchDoesNotOverwriteNewPage（晚到竞争，按图集身份阻塞/释放并等待旧请求完成） /
#  archiveFailureShowsToastAndKeepsSource（T01-UIFEEDBACK 归档失败 Toast，debugForceArchiveCopyFailure 接缝） /
#  unknownDeleteVerificationKeepsSourceRecordAndReportsReason），
# CloudRestoreUiTest 3 例（singleRestore / batchRestore / restoreKeepsUnknown）。
# CreateAlbumDiagnosticDeviceTest 2 例（O2 诊断：createBucket 分阶段 + createAlbum 真实入口）。
# ForegroundPermissionUiTest 2 例（C4/T07：外部删除/外部编辑回前台刷新；撤销路径强停应用进程含 instrumentation，探针不可执行，见套件 KDoc）。
for class in CloudCleanupUiTest CloudRestoreUiTest CreateAlbumDiagnosticDeviceTest ForegroundPermissionUiTest; do
  case "$class" in
    CloudCleanupUiTest) expected_tests=8 ;;
    CloudRestoreUiTest) expected_tests=3 ;;
    CreateAlbumDiagnosticDeviceTest) expected_tests=2 ;;
    ForegroundPermissionUiTest) expected_tests=2 ;;
    *) expected_tests=2 ;;
  esac
  dir="device-results/$class"
  mkdir -p "$dir"
  if ! adb shell pm clear com.tabula.v3 | tr -d '\r' | grep -Fx Success; then
    echo "Failed to clear app data for $class" >&2
    status=1
    continue
  fi
  output="$dir/instrumentation.txt"
  if ! python3 tools/run-volume-device-probe.py window before "$class"; then
    echo "Failed to capture device state before $class" >&2
    status=1
    continue
  fi
  if timeout 300s adb shell am instrument -w -r \
    -e allowSyntheticMediaProbe true \
    -e class "com.tabula.v3.${class}" \
    com.tabula.v3.test/androidx.test.runner.AndroidJUnitRunner | tee "$output"; then
    :
  else
    echo "Instrumentation failed or timed out for $class" >&2
    status=1
  fi
  if ! python3 tools/run-volume-device-probe.py window after "$class"; then
    echo "Device state or log check failed for $class" >&2
    status=1
  fi
  if ! grep -Eq "^OK \(${expected_tests} tests?\)" "$output"; then
    echo "Expected ${expected_tests} passing tests in $class" >&2
    status=1
  fi
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=|INSTRUMENTATION_STATUS_CODE: -[34]' "$output"; then
    echo "Failure or skip observed in $class" >&2
    status=1
  fi
  adb logcat -d -v threadtime > "$dir/logcat.txt" || true
  adb pull /sdcard/Android/data/com.tabula.v3/files/cloud-ui-failures "$dir/ui-failures" || true
done
exit "$status"
