# -- Generated from profiling.hardware.loopback.BandwidthTester
project(BandwidthTester)

cmake_minimum_required(VERSION 3.10)

project (bandwidth)

set(CMAKE_CXX_STANDARD 14)


# -- test parameters
set(NUM_LOOPS_VALUE "100000" CACHE STRING "Number repeated experiments")
set(BUFFER_SIZE_MIN "8" CACHE STRING "Minimum size of the FIFOs")
set(BUFFER_SIZE_MAX "4194304" CACHE STRING "Maximum size of the FIFOs")


# -- Configure output folder for generated binary
set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/bin)


# -- art-runtime and art-plink libray
add_subdirectory(lib) 
# -- the vivado-hls files to generate the xclbin
add_subdirectory(vivado-hls)
# -- Generated code source files
set(multicore_sources
	src/system_plink_0.cc
	src/source.cc
	src/sink.cc
	src/globals.cc
	src/main.cc
)

# -- Generated code headers
set(multicore_headers
	include/__arrayCopy.h
	include/globals.h
)


# -- configure the execution script
configure_file(${PROJECT_SOURCE_DIR}/scripts/bandwidth-test.sh.in ${EXECUTABLE_OUTPUT_PATH}/bandwidth-test @ONLY)


set(buff_sz ${BUFFER_SIZE_MIN})
set(AL_TARGETS "")
while(${buff_sz} LESS_EQUAL ${BUFFER_SIZE_MAX})

	# message(STATUS "buff_sz=${buff_sz}")	
	set(TARGET_NAME ${PROJECT_NAME}_${buff_sz}_${NUM_LOOPS_VALUE})
	add_executable(${TARGET_NAME} ${multicore_sources})
	target_include_directories(${TARGET_NAME} PRIVATE lib/art-runtime/include lib/art-plink ./include ${extra_includes})
	target_link_libraries(${TARGET_NAME} art-runtime art-plink ${extra_libraries})
	target_compile_definitions(${TARGET_NAME} PRIVATE -DBUFFER_SIZE=${buff_sz} -DNUM_LOOPS=${NUM_LOOPS_VALUE})
	set_target_properties(
		${TARGET_NAME}
		PROPERTIES 
		CXX_STANDARD 14
		CXX_STANDARD_REQUIRED YES
	)
	math(EXPR buff_sz "${buff_sz}*2")
	set(AL_TARGETS ${AL_TARGETS} ${TARGET_NAME})
endwhile()

add_custom_target(bandwidth-test DEPENDS ${AL_TARGETS})

