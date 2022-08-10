StreamBlocks Partitioning
=========================

Welcome to `streamblocks-partitioning`, yet another platform for StreamBlocks!
This platforms implements a profile-guided hardware-software partitioning flow
for StreamBlocks. The platforms takes various profiling information for a program
both on hardware and software and produces many partitions that are supposed to
perform better than trivial all-in-hardware or all-in-software partitions.


We assume you have a basic understanding of StreamBlocks' compilation flow,
therefore we jump right into running a simple example.


## Setup and dependencies

The setup is similar to `streamblocks-platform`, we use maven to build `jar`
files and use a shell script to run the partitioning tool (you need to have
`streamblocks-tycho` installed before).

```bash
> cd partitioning/platform-partition
> mvn install
```

We use a mixed-integer linear programming approach to partition the design
across hardware and software. To solve the formulas, we rely on Gurobi 8.0.1
which is a commercial optimizer with free academic licenses. Please head over to
[gurobi](https://www.gurobi.com/academia/academic-program-and-licenses/) to get
a license and then download version 8.0.1:

```
> wget https://packages.gurobi.com/8.0/gurobi8.0.1_linux64.tar.gz
> mkdir -p gurobi
> tar -xzf gurobi8.0.1_linux64.tar.gz -C gurobi
```

Activate your license:
```
./gurobi/gurobi801/linux64/bin/grbgetkey ${YOUR_LICENSE}
```

To use the Java bindings you need to include `gurobi/gurobi801/linux64/lib/`
in your `LD_LIBRARY_PATH`.


We rely on program profiles to perform partitioning. Although software profiles
can be easily obtained through real execution for hardware profiles we rely on
simulation. `streamblocks-platforms` can generate SystemC code for hardware-software
cosimulation to collect per actor hardware profile information. The simulation
relies on Verilator.

Install [Verilator](https://github.com/verilator/verilator) and and ensure
`VERILATOR_ROOT` is set properly.

## Usage
This repository works like the other StreamBlocks' platforms, i.e., you have
to pass the CAL source files and a target directory. In addition to that
you pass `--set config=profile_data.json` which a profiling config file in
`json`. It looks something like:

```json
{
  "name": "RVCDecoder",
  "cores": 8,
  "mode": "heterogeneous",
  "systemc": {
    "multiplier": 1,
    "freq": 210,
    "path": "PATH_TO_SYSTEMC_PROFILE_XML"
  },
  "opencl": {
    "multiplier": 1,
    "freq": 1000,
    "path": "PATH_TO_OPENCL_PROFILE_XML"
  },
  "bandwidth": {
    "multiplier": 1,
    "freq": 3400.0,
    "path": "PATH_TO_FIFO_BW_PROFILE_XML"
  },
  "software" : {
    "multiplier" : 1.0,
    "freq" : 3400.0,
    "path" : "PATH_TO_SOFTWARE_PROFILE_XML"
  }
}
```
This `json` file points to 4 other `xml` file that contain profiling
information. `opencl` and `bandwidth` are platform dependent but program
independent. They model the `opencl` read/write bandwidth over the CPU-FPGA
interconnect (e.g., PCIe) for various buffer sizes. `bandwidth` models the
software FIFO bandwidth within
and across threads. To obtain the platform profile data consult [this guide](profiling/readme.md).


Software profiles can obtained by compiling a project for software-only execution.
Suppose you have the followed the `PassThrough` example from the [streamblock-platform](https://github.com/streamblocks/streamblocks-platforms/readme.md) guide:
```bash
> ./PassThrough --with-bandwidth --with-complexity --generate=software_profile.xml
```
Will log the per-actor software profile in the `software_profile.xml`.


Hardware profiles requires a bit more work. When compiling CAL, pass `--set
enable-systemc=on` to `streamblocks` to generate code needed for
simulation-based profiling. The `Streamblocks.cmake` file in
[streamblocks-examples](https://github.com/streamblocks/streamblocks-examples/Streamblocks.cmake)
provides a handy function for generating simulation code which we recommend you
to use.

After building the binary, you can collect the hardware profile using:
```
> ./PassThrough --hardware-profile=systemc_profile.xml
```
