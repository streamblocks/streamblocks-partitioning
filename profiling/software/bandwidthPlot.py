from xml.dom import minidom
import argparse
import matplotlib.pyplot as plt
import numpy as np

class BandWidthStat():

  def __init__(self, element):
    
    
    self.tick = float(element.attributes["ticks"].value)
    
    self.buffer_size = float(element.attributes["buffer-size"].value)
    
  
def parseProfileXml(file_name):

  profile = minidom.parse(file_name)

  single_core = profile.getElementsByTagName("bandwidth-test")[0]
  dual_core = profile.getElementsByTagName("bandwidth-test")[1]

  print(single_core.attributes['type'].value)
  print(dual_core.attributes['type'].value)
  
  single_stats = [BandWidthStat(elem) 
    for elem in single_core.getElementsByTagName("Connection")]
  dual_stats = [BandWidthStat(elem) 
    for elem in dual_core.getElementsByTagName("Connection")]
  for stat in dual_stats:
    print(stat.tick)
  for stat in single_stats:
    print(stat.tick)
  x = [stat.buffer_size for stat in dual_stats]
  
  yd = [stat.tick for stat in dual_stats]
  ys = [stat.tick for stat in single_stats]

  fig, ax = plt.subplots()

  ax.plot(x, yd, marker='x', label='dual core ticks ')
  ax.plot(x, ys, marker='o', label='single core  ticks ')
  ax.legend()
  ax.set_xscale('log', basex=2)
  ax.set_yscale('log', basey=10)
  ax.set_title('title')
  plt.show()

  

if __name__ == "__main__":

  argsParser = argparse.ArgumentParser(description="Create a plot of bandwidth test")
  argsParser.add_argument("profile_xml", metavar="FILE", type=str, help="profile xml file")
  args = argsParser.parse_args()
  parseProfileXml(args.profile_xml)


