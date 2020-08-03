`include "TriggerTypes.sv"
import TriggerTypes::*;

module BandwidthTester (
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
	// -- Trigger signals from IO stages
	// -- inputs
	input  wire source_Out_pass_In_sleep,
	input  wire source_Out_pass_In_sync_wait,
	input  wire source_Out_pass_In_sync_exec,
	input  wire source_Out_pass_In_waited,
	output wire source_Out_pass_In_all_waited,
	// -- outputs
	input  wire pass_Out_sink_In_sleep,
	input  wire pass_Out_sink_In_sync_wait,
	input  wire pass_Out_sink_In_sync_exec,
	input  wire pass_Out_sink_In_waited,
	output wire pass_Out_sink_In_all_waited,
	// -- global signals
	output wire all_sync,
	output wire all_sync_wait,
	output wire all_sleep,
	input  wire ap_clk,
	input  wire ap_rst_n,
	input  wire ap_start,
	output wire ap_idle,
	output wire ap_done
);
	timeunit 1ps;
	timeprecision 1ps;
	// ------------------------------------------------------------------------
	// -- Parameters

	// -- Trigger Mode
	localparam mode_t trigger_mode = ACTOR_TRIGGER;

	// -- Queue depth parameters
	parameter Q_SOURCE_OUT_PASS_IN_PASS_IN_ADDR_WIDTH = 12;
	parameter Q_PASS_OUT_PASS_OUT_SINK_IN_ADDR_WIDTH = 12;

	// ------------------------------------------------------------------------
	// -- Wires & Regs

	reg am_idle_r = 1'b1;
	wire am_idle;

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


	// -- global trigger signals
	wire    all_sleep;
	wire    all_sync;
	wire    all_sync_wait;
	wire    external_enqueue;

	// -- Signals for the trigger module of pass
	wire    pass_trigger_ap_done;
	wire    pass_trigger_ap_idle;
	wire    pass_trigger_ap_ready;	// currently inactive
	// -- Local synchronization signals
	wire    pass_sleep;
	wire    pass_sync_wait;
	wire    pass_sync_exec;
	wire    pass_sync;
	// -- Signal for wake up and sleep
	wire    pass_waited;
	wire    pass_all_waited;



	// -- Local IO sync signals
	wire    source_Out_pass_In_sync;
	wire    pass_Out_sink_In_sync;

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
	trigger #(.mode(trigger_mode)) i_pass_trigger (
		.ap_clk(ap_clk),
		.ap_rst_n(ap_rst_n),
		.ap_start(ap_start),
		.ap_done(pass_trigger_ap_done),
		.ap_idle(pass_trigger_ap_idle),
		.ap_ready(pass_trigger_ap_ready),
		.external_enqueue(external_enqueue),
		.all_sync(all_sync),
		.all_sync_wait(all_sync_wait),
		.all_sleep(all_sleep),
		.sleep(pass_sleep),
		.sync_exec(pass_sync_exec),
		.sync_wait(pass_sync_wait),
		.waited(pass_waited),
		.all_waited(pass_all_waited),
		.actor_return(pass_ap_return[1:0]),
		.actor_done(pass_ap_done),
		.actor_ready(pass_ap_ready),
		.actor_idle(pass_ap_idle),
		.actor_start(pass_ap_start)
	);

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

	// ------------------------------------------------------------------------
	// -- Assignments

	// --Local sync signals
	assign pass_sync = pass_sync_exec | pass_sync_wait;
	assign source_Out_pass_In_sync = source_Out_pass_In_sync_exec | source_Out_pass_In_sync_wait;
	assign pass_Out_sink_In_sync = pass_Out_sink_In_sync_exec | pass_Out_sink_In_sync_wait;
	// -- Local wait signals
	assign pass_all_waited = source_Out_pass_In_waited & pass_Out_sink_In_waited;
	assign source_Out_pass_In_all_waited = pass_waited & pass_Out_sink_In_waited;
	assign pass_Out_sink_In_all_waited = pass_waited & source_Out_pass_In_waited;

	// -- global sync signals
	assign all_sleep = pass_sleep & source_Out_pass_In_sleep & pass_Out_sink_In_sleep;
	assign all_sync_wait = pass_sync_wait & source_Out_pass_In_sync_wait & pass_Out_sink_In_sync_wait;
	assign all_sync = pass_sync & source_Out_pass_In_sync & pass_Out_sink_In_sync;
	assign external_enqueue = 1'b0;

	// -- Actor Machine Idleness
	always @(posedge ap_clk) begin
		if (ap_rst_n == 1'b0)
			am_idle_r <= 1'b1;
		else
			am_idle_r <= am_idle;
	end
	assign am_idle = pass_trigger_ap_idle;
	// -- AP Done
	assign ap_done = am_idle & (~am_idle_r);

	// -- AP Idle
	assign ap_idle = am_idle;

	// -- external assignments
	assign  source_Out_pass_In_fifo_count = q_source_Out_pass_In_pass_In_count;
	assign  source_Out_pass_In_fifo_size = q_source_Out_pass_In_pass_In_size;
	assign  pass_Out_sink_In_fifo_count = q_pass_Out_pass_Out_sink_In_count;
	assign  pass_Out_sink_In_fifo_size = q_pass_Out_pass_Out_sink_In_size;


endmodule
