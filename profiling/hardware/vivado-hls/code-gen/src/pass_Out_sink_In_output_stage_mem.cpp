#include <stdint.h>
#include <hls_stream.h>
#include "iostage.h"

using namespace iostage;

uint32_t pass_Out_sink_In_output_stage_mem(uint32_t pass_Out_sink_In_available_size, bus_t *pass_Out_sink_In_size, bus_t *pass_Out_sink_In_buffer, uint32_t fifo_count, hls::stream< uint32_t > &pass_Out_sink_In, hls::stream< uint64_t > &pass_Out_sink_In_offset) {
#pragma HLS INTERFACE m_axi port=pass_Out_sink_In_size offset=direct bundle=pass_Out_sink_In
#pragma HLS INTERFACE m_axi port=pass_Out_sink_In_buffer offset=direct bundle=pass_Out_sink_In
#pragma HLS INTERFACE ap_fifo port=pass_Out_sink_In
#pragma HLS INTERFACE ap_fifo port=pass_Out_sink_In_offset
#pragma HLS INTERFACE ap_ctrl_hs register port=return
	static class_output_stage_mem< uint32_t  > i_pass_Out_sink_In_output_stage_mem;

	return i_pass_Out_sink_In_output_stage_mem(pass_Out_sink_In_available_size, pass_Out_sink_In_size, pass_Out_sink_In_buffer, fifo_count, pass_Out_sink_In, pass_Out_sink_In_offset);
}
