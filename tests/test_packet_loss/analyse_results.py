log_device_1 = open("./log_device1.txt", "r")
log_device_2 = open("./log_device2.txt", "r")

messages_sent = 0

messages_received = 0

received_nonces = []

for line in log_device_1:
  line_list = line.split(" ")
  if len(line_list) > 8 and line_list[5] == "MulticastClient:" and "FULL_LEDGER" in line_list[8]:
    print(line)
    messages_sent += 1

for line in log_device_2:
  line_list = line.split(" ")
  if len(line_list) > 6 and line_list[5] == "MulticastServer:" and "FULL_LEDGER" in line_list[6]:
    nonce = int(line_list[7])
    if not nonce in received_nonces:
      print(line)
      messages_received += 1
      received_nonces.append(nonce)

package_loss = 1 - (messages_received / messages_sent)
print("Package loss:", package_loss)




