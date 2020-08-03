`timescale 1 ns / 1 ps 
`define NULL 0

module tb_pass();
	// -- CLK, reset_n and clock cycle
	parameter cycle = 10.0;
	reg clock;
	reg reset_n;

	reg start;
	reg ap_start;
	wire idle;
	wire done;

	// ------------------------------------------------------------------------
	// -- Input port registers & wires

	// -- File Integers
	integer In_data_file;
	integer In_scan_file;

	// -- Input port registers, wires and state for reading
	reg  [31:0] In_din;
	reg  [31:0] In_din_tmp;
	reg In_write;
	reg In_idle = 1'b0;
	wire In_full_n;

	// ------------------------------------------------------------------------
	// -- Output port registers & wires

	// -- File Integers
	integer Out_data_file;
	integer Out_scan_file;

	// -- Output port registers, wires and state for reading
	wire  [31:0] Out_dout;
	wire Out_empty_n;
	reg Out_read;

	// -- Expected value, end of file and "Out" token counter
	reg  [31:0] Out_exp_value;
	reg Out_end_of_file;
	reg [31:0] Out_token_counter;

	// -- Queue wires for port : In
	wire q_pass_In_empty_n;
	wire q_pass_In_read;
	wire [31:0] q_pass_In_dout;
	wire [31:0] q_pass_In_peek;
	wire [31:0] q_pass_In_count;
	wire [31:0] q_pass_In_size;

	// -- Queue wires for port : Out
	wire q_pass_Out_full_n;
	wire q_pass_Out_write;
	wire [31:0] q_pass_Out_din;
	wire [31:0] q_pass_Out_peek;
	wire [31:0] q_pass_Out_count;
	wire [31:0] q_pass_Out_size;

	// ------------------------------------------------------------------------
	// -- Initial block
	initial begin
		$display("Testbench for entity : pass");

		// -- Initialize clock reset and start
		clock = 1'b0;
		reset_n = 1'b0;
		start = 1'b0;

		// -- Initialize input port registers
		In_din = 1'b0;
		In_din_tmp = 1'b0;
		In_write = 1'b0;

		// -- Initialize output port registers
		Out_read = 1'b0;
		Out_end_of_file = 1'b0;
		Out_token_counter = 0;

		// -- Open input vector data files
		In_data_file = $fopen("../../../../../fifo-traces/pass/In.txt" ,"r");
		if (In_data_file == `NULL) begin
			$display("Error: File pass/In.txt does not exist !!!");
			$finish;
		end

		// -- Open output vector data files
		Out_data_file = $fopen("../../../../../fifo-traces/pass/Out.txt" ,"r");
		if (Out_data_file == `NULL) begin
			$display("Error: File pass/Out.txt does not exist !!!");
			$finish;
		end

		#55 reset_n = 1'b1;
		#10 start = 1'b1;
	end

	// ------------------------------------------------------------------------
	// -- Clock generation
	always #(cycle / 2) clock = !clock;

	// ------------------------------------------------------------------------
	// -- Read from the files and write to the input fifos
	always @(posedge clock) begin
		if (reset_n == 1'b1) begin
			if (In_full_n) begin
				if (!$feof(In_data_file)) begin
					In_scan_file = $fscanf(In_data_file, "%d\n", In_din_tmp);
					In_din <= In_din_tmp;
					In_write <= 1'b1;
					In_idle <= 1'b0;
				end else begin
					In_write <= 1'b0;
					In_idle <= 1'b1;
				end
			end
		end
	end

	// ------------------------------------------------------------------------
	// -- Read from output ports
	always @(posedge clock) begin
		if (reset_n == 1'b1) begin
			if (Out_empty_n) begin
				Out_read <= 1'b1;
			end else begin
				Out_read <= 1'b0;
			end
		end
	end

	// ------------------------------------------------------------------------
	// -- Compare with golden reference
	always @(posedge clock) begin
		if (!$feof(Out_data_file)) begin
			if(Out_read & Out_empty_n) begin
				Out_scan_file = $fscanf(Out_data_file, "%d\n", Out_exp_value);
				if (Out_dout != Out_exp_value) begin
					$display("Time: %0d ns, Port Out: Error !!! Expected value does not match golden reference, Token Counter: %0d", $time, Out_token_counter);
					$display("\tGot      : %0d", Out_dout);
					$display("\tExpected : %0d", Out_exp_value);

					$finish;
				end
				Out_token_counter <= Out_token_counter + 1;
			end
		end else begin
			Out_end_of_file <= 1'b1;
		end
	end

	// ------------------------------------------------------------------------
	// -- Queues for input ports
	// -- Queue FIFO for port : In
	FIFO #(
		.MEM_STYLE("block"),
		.DATA_WIDTH(32),
		.ADDR_WIDTH(9)
	) q_pass_In (
		.clk(clock),
		.reset_n(reset_n),
		.if_full_n(In_full_n),
		.if_write(In_write),
		.if_din(In_din),

		.if_empty_n(q_pass_In_empty_n),
		.if_read(q_pass_In_read),
		.if_dout(q_pass_In_dout),

		.peek(q_pass_In_peek),
		.count(q_pass_In_count),
		.size(q_pass_In_size)
	);

	// ------------------------------------------------------------------------
	// -- Queues for output ports
	// -- Queue FIFO for port : Out
	FIFO #(
		.MEM_STYLE("block"),
		.DATA_WIDTH(32),
		.ADDR_WIDTH(9)
	) q_pass_Out (
		.clk(clock),
		.reset_n(reset_n),
		.if_full_n(q_pass_Out_full_n),
		.if_write(q_pass_Out_write),
		.if_din(q_pass_Out_din),

		.if_empty_n(Out_empty_n),
		.if_read(Out_read),
		.if_dout(Out_dout),

		.peek(q_pass_Out_peek),
		.count(q_pass_Out_count),
		.size(q_pass_Out_size)
	);

	// ------------------------------------------------------------------------
	// -- Design under test
	pass dut(
		.In_r_V_dout(q_pass_In_dout),
		.In_r_V_empty_n(q_pass_In_empty_n),
		.In_r_V_read(q_pass_In_read),
		.In_r_V_fifo_count(), // unused
		.In_r_V_fifo_size(), // unused
		// -- trigger constants
		.In_r_V_sleep(1'b1),
		.In_r_V_sync_wait(1'b1),
		.In_r_V_sync_exec(1'b0),
		.In_r_V_waited(1'b1),
		.In_r_V_all_waited(), // unused

		.Out_r_V_din(q_pass_Out_din),
		.Out_r_V_full_n(q_pass_Out_full_n),
		.Out_r_V_write(q_pass_Out_write),
		.Out_r_V_fifo_count(), // unused
		.Out_r_V_fifo_size(), // unused
		// -- trigger constants
		.Out_r_V_sleep(1'b1),
		.Out_r_V_sync_wait(1'b1),
		.Out_r_V_sync_exec(1'b0),
		.Out_r_V_waited(1'b1),
		.Out_r_V_all_waited(), // unused

		.io_In_peek(q_pass_In_peek),
		.io_In_count(q_pass_In_count),

		.io_Out_size(4096),
		.io_Out_count(0),

		.ap_clk(clock),
		.ap_rst_n(reset_n),
		.ap_start(start),
		.ap_idle(idle)
	);

	// ------------------------------------------------------------------------
	// -- End of simulation
	always @(posedge clock) begin
		if (done || Out_end_of_file) begin
			$display("Simulation has terminated !");
			$finish;
		end
	end

endmodule
