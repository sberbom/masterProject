# Gets the first two connected devices and their id
counter=1
./gradlew assembleDebug
 for i in "$@"
 do
   echo "$i"
   # Remove all running logcats, to ensure no double logging
   adb -s "$i" shell killall -2 logcat
   adb -s "$i" logcat --clear
   adb -s "$i" logcat MulticastClient:D MulticastServer:D RegistrationHandler:D System.err:W AndroidRuntime:E \*:S >> ./log_device$counter.txt &
   echo "Number of rounds: $rounds" > ./log_device$counter.txt
   adb -s $device1 install app/build/outputs/apk/debug/app-debug.apk
   counter=$(($counter+1))
done
