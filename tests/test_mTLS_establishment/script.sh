. ../common/initialize.sh
# Start test
rounds=100
echo "Number of rounds: $rounds" > log_device1.txt
echo "Number of rounds: $rounds" > log_device2.txt
for ((i=1; i<=$rounds; i++))
do
   echo "round $i of $rounds"
   echo "round $i of $rounds" >> log_device1.txt
   echo "round $i of $rounds" >> log_device2.txt
   adb -s $device1 shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
   sleep 2
   adb -s $device2 shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
   sleep 4
   adb -s $device1 shell input tap 500 750
   sleep 4
   adb -s $device1 shell am force-stop com.example.masterproject
   adb -s $device2 shell am force-stop com.example.masterproject
done
adb -s $device1 shell killall -2 logcat
adb -s $device2 shell killall -2 logcat
