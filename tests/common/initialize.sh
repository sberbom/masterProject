# Gets the first two connected devices and their id
counter=1
 for i in "$@"
 do
   echo "$i"
   # Remove all running logcats, to ensure no double logging
   adb -s "$i" shell killall -2 logcat
   adb -s "$i" logcat --clear
   adb -s "$i" logcat MulticastClient:D MulticastServer:D RegistrationHandler:D System.err:W \*:S >> log_device$counter.txt &
   echo "Number of rounds: $rounds" > log_device$counter.txt
   counter=$(($counter+1))
done
