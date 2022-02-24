import pylab

log_device_1 = open("./log_device1.txt", "r")

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

averageEstablishmentTime = totalRegistrationTime / len(registrationTimes)

print("Average TCP establishment time: {:.0f}ms".format(averageEstablishmentTime))
print("Failed TCP establishment: {}".format(failedRegistrations))

x_coordinates = list(range(len(registrationTimes)))

pylab.bar(x_coordinates, registrationTimes)
pylab.axhline(y=averageEstablishmentTime, color="orange")
pylab.xlabel("Trail number")
pylab.ylabel("TCP establishment time (ms)")
pylab.show()