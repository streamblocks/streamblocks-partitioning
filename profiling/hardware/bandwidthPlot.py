from xml.dom import minidom
import argparse
import matplotlib.pyplot as plt
import numpy as np

class BandWidthStat():

  def __init__(self, element):
    
    self.repeats = int(element.attributes["repeats"].value)
    self.write_time = float(element.attributes["write-total"].value) / self.repeats * 1e-9
    self.read_time = float(element.attributes["read-total"].value) / self.repeats * 1e-9
    self.buffer_size = float(element.attributes["buffer-size"].value)
    
  def getReadBanwidth(self):

    return self.buffer_size / self.read_time / 1024. / 1024.
  def getWriteBandwidth(self):
    return self.buffer_size / self.write_time / 1024. / 1024.

def parseProfileXml(file_name):

  profile = minidom.parse(file_name)

  tests = profile.getElementsByTagName("bandwidth-test")
  
  def plotBandwidth(title, elements):

    bandwidth_stats = [BandWidthStat(elem) for elem in elements]
    
    
    x = [stat.buffer_size for stat in bandwidth_stats]
    yw = [stat.getWriteBandwidth() for stat in bandwidth_stats]
    yr = [stat.getReadBanwidth() for stat in bandwidth_stats]

    fig, ax = plt.subplots()

    ax.plot(x, yw, marker='x', label='write bandwidth (MiB/s)')
    ax.plot(x, yr, marker='o', label='read  bandwidth (MiB/s)')
    ax.legend()
    ax.set_xscale('log', basex=2)
    ax.set_title(title)
    plt.show()
  
  for test in tests:

    plotBandwidth(test.attributes['type'].value, test.getElementsByTagName("Connection"))

if __name__ == "__main__":

  argsParser = argparse.ArgumentParser(description="Create a plot of bandwidth test")
  argsParser.add_argument("profile_xml", metavar="FILE", type=str, help="profile xml file")
  args = argsParser.parse_args()
  parseProfileXml(args.profile_xml)


