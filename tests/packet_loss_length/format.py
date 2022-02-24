f = open("./ledger.txt", "r") 

entries = []

for line in f:
  a = line.split(" ")[1].replace("\"", "\\" + "\"")
  entries.append(a)

print("\"" + "\", \"".join(entries[250:]).replace("\n", "") + "\"")
