devices=$(echo $(adb devices))
device1=$(echo $devices | cut -f5 -d$' ')
device2=$(echo $devices | cut -f7 -d$' ')
rounds=500
. ../common/initialize.sh "$device1" "$device2"
for var in "$@"
do
git checkout test/inter_packet_time/"$var"
   for i in {1..$rounds}
   do
      echo "round $i of $rounds"
      echo "round $i of $rounds" >> ./"$var"/log_device1.txt
      echo "round $i of $rounds" >> ./"$var"/log_device2.txt
      adb -s $device1 shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
      sleep 6
      adb -s $device2 shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
      sleep 15
      adb -s $device1 shell am force-stop com.example.masterproject
      adb -s $device2 shell am force-stop com.example.masterproject
      sleep 1
   done
git add ./"$var"
git commit -m "Add results for $var"
done

adb -s $device1 shell killall -2 logcat
adb -s $device2 shell killall -2 logcat