# Gets the first two connected devices and their id
devices=$(echo $(adb devices))
device1=$(echo $devices | cut -f5 -d$' ')
device2=$(echo $devices | cut -f7 -d$' ')
device3=$(echo $devices | cut -f9 -d$' ')
device4=$(echo $devices | cut -f11 -d$' ')
device5=$(echo $devices | cut -f13 -d$' ')
device6=$(echo $devices | cut -f15 -d$' ')
rounds=100
counter=1
./gradlew assembleDebug
 for i in "$device1" "$device2" "$device3" "$device4" "$device5" "$device6"
 do
   echo "$i"
   adb -s "$i" install app/build/outputs/apk/debug/app-debug.apk
   # Remove all running logcats, to ensure no double logging
   adb -s "$i" shell killall -2 logcat
   adb -s "$i" logcat --clear
   #adb -s "$i" logcat MulticastClient:D MulticastServer:D RegistrationHandler:D System.err:W AndroidRuntime:E \*:S >> ./tests/accept_ledger_time/log_device$counter.txt &
   adb -s "$i" logcat MulticastClient:D MulticastServer:D RegistrationHandler:D \*:S >> ./tests/accept_ledger_time/log_device$counter.txt &
   echo "Number of rounds: $rounds" > ./tests/accept_ledger_time/log_device$counter.txt
   counter=$(($counter+1))
done
for round in {1..$rounds}
do
   echo "round $round of $rounds"
   echo "round $round of $rounds" >> ./tests/accept_ledger_time/log_device1.txt
   adb -s "$device1" shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
   sleep 3
   device_counter=2
   for device in $device2 $device3 $device4 $device5 $device6
   do
      echo "round $round of $rounds" >> ./tests/accept_ledger_time/log_device$device_counter.txt
      adb -s "$device" shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
      device_counter=$((device_counter+1))
      sleep 7
   done
   for device in $device1 $device2 $device3 $device4 $device5 $device6
   do
      adb -s "$device" shell am force-stop com.example.masterproject
   done
   sleep 2
done
for device in $device1 $device2 $device3 $device4 $device5 $device6
do
   adb -s "$device" shell killall -2 logcat
done
