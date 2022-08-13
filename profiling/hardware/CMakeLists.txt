cmake_minimum_required(VERSION 3.3)

project(hetero.simple)

include(StreamBlocks.cmake)



set(CLOCK_SETTINGS "3.3;10" CACHE STRING "List of ; seperated HLS clock settings")

set(FPGA_NAME "xczu7ev-ffvc1156-2-e"
  CACHE STRING
  "Name of Xilinx FPGA, e.g \"xcku115-flvb2104-2-e\", \"xcu250-figd2104-2L-e\", \"xczu7ev-ffvc1156-2-e\", \"xczu3eg-sbva484-1-e\", ...")

set(PLATFORM "xilinx_zcu106_base_202010_1"
  CACHE STRING "Supported platform name, e.g \"xilinx_kcu1500_dynamic_5_0\", \"xilinx_u250_xdma_201830_2\", \"zcu102_base\", \"xilinx_zcu106_base_202010_1\", \"ultra96_base\",... ")

# set(KERNEL_FREQ "150" CACHE STRING "Clock frequency in MHz.")

option(USE_VITIS "Build an RTL OpenCL Kernel for Vitis" OFF)
option(USE_SDACCEL "Build an RTL OpenCL Kernel for SDAccel" OFF)
option(IS_MPSOC "Vitis Embedded Platform" OFF)

add_custom_target(all_xclbin)
add_custom_target(all_hls)
add_custom_target(all_multicore)


macro(generate_loopback NUM CLOCK)

  set(TARGET_NAME Loopback_${NUM}_${CLOCK})
  set(TARGET_PATH generated/Loopback_${NUM}_${CLOCK}
  )
  streamblocks(
    ${TARGET_NAME}_HLS
    QID hetero.simple.loopback.Loopback${NUM}
    PLATFORM vivado-hls
    SOURCE_PATH ${CMAKE_SOURCE_DIR}
    TARGET_PATH ${TARGET_PATH}
    PARTITIONING
  )

  streamblocks(
    ${TARGET_NAME}_MULTICORE
    QID hetero.simple.loopback.Loopback${NUM}
    PLATFORM multicore
    SOURCE_PATH ${CMAKE_SOURCE_DIR}
    TARGET_PATH ${TARGET_PATH}
    PARTITIONING
  )


  add_custom_target(
    ${TARGET_NAME} ALL
    DEPENDS ${TARGET_NAME}_MULTICORE ${TARGET_NAME}_HLS
  )

  file(MAKE_DIRECTORY ${CMAKE_BINARY_DIR}/${TARGET_PATH}/build)

  add_custom_command(
    OUTPUT  ${TARGET_PATH}/bin/xclbin/Loopback.hw._kernel_xclbin
    COMMAND ${CMAKE_COMMAND} -DHLS_CLOCK_PERIOD=${CLOCK} -DUSE_SDACCEL=${USE_SDACCEL} -DUSE_VITIS=${USE_VITIS} -DFPGA_NAME=${FPGA_NAME} -DPLATFORM=${PLATFORM} -DIS_MPSOC=${IS_MPSOC} ..
    COMMAND ${CMAKE_COMMAND} --build . -t Loopback${NUM}_kernel_xclbin
    WORKING_DIRECTORY  ${TARGET_PATH}/build
    COMMENT "Generating xclbin ${TARGET_NAME}"
    DEPENDS ${TARGET_NAME}
  )

  add_custom_target(
    ${TARGET_NAME}_xclbin
    DEPENDS ${TARGET_PATH}/bin/xclbin/Loopback.hw._kernel_xclbin
  )

  add_dependencies(all_xclbin ${TARGET_NAME}_xclbin)
  add_dependencies(all_hls ${TARGET_NAME}_HLS)
  add_dependencies(all_multicore ${TARGET_NAME}_MULTICORE)

endmacro()


foreach (FANOUT RANGE 1 7)
  foreach (CLK ${CLOCK_SETTINGS})
    generate_loopback(${FANOUT} ${CLK})
  endforeach()
endforeach()

