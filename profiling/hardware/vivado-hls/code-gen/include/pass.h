#ifndef __PASS__
#define __PASS__

#include <ap_int.h>
#include <hls_stream.h>
#include <stdint.h>
#include <stddef.h>
#include "globals.h"

// -- Struct IO
struct IO_pass {
	uint32_t In_peek;
	int In_count;
	int Out_size;
	int Out_count;
};

// -- Instance Class
class class_pass {
private:
	// -- Actor machine state
	int program_counter;
	// -- Actor return value
	unsigned int __ret;

	// -- PinConsume
	uint32_t __consume_In;
	// -- Scopes
	// -- Conditions
	bool condition_0(hls::stream< uint32_t > &In, IO_pass io);
	bool condition_1(hls::stream< uint32_t > &Out, IO_pass io);

	// -- Transitions
	void transition_0(hls::stream< uint32_t > &In, hls::stream< uint32_t > &Out);

public:
	class_pass(){
		// -- PinConsume
		__consume_In = 0;
	}

	int operator()(hls::stream< uint32_t > &In, hls::stream< uint32_t > &Out, IO_pass io);
};

#endif // __PASS__

