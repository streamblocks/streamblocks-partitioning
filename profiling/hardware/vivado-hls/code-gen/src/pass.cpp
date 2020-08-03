#include "pass.h"

// -- --------------------------------------------------------------------------
// -- HLS Top Function
// -- --------------------------------------------------------------------------
int pass(hls::stream< uint32_t > &In, hls::stream< uint32_t > &Out, IO_pass io) {
	static class_pass i_pass;

	return i_pass(In, Out, io);
}

// -- --------------------------------------------------------------------------
// -- pass : Members declaration
// -- --------------------------------------------------------------------------

int class_pass::operator()(hls::stream< uint32_t > &In, hls::stream< uint32_t > &Out, IO_pass io) {
#pragma HLS INLINE
	switch (this->program_counter) {
		case 0: goto S0;
	}

	S0:
	if (condition_0(In, io)) {
		goto S1;
	} else {
		goto S2;
	}

	S1:
	if (condition_1(Out, io)) {
		goto S3;
	} else {
		goto S2;
	}

	S2:
	this->program_counter = 0;
	return RETURN_WAIT;

	S3:
	transition_0(In, Out);
	this->program_counter = 0;
	return RETURN_EXECUTED;

	return this->__ret;
}

// -- Scopes
// -- Conditions
bool class_pass::condition_0(hls::stream< uint32_t > &In, IO_pass io){
#pragma HLS INLINE
	return !In.empty();
}

bool class_pass::condition_1(hls::stream< uint32_t > &Out, IO_pass io){
#pragma HLS INLINE
	return !Out.full();
}

// -- Transitions
void class_pass::transition_0(hls::stream< uint32_t > &In, hls::stream< uint32_t > &Out){
#pragma HLS INLINE
	{
		uint32_t l_t__1;
		pinRead(In, l_t__1);
		uint32_t t_11;
		t_11 = l_t__1;
		pinWrite(Out, t_11);
		pinConsume(In);
	}
}

// -- Callables
