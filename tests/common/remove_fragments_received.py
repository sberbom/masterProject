
test = "fragmentation/results"

folders = ["1", "2", "3", "4", "5", "7"]

for folder in folders:
    log_device_1 = open("../{}/{}/log_device1.txt".format(test, folder), "r")
    log_device_2 = open("../{}/{}/log_device2.txt".format(test, folder), "r")

    copy_device_1 = open("../{}/{}/copy/log_device1.txt".format(test, folder), "w")
    copy_device_2 = open("../{}/{}/copy/log_device2.txt".format(test, folder), "w")

    for line in log_device_1:
        line_list = line.split(" ")
        filtered_line_list = list(filter(lambda el: el != "", line_list))
        if not "System.err:" in line:
            copy_device_1.write(line)

    for line in log_device_2:
        line_list = line.split(" ")
        filtered_line_list = list(filter(lambda el: el != "", line_list))
        if not "System.err:" in line and not (len(filtered_line_list) > 7 and filtered_line_list[6] == "Has" and filtered_line_list[7] == "received"):
            copy_device_2.write(line)