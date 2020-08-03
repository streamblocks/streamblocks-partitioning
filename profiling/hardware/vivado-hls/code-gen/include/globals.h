#ifndef __GLOBALS_BANDWIDTHTESTER__
#define __GLOBALS_BANDWIDTHTESTER__

#include <stdint.h>
#include "ap_int.h"


// -- Actor Return values
#define RETURN_IDLE 0
#define RETURN_WAIT 1
#define RETURN_TEST 2
#define RETURN_EXECUTED 3


// -- Pins
#define pinPeekFront(NAME, value) value = io.NAME ## _peek

#define pinPeekRepeat(NAME, value, d) \
 {\
    int tmp = __consume_ ## NAME;\
    for(int i = tmp; i < d; i++){\
        value[i] = NAME.read();\
        __consume_ ## NAME++; \
    }\
}

#define pinRead(NAME, value) \
	NAME.read_nb(value);\
	__consume_ ## NAME++;\

#define pinReadBlocking(NAME, value) value = NAME.read()

#define pinReadRepeat(NAME, value, d) \
{\
    for(int i = 0; i < d; i++){\
        NAME.read_nb(value[i]);\
        __consume_ ## NAME++; \
    }\
}

#define pinReadRepeatBlocking(NAME, value, d) \
{\
	for(int i = 0; i < d; i++){\
		value[i] = NAME.read();\
	}\
}

#define pinWrite(NAME, value) NAME.write_nb(value)

#define pinWriteBlocking(NAME, value) NAME.write(value)

#define pinWriteRepeat(NAME, value, d) \
{\
	for(int i = 0; i < d; i++){\
		NAME.write_nb(value[i]);\
	}\
}

#define pinWriteRepeatBlocking(NAME, value, d) \
{\
	for(int i = 0; i < d; i++){\
		NAME.write(value[i]);\
	}\
}

#define pinAvailIn(NAME, IO) IO.NAME ## _count

#define pinAvailOut(NAME, IO) IO.NAME ## _size - IO.NAME ## _count

#define pinConsume(NAME) \
{\
    if(__consume_ ## NAME == 0) {\
        NAME.read(); \
    }\
    __consume_ ## NAME = 0; \
}

#define pinConsumeRepeat(NAME, d) \
{\
    int tmp = __consume_ ## NAME;\
    if(__consume_ ## NAME == 0 ){ \
         for(int i = tmp; i < d; i++){\
            NAME.read(); \
        }\
    } else {\
        __consume_ ## NAME-=d; \
    }\
}


#include <ap_int.h>
#include <stdint.h>

// -- Global variable declaration
static uint32_t g_profiling_hardware_loopback_payload = (1 << 16);

// -- External Callables Declaration

#endif // __GLOBALS_BANDWIDTHTESTER__

