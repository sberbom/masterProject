from datetime import datetime
from statistics import mean

log_device_1 = open("./log_device1.txt", "r")
log_device_2 = open("./log_device2.txt", "r")

accept_ledger_times = []

start_app_timestamp = []

current_round_number = 0

for line in log_device_2:
  line_list = line.split(" ")
  filtered_line_list = list(filter(lambda el: el != "", line_list))
  if "Number of rounds:" in line:
    number_of_rounds = int(filtered_line_list[-1])
    accept_ledger_times = [None] * number_of_rounds
    start_app_timestamp = [None] * number_of_rounds
  elif "round" == filtered_line_list[0]:
    current_round_number = int(filtered_line_list[1]) - 1
  elif len(filtered_line_list) > 6 and filtered_line_list[5] == "MulticastServer:" and "MULTICAST_SERVER_LISTENING" in filtered_line_list[6]:
    start_app_timestamp[current_round_number] = datetime(
      2022,
      int(filtered_line_list[0][:2]),
      int(filtered_line_list[0][3:]),
      int(filtered_line_list[1][:2]),
      int(filtered_line_list[1][3:5]),
      int(filtered_line_list[1][6:8]),
      int(filtered_line_list[1][9:])
    )
  elif len(filtered_line_list) > 6 and filtered_line_list[5] == "RegistrationHandler:" and "ACCEPTED_LEDGER" in filtered_line_list[6]:
    start = start_app_timestamp[current_round_number]
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
    accept_ledger_times[current_round_number] = time_diff.total_seconds() * 1000

filtered_accepted_ledger_times = list(filter(lambda el: el is not None, accept_ledger_times))

print("Average time to accept ledger was {:.3f}".format(mean(filtered_accepted_ledger_times)))