folder = "stored_result_1_send_1"

log_device_1 = open("./{}/log_device1.txt".format(folder), "r")
log_device_2 = open("./{}/log_device2.txt".format(folder), "r")

messages_sent = 0

messages_received = 0

received_nonces = []

number_of_rounds = 0

for line in log_device_1:
  line_list = line.split(" ")
  filtered_line_list = list(filter(lambda el: el != "", line_list))
  if "Number of rounds:" in line:
    number_of_rounds = int(filtered_line_list[-1])
  if len(filtered_line_list) > 8 and filtered_line_list[5] == "MulticastClient:" and "FULL_LEDGER" in filtered_line_list[8]:
    messages_sent += 1

for line in log_device_2:
  line_list = line.split(" ")
  filtered_line_list = list(filter(lambda el: el != "", line_list))
  if len(filtered_line_list) > 6 and filtered_line_list[5] == "MulticastServer:" and "FULL_LEDGER" in filtered_line_list[6]:
    nonce = int(filtered_line_list[7])
    if not nonce in received_nonces:
      messages_received += 1
      received_nonces.append(nonce)

print("Received {0} out of {1} requests for ledger.".format(messages_sent, number_of_rounds))

print("Packet loss of request ledger: {:.3f}".format(1 - messages_sent/number_of_rounds))

print("Received {0} out of {1} full ledgers.".format(messages_received, messages_sent))

if messages_sent > 0:
  packet_loss_ledger = 1 - (messages_received / messages_sent)
  print("Packet loss of full ledger: {0:.3f}".format(packet_loss_ledger))
else: 
  print("No ledgers sent.")
