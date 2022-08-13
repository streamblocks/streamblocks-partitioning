################################################################################
# CMake utilities for streamblocks-platform
################################################################################

cmake_minimum_required(VERSION 3.10)


# Make sure that STREAMBLOCKS_HOME is defined
if (NOT DEFINED ENV{STREAMBLOCKS_HOME})
  message(FATAL_ERROR "STREAMBLOCKS_HOME environment variable is not set")
endif()

set(STREAMBLOCKS_HOME "$ENV{STREAMBLOCKS_HOME}" CACHE PATH "STREAMBLOCKS_HOME")

# -- streamblocks binary
set(STREAMBLOCKS_BIN "${STREAMBLOCKS_HOME}/streamblocks-platforms/streamblocks")

function (streamblocks TARGET)

  # Extra arguments and settings
  # -- on/off settings
  set(streamblocks_settings PARTITIONING SYSTEMC ACTION_PROFILE EXPERIMENTAL NO_PIPELINING)
  
  # -- value settings
  set(streamblocks_value_settings 
    PLATFORM
    QID 
    TARGET_PATH 
    MAX_BRAM
    QUEUE_DEPTH)

  #-- path settings
  set(streamblocks_path_settings 
    SOURCE_PATH 
    ORCC_SOURCE_PATH
    XDF_SOURCE_PATH
    XCF_SOURCE_PATH)
  
  
  cmake_parse_arguments(STREAMBLOCKS_ARGS 
    "${streamblocks_settings}"
    "${streamblocks_value_settings}"
    "${streamblocks_path_settings}"
    ${ARGN})

  
  

  if (NOT STREAMBLOCKS_ARGS_QID) 
    message(FATAL_ERROR "Entity QID not defined for ${TARGET}")
  endif()
  
  if (NOT STREAMBLOCKS_ARGS_PLATFORM) 
    message(FATAL_ERROR "streamblocks platform not defined for ${TARGET}")
  endif()
  if (NOT STREAMBLOCKS_ARGS_SOURCE_PATH)
    message(FATAL_ERROR "--source-path not defined for ${TARGET}")
  endif()

  if (NOT STREAMBLOCKS_ARGS_TARGET_PATH)
    message(FATAL_ERROR "--target-path not defined for ${TARGET}")
  endif()

  set(STREAMBLOCKS_SETTINGS_OPTIONS "")

  # --set enable-systemc
  if (STREAMBLOCKS_ARGS_SYSTEMC)
  list(APPEND STREAMBLOCKS_SETTINGS_OPTIONS --set enable-systemc=on)
  endif()
  
  # --set enable-action-profile
  if (STREAMBLOCKS_ARGS_ACTION_PROFILE)
    list(APPEND STREAMBLOCKS_SETTINGS_OPTIONS --set enable-action-profile=on)
  endif()
  
  # --set partitioning
  if (STREAMBLOCKS_ARGS_PARTITIONING)
    list(APPEND STREAMBLOCKS_SETTINGS_OPTIONS --set partitioning=on)
  endif()

   # --set partitioning
  if (STREAMBLOCKS_ARGS_NO_PIPELINING)
   list(APPEND STREAMBLOCKS_SETTINGS_OPTIONS --set disable-pipelining=on)
  endif()

  # --set experimental network elaboration
  if (STREAMBLOCKS_ARGS_EXPERIMENTAL)
    list(APPEND STREAMBLOCKS_SETTINGS_OPTIONS --set experimental-network-elaboration=on)
  endif()
  
  # --set max-bram
  if (STREAMBLOCKS_ARGS_MAX_BRAM)
    list(APPEND STREAMBLOCKS_SETTINGS_OPTIONS --set max-bram=${STREAMBLOCKS_ARGS_MAX_BRAM})
  endif()
  
    # --set queue-depth
  if (STREAMBLOCKS_ARGS_QUEUE_DEPTH)
    list(APPEND STREAMBLOCKS_SETTINGS_OPTIONS --set queue-depth=${STREAMBLOCKS_ARGS_QUEUE_DEPTH})
  endif()
  
  
  # -- xcf input
  if (STREAMBLOCKS_ARGS_XCF_SOURCE_PATH) 
    # message(STATUS "XCF PATH ${STREAMBLOCKS_ARGS_XCF_SOURCE_PATH}")
    string(REPLACE ";" ":" XCF_SOURCE_PATH_STRING "${STREAMBLOCKS_ARGS_XCF_SOURCE_PATH}")
    set(STREAMBLOCKS_XCF_SOURCE_PATH --xcf-path ${XCF_SOURCE_PATH_STRING})
  endif()
  
  # -- xdf source path
  if (STREAMBLOCKS_ARGS_XDF_SOURCE_PATH)
    string(REPLACE ";" ":" XDF_SOURCE_STRING "${STREAMBLOCKS_ARGS_XDF_SOURCE_PATH}")
    # message(STATUS "XDF: ${XDF_SOURCE_STRING}")
    set(STREAMBLOCKS_XDF_SOURCE_PATH --xdf-source-path ${XDF_SOURCE_STRING})
  endif()
  
  # -- orcc source path 
  if (STREAMBLOCKS_ARGS_ORCC_SOURCE_PATH)
    string(REPLACE ";" ":" ORCC_SOURCE_PATH_STRING "${STREAMBLOCKS_ARGS_ORCC_SOURCE_PATH}")
    set(STREAMBLOCKS_ORCC_SOURCE_PATH --orcc-source-path ${ORCC_SOURCE_PATH_STRING})
  endif()
  

  # -- Glob all the .cal files in the source path directories
  
  # -- cal source files
  
  string(REPLACE ";" ":" SOURCE_PATH_STRING "${STREAMBLOCKS_ARGS_SOURCE_PATH}")

  # message(STATUS "SOURCE_PATH_STRING: ${SOURCE_PATH_STRING}")

  
  set(TARGET_PATH_STRING ${STREAMBLOCKS_ARGS_TARGET_PATH})
  message(STATUS "Adding target ${TARGET} for entity ${STREAMBLOCKS_ARGS_QID}")

  
  
  # append the platform to the output directory
  if (STREAMBLOCKS_ARGS_PARTITIONING)
    set(TARGET_PATH_OUTPUT_STRING "${TARGET_PATH_STRING}/${STREAMBLOCKS_ARGS_PLATFORM}")
  else()
    set(TARGET_PATH_OUTPUT_STRING "${TARGET_PATH_STRING}")
  endif()
  
  message(STATUS "Target path: ${TARGET_PATH_STRING}")
  message(STATUS "Generated path: ${TARGET_PATH_OUTPUT_STRING}")  
  add_custom_command(
    OUTPUT ${TARGET_PATH_OUTPUT_STRING}
    
    COMMAND ${STREAMBLOCKS_BIN}
    ${STREAMBLOCKS_ARGS_PLATFORM}
    ${STREAMBLOCKS_SETTINGS_OPTIONS}
    "--source-path" ${SOURCE_PATH_STRING}
    ${STREAMBLOCKS_ORCC_SOURCE_PATH}
    ${STREAMBLOCKS_XDF_SOURCE_PATH}
    ${STREAMBLOCKS_XCF_SOURCE_PATH}
    "--target-path" ${TARGET_PATH_STRING}
    ${STREAMBLOCKS_ARGS_QID} 
    COMMENT "Generating code for ${STREAMBLOCKS_ARGS_QID} on platform ${STREAMBLOCKS_ARGS_PLATFORM}"
    DEPENDS ${SOURCE_CAL_DEPS} ${SOURCE_ORCC_DEPS} ${SOURCE_XDF_DEPS} ${SOURCE_XCF_DEPS}
    VERBATIM
    ) 
    
    add_custom_target(
      ${TARGET}
      DEPENDS ${TARGET_PATH_OUTPUT_STRING} 
    )
      
