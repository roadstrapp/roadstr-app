#!/usr/bin/env bash
set -euo pipefail

package_name="${1:-app.roadstr}"
output_dir="${2:-/tmp/roadstr-performance-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$output_dir"

adb get-state > "$output_dir/device-state.txt"
adb shell dumpsys gfxinfo "$package_name" reset > /dev/null
adb shell dumpsys batterystats --reset > /dev/null

echo "Collecting a 60-second Roadstr performance sample for $package_name..."
adb shell top -b -d 1 -n 60 -o PID,CPU,RES,ARGS > "$output_dir/cpu.txt" &
top_pid=$!
sleep 60
wait "$top_pid"

adb shell dumpsys gfxinfo "$package_name" framestats > "$output_dir/frame-stats.txt"
adb shell dumpsys meminfo "$package_name" > "$output_dir/memory.txt"
adb shell dumpsys batterystats "$package_name" > "$output_dir/battery.txt"
adb shell dumpsys thermalservice > "$output_dir/thermal.txt"
adb shell dumpsys power > "$output_dir/power.txt"
adb logcat -d -s RoadstrPerformance:D > "$output_dir/roadstr-counters.txt"

echo "Probe written to $output_dir"
