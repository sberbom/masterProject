import pylab

log_device_1 = open("./log_device1.txt", "r")
log_device_2 = open("./log_device2.txt", "r")

startingTLSClientAt = {}
startingTLSServerAt = {}

trail_number = 0
for line in log_device_1:
    line_list = line.split(" ")
    filtered_line_list = list(filter(lambda el: el != "", line_list))
    if "round" in line and "of 100" in line:
        trail_number = int(filtered_line_list[1])
    if "Starting TLSClient" in line:
        time_list = filtered_line_list[1].split(":")
        second_list = time_list[2].split(".")
        microsecounds = int(time_list[0])*60000*60 + int(time_list[1])*60000 + int(second_list[0]) * 1000 + int(second_list[1])
        startingTLSClientAt[trail_number] = microsecounds

trail_number = 0
for line in log_device_2:
    line_list = line.split(" ")
    filtered_line_list = list(filter(lambda el: el != "", line_list))
    if "round" in line and "of 100" in line:
        trail_number = int(filtered_line_list[1])
    if "Starting TLSServer" in line:
        time_list = filtered_line_list[1].split(":")
        second_list = time_list[2].split(".")
        microsecounds = int(time_list[0])*60000*60 + int(time_list[1])*60000 + int(second_list[0]) * 1000 + int(second_list[1])
        startingTLSServerAt[trail_number] = microsecounds

totalEstablishmentTime = 0 
establishmentTimes = []
failedEstablishments = 0

clientKeys = startingTLSClientAt.keys()
serverKeys = startingTLSServerAt.keys()
for i in range(len(startingTLSServerAt)):
    if(i in clientKeys and i in serverKeys):
        establishmentTime = startingTLSServerAt[i] - startingTLSClientAt[i]
        establishmentTimes.append(establishmentTime)
        totalEstablishmentTime += establishmentTime
    else:
        failedEstablishments += 1

averageEstablishmentTime = totalEstablishmentTime / len(serverKeys)

print("Average mTLS establishment time: {:.0f}ms".format(averageEstablishmentTime))
print("Failed mTLS establishment: {}".format(failedEstablishments))

x_coordinates = list(range(len(establishmentTimes)))

pylab.bar(x_coordinates, establishmentTimes)
pylab.axhline(y=averageEstablishmentTime, color="orange")
pylab.xlabel("Trail number")
pylab.ylabel("mTLS establishment time (ms)")
pylab.show()