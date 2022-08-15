cmake_minimum_required(VERSION 3.3)

project(bandwidth_test)

add_subdirectory(art-plink)
set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR})

find_package(Boost COMPONENTS program_options REQUIRED)
message(STATUS "Boost include ${Boost_INCLUDE_DIRS} ")
message(STATUS "Boost lib ${Boost_LIBRARIES}")

add_executable(bandwidth bandwidth.cpp)
target_include_directories(bandwidth PUBLIC art-plink/include ${Boost_INCLUDE_DIRS})
target_link_libraries(bandwidth art-plink ${Boost_LIBRARIES})
target_compile_definitions(bandwidth PUBLIC)

set_target_properties(bandwidth PROPERTIES CXX_STANDARD 14)

