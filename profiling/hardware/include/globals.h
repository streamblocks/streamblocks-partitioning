#ifndef __GLOBALS_BANDWIDTHTESTER__
#define __GLOBALS_BANDWIDTHTESTER__

#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include "actors-rts.h"
#include "natives.h"

#define BUFFER_SIZE_WORDS (BUFFER_SIZE / sizeof(uint32_t))

// -- Type declarations


// -- External Callables Declaration

 // -- Global Variables Declaration
void init_global_variables(void);
extern uint32_t g_profiling_hardware_loopback_$eval1;

#endif // __GLOBALS_BANDWIDTHTESTER__
