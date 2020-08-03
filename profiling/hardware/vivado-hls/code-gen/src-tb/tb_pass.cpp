#include <fstream>
#include <sstream>
#include <iostream>
#include <string>
#include <stdint.h>
#include "hls_stream.h"

#include "pass.h"

// -- Instance prototype
int pass(hls::stream< uint32_t > &In, hls::stream< uint32_t > &Out, IO_pass io);

// -- HLS Testbench
int main(){
	// -- File Streams
	std::ifstream In_file("fifo-traces/pass/In.txt");
	if(In_file.fail()){
		std::cout <<"In input file not found!" << std::endl;
		return 1;
	}

	std::ifstream Out_file("fifo-traces/pass/Out.txt");
	if(Out_file.fail()){
		std::cout <<"Out input file not found!" << std::endl;
		return 1;
	}

	// -- Input Channels
	hls::stream< uint32_t > In;

	// -- Output Channels
	hls::stream< uint32_t > Out;

	// -- Queue reference
	std::queue< uint32_t > qref_Out;

	// -- Write tokens to stream
	std::string In_line;
	while(std::getline(In_file, In_line)){
		std::istringstream iss(In_line);
		uint32_t In_tmp;
		iss >> In_tmp;
		In.write((uint32_t) In_tmp);
	}

	// -- Store output tokens to the reference queue
	std::string Out_line;
	while(std::getline(Out_file, Out_line)){
		std::istringstream iss(Out_line);
		uint32_t Out_tmp;
		iss >> Out_tmp;
		qref_Out.push((uint32_t) Out_tmp);
	}

	// -- Output Counters
	uint32_t Out_token_counter = 0;

	// -- End of execution
	bool end_of_execution = false;

	// -- Execute instance under test
	while(!end_of_execution) {
		IO_pass io_pass;

		io_pass.In_count = In.size();
		io_pass.In_peek = In._data[0];
		io_pass.Out_count = 0;
		io_pass.Out_size = 512;

		int ret = pass(In, Out, io_pass);

		while(!Out.empty() && !qref_Out.empty()) {
			uint32_t got_value = Out.read();
			uint32_t ref_value = qref_Out.front();
			qref_Out.pop();
			if(got_value != ref_value) {
				std::cout << "Port Out: Error !!! Expected value does not match golden reference, Token Counter: " << Out_token_counter << std::endl;
				std::cout << "Expected: " << unsigned(ref_value) << std::endl;
				std::cout << "Got: "      << unsigned(got_value) << std::endl;

				return 1;
			}

			Out_token_counter++;
		}

		end_of_execution = ret == RETURN_WAIT;
	}

	std::cout <<  "Port Out : " << Out_token_counter << " produced." << std::endl;

	return 0;
}
