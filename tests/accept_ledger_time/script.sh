# Gets the first two connected devices and their id
devices=$(echo $(adb devices))
device1=$(echo $devices | cut -f5 -d$' ')
device2=$(echo $devices | cut -f7 -d$' ')
device3=$(echo $devices | cut -f9 -d$' ')
device4=$(echo $devices | cut -f11 -d$' ')
device5=$(echo $devices | cut -f13 -d$' ')
device6=$(echo $devices | cut -f15 -d$' ')
rounds=10
. ../common/initialize.sh "$device1" "$device2" "$device3" "$device4" "$device5" "$device6"
for round in {1..$rounds}
do
   echo "round $round of $rounds"
   echo "round $round of $rounds" >> log_device1.txt
   adb -s "$device1" shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
   sleep 15
   device_counter=2
   for device in $device2 $device3 $device4 $device5 $device6
   do
      echo "round $round of $rounds" >> log_device$device_counter.txt
      adb -s "$device" shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
      device_counter=$((device_counter+1))
      sleep 15
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
