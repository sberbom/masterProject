import pylab

folder = "stored_result_3_send_1"

folders = ["stored_result_1_send_1", "stored_result_2_send_1", "stored_result_3_send_1", "stored_result_4_send_1"]
number_of_multicast_packets = 0
packet_loss_request_ledger_total = []
packet_loss_full_ledger_total = []
packet_loss_total = []

for folder in folders:
  number_of_multicast_packets = number_of_multicast_packets + 1

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
    if len(filtered_line_list) > 8 and filtered_line_list[5] == "MulticastClient:" and "FULL_LEDGER" in filtered_line_list[8]:
      ledgers_sent += 1

  for line in log_device_2:
    line_list = line.split(" ")
    filtered_line_list = list(filter(lambda el: el != "", line_list))
    if len(filtered_line_list) > 6 and filtered_line_list[5] == "MulticastServer:" and "FULL_LEDGER" in filtered_line_list[6]:
      nonce = int(filtered_line_list[7])
      if not nonce in received_nonces:
        ledgers_received += 1
        received_nonces.append(nonce)

  print("\n\n")
  print("Number of multicast packets: {}".format(number_of_multicast_packets))

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

  packet_loss_request_ledger_total.append(1 - ledgers_sent/requests_sent)
  packet_loss_full_ledger_total.append(packet_loss_ledger)
  packet_loss_total.append(1 - total_received / total_sent)

def valuelabel(x,y, offset=0):
    for i in range(len(x)):
        value = round(round(y[i], 3) * 100, 1)
        value_string = str(value) + "%"
        pylab.text(i+offset, y[i] + 0.0005, value_string, ha = 'center')

x_coordinates = pylab.arange(len(folders))
width = 0.3

request_ledger_bar = pylab.bar(x_coordinates-width, packet_loss_request_ledger_total, width, label="Request ledger")
ledgers_receved_bar = pylab.bar(x_coordinates, packet_loss_full_ledger_total, width, label="Ledgers received")
total_bar = pylab.bar(x_coordinates+width, packet_loss_total, width, label="Total")


valuelabel(x_coordinates, packet_loss_request_ledger_total, offset=-width)
valuelabel(x_coordinates, packet_loss_full_ledger_total)
valuelabel(x_coordinates, packet_loss_total, offset=width+0.05)

pylab.xlabel("Number of multicast messages")
pylab.ylabel("Packet loss %")
pylab.xticks(x_coordinates, x_coordinates+1)
pylab.legend(loc="upper right")
pylab.show()

