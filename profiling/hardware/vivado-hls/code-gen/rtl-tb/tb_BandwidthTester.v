`timescale 1 ns / 1 ps 
`define NULL 0

module tb_BandwidthTester();
	// -- CLK, reset_n and clock cycle
	parameter cycle = 10.0;
	reg clock;
	reg reset_n;

	reg start;
	reg ap_start;
	wire idle;
	wire done;

	// -- Input(s) Idle
	wire input_idle = 1'b0;
	// ------------------------------------------------------------------------
	// -- Input port registers & wires

	// -- File Integers
	integer source_Out_pass_In_data_file;
	integer source_Out_pass_In_scan_file;

	// -- Input port registers, wires and state for reading
	reg  [31:0] source_Out_pass_In_din;
	reg  [31:0] source_Out_pass_In_din_tmp;
	reg source_Out_pass_In_write;
	reg source_Out_pass_In_idle = 1'b0;
	wire source_Out_pass_In_full_n;

	// ------------------------------------------------------------------------
	// -- Output port registers & wires

	// -- File Integers
	integer pass_Out_sink_In_data_file;
	integer pass_Out_sink_In_scan_file;

	// -- Output port registers, wires and state for reading
	wire  [31:0] pass_Out_sink_In_dout;
	wire pass_Out_sink_In_empty_n;
	reg pass_Out_sink_In_read;

	// -- Expected value, end of file and "pass_Out_sink_In" token counter
	reg  [31:0] pass_Out_sink_In_exp_value;
	reg pass_Out_sink_In_end_of_file;
	reg [31:0] pass_Out_sink_In_token_counter;

	// ------------------------------------------------------------------------
	// -- Initial block
	initial begin
		$display("Testbench for entity : BandwidthTester");

		// -- Initialize clock reset and start
		clock = 1'b0;
		reset_n = 1'b0;
		start = 1'b0;

		// -- Initialize input port registers
		source_Out_pass_In_din = 1'b0;
		source_Out_pass_In_din_tmp = 1'b0;
		source_Out_pass_In_write = 1'b0;

		// -- Initialize output port registers
		pass_Out_sink_In_read = 1'b0;
		pass_Out_sink_In_end_of_file = 1'b0;
		pass_Out_sink_In_token_counter = 0;

		// -- Open input vector data files
		source_Out_pass_In_data_file = $fopen("../../../../../fifo-traces/source_Out_pass_In.txt" ,"r");
		if (source_Out_pass_In_data_file == `NULL) begin
			$display("Error: File source_Out_pass_In.txt does not exist !!!");
			$finish;
		end

		// -- Open output vector data files
		pass_Out_sink_In_data_file = $fopen("../../../../../fifo-traces/pass_Out_sink_In.txt" ,"r");
		if (pass_Out_sink_In_data_file == `NULL) begin
			$display("Error: File pass_Out_sink_In.txt does not exist !!!");
			$finish;
		end

		#55 reset_n = 1'b1;
		#10 start = 1'b1;
	end

	// ------------------------------------------------------------------------
	// -- Clock generation
	always #(cycle / 2) clock = !clock;

	// ------------------------------------------------------------------------
	// -- ap_start pulse generator
	reg pulse_delay;

	always @(posedge clock)
		pulse_delay <= start;

	always @(posedge clock)
		ap_start <= start && !pulse_delay;

	// ------------------------------------------------------------------------
	// -- Read from the files and write to the input fifos
	always @(posedge clock) begin
		if (reset_n == 1'b1) begin
			if (source_Out_pass_In_full_n) begin
				if (!$feof(source_Out_pass_In_data_file)) begin
					source_Out_pass_In_scan_file = $fscanf(source_Out_pass_In_data_file, "%d\n", source_Out_pass_In_din_tmp);
					source_Out_pass_In_din <= source_Out_pass_In_din_tmp;
					source_Out_pass_In_write <= 1'b1;
					source_Out_pass_In_idle <= 1'b0;
				end else begin
					source_Out_pass_In_write <= 1'b0;
					source_Out_pass_In_idle <= 1'b1;
				end
			end
		end
	end

	// ------------------------------------------------------------------------
	// -- Read from output ports
	always @(posedge clock) begin
		if (reset_n == 1'b1) begin
			if (pass_Out_sink_In_empty_n) begin
				pass_Out_sink_In_read <= 1'b1;
			end else begin
				pass_Out_sink_In_read <= 1'b0;
			end
		end
	end

	// ------------------------------------------------------------------------
	// -- Compare with golden reference
	always @(posedge clock) begin
		if (!$feof(pass_Out_sink_In_data_file)) begin
			if(pass_Out_sink_In_read & pass_Out_sink_In_empty_n) begin
				pass_Out_sink_In_scan_file = $fscanf(pass_Out_sink_In_data_file, "%d\n", pass_Out_sink_In_exp_value);
				if (pass_Out_sink_In_dout != pass_Out_sink_In_exp_value) begin
					$display("Time: %0d ns, Port pass_Out_sink_In: Error !!! Expected value does not match golden reference, Token Counter: %0d", $time, pass_Out_sink_In_token_counter);
					$display("\tGot      : %0d", pass_Out_sink_In_dout);
					$display("\tExpected : %0d", pass_Out_sink_In_exp_value);

					$finish;
				end
				pass_Out_sink_In_token_counter <= pass_Out_sink_In_token_counter + 1;
			end
		end else begin
			pass_Out_sink_In_end_of_file <= 1'b1;
		end
	end

	// ------------------------------------------------------------------------
	// -- Design under test

	// -- Network input idle
	assign input_idle = source_Out_pass_In_idle;

	BandwidthTester dut(
		.source_Out_pass_In_din(source_Out_pass_In_din),
		.source_Out_pass_In_full_n(source_Out_pass_In_full_n),
		.source_Out_pass_In_write(source_Out_pass_In_write),
		.source_Out_pass_In_fifo_count(), // unused
		.source_Out_pass_In_fifo_size(), // unused
		// -- trigger constants
		.source_Out_pass_In_sleep(1'b1),
		.source_Out_pass_In_sync_wait(1'b1),
		.source_Out_pass_In_sync_exec(1'b0),
		.source_Out_pass_In_waited(1'b1),
		.source_Out_pass_In_all_waited(), // unused

		.pass_Out_sink_In_dout(pass_Out_sink_In_dout),
		.pass_Out_sink_In_empty_n(pass_Out_sink_In_empty_n),
		.pass_Out_sink_In_read(pass_Out_sink_In_read),
		.pass_Out_sink_In_fifo_count(), // unused
		.pass_Out_sink_In_fifo_size(), // unused
		// -- trigger constants
		.pass_Out_sink_In_sleep(1'b1),
		.pass_Out_sink_In_sync_wait(1'b1),
		.pass_Out_sink_In_sync_exec(1'b0),
		.pass_Out_sink_In_waited(1'b1),
		.pass_Out_sink_In_all_waited(), // unused

		.ap_clk(clock),
		.ap_rst_n(reset_n),
		.ap_start(ap_start),
		.ap_idle(idle),
		.ap_done(done)
	);

	// ------------------------------------------------------------------------
	// -- End of simulation
	always @(posedge clock) begin
		if (done || pass_Out_sink_In_end_of_file) begin
			$display("Simulation has terminated !");
			$finish;
		end
	end

endmodule
