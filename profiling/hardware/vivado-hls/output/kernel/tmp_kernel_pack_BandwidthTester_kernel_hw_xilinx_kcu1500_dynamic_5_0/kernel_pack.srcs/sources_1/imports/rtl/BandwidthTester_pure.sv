module BandwidthTester_pure (
	input  wire [31:0] source_Out_pass_In_din,
	output wire source_Out_pass_In_full_n,
	input  wire source_Out_pass_In_write,
	output wire [31:0] source_Out_pass_In_fifo_count,
	output wire [31:0] source_Out_pass_In_fifo_size,
	output wire [31:0] pass_Out_sink_In_dout,
	output wire pass_Out_sink_In_empty_n,
	input  wire pass_Out_sink_In_read,
	output wire [31:0] pass_Out_sink_In_fifo_count,
	output wire [31:0] pass_Out_sink_In_fifo_size,
	input  wire ap_clk,
	input  wire ap_rst_n,
	input  wire ap_start
);
	timeunit 1ps;
	timeprecision 1ps;
	// ------------------------------------------------------------------------
	// -- Parameters

	// -- Queue depth parameters
	parameter Q_SOURCE_OUT_PASS_IN_PASS_IN_ADDR_WIDTH = 12;
	parameter Q_PASS_OUT_PASS_OUT_SINK_IN_ADDR_WIDTH = 12;

	// ------------------------------------------------------------------------
	// -- Wires & Regs

	// -- Queue wires : q_source_Out_pass_In_pass_In
	wire [31:0] q_pass_In_dout;
	wire q_pass_In_empty_n;
	wire q_pass_In_read;

	wire [31:0] q_source_Out_pass_In_pass_In_peek;
	wire [31:0] q_source_Out_pass_In_pass_In_count;
	wire [31:0] q_source_Out_pass_In_pass_In_size;

	// -- Queue wires : q_pass_Out_pass_Out_sink_In
	wire [31:0] q_pass_Out_din;
	wire q_pass_Out_full_n;
	wire q_pass_Out_write;

	wire [31:0] q_pass_Out_pass_Out_sink_In_peek;
	wire [31:0] q_pass_Out_pass_Out_sink_In_count;
	wire [31:0] q_pass_Out_pass_Out_sink_In_size;

	// -- Instance AP Control Wires : pass
	wire pass_ap_start;
	wire pass_ap_done;
	wire pass_ap_idle;
	wire pass_ap_ready;
	wire [31:0] pass_ap_return;

	// ------------------------------------------------------------------------
	// -- FIFO Queues

	// -- Queue FIFO : q_source_Out_pass_In_pass_In
	FIFO #(
		.MEM_STYLE("block"),
		.DATA_WIDTH(32),
		.ADDR_WIDTH(Q_SOURCE_OUT_PASS_IN_PASS_IN_ADDR_WIDTH)
	) q_source_Out_pass_In_pass_In (
		.clk(ap_clk),
		.reset_n(ap_rst_n),
		.if_full_n(source_Out_pass_In_full_n),
		.if_write(source_Out_pass_In_write),
		.if_din(source_Out_pass_In_din),

		.if_empty_n(q_pass_In_empty_n),
		.if_read(q_pass_In_read),
		.if_dout(q_pass_In_dout),

		.peek(q_source_Out_pass_In_pass_In_peek),
		.count(q_source_Out_pass_In_pass_In_count),
		.size(q_source_Out_pass_In_pass_In_size)
	);

	// -- Queue FIFO : q_pass_Out_pass_Out_sink_In
	FIFO #(
		.MEM_STYLE("block"),
		.DATA_WIDTH(32),
		.ADDR_WIDTH(Q_PASS_OUT_PASS_OUT_SINK_IN_ADDR_WIDTH)
	) q_pass_Out_pass_Out_sink_In (
		.clk(ap_clk),
		.reset_n(ap_rst_n),
		.if_full_n(q_pass_Out_full_n),
		.if_write(q_pass_Out_write),
		.if_din(q_pass_Out_din),

		.if_empty_n(pass_Out_sink_In_empty_n),
		.if_read(pass_Out_sink_In_read),
		.if_dout(pass_Out_sink_In_dout),

		.peek(q_pass_Out_pass_Out_sink_In_peek),
		.count(q_pass_Out_pass_Out_sink_In_count),
		.size(q_pass_Out_pass_Out_sink_In_size)
	);

	// ------------------------------------------------------------------------
	// -- Instances

	// -- Instance : pass
	assign pass_ap_start = ap_start;

	pass i_pass(
		.In_V_empty_n(q_pass_In_empty_n),
		.In_V_read(q_pass_In_read),
		.In_V_dout(q_pass_In_dout),


		.Out_V_full_n(q_pass_Out_full_n),
		.Out_V_write(q_pass_Out_write),
		.Out_V_din(q_pass_Out_din),


		.io_In_peek(q_source_Out_pass_In_pass_In_peek),
		.io_In_count(q_source_Out_pass_In_pass_In_count),

		.io_Out_size(q_pass_Out_pass_Out_sink_In_size),
		.io_Out_count(q_pass_Out_pass_Out_sink_In_count),

		.ap_start(pass_ap_start),
		.ap_done(pass_ap_done),
		.ap_idle(pass_ap_idle),
		.ap_ready(pass_ap_ready),
		.ap_return(pass_ap_return),

		.ap_clk(ap_clk),
		.ap_rst_n(ap_rst_n)
	);


endmodule
