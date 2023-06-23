import requests

for i in range (1,93,1):
    for j in range(1,12):
        url='http://ita.ee.lbl.gov/traces/WorldCup/wc_day{0}_{1}.gz'.format(i,j)
        response = requests.get(url)
        if response.status_code == 200:
            open("data/wc_day{0}_{1}.gz".format(i,j), "wb").write(response.content)
