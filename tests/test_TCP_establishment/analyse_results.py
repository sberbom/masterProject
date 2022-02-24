import pylab

log_device_1 = open("./log_device1.txt", "r")
log_device_2 = open("./log_device2.txt", "r")

startingTCPClientAt = {}
startingTCPServerAt = {}

trail_number = 0
for line in log_device_1:
    line_list = line.split(" ")
    filtered_line_list = list(filter(lambda el: el != "", line_list))
    if "round" in line and "of 10" in line:
        trail_number = int(filtered_line_list[1])
    if "Starting TCPClient" in line:
        time_list = filtered_line_list[1].split(":")
        second_list = time_list[2].split(".")
        microsecounds = int(time_list[0])*60000*60 + int(time_list[1])*60000 + int(second_list[0]) * 1000 + int(second_list[1])
        startingTCPClientAt[trail_number] = microsecounds

trail_number = 0
for line in log_device_2:
    line_list = line.split(" ")
    filtered_line_list = list(filter(lambda el: el != "", line_list))
    if "round" in line and "of 10" in line:
        trail_number = int(filtered_line_list[1])
    if "Starting TCPServer" in line:
        time_list = filtered_line_list[1].split(":")
        second_list = time_list[2].split(".")
        microsecounds = int(time_list[0])*60000*60 + int(time_list[1])*60000 + int(second_list[0]) * 1000 + int(second_list[1])
        startingTCPServerAt[trail_number] = microsecounds

totalEstablishmentTime = 0 
establishmentTimes = []
failedEstablishments = 0

clientKeys = startingTCPClientAt.keys()
serverKeys = startingTCPServerAt.keys()
for i in range(len(startingTCPServerAt)):
    if(i in clientKeys and i in serverKeys):
        establishmentTime = startingTCPServerAt[i] - startingTCPClientAt[i]
        establishmentTimes.append(establishmentTime)
        totalEstablishmentTime += establishmentTime
    else:
        failedEstablishments += 1

averageEstablishmentTime = totalEstablishmentTime / len(serverKeys)

print(len(serverKeys))

print("Average TCP establishment time: {:.0f}ms".format(averageEstablishmentTime))
print("Failed TCP establishment: {}".format(failedEstablishments))

x_coordinates = list(range(len(establishmentTimes)))

pylab.bar(x_coordinates, establishmentTimes)
pylab.axhline(y=averageEstablishmentTime, color="orange")
pylab.xlabel("Trail number")
pylab.ylabel("TCP establishment time (ms)")
pylab.show()