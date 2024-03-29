# ART Runtime sources
set(art_runtime_sources
        src/actors-rts
        src/termination-report.c
        src/xmlParser.c
        src/xmlTrace.c
        src/jsonTrace.c
        )

# ART Runtime includes
set(art_runtime_header
        include/actors-config.h
        include/actors-fifo.h
        include/actors-rts.h
        include/actors_execute_network.h
        include/dll.h
        include/internal.h
        include/natives.h
        include/xmlParser.h
        include/xmlTrace.h
        include/jsonTrace.h
        )


find_package(ZLIB REQUIRED)
if(ZLIB_FOUND)
        list(APPEND extra_includes ${ZLIB_INCLUDE_DIRS})
        list(APPEND extra_libraries ${ZLIB_LIBRARIES})
endif()

include_directories(${extra_includes})
add_definitions(${extra_definitions})

add_library(art-runtime STATIC ${art_runtime_sources} ${art_runtime_header})

target_link_libraries(art-runtime ${extra_libraries})

install(TARGETS art-runtime
        ARCHIVE DESTINATION lib)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)