devices=$(echo $(adb devices))
device1=$(echo $devices | cut -f5 -d$' ')
device2=$(echo $devices | cut -f7 -d$' ')
test_path="tests/resends"
rounds=500
for var in "$@"
do
   git checkout test/resends/"$var"
   ./gradlew assembleDebug
   adb -s $device1 install app/build/outputs/apk/debug/app-debug.apk
   adb -s $device2 install app/build/outputs/apk/debug/app-debug.apk
   # Gets the first two connected devices and their id
   counter=1
   for i in "$device1" "$device2"
    do
      echo "$i"
      # Remove all running logcats, to ensure no double logging
      adb -s "$i" shell killall -2 logcat
      adb -s "$i" logcat --clear
      mkdir ./"$test_path"/"$var"
      # adb -s "$i" logcat MulticastClient:D MulticastServer:D RegistrationHandler:D System.err:W AndroidRuntime:E \*:S >> ./"$test_path"/"$var"/log_device$counter.txt &
      adb -s "$i" logcat MulticastClient:D MulticastServer:D RegistrationHandler:D AndroidRuntime:E \*:S >> ./"$test_path"/"$var"/log_device$counter.txt &
      echo "Number of rounds: $rounds" > ./"$test_path"/"$var"/log_device$counter.txt
      counter=$(($counter+1))
   done
   for i in {1..$rounds}
      do
         echo "round $i of $rounds"
         echo "round $i of $rounds" >> ./"$test_path"/"$var"/log_device1.txt
         echo "round $i of $rounds" >> ./"$test_path"/"$var"/log_device2.txt
         adb -s $device1 shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
         sleep 5
         adb -s $device2 shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
         sleep 8
         adb -s $device1 shell am force-stop com.example.masterproject
         adb -s $device2 shell am force-stop com.example.masterproject
         sleep 1
      done
   git add .
   git commit -m "Add results for $var"
done

adb -s $device1 shell killall -2 logcat
adb -s $device2 shell killall -2 logcat