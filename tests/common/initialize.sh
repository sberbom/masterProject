# Gets the first two connected devices and their id
devices=$(echo $(adb devices))
device1=$(echo $devices | cut -f5 -d$' ')
device2=$(echo $devices | cut -f7 -d$' ')
echo $device1
echo $device2
# Remove all running logcats, to ensure no double logging
adb -s $device1 shell killall -2 logcat
adb -s $device2 shell killall -2 logcat
adb -s $device1 logcat --clear
adb -s $device2 logcat --clear
adb -s $device1 logcat MulticastClient:D MulticastServer:D RegistrationHandler:D \*:S >> log_device1.txt &
adb -s $device2 logcat MulticastClient:D MulticastServer:D RegistrationHandler:D \*:S >> log_device2.txt &