endfunction()


function (streamblocks_systemc TARGET)

  cmake_parse_arguments(STREAMBLOCKS_ARGS
    ""
    ""
    ""
    ${ARGN}
  )
  set(HLS_TARGET ${TARGET}_HLS)
  set(MULTICORE_TARGET ${TARGET}_MULTICORE)
  message(STATUS "Creating systemc simulation targets ${HLS_TARGET} and ${MULTICORE_TARGET}")
  streamblocks(
    ${HLS_TARGET}
    PLATFORM vivado-hls
    SYSTEMC
    PARTITIONING
    ACTION_PROFILE
    MAX_BRAM 128MiB
    ${ARGN}
  )

  streamblocks(
    ${MULTICORE_TARGET}
    PLATFORM multicore
    SYSTEMC
    PARTITIONING
    ${ARGN}
  )

  add_custom_target(
    ${TARGET}
    DEPENDS ${MULTICORE_TARGET} ${HLS_TARGET}
  )

endfunction()

# -- make the paths absolute
macro (make_absolute_path_list ABSOLUTE_PATH_LIST PATH_LIST) 

  
  foreach(__PATH__ ${PATH_LIST})
    if (NOT IS_ABSOLUTE ${__PATH__})
      
      list(APPEND ${ABSOLUTE_PATH_LIST} "${CMAKE_CURRENT_SOURCE_DIR}/${__PATH__}")
    else()
      list(APPEND ${ABSOLUTE_PATH_LIST} "${ABSOLUTE_PATH_LIST} ${__PATH__}")
    endif()
  endforeach()
  
  
endmacro()



# -- recursively finds the files with FILE_EXTENSTION in PATH_LIST
macro (find_source_dependencies CAL_DEPS PATH_LIST FILE_EXTENTION)
  
  foreach (__PATH__ ${PATH_LIST})
    file(GLOB_RECURSE cal_source "${__PATH__}/*.${FILE_EXTENTION}")
 
    list(APPEND ${CAL_DEPS} ${cal_source})
   
  endforeach()
  
endmacro()

macro (analyze_path PATH_STRING SOURCE_DEPS PATH_LIST FILE_EXTENSION )


  # make_absolute_path_list(ABSOLUTE_PATH_LIST "${PATH_LIST}")


  # find_source_dependencies(__SOURCE_DEPS__ "${ABSOLUTE_PATH_LIST}" "cal")

 
  string(REPLACE ";" ":" __PATH_STRING__ "${ABSOLUTE_PATH_LIST}")

  set(${PATH_STRING} "${__PATH_STRING__}")
  # set(${SOURCE_DEPS} "${__SOURCE_DEPS__}")

endmacro()