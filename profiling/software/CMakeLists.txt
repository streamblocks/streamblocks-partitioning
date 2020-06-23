# -- Generated from ch.epfl.mpeg4.part2.SB_Top_Parallel
cmake_minimum_required(VERSION 3.10)

project (bandwidth)

set(CMAKE_C_STANDARD 11)


set(NUM_LOOPS_VALUE "100000000" CACHE STRING "Number repeated experiments")
set(BUFFER_SIZE_MIN "32" CACHE STRING "Maximum size of the FIFOs")
set(BUFFER_SIZE_MAX "32556" CACHE STRING "Minimum size of the FIFOs")


# -- Default C Flags
set(CMAKE_C_FLAGS "-Wall -Wno-unused-variable -Wno-missing-braces")

# -- Configure output Folder for generated binary
set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/bin)

# -- Definitions used in sub directories
set(extra_definitions)
set(extra_includes)

# -- Include directories
# include_directories(lib/art-runtime/include)

# -- Add sub directories 
add_subdirectory(lib)

# -- configure the execution script
configure_file(${PROJECT_SOURCE_DIR}/scripts/bandwidth-test.sh.in ${EXECUTABLE_OUTPUT_PATH}/bandwidth-test @ONLY)
configure_file(${PROJECT_SOURCE_DIR}/scripts/config.xml.in ${EXECUTABLE_OUTPUT_PATH}/config.xml)

set(buff_sz ${BUFFER_SIZE_MIN})
while(${buff_sz} LESS_EQUAL ${BUFFER_SIZE_MAX})

	# message(STATUS "buff_sz=${buff_sz}")	
	set(TARGET_NAME ${PROJECT_NAME}_${buff_sz}_${NUM_LOOPS_VALUE})
	add_executable(${TARGET_NAME} src/main.cc src/timed_source.cc src/timed_sink.cc)
	target_include_directories(${TARGET_NAME} PRIVATE lib/art-runtime/include)
	target_link_libraries(${TARGET_NAME} art-runtime )
	target_compile_definitions(${TARGET_NAME} PRIVATE -DBUFFER_SIZE=${buff_sz} -DNUM_LOOPS=${NUM_LOOPS_VALUE})
	math(EXPR buff_sz "${buff_sz}*2")

endwhile()

