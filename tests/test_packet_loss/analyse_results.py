log_device_1 = open("./stored_result_1/log_device1.txt", "r")
log_device_2 = open("./stored_result_1/log_device2.txt", "r")

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
  else:
    print(filtered_line_list)

for line in log_device_2:
  line_list = line.split(" ")
  filtered_line_list = list(filter(lambda el: el != "", line_list))
  if len(filtered_line_list) > 6 and filtered_line_list[5] == "MulticastServer:" and "FULL_LEDGER" in filtered_line_list[6]:
    nonce = int(filtered_line_list[7])
    if not nonce in received_nonces:
      messages_received += 1
      received_nonces.append(nonce)
    else:
      print(filtered_line_list)

print("Sent {0} packets. Expected to have sent {1} packets".format(messages_sent, number_of_rounds))

print("Received {0} out of {1} packets.".format(messages_received, messages_sent))

if messages_sent > 0:
  package_loss = 1 - (messages_received / messages_sent)
  print("Package loss:", "{0:.3f}".format(package_loss))
else: 
  print("No packets sent.")
