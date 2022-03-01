from datetime import datetime
from statistics import mean

log_device_1 = open("./log_device1.txt", "r")
log_device_2 = open("./log_device2.txt", "r")
log_device_3 = open("./log_device3.txt", "r")
log_device_4 = open("./log_device4.txt", "r")
log_device_5 = open("./log_device5.txt", "r")
log_device_6 = open("./log_device6.txt", "r")

accept_ledger_times = {}

start_app_timestamp = {}

current_round_number = 0

def search_file(index):
  log_device = open("./log_device{}.txt".format(index), "r")
  for line in log_device:
    line_list = line.split(" ")
    filtered_line_list = list(filter(lambda el: el != "", line_list))
    if "Number of rounds:" in line:
      number_of_rounds = int(filtered_line_list[-1])
      accept_ledger_times[index] = [None] * number_of_rounds
      start_app_timestamp[index] = [None] * number_of_rounds
    elif "round" == filtered_line_list[0]:
      current_round_number = int(filtered_line_list[1]) - 1
    elif len(filtered_line_list) > 6 and filtered_line_list[5] == "MulticastServer:" and "MULTICAST_SERVER_LISTENING" in filtered_line_list[6]:
      start_app_timestamp[index][current_round_number] = datetime(
        2022,
        int(filtered_line_list[0][:2]),
        int(filtered_line_list[0][3:]),
        int(filtered_line_list[1][:2]),
        int(filtered_line_list[1][3:5]),
        int(filtered_line_list[1][6:8]),
        int(filtered_line_list[1][9:])
      )
    elif len(filtered_line_list) > 6 and accept_ledger_times[index][current_round_number] is None and filtered_line_list[5] == "RegistrationHandler:" and "ACCEPTED_LEDGER" in filtered_line_list[6]:
      start = start_app_timestamp[index][current_round_number]
      accept_time = datetime(
        2022,
        int(filtered_line_list[0][:2]),
        int(filtered_line_list[0][3:]),
        int(filtered_line_list[1][:2]),
        int(filtered_line_list[1][3:5]),
        int(filtered_line_list[1][6:8]),
        int(filtered_line_list[1][9:])
      )
      time_diff = accept_time - start
      accept_ledger_times[index][current_round_number] = time_diff.total_seconds() * 1000

  filtered_accepted_ledger_times = list(filter(lambda el: el is not None, accept_ledger_times[index]))

  print("Average time to accept ledger of length {0} was {1:.3f}".format(index, mean(filtered_accepted_ledger_times)))

for i in range(2, 7):
  search_file(i)


