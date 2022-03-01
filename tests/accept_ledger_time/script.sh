# Gets the first two connected devices and their id
devices=$(echo $(adb devices))
device1=$(echo $devices | cut -f5 -d$' ')
device2=$(echo $devices | cut -f7 -d$' ')
device3=$(echo $devices | cut -f9 -d$' ')
device4=$(echo $devices | cut -f11 -d$' ')
device5=$(echo $devices | cut -f13 -d$' ')
device6=$(echo $devices | cut -f15 -d$' ')
rounds=5
. ../commmon/initialize.sh $device1 $device2 $device3 $device4 $device5 $device6
for i in {1..$rounds}
do
   echo "round $i of $rounds"
   echo "round $i of $rounds" >> log_device1.txt
   adb -s "$device1" shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
   sleep 2
   device_counter=2
   for i in $device2 $device3 $device4 $device5 $device6
   do
      adb -s "$i" shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
      sleep 15
   done
   for i in $device1 $device2 $device3 $device4 $device5 $device6
   do
      adb -s "$i" shell am force-stop com.example.masterproject
   done
done
adb -s $device1 shell killall -2 logcat
adb -s $device2 shell killall -2 logcat