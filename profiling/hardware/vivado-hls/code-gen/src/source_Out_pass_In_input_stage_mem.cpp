#include <stdint.h>
#include <hls_stream.h>
#include "iostage.h"

using namespace iostage;

uint32_t source_Out_pass_In_input_stage_mem(uint32_t source_Out_pass_In_requested_size, bus_t *source_Out_pass_In_size, bus_t *source_Out_pass_In_buffer, uint32_t fifo_count, uint32_t fifo_size, hls::stream< uint32_t > &source_Out_pass_In, hls::stream< uint64_t > source_Out_pass_In_offset) {
#pragma HLS INTERFACE m_axi port=source_Out_pass_In_size offset=direct bundle=source_Out_pass_In max_read_burst_length=256 max_write_burst_length=256
#pragma HLS INTERFACE m_axi port=source_Out_pass_In_buffer offset=direct bundle=source_Out_pass_In max_read_burst_length=256 max_write_burst_length=256
#pragma HLS INTERFACE ap_fifo port=source_Out_pass_In
#pragma HLS INTERFACE ap_fifo port=source_Out_pass_In_offset
#pragma HLS INTERFACE ap_ctrl_hs register port=return
	static class_input_stage_mem< uint32_t  > i_source_Out_pass_In_input_stage_mem;

	return i_source_Out_pass_In_input_stage_mem(source_Out_pass_In_requested_size, source_Out_pass_In_size, source_Out_pass_In_buffer, fifo_count, fifo_size, source_Out_pass_In, source_Out_pass_In_offset);
}
