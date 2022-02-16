# Remove all running logcats, to ensure no double logging
adb -s $device1 shell killall -2 logcat
adb -s $device2 shell killall -2 logcat
# Gets the first two connected devices and their id
devices=$(echo $(adb devices))
device1=$(echo $devices | cut -f5 -d$' ')
device2=$(echo $devices | cut -f7 -d$' ')
echo $device1
echo $device2
adb -s $device1 logcat --clear
adb -s $device2 logcat --clear
adb -s $device1 logcat MulticastClient:D MulticastServer:D RegistrationHandler:D \*:S >> log_device1.txt &
adb -s $device2 logcat MulticastClient:D MulticastServer:D RegistrationHandler:D \*:S >> log_device2.txt &
rounds=100
echo "Number of rounds: $rounds" > log_device1.txt
echo "Number of rounds: $rounds" > log_device2.txt
for i in {1..$rounds}
do
   echo "round $i of $rounds" 
   echo "round $i of $rounds" >> log_device1.txt
   echo "round $i of $rounds" >> log_device2.txt
   adb -s $device1 shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
   sleep 2
   adb -s $device2 shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
   sleep 2
   adb -s $device1 shell am force-stop com.example.masterproject
   adb -s $device2 shell am force-stop com.example.masterproject
done
adb -s $device1 shell killall -2 logcat
adb -s $device2 shell killall -2 logcat