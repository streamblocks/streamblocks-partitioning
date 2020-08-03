# -- --------------------------------------------------------------------------
# -- StremBlocks Vivado HLS Code Generation
# -- Generated from: profiling.hardware.loopback.BandwidthTester
# --
# -- --------------------------------------------------------------------------
cmake_minimum_required(VERSION 3.3)

# -- --------------------------------------------------------------------------
# -- CMake Project
# -- --------------------------------------------------------------------------
project (BandwidthTester-vivadohls)

set(__NETWORK_TOP_NAME__ BandwidthTester)
# -- set the instance actors
set(__ACTORS_IN_NETWORK__
	pass
)

# -- set the input/output stage actors
set(__INPUT_STAGE_ACTORS__
	source_Out_pass_In_input_stage_mem
)

set(__OUTPUT_STAGE_ACTORS__
	pass_Out_sink_In_output_stage_mem
)

# -- all the actors in the kernel
set(__ACTORS_IN_KERNEL__
	${__ACTORS_IN_NETWORK__}
	${__INPUT_STAGE_ACTORS__}
	${__OUTPUT_STAGE_ACTORS__}
)

# -- Configure the cmake script
configure_file(${PROJECT_SOURCE_DIR}/scripts/Helper.cmake.in ${PROJECT_SOURCE_DIR}/cmake/Helper.cmake @ONLY)
# -- Include  the cmake scripts for generating verilog and xclbin
include(${PROJECT_SOURCE_DIR}/cmake/Helper.cmake)
