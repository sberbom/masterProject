from cProfile import label
from turtle import color
import pylab

log_device_1 = open("./log_device_a21.txt", "r")
log_device_small = open("./log_device_s21.txt", "r")

startingRegistrationAt = {}
endRegistrationAt = {}

trail_number = 0
for line in log_device_1:
    line_list = line.split(" ")
    filtered_line_list = list(filter(lambda el: el != "", line_list))
    if "round" in line and "of 10" in line:
        trail_number = int(filtered_line_list[1])
    if "Sign up button pressed" in line:
        time_list = filtered_line_list[1].split(":")
        second_list = time_list[2].split(".")
        microsecounds = int(time_list[0])*60000*60 + int(time_list[1])*60000 + int(second_list[0]) * 1000 + int(second_list[1])
        startingRegistrationAt[trail_number] = microsecounds
    if "Offline registration complete" in line:
        time_list = filtered_line_list[1].split(":")
        second_list = time_list[2].split(".")
        microsecounds = int(time_list[0])*60000*60 + int(time_list[1])*60000 + int(second_list[0]) * 1000 + int(second_list[1])
        endRegistrationAt[trail_number] = microsecounds


startingRegistrationSmallAt = {}
endRegistrationSmallAt = {}

for line in log_device_small:
    line_list = line.split(" ")
    filtered_line_list = list(filter(lambda el: el != "", line_list))
    if "round" in line and "of 10" in line:
        trail_number = int(filtered_line_list[1])
    if "Sign up button pressed" in line:
        time_list = filtered_line_list[1].split(":")
        second_list = time_list[2].split(".")
        microsecounds = int(time_list[0])*60000*60 + int(time_list[1])*60000 + int(second_list[0]) * 1000 + int(second_list[1])
        startingRegistrationSmallAt[trail_number] = microsecounds
    if "Offline registration complete" in line:
        time_list = filtered_line_list[1].split(":")
        second_list = time_list[2].split(".")
        microsecounds = int(time_list[0])*60000*60 + int(time_list[1])*60000 + int(second_list[0]) * 1000 + int(second_list[1])
        endRegistrationSmallAt[trail_number] = microsecounds

totalRegistrationTime = 0 
registrationTimes = []
failedRegistrations = 0

startKeys = startingRegistrationAt.keys()
endKeys = endRegistrationAt.keys()
for i in range(len(startingRegistrationAt)):
    if(i in startKeys and i in endKeys):
        establishmentTime = endRegistrationAt[i] - startingRegistrationAt[i]
        registrationTimes.append(establishmentTime)
        totalRegistrationTime += establishmentTime
    else:
        failedRegistrations += 1

averageRegistrationTime = totalRegistrationTime / len(registrationTimes)


totalRegistrationTimeSmall = 0
registrationTimesSmall = []
failedRegistrationsSmall = 0

startKeys = startingRegistrationSmallAt.keys()
endKeys = endRegistrationSmallAt.keys()
for i in range(len(startingRegistrationSmallAt)):
    if(i in startKeys and i in endKeys):
        establishmentTime = endRegistrationSmallAt[i] - startingRegistrationSmallAt[i]
        registrationTimesSmall.append(establishmentTime)
        totalRegistrationTimeSmall += establishmentTime
    else:
        failedRegistrationsSmall += 1

averageRegistrationTimeSmall = totalRegistrationTimeSmall / len(registrationTimesSmall)

print("Average registration time: {:.0f}ms".format(averageRegistrationTime))
print("Failed registrations: {}".format(failedRegistrations))
print("Average registration time small: {:.0f}ms".format(averageRegistrationTimeSmall))
print("Failed registrations: {}".format(failedRegistrationsSmall))

x_coordinates = list(range(len(registrationTimes)))
pylab.bar(x_coordinates, registrationTimes, color="blue", label="Galaxy a71")
pylab.axhline(y=averageRegistrationTime, color="darkblue", label="Galaxy a71 average")

x_coordinates = list(range(len(registrationTimesSmall)))
pylab.bar(x_coordinates, registrationTimesSmall, color="orange", label="Galaxy s21")
pylab.axhline(y=averageRegistrationTimeSmall, color="red", label="Galaxy s21 average")

pylab.xlabel("Trial number")
pylab.ylabel("Registration time (ms)")
pylab.xticks([0, 20, 40, 60, 80, 99], [1, 20, 40, 60, 80, 100])
pylab.legend()
pylab.show()