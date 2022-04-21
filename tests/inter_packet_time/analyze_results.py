import pylab

folders = []
packet_loss_total = []

for folder in folders:
    log_device_1 = open("./{}/log_device1.txt".format(folder), "r")
    log_device_2 = open("./{}/log_device2.txt".format(folder), "r")

    ledgers_sent = 0

    ledgers_received = 0

    received_nonces = []

    requests_sent = 0

    for line in log_device_1:
        line_list = line.split(" ")
        filtered_line_list = list(filter(lambda el: el != "", line_list))
        if "Number of rounds:" in line:
            requests_sent = int(filtered_line_list[-1])
        if len(filtered_line_list) > 6 and filtered_line_list[5] == "MulticastClient:" and "SENT_FULL_LEDGER" in filtered_line_list[6]:
            ledgers_sent += 1

    for line in log_device_2:
        line_list = line.split(" ")
        filtered_line_list = list(filter(lambda el: el != "", line_list))
        if len(filtered_line_list) > 7 and filtered_line_list[5] == "RegistrationHandler:" and "FULL_LEDGER" in filtered_line_list[6]:
            nonce = int(filtered_line_list[7])
            if not nonce in received_nonces:
                ledgers_received += 1
                received_nonces.append(nonce)

    print("\n\n")
    print("Leder length: {}".format(folder))

    print("Received {0} out of {1} requests for ledger.".format(ledgers_sent, requests_sent))

    print("Packet loss of request ledger: {:.3f}".format(1 - ledgers_sent/requests_sent))

    print("Received {0} out of {1} full ledgers.".format(ledgers_received, ledgers_sent))

    if ledgers_sent > 0:
        packet_loss_ledger = 1 - (ledgers_received / ledgers_sent)
        print("Packet loss of full ledger: {0:.3f}".format(packet_loss_ledger))
    else:
        print("No ledgers sent.")

    total_received = ledgers_sent + ledgers_received

    total_sent = ledgers_sent + requests_sent

    print("Received {0} out of {1} total messages.".format(total_received, total_sent))

    print("Total packet loss: {}".format(1 - total_received / total_sent))

    packet_loss_total.append(1 - ledgers_received / ledgers_sent)

def valuelabel(x,y, offset=0):
    for i in range(len(x)):
        value = round(round(y[i], 3) * 100, 1)
        value_string = str(value) + "%"
        pylab.text(i+offset, y[i] + 0.0005, value_string, ha = 'center')

x_coordinates = pylab.arange(len(folders))

valuelabel(x_coordinates, packet_loss_total)

pylab.bar(x_coordinates, packet_loss_total)
pylab.xlabel("Number of ledger entries")
pylab.ylabel("Packets loss %")
pylab.xticks(x_coordinates, folders)
pylab.show()