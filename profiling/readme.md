StreamBlocks Platform Profilers
================================


In this directory you can find two basic profilers for software (FIFO bandwidth)
and hardware (OpenCL r/w over PCIe).


## Software bandwidth
Go to `software` directory and run the following commands:
```
> cd software
> mkdir -p build
> cd build
> cmake .. -DCMAKE_BUILD_TYPE=Release
> cmake --build .
> cd ../bin
> chmod +x bandwidth-test
> ./bandwidth-test
```

You can configure the profiler in terms of number of repetitions, minimum and
maximum buffer size in bytes (should be power of 2). By either modifying the
`CMakeLists.txt` file in `software` or passing extra flags to `cmake`, e.g.,:

```
> cmake .. -DCMAKE_BUILD_TYPE=Release -DNUM_LOOPS_VALUE=100 -DBUFFER_SIZE_MIN=8 -DBUFFER_SIZE_MAX=1024
```

Once the test is done, a `bandwidth.xml` file is generated. You can use that
in the partitioning tool (i.e., provide its path in the json file).


## Hardware-software bandwidth

Performing hardware-software profile is slightly more complicated. You need to
first implement a loopback example in CAL for your platform of choice (you need
the usual environment variables for Vivado and XRT):
```
> cd hardware
> mkdir -p build
> cd build
> cmake .. -DFPGA_NAME=... -DPLATFORM_NAME=... -DUSE_VITIS=on
> cmake --build . -t Loopback_1_10_xclbin
```
This will generate an FPGA bitstream using 10ns HSL clock target (though the
actual kernel may run at a different clock). This kernel has a single input and
a single output, you can change the number of inputs and outputs up to to what
is defined in `Loopback.cal` (e.g, `-t Loopback_4_10_xclbin` would have 4
connections).

After you have the FPGA bitstream (environment variable `XILINX_XRT` should be defined):

```
cd ../bandwidth
mkdir -p build
cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build .
cd ../
./bandwidth --repeats 1000 --min-buffer 8 --max-buffer 1048576  ---randomize 0 --width 1
```
You can change the argument values if you are profiling for different
configurations. The result of this run would be dumped in the run directory
prefixed with `stat_`. The profiling information is originally in a detailed `json`
format per buffer size configuration. You can use `bandwidth.py` to obtain an
`xml` summary that is used by the partitioning tool.

```
python3 bandwidth.py stats_ --use-mean --output opencl.xml
```


