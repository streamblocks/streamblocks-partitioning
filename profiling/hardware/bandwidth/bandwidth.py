import json
import argparse

import lxml.etree as ET
import numpy as np
import matplotlib.pyplot as plt
import matplotlib

if __name__ == "__main__":

    argsParser = argparse.ArgumentParser(
        description="Aggregate the results of a bandwidth test into an xml summary, use a non-randomized test.")
    argsParser.add_argument("prefix", metavar="PREFIX", type=str,
                            default="", help="Path prefix for test json files, e.g., data/stat_")
    argsParser.add_argument(
        "--use-mean", "-m", action='store_true', help='use the mean value for the summary')
    argsParser.add_argument('--output', '-o', type=str,
                            required=True, help='output file', metavar='FILE')
    argsParser.add_argument(
        '--plot', '-P', action='store_true', help="plot the bandwidth curve")
    args = argsParser.parse_args()

    print(args)
    tested_buffers = [1 << i for i in range(2, 30)]

    def getDuration(entry):
        queued = entry['queued']
        end = entry['end']
        return end - queued

    profile_summary = ET.Element('profile-data')
    bw_test = ET.SubElement(profile_summary, 'bandwidth-test')
    bw_test.set('type', 'FPGA')
    write_data = {'lower_q': [], 'median': [], 'upper_q': []}
    read_data = {'lower_q': [], 'median': [], 'upper_q': []}
    buffers = []
    for buffer_size in tested_buffers:
        file_path = args.prefix + str(buffer_size) + '.json'
        try:
            with open(file_path, 'r') as test_json:
                data = json.load(test_json)
                inputs_port = data['input_ports'][0]
                output_port = data['output_ports'][0]
                write_times = [getDuration(e) for e in inputs_port['stats']]
                read_times = [getDuration(e) for e in output_port['stats']]
                kernel_times = [getDuration(e) for e in data['kernel']]
                num_bytes = inputs_port['alloc_size'] * \
                    inputs_port['token_size']
                num_MiB = num_bytes / 1024. / 1024.

                def summary_op(x): return int(np.mean(
                    x) if args.use_mean else np.median(x))
                def get_bw(x): return num_MiB * 1. / (x * 1e-9)

                con = ET.SubElement(bw_test, 'Connection')

                summray_write = summary_op(write_times)

                write_data['lower_q'].append(get_bw(np.quantile(write_times, 0.1)))
                write_data['median'].append(get_bw(np.mean(write_times))
                                            if args.use_mean else get_bw(np.median(write_times)))
                write_data['upper_q'].append(get_bw(np.quantile(write_times, 0.9)))

                read_data['lower_q'].append(get_bw(np.quantile(read_times, 0.1)))
                read_data['median'].append(get_bw(np.mean(read_times))
                                           if args.use_mean else get_bw(np.median(read_times)))
                read_data['upper_q'].append(get_bw(np.quantile(read_times, 0.9)))

                buffers.append(buffer_size)

                summary_kernel = summary_op(kernel_times)

                summary_read = summary_op(read_times)
                r_lq = np.quantile(read_times, 0.1)
                r_hq = np.quantile(read_times, 0.9)

                con.set('kernel-total', str(summary_kernel))
                con.set('write-total', str(summray_write))
                con.set('read-total', str(summary_read))
                print("%d B(%3.3f MiB) :\nwrite %3.3f\tkernel %3.3f\tread %3.3f"
                      % (num_bytes, num_MiB, get_bw(summray_write), get_bw(summary_kernel), get_bw(summary_read)))

                con.set('buffer-size', str(num_bytes))

                # if args.use_mean:

        except FileNotFoundError as e:
            print("Could not open " + file_path)
            pass
        except KeyError as e:
            print("Error reading test json %s:\n%s" % (file_path, str(e)))

    profile_summary_raw = ET.tostring(profile_summary, pretty_print=True)
    try:
        with open(args.output, 'wb') as summary_file:
            summary_file.write(profile_summary_raw)
    except Exception as e:
        print("Error saving summary to %s:\n%s" % (args.output, str(e)))

    if args.plot:
        font = {'family': 'sans-serif',
                'sans-serif': ['Helvetica'],
                'size': 18}
        matplotlib.rc('font', **font)
        fig, ax = plt.subplots()

        def makeLabel(x):
            if x >= 2 ** 30:
                return str(int(x / 2 ** 30)) + "GiB"
            elif x >= 2 ** 20:
                return str(int(x / 2 ** 20)) + "MiB"
            elif x >= 2 ** 10:
                return str(int(x / 2 ** 10)) + "KiB"
            else:
                return str(x) + "B"

        def plotBw(ax, data, label):
          err_u = np.array(data['upper_q']) - np.array(data['median'])
          err_l = np.array(data['median']) - np.array(data['lower_q'])
          ax.errorbar(buffers, data['median'], marker='o', linestyle='--', label=label, yerr=(err_l, err_u), capsize=5.)
          ax.legend()
          ax.set_xscale('log', basex=2)
          xtks = [2 ** x for x in range(0, 27, 1)]
          ax.set_xticks(xtks)
          xlabels = [makeLabel(x) for x in xtks]
          ax.set_xticklabels(xlabels, rotation=-90)
          # ax.set_title("Bandwidth measuresments")
          ax.set_xlim(2**2, 2**26)
          ax.grid(True)

        plotBw(ax, write_data, 'write')
        plotBw(ax, read_data, 'read')
        ax.set_xlabel('PCIe payload size')
        ax.set_ylabel('PCIe read/write bandwidth')
        plt.show()
