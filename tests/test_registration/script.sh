# Remove all running logcats, to ensure no double logging
adb -s $device1 shell killall -2 logcat
# Gets the first two connected devices and their id
devices=$(echo $(adb devices))
device1=$(echo $devices | cut -f5 -d$' ')
echo $device1
# Set up logcat
adb -s $device1 logcat --clear
adb -s $device1 logcat SignupActivity:D SignupActivity:D \*:S >> log_device1.txt &
# Delete stored data
adb -s $device1 shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
sleep 2
adb -s $device1 shell input tap 50 170 #hamburger menu
sleep 1
adb -s $device1 shell input tap 220 880 #delete all data
sleep 1
adb -s $device1 shell am force-stop com.example.masterproject
# Start test
rounds=100
echo "Number of rounds: $rounds" > log_device1.txt
for ((i=1; i<=$rounds; i++))
do
    echo "round $i of $rounds"
    echo "round $i of $rounds" >> log_device1.txt
    adb -s $device1 shell am start -n com.example.masterproject/com.example.masterproject.activities.MainActivity
    sleep 2
    adb -s $device1 shell input tap 50 170 #hamburger menu
    sleep 0.5
    adb -s $device1 shell input tap 220 450 #signup page
    sleep 0.5
    adb -s $device1 shell input tap 220 350 #select email
    sleep 0.5
    adb -s $device1 shell input text 'test@mail.no'
    sleep 0.5
    adb -s $device1 shell input tap 190 860 #singup button
    sleep 10
    adb -s $device1 shell input tap 50 170 #hamburger menu
    sleep 0.5
    adb -s $device1 shell input tap 220 880 #delete all data
    sleep 0.5
    adb -s $device1 shell am force-stop com.example.masterproject
    sleep 2
done
adb -s $device1 shell killall -2 logcat