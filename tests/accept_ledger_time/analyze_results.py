from datetime import datetime
from statistics import mean
import pylab


results_accepting_correct_ledger = {}

results_time_to_accept_ledger = {}

# Maps the index of a device, to a dictionary mapping the round number to the time it took to
# accept the ledger that round
accept_ledger_times = {}

# Maps the the index of a device to a dictionary mapping the round number to the time the
# application started
start_app_timestamp = {}

# Maps the index of a device to the number of times it has accepted a ledger based on its own ledger
# request, in total during all the tests
total_ledgers = {}

# Maps the index of a device to the number of times it has accepted a correct ledger after its own
# ledger request, in total during all the tests. Is analog to total_ledgers but for correct ledgers
# only
correct_ledger = {}

# Maps the index of a device to the rounds where a ledger has been accepted
rounds_checked = {}

# Maps the index of a device to a list where the index is the round number and the value is the
# nonce user for this users request that round
nonce_of_request_sent = {}

# Maps the index of a device to the number of times it did not accept any ledger as a response to
# its own request
ledgers_missing = {}

# Maps the index of a device to the rounds where it did not accept a ledger
rounds_with_missing_ledger = {}

# Maps the index of a device to the rounds where the ledger was incorrect
incorrect_ledgers = {}

def search_file(index, folder):
  log_device = open("{0}/log_device{1}.txt".format(folder, index), "r")
  # Initialize some counters and dicts for this device
  total_ledgers[index] = 0
  correct_ledger[index] = 0
  ledgers_missing[index] = 0
  rounds_checked[index] = []
  incorrect_ledgers[index] = []
  rounds_with_missing_ledger[index] = []
  current_round_number = -1
  for line in log_device:
    line_list = line.split(" ")
    filtered_line_list = list(filter(lambda el: el != "", line_list))
    # Initialize some dicts that depend on the number of rounds in total
    if "Number of rounds:" in line:
      number_of_rounds = int(filtered_line_list[-1])
      nonce_of_request_sent[index] = [None] * number_of_rounds
      accept_ledger_times[index] = [None] * number_of_rounds
      start_app_timestamp[index] = [None] * number_of_rounds
    # If this is the first line of this round...
    elif "round" == filtered_line_list[0]:
      # ... and a ledger was not accepted last round...
      if current_round_number not in rounds_checked[index] and current_round_number != -1:
        #... we update the number of ledgers missed
        ledgers_missing[index] += 1
        rounds_with_missing_ledger[index].append(current_round_number)
      #... we update the current round number
      current_round_number = int(filtered_line_list[1]) - 1
    # If this is the line where the multicast server starts listening...
    elif len(filtered_line_list) > 6 and filtered_line_list[5] == "MulticastServer:" and "MULTICAST_SERVER_LISTENING" in filtered_line_list[6]:
      #... we record the time.
      start_app_timestamp[index][current_round_number] = datetime(
        2022,
        int(filtered_line_list[0][:2]),
        int(filtered_line_list[0][3:]),
        int(filtered_line_list[1][:2]),
        int(filtered_line_list[1][3:5]),
        int(filtered_line_list[1][6:8]),
        int(filtered_line_list[1][9:]) * 1000
      )
    # If this is the line where this user sends a request for ledger...
    elif len(filtered_line_list) > 7 and filtered_line_list[5] == "MulticastClient:" and "REQUEST_LEDGER" in filtered_line_list[6]:
      #... we record the nonce of the request
      nonce_of_request_sent[index][current_round_number] = int(filtered_line_list[7])
    # If this is the line where a ledger is accepted, and the nonce used was the nonce of this
    # user's request...
    elif len(filtered_line_list) > 8 and accept_ledger_times[index][current_round_number] is None and filtered_line_list[5] == "RegistrationHandler:" and "ACCEPTED_LEDGER" in filtered_line_list[6] and int(filtered_line_list[7]) == nonce_of_request_sent[index][current_round_number]:
      start = start_app_timestamp[index][current_round_number]
      #... and the ledger has not already been recorded as accepted for this nonce...
      if current_round_number not in rounds_checked[index]:
        #... we record the accepted ledger
        total_ledgers[index] += 1
        #... record that we have recorded that a ledger was accepted for this nonce...
        rounds_checked[index].append(current_round_number)
        #... and if the ledger was correct...
        if int(filtered_line_list[8]) == index - 1:
          #... we record that we accepted the correct ledger
          correct_ledger[index] += 1
        else:
          incorrect_ledgers[index].append(current_round_number)
      # We get the timestamp from when the ledger was accepted...
      accept_time = datetime(
        2022,
        int(filtered_line_list[0][:2]),
        int(filtered_line_list[0][3:]),
        int(filtered_line_list[1][:2]),
        int(filtered_line_list[1][3:5]),
        int(filtered_line_list[1][6:8]),
        int(filtered_line_list[1][9:]) * 1000
      )
      #... computes the time between the application was started and when the ledger was accepted...
      time_diff = accept_time - start
      #... and record this time difference
      accept_ledger_times[index][current_round_number] = time_diff.total_seconds()


  filtered_accepted_ledger_times = list(filter(lambda el: el is not None, accept_ledger_times[index]))

  time_to_accept = mean(filtered_accepted_ledger_times)
  prob_accept_correct = correct_ledger[index]/(total_ledgers[index] + ledgers_missing[index])

  print("Average time to accept ledger of length {0} was {1:.3f} seconds".format(index - 1, time_to_accept))
  results_time_to_accept_ledger[folder].append(time_to_accept)
  print("The probability of accepting the correct ledger at length {0} is {1:.3f}".format(index - 1, prob_accept_correct))
  results_accepting_correct_ledger[folder].append(prob_accept_correct)
  print("The probability of not accepting a ledger: {:.3f}".format(ledgers_missing[index]/(total_ledgers[index] + ledgers_missing[index])))
  print("The rounds it did not accept a ledger (0-indexed rounds): {}".format(rounds_with_missing_ledger[index]))
  print("The probability of accepting a ledger without all the entries at length {0} is {1:.3f}".format(index - 1, (total_ledgers[index] - correct_ledger[index])/total_ledgers[index]))
  print("Rounds with incorrect ledger (0-indexed rounds): {}".format(incorrect_ledgers[index]))

