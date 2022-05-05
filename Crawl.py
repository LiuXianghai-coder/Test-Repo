import requests
from bs4 import BeautifulSoup
import time

mainHref = "https://mangadna.com/manga/secret-class"
proxy = "http://127.0.0.1:10809"
proxyDict = {
    "http": "http://127.0.0.1:10809"
}
headers = {"User-Agent":"Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1; AcooBrowser; .NET CLR 1.1.4322; .NET CLR 2.0.50727)"}


def getMainPage():
    mainResponse = requests.get(mainHref, headers=headers, proxies=proxyDict)
    hostUrl = "https://mangadna.com"
    hrefList = []
    # print(mainResponse.content.decode("utf8"))
    soup = BeautifulSoup(mainResponse.content.decode("utf8"), 'html.parser')
    priId = 1
    for ul in soup.findAll("ul", {"class", "row-content-chapter"}):
        soupHref = BeautifulSoup(str(ul), 'html.parser')
        for hrefTag in soupHref.findAll("a"):
            chapterHref = hostUrl + hrefTag["href"]
            chapterId = str(hrefTag["href"])[-2:]
            chapterResponse = requests.get(chapterHref, headers=headers, proxies=proxyDict)
            chapterSoup = BeautifulSoup(chapterResponse.content.decode("utf8"), "html.parser")
            imgId = 1
            with open("imgData_1.txt", "a", encoding="utf8") as dataFile:
                for img in chapterSoup.find("div", {"class", "read-content"}).findAll("img"):
                    print(str(priId) + "\t" + chapterId + "\t" + str(imgId) + "\t" + img["src"])
                    dataFile.write(str(priId) + "\t" + chapterId + "\t" + str(imgId) + "\t" + img["src"] + "\n")
                    priId += 1
                    imgId += 1
            time.sleep(1.5)


    # href = hrefList[0]
    # detailResponse = requests.get(mainHref + href, headers=headers, proxies=proxyDict)
    # print(detailResponse.content.decode("utf-8"))


if __name__ == '__main__':
    # getMainPage()
    # response = requests.get("https://mangadna.com/manga/secret-class/chapter-94", headers=headers, proxies=proxyDict)
    # string = "https://mangadna.com/manga/secret-class/chapter-94"
    # print(string[-2:])

    priId = 1445
    with open("chapter_1.txt", "r", encoding="utf8") as file:
        for url in file.readlines():
            url = url.rstrip("\n")
            chapterId = url[-1:]
            chapterResponse = requests.get(url, headers=headers, proxies=proxyDict)
            chapterSoup = BeautifulSoup(chapterResponse.content.decode("utf8"), "html.parser")
            imgId = 1
            with open("imgData_1.txt", "a", encoding="utf8") as dataFile:
                for img in chapterSoup.find("div", {"class", "read-content"}).findAll("img"):
                    print(str(priId) + "\t" + chapterId + "\t" + str(imgId) + "\t" + img["src"])
                    dataFile.write(str(priId) + "\t" + chapterId + "\t" + str(imgId) + "\t" + img["src"] + "\n")
                    priId += 1
                    imgId += 1
            time.sleep(1.5)