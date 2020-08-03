#include <fstream>
#include <sstream>
#include <iostream>
#include <string>
#include <stdint.h>
#include "hls_stream.h"

#include "pass.h"

// -- Instance prototypes
int pass(hls::stream< uint32_t > &In, hls::stream< uint32_t > &Out, IO_pass io);

// -- HLS Network Testbench
int main(){
	// -- File Streams
	std::ifstream source_Out_pass_In_file("fifo-traces/source_Out_pass_In.txt");
	if(source_Out_pass_In_file.fail()){
		std::cout <<"source_Out_pass_In input file not found!" << std::endl;
		return 1;
	}

	std::ifstream pass_Out_sink_In_file("fifo-traces/pass_Out_sink_In.txt");
	if(pass_Out_sink_In_file.fail()){
		std::cout <<"pass_Out_sink_In input file not found!" << std::endl;
		return 1;
	}

	hls::stream< uint32_t > q_source_Out_pass_In_pass_In("q_source_Out_pass_In_pass_In");
	hls::stream< uint32_t > q_pass_Out_pass_Out_sink_In("q_pass_Out_pass_Out_sink_In");

	// -- Queue reference
	std::queue< uint32_t > qref_pass_Out_sink_In;

	// -- Write tokens to stream
	std::string source_Out_pass_In_line;
	while(std::getline(source_Out_pass_In_file, source_Out_pass_In_line)){
		std::istringstream iss(source_Out_pass_In_line);
		uint32_t source_Out_pass_In_tmp;
		iss >> source_Out_pass_In_tmp;
		q_source_Out_pass_In_pass_In.write((uint32_t) source_Out_pass_In_tmp);
	}

	// -- Store output tokens to the reference queue
	std::string pass_Out_sink_In_line;
	while(std::getline(pass_Out_sink_In_file, pass_Out_sink_In_line)){
		std::istringstream iss(pass_Out_sink_In_line);
		uint32_t pass_Out_sink_In_tmp;
		iss >> pass_Out_sink_In_tmp;
		qref_pass_Out_sink_In.push((uint32_t) pass_Out_sink_In_tmp);
	}

	// -- Output Counters
	uint32_t pass_Out_sink_In_token_counter = 0;

	// -- End of execution
	bool end_of_execution;

	// -- Execute instance under test
	do {
		end_of_execution = false;

		IO_pass io_pass;

		io_pass.In_count = q_source_Out_pass_In_pass_In.size();
		if(io_pass.In_count)
			io_pass.In_peek = q_source_Out_pass_In_pass_In._data[0];
		io_pass.Out_count = q_pass_Out_pass_Out_sink_In.size();
		io_pass.Out_size = 4096;

		end_of_execution |= pass(q_source_Out_pass_In_pass_In, q_pass_Out_pass_Out_sink_In, io_pass) == RETURN_EXECUTED;

		while(!q_pass_Out_pass_Out_sink_In.empty() && !qref_pass_Out_sink_In.empty()) {
			uint32_t got_value = q_pass_Out_pass_Out_sink_In.read();
			uint32_t ref_value = qref_pass_Out_sink_In.front();
			qref_pass_Out_sink_In.pop();
			if(got_value != ref_value) {
				std::cout << "Port pass_Out_sink_In: Error !!! Expected value does not match golden reference, Token Counter: " << pass_Out_sink_In_token_counter << std::endl;
				std::cout << "Expected: " << unsigned(ref_value) << std::endl;
				std::cout << "Got: "      << unsigned(got_value) << std::endl;

				return 1;
			}

			pass_Out_sink_In_token_counter++;
		}

	} while(end_of_execution);

	std::cout <<  "Port pass_Out_sink_In : " << pass_Out_sink_In_token_counter << " produced." << std::endl;

	return 0;
}