for folder in ["CA_fors??k 1", "IKKECA_fors??k 1"]:
  results_time_to_accept_ledger[folder] = []
  results_accepting_correct_ledger[folder] = []
  for i in range(2, 7):
    search_file(i, folder)



def valuelabel(x,y, offset=0):
  for i in range(5):
    #value = round(round(y[i], 3)*100, 1)
    #value_string = str(value) + "%"
    value = round(y[i], 3)
    value_string = str(value) + " s"
    pylab.text(i+offset, y[i] + 0.0005, value_string, ha = 'center', fontsize=7)

x_coordinates = pylab.arange(5)

#valuelabel(x_coordinates, results_accepting_correct_ledger["IKKECA_fors??k 1"])
#valuelabel(x_coordinates, results_accepting_correct_ledger["CA_fors??k 1"])
valuelabel(x_coordinates, results_time_to_accept_ledger["IKKECA_fors??k 1"], -0.2)
valuelabel(x_coordinates, results_time_to_accept_ledger["CA_fors??k 1"], 0.2)


#pylab.bar(x_coordinates, results_accepting_correct_ledger["IKKECA_fors??k 1"])
#pylab.bar(x_coordinates, results_accepting_correct_ledger["CA_fors??k 1"])
pylab.bar(x_coordinates - 0.2, results_time_to_accept_ledger["IKKECA_fors??k 1"], 0.4, label="CA verified users")
pylab.bar(x_coordinates + 0.2, results_time_to_accept_ledger["CA_fors??k 1"], 0.4, label="No CA verified users")


pylab.xlabel("Number of users in the network")

#pylab.ylabel("Probability of accepting correct ledger %")
pylab.ylabel("Time to accept ledger (s)")

pylab.xticks(x_coordinates, [i + 1 for i in range(5)])

pylab.legend(loc=3)

pylab.show()
