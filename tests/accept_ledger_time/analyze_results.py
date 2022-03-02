from datetime import datetime
from statistics import mean

log_device_1 = open("non_ca_signed/log_device1.txt", "r")
log_device_2 = open("non_ca_signed/log_device2.txt", "r")
log_device_3 = open("non_ca_signed/log_device3.txt", "r")
log_device_4 = open("non_ca_signed/log_device4.txt", "r")
log_device_5 = open("non_ca_signed/log_device5.txt", "r")
log_device_6 = open("non_ca_signed/log_device6.txt", "r")

accept_ledger_times = {}

start_app_timestamp = {}

current_round_number = 0

total_ledgers = {}

correct_ledger = {}

rounds_checked = {}

def search_file(index):
  log_device = open("./log_device{}.txt".format(index), "r")
  total_ledgers[index] = 0
  correct_ledger[index] = 0
  for line in log_device:
    line_list = line.split(" ")
    filtered_line_list = list(filter(lambda el: el != "", line_list))
    if "Number of rounds:" in line:
      number_of_rounds = int(filtered_line_list[-1])
      accept_ledger_times[index] = [None] * number_of_rounds
      start_app_timestamp[index] = [None] * number_of_rounds
      rounds_checked[index] = []
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
        int(filtered_line_list[1][9:]) * 1000
      )
    elif len(filtered_line_list) > 6 and accept_ledger_times[index][current_round_number] is None and filtered_line_list[5] == "RegistrationHandler:" and "ACCEPTED_LEDGER" in filtered_line_list[6]:
      start = start_app_timestamp[index][current_round_number]
      if current_round_number not in rounds_checked[index]:
        total_ledgers[index] += 1
        rounds_checked[index].append(current_round_number)
        if int(filtered_line_list[8]) == index - 1:
          correct_ledger[index] += 1
      accept_time = datetime(
        2022,
        int(filtered_line_list[0][:2]),
        int(filtered_line_list[0][3:]),
        int(filtered_line_list[1][:2]),
        int(filtered_line_list[1][3:5]),
        int(filtered_line_list[1][6:8]),
        int(filtered_line_list[1][9:]) * 1000
      )
      time_diff = accept_time - start
      accept_ledger_times[index][current_round_number] = time_diff.total_seconds()

  filtered_accepted_ledger_times = list(filter(lambda el: el is not None, accept_ledger_times[index]))

  print("Average time to accept ledger of length {0} was {1:.3f} seconds".format(index, mean(filtered_accepted_ledger_times)))
  print("The probability of accepting the correct ledger at length {0} is {1:.3f}".format(index, correct_ledger[index]/total_ledgers[index]))

for i in range(2, 7):
  search_file(i)